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

import io.netty.util.internal.StringUtil
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
 * Read-only ByteBuf which wraps a read-only ByteBuffer.
 */
internal open class ReadOnlyByteBufferBuf(
    allocator: ByteBufAllocator,
    buffer: ByteBuffer
) : AbstractReferenceCountedByteBuf(buffer.remaining()) {

    @JvmField
    protected val buffer: ByteBuffer
    private val allocator: ByteBufAllocator
    private var tmpNioBuf: ByteBuffer? = null

    init {
        require(buffer.isReadOnly) { "must be a readonly buffer: ${StringUtil.simpleClassName(buffer)}" }
        this.allocator = allocator
        this.buffer = buffer.slice().order(ByteOrder.BIG_ENDIAN)
        writerIndex(this.buffer.limit())
    }

    override fun deallocate() {}

    override fun isWritable(): Boolean = false

    override fun isWritable(numBytes: Int): Boolean = false

    override fun ensureWritable(minWritableBytes: Int): ByteBuf {
        throw ReadOnlyBufferException()
    }

    override fun ensureWritable(minWritableBytes: Int, force: Boolean): Int = 1

    override fun getByte(index: Int): Byte {
        ensureAccessible()
        return _getByte(index)
    }

    override fun _getByte(index: Int): Byte = buffer.get(index)

    override fun getShort(index: Int): Short {
        ensureAccessible()
        return _getShort(index)
    }

    override fun _getShort(index: Int): Short = buffer.getShort(index)

    override fun getShortLE(index: Int): Short {
        ensureAccessible()
        return _getShortLE(index)
    }

    override fun _getShortLE(index: Int): Short = ByteBufUtil.swapShort(buffer.getShort(index))

    override fun getUnsignedMedium(index: Int): Int {
        ensureAccessible()
        return _getUnsignedMedium(index)
    }

    override fun _getUnsignedMedium(index: Int): Int {
        return (getByte(index).toInt() and 0xff shl 16) or
                (getByte(index + 1).toInt() and 0xff shl 8) or
                (getByte(index + 2).toInt() and 0xff)
    }

    override fun getUnsignedMediumLE(index: Int): Int {
        ensureAccessible()
        return _getUnsignedMediumLE(index)
    }

    override fun _getUnsignedMediumLE(index: Int): Int {
        return (getByte(index).toInt() and 0xff) or
                (getByte(index + 1).toInt() and 0xff shl 8) or
                (getByte(index + 2).toInt() and 0xff shl 16)
    }

    override fun getInt(index: Int): Int {
        ensureAccessible()
        return _getInt(index)
    }

    override fun _getInt(index: Int): Int = buffer.getInt(index)

    override fun getIntLE(index: Int): Int {
        ensureAccessible()
        return _getIntLE(index)
    }

    override fun _getIntLE(index: Int): Int = ByteBufUtil.swapInt(buffer.getInt(index))

    override fun getLong(index: Int): Long {
        ensureAccessible()
        return _getLong(index)
    }

    override fun _getLong(index: Int): Long = buffer.getLong(index)

    override fun getLongLE(index: Int): Long {
        ensureAccessible()
        return _getLongLE(index)
    }

    override fun _getLongLE(index: Int): Long = ByteBufUtil.swapLong(buffer.getLong(index))

    override fun getBytes(index: Int, dst: ByteBuf, dstIndex: Int, length: Int): ByteBuf {
        return getBytes(index, dst, dstIndex, length, false)
    }

    override fun readBytes(dst: ByteBuf, dstIndex: Int, length: Int): ByteBuf {
        checkReadableBytes(length)
        getBytes(readerIndex, dst, dstIndex, length, true)
        readerIndex += length
        return this
    }

    protected open fun getBytes(index: Int, dst: ByteBuf, dstIndex: Int, length: Int, internal: Boolean): ByteBuf {
        checkDstIndex(index, length, dstIndex, dst.capacity())
        if (dst.hasArray()) {
            getBytes(index, dst.array(), dst.arrayOffset() + dstIndex, length)
        } else if (dst.nioBufferCount() > 0) {
            for (bb in dst.nioBuffers(dstIndex, length)) {
                val bbLen = bb.remaining()
                getBytes(index, bb, internal)
            }
        } else {
            dst.setBytes(dstIndex, this, index, length)
        }
        return this
    }

    override fun getBytes(index: Int, dst: ByteArray, dstIndex: Int, length: Int): ByteBuf {
        return getBytes(index, dst, dstIndex, length, false)
    }

    override fun readBytes(dst: ByteArray, dstIndex: Int, length: Int): ByteBuf {
        checkReadableBytes(length)
        getBytes(readerIndex, dst, dstIndex, length, true)
        readerIndex += length
        return this
    }

    protected open fun getBytes(index: Int, dst: ByteArray, dstIndex: Int, length: Int, internal: Boolean): ByteBuf {
        checkDstIndex(index, length, dstIndex, dst.size)
        val tmpBuf = nioBuffer(internal)
        tmpBuf.clear().position(index).limit(index + length)
        tmpBuf.get(dst, dstIndex, length)
        return this
    }

    override fun getBytes(index: Int, dst: ByteBuffer): ByteBuf {
        return getBytes(index, dst, false)
    }

    override fun readBytes(dst: ByteBuffer): ByteBuf {
        val length = dst.remaining()
        checkReadableBytes(length)
        getBytes(readerIndex, dst, true)
        readerIndex += length
        return this
    }

    private fun getBytes(index: Int, dst: ByteBuffer, internal: Boolean): ByteBuf {
        checkIndex(index, dst.remaining())
        val tmpBuf = nioBuffer(internal)
        tmpBuf.clear().position(index).limit(index + dst.remaining())
        dst.put(tmpBuf)
        return this
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

    override fun capacity(): Int = maxCapacity()

    override fun capacity(newCapacity: Int): ByteBuf {
        throw ReadOnlyBufferException()
    }

    override fun alloc(): ByteBufAllocator = allocator

    override fun order(): ByteOrder = ByteOrder.BIG_ENDIAN

    override fun unwrap(): ByteBuf? = null

    override fun isReadOnly(): Boolean = buffer.isReadOnly

    override fun isDirect(): Boolean = buffer.isDirect

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: OutputStream, length: Int): ByteBuf {
        return getBytes(index, out, length, false)
    }

    @Throws(IOException::class)
    override fun readBytes(out: OutputStream, length: Int): ByteBuf {
        checkReadableBytes(length)
        getBytes(readerIndex, out, length, true)
        readerIndex += length
        return this
    }

    @Throws(IOException::class)
    private fun getBytes(index: Int, out: OutputStream, length: Int, internal: Boolean): ByteBuf {
        ensureAccessible()
        if (length == 0) {
            return this
        }

        if (buffer.hasArray()) {
            out.write(buffer.array(), index + buffer.arrayOffset(), length)
        } else {
            val tmp = ByteBufUtil.threadLocalTempArray(length)
            val tmpBuf = nioBuffer(internal)
            tmpBuf.clear().position(index)
            tmpBuf.get(tmp, 0, length)
            out.write(tmp, 0, length)
        }
        return this
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: GatheringByteChannel, length: Int): Int {
        return getBytes(index, out, length, false)
    }

    @Throws(IOException::class)
    override fun readBytes(out: GatheringByteChannel, length: Int): Int {
        checkReadableBytes(length)
        val readBytes = getBytes(readerIndex, out, length, true)
        readerIndex += readBytes
        return readBytes
    }

    @Throws(IOException::class)
    private fun getBytes(index: Int, out: GatheringByteChannel, length: Int, internal: Boolean): Int {
        ensureAccessible()
        if (length == 0) {
            return 0
        }

        val tmpBuf = nioBuffer(internal)
        tmpBuf.clear().position(index).limit(index + length)
        return out.write(tmpBuf)
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: FileChannel, position: Long, length: Int): Int {
        return getBytes(index, out, position, length, false)
    }

    @Throws(IOException::class)
    override fun readBytes(out: FileChannel, position: Long, length: Int): Int {
        checkReadableBytes(length)
        val readBytes = getBytes(readerIndex, out, position, length, true)
        readerIndex += readBytes
        return readBytes
    }

    @Throws(IOException::class)
    private fun getBytes(index: Int, out: FileChannel, position: Long, length: Int, internal: Boolean): Int {
        ensureAccessible()
        if (length == 0) {
            return 0
        }

        val tmpBuf = nioBuffer(internal)
        tmpBuf.clear().position(index).limit(index + length)
        return out.write(tmpBuf, position)
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

    protected fun internalNioBuffer(): ByteBuffer {
        var tmpNioBuf = this.tmpNioBuf
        if (tmpNioBuf == null) {
            tmpNioBuf = buffer.duplicate()
            this.tmpNioBuf = tmpNioBuf
        }
        return tmpNioBuf
    }

    override fun copy(index: Int, length: Int): ByteBuf {
        ensureAccessible()
        val src: ByteBuffer
        try {
            src = buffer.duplicate().clear().position(index).limit(index + length) as ByteBuffer
        } catch (ignored: IllegalArgumentException) {
            throw IndexOutOfBoundsException("Too many bytes to read - Need ${index + length}")
        }

        val dst = if (src.isDirect) alloc().directBuffer(length) else alloc().heapBuffer(length)
        dst.writeBytes(src)
        return dst
    }

    override fun nioBufferCount(): Int = 1

    override fun nioBuffers(index: Int, length: Int): Array<ByteBuffer> {
        return arrayOf(nioBuffer(index, length))
    }

    override fun nioBuffer(index: Int, length: Int): ByteBuffer {
        checkIndex(index, length)
        return buffer.duplicate().position(index).limit(index + length) as ByteBuffer
    }

    override fun internalNioBuffer(index: Int, length: Int): ByteBuffer {
        ensureAccessible()
        return internalNioBuffer().clear().position(index).limit(index + length) as ByteBuffer
    }

    final override fun isContiguous(): Boolean = true

    override fun hasArray(): Boolean = buffer.hasArray()

    override fun array(): ByteArray = buffer.array()

    override fun arrayOffset(): Int = buffer.arrayOffset()

    override fun hasMemoryAddress(): Boolean = false

    override fun memoryAddress(): Long {
        throw UnsupportedOperationException()
    }

    private fun nioBuffer(internal: Boolean): ByteBuffer {
        return if (internal) internalNioBuffer() else buffer.duplicate()
    }

    override fun duplicate(): ByteBuf {
        return ReadOnlyDuplicatedByteBuf(this)
    }

    override fun slice(index: Int, length: Int): ByteBuf {
        return ReadOnlySlicedByteBuf(this, index, length)
    }

    override fun asReadOnly(): ByteBuf = this

    @Suppress("DEPRECATION")
    private class ReadOnlySlicedByteBuf(
        buffer: ByteBuf,
        index: Int,
        length: Int
    ) : SlicedByteBuf(buffer, index, length) {

        override fun asReadOnly(): ByteBuf = this

        override fun slice(index: Int, length: Int): ByteBuf {
            return ReadOnlySlicedByteBuf(this, index, length)
        }

        override fun duplicate(): ByteBuf {
            return slice(0, capacity()).setIndex(readerIndex(), writerIndex())
        }

        override fun isWritable(): Boolean = false

        override fun isWritable(numBytes: Int): Boolean = false

        override fun ensureWritable(minWritableBytes: Int, force: Boolean): Int = 1
    }

    @Suppress("DEPRECATION")
    private class ReadOnlyDuplicatedByteBuf(buffer: ByteBuf) : DuplicatedByteBuf(buffer) {

        override fun asReadOnly(): ByteBuf = this

        override fun slice(index: Int, length: Int): ByteBuf {
            return ReadOnlySlicedByteBuf(this, index, length)
        }

        override fun duplicate(): ByteBuf {
            return ReadOnlyDuplicatedByteBuf(this)
        }

        override fun isWritable(): Boolean = false

        override fun isWritable(numBytes: Int): Boolean = false

        override fun ensureWritable(minWritableBytes: Int, force: Boolean): Int = 1
    }
}
