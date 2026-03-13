/*
 * Copyright 2022 The Netty Project
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

import io.netty.util.ByteProcessor
import io.netty.util.CharsetUtil
import io.netty.util.IllegalReferenceCountException
import io.netty.util.NettyRuntime
import io.netty.util.Recycler
import io.netty.util.Recycler.EnhancedHandle
import io.netty.util.concurrent.ConcurrentSkipListIntObjMultimap
import io.netty.util.concurrent.FastThreadLocal
import io.netty.util.concurrent.FastThreadLocalThread
import io.netty.util.concurrent.MpscIntQueue
import io.netty.util.internal.MathUtil
import io.netty.util.internal.ObjectUtil
import io.netty.util.internal.PlatformDependent
import io.netty.util.internal.RefCnt
import io.netty.util.internal.SystemPropertyUtil
import io.netty.util.internal.ThreadExecutorMap
import io.netty.util.internal.UnstableApi
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.ClosedChannelException
import java.nio.channels.FileChannel
import java.nio.channels.GatheringByteChannel
import java.nio.channels.ScatteringByteChannel
import java.nio.charset.Charset
import java.util.Arrays
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater
import java.util.concurrent.atomic.LongAdder
import java.util.concurrent.locks.StampedLock
import java.util.function.IntConsumer

/**
 * An auto-tuning pooling allocator, that follows an anti-generational hypothesis.
 *
 * The allocator is organized into a list of Magazines, and each magazine has a chunk-buffer that they allocate buffers
 * from.
 *
 * The magazines hold the mutexes that ensure the thread-safety of the allocator, and each thread picks a magazine
 * based on the id of the thread. This spreads the contention of multi-threaded access across the magazines.
 * If contention is detected above a certain threshold, the number of magazines are increased in response to the
 * contention.
 *
 * The magazines maintain histograms of the sizes of the allocations they do. The histograms are used to compute the
 * preferred chunk size. The preferred chunk size is one that is big enough to service 10 allocations of the
 * 99-percentile size. This way, the chunk size is adapted to the allocation patterns.
 *
 * Computing the preferred chunk size is a somewhat expensive operation. Therefore, the frequency with which this is
 * done, is also adapted to the allocation pattern. If a newly computed preferred chunk is the same as the previous
 * preferred chunk size, then the frequency is reduced. Otherwise, the frequency is increased.
 *
 * This allows the allocator to quickly respond to changes in the application workload,
 * without suffering undue overhead from maintaining its statistics.
 *
 * Since magazines are "relatively thread-local", the allocator has a central queue that allow excess chunks from any
 * magazine, to be shared with other magazines.
 * The [createSharedChunkQueue] method can be overridden to customize this queue.
 */
