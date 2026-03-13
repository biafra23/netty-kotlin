/*
 * Copyright 2020 The Netty Project
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

/**
 * Internal primitive priority queue, used by [PoolChunk].
 * The implementation is based on the binary heap, as described in Algorithms by Sedgewick and Wayne.
 */
internal class IntPriorityQueue {
    private var array = IntArray(9)
    private var size = 0

    fun offer(handle: Int) {
        require(handle != NO_VALUE) {
            "The NO_VALUE ($NO_VALUE) cannot be added to the queue."
        }
        size++
        if (size == array.size) {
            // Grow queue capacity.
            array = array.copyOf(1 + (array.size - 1) * 2)
        }
        array[size] = handle
        lift(size)
    }

    fun remove(value: Int) {
        for (i in 1..size) {
            if (array[i] == value) {
                array[i] = array[size--]
                lift(i)
                sink(i)
                return
            }
        }
    }

    fun peek(): Int {
        if (size == 0) {
            return NO_VALUE
        }
        return array[1]
    }

    fun poll(): Int {
        if (size == 0) {
            return NO_VALUE
        }
        val value = array[1]
        array[1] = array[size]
        array[size] = 0
        size--
        sink(1)
        return value
    }

    val isEmpty: Boolean
        get() = size == 0

    private fun lift(index: Int) {
        var idx = index
        while (idx > 1) {
            val parentIndex = idx shr 1
            if (!subord(parentIndex, idx)) break
            swap(idx, parentIndex)
            idx = parentIndex
        }
    }

    private fun sink(index: Int) {
        var idx = index
        var child: Int
        while ((idx shl 1).also { child = it } <= size) {
            if (child < size && subord(child, child + 1)) {
                child++
            }
            if (!subord(idx, child)) {
                break
            }
            swap(idx, child)
            idx = child
        }
    }

    private fun subord(a: Int, b: Int): Boolean = array[a] > array[b]

    private fun swap(a: Int, b: Int) {
        val value = array[a]
        array[a] = array[b]
        array[b] = value
    }

    companion object {
        const val NO_VALUE: Int = -1
    }
}
