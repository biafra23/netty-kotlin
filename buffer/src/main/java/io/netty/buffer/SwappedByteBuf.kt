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
import io.netty.util.internal.ObjectUtil

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
 * Wrapper which swap the [ByteOrder] of a [ByteBuf].
 *
 * @deprecated use the Little Endian accessors, e.g. `getShortLE`, `getIntLE`
 * instead.
 */
@Deprecated("use the Little Endian accessors, e.g. getShortLE, getIntLE instead.")
open class SwappedByteBuf(private val buf: ByteBuf) : ByteBuf() {

    private val order: ByteOrder

    init {
        ObjectUtil.checkNotNull(buf, "buf")
        order = if (buf.order() == ByteOrder.BIG_ENDIAN) {
            ByteOrder.LITTLE_ENDIAN
        } else {
            ByteOrder.BIG_ENDIAN
        }
    }

    override fun order(): ByteOrder {
        return order
    }

    override fun order(endianness: ByteOrder): ByteBuf {
        if (ObjectUtil.checkNotNull(endianness, "endianness") == order) {
            return this
        }
        return buf
    }

    override fun unwrap(): ByteBuf {
        return buf
    }

    override fun alloc(): ByteBufAllocator {
        return buf.alloc()
    }

    override fun capacity(): Int {
        return buf.capacity()
    }

    override fun capacity(newCapacity: Int): ByteBuf {
        buf.capacity(newCapacity)
        return this
    }

    override fun maxCapacity(): Int {
        return buf.maxCapacity()
    }

    override fun isReadOnly(): Boolean {
        return buf.isReadOnly()
    }

    override fun asReadOnly(): ByteBuf {
        return Unpooled.unmodifiableBuffer(this)
    }

    override fun isDirect(): Boolean {
        return buf.isDirect()
    }

    override fun readerIndex(): Int {
        return buf.readerIndex()
    }

    override fun readerIndex(readerIndex: Int): ByteBuf {
        buf.readerIndex(readerIndex)
        return this
    }

    override fun writerIndex(): Int {
        return buf.writerIndex()
    }

    override fun writerIndex(writerIndex: Int): ByteBuf {
        buf.writerIndex(writerIndex)
        return this
    }

    override fun setIndex(readerIndex: Int, writerIndex: Int): ByteBuf {
        buf.setIndex(readerIndex, writerIndex)
        return this
    }

    override fun readableBytes(): Int {
        return buf.readableBytes()
    }

    override fun writableBytes(): Int {
        return buf.writableBytes()
    }

    override fun maxWritableBytes(): Int {
        return buf.maxWritableBytes()
    }

    override fun maxFastWritableBytes(): Int {
        return buf.maxFastWritableBytes()
    }

    override fun isReadable(): Boolean {
        return buf.isReadable()
    }

    override fun isReadable(size: Int): Boolean {
        return buf.isReadable(size)
    }

    override fun isWritable(): Boolean {
        return buf.isWritable()
    }

    override fun isWritable(size: Int): Boolean {
        return buf.isWritable(size)
    }

    override fun clear(): ByteBuf {
        buf.clear()
        return this
    }

    override fun markReaderIndex(): ByteBuf {
        buf.markReaderIndex()
        return this
    }

    override fun resetReaderIndex(): ByteBuf {
        buf.resetReaderIndex()
        return this
    }

    override fun markWriterIndex(): ByteBuf {
        buf.markWriterIndex()
        return this
    }

