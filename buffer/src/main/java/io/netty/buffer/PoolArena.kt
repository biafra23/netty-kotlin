/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.buffer

import io.netty.util.internal.CleanableDirectBuffer
import io.netty.util.internal.PlatformDependent
import io.netty.util.internal.StringUtil
import java.nio.ByteBuffer
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.LongAdder
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.max

abstract class PoolArena<T> internal constructor(
    @JvmField val parent: PooledByteBufAllocator?,
    @JvmField internal val sizeClass: SizeClasses
) : PoolArenaMetric {

    enum class SizeClass {
        Small,
        Normal
    }

    @JvmField
    internal val smallSubpagePools: Array<PoolSubpage<T>>

    private val q050: PoolChunkList<T>
    private val q025: PoolChunkList<T>
    private val q000: PoolChunkList<T>
    private val qInit: PoolChunkList<T>
    private val q075: PoolChunkList<T>
    private val q100: PoolChunkList<T>

    private val chunkListMetrics: List<PoolChunkListMetric>

    // Metrics for allocations and deallocations
    private var allocationsNormal: Long = 0
    // We need to use the LongCounter here as this is not guarded via synchronized block.
    private val allocationsSmall = LongAdder()
    private val allocationsHuge = LongAdder()
    private val activeBytesHuge = LongAdder()

    private var deallocationsSmall: Long = 0
    private var deallocationsNormal: Long = 0

    private var pooledChunkAllocations: Long = 0
    private var pooledChunkDeallocations: Long = 0

    // We need to use the LongCounter here as this is not guarded via synchronized block.
    private val deallocationsHuge = LongAdder()

    // Number of thread caches backed by this arena.
    @JvmField
    val numThreadCaches: AtomicInteger = AtomicInteger()

    private val lock = ReentrantLock()

    init {
        assert(sizeClass != null)

        @Suppress("UNCHECKED_CAST")
        smallSubpagePools = arrayOfNulls<PoolSubpage<*>>(sizeClass.nSubpages) as Array<PoolSubpage<T>>
        for (i in smallSubpagePools.indices) {
            smallSubpagePools[i] = newSubpagePoolHead(i)
        }

        q100 = PoolChunkList(this, null, 100, Int.MAX_VALUE, sizeClass.chunkSize)
        q075 = PoolChunkList(this, q100, 75, 100, sizeClass.chunkSize)
        q050 = PoolChunkList(this, q100, 50, 100, sizeClass.chunkSize)
        q025 = PoolChunkList(this, q050, 25, 75, sizeClass.chunkSize)
        q000 = PoolChunkList(this, q025, 1, 50, sizeClass.chunkSize)
        qInit = PoolChunkList(this, q000, Int.MIN_VALUE, 25, sizeClass.chunkSize)

        q100.prevList(q075)
        q075.prevList(q050)
        q050.prevList(q025)
        q025.prevList(q000)
        q000.prevList(null)
        qInit.prevList(qInit)

        val metrics = ArrayList<PoolChunkListMetric>(6)
        metrics.add(qInit)
        metrics.add(q000)
        metrics.add(q025)
        metrics.add(q050)
        metrics.add(q075)
        metrics.add(q100)
        chunkListMetrics = Collections.unmodifiableList(metrics)
    }

    private fun newSubpagePoolHead(index: Int): PoolSubpage<T> {
        val head = PoolSubpage<T>(index)
        head.prev = head
        head.next = head
        return head
    }

    internal abstract fun isDirect(): Boolean

    internal fun allocate(cache: PoolThreadCache, reqCapacity: Int, maxCapacity: Int): PooledByteBuf<T> {
        val buf = newByteBuf(maxCapacity)
        allocate(cache, buf, reqCapacity)
        return buf
    }

    private fun allocate(cache: PoolThreadCache, buf: PooledByteBuf<T>, reqCapacity: Int) {
        val sizeIdx = sizeClass.size2SizeIdx(reqCapacity)

        if (sizeIdx <= sizeClass.smallMaxSizeIdx) {
            tcacheAllocateSmall(cache, buf, reqCapacity, sizeIdx)
        } else if (sizeIdx < sizeClass.nSizes) {
            tcacheAllocateNormal(cache, buf, reqCapacity, sizeIdx)
        } else {
            val normCapacity = if (sizeClass.directMemoryCacheAlignment > 0)
                sizeClass.normalizeSize(reqCapacity) else reqCapacity
            // Huge allocations are never served via the cache so just call allocateHuge
            allocateHuge(buf, normCapacity)
        }
    }

    private fun tcacheAllocateSmall(
        cache: PoolThreadCache, buf: PooledByteBuf<T>, reqCapacity: Int, sizeIdx: Int
    ) {
        if (cache.allocateSmall(this, buf, reqCapacity, sizeIdx)) {
            // was able to allocate out of the cache so move on
            return
        }

        /*
         * Synchronize on the head. This is needed as PoolChunk.allocateSubpage(int) and
         * PoolChunk.free(long) may modify the doubly linked list as well.
         */
        val head = smallSubpagePools[sizeIdx]
        val needsNormalAllocation: Boolean
        head.lock()
        try {
            val s = head.next!!
            needsNormalAllocation = s === head
            if (!needsNormalAllocation) {
                assert(s.doNotDestroy && s.elemSize == sizeClass.sizeIdx2size(sizeIdx)) {
                    "doNotDestroy=${s.doNotDestroy}, elemSize=${s.elemSize}, sizeIdx=$sizeIdx"
                }
                val handle = s.allocate()
                assert(handle >= 0)
                s.chunk!!.initBufWithSubpage(buf, null, handle, reqCapacity, cache, false)
            }
        } finally {
            head.unlock()
        }

        if (needsNormalAllocation) {
            lock()
            try {
                allocateNormal(buf, reqCapacity, sizeIdx, cache)
            } finally {
                unlock()
            }
        }

        incSmallAllocation()
    }

    private fun tcacheAllocateNormal(
        cache: PoolThreadCache, buf: PooledByteBuf<T>, reqCapacity: Int, sizeIdx: Int
    ) {
        if (cache.allocateNormal(this, buf, reqCapacity, sizeIdx)) {
            // was able to allocate out of the cache so move on
            return
        }
        lock()
        try {
            allocateNormal(buf, reqCapacity, sizeIdx, cache)
            ++allocationsNormal
        } finally {
            unlock()
        }
    }

    private fun allocateNormal(buf: PooledByteBuf<T>, reqCapacity: Int, sizeIdx: Int, threadCache: PoolThreadCache) {
        assert(lock.isHeldByCurrentThread)
        if (q050.allocate(buf, reqCapacity, sizeIdx, threadCache) ||
            q025.allocate(buf, reqCapacity, sizeIdx, threadCache) ||
            q000.allocate(buf, reqCapacity, sizeIdx, threadCache) ||
            qInit.allocate(buf, reqCapacity, sizeIdx, threadCache) ||
            q075.allocate(buf, reqCapacity, sizeIdx, threadCache)
        ) {
            return
        }

        // Add a new chunk.
        val c = newChunk(sizeClass.pageSize, sizeClass.nPSizes, sizeClass.pageShifts, sizeClass.chunkSize)
        PooledByteBufAllocator.onAllocateChunk(c, true)
        val success = c.allocate(buf, reqCapacity, sizeIdx, threadCache)
        assert(success)
        qInit.add(c)
        ++pooledChunkAllocations
    }

    private fun incSmallAllocation() {
        allocationsSmall.increment()
    }

    private fun allocateHuge(buf: PooledByteBuf<T>, reqCapacity: Int) {
        val chunk = newUnpooledChunk(reqCapacity)
        PooledByteBufAllocator.onAllocateChunk(chunk, false)
        activeBytesHuge.add(chunk.chunkSize().toLong())
        buf.initUnpooled(chunk, reqCapacity)
        allocationsHuge.increment()
    }

    internal fun free(chunk: PoolChunk<T>, nioBuffer: ByteBuffer?, handle: Long, normCapacity: Int, cache: PoolThreadCache?) {
        chunk.decrementPinnedMemory(normCapacity)
        if (chunk.unpooled) {
            val size = chunk.chunkSize()
            destroyChunk(chunk)
            activeBytesHuge.add(-size.toLong())
            deallocationsHuge.increment()
        } else {
            val sizeClass = sizeClass(handle)
            if (cache != null && cache.add(this, chunk, nioBuffer, handle, normCapacity, sizeClass)) {
                // cached so not free it.
                return
            }

            freeChunk(chunk, handle, normCapacity, sizeClass, nioBuffer, false)
        }
    }

    internal fun freeChunk(
        chunk: PoolChunk<T>, handle: Long, normCapacity: Int, sizeClass: SizeClass,
        nioBuffer: ByteBuffer?, finalizer: Boolean
    ) {
        val destroyChunk: Boolean
        lock()
        try {
            // We only call this if freeChunk is not called because of the PoolThreadCache finalizer as otherwise this
            // may fail due lazy class-loading in for example tomcat.
            if (!finalizer) {
                when (sizeClass) {
                    SizeClass.Normal -> ++deallocationsNormal
                    SizeClass.Small -> ++deallocationsSmall
                }
            }
            destroyChunk = !chunk.parent!!.free(chunk, handle, normCapacity, nioBuffer)
            if (destroyChunk) {
                // all other destroyChunk calls come from the arena itself being finalized, so don't need to be counted
                ++pooledChunkDeallocations
            }
        } finally {
            unlock()
        }
        if (destroyChunk) {
            // destroyChunk not need to be called while holding the synchronized lock.
            destroyChunk(chunk)
        }
    }

    internal fun reallocate(buf: PooledByteBuf<T>, newCapacity: Int) {
        assert(newCapacity >= 0 && newCapacity <= buf.maxCapacity())

        val oldCapacity: Int
        val oldChunk: PoolChunk<T>
        val oldNioBuffer: ByteBuffer?
        val oldHandle: Long
        val oldMemory: T
        val oldOffset: Int
        val oldMaxLength: Int
        val oldCache: PoolThreadCache?

        // We synchronize on the ByteBuf itself to ensure there is no "concurrent" reallocations for the same buffer.
        // We do this to ensure the ByteBuf internal fields that are used to allocate / free are not accessed
        // concurrently. This is important as otherwise we might end up corrupting our internal state of our data
        // structures.
        //
        // Also note we don't use a Lock here but just synchronized even tho this might seem like a bad choice for Loom.
        // This is done to minimize the overhead per ByteBuf. The time this would block another thread should be
        // relative small and so not be a problem for Loom.
        // See https://github.com/netty/netty/issues/13467
        synchronized(buf) {
            oldCapacity = buf.length
            if (oldCapacity == newCapacity) {
                return
            }

            oldChunk = buf.chunk!!
            oldNioBuffer = buf.tmpNioBuf
            oldHandle = buf.handle
            oldMemory = buf.memory!!
            oldOffset = buf.offset
            oldMaxLength = buf.maxLength
            oldCache = buf.cache

            // This does not touch buf's reader/writer indices
            allocate(parent!!.threadCache(), buf, newCapacity)
        }
        val bytesToCopy: Int
        if (newCapacity > oldCapacity) {
            bytesToCopy = oldCapacity
        } else {
            buf.trimIndicesToCapacityInternal(newCapacity)
            bytesToCopy = newCapacity
        }
        memoryCopy(oldMemory, oldOffset, buf, bytesToCopy)
        free(oldChunk, oldNioBuffer, oldHandle, oldMaxLength, oldCache)
    }

    override fun numThreadCaches(): Int = numThreadCaches.get()

    override fun numTinySubpages(): Int = 0

    override fun numSmallSubpages(): Int = smallSubpagePools.size

    override fun numChunkLists(): Int = chunkListMetrics.size

    override fun tinySubpages(): List<PoolSubpageMetric> = emptyList()

    override fun smallSubpages(): List<PoolSubpageMetric> = subPageMetricList(smallSubpagePools)

    override fun chunkLists(): List<PoolChunkListMetric> = chunkListMetrics

    override fun numAllocations(): Long {
        val allocsNormal: Long
        lock()
        try {
            allocsNormal = allocationsNormal
        } finally {
            unlock()
        }
        return allocationsSmall.sum() + allocsNormal + allocationsHuge.sum()
    }

    override fun numTinyAllocations(): Long = 0

    override fun numSmallAllocations(): Long = allocationsSmall.sum()

    override fun numNormalAllocations(): Long {
        lock()
        try {
            return allocationsNormal
        } finally {
            unlock()
        }
    }

    override fun numChunkAllocations(): Long {
        lock()
        try {
            return pooledChunkAllocations
        } finally {
            unlock()
        }
    }

    override fun numDeallocations(): Long {
        val deallocs: Long
        lock()
        try {
            deallocs = deallocationsSmall + deallocationsNormal
        } finally {
            unlock()
        }
        return deallocs + deallocationsHuge.sum()
    }

    override fun numTinyDeallocations(): Long = 0

    override fun numSmallDeallocations(): Long {
        lock()
        try {
            return deallocationsSmall
        } finally {
            unlock()
        }
    }

    override fun numNormalDeallocations(): Long {
        lock()
        try {
            return deallocationsNormal
        } finally {
            unlock()
        }
    }

    override fun numChunkDeallocations(): Long {
        lock()
        try {
            return pooledChunkDeallocations
        } finally {
            unlock()
        }
    }

    override fun numHugeAllocations(): Long = allocationsHuge.sum()

    override fun numHugeDeallocations(): Long = deallocationsHuge.sum()

    override fun numActiveAllocations(): Long {
        var `val` = allocationsSmall.sum() + allocationsHuge.sum() - deallocationsHuge.sum()
        lock()
        try {
            `val` += allocationsNormal - (deallocationsSmall + deallocationsNormal)
        } finally {
            unlock()
        }
        return max(`val`, 0)
    }

    override fun numActiveTinyAllocations(): Long = 0

    override fun numActiveSmallAllocations(): Long =
        max(numSmallAllocations() - numSmallDeallocations(), 0)

    override fun numActiveNormalAllocations(): Long {
        val `val`: Long
        lock()
        try {
            `val` = allocationsNormal - deallocationsNormal
        } finally {
            unlock()
        }
        return max(`val`, 0)
    }

    override fun numActiveChunks(): Long {
        val `val`: Long
        lock()
        try {
            `val` = pooledChunkAllocations - pooledChunkDeallocations
        } finally {
            unlock()
        }
        return max(`val`, 0)
    }

    override fun numActiveHugeAllocations(): Long =
        max(numHugeAllocations() - numHugeDeallocations(), 0)

    override fun numActiveBytes(): Long {
        var `val` = activeBytesHuge.sum()
        lock()
        try {
            for (i in chunkListMetrics.indices) {
                for (m in chunkListMetrics[i]) {
                    `val` += m.chunkSize()
                }
            }
        } finally {
            unlock()
        }
        return max(0, `val`)
    }

    /**
     * Return an estimate of the number of bytes that are currently pinned to buffer instances, by the arena. The
     * pinned memory is not accessible for use by any other allocation, until the buffers using have all been released.
     */
    fun numPinnedBytes(): Long {
        var `val` = activeBytesHuge.sum() // Huge chunks are exact-sized for the buffers they were allocated to.
        for (i in chunkListMetrics.indices) {
            for (m in chunkListMetrics[i]) {
                `val` += (m as PoolChunk<*>).pinnedBytes()
            }
        }
        return max(0, `val`)
    }

    internal abstract fun newChunk(pageSize: Int, maxPageIdx: Int, pageShifts: Int, chunkSize: Int): PoolChunk<T>
    internal abstract fun newUnpooledChunk(capacity: Int): PoolChunk<T>
    internal abstract fun newByteBuf(maxCapacity: Int): PooledByteBuf<T>
    abstract fun memoryCopy(src: T, srcOffset: Int, dst: PooledByteBuf<T>, length: Int)
    internal open fun destroyChunk(chunk: PoolChunk<T>) {}

    override fun toString(): String {
        lock()
        try {
            val buf = StringBuilder()
                .append("Chunk(s) at 0~25%:")
                .append(StringUtil.NEWLINE)
                .append(qInit)
                .append(StringUtil.NEWLINE)
                .append("Chunk(s) at 0~50%:")
                .append(StringUtil.NEWLINE)
                .append(q000)
                .append(StringUtil.NEWLINE)
                .append("Chunk(s) at 25~75%:")
                .append(StringUtil.NEWLINE)
                .append(q025)
                .append(StringUtil.NEWLINE)
                .append("Chunk(s) at 50~100%:")
                .append(StringUtil.NEWLINE)
                .append(q050)
                .append(StringUtil.NEWLINE)
                .append("Chunk(s) at 75~100%:")
                .append(StringUtil.NEWLINE)
                .append(q075)
                .append(StringUtil.NEWLINE)
                .append("Chunk(s) at 100%:")
                .append(StringUtil.NEWLINE)
                .append(q100)
                .append(StringUtil.NEWLINE)
                .append("small subpages:")
            appendPoolSubPages(buf, smallSubpagePools)
            buf.append(StringUtil.NEWLINE)
            return buf.toString()
        } finally {
            unlock()
        }
    }

    @Suppress("deprecation")
    protected fun finalize() {
        try {
            destroyPoolSubPages(smallSubpagePools)
            destroyPoolChunkLists(qInit, q000, q025, q050, q075, q100)
        } finally {
            // No super.finalize() in Kotlin - Object.finalize() is handled by the runtime
        }
    }

    private fun destroyPoolChunkLists(vararg chunkLists: PoolChunkList<T>) {
        for (chunkList in chunkLists) {
            chunkList.destroy(this)
        }
    }

    internal fun lock() {
        lock.lock()
    }

    internal fun unlock() {
        lock.unlock()
    }

    override fun sizeIdx2size(sizeIdx: Int): Int = sizeClass.sizeIdx2size(sizeIdx)

    override fun sizeIdx2sizeCompute(sizeIdx: Int): Int = sizeClass.sizeIdx2sizeCompute(sizeIdx)

    override fun pageIdx2size(pageIdx: Int): Long = sizeClass.pageIdx2size(pageIdx)

    override fun pageIdx2sizeCompute(pageIdx: Int): Long = sizeClass.pageIdx2sizeCompute(pageIdx)

    override fun size2SizeIdx(size: Int): Int = sizeClass.size2SizeIdx(size)

    override fun pages2pageIdx(pages: Int): Int = sizeClass.pages2pageIdx(pages)

    override fun pages2pageIdxFloor(pages: Int): Int = sizeClass.pages2pageIdxFloor(pages)

    override fun normalizeSize(size: Int): Int = sizeClass.normalizeSize(size)

    internal class HeapArena(parent: PooledByteBufAllocator?, sizeClass: SizeClasses) : PoolArena<ByteArray>(parent, sizeClass) {
        private val lastDestroyedChunk: AtomicReference<PoolChunk<ByteArray>> = AtomicReference()

        override fun isDirect(): Boolean = false

        override fun newChunk(pageSize: Int, maxPageIdx: Int, pageShifts: Int, chunkSize: Int): PoolChunk<ByteArray> {
            val chunk = lastDestroyedChunk.getAndSet(null)
            if (chunk != null) {
                assert(
                    chunk.chunkSize == chunkSize &&
                    chunk.pageSize == pageSize &&
                    chunk.maxPageIdx == maxPageIdx &&
                    chunk.pageShifts == pageShifts
                )
                return chunk // The parameters are always the same, so it's fine to reuse a previously allocated chunk.
            }
            return PoolChunk(
                this, null, null, newByteArray(chunkSize), pageSize, pageShifts, chunkSize, maxPageIdx
            )
        }

        override fun newUnpooledChunk(capacity: Int): PoolChunk<ByteArray> {
            return PoolChunk(this, null, null, newByteArray(capacity), capacity)
        }

        override fun destroyChunk(chunk: PoolChunk<ByteArray>) {
            PooledByteBufAllocator.onDeallocateChunk(chunk, !chunk.unpooled)
            // Rely on GC. But keep one chunk for reuse.
            if (!chunk.unpooled && lastDestroyedChunk.get() == null) {
                lastDestroyedChunk.set(chunk) // The check-and-set does not need to be atomic.
            }
        }

        override fun newByteBuf(maxCapacity: Int): PooledByteBuf<ByteArray> {
            return if (HAS_UNSAFE) PooledUnsafeHeapByteBuf.newUnsafeInstance(maxCapacity)
            else PooledHeapByteBuf.newInstance(maxCapacity)
        }

        override fun memoryCopy(src: ByteArray, srcOffset: Int, dst: PooledByteBuf<ByteArray>, length: Int) {
            if (length == 0) {
                return
            }
            System.arraycopy(src, srcOffset, dst.memory!!, dst.offset, length)
        }

        companion object {
            @JvmStatic
            private fun newByteArray(size: Int): ByteArray {
                return PlatformDependent.allocateUninitializedArray(size)
            }
        }
    }

    internal class DirectArena(parent: PooledByteBufAllocator?, sizeClass: SizeClasses) : PoolArena<ByteBuffer>(parent, sizeClass) {

        override fun isDirect(): Boolean = true

        override fun newChunk(pageSize: Int, maxPageIdx: Int, pageShifts: Int, chunkSize: Int): PoolChunk<ByteBuffer> {
            if (sizeClass.directMemoryCacheAlignment == 0) {
                val cleanableDirectBuffer = allocateDirect(chunkSize)
                val memory = cleanableDirectBuffer.buffer()
                return PoolChunk(
                    this, cleanableDirectBuffer, memory, memory, pageSize, pageShifts,
                    chunkSize, maxPageIdx
                )
            }

            val cleanableDirectBuffer = allocateDirect(chunkSize + sizeClass.directMemoryCacheAlignment)
            val base = cleanableDirectBuffer.buffer()
            val memory = PlatformDependent.alignDirectBuffer(base, sizeClass.directMemoryCacheAlignment)
            return PoolChunk(
                this, cleanableDirectBuffer, base, memory, pageSize,
                pageShifts, chunkSize, maxPageIdx
            )
        }

        override fun newUnpooledChunk(capacity: Int): PoolChunk<ByteBuffer> {
            if (sizeClass.directMemoryCacheAlignment == 0) {
                val cleanableDirectBuffer = allocateDirect(capacity)
                val memory = cleanableDirectBuffer.buffer()
                return PoolChunk(this, cleanableDirectBuffer, memory, memory, capacity)
            }

            val cleanableDirectBuffer = allocateDirect(capacity + sizeClass.directMemoryCacheAlignment)
            val base = cleanableDirectBuffer.buffer()
            val memory = PlatformDependent.alignDirectBuffer(base, sizeClass.directMemoryCacheAlignment)
            return PoolChunk(this, cleanableDirectBuffer, base, memory, capacity)
        }

        override fun destroyChunk(chunk: PoolChunk<ByteBuffer>) {
            PooledByteBufAllocator.onDeallocateChunk(chunk, !chunk.unpooled)
            chunk.cleanable!!.clean()
        }

        override fun newByteBuf(maxCapacity: Int): PooledByteBuf<ByteBuffer> {
            return if (HAS_UNSAFE) {
                PooledUnsafeDirectByteBuf.newInstance(maxCapacity)
            } else {
                PooledDirectByteBuf.newInstance(maxCapacity)
            }
        }

        override fun memoryCopy(src: ByteBuffer, srcOffset: Int, dstBuf: PooledByteBuf<ByteBuffer>, length: Int) {
            if (length == 0) {
                return
            }

            if (HAS_UNSAFE) {
                PlatformDependent.copyMemory(
                    PlatformDependent.directBufferAddress(src) + srcOffset,
                    PlatformDependent.directBufferAddress(dstBuf.memory!!) + dstBuf.offset, length.toLong()
                )
            } else {
                // We must duplicate the NIO buffers because they may be accessed by other Netty buffers.
                val srcDup = src.duplicate()
                val dst = dstBuf.internalNioBuffer()
                srcDup.position(srcOffset).limit(srcOffset + length)
                dst.position(dstBuf.offset)
                dst.put(srcDup)
            }
        }

        companion object {
            @JvmStatic
            private fun allocateDirect(capacity: Int): CleanableDirectBuffer {
                return PlatformDependent.allocateDirect(capacity)
            }
        }
    }

    companion object {
        @JvmStatic
        private val HAS_UNSAFE: Boolean = PlatformDependent.hasUnsafe()

        @JvmStatic
        private fun subPageMetricList(pages: Array<out PoolSubpage<*>>): List<PoolSubpageMetric> {
            val metrics = ArrayList<PoolSubpageMetric>()
            for (head in pages) {
                if (head.next === head) {
                    continue
                }
                var s = head.next
                while (true) {
                    metrics.add(s!!)
                    s = s.next
                    if (s === head) {
                        break
                    }
                }
            }
            return metrics
        }

        @JvmStatic
        private fun appendPoolSubPages(buf: StringBuilder, subpages: Array<out PoolSubpage<*>>) {
            for (i in subpages.indices) {
                val head = subpages[i]
                if (head.next === head || head.next == null) {
                    continue
                }

                buf.append(StringUtil.NEWLINE)
                    .append(i)
                    .append(": ")
                var s = head.next
                while (s != null) {
                    buf.append(s)
                    s = s.next
                    if (s === head) {
                        break
                    }
                }
            }
        }

        @JvmStatic
        private fun destroyPoolSubPages(pages: Array<out PoolSubpage<*>>) {
            for (page in pages) {
                page.destroy()
            }
        }

        @JvmStatic
        private fun sizeClass(handle: Long): SizeClass {
            return if (PoolChunk.isSubpage(handle)) SizeClass.Small else SizeClass.Normal
        }
    }
}
