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
package io.netty.util.internal

import io.netty.util.internal.ObjectUtil.checkNotNull
import java.io.IOException
import java.util.Arrays

object StringUtil {

    const val EMPTY_STRING: String = ""

    @JvmField
    val NEWLINE: String = SystemPropertyUtil.get("line.separator", "\n") ?: "\n"

    const val DOUBLE_QUOTE: Char = '\"'
    const val COMMA: Char = ','
    const val LINE_FEED: Char = '\n'
    const val CARRIAGE_RETURN: Char = '\r'
    const val TAB: Char = '\t'
    const val SPACE: Char = 0x20.toChar()

    private val BYTE2HEX_PAD = arrayOfNulls<String>(256)
    private val BYTE2HEX_NOPAD = arrayOfNulls<String>(256)
    private val HEX2B: ByteArray

    private const val CSV_NUMBER_ESCAPE_CHARACTERS: Int = 2 + 5
    private const val PACKAGE_SEPARATOR_CHAR: Char = '.'

    init {
        for (i in BYTE2HEX_PAD.indices) {
            val str = Integer.toHexString(i)
            BYTE2HEX_PAD[i] = if (i > 0xf) str else ("0$str")
            BYTE2HEX_NOPAD[i] = str
        }
        HEX2B = ByteArray(Char.MAX_VALUE.code + 1)
        Arrays.fill(HEX2B, (-1).toByte())
        HEX2B['0'.code] = 0
        HEX2B['1'.code] = 1
        HEX2B['2'.code] = 2
        HEX2B['3'.code] = 3
        HEX2B['4'.code] = 4
        HEX2B['5'.code] = 5
        HEX2B['6'.code] = 6
        HEX2B['7'.code] = 7
        HEX2B['8'.code] = 8
        HEX2B['9'.code] = 9
        HEX2B['A'.code] = 10
        HEX2B['B'.code] = 11
        HEX2B['C'.code] = 12
        HEX2B['D'.code] = 13
        HEX2B['E'.code] = 14
        HEX2B['F'.code] = 15
        HEX2B['a'.code] = 10
        HEX2B['b'.code] = 11
        HEX2B['c'.code] = 12
        HEX2B['d'.code] = 13
        HEX2B['e'.code] = 14
        HEX2B['f'.code] = 15
    }

    @JvmStatic
    fun substringAfter(value: String, delim: Char): String? {
        val pos = value.indexOf(delim)
        return if (pos >= 0) value.substring(pos + 1) else null
    }

    @JvmStatic
    fun substringBefore(value: String, delim: Char): String? {
        val pos = value.indexOf(delim)
        return if (pos >= 0) value.substring(0, pos) else null
    }

    @JvmStatic
    fun commonSuffixOfLength(s: String?, p: String?, len: Int): Boolean {
        return s != null && p != null && len >= 0 &&
                s.regionMatches(s.length - len, p, p.length - len, len)
    }

    @JvmStatic
    fun byteToHexStringPadded(value: Int): String {
        return BYTE2HEX_PAD[value and 0xff]!!
    }

    @JvmStatic
    fun <T : Appendable> byteToHexStringPadded(buf: T, value: Int): T {
        try {
            buf.append(byteToHexStringPadded(value))
        } catch (e: IOException) {
            PlatformDependent.throwException(e)
        }
        return buf
    }

    @JvmStatic
    fun toHexStringPadded(src: ByteArray): String {
        return toHexStringPadded(src, 0, src.size)
    }

    @JvmStatic
    fun toHexStringPadded(src: ByteArray, offset: Int, length: Int): String {
        return toHexStringPadded(StringBuilder(length shl 1), src, offset, length).toString()
    }

    @JvmStatic
    fun <T : Appendable> toHexStringPadded(dst: T, src: ByteArray): T {
        return toHexStringPadded(dst, src, 0, src.size)
    }