    override fun resetWriterIndex(): ByteBuf {
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

    override fun ensureWritable(writableBytes: Int): ByteBuf {
        buf.ensureWritable(writableBytes)
        return this
    }

    override fun ensureWritable(minWritableBytes: Int, force: Boolean): Int {
        return buf.ensureWritable(minWritableBytes, force)
    }

    override fun getBoolean(index: Int): Boolean {
        return buf.getBoolean(index)
    }

    override fun getByte(index: Int): Byte {
        return buf.getByte(index)
    }

    override fun getUnsignedByte(index: Int): Short {
        return buf.getUnsignedByte(index)
    }

    open override fun getShort(index: Int): Short {
        return ByteBufUtil.swapShort(buf.getShort(index))
    }

    override fun getShortLE(index: Int): Short {
        return buf.getShortLE(index)
    }

    open override fun getUnsignedShort(index: Int): Int {
        return getShort(index).toInt() and 0xFFFF
    }

    override fun getUnsignedShortLE(index: Int): Int {
        return getShortLE(index).toInt() and 0xFFFF
    }

    override fun getMedium(index: Int): Int {
        return ByteBufUtil.swapMedium(buf.getMedium(index))
    }

    override fun getMediumLE(index: Int): Int {
        return buf.getMediumLE(index)
    }

    override fun getUnsignedMedium(index: Int): Int {
        return getMedium(index) and 0xFFFFFF
    }

    override fun getUnsignedMediumLE(index: Int): Int {
        return getMediumLE(index) and 0xFFFFFF
    }

    open override fun getInt(index: Int): Int {
        return ByteBufUtil.swapInt(buf.getInt(index))
    }

    override fun getIntLE(index: Int): Int {
        return buf.getIntLE(index)
    }

    open override fun getUnsignedInt(index: Int): Long {
        return getInt(index).toLong() and 0xFFFFFFFFL
    }

    override fun getUnsignedIntLE(index: Int): Long {
        return getIntLE(index).toLong() and 0xFFFFFFFFL
    }

    open override fun getLong(index: Int): Long {
        return ByteBufUtil.swapLong(buf.getLong(index))
    }

    override fun getLongLE(index: Int): Long {
        return buf.getLongLE(index)
    }

    open override fun getChar(index: Int): Char {
        return getShort(index).toInt().toChar()
    }

    open override fun getFloat(index: Int): Float {
        return java.lang.Float.intBitsToFloat(getInt(index))
    }

    open override fun getDouble(index: Int): Double {
        return java.lang.Double.longBitsToDouble(getLong(index))
    }

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
    override fun getBytes(index: Int, out: GatheringByteChannel, length: Int): Int {
        return buf.getBytes(index, out, length)
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: FileChannel, position: Long, length: Int): Int {
        return buf.getBytes(index, out, position, length)
    }

    override fun getCharSequence(index: Int, length: Int, charset: Charset): CharSequence {
        return buf.getCharSequence(index, length, charset)
    }

    override fun setBoolean(index: Int, value: Boolean): ByteBuf {
        buf.setBoolean(index, value)
        return this
    }

    override fun setByte(index: Int, value: Int): ByteBuf {
        buf.setByte(index, value)
        return this
    }

    open override fun setShort(index: Int, value: Int): ByteBuf {
        buf.setShort(index, ByteBufUtil.swapShort(value.toShort()).toInt())
        return this
    }

    override fun setShortLE(index: Int, value: Int): ByteBuf {
        buf.setShortLE(index, value.toShort().toInt())
        return this
    }

    override fun setMedium(index: Int, value: Int): ByteBuf {
        buf.setMedium(index, ByteBufUtil.swapMedium(value))
        return this
    }

    override fun setMediumLE(index: Int, value: Int): ByteBuf {
        buf.setMediumLE(index, value)
        return this
    }

    open override fun setInt(index: Int, value: Int): ByteBuf {
        buf.setInt(index, ByteBufUtil.swapInt(value))
        return this
    }

    override fun setIntLE(index: Int, value: Int): ByteBuf {
        buf.setIntLE(index, value)
        return this
    }

    open override fun setLong(index: Int, value: Long): ByteBuf {
        buf.setLong(index, ByteBufUtil.swapLong(value))
        return this
    }

    override fun setLongLE(index: Int, value: Long): ByteBuf {
        buf.setLongLE(index, value)
        return this
    }

    open override fun setChar(index: Int, value: Int): ByteBuf {
        setShort(index, value)
        return this
    }

    open override fun setFloat(index: Int, value: Float): ByteBuf {
        setInt(index, java.lang.Float.floatToRawIntBits(value))
        return this
    }

    open override fun setDouble(index: Int, value: Double): ByteBuf {
        setLong(index, java.lang.Double.doubleToRawLongBits(value))
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
    override fun setBytes(index: Int, `in`: InputStream, length: Int): Int {
        return buf.setBytes(index, `in`, length)
    }

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: ScatteringByteChannel, length: Int): Int {
        return buf.setBytes(index, `in`, length)
    }

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: FileChannel, position: Long, length: Int): Int {
        return buf.setBytes(index, `in`, position, length)
    }

    override fun setZero(index: Int, length: Int): ByteBuf {
        buf.setZero(index, length)
        return this
    }

    override fun setCharSequence(index: Int, sequence: CharSequence, charset: Charset): Int {
        return buf.setCharSequence(index, sequence, charset)
    }

    override fun readBoolean(): Boolean {
        return buf.readBoolean()
    }

    override fun readByte(): Byte {
        return buf.readByte()
    }

    override fun readUnsignedByte(): Short {
        return buf.readUnsignedByte()
    }