@UnstableApi
internal class AdaptivePoolingAllocator(
    private val chunkAllocator: ChunkAllocator,
    useCacheForNonEventLoopThreads: Boolean
) {
    private val chunkRegistry: ChunkRegistry = ChunkRegistry()
    private val sizeClassedMagazineGroups: Array<MagazineGroup>
    private val largeBufferMagazineGroup: MagazineGroup
    private val threadLocalGroup: FastThreadLocal<Array<MagazineGroup>?>?

    init {
        sizeClassedMagazineGroups = createMagazineGroupSizeClasses(this, false)
        largeBufferMagazineGroup = MagazineGroup(
            this, chunkAllocator, BuddyChunkManagementStrategy(), false
        )

        val disableThreadLocalGroups = IS_LOW_MEM && DISABLE_THREAD_LOCAL_MAGAZINES_ON_LOW_MEM
        threadLocalGroup = if (disableThreadLocalGroups) null else object : FastThreadLocal<Array<MagazineGroup>?>() {
            override fun initialValue(): Array<MagazineGroup>? {
                return if (useCacheForNonEventLoopThreads || ThreadExecutorMap.currentExecutor() != null) {
                    createMagazineGroupSizeClasses(this@AdaptivePoolingAllocator, true)
                } else {
                    null
                }
            }

            @Throws(Exception::class)
            override fun onRemoval(groups: Array<MagazineGroup>?) {
                groups?.forEach { it.free() }
            }
        }
    }

    fun allocate(size: Int, maxCapacity: Int): ByteBuf =
        allocate(size, maxCapacity, Thread.currentThread(), null)

    private fun allocate(size: Int, maxCapacity: Int, currentThread: Thread, buf: AdaptiveByteBuf?): AdaptiveByteBuf {
        var allocated: AdaptiveByteBuf? = null
        if (size <= MAX_POOLED_BUF_SIZE) {
            val index = sizeClassIndexOf(size)
            var magazineGroups: Array<MagazineGroup> = sizeClassedMagazineGroups
            if (FastThreadLocalThread.currentThreadWillCleanupFastThreadLocals() &&
                !IS_LOW_MEM
            ) {
                val tlGroups = threadLocalGroup?.get()
                if (tlGroups != null) {
                    magazineGroups = tlGroups
                }
            }
            if (index < magazineGroups.size) {
                allocated = magazineGroups[index].allocate(size, maxCapacity, currentThread, buf)
            } else if (!IS_LOW_MEM) {
                allocated = largeBufferMagazineGroup.allocate(size, maxCapacity, currentThread, buf)
            }
        }
        return allocated ?: allocateFallback(size, maxCapacity, currentThread, buf)
    }

    private fun allocateFallback(
        size: Int, maxCapacity: Int, currentThread: Thread, buf: AdaptiveByteBuf?
    ): AdaptiveByteBuf {
        var buffer = buf
        // If we don't already have a buffer, obtain one from the most conveniently available magazine.
        var magazine: Magazine? = null
        if (buffer != null) {
            val chunk = buffer.chunk
            if (chunk != null && chunk !== Magazine.MAGAZINE_FREED) {
                magazine = chunk.currentMagazine()
            }
            if (magazine == null) {
                magazine = getFallbackMagazine(currentThread)
            }
        } else {
            magazine = getFallbackMagazine(currentThread)
            buffer = magazine!!.newBuffer()
        }
        // Create a one-off chunk for this allocation.
        val innerChunk = chunkAllocator.allocate(size, maxCapacity)
        val chunk = Chunk(innerChunk, magazine!!, false)
        chunkRegistry.add(chunk)
        try {
            val success = chunk.readInitInto(buffer!!, size, size, maxCapacity)
            assert(success) { "Failed to initialize ByteBuf with dedicated chunk" }
        } finally {
            // As the chunk is a one-off we need to always call release explicitly as readInitInto(...)
            // will take care of retain once when successful. Once the AdaptiveByteBuf is released it will
            // completely release the Chunk and so the contained innerChunk.
            chunk.release()
        }
        return buffer!!
    }

    private fun getFallbackMagazine(currentThread: Thread): Magazine {
        val mags = largeBufferMagazineGroup.magazines!!
        return mags[(currentThread.id.toInt()) and (mags.size - 1)]
    }

    /**
     * Allocate into the given buffer. Used by [AdaptiveByteBuf.capacity].
     */
    fun reallocate(size: Int, maxCapacity: Int, into: AdaptiveByteBuf) {
        val result = allocate(size, maxCapacity, Thread.currentThread(), into)
        assert(result === into) { "Re-allocation created separate buffer instance" }
    }

    fun usedMemory(): Long = chunkRegistry.totalCapacity()

    // Ensure that we release all previous pooled resources when this object is finalized. This is needed as otherwise
    // we might end up with leaks. While these leaks are usually harmless in reality it would still at least be
    // very confusing for users.
    @Suppress("removal", "deprecation")
    @Throws(Throwable::class)
    protected fun finalize() {
        try {
            free()
        } finally {
            // Note: In Kotlin, there is no super.finalize() to call
        }
    }

    private fun free() {
        largeBufferMagazineGroup.free()
    }

    internal class MagazineGroup(
        val allocator: AdaptivePoolingAllocator,
        val chunkAllocator: ChunkAllocator,
        private val chunkManagementStrategy: ChunkManagementStrategy,
        isThreadLocal: Boolean
    ) {
        private val chunkCache: ChunkCache
        private val magazineExpandLock: StampedLock?
        private val threadLocalMagazine: Magazine?
        var ownerThread: Thread?
        @Volatile
        var magazines: Array<Magazine>? = null
            private set
        @Volatile
        private var freed: Boolean = false

        init {
            chunkCache = chunkManagementStrategy.createChunkCache(isThreadLocal)
            if (isThreadLocal) {
                ownerThread = Thread.currentThread()
                magazineExpandLock = null
                threadLocalMagazine = Magazine(this, false, chunkManagementStrategy.createController(this))
                magazines = null
            } else {
                ownerThread = null
                magazineExpandLock = StampedLock()
                threadLocalMagazine = null
                val mags = Array(INITIAL_MAGAZINES) {
                    Magazine(this, true, chunkManagementStrategy.createController(this))
                }
                magazines = mags
            }
        }

        fun allocate(size: Int, maxCapacity: Int, currentThread: Thread, buf: AdaptiveByteBuf?): AdaptiveByteBuf? {
            val reallocate = buf != null
            var buffer = buf

            // Path for thread-local allocation.
            val tlMag = threadLocalMagazine
            if (tlMag != null) {
                if (buffer == null) {
                    buffer = tlMag.newBuffer()
                }
                val allocated = tlMag.tryAllocate(size, maxCapacity, buffer, reallocate)
                assert(allocated) { "Allocation of threadLocalMagazine must always succeed" }
                return buffer
            }

            // Path for concurrent allocation.
            val threadId = currentThread.id
            var mags: Array<Magazine>
            var expansions = 0
            do {
                mags = magazines!!
                val mask = mags.size - 1
                val index = (threadId.toInt()) and mask
                var i = 0
                val m = mags.size shl 1
                while (i < m) {
                    val mag = mags[(index + i) and mask]
                    if (buffer == null) {
                        buffer = mag.newBuffer()
                    }
                    if (mag.tryAllocate(size, maxCapacity, buffer, reallocate)) {
                        // Was able to allocate.
                        return buffer
                    }
                    i++
                }
                expansions++
            } while (expansions <= EXPANSION_ATTEMPTS && tryExpandMagazines(mags.size))

            // The magazines failed us; contention too high and we don't want to spend more effort expanding the array.
            if (!reallocate && buffer != null) {
                buffer.release() // Release the previously claimed buffer before we return.
            }
            return null
        }

        private fun tryExpandMagazines(currentLength: Int): Boolean {
            if (currentLength >= MAX_STRIPES) {
                return true
            }
            val writeLock = magazineExpandLock!!.tryWriteLock()
            val mags: Array<Magazine>
            if (writeLock != 0L) {
                try {
                    mags = magazines!!
                    if (mags.size >= MAX_STRIPES || mags.size > currentLength || freed) {
                        return true
                    }
                    val expanded = Array(mags.size * 2) {
                        Magazine(this, true, chunkManagementStrategy.createController(this))
                    }
                    magazines = expanded
                } finally {
                    magazineExpandLock.unlockWrite(writeLock)
                }
                for (magazine in mags) {
                    magazine.free()
                }
            }
            return true
        }

        fun pollChunk(size: Int): Chunk? = chunkCache.pollChunk(size)

        fun offerChunk(chunk: Chunk): Boolean {
            if (freed) {
                return false
            }

            if (chunk.hasUnprocessedFreelistEntries()) {
                chunk.processFreelistEntries()
            }
            val isAdded = chunkCache.offerChunk(chunk)

            if (freed && isAdded) {
                // Help to free the reuse queue.
                freeChunkReuseQueue(ownerThread)
            }
            return isAdded
        }

        fun free() {
            freed = true
            val ownerThread = this.ownerThread
            if (threadLocalMagazine != null) {
                this.ownerThread = null
                threadLocalMagazine.free()
            } else {
                val stamp = magazineExpandLock!!.writeLock()
                try {
                    val mags = magazines!!
                    for (magazine in mags) {
                        magazine.free()
                    }
                } finally {
                    magazineExpandLock.unlockWrite(stamp)
                }
            }
            freeChunkReuseQueue(ownerThread)
        }

        private fun freeChunkReuseQueue(ownerThread: Thread?) {
            var chunk: Chunk?
            while (chunkCache.pollChunk(0).also { chunk = it } != null) {
                if (ownerThread != null && chunk is SizeClassedChunk) {
                    val threadLocalChunk = chunk as SizeClassedChunk
                    assert(ownerThread === threadLocalChunk.ownerThread)
                    // no release segment can ever happen from the owner Thread since it's not running anymore
                    // This is required to let the ownerThread to be GC'ed despite there are AdaptiveByteBuf
                    // that reference some thread local chunk
                    threadLocalChunk.ownerThread = null
                }
                chunk!!.markToDeallocate()
            }
        }
    }

    internal interface ChunkCache {
        fun pollChunk(size: Int): Chunk?
        fun offerChunk(chunk: Chunk): Boolean
    }

    private class ConcurrentQueueChunkCache : ChunkCache {
        private val queue: Queue<SizeClassedChunk> = createSharedChunkQueue()

        override fun pollChunk(size: Int): SizeClassedChunk? {
            // we really don't care about size here since the sized class chunk q
            // just care about segments of fixed size!
            val queue = this.queue
            for (i in 0 until CHUNK_REUSE_QUEUE) {
                val chunk = queue.poll() ?: return null
                if (chunk.hasRemainingCapacity()) {
                    return chunk
                }
                queue.offer(chunk)
            }
            return null
        }

        override fun offerChunk(chunk: Chunk): Boolean =
            queue.offer(chunk as SizeClassedChunk)
    }

    private class ConcurrentSkipListChunkCache : ChunkCache {
        private val chunks = ConcurrentSkipListIntObjMultimap<Chunk>(-1)

        override fun pollChunk(size: Int): Chunk? {
            if (chunks.isEmpty) {
                return null
            }
            val entry = chunks.pollCeilingEntry(size)
            if (entry != null) {
                val chunk = entry.value
                if (chunk.hasUnprocessedFreelistEntries()) {
                    chunk.processFreelistEntries()
                }
                return chunk
            }

            var bestChunk: Chunk? = null
            var bestRemainingCapacity = 0
            val itr = chunks.iterator()
            while (itr.hasNext()) {
                val nextEntry = itr.next()
                val chunk: Chunk?
                if (nextEntry != null) {
                    chunk = nextEntry.value
                    if (chunk.hasUnprocessedFreelistEntries()) {
                        if (!chunks.remove(nextEntry.key, nextEntry.value)) {
                            continue
                        }
                        chunk.processFreelistEntries()
                        val remainingCapacity = chunk.remainingCapacity()
                        if (remainingCapacity >= size &&
                            (bestChunk == null || remainingCapacity > bestRemainingCapacity)
                        ) {
                            if (bestChunk != null) {
                                chunks.put(bestRemainingCapacity, bestChunk)
                            }
                            bestChunk = chunk
                            bestRemainingCapacity = remainingCapacity
                        } else {
                            chunks.put(remainingCapacity, chunk)
                        }
                    }
                }
            }

            return bestChunk
        }

        override fun offerChunk(chunk: Chunk): Boolean {
            chunks.put(chunk.remainingCapacity(), chunk)

            var size = chunks.size()
            while (size > CHUNK_REUSE_QUEUE) {
                // Deallocate the chunk with the fewest incoming references.
                var key = -1
                var toDeallocate: Chunk? = null
                for (entry in chunks) {
                    val candidate = entry.value
                    if (candidate != null) {
                        if (toDeallocate == null) {
                            toDeallocate = candidate
                            key = entry.key
                        } else {
                            val candidateRefCnt = RefCnt.refCnt(candidate.refCnt)
                            val toDeallocateRefCnt = RefCnt.refCnt(toDeallocate.refCnt)
                            if (candidateRefCnt < toDeallocateRefCnt ||
                                (candidateRefCnt == toDeallocateRefCnt &&
                                    candidate.capacity() < toDeallocate.capacity())
                            ) {
                                toDeallocate = candidate
                                key = entry.key
                            }
                        }
                    }
                }
                if (toDeallocate == null) {
                    break
                }
                if (chunks.remove(key, toDeallocate)) {
                    toDeallocate.markToDeallocate()
                }
                size = chunks.size()
            }
            return true
        }
    }

    internal interface ChunkManagementStrategy {
        fun createController(group: MagazineGroup): ChunkController
        fun createChunkCache(isThreadLocal: Boolean): ChunkCache
    }

    internal interface ChunkController {
        /**
         * Compute the "fast max capacity" value for the buffer.
         */
        fun computeBufferCapacity(requestedSize: Int, maxCapacity: Int, isReallocation: Boolean): Int

        /**
         * Allocate a new [Chunk] for the given [Magazine].
         */
        fun newChunkAllocation(promptingSize: Int, magazine: Magazine): Chunk
    }

    private class SizeClassChunkManagementStrategy(segmentSize: Int) : ChunkManagementStrategy {
        private val segmentSize: Int = ObjectUtil.checkPositive(segmentSize, "segmentSize")
        private val chunkSize: Int = Math.max(MIN_CHUNK_SIZE, segmentSize * MIN_SEGMENTS_PER_CHUNK)

        override fun createController(group: MagazineGroup): ChunkController =
            SizeClassChunkController(group, segmentSize, chunkSize)

        override fun createChunkCache(isThreadLocal: Boolean): ChunkCache =
            ConcurrentQueueChunkCache()

        companion object {
            // To amortize activation/deactivation of chunks, we should have a minimum number of segments per chunk.
            // We choose 32 because it seems neither too small nor too big.
            // For segments of 16 KiB, the chunks will be half a megabyte.
            private const val MIN_SEGMENTS_PER_CHUNK = 32
        }
    }

    private class SizeClassChunkController(
        group: MagazineGroup,
        val segmentSize: Int,
        val chunkSize: Int
    ) : ChunkController {
        private val chunkAllocator: ChunkAllocator = group.chunkAllocator
        private val chunkRegistry: ChunkRegistry = group.allocator.chunkRegistry

        fun createEmptyFreeList(): MpscIntQueue =
            MpscIntQueue.create(chunkSize / segmentSize, SizeClassedChunk.FREE_LIST_EMPTY)

        fun createFreeList(): MpscIntQueue {
            val segmentsCount = chunkSize / segmentSize
            val freeList = MpscIntQueue.create(segmentsCount, SizeClassedChunk.FREE_LIST_EMPTY)
            var segmentOffset = 0
            for (i in 0 until segmentsCount) {
                freeList.offer(segmentOffset)
                segmentOffset += segmentSize
            }
            return freeList
        }

        fun createLocalFreeList(): IntStack {
            val segmentsCount = chunkSize / segmentSize
            var segmentOffset = chunkSize
            val offsets = IntArray(segmentsCount)
            for (i in 0 until segmentsCount) {
                segmentOffset -= segmentSize
                offsets[i] = segmentOffset
            }
            return IntStack(offsets)
        }

        override fun computeBufferCapacity(requestedSize: Int, maxCapacity: Int, isReallocation: Boolean): Int =
            Math.min(segmentSize, maxCapacity)

        override fun newChunkAllocation(promptingSize: Int, magazine: Magazine): Chunk {
            val chunkBuffer = chunkAllocator.allocate(chunkSize, chunkSize)
            assert(chunkBuffer.capacity() == chunkSize)
            val chunk = SizeClassedChunk(chunkBuffer, magazine, this)
            chunkRegistry.add(chunk)
            return chunk
        }
    }

    private class BuddyChunkManagementStrategy : ChunkManagementStrategy {
        private val maxChunkSize = AtomicInteger()

        override fun createController(group: MagazineGroup): ChunkController =
            BuddyChunkController(group, maxChunkSize)

        override fun createChunkCache(isThreadLocal: Boolean): ChunkCache =
            ConcurrentSkipListChunkCache()
    }

    private class BuddyChunkController(
        group: MagazineGroup,
        private val maxChunkSize: AtomicInteger
    ) : ChunkController {
        private val chunkAllocator: ChunkAllocator = group.chunkAllocator
        private val chunkRegistry: ChunkRegistry = group.allocator.chunkRegistry

        override fun computeBufferCapacity(requestedSize: Int, maxCapacity: Int, isReallocation: Boolean): Int =
            MathUtil.safeFindNextPositivePowerOfTwo(requestedSize)

        override fun newChunkAllocation(promptingSize: Int, magazine: Magazine): Chunk {
            val currentMaxChunkSize = maxChunkSize.get()
            val proposedChunkSize = MathUtil.safeFindNextPositivePowerOfTwo(BUFS_PER_CHUNK * promptingSize)
            val chunkSize = Math.min(MAX_CHUNK_SIZE, Math.max(currentMaxChunkSize, proposedChunkSize))
            if (chunkSize > currentMaxChunkSize) {
                // Update our stored max chunk size. It's fine that this is racy.
                maxChunkSize.set(chunkSize)
            }
            val chunk = BuddyChunk(chunkAllocator.allocate(chunkSize, chunkSize), magazine)
            chunkRegistry.add(chunk)
            return chunk
        }
    }

    internal class Magazine(
        val group: MagazineGroup,
        shareable: Boolean,
        private val chunkController: ChunkController
    ) {
        private var current: Chunk? = null
        @Suppress("unused") // updated via NEXT_IN_LINE
        @Volatile
        private var nextInLine: Chunk? = null
        private val allocationLock: StampedLock?
        private val recycler: AdaptiveRecycler?

        val allocationLockForTest: StampedLock?
            get() = allocationLock

        init {
            if (shareable) {
                // We only need the StampedLock if this Magazine will be shared across threads.
                allocationLock = StampedLock()
                recycler = AdaptiveRecycler.sharedWith(MAGAZINE_BUFFER_QUEUE_CAPACITY)
            } else {
                allocationLock = null
                recycler = null
            }
        }

        fun tryAllocate(size: Int, maxCapacity: Int, buf: AdaptiveByteBuf, reallocate: Boolean): Boolean {
            if (allocationLock == null) {
                // This magazine is not shared across threads, just allocate directly.
                return allocate(size, maxCapacity, buf, reallocate)
            }

            // Try to retrieve the lock and if successful allocate.
            val writeLock = allocationLock.tryWriteLock()
            if (writeLock != 0L) {
                try {
                    return allocate(size, maxCapacity, buf, reallocate)
                } finally {
                    allocationLock.unlockWrite(writeLock)
                }
            }
            return allocateWithoutLock(size, maxCapacity, buf)
        }

        private fun allocateWithoutLock(size: Int, maxCapacity: Int, buf: AdaptiveByteBuf): Boolean {
            var curr = NEXT_IN_LINE.getAndSet(this, null)
            if (curr === MAGAZINE_FREED) {
                // Allocation raced with a stripe-resize that freed this magazine.
                restoreMagazineFreed()
                return false
            }
            if (curr == null) {
                curr = group.pollChunk(size) ?: return false
                curr.attachToMagazine(this)
            }
            var allocated = false
            var remainingCapacity = curr.remainingCapacity()
            val startingCapacity = chunkController.computeBufferCapacity(
                size, maxCapacity, true /* never update stats as we don't hold the magazine lock */
            )
            if (remainingCapacity >= size &&
                curr.readInitInto(buf, size, Math.min(remainingCapacity, startingCapacity), maxCapacity)
            ) {
                allocated = true
                remainingCapacity = curr.remainingCapacity()
            }
            try {
                if (remainingCapacity >= RETIRE_CAPACITY) {
                    transferToNextInLineOrRelease(curr)
                    curr = null
                }
            } finally {
                curr?.releaseFromMagazine()
            }
            return allocated
        }

        private fun allocate(size: Int, maxCapacity: Int, buf: AdaptiveByteBuf, reallocate: Boolean): Boolean {
            val startingCapacity = chunkController.computeBufferCapacity(size, maxCapacity, reallocate)
            var curr = current
            if (curr != null) {
                val success = curr.readInitInto(buf, size, startingCapacity, maxCapacity)
                val remainingCapacity = curr.remainingCapacity()
                if (!success && remainingCapacity > 0) {
                    current = null
                    transferToNextInLineOrRelease(curr)
                } else if (remainingCapacity == 0) {
                    current = null
                    curr.releaseFromMagazine()
                }
                if (success) {
                    return true
                }
            }

            assert(current == null)
            // The fast-path for allocations did not work.
            //
            // Try to fetch the next "Magazine local" Chunk first, if this fails because we don't have a
            // next-in-line chunk available, we will poll our centralQueue.
            // If this fails as well we will just allocate a new Chunk.
            //
            // In any case we will store the Chunk as the current so it will be used again for the next allocation and
            // thus be "reserved" by this Magazine for exclusive usage.
            curr = NEXT_IN_LINE.getAndSet(this, null)
            if (curr != null) {
                if (curr === MAGAZINE_FREED) {
                    // Allocation raced with a stripe-resize that freed this magazine.
                    restoreMagazineFreed()
                    return false
                }

                var remainingCapacity = curr.remainingCapacity()
                if (remainingCapacity > startingCapacity &&
                    curr.readInitInto(buf, size, startingCapacity, maxCapacity)
                ) {
                    // We have a Chunk that has some space left.
                    current = curr
                    return true
                }

                try {
                    if (remainingCapacity >= size) {
                        // At this point we know that this will be the last time curr will be used, so directly set it
                        // to null and release it once we are done.
                        return curr.readInitInto(buf, size, remainingCapacity, maxCapacity)
                    }
                } finally {
                    // Release in a finally block so even if readInitInto(...) would throw we would still correctly
                    // release the current chunk before null it out.
                    curr.releaseFromMagazine()
                }
            }

            // Now try to poll from the central queue first
            curr = group.pollChunk(size)
            if (curr == null) {
                curr = chunkController.newChunkAllocation(size, this)
            } else {
                curr.attachToMagazine(this)

                var remainingCapacity = curr.remainingCapacity()
                if (remainingCapacity == 0 || remainingCapacity < size) {
                    // Check if we either retain the chunk in the nextInLine cache or releasing it.
                    if (remainingCapacity < RETIRE_CAPACITY) {
                        curr.releaseFromMagazine()
                    } else {
                        // See if it makes sense to transfer the Chunk to the nextInLine cache for later usage.
                        // This method will release curr if this is not the case
                        transferToNextInLineOrRelease(curr)
                    }
                    curr = chunkController.newChunkAllocation(size, this)
                }
            }

            current = curr
            var success: Boolean
            try {
                val remainingCapacity = curr!!.remainingCapacity()
                assert(remainingCapacity >= size)
                if (remainingCapacity > startingCapacity) {
                    success = curr.readInitInto(buf, size, startingCapacity, maxCapacity)
                    curr = null
                } else {
                    success = curr.readInitInto(buf, size, remainingCapacity, maxCapacity)
                }
            } finally {
                if (curr != null) {
                    // Release in a finally block so even if readInitInto(...) would throw we would still correctly
                    // release the current chunk before null it out.
                    curr.releaseFromMagazine()
                    current = null
                }
            }
            return success
        }

        private fun restoreMagazineFreed() {
            val next = NEXT_IN_LINE.getAndSet(this, MAGAZINE_FREED)
            if (next != null && next !== MAGAZINE_FREED) {
                // A chunk snuck in through a race. Release it after restoring MAGAZINE_FREED state.
                next.releaseFromMagazine()
            }
        }

        private fun transferToNextInLineOrRelease(chunk: Chunk) {
            if (NEXT_IN_LINE.compareAndSet(this, null, chunk)) {
                return
            }

            val nextChunk = NEXT_IN_LINE.get(this)
            if (nextChunk != null && nextChunk !== MAGAZINE_FREED &&
                chunk.remainingCapacity() > nextChunk.remainingCapacity()
            ) {
                if (NEXT_IN_LINE.compareAndSet(this, nextChunk, chunk)) {
                    nextChunk.releaseFromMagazine()
                    return
                }
            }
            // Next-in-line is occupied. We don't try to add it to the central queue yet as it might still be used
            // by some buffers and so is attached to a Magazine.
            // Once a Chunk is completely released by Chunk.release() it will try to move itself to the queue
            // as last resort.
            chunk.releaseFromMagazine()
        }

        fun free() {
            // Release the current Chunk and the next that was stored for later usage.
            restoreMagazineFreed()
            val stamp = allocationLock?.writeLock() ?: 0L
            try {
                current?.releaseFromMagazine()
                current = null
            } finally {
                if (allocationLock != null) {
                    allocationLock.unlockWrite(stamp)
                }
            }
        }

        fun newBuffer(): AdaptiveByteBuf {
            val recycler = this.recycler
            val buf = recycler?.get() ?: EVENT_LOOP_LOCAL_BUFFER_POOL.get()
            buf.resetForRecycle()
            return buf
        }

        fun offerToQueue(chunk: Chunk): Boolean = group.offerChunk(chunk)

        companion object {
            private val NEXT_IN_LINE: AtomicReferenceFieldUpdater<Magazine, Chunk> =
                AtomicReferenceFieldUpdater.newUpdater(Magazine::class.java, Chunk::class.java, "nextInLine")

            @JvmField
            val MAGAZINE_FREED: Chunk = Chunk()

            private val EVENT_LOOP_LOCAL_BUFFER_POOL = AdaptiveRecycler.threadLocal()

            private class AdaptiveRecycler : Recycler<AdaptiveByteBuf> {
                private constructor(unguarded: Boolean) : super(unguarded)
                private constructor(maxCapacity: Int, unguarded: Boolean) : super(maxCapacity, unguarded)

                override fun newObject(handle: Handle<AdaptiveByteBuf>): AdaptiveByteBuf =
                    AdaptiveByteBuf(handle as EnhancedHandle<AdaptiveByteBuf>)

                companion object {
                    fun threadLocal(): AdaptiveRecycler = AdaptiveRecycler(true)
                    fun sharedWith(maxCapacity: Int): AdaptiveRecycler = AdaptiveRecycler(maxCapacity, true)
                }
            }
        }
    }

    private class ChunkRegistry {
        private val totalCapacity = LongAdder()

        fun totalCapacity(): Long = totalCapacity.sum()

        fun add(chunk: Chunk) {
            totalCapacity.add(chunk.capacity().toLong())
        }

        fun remove(chunk: Chunk) {
            totalCapacity.add(-chunk.capacity().toLong())
        }
    }

    internal open class Chunk : ChunkInfo {
        val delegate: AbstractByteBuf?
        var magazine: Magazine?
        val allocator: AdaptivePoolingAllocator?
        // Always populate the refCnt field, so HotSpot doesn't emit `null` checks.
        // This is safe to do even on native-image.
        @JvmField
        val refCnt: RefCnt = RefCnt()
        private val _capacity: Int
        @JvmField
        val pooled: Boolean
        @JvmField
        protected var allocatedBytes: Int = 0

        constructor() {
            // Constructor only used by the MAGAZINE_FREED sentinel.
            delegate = null
            magazine = null
            allocator = null
            _capacity = 0
            pooled = false
        }

        constructor(delegate: AbstractByteBuf, magazine: Magazine, pooled: Boolean) {
            this.delegate = delegate
            this.pooled = pooled
            _capacity = delegate.capacity()
            this.magazine = null // will be set by attachToMagazine
            attachToMagazine(magazine)

            // We need the top-level allocator so ByteBuf.capacity(int) can call reallocate()
            allocator = magazine.group.allocator

            if (PlatformDependent.isJfrEnabled() && AllocateChunkEvent.isEventEnabled()) {
                val event = AllocateChunkEvent()
                if (event.shouldCommit()) {
                    event.fill(this, AdaptiveByteBufAllocator::class.java)
                    event.pooled = pooled
                    event.threadLocal = magazine.allocationLockForTest == null
                    event.commit()
                }
            }
        }

        fun currentMagazine(): Magazine? = magazine

        open fun detachFromMagazine() {
            if (magazine != null) {
                magazine = null
            }
        }

        fun attachToMagazine(magazine: Magazine) {
            assert(this.magazine == null)
            this.magazine = magazine
        }

        /**
         * Called when a magazine is done using this chunk, probably because it was emptied.
         */
        open fun releaseFromMagazine() {
            // Chunks can be reused before they become empty.
            // We can therefore put them in the shared queue as soon as the magazine is done with this chunk.
            val mag = magazine!!
            detachFromMagazine()
            if (!mag.offerToQueue(this)) {
                markToDeallocate()
            }
        }

        /**
         * Called when a ByteBuf is done using its allocation in this chunk.
         */
        open fun releaseSegment(ignoredSegmentId: Int, size: Int) {
            release()
        }

        open fun markToDeallocate() {
            release()
        }

        fun retain() {
            RefCnt.retain(refCnt)
        }

        open fun release(): Boolean {
            val deallocate = RefCnt.release(refCnt)
            if (deallocate) {
                deallocate()
            }
            return deallocate
        }

        protected open fun deallocate() {
            onRelease()
            allocator!!.chunkRegistry.remove(this)
            delegate!!.release()
        }

        private fun onRelease() {
            if (PlatformDependent.isJfrEnabled() && FreeChunkEvent.isEventEnabled()) {
                val event = FreeChunkEvent()
                if (event.shouldCommit()) {
                    event.fill(this, AdaptiveByteBufAllocator::class.java)
                    event.pooled = pooled
                    event.commit()
                }
            }
        }

        open fun readInitInto(buf: AdaptiveByteBuf, size: Int, startingCapacity: Int, maxCapacity: Int): Boolean {
            val startIndex = allocatedBytes
            allocatedBytes = startIndex + startingCapacity
            var chunk: Chunk? = this
            chunk!!.retain()
            try {
                buf.init(delegate, chunk, 0, 0, startIndex, size, startingCapacity, maxCapacity)
                chunk = null
            } finally {
                if (chunk != null) {
                    // If chunk is not null we know that buf.init(...) failed and so we need to manually release
                    // the chunk again as we retained it before calling buf.init(...). Beside this we also need to
                    // restore the old allocatedBytes value.
                    allocatedBytes = startIndex
                    chunk.release()
                }
            }
            return true
        }

        open fun remainingCapacity(): Int = _capacity - allocatedBytes

        open fun hasUnprocessedFreelistEntries(): Boolean = false

        open fun processFreelistEntries() {}

        override fun capacity(): Int = _capacity

        override fun isDirect(): Boolean = delegate!!.isDirect()

        override fun memoryAddress(): Long = delegate!!._memoryAddress()
    }

    private class IntStack(private val stack: IntArray) {
        private var top: Int = stack.size - 1

        fun isEmpty(): Boolean = top == -1

        fun pop(): Int {
            val last = stack[top]
            top--
            return last
        }

        fun push(value: Int) {
            stack[top + 1] = value
            top++
        }

        fun size(): Int = top + 1
    }

    /**
     * Removes per-allocation retain()/release() atomic ops from the hot path by replacing ref counting
     * with a segment-count state machine. Atomics are only needed on the cold deallocation path
     * ([markToDeallocate]), which is rare for long-lived chunks that cycle segments many times.
     * The tradeoff is a [MpscIntQueue.size] call (volatile reads, no RMW) per remaining segment
     * return after mark -- acceptable since it avoids atomic RMWs entirely.
     *
     * State transitions:
     * - [AVAILABLE] (-1): chunk is in use, no deallocation tracking needed
     * - 0..N: local free list size at the time [markToDeallocate] was called;
     *     used to track when all segments have been returned
     * - [DEALLOCATED] (Integer.MIN_VALUE): all segments returned, chunk deallocated
     *
     * Ordering: external [releaseSegment] pushes to the MPSC queue (which has an implicit
     * StoreLoad barrier via its `offer()`), then reads `state` -- this guarantees
     * visibility of any preceding [markToDeallocate] write.
     */
    private class SizeClassedChunk(
        delegate: AbstractByteBuf,
        magazine: Magazine,
        controller: SizeClassChunkController
    ) : Chunk(delegate, magazine, true) {
        @Volatile
        private var state: Int = 0
        private val segments: Int
        private val segmentSize: Int
        private val externalFreeList: MpscIntQueue
        private val localFreeList: IntStack?
        @JvmField
        var ownerThread: Thread?

        init {
            segmentSize = controller.segmentSize
            segments = controller.chunkSize / segmentSize
            STATE.lazySet(this, AVAILABLE)
            ownerThread = magazine.group.ownerThread
            if (ownerThread == null) {
                externalFreeList = controller.createFreeList()
                localFreeList = null
            } else {
                externalFreeList = controller.createEmptyFreeList()
                localFreeList = controller.createLocalFreeList()
            }
        }

        override fun readInitInto(buf: AdaptiveByteBuf, size: Int, startingCapacity: Int, maxCapacity: Int): Boolean {
            assert(state == AVAILABLE)
            val startIndex = nextAvailableSegmentOffset()
            if (startIndex == FREE_LIST_EMPTY) {
                return false
            }
            allocatedBytes += segmentSize
            try {
                buf.init(delegate, this, 0, 0, startIndex, size, startingCapacity, maxCapacity)
            } catch (t: Throwable) {
                allocatedBytes -= segmentSize
                releaseSegmentOffsetIntoFreeList(startIndex)
                throw t
            }
            return true
        }

        private fun nextAvailableSegmentOffset(): Int {
            val localFreeList = this.localFreeList
            return if (localFreeList != null) {
                assert(Thread.currentThread() == ownerThread)
                if (localFreeList.isEmpty()) {
                    externalFreeList.poll()
                } else {
                    localFreeList.pop()
                }
            } else {
                externalFreeList.poll()
            }
        }

        // this can be used by the ConcurrentQueueChunkCache to find the first buffer to use:
        // it doesn't update the remaining capacity and it's not consider a single segmentSize
        // case as not suitable to be reused
        fun hasRemainingCapacity(): Boolean {
            val remaining = super.remainingCapacity()
            if (remaining > 0) {
                return true
            }
            return if (localFreeList != null) {
                !localFreeList.isEmpty()
            } else {
                !externalFreeList.isEmpty()
            }
        }

        override fun remainingCapacity(): Int {
            val remaining = super.remainingCapacity()
            return if (remaining > segmentSize) remaining else updateRemainingCapacity(remaining)
        }

        private fun updateRemainingCapacity(snapshotted: Int): Int {
            var freeSegments = externalFreeList.size()
            val localFreeList = this.localFreeList
            if (localFreeList != null) {
                freeSegments += localFreeList.size()
            }
            val updated = freeSegments * segmentSize
            if (updated != snapshotted) {
                allocatedBytes = capacity() - updated
            }
            return updated
        }

        private fun releaseSegmentOffsetIntoFreeList(startIndex: Int) {
            val localFreeList = this.localFreeList
            if (localFreeList != null && Thread.currentThread() === ownerThread) {
                localFreeList.push(startIndex)
            } else {
                val segmentReturned = externalFreeList.offer(startIndex)
                assert(segmentReturned) { "Unable to return segment $startIndex to free list" }
            }
        }

        override fun releaseSegment(startIndex: Int, size: Int) {
            val localFreeList = this.localFreeList
            if (localFreeList != null && Thread.currentThread() === ownerThread) {
                localFreeList.push(startIndex)
                val state = this.state
                if (state != AVAILABLE) {
                    updateStateOnLocalReleaseSegment(state, localFreeList)
                }
            } else {
                val segmentReturned = externalFreeList.offer(startIndex)
                assert(segmentReturned)
                // implicit StoreLoad barrier from MPSC offer()
                val state = this.state
                if (state != AVAILABLE) {
                    deallocateIfNeeded(state)
                }
            }
        }

        private fun updateStateOnLocalReleaseSegment(previousLocalSize: Int, localFreeList: IntStack) {
            val newLocalSize = localFreeList.size()
            val alwaysTrue = STATE.compareAndSet(this, previousLocalSize, newLocalSize)
            assert(alwaysTrue) { "this shouldn't happen unless double release in the local free list" }
            deallocateIfNeeded(newLocalSize)
        }

        private fun deallocateIfNeeded(localSize: Int) {
            // Check if all segments have been returned.
            val totalFreeSegments = localSize + externalFreeList.size()
            if (totalFreeSegments == segments && STATE.compareAndSet(this, localSize, DEALLOCATED)) {
                deallocate()
            }
        }

        override fun markToDeallocate() {
            val localFreeList = this.localFreeList
            val localSize = localFreeList?.size() ?: 0
            STATE.set(this, localSize)
            deallocateIfNeeded(localSize)
        }

        companion object {
            const val FREE_LIST_EMPTY = -1
            private const val AVAILABLE = -1
            // Integer.MIN_VALUE so that `DEALLOCATED + externalFreeList.size()` can never equal `segments`,
            // making late-arriving releaseSegment calls on external threads arithmetically harmless.
            private const val DEALLOCATED = Int.MIN_VALUE
            private val STATE: AtomicIntegerFieldUpdater<SizeClassedChunk> =
                AtomicIntegerFieldUpdater.newUpdater(SizeClassedChunk::class.java, "state")
        }
    }

    private class BuddyChunk(
        delegate: AbstractByteBuf,
        magazine: Magazine
    ) : Chunk(delegate, magazine, true), IntConsumer {
        private val freeList: MpscIntQueue
        // The bits of each buddy: [1: is claimed][1: has claimed children][30: MIN_BUDDY_SIZE shift to get size]
        private val buddies: ByteArray
        private val freeListCapacity: Int

        init {
            freeListCapacity = delegate.capacity() / MIN_BUDDY_SIZE
            val maxShift = Integer.numberOfTrailingZeros(freeListCapacity)
            assert(maxShift <= 30) // The top 2 bits are used for marking.
            freeList = MpscIntQueue.create(freeListCapacity, -1) // At most half of tree (all leaf nodes) can be freed.
            buddies = ByteArray(freeListCapacity shl 1)

            // Generate the buddies entries.
            var index = 1
            var runLength = 1
            var currentRun = 0
            var shift = maxShift
            while (shift > 0) {
                buddies[index++] = shift.toByte()
                if (++currentRun == runLength) {
                    currentRun = 0
                    runLength = runLength shl 1
                    shift--
                }
            }
        }

        override fun readInitInto(buf: AdaptiveByteBuf, size: Int, startingCapacity: Int, maxCapacity: Int): Boolean {
            if (!freeList.isEmpty()) {
                freeList.drain(freeListCapacity, this)
            }
            val startIndex = chooseFirstFreeBuddy(1, startingCapacity, 0)
            if (startIndex == -1) {
                return false
            }
            var chunk: Chunk? = this
            chunk!!.retain()
            try {
                buf.init(delegate, this, 0, 0, startIndex, size, startingCapacity, maxCapacity)
                allocatedBytes += startingCapacity
                chunk = null
            } finally {
                if (chunk != null) {
                    unreserveMatchingBuddy(1, startingCapacity, startIndex, 0)
                    // If chunk is not null we know that buf.init(...) failed and so we need to manually release
                    // the chunk again as we retained it before calling buf.init(...).
                    chunk.release()
                }
            }
            return true
        }

        override fun accept(packed: Int) {
            // Called by allocating thread when draining freeList.
            val size = MIN_BUDDY_SIZE shl (packed ushr PACK_SIZE_SHIFT)
            val offset = (packed and PACK_OFFSET_MASK) * MIN_BUDDY_SIZE
            unreserveMatchingBuddy(1, size, offset, 0)
            allocatedBytes -= size
        }

        override fun releaseSegment(startingIndex: Int, size: Int) {
            val packedOffset = startingIndex / MIN_BUDDY_SIZE
            val packedSize = Integer.numberOfTrailingZeros(size / MIN_BUDDY_SIZE) shl PACK_SIZE_SHIFT
            val packed = packedOffset or packedSize
            freeList.offer(packed)
            release()
        }

        override fun remainingCapacity(): Int {
            if (!freeList.isEmpty()) {
                freeList.drain(freeListCapacity, this)
            }
            return super.remainingCapacity()
        }

        override fun hasUnprocessedFreelistEntries(): Boolean = !freeList.isEmpty()

        override fun processFreelistEntries() {
            freeList.drain(freeListCapacity, this)
        }

        /**
         * Claim a suitable buddy and return its start offset into the delegate chunk, or return -1 if nothing claimed.
         */
        private fun chooseFirstFreeBuddy(index: Int, size: Int, currOffset: Int): Int {
            val buddies = this.buddies
            var idx = index
            var offset = currOffset
            while (idx < buddies.size) {
                val buddy = buddies[idx]
                val currValue = MIN_BUDDY_SIZE shl (buddy.toInt() and SHIFT_MASK.toInt())
                if (currValue < size || (buddy.toInt() and IS_CLAIMED.toInt()) == IS_CLAIMED.toInt()) {
                    return -1
                }
                if (currValue == size && (buddy.toInt() and HAS_CLAIMED_CHILDREN.toInt()) == 0) {
                    buddies[idx] = (buddy.toInt() or IS_CLAIMED.toInt()).toByte()
                    return offset
                }
                val found = chooseFirstFreeBuddy(idx shl 1, size, offset)
                if (found != -1) {
                    buddies[idx] = (buddy.toInt() or HAS_CLAIMED_CHILDREN.toInt()).toByte()
                    return found
                }
                idx = (idx shl 1) + 1
                offset += currValue ushr 1 // Bump offset to skip first half of this layer.
            }
            return -1
        }

        /**
         * Un-reserve the matching buddy and return whether there are any other child or sibling reservations.
         */
        private fun unreserveMatchingBuddy(index: Int, size: Int, offset: Int, currOffset: Int): Boolean {
            val buddies = this.buddies
            if (buddies.size <= index) {
                return false
            }
            val buddy = buddies[index]
            val currSize = MIN_BUDDY_SIZE shl (buddy.toInt() and SHIFT_MASK.toInt())

            if (currSize == size) {
                // We're at the right size level.
                if (currOffset == offset) {
                    buddies[index] = (buddy.toInt() and SHIFT_MASK.toInt()).toByte()
                    return false
                }
                throw IllegalStateException(
                    "The intended segment was not found at index $index, for size $size and offset $offset"
                )
            }

            // We're at a parent size level. Use the target offset to guide our drill-down path.
            val claims: Boolean
            val siblingIndex: Int
            if (offset < currOffset + (currSize ushr 1)) {
                // Must be down the left path.
                claims = unreserveMatchingBuddy(index shl 1, size, offset, currOffset)
                siblingIndex = (index shl 1) + 1
            } else {
                // Must be down the right path.
                claims = unreserveMatchingBuddy(
                    (index shl 1) + 1, size, offset, currOffset + (currSize ushr 1)
                )
                siblingIndex = index shl 1
            }
            if (!claims) {
                // No other claims down the path we took. Check if the sibling has claims.
                val sibling = buddies[siblingIndex]
                if ((sibling.toInt() and SHIFT_MASK.toInt()) == sibling.toInt()) {
                    // No claims in the sibling. We can clear this level as well.
                    buddies[index] = (buddy.toInt() and SHIFT_MASK.toInt()).toByte()
                    return false
                }
            }
            return true
        }

        override fun toString(): String {
            val cap = delegate!!.capacity()
            val remaining = cap - allocatedBytes
            return "BuddyChunk[capacity: $cap, remaining: $remaining, free list: ${freeList.size()}]"
        }

        companion object {
            private const val MIN_BUDDY_SIZE = 32768
            private const val IS_CLAIMED = (1 shl 7).toByte()
            private const val HAS_CLAIMED_CHILDREN = (1 shl 6).toByte()
            private val SHIFT_MASK = (IS_CLAIMED.toInt() or HAS_CLAIMED_CHILDREN.toInt()).inv().toByte()
            private const val PACK_OFFSET_MASK = 0xFFFF
            private val PACK_SIZE_SHIFT = Integer.SIZE - Integer.numberOfLeadingZeros(PACK_OFFSET_MASK)
        }
    }

    internal class AdaptiveByteBuf(
        private val handle: EnhancedHandle<AdaptiveByteBuf>
    ) : AbstractReferenceCountedByteBuf(0) {

        // this both act as adjustment and the start index for a free list segment allocation
        private var startIndex: Int = 0
        private var rootParent: AbstractByteBuf? = null
        @JvmField
        var chunk: Chunk? = null
        private var length: Int = 0
        private var maxFastCapacity: Int = 0
        private var tmpNioBuf: ByteBuffer? = null
        private var hasArray: Boolean = false
        private var hasMemoryAddress: Boolean = false

        fun init(
            unwrapped: AbstractByteBuf?, wrapped: Chunk, readerIndex: Int, writerIndex: Int,
            startIndex: Int, size: Int, capacity: Int, maxCapacity: Int
        ) {
            this.startIndex = startIndex
            chunk = wrapped
            length = size
            maxFastCapacity = capacity
            maxCapacity(maxCapacity)
            setIndex0(readerIndex, writerIndex)
            hasArray = unwrapped!!.hasArray()
            hasMemoryAddress = unwrapped.hasMemoryAddress()
            rootParent = unwrapped
            tmpNioBuf = null

            if (PlatformDependent.isJfrEnabled() && AllocateBufferEvent.isEventEnabled()) {
                val event = AllocateBufferEvent()
                if (event.shouldCommit()) {
                    event.fill(this, AdaptiveByteBufAllocator::class.java)
                    event.chunkPooled = wrapped.pooled
                    val m = wrapped.magazine
                    event.chunkThreadLocal = m != null && m.allocationLockForTest == null
                    event.commit()
                }
            }
        }

        /**
         * Package-internal method to reset the reference count and discard marks.
         * Needed because Kotlin `protected` does not allow same-package access on other instances.
         */
        fun resetForRecycle() {
            resetRefCnt()
            discardMarks()
        }

        private fun rootParent(): AbstractByteBuf =
            rootParent ?: throw IllegalReferenceCountException()

        override fun capacity(): Int = length

        override fun maxFastWritableBytes(): Int =
            Math.min(maxFastCapacity, maxCapacity()) - writerIndex

        override fun capacity(newCapacity: Int): ByteBuf {
            checkNewCapacity(newCapacity)
            if (length <= newCapacity && newCapacity <= maxFastCapacity) {
                ensureAccessible()
                length = newCapacity
                return this
            }
            if (newCapacity < capacity()) {
                length = newCapacity
                trimIndicesToCapacity(newCapacity)
                return this
            }

            if (PlatformDependent.isJfrEnabled() && ReallocateBufferEvent.isEventEnabled()) {
                val event = ReallocateBufferEvent()
                if (event.shouldCommit()) {
                    event.fill(this, AdaptiveByteBufAllocator::class.java)
                    event.newCapacity = newCapacity
                    event.commit()
                }
            }

            // Reallocation required.
            val chunk = this.chunk!!
            val allocator = chunk.allocator
            val readerIdx = this.readerIndex
            val writerIdx = this.writerIndex
            val baseOldRootIndex = startIndex
            val oldLength = length
            val oldCapacity = maxFastCapacity
            val oldRoot = rootParent()
            allocator!!.reallocate(newCapacity, maxCapacity(), this)
            oldRoot.getBytes(baseOldRootIndex, this, 0, oldLength)
            chunk.releaseSegment(baseOldRootIndex, oldCapacity)
            assert(oldCapacity < maxFastCapacity && newCapacity <= maxFastCapacity) {
                "Capacity increase failed"
            }
            this.readerIndex = readerIdx
            this.writerIndex = writerIdx
            return this
        }

        override fun alloc(): ByteBufAllocator = rootParent().alloc()

        @Suppress("DEPRECATION")
        override fun order(): ByteOrder = rootParent().order()

        override fun unwrap(): ByteBuf? = null

        override fun isDirect(): Boolean = rootParent().isDirect()

        override fun arrayOffset(): Int = idx(rootParent().arrayOffset())

        override fun hasMemoryAddress(): Boolean = hasMemoryAddress

        override fun memoryAddress(): Long {
            ensureAccessible()
            return _memoryAddress()
        }

        override fun _memoryAddress(): Long {
            val root = rootParent
            return if (root != null) root._memoryAddress() + startIndex else 0L
        }

        override fun nioBuffer(index: Int, length: Int): ByteBuffer {
            checkIndex(index, length)
            return rootParent().nioBuffer(idx(index), length)
        }

        override fun internalNioBuffer(index: Int, length: Int): ByteBuffer {
            checkIndex(index, length)
            return (internalNioBuffer().position(index) as ByteBuffer).limit(index + length) as ByteBuffer
        }

        private fun internalNioBuffer(): ByteBuffer {
            if (tmpNioBuf == null) {
                tmpNioBuf = rootParent().nioBuffer(startIndex, maxFastCapacity)
            }
            return (tmpNioBuf!!.clear()) as ByteBuffer
        }

        override fun nioBuffers(index: Int, length: Int): Array<ByteBuffer> {
            checkIndex(index, length)
            return rootParent().nioBuffers(idx(index), length)
        }

        override fun hasArray(): Boolean = hasArray

        override fun array(): ByteArray {
            ensureAccessible()
            return rootParent().array()
        }

        override fun copy(index: Int, length: Int): ByteBuf {
            checkIndex(index, length)
            return rootParent().copy(idx(index), length)
        }

        override fun nioBufferCount(): Int = rootParent().nioBufferCount()

        override fun _getByte(index: Int): Byte = rootParent().getByte(idx(index))

        override fun _getShort(index: Int): Short = rootParent().getShort(idx(index))

        override fun _getShortLE(index: Int): Short = rootParent().getShortLE(idx(index))

        override fun _getUnsignedMedium(index: Int): Int = rootParent().getUnsignedMedium(idx(index))

        override fun _getUnsignedMediumLE(index: Int): Int = rootParent().getUnsignedMediumLE(idx(index))

        override fun _getInt(index: Int): Int = rootParent().getInt(idx(index))

        override fun _getIntLE(index: Int): Int = rootParent().getIntLE(idx(index))

        override fun _getLong(index: Int): Long = rootParent().getLong(idx(index))

        override fun _getLongLE(index: Int): Long = rootParent().getLongLE(idx(index))

        override fun getBytes(index: Int, dst: ByteBuf, dstIndex: Int, length: Int): ByteBuf {
            checkIndex(index, length)
            rootParent().getBytes(idx(index), dst, dstIndex, length)
            return this
        }

        override fun getBytes(index: Int, dst: ByteArray, dstIndex: Int, length: Int): ByteBuf {
            checkIndex(index, length)
            rootParent().getBytes(idx(index), dst, dstIndex, length)
            return this
        }

        override fun getBytes(index: Int, dst: ByteBuffer): ByteBuf {
            checkIndex(index, dst.remaining())
            rootParent().getBytes(idx(index), dst)
            return this
        }

        override fun _setByte(index: Int, value: Int) {
            rootParent().setByte(idx(index), value)
        }

        override fun _setShort(index: Int, value: Int) {
            rootParent().setShort(idx(index), value)
        }

        override fun _setShortLE(index: Int, value: Int) {
            rootParent().setShortLE(idx(index), value)
        }

        override fun _setMedium(index: Int, value: Int) {
            rootParent().setMedium(idx(index), value)
        }

        override fun _setMediumLE(index: Int, value: Int) {
            rootParent().setMediumLE(idx(index), value)
        }

        override fun _setInt(index: Int, value: Int) {
            rootParent().setInt(idx(index), value)
        }

        override fun _setIntLE(index: Int, value: Int) {
            rootParent().setIntLE(idx(index), value)
        }

        override fun _setLong(index: Int, value: Long) {
            rootParent().setLong(idx(index), value)
        }

        override fun _setLongLE(index: Int, value: Long) {
            rootParent().setLongLE(idx(index), value)
        }

        override fun setBytes(index: Int, src: ByteArray, srcIndex: Int, length: Int): ByteBuf {
            checkIndex(index, length)
            if (tmpNioBuf == null && PlatformDependent.javaVersion() >= 13) {
                val dstBuffer = rootParent()._internalNioBuffer()
                PlatformDependent.absolutePut(dstBuffer, idx(index), src, srcIndex, length)
            } else {
                val tmp = (internalNioBuffer().clear() as ByteBuffer).position(index) as ByteBuffer
                tmp.put(src, srcIndex, length)
            }
            return this
        }

        override fun setBytes(index: Int, src: ByteBuf, srcIndex: Int, length: Int): ByteBuf {
            checkIndex(index, length)
            if (src is AdaptiveByteBuf && PlatformDependent.javaVersion() >= 16) {
                src.checkIndex(srcIndex, length)
                val dstBuffer = rootParent()._internalNioBuffer()
                val srcBuffer = src.rootParent()._internalNioBuffer()
                PlatformDependent.absolutePut(dstBuffer, idx(index), srcBuffer, src.idx(srcIndex), length)
            } else {
                val tmp = internalNioBuffer()
                tmp.position(index)
                tmp.put(src.nioBuffer(srcIndex, length))
            }
            return this
        }

        override fun setBytes(index: Int, src: ByteBuffer): ByteBuf {
            val length = src.remaining()
            checkIndex(index, length)
            val tmp = internalNioBuffer()
            if (PlatformDependent.javaVersion() >= 16) {
                val offset = src.position()
                PlatformDependent.absolutePut(tmp, index, src, offset, length)
                src.position(offset + length)
            } else {
                tmp.position(index)
                tmp.put(src)
            }
            return this
        }

        @Throws(IOException::class)
        override fun getBytes(index: Int, out: OutputStream, length: Int): ByteBuf {
            checkIndex(index, length)
            if (length != 0) {
                val tmp = internalNioBuffer()
                ByteBufUtil.readBytes(alloc(), if (tmp.hasArray()) tmp else tmp.duplicate(), index, length, out)
            }
            return this
        }

        @Throws(IOException::class)
        override fun getBytes(index: Int, out: GatheringByteChannel, length: Int): Int {
            val buf = internalNioBuffer().duplicate()
            buf.clear().position(index).limit(index + length)
            return out.write(buf)
        }

        @Throws(IOException::class)
        override fun getBytes(index: Int, out: FileChannel, position: Long, length: Int): Int {
            val buf = internalNioBuffer().duplicate()
            buf.clear().position(index).limit(index + length)
            return out.write(buf, position)
        }

        @Throws(IOException::class)
        override fun setBytes(index: Int, `in`: InputStream, length: Int): Int {
            checkIndex(index, length)
            val rootParent = rootParent()
            if (rootParent.hasArray()) {
                return rootParent.setBytes(idx(index), `in`, length)
            }
            val tmp = ByteBufUtil.threadLocalTempArray(length)
            val readBytes = `in`.read(tmp, 0, length)
            if (readBytes <= 0) {
                return readBytes
            }
            setBytes(index, tmp, 0, readBytes)
            return readBytes
        }

        @Throws(IOException::class)
        override fun setBytes(index: Int, `in`: ScatteringByteChannel, length: Int): Int {
            return try {
                `in`.read(internalNioBuffer(index, length))
            } catch (ignored: ClosedChannelException) {
                -1
            }
        }

        @Throws(IOException::class)
        override fun setBytes(index: Int, `in`: FileChannel, position: Long, length: Int): Int {
            return try {
                `in`.read(internalNioBuffer(index, length), position)
            } catch (ignored: ClosedChannelException) {
                -1
            }
        }

        override fun setCharSequence(index: Int, sequence: CharSequence, charset: Charset): Int =
            setCharSequence0(index, sequence, charset, false)

        private fun setCharSequence0(index: Int, sequence: CharSequence, charset: Charset, expand: Boolean): Int {
            if (charset == CharsetUtil.UTF_8) {
                val length = ByteBufUtil.utf8MaxBytes(sequence)
                if (expand) {
                    ensureWritable0(length)
                    checkIndex0(index, length)
                } else {
                    checkIndex(index, length)
                }
                return ByteBufUtil.writeUtf8(this, index, length, sequence, sequence.length)
            }
            if (charset == CharsetUtil.US_ASCII || charset == CharsetUtil.ISO_8859_1) {
                val length = sequence.length
                if (expand) {
                    ensureWritable0(length)
                    checkIndex0(index, length)
                } else {
                    checkIndex(index, length)
                }
                return ByteBufUtil.writeAscii(this, index, sequence, length)
            }
            val bytes = sequence.toString().toByteArray(charset)
            if (expand) {
                ensureWritable0(bytes.size)
                // setBytes(...) will take care of checking the indices.
            }
            setBytes(index, bytes)
            return bytes.size
        }

        override fun writeCharSequence(sequence: CharSequence, charset: Charset): Int {
            val written = setCharSequence0(writerIndex, sequence, charset, true)
            writerIndex += written
            return written
        }

        override fun forEachByte(index: Int, length: Int, processor: ByteProcessor): Int {
            checkIndex(index, length)
            val ret = rootParent().forEachByte(idx(index), length, processor)
            return forEachResult(ret)
        }

        override fun forEachByteDesc(index: Int, length: Int, processor: ByteProcessor): Int {
            checkIndex(index, length)
            val ret = rootParent().forEachByteDesc(idx(index), length, processor)
            return forEachResult(ret)
        }

        override fun setZero(index: Int, length: Int): ByteBuf {
            checkIndex(index, length)
            rootParent().setZero(idx(index), length)
            return this
        }

        override fun writeZero(length: Int): ByteBuf {
            ensureWritable(length)
            rootParent().setZero(idx(writerIndex), length)
            writerIndex += length
            return this
        }

        private fun forEachResult(ret: Int): Int =
            if (ret < startIndex) -1 else ret - startIndex

        override fun isContiguous(): Boolean = rootParent().isContiguous()

        private fun idx(index: Int): Int = index + startIndex

        override fun deallocate() {
            if (PlatformDependent.isJfrEnabled() && FreeBufferEvent.isEventEnabled()) {
                val event = FreeBufferEvent()
                if (event.shouldCommit()) {
                    event.fill(this, AdaptiveByteBufAllocator::class.java)
                    event.commit()
                }
            }

            chunk?.releaseSegment(startIndex, maxFastCapacity)
            tmpNioBuf = null
            chunk = null
            rootParent = null
            handle.unguardedRecycle(this)
        }
    }

    /**
     * The strategy for how [AdaptivePoolingAllocator] should allocate chunk buffers.
     */
    interface ChunkAllocator {
        /**
         * Allocate a buffer for a chunk. This can be any kind of [AbstractByteBuf] implementation.
         * @param initialCapacity The initial capacity of the returned [AbstractByteBuf].
         * @param maxCapacity The maximum capacity of the returned [AbstractByteBuf].
         * @return The buffer that represents the chunk memory.
         */
        fun allocate(initialCapacity: Int, maxCapacity: Int): AbstractByteBuf
    }

    companion object {
        private val LOW_MEM_THRESHOLD = 512 * 1024 * 1024
        private val IS_LOW_MEM = Runtime.getRuntime().maxMemory() <= LOW_MEM_THRESHOLD

        /**
         * Whether the IS_LOW_MEM setting should disable thread-local magazines.
         * This can have fairly high performance overhead.
         */
        private val DISABLE_THREAD_LOCAL_MAGAZINES_ON_LOW_MEM = SystemPropertyUtil.getBoolean(
            "io.netty.allocator.disableThreadLocalMagazinesOnLowMemory", true
        )

        /**
         * The 128 KiB minimum chunk size is chosen to encourage the system allocator to delegate to mmap for chunk
         * allocations.
         */
        @JvmField
        val MIN_CHUNK_SIZE = 128 * 1024
        private const val EXPANSION_ATTEMPTS = 3
        private const val INITIAL_MAGAZINES = 1
        private const val RETIRE_CAPACITY = 256
        private val MAX_STRIPES = if (IS_LOW_MEM) 1 else NettyRuntime.availableProcessors() * 2
        private const val BUFS_PER_CHUNK = 8 // For large buffers, aim to have about this many buffers per chunk.

        /**
         * The maximum size of a pooled chunk, in bytes. Allocations bigger than this will never be pooled.
         *
         * This number is 8 MiB, and is derived from the limitations of internal histograms.
         */
        private val MAX_CHUNK_SIZE = if (IS_LOW_MEM) {
            2 * 1024 * 1024 // 2 MiB for systems with small heaps.
        } else {
            8 * 1024 * 1024 // 8 MiB.
        }
        private val MAX_POOLED_BUF_SIZE = MAX_CHUNK_SIZE / BUFS_PER_CHUNK

        /**
         * The capacity of the chunk reuse queues.
         */
        private val CHUNK_REUSE_QUEUE = Math.max(
            2, SystemPropertyUtil.getInt(
                "io.netty.allocator.chunkReuseQueueCapacity", NettyRuntime.availableProcessors() * 2
            )
        )

        /**
         * The capacity of the magazine local buffer queue.
         */
        private val MAGAZINE_BUFFER_QUEUE_CAPACITY = SystemPropertyUtil.getInt(
            "io.netty.allocator.magazineBufferQueueCapacity", 1024
        )

        /**
         * The size classes.
         */
        private val SIZE_CLASSES = intArrayOf(
            32, 64, 128, 256, 512,
            640, // 512 + 128
            1024,
            1152, // 1024 + 128
            2048,
            2304, // 2048 + 256
            4096,
            4352, // 4096 + 256
            8192,
            8704, // 8192 + 512
            16384,
            16896  // 16384 + 512
        )

        private val SIZE_CLASSES_COUNT = SIZE_CLASSES.size
        private val SIZE_INDEXES = ByteArray(SIZE_CLASSES[SIZE_CLASSES_COUNT - 1] / 32 + 1)

        init {
            require(MAGAZINE_BUFFER_QUEUE_CAPACITY >= 2) {
                "MAGAZINE_BUFFER_QUEUE_CAPACITY: $MAGAZINE_BUFFER_QUEUE_CAPACITY (expected: >= 2)"
            }
            var lastIndex = 0
            for (i in 0 until SIZE_CLASSES_COUNT) {
                val sizeClass = SIZE_CLASSES[i]
                assert((sizeClass and 5) == 0) { "Size class must be a multiple of 32" }
                val sizeIndex = sizeIndexOf(sizeClass)
                Arrays.fill(SIZE_INDEXES, lastIndex + 1, sizeIndex + 1, i.toByte())
                lastIndex = sizeIndex
            }
        }

        private fun sizeIndexOf(size: Int): Int =
            (size + 31) ushr 5

        @JvmStatic
        fun sizeClassIndexOf(size: Int): Int {
            val sizeIndex = sizeIndexOf(size)
            return if (sizeIndex < SIZE_INDEXES.size) {
                SIZE_INDEXES[sizeIndex].toInt()
            } else {
                SIZE_CLASSES_COUNT
            }
        }

        @JvmStatic
        fun getSizeClasses(): IntArray = SIZE_CLASSES.clone()

        /**
         * Create a thread-safe multi-producer, multi-consumer queue to hold chunks that spill over from the
         * internal Magazines.
         */
        private fun createSharedChunkQueue(): Queue<SizeClassedChunk> =
            PlatformDependent.newFixedMpmcQueue(CHUNK_REUSE_QUEUE)

        private fun createMagazineGroupSizeClasses(
            allocator: AdaptivePoolingAllocator, isThreadLocal: Boolean
        ): Array<MagazineGroup> =
            Array(SIZE_CLASSES.size) { i ->
                val segmentSize = SIZE_CLASSES[i]
                MagazineGroup(
                    allocator, allocator.chunkAllocator,
                    SizeClassChunkManagementStrategy(segmentSize), isThreadLocal
                )
            }
    }
}
