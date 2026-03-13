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

import io.netty.util.ByteProcessor
import io.netty.util.ReferenceCounted
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.channels.GatheringByteChannel
import java.nio.channels.ScatteringByteChannel
import java.nio.charset.Charset

/**
 * A random and sequential accessible sequence of zero or more bytes (octets).
 * This interface provides an abstract view for one or more primitive byte
 * arrays (`byte[]`) and [NIO buffers][ByteBuffer].
 */
abstract class ByteBuf : ReferenceCounted, Comparable<ByteBuf>, ByteBufConvertible {

    /**
     * Returns the number of bytes (octets) this buffer can contain.
     */
    abstract open fun capacity(): Int

    /**
     * Adjusts the capacity of this buffer. If the `newCapacity` is less than the current
     * capacity, the content of this buffer is truncated. If the `newCapacity` is greater
     * than the current capacity, the buffer is appended with unspecified data whose length is
     * `(newCapacity - currentCapacity)`.
     *
     * @throws IllegalArgumentException if the `newCapacity` is greater than [maxCapacity]
     */
    abstract open fun capacity(newCapacity: Int): ByteBuf

    /**
     * Returns the maximum allowed capacity of this buffer. This value provides an upper
     * bound on [capacity].
     */
    abstract open fun maxCapacity(): Int

    /**
     * Returns the [ByteBufAllocator] which created this buffer.
     */
    abstract open fun alloc(): ByteBufAllocator

    /**
     * Returns the [endianness](https://en.wikipedia.org/wiki/Endianness)
     * of this buffer.
     *
     * @deprecated use the Little Endian accessors, e.g. `getShortLE`, `getIntLE`
     * instead of creating a buffer with swapped `endianness`.
     */
    @Deprecated("Use Little Endian accessors instead")
    abstract open fun order(): ByteOrder

    /**
     * Returns a buffer with the specified `endianness` which shares the whole region,
     * indexes, and marks of this buffer.
     *
     * @deprecated use the Little Endian accessors, e.g. `getShortLE`, `getIntLE`
     * instead of creating a buffer with swapped `endianness`.
     */
    @Deprecated("Use Little Endian accessors instead")
    abstract open fun order(endianness: ByteOrder): ByteBuf

    /**
     * Return the underlying buffer instance if this buffer is a wrapper of another buffer.
     *
     * @return `null` if this buffer is not a wrapper
     */
    abstract open fun unwrap(): ByteBuf?

    /**
     * Returns `true` if and only if this buffer is backed by an
     * NIO direct buffer.
     */
    abstract open fun isDirect(): Boolean

    /**
     * Returns `true` if and only if this buffer is read-only.
     */
    abstract open fun isReadOnly(): Boolean

    /**
     * Returns a read-only version of this buffer.
     */
    abstract open fun asReadOnly(): ByteBuf

    /**
     * Returns the `readerIndex` of this buffer.
     */
    abstract open fun readerIndex(): Int

    /**
     * Sets the `readerIndex` of this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified `readerIndex` is
     *            less than `0` or
     *            greater than `this.writerIndex`
     */
    abstract open fun readerIndex(readerIndex: Int): ByteBuf

    /**
     * Returns the `writerIndex` of this buffer.
     */
    abstract open fun writerIndex(): Int

    /**
     * Sets the `writerIndex` of this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified `writerIndex` is
     *            less than `this.readerIndex` or
     *            greater than `this.capacity`
     */
    abstract open fun writerIndex(writerIndex: Int): ByteBuf

    /**
     * Sets the `readerIndex` and `writerIndex` of this buffer in one shot.
     *
     * @throws IndexOutOfBoundsException
     *         if the specified `readerIndex` is less than 0,
     *         if the specified `writerIndex` is less than the specified
     *         `readerIndex` or if the specified `writerIndex` is
     *         greater than `this.capacity`
     */
    abstract open fun setIndex(readerIndex: Int, writerIndex: Int): ByteBuf

    /**
     * Returns the number of readable bytes which is equal to
     * `(this.writerIndex - this.readerIndex)`.
     */
    abstract open fun readableBytes(): Int

