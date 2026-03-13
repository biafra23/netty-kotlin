/*
 * Copyright 2013 The Netty Project
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

import io.netty.util.internal.ObjectUtil.checkNonEmpty
import io.netty.util.internal.ObjectUtil.checkPositive
import java.util.Arrays

class AppendableCharSequence : CharSequence, Appendable {

    private var chars: CharArray
    private var pos: Int

    constructor(length: Int) {
        chars = CharArray(checkPositive(length, "length"))
        pos = 0
    }

    private constructor(chars: CharArray) {
        this.chars = checkNonEmpty(chars, "chars")
        pos = chars.size
    }

    fun setLength(length: Int) {
        if (length < 0 || length > pos) {
            throw IllegalArgumentException("length: $length (length: >= 0, <= $pos)")
        }
        this.pos = length
    }

    override val length: Int
        get() = pos

    override fun get(index: Int): Char {
        if (index > pos) {
            throw IndexOutOfBoundsException()
        }
        return chars[index]
    }

    /**
     * Access a value in this [CharSequence].
     * This method is considered unsafe as index values are assumed to be legitimate.
     * Only underlying array bounds checking is done.
     * @param index The index to access the underlying array at.
     * @return The value at [index].
     */
    fun charAtUnsafe(index: Int): Char = chars[index]

    override fun subSequence(startIndex: Int, endIndex: Int): AppendableCharSequence {
        if (startIndex == endIndex) {
            // If start and end index is the same we need to return an empty sequence to conform to the interface.
            // As our expanding logic depends on the fact that we have a char[] with length > 0 we need to construct
            // an instance for which this is true.
            return AppendableCharSequence(minOf(16, chars.size))
        }
        return AppendableCharSequence(Arrays.copyOfRange(chars, startIndex, endIndex))
    }

    override fun append(c: Char): AppendableCharSequence {
        if (pos == chars.size) {
            val old = chars
            chars = CharArray(old.size shl 1)
            System.arraycopy(old, 0, chars, 0, old.size)
        }
        chars[pos++] = c
        return this
    }

    override fun append(csq: CharSequence?): AppendableCharSequence {
        return append(csq!!, 0, csq.length)
    }

    override fun append(csq: CharSequence?, start: Int, end: Int): AppendableCharSequence {
        val csq = csq!!
        if (csq.length < end) {
            throw IndexOutOfBoundsException("expected: csq.length() >= ($end),but actual is (${csq.length})")
        }
        val length = end - start
        if (length > chars.size - pos) {
            chars = expand(chars, pos + length, pos)
        }
        if (csq is AppendableCharSequence) {
            // Optimize append operations via array copy
            val src = csq.chars
            System.arraycopy(src, start, chars, pos, length)
            pos += length
            return this
        }
        for (i in start until end) {
            chars[pos++] = csq[i]
        }
        return this
    }

    /**
     * Reset the [AppendableCharSequence]. Be aware this will only reset the current internal position and not
     * shrink the internal char array.
     */
    fun reset() {
        pos = 0
    }

    override fun toString(): String = String(chars, 0, pos)

    /**
     * Create a new [String] from the given start to end.
     */
    fun substring(start: Int, end: Int): String {
        val length = end - start
        if (start > pos || length > pos) {
            throw IndexOutOfBoundsException("expected: start and length <= ($pos)")
        }
        return String(chars, start, length)
    }

    /**
     * Create a new [String] from the given start to end.
     * This method is considered unsafe as index values are assumed to be legitimate.
     * Only underlying array bounds checking is done.
     */
    fun subStringUnsafe(start: Int, end: Int): String = String(chars, start, end - start)

    companion object {
        @JvmStatic
        private fun expand(array: CharArray, neededSpace: Int, size: Int): CharArray {
            var newCapacity = array.size
            do {
                // double capacity until it is big enough
                newCapacity = newCapacity shl 1
                if (newCapacity < 0) {
                    throw IllegalStateException()
                }
            } while (neededSpace > newCapacity)

            val newArray = CharArray(newCapacity)
            System.arraycopy(array, 0, newArray, 0, size)
            return newArray
        }
    }
}
