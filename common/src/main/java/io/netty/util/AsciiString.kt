/*
 * Copyright 2014 The Netty Project
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
package io.netty.util

import io.netty.util.internal.EmptyArrays
import io.netty.util.internal.InternalThreadLocalMap
import io.netty.util.internal.ObjectUtil
import io.netty.util.internal.PlatformDependent
import io.netty.util.internal.MathUtil.isOutOfBounds
import io.netty.util.internal.ObjectUtil.checkNotNull
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.util.Arrays
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * A string which has been encoded into a character encoding whose character always takes a single byte, similarly to
 * ASCII. It internally keeps its content in a byte array unlike [String], which uses a character array, for
 * reduced memory footprint and faster data transfer from/to byte-based data structures such as a byte array and
 * [ByteBuffer]. It is often used in conjunction with `Headers` that require a [CharSequence].
 *
 * This class was designed to provide an immutable array of bytes, and caches some internal state based upon the value
 * of this array. However underlying access to this byte array is provided via not copying the array on construction or
 * [array]. If any changes are made to the underlying byte array it is the user's responsibility to call
 * [arrayChanged] so the state of this class can be reset.
 */
class AsciiString : CharSequence, Comparable<CharSequence> {

    /**
     * If this value is modified outside the constructor then call [arrayChanged].
     */
    private val value: ByteArray

    /**
     * Offset into [value] that all operations should use when acting upon [value].
     */
    private val offset: Int

    /**
     * Length in bytes for [value] that we care about. This is independent from `value.length`
     * because we may be looking at a subsection of the array.
     */
    override val length: Int

    /**
     * The hash code is cached after it is first computed. It can be reset with [arrayChanged].
     */
    private var hash: Int = 0

    /**
     * Used to cache the [toString] value.
     */
    private var string: String? = null

    /**
     * Initialize this byte string based upon a byte array. A copy will be made.
     */
    constructor(value: ByteArray) : this(value, true)

    /**
     * Initialize this byte string based upon a byte array.
     * `copy` determines if a copy is made or the array is shared.
     */
    constructor(value: ByteArray, copy: Boolean) : this(value, 0, value.size, copy)

    /**
     * Construct a new instance from a `byte[]` array.
     * @param copy `true` then a copy of the memory will be made. `false` the underlying memory
     * will be shared.
     */
    constructor(value: ByteArray, start: Int, length: Int, copy: Boolean) {
        if (copy) {
            val rangedCopy = ByteArray(length)
            System.arraycopy(value, start, rangedCopy, 0, rangedCopy.size)
            this.value = rangedCopy
            this.offset = 0
        } else {
            if (isOutOfBounds(start, length, value.size)) {
                throw IndexOutOfBoundsException(
                    "expected: 0 <= start($start) <= start + length($length) <= value.length(${value.size})"
                )
            }
            this.value = value
            this.offset = start
        }
        this.length = length
    }

    /**
     * Create a copy of the underlying storage from `value`.
     * The copy will start at [ByteBuffer.position] and copy [ByteBuffer.remaining] bytes.
     */
    constructor(value: ByteBuffer) : this(value, true)

    /**
     * Initialize an instance based upon the underlying storage from `value`.
     * There is a potential to share the underlying array storage if [ByteBuffer.hasArray] is `true`.
     * if `copy` is `true` a copy will be made of the memory.
     * if `copy` is `false` the underlying storage will be shared, if possible.
     */
    constructor(value: ByteBuffer, copy: Boolean) : this(value, value.position(), value.remaining(), copy)

    /**
     * Initialize an [AsciiString] based upon the underlying storage from `value`.
     * There is a potential to share the underlying array storage if [ByteBuffer.hasArray] is `true`.
     * if `copy` is `true` a copy will be made of the memory.
     * if `copy` is `false` the underlying storage will be shared, if possible.
     */
    constructor(value: ByteBuffer, start: Int, length: Int, copy: Boolean) {
        if (isOutOfBounds(start, length, value.capacity())) {
            throw IndexOutOfBoundsException(
                "expected: 0 <= start($start) <= start + length($length) <= value.capacity(${value.capacity()})"
            )
        }

        if (value.hasArray()) {
            if (copy) {
                val bufferOffset = value.arrayOffset() + start
                this.value = Arrays.copyOfRange(value.array(), bufferOffset, bufferOffset + length)
                this.offset = 0
            } else {
                this.value = value.array()
                this.offset = start
            }
        } else {
            this.value = PlatformDependent.allocateUninitializedArray(length)
            val oldPos = value.position()
            value.get(this.value, 0, length)
            value.position(oldPos)
            this.offset = 0
        }
        this.length = length
    }

    /**
     * Create a copy of `value` into this instance assuming ASCII encoding.
     */
    constructor(value: CharArray) : this(value, 0, value.size)

    /**
     * Create a copy of `value` into this instance assuming ASCII encoding.
     * The copy will start at index `start` and copy `length` bytes.
     */
    constructor(value: CharArray, start: Int, length: Int) {
        if (isOutOfBounds(start, length, value.size)) {
            throw IndexOutOfBoundsException(
                "expected: 0 <= start($start) <= start + length($length) <= value.length(${value.size})"
            )
        }

        this.value = PlatformDependent.allocateUninitializedArray(length)
        var j = start
        for (i in 0 until length) {
            this.value[i] = c2b(value[j])
            j++
        }
        this.offset = 0
        this.length = length
    }

    /**
     * Create a copy of `value` into this instance using the encoding type of `charset`.
     */
    constructor(value: CharArray, charset: Charset) : this(value, charset, 0, value.size)

    /**
     * Create a copy of `value` into a this instance using the encoding type of `charset`.
     * The copy will start at index `start` and copy `length` bytes.
     */
    constructor(value: CharArray, charset: Charset, start: Int, length: Int) {
        val cbuf = CharBuffer.wrap(value, start, length)
        val encoder = CharsetUtil.encoder(charset)
        val nativeBuffer = ByteBuffer.allocate((encoder.maxBytesPerChar() * length).toInt())
        encoder.encode(cbuf, nativeBuffer, true)
        val bufferOffset = nativeBuffer.arrayOffset()
        this.value = Arrays.copyOfRange(nativeBuffer.array(), bufferOffset, bufferOffset + nativeBuffer.position())
        this.offset = 0
        this.length = this.value.size
    }

    /**
     * Create a copy of `value` into this instance assuming ASCII encoding.
     */
    constructor(value: CharSequence) : this(value, 0, value.length)

    /**
     * Create a copy of `value` into this instance assuming ASCII encoding.
     * The copy will start at index `start` and copy `length` bytes.
     */
    constructor(value: CharSequence, start: Int, length: Int) {
        if (isOutOfBounds(start, length, value.length)) {
            throw IndexOutOfBoundsException(
                "expected: 0 <= start($start) <= start + length($length) <= value.length(${value.length})"
            )
        }

        this.value = PlatformDependent.allocateUninitializedArray(length)
        var j = start
        for (i in 0 until length) {
            this.value[i] = c2b(value[j])
            j++
        }
        this.offset = 0
        this.length = length
    }

