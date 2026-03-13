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

import io.netty.util.internal.EmptyArrays
import io.netty.util.internal.ObjectUtil.checkNotNull
import io.netty.util.internal.PlatformDependent

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.ClosedChannelException
import java.nio.channels.FileChannel
import java.nio.channels.GatheringByteChannel
import java.nio.channels.ScatteringByteChannel

/**
 * Big endian Java heap buffer implementation. It is recommended to use
 * [UnpooledByteBufAllocator.heapBuffer], [Unpooled.buffer] and
 * [Unpooled.wrappedBuffer] instead of calling the constructor explicitly.
 */
open class UnpooledHeapByteBuf : AbstractReferenceCountedByteBuf {

    private val alloc: ByteBufAllocator

    @JvmField
    var array: ByteArray = byteArrayOf()

    private var tmpNioBuf: ByteBuffer? = null

    /**
     * Creates a new heap buffer with a newly allocated byte array.
     *
     * @param initialCapacity the initial capacity of the underlying byte array
     * @param maxCapacity the max capacity of the underlying byte array
     */
    constructor(alloc: ByteBufAllocator, initialCapacity: Int, maxCapacity: Int) : super(maxCapacity) {
        require(initialCapacity <= maxCapacity) {
            "initialCapacity($initialCapacity) > maxCapacity($maxCapacity)"
        }

        this.alloc = checkNotNull(alloc, "alloc")
        setArray(allocateArray(initialCapacity))
        setIndex(0, 0)
    }

    /**
     * Creates a new heap buffer with an existing byte array.
     *
     * @param initialArray the initial underlying byte array
     * @param maxCapacity the max capacity of the underlying byte array
     */
    internal constructor(alloc: ByteBufAllocator, initialArray: ByteArray, maxCapacity: Int) : super(maxCapacity) {
        checkNotNull(alloc, "alloc")
        checkNotNull(initialArray, "initialArray")
        require(initialArray.size <= maxCapacity) {
            "initialCapacity(${initialArray.size}) > maxCapacity($maxCapacity)"
        }

        this.alloc = alloc
        setArray(initialArray)
        setIndex(0, initialArray.size)
    }

    protected open fun allocateArray(initialCapacity: Int): ByteArray {
        return ByteArray(initialCapacity)
    }

    protected open fun freeArray(array: ByteArray) {
        // NOOP
    }

    private fun setArray(initialArray: ByteArray) {
        array = initialArray
        tmpNioBuf = null
    }

    override fun alloc(): ByteBufAllocator = alloc

    override fun order(): ByteOrder = ByteOrder.BIG_ENDIAN

    override fun isDirect(): Boolean = false

    override fun capacity(): Int = array.size

    override fun capacity(newCapacity: Int): ByteBuf {
        checkNewCapacity(newCapacity)
        val oldArray = array
        val oldCapacity = oldArray.size
        if (newCapacity == oldCapacity) {
            return this
        }

        val bytesToCopy: Int
        if (newCapacity > oldCapacity) {
            bytesToCopy = oldCapacity
        } else {
            trimIndicesToCapacity(newCapacity)
            bytesToCopy = newCapacity
        }
        val newArray = allocateArray(newCapacity)
        System.arraycopy(oldArray, 0, newArray, 0, bytesToCopy)
        setArray(newArray)
        freeArray(oldArray)
        return this
    }

    override fun hasArray(): Boolean = true

    override fun array(): ByteArray {
        ensureAccessible()
        return array
    }

    override fun arrayOffset(): Int = 0

    override fun hasMemoryAddress(): Boolean = false

    override fun memoryAddress(): Long {
        throw UnsupportedOperationException()
    }

    override fun getBytes(index: Int, dst: ByteBuf, dstIndex: Int, length: Int): ByteBuf {
        checkDstIndex(index, length, dstIndex, dst.capacity())
        if (dst.hasMemoryAddress() && PlatformDependent.hasUnsafe()) {
            PlatformDependent.copyMemory(array, index, dst.memoryAddress() + dstIndex, length.toLong())
        } else if (dst.hasArray()) {
            getBytes(index, dst.array(), dst.arrayOffset() + dstIndex, length)
        } else {
            dst.setBytes(dstIndex, array, index, length)
        }
        return this
    }

    override fun getBytes(index: Int, dst: ByteArray, dstIndex: Int, length: Int): ByteBuf {
        checkDstIndex(index, length, dstIndex, dst.size)
        System.arraycopy(array, index, dst, dstIndex, length)
        return this
    }

