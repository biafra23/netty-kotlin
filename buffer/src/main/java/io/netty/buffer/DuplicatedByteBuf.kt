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
import java.nio.channels.FileChannel
import java.nio.channels.GatheringByteChannel
import java.nio.channels.ScatteringByteChannel

/**
 * A derived buffer which simply forwards all data access requests to its
 * parent.  It is recommended to use [ByteBuf.duplicate] instead
 * of calling the constructor explicitly.
 *
 * @deprecated Do not use.
 */
@Suppress("DEPRECATION")
@Deprecated("Do not use.")
open class DuplicatedByteBuf : AbstractDerivedByteBuf {

    private val buffer: ByteBuf

    constructor(buffer: ByteBuf) : this(buffer, buffer.readerIndex(), buffer.writerIndex())

    internal constructor(buffer: ByteBuf, readerIndex: Int, writerIndex: Int) : super(buffer.maxCapacity()) {
        this.buffer = when (buffer) {
            is DuplicatedByteBuf -> buffer.buffer
            is AbstractPooledDerivedByteBuf -> buffer.unwrap()
            else -> buffer
        }

        setIndex(readerIndex, writerIndex)
        markReaderIndex()
        markWriterIndex()
    }

    override fun unwrap(): ByteBuf = buffer

    override fun alloc(): ByteBufAllocator = unwrap().alloc()

    @Deprecated("Use Little Endian accessors instead.")
    override fun order(): ByteOrder = unwrap().order()

    override fun isDirect(): Boolean = unwrap().isDirect()

    override fun capacity(): Int = unwrap().capacity()

    override fun capacity(newCapacity: Int): ByteBuf {
        unwrap().capacity(newCapacity)
        return this
    }

    override fun hasArray(): Boolean = unwrap().hasArray()

    override fun array(): ByteArray = unwrap().array()

    override fun arrayOffset(): Int = unwrap().arrayOffset()

    override fun hasMemoryAddress(): Boolean = unwrap().hasMemoryAddress()

    override fun memoryAddress(): Long = unwrap().memoryAddress()

    override fun getByte(index: Int): Byte = unwrap().getByte(index)

    override fun _getByte(index: Int): Byte = unwrap().getByte(index)

    override fun getShort(index: Int): Short = unwrap().getShort(index)

    override fun _getShort(index: Int): Short = unwrap().getShort(index)

    override fun getShortLE(index: Int): Short = unwrap().getShortLE(index)

    override fun _getShortLE(index: Int): Short = unwrap().getShortLE(index)

    override fun getUnsignedMedium(index: Int): Int = unwrap().getUnsignedMedium(index)

    override fun _getUnsignedMedium(index: Int): Int = unwrap().getUnsignedMedium(index)

    override fun getUnsignedMediumLE(index: Int): Int = unwrap().getUnsignedMediumLE(index)

    override fun _getUnsignedMediumLE(index: Int): Int = unwrap().getUnsignedMediumLE(index)

    override fun getInt(index: Int): Int = unwrap().getInt(index)

    override fun _getInt(index: Int): Int = unwrap().getInt(index)

    override fun getIntLE(index: Int): Int = unwrap().getIntLE(index)

    override fun _getIntLE(index: Int): Int = unwrap().getIntLE(index)

    override fun getLong(index: Int): Long = unwrap().getLong(index)

    override fun _getLong(index: Int): Long = unwrap().getLong(index)

    override fun getLongLE(index: Int): Long = unwrap().getLongLE(index)

    override fun _getLongLE(index: Int): Long = unwrap().getLongLE(index)

    override fun copy(index: Int, length: Int): ByteBuf = unwrap().copy(index, length)

    override fun slice(index: Int, length: Int): ByteBuf = unwrap().slice(index, length)

    override fun getBytes(index: Int, dst: ByteBuf, dstIndex: Int, length: Int): ByteBuf {
        unwrap().getBytes(index, dst, dstIndex, length)
        return this
    }