    /**
     * Create a copy of `value` into this instance using the encoding type of `charset`.
     */
    constructor(value: CharSequence, charset: Charset) : this(value, charset, 0, value.length)

    /**
     * Create a copy of `value` into this instance using the encoding type of `charset`.
     * The copy will start at index `start` and copy `length` bytes.
     */
    constructor(value: CharSequence, charset: Charset, start: Int, length: Int) {
        val cbuf = CharBuffer.wrap(value, start, start + length)
        val encoder = CharsetUtil.encoder(charset)
        val nativeBuffer = ByteBuffer.allocate((encoder.maxBytesPerChar() * length).toInt())
        encoder.encode(cbuf, nativeBuffer, true)
        val bufferOffset = nativeBuffer.arrayOffset()
        this.value = Arrays.copyOfRange(nativeBuffer.array(), bufferOffset, bufferOffset + nativeBuffer.position())
        this.offset = 0
        this.length = this.value.size
    }

    /**
     * Iterates over the readable bytes of this buffer with the specified `processor` in ascending order.
     *
     * @return `-1` if the processor iterated to or beyond the end of the readable bytes.
     *         The last-visited index If the [ByteProcessor.process] returned `false`.
     */
    @Throws(Exception::class)
    fun forEachByte(visitor: ByteProcessor): Int {
        return forEachByte0(0, length, visitor)
    }

    /**
     * Iterates over the specified area of this buffer with the specified `processor` in ascending order.
     * (i.e. `index`, `(index + 1)`,  .. `(index + length - 1)`).
     *
     * @return `-1` if the processor iterated to or beyond the end of the specified area.
     *         The last-visited index If the [ByteProcessor.process] returned `false`.
     */
    @Throws(Exception::class)
    fun forEachByte(index: Int, length: Int, visitor: ByteProcessor): Int {
        if (isOutOfBounds(index, length, this.length)) {
            throw IndexOutOfBoundsException(
                "expected: 0 <= index($index) <= start + length($length) <= length(${this.length})"
            )
        }
        return forEachByte0(index, length, visitor)
    }

    @Throws(Exception::class)
    private fun forEachByte0(index: Int, length: Int, visitor: ByteProcessor): Int {
        val len = offset + index + length
        for (i in (offset + index) until len) {
            if (!visitor.process(value[i])) {
                return i - offset
            }
        }
        return -1
    }

    /**
     * Iterates over the readable bytes of this buffer with the specified `processor` in descending order.
     *
     * @return `-1` if the processor iterated to or beyond the beginning of the readable bytes.
     *         The last-visited index If the [ByteProcessor.process] returned `false`.
     */
    @Throws(Exception::class)
    fun forEachByteDesc(visitor: ByteProcessor): Int {
        return forEachByteDesc0(0, length, visitor)
    }

    /**
     * Iterates over the specified area of this buffer with the specified `processor` in descending order.
     * (i.e. `(index + length - 1)`, `(index + length - 2)`, ... `index`).
     *
     * @return `-1` if the processor iterated to or beyond the beginning of the specified area.
     *         The last-visited index If the [ByteProcessor.process] returned `false`.
     */
    @Throws(Exception::class)
    fun forEachByteDesc(index: Int, length: Int, visitor: ByteProcessor): Int {
        if (isOutOfBounds(index, length, this.length)) {
            throw IndexOutOfBoundsException(
                "expected: 0 <= index($index) <= start + length($length) <= length(${this.length})"
            )
        }
        return forEachByteDesc0(index, length, visitor)
    }

    @Throws(Exception::class)
    private fun forEachByteDesc0(index: Int, length: Int, visitor: ByteProcessor): Int {
        val end = offset + index
        for (i in (offset + index + length - 1) downTo end) {
            if (!visitor.process(value[i])) {
                return i - offset
            }
        }
        return -1
    }

    fun byteAt(index: Int): Byte {
        // We must do a range check here to enforce the access does not go outside our sub region of the array.
        // We rely on the array access itself to pick up the array out of bounds conditions
        if (index < 0 || index >= length) {
            throw IndexOutOfBoundsException("index: $index must be in the range [0,$length)")
        }
        // Try to use unsafe to avoid double checking the index bounds
        if (PlatformDependent.hasUnsafe()) {
            return PlatformDependent.getByte(value, index + offset)
        }
        return value[index + offset]
    }

    /**
     * Determine if this instance has 0 length.
     */
    override fun isEmpty(): Boolean {
        return length == 0
    }

    /**
     * During normal use cases the [AsciiString] should be immutable, but if the underlying array is shared,
     * and changes then this needs to be called.
     */
    fun arrayChanged() {
        string = null
        hash = 0
    }

    /**
     * This gives direct access to the underlying storage array.
     * The [toByteArray] should be preferred over this method.
     * If the return value is changed then [arrayChanged] must be called.
     * @see arrayOffset
     * @see isEntireArrayUsed
     */
    fun array(): ByteArray {
        return value
    }

    /**
     * The offset into [array] for which data for this ByteString begins.
     * @see array
     * @see isEntireArrayUsed
     */
    fun arrayOffset(): Int {
        return offset
    }

    /**
     * Determine if the storage represented by [array] is entirely used.
     * @see array
     */
    fun isEntireArrayUsed(): Boolean {
        return offset == 0 && length == value.size
    }

    /**
     * Converts this string to a byte array.
     */
    fun toByteArray(): ByteArray {
        return toByteArray(0, length)
    }

    /**
     * Converts a subset of this string to a byte array.
     * The subset is defined by the range [`start`, `end`).
     */
    fun toByteArray(start: Int, end: Int): ByteArray {
        return Arrays.copyOfRange(value, start + offset, end + offset)
    }

    /**
     * Copies the content of this string to a byte array.
     *
     * @param srcIdx the starting offset of characters to copy.
     * @param dst the destination byte array.
     * @param dstIdx the starting offset in the destination byte array.
     * @param length the number of characters to copy.
     */
    fun copy(srcIdx: Int, dst: ByteArray, dstIdx: Int, length: Int) {
        if (isOutOfBounds(srcIdx, length, this.length)) {
            throw IndexOutOfBoundsException(
                "expected: 0 <= srcIdx($srcIdx) <= srcIdx + length($length) <= srcLen(${this.length})"
            )
        }

        System.arraycopy(value, srcIdx + offset, checkNotNull(dst, "dst"), dstIdx, length)
    }

    override fun get(index: Int): Char {
        return b2c(byteAt(index))
    }

    /**
     * Determines if this `String` contains the sequence of characters in the `CharSequence` passed.
     *
     * @param cs the character sequence to search for.
     * @return `true` if the sequence of characters are contained in this string, otherwise `false`.
     */
    operator fun contains(cs: CharSequence): Boolean {
        return indexOf(cs) >= 0
    }

