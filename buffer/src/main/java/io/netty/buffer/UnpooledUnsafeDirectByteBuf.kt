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

import io.netty.util.internal.CleanableDirectBuffer
import io.netty.util.internal.PlatformDependent
import io.netty.util.internal.UnstableApi

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * A NIO [ByteBuffer] based buffer. It is recommended to use
 * [UnpooledByteBufAllocator.directBuffer], [Unpooled.directBuffer] and
 * [Unpooled.wrappedBuffer] instead of calling the constructor explicitly.
 */
open class UnpooledUnsafeDirectByteBuf : UnpooledDirectByteBuf {

    companion object {
        @JvmStatic
        private val USE_VAR_HANDLE: Boolean = PlatformDependent.useVarHandleForMultiByteAccess()
    }

    @JvmField
    var memoryAddress: Long = 0

    /**
     * Creates a new direct buffer.
     *
     * @param initialCapacity the initial capacity of the underlying direct buffer
     * @param maxCapacity     the maximum capacity of the underlying direct buffer
     */
    constructor(alloc: ByteBufAllocator, initialCapacity: Int, maxCapacity: Int)
            : super(alloc, initialCapacity, maxCapacity)

    /**
     * Creates a new direct buffer by wrapping the specified initial buffer.
     *
     * @param maxCapacity the maximum capacity of the underlying direct buffer
     */
    internal constructor(alloc: ByteBufAllocator, initialBuffer: ByteBuffer, maxCapacity: Int)
            : super(alloc, initialBuffer, maxCapacity, false, true)

    /**
     * Creates a new direct ByteBuf by wrapping the specified initial buffer.
     * Allows subclasses to control if initialBuffer.slice() should be invoked.
     */
    @UnstableApi
    protected constructor(alloc: ByteBufAllocator, slice: Boolean, initialBuffer: ByteBuffer, maxCapacity: Int)
            : super(alloc, initialBuffer, maxCapacity, false, slice)

    internal constructor(alloc: ByteBufAllocator, initialBuffer: ByteBuffer, maxCapacity: Int, doFree: Boolean)
            : super(alloc, initialBuffer, maxCapacity, doFree, false)

    final override fun setByteBuffer(buffer: ByteBuffer, tryFree: Boolean) {
        super.setByteBuffer(buffer, tryFree)
        memoryAddress = PlatformDependent.directBufferAddress(buffer)
    }

    final override fun setByteBuffer(cleanableDirectBuffer: CleanableDirectBuffer, tryFree: Boolean) {
        super.setByteBuffer(cleanableDirectBuffer, tryFree)
        memoryAddress = PlatformDependent.directBufferAddress(cleanableDirectBuffer.buffer())
    }

    override fun hasMemoryAddress(): Boolean = true

    override fun memoryAddress(): Long {
        ensureAccessible()
        return memoryAddress
    }

    override fun getByte(index: Int): Byte {
        checkIndex(index)
        return _getByte(index)
    }

    override fun _getByte(index: Int): Byte = UnsafeByteBufUtil.getByte(addr(index))

    override fun getShort(index: Int): Short {
        checkIndex(index, 2)
        return _getShort(index)
    }

    override fun _getShort(index: Int): Short {
        if (USE_VAR_HANDLE) {
            return VarHandleByteBufferAccess.getShortBE(buffer!!, index)
        }
        return UnsafeByteBufUtil.getShort(addr(index))
    }

    override fun _getShortLE(index: Int): Short {
        if (USE_VAR_HANDLE) {
            return VarHandleByteBufferAccess.getShortLE(buffer!!, index)
        }
        return UnsafeByteBufUtil.getShortLE(addr(index))
    }

    override fun getUnsignedMedium(index: Int): Int {
        checkIndex(index, 3)
        return _getUnsignedMedium(index)
    }

    override fun _getUnsignedMedium(index: Int): Int = UnsafeByteBufUtil.getUnsignedMedium(addr(index))

    override fun _getUnsignedMediumLE(index: Int): Int = UnsafeByteBufUtil.getUnsignedMediumLE(addr(index))

    override fun getInt(index: Int): Int {
        checkIndex(index, 4)
        return _getInt(index)
    }

    override fun _getInt(index: Int): Int {
        if (USE_VAR_HANDLE) {
            return VarHandleByteBufferAccess.getIntBE(buffer!!, index)
        }
        return UnsafeByteBufUtil.getInt(addr(index))
    }

    override fun _getIntLE(index: Int): Int {
        if (USE_VAR_HANDLE) {
            return VarHandleByteBufferAccess.getIntLE(buffer!!, index)
        }
        return UnsafeByteBufUtil.getIntLE(addr(index))
    }

    override fun getLong(index: Int): Long {
        checkIndex(index, 8)
        return _getLong(index)
    }

    override fun _getLong(index: Int): Long {
        if (USE_VAR_HANDLE) {
            return VarHandleByteBufferAccess.getLongBE(buffer!!, index)
        }
        return UnsafeByteBufUtil.getLong(addr(index))
    }

    override fun _getLongLE(index: Int): Long {
        if (USE_VAR_HANDLE) {
            return VarHandleByteBufferAccess.getLongLE(buffer!!, index)
        }
        return UnsafeByteBufUtil.getLongLE(addr(index))
    }

