/*
 * Copyright 2016 The Netty Project
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

/**
 * Utility methods for constant-time comparisons, to avoid timing side-channel attacks.
 */
object ConstantTimeUtils {

    /**
     * Compare two `int`s without leaking timing information.
     *
     * The `int` return type is intentional and is designed to allow cascading of constant time operations:
     * ```
     * val l1 = 1
     * val l2 = 1
     * val l3 = 1
     * val l4 = 500
     * val equals = (equalsConstantTime(l1, l2) and equalsConstantTime(l3, l4)) != 0
     * ```
     * @param x the first value.
     * @param y the second value.
     * @return `0` if not equal. `1` if equal.
     */
    @JvmStatic
    fun equalsConstantTime(x: Int, y: Int): Int {
        var z = (x xor y).inv()
        z = z and (z shr 16)
        z = z and (z shr 8)
        z = z and (z shr 4)
        z = z and (z shr 2)
        z = z and (z shr 1)
        return z and 1
    }

    /**
     * Compare two `long`s without leaking timing information.
     *
     * The `int` return type is intentional and is designed to allow cascading of constant time operations:
     * ```
     * val l1 = 1L
     * val l2 = 1L
     * val l3 = 1L
     * val l4 = 500L
     * val equals = (equalsConstantTime(l1, l2) and equalsConstantTime(l3, l4)) != 0
     * ```
     * @param x the first value.
     * @param y the second value.
     * @return `0` if not equal. `1` if equal.
     */
    @JvmStatic
    fun equalsConstantTime(x: Long, y: Long): Int {
        var z = (x xor y).inv()
        z = z and (z shr 32)
        z = z and (z shr 16)
        z = z and (z shr 8)
        z = z and (z shr 4)
        z = z and (z shr 2)
        z = z and (z shr 1)
        return (z and 1L).toInt()
    }

    /**
     * Compare two `byte` arrays for equality without leaking timing information.
     * For performance reasons no bounds checking on the parameters is performed.
     *
     * The `int` return type is intentional and is designed to allow cascading of constant time operations:
     * ```
     * val s1 = byteArrayOf(1, 2, 3)
     * val s2 = byteArrayOf(1, 2, 3)
     * val s3 = byteArrayOf(1, 2, 3)
     * val s4 = byteArrayOf(4, 5, 6)
     * val equals = (equalsConstantTime(s1, 0, s2, 0, s1.size) and
     *               equalsConstantTime(s3, 0, s4, 0, s3.size)) != 0
     * ```
     * @param bytes1 the first byte array.
     * @param startPos1 the position (inclusive) to start comparing in `bytes1`.
     * @param bytes2 the second byte array.
     * @param startPos2 the position (inclusive) to start comparing in `bytes2`.
     * @param length the amount of bytes to compare. This is assumed to be validated as not going out of bounds
     * by the caller.
     * @return `0` if not equal. `1` if equal.
     */
    @JvmStatic
    fun equalsConstantTime(
        bytes1: ByteArray, startPos1: Int,
        bytes2: ByteArray, startPos2: Int, length: Int
    ): Int {
        // Benchmarking demonstrates that using an int to accumulate is faster than other data types.
        var b = 0
        val end = startPos1 + length
        var pos1 = startPos1
        var pos2 = startPos2
        while (pos1 < end) {
            b = b or (bytes1[pos1].toInt() xor bytes2[pos2].toInt())
            pos1++
            pos2++
        }
        return equalsConstantTime(b, 0)
    }

    /**
     * Compare two [CharSequence] objects without leaking timing information.
     *
     * The `int` return type is intentional and is designed to allow cascading of constant time operations:
     * ```
     * val s1 = "foo"
     * val s2 = "foo"
     * val s3 = "foo"
     * val s4 = "goo"
     * val equals = (equalsConstantTime(s1, s2) and equalsConstantTime(s3, s4)) != 0
     * ```
     * @param s1 the first value.
     * @param s2 the second value.
     * @return `0` if not equal. `1` if equal.
     */
    @JvmStatic
    fun equalsConstantTime(s1: CharSequence, s2: CharSequence): Int {
        if (s1.length != s2.length) {
            return 0
        }

        // Benchmarking demonstrates that using an int to accumulate is faster than other data types.
        var c = 0
        for (i in s1.indices) {
            c = c or (s1[i].code xor s2[i].code)
        }
        return equalsConstantTime(c, 0)
    }
}
