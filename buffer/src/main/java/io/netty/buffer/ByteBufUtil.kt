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

import io.netty.util.AsciiString
import io.netty.util.ByteProcessor
import io.netty.util.CharsetUtil
import io.netty.util.IllegalReferenceCountException
import io.netty.util.Recycler
import io.netty.util.Recycler.EnhancedHandle
import io.netty.util.ResourceLeakDetector
import io.netty.util.concurrent.FastThreadLocal
import io.netty.util.concurrent.FastThreadLocalThread
import io.netty.util.internal.MathUtil.isOutOfBounds
import io.netty.util.internal.ObjectPool.Handle
import io.netty.util.internal.PlatformDependent
import io.netty.util.internal.SWARUtil
import io.netty.util.internal.StringUtil
import io.netty.util.internal.StringUtil.NEWLINE
import io.netty.util.internal.StringUtil.isSurrogate
import io.netty.util.internal.SystemPropertyUtil
import io.netty.util.internal.logging.InternalLoggerFactory
import io.netty.util.internal.ObjectUtil.checkNotNull
import io.netty.util.internal.ObjectUtil.checkPositiveOrZero
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Arrays

/**
 * A collection of utility methods that is related with handling [ByteBuf],
 * such as the generation of hex dump and swapping an integer's byte order.
 */
object ByteBufUtil {

    private val logger = InternalLoggerFactory.getInstance(ByteBufUtil::class.java)

    private val BYTE_ARRAYS: FastThreadLocal<ByteArray> = object : FastThreadLocal<ByteArray>() {
        @Throws(Exception::class)
        override fun initialValue(): ByteArray {
            return PlatformDependent.allocateUninitializedArray(MAX_TL_ARRAY_LEN)
        }
    }

    private const val WRITE_UTF_UNKNOWN: Byte = '?'.code.toByte()
    private val MAX_CHAR_BUFFER_SIZE: Int
    private val THREAD_LOCAL_BUFFER_SIZE: Int
    private val MAX_BYTES_PER_CHAR_UTF8: Int =
        CharsetUtil.encoder(CharsetUtil.UTF_8).maxBytesPerChar().toInt()

    @JvmField
    internal val WRITE_CHUNK_SIZE: Int = 8192

    @JvmField
    internal val DEFAULT_ALLOCATOR: ByteBufAllocator

    private val SWAR_UNALIGNED: Boolean = PlatformDependent.canUnalignedAccess()

    init {
        val allocType = SystemPropertyUtil.get(
            "io.netty.allocator.type", "adaptive"
        ) ?: "adaptive"

        val alloc: ByteBufAllocator
        if ("unpooled" == allocType) {
            alloc = UnpooledByteBufAllocator.DEFAULT
            logger.debug("-Dio.netty.allocator.type: {}", allocType)
        } else if ("pooled" == allocType) {
            alloc = PooledByteBufAllocator.DEFAULT
            logger.debug("-Dio.netty.allocator.type: {}", allocType)
        } else if ("adaptive" == allocType) {
            alloc = AdaptiveByteBufAllocator()
            logger.debug("-Dio.netty.allocator.type: {}", allocType)
        } else {
            alloc = PooledByteBufAllocator.DEFAULT
            logger.debug("-Dio.netty.allocator.type: pooled (unknown: {})", allocType)
        }

        DEFAULT_ALLOCATOR = alloc

        THREAD_LOCAL_BUFFER_SIZE = SystemPropertyUtil.getInt("io.netty.threadLocalDirectBufferSize", 0)
        logger.debug("-Dio.netty.threadLocalDirectBufferSize: {}", THREAD_LOCAL_BUFFER_SIZE)

        MAX_CHAR_BUFFER_SIZE = SystemPropertyUtil.getInt("io.netty.maxThreadLocalCharBufferSize", 16 * 1024)
        logger.debug("-Dio.netty.maxThreadLocalCharBufferSize: {}", MAX_CHAR_BUFFER_SIZE)
    }

    @JvmField
    internal val MAX_TL_ARRAY_LEN: Int = 1024

    /**
     * Allocates a new array if minLength > [ByteBufUtil.MAX_TL_ARRAY_LEN]
     */
    @JvmStatic
    internal fun threadLocalTempArray(minLength: Int): ByteArray {
        // Only make use of ThreadLocal if we use a FastThreadLocalThread to make the implementation
        // Virtual Thread friendly.
        // See https://github.com/netty/netty/issues/14609
        if (minLength <= MAX_TL_ARRAY_LEN && FastThreadLocalThread.currentThreadHasFastThreadLocal()) {
            return BYTE_ARRAYS.get()
        }
        return PlatformDependent.allocateUninitializedArray(minLength)
    }

    /**
     * @return whether the specified buffer has a nonzero ref count
     */
    @JvmStatic
    fun isAccessible(buffer: ByteBuf): Boolean {
        return buffer.isAccessible()
    }

    /**
     * @throws IllegalReferenceCountException if the buffer has a zero ref count
     * @return the passed in buffer
     */
    @JvmStatic
    fun ensureAccessible(buffer: ByteBuf): ByteBuf {
        if (!buffer.isAccessible()) {
            throw IllegalReferenceCountException(buffer.refCnt())
        }
        return buffer
    }

    /**
     * Returns a [hex dump](https://en.wikipedia.org/wiki/Hex_dump)
     * of the specified buffer's readable bytes.
     */
    @JvmStatic
    fun hexDump(buffer: ByteBuf): String {
        return hexDump(buffer, buffer.readerIndex(), buffer.readableBytes())
    }

    /**
     * Returns a [hex dump](https://en.wikipedia.org/wiki/Hex_dump)
     * of the specified buffer's sub-region.
     */
    @JvmStatic
    fun hexDump(buffer: ByteBuf, fromIndex: Int, length: Int): String {
        return HexUtil.hexDump(buffer, fromIndex, length)
    }

    /**
     * Returns a [hex dump](https://en.wikipedia.org/wiki/Hex_dump)
     * of the specified byte array.
     */
    @JvmStatic
    fun hexDump(array: ByteArray): String {
        return hexDump(array, 0, array.size)
    }

    /**
     * Returns a [hex dump](https://en.wikipedia.org/wiki/Hex_dump)
     * of the specified byte array's sub-region.
     */
    @JvmStatic
    fun hexDump(array: ByteArray, fromIndex: Int, length: Int): String {
        return HexUtil.hexDump(array, fromIndex, length)
    }

    /**
     * Decode a 2-digit hex byte from within a string.
     */
    @JvmStatic
    fun decodeHexByte(s: CharSequence, pos: Int): Byte {
        return StringUtil.decodeHexByte(s, pos)
    }

    /**
     * Decodes a string generated by [.hexDump]
     */
    @JvmStatic
    fun decodeHexDump(hexDump: CharSequence): ByteArray {
        return StringUtil.decodeHexDump(hexDump, 0, hexDump.length)
    }

    /**
     * Decodes part of a string generated by [.hexDump]
     */
    @JvmStatic
    fun decodeHexDump(hexDump: CharSequence, fromIndex: Int, length: Int): ByteArray {
        return StringUtil.decodeHexDump(hexDump, fromIndex, length)
    }

    /**
     * Used to determine if the return value of [ByteBuf.ensureWritable] means that there is
     * adequate space and a write operation will succeed.
     * @param ensureWritableResult The return value from [ByteBuf.ensureWritable].
     * @return `true` if `ensureWritableResult` means that there is adequate space and a write operation
     * will succeed.
     */
    @JvmStatic
    fun ensureWritableSuccess(ensureWritableResult: Int): Boolean {
        return ensureWritableResult == 0 || ensureWritableResult == 2
    }

    /**
     * Calculates the hash code of the specified buffer.  This method is
     * useful when implementing a new buffer type.
     */
    @JvmStatic
    fun hashCode(buffer: ByteBuf): Int {
        val aLen = buffer.readableBytes()
        val intCount = aLen ushr 2
        val byteCount = aLen and 3

        var hashCode = EmptyByteBuf.EMPTY_BYTE_BUF_HASH_CODE
        var arrayIndex = buffer.readerIndex()
        if (buffer.order() == ByteOrder.BIG_ENDIAN) {
            for (i in intCount downTo 1) {
                hashCode = 31 * hashCode + buffer.getInt(arrayIndex)
                arrayIndex += 4
            }
        } else {
            for (i in intCount downTo 1) {
                hashCode = 31 * hashCode + swapInt(buffer.getInt(arrayIndex))
                arrayIndex += 4
            }
        }

        for (i in byteCount downTo 1) {
            hashCode = 31 * hashCode + buffer.getByte(arrayIndex++)
        }

        if (hashCode == 0) {
            hashCode = 1
        }

        return hashCode
    }