    /**
     * Returns the number of writable bytes which is equal to
     * `(this.capacity - this.writerIndex)`.
     */
    abstract open fun writableBytes(): Int

    /**
     * Returns the maximum possible number of writable bytes, which is equal to
     * `(this.maxCapacity - this.writerIndex)`.
     */
    abstract open fun maxWritableBytes(): Int

    /**
     * Returns the maximum number of bytes which can be written for certain without involving
     * an internal reallocation or data-copy. The returned value will be >= [writableBytes]
     * and <= [maxWritableBytes].
     */
    open fun maxFastWritableBytes(): Int = writableBytes()

    /**
     * Returns `true`
     * if and only if `(this.writerIndex - this.readerIndex)` is greater
     * than `0`.
     */
    abstract open fun isReadable(): Boolean

    /**
     * Returns `true` if and only if this buffer contains equal to or more than the specified number of elements.
     */
    abstract open fun isReadable(size: Int): Boolean

    /**
     * Returns `true`
     * if and only if `(this.capacity - this.writerIndex)` is greater
     * than `0`.
     */
    abstract open fun isWritable(): Boolean

    /**
     * Returns `true` if and only if this buffer has enough room to allow writing the specified number of
     * elements.
     */
    abstract open fun isWritable(size: Int): Boolean

    /**
     * Sets the `readerIndex` and `writerIndex` of this buffer to `0`.
     * This method is identical to [setIndex(0, 0)][setIndex].
     */
    abstract open fun clear(): ByteBuf

    /**
     * Marks the current `readerIndex` in this buffer.
     */
    abstract open fun markReaderIndex(): ByteBuf

    /**
     * Repositions the current `readerIndex` to the marked `readerIndex` in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the current `writerIndex` is less than the marked `readerIndex`
     */
    abstract open fun resetReaderIndex(): ByteBuf

    /**
     * Marks the current `writerIndex` in this buffer.
     */
    abstract open fun markWriterIndex(): ByteBuf

    /**
     * Repositions the current `writerIndex` to the marked `writerIndex` in this buffer.
     *
     * @throws IndexOutOfBoundsException
     *         if the current `readerIndex` is greater than the marked `writerIndex`
     */
    abstract open fun resetWriterIndex(): ByteBuf

    /**
     * Discards the bytes between the 0th index and `readerIndex`.
     */
    abstract open fun discardReadBytes(): ByteBuf

    /**
     * Similar to [discardReadBytes] except that this method might discard
     * some, all, or none of read bytes depending on its internal implementation.
     */
    abstract open fun discardSomeReadBytes(): ByteBuf

    /**
     * Expands the buffer [capacity] to make sure the number of
     * [writable bytes][writableBytes] is equal to or greater than the specified value.
     *
     * @param minWritableBytes the expected minimum number of writable bytes
     * @throws IndexOutOfBoundsException if [writerIndex] + `minWritableBytes` > [maxCapacity]
     */
    abstract open fun ensureWritable(minWritableBytes: Int): ByteBuf

    /**
     * Expands the buffer [capacity] to make sure the number of
     * [writable bytes][writableBytes] is equal to or greater than the specified value.
     * Unlike [ensureWritable(Int)][ensureWritable], this method returns a status code.
     *
     * @return `0` if the buffer has enough writable bytes, and its capacity is unchanged.
     *         `1` if the buffer does not have enough bytes, and its capacity is unchanged.
     *         `2` if the buffer has enough writable bytes, and its capacity has been increased.
     *         `3` if the buffer does not have enough bytes, but its capacity has been
     *             increased to its maximum.
     */
    abstract open fun ensureWritable(minWritableBytes: Int, force: Boolean): Int

    // --- Get operations ---