    @JvmStatic
    fun <T : Appendable> toHexStringPadded(dst: T, src: ByteArray, offset: Int, length: Int): T {
        val end = offset + length
        for (i in offset until end) {
            byteToHexStringPadded(dst, src[i].toInt())
        }
        return dst
    }

    @JvmStatic
    fun byteToHexString(value: Int): String {
        return BYTE2HEX_NOPAD[value and 0xff]!!
    }

    @JvmStatic
    fun <T : Appendable> byteToHexString(buf: T, value: Int): T {
        try {
            buf.append(byteToHexString(value))
        } catch (e: IOException) {
            PlatformDependent.throwException(e)
        }
        return buf
    }

    @JvmStatic
    fun toHexString(src: ByteArray): String {
        return toHexString(src, 0, src.size)
    }

    @JvmStatic
    fun toHexString(src: ByteArray, offset: Int, length: Int): String {
        return toHexString(StringBuilder(length shl 1), src, offset, length).toString()
    }

    @JvmStatic
    fun <T : Appendable> toHexString(dst: T, src: ByteArray): T {
        return toHexString(dst, src, 0, src.size)
    }

    @JvmStatic
    fun <T : Appendable> toHexString(dst: T, src: ByteArray, offset: Int, length: Int): T {
        assert(length >= 0)
        if (length == 0) {
            return dst
        }
        val end = offset + length
        val endMinusOne = end - 1
        var i = offset
        while (i < endMinusOne) {
            if (src[i].toInt() and 0xff != 0) {
                break
            }
            i++
        }
        byteToHexString(dst, src[i].toInt())
        i++
        val remaining = end - i
        toHexStringPadded(dst, src, i, remaining)
        return dst
    }

    @JvmStatic
    fun decodeHexNibble(c: Char): Int {
        return HEX2B[c.code].toInt()
    }

    @JvmStatic
    fun decodeHexNibble(b: Byte): Int {
        return HEX2B[b.toInt() and 0xff].toInt()
    }

    @JvmStatic
    fun decodeHexByte(s: CharSequence, pos: Int): Byte {
        val hi = decodeHexNibble(s[pos])
        val lo = decodeHexNibble(s[pos + 1])
        if (hi == -1 || lo == -1) {
            throw IllegalArgumentException(
                String.format(
                    "invalid hex byte '%s' at index %d of '%s'",
                    s.subSequence(pos, pos + 2), pos, s
                )
            )
        }
        return ((hi shl 4) + lo).toByte()
    }

    @JvmStatic
    fun decodeHexDump(hexDump: CharSequence, fromIndex: Int, length: Int): ByteArray {
        if (length < 0 || (length and 1) != 0) {
            throw IllegalArgumentException("length: $length")
        }
        if (length == 0) {
            return EmptyArrays.EMPTY_BYTES
        }
        val bytes = ByteArray(length ushr 1)
        var i = 0
        while (i < length) {
            bytes[i ushr 1] = decodeHexByte(hexDump, fromIndex + i)
            i += 2
        }
        return bytes
    }

    @JvmStatic
    fun decodeHexDump(hexDump: CharSequence): ByteArray {
        return decodeHexDump(hexDump, 0, hexDump.length)
    }

    @JvmStatic
    fun className(o: Any?): String {
        return if (o == null) "null_object" else o.javaClass.name
    }

    @JvmStatic
    fun simpleClassName(o: Any?): String {
        return if (o == null) "null_object" else simpleClassName(o.javaClass)
    }

    @JvmStatic
    fun simpleClassName(clazz: Class<*>): String {
        val className = checkNotNull(clazz, "clazz").name
        val lastDotIdx = className.lastIndexOf(PACKAGE_SEPARATOR_CHAR)
        return if (lastDotIdx > -1) className.substring(lastDotIdx + 1) else className
    }

    @JvmStatic
    fun escapeCsv(value: CharSequence): CharSequence {
        return escapeCsv(value, false)
    }