    /**
     * Returns the reader index of needle in haystack, or -1 if needle is not in haystack.
     * This method uses the [Two-Way
     * string matching algorithm](https://en.wikipedia.org/wiki/Two-way_string-matching_algorithm), which yields O(1) space complexity and excellent performance.
     */
    @JvmStatic
    fun indexOf(needle: ByteBuf?, haystack: ByteBuf?): Int {
        if (haystack == null || needle == null) {
            return -1
        }

        if (needle.readableBytes() > haystack.readableBytes()) {
            return -1
        }

        val n = haystack.readableBytes()
        val m = needle.readableBytes()
        if (m == 0) {
            return 0
        }

        // When the needle has only one byte that can be read,
        // the ByteBuf.indexOf() can be used
        if (m == 1) {
            return haystack.indexOf(
                haystack.readerIndex(), haystack.writerIndex(),
                needle.getByte(needle.readerIndex())
            )
        }

        var i: Int
        var j = 0
        val aStartIndex = needle.readerIndex()
        val bStartIndex = haystack.readerIndex()
        val suffixes = maxSuf(needle, m, aStartIndex, true)
        val prefixes = maxSuf(needle, m, aStartIndex, false)
        val ell = Math.max((suffixes shr 32).toInt(), (prefixes shr 32).toInt())
        var per = Math.max(suffixes.toInt(), prefixes.toInt())
        var memory: Int
        val length = Math.min(m - per, ell + 1)

        if (equals(needle, aStartIndex, needle, aStartIndex + per, length)) {
            memory = -1
            while (j <= n - m) {
                i = Math.max(ell, memory) + 1
                while (i < m && needle.getByte(i + aStartIndex) == haystack.getByte(i + j + bStartIndex)) {
                    ++i
                }
                if (i > n) {
                    return -1
                }
                if (i >= m) {
                    i = ell
                    while (i > memory && needle.getByte(i + aStartIndex) == haystack.getByte(i + j + bStartIndex)) {
                        --i
                    }
                    if (i <= memory) {
                        return j + bStartIndex
                    }
                    j += per
                    memory = m - per - 1
                } else {
                    j += i - ell
                    memory = -1
                }
            }
        } else {
            per = Math.max(ell + 1, m - ell - 1) + 1
            while (j <= n - m) {
                i = ell + 1
                while (i < m && needle.getByte(i + aStartIndex) == haystack.getByte(i + j + bStartIndex)) {
                    ++i
                }
                if (i > n) {
                    return -1
                }
                if (i >= m) {
                    i = ell
                    while (i >= 0 && needle.getByte(i + aStartIndex) == haystack.getByte(i + j + bStartIndex)) {
                        --i
                    }
                    if (i < 0) {
                        return j + bStartIndex
                    }
                    j += per
                } else {
                    j += i - ell
                }
            }
        }
        return -1
    }

    private fun maxSuf(x: ByteBuf, m: Int, start: Int, isSuffix: Boolean): Long {
        var p = 1
        var ms = -1
        var j = start
        var k = 1
        while (j + k < m) {
            val a = x.getByte(j + k)
            val b = x.getByte(ms + k)
            val suffix = if (isSuffix) a < b else a > b
            if (suffix) {
                j += k
                k = 1
                p = j - ms
            } else if (a == b) {
                if (k != p) {
                    ++k
                } else {
                    j += p
                    k = 1
                }
            } else {
                ms = j
                j = ms + 1
                p = 1
                k = 1
            }
        }
        return (ms.toLong() shl 32) + p
    }

    /**
     * Returns `true` if and only if the two specified buffers are
     * identical to each other for `length` bytes starting at `aStartIndex`
     * index for the `a` buffer and `bStartIndex` index for the `b` buffer.
     * A more compact way to express this is:
     *
     * `a[aStartIndex : aStartIndex + length] == b[bStartIndex : bStartIndex + length]`
     */
    @JvmStatic
    fun equals(a: ByteBuf, aStartIndex: Int, b: ByteBuf, bStartIndex: Int, length: Int): Boolean {
        var aIdx = aStartIndex
        var bIdx = bStartIndex
        checkNotNull(a, "a")
        checkNotNull(b, "b")
        // All indexes and lengths must be non-negative
        checkPositiveOrZero(aIdx, "aStartIndex")
        checkPositiveOrZero(bIdx, "bStartIndex")
        checkPositiveOrZero(length, "length")

        if (a.writerIndex() - length < aIdx || b.writerIndex() - length < bIdx) {
            return false
        }

        val longCount = length ushr 3
        val byteCount = length and 7

        if (a.order() == b.order()) {
            for (i in longCount downTo 1) {
                if (a.getLong(aIdx) != b.getLong(bIdx)) {
                    return false
                }
                aIdx += 8
                bIdx += 8
            }
        } else {
            for (i in longCount downTo 1) {
                if (a.getLong(aIdx) != swapLong(b.getLong(bIdx))) {
                    return false
                }
                aIdx += 8
                bIdx += 8
            }
        }

        for (i in byteCount downTo 1) {
            if (a.getByte(aIdx) != b.getByte(bIdx)) {
                return false
            }
            aIdx++
            bIdx++
        }

        return true
    }

    /**
     * Returns `true` if and only if the two specified buffers are
     * identical to each other as described in [ByteBuf.equals].
     * This method is useful when implementing a new buffer type.
     */
    @JvmStatic
    fun equals(bufferA: ByteBuf, bufferB: ByteBuf): Boolean {
        if (bufferA === bufferB) {
            return true
        }
        val aLen = bufferA.readableBytes()
        if (aLen != bufferB.readableBytes()) {
            return false
        }
        return equals(bufferA, bufferA.readerIndex(), bufferB, bufferB.readerIndex(), aLen)
    }

    /**
     * Compares the two specified buffers as described in [ByteBuf.compareTo].
     * This method is useful when implementing a new buffer type.
     */
    @JvmStatic
    fun compare(bufferA: ByteBuf, bufferB: ByteBuf): Int {
        if (bufferA === bufferB) {
            return 0
        }
        val aLen = bufferA.readableBytes()
        val bLen = bufferB.readableBytes()
        val minLength = Math.min(aLen, bLen)
        val uintCount = minLength ushr 2
        val byteCount = minLength and 3
        var aIndex = bufferA.readerIndex()
        var bIndex = bufferB.readerIndex()

        if (uintCount > 0) {
            val bufferAIsBigEndian = bufferA.order() == ByteOrder.BIG_ENDIAN
            val res: Long
            val uintCountIncrement = uintCount shl 2

            if (bufferA.order() == bufferB.order()) {
                res = if (bufferAIsBigEndian) compareUintBigEndian(bufferA, bufferB, aIndex, bIndex, uintCountIncrement)
                else compareUintLittleEndian(bufferA, bufferB, aIndex, bIndex, uintCountIncrement)
            } else {
                res = if (bufferAIsBigEndian) compareUintBigEndianA(bufferA, bufferB, aIndex, bIndex, uintCountIncrement)
                else compareUintBigEndianB(bufferA, bufferB, aIndex, bIndex, uintCountIncrement)
            }
            if (res != 0L) {
                // Ensure we not overflow when cast
                return Math.min(Integer.MAX_VALUE.toLong(), Math.max(Integer.MIN_VALUE.toLong(), res)).toInt()
            }
            aIndex += uintCountIncrement
            bIndex += uintCountIncrement
        }

        val aEnd = aIndex + byteCount
        while (aIndex < aEnd) {
            val comp = bufferA.getUnsignedByte(aIndex) - bufferB.getUnsignedByte(bIndex)
            if (comp != 0) {
                return comp
            }
            ++aIndex
            ++bIndex
        }

        return aLen - bLen
    }

    private fun compareUintBigEndian(
        bufferA: ByteBuf, bufferB: ByteBuf, aIndex: Int, bIndex: Int, uintCountIncrement: Int
    ): Long {
        var aIdx = aIndex
        var bIdx = bIndex
        val aEnd = aIdx + uintCountIncrement
        while (aIdx < aEnd) {
            val comp = bufferA.getUnsignedInt(aIdx) - bufferB.getUnsignedInt(bIdx)
            if (comp != 0L) {
                return comp
            }
            aIdx += 4
            bIdx += 4
        }
        return 0
    }

    private fun compareUintLittleEndian(
        bufferA: ByteBuf, bufferB: ByteBuf, aIndex: Int, bIndex: Int, uintCountIncrement: Int
    ): Long {
        var aIdx = aIndex
        var bIdx = bIndex
        val aEnd = aIdx + uintCountIncrement
        while (aIdx < aEnd) {
            val comp = uintFromLE(bufferA.getUnsignedIntLE(aIdx)) - uintFromLE(bufferB.getUnsignedIntLE(bIdx))
            if (comp != 0L) {
                return comp
            }
            aIdx += 4
            bIdx += 4
        }
        return 0
    }

    private fun compareUintBigEndianA(
        bufferA: ByteBuf, bufferB: ByteBuf, aIndex: Int, bIndex: Int, uintCountIncrement: Int
    ): Long {
        var aIdx = aIndex
        var bIdx = bIndex
        val aEnd = aIdx + uintCountIncrement
        while (aIdx < aEnd) {
            val a = bufferA.getUnsignedInt(aIdx)
            val b = uintFromLE(bufferB.getUnsignedIntLE(bIdx))
            val comp = a - b
            if (comp != 0L) {
                return comp
            }
            aIdx += 4
            bIdx += 4
        }
        return 0
    }

    private fun compareUintBigEndianB(
        bufferA: ByteBuf, bufferB: ByteBuf, aIndex: Int, bIndex: Int, uintCountIncrement: Int
    ): Long {
        var aIdx = aIndex
        var bIdx = bIndex
        val aEnd = aIdx + uintCountIncrement
        while (aIdx < aEnd) {
            val a = uintFromLE(bufferA.getUnsignedIntLE(aIdx))
            val b = bufferB.getUnsignedInt(bIdx)
            val comp = a - b
            if (comp != 0L) {
                return comp
            }
            aIdx += 4
            bIdx += 4
        }
        return 0
    }

    private fun uintFromLE(value: Long): Long {
        return java.lang.Long.reverseBytes(value) ushr Integer.SIZE
    }

