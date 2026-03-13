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
import io.netty.util.internal.LongLongHashMap
import io.netty.util.internal.SystemPropertyUtil
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.concurrent.atomic.LongAdder
import java.util.concurrent.locks.ReentrantLock

internal class PoolChunk<T> : PoolChunkMetric, ChunkInfo {

    @JvmField
    val arena: PoolArena<T>
    @JvmField
    val cleanable: CleanableDirectBuffer?
    @JvmField
    val base: Any?
    @JvmField
    val memory: T
    @JvmField
    val unpooled: Boolean

    /**
     * store the first page and last page of each avail run
     */
    private val runsAvailMap: LongLongHashMap?

    /**
     * manage all avail runs
     */
    private val runsAvail: Array<IntPriorityQueue>?

    private val runsAvailLock: ReentrantLock?

    /**
     * manage all subpages in this chunk
     */
    private val subpages: Array<PoolSubpage<T>?>?

    /**
     * Accounting of pinned memory -- memory that is currently in use by ByteBuf instances.
     */
    private val pinnedBytes: LongAdder?

    @JvmField
    val pageSize: Int
    @JvmField
    val pageShifts: Int
    @JvmField
    val chunkSize: Int
    @JvmField
    val maxPageIdx: Int

    // Use as cache for ByteBuffer created from the memory. These are just duplicates and so are only a container
    // around the memory itself. These are often needed for operations within the Pooled*ByteBuf and so
    // may produce extra GC, which can be greatly reduced by caching the duplicates.
    //
    // This may be null if the PoolChunk is unpooled as pooling the ByteBuffer instances does not make any sense here.
    private val cachedNioBuffers: ArrayDeque<ByteBuffer>?

    @JvmField
    var freeBytes: Int = 0

    @JvmField
    var parent: PoolChunkList<T>? = null
    @JvmField
    var prev: PoolChunk<T>? = null
    @JvmField
    var next: PoolChunk<T>? = null

    @Suppress("UNCHECKED_CAST")
    constructor(
        arena: PoolArena<T>, cleanable: CleanableDirectBuffer?, base: Any?, memory: T & Any, pageSize: Int,
        pageShifts: Int, chunkSize: Int, maxPageIdx: Int
    ) {
        unpooled = false
        this.arena = arena
        this.cleanable = cleanable
        this.base = base
        this.memory = memory
        this.pageSize = pageSize
        this.pageShifts = pageShifts
        this.chunkSize = chunkSize
        this.maxPageIdx = maxPageIdx
        freeBytes = chunkSize

        runsAvail = newRunsAvailqueueArray(maxPageIdx)
        runsAvailLock = ReentrantLock()
        runsAvailMap = LongLongHashMap(-1)
        subpages = arrayOfNulls<PoolSubpage<*>>(chunkSize shr pageShifts) as Array<PoolSubpage<T>?>

        // insert initial run, offset = 0, pages = chunkSize / pageSize
        val pages = chunkSize shr pageShifts
        val initHandle = pages.toLong() shl SIZE_SHIFT
        insertAvailRun(0, pages, initHandle)

        cachedNioBuffers = ArrayDeque(8)
        this.pinnedBytes = if (trackPinnedMemory) LongAdder() else null
    }

    /** Creates a special chunk that is not pooled. */
    constructor(arena: PoolArena<T>, cleanable: CleanableDirectBuffer?, base: Any?, memory: T & Any, size: Int) {
        unpooled = true
        this.arena = arena
        this.cleanable = cleanable
        this.base = base
        this.memory = memory
        pageSize = 0
        pageShifts = 0
        maxPageIdx = 0
        runsAvailMap = null
        runsAvail = null
        runsAvailLock = null
        subpages = null
        chunkSize = size
        cachedNioBuffers = null
        this.pinnedBytes = if (trackPinnedMemory) LongAdder() else null
    }