    @JvmStatic
    fun escapeCsv(value: CharSequence, trimWhiteSpace: Boolean): CharSequence {
        val length = checkNotNull(value, "value").length
        val start: Int
        val last: Int
        if (trimWhiteSpace) {
            start = indexOfFirstNonOwsChar(value, length)
            last = indexOfLastNonOwsChar(value, start, length)
        } else {
            start = 0
            last = length - 1
        }
        if (start > last) {
            return EMPTY_STRING
        }

        var mutableStart = start
        var mutableLast = last
        var firstUnescapedSpecial = -1
        var quoted = false
        if (isDoubleQuote(value[mutableStart])) {
            quoted = isDoubleQuote(value[mutableLast]) && mutableLast > mutableStart
            if (quoted) {
                mutableStart++
                mutableLast--
            } else {
                firstUnescapedSpecial = mutableStart
            }
        }

        if (firstUnescapedSpecial < 0) {
            if (quoted) {
                var i = mutableStart
                while (i <= mutableLast) {
                    if (isDoubleQuote(value[i])) {
                        if (i == mutableLast || !isDoubleQuote(value[i + 1])) {
                            firstUnescapedSpecial = i
                            break
                        }
                        i++
                    }
                    i++
                }
            } else {
                var i = mutableStart
                while (i <= mutableLast) {
                    val c = value[i]
                    if (c == LINE_FEED || c == CARRIAGE_RETURN || c == COMMA) {
                        firstUnescapedSpecial = i
                        break
                    }
                    if (isDoubleQuote(c)) {
                        if (i == mutableLast || !isDoubleQuote(value[i + 1])) {
                            firstUnescapedSpecial = i
                            break
                        }
                        i++
                    }
                    i++
                }
            }

            if (firstUnescapedSpecial < 0) {
                return if (quoted) {
                    value.subSequence(mutableStart - 1, mutableLast + 2)
                } else {
                    value.subSequence(mutableStart, mutableLast + 1)
                }
            }
        }

        val result = StringBuilder(mutableLast - mutableStart + 1 + CSV_NUMBER_ESCAPE_CHARACTERS)
        result.append(DOUBLE_QUOTE).append(value, mutableStart, firstUnescapedSpecial)
        var i = firstUnescapedSpecial
        while (i <= mutableLast) {
            val c = value[i]
            if (isDoubleQuote(c)) {
                result.append(DOUBLE_QUOTE)
                if (i < mutableLast && isDoubleQuote(value[i + 1])) {
                    i++
                }
            }
            result.append(c)
            i++
        }
        return result.append(DOUBLE_QUOTE)
    }

    @JvmStatic
    fun unescapeCsv(value: CharSequence): CharSequence {
        val length = checkNotNull(value, "value").length
        if (length == 0) {
            return value
        }
        val last = length - 1
        val quoted = isDoubleQuote(value[0]) && isDoubleQuote(value[last]) && length != 1
        if (!quoted) {
            validateCsvFormat(value)
            return value
        }
        val unescaped = InternalThreadLocalMap.get().stringBuilder()
        var i = 1
        while (i < last) {
            val current = value[i]
            if (current == DOUBLE_QUOTE) {
                if (isDoubleQuote(value[i + 1]) && (i + 1) != last) {
                    i++
                } else {
                    throw newInvalidEscapedCsvFieldException(value, i)
                }
            }
            unescaped.append(current)
            i++
        }
        return unescaped.toString()
    }