    /**
     * Compares the specified string to this string using the ASCII values of the characters. Returns 0 if the strings
     * contain the same characters in the same order. Returns a negative integer if the first non-equal character in
     * this string has an ASCII value which is less than the ASCII value of the character at the same position in the
     * specified string, or if this string is a prefix of the specified string. Returns a positive integer if the first
     * non-equal character in this string has a ASCII value which is greater than the ASCII value of the character at
     * the same position in the specified string, or if the specified string is a prefix of this string.
     *
     * @param other the string to compare.
     * @return 0 if the strings are equal, a negative integer if this string is before the specified string, or a
     *         positive integer if this string is after the specified string.
     * @throws NullPointerException if `string` is `null`.
     */
    override fun compareTo(other: CharSequence): Int {
        if (this === other) {
            return 0
        }

        var result: Int
        val length1 = length
        val length2 = other.length
        val minLength = Math.min(length1, length2)
        var j = arrayOffset()
        for (i in 0 until minLength) {
            result = b2c(value[j]).code - other[i].code
            if (result != 0) {
                return result
            }
            j++
        }

        return length1 - length2
    }

    /**
     * Concatenates this string and the specified string.
     *
     * @param string the string to concatenate
     * @return a new string which is the concatenation of this string and the specified string.
     */
    fun concat(string: CharSequence): AsciiString {
        val thisLen = length
        val thatLen = string.length
        if (thatLen == 0) {
            return this
        }

        if (string is AsciiString) {
            if (isEmpty()) {
                return string
            }

            val newValue = PlatformDependent.allocateUninitializedArray(thisLen + thatLen)
            System.arraycopy(value, arrayOffset(), newValue, 0, thisLen)
            System.arraycopy(string.value, string.arrayOffset(), newValue, thisLen, thatLen)
            return AsciiString(newValue, false)
        }

        if (isEmpty()) {
            return AsciiString(string)
        }

        val newValue = PlatformDependent.allocateUninitializedArray(thisLen + thatLen)
        System.arraycopy(value, arrayOffset(), newValue, 0, thisLen)
        var i = thisLen
        var j = 0
        while (i < newValue.size) {
            newValue[i] = c2b(string[j])
            i++
            j++
        }

        return AsciiString(newValue, false)
    }

    /**
     * Compares the specified string to this string to determine if the specified string is a suffix.
     *
     * @param suffix the suffix to look for.
     * @return `true` if the specified string is a suffix of this string, `false` otherwise.
     * @throws NullPointerException if `suffix` is `null`.
     */
    fun endsWith(suffix: CharSequence): Boolean {
        val suffixLen = suffix.length
        return regionMatches(length - suffixLen, suffix, 0, suffixLen)
    }

    /**
     * Compares the specified string to this string ignoring the case of the characters and returns true if they are
     * equal.
     *
     * @param string the string to compare.
     * @return `true` if the specified string is equal to this string, `false` otherwise.
     */
    fun contentEqualsIgnoreCase(string: CharSequence?): Boolean {
        if (this === string) {
            return true
        }

        if (string == null || string.length != length) {
            return false
        }

        if (string is AsciiString) {
            val thisValue = this.value
            if (offset == 0 && string.offset == 0 && length == thisValue.size) {
                val otherValue = string.value
                for (i in thisValue.indices) {
                    if (!equalsIgnoreCase(thisValue[i], otherValue[i])) {
                        return false
                    }
                }
                return true
            }
            return misalignedEqualsIgnoreCase(string)
        }

        val thisValue = this.value
        var i = offset
        var j = 0
        while (j < string.length) {
            if (!equalsIgnoreCase(b2c(thisValue[i]), string[j])) {
                return false
            }
            i++
            j++
        }
        return true
    }

    private fun misalignedEqualsIgnoreCase(other: AsciiString): Boolean {
        val thisValue = this.value
        val otherValue = other.value
        var i = offset
        var j = other.offset
        val end = offset + length
        while (i < end) {
            if (!equalsIgnoreCase(thisValue[i], otherValue[j])) {
                return false
            }
            i++
            j++
        }
        return true
    }

    /**
     * Copies the characters in this string to a character array.
     *
     * @return a character array containing the characters of this string.
     */
    fun toCharArray(): CharArray {
        return toCharArray(0, length)
    }

    /**
     * Copies the characters in this string to a character array.
     *
     * @return a character array containing the characters of this string.
     */
    fun toCharArray(start: Int, end: Int): CharArray {
        val length = end - start
        if (length == 0) {
            return EmptyArrays.EMPTY_CHARS
        }

        if (isOutOfBounds(start, length, this.length)) {
            throw IndexOutOfBoundsException(
                "expected: 0 <= start($start) <= srcIdx + length($length) <= srcLen(${this.length})"
            )
        }

        val buffer = CharArray(length)
        var j = start + arrayOffset()
        for (i in 0 until length) {
            buffer[i] = b2c(value[j])
            j++
        }
        return buffer
    }

    /**
     * Copied the content of this string to a character array.
     *
     * @param srcIdx the starting offset of characters to copy.
     * @param dst the destination character array.
     * @param dstIdx the starting offset in the destination byte array.
     * @param length the number of characters to copy.
     */
    fun copy(srcIdx: Int, dst: CharArray, dstIdx: Int, length: Int) {
        ObjectUtil.checkNotNull(dst, "dst")

        if (isOutOfBounds(srcIdx, length, this.length)) {
            throw IndexOutOfBoundsException(
                "expected: 0 <= srcIdx($srcIdx) <= srcIdx + length($length) <= srcLen(${this.length})"
            )
        }

        val dstEnd = dstIdx + length
        var i = dstIdx
        var j = srcIdx + arrayOffset()
        while (i < dstEnd) {
            dst[i] = b2c(value[j])
            i++
            j++
        }
    }

    /**
     * Copies a range of characters into a new string.
     * @param start the offset of the first character (inclusive).
     * @return a new string containing the characters from start to the end of the string.
     * @throws IndexOutOfBoundsException if `start < 0` or `start > length()`.
     */
    fun subSequence(start: Int): AsciiString {
        return subSequence(start, length)
    }

    /**
     * Copies a range of characters into a new string.
     * @param startIndex the offset of the first character (inclusive).
     * @param endIndex The index to stop at (exclusive).
     * @return a new string containing the characters from start to the end of the string.
     * @throws IndexOutOfBoundsException if `start < 0` or `start > length()`.
     */
    override fun subSequence(startIndex: Int, endIndex: Int): AsciiString {
        return subSequence(startIndex, endIndex, true)
    }

    /**
     * Either copy or share a subset of underlying sub-sequence of bytes.
     * @param start the offset of the first character (inclusive).
     * @param end The index to stop at (exclusive).
     * @param copy If `true` then a copy of the underlying storage will be made.
     * If `false` then the underlying storage will be shared.
     * @return a new string containing the characters from start to the end of the string.
     * @throws IndexOutOfBoundsException if `start < 0` or `start > length()`.
     */
    fun subSequence(start: Int, end: Int, copy: Boolean): AsciiString {
        if (isOutOfBounds(start, end - start, length)) {
            throw IndexOutOfBoundsException(
                "expected: 0 <= start($start) <= end ($end) <= length($length)"
            )
        }

        if (start == 0 && end == length) {
            return this
        }

        if (end == start) {
            return EMPTY_STRING
        }

        return AsciiString(value, start + offset, end - start, copy)
    }

