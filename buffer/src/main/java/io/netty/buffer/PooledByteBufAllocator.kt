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

import io.netty.util.NettyRuntime
import io.netty.util.concurrent.EventExecutor
import io.netty.util.concurrent.FastThreadLocal
import io.netty.util.concurrent.FastThreadLocalThread
import io.netty.util.internal.ObjectUtil.checkPositiveOrZero
import io.netty.util.internal.PlatformDependent
import io.netty.util.internal.StringUtil
import io.netty.util.internal.SystemPropertyUtil
import io.netty.util.internal.ThreadExecutorMap
import io.netty.util.internal.logging.InternalLoggerFactory
import java.nio.ByteBuffer
import java.util.Collections
import java.util.concurrent.TimeUnit

open class PooledByteBufAllocator : AbstractByteBufAllocator, ByteBufAllocatorMetricProvider {

    private val trimTask = Runnable { this@PooledByteBufAllocator.trimCurrentThreadCache() }

    private val heapArenas: Array<PoolArena<ByteArray>>?
    private val directArenas: Array<PoolArena<ByteBuffer>>?
    private val smallCacheSize: Int
    private val normalCacheSize: Int
    private val heapArenaMetrics: List<PoolArenaMetric>
    private val directArenaMetrics: List<PoolArenaMetric>
    private val threadCache: PoolThreadLocalCache
    private val chunkSize: Int
    private val metric: PooledByteBufAllocatorMetric

    constructor() : this(false)

    @Suppress("DEPRECATION")
    constructor(preferDirect: Boolean) : this(
        preferDirect, DEFAULT_NUM_HEAP_ARENA, DEFAULT_NUM_DIRECT_ARENA, DEFAULT_PAGE_SIZE, DEFAULT_MAX_ORDER
    )

    @Suppress("DEPRECATION")
    constructor(nHeapArena: Int, nDirectArena: Int, pageSize: Int, maxOrder: Int) : this(
        false, nHeapArena, nDirectArena, pageSize, maxOrder
    )

    /**
     * @deprecated use
     * [PooledByteBufAllocator(boolean, int, int, int, int, int, int, boolean)][PooledByteBufAllocator]
     */
    @Deprecated("Use the constructor without tinyCacheSize")
    constructor(
        preferDirect: Boolean, nHeapArena: Int, nDirectArena: Int, pageSize: Int, maxOrder: Int
    ) : this(
        preferDirect, nHeapArena, nDirectArena, pageSize, maxOrder,
        0, DEFAULT_SMALL_CACHE_SIZE, DEFAULT_NORMAL_CACHE_SIZE
    )

    /**
     * @deprecated use
     * [PooledByteBufAllocator(boolean, int, int, int, int, int, int, boolean)][PooledByteBufAllocator]
     */
    @Deprecated("Use the constructor without tinyCacheSize")
    constructor(
        preferDirect: Boolean, nHeapArena: Int, nDirectArena: Int, pageSize: Int, maxOrder: Int,
        @Suppress("UNUSED_PARAMETER") tinyCacheSize: Int, smallCacheSize: Int, normalCacheSize: Int
    ) : this(
        preferDirect, nHeapArena, nDirectArena, pageSize, maxOrder, smallCacheSize,
        normalCacheSize, DEFAULT_USE_CACHE_FOR_ALL_THREADS, DEFAULT_DIRECT_MEMORY_CACHE_ALIGNMENT
    )

    /**
     * @deprecated use
     * [PooledByteBufAllocator(boolean, int, int, int, int, int, int, boolean)][PooledByteBufAllocator]
     */
    @Deprecated("Use the constructor without tinyCacheSize")
    constructor(
        preferDirect: Boolean, nHeapArena: Int, nDirectArena: Int, pageSize: Int, maxOrder: Int,
        @Suppress("UNUSED_PARAMETER") tinyCacheSize: Int, smallCacheSize: Int, normalCacheSize: Int,
        useCacheForAllThreads: Boolean
    ) : this(
        preferDirect, nHeapArena, nDirectArena, pageSize, maxOrder,
        smallCacheSize, normalCacheSize, useCacheForAllThreads
    )

    constructor(
        preferDirect: Boolean, nHeapArena: Int, nDirectArena: Int, pageSize: Int, maxOrder: Int,
        smallCacheSize: Int, normalCacheSize: Int, useCacheForAllThreads: Boolean
    ) : this(
        preferDirect, nHeapArena, nDirectArena, pageSize, maxOrder,
        smallCacheSize, normalCacheSize, useCacheForAllThreads, DEFAULT_DIRECT_MEMORY_CACHE_ALIGNMENT
    )

