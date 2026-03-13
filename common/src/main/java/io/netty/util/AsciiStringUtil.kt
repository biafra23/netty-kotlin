/*
 * Copyright 2024 The Netty Project
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

import io.netty.util.internal.PlatformDependent
import io.netty.util.internal.SWARUtil

/**
 * A collection of utility methods that is related with handling [AsciiString].
 */
internal object AsciiStringUtil {

    /**
     * Convert the [AsciiString] to a lower case.
     *
     * @param string the [AsciiString] to convert
     * @return the new [AsciiString] in lower case
     */
    @JvmStatic
    fun toLowerCase(string: AsciiString): AsciiString {
        val byteArray = string.array()
        val offset = string.arrayOffset()
        val length = string.length
        if (!containsUpperCase(byteArray, offset, length)) {
            return string
        }
        val newByteArray = PlatformDependent.allocateUninitializedArray(length)
        toLowerCase(byteArray, offset, newByteArray)
        return AsciiString(newByteArray, false)
    }

    private fun containsUpperCase(byteArray: ByteArray, offset: Int, length: Int): Boolean {
        var off = offset
        if (!PlatformDependent.isUnaligned()) {
            return linearContainsUpperCase(byteArray, off, length)
        }

        val longCount = length ushr 3
        for (i in 0 until longCount) {
            val word = PlatformDependent.getLong(byteArray, off)
            if (SWARUtil.containsUpperCase(word)) {
                return true
            }
            off += java.lang.Long.BYTES
        }
        return unrolledContainsUpperCase(byteArray, off, length and 7)
    }

    private fun linearContainsUpperCase(byteArray: ByteArray, offset: Int, length: Int): Boolean {
        val end = offset + length
        for (idx in offset until end) {
            if (isUpperCase(byteArray[idx])) {
                return true
            }
        }
        return false
    }

    private fun unrolledContainsUpperCase(byteArray: ByteArray, offset: Int, byteCount: Int): Boolean {
        assert(byteCount >= 0 && byteCount < 8)
        var off = offset
        if ((byteCount and Int.SIZE_BYTES) != 0) {
            val word = PlatformDependent.getInt(byteArray, off)
            if (SWARUtil.containsUpperCase(word)) {
                return true
            }
            off += Int.SIZE_BYTES
        }
        if ((byteCount and Short.SIZE_BYTES) != 0) {
            if (isUpperCase(PlatformDependent.getByte(byteArray, off))) {
                return true
            }
            if (isUpperCase(PlatformDependent.getByte(byteArray, off + 1))) {
                return true
            }
            off += Short.SIZE_BYTES
        }
        if ((byteCount and Byte.SIZE_BYTES) != 0) {
            return isUpperCase(PlatformDependent.getByte(byteArray, off))
        }
        return false
    }

    private fun toLowerCase(src: ByteArray, srcOffset: Int, dst: ByteArray) {
        if (!PlatformDependent.isUnaligned()) {
            linearToLowerCase(src, srcOffset, dst)
            return
        }

        val length = dst.size
        val longCount = length ushr 3
        var offset = 0
        for (i in 0 until longCount) {
            val word = PlatformDependent.getLong(src, srcOffset + offset)
            PlatformDependent.putLong(dst, offset, SWARUtil.toLowerCase(word))
            offset += java.lang.Long.BYTES
        }
        unrolledToLowerCase(src, srcOffset + offset, dst, offset, length and 7)
    }

    private fun linearToLowerCase(src: ByteArray, srcOffset: Int, dst: ByteArray) {
        for (i in dst.indices) {
            dst[i] = toLowerCase(src[srcOffset + i])
        }
    }

    private fun unrolledToLowerCase(src: ByteArray, srcPos: Int, dst: ByteArray, dstOffset: Int, byteCount: Int) {
        assert(byteCount >= 0 && byteCount < 8)
        var offset = 0
        if ((byteCount and Int.SIZE_BYTES) != 0) {
            val word = PlatformDependent.getInt(src, srcPos + offset)
            PlatformDependent.putInt(dst, dstOffset + offset, SWARUtil.toLowerCase(word))
            offset += Int.SIZE_BYTES
        }

        if ((byteCount and Short.SIZE_BYTES) != 0) {
            val word = PlatformDependent.getShort(src, srcPos + offset)
            val result = ((toLowerCase((word.toInt() ushr 8).toByte()).toInt() shl 8) or
                    (toLowerCase(word.toByte()).toInt() and 0xFF)).toShort()
            PlatformDependent.putShort(dst, dstOffset + offset, result)
            offset += Short.SIZE_BYTES
        }

        // this is equivalent to byteCount >= Byte.BYTES (i.e. whether byteCount is odd)
        if ((byteCount and Byte.SIZE_BYTES) != 0) {
            PlatformDependent.putByte(dst, dstOffset + offset,
                toLowerCase(PlatformDependent.getByte(src, srcPos + offset)))
        }
    }

