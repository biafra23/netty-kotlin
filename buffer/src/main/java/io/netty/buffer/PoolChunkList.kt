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

import io.netty.util.internal.StringUtil
import java.nio.ByteBuffer
import java.util.Collections
import kotlin.math.max
import kotlin.math.min

internal class PoolChunkList<T>(
    private val arena: PoolArena<T>,
    private val nextList: PoolChunkList<T>?,
    private val minUsage: Int,
    private val maxUsage: Int,
    chunkSize: Int
) : PoolChunkListMetric {

    private val maxCapacity: Int
    private var head: PoolChunk<T>? = null
    private val freeMinThreshold: Int
    private val freeMaxThreshold: Int

    // This is only update once when create the linked like list of PoolChunkList in PoolArena constructor.
    private var prevList: PoolChunkList<T>? = null

    init {
        assert(minUsage <= maxUsage)
        maxCapacity = calculateMaxCapacity(minUsage, chunkSize)

        freeMinThreshold = if (maxUsage == 100) 0 else (chunkSize * (100.0 - maxUsage + 0.99999999) / 100L).toInt()
        freeMaxThreshold = if (minUsage == 100) 0 else (chunkSize * (100.0 - minUsage + 0.99999999) / 100L).toInt()
    }

    fun prevList(prevList: PoolChunkList<T>?) {
        assert(this.prevList == null)
        this.prevList = prevList
    }

    fun allocate(buf: PooledByteBuf<T>, reqCapacity: Int, sizeIdx: Int, threadCache: PoolThreadCache): Boolean {
        val normCapacity = arena.sizeClass.sizeIdx2size(sizeIdx)
        if (normCapacity > maxCapacity) {
            // Either this PoolChunkList is empty or the requested capacity is larger then the capacity which can
            // be handled by the PoolChunks that are contained in this PoolChunkList.
            return false
        }

        var cur = head
        while (cur != null) {
            if (cur.allocate(buf, reqCapacity, sizeIdx, threadCache)) {
                if (cur.freeBytes <= freeMinThreshold) {
                    remove(cur)
                    nextList!!.add(cur)
                }
                return true
            }
            cur = cur.next
        }
        return false
    }

    fun free(chunk: PoolChunk<T>, handle: Long, normCapacity: Int, nioBuffer: ByteBuffer?): Boolean {
        chunk.free(handle, normCapacity, nioBuffer)
        if (chunk.freeBytes > freeMaxThreshold) {
            remove(chunk)
            // Move the PoolChunk down the PoolChunkList linked-list.
            return move0(chunk)
        }
        return true
    }

    private fun move(chunk: PoolChunk<T>): Boolean {
        assert(chunk.usage() < maxUsage)

        if (chunk.freeBytes > freeMaxThreshold) {
            // Move the PoolChunk down the PoolChunkList linked-list.
            return move0(chunk)
        }

        // PoolChunk fits into this PoolChunkList, adding it here.
        add0(chunk)
        return true
    }

    /**
     * Moves the [PoolChunk] down the [PoolChunkList] linked-list so it will end up in the right
     * [PoolChunkList] that has the correct minUsage / maxUsage in respect to [PoolChunk.usage].
     */
    private fun move0(chunk: PoolChunk<T>): Boolean {
        if (prevList == null) {
            // There is no previous PoolChunkList so return false which result in having the PoolChunk destroyed and
            // all memory associated with the PoolChunk will be released.
            assert(chunk.usage() == 0)
            return false
        }
        return prevList!!.move(chunk)
    }

    fun add(chunk: PoolChunk<T>) {
        if (chunk.freeBytes <= freeMinThreshold) {
            nextList!!.add(chunk)
            return
        }
        add0(chunk)
    }

    /**
     * Adds the [PoolChunk] to this [PoolChunkList].
     */
    fun add0(chunk: PoolChunk<T>) {
        chunk.parent = this
        if (head == null) {
            head = chunk
            chunk.prev = null
            chunk.next = null
        } else {
            chunk.prev = null
            chunk.next = head
            head!!.prev = chunk
            head = chunk
        }
    }

    private fun remove(cur: PoolChunk<T>) {
        if (cur === head) {
            head = cur.next
            if (head != null) {
                head!!.prev = null
            }
        } else {
            val next = cur.next
            cur.prev!!.next = next
            if (next != null) {
                next.prev = cur.prev
            }
        }
    }

    override fun minUsage(): Int = minUsage0(minUsage)

    override fun maxUsage(): Int = min(maxUsage, 100)

    override fun iterator(): MutableIterator<PoolChunkMetric> {
        arena.lock()
        try {
            if (head == null) {
                return EMPTY_METRICS
            }
            val metrics = ArrayList<PoolChunkMetric>()
            var cur = head
            while (cur != null) {
                metrics.add(cur)
                cur = cur.next
            }
            return metrics.iterator()
        } finally {
            arena.unlock()
        }
    }

    override fun toString(): String {
        val buf = StringBuilder()
        arena.lock()
        try {
            if (head == null) {
                return "none"
            }

            var cur = head
            while (cur != null) {
                buf.append(cur)
                cur = cur.next
                if (cur == null) {
                    break
                }
                buf.append(StringUtil.NEWLINE)
            }
        } finally {
            arena.unlock()
        }
        return buf.toString()
    }

    fun destroy(arena: PoolArena<T>) {
        var chunk = head
        while (chunk != null) {
            arena.destroyChunk(chunk)
            chunk = chunk.next
        }
        head = null
    }

    companion object {
        private val EMPTY_METRICS: MutableIterator<PoolChunkMetric> = Collections.emptyIterator()

        @JvmStatic
        private fun calculateMaxCapacity(minUsage: Int, chunkSize: Int): Int {
            val minUsage = minUsage0(minUsage)

            if (minUsage == 100) {
                // If the minUsage is 100 we can not allocate anything out of this list.
                return 0
            }

            // Calculate the maximum amount of bytes that can be allocated from a PoolChunk in this PoolChunkList.
            return (chunkSize * (100L - minUsage) / 100L).toInt()
        }

        @JvmStatic
        private fun minUsage0(value: Int): Int = max(1, value)
    }
}