    private fun insertAvailRun(runOffset: Int, pages: Int, handle: Long) {
        val pageIdxFloor = arena.sizeClass.pages2pageIdxFloor(pages)
        val queue = runsAvail!![pageIdxFloor]
        assert(isRun(handle))
        queue.offer((handle shr BITMAP_IDX_BIT_LENGTH).toInt())

        // insert first page of run
        insertAvailRun0(runOffset, handle)
        if (pages > 1) {
            // insert last page of run
            insertAvailRun0(lastPage(runOffset, pages), handle)
        }
    }

    private fun insertAvailRun0(runOffset: Int, handle: Long) {
        val pre = runsAvailMap!!.put(runOffset.toLong(), handle)
        assert(pre == -1L)
    }

    private fun removeAvailRun(handle: Long) {
        val pageIdxFloor = arena.sizeClass.pages2pageIdxFloor(runPages(handle))
        runsAvail!![pageIdxFloor].remove((handle shr BITMAP_IDX_BIT_LENGTH).toInt())
        removeAvailRun0(handle)
    }

    private fun removeAvailRun0(handle: Long) {
        val runOffset = runOffset(handle)
        val pages = runPages(handle)
        // remove first page of run
        runsAvailMap!!.remove(runOffset.toLong())
        if (pages > 1) {
            // remove last page of run
            runsAvailMap.remove(lastPage(runOffset, pages).toLong())
        }
    }

    private fun getAvailRunByOffset(runOffset: Int): Long {
        return runsAvailMap!!.get(runOffset.toLong())
    }

    override fun usage(): Int {
        val freeBytes: Int
        if (this.unpooled) {
            freeBytes = this.freeBytes
        } else {
            runsAvailLock!!.lock()
            try {
                freeBytes = this.freeBytes
            } finally {
                runsAvailLock.unlock()
            }
        }
        return usage(freeBytes)
    }

    private fun usage(freeBytes: Int): Int {
        if (freeBytes == 0) {
            return 100
        }

        val freePercentage = (freeBytes * 100L / chunkSize).toInt()
        if (freePercentage == 0) {
            return 99
        }
        return 100 - freePercentage
    }

    fun allocate(buf: PooledByteBuf<T>, reqCapacity: Int, sizeIdx: Int, cache: PoolThreadCache): Boolean {
        val handle: Long
        if (sizeIdx <= arena.sizeClass.smallMaxSizeIdx) {
            val nextSub: PoolSubpage<T>
            // small
            // Obtain the head of the PoolSubPage pool that is owned by the PoolArena and synchronize on it.
            // This is need as we may add it back and so alter the linked-list structure.
            val head = arena.smallSubpagePools[sizeIdx]
            head.lock()
            try {
                nextSub = head.next as PoolSubpage<T>
                if (nextSub !== head) {
                    assert(
                        nextSub.doNotDestroy && nextSub.elemSize == arena.sizeClass.sizeIdx2size(sizeIdx)
                    ) {
                        "doNotDestroy=${nextSub.doNotDestroy}, elemSize=${nextSub.elemSize}, sizeIdx=$sizeIdx"
                    }
                    handle = nextSub.allocate()
                    assert(handle >= 0)
                    assert(isSubpage(handle))
                    nextSub.chunk!!.initBufWithSubpage(buf, null, handle, reqCapacity, cache, false)
                    return true
                }
                handle = allocateSubpage(sizeIdx, head)
                if (handle < 0) {
                    return false
                }
                assert(isSubpage(handle))
            } finally {
                head.unlock()
            }
        } else {
            // normal
            // runSize must be multiple of pageSize
            val runSize = arena.sizeClass.sizeIdx2size(sizeIdx)
            handle = allocateRun(runSize)
            if (handle < 0) {
                return false
            }
            assert(!isSubpage(handle))
        }

        val nioBuffer = cachedNioBuffers?.pollLast()
        initBuf(buf, nioBuffer, handle, reqCapacity, cache, false)
        return true
    }

