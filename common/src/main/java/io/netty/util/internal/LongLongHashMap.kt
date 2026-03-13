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

import java.util.Arrays

class LongLongHashMap {

    private var mask: Int
    private var array: LongArray
    private var maxProbe: Int = 0
    private var zeroVal: Long
    private val emptyVal: Long

    constructor(emptyVal: Long) {
        this.emptyVal = emptyVal
        zeroVal = emptyVal
        val initialSize = 32
        array = LongArray(initialSize)
        mask = initialSize - 1
        computeMaskAndProbe()
    }

    constructor(other: LongLongHashMap) {
        this.mask = other.mask
        this.array = Arrays.copyOf(other.array, other.array.size)
        this.maxProbe = other.maxProbe
        this.zeroVal = other.zeroVal
        this.emptyVal = other.emptyVal
    }

    fun put(key: Long, value: Long): Long {
        if (key == 0L) {
            val prev = zeroVal
            zeroVal = value
            return prev
        }

        while (true) {
            var index = index(key)
            var i = 0
            while (i < maxProbe) {
                val existing = array[index]
                if (existing == key || existing == 0L) {
                    val prev = if (existing == 0L) emptyVal else array[index + 1]
                    array[index] = key
                    array[index + 1] = value
                    // Nerf any existing misplaced entries.
                    while (i < maxProbe) {
                        index = index + 2 and mask
                        if (array[index] == key) {
                            array[index] = 0
                            return array[index + 1]
                        }
                        i++
                    }
                    return prev
                }
                index = index + 2 and mask
                i++
            }
            expand() // Grow array and re-hash.
        }
    }

    fun remove(key: Long) {
        if (key == 0L) {
            zeroVal = emptyVal
            return
        }
        var index = index(key)
        for (i in 0 until maxProbe) {
            val existing = array[index]
            if (existing == key) {
                array[index] = 0
                break
            }
            index = index + 2 and mask
        }
    }

    fun get(key: Long): Long {
        if (key == 0L) {
            return zeroVal
        }
        var index = index(key)
        for (i in 0 until maxProbe) {
            val existing = array[index]
            if (existing == key) {
                return array[index + 1]
            }
            index = index + 2 and mask
        }
        return emptyVal
    }

    private fun index(key: Long): Int {
        // Hash with murmur64, and mask.
        @Suppress("NAME_SHADOWING")
        var key = key
        key = key xor (key ushr 33)
        key *= -0xae502812aa7333L // 0xff51afd7ed558ccdL
        key = key xor (key ushr 33)
        key *= -0x3b314601e57a13adL // 0xc4ceb9fe1a85ec53L
        key = key xor (key ushr 33)
        return key.toInt() and mask
    }

    private fun expand() {
        val prev = array
        array = LongArray(prev.size * 2)
        computeMaskAndProbe()
        var i = 0
        while (i < prev.size) {
            val key = prev[i]
            if (key != 0L) {
                val value = prev[i + 1]
                put(key, value)
            }
            i += 2
        }
    }

    private fun computeMaskAndProbe() {
        val length = array.size
        mask = length - 1 and MASK_TEMPLATE
        maxProbe = Math.log(length.toDouble()).toInt()
    }

    companion object {
        private const val MASK_TEMPLATE = 1.inv()
    }
}