    /**
     * @deprecated use
     * [PooledByteBufAllocator(boolean, int, int, int, int, int, int, boolean, int)][PooledByteBufAllocator]
     */
    @Deprecated("Use the constructor without tinyCacheSize")
    constructor(
        preferDirect: Boolean, nHeapArena: Int, nDirectArena: Int, pageSize: Int, maxOrder: Int,
        @Suppress("UNUSED_PARAMETER") tinyCacheSize: Int, smallCacheSize: Int, normalCacheSize: Int,
        useCacheForAllThreads: Boolean, directMemoryCacheAlignment: Int
    ) : this(
        preferDirect, nHeapArena, nDirectArena, pageSize, maxOrder,
        smallCacheSize, normalCacheSize, useCacheForAllThreads, directMemoryCacheAlignment
    )

    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(
        preferDirect: Boolean, nHeapArena: Int, nDirectArena: Int, pageSize: Int, maxOrder: Int,
        smallCacheSize: Int, normalCacheSize: Int,
        useCacheForAllThreads: Boolean, directMemoryCacheAlignment: Int
    ) : super(preferDirect) {
        var pageSizeVar = pageSize
        var directMemoryCacheAlignmentVar = directMemoryCacheAlignment

        threadCache = PoolThreadLocalCache(useCacheForAllThreads)
        this.smallCacheSize = smallCacheSize
        this.normalCacheSize = normalCacheSize

        if (directMemoryCacheAlignmentVar != 0) {
            if (!PlatformDependent.hasAlignDirectByteBuffer()) {
                throw UnsupportedOperationException(
                    "Buffer alignment is not supported. " +
                        "Either Unsafe or ByteBuffer.alignSlice() must be available."
                )
            }
            // Ensure page size is a whole multiple of the alignment, or bump it to the next whole multiple.
            pageSizeVar = PlatformDependent.align(pageSizeVar.toLong(), directMemoryCacheAlignmentVar).toInt()
        }

        chunkSize = validateAndCalculateChunkSize(pageSizeVar, maxOrder)

        checkPositiveOrZero(nHeapArena, "nHeapArena")
        checkPositiveOrZero(nDirectArena, "nDirectArena")

        checkPositiveOrZero(directMemoryCacheAlignmentVar, "directMemoryCacheAlignment")
        if (directMemoryCacheAlignmentVar > 0 && !isDirectMemoryCacheAlignmentSupported()) {
            throw IllegalArgumentException("directMemoryCacheAlignment is not supported")
        }

        if (directMemoryCacheAlignmentVar and -directMemoryCacheAlignmentVar != directMemoryCacheAlignmentVar) {
            throw IllegalArgumentException(
                "directMemoryCacheAlignment: $directMemoryCacheAlignmentVar (expected: power of two)"
            )
        }

        val pageShifts = validateAndCalculatePageShifts(pageSizeVar, directMemoryCacheAlignmentVar)

        if (nHeapArena > 0) {
            heapArenas = newArenaArray(nHeapArena)
            val metrics = ArrayList<PoolArenaMetric>(heapArenas.size)
            val sizeClasses = SizeClasses(pageSizeVar, pageShifts, chunkSize, 0)
            for (i in heapArenas.indices) {
                val arena = PoolArena.HeapArena(this, sizeClasses)
                heapArenas[i] = arena
                metrics.add(arena)
            }
            heapArenaMetrics = Collections.unmodifiableList(metrics)
        } else {
            heapArenas = null
            heapArenaMetrics = emptyList()
        }

        if (nDirectArena > 0) {
            directArenas = newArenaArray(nDirectArena)
            val metrics = ArrayList<PoolArenaMetric>(directArenas.size)
            val sizeClasses = SizeClasses(pageSizeVar, pageShifts, chunkSize, directMemoryCacheAlignmentVar)
            for (i in directArenas.indices) {
                val arena = PoolArena.DirectArena(this, sizeClasses)
                directArenas[i] = arena
                metrics.add(arena)
            }
            directArenaMetrics = Collections.unmodifiableList(metrics)
        } else {
            directArenas = null
            directArenaMetrics = emptyList()
        }
        metric = PooledByteBufAllocatorMetric(this)
    }

