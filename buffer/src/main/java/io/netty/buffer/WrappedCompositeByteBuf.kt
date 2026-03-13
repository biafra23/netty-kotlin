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
package io.netty.buffer

import io.netty.util.ByteProcessor
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.channels.GatheringByteChannel
import java.nio.channels.ScatteringByteChannel
import java.nio.charset.Charset

internal open class WrappedCompositeByteBuf(
    private val wrapped: CompositeByteBuf
) : CompositeByteBuf(wrapped.alloc()) {

    override fun release(): Boolean = wrapped.release()

    override fun release(decrement: Int): Boolean = wrapped.release(decrement)

    final override fun maxCapacity(): Int = wrapped.maxCapacity()

    final override fun readerIndex(): Int = wrapped.readerIndex()

    final override fun writerIndex(): Int = wrapped.writerIndex()

    final override fun isReadable(): Boolean = wrapped.isReadable()

    final override fun isReadable(numBytes: Int): Boolean = wrapped.isReadable(numBytes)

    final override fun isWritable(): Boolean = wrapped.isWritable()

    final override fun isWritable(numBytes: Int): Boolean = wrapped.isWritable(numBytes)

    final override fun readableBytes(): Int = wrapped.readableBytes()

    final override fun writableBytes(): Int = wrapped.writableBytes()

    final override fun maxWritableBytes(): Int = wrapped.maxWritableBytes()

    override fun maxFastWritableBytes(): Int = wrapped.maxFastWritableBytes()

    override fun ensureWritable(minWritableBytes: Int, force: Boolean): Int =
        wrapped.ensureWritable(minWritableBytes, force)

    @Suppress("DEPRECATION")
    override fun order(endianness: ByteOrder): ByteBuf = wrapped.order(endianness)

    override fun getBoolean(index: Int): Boolean = wrapped.getBoolean(index)

    override fun getUnsignedByte(index: Int): Short = wrapped.getUnsignedByte(index)

    override fun getShort(index: Int): Short = wrapped.getShort(index)

    override fun getShortLE(index: Int): Short = wrapped.getShortLE(index)

    override fun getUnsignedShort(index: Int): Int = wrapped.getUnsignedShort(index)

    override fun getUnsignedShortLE(index: Int): Int = wrapped.getUnsignedShortLE(index)

    override fun getUnsignedMedium(index: Int): Int = wrapped.getUnsignedMedium(index)

    override fun getUnsignedMediumLE(index: Int): Int = wrapped.getUnsignedMediumLE(index)

    override fun getMedium(index: Int): Int = wrapped.getMedium(index)

    override fun getMediumLE(index: Int): Int = wrapped.getMediumLE(index)

    override fun getInt(index: Int): Int = wrapped.getInt(index)

    override fun getIntLE(index: Int): Int = wrapped.getIntLE(index)

    override fun getUnsignedInt(index: Int): Long = wrapped.getUnsignedInt(index)

    override fun getUnsignedIntLE(index: Int): Long = wrapped.getUnsignedIntLE(index)

    override fun getLong(index: Int): Long = wrapped.getLong(index)

    override fun getLongLE(index: Int): Long = wrapped.getLongLE(index)

    override fun getChar(index: Int): Char = wrapped.getChar(index)

    override fun getFloat(index: Int): Float = wrapped.getFloat(index)

    override fun getDouble(index: Int): Double = wrapped.getDouble(index)

    override fun setShortLE(index: Int, value: Int): ByteBuf = wrapped.setShortLE(index, value)

    override fun setMediumLE(index: Int, value: Int): ByteBuf = wrapped.setMediumLE(index, value)

    override fun setIntLE(index: Int, value: Int): ByteBuf = wrapped.setIntLE(index, value)

    override fun setLongLE(index: Int, value: Long): ByteBuf = wrapped.setLongLE(index, value)

    override fun readByte(): Byte = wrapped.readByte()

    override fun readBoolean(): Boolean = wrapped.readBoolean()

    override fun readUnsignedByte(): Short = wrapped.readUnsignedByte()

    override fun readShort(): Short = wrapped.readShort()

    override fun readShortLE(): Short = wrapped.readShortLE()

    override fun readUnsignedShort(): Int = wrapped.readUnsignedShort()

    override fun readUnsignedShortLE(): Int = wrapped.readUnsignedShortLE()

    override fun readMedium(): Int = wrapped.readMedium()

    override fun readMediumLE(): Int = wrapped.readMediumLE()

    override fun readUnsignedMedium(): Int = wrapped.readUnsignedMedium()

    override fun readUnsignedMediumLE(): Int = wrapped.readUnsignedMediumLE()

    override fun readInt(): Int = wrapped.readInt()

    override fun readIntLE(): Int = wrapped.readIntLE()

    override fun readUnsignedInt(): Long = wrapped.readUnsignedInt()

    override fun readUnsignedIntLE(): Long = wrapped.readUnsignedIntLE()

    override fun readLong(): Long = wrapped.readLong()

    override fun readLongLE(): Long = wrapped.readLongLE()

    override fun readChar(): Char = wrapped.readChar()

    override fun readFloat(): Float = wrapped.readFloat()

    override fun readDouble(): Double = wrapped.readDouble()

    override fun readBytes(length: Int): ByteBuf = wrapped.readBytes(length)

    override fun slice(): ByteBuf = wrapped.slice()

    override fun retainedSlice(): ByteBuf = wrapped.retainedSlice()

    override fun slice(index: Int, length: Int): ByteBuf = wrapped.slice(index, length)

    override fun retainedSlice(index: Int, length: Int): ByteBuf = wrapped.retainedSlice(index, length)

    override fun nioBuffer(): ByteBuffer = wrapped.nioBuffer()

    override fun toString(charset: Charset): String = wrapped.toString(charset)

    override fun toString(index: Int, length: Int, charset: Charset): String =
        wrapped.toString(index, length, charset)

    override fun indexOf(fromIndex: Int, toIndex: Int, value: Byte): Int =
        wrapped.indexOf(fromIndex, toIndex, value)

    override fun bytesBefore(value: Byte): Int = wrapped.bytesBefore(value)

    override fun bytesBefore(length: Int, value: Byte): Int = wrapped.bytesBefore(length, value)

    override fun bytesBefore(index: Int, length: Int, value: Byte): Int =
        wrapped.bytesBefore(index, length, value)

    override fun forEachByte(processor: ByteProcessor): Int = wrapped.forEachByte(processor)

    override fun forEachByte(index: Int, length: Int, processor: ByteProcessor): Int =
        wrapped.forEachByte(index, length, processor)

    override fun forEachByteDesc(processor: ByteProcessor): Int = wrapped.forEachByteDesc(processor)

    override fun forEachByteDesc(index: Int, length: Int, processor: ByteProcessor): Int =
        wrapped.forEachByteDesc(index, length, processor)

    @Throws(Exception::class)
    override fun forEachByteAsc0(start: Int, end: Int, processor: ByteProcessor): Int =
        wrapped.forEachByteAsc0(start, end, processor)

    @Throws(Exception::class)
    override fun forEachByteDesc0(rStart: Int, rEnd: Int, processor: ByteProcessor): Int =
        wrapped.forEachByteDesc0(rStart, rEnd, processor)

    final override fun hashCode(): Int = wrapped.hashCode()

    final override fun equals(other: Any?): Boolean = wrapped == other

    final override fun compareTo(other: ByteBuf): Int = wrapped.compareTo(other)

    final override fun refCnt(): Int = wrapped.refCnt()

    final override fun isAccessible(): Boolean = wrapped.isAccessible()

    override fun duplicate(): ByteBuf = wrapped.duplicate()

    override fun retainedDuplicate(): ByteBuf = wrapped.retainedDuplicate()

    override fun readSlice(length: Int): ByteBuf = wrapped.readSlice(length)

    override fun readRetainedSlice(length: Int): ByteBuf = wrapped.readRetainedSlice(length)

    @Throws(IOException::class)
    override fun readBytes(out: GatheringByteChannel, length: Int): Int =
        wrapped.readBytes(out, length)

    override fun writeShortLE(value: Int): ByteBuf = wrapped.writeShortLE(value)

    override fun writeMediumLE(value: Int): ByteBuf = wrapped.writeMediumLE(value)

    override fun writeIntLE(value: Int): ByteBuf = wrapped.writeIntLE(value)

    override fun writeLongLE(value: Long): ByteBuf = wrapped.writeLongLE(value)

    @Throws(IOException::class)
    override fun writeBytes(`in`: InputStream, length: Int): Int = wrapped.writeBytes(`in`, length)

    @Throws(IOException::class)
    override fun writeBytes(`in`: ScatteringByteChannel, length: Int): Int =
        wrapped.writeBytes(`in`, length)

    override fun copy(): ByteBuf = wrapped.copy()

    override fun addComponent(buffer: ByteBuf): CompositeByteBuf {
        wrapped.addComponent(buffer)
        return this
    }

    override fun addComponents(vararg buffers: ByteBuf): CompositeByteBuf {
        wrapped.addComponents(*buffers)
        return this
    }

    override fun addComponents(buffers: Iterable<ByteBuf>): CompositeByteBuf {
        wrapped.addComponents(buffers)
        return this
    }

    override fun addComponent(cIndex: Int, buffer: ByteBuf): CompositeByteBuf {
        wrapped.addComponent(cIndex, buffer)
        return this
    }

    override fun addComponents(cIndex: Int, vararg buffers: ByteBuf): CompositeByteBuf {
        wrapped.addComponents(cIndex, *buffers)
        return this
    }

    override fun addComponents(cIndex: Int, buffers: Iterable<ByteBuf>): CompositeByteBuf {
        wrapped.addComponents(cIndex, buffers)
        return this
    }

    override fun addComponent(increaseWriterIndex: Boolean, buffer: ByteBuf): CompositeByteBuf {
        wrapped.addComponent(increaseWriterIndex, buffer)
        return this
    }

    override fun addComponents(increaseWriterIndex: Boolean, vararg buffers: ByteBuf): CompositeByteBuf {
        wrapped.addComponents(increaseWriterIndex, *buffers)
        return this
    }

    override fun addComponents(increaseWriterIndex: Boolean, buffers: Iterable<ByteBuf>): CompositeByteBuf {
        wrapped.addComponents(increaseWriterIndex, buffers)
        return this
    }

    override fun addComponent(increaseWriterIndex: Boolean, cIndex: Int, buffer: ByteBuf): CompositeByteBuf {
        wrapped.addComponent(increaseWriterIndex, cIndex, buffer)
        return this
    }

    override fun addFlattenedComponents(increaseWriterIndex: Boolean, buffer: ByteBuf): CompositeByteBuf {
        wrapped.addFlattenedComponents(increaseWriterIndex, buffer)
        return this
    }

    override fun removeComponent(cIndex: Int): CompositeByteBuf {
        wrapped.removeComponent(cIndex)
        return this
    }

    override fun removeComponents(cIndex: Int, numComponents: Int): CompositeByteBuf {
        wrapped.removeComponents(cIndex, numComponents)
        return this
    }

    override fun iterator(): MutableIterator<ByteBuf> = wrapped.iterator()

    override fun decompose(offset: Int, length: Int): List<ByteBuf> = wrapped.decompose(offset, length)

    final override fun isDirect(): Boolean = wrapped.isDirect()

    final override fun hasArray(): Boolean = wrapped.hasArray()

    final override fun array(): ByteArray = wrapped.array()

    final override fun arrayOffset(): Int = wrapped.arrayOffset()

    final override fun hasMemoryAddress(): Boolean = wrapped.hasMemoryAddress()

    final override fun memoryAddress(): Long = wrapped.memoryAddress()

    final override fun capacity(): Int = wrapped.capacity()

    override fun capacity(newCapacity: Int): CompositeByteBuf {
        wrapped.capacity(newCapacity)
        return this
    }

    final override fun alloc(): ByteBufAllocator = wrapped.alloc()

    @Suppress("DEPRECATION")
    final override fun order(): ByteOrder = wrapped.order()

    final override fun numComponents(): Int = wrapped.numComponents()

    final override fun maxNumComponents(): Int = wrapped.maxNumComponents()

    final override fun toComponentIndex(offset: Int): Int = wrapped.toComponentIndex(offset)

    final override fun toByteIndex(cIndex: Int): Int = wrapped.toByteIndex(cIndex)

    override fun getByte(index: Int): Byte = wrapped.getByte(index)

    final override fun _getByte(index: Int): Byte = wrapped.invoke_getByte(index)

    final override fun _getShort(index: Int): Short = wrapped.invoke_getShort(index)

    final override fun _getShortLE(index: Int): Short = wrapped.invoke_getShortLE(index)

    final override fun _getUnsignedMedium(index: Int): Int = wrapped.invoke_getUnsignedMedium(index)

    final override fun _getUnsignedMediumLE(index: Int): Int = wrapped.invoke_getUnsignedMediumLE(index)

    final override fun _getInt(index: Int): Int = wrapped.invoke_getInt(index)

    final override fun _getIntLE(index: Int): Int = wrapped.invoke_getIntLE(index)

    final override fun _getLong(index: Int): Long = wrapped.invoke_getLong(index)

    final override fun _getLongLE(index: Int): Long = wrapped.invoke_getLongLE(index)

    override fun getBytes(index: Int, dst: ByteArray, dstIndex: Int, length: Int): CompositeByteBuf {
        wrapped.getBytes(index, dst, dstIndex, length)
        return this
    }

    override fun getBytes(index: Int, dst: ByteBuffer): CompositeByteBuf {
        wrapped.getBytes(index, dst)
        return this
    }

    override fun getBytes(index: Int, dst: ByteBuf, dstIndex: Int, length: Int): CompositeByteBuf {
        wrapped.getBytes(index, dst, dstIndex, length)
        return this
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: GatheringByteChannel, length: Int): Int =
        wrapped.getBytes(index, out, length)

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: OutputStream, length: Int): CompositeByteBuf {
        wrapped.getBytes(index, out, length)
        return this
    }

    override fun setByte(index: Int, value: Int): CompositeByteBuf {
        wrapped.setByte(index, value)
        return this
    }

    final override fun _setByte(index: Int, value: Int) {
        wrapped.invoke_setByte(index, value)
    }

    override fun setShort(index: Int, value: Int): CompositeByteBuf {
        wrapped.setShort(index, value)
        return this
    }

    final override fun _setShort(index: Int, value: Int) {
        wrapped.invoke_setShort(index, value)
    }

    final override fun _setShortLE(index: Int, value: Int) {
        wrapped.invoke_setShortLE(index, value)
    }

    override fun setMedium(index: Int, value: Int): CompositeByteBuf {
        wrapped.setMedium(index, value)
        return this
    }

    final override fun _setMedium(index: Int, value: Int) {
        wrapped.invoke_setMedium(index, value)
    }

    final override fun _setMediumLE(index: Int, value: Int) {
        wrapped.invoke_setMediumLE(index, value)
    }

    override fun setInt(index: Int, value: Int): CompositeByteBuf {
        wrapped.setInt(index, value)
        return this
    }

    final override fun _setInt(index: Int, value: Int) {
        wrapped.invoke_setInt(index, value)
    }

    final override fun _setIntLE(index: Int, value: Int) {
        wrapped.invoke_setIntLE(index, value)
    }

    override fun setLong(index: Int, value: Long): CompositeByteBuf {
        wrapped.setLong(index, value)
        return this
    }

    final override fun _setLong(index: Int, value: Long) {
        wrapped.invoke_setLong(index, value)
    }

    final override fun _setLongLE(index: Int, value: Long) {
        wrapped.invoke_setLongLE(index, value)
    }

    override fun setBytes(index: Int, src: ByteArray, srcIndex: Int, length: Int): CompositeByteBuf {
        wrapped.setBytes(index, src, srcIndex, length)
        return this
    }

    override fun setBytes(index: Int, src: ByteBuffer): CompositeByteBuf {
        wrapped.setBytes(index, src)
        return this
    }

    override fun setBytes(index: Int, src: ByteBuf, srcIndex: Int, length: Int): CompositeByteBuf {
        wrapped.setBytes(index, src, srcIndex, length)
        return this
    }

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: InputStream, length: Int): Int =
        wrapped.setBytes(index, `in`, length)

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: ScatteringByteChannel, length: Int): Int =
        wrapped.setBytes(index, `in`, length)

    override fun copy(index: Int, length: Int): ByteBuf = wrapped.copy(index, length)

    final override fun component(cIndex: Int): ByteBuf = wrapped.component(cIndex)

    final override fun componentSlice(cIndex: Int): ByteBuf = wrapped.componentSlice(cIndex)

    final override fun componentAtOffset(offset: Int): ByteBuf = wrapped.componentAtOffset(offset)

    final override fun internalComponent(cIndex: Int): ByteBuf = wrapped.internalComponent(cIndex)

    final override fun internalComponentAtOffset(offset: Int): ByteBuf =
        wrapped.internalComponentAtOffset(offset)

    override fun nioBufferCount(): Int = wrapped.nioBufferCount()

    override fun internalNioBuffer(index: Int, length: Int): ByteBuffer =
        wrapped.internalNioBuffer(index, length)

    override fun nioBuffer(index: Int, length: Int): ByteBuffer = wrapped.nioBuffer(index, length)

    override fun nioBuffers(index: Int, length: Int): Array<ByteBuffer> =
        wrapped.nioBuffers(index, length)

    override fun consolidate(): CompositeByteBuf {
        wrapped.consolidate()
        return this
    }

    override fun consolidate(cIndex: Int, numComponents: Int): CompositeByteBuf {
        wrapped.consolidate(cIndex, numComponents)
        return this
    }

    override fun discardReadComponents(): CompositeByteBuf {
        wrapped.discardReadComponents()
        return this
    }

    override fun discardReadBytes(): CompositeByteBuf {
        wrapped.discardReadBytes()
        return this
    }

    final override fun toString(): String = wrapped.toString()

    final override fun readerIndex(readerIndex: Int): CompositeByteBuf {
        wrapped.readerIndex(readerIndex)
        return this
    }

    final override fun writerIndex(writerIndex: Int): CompositeByteBuf {
        wrapped.writerIndex(writerIndex)
        return this
    }

    final override fun setIndex(readerIndex: Int, writerIndex: Int): CompositeByteBuf {
        wrapped.setIndex(readerIndex, writerIndex)
        return this
    }

    final override fun clear(): CompositeByteBuf {
        wrapped.clear()
        return this
    }

    final override fun markReaderIndex(): CompositeByteBuf {
        wrapped.markReaderIndex()
        return this
    }

    final override fun resetReaderIndex(): CompositeByteBuf {
        wrapped.resetReaderIndex()
        return this
    }

    final override fun markWriterIndex(): CompositeByteBuf {
        wrapped.markWriterIndex()
        return this
    }

    final override fun resetWriterIndex(): CompositeByteBuf {
        wrapped.resetWriterIndex()
        return this
    }

    override fun ensureWritable(minWritableBytes: Int): CompositeByteBuf {
        wrapped.ensureWritable(minWritableBytes)
        return this
    }

    override fun getBytes(index: Int, dst: ByteBuf): CompositeByteBuf {
        wrapped.getBytes(index, dst)
        return this
    }

    override fun getBytes(index: Int, dst: ByteBuf, length: Int): CompositeByteBuf {
        wrapped.getBytes(index, dst, length)
        return this
    }

    override fun getBytes(index: Int, dst: ByteArray): CompositeByteBuf {
        wrapped.getBytes(index, dst)
        return this
    }

    override fun setBoolean(index: Int, value: Boolean): CompositeByteBuf {
        wrapped.setBoolean(index, value)
        return this
    }

    override fun setChar(index: Int, value: Int): CompositeByteBuf {
        wrapped.setChar(index, value)
        return this
    }

    override fun setFloat(index: Int, value: Float): CompositeByteBuf {
        wrapped.setFloat(index, value)
        return this
    }

    override fun setDouble(index: Int, value: Double): CompositeByteBuf {
        wrapped.setDouble(index, value)
        return this
    }

    override fun setBytes(index: Int, src: ByteBuf): CompositeByteBuf {
        wrapped.setBytes(index, src)
        return this
    }

    override fun setBytes(index: Int, src: ByteBuf, length: Int): CompositeByteBuf {
        wrapped.setBytes(index, src, length)
        return this
    }

    override fun setBytes(index: Int, src: ByteArray): CompositeByteBuf {
        wrapped.setBytes(index, src)
        return this
    }

    override fun setZero(index: Int, length: Int): CompositeByteBuf {
        wrapped.setZero(index, length)
        return this
    }

    override fun readBytes(dst: ByteBuf): CompositeByteBuf {
        wrapped.readBytes(dst)
        return this
    }

    override fun readBytes(dst: ByteBuf, length: Int): CompositeByteBuf {
        wrapped.readBytes(dst, length)
        return this
    }

    override fun readBytes(dst: ByteBuf, dstIndex: Int, length: Int): CompositeByteBuf {
        wrapped.readBytes(dst, dstIndex, length)
        return this
    }

    override fun readBytes(dst: ByteArray): CompositeByteBuf {
        wrapped.readBytes(dst)
        return this
    }

    override fun readBytes(dst: ByteArray, dstIndex: Int, length: Int): CompositeByteBuf {
        wrapped.readBytes(dst, dstIndex, length)
        return this
    }

    override fun readBytes(dst: ByteBuffer): CompositeByteBuf {
        wrapped.readBytes(dst)
        return this
    }

    @Throws(IOException::class)
    override fun readBytes(out: OutputStream, length: Int): CompositeByteBuf {
        wrapped.readBytes(out, length)
        return this
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: FileChannel, position: Long, length: Int): Int =
        wrapped.getBytes(index, out, position, length)

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: FileChannel, position: Long, length: Int): Int =
        wrapped.setBytes(index, `in`, position, length)

    override fun isReadOnly(): Boolean = wrapped.isReadOnly()

    override fun asReadOnly(): ByteBuf = wrapped.asReadOnly()

    @Suppress("DEPRECATION")
    override fun newSwappedByteBuf(): SwappedByteBuf = wrapped.newSwappedByteBuf()

    override fun getCharSequence(index: Int, length: Int, charset: Charset): CharSequence =
        wrapped.getCharSequence(index, length, charset)

    override fun readCharSequence(length: Int, charset: Charset): CharSequence =
        wrapped.readCharSequence(length, charset)

    override fun readString(length: Int, charset: Charset): String =
        wrapped.readString(length, charset)

    override fun setCharSequence(index: Int, sequence: CharSequence, charset: Charset): Int =
        wrapped.setCharSequence(index, sequence, charset)

    @Throws(IOException::class)
    override fun readBytes(out: FileChannel, position: Long, length: Int): Int =
        wrapped.readBytes(out, position, length)

    @Throws(IOException::class)
    override fun writeBytes(`in`: FileChannel, position: Long, length: Int): Int =
        wrapped.writeBytes(`in`, position, length)

    override fun writeCharSequence(sequence: CharSequence, charset: Charset): Int =
        wrapped.writeCharSequence(sequence, charset)

    override fun skipBytes(length: Int): CompositeByteBuf {
        wrapped.skipBytes(length)
        return this
    }

    override fun writeBoolean(value: Boolean): CompositeByteBuf {
        wrapped.writeBoolean(value)
        return this
    }

    override fun writeByte(value: Int): CompositeByteBuf {
        wrapped.writeByte(value)
        return this
    }

    override fun writeShort(value: Int): CompositeByteBuf {
        wrapped.writeShort(value)
        return this
    }

    override fun writeMedium(value: Int): CompositeByteBuf {
        wrapped.writeMedium(value)
        return this
    }

    override fun writeInt(value: Int): CompositeByteBuf {
        wrapped.writeInt(value)
        return this
    }

    override fun writeLong(value: Long): CompositeByteBuf {
        wrapped.writeLong(value)
        return this
    }

    override fun writeChar(value: Int): CompositeByteBuf {
        wrapped.writeChar(value)
        return this
    }

    override fun writeFloat(value: Float): CompositeByteBuf {
        wrapped.writeFloat(value)
        return this
    }

    override fun writeDouble(value: Double): CompositeByteBuf {
        wrapped.writeDouble(value)
        return this
    }

    override fun writeBytes(src: ByteBuf): CompositeByteBuf {
        wrapped.writeBytes(src)
        return this
    }

    override fun writeBytes(src: ByteBuf, length: Int): CompositeByteBuf {
        wrapped.writeBytes(src, length)
        return this
    }

    override fun writeBytes(src: ByteBuf, srcIndex: Int, length: Int): CompositeByteBuf {
        wrapped.writeBytes(src, srcIndex, length)
        return this
    }

    override fun writeBytes(src: ByteArray): CompositeByteBuf {
        wrapped.writeBytes(src)
        return this
    }

    override fun writeBytes(src: ByteArray, srcIndex: Int, length: Int): CompositeByteBuf {
        wrapped.writeBytes(src, srcIndex, length)
        return this
    }

    override fun writeBytes(src: ByteBuffer): CompositeByteBuf {
        wrapped.writeBytes(src)
        return this
    }

    override fun writeZero(length: Int): CompositeByteBuf {
        wrapped.writeZero(length)
        return this
    }

    override fun retain(increment: Int): CompositeByteBuf {
        wrapped.retain(increment)
        return this
    }

    override fun retain(): CompositeByteBuf {
        wrapped.retain()
        return this
    }

    override fun touch(): CompositeByteBuf {
        wrapped.touch()
        return this
    }

    override fun touch(hint: Any?): CompositeByteBuf {
        wrapped.touch(hint)
        return this
    }

    override fun nioBuffers(): Array<ByteBuffer> = wrapped.nioBuffers()

    override fun discardSomeReadBytes(): CompositeByteBuf {
        wrapped.discardSomeReadBytes()
        return this
    }

    final override fun deallocate() {
        wrapped.invokeDeallocate()
    }

    final override fun unwrap(): ByteBuf = wrapped
}