    /**
     * Searches in this string for the first index of the specified string. The search for the string starts at the
     * beginning and moves towards the end of this string.
     *
     * @param string the string to find.
     * @return the index of the first character of the specified string in this string, -1 if the specified string is
     *         not a substring.
     * @throws NullPointerException if `string` is `null`.
     */
    fun indexOf(string: CharSequence): Int {
        return indexOf(string, 0)
    }

    /**
     * Searches in this string for the index of the specified string. The search for the string starts at the specified
     * offset and moves towards the end of this string.
     *
     * @param subString the string to find.
     * @param start the starting offset.
     * @return the index of the first character of the specified string in this string, -1 if the specified string is
     *         not a substring.
     * @throws NullPointerException if `subString` is `null`.
     */
    fun indexOf(subString: CharSequence, start: Int): Int {
        var startPos = start
        val subCount = subString.length
        if (startPos < 0) {
            startPos = 0
        }
        if (subCount <= 0) {
            return if (startPos < length) startPos else length
        }
        if (subCount > length - startPos) {
            return INDEX_NOT_FOUND
        }

        val firstChar = subString[0]
        if (firstChar > MAX_CHAR_VALUE) {
            return INDEX_NOT_FOUND
        }
        val firstCharAsByte = c2b0(firstChar)
        val len = offset + length - subCount
        var i = startPos + offset
        while (i <= len) {
            if (value[i] == firstCharAsByte) {
                var o1 = i
                var o2 = 0
                o2++
                o1++
                while (o2 < subCount && b2c(value[o1]) == subString[o2]) {
                    o2++
                    o1++
                }
                if (o2 == subCount) {
                    return i - offset
                }
            }
            i++
        }
        return INDEX_NOT_FOUND
    }

    /**
     * Searches in this string for the index of the specified char `ch`.
     * The search for the char starts at the specified offset `start` and moves towards the end of this string.
     *
     * @param ch the char to find.
     * @param start the starting offset.
     * @return the index of the first occurrence of the specified char `ch` in this string,
     * -1 if found no occurrence.
     */
    fun indexOf(ch: Char, start: Int): Int {
        if (ch > MAX_CHAR_VALUE) {
            return INDEX_NOT_FOUND
        }

        var startPos = start
        if (startPos < 0) {
            startPos = 0
        }

        val chAsByte = c2b0(ch)
        val len = offset + length
        for (i in (startPos + offset) until len) {
            if (value[i] == chAsByte) {
                return i - offset
            }
        }
        return INDEX_NOT_FOUND
    }

    /**
     * Searches in this string for the last index of the specified string. The search for the string starts at the end
     * and moves towards the beginning of this string.
     *
     * @param string the string to find.
     * @return the index of the first character of the specified string in this string, -1 if the specified string is
     *         not a substring.
     * @throws NullPointerException if `string` is `null`.
     */
    fun lastIndexOf(string: CharSequence): Int {
        // Use count instead of count - 1 so lastIndexOf("") answers count
        return lastIndexOf(string, length)
    }

    /**
     * Searches in this string for the index of the specified string. The search for the string starts at the specified
     * offset and moves towards the beginning of this string.
     *
     * @param subString the string to find.
     * @param start the starting offset.
     * @return the index of the first character of the specified string in this string , -1 if the specified string is
     *         not a substring.
     * @throws NullPointerException if `subString` is `null`.
     */
    fun lastIndexOf(subString: CharSequence, start: Int): Int {
        val subCount = subString.length
        val startPos = Math.min(start, length - subCount)
        if (startPos < 0) {
            return INDEX_NOT_FOUND
        }
        if (subCount == 0) {
            return startPos
        }

        val firstChar = subString[0]
        if (firstChar > MAX_CHAR_VALUE) {
            return INDEX_NOT_FOUND
        }
        val firstCharAsByte = c2b0(firstChar)
        var i = offset + startPos
        while (i >= offset) {
            if (value[i] == firstCharAsByte) {
                var o1 = i
                var o2 = 0
                o2++
                o1++
                while (o2 < subCount && b2c(value[o1]) == subString[o2]) {
                    o2++
                    o1++
                }
                if (o2 == subCount) {
                    return i - offset
                }
            }
            i--
        }
        return INDEX_NOT_FOUND
    }

    /**
     * Compares the specified string to this string and compares the specified range of characters to determine if they
     * are the same.
     *
     * @param thisStart the starting offset in this string.
     * @param string the string to compare.
     * @param start the starting offset in the specified string.
     * @param length the number of characters to compare.
     * @return `true` if the ranges of characters are equal, `false` otherwise
     * @throws NullPointerException if `string` is `null`.
     */
    fun regionMatches(thisStart: Int, string: CharSequence, start: Int, length: Int): Boolean {
        ObjectUtil.checkNotNull(string, "string")

        if (start < 0 || string.length - start < length) {
            return false
        }

        val thisLen = this.length
        if (thisStart < 0 || thisLen - thisStart < length) {
            return false
        }

        if (length <= 0) {
            return true
        }

        if (string is AsciiString) {
            return PlatformDependent.equals(value, thisStart + offset, string.value, start + string.offset, length)
        }
        val thatEnd = start + length
        var i = start
        var j = thisStart + arrayOffset()
        while (i < thatEnd) {
            if (b2c(value[j]) != string[i]) {
                return false
            }
            i++
            j++
        }
        return true
    }

    /**
     * Compares the specified string to this string and compares the specified range of characters to determine if they
     * are the same. When ignoreCase is true, the case of the characters is ignored during the comparison.
     *
     * @param ignoreCase specifies if case should be ignored.
     * @param thisStart the starting offset in this string.
     * @param string the string to compare.
     * @param start the starting offset in the specified string.
     * @param length the number of characters to compare.
     * @return `true` if the ranges of characters are equal, `false` otherwise.
     * @throws NullPointerException if `string` is `null`.
     */
    fun regionMatches(ignoreCase: Boolean, thisStart: Int, string: CharSequence, start: Int, length: Int): Boolean {
        if (!ignoreCase) {
            return regionMatches(thisStart, string, start, length)
        }

        ObjectUtil.checkNotNull(string, "string")

        val thisLen = this.length
        if (thisStart < 0 || length > thisLen - thisStart) {
            return false
        }
        if (start < 0 || length > string.length - start) {
            return false
        }

        var thisIdx = thisStart + arrayOffset()
        val thisEnd = thisIdx + length
        var startIdx = start
        if (string is AsciiString) {
            val thisValue = this.value
            val otherValue = string.value
            var otherIdx = startIdx + string.offset
            while (thisIdx < thisEnd) {
                if (!equalsIgnoreCase(thisValue[thisIdx++], otherValue[otherIdx++])) {
                    return false
                }
            }
            return true
        }
        val thisValue = this.value
        while (thisIdx < thisEnd) {
            if (!equalsIgnoreCase(b2c(thisValue[thisIdx++]), string[startIdx++])) {
                return false
            }
        }
        return true
    }