    override fun newHeapBuffer(initialCapacity: Int, maxCapacity: Int): ByteBuf {
        val cache = threadCache.get()
        val heapArena = cache.heapArena

        val buf: AbstractByteBuf
        if (heapArena != null) {
            buf = heapArena.allocate(cache, initialCapacity, maxCapacity)
        } else {
            buf = if (PlatformDependent.hasUnsafe()) {
                UnpooledUnsafeHeapByteBuf(this, initialCapacity, maxCapacity)
            } else {
                UnpooledHeapByteBuf(this, initialCapacity, maxCapacity)
            }
            onAllocateBuffer(buf, false, false)
        }
        return toLeakAwareBuffer(buf)
    }

    override fun newDirectBuffer(initialCapacity: Int, maxCapacity: Int): ByteBuf {
        val cache = threadCache.get()
        val directArena = cache.directArena

        val buf: AbstractByteBuf
        if (directArena != null) {
            buf = directArena.allocate(cache, initialCapacity, maxCapacity)
        } else {
            buf = if (PlatformDependent.hasUnsafe()) {
                UnsafeByteBufUtil.newUnsafeDirectByteBuf(this, initialCapacity, maxCapacity)
            } else {
                UnpooledDirectByteBuf(this, initialCapacity, maxCapacity)
            }
            onAllocateBuffer(buf, false, false)
        }
        return toLeakAwareBuffer(buf)
    }

    override fun isDirectBufferPooled(): Boolean = directArenas != null

    /**
     * @deprecated will be removed
     * Returns `true` if the calling [Thread] has a [ThreadLocal] cache for the allocated
     * buffers.
     */
    @Deprecated("will be removed")
    fun hasThreadLocalCache(): Boolean = threadCache.isSet()

    /**
     * @deprecated will be removed
     * Free all cached buffers for the calling [Thread].
     */
    @Deprecated("will be removed")
    fun freeThreadLocalCache() {
        threadCache.remove()
    }

    override fun metric(): PooledByteBufAllocatorMetric = metric

    /**
     * Return the number of heap arenas.
     *
     * @deprecated use [PooledByteBufAllocatorMetric.numHeapArenas].
     */
    @Deprecated("use PooledByteBufAllocatorMetric.numHeapArenas()")
    fun numHeapArenas(): Int = heapArenaMetrics.size

    /**
     * Return the number of direct arenas.
     *
     * @deprecated use [PooledByteBufAllocatorMetric.numDirectArenas].
     */
    @Deprecated("use PooledByteBufAllocatorMetric.numDirectArenas()")
    fun numDirectArenas(): Int = directArenaMetrics.size

    /**
     * Return a [List] of all heap [PoolArenaMetric]s that are provided by this pool.
     *
     * @deprecated use [PooledByteBufAllocatorMetric.heapArenas].
     */
    @Deprecated("use PooledByteBufAllocatorMetric.heapArenas()")
    fun heapArenas(): List<PoolArenaMetric> = heapArenaMetrics

    /**
     * Return a [List] of all direct [PoolArenaMetric]s that are provided by this pool.
     *
     * @deprecated use [PooledByteBufAllocatorMetric.directArenas].
     */
    @Deprecated("use PooledByteBufAllocatorMetric.directArenas()")
    fun directArenas(): List<PoolArenaMetric> = directArenaMetrics

    /**
     * Return the number of thread local caches used by this [PooledByteBufAllocator].
     *
     * @deprecated use [PooledByteBufAllocatorMetric.numThreadLocalCaches].
     */
    @Deprecated("use PooledByteBufAllocatorMetric.numThreadLocalCaches()")
    @Suppress("UNCHECKED_CAST")
    fun numThreadLocalCaches(): Int =
        Math.max(
            numThreadLocalCaches(heapArenas as Array<PoolArena<*>>?),
            numThreadLocalCaches(directArenas as Array<PoolArena<*>>?)
        )

    /**
     * Return the size of the tiny cache.
     *
     * @deprecated use [PooledByteBufAllocatorMetric.tinyCacheSize].
     */
    @Deprecated("Tiny caches have been merged into small caches.")
    fun tinyCacheSize(): Int = 0

    /**
     * Return the size of the small cache.
     *
     * @deprecated use [PooledByteBufAllocatorMetric.smallCacheSize].
     */
    @Deprecated("use PooledByteBufAllocatorMetric.smallCacheSize()")
    fun smallCacheSize(): Int = smallCacheSize

    /**
     * Return the size of the normal cache.
     *
     * @deprecated use [PooledByteBufAllocatorMetric.normalCacheSize].
     */
    @Deprecated("use PooledByteBufAllocatorMetric.normalCacheSize()")
    fun normalCacheSize(): Int = normalCacheSize