    private fun allocateRun(runSize: Int): Long {
        val pages = runSize shr pageShifts
        val pageIdx = arena.sizeClass.pages2pageIdx(pages)

        runsAvailLock!!.lock()
        try {
            // find first queue which has at least one big enough run
            val queueIdx = runFirstBestFit(pageIdx)
            if (queueIdx == -1) {
                return -1
            }

            // get run with min offset in this queue
            val queue = runsAvail!![queueIdx]
            var handle = queue.poll().toLong()
            assert(handle != IntPriorityQueue.NO_VALUE.toLong())
            handle = handle shl BITMAP_IDX_BIT_LENGTH
            assert(!isUsed(handle)) { "invalid handle: $handle" }

            removeAvailRun0(handle)

            handle = splitLargeRun(handle, pages)

            val pinnedSize = runSize(pageShifts, handle)
            freeBytes -= pinnedSize
            return handle
        } finally {
            runsAvailLock.unlock()
        }
    }

    private fun calculateRunSize(sizeIdx: Int): Int {
        val maxElements = 1 shl (pageShifts - SizeClasses.LOG2_QUANTUM)
        var runSize = 0
        var nElements: Int

        val elemSize = arena.sizeClass.sizeIdx2size(sizeIdx)

        // find lowest common multiple of pageSize and elemSize
        do {
            runSize += pageSize
            nElements = runSize / elemSize
        } while (nElements < maxElements && runSize != nElements * elemSize)

        while (nElements > maxElements) {
            runSize -= pageSize
            nElements = runSize / elemSize
        }

        assert(nElements > 0)
        assert(runSize <= chunkSize)
        assert(runSize >= elemSize)

        return runSize
    }

    private fun runFirstBestFit(pageIdx: Int): Int {
        if (freeBytes == chunkSize) {
            return arena.sizeClass.nPSizes - 1
        }
        for (i in pageIdx until arena.sizeClass.nPSizes) {
            val queue = runsAvail!![i]
            if (!queue.isEmpty) {
                return i
            }
        }
        return -1
    }

    private fun splitLargeRun(handle: Long, needPages: Int): Long {
        assert(needPages > 0)

        val totalPages = runPages(handle)
        assert(needPages <= totalPages)

        val remPages = totalPages - needPages

        if (remPages > 0) {
            val runOffset = runOffset(handle)

            // keep track of trailing unused pages for later use
            val availOffset = runOffset + needPages
            val availRun = toRunHandle(availOffset, remPages, 0)
            insertAvailRun(availOffset, remPages, availRun)

            // not avail
            return toRunHandle(runOffset, needPages, 1)
        }

        // mark it as used
        return handle or (1L shl IS_USED_SHIFT)
    }

    /**
     * Create / initialize a new PoolSubpage of normCapacity. Any PoolSubpage created / initialized here is added to
     * subpage pool in the PoolArena that owns this PoolChunk.
     *
     * @param sizeIdx sizeIdx of normalized size
     * @param head head of subpages
     *
     * @return index in memoryMap
     */
    private fun allocateSubpage(sizeIdx: Int, head: PoolSubpage<T>): Long {
        // allocate a new run
        val runSize = calculateRunSize(sizeIdx)
        // runSize must be multiples of pageSize
        val runHandle = allocateRun(runSize)
        if (runHandle < 0) {
            return -1
        }

        val runOffset = runOffset(runHandle)
        assert(subpages!![runOffset] == null)
        val elemSize = arena.sizeClass.sizeIdx2size(sizeIdx)

        val subpage = PoolSubpage(
            head, this, pageShifts, runOffset,
            runSize(pageShifts, runHandle), elemSize
        )

        subpages[runOffset] = subpage
        return subpage.allocate()
    }