    override fun getBytes(index: Int, dst: ByteBuf, dstIndex: Int, length: Int): ByteBuf {
        UnsafeByteBufUtil.getBytes(this, addr(index), index, dst, dstIndex, length)
        return this
    }

    override fun getBytes(index: Int, dst: ByteArray, dstIndex: Int, length: Int, internal: Boolean) {
        UnsafeByteBufUtil.getBytes(this, addr(index), index, dst, dstIndex, length)
    }

    override fun getBytes(index: Int, dst: ByteBuffer, internal: Boolean) {
        UnsafeByteBufUtil.getBytes(this, addr(index), index, dst)
    }

    override fun setByte(index: Int, value: Int): ByteBuf {
        checkIndex(index)
        _setByte(index, value)
        return this
    }

    override fun _setByte(index: Int, value: Int) {
        UnsafeByteBufUtil.setByte(addr(index), value)
    }

    override fun setShort(index: Int, value: Int): ByteBuf {
        checkIndex(index, 2)
        _setShort(index, value)
        return this
    }

    override fun _setShort(index: Int, value: Int) {
        if (USE_VAR_HANDLE) {
            VarHandleByteBufferAccess.setShortBE(buffer!!, index, value)
            return
        }
        UnsafeByteBufUtil.setShort(addr(index), value)
    }

    override fun _setShortLE(index: Int, value: Int) {
        if (USE_VAR_HANDLE) {
            VarHandleByteBufferAccess.setShortLE(buffer!!, index, value)
            return
        }
        UnsafeByteBufUtil.setShortLE(addr(index), value)
    }

    override fun setMedium(index: Int, value: Int): ByteBuf {
        checkIndex(index, 3)
        _setMedium(index, value)
        return this
    }

    override fun _setMedium(index: Int, value: Int) {
        UnsafeByteBufUtil.setMedium(addr(index), value)
    }

    override fun _setMediumLE(index: Int, value: Int) {
        UnsafeByteBufUtil.setMediumLE(addr(index), value)
    }

    override fun setInt(index: Int, value: Int): ByteBuf {
        checkIndex(index, 4)
        _setInt(index, value)
        return this
    }

    override fun _setInt(index: Int, value: Int) {
        if (USE_VAR_HANDLE) {
            VarHandleByteBufferAccess.setIntBE(buffer!!, index, value)
            return
        }
        UnsafeByteBufUtil.setInt(addr(index), value)
    }

    override fun _setIntLE(index: Int, value: Int) {
        if (USE_VAR_HANDLE) {
            VarHandleByteBufferAccess.setIntLE(buffer!!, index, value)
            return
        }
        UnsafeByteBufUtil.setIntLE(addr(index), value)
    }

    override fun setLong(index: Int, value: Long): ByteBuf {
        checkIndex(index, 8)
        _setLong(index, value)
        return this
    }

    override fun _setLong(index: Int, value: Long) {
        if (USE_VAR_HANDLE) {
            VarHandleByteBufferAccess.setLongBE(buffer!!, index, value)
            return
        }
        UnsafeByteBufUtil.setLong(addr(index), value)
    }

    override fun _setLongLE(index: Int, value: Long) {
        if (USE_VAR_HANDLE) {
            VarHandleByteBufferAccess.setLongLE(buffer!!, index, value)
            return
        }
        UnsafeByteBufUtil.setLongLE(addr(index), value)
    }

    override fun setBytes(index: Int, src: ByteBuf, srcIndex: Int, length: Int): ByteBuf {
        UnsafeByteBufUtil.setBytes(this, addr(index), index, src, srcIndex, length)
        return this
    }

    override fun setBytes(index: Int, src: ByteArray, srcIndex: Int, length: Int): ByteBuf {
        UnsafeByteBufUtil.setBytes(this, addr(index), index, src, srcIndex, length)
        return this
    }

    override fun setBytes(index: Int, src: ByteBuffer): ByteBuf {
        UnsafeByteBufUtil.setBytes(this, addr(index), index, src)
        return this
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: OutputStream, length: Int, internal: Boolean) {
        UnsafeByteBufUtil.getBytes(this, addr(index), index, out, length)
    }

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: InputStream, length: Int): Int {
        return UnsafeByteBufUtil.setBytes(this, addr(index), index, `in`, length)
    }

    override fun copy(index: Int, length: Int): ByteBuf {
        return UnsafeByteBufUtil.copy(this, addr(index), index, length)
    }

    internal fun addr(index: Int): Long = memoryAddress + index

    override fun newSwappedByteBuf(): SwappedByteBuf {
        if (PlatformDependent.isUnaligned()) {
            // Only use if unaligned access is supported otherwise there is no gain.
            return UnsafeDirectSwappedByteBuf(this)
        }
        return super.newSwappedByteBuf()
    }

    override fun setZero(index: Int, length: Int): ByteBuf {
        checkIndex(index, length)
        UnsafeByteBufUtil.setZero(addr(index), length)
        return this
    }

    override fun writeZero(length: Int): ByteBuf {
        ensureWritable(length)
        val wIndex = writerIndex
        UnsafeByteBufUtil.setZero(addr(wIndex), length)
        writerIndex = wIndex + length
        return this
    }
}