    /**
     * Return the chunk size for an arena.
     *
     * @deprecated use [PooledByteBufAllocatorMetric.chunkSize].
     */
    @Deprecated("use PooledByteBufAllocatorMetric.chunkSize()")
    fun chunkSize(): Int = chunkSize

    @Suppress("UNCHECKED_CAST")
    internal fun usedHeapMemory(): Long = usedMemory(heapArenas as Array<PoolArena<*>>?)

    @Suppress("UNCHECKED_CAST")
    internal fun usedDirectMemory(): Long = usedMemory(directArenas as Array<PoolArena<*>>?)

    /**
     * Returns the number of bytes of heap memory that is currently pinned to heap buffers allocated by a
     * [ByteBufAllocator], or `-1` if unknown.
     * A buffer can pin more memory than its [capacity][ByteBuf.capacity] might indicate,
     * due to implementation details of the allocator.
     */
    @Suppress("UNCHECKED_CAST")
    fun pinnedHeapMemory(): Long = pinnedMemory(heapArenas as Array<PoolArena<*>>?)

    /**
     * Returns the number of bytes of direct memory that is currently pinned to direct buffers allocated by a
     * [ByteBufAllocator], or `-1` if unknown.
     * A buffer can pin more memory than its [capacity][ByteBuf.capacity] might indicate,
     * due to implementation details of the allocator.
     */
    @Suppress("UNCHECKED_CAST")
    fun pinnedDirectMemory(): Long = pinnedMemory(directArenas as Array<PoolArena<*>>?)

    internal fun threadCache(): PoolThreadCache {
        val cache = threadCache.get()
        assert(cache != null)
        return cache
    }

    /**
     * Trim thread local cache for the current [Thread], which will give back any cached memory that was not
     * allocated frequently since the last trim operation.
     *
     * Returns `true` if a cache for the current [Thread] exists and so was trimmed, false otherwise.
     */
    fun trimCurrentThreadCache(): Boolean {
        val cache = threadCache.getIfExists()
        if (cache != null) {
            cache.trim()
            return true
        }
        return false
    }

    /**
     * Returns the status of the allocator (which contains all metrics) as string. Be aware this may be expensive
     * and so should not be called too frequently.
     */
    fun dumpStats(): String {
        val heapArenasLen = heapArenas?.size ?: 0
        val buf = StringBuilder(512)
            .append(heapArenasLen)
            .append(" heap arena(s):")
            .append(StringUtil.NEWLINE)
        if (heapArenasLen > 0) {
            for (a in heapArenas!!) {
                buf.append(a)
            }
        }

        val directArenasLen = directArenas?.size ?: 0

        buf.append(directArenasLen)
            .append(" direct arena(s):")
            .append(StringUtil.NEWLINE)
        if (directArenasLen > 0) {
            for (a in directArenas!!) {
                buf.append(a)
            }
        }

        return buf.toString()
    }

    private inner class PoolThreadLocalCache(
        private val useCacheForAllThreads: Boolean
    ) : FastThreadLocal<PoolThreadCache>() {

        @Synchronized
        override fun initialValue(): PoolThreadCache {
            val heapArena = leastUsedArena(heapArenas)
            val directArena = leastUsedArena(directArenas)

            val current = Thread.currentThread()
            val executor: EventExecutor? = ThreadExecutorMap.currentExecutor()

            if (useCacheForAllThreads ||
                // If the current thread is a FastThreadLocalThread we will always use the cache
                FastThreadLocalThread.currentThreadHasFastThreadLocal() ||
                // The Thread is used by an EventExecutor, let's use the cache as the chances are good that we
                // will allocate a lot!
                executor != null
            ) {
                val cache = PoolThreadCache(
                    heapArena, directArena, smallCacheSize, normalCacheSize,
                    DEFAULT_MAX_CACHED_BUFFER_CAPACITY, DEFAULT_CACHE_TRIM_INTERVAL, useCacheFinalizers()
                )

                if (DEFAULT_CACHE_TRIM_INTERVAL_MILLIS > 0) {
                    executor?.scheduleAtFixedRate(
                        trimTask, DEFAULT_CACHE_TRIM_INTERVAL_MILLIS,
                        DEFAULT_CACHE_TRIM_INTERVAL_MILLIS, TimeUnit.MILLISECONDS
                    )
                }
                return cache
            }
            // No caching so just use 0 as sizes.
            return PoolThreadCache(heapArena, directArena, 0, 0, 0, 0, false)
        }

        override fun onRemoval(threadCache: PoolThreadCache) {
            threadCache.free(false)
        }

        private fun <T> leastUsedArena(arenas: Array<PoolArena<T>>?): PoolArena<T>? {
            if (arenas.isNullOrEmpty()) {
                return null
            }

            var minArena = arenas[0]
            // Optimized: if it is the first execution, directly return minArena
            // and reduce the number of for loop comparisons below
            if (minArena.numThreadCaches.get() == CACHE_NOT_USED) {
                return minArena
            }
            for (i in 1 until arenas.size) {
                val arena = arenas[i]
                if (arena.numThreadCaches.get() < minArena.numThreadCaches.get()) {
                    minArena = arena
                }
            }

            return minArena
        }
    }

