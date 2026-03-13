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
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ReadOnlyBufferException
import java.nio.channels.FileChannel
import java.nio.channels.GatheringByteChannel
import java.nio.channels.ScatteringByteChannel

/**
 * A derived buffer which forbids any write requests to its parent.  It is
 * recommended to use [Unpooled.unmodifiableBuffer] instead of calling the
 * constructor explicitly.
 *
 * @suppress
 */
@Deprecated("Do not use.")
open class ReadOnlyByteBuf(buffer: ByteBuf) : AbstractDerivedByteBuf(buffer.maxCapacity()) {

    private val buffer: ByteBuf

    init {
        this.buffer = if (buffer is ReadOnlyByteBuf || buffer is DuplicatedByteBuf) {
            buffer.unwrap()!!
        } else {
            buffer
        }
        setIndex(buffer.readerIndex(), buffer.writerIndex())
    }

    override fun isReadOnly(): Boolean = true

    override fun isWritable(): Boolean = false

    override fun isWritable(numBytes: Int): Boolean = false

    override fun ensureWritable(minWritableBytes: Int, force: Boolean): Int = 1

    override fun ensureWritable(minWritableBytes: Int): ByteBuf {
        throw ReadOnlyBufferException()
    }

    override fun unwrap(): ByteBuf = buffer

    override fun alloc(): ByteBufAllocator = unwrap().alloc()

    @Deprecated("")
    override fun order(): ByteOrder = unwrap().order()

    override fun isDirect(): Boolean = unwrap().isDirect()

    override fun hasArray(): Boolean = false

    override fun array(): ByteArray {
        throw ReadOnlyBufferException()
    }

    override fun arrayOffset(): Int {
        throw ReadOnlyBufferException()
    }

    override fun hasMemoryAddress(): Boolean = unwrap().hasMemoryAddress()

    override fun memoryAddress(): Long = unwrap().memoryAddress()

    override fun discardReadBytes(): ByteBuf {
        throw ReadOnlyBufferException()
    }

    override fun setBytes(index: Int, src: ByteBuf, srcIndex: Int, length: Int): ByteBuf {
        throw ReadOnlyBufferException()
    }

    override fun setBytes(index: Int, src: ByteArray, srcIndex: Int, length: Int): ByteBuf {
        throw ReadOnlyBufferException()
    }

    override fun setBytes(index: Int, src: ByteBuffer): ByteBuf {
        throw ReadOnlyBufferException()
    }

    override fun setByte(index: Int, value: Int): ByteBuf {
        throw ReadOnlyBufferException()
    }

    override fun _setByte(index: Int, value: Int) {
        throw ReadOnlyBufferException()
    }

    override fun setShort(index: Int, value: Int): ByteBuf {
        throw ReadOnlyBufferException()
    }

    override fun _setShort(index: Int, value: Int) {
        throw ReadOnlyBufferException()
    }

    override fun setShortLE(index: Int, value: Int): ByteBuf {
        throw ReadOnlyBufferException()
    }

    override fun _setShortLE(index: Int, value: Int) {
        throw ReadOnlyBufferException()
    }

    override fun setMedium(index: Int, value: Int): ByteBuf {
        throw ReadOnlyBufferException()
    }

    override fun _setMedium(index: Int, value: Int) {
        throw ReadOnlyBufferException()
    }

    override fun setMediumLE(index: Int, value: Int): ByteBuf {
        throw ReadOnlyBufferException()
    }

    override fun _setMediumLE(index: Int, value: Int) {
        throw ReadOnlyBufferException()
    }

    override fun setInt(index: Int, value: Int): ByteBuf {
        throw ReadOnlyBufferException()
    }

    override fun _setInt(index: Int, value: Int) {
        throw ReadOnlyBufferException()
    }

    override fun setIntLE(index: Int, value: Int): ByteBuf {
        throw ReadOnlyBufferException()
    }

    override fun _setIntLE(index: Int, value: Int) {
        throw ReadOnlyBufferException()
    }

    override fun setLong(index: Int, value: Long): ByteBuf {
        throw ReadOnlyBufferException()
    }

    override fun _setLong(index: Int, value: Long) {
        throw ReadOnlyBufferException()
    }

    override fun setLongLE(index: Int, value: Long): ByteBuf {
        throw ReadOnlyBufferException()
    }

