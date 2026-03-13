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
package io.netty.buffer

import io.netty.util.ByteProcessor
import io.netty.util.internal.EmptyArrays
import io.netty.util.internal.ObjectUtil
import io.netty.util.internal.PlatformDependent
import io.netty.util.internal.StringUtil
import io.netty.util.internal.ObjectUtil.checkPositiveOrZero
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ReadOnlyBufferException
import java.nio.channels.FileChannel
import java.nio.channels.GatheringByteChannel
import java.nio.channels.ScatteringByteChannel
import java.nio.charset.Charset

/**
 * An empty [ByteBuf] whose capacity and maximum capacity are all `0`.
 */
open class EmptyByteBuf(private val alloc: ByteBufAllocator) : ByteBuf() {

    private val order: ByteOrder
    private val str: String
    private var swapped: EmptyByteBuf? = null

    init {
        ObjectUtil.checkNotNull(alloc, "alloc")
        this.order = ByteOrder.BIG_ENDIAN
        str = "${StringUtil.simpleClassName(this)}BE"
    }

    private constructor(alloc: ByteBufAllocator, order: ByteOrder) : this(alloc) {
        // Use reflection-style trick: we set the fields directly
    }

    // Private constructor that actually sets order
    companion object {
        @JvmField
        val EMPTY_BYTE_BUF_HASH_CODE: Int = 1

        private val EMPTY_BYTE_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0)

        @JvmField
        val EMPTY_BYTE_BUFFER_ADDRESS: Long

        init {
            var emptyByteBufferAddress = 0L
            try {
                if (PlatformDependent.hasUnsafe()) {
                    emptyByteBufferAddress = PlatformDependent.directBufferAddress(EMPTY_BYTE_BUFFER)
                }
            } catch (t: Throwable) {
                // Ignore
            }
            EMPTY_BYTE_BUFFER_ADDRESS = emptyByteBufferAddress
        }

