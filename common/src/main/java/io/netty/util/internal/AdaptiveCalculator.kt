/*
 * Copyright 2025 The Netty Project
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
package io.netty.util.internal

import io.netty.util.internal.ObjectUtil.checkPositive
import kotlin.math.max
import kotlin.math.min

/**
 * Calculate sizes in a adaptive way.
 */
class AdaptiveCalculator(minimum: Int, initial: Int, maximum: Int) {

    private val minIndex: Int
    private val maxIndex: Int
    private val minCapacity: Int
    private val maxCapacity: Int
    private var index: Int
    private var nextSize: Int
    private var decreaseNow: Boolean = false

    init {
        checkPositive(minimum, "minimum")
        if (initial < minimum) {
            throw IllegalArgumentException("initial: $initial")
        }
        if (maximum < initial) {
            throw IllegalArgumentException("maximum: $maximum")
        }

        var minIdx = getSizeTableIndex(minimum)
        if (SIZE_TABLE[minIdx] < minimum) {
            minIdx++
        }
        this.minIndex = minIdx

        var maxIdx = getSizeTableIndex(maximum)
        if (SIZE_TABLE[maxIdx] > maximum) {
            maxIdx--
        }
        this.maxIndex = maxIdx

        var initialIndex = getSizeTableIndex(initial)
        if (SIZE_TABLE[initialIndex] > initial) {
            initialIndex--
        }
        this.index = initialIndex
        this.minCapacity = minimum
        this.maxCapacity = maximum
        nextSize = max(SIZE_TABLE[index], minCapacity)
    }

    fun record(size: Int) {
        if (size <= SIZE_TABLE[max(0, index - INDEX_DECREMENT)]) {
            if (decreaseNow) {
                index = max(index - INDEX_DECREMENT, minIndex)
                nextSize = max(SIZE_TABLE[index], minCapacity)
                decreaseNow = false
            } else {
                decreaseNow = true
            }
        } else if (size >= nextSize) {
            index = min(index + INDEX_INCREMENT, maxIndex)
            nextSize = min(SIZE_TABLE[index], maxCapacity)
            decreaseNow = false
        }
    }

    fun nextSize(): Int = nextSize

    companion object {
        private const val INDEX_INCREMENT = 4
        private const val INDEX_DECREMENT = 1

        @JvmStatic
        private val SIZE_TABLE: IntArray

        init {
            val sizeTable = ArrayList<Int>()
            var i = 16
            while (i < 512) {
                sizeTable.add(i)
                i += 16
            }

            // Suppress a warning since i becomes negative when an integer overflow happens
            i = 512
            while (i > 0) {
                sizeTable.add(i)
                i = i shl 1
            }

            SIZE_TABLE = IntArray(sizeTable.size)
            for (idx in SIZE_TABLE.indices) {
                SIZE_TABLE[idx] = sizeTable[idx]
            }
        }

        @JvmStatic
        private fun getSizeTableIndex(size: Int): Int {
            var low = 0
            var high = SIZE_TABLE.size - 1
            while (true) {
                if (high < low) {
                    return low
                }
                if (high == low) {
                    return high
                }

                val mid = low + high ushr 1
                val a = SIZE_TABLE[mid]
                val b = SIZE_TABLE[mid + 1]
                if (size > b) {
                    low = mid + 1
                } else if (size < a) {
                    high = mid - 1
                } else if (size == a) {
                    return mid
                } else {
                    return mid + 1
                }
            }
        }
    }
}