    private fun unrolledFirstIndexOf(buffer: AbstractByteBuf, fromIndex: Int, byteCount: Int, value: Byte): Int {
        assert(byteCount > 0 && byteCount < 8)
        if (buffer.invoke_getByte(fromIndex) == value) {
            return fromIndex
        }
        if (byteCount == 1) {
            return -1
        }
        if (buffer.invoke_getByte(fromIndex + 1) == value) {
            return fromIndex + 1
        }
        if (byteCount == 2) {
            return -1
        }
        if (buffer.invoke_getByte(fromIndex + 2) == value) {
            return fromIndex + 2
        }
        if (byteCount == 3) {
            return -1
        }
        if (buffer.invoke_getByte(fromIndex + 3) == value) {
            return fromIndex + 3
        }
        if (byteCount == 4) {
            return -1
        }
        if (buffer.invoke_getByte(fromIndex + 4) == value) {
            return fromIndex + 4
        }
        if (byteCount == 5) {
            return -1
        }
        if (buffer.invoke_getByte(fromIndex + 5) == value) {
            return fromIndex + 5
        }
        if (byteCount == 6) {
            return -1
        }
        if (buffer.invoke_getByte(fromIndex + 6) == value) {
            return fromIndex + 6
        }
        return -1
    }

    /**
     * This is using a SWAR (SIMD Within A Register) batch read technique to minimize bound-checks and improve memory
     * usage while searching for `value`.
     */
    @JvmStatic
    internal fun firstIndexOf(buffer: AbstractByteBuf, fromIndex: Int, toIndex: Int, value: Byte): Int {
        var offset = Math.max(fromIndex, 0)
        if (offset >= toIndex || buffer.capacity() == 0) {
            return -1
        }
        val length = toIndex - offset
        buffer.checkIndex(offset, length)
        if (!SWAR_UNALIGNED) {
            return linearFirstIndexOf(buffer, offset, toIndex, value)
        }
        assert(SWAR_UNALIGNED)
        val byteCount = length and 7
        if (byteCount > 0) {
            val index = unrolledFirstIndexOf(buffer, offset, byteCount, value)
            if (index != -1) {
                return index
            }
            offset += byteCount
            if (offset == toIndex) {
                return -1
            }
        }
        val longCount = length ushr 3
        val nativeOrder = ByteOrder.nativeOrder()
        val isNative = nativeOrder == buffer.order()
        val useLE = nativeOrder == ByteOrder.LITTLE_ENDIAN
        val pattern = SWARUtil.compilePattern(value)
        for (i in 0 until longCount) {
            // use the faster available getLong
            val word = if (useLE) buffer.invoke_getLongLE(offset) else buffer.invoke_getLong(offset)
            val result = SWARUtil.applyPattern(word, pattern)
            if (result != 0L) {
                return offset + SWARUtil.getIndex(result, isNative)
            }
            offset += java.lang.Long.BYTES
        }
        return -1
    }

    private fun linearFirstIndexOf(buffer: AbstractByteBuf, fromIndex: Int, toIndex: Int, value: Byte): Int {
        for (i in fromIndex until toIndex) {
            if (buffer.invoke_getByte(i) == value) {
                return i
            }
        }
        return -1
    }

    /**
     * The default implementation of [ByteBuf.indexOf].
     * This method is useful when implementing a new buffer type.
     */
    @JvmStatic
    fun indexOf(buffer: ByteBuf, fromIndex: Int, toIndex: Int, value: Byte): Int {
        return buffer.indexOf(fromIndex, toIndex, value)
    }

    /**
     * Toggles the endianness of the specified 16-bit short integer.
     */
    @JvmStatic
    fun swapShort(value: Short): Short {
        return java.lang.Short.reverseBytes(value)
    }

    /**
     * Toggles the endianness of the specified 24-bit medium integer.
     */
    @JvmStatic
    fun swapMedium(value: Int): Int {
        var swapped = value shl 16 and 0xff0000 or (value and 0xff00) or (value ushr 16 and 0xff)
        if (swapped and 0x800000 != 0) {
            swapped = swapped or 0xff000000.toInt()
        }
        return swapped
    }

    /**
     * Toggles the endianness of the specified 32-bit integer.
     */
    @JvmStatic
    fun swapInt(value: Int): Int {
        return Integer.reverseBytes(value)
    }

    /**
     * Toggles the endianness of the specified 64-bit long integer.
     */
    @JvmStatic
    fun swapLong(value: Long): Long {
        return java.lang.Long.reverseBytes(value)
    }

    /**
     * Writes a big-endian 16-bit short integer to the buffer.
     */
    @Suppress("deprecation")
    @JvmStatic
    fun writeShortBE(buf: ByteBuf, shortValue: Int): ByteBuf {
        return if (buf.order() == ByteOrder.BIG_ENDIAN) buf.writeShort(shortValue)
        else buf.writeShort(swapShort(shortValue.toShort()).toInt())
    }

    /**
     * Sets a big-endian 16-bit short integer to the buffer.
     */
    @Suppress("deprecation")
    @JvmStatic
    fun setShortBE(buf: ByteBuf, index: Int, shortValue: Int): ByteBuf {
        return if (buf.order() == ByteOrder.BIG_ENDIAN) buf.setShort(index, shortValue)
        else buf.setShort(index, swapShort(shortValue.toShort()).toInt())
    }

    /**
     * Writes a big-endian 24-bit medium integer to the buffer.
     */
    @Suppress("deprecation")
    @JvmStatic
    fun writeMediumBE(buf: ByteBuf, mediumValue: Int): ByteBuf {
        return if (buf.order() == ByteOrder.BIG_ENDIAN) buf.writeMedium(mediumValue)
        else buf.writeMedium(swapMedium(mediumValue))
    }

    /**
     * Reads a big-endian unsigned 16-bit short integer from the buffer.
     */
    @Suppress("deprecation")
    @JvmStatic
    fun readUnsignedShortBE(buf: ByteBuf): Int {
        return if (buf.order() == ByteOrder.BIG_ENDIAN) buf.readUnsignedShort()
        else swapShort(buf.readUnsignedShort().toShort()).toInt() and 0xFFFF
    }

    /**
     * Reads a big-endian 32-bit integer from the buffer.
     */
    @Suppress("deprecation")
    @JvmStatic
    fun readIntBE(buf: ByteBuf): Int {
        return if (buf.order() == ByteOrder.BIG_ENDIAN) buf.readInt()
        else swapInt(buf.readInt())
    }

    /**
     * Read the given amount of bytes into a new [ByteBuf] that is allocated from the [ByteBufAllocator].
     */
    @JvmStatic
    fun readBytes(alloc: ByteBufAllocator, buffer: ByteBuf, length: Int): ByteBuf {
        var release = true
        val dst = alloc.buffer(length)
        try {
            buffer.readBytes(dst)
            release = false
            return dst
        } finally {
            if (release) {
                dst.release()
            }
        }
    }

    @JvmStatic
    internal fun lastIndexOf(buffer: AbstractByteBuf, fromIndex: Int, toIndex: Int, value: Byte): Int {
        assert(fromIndex > toIndex)
        val capacity = buffer.capacity()
        var from = Math.min(fromIndex, capacity)
        if (from <= 0) { // fromIndex is the exclusive upper bound.
            return -1
        }
        val length = from - toIndex
        buffer.checkIndex(toIndex, length)
        if (!SWAR_UNALIGNED) {
            return linearLastIndexOf(buffer, from, toIndex, value)
        }
        val longCount = length ushr 3
        if (longCount > 0) {
            val nativeOrder = ByteOrder.nativeOrder()
            val isNative = nativeOrder == buffer.order()
            val useLE = nativeOrder == ByteOrder.LITTLE_ENDIAN
            val pattern = SWARUtil.compilePattern(value)
            var i = 0
            var offset = from - java.lang.Long.BYTES
            while (i < longCount) {
                // use the faster available getLong
                val word = if (useLE) buffer.invoke_getLongLE(offset) else buffer.invoke_getLong(offset)
                val result = SWARUtil.applyPattern(word, pattern)
                if (result != 0L) {
                    // used the opposite endianness since we are looking for the last index.
                    return offset + java.lang.Long.BYTES - 1 - SWARUtil.getIndex(result, !isNative)
                }
                i++
                offset -= java.lang.Long.BYTES
            }
        }
        return unrolledLastIndexOf(buffer, from - (longCount shl 3), length and 7, value)
    }

    private fun linearLastIndexOf(buffer: AbstractByteBuf, fromIndex: Int, toIndex: Int, value: Byte): Int {
        for (i in fromIndex - 1 downTo toIndex) {
            if (buffer.invoke_getByte(i) == value) {
                return i
            }
        }
        return -1
    }

    private fun unrolledLastIndexOf(buffer: AbstractByteBuf, fromIndex: Int, byteCount: Int, value: Byte): Int {
        assert(byteCount >= 0 && byteCount < 8)
        if (byteCount == 0) {
            return -1
        }
        if (buffer.invoke_getByte(fromIndex - 1) == value) {
            return fromIndex - 1
        }
        if (byteCount == 1) {
            return -1
        }
        if (buffer.invoke_getByte(fromIndex - 2) == value) {
            return fromIndex - 2
        }
        if (byteCount == 2) {
            return -1
        }
        if (buffer.invoke_getByte(fromIndex - 3) == value) {
            return fromIndex - 3
        }
        if (byteCount == 3) {
            return -1
        }
        if (buffer.invoke_getByte(fromIndex - 4) == value) {
            return fromIndex - 4
        }
        if (byteCount == 4) {
            return -1
        }
        if (buffer.invoke_getByte(fromIndex - 5) == value) {
            return fromIndex - 5
        }
        if (byteCount == 5) {
            return -1
        }
        if (buffer.invoke_getByte(fromIndex - 6) == value) {
            return fromIndex - 6
        }
        if (byteCount == 6) {
            return -1
        }
        if (buffer.invoke_getByte(fromIndex - 7) == value) {
            return fromIndex - 7
        }
        return -1
    }