    /**
     * Free a subpage or a run of pages When a subpage is freed from PoolSubpage, it might be added back to subpage pool
     * of the owning PoolArena. If the subpage pool in PoolArena has at least one other PoolSubpage of given elemSize,
     * we can completely free the owning Page so it is available for subsequent allocations
     *
     * @param handle handle to free
     */
    fun free(handle: Long, normCapacity: Int, nioBuffer: ByteBuffer?) {
        if (isSubpage(handle)) {
            val sIdx = runOffset(handle)
            val subpage = subpages!![sIdx]!!
            val head = subpage.chunk!!.arena.smallSubpagePools[subpage.headIndex]
            // Obtain the head of the PoolSubPage pool that is owned by the PoolArena and synchronize on it.
            // This is need as we may add it back and so alter the linked-list structure.
            head.lock()
            try {
                assert(subpage.doNotDestroy)
                if (subpage.free(head, bitmapIdx(handle))) {
                    // the subpage is still used, do not free it
                    return
                }
                assert(!subpage.doNotDestroy)
                // Null out slot in the array as it was freed and we should not use it anymore.
                subpages[sIdx] = null
            } finally {
                head.unlock()
            }
        }

        val runSize = runSize(pageShifts, handle)
        // start free run
        runsAvailLock!!.lock()
        try {
            // collapse continuous runs, successfully collapsed runs
            // will be removed from runsAvail and runsAvailMap
            var finalRun = collapseRuns(handle)

            // set run as not used
            finalRun = finalRun and (1L shl IS_USED_SHIFT).inv()
            // if it is a subpage, set it to run
            finalRun = finalRun and (1L shl IS_SUBPAGE_SHIFT).inv()

            insertAvailRun(runOffset(finalRun), runPages(finalRun), finalRun)
            freeBytes += runSize
        } finally {
            runsAvailLock.unlock()
        }

        if (nioBuffer != null && cachedNioBuffers != null &&
            cachedNioBuffers.size < PooledByteBufAllocator.DEFAULT_MAX_CACHED_BYTEBUFFERS_PER_CHUNK
        ) {
            cachedNioBuffers.offer(nioBuffer)
        }
    }

    private fun collapseRuns(handle: Long): Long {
        return collapseNext(collapsePast(handle))
    }

    private fun collapsePast(handle: Long): Long {
        var handle = handle
        while (true) {
            val runOffset = runOffset(handle)
            val runPages = runPages(handle)

            val pastRun = getAvailRunByOffset(runOffset - 1)
            if (pastRun == -1L) {
                return handle
            }

            val pastOffset = runOffset(pastRun)
            val pastPages = runPages(pastRun)

            // is continuous
            if (pastRun != handle && pastOffset + pastPages == runOffset) {
                // remove past run
                removeAvailRun(pastRun)
                handle = toRunHandle(pastOffset, pastPages + runPages, 0)
            } else {
                return handle
            }
        }
    }

    private fun collapseNext(handle: Long): Long {
        var handle = handle
        while (true) {
            val runOffset = runOffset(handle)
            val runPages = runPages(handle)

            val nextRun = getAvailRunByOffset(runOffset + runPages)
            if (nextRun == -1L) {
                return handle
            }

            val nextOffset = runOffset(nextRun)
            val nextPages = runPages(nextRun)

            // is continuous
            if (nextRun != handle && runOffset + runPages == nextOffset) {
                // remove next run
                removeAvailRun(nextRun)
                handle = toRunHandle(runOffset, runPages + nextPages, 0)
            } else {
                return handle
            }
        }
    }

    fun initBuf(
        buf: PooledByteBuf<T>, nioBuffer: ByteBuffer?, handle: Long, reqCapacity: Int,
        threadCache: PoolThreadCache, threadLocal: Boolean
    ) {
        if (isSubpage(handle)) {
            initBufWithSubpage(buf, nioBuffer, handle, reqCapacity, threadCache, threadLocal)
        } else {
            val maxLength = runSize(pageShifts, handle)
            buf.init(
                this, nioBuffer, handle, runOffset(handle) shl pageShifts,
                reqCapacity, maxLength, arena.parent!!.threadCache(), threadLocal
            )
        }
    }

    fun initBufWithSubpage(
        buf: PooledByteBuf<T>, nioBuffer: ByteBuffer?, handle: Long, reqCapacity: Int,
        threadCache: PoolThreadCache, threadLocal: Boolean
    ) {
        val runOffset = runOffset(handle)
        val bitmapIdx = bitmapIdx(handle)

        val s = subpages!![runOffset]!!
        assert(s.isDoNotDestroy())
        assert(reqCapacity <= s.elemSize) { "$reqCapacity<=${s.elemSize}" }

        val offset = (runOffset shl pageShifts) + bitmapIdx * s.elemSize
        buf.init(this, nioBuffer, handle, offset, reqCapacity, s.elemSize, threadCache, threadLocal)
    }

