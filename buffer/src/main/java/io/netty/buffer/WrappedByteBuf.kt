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
import io.netty.util.internal.ObjectUtil
import io.netty.util.internal.StringUtil
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
 * Wraps another [ByteBuf].
 *
 * It's important that the [readerIndex] and [writerIndex] will not do any adjustments on the
 * indices on the fly because of internal optimizations made by [ByteBufUtil.writeAscii]
 * and [ByteBufUtil.writeUtf8].
 */
open class WrappedByteBuf protected constructor(
    @JvmField internal val buf: ByteBuf
) : ByteBuf() {

    init {
        ObjectUtil.checkNotNull(buf, "buf")
    }

    final override fun hasMemoryAddress(): Boolean = buf.hasMemoryAddress()

    override fun isContiguous(): Boolean = buf.isContiguous()

    final override fun memoryAddress(): Long = buf.memoryAddress()

    final override fun capacity(): Int = buf.capacity()

    override fun capacity(newCapacity: Int): ByteBuf {
        buf.capacity(newCapacity)
        return this
    }

    final override fun maxCapacity(): Int = buf.maxCapacity()

    final override fun alloc(): ByteBufAllocator = buf.alloc()

    @Suppress("DEPRECATION")
    final override fun order(): ByteOrder = buf.order()

    @Suppress("DEPRECATION")
    override fun order(endianness: ByteOrder): ByteBuf = buf.order(endianness)

    final override fun unwrap(): ByteBuf = buf

    override fun asReadOnly(): ByteBuf = buf.asReadOnly()

    override fun isReadOnly(): Boolean = buf.isReadOnly()

    final override fun isDirect(): Boolean = buf.isDirect()

    final override fun readerIndex(): Int = buf.readerIndex()

    final override fun readerIndex(readerIndex: Int): ByteBuf {
        buf.readerIndex(readerIndex)
        return this
    }

    final override fun writerIndex(): Int = buf.writerIndex()

    final override fun writerIndex(writerIndex: Int): ByteBuf {
        buf.writerIndex(writerIndex)
        return this
    }

    override fun setIndex(readerIndex: Int, writerIndex: Int): ByteBuf {
        buf.setIndex(readerIndex, writerIndex)
        return this
    }

    final override fun readableBytes(): Int = buf.readableBytes()

    final override fun writableBytes(): Int = buf.writableBytes()

    final override fun maxWritableBytes(): Int = buf.maxWritableBytes()

    override fun maxFastWritableBytes(): Int = buf.maxFastWritableBytes()

    final override fun isReadable(): Boolean = buf.isReadable()

    final override fun isWritable(): Boolean = buf.isWritable()

    final override fun clear(): ByteBuf {
        buf.clear()
        return this
    }

    final override fun markReaderIndex(): ByteBuf {
        buf.markReaderIndex()
        return this
    }

    final override fun resetReaderIndex(): ByteBuf {
        buf.resetReaderIndex()
        return this
    }

    final override fun markWriterIndex(): ByteBuf {
        buf.markWriterIndex()
        return this
    }

    final override fun resetWriterIndex(): ByteBuf {
        buf.resetWriterIndex()
        return this
    }

    override fun discardReadBytes(): ByteBuf {
        buf.discardReadBytes()
        return this
    }

    override fun discardSomeReadBytes(): ByteBuf {
        buf.discardSomeReadBytes()
        return this
    }

    override fun ensureWritable(minWritableBytes: Int): ByteBuf {
        buf.ensureWritable(minWritableBytes)
        return this
    }

    override fun ensureWritable(minWritableBytes: Int, force: Boolean): Int =
        buf.ensureWritable(minWritableBytes, force)

    override fun getBoolean(index: Int): Boolean = buf.getBoolean(index)

    override fun getByte(index: Int): Byte = buf.getByte(index)

    override fun getUnsignedByte(index: Int): Short = buf.getUnsignedByte(index)

    override fun getShort(index: Int): Short = buf.getShort(index)

    override fun getShortLE(index: Int): Short = buf.getShortLE(index)

    override fun getUnsignedShort(index: Int): Int = buf.getUnsignedShort(index)

    override fun getUnsignedShortLE(index: Int): Int = buf.getUnsignedShortLE(index)

    override fun getMedium(index: Int): Int = buf.getMedium(index)

    override fun getMediumLE(index: Int): Int = buf.getMediumLE(index)

    override fun getUnsignedMedium(index: Int): Int = buf.getUnsignedMedium(index)

    override fun getUnsignedMediumLE(index: Int): Int = buf.getUnsignedMediumLE(index)

    override fun getInt(index: Int): Int = buf.getInt(index)

    override fun getIntLE(index: Int): Int = buf.getIntLE(index)

    override fun getUnsignedInt(index: Int): Long = buf.getUnsignedInt(index)

    override fun getUnsignedIntLE(index: Int): Long = buf.getUnsignedIntLE(index)

    override fun getLong(index: Int): Long = buf.getLong(index)

    override fun getLongLE(index: Int): Long = buf.getLongLE(index)

    override fun getChar(index: Int): Char = buf.getChar(index)

    override fun getFloat(index: Int): Float = buf.getFloat(index)

    override fun getDouble(index: Int): Double = buf.getDouble(index)

    override fun getBytes(index: Int, dst: ByteBuf): ByteBuf {
        buf.getBytes(index, dst)
        return this
    }

    override fun getBytes(index: Int, dst: ByteBuf, length: Int): ByteBuf {
        buf.getBytes(index, dst, length)
        return this
    }

    override fun getBytes(index: Int, dst: ByteBuf, dstIndex: Int, length: Int): ByteBuf {
        buf.getBytes(index, dst, dstIndex, length)
        return this
    }

    override fun getBytes(index: Int, dst: ByteArray): ByteBuf {
        buf.getBytes(index, dst)
        return this
    }

    override fun getBytes(index: Int, dst: ByteArray, dstIndex: Int, length: Int): ByteBuf {
        buf.getBytes(index, dst, dstIndex, length)
        return this
    }

    override fun getBytes(index: Int, dst: ByteBuffer): ByteBuf {
        buf.getBytes(index, dst)
        return this
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: OutputStream, length: Int): ByteBuf {
        buf.getBytes(index, out, length)
        return this
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: GatheringByteChannel, length: Int): Int =
        buf.getBytes(index, out, length)

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: FileChannel, position: Long, length: Int): Int =
        buf.getBytes(index, out, position, length)

    override fun getCharSequence(index: Int, length: Int, charset: Charset): CharSequence =
        buf.getCharSequence(index, length, charset)

    override fun setBoolean(index: Int, value: Boolean): ByteBuf {
        buf.setBoolean(index, value)
        return this
    }

    override fun setByte(index: Int, value: Int): ByteBuf {
        buf.setByte(index, value)
        return this
    }

    override fun setShort(index: Int, value: Int): ByteBuf {
        buf.setShort(index, value)
        return this
    }

    override fun setShortLE(index: Int, value: Int): ByteBuf {
        buf.setShortLE(index, value)
        return this
    }

    override fun setMedium(index: Int, value: Int): ByteBuf {
        buf.setMedium(index, value)
        return this
    }

    override fun setMediumLE(index: Int, value: Int): ByteBuf {
        buf.setMediumLE(index, value)
        return this
    }

    override fun setInt(index: Int, value: Int): ByteBuf {
        buf.setInt(index, value)
        return this
    }

    override fun setIntLE(index: Int, value: Int): ByteBuf {
        buf.setIntLE(index, value)
        return this
    }

    override fun setLong(index: Int, value: Long): ByteBuf {
        buf.setLong(index, value)
        return this
    }

    override fun setLongLE(index: Int, value: Long): ByteBuf {
        buf.setLongLE(index, value)
        return this
    }

    override fun setChar(index: Int, value: Int): ByteBuf {
        buf.setChar(index, value)
        return this
    }

    override fun setFloat(index: Int, value: Float): ByteBuf {
        buf.setFloat(index, value)
        return this
    }

    override fun setDouble(index: Int, value: Double): ByteBuf {
        buf.setDouble(index, value)
        return this
    }

    override fun setBytes(index: Int, src: ByteBuf): ByteBuf {
        buf.setBytes(index, src)
        return this
    }

    override fun setBytes(index: Int, src: ByteBuf, length: Int): ByteBuf {
        buf.setBytes(index, src, length)
        return this
    }

    override fun setBytes(index: Int, src: ByteBuf, srcIndex: Int, length: Int): ByteBuf {
        buf.setBytes(index, src, srcIndex, length)
        return this
    }

    override fun setBytes(index: Int, src: ByteArray): ByteBuf {
        buf.setBytes(index, src)
        return this
    }

    override fun setBytes(index: Int, src: ByteArray, srcIndex: Int, length: Int): ByteBuf {
        buf.setBytes(index, src, srcIndex, length)
        return this
    }

    override fun setBytes(index: Int, src: ByteBuffer): ByteBuf {
        buf.setBytes(index, src)
        return this
    }

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: InputStream, length: Int): Int =
        buf.setBytes(index, `in`, length)

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: ScatteringByteChannel, length: Int): Int =
        buf.setBytes(index, `in`, length)

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: FileChannel, position: Long, length: Int): Int =
        buf.setBytes(index, `in`, position, length)

    override fun setZero(index: Int, length: Int): ByteBuf {
        buf.setZero(index, length)
        return this
    }

    override fun setCharSequence(index: Int, sequence: CharSequence, charset: Charset): Int =
        buf.setCharSequence(index, sequence, charset)

    override fun readBoolean(): Boolean = buf.readBoolean()

    override fun readByte(): Byte = buf.readByte()

    override fun readUnsignedByte(): Short = buf.readUnsignedByte()

    override fun readShort(): Short = buf.readShort()

    override fun readShortLE(): Short = buf.readShortLE()

    override fun readUnsignedShort(): Int = buf.readUnsignedShort()

    override fun readUnsignedShortLE(): Int = buf.readUnsignedShortLE()

    override fun readMedium(): Int = buf.readMedium()

    override fun readMediumLE(): Int = buf.readMediumLE()

    override fun readUnsignedMedium(): Int = buf.readUnsignedMedium()

    override fun readUnsignedMediumLE(): Int = buf.readUnsignedMediumLE()

    override fun readInt(): Int = buf.readInt()

    override fun readIntLE(): Int = buf.readIntLE()

    override fun readUnsignedInt(): Long = buf.readUnsignedInt()

    override fun readUnsignedIntLE(): Long = buf.readUnsignedIntLE()

    override fun readLong(): Long = buf.readLong()

    override fun readLongLE(): Long = buf.readLongLE()

    override fun readChar(): Char = buf.readChar()

    override fun readFloat(): Float = buf.readFloat()

    override fun readDouble(): Double = buf.readDouble()

    override fun readBytes(length: Int): ByteBuf = buf.readBytes(length)

    override fun readSlice(length: Int): ByteBuf = buf.readSlice(length)

    override fun readRetainedSlice(length: Int): ByteBuf = buf.readRetainedSlice(length)

    override fun readBytes(dst: ByteBuf): ByteBuf {
        buf.readBytes(dst)
        return this
    }

    override fun readBytes(dst: ByteBuf, length: Int): ByteBuf {
        buf.readBytes(dst, length)
        return this
    }

    override fun readBytes(dst: ByteBuf, dstIndex: Int, length: Int): ByteBuf {
        buf.readBytes(dst, dstIndex, length)
        return this
    }

    override fun readBytes(dst: ByteArray): ByteBuf {
        buf.readBytes(dst)
        return this
    }

    override fun readBytes(dst: ByteArray, dstIndex: Int, length: Int): ByteBuf {
        buf.readBytes(dst, dstIndex, length)
        return this
    }

    override fun readBytes(dst: ByteBuffer): ByteBuf {
        buf.readBytes(dst)
        return this
    }

    @Throws(IOException::class)
    override fun readBytes(out: OutputStream, length: Int): ByteBuf {
        buf.readBytes(out, length)
        return this
    }

    @Throws(IOException::class)
    override fun readBytes(out: GatheringByteChannel, length: Int): Int =
        buf.readBytes(out, length)

    @Throws(IOException::class)
    override fun readBytes(out: FileChannel, position: Long, length: Int): Int =
        buf.readBytes(out, position, length)

    override fun readCharSequence(length: Int, charset: Charset): CharSequence =
        buf.readCharSequence(length, charset)

    override fun readString(length: Int, charset: Charset): String =
        buf.readString(length, charset)

    override fun skipBytes(length: Int): ByteBuf {
        buf.skipBytes(length)
        return this
    }

    override fun writeBoolean(value: Boolean): ByteBuf {
        buf.writeBoolean(value)
        return this
    }

    override fun writeByte(value: Int): ByteBuf {
        buf.writeByte(value)
        return this
    }

    override fun writeShort(value: Int): ByteBuf {
        buf.writeShort(value)
        return this
    }

    override fun writeShortLE(value: Int): ByteBuf {
        buf.writeShortLE(value)
        return this
    }

    override fun writeMedium(value: Int): ByteBuf {
        buf.writeMedium(value)
        return this
    }

    override fun writeMediumLE(value: Int): ByteBuf {
        buf.writeMediumLE(value)
        return this
    }

    override fun writeInt(value: Int): ByteBuf {
        buf.writeInt(value)
        return this
    }

    override fun writeIntLE(value: Int): ByteBuf {
        buf.writeIntLE(value)
        return this
    }

    override fun writeLong(value: Long): ByteBuf {
        buf.writeLong(value)
        return this
    }

    override fun writeLongLE(value: Long): ByteBuf {
        buf.writeLongLE(value)
        return this
    }

    override fun writeChar(value: Int): ByteBuf {
        buf.writeChar(value)
        return this
    }

    override fun writeFloat(value: Float): ByteBuf {
        buf.writeFloat(value)
        return this
    }

    override fun writeDouble(value: Double): ByteBuf {
        buf.writeDouble(value)
        return this
    }

    override fun writeBytes(src: ByteBuf): ByteBuf {
        buf.writeBytes(src)
        return this
    }

    override fun writeBytes(src: ByteBuf, length: Int): ByteBuf {
        buf.writeBytes(src, length)
        return this
    }

    override fun writeBytes(src: ByteBuf, srcIndex: Int, length: Int): ByteBuf {
        buf.writeBytes(src, srcIndex, length)
        return this
    }

    override fun writeBytes(src: ByteArray): ByteBuf {
        buf.writeBytes(src)
        return this
    }

    override fun writeBytes(src: ByteArray, srcIndex: Int, length: Int): ByteBuf {
        buf.writeBytes(src, srcIndex, length)
        return this
    }

    override fun writeBytes(src: ByteBuffer): ByteBuf {
        buf.writeBytes(src)
        return this
    }

    @Throws(IOException::class)
    override fun writeBytes(`in`: InputStream, length: Int): Int =
        buf.writeBytes(`in`, length)

    @Throws(IOException::class)
    override fun writeBytes(`in`: ScatteringByteChannel, length: Int): Int =
        buf.writeBytes(`in`, length)

    @Throws(IOException::class)
    override fun writeBytes(`in`: FileChannel, position: Long, length: Int): Int =
        buf.writeBytes(`in`, position, length)

    override fun writeZero(length: Int): ByteBuf {
        buf.writeZero(length)
        return this
    }

    override fun writeCharSequence(sequence: CharSequence, charset: Charset): Int =
        buf.writeCharSequence(sequence, charset)

    override fun indexOf(fromIndex: Int, toIndex: Int, value: Byte): Int =
        buf.indexOf(fromIndex, toIndex, value)

    override fun bytesBefore(value: Byte): Int = buf.bytesBefore(value)

    override fun bytesBefore(length: Int, value: Byte): Int = buf.bytesBefore(length, value)

    override fun bytesBefore(index: Int, length: Int, value: Byte): Int =
        buf.bytesBefore(index, length, value)

    override fun forEachByte(processor: ByteProcessor): Int = buf.forEachByte(processor)

    override fun forEachByte(index: Int, length: Int, processor: ByteProcessor): Int =
        buf.forEachByte(index, length, processor)

    override fun forEachByteDesc(processor: ByteProcessor): Int = buf.forEachByteDesc(processor)

    override fun forEachByteDesc(index: Int, length: Int, processor: ByteProcessor): Int =
        buf.forEachByteDesc(index, length, processor)

    override fun copy(): ByteBuf = buf.copy()

    override fun copy(index: Int, length: Int): ByteBuf = buf.copy(index, length)

    override fun slice(): ByteBuf = buf.slice()

    override fun retainedSlice(): ByteBuf = buf.retainedSlice()

    override fun slice(index: Int, length: Int): ByteBuf = buf.slice(index, length)

    override fun retainedSlice(index: Int, length: Int): ByteBuf = buf.retainedSlice(index, length)

    override fun duplicate(): ByteBuf = buf.duplicate()

    override fun retainedDuplicate(): ByteBuf = buf.retainedDuplicate()

    override fun nioBufferCount(): Int = buf.nioBufferCount()

    override fun nioBuffer(): ByteBuffer = buf.nioBuffer()

    override fun nioBuffer(index: Int, length: Int): ByteBuffer = buf.nioBuffer(index, length)

    override fun nioBuffers(): Array<ByteBuffer> = buf.nioBuffers()

    override fun nioBuffers(index: Int, length: Int): Array<ByteBuffer> = buf.nioBuffers(index, length)

    override fun internalNioBuffer(index: Int, length: Int): ByteBuffer = buf.internalNioBuffer(index, length)

    override fun hasArray(): Boolean = buf.hasArray()

    override fun array(): ByteArray = buf.array()

    override fun arrayOffset(): Int = buf.arrayOffset()

    override fun toString(charset: Charset): String = buf.toString(charset)

    override fun toString(index: Int, length: Int, charset: Charset): String =
        buf.toString(index, length, charset)

    override fun hashCode(): Int = buf.hashCode()

    @Suppress("EqualsOrHashCode")
    override fun equals(other: Any?): Boolean = buf == other

    override fun compareTo(other: ByteBuf): Int = buf.compareTo(other)

    override fun toString(): String = "${StringUtil.simpleClassName(this)}(${buf})"

    override fun retain(increment: Int): ByteBuf {
        buf.retain(increment)
        return this
    }

    override fun retain(): ByteBuf {
        buf.retain()
        return this
    }

    override fun touch(): ByteBuf {
        buf.touch()
        return this
    }

    override fun touch(hint: Any?): ByteBuf {
        buf.touch(hint)
        return this
    }

    final override fun isReadable(size: Int): Boolean = buf.isReadable(size)

    final override fun isWritable(size: Int): Boolean = buf.isWritable(size)

    final override fun refCnt(): Int = buf.refCnt()

    override fun release(): Boolean = buf.release()

    override fun release(decrement: Int): Boolean = buf.release(decrement)

    final override fun isAccessible(): Boolean = buf.isAccessible()
}