    /**
     * Copies this string replacing occurrences of the specified character with another character.
     *
     * @param oldChar the character to replace.
     * @param newChar the replacement character.
     * @return a new string with occurrences of oldChar replaced by newChar.
     */
    fun replace(oldChar: Char, newChar: Char): AsciiString {
        if (oldChar > MAX_CHAR_VALUE) {
            return this
        }

        val oldCharAsByte = c2b0(oldChar)
        val newCharAsByte = c2b(newChar)
        val len = offset + length
        for (i in offset until len) {
            if (value[i] == oldCharAsByte) {
                val buffer = PlatformDependent.allocateUninitializedArray(length)
                System.arraycopy(value, offset, buffer, 0, i - offset)
                buffer[i - offset] = newCharAsByte
                var j = i + 1
                while (j < len) {
                    val oldValue = value[j]
                    buffer[j - offset] = if (oldValue != oldCharAsByte) oldValue else newCharAsByte
                    j++
                }
                return AsciiString(buffer, false)
            }
        }
        return this
    }

    /**
     * Compares the specified string to this string to determine if the specified string is a prefix.
     *
     * @param prefix the string to look for.
     * @return `true` if the specified string is a prefix of this string, `false` otherwise
     * @throws NullPointerException if `prefix` is `null`.
     */
    fun startsWith(prefix: CharSequence): Boolean {
        return startsWith(prefix, 0)
    }

    /**
     * Compares the specified string to this string, starting at the specified offset, to determine if the specified
     * string is a prefix.
     *
     * @param prefix the string to look for.
     * @param start the starting offset.
     * @return `true` if the specified string occurs in this string at the specified offset, `false`
     *         otherwise.
     * @throws NullPointerException if `prefix` is `null`.
     */
    fun startsWith(prefix: CharSequence, start: Int): Boolean {
        return regionMatches(start, prefix, 0, prefix.length)
    }

    /**
     * Converts the characters in this string to lowercase, using the default Locale.
     *
     * @return a new string containing the lowercase characters equivalent to the characters in this string.
     */
    fun toLowerCase(): AsciiString {
        return AsciiStringUtil.toLowerCase(this)
    }

    /**
     * Converts the characters in this string to uppercase, using the default Locale.
     *
     * @return a new string containing the uppercase characters equivalent to the characters in this string.
     */
    fun toUpperCase(): AsciiString {
        return AsciiStringUtil.toUpperCase(this)
    }

    /**
     * Duplicates this string removing white space characters from the beginning and end of the
     * string, without copying.
     *
     * @return a new string with characters `<= \\u0020` removed from the beginning and the end.
     */
    fun trim(): AsciiString {
        var start = arrayOffset()
        val last = arrayOffset() + length - 1
        var end = last
        while (start <= end && value[start] <= ' '.code.toByte()) {
            start++
        }
        while (end >= start && value[end] <= ' '.code.toByte()) {
            end--
        }
        if (start == 0 && end == last) {
            return this
        }
        return AsciiString(value, start, end - start + 1, false)
    }

    /**
     * Compares a `CharSequence` to this `String` to determine if their contents are equal.
     *
     * @param a the character sequence to compare to.
     * @return `true` if equal, otherwise `false`
     */
    fun contentEquals(a: CharSequence?): Boolean {
        if (this === a) {
            return true
        }

        if (a == null || a.length != length) {
            return false
        }
        if (a is AsciiString) {
            return equals(a)
        }

        var i = arrayOffset()
        var j = 0
        while (j < a.length) {
            if (b2c(value[i]) != a[j]) {
                return false
            }
            i++
            j++
        }
        return true
    }

    /**
     * Determines whether this string matches a given regular expression.
     *
     * @param expr the regular expression to be matched.
     * @return `true` if the expression matches, otherwise `false`.
     * @throws PatternSyntaxException if the syntax of the supplied regular expression is not valid.
     * @throws NullPointerException if `expr` is `null`.
     */
    fun matches(expr: String): Boolean {
        return Pattern.matches(expr, this)
    }

    /**
     * Splits this string using the supplied regular expression `expr`. The parameter `max` controls the
     * behavior how many times the pattern is applied to the string.
     *
     * @param expr the regular expression used to divide the string.
     * @param max the number of entries in the resulting array.
     * @return an array of Strings created by separating the string along matches of the regular expression.
     * @throws NullPointerException if `expr` is `null`.
     * @throws PatternSyntaxException if the syntax of the supplied regular expression is not valid.
     * @see Pattern.split
     */
    fun split(expr: String, max: Int): Array<AsciiString> {
        return toAsciiStringArray(Pattern.compile(expr).split(this, max))
    }

    /**
     * Splits the specified [String] with the specified delimiter..
     */
    fun split(delim: Char): Array<AsciiString> {
        val res = InternalThreadLocalMap.get().arrayList<AsciiString>()

        var start = 0
        val len = length
        for (i in start until len) {
            if (get(i) == delim) {
                if (start == i) {
                    res.add(EMPTY_STRING)
                } else {
                    res.add(AsciiString(value, start + arrayOffset(), i - start, false))
                }
                start = i + 1
            }
        }

        if (start == 0) { // If no delimiter was found in the value
            res.add(this)
        } else {
            if (start != len) {
                // Add the last element if it's not empty.
                res.add(AsciiString(value, start + arrayOffset(), len - start, false))
            } else {
                // Truncate trailing empty elements.
                for (i in res.size - 1 downTo 0) {
                    if (res[i].isEmpty()) {
                        res.removeAt(i)
                    } else {
                        break
                    }
                }
            }
        }

        return res.toArray(EmptyArrays.EMPTY_ASCII_STRINGS)
    }

    /**
     * Provides a case-insensitive hash code for Ascii like byte strings.
     */
    override fun hashCode(): Int {
        var h = hash
        if (h == 0) {
            h = PlatformDependent.hashCodeAscii(value, offset, length)
            hash = h
        }
        return h
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other.javaClass != AsciiString::class.java) {
            return false
        }
        if (this === other) {
            return true
        }