    fun incrementPinnedMemory(delta: Int) {
        assert(delta > 0)
        pinnedBytes?.add(delta.toLong())
    }

    fun decrementPinnedMemory(delta: Int) {
        assert(delta > 0)
        pinnedBytes?.add(-delta.toLong())
    }

    override fun chunkSize(): Int = chunkSize

    override fun freeBytes(): Int {
        if (this.unpooled) {
            return freeBytes
        }
        runsAvailLock!!.lock()
        try {
            return freeBytes
        } finally {
            runsAvailLock.unlock()
        }
    }

    fun pinnedBytes(): Int = pinnedBytes?.sum()?.toInt() ?: 0

    override fun toString(): String {
        val freeBytes: Int
        if (this.unpooled) {
            freeBytes = this.freeBytes
        } else {
            runsAvailLock!!.lock()
            try {
                freeBytes = this.freeBytes
            } finally {
                runsAvailLock.unlock()
            }
        }

        return "Chunk(${Integer.toHexString(System.identityHashCode(this))}: ${usage(freeBytes)}%, ${chunkSize - freeBytes}/$chunkSize)"
    }

    fun destroy() {
        arena.destroyChunk(this)
    }

    override fun capacity(): Int = chunkSize

    override fun isDirect(): Boolean = cleanable != null && cleanable.buffer().isDirect

    override fun memoryAddress(): Long =
        if (cleanable != null && cleanable.hasMemoryAddress()) cleanable.memoryAddress() else 0L

    companion object {
        private const val SIZE_BIT_LENGTH = 15
        private const val INUSED_BIT_LENGTH = 1
        private const val SUBPAGE_BIT_LENGTH = 1
        private const val BITMAP_IDX_BIT_LENGTH = 32

        @JvmStatic
        private val trackPinnedMemory: Boolean =
            SystemPropertyUtil.getBoolean("io.netty.trackPinnedMemory", true)

        @JvmField
        val IS_SUBPAGE_SHIFT: Int = BITMAP_IDX_BIT_LENGTH
        @JvmField
        val IS_USED_SHIFT: Int = SUBPAGE_BIT_LENGTH + IS_SUBPAGE_SHIFT
        @JvmField
        val SIZE_SHIFT: Int = INUSED_BIT_LENGTH + IS_USED_SHIFT
        @JvmField
        val RUN_OFFSET_SHIFT: Int = SIZE_BIT_LENGTH + SIZE_SHIFT

        @JvmStatic
        private fun newRunsAvailqueueArray(size: Int): Array<IntPriorityQueue> {
            return Array(size) { IntPriorityQueue() }
        }

        @JvmStatic
        private fun lastPage(runOffset: Int, pages: Int): Int = runOffset + pages - 1

        @JvmStatic
        private fun toRunHandle(runOffset: Int, runPages: Int, inUsed: Int): Long {
            return (runOffset.toLong() shl RUN_OFFSET_SHIFT
                or (runPages.toLong() shl SIZE_SHIFT)
                or (inUsed.toLong() shl IS_USED_SHIFT))
        }

        @JvmStatic
        fun runOffset(handle: Long): Int = (handle shr RUN_OFFSET_SHIFT).toInt()

        @JvmStatic
        fun runSize(pageShifts: Int, handle: Long): Int = runPages(handle) shl pageShifts

        @JvmStatic
        fun runPages(handle: Long): Int = (handle shr SIZE_SHIFT and 0x7fff).toInt()

        @JvmStatic
        fun isUsed(handle: Long): Boolean = (handle shr IS_USED_SHIFT and 1) == 1L

        @JvmStatic
        fun isRun(handle: Long): Boolean = !isSubpage(handle)

        @JvmStatic
        fun isSubpage(handle: Long): Boolean = (handle shr IS_SUBPAGE_SHIFT and 1) == 1L

        @JvmStatic
        fun bitmapIdx(handle: Long): Int = handle.toInt()
    }
}
