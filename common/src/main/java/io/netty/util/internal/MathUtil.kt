/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at:
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

object MathUtil {

    @JvmStatic
    fun findNextPositivePowerOfTwo(value: Int): Int {
        assert(value > Int.MIN_VALUE && value < 0x40000000)
        return 1 shl (32 - Integer.numberOfLeadingZeros(value - 1))
    }

    @JvmStatic
    fun safeFindNextPositivePowerOfTwo(value: Int): Int {
        return if (value <= 0) 1 else if (value >= 0x40000000) 0x40000000 else findNextPositivePowerOfTwo(value)
    }

    @JvmStatic
    fun isOutOfBounds(index: Int, length: Int, capacity: Int): Boolean {
        return (index or length or capacity or (index + length)) < 0 || index + length > capacity
    }

    @JvmStatic
    @Deprecated("Use Integer.compare() directly.", replaceWith = ReplaceWith("Integer.compare(x, y)"))
    fun compare(x: Int, y: Int): Int {
        return Integer.compare(x, y)
    }

    @JvmStatic
    @Deprecated("Use java.lang.Long.compare() directly.", replaceWith = ReplaceWith("java.lang.Long.compare(x, y)"))
    fun compare(x: Long, y: Long): Int {
        return java.lang.Long.compare(x, y)
    }
}