    /**
     * Convert the [AsciiString] to a upper case.
     *
     * @param string the [AsciiString] to convert
     * @return the [AsciiString] in upper case
     */
    @JvmStatic
    fun toUpperCase(string: AsciiString): AsciiString {
        val byteArray = string.array()
        val offset = string.arrayOffset()
        val length = string.length
        if (!containsLowerCase(byteArray, offset, length)) {
            return string
        }
        val newByteArray = PlatformDependent.allocateUninitializedArray(length)
        toUpperCase(byteArray, offset, newByteArray)
        return AsciiString(newByteArray, false)
    }

    private fun containsLowerCase(byteArray: ByteArray, offset: Int, length: Int): Boolean {
        var off = offset
        if (!PlatformDependent.isUnaligned()) {
            return linearContainsLowerCase(byteArray, off, length)
        }

        val longCount = length ushr 3
        for (i in 0 until longCount) {
            val word = PlatformDependent.getLong(byteArray, off)
            if (SWARUtil.containsLowerCase(word)) {
                return true
            }
            off += java.lang.Long.BYTES
        }
        return unrolledContainsLowerCase(byteArray, off, length and 7)
    }

    private fun linearContainsLowerCase(byteArray: ByteArray, offset: Int, length: Int): Boolean {
        val end = offset + length
        for (idx in offset until end) {
            if (isLowerCase(byteArray[idx])) {
                return true
            }
        }
        return false
    }

    private fun unrolledContainsLowerCase(byteArray: ByteArray, offset: Int, byteCount: Int): Boolean {
        assert(byteCount >= 0 && byteCount < 8)
        var off = offset
        if ((byteCount and Int.SIZE_BYTES) != 0) {
            val word = PlatformDependent.getInt(byteArray, off)
            if (SWARUtil.containsLowerCase(word)) {
                return true
            }
            off += Int.SIZE_BYTES
        }
        if ((byteCount and Short.SIZE_BYTES) != 0) {
            if (isLowerCase(PlatformDependent.getByte(byteArray, off))) {
                return true
            }
            if (isLowerCase(PlatformDependent.getByte(byteArray, off + 1))) {
                return true
            }
            off += Short.SIZE_BYTES
        }
        if ((byteCount and Byte.SIZE_BYTES) != 0) {
            return isLowerCase(PlatformDependent.getByte(byteArray, off))
        }
        return false
    }

    private fun toUpperCase(src: ByteArray, srcOffset: Int, dst: ByteArray) {
        if (!PlatformDependent.isUnaligned()) {
            linearToUpperCase(src, srcOffset, dst)
            return
        }

        val length = dst.size
        val longCount = length ushr 3
        var offset = 0
        for (i in 0 until longCount) {
            val word = PlatformDependent.getLong(src, srcOffset + offset)
            PlatformDependent.putLong(dst, offset, SWARUtil.toUpperCase(word))
            offset += java.lang.Long.BYTES
        }
        unrolledToUpperCase(src, srcOffset + offset, dst, offset, length and 7)
    }

    private fun linearToUpperCase(src: ByteArray, srcOffset: Int, dst: ByteArray) {
        for (i in dst.indices) {
            dst[i] = toUpperCase(src[srcOffset + i])
        }
    }

    private fun unrolledToUpperCase(src: ByteArray, srcOffset: Int, dst: ByteArray, dstOffset: Int, byteCount: Int) {
        assert(byteCount >= 0 && byteCount < 8)
        var offset = 0
        if ((byteCount and Int.SIZE_BYTES) != 0) {
            val word = PlatformDependent.getInt(src, srcOffset + offset)
            PlatformDependent.putInt(dst, dstOffset + offset, SWARUtil.toUpperCase(word))
            offset += Int.SIZE_BYTES
        }
        if ((byteCount and Short.SIZE_BYTES) != 0) {
            val word = PlatformDependent.getShort(src, srcOffset + offset)
            val result = ((toUpperCase((word.toInt() ushr 8).toByte()).toInt() shl 8) or
                    (toUpperCase(word.toByte()).toInt() and 0xFF)).toShort()
            PlatformDependent.putShort(dst, dstOffset + offset, result)
            offset += Short.SIZE_BYTES
        }

        if ((byteCount and Byte.SIZE_BYTES) != 0) {
            PlatformDependent.putByte(dst, dstOffset + offset,
                toUpperCase(PlatformDependent.getByte(src, srcOffset + offset)))
        }
    }

    private fun isLowerCase(value: Byte): Boolean {
        return value >= 'a'.code.toByte() && value <= 'z'.code.toByte()
    }

    /**
     * Check if the given byte is upper case.
     *
     * @param value the byte to check
     * @return `true` if the byte is upper case, `false` otherwise.
     */
    @JvmStatic
    fun isUpperCase(value: Byte): Boolean {
        return value >= 'A'.code.toByte() && value <= 'Z'.code.toByte()
    }

    /**
     * Convert the given byte to lower case.
     *
     * @param value the byte to convert
     * @return the lower case byte
     */
    @JvmStatic
    fun toLowerCase(value: Byte): Byte {
        return if (isUpperCase(value)) (value + 32).toByte() else value
    }

    /**
     * Convert the given byte to upper case.
     *
     * @param value the byte to convert
     * @return the upper case byte
     */
    @JvmStatic
    fun toUpperCase(value: Byte): Byte {
        return if (isLowerCase(value)) (value - 32).toByte() else value
    }
}