    private fun checkCharSequenceBounds(seq: CharSequence, start: Int, end: Int): CharSequence {
        if (isOutOfBounds(start, end - start, seq.length)) {
            throw IndexOutOfBoundsException(
                "expected: 0 <= start($start) <= end ($end) <= seq.length(${seq.length})"
            )
        }
        return seq
    }

    /**
     * Encode a [CharSequence] in [UTF-8](https://en.wikipedia.org/wiki/UTF-8) and write
     * it to a [ByteBuf] allocated with `alloc`.
     * @param alloc The allocator used to allocate a new [ByteBuf].
     * @param seq The characters to write into a buffer.
     * @return The [ByteBuf] which contains the [UTF-8](https://en.wikipedia.org/wiki/UTF-8) encoded
     * result.
     */
    @JvmStatic
    fun writeUtf8(alloc: ByteBufAllocator, seq: CharSequence): ByteBuf {
        // UTF-8 uses max. 3 bytes per char, so calculate the worst case.
        val buf = alloc.buffer(utf8MaxBytes(seq))
        writeUtf8(buf, seq)
        return buf
    }

    /**
     * Encode a [CharSequence] in [UTF-8](https://en.wikipedia.org/wiki/UTF-8) and write
     * it to a [ByteBuf].
     *
     * It behaves like [.reserveAndWriteUtf8] with `reserveBytes`
     * computed by [.utf8MaxBytes].
     * This method returns the actual number of bytes written.
     */
    @JvmStatic
    fun writeUtf8(buf: ByteBuf, seq: CharSequence): Int {
        val seqLength = seq.length
        return reserveAndWriteUtf8Seq(buf, seq, 0, seqLength, utf8MaxBytes(seqLength))
    }

    /**
     * Equivalent to `writeUtf8(buf, seq.subSequence(start, end))`
     * but avoids subsequence object allocation.
     */
    @JvmStatic
    fun writeUtf8(buf: ByteBuf, seq: CharSequence, start: Int, end: Int): Int {
        checkCharSequenceBounds(seq, start, end)
        return reserveAndWriteUtf8Seq(buf, seq, start, end, utf8MaxBytes(end - start))
    }

    /**
     * Encode a [CharSequence] in [UTF-8](https://en.wikipedia.org/wiki/UTF-8) and write
     * it into `reserveBytes` of a [ByteBuf].
     *
     * The `reserveBytes` must be computed (ie eagerly using [.utf8MaxBytes]
     * or exactly with [.utf8Bytes]) to ensure this method to not fail: for performance reasons
     * the index checks will be performed using just `reserveBytes`.
     * This method returns the actual number of bytes written.
     */
    @JvmStatic
    fun reserveAndWriteUtf8(buf: ByteBuf, seq: CharSequence, reserveBytes: Int): Int {
        return reserveAndWriteUtf8Seq(buf, seq, 0, seq.length, reserveBytes)
    }

    /**
     * Equivalent to `reserveAndWriteUtf8(buf, seq.subSequence(start, end), reserveBytes)` but avoids
     * subsequence object allocation if possible.
     *
     * @return actual number of bytes written
     */
    @JvmStatic
    fun reserveAndWriteUtf8(buf: ByteBuf, seq: CharSequence, start: Int, end: Int, reserveBytes: Int): Int {
        return reserveAndWriteUtf8Seq(buf, checkCharSequenceBounds(seq, start, end), start, end, reserveBytes)
    }

    private fun reserveAndWriteUtf8Seq(buf: ByteBuf, seq: CharSequence, start: Int, end: Int, reserveBytes: Int): Int {
        @Suppress("NAME_SHADOWING")
        var buf = buf
        while (true) {
            if (buf is WrappedCompositeByteBuf) {
                // WrappedCompositeByteBuf is a sub-class of AbstractByteBuf so it needs special handling.
                buf = buf.unwrap()
            } else if (buf is AbstractByteBuf) {
                buf.ensureWritable0(reserveBytes)
                val written = writeUtf8(buf, buf.writerIndex, reserveBytes, seq, start, end)
                buf.writerIndex += written
                return written
            } else if (buf is WrappedByteBuf) {
                // Unwrap as the wrapped buffer may be an AbstractByteBuf and so we can use fast-path.
                buf = buf.unwrap()
            } else {
                val bytes = seq.subSequence(start, end).toString().toByteArray(CharsetUtil.UTF_8)
                buf.writeBytes(bytes)
                return bytes.size
            }
        }
    }

    @JvmStatic
    internal fun writeUtf8(buffer: AbstractByteBuf, writerIndex: Int, reservedBytes: Int, seq: CharSequence, len: Int): Int {
        return writeUtf8(buffer, writerIndex, reservedBytes, seq, 0, len)
    }

    // Fast-Path implementation
    @JvmStatic
    internal fun writeUtf8(
        buffer: AbstractByteBuf, writerIndex: Int, reservedBytes: Int,
        seq: CharSequence, start: Int, end: Int
    ): Int {
        if (seq is AsciiString) {
            writeAsciiString(buffer, writerIndex, seq, start, end)
            return end - start
        }
        if (PlatformDependent.hasUnsafe()) {
            if (buffer.hasArray()) {
                return unsafeWriteUtf8(
                    buffer.array(), PlatformDependent.byteArrayBaseOffset(),
                    buffer.arrayOffset() + writerIndex, seq, start, end
                )
            }
            if (buffer.hasMemoryAddress()) {
                return unsafeWriteUtf8(null, buffer.memoryAddress(), writerIndex, seq, start, end)
            }
        } else {
            if (buffer.hasArray()) {
                return safeArrayWriteUtf8(buffer.array(), buffer.arrayOffset() + writerIndex, seq, start, end)
            }
            if (buffer.isDirect() && buffer.nioBufferCount() == 1) {
                val internalDirectBuffer = buffer.internalNioBuffer(writerIndex, reservedBytes)
                val bufferPosition = internalDirectBuffer.position()
                return safeDirectWriteUtf8(internalDirectBuffer, bufferPosition, seq, start, end)
            }
        }
        return safeWriteUtf8(buffer, writerIndex, seq, start, end)
    }

    // AsciiString Fast-Path implementation - no explicit bound-checks
    @JvmStatic
    internal fun writeAsciiString(buffer: AbstractByteBuf, writerIndex: Int, seq: AsciiString, start: Int, end: Int) {
        val begin = seq.arrayOffset() + start
        val length = end - start
        if (PlatformDependent.hasUnsafe()) {
            if (buffer.hasArray()) {
                PlatformDependent.copyMemory(
                    seq.array(), begin,
                    buffer.array(), buffer.arrayOffset() + writerIndex, length.toLong()
                )
                return
            }
            if (buffer.hasMemoryAddress()) {
                PlatformDependent.copyMemory(seq.array(), begin, buffer.memoryAddress() + writerIndex, length.toLong())
                return
            }
        }
        if (buffer.hasArray()) {
            System.arraycopy(seq.array(), begin, buffer.array(), buffer.arrayOffset() + writerIndex, length)
            return
        }
        buffer.setBytes(writerIndex, seq.array(), begin, length)
    }

    // Safe off-heap Fast-Path implementation
    private fun safeDirectWriteUtf8(buffer: ByteBuffer, writerIndex: Int, seq: CharSequence, start: Int, end: Int): Int {
        assert(seq !is AsciiString)
        var idx = writerIndex
        val oldWriterIndex = idx

        // We can use the _set methods as these not need to do any index checks and reference checks.
        // This is possible as we called ensureWritable(...) before.
        var i = start
        while (i < end) {
            val c = seq[i]
            if (c.code < 0x80) {
                buffer.put(idx++, c.code.toByte())
            } else if (c.code < 0x800) {
                buffer.put(idx++, (0xc0 or (c.code shr 6)).toByte())
                buffer.put(idx++, (0x80 or (c.code and 0x3f)).toByte())
            } else if (isSurrogate(c)) {
                if (!Character.isHighSurrogate(c)) {
                    buffer.put(idx++, WRITE_UTF_UNKNOWN)
                    i++
                    continue
                }
                // Surrogate Pair consumes 2 characters.
                if (++i == end) {
                    buffer.put(idx++, WRITE_UTF_UNKNOWN)
                    break
                }
                // Extra method is copied here to NOT allow inlining of writeUtf8
                // and increase the chance to inline CharSequence::charAt instead
                val c2 = seq[i]
                if (!Character.isLowSurrogate(c2)) {
                    buffer.put(idx++, WRITE_UTF_UNKNOWN)
                    buffer.put(idx++, if (Character.isHighSurrogate(c2)) WRITE_UTF_UNKNOWN else c2.code.toByte())
                } else {
                    val codePoint = Character.toCodePoint(c, c2)
                    // See https://www.unicode.org/versions/Unicode7.0.0/ch03.pdf#G2630.
                    buffer.put(idx++, (0xf0 or (codePoint shr 18)).toByte())
                    buffer.put(idx++, (0x80 or ((codePoint shr 12) and 0x3f)).toByte())
                    buffer.put(idx++, (0x80 or ((codePoint shr 6) and 0x3f)).toByte())
                    buffer.put(idx++, (0x80 or (codePoint and 0x3f)).toByte())
                }
            } else {
                buffer.put(idx++, (0xe0 or (c.code shr 12)).toByte())
                buffer.put(idx++, (0x80 or ((c.code shr 6) and 0x3f)).toByte())
                buffer.put(idx++, (0x80 or (c.code and 0x3f)).toByte())
            }
            i++
        }
        return idx - oldWriterIndex
    }