    override fun readShort(): Short {
        return ByteBufUtil.swapShort(buf.readShort())
    }

    override fun readShortLE(): Short {
        return buf.readShortLE()
    }

    override fun readUnsignedShort(): Int {
        return readShort().toInt() and 0xFFFF
    }

    override fun readUnsignedShortLE(): Int {
        return readShortLE().toInt() and 0xFFFF
    }

    override fun readMedium(): Int {
        return ByteBufUtil.swapMedium(buf.readMedium())
    }

    override fun readMediumLE(): Int {
        return buf.readMediumLE()
    }

    override fun readUnsignedMedium(): Int {
        return readMedium() and 0xFFFFFF
    }

    override fun readUnsignedMediumLE(): Int {
        return readMediumLE() and 0xFFFFFF
    }

    override fun readInt(): Int {
        return ByteBufUtil.swapInt(buf.readInt())
    }

    override fun readIntLE(): Int {
        return buf.readIntLE()
    }

    override fun readUnsignedInt(): Long {
        return readInt().toLong() and 0xFFFFFFFFL
    }

    override fun readUnsignedIntLE(): Long {
        return readIntLE().toLong() and 0xFFFFFFFFL
    }

    override fun readLong(): Long {
        return ByteBufUtil.swapLong(buf.readLong())
    }

    override fun readLongLE(): Long {
        return buf.readLongLE()
    }

    override fun readChar(): Char {
        return readShort().toInt().toChar()
    }

    override fun readFloat(): Float {
        return java.lang.Float.intBitsToFloat(readInt())
    }

    override fun readDouble(): Double {
        return java.lang.Double.longBitsToDouble(readLong())
    }

    override fun readBytes(length: Int): ByteBuf {
        return buf.readBytes(length).order(order())
    }

    override fun readSlice(length: Int): ByteBuf {
        return buf.readSlice(length).order(order)
    }

    override fun readRetainedSlice(length: Int): ByteBuf {
        return buf.readRetainedSlice(length).order(order)
    }

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
    override fun readBytes(out: GatheringByteChannel, length: Int): Int {
        return buf.readBytes(out, length)
    }

    @Throws(IOException::class)
    override fun readBytes(out: FileChannel, position: Long, length: Int): Int {
        return buf.readBytes(out, position, length)
    }

    override fun readCharSequence(length: Int, charset: Charset): CharSequence {
        return buf.readCharSequence(length, charset)
    }

    override fun readString(length: Int, charset: Charset): String {
        return buf.readString(length, charset)
    }

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

    open override fun writeShort(value: Int): ByteBuf {
        buf.writeShort(ByteBufUtil.swapShort(value.toShort()).toInt())
        return this
    }

    override fun writeShortLE(value: Int): ByteBuf {
        buf.writeShortLE(value.toShort().toInt())
        return this
    }

    override fun writeMedium(value: Int): ByteBuf {
        buf.writeMedium(ByteBufUtil.swapMedium(value))
        return this
    }

    override fun writeMediumLE(value: Int): ByteBuf {
        buf.writeMediumLE(value)
        return this
    }

    open override fun writeInt(value: Int): ByteBuf {
        buf.writeInt(ByteBufUtil.swapInt(value))
        return this
    }

    override fun writeIntLE(value: Int): ByteBuf {
        buf.writeIntLE(value)
        return this
    }

    open override fun writeLong(value: Long): ByteBuf {
        buf.writeLong(ByteBufUtil.swapLong(value))
        return this
    }

    override fun writeLongLE(value: Long): ByteBuf {
        buf.writeLongLE(value)
        return this
    }

    open override fun writeChar(value: Int): ByteBuf {
        writeShort(value)
        return this
    }

    open override fun writeFloat(value: Float): ByteBuf {
        writeInt(java.lang.Float.floatToRawIntBits(value))
        return this
    }