    @JvmStatic
    fun unescapeCsvFields(value: CharSequence): List<CharSequence> {
        val unescaped = ArrayList<CharSequence>(2)
        val current = InternalThreadLocalMap.get().stringBuilder()
        var quoted = false
        val last = value.length - 1
        var i = 0
        while (i <= last) {
            val c = value[i]
            if (quoted) {
                when (c) {
                    DOUBLE_QUOTE -> {
                        if (i == last) {
                            unescaped.add(current.toString())
                            return unescaped
                        }
                        val next = value[++i]
                        if (next == DOUBLE_QUOTE) {
                            current.append(DOUBLE_QUOTE)
                        } else if (next == COMMA) {
                            quoted = false
                            unescaped.add(current.toString())
                            current.setLength(0)
                        } else {
                            throw newInvalidEscapedCsvFieldException(value, i - 1)
                        }
                    }
                    else -> current.append(c)
                }
            } else {
                when (c) {
                    COMMA -> {
                        unescaped.add(current.toString())
                        current.setLength(0)
                    }
                    DOUBLE_QUOTE -> {
                        if (current.isEmpty()) {
                            quoted = true
                        } else {
                            throw newInvalidEscapedCsvFieldException(value, i)
                        }
                    }
                    LINE_FEED, CARRIAGE_RETURN -> throw newInvalidEscapedCsvFieldException(value, i)
                    else -> current.append(c)
                }
            }
            i++
        }
        if (quoted) {
            throw newInvalidEscapedCsvFieldException(value, last)
        }
        unescaped.add(current.toString())
        return unescaped
    }

    private fun validateCsvFormat(value: CharSequence) {
        val length = value.length
        for (i in 0 until length) {
            when (value[i]) {
                DOUBLE_QUOTE, LINE_FEED, CARRIAGE_RETURN, COMMA ->
                    throw newInvalidEscapedCsvFieldException(value, i)
            }
        }
    }

    private fun newInvalidEscapedCsvFieldException(value: CharSequence, index: Int): IllegalArgumentException {
        return IllegalArgumentException("invalid escaped CSV field: $value index: $index")
    }

    @JvmStatic
    fun length(s: String?): Int {
        return s?.length ?: 0
    }

    @JvmStatic
    fun isNullOrEmpty(s: String?): Boolean {
        return s.isNullOrEmpty()
    }

    @JvmStatic
    fun indexOfNonWhiteSpace(seq: CharSequence, offset: Int): Int {
        var i = offset
        while (i < seq.length) {
            if (!Character.isWhitespace(seq[i])) {
                return i
            }
            i++
        }
        return -1
    }

    @JvmStatic
    fun indexOfWhiteSpace(seq: CharSequence, offset: Int): Int {
        var i = offset
        while (i < seq.length) {
            if (Character.isWhitespace(seq[i])) {
                return i
            }
            i++
        }
        return -1
    }

    @JvmStatic
    fun isSurrogate(c: Char): Boolean {
        return c in '\uD800'..'\uDFFF'
    }

    private fun isDoubleQuote(c: Char): Boolean {
        return c == DOUBLE_QUOTE
    }

    @JvmStatic
    fun endsWith(s: CharSequence, c: Char): Boolean {
        val len = s.length
        return len > 0 && s[len - 1] == c
    }

    @JvmStatic
    fun trimOws(value: CharSequence): CharSequence {
        val length = value.length
        if (length == 0) {
            return value
        }
        val start = indexOfFirstNonOwsChar(value, length)
        val end = indexOfLastNonOwsChar(value, start, length)
        return if (start == 0 && end == length - 1) value else value.subSequence(start, end + 1)
    }

    @JvmStatic
    fun join(separator: CharSequence, elements: Iterable<CharSequence>): CharSequence {
        ObjectUtil.checkNotNull(separator, "separator")
        ObjectUtil.checkNotNull(elements, "elements")
        val iterator = elements.iterator()
        if (!iterator.hasNext()) {
            return EMPTY_STRING
        }
        val firstElement = iterator.next()
        if (!iterator.hasNext()) {
            return firstElement
        }
        val builder = StringBuilder(firstElement)
        do {
            builder.append(separator).append(iterator.next())
        } while (iterator.hasNext())
        return builder
    }

    private fun indexOfFirstNonOwsChar(value: CharSequence, length: Int): Int {
        var i = 0
        while (i < length && isOws(value[i])) {
            i++
        }
        return i
    }

    private fun indexOfLastNonOwsChar(value: CharSequence, start: Int, length: Int): Int {
        var i = length - 1
        while (i > start && isOws(value[i])) {
            i--
        }
        return i
    }

    private fun isOws(c: Char): Boolean {
        return c == SPACE || c == TAB
    }
}