    // Safe off-heap Fast-Path implementation
    private fun safeWriteUtf8(buffer: AbstractByteBuf, writerIndex: Int, seq: CharSequence, start: Int, end: Int): Int {
        assert(seq !is AsciiString)
        var idx = writerIndex
        val oldWriterIndex = idx

        // We can use the _set methods as these not need to do any index checks and reference checks.
        // This is possible as we called ensureWritable(...) before.
        var i = start
        while (i < end) {
            val c = seq[i]
            if (c.code < 0x80) {
                buffer.invoke_setByte(idx++, c.code)
            } else if (c.code < 0x800) {
                buffer.invoke_setByte(idx++, 0xc0 or (c.code shr 6))
                buffer.invoke_setByte(idx++, 0x80 or (c.code and 0x3f))
            } else if (isSurrogate(c)) {
                if (!Character.isHighSurrogate(c)) {
                    buffer.invoke_setByte(idx++, WRITE_UTF_UNKNOWN.toInt())
                    i++
                    continue
                }
                // Surrogate Pair consumes 2 characters.
                if (++i == end) {
                    buffer.invoke_setByte(idx++, WRITE_UTF_UNKNOWN.toInt())
                    break
                }
                // Extra method is copied here to NOT allow inlining of writeUtf8
                // and increase the chance to inline CharSequence::charAt instead
                val c2 = seq[i]
                if (!Character.isLowSurrogate(c2)) {
                    buffer.invoke_setByte(idx++, WRITE_UTF_UNKNOWN.toInt())
                    buffer.invoke_setByte(idx++, if (Character.isHighSurrogate(c2)) WRITE_UTF_UNKNOWN.toInt() else c2.code)
                } else {
                    val codePoint = Character.toCodePoint(c, c2)
                    // See https://www.unicode.org/versions/Unicode7.0.0/ch03.pdf#G2630.
                    buffer.invoke_setByte(idx++, 0xf0 or (codePoint shr 18))
                    buffer.invoke_setByte(idx++, 0x80 or ((codePoint shr 12) and 0x3f))
                    buffer.invoke_setByte(idx++, 0x80 or ((codePoint shr 6) and 0x3f))
                    buffer.invoke_setByte(idx++, 0x80 or (codePoint and 0x3f))
                }
            } else {
                buffer.invoke_setByte(idx++, 0xe0 or (c.code shr 12))
                buffer.invoke_setByte(idx++, 0x80 or ((c.code shr 6) and 0x3f))
                buffer.invoke_setByte(idx++, 0x80 or (c.code and 0x3f))
            }
            i++
        }
        return idx - oldWriterIndex
    }

    // safe byte[] Fast-Path implementation
    private fun safeArrayWriteUtf8(buffer: ByteArray, writerIndex: Int, seq: CharSequence, start: Int, end: Int): Int {
        var idx = writerIndex
        val oldWriterIndex = idx
        var i = start
        while (i < end) {
            val c = seq[i]
            if (c.code < 0x80) {
                buffer[idx++] = c.code.toByte()
            } else if (c.code < 0x800) {
                buffer[idx++] = (0xc0 or (c.code shr 6)).toByte()
                buffer[idx++] = (0x80 or (c.code and 0x3f)).toByte()
            } else if (isSurrogate(c)) {
                if (!Character.isHighSurrogate(c)) {
                    buffer[idx++] = WRITE_UTF_UNKNOWN
                    i++
                    continue
                }
                // Surrogate Pair consumes 2 characters.
                if (++i == end) {
                    buffer[idx++] = WRITE_UTF_UNKNOWN
                    break
                }
                val c2 = seq[i]
                // Extra method is copied here to NOT allow inlining of writeUtf8
                // and increase the chance to inline CharSequence::charAt instead
                if (!Character.isLowSurrogate(c2)) {
                    buffer[idx++] = WRITE_UTF_UNKNOWN
                    buffer[idx++] = if (Character.isHighSurrogate(c2)) WRITE_UTF_UNKNOWN else c2.code.toByte()
                } else {
                    val codePoint = Character.toCodePoint(c, c2)
                    // See https://www.unicode.org/versions/Unicode7.0.0/ch03.pdf#G2630.
                    buffer[idx++] = (0xf0 or (codePoint shr 18)).toByte()
                    buffer[idx++] = (0x80 or ((codePoint shr 12) and 0x3f)).toByte()
                    buffer[idx++] = (0x80 or ((codePoint shr 6) and 0x3f)).toByte()
                    buffer[idx++] = (0x80 or (codePoint and 0x3f)).toByte()
                }
            } else {
                buffer[idx++] = (0xe0 or (c.code shr 12)).toByte()
                buffer[idx++] = (0x80 or ((c.code shr 6) and 0x3f)).toByte()
                buffer[idx++] = (0x80 or (c.code and 0x3f)).toByte()
            }
            i++
        }
        return idx - oldWriterIndex
    }

    // unsafe Fast-Path implementation
    private fun unsafeWriteUtf8(
        buffer: ByteArray?, memoryOffset: Long, writerIndex: Int,
        seq: CharSequence, start: Int, end: Int
    ): Int {
        assert(seq !is AsciiString)
        var writerOffset = memoryOffset + writerIndex
        val oldWriterOffset = writerOffset
        var i = start
        while (i < end) {
            val c = seq[i]
            if (c.code < 0x80) {
                PlatformDependent.putByte(buffer, writerOffset++, c.code.toByte())
            } else if (c.code < 0x800) {
                PlatformDependent.putByte(buffer, writerOffset++, (0xc0 or (c.code shr 6)).toByte())
                PlatformDependent.putByte(buffer, writerOffset++, (0x80 or (c.code and 0x3f)).toByte())
            } else if (isSurrogate(c)) {
                if (!Character.isHighSurrogate(c)) {
                    PlatformDependent.putByte(buffer, writerOffset++, WRITE_UTF_UNKNOWN)
                    i++
                    continue
                }
                // Surrogate Pair consumes 2 characters.
                if (++i == end) {
                    PlatformDependent.putByte(buffer, writerOffset++, WRITE_UTF_UNKNOWN)
                    break
                }
                val c2 = seq[i]
                // Extra method is copied here to NOT allow inlining of writeUtf8
                // and increase the chance to inline CharSequence::charAt instead
                if (!Character.isLowSurrogate(c2)) {
                    PlatformDependent.putByte(buffer, writerOffset++, WRITE_UTF_UNKNOWN)
                    PlatformDependent.putByte(
                        buffer, writerOffset++,
                        if (Character.isHighSurrogate(c2)) WRITE_UTF_UNKNOWN else c2.code.toByte()
                    )
                } else {
                    val codePoint = Character.toCodePoint(c, c2)
                    // See https://www.unicode.org/versions/Unicode7.0.0/ch03.pdf#G2630.
                    PlatformDependent.putByte(buffer, writerOffset++, (0xf0 or (codePoint shr 18)).toByte())
                    PlatformDependent.putByte(buffer, writerOffset++, (0x80 or ((codePoint shr 12) and 0x3f)).toByte())
                    PlatformDependent.putByte(buffer, writerOffset++, (0x80 or ((codePoint shr 6) and 0x3f)).toByte())
                    PlatformDependent.putByte(buffer, writerOffset++, (0x80 or (codePoint and 0x3f)).toByte())
                }
            } else {
                PlatformDependent.putByte(buffer, writerOffset++, (0xe0 or (c.code shr 12)).toByte())
                PlatformDependent.putByte(buffer, writerOffset++, (0x80 or ((c.code shr 6) and 0x3f)).toByte())
                PlatformDependent.putByte(buffer, writerOffset++, (0x80 or (c.code and 0x3f)).toByte())
            }
            i++
        }
        return (writerOffset - oldWriterOffset).toInt()
    }

    /**
     * Returns max bytes length of UTF8 character sequence of the given length.
     */
    @JvmStatic
    fun utf8MaxBytes(seqLength: Int): Int {
        return seqLength * MAX_BYTES_PER_CHAR_UTF8
    }

    /**
     * Returns max bytes length of UTF8 character sequence.
     *
     * It behaves like [.utf8MaxBytes] applied to `seq` [CharSequence.length].
     */
    @JvmStatic
    fun utf8MaxBytes(seq: CharSequence): Int {
        if (seq is AsciiString) {
            return seq.length
        }
        return utf8MaxBytes(seq.length)
    }

    /**
     * Returns the exact bytes length of UTF8 character sequence.
     *
     * This method is producing the exact length according to [.writeUtf8].
     */
    @JvmStatic
    fun utf8Bytes(seq: CharSequence): Int {
        return utf8ByteCount(seq, 0, seq.length)
    }

    /**
     * Equivalent to `utf8Bytes(seq.subSequence(start, end))`
     * but avoids subsequence object allocation.
     *
     * This method is producing the exact length according to [.writeUtf8].
     */
    @JvmStatic
    fun utf8Bytes(seq: CharSequence, start: Int, end: Int): Int {
        return utf8ByteCount(checkCharSequenceBounds(seq, start, end), start, end)
    }

    private fun utf8ByteCount(seq: CharSequence, start: Int, end: Int): Int {
        if (seq is AsciiString) {
            return end - start
        }
        var i = start
        // ASCII fast path
        while (i < end && seq[i].code < 0x80) {
            ++i
        }
        // !ASCII is packed in a separate method to let the ASCII case be smaller
        return if (i < end) (i - start) + utf8BytesNonAscii(seq, i, end) else i - start
    }

