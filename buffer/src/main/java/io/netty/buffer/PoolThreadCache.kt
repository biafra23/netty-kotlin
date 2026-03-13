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

import io.netty.buffer.PoolArena.SizeClass
import io.netty.util.Recycler
import io.netty.util.Recycler.EnhancedHandle
import io.netty.util.internal.MathUtil
import io.netty.util.internal.ObjectPool.Handle
import io.netty.util.internal.ObjectUtil.checkPositiveOrZero
import io.netty.util.internal.PlatformDependent
import io.netty.util.internal.logging.InternalLoggerFactory
import java.nio.ByteBuffer
import java.util.Queue
import java.util.concurrent.atomic.AtomicBoolean

internal class PoolThreadCache(
    @JvmField val heapArena: PoolArena<ByteArray>?,
    @JvmField val directArena: PoolArena<ByteBuffer>?,
    smallCacheSize: Int,
    normalCacheSize: Int,
    maxCachedBufferCapacity: Int,
    private val freeSweepAllocationThreshold: Int,
    useFinalizer: Boolean
) {

    // Hold the caches for the different size classes, which are small and normal.
    private val smallSubPageHeapCaches: Array<MemoryRegionCache<ByteArray>>?
    private val smallSubPageDirectCaches: Array<MemoryRegionCache<ByteBuffer>>?
    private val normalHeapCaches: Array<MemoryRegionCache<ByteArray>>?
    private val normalDirectCaches: Array<MemoryRegionCache<ByteBuffer>>?

    private val freed = AtomicBoolean()
    @Suppress("unused") // Field is only here for the finalizer.
    private val freeOnFinalize: FreeOnFinalize?

    private var allocations: Int = 0

    init {
        checkPositiveOrZero(maxCachedBufferCapacity, "maxCachedBufferCapacity")
        if (directArena != null) {
            smallSubPageDirectCaches = createSubPageCaches(smallCacheSize, directArena.sizeClass.nSubpages)
            normalDirectCaches = createNormalCaches(normalCacheSize, maxCachedBufferCapacity, directArena)
            directArena.numThreadCaches.getAndIncrement()
        } else {
            // No directArea is configured so just null out all caches
            smallSubPageDirectCaches = null
            normalDirectCaches = null
        }
        if (heapArena != null) {
            // Create the caches for the heap allocations
            smallSubPageHeapCaches = createSubPageCaches(smallCacheSize, heapArena.sizeClass.nSubpages)
            normalHeapCaches = createNormalCaches(normalCacheSize, maxCachedBufferCapacity, heapArena)
            heapArena.numThreadCaches.getAndIncrement()
        } else {
            // No heapArea is configured so just null out all caches
            smallSubPageHeapCaches = null
            normalHeapCaches = null
        }

        // Only check if there are caches in use.
        if ((smallSubPageDirectCaches != null || normalDirectCaches != null
                    || smallSubPageHeapCaches != null || normalHeapCaches != null)
            && freeSweepAllocationThreshold < 1
        ) {
            throw IllegalArgumentException(
                "freeSweepAllocationThreshold: $freeSweepAllocationThreshold (expected: > 0)"
            )
        }
        freeOnFinalize = if (useFinalizer) FreeOnFinalize(this) else null
    }

    /**
     * Try to allocate a small buffer out of the cache. Returns `true` if successful `false` otherwise
     */
    fun allocateSmall(area: PoolArena<*>, buf: PooledByteBuf<*>, reqCapacity: Int, sizeIdx: Int): Boolean {
        return allocate(cacheForSmall(area, sizeIdx), buf, reqCapacity)
    }

    /**
     * Try to allocate a normal buffer out of the cache. Returns `true` if successful `false` otherwise
     */
    fun allocateNormal(area: PoolArena<*>, buf: PooledByteBuf<*>, reqCapacity: Int, sizeIdx: Int): Boolean {
        return allocate(cacheForNormal(area, sizeIdx), buf, reqCapacity)
    }

    @Suppress("UNCHECKED_CAST")
    private fun allocate(cache: MemoryRegionCache<*>?, buf: PooledByteBuf<*>, reqCapacity: Int): Boolean {
        if (cache == null) {
            // no cache found so just return false here
            return false
        }
        val allocated = (cache as MemoryRegionCache<Any>).allocate(buf as PooledByteBuf<Any>, reqCapacity, this)
        if (++allocations >= freeSweepAllocationThreshold) {
            allocations = 0
            trim()
        }
        return allocated
    }

    /**
     * Add [PoolChunk] and `handle` to the cache if there is enough room.
     * Returns `true` if it fit into the cache `false` otherwise.
     */
    @Suppress("UNCHECKED_CAST")
    fun add(
        area: PoolArena<*>, chunk: PoolChunk<*>, nioBuffer: ByteBuffer?,
        handle: Long, normCapacity: Int, sizeClass: SizeClass
    ): Boolean {
        val sizeIdx = area.sizeClass.size2SizeIdx(normCapacity)
        val cache = cache(area, sizeIdx, sizeClass)
        if (cache == null) {
            return false
        }
        if (freed.get()) {
            return false
        }
        return (cache as MemoryRegionCache<Any>).add(chunk as PoolChunk<Any>, nioBuffer, handle, normCapacity)
    }

    private fun cache(area: PoolArena<*>, sizeIdx: Int, sizeClass: SizeClass): MemoryRegionCache<*>? {
        return when (sizeClass) {
            SizeClass.Normal -> cacheForNormal(area, sizeIdx)
            SizeClass.Small -> cacheForSmall(area, sizeIdx)
        }
    }

    /**
     *  Should be called if the Thread that uses this cache is about to exit to release resources out of the cache
     */
    fun free(finalizer: Boolean) {
        // As free() may be called either by the finalizer or by FastThreadLocal.onRemoval(...) we need to ensure
        // we only call this one time.
        if (freed.compareAndSet(false, true)) {
            if (freeOnFinalize != null) {
                // Help GC: this can race with a finalizer thread, but will be null out regardless
                freeOnFinalize.cache = null
            }
            @Suppress("UNCHECKED_CAST")
            val numFreed = free(smallSubPageDirectCaches as Array<MemoryRegionCache<*>>?, finalizer) +
                           free(normalDirectCaches as Array<MemoryRegionCache<*>>?, finalizer) +
                           free(smallSubPageHeapCaches as Array<MemoryRegionCache<*>>?, finalizer) +
                           free(normalHeapCaches as Array<MemoryRegionCache<*>>?, finalizer)

            if (numFreed > 0 && logger.isDebugEnabled()) {
                logger.debug(
                    "Freed {} thread-local buffer(s) from thread: {}", numFreed,
                    Thread.currentThread().name
                )
            }

            directArena?.numThreadCaches?.getAndDecrement()
            heapArena?.numThreadCaches?.getAndDecrement()
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun trim() {
        trim(smallSubPageDirectCaches as Array<MemoryRegionCache<*>>?)
        trim(normalDirectCaches as Array<MemoryRegionCache<*>>?)
        trim(smallSubPageHeapCaches as Array<MemoryRegionCache<*>>?)
        trim(normalHeapCaches as Array<MemoryRegionCache<*>>?)
    }

    private fun cacheForSmall(area: PoolArena<*>, sizeIdx: Int): MemoryRegionCache<*>? {
        return if (area.isDirect()) {
            cache(smallSubPageDirectCaches, sizeIdx)
        } else {
            cache(smallSubPageHeapCaches, sizeIdx)
        }
    }

    private fun cacheForNormal(area: PoolArena<*>, sizeIdx: Int): MemoryRegionCache<*>? {
        // We need to subtract area.sizeClass.nSubpages as sizeIdx is the overall index for all sizes.
        val idx = sizeIdx - area.sizeClass.nSubpages
        return if (area.isDirect()) {
            cache(normalDirectCaches, idx)
        } else {
            cache(normalHeapCaches, idx)
        }
    }

    /**
     * Cache used for buffers which are backed by TINY or SMALL size.
     */
    private class SubPageMemoryRegionCache<T>(size: Int) : MemoryRegionCache<T>(size, SizeClass.Small) {
        override fun initBuf(
            chunk: PoolChunk<T>, nioBuffer: ByteBuffer?, handle: Long, buf: PooledByteBuf<T>, reqCapacity: Int,
            threadCache: PoolThreadCache
        ) {
            chunk.initBufWithSubpage(buf, nioBuffer, handle, reqCapacity, threadCache, true)
        }
    }

    /**
     * Cache used for buffers which are backed by NORMAL size.
     */
    private class NormalMemoryRegionCache<T>(size: Int) : MemoryRegionCache<T>(size, SizeClass.Normal) {
        override fun initBuf(
            chunk: PoolChunk<T>, nioBuffer: ByteBuffer?, handle: Long, buf: PooledByteBuf<T>, reqCapacity: Int,
            threadCache: PoolThreadCache
        ) {
            chunk.initBuf(buf, nioBuffer, handle, reqCapacity, threadCache, true)
        }
    }

    private abstract class MemoryRegionCache<T>(size: Int, private val sizeClass: SizeClass) {
        private val size: Int = MathUtil.safeFindNextPositivePowerOfTwo(size)
        private val queue: Queue<Entry<T>> = PlatformDependent.newFixedMpscUnpaddedQueue(this.size)
        private var allocations: Int = 0

        /**
         * Init the [PooledByteBuf] using the provided chunk and handle with the capacity restrictions.
         */
        protected abstract fun initBuf(
            chunk: PoolChunk<T>, nioBuffer: ByteBuffer?, handle: Long,
            buf: PooledByteBuf<T>, reqCapacity: Int, threadCache: PoolThreadCache
        )

        /**
         * Add to cache if not already full.
         */
        fun add(chunk: PoolChunk<T>, nioBuffer: ByteBuffer?, handle: Long, normCapacity: Int): Boolean {
            val entry = Entry.newEntry(chunk, nioBuffer, handle, normCapacity)
            val queued = queue.offer(entry)
            if (!queued) {
                // If it was not possible to cache the chunk, immediately recycle the entry
                entry.unguardedRecycle()
            }
            return queued
        }

        /**
         * Allocate something out of the cache if possible and remove the entry from the cache.
         */
        fun allocate(buf: PooledByteBuf<T>, reqCapacity: Int, threadCache: PoolThreadCache): Boolean {
            val entry = queue.poll() ?: return false
            initBuf(entry.chunk!!, entry.nioBuffer, entry.handle, buf, reqCapacity, threadCache)
            entry.unguardedRecycle()

            // allocations is not thread-safe which is fine as this is only called from the same thread all time.
            ++allocations
            return true
        }

        /**
         * Clear out this cache and free up all previous cached [PoolChunk]s and `handle`s.
         */
        fun free(finalizer: Boolean): Int {
            return free(Int.MAX_VALUE, finalizer)
        }

        private fun free(max: Int, finalizer: Boolean): Int {
            var numFreed = 0
            while (numFreed < max) {
                val entry = queue.poll()
                if (entry != null) {
                    freeEntry(entry, finalizer)
                } else {
                    // all cleared
                    return numFreed
                }
                numFreed++
            }
            return numFreed
        }

        /**
         * Free up cached [PoolChunk]s if not allocated frequently enough.
         */
        fun trim() {
            val free = size - allocations
            allocations = 0

            // We not even allocated all the number that are
            if (free > 0) {
                free(free, false)
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun freeEntry(entry: Entry<*>, finalizer: Boolean) {
            // Capture entry state before we recycle the entry object.
            val chunk = entry.chunk as PoolChunk<Any>
            val handle = entry.handle
            val nioBuffer = entry.nioBuffer
            val normCapacity = entry.normCapacity

            if (!finalizer) {
                // recycle now so PoolChunk can be GC'ed. This will only be done if this is not freed because of
                // a finalizer.
                entry.recycle()
            }

            chunk.arena.freeChunk(chunk, handle, normCapacity, sizeClass, nioBuffer, finalizer)
        }

        class Entry<T> internal constructor(recyclerHandle: Handle<Entry<*>>) {
            @JvmField
            val recyclerHandle: EnhancedHandle<Entry<*>> = recyclerHandle as EnhancedHandle<Entry<*>>
            @JvmField
            var chunk: PoolChunk<T>? = null
            @JvmField
            var nioBuffer: ByteBuffer? = null
            @JvmField
            var handle: Long = -1
            @JvmField
            var normCapacity: Int = 0

            fun recycle() {
                chunk = null
                nioBuffer = null
                handle = -1
                recyclerHandle.recycle(this)
            }

            fun unguardedRecycle() {
                chunk = null
                nioBuffer = null
                handle = -1
                recyclerHandle.unguardedRecycle(this)
            }

            companion object {
                @Suppress("UNCHECKED_CAST")
                @JvmStatic
                private val RECYCLER: Recycler<Entry<*>> = object : Recycler<Entry<*>>() {
                    override fun newObject(handle: Handle<Entry<*>>): Entry<*> {
                        return Entry<Any>(handle)
                    }
                }

                @JvmStatic
                fun <T> newEntry(chunk: PoolChunk<T>, nioBuffer: ByteBuffer?, handle: Long, normCapacity: Int): Entry<T> {
                    @Suppress("UNCHECKED_CAST")
                    val entry = RECYCLER.get() as Entry<T>
                    entry.chunk = chunk
                    entry.nioBuffer = nioBuffer
                    entry.handle = handle
                    entry.normCapacity = normCapacity
                    return entry
                }
            }
        }
    }

    private class FreeOnFinalize(@Volatile @JvmField var cache: PoolThreadCache?) {
        /// TODO: In the future when we move to Java9+ we should use java.lang.ref.Cleaner.
        @Suppress("deprecation")
        protected fun finalize() {
            try {
                val cache = this.cache
                // this can race with a non-finalizer thread calling free: regardless who wins, the cache will be
                // null out
                this.cache = null
                cache?.free(true)
            } finally {
                // No super.finalize() needed in Kotlin
            }
        }
    }

    companion object {
        private val logger = InternalLoggerFactory.getInstance(PoolThreadCache::class.java)
        private const val INTEGER_SIZE_MINUS_ONE = Int.SIZE_BITS - 1

        @JvmStatic
        fun log2(`val`: Int): Int {
            return INTEGER_SIZE_MINUS_ONE - Integer.numberOfLeadingZeros(`val`)
        }

        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        private fun <T> createSubPageCaches(cacheSize: Int, numCaches: Int): Array<MemoryRegionCache<T>>? {
            if (cacheSize > 0 && numCaches > 0) {
                val cache = arrayOfNulls<MemoryRegionCache<*>>(numCaches) as Array<MemoryRegionCache<T>?>
                for (i in cache.indices) {
                    cache[i] = SubPageMemoryRegionCache(cacheSize)
                }
                @Suppress("UNCHECKED_CAST")
                return cache as Array<MemoryRegionCache<T>>
            }
            return null
        }

        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        private fun <T> createNormalCaches(
            cacheSize: Int, maxCachedBufferCapacity: Int, area: PoolArena<T>
        ): Array<MemoryRegionCache<T>>? {
            if (cacheSize > 0 && maxCachedBufferCapacity > 0) {
                val max = Math.min(area.sizeClass.chunkSize, maxCachedBufferCapacity)
                val cache = ArrayList<MemoryRegionCache<T>>()
                var idx = area.sizeClass.nSubpages
                while (idx < area.sizeClass.nSizes && area.sizeClass.sizeIdx2size(idx) <= max) {
                    cache.add(NormalMemoryRegionCache(cacheSize))
                    idx++
                }
                return cache.toTypedArray() as Array<MemoryRegionCache<T>>
            }
            return null
        }

        @JvmStatic
        private fun free(caches: Array<MemoryRegionCache<*>>?, finalizer: Boolean): Int {
            if (caches == null) {
                return 0
            }
            var numFreed = 0
            for (c in caches) {
                numFreed += free(c, finalizer)
            }
            return numFreed
        }

        @JvmStatic
        private fun free(cache: MemoryRegionCache<*>?, finalizer: Boolean): Int {
            return cache?.free(finalizer) ?: 0
        }

        @JvmStatic
        private fun trim(caches: Array<MemoryRegionCache<*>>?) {
            if (caches == null) {
                return
            }
            for (c in caches) {
                trim(c)
            }
        }

        @JvmStatic
        private fun trim(cache: MemoryRegionCache<*>?) {
            cache?.trim()
        }

        @JvmStatic
        private fun <T> cache(cache: Array<MemoryRegionCache<T>>?, sizeIdx: Int): MemoryRegionCache<T>? {
            if (cache == null || sizeIdx >= cache.size) {
                return null
            }
            return cache[sizeIdx]
        }
    }
}