    abstract open fun getBoolean(index: Int): Boolean
    abstract open fun getByte(index: Int): Byte
    abstract open fun getUnsignedByte(index: Int): Short
    abstract open fun getShort(index: Int): Short
    abstract open fun getShortLE(index: Int): Short
    abstract open fun getUnsignedShort(index: Int): Int
    abstract open fun getUnsignedShortLE(index: Int): Int
    abstract open fun getMedium(index: Int): Int
    abstract open fun getMediumLE(index: Int): Int
    abstract open fun getUnsignedMedium(index: Int): Int
    abstract open fun getUnsignedMediumLE(index: Int): Int
    abstract open fun getInt(index: Int): Int
    abstract open fun getIntLE(index: Int): Int
    abstract open fun getUnsignedInt(index: Int): Long
    abstract open fun getUnsignedIntLE(index: Int): Long
    abstract open fun getLong(index: Int): Long
    abstract open fun getLongLE(index: Int): Long
    abstract open fun getChar(index: Int): Char
    abstract open fun getFloat(index: Int): Float

    open fun getFloatLE(index: Int): Float = Float.fromBits(getIntLE(index))

    abstract open fun getDouble(index: Int): Double

    open fun getDoubleLE(index: Int): Double = Double.fromBits(getLongLE(index).toLong())

    abstract open fun getBytes(index: Int, dst: ByteBuf): ByteBuf
    abstract open fun getBytes(index: Int, dst: ByteBuf, length: Int): ByteBuf
    abstract open fun getBytes(index: Int, dst: ByteBuf, dstIndex: Int, length: Int): ByteBuf
    abstract open fun getBytes(index: Int, dst: ByteArray): ByteBuf
    abstract open fun getBytes(index: Int, dst: ByteArray, dstIndex: Int, length: Int): ByteBuf
    abstract open fun getBytes(index: Int, dst: ByteBuffer): ByteBuf

    @Throws(IOException::class)
    abstract open fun getBytes(index: Int, out: OutputStream, length: Int): ByteBuf

    @Throws(IOException::class)
    abstract open fun getBytes(index: Int, out: GatheringByteChannel, length: Int): Int

    @Throws(IOException::class)
    abstract open fun getBytes(index: Int, out: FileChannel, position: Long, length: Int): Int

    abstract open fun getCharSequence(index: Int, length: Int, charset: Charset): CharSequence

    // --- Set operations ---

    abstract open fun setBoolean(index: Int, value: Boolean): ByteBuf
    abstract open fun setByte(index: Int, value: Int): ByteBuf
    abstract open fun setShort(index: Int, value: Int): ByteBuf
    abstract open fun setShortLE(index: Int, value: Int): ByteBuf
    abstract open fun setMedium(index: Int, value: Int): ByteBuf
    abstract open fun setMediumLE(index: Int, value: Int): ByteBuf
    abstract open fun setInt(index: Int, value: Int): ByteBuf
    abstract open fun setIntLE(index: Int, value: Int): ByteBuf
    abstract open fun setLong(index: Int, value: Long): ByteBuf
    abstract open fun setLongLE(index: Int, value: Long): ByteBuf
    abstract open fun setChar(index: Int, value: Int): ByteBuf
    abstract open fun setFloat(index: Int, value: Float): ByteBuf

    open fun setFloatLE(index: Int, value: Float): ByteBuf = setIntLE(index, java.lang.Float.floatToRawIntBits(value))

    abstract open fun setDouble(index: Int, value: Double): ByteBuf

    open fun setDoubleLE(index: Int, value: Double): ByteBuf = setLongLE(index, java.lang.Double.doubleToRawLongBits(value))

    abstract open fun setBytes(index: Int, src: ByteBuf): ByteBuf
    abstract open fun setBytes(index: Int, src: ByteBuf, length: Int): ByteBuf
    abstract open fun setBytes(index: Int, src: ByteBuf, srcIndex: Int, length: Int): ByteBuf
    abstract open fun setBytes(index: Int, src: ByteArray): ByteBuf
    abstract open fun setBytes(index: Int, src: ByteArray, srcIndex: Int, length: Int): ByteBuf
    abstract open fun setBytes(index: Int, src: ByteBuffer): ByteBuf

    @Throws(IOException::class)
    abstract open fun setBytes(index: Int, `in`: InputStream, length: Int): Int