    private fun utf8BytesNonAscii(seq: CharSequence, start: Int, end: Int): Int {
        var encodedLength = 0
        var i = start
        while (i < end) {
            val c = seq[i]
            // making it 100% branchless isn't rewarding due to the many bit operations necessary!
            if (c.code < 0x800) {
                // branchless version of: (c <= 127 ? 0:1) + 1
                encodedLength += ((0x7f - c.code) ushr 31) + 1
            } else if (isSurrogate(c)) {
                if (!Character.isHighSurrogate(c)) {
                    encodedLength++
                    // WRITE_UTF_UNKNOWN
                    i++
                    continue
                }
                // Surrogate Pair consumes 2 characters.
                if (++i == end) {
                    encodedLength++
                    // WRITE_UTF_UNKNOWN
                    break
                }
                if (!Character.isLowSurrogate(seq[i])) {
                    // WRITE_UTF_UNKNOWN + (Character.isHighSurrogate(c2) ? WRITE_UTF_UNKNOWN : c2)
                    encodedLength += 2
                    i++
                    continue
                }
                // See https://www.unicode.org/versions/Unicode7.0.0/ch03.pdf#G2630.
                encodedLength += 4
            } else {
                encodedLength += 3
            }
            i++
        }
        return encodedLength
    }

    /**
     * Encode a [CharSequence] in [ASCII](https://en.wikipedia.org/wiki/ASCII) and write
     * it to a [ByteBuf] allocated with `alloc`.
     * @param alloc The allocator used to allocate a new [ByteBuf].
     * @param seq The characters to write into a buffer.
     * @return The [ByteBuf] which contains the [ASCII](https://en.wikipedia.org/wiki/ASCII) encoded
     * result.
     */
    @JvmStatic
    fun writeAscii(alloc: ByteBufAllocator, seq: CharSequence): ByteBuf {
        // ASCII uses 1 byte per char
        val buf = alloc.buffer(seq.length)
        writeAscii(buf, seq)
        return buf
    }

    /**
     * Encode a [CharSequence] in [ASCII](https://en.wikipedia.org/wiki/ASCII) and write it
     * to a [ByteBuf].
     *
     * This method returns the actual number of bytes written.
     */
    @JvmStatic
    fun writeAscii(buf: ByteBuf, seq: CharSequence): Int {
        @Suppress("NAME_SHADOWING")
        var buf = buf
        // ASCII uses 1 byte per char
        while (true) {
            if (buf is WrappedCompositeByteBuf) {
                // WrappedCompositeByteBuf is a sub-class of AbstractByteBuf so it needs special handling.
                buf = buf.unwrap()
            } else if (buf is AbstractByteBuf) {
                val len = seq.length
                buf.ensureWritable0(len)
                if (seq is AsciiString) {
                    writeAsciiString(buf, buf.writerIndex, seq, 0, len)
                } else {
                    val written = writeAscii(buf, buf.writerIndex, seq, len)
                    assert(written == len)
                }
                buf.writerIndex += len
                return len
            } else if (buf is WrappedByteBuf) {
                // Unwrap as the wrapped buffer may be an AbstractByteBuf and so we can use fast-path.
                buf = buf.unwrap()
            } else {
                val bytes = seq.toString().toByteArray(CharsetUtil.US_ASCII)
                buf.writeBytes(bytes)
                return bytes.size
            }
        }
    }

    @JvmStatic
    internal fun writeAscii(buffer: AbstractByteBuf, writerIndex: Int, seq: CharSequence, len: Int): Int {
        if (seq is AsciiString) {
            writeAsciiString(buffer, writerIndex, seq, 0, len)
        } else {
            writeAsciiCharSequence(buffer, writerIndex, seq, len)
        }
        return len
    }

    private fun writeAsciiCharSequence(buffer: AbstractByteBuf, writerIndex: Int, seq: CharSequence, len: Int): Int {
        var idx = writerIndex
        // We can use the _set methods as these not need to do any index checks and reference checks.
        // This is possible as we called ensureWritable(...) before.
        for (i in 0 until len) {
            buffer.invoke_setByte(idx++, AsciiString.c2b(seq[i]).toInt())
        }
        return len
    }

    /**
     * Encode the given [CharBuffer] using the given [Charset] into a new [ByteBuf] which
     * is allocated via the [ByteBufAllocator].
     */
    @JvmStatic
    fun encodeString(alloc: ByteBufAllocator, src: CharBuffer, charset: Charset): ByteBuf {
        return encodeString0(alloc, false, src, charset, 0)
    }

    /**
     * Encode the given [CharBuffer] using the given [Charset] into a new [ByteBuf] which
     * is allocated via the [ByteBufAllocator].
     *
     * @param alloc The [ByteBufAllocator] to allocate [ByteBuf].
     * @param src The [CharBuffer] to encode.
     * @param charset The specified [Charset].
     * @param extraCapacity the extra capacity to alloc except the space for decoding.
     */
    @JvmStatic
    fun encodeString(alloc: ByteBufAllocator, src: CharBuffer, charset: Charset, extraCapacity: Int): ByteBuf {
        return encodeString0(alloc, false, src, charset, extraCapacity)
    }

    @JvmStatic
    internal fun encodeString0(
        alloc: ByteBufAllocator, enforceHeap: Boolean, src: CharBuffer, charset: Charset,
        extraCapacity: Int
    ): ByteBuf {
        val encoder = CharsetUtil.encoder(charset)
        val length = (src.remaining().toDouble() * encoder.maxBytesPerChar()).toInt() + extraCapacity
        var release = true
        val dst: ByteBuf = if (enforceHeap) {
            alloc.heapBuffer(length)
        } else {
            alloc.buffer(length)
        }
        try {
            val dstBuf = dst.internalNioBuffer(dst.readerIndex(), length)
            val pos = dstBuf.position()
            var cr = encoder.encode(src, dstBuf, true)
            if (!cr.isUnderflow) {
                cr.throwException()
            }
            cr = encoder.flush(dstBuf)
            if (!cr.isUnderflow) {
                cr.throwException()
            }
            dst.writerIndex(dst.writerIndex() + dstBuf.position() - pos)
            release = false
            return dst
        } catch (x: CharacterCodingException) {
            throw IllegalStateException(x)
        } finally {
            if (release) {
                dst.release()
            }
        }
    }

    @Suppress("deprecation")
    @JvmStatic
    fun decodeString(src: ByteBuf, readerIndex: Int, len: Int, charset: Charset): String {
        if (len == 0) {
            return StringUtil.EMPTY_STRING
        }
        val array: ByteArray
        val offset: Int

        if (src.hasArray()) {
            array = src.array()
            offset = src.arrayOffset() + readerIndex
        } else {
            array = threadLocalTempArray(len)
            offset = 0
            src.getBytes(readerIndex, array, 0, len)
        }
        if (CharsetUtil.US_ASCII == charset) {
            // Fast-path for US-ASCII which is used frequently.
            return String(array, offset, len, Charsets.US_ASCII)
        }
        return String(array, offset, len, charset)
    }

    /**
     * Returns a cached thread-local direct buffer, if available.
     *
     * @return a cached thread-local direct buffer, if available.  `null` otherwise.
     */
    @JvmStatic
    fun threadLocalDirectBuffer(): ByteBuf? {
        if (THREAD_LOCAL_BUFFER_SIZE <= 0) {
            return null
        }

        return if (PlatformDependent.hasUnsafe()) {
            ThreadLocalUnsafeDirectByteBuf.newInstance()
        } else {
            ThreadLocalDirectByteBuf.newInstance()
        }
    }

    /**
     * Create a copy of the underlying storage from `buf` into a byte array.
     * The copy will start at [ByteBuf.readerIndex] and copy [ByteBuf.readableBytes] bytes.
     */
    @JvmStatic
    fun getBytes(buf: ByteBuf): ByteArray {
        return getBytes(buf, buf.readerIndex(), buf.readableBytes())
    }

    /**
     * Create a copy of the underlying storage from `buf` into a byte array.
     * The copy will start at `start` and copy `length` bytes.
     */
    @JvmStatic
    fun getBytes(buf: ByteBuf, start: Int, length: Int): ByteArray {
        return getBytes(buf, start, length, true)
    }

    /**
     * Return an array of the underlying storage from `buf` into a byte array.
     * The copy will start at `start` and copy `length` bytes.
     * If `copy` is true a copy will be made of the memory.
     * If `copy` is false the underlying storage will be shared, if possible.
     */
    @JvmStatic
    fun getBytes(buf: ByteBuf, start: Int, length: Int, copy: Boolean): ByteArray {
        val capacity = buf.capacity()
        if (isOutOfBounds(start, length, capacity)) {
            throw IndexOutOfBoundsException(
                "expected: 0 <= start($start) <= start + length($length) <= buf.capacity($capacity)"
            )
        }

        if (buf.hasArray()) {
            val baseOffset = buf.arrayOffset() + start
            val bytes = buf.array()
            if (copy || baseOffset != 0 || length != bytes.size) {
                return Arrays.copyOfRange(bytes, baseOffset, baseOffset + length)
            } else {
                return bytes
            }
        }

        val bytes = PlatformDependent.allocateUninitializedArray(length)
        buf.getBytes(start, bytes)
        return bytes
    }

    /**
     * Copies the all content of `src` to a [ByteBuf] using [ByteBuf.writeBytes].
     *
     * @param src the source string to copy
     * @param dst the destination buffer
     */
    @JvmStatic
    fun copy(src: AsciiString, dst: ByteBuf) {
        copy(src, 0, dst, src.length)
    }

    /**
     * Copies the content of `src` to a [ByteBuf] using [ByteBuf.setBytes].
     * Unlike the [.copy] and [.copy] methods,
     * this method do not increase a `writerIndex` of `dst` buffer.
     *
     * @param src the source string to copy
     * @param srcIdx the starting offset of characters to copy
     * @param dst the destination buffer
     * @param dstIdx the starting offset in the destination buffer
     * @param length the number of characters to copy
     */
    @JvmStatic
    fun copy(src: AsciiString, srcIdx: Int, dst: ByteBuf, dstIdx: Int, length: Int) {
        if (isOutOfBounds(srcIdx, length, src.length)) {
            throw IndexOutOfBoundsException(
                "expected: 0 <= srcIdx($srcIdx) <= srcIdx + length($length) <= srcLen(${src.length})"
            )
        }

        checkNotNull(dst, "dst").setBytes(dstIdx, src.array(), srcIdx + src.arrayOffset(), length)
    }