        private fun createWithOrder(alloc: ByteBufAllocator, order: ByteOrder): EmptyByteBuf {
            val buf = EmptyByteBuf(alloc)
            // We need to use reflection-like approach since we can't call private constructor
            // Instead, let's use a factory approach
            return EmptyByteBufWithOrder(alloc, order)
        }
    }

    private class EmptyByteBufWithOrder(alloc: ByteBufAllocator, private val orderOverride: ByteOrder) : EmptyByteBuf(alloc) {
        private val strOverride: String = "${StringUtil.simpleClassName(this)}${if (orderOverride == ByteOrder.BIG_ENDIAN) "BE" else "LE"}"
        override fun order(): ByteOrder = orderOverride
        override fun toString(): String = strOverride
    }

    override fun capacity(): Int = 0

    override fun capacity(newCapacity: Int): ByteBuf {
        throw ReadOnlyBufferException()
    }

    override fun alloc(): ByteBufAllocator = alloc

    override fun order(): ByteOrder = order

    override fun unwrap(): ByteBuf? = null

    override fun asReadOnly(): ByteBuf = Unpooled.unmodifiableBuffer(this)

    override fun isReadOnly(): Boolean = false

    override fun isDirect(): Boolean = true

    override fun maxCapacity(): Int = 0

    override fun order(endianness: ByteOrder): ByteBuf {
        if (ObjectUtil.checkNotNull(endianness, "endianness") == order()) {
            return this
        }

        var swapped = this.swapped
        if (swapped != null) {
            return swapped
        }

        swapped = createWithOrder(alloc(), endianness)
        this.swapped = swapped
        return swapped
    }

    override fun readerIndex(): Int = 0

    override fun readerIndex(readerIndex: Int): ByteBuf = checkIndex(readerIndex)

    override fun writerIndex(): Int = 0

    override fun writerIndex(writerIndex: Int): ByteBuf = checkIndex(writerIndex)

    override fun setIndex(readerIndex: Int, writerIndex: Int): ByteBuf {
        checkIndex(readerIndex)
        checkIndex(writerIndex)
        return this
    }

    override fun readableBytes(): Int = 0

    override fun writableBytes(): Int = 0

    override fun maxWritableBytes(): Int = 0

    override fun isReadable(): Boolean = false

    override fun isWritable(): Boolean = false

    override fun clear(): ByteBuf = this

    override fun markReaderIndex(): ByteBuf = this

    override fun resetReaderIndex(): ByteBuf = this

    override fun markWriterIndex(): ByteBuf = this

    override fun resetWriterIndex(): ByteBuf = this

    override fun discardReadBytes(): ByteBuf = this

    override fun discardSomeReadBytes(): ByteBuf = this

    override fun ensureWritable(minWritableBytes: Int): ByteBuf {
        checkPositiveOrZero(minWritableBytes, "minWritableBytes")
        if (minWritableBytes != 0) {
            throw IndexOutOfBoundsException()
        }
        return this
    }

    override fun ensureWritable(minWritableBytes: Int, force: Boolean): Int {
        checkPositiveOrZero(minWritableBytes, "minWritableBytes")
        return if (minWritableBytes == 0) 0 else 1
    }

    override fun getBoolean(index: Int): Boolean { throw IndexOutOfBoundsException() }
    override fun getByte(index: Int): Byte { throw IndexOutOfBoundsException() }
    override fun getUnsignedByte(index: Int): Short { throw IndexOutOfBoundsException() }
    override fun getShort(index: Int): Short { throw IndexOutOfBoundsException() }
    override fun getShortLE(index: Int): Short { throw IndexOutOfBoundsException() }
    override fun getUnsignedShort(index: Int): Int { throw IndexOutOfBoundsException() }
    override fun getUnsignedShortLE(index: Int): Int { throw IndexOutOfBoundsException() }
    override fun getMedium(index: Int): Int { throw IndexOutOfBoundsException() }
    override fun getMediumLE(index: Int): Int { throw IndexOutOfBoundsException() }
    override fun getUnsignedMedium(index: Int): Int { throw IndexOutOfBoundsException() }
    override fun getUnsignedMediumLE(index: Int): Int { throw IndexOutOfBoundsException() }
    override fun getInt(index: Int): Int { throw IndexOutOfBoundsException() }
    override fun getIntLE(index: Int): Int { throw IndexOutOfBoundsException() }
    override fun getUnsignedInt(index: Int): Long { throw IndexOutOfBoundsException() }
    override fun getUnsignedIntLE(index: Int): Long { throw IndexOutOfBoundsException() }
    override fun getLong(index: Int): Long { throw IndexOutOfBoundsException() }
    override fun getLongLE(index: Int): Long { throw IndexOutOfBoundsException() }
    override fun getChar(index: Int): Char { throw IndexOutOfBoundsException() }
    override fun getFloat(index: Int): Float { throw IndexOutOfBoundsException() }
    override fun getDouble(index: Int): Double { throw IndexOutOfBoundsException() }

    override fun getBytes(index: Int, dst: ByteBuf): ByteBuf = checkIndex(index, dst.writableBytes())
    override fun getBytes(index: Int, dst: ByteBuf, length: Int): ByteBuf = checkIndex(index, length)
    override fun getBytes(index: Int, dst: ByteBuf, dstIndex: Int, length: Int): ByteBuf = checkIndex(index, length)
    override fun getBytes(index: Int, dst: ByteArray): ByteBuf = checkIndex(index, dst.size)
    override fun getBytes(index: Int, dst: ByteArray, dstIndex: Int, length: Int): ByteBuf = checkIndex(index, length)
    override fun getBytes(index: Int, dst: ByteBuffer): ByteBuf = checkIndex(index, dst.remaining())
    override fun getBytes(index: Int, out: OutputStream, length: Int): ByteBuf = checkIndex(index, length)

    override fun getBytes(index: Int, out: GatheringByteChannel, length: Int): Int {
        checkIndex(index, length)
        return 0
    }

    override fun getBytes(index: Int, out: FileChannel, position: Long, length: Int): Int {
        checkIndex(index, length)
        return 0
    }

    override fun getCharSequence(index: Int, length: Int, charset: Charset): CharSequence {
        checkIndex(index, length)
        return ""
    }

    override fun setBoolean(index: Int, value: Boolean): ByteBuf { throw IndexOutOfBoundsException() }
    override fun setByte(index: Int, value: Int): ByteBuf { throw IndexOutOfBoundsException() }
    override fun setShort(index: Int, value: Int): ByteBuf { throw IndexOutOfBoundsException() }
    override fun setShortLE(index: Int, value: Int): ByteBuf { throw IndexOutOfBoundsException() }
    override fun setMedium(index: Int, value: Int): ByteBuf { throw IndexOutOfBoundsException() }
    override fun setMediumLE(index: Int, value: Int): ByteBuf { throw IndexOutOfBoundsException() }
    override fun setInt(index: Int, value: Int): ByteBuf { throw IndexOutOfBoundsException() }
    override fun setIntLE(index: Int, value: Int): ByteBuf { throw IndexOutOfBoundsException() }
    override fun setLong(index: Int, value: Long): ByteBuf { throw IndexOutOfBoundsException() }
    override fun setLongLE(index: Int, value: Long): ByteBuf { throw IndexOutOfBoundsException() }
    override fun setChar(index: Int, value: Int): ByteBuf { throw IndexOutOfBoundsException() }
    override fun setFloat(index: Int, value: Float): ByteBuf { throw IndexOutOfBoundsException() }
    override fun setDouble(index: Int, value: Double): ByteBuf { throw IndexOutOfBoundsException() }

    override fun setBytes(index: Int, src: ByteBuf): ByteBuf { throw IndexOutOfBoundsException() }
    override fun setBytes(index: Int, src: ByteBuf, length: Int): ByteBuf = checkIndex(index, length)
    override fun setBytes(index: Int, src: ByteBuf, srcIndex: Int, length: Int): ByteBuf = checkIndex(index, length)
    override fun setBytes(index: Int, src: ByteArray): ByteBuf = checkIndex(index, src.size)
    override fun setBytes(index: Int, src: ByteArray, srcIndex: Int, length: Int): ByteBuf = checkIndex(index, length)
    override fun setBytes(index: Int, src: ByteBuffer): ByteBuf = checkIndex(index, src.remaining())

    override fun setBytes(index: Int, `in`: InputStream, length: Int): Int {
        checkIndex(index, length)
        return 0
    }

    override fun setBytes(index: Int, `in`: ScatteringByteChannel, length: Int): Int {
        checkIndex(index, length)
        return 0
    }

    override fun setBytes(index: Int, `in`: FileChannel, position: Long, length: Int): Int {
        checkIndex(index, length)
        return 0
    }

    override fun setZero(index: Int, length: Int): ByteBuf = checkIndex(index, length)

    override fun setCharSequence(index: Int, sequence: CharSequence, charset: Charset): Int {
        throw IndexOutOfBoundsException()
    }

    override fun readBoolean(): Boolean { throw IndexOutOfBoundsException() }
    override fun readByte(): Byte { throw IndexOutOfBoundsException() }
    override fun readUnsignedByte(): Short { throw IndexOutOfBoundsException() }
    override fun readShort(): Short { throw IndexOutOfBoundsException() }
    override fun readShortLE(): Short { throw IndexOutOfBoundsException() }
    override fun readUnsignedShort(): Int { throw IndexOutOfBoundsException() }
    override fun readUnsignedShortLE(): Int { throw IndexOutOfBoundsException() }
    override fun readMedium(): Int { throw IndexOutOfBoundsException() }
    override fun readMediumLE(): Int { throw IndexOutOfBoundsException() }
    override fun readUnsignedMedium(): Int { throw IndexOutOfBoundsException() }
    override fun readUnsignedMediumLE(): Int { throw IndexOutOfBoundsException() }
    override fun readInt(): Int { throw IndexOutOfBoundsException() }
    override fun readIntLE(): Int { throw IndexOutOfBoundsException() }
    override fun readUnsignedInt(): Long { throw IndexOutOfBoundsException() }
    override fun readUnsignedIntLE(): Long { throw IndexOutOfBoundsException() }
    override fun readLong(): Long { throw IndexOutOfBoundsException() }
    override fun readLongLE(): Long { throw IndexOutOfBoundsException() }
    override fun readChar(): Char { throw IndexOutOfBoundsException() }
    override fun readFloat(): Float { throw IndexOutOfBoundsException() }
    override fun readDouble(): Double { throw IndexOutOfBoundsException() }

    override fun readBytes(length: Int): ByteBuf = checkLength(length)
    override fun readSlice(length: Int): ByteBuf = checkLength(length)
    override fun readRetainedSlice(length: Int): ByteBuf = checkLength(length)
    override fun readBytes(dst: ByteBuf): ByteBuf = checkLength(dst.writableBytes())
    override fun readBytes(dst: ByteBuf, length: Int): ByteBuf = checkLength(length)
    override fun readBytes(dst: ByteBuf, dstIndex: Int, length: Int): ByteBuf = checkLength(length)
    override fun readBytes(dst: ByteArray): ByteBuf = checkLength(dst.size)
    override fun readBytes(dst: ByteArray, dstIndex: Int, length: Int): ByteBuf = checkLength(length)
    override fun readBytes(dst: ByteBuffer): ByteBuf = checkLength(dst.remaining())
    override fun readBytes(out: OutputStream, length: Int): ByteBuf = checkLength(length)

    override fun readBytes(out: GatheringByteChannel, length: Int): Int {
        checkLength(length)
        return 0
    }

    override fun readBytes(out: FileChannel, position: Long, length: Int): Int {
        checkLength(length)
        return 0
    }

    override fun readCharSequence(length: Int, charset: Charset): CharSequence {
        checkLength(length)
        return StringUtil.EMPTY_STRING
    }

    override fun readString(length: Int, charset: Charset): String {
        checkLength(length)
        return StringUtil.EMPTY_STRING
    }

    override fun skipBytes(length: Int): ByteBuf = checkLength(length)

    override fun writeBoolean(value: Boolean): ByteBuf { throw IndexOutOfBoundsException() }
    override fun writeByte(value: Int): ByteBuf { throw IndexOutOfBoundsException() }
    override fun writeShort(value: Int): ByteBuf { throw IndexOutOfBoundsException() }
    override fun writeShortLE(value: Int): ByteBuf { throw IndexOutOfBoundsException() }
    override fun writeMedium(value: Int): ByteBuf { throw IndexOutOfBoundsException() }
    override fun writeMediumLE(value: Int): ByteBuf { throw IndexOutOfBoundsException() }
    override fun writeInt(value: Int): ByteBuf { throw IndexOutOfBoundsException() }
    override fun writeIntLE(value: Int): ByteBuf { throw IndexOutOfBoundsException() }
    override fun writeLong(value: Long): ByteBuf { throw IndexOutOfBoundsException() }
    override fun writeLongLE(value: Long): ByteBuf { throw IndexOutOfBoundsException() }
    override fun writeChar(value: Int): ByteBuf { throw IndexOutOfBoundsException() }
    override fun writeFloat(value: Float): ByteBuf { throw IndexOutOfBoundsException() }
    override fun writeDouble(value: Double): ByteBuf { throw IndexOutOfBoundsException() }

    override fun writeBytes(src: ByteBuf): ByteBuf = checkLength(src.readableBytes())
    override fun writeBytes(src: ByteBuf, length: Int): ByteBuf = checkLength(length)
    override fun writeBytes(src: ByteBuf, srcIndex: Int, length: Int): ByteBuf = checkLength(length)
    override fun writeBytes(src: ByteArray): ByteBuf = checkLength(src.size)
    override fun writeBytes(src: ByteArray, srcIndex: Int, length: Int): ByteBuf = checkLength(length)
    override fun writeBytes(src: ByteBuffer): ByteBuf = checkLength(src.remaining())

    override fun writeBytes(`in`: InputStream, length: Int): Int {
        checkLength(length)
        return 0
    }

    override fun writeBytes(`in`: ScatteringByteChannel, length: Int): Int {
        checkLength(length)
        return 0
    }

    override fun writeBytes(`in`: FileChannel, position: Long, length: Int): Int {
        checkLength(length)
        return 0
    }

    override fun writeZero(length: Int): ByteBuf = checkLength(length)

    override fun writeCharSequence(sequence: CharSequence, charset: Charset): Int {
        throw IndexOutOfBoundsException()
    }

    override fun indexOf(fromIndex: Int, toIndex: Int, value: Byte): Int {
        checkIndex(fromIndex)
        checkIndex(toIndex)
        return -1
    }

    override fun bytesBefore(value: Byte): Int = -1

    override fun bytesBefore(length: Int, value: Byte): Int {
        checkLength(length)
        return -1
    }

    override fun bytesBefore(index: Int, length: Int, value: Byte): Int {
        checkIndex(index, length)
        return -1
    }

    override fun forEachByte(processor: ByteProcessor): Int = -1

    override fun forEachByte(index: Int, length: Int, processor: ByteProcessor): Int {
        checkIndex(index, length)
        return -1
    }

    override fun forEachByteDesc(processor: ByteProcessor): Int = -1

    override fun forEachByteDesc(index: Int, length: Int, processor: ByteProcessor): Int {
        checkIndex(index, length)
        return -1
    }

    override fun copy(): ByteBuf = this

    override fun copy(index: Int, length: Int): ByteBuf = checkIndex(index, length)

    override fun slice(): ByteBuf = this

    override fun retainedSlice(): ByteBuf = this

    override fun slice(index: Int, length: Int): ByteBuf = checkIndex(index, length)

    override fun retainedSlice(index: Int, length: Int): ByteBuf = checkIndex(index, length)

    override fun duplicate(): ByteBuf = this

    override fun retainedDuplicate(): ByteBuf = this

    override fun nioBufferCount(): Int = 1

    override fun nioBuffer(): ByteBuffer = EMPTY_BYTE_BUFFER

    override fun nioBuffer(index: Int, length: Int): ByteBuffer {
        checkIndex(index, length)
        return nioBuffer()
    }

    override fun nioBuffers(): Array<ByteBuffer> = arrayOf(EMPTY_BYTE_BUFFER)

    override fun nioBuffers(index: Int, length: Int): Array<ByteBuffer> {
        checkIndex(index, length)
        return nioBuffers()
    }

    override fun internalNioBuffer(index: Int, length: Int): ByteBuffer = EMPTY_BYTE_BUFFER

    override fun hasArray(): Boolean = true

    override fun array(): ByteArray = EmptyArrays.EMPTY_BYTES

    override fun arrayOffset(): Int = 0

    override fun hasMemoryAddress(): Boolean = EMPTY_BYTE_BUFFER_ADDRESS != 0L

    override fun memoryAddress(): Long {
        if (hasMemoryAddress()) {
            return EMPTY_BYTE_BUFFER_ADDRESS
        }
        throw UnsupportedOperationException()
    }

    override fun isContiguous(): Boolean = true

    override fun toString(charset: Charset): String = ""

    override fun toString(index: Int, length: Int, charset: Charset): String {
        checkIndex(index, length)
        return toString(charset)
    }

    override fun hashCode(): Int = EMPTY_BYTE_BUF_HASH_CODE

    override fun equals(other: Any?): Boolean {
        return other is ByteBuf && !other.isReadable()
    }

    override fun compareTo(other: ByteBuf): Int {
        return if (other.isReadable()) -1 else 0
    }

    override fun toString(): String = str

    override fun isReadable(size: Int): Boolean = false

    override fun isWritable(size: Int): Boolean = false

    override fun refCnt(): Int = 1

    override fun retain(): ByteBuf = this

    override fun retain(increment: Int): ByteBuf = this

    override fun touch(): ByteBuf = this

    override fun touch(hint: Any?): ByteBuf = this

    override fun release(): Boolean = false

    override fun release(decrement: Int): Boolean = false

    private fun checkIndex(index: Int): ByteBuf {
        if (index != 0) {
            throw IndexOutOfBoundsException()
        }
        return this
    }

    private fun checkIndex(index: Int, length: Int): ByteBuf {
        checkPositiveOrZero(length, "length")
        if (index != 0 || length != 0) {
            throw IndexOutOfBoundsException()
        }
        return this
    }

    private fun checkLength(length: Int): ByteBuf {
        checkPositiveOrZero(length, "length")
        if (length != 0) {
            throw IndexOutOfBoundsException()
        }
        return this
    }
}