    open override fun writeDouble(value: Double): ByteBuf {
        writeLong(java.lang.Double.doubleToRawLongBits(value))
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
    override fun writeBytes(`in`: InputStream, length: Int): Int {
        return buf.writeBytes(`in`, length)
    }

    @Throws(IOException::class)
    override fun writeBytes(`in`: ScatteringByteChannel, length: Int): Int {
        return buf.writeBytes(`in`, length)
    }

    @Throws(IOException::class)
    override fun writeBytes(`in`: FileChannel, position: Long, length: Int): Int {
        return buf.writeBytes(`in`, position, length)
    }

    override fun writeZero(length: Int): ByteBuf {
        buf.writeZero(length)
        return this
    }

    override fun writeCharSequence(sequence: CharSequence, charset: Charset): Int {
        return buf.writeCharSequence(sequence, charset)
    }

    override fun indexOf(fromIndex: Int, toIndex: Int, value: Byte): Int {
        return buf.indexOf(fromIndex, toIndex, value)
    }

    override fun bytesBefore(value: Byte): Int {
        return buf.bytesBefore(value)
    }

    override fun bytesBefore(length: Int, value: Byte): Int {
        return buf.bytesBefore(length, value)
    }

    override fun bytesBefore(index: Int, length: Int, value: Byte): Int {
        return buf.bytesBefore(index, length, value)
    }

    override fun forEachByte(processor: ByteProcessor): Int {
        return buf.forEachByte(processor)
    }

    override fun forEachByte(index: Int, length: Int, processor: ByteProcessor): Int {
        return buf.forEachByte(index, length, processor)
    }

    override fun forEachByteDesc(processor: ByteProcessor): Int {
        return buf.forEachByteDesc(processor)
    }

    override fun forEachByteDesc(index: Int, length: Int, processor: ByteProcessor): Int {
        return buf.forEachByteDesc(index, length, processor)
    }

    override fun copy(): ByteBuf {
        return buf.copy().order(order)
    }

    override fun copy(index: Int, length: Int): ByteBuf {
        return buf.copy(index, length).order(order)
    }

    override fun slice(): ByteBuf {
        return buf.slice().order(order)
    }

    override fun retainedSlice(): ByteBuf {
        return buf.retainedSlice().order(order)
    }

    override fun slice(index: Int, length: Int): ByteBuf {
        return buf.slice(index, length).order(order)
    }

    override fun retainedSlice(index: Int, length: Int): ByteBuf {
        return buf.retainedSlice(index, length).order(order)
    }

    override fun duplicate(): ByteBuf {
        return buf.duplicate().order(order)
    }

    override fun retainedDuplicate(): ByteBuf {
        return buf.retainedDuplicate().order(order)
    }

    override fun nioBufferCount(): Int {
        return buf.nioBufferCount()
    }

    override fun nioBuffer(): ByteBuffer {
        return buf.nioBuffer().order(order)
    }

    override fun nioBuffer(index: Int, length: Int): ByteBuffer {
        return buf.nioBuffer(index, length).order(order)
    }

    override fun internalNioBuffer(index: Int, length: Int): ByteBuffer {
        return nioBuffer(index, length)
    }

    override fun nioBuffers(): Array<ByteBuffer> {
        val nioBuffers = buf.nioBuffers()
        for (i in nioBuffers.indices) {
            nioBuffers[i] = nioBuffers[i].order(order)
        }
        return nioBuffers
    }

    override fun nioBuffers(index: Int, length: Int): Array<ByteBuffer> {
        val nioBuffers = buf.nioBuffers(index, length)
        for (i in nioBuffers.indices) {
            nioBuffers[i] = nioBuffers[i].order(order)
        }
        return nioBuffers
    }

    override fun hasArray(): Boolean {
        return buf.hasArray()
    }

    override fun array(): ByteArray {
        return buf.array()
    }

    override fun arrayOffset(): Int {
        return buf.arrayOffset()
    }

    override fun hasMemoryAddress(): Boolean {
        return buf.hasMemoryAddress()
    }

    override fun isContiguous(): Boolean {
        return buf.isContiguous()
    }

    override fun memoryAddress(): Long {
        return buf.memoryAddress()
    }

    override fun toString(charset: Charset): String {
        return buf.toString(charset)
    }

    override fun toString(index: Int, length: Int, charset: Charset): String {
        return buf.toString(index, length, charset)
    }

    override fun refCnt(): Int {
        return buf.refCnt()
    }

    final override fun isAccessible(): Boolean {
        return buf.isAccessible()
    }

    override fun retain(): ByteBuf {
        buf.retain()
        return this
    }

    override fun retain(increment: Int): ByteBuf {
        buf.retain(increment)
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

    override fun release(): Boolean {
        return buf.release()
    }

    override fun release(decrement: Int): Boolean {
        return buf.release(decrement)
    }

    override fun hashCode(): Int {
        return buf.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other is ByteBuf) {
            return ByteBufUtil.equals(this, other)
        }
        return false
    }

    override fun compareTo(other: ByteBuf): Int {
        return ByteBufUtil.compare(this, other)
    }

    override fun toString(): String {
        return "Swapped($buf)"
    }
}