    @Throws(IOException::class)
    abstract open fun setBytes(index: Int, `in`: ScatteringByteChannel, length: Int): Int

    @Throws(IOException::class)
    abstract open fun setBytes(index: Int, `in`: FileChannel, position: Long, length: Int): Int

    abstract open fun setZero(index: Int, length: Int): ByteBuf
    abstract open fun setCharSequence(index: Int, sequence: CharSequence, charset: Charset): Int

    // --- Read operations ---

    abstract open fun readBoolean(): Boolean
    abstract open fun readByte(): Byte
    abstract open fun readUnsignedByte(): Short
    abstract open fun readShort(): Short
    abstract open fun readShortLE(): Short
    abstract open fun readUnsignedShort(): Int
    abstract open fun readUnsignedShortLE(): Int
    abstract open fun readMedium(): Int
    abstract open fun readMediumLE(): Int
    abstract open fun readUnsignedMedium(): Int
    abstract open fun readUnsignedMediumLE(): Int
    abstract open fun readInt(): Int
    abstract open fun readIntLE(): Int
    abstract open fun readUnsignedInt(): Long
    abstract open fun readUnsignedIntLE(): Long
    abstract open fun readLong(): Long
    abstract open fun readLongLE(): Long
    abstract open fun readChar(): Char
    abstract open fun readFloat(): Float

    open fun readFloatLE(): Float = Float.fromBits(readIntLE())

    abstract open fun readDouble(): Double

    open fun readDoubleLE(): Double = Double.fromBits(readLongLE().toLong())

    abstract open fun readBytes(length: Int): ByteBuf
    abstract open fun readSlice(length: Int): ByteBuf
    abstract open fun readRetainedSlice(length: Int): ByteBuf
    abstract open fun readBytes(dst: ByteBuf): ByteBuf
    abstract open fun readBytes(dst: ByteBuf, length: Int): ByteBuf
    abstract open fun readBytes(dst: ByteBuf, dstIndex: Int, length: Int): ByteBuf
    abstract open fun readBytes(dst: ByteArray): ByteBuf
    abstract open fun readBytes(dst: ByteArray, dstIndex: Int, length: Int): ByteBuf
    abstract open fun readBytes(dst: ByteBuffer): ByteBuf

    @Throws(IOException::class)
    abstract open fun readBytes(out: OutputStream, length: Int): ByteBuf

    @Throws(IOException::class)
    abstract open fun readBytes(out: GatheringByteChannel, length: Int): Int

    abstract open fun readCharSequence(length: Int, charset: Charset): CharSequence

    open fun readString(length: Int, charset: Charset): String {
        val readerIdx = readerIndex()
        val string = toString(readerIdx, length, charset)
        readerIndex(readerIdx + length)
        return string
    }

    @Throws(IOException::class)
    abstract open fun readBytes(out: FileChannel, position: Long, length: Int): Int

    abstract open fun skipBytes(length: Int): ByteBuf

    // --- Write operations ---

    abstract open fun writeBoolean(value: Boolean): ByteBuf
    abstract open fun writeByte(value: Int): ByteBuf
    abstract open fun writeShort(value: Int): ByteBuf
    abstract open fun writeShortLE(value: Int): ByteBuf
    abstract open fun writeMedium(value: Int): ByteBuf
    abstract open fun writeMediumLE(value: Int): ByteBuf
    abstract open fun writeInt(value: Int): ByteBuf
    abstract open fun writeIntLE(value: Int): ByteBuf
    abstract open fun writeLong(value: Long): ByteBuf
    abstract open fun writeLongLE(value: Long): ByteBuf
    abstract open fun writeChar(value: Int): ByteBuf
    abstract open fun writeFloat(value: Float): ByteBuf

    open fun writeFloatLE(value: Float): ByteBuf = writeIntLE(java.lang.Float.floatToRawIntBits(value))

    abstract open fun writeDouble(value: Double): ByteBuf

    open fun writeDoubleLE(value: Double): ByteBuf = writeLongLE(java.lang.Double.doubleToRawLongBits(value))