        val otherStr = other as AsciiString
        return length == otherStr.length &&
               hashCode() == otherStr.hashCode() &&
               PlatformDependent.equals(array(), arrayOffset(), otherStr.array(), otherStr.arrayOffset(), length)
    }

    /**
     * Translates the entire byte string to a [String].
     * @see toString
     */
    override fun toString(): String {
        var cache = string
        if (cache == null) {
            cache = toString(0)
            string = cache
        }
        return cache
    }

    /**
     * Translates the entire byte string to a [String] using the `charset` encoding.
     * @see toString
     */
    fun toString(start: Int): String {
        return toString(start, length)
    }

    /**
     * Translates the [`start`, `end`) range of this byte string to a [String].
     */
    fun toString(start: Int, end: Int): String {
        val length = end - start
        if (length == 0) {
            return ""
        }

        if (isOutOfBounds(start, length, this.length)) {
            throw IndexOutOfBoundsException(
                "expected: 0 <= start($start) <= srcIdx + length($length) <= srcLen(${this.length})"
            )
        }

        @Suppress("DEPRECATION")
        return java.lang.String(value, 0, start + offset, length) as String
    }

    fun parseBoolean(): Boolean {
        return length >= 1 && value[offset] != 0.toByte()
    }

    fun parseChar(): Char {
        return parseChar(0)
    }

    fun parseChar(start: Int): Char {
        if (start + 1 >= length) {
            throw IndexOutOfBoundsException(
                "2 bytes required to convert to character. index $start would go out of bounds."
            )
        }
        val startWithOffset = start + offset
        return ((b2c(value[startWithOffset]).code shl 8) or b2c(value[startWithOffset + 1]).code).toChar()
    }

    fun parseShort(): Short {
        return parseShort(0, length, 10)
    }

    fun parseShort(radix: Int): Short {
        return parseShort(0, length, radix)
    }

    fun parseShort(start: Int, end: Int): Short {
        return parseShort(start, end, 10)
    }

    fun parseShort(start: Int, end: Int, radix: Int): Short {
        val intValue = parseInt(start, end, radix)
        val result = intValue.toShort()
        if (result.toInt() != intValue) {
            throw NumberFormatException(subSequence(start, end, false).toString())
        }
        return result
    }

    fun parseInt(): Int {
        return parseInt(0, length, 10)
    }

    fun parseInt(radix: Int): Int {
        return parseInt(0, length, radix)
    }

    fun parseInt(start: Int, end: Int): Int {
        return parseInt(start, end, 10)
    }

    fun parseInt(start: Int, end: Int, radix: Int): Int {
        if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
            throw NumberFormatException()
        }

        if (start == end) {
            throw NumberFormatException()
        }

        var i = start
        val negative = byteAt(i) == '-'.code.toByte()
        if (negative && ++i == end) {
            throw NumberFormatException(subSequence(start, end, false).toString())
        }

        return parseInt(i, end, radix, negative)
    }

    private fun parseInt(start: Int, end: Int, radix: Int, negative: Boolean): Int {
        val max = Int.MIN_VALUE / radix
        var result = 0
        var currOffset = start
        while (currOffset < end) {
            val digit = Character.digit((value[currOffset++ + offset].toInt() and 0xFF).toChar(), radix)
            if (digit == -1) {
                throw NumberFormatException(subSequence(start, end, false).toString())
            }
            if (max > result) {
                throw NumberFormatException(subSequence(start, end, false).toString())
            }
            val next = result * radix - digit
            if (next > result) {
                throw NumberFormatException(subSequence(start, end, false).toString())
            }
            result = next
        }
        if (!negative) {
            result = -result
            if (result < 0) {
                throw NumberFormatException(subSequence(start, end, false).toString())
            }
        }
        return result
    }

    fun parseLong(): Long {
        return parseLong(0, length, 10)
    }

    fun parseLong(radix: Int): Long {
        return parseLong(0, length, radix)
    }

    fun parseLong(start: Int, end: Int): Long {
        return parseLong(start, end, 10)
    }

    fun parseLong(start: Int, end: Int, radix: Int): Long {
        if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
            throw NumberFormatException()
        }

        if (start == end) {
            throw NumberFormatException()
        }

        var i = start
        val negative = byteAt(i) == '-'.code.toByte()
        if (negative && ++i == end) {
            throw NumberFormatException(subSequence(start, end, false).toString())
        }

        return parseLong(i, end, radix, negative)
    }

    private fun parseLong(start: Int, end: Int, radix: Int, negative: Boolean): Long {
        val max = Long.MIN_VALUE / radix
        var result = 0L
        var currOffset = start
        while (currOffset < end) {
            val digit = Character.digit((value[currOffset++ + offset].toInt() and 0xFF).toChar(), radix)
            if (digit == -1) {
                throw NumberFormatException(subSequence(start, end, false).toString())
            }
            if (max > result) {
                throw NumberFormatException(subSequence(start, end, false).toString())
            }
            val next = result * radix - digit
            if (next > result) {
                throw NumberFormatException(subSequence(start, end, false).toString())
            }
            result = next
        }
        if (!negative) {
            result = -result
            if (result < 0) {
                throw NumberFormatException(subSequence(start, end, false).toString())
            }
        }
        return result
    }

    fun parseFloat(): Float {
        return parseFloat(0, length)
    }

    fun parseFloat(start: Int, end: Int): Float {
        return java.lang.Float.parseFloat(toString(start, end))
    }

    fun parseDouble(): Double {
        return parseDouble(0, length)
    }

    fun parseDouble(start: Int, end: Int): Double {
        return java.lang.Double.parseDouble(toString(start, end))
    }

    private interface CharEqualityComparator {
        fun equals(a: Char, b: Char): Boolean
    }

    private class DefaultCharEqualityComparator private constructor() : CharEqualityComparator {
        override fun equals(a: Char, b: Char): Boolean {
            return a == b
        }

        companion object {
            val INSTANCE = DefaultCharEqualityComparator()
        }
    }

    private class AsciiCaseInsensitiveCharEqualityComparator private constructor() : CharEqualityComparator {
        override fun equals(a: Char, b: Char): Boolean {
            return equalsIgnoreCase(a, b)
        }

        companion object {
            val INSTANCE = AsciiCaseInsensitiveCharEqualityComparator()
        }
    }

    private class GeneralCaseInsensitiveCharEqualityComparator private constructor() : CharEqualityComparator {
        override fun equals(a: Char, b: Char): Boolean {
            //For motivation, why we need two checks, see comment in String#regionMatches
            return Character.toUpperCase(a) == Character.toUpperCase(b) ||
                Character.toLowerCase(a) == Character.toLowerCase(b)
        }

        companion object {
            val INSTANCE = GeneralCaseInsensitiveCharEqualityComparator()
        }
    }

    companion object {
        @JvmField
        val EMPTY_STRING: AsciiString = cached("")

        private const val MAX_CHAR_VALUE: Char = '\u00FF'

        const val INDEX_NOT_FOUND: Int = -1

        @JvmField
        val CASE_INSENSITIVE_HASHER: HashingStrategy<CharSequence> =
            object : HashingStrategy<CharSequence> {
                override fun hashCode(o: CharSequence): Int {
                    return AsciiString.hashCode(o)
                }

                override fun equals(a: CharSequence, b: CharSequence): Boolean {
                    return contentEqualsIgnoreCase(a, b)
                }
            }

        @JvmField
        val CASE_SENSITIVE_HASHER: HashingStrategy<CharSequence> =
            object : HashingStrategy<CharSequence> {
                override fun hashCode(o: CharSequence): Int {
                    return AsciiString.hashCode(o)
                }

                override fun equals(a: CharSequence, b: CharSequence): Boolean {
                    return contentEquals(a, b)
                }
            }

        /**
         * Copies this string removing white space characters from the beginning and end of the string, and tries not to
         * copy if possible.
         *
         * @param c The [CharSequence] to trim.
         * @return a new string with characters `<= \\u0020` removed from the beginning and the end.
         */
        @JvmStatic
        fun trim(c: CharSequence): CharSequence {
            if (c is AsciiString) {
                return c.trim()
            }
            if (c is String) {
                return c.trim()
            }
            var start = 0
            val last = c.length - 1
            var end = last
            while (start <= end && c[start] <= ' ') {
                start++
            }
            while (end >= start && c[end] <= ' ') {
                end--
            }
            if (start == 0 && end == last) {
                return c
            }
            return c.subSequence(start, end)
        }

        /**
         * Returns an [AsciiString] containing the given character sequence. If the given string is already a
         * [AsciiString], just returns the same instance.
         */
        @JvmStatic
        fun of(string: CharSequence): AsciiString {
            return if (string is AsciiString) string else AsciiString(string)
        }

        /**
         * Returns an [AsciiString] containing the given string and retains/caches the input
         * string for later use in [toString].
         * Used for the constants (which already stored in the JVM's string table) and in cases
         * where the guaranteed use of the [toString] method.
         */
        @JvmStatic
        fun cached(string: String): AsciiString {
            val asciiString = AsciiString(string)
            asciiString.string = string
            return asciiString
        }

        /**
         * Returns the case-insensitive hash code of the specified string. Note that this method uses the same hashing
         * algorithm with [hashCode] so that you can put both [AsciiString]s and arbitrary
         * [CharSequence]s into the same headers.
         */
        @JvmStatic
        fun hashCode(value: CharSequence?): Int {
            if (value == null) {
                return 0
            }
            if (value is AsciiString) {
                return value.hashCode()
            }

            return PlatformDependent.hashCodeAscii(value)
        }

        /**
         * Determine if `a` contains `b` in a case sensitive manner.
         */
        @JvmStatic
        fun contains(a: CharSequence?, b: CharSequence?): Boolean {
            return contains(a, b, DefaultCharEqualityComparator.INSTANCE)
        }

        /**
         * Determine if `a` contains `b` in a case insensitive manner.
         */
        @JvmStatic
        fun containsIgnoreCase(a: CharSequence?, b: CharSequence?): Boolean {
            return contains(a, b, AsciiCaseInsensitiveCharEqualityComparator.INSTANCE)
        }

        /**
         * Returns `true` if both [CharSequence]'s are equals when ignore the case. This only supports 8-bit
         * ASCII.
         */
        @JvmStatic
        fun contentEqualsIgnoreCase(a: CharSequence?, b: CharSequence?): Boolean {
            if (a == null || b == null) {
                return a === b
            }

            if (a is AsciiString) {
                return a.contentEqualsIgnoreCase(b)
            }
            if (b is AsciiString) {
                return b.contentEqualsIgnoreCase(a)
            }

            if (a.length != b.length) {
                return false
            }
            for (i in 0 until a.length) {
                if (!equalsIgnoreCase(a[i], b[i])) {
                    return false
                }
            }
            return true
        }

        /**
         * Determine if `collection` contains `value` and using
         * [contentEqualsIgnoreCase] to compare values.
         * @param collection The collection to look for and equivalent element as `value`.
         * @param value The value to look for in `collection`.
         * @return `true` if `collection` contains `value` according to
         * [contentEqualsIgnoreCase]. `false` otherwise.
         * @see contentEqualsIgnoreCase
         */
        @JvmStatic
        fun containsContentEqualsIgnoreCase(collection: Collection<CharSequence>, value: CharSequence): Boolean {
            for (v in collection) {
                if (contentEqualsIgnoreCase(value, v)) {
                    return true
                }
            }
            return false
        }

        /**
         * Determine if `a` contains all of the values in `b` using
         * [contentEqualsIgnoreCase] to compare values.
         * @param a The collection under test.
         * @param b The values to test for.
         * @return `true` if `a` contains all of the values in `b` using
         * [contentEqualsIgnoreCase] to compare values. `false` otherwise.
         * @see contentEqualsIgnoreCase
         */
        @JvmStatic
        fun containsAllContentEqualsIgnoreCase(
            a: Collection<CharSequence>,
            b: Collection<CharSequence>
        ): Boolean {
            for (v in b) {
                if (!containsContentEqualsIgnoreCase(a, v)) {
                    return false
                }
            }
            return true
        }

        /**
         * Returns `true` if the content of both [CharSequence]'s are equals. This only supports 8-bit ASCII.
         */
        @JvmStatic
        fun contentEquals(a: CharSequence?, b: CharSequence?): Boolean {
            if (a == null || b == null) {
                return a === b
            }

            if (a is AsciiString) {
                return a.contentEquals(b)
            }

            if (b is AsciiString) {
                return b.contentEquals(a)
            }

            if (a.length != b.length) {
                return false
            }
            for (i in 0 until a.length) {
                if (a[i] != b[i]) {
                    return false
                }
            }
            return true
        }

        private fun toAsciiStringArray(jdkResult: Array<String>): Array<AsciiString> {
            return Array(jdkResult.size) { i -> AsciiString(jdkResult[i]) }
        }

        private fun contains(a: CharSequence?, b: CharSequence?, cmp: CharEqualityComparator): Boolean {
            if (a == null || b == null || a.length < b.length) {
                return false
            }
            if (b.isEmpty()) {
                return true
            }
            var bStart = 0
            for (i in 0 until a.length) {
                if (cmp.equals(b[bStart], a[i])) {
                    // If b is consumed then true.
                    if (++bStart == b.length) {
                        return true
                    }
                } else if (a.length - i < b.length) {
                    // If there are not enough characters left in a for b to be contained, then false.
                    return false
                } else {
                    bStart = 0
                }
            }
            return false
        }

        private fun regionMatchesCharSequences(
            cs: CharSequence, csStart: Int,
            string: CharSequence, start: Int, length: Int,
            charEqualityComparator: CharEqualityComparator
        ): Boolean {
            //general purpose implementation for CharSequences
            if (csStart < 0 || length > cs.length - csStart) {
                return false
            }
            if (start < 0 || length > string.length - start) {
                return false
            }

            var csIndex = csStart
            val csEnd = csIndex + length
            var stringIndex = start

            while (csIndex < csEnd) {
                val c1 = cs[csIndex++]
                val c2 = string[stringIndex++]

                if (!charEqualityComparator.equals(c1, c2)) {
                    return false
                }
            }
            return true
        }

        /**
         * This methods make regionMatches operation correctly for any chars in strings
         * @param cs the `CharSequence` to be processed
         * @param ignoreCase specifies if case should be ignored.
         * @param csStart the starting offset in the `cs` CharSequence
         * @param string the `CharSequence` to compare.
         * @param start the starting offset in the specified `string`.
         * @param length the number of characters to compare.
         * @return `true` if the ranges of characters are equal, `false` otherwise.
         */
        @JvmStatic
        fun regionMatches(
            cs: CharSequence?, ignoreCase: Boolean, csStart: Int,
            string: CharSequence?, start: Int, length: Int
        ): Boolean {
            if (cs == null || string == null) {
                return false
            }

            if (cs is String && string is String) {
                return cs.regionMatches(csStart, string, start, length, ignoreCase = ignoreCase)
            }

            if (cs is AsciiString) {
                return cs.regionMatches(ignoreCase, csStart, string, start, length)
            }

            return regionMatchesCharSequences(
                cs, csStart, string, start, length,
                if (ignoreCase) GeneralCaseInsensitiveCharEqualityComparator.INSTANCE
                else DefaultCharEqualityComparator.INSTANCE
            )
        }

        /**
         * This is optimized version of regionMatches for string with ASCII chars only
         * @param cs the `CharSequence` to be processed
         * @param ignoreCase specifies if case should be ignored.
         * @param csStart the starting offset in the `cs` CharSequence
         * @param string the `CharSequence` to compare.
         * @param start the starting offset in the specified `string`.
         * @param length the number of characters to compare.
         * @return `true` if the ranges of characters are equal, `false` otherwise.
         */
        @JvmStatic
        fun regionMatchesAscii(
            cs: CharSequence?, ignoreCase: Boolean, csStart: Int,
            string: CharSequence?, start: Int, length: Int
        ): Boolean {
            if (cs == null || string == null) {
                return false
            }

            if (!ignoreCase && cs is String && string is String) {
                //we don't call regionMatches from String for ignoreCase==true. It's a general purpose method,
                //which make complex comparison in case of ignoreCase==true, which is useless for ASCII-only strings.
                //To avoid applying this complex ignore-case comparison, we will use regionMatchesCharSequences
                return cs.regionMatches(csStart, string, start, length, ignoreCase = false)
            }

            if (cs is AsciiString) {
                return cs.regionMatches(ignoreCase, csStart, string, start, length)
            }

            return regionMatchesCharSequences(
                cs, csStart, string, start, length,
                if (ignoreCase) AsciiCaseInsensitiveCharEqualityComparator.INSTANCE
                else DefaultCharEqualityComparator.INSTANCE
            )
        }

        /**
         * Case in-sensitive find of the first index within a CharSequence
         * from the specified position.
         *
         * A `null` CharSequence will return `-1`.
         * A negative start position is treated as zero.
         * An empty ("") search CharSequence always matches.
         * A start position greater than the string length only matches
         * an empty search CharSequence.
         *
         * @param str  the CharSequence to check, may be null
         * @param searchStr  the CharSequence to find, may be null
         * @param startPos  the start position, negative treated as zero
         * @return the first index of the search CharSequence (always >= startPos),
         *  -1 if no match or `null` string input
         */
        @JvmStatic
        fun indexOfIgnoreCase(str: CharSequence?, searchStr: CharSequence?, startPos: Int): Int {
            if (str == null || searchStr == null) {
                return INDEX_NOT_FOUND
            }
            var start = startPos
            if (start < 0) {
                start = 0
            }
            val searchStrLen = searchStr.length
            val endLimit = str.length - searchStrLen + 1
            if (start > endLimit) {
                return INDEX_NOT_FOUND
            }
            if (searchStrLen == 0) {
                return start
            }
            for (i in start until endLimit) {
                if (regionMatches(str, true, i, searchStr, 0, searchStrLen)) {
                    return i
                }
            }
            return INDEX_NOT_FOUND
        }

        /**
         * Case in-sensitive find of the first index within a CharSequence
         * from the specified position. This method optimized and works correctly for ASCII CharSequences only.
         *
         * A `null` CharSequence will return `-1`.
         * A negative start position is treated as zero.
         * An empty ("") search CharSequence always matches.
         * A start position greater than the string length only matches
         * an empty search CharSequence.
         *
         * @param str  the CharSequence to check, may be null
         * @param searchStr  the CharSequence to find, may be null
         * @param startPos  the start position, negative treated as zero
         * @return the first index of the search CharSequence (always >= startPos),
         *  -1 if no match or `null` string input
         */
        @JvmStatic
        fun indexOfIgnoreCaseAscii(str: CharSequence?, searchStr: CharSequence?, startPos: Int): Int {
            if (str == null || searchStr == null) {
                return INDEX_NOT_FOUND
            }
            var start = startPos
            if (start < 0) {
                start = 0
            }
            val searchStrLen = searchStr.length
            val endLimit = str.length - searchStrLen + 1
            if (start > endLimit) {
                return INDEX_NOT_FOUND
            }
            if (searchStrLen == 0) {
                return start
            }
            for (i in start until endLimit) {
                if (regionMatchesAscii(str, true, i, searchStr, 0, searchStrLen)) {
                    return i
                }
            }
            return INDEX_NOT_FOUND
        }

        /**
         * Finds the first index in the `CharSequence` that matches the
         * specified character.
         *
         * @param cs  the `CharSequence` to be processed, not null
         * @param searchChar the char to be searched for
         * @param start  the start index, negative starts at the string start
         * @return the index where the search char was found,
         * -1 if char `searchChar` is not found or `cs == null`
         */
        @JvmStatic
        fun indexOf(cs: CharSequence?, searchChar: Char, start: Int): Int {
            if (cs is String) {
                return cs.indexOf(searchChar, start)
            } else if (cs is AsciiString) {
                return cs.indexOf(searchChar, start)
            }
            if (cs == null) {
                return INDEX_NOT_FOUND
            }
            val sz = cs.length
            for (i in (if (start < 0) 0 else start) until sz) {
                if (cs[i] == searchChar) {
                    return i
                }
            }
            return INDEX_NOT_FOUND
        }

        private fun equalsIgnoreCase(a: Byte, b: Byte): Boolean {
            return a == b || AsciiStringUtil.toLowerCase(a) == AsciiStringUtil.toLowerCase(b)
        }

        private fun equalsIgnoreCase(a: Char, b: Char): Boolean {
            return a == b || toLowerCase(a) == toLowerCase(b)
        }

        /**
         * If the character is uppercase - converts the character to lowercase,
         * otherwise returns the character as it is. Only for ASCII characters.
         *
         * @return lowercase ASCII character equivalent
         */
        @JvmStatic
        fun toLowerCase(c: Char): Char {
            return if (isUpperCase(c)) (c + 32) else c
        }

        private fun toUpperCase(b: Byte): Byte {
            return AsciiStringUtil.toUpperCase(b)
        }

        @JvmStatic
        fun isUpperCase(value: Byte): Boolean {
            return AsciiStringUtil.isUpperCase(value)
        }

        @JvmStatic
        fun isUpperCase(value: Char): Boolean {
            return value in 'A'..'Z'
        }

        @JvmStatic
        fun c2b(c: Char): Byte {
            return (if (c > MAX_CHAR_VALUE) '?'.code else c.code).toByte()
        }

        private fun c2b0(c: Char): Byte {
            return c.code.toByte()
        }

        @JvmStatic
        fun b2c(b: Byte): Char {
            return (b.toInt() and 0xFF).toChar()
        }
    }
}