    companion object {
        private val logger = InternalLoggerFactory.getInstance(PooledByteBufAllocator::class.java)

        private val DEFAULT_NUM_HEAP_ARENA: Int
        private val DEFAULT_NUM_DIRECT_ARENA: Int
        private val DEFAULT_PAGE_SIZE: Int
        private val DEFAULT_MAX_ORDER: Int // 8192 << 9 = 4 MiB per chunk
        private val DEFAULT_SMALL_CACHE_SIZE: Int
        private val DEFAULT_NORMAL_CACHE_SIZE: Int
        @JvmField
        internal val DEFAULT_MAX_CACHED_BUFFER_CAPACITY: Int
        private val DEFAULT_CACHE_TRIM_INTERVAL: Int
        private val DEFAULT_CACHE_TRIM_INTERVAL_MILLIS: Long
        private val DEFAULT_USE_CACHE_FOR_ALL_THREADS: Boolean
        private val DEFAULT_DIRECT_MEMORY_CACHE_ALIGNMENT: Int
        @JvmField
        internal val DEFAULT_MAX_CACHED_BYTEBUFFERS_PER_CHUNK: Int
        private val DEFAULT_DISABLE_CACHE_FINALIZERS_FOR_FAST_THREAD_LOCAL_THREADS: Boolean

        private const val MIN_PAGE_SIZE = 4096
        private val MAX_CHUNK_SIZE = ((Int.MAX_VALUE.toLong() + 1) / 2).toInt()

        private const val CACHE_NOT_USED = 0

        init {
            var defaultAlignment = SystemPropertyUtil.getInt(
                "io.netty.allocator.directMemoryCacheAlignment", 0
            )
            var defaultPageSize = SystemPropertyUtil.getInt("io.netty.allocator.pageSize", 8192)
            var pageSizeFallbackCause: Throwable? = null
            try {
                validateAndCalculatePageShifts(defaultPageSize, defaultAlignment)
            } catch (t: Throwable) {
                pageSizeFallbackCause = t
                defaultPageSize = 8192
                defaultAlignment = 0
            }
            DEFAULT_PAGE_SIZE = defaultPageSize
            DEFAULT_DIRECT_MEMORY_CACHE_ALIGNMENT = defaultAlignment

            var defaultMaxOrder = SystemPropertyUtil.getInt("io.netty.allocator.maxOrder", 9)
            var maxOrderFallbackCause: Throwable? = null
            try {
                validateAndCalculateChunkSize(DEFAULT_PAGE_SIZE, defaultMaxOrder)
            } catch (t: Throwable) {
                maxOrderFallbackCause = t
                defaultMaxOrder = 9
            }
            DEFAULT_MAX_ORDER = defaultMaxOrder

            // Determine reasonable default for nHeapArena and nDirectArena.
            // Assuming each arena has 3 chunks, the pool should not consume more than 50% of max memory.
            val runtime = Runtime.getRuntime()

            /*
             * We use 2 * available processors by default to reduce contention as we use 2 * available processors for the
             * number of EventLoops in NIO and EPOLL as well. If we choose a smaller number we will run into hot spots as
             * allocation and de-allocation needs to be synchronized on the PoolArena.
             *
             * See https://github.com/netty/netty/issues/3888.
             */
            val defaultMinNumArena = NettyRuntime.availableProcessors() * 2
            val defaultChunkSize = DEFAULT_PAGE_SIZE shl DEFAULT_MAX_ORDER
            DEFAULT_NUM_HEAP_ARENA = Math.max(
                0,
                SystemPropertyUtil.getInt(
                    "io.netty.allocator.numHeapArenas",
                    Math.min(
                        defaultMinNumArena.toLong(),
                        runtime.maxMemory() / defaultChunkSize / 2 / 3
                    ).toInt()
                )
            )
            DEFAULT_NUM_DIRECT_ARENA = Math.max(
                0,
                SystemPropertyUtil.getInt(
                    "io.netty.allocator.numDirectArenas",
                    Math.min(
                        defaultMinNumArena.toLong(),
                        PlatformDependent.maxDirectMemory() / defaultChunkSize / 2 / 3
                    ).toInt()
                )
            )

            // cache sizes
            DEFAULT_SMALL_CACHE_SIZE = SystemPropertyUtil.getInt("io.netty.allocator.smallCacheSize", 256)
            DEFAULT_NORMAL_CACHE_SIZE = SystemPropertyUtil.getInt("io.netty.allocator.normalCacheSize", 64)

            // 32 kb is the default maximum capacity of the cached buffer. Similar to what is explained in
            // 'Scalable memory allocation using jemalloc'
            DEFAULT_MAX_CACHED_BUFFER_CAPACITY = SystemPropertyUtil.getInt(
                "io.netty.allocator.maxCachedBufferCapacity", 32 * 1024
            )

            // the number of threshold of allocations when cached entries will be freed up if not frequently used
            DEFAULT_CACHE_TRIM_INTERVAL = SystemPropertyUtil.getInt(
                "io.netty.allocator.cacheTrimInterval", 8192
            )

            if (SystemPropertyUtil.contains("io.netty.allocation.cacheTrimIntervalMillis")) {
                logger.warn(
                    "-Dio.netty.allocation.cacheTrimIntervalMillis is deprecated," +
                        " use -Dio.netty.allocator.cacheTrimIntervalMillis"
                )

                DEFAULT_CACHE_TRIM_INTERVAL_MILLIS = if (SystemPropertyUtil.contains("io.netty.allocator.cacheTrimIntervalMillis")) {
                    // Both system properties are specified. Use the non-deprecated one.
                    SystemPropertyUtil.getLong("io.netty.allocator.cacheTrimIntervalMillis", 0)
                } else {
                    SystemPropertyUtil.getLong("io.netty.allocation.cacheTrimIntervalMillis", 0)
                }
            } else {
                DEFAULT_CACHE_TRIM_INTERVAL_MILLIS = SystemPropertyUtil.getLong(
                    "io.netty.allocator.cacheTrimIntervalMillis", 0
                )
            }

            DEFAULT_USE_CACHE_FOR_ALL_THREADS = SystemPropertyUtil.getBoolean(
                "io.netty.allocator.useCacheForAllThreads", false
            )

            DEFAULT_DISABLE_CACHE_FINALIZERS_FOR_FAST_THREAD_LOCAL_THREADS = SystemPropertyUtil.getBoolean(
                "io.netty.allocator.disableCacheFinalizersForFastThreadLocalThreads", false
            )

            // Use 1023 by default as we use an ArrayDeque as backing storage which will then allocate an internal array
            // of 1024 elements. Otherwise we would allocate 2048 and only use 1024 which is wasteful.
            DEFAULT_MAX_CACHED_BYTEBUFFERS_PER_CHUNK = SystemPropertyUtil.getInt(
                "io.netty.allocator.maxCachedByteBuffersPerChunk", 1023
            )

            if (logger.isDebugEnabled()) {
                logger.debug("-Dio.netty.allocator.numHeapArenas: {}", DEFAULT_NUM_HEAP_ARENA)
                logger.debug("-Dio.netty.allocator.numDirectArenas: {}", DEFAULT_NUM_DIRECT_ARENA)
                if (pageSizeFallbackCause == null) {
                    logger.debug("-Dio.netty.allocator.pageSize: {}", DEFAULT_PAGE_SIZE)
                } else {
                    logger.debug("-Dio.netty.allocator.pageSize: {}", DEFAULT_PAGE_SIZE, pageSizeFallbackCause)
                }
                if (maxOrderFallbackCause == null) {
                    logger.debug("-Dio.netty.allocator.maxOrder: {}", DEFAULT_MAX_ORDER)
                } else {
                    logger.debug("-Dio.netty.allocator.maxOrder: {}", DEFAULT_MAX_ORDER, maxOrderFallbackCause)
                }
                logger.debug("-Dio.netty.allocator.chunkSize: {}", DEFAULT_PAGE_SIZE shl DEFAULT_MAX_ORDER)
                logger.debug("-Dio.netty.allocator.smallCacheSize: {}", DEFAULT_SMALL_CACHE_SIZE)
                logger.debug("-Dio.netty.allocator.normalCacheSize: {}", DEFAULT_NORMAL_CACHE_SIZE)
                logger.debug("-Dio.netty.allocator.maxCachedBufferCapacity: {}", DEFAULT_MAX_CACHED_BUFFER_CAPACITY)
                logger.debug("-Dio.netty.allocator.cacheTrimInterval: {}", DEFAULT_CACHE_TRIM_INTERVAL)
                logger.debug("-Dio.netty.allocator.cacheTrimIntervalMillis: {}", DEFAULT_CACHE_TRIM_INTERVAL_MILLIS)
                logger.debug("-Dio.netty.allocator.useCacheForAllThreads: {}", DEFAULT_USE_CACHE_FOR_ALL_THREADS)
                logger.debug(
                    "-Dio.netty.allocator.maxCachedByteBuffersPerChunk: {}",
                    DEFAULT_MAX_CACHED_BYTEBUFFERS_PER_CHUNK
                )
                logger.debug(
                    "-Dio.netty.allocator.disableCacheFinalizersForFastThreadLocalThreads: {}",
                    DEFAULT_DISABLE_CACHE_FINALIZERS_FOR_FAST_THREAD_LOCAL_THREADS
                )
            }
        }

        @JvmField
        val DEFAULT: PooledByteBufAllocator =
            PooledByteBufAllocator(!PlatformDependent.isExplicitNoPreferDirect())

        @Suppress("UNCHECKED_CAST")
        private fun <T> newArenaArray(size: Int): Array<PoolArena<T>> =
            arrayOfNulls<PoolArena<*>>(size) as Array<PoolArena<T>>

        private fun validateAndCalculatePageShifts(pageSize: Int, alignment: Int): Int {
            if (pageSize < MIN_PAGE_SIZE) {
                throw IllegalArgumentException("pageSize: $pageSize (expected: $MIN_PAGE_SIZE)")
            }

            if (pageSize and (pageSize - 1) != 0) {
                throw IllegalArgumentException("pageSize: $pageSize (expected: power of 2)")
            }

            if (pageSize < alignment) {
                throw IllegalArgumentException(
                    "Alignment cannot be greater than page size. Alignment: $alignment, page size: $pageSize."
                )
            }

            // Logarithm base 2. At this point we know that pageSize is a power of two.
            return Integer.SIZE - 1 - Integer.numberOfLeadingZeros(pageSize)
        }

        private fun validateAndCalculateChunkSize(pageSize: Int, maxOrder: Int): Int {
            if (maxOrder > 14) {
                throw IllegalArgumentException("maxOrder: $maxOrder (expected: 0-14)")
            }

            // Ensure the resulting chunkSize does not overflow.
            var chunkSize = pageSize
            for (i in maxOrder downTo 1) {
                if (chunkSize > MAX_CHUNK_SIZE / 2) {
                    throw IllegalArgumentException(
                        "pageSize ($pageSize) << maxOrder ($maxOrder) must not exceed $MAX_CHUNK_SIZE"
                    )
                }
                chunkSize = chunkSize shl 1
            }
            return chunkSize
        }

        /**
         * Default number of heap arenas - System Property: io.netty.allocator.numHeapArenas - default 2 * cores
         */
        @JvmStatic
        fun defaultNumHeapArena(): Int = DEFAULT_NUM_HEAP_ARENA

        /**
         * Default number of direct arenas - System Property: io.netty.allocator.numDirectArenas - default 2 * cores
         */
        @JvmStatic
        fun defaultNumDirectArena(): Int = DEFAULT_NUM_DIRECT_ARENA

        /**
         * Default buffer page size - System Property: io.netty.allocator.pageSize - default 8192
         */
        @JvmStatic
        fun defaultPageSize(): Int = DEFAULT_PAGE_SIZE

        /**
         * Default maximum order - System Property: io.netty.allocator.maxOrder - default 9
         */
        @JvmStatic
        fun defaultMaxOrder(): Int = DEFAULT_MAX_ORDER

        /**
         * Default control creation of PoolThreadCache finalizers for FastThreadLocalThreads -
         * System Property: io.netty.allocator.disableCacheFinalizersForFastThreadLocalThreads - default false
         */
        @JvmStatic
        fun defaultDisableCacheFinalizersForFastThreadLocalThreads(): Boolean =
            DEFAULT_DISABLE_CACHE_FINALIZERS_FOR_FAST_THREAD_LOCAL_THREADS

        /**
         * Default thread caching behavior - System Property: io.netty.allocator.useCacheForAllThreads - default false
         */
        @JvmStatic
        fun defaultUseCacheForAllThreads(): Boolean = DEFAULT_USE_CACHE_FOR_ALL_THREADS

        /**
         * Default prefer direct - System Property: io.netty.noPreferDirect - default false
         */
        @JvmStatic
        fun defaultPreferDirect(): Boolean = PlatformDependent.directBufferPreferred()

        /**
         * Default tiny cache size - default 0
         *
         * @deprecated Tiny caches have been merged into small caches.
         */
        @JvmStatic
        @Deprecated("Tiny caches have been merged into small caches.")
        fun defaultTinyCacheSize(): Int = 0

        /**
         * Default small cache size - System Property: io.netty.allocator.smallCacheSize - default 256
         */
        @JvmStatic
        fun defaultSmallCacheSize(): Int = DEFAULT_SMALL_CACHE_SIZE

        /**
         * Default normal cache size - System Property: io.netty.allocator.normalCacheSize - default 64
         */
        @JvmStatic
        fun defaultNormalCacheSize(): Int = DEFAULT_NORMAL_CACHE_SIZE

        /**
         * Return `true` if direct memory cache alignment is supported, `false` otherwise.
         */
        @JvmStatic
        fun isDirectMemoryCacheAlignmentSupported(): Boolean = PlatformDependent.hasUnsafe()

        private fun useCacheFinalizers(): Boolean {
            if (!defaultDisableCacheFinalizersForFastThreadLocalThreads()) {
                return true
            }
            return FastThreadLocalThread.currentThreadWillCleanupFastThreadLocals()
        }

        private fun numThreadLocalCaches(arenas: Array<PoolArena<*>>?): Int {
            if (arenas == null) {
                return 0
            }
            var total = 0
            for (arena in arenas) {
                total += arena.numThreadCaches.get()
            }
            return total
        }

        private fun usedMemory(arenas: Array<PoolArena<*>>?): Long {
            if (arenas == null) {
                return -1
            }
            var used = 0L
            for (arena in arenas) {
                used += arena.numActiveBytes()
                if (used < 0) {
                    return Long.MAX_VALUE
                }
            }
            return used
        }

        private fun pinnedMemory(arenas: Array<PoolArena<*>>?): Long {
            if (arenas == null) {
                return -1
            }
            var used = 0L
            for (arena in arenas) {
                used += arena.numPinnedBytes()
                if (used < 0) {
                    return Long.MAX_VALUE
                }
            }
            return used
        }

        @JvmStatic
        internal fun onAllocateBuffer(buf: AbstractByteBuf, pooled: Boolean, threadLocal: Boolean) {
            if (PlatformDependent.isJfrEnabled() && AllocateBufferEvent.isEventEnabled()) {
                val event = AllocateBufferEvent()
                if (event.shouldCommit()) {
                    event.fill(buf, PooledByteBufAllocator::class.java)
                    event.chunkPooled = pooled
                    event.chunkThreadLocal = threadLocal
                    event.commit()
                }
            }
        }

        @JvmStatic
        internal fun onDeallocateBuffer(buf: AbstractByteBuf) {
            if (PlatformDependent.isJfrEnabled() && FreeBufferEvent.isEventEnabled()) {
                val event = FreeBufferEvent()
                if (event.shouldCommit()) {
                    event.fill(buf, PooledByteBufAllocator::class.java)
                    event.commit()
                }
            }
        }

        @JvmStatic
        internal fun onReallocateBuffer(buf: AbstractByteBuf, newCapacity: Int) {
            if (PlatformDependent.isJfrEnabled() && ReallocateBufferEvent.isEventEnabled()) {
                val event = ReallocateBufferEvent()
                if (event.shouldCommit()) {
                    event.fill(buf, PooledByteBufAllocator::class.java)
                    event.newCapacity = newCapacity
                    event.commit()
                }
            }
        }

        @JvmStatic
        internal fun onAllocateChunk(chunk: ChunkInfo, pooled: Boolean) {
            if (PlatformDependent.isJfrEnabled() && AllocateChunkEvent.isEventEnabled()) {
                val event = AllocateChunkEvent()
                if (event.shouldCommit()) {
                    event.fill(chunk, PooledByteBufAllocator::class.java)
                    event.pooled = pooled
                    event.threadLocal = false // Chunks in the pooled allocator are always shared.
                    event.commit()
                }
            }
        }

        @JvmStatic
        internal fun onDeallocateChunk(chunk: ChunkInfo, pooled: Boolean) {
            if (PlatformDependent.isJfrEnabled() && FreeChunkEvent.isEventEnabled()) {
                val event = FreeChunkEvent()
                if (event.shouldCommit()) {
                    event.fill(chunk, PooledByteBufAllocator::class.java)
                    event.pooled = pooled
                    event.commit()
                }
            }
        }
    }
}
