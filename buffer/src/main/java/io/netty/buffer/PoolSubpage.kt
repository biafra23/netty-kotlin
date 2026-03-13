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

import java.util.concurrent.locks.ReentrantLock

internal class PoolSubpage<T> : PoolSubpageMetric {

    @JvmField
    val chunk: PoolChunk<T>?
    @JvmField
    val elemSize: Int
    private val pageShifts: Int
    private val runOffset: Int
    private val runSize: Int
    private val bitmap: LongArray?
    private val bitmapLength: Int
    private val maxNumElems: Int
    @JvmField
    val headIndex: Int

    @JvmField
    var prev: PoolSubpage<T>? = null
    @JvmField
    var next: PoolSubpage<T>? = null

    @JvmField
    var doNotDestroy: Boolean = false
    private var nextAvail: Int = 0
    private var numAvail: Int = 0

    @JvmField
    val lock: ReentrantLock?

    /** Special constructor that creates a linked list head */
    constructor(headIndex: Int) {
        chunk = null
        lock = ReentrantLock()
        pageShifts = -1
        runOffset = -1
        elemSize = -1
        runSize = -1
        bitmap = null
        bitmapLength = -1
        maxNumElems = 0
        this.headIndex = headIndex
    }

    constructor(head: PoolSubpage<T>, chunk: PoolChunk<T>, pageShifts: Int, runOffset: Int, runSize: Int, elemSize: Int) {
        this.headIndex = head.headIndex
        this.chunk = chunk
        this.pageShifts = pageShifts
        this.runOffset = runOffset
        this.runSize = runSize
        this.elemSize = elemSize

        doNotDestroy = true

        maxNumElems = runSize / elemSize
        numAvail = maxNumElems
        var bitmapLength = maxNumElems ushr 6
        if (maxNumElems and 63 != 0) {
            bitmapLength++
        }
        this.bitmapLength = bitmapLength
        bitmap = LongArray(bitmapLength)
        nextAvail = 0

        lock = null
        addToPool(head)
    }

    /**
     * Returns the bitmap index of the subpage allocation.
     */
    fun allocate(): Long {
        if (numAvail == 0 || !doNotDestroy) {
            return -1
        }

        val bitmapIdx = getNextAvail()
        if (bitmapIdx < 0) {
            removeFromPool() // Subpage appear to be in an invalid state. Remove to prevent repeated errors.
            throw AssertionError(
                "No next available bitmap index found (bitmapIdx = $bitmapIdx), " +
                "even though there are supposed to be (numAvail = $numAvail) " +
                "out of (maxNumElems = $maxNumElems) available indexes."
            )
        }
        val q = bitmapIdx ushr 6
        val r = bitmapIdx and 63
        assert(bitmap!![q] ushr r and 1L == 0L)
        bitmap[q] = bitmap[q] or (1L shl r)

        if (--numAvail == 0) {
            removeFromPool()
        }

        return toHandle(bitmapIdx)
    }

    /**
     * @return `true` if this subpage is in use.
     *         `false` if this subpage is not used by its chunk and thus it's OK to be released.
     */
    fun free(head: PoolSubpage<T>, bitmapIdx: Int): Boolean {
        val q = bitmapIdx ushr 6
        val r = bitmapIdx and 63
        assert(bitmap!![q] ushr r and 1L != 0L)
        bitmap[q] = bitmap[q] xor (1L shl r)

        setNextAvail(bitmapIdx)

        if (numAvail++ == 0) {
            addToPool(head)
            /* When maxNumElems == 1, the maximum numAvail is also 1.
             * Each of these PoolSubpages will go in here when they do free operation.
             * If they return true directly from here, then the rest of the code will be unreachable
             * and they will not actually be recycled. So return true only on maxNumElems > 1. */
            if (maxNumElems > 1) {
                return true
            }
        }

        if (numAvail != maxNumElems) {
            return true
        } else {
            // Subpage not in use (numAvail == maxNumElems)
            if (prev === next) {
                // Do not remove if this subpage is the only one left in the pool.
                return true
            }

            // Remove this subpage from the pool if there are other subpages left in the pool.
            doNotDestroy = false
            removeFromPool()
            return false
        }
    }

    private fun addToPool(head: PoolSubpage<T>) {
        assert(prev == null && next == null)
        prev = head
        next = head.next
        next!!.prev = this
        head.next = this
    }

    private fun removeFromPool() {
        assert(prev != null && next != null)
        prev!!.next = next
        next!!.prev = prev
        next = null
        prev = null
    }

    private fun setNextAvail(bitmapIdx: Int) {
        nextAvail = bitmapIdx
    }

    private fun getNextAvail(): Int {
        val nextAvail = this.nextAvail
        if (nextAvail >= 0) {
            this.nextAvail = -1
            return nextAvail
        }
        return findNextAvail()
    }

    private fun findNextAvail(): Int {
        for (i in 0 until bitmapLength) {
            val bits = bitmap!![i]
            if (bits.inv() != 0L) {
                return findNextAvail0(i, bits)
            }
        }
        return -1
    }

    private fun findNextAvail0(i: Int, bits: Long): Int {
        var bits = bits
        val baseVal = i shl 6
        for (j in 0 until 64) {
            if (bits and 1L == 0L) {
                val `val` = baseVal or j
                return if (`val` < maxNumElems) {
                    `val`
                } else {
                    break
                }
            }
            bits = bits ushr 1
        }
        return -1
    }

    private fun toHandle(bitmapIdx: Int): Long {
        val pages = runSize shr pageShifts
        return (runOffset.toLong() shl PoolChunk.RUN_OFFSET_SHIFT
            or (pages.toLong() shl PoolChunk.SIZE_SHIFT)
            or (1L shl PoolChunk.IS_USED_SHIFT)
            or (1L shl PoolChunk.IS_SUBPAGE_SHIFT)
            or bitmapIdx.toLong())
    }

    override fun toString(): String {
        val numAvail: Int
        if (chunk == null) {
            // This is the head so there is no need to synchronize at all as these never change.
            numAvail = 0
        } else {
            val doNotDestroy: Boolean
            val head = chunk.arena.smallSubpagePools[headIndex]
            head.lock()
            try {
                doNotDestroy = this.doNotDestroy
                numAvail = this.numAvail
            } finally {
                head.unlock()
            }
            if (!doNotDestroy) {
                return "($runOffset: not in use)"
            }
        }

        return "($runOffset: ${maxNumElems - numAvail}/$maxNumElems, offset: $runOffset, length: $runSize, elemSize: $elemSize)"
    }

    override fun maxNumElements(): Int = maxNumElems

    override fun numAvailable(): Int {
        if (chunk == null) {
            // It's the head.
            return 0
        }
        val head = chunk.arena.smallSubpagePools[headIndex]
        head.lock()
        try {
            return numAvail
        } finally {
            head.unlock()
        }
    }

    override fun elementSize(): Int = elemSize

    override fun pageSize(): Int = 1 shl pageShifts

    fun isDoNotDestroy(): Boolean {
        if (chunk == null) {
            // It's the head.
            return true
        }
        val head = chunk.arena.smallSubpagePools[headIndex]
        head.lock()
        try {
            return doNotDestroy
        } finally {
            head.unlock()
        }
    }

    fun destroy() {
        chunk?.destroy()
    }

    fun lock() {
        lock!!.lock()
    }

    fun unlock() {
        lock!!.unlock()
    }
}