    override fun getBytes(index: Int, dst: ByteBuffer): ByteBuf {
        ensureAccessible()
        dst.put(array, index, dst.remaining())
        return this
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: OutputStream, length: Int): ByteBuf {
        ensureAccessible()
        out.write(array, index, length)
        return this
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: GatheringByteChannel, length: Int): Int {
        ensureAccessible()
        return getBytes(index, out, length, false)
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: FileChannel, position: Long, length: Int): Int {
        ensureAccessible()
        return getBytes(index, out, position, length, false)
    }

    @Throws(IOException::class)
    private fun getBytes(index: Int, out: GatheringByteChannel, length: Int, internal: Boolean): Int {
        ensureAccessible()
        val tmpBuf: ByteBuffer = if (internal) {
            _internalNioBuffer()
        } else {
            ByteBuffer.wrap(array)
        }
        return out.write(tmpBuf.clear().position(index).limit(index + length) as ByteBuffer)
    }

    @Throws(IOException::class)
    private fun getBytes(index: Int, out: FileChannel, position: Long, length: Int, internal: Boolean): Int {
        ensureAccessible()
        val tmpBuf: ByteBuffer = if (internal) _internalNioBuffer() else ByteBuffer.wrap(array)
        return out.write(tmpBuf.clear().position(index).limit(index + length) as ByteBuffer, position)
    }

    @Throws(IOException::class)
    override fun readBytes(out: GatheringByteChannel, length: Int): Int {
        checkReadableBytes(length)
        val readBytes = getBytes(readerIndex, out, length, true)
        readerIndex += readBytes
        return readBytes
    }

    @Throws(IOException::class)
    override fun readBytes(out: FileChannel, position: Long, length: Int): Int {
        checkReadableBytes(length)
        val readBytes = getBytes(readerIndex, out, position, length, true)
        readerIndex += readBytes
        return readBytes
    }

    override fun setBytes(index: Int, src: ByteBuf, srcIndex: Int, length: Int): ByteBuf {
        checkSrcIndex(index, length, srcIndex, src.capacity())
        if (src.hasMemoryAddress() && PlatformDependent.hasUnsafe()) {
            PlatformDependent.copyMemory(src.memoryAddress() + srcIndex, array, index, length.toLong())
        } else if (src.hasArray()) {
            setBytes(index, src.array(), src.arrayOffset() + srcIndex, length)
        } else {
            src.getBytes(srcIndex, array, index, length)
        }
        return this
    }

    override fun setBytes(index: Int, src: ByteArray, srcIndex: Int, length: Int): ByteBuf {
        checkSrcIndex(index, length, srcIndex, src.size)
        System.arraycopy(src, srcIndex, array, index, length)
        return this
    }