    /**
     * Copies the content of `src` to a [ByteBuf] using [ByteBuf.writeBytes].
     *
     * @param src the source string to copy
     * @param srcIdx the starting offset of characters to copy
     * @param dst the destination buffer
     * @param length the number of characters to copy
     */
    @JvmStatic
    fun copy(src: AsciiString, srcIdx: Int, dst: ByteBuf, length: Int) {
        if (isOutOfBounds(srcIdx, length, src.length)) {
            throw IndexOutOfBoundsException(
                "expected: 0 <= srcIdx($srcIdx) <= srcIdx + length($length) <= srcLen(${src.length})"
            )
        }

        checkNotNull(dst, "dst").writeBytes(src.array(), srcIdx + src.arrayOffset(), length)
    }

    /**
     * Returns a multi-line hexadecimal dump of the specified [ByteBuf] that is easy to read by humans.
     */
    @JvmStatic
    fun prettyHexDump(buffer: ByteBuf): String {
        return prettyHexDump(buffer, buffer.readerIndex(), buffer.readableBytes())
    }

    /**
     * Returns a multi-line hexadecimal dump of the specified [ByteBuf] that is easy to read by humans,
     * starting at the given `offset` using the given `length`.
     */
    @JvmStatic
    fun prettyHexDump(buffer: ByteBuf, offset: Int, length: Int): String {
        return HexUtil.prettyHexDump(buffer, offset, length)
    }

    /**
     * Appends the prettified multi-line hexadecimal dump of the specified [ByteBuf] to the specified
     * [StringBuilder] that is easy to read by humans.
     */
    @JvmStatic
    fun appendPrettyHexDump(dump: StringBuilder, buf: ByteBuf) {
        appendPrettyHexDump(dump, buf, buf.readerIndex(), buf.readableBytes())
    }

    /**
     * Appends the prettified multi-line hexadecimal dump of the specified [ByteBuf] to the specified
     * [StringBuilder] that is easy to read by humans, starting at the given `offset` using
     * the given `length`.
     */
    @JvmStatic
    fun appendPrettyHexDump(dump: StringBuilder, buf: ByteBuf, offset: Int, length: Int) {
        HexUtil.appendPrettyHexDump(dump, buf, offset, length)
    }

    /* Separate class so that the expensive static initialization is only done when needed */
    private object HexUtil {

        private val BYTE2CHAR = CharArray(256)
        private val HEXDUMP_TABLE = CharArray(256 * 4)
        private val HEXPADDING = arrayOfNulls<String>(16)
        private val HEXDUMP_ROWPREFIXES = arrayOfNulls<String>(65536 ushr 4)
        private val BYTE2HEX = arrayOfNulls<String>(256)
        private val BYTEPADDING = arrayOfNulls<String>(16)

        init {
            val DIGITS = "0123456789abcdef".toCharArray()
            for (i in 0 until 256) {
                HEXDUMP_TABLE[i shl 1] = DIGITS[i ushr 4 and 0x0F]
                HEXDUMP_TABLE[(i shl 1) + 1] = DIGITS[i and 0x0F]
            }

            // Generate the lookup table for hex dump paddings
            for (i in HEXPADDING.indices) {
                val padding = HEXPADDING.size - i
                val buf = StringBuilder(padding * 3)
                for (j in 0 until padding) {
                    buf.append("   ")
                }
                HEXPADDING[i] = buf.toString()
            }

            // Generate the lookup table for the start-offset header in each row (up to 64KiB).
            for (i in HEXDUMP_ROWPREFIXES.indices) {
                val buf = StringBuilder(12)
                buf.append(NEWLINE)
                buf.append(java.lang.Long.toHexString((i.toLong() shl 4) and 0xFFFFFFFFL or 0x100000000L))
                buf.setCharAt(buf.length - 9, '|')
                buf.append('|')
                HEXDUMP_ROWPREFIXES[i] = buf.toString()
            }

            // Generate the lookup table for byte-to-hex-dump conversion
            for (i in BYTE2HEX.indices) {
                BYTE2HEX[i] = ' ' + StringUtil.byteToHexStringPadded(i)
            }

            // Generate the lookup table for byte dump paddings
            for (i in BYTEPADDING.indices) {
                val padding = BYTEPADDING.size - i
                val buf = StringBuilder(padding)
                for (j in 0 until padding) {
                    buf.append(' ')
                }
                BYTEPADDING[i] = buf.toString()
            }

            // Generate the lookup table for byte-to-char conversion
            for (i in BYTE2CHAR.indices) {
                if (i <= 0x1f || i >= 0x7f) {
                    BYTE2CHAR[i] = '.'
                } else {
                    BYTE2CHAR[i] = i.toChar()
                }
            }
        }

        fun hexDump(buffer: ByteBuf, fromIndex: Int, length: Int): String {
            checkPositiveOrZero(length, "length")
            if (length == 0) {
                return ""
            }

            val endIndex = fromIndex + length
            val buf = CharArray(length shl 1)

            var srcIdx = fromIndex
            var dstIdx = 0
            while (srcIdx < endIndex) {
                System.arraycopy(
                    HEXDUMP_TABLE, buffer.getUnsignedByte(srcIdx).toInt() shl 1,
                    buf, dstIdx, 2
                )
                srcIdx++
                dstIdx += 2
            }

            return String(buf)
        }

        fun hexDump(array: ByteArray, fromIndex: Int, length: Int): String {
            checkPositiveOrZero(length, "length")
            if (length == 0) {
                return ""
            }

            val endIndex = fromIndex + length
            val buf = CharArray(length shl 1)

            var srcIdx = fromIndex
            var dstIdx = 0
            while (srcIdx < endIndex) {
                System.arraycopy(
                    HEXDUMP_TABLE, (array[srcIdx].toInt() and 0xFF) shl 1,
                    buf, dstIdx, 2
                )
                srcIdx++
                dstIdx += 2
            }

            return String(buf)
        }

        fun prettyHexDump(buffer: ByteBuf, offset: Int, length: Int): String {
            if (length == 0) {
                return StringUtil.EMPTY_STRING
            } else {
                val rows = length / 16 + (if (length and 15 == 0) 0 else 1) + 4
                val buf = StringBuilder(rows * 80)
                appendPrettyHexDump(buf, buffer, offset, length)
                return buf.toString()
            }
        }

        fun appendPrettyHexDump(dump: StringBuilder, buf: ByteBuf, offset: Int, length: Int) {
            if (isOutOfBounds(offset, length, buf.capacity())) {
                throw IndexOutOfBoundsException(
                    "expected: 0 <= offset($offset) <= offset + length($length) <= buf.capacity(${buf.capacity()})"
                )
            }
            if (length == 0) {
                return
            }
            dump.append(
                "         +-------------------------------------------------+" +
                    NEWLINE + "         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |" +
                    NEWLINE + "+--------+-------------------------------------------------+----------------+"
            )

            val fullRows = length ushr 4
            val remainder = length and 0xF

            // Dump the rows which have 16 bytes.
            for (row in 0 until fullRows) {
                val rowStartIndex = (row shl 4) + offset

                // Per-row prefix.
                appendHexDumpRowPrefix(dump, row, rowStartIndex)

                // Hex dump
                val rowEndIndex = rowStartIndex + 16
                for (j in rowStartIndex until rowEndIndex) {
                    dump.append(BYTE2HEX[buf.getUnsignedByte(j).toInt()])
                }
                dump.append(" |")

                // ASCII dump
                for (j in rowStartIndex until rowEndIndex) {
                    dump.append(BYTE2CHAR[buf.getUnsignedByte(j).toInt()])
                }
                dump.append('|')
            }

            // Dump the last row which has less than 16 bytes.
            if (remainder != 0) {
                val rowStartIndex = (fullRows shl 4) + offset
                appendHexDumpRowPrefix(dump, fullRows, rowStartIndex)

                // Hex dump
                val rowEndIndex = rowStartIndex + remainder
                for (j in rowStartIndex until rowEndIndex) {
                    dump.append(BYTE2HEX[buf.getUnsignedByte(j).toInt()])
                }
                dump.append(HEXPADDING[remainder])
                dump.append(" |")

                // Ascii dump
                for (j in rowStartIndex until rowEndIndex) {
                    dump.append(BYTE2CHAR[buf.getUnsignedByte(j).toInt()])
                }
                dump.append(BYTEPADDING[remainder])
                dump.append('|')
            }

            dump.append(
                NEWLINE +
                    "+--------+-------------------------------------------------+----------------+"
            )
        }

        private fun appendHexDumpRowPrefix(dump: StringBuilder, row: Int, rowStartIndex: Int) {
            if (row < HEXDUMP_ROWPREFIXES.size) {
                dump.append(HEXDUMP_ROWPREFIXES[row])
            } else {
                dump.append(NEWLINE)
                dump.append(java.lang.Long.toHexString(rowStartIndex.toLong() and 0xFFFFFFFFL or 0x100000000L))
                dump.setCharAt(dump.length - 9, '|')
                dump.append('|')
            }
        }
    }