    override fun _setLongLE(index: Int, value: Long) {
        throw ReadOnlyBufferException()
    }

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: InputStream, length: Int): Int {
        throw ReadOnlyBufferException()
    }

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: ScatteringByteChannel, length: Int): Int {
        throw ReadOnlyBufferException()
    }

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: FileChannel, position: Long, length: Int): Int {
        throw ReadOnlyBufferException()
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: GatheringByteChannel, length: Int): Int {
        return unwrap().getBytes(index, out, length)
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: FileChannel, position: Long, length: Int): Int {
        return unwrap().getBytes(index, out, position, length)
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: OutputStream, length: Int): ByteBuf {
        unwrap().getBytes(index, out, length)
        return this
    }

    override fun getBytes(index: Int, dst: ByteArray, dstIndex: Int, length: Int): ByteBuf {
        unwrap().getBytes(index, dst, dstIndex, length)
        return this
    }

    override fun getBytes(index: Int, dst: ByteBuf, dstIndex: Int, length: Int): ByteBuf {
        unwrap().getBytes(index, dst, dstIndex, length)
        return this
    }

    override fun getBytes(index: Int, dst: ByteBuffer): ByteBuf {
        unwrap().getBytes(index, dst)
        return this
    }

    override fun duplicate(): ByteBuf {
        return ReadOnlyByteBuf(this)
    }

    override fun copy(index: Int, length: Int): ByteBuf {
        return unwrap().copy(index, length)
    }

    override fun slice(index: Int, length: Int): ByteBuf {
        return ReadOnlyByteBuf(unwrap().slice(index, length))
    }

    override fun getByte(index: Int): Byte {
        return unwrap().getByte(index)
    }

    override fun _getByte(index: Int): Byte {
        return unwrap().getByte(index)
    }

    override fun getShort(index: Int): Short {
        return unwrap().getShort(index)
    }

    override fun _getShort(index: Int): Short {
        return unwrap().getShort(index)
    }

    override fun getShortLE(index: Int): Short {
        return unwrap().getShortLE(index)
    }

    override fun _getShortLE(index: Int): Short {
        return unwrap().getShortLE(index)
    }

    override fun getUnsignedMedium(index: Int): Int {
        return unwrap().getUnsignedMedium(index)
    }

    override fun _getUnsignedMedium(index: Int): Int {
        return unwrap().getUnsignedMedium(index)
    }

    override fun getUnsignedMediumLE(index: Int): Int {
        return unwrap().getUnsignedMediumLE(index)
    }

    override fun _getUnsignedMediumLE(index: Int): Int {
        return unwrap().getUnsignedMediumLE(index)
    }

    override fun getInt(index: Int): Int {
        return unwrap().getInt(index)
    }

    override fun _getInt(index: Int): Int {
        return unwrap().getInt(index)
    }

    override fun getIntLE(index: Int): Int {
        return unwrap().getIntLE(index)
    }

    override fun _getIntLE(index: Int): Int {
        return unwrap().getIntLE(index)
    }

    override fun getLong(index: Int): Long {
        return unwrap().getLong(index)
    }

    override fun _getLong(index: Int): Long {
        return unwrap().getLong(index)
    }

    override fun getLongLE(index: Int): Long {
        return unwrap().getLongLE(index)
    }

    override fun _getLongLE(index: Int): Long {
        return unwrap().getLongLE(index)
    }

    override fun nioBufferCount(): Int {
        return unwrap().nioBufferCount()
    }

    override fun nioBuffer(index: Int, length: Int): ByteBuffer {
        return unwrap().nioBuffer(index, length).asReadOnlyBuffer()
    }

    override fun nioBuffers(index: Int, length: Int): Array<ByteBuffer> {
        val buffers = unwrap().nioBuffers(index, length)
        for (i in buffers.indices) {
            val buf = buffers[i]
            if (!buf.isReadOnly) {
                buffers[i] = buf.asReadOnlyBuffer()
            }
        }
        return buffers
    }

    override fun forEachByte(index: Int, length: Int, processor: ByteProcessor): Int {
        return unwrap().forEachByte(index, length, processor)
    }

    override fun forEachByteDesc(index: Int, length: Int, processor: ByteProcessor): Int {
        return unwrap().forEachByteDesc(index, length, processor)
    }

    override fun capacity(): Int {
        return unwrap().capacity()
    }

    override fun capacity(newCapacity: Int): ByteBuf {
        throw ReadOnlyBufferException()
    }

    override fun asReadOnly(): ByteBuf {
        return this
    }
}