    override fun setBytes(index: Int, src: ByteBuffer): ByteBuf {
        ensureAccessible()
        src.get(array, index, src.remaining())
        return this
    }

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: InputStream, length: Int): Int {
        ensureAccessible()
        return `in`.read(array, index, length)
    }

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: ScatteringByteChannel, length: Int): Int {
        ensureAccessible()
        return try {
            `in`.read(_internalNioBuffer().clear().position(index).limit(index + length) as ByteBuffer)
        } catch (ignored: ClosedChannelException) {
            -1
        }
    }

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: FileChannel, position: Long, length: Int): Int {
        ensureAccessible()
        return try {
            `in`.read(_internalNioBuffer().clear().position(index).limit(index + length) as ByteBuffer, position)
        } catch (ignored: ClosedChannelException) {
            -1
        }
    }

    override fun nioBufferCount(): Int = 1

    override fun nioBuffer(index: Int, length: Int): ByteBuffer {
        ensureAccessible()
        return ByteBuffer.wrap(array, index, length).slice()
    }

    override fun nioBuffers(index: Int, length: Int): Array<ByteBuffer> {
        return arrayOf(nioBuffer(index, length))
    }

    override fun internalNioBuffer(index: Int, length: Int): ByteBuffer {
        checkIndex(index, length)
        return _internalNioBuffer().clear().position(index).limit(index + length) as ByteBuffer
    }

    final override fun isContiguous(): Boolean = true

    override fun getByte(index: Int): Byte {
        ensureAccessible()
        return _getByte(index)
    }

    override fun _getByte(index: Int): Byte = HeapByteBufUtil.getByte(array, index)

    override fun getShort(index: Int): Short {
        ensureAccessible()
        return _getShort(index)
    }

    override fun _getShort(index: Int): Short = HeapByteBufUtil.getShort(array, index)

    override fun getShortLE(index: Int): Short {
        ensureAccessible()
        return _getShortLE(index)
    }

    override fun _getShortLE(index: Int): Short = HeapByteBufUtil.getShortLE(array, index)

    override fun getUnsignedMedium(index: Int): Int {
        ensureAccessible()
        return _getUnsignedMedium(index)
    }

    override fun _getUnsignedMedium(index: Int): Int = HeapByteBufUtil.getUnsignedMedium(array, index)

    override fun getUnsignedMediumLE(index: Int): Int {
        ensureAccessible()
        return _getUnsignedMediumLE(index)
    }

    override fun _getUnsignedMediumLE(index: Int): Int = HeapByteBufUtil.getUnsignedMediumLE(array, index)

    override fun getInt(index: Int): Int {
        ensureAccessible()
        return _getInt(index)
    }

    override fun _getInt(index: Int): Int = HeapByteBufUtil.getInt(array, index)

    override fun getIntLE(index: Int): Int {
        ensureAccessible()
        return _getIntLE(index)
    }

    override fun _getIntLE(index: Int): Int = HeapByteBufUtil.getIntLE(array, index)

    override fun getLong(index: Int): Long {
        ensureAccessible()
        return _getLong(index)
    }

    override fun _getLong(index: Int): Long = HeapByteBufUtil.getLong(array, index)

    override fun getLongLE(index: Int): Long {
        ensureAccessible()
        return _getLongLE(index)
    }

    override fun _getLongLE(index: Int): Long = HeapByteBufUtil.getLongLE(array, index)

    override fun setByte(index: Int, value: Int): ByteBuf {
        ensureAccessible()
        _setByte(index, value)
        return this
    }

    override fun _setByte(index: Int, value: Int) {
        HeapByteBufUtil.setByte(array, index, value)
    }

    override fun setShort(index: Int, value: Int): ByteBuf {
        ensureAccessible()
        _setShort(index, value)
        return this
    }

    override fun _setShort(index: Int, value: Int) {
        HeapByteBufUtil.setShort(array, index, value)
    }

    override fun setShortLE(index: Int, value: Int): ByteBuf {
        ensureAccessible()
        _setShortLE(index, value)
        return this
    }

    override fun _setShortLE(index: Int, value: Int) {
        HeapByteBufUtil.setShortLE(array, index, value)
    }

    override fun setMedium(index: Int, value: Int): ByteBuf {
        ensureAccessible()
        _setMedium(index, value)
        return this
    }

    override fun _setMedium(index: Int, value: Int) {
        HeapByteBufUtil.setMedium(array, index, value)
    }

    override fun setMediumLE(index: Int, value: Int): ByteBuf {
        ensureAccessible()
        _setMediumLE(index, value)
        return this
    }

    override fun _setMediumLE(index: Int, value: Int) {
        HeapByteBufUtil.setMediumLE(array, index, value)
    }

    override fun setInt(index: Int, value: Int): ByteBuf {
        ensureAccessible()
        _setInt(index, value)
        return this
    }

    override fun _setInt(index: Int, value: Int) {
        HeapByteBufUtil.setInt(array, index, value)
    }

    override fun setIntLE(index: Int, value: Int): ByteBuf {
        ensureAccessible()
        _setIntLE(index, value)
        return this
    }

    override fun _setIntLE(index: Int, value: Int) {
        HeapByteBufUtil.setIntLE(array, index, value)
    }

    override fun setLong(index: Int, value: Long): ByteBuf {
        ensureAccessible()
        _setLong(index, value)
        return this
    }

    override fun _setLong(index: Int, value: Long) {
        HeapByteBufUtil.setLong(array, index, value)
    }

    override fun setLongLE(index: Int, value: Long): ByteBuf {
        ensureAccessible()
        _setLongLE(index, value)
        return this
    }

    override fun _setLongLE(index: Int, value: Long) {
        HeapByteBufUtil.setLongLE(array, index, value)
    }

    override fun copy(index: Int, length: Int): ByteBuf {
        checkIndex(index, length)
        return alloc().heapBuffer(length, maxCapacity()).writeBytes(array, index, length)
    }

    override fun _internalNioBuffer(): ByteBuffer {
        var tmpNioBuf = this.tmpNioBuf
        if (tmpNioBuf == null) {
            tmpNioBuf = ByteBuffer.wrap(array)
            this.tmpNioBuf = tmpNioBuf
        }
        return tmpNioBuf
    }

    override fun deallocate() {
        freeArray(array)
        array = EmptyArrays.EMPTY_BYTES
    }

    override fun unwrap(): ByteBuf? = null
}