    abstract open fun writeBytes(src: ByteBuf): ByteBuf
    abstract open fun writeBytes(src: ByteBuf, length: Int): ByteBuf
    abstract open fun writeBytes(src: ByteBuf, srcIndex: Int, length: Int): ByteBuf
    abstract open fun writeBytes(src: ByteArray): ByteBuf
    abstract open fun writeBytes(src: ByteArray, srcIndex: Int, length: Int): ByteBuf
    abstract open fun writeBytes(src: ByteBuffer): ByteBuf

    @Throws(IOException::class)
    abstract open fun writeBytes(`in`: InputStream, length: Int): Int

    @Throws(IOException::class)
    abstract open fun writeBytes(`in`: ScatteringByteChannel, length: Int): Int

    @Throws(IOException::class)
    abstract open fun writeBytes(`in`: FileChannel, position: Long, length: Int): Int

    abstract open fun writeZero(length: Int): ByteBuf
    abstract open fun writeCharSequence(sequence: CharSequence, charset: Charset): Int

    // --- Search operations ---

    abstract open fun indexOf(fromIndex: Int, toIndex: Int, value: Byte): Int
    abstract open fun bytesBefore(value: Byte): Int
    abstract open fun bytesBefore(length: Int, value: Byte): Int
    abstract open fun bytesBefore(index: Int, length: Int, value: Byte): Int
    abstract open fun forEachByte(processor: ByteProcessor): Int
    abstract open fun forEachByte(index: Int, length: Int, processor: ByteProcessor): Int
    abstract open fun forEachByteDesc(processor: ByteProcessor): Int
    abstract open fun forEachByteDesc(index: Int, length: Int, processor: ByteProcessor): Int

    // --- Copy / Slice / Duplicate ---

    abstract open fun copy(): ByteBuf
    abstract open fun copy(index: Int, length: Int): ByteBuf
    abstract open fun slice(): ByteBuf
    abstract open fun retainedSlice(): ByteBuf
    abstract open fun slice(index: Int, length: Int): ByteBuf
    abstract open fun retainedSlice(index: Int, length: Int): ByteBuf
    abstract open fun duplicate(): ByteBuf
    abstract open fun retainedDuplicate(): ByteBuf

    // --- NIO buffer ---

    abstract open fun nioBufferCount(): Int
    abstract open fun nioBuffer(): ByteBuffer
    abstract open fun nioBuffer(index: Int, length: Int): ByteBuffer
    abstract open fun internalNioBuffer(index: Int, length: Int): ByteBuffer
    abstract open fun nioBuffers(): Array<ByteBuffer>
    abstract open fun nioBuffers(index: Int, length: Int): Array<ByteBuffer>

    // --- Array ---

    abstract open fun hasArray(): Boolean
    abstract open fun array(): ByteArray
    abstract open fun arrayOffset(): Int

    // --- Memory address ---

    abstract open fun hasMemoryAddress(): Boolean
    abstract open fun memoryAddress(): Long

    /**
     * Returns `true` if this [ByteBuf] implementation is backed by a single memory region.
     */
    open fun isContiguous(): Boolean = false

    /**
     * A `ByteBuf` can turn into itself.
     */
    override fun asByteBuf(): ByteBuf = this

    // --- String ---

    abstract open fun toString(charset: Charset): String
    abstract open fun toString(index: Int, length: Int, charset: Charset): String
    abstract override fun hashCode(): Int
    abstract override fun equals(other: Any?): Boolean
    abstract override fun compareTo(other: ByteBuf): Int
    abstract override fun toString(): String

    // --- ReferenceCounted ---

    abstract override fun retain(increment: Int): ByteBuf
    abstract override fun retain(): ByteBuf
    abstract override fun touch(): ByteBuf
    abstract override fun touch(hint: Any?): ByteBuf

    /**
     * Used internally by [AbstractByteBuf.ensureAccessible] to try to guard
     * against using the buffer after it was released (best-effort).
     */
    open fun isAccessible(): Boolean = refCnt() != 0
}