    override fun getBytes(index: Int, dst: ByteArray, dstIndex: Int, length: Int): ByteBuf {
        unwrap().getBytes(index, dst, dstIndex, length)
        return this
    }

    override fun getBytes(index: Int, dst: ByteBuffer): ByteBuf {
        unwrap().getBytes(index, dst)
        return this
    }

    override fun setByte(index: Int, value: Int): ByteBuf {
        unwrap().setByte(index, value)
        return this
    }

    override fun _setByte(index: Int, value: Int) {
        unwrap().setByte(index, value)
    }

    override fun setShort(index: Int, value: Int): ByteBuf {
        unwrap().setShort(index, value)
        return this
    }

    override fun _setShort(index: Int, value: Int) {
        unwrap().setShort(index, value)
    }

    override fun setShortLE(index: Int, value: Int): ByteBuf {
        unwrap().setShortLE(index, value)
        return this
    }

    override fun _setShortLE(index: Int, value: Int) {
        unwrap().setShortLE(index, value)
    }

    override fun setMedium(index: Int, value: Int): ByteBuf {
        unwrap().setMedium(index, value)
        return this
    }

    override fun _setMedium(index: Int, value: Int) {
        unwrap().setMedium(index, value)
    }

    override fun setMediumLE(index: Int, value: Int): ByteBuf {
        unwrap().setMediumLE(index, value)
        return this
    }

    override fun _setMediumLE(index: Int, value: Int) {
        unwrap().setMediumLE(index, value)
    }

    override fun setInt(index: Int, value: Int): ByteBuf {
        unwrap().setInt(index, value)
        return this
    }

    override fun _setInt(index: Int, value: Int) {
        unwrap().setInt(index, value)
    }

    override fun setIntLE(index: Int, value: Int): ByteBuf {
        unwrap().setIntLE(index, value)
        return this
    }

    override fun _setIntLE(index: Int, value: Int) {
        unwrap().setIntLE(index, value)
    }

    override fun setLong(index: Int, value: Long): ByteBuf {
        unwrap().setLong(index, value)
        return this
    }

    override fun _setLong(index: Int, value: Long) {
        unwrap().setLong(index, value)
    }

    override fun setLongLE(index: Int, value: Long): ByteBuf {
        unwrap().setLongLE(index, value)
        return this
    }

    override fun _setLongLE(index: Int, value: Long) {
        unwrap().setLongLE(index, value)
    }

    override fun setBytes(index: Int, src: ByteArray, srcIndex: Int, length: Int): ByteBuf {
        unwrap().setBytes(index, src, srcIndex, length)
        return this
    }

    override fun setBytes(index: Int, src: ByteBuf, srcIndex: Int, length: Int): ByteBuf {
        unwrap().setBytes(index, src, srcIndex, length)
        return this
    }

    override fun setBytes(index: Int, src: ByteBuffer): ByteBuf {
        unwrap().setBytes(index, src)
        return this
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: OutputStream, length: Int): ByteBuf {
        unwrap().getBytes(index, out, length)
        return this
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: GatheringByteChannel, length: Int): Int =
        unwrap().getBytes(index, out, length)

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: FileChannel, position: Long, length: Int): Int =
        unwrap().getBytes(index, out, position, length)

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: InputStream, length: Int): Int =
        unwrap().setBytes(index, `in`, length)

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: ScatteringByteChannel, length: Int): Int =
        unwrap().setBytes(index, `in`, length)

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: FileChannel, position: Long, length: Int): Int =
        unwrap().setBytes(index, `in`, position, length)

    override fun nioBufferCount(): Int = unwrap().nioBufferCount()

    override fun nioBuffers(index: Int, length: Int): Array<ByteBuffer> =
        unwrap().nioBuffers(index, length)

    override fun forEachByte(index: Int, length: Int, processor: ByteProcessor): Int =
        unwrap().forEachByte(index, length, processor)

    override fun forEachByteDesc(index: Int, length: Int, processor: ByteProcessor): Int =
        unwrap().forEachByteDesc(index, length, processor)
}