    internal class ThreadLocalUnsafeDirectByteBuf private constructor(
        handle: Handle<ThreadLocalUnsafeDirectByteBuf>
    ) : UnpooledUnsafeDirectByteBuf(UnpooledByteBufAllocator.DEFAULT, 256, Integer.MAX_VALUE) {

        @Suppress("UNCHECKED_CAST")
        private val handle: EnhancedHandle<ThreadLocalUnsafeDirectByteBuf> =
            handle as EnhancedHandle<ThreadLocalUnsafeDirectByteBuf>

        override fun deallocate() {
            if (capacity() > THREAD_LOCAL_BUFFER_SIZE) {
                super.deallocate()
            } else {
                clear()
                handle.unguardedRecycle(this)
            }
        }

        companion object {
            private val RECYCLER: Recycler<ThreadLocalUnsafeDirectByteBuf> =
                object : Recycler<ThreadLocalUnsafeDirectByteBuf>() {
                    override fun newObject(handle: Handle<ThreadLocalUnsafeDirectByteBuf>): ThreadLocalUnsafeDirectByteBuf {
                        return ThreadLocalUnsafeDirectByteBuf(handle)
                    }
                }

            fun newInstance(): ThreadLocalUnsafeDirectByteBuf {
                val buf = RECYCLER.get()
                buf.resetRefCnt()
                return buf
            }
        }
    }

    internal class ThreadLocalDirectByteBuf private constructor(
        handle: Handle<ThreadLocalDirectByteBuf>
    ) : UnpooledDirectByteBuf(UnpooledByteBufAllocator.DEFAULT, 256, Integer.MAX_VALUE) {

        @Suppress("UNCHECKED_CAST")
        private val handle: EnhancedHandle<ThreadLocalDirectByteBuf> =
            handle as EnhancedHandle<ThreadLocalDirectByteBuf>

        override fun deallocate() {
            if (capacity() > THREAD_LOCAL_BUFFER_SIZE) {
                super.deallocate()
            } else {
                clear()
                handle.unguardedRecycle(this)
            }
        }

        companion object {
            private val RECYCLER: Recycler<ThreadLocalDirectByteBuf> =
                object : Recycler<ThreadLocalDirectByteBuf>() {
                    override fun newObject(handle: Handle<ThreadLocalDirectByteBuf>): ThreadLocalDirectByteBuf {
                        return ThreadLocalDirectByteBuf(handle)
                    }
                }

            fun newInstance(): ThreadLocalDirectByteBuf {
                val buf = RECYCLER.get()
                buf.resetRefCnt()
                return buf
            }
        }
    }

    /**
     * Returns `true` if the given [ByteBuf] is valid text using the given [Charset],
     * otherwise return `false`.
     *
     * @param buf The given [ByteBuf].
     * @param charset The specified [Charset].
     */
    @JvmStatic
    fun isText(buf: ByteBuf, charset: Charset): Boolean {
        return isText(buf, buf.readerIndex(), buf.readableBytes(), charset)
    }

    /**
     * Returns `true` if the specified [ByteBuf] starting at `index` with `length` is valid
     * text using the given [Charset], otherwise return `false`.
     *
     * @param buf The given [ByteBuf].
     * @param index The start index of the specified buffer.
     * @param length The length of the specified buffer.
     * @param charset The specified [Charset].
     *
     * @throws IndexOutOfBoundsException if `index` + `length` is greater than `buf.readableBytes`
     */
    @JvmStatic
    fun isText(buf: ByteBuf, index: Int, length: Int, charset: Charset): Boolean {
        checkNotNull(buf, "buf")
        checkNotNull(charset, "charset")
        val maxIndex = buf.readerIndex() + buf.readableBytes()
        if (index < 0 || length < 0 || index > maxIndex - length) {
            throw IndexOutOfBoundsException("index: $index length: $length")
        }
        if (charset == CharsetUtil.UTF_8) {
            return isUtf8(buf, index, length)
        } else if (charset == CharsetUtil.US_ASCII) {
            return isAscii(buf, index, length)
        } else {
            val decoder = CharsetUtil.decoder(charset, CodingErrorAction.REPORT, CodingErrorAction.REPORT)
            try {
                if (buf.nioBufferCount() == 1) {
                    decoder.decode(buf.nioBuffer(index, length))
                } else {
                    val heapBuffer = buf.alloc().heapBuffer(length)
                    try {
                        heapBuffer.writeBytes(buf, index, length)
                        decoder.decode(heapBuffer.internalNioBuffer(heapBuffer.readerIndex(), length))
                    } finally {
                        heapBuffer.release()
                    }
                }
                return true
            } catch (ignore: CharacterCodingException) {
                return false
            }
        }
    }

    /**
     * Aborts on a byte which is not a valid ASCII character.
     */
    private val FIND_NON_ASCII: ByteProcessor = object : ByteProcessor {
        override fun process(value: Byte): Boolean {
            return value >= 0
        }
    }

    /**
     * Returns `true` if the specified [ByteBuf] starting at `index` with `length` is valid
     * ASCII text, otherwise return `false`.
     *
     * @param buf    The given [ByteBuf].
     * @param index  The start index of the specified buffer.
     * @param length The length of the specified buffer.
     */
    private fun isAscii(buf: ByteBuf, index: Int, length: Int): Boolean {
        return buf.forEachByte(index, length, FIND_NON_ASCII) == -1
    }

    /**
     * Returns `true` if the specified [ByteBuf] starting at `index` with `length` is valid
     * UTF8 text, otherwise return `false`.
     *
     * @param buf The given [ByteBuf].
     * @param index The start index of the specified buffer.
     * @param length The length of the specified buffer.
     *
     * @see [UTF-8 Definition](https://www.ietf.org/rfc/rfc3629.txt)
     */
    private fun isUtf8(buf: ByteBuf, index: Int, length: Int): Boolean {
        val endIndex = index + length
        var idx = index
        while (idx < endIndex) {
            val b1 = buf.getByte(idx++)
            if (b1.toInt() and 0x80 == 0) {
                // 1 byte
                continue
            }
            if (b1.toInt() and 0xE0 == 0xC0) {
                // 2 bytes
                if (idx >= endIndex) { // no enough bytes
                    return false
                }
                val b2 = buf.getByte(idx++)
                if (b2.toInt() and 0xC0 != 0x80) { // 2nd byte not starts with 10
                    return false
                }
                if (b1.toInt() and 0xFF < 0xC2) { // out of lower bound
                    return false
                }
            } else if (b1.toInt() and 0xF0 == 0xE0) {
                // 3 bytes
                if (idx > endIndex - 2) { // no enough bytes
                    return false
                }
                val b2 = buf.getByte(idx++)
                val b3 = buf.getByte(idx++)
                if (b2.toInt() and 0xC0 != 0x80 || b3.toInt() and 0xC0 != 0x80) {
                    return false
                }
                if (b1.toInt() and 0x0F == 0x00 && b2.toInt() and 0xFF < 0xA0) { // out of lower bound
                    return false
                }
                if (b1.toInt() and 0x0F == 0x0D && b2.toInt() and 0xFF > 0x9F) { // out of upper bound
                    return false
                }
            } else if (b1.toInt() and 0xF8 == 0xF0) {
                // 4 bytes
                if (idx > endIndex - 3) { // no enough bytes
                    return false
                }
                val b2 = buf.getByte(idx++)
                val b3 = buf.getByte(idx++)
                val b4 = buf.getByte(idx++)
                if (b2.toInt() and 0xC0 != 0x80 || b3.toInt() and 0xC0 != 0x80 || b4.toInt() and 0xC0 != 0x80) {
                    // 2nd, 3rd or 4th bytes not start with 10
                    return false
                }
                if (b1.toInt() and 0xFF > 0xF4 // b1 invalid
                    || b1.toInt() and 0xFF == 0xF0 && b2.toInt() and 0xFF < 0x90 // b2 out of lower bound
                    || b1.toInt() and 0xFF == 0xF4 && b2.toInt() and 0xFF > 0x8F
                ) { // b2 out of upper bound
                    return false
                }
            } else {
                return false
            }
        }
        return true
    }

    /**
     * Read bytes from the given [ByteBuffer] into the given [OutputStream] using the `position` and
     * `length`. The position and limit of the given [ByteBuffer] may be adjusted.
     */
    @JvmStatic
    @Throws(IOException::class)
    internal fun readBytes(allocator: ByteBufAllocator, buffer: ByteBuffer, position: Int, length: Int, out: OutputStream) {
        if (buffer.hasArray()) {
            out.write(buffer.array(), position + buffer.arrayOffset(), length)
        } else {
            val chunkLen = Math.min(length, WRITE_CHUNK_SIZE)
            buffer.clear().position(position).limit(position + length)

            if (length <= MAX_TL_ARRAY_LEN || !allocator.isDirectBufferPooled()) {
                getBytes(buffer, threadLocalTempArray(chunkLen), 0, chunkLen, out, length)
            } else {
                // if direct buffers are pooled chances are good that heap buffers are pooled as well.
                val tmpBuf = allocator.heapBuffer(chunkLen)
                try {
                    val tmp = tmpBuf.array()
                    val offset = tmpBuf.arrayOffset()
                    getBytes(buffer, tmp, offset, chunkLen, out, length)
                } finally {
                    tmpBuf.release()
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun getBytes(inBuffer: ByteBuffer, `in`: ByteArray, inOffset: Int, inLen: Int, out: OutputStream, outLen: Int) {
        var remaining = outLen
        do {
            val len = Math.min(inLen, remaining)
            inBuffer.get(`in`, inOffset, len)
            out.write(`in`, inOffset, len)
            remaining -= len
        } while (remaining > 0)
    }

    /**
     * Set [AbstractByteBuf.leakDetector]'s [ResourceLeakDetector.LeakListener].
     *
     * @param leakListener If leakListener is not null, it will be notified once a ByteBuf leak is detected.
     */
    @JvmStatic
    fun setLeakListener(leakListener: ResourceLeakDetector.LeakListener) {
        AbstractByteBuf.leakDetector.setLeakListener(leakListener)
    }
}
