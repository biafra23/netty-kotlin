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

import io.netty.util.Recycler
import io.netty.util.internal.ObjectPool.Handle
import io.netty.util.internal.PlatformDependent
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

internal class PooledUnsafeDirectByteBuf private constructor(
    recyclerHandle: Handle<PooledUnsafeDirectByteBuf>,
    maxCapacity: Int
) : PooledByteBuf<ByteBuffer>(recyclerHandle, maxCapacity) {

    private var memoryAddress: Long = 0

    internal override fun init(
        chunk: PoolChunk<ByteBuffer>, nioBuffer: ByteBuffer?,
        handle: Long, offset: Int, length: Int, maxLength: Int, cache: PoolThreadCache, threadLocal: Boolean
    ) {
        super.init(chunk, nioBuffer, handle, offset, length, maxLength, cache, threadLocal)
        initMemoryAddress()
    }

    internal override fun initUnpooled(chunk: PoolChunk<ByteBuffer>, length: Int) {
        super.initUnpooled(chunk, length)
        initMemoryAddress()
    }

    private fun initMemoryAddress() {
        memoryAddress = PlatformDependent.directBufferAddress(memory) + offset
    }

    internal override fun newInternalNioBuffer(memory: ByteBuffer): ByteBuffer =
        memory.duplicate()

    override fun isDirect(): Boolean = true

    protected override fun _getByte(index: Int): Byte =
        UnsafeByteBufUtil.getByte(addr(index))

    protected override fun _getShort(index: Int): Short =
        UnsafeByteBufUtil.getShort(addr(index))

    protected override fun _getShortLE(index: Int): Short =
        UnsafeByteBufUtil.getShortLE(addr(index))

    protected override fun _getUnsignedMedium(index: Int): Int =
        UnsafeByteBufUtil.getUnsignedMedium(addr(index))

    protected override fun _getUnsignedMediumLE(index: Int): Int =
        UnsafeByteBufUtil.getUnsignedMediumLE(addr(index))

    protected override fun _getInt(index: Int): Int =
        UnsafeByteBufUtil.getInt(addr(index))

    protected override fun _getIntLE(index: Int): Int =
        UnsafeByteBufUtil.getIntLE(addr(index))

    protected override fun _getLong(index: Int): Long =
        UnsafeByteBufUtil.getLong(addr(index))

    protected override fun _getLongLE(index: Int): Long =
        UnsafeByteBufUtil.getLongLE(addr(index))

    override fun getBytes(index: Int, dst: ByteBuf, dstIndex: Int, length: Int): ByteBuf {
        UnsafeByteBufUtil.getBytes(this, addr(index), index, dst, dstIndex, length)
        return this
    }

    override fun getBytes(index: Int, dst: ByteArray, dstIndex: Int, length: Int): ByteBuf {
        UnsafeByteBufUtil.getBytes(this, addr(index), index, dst, dstIndex, length)
        return this
    }

    override fun getBytes(index: Int, dst: ByteBuffer): ByteBuf {
        UnsafeByteBufUtil.getBytes(this, addr(index), index, dst)
        return this
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: OutputStream, length: Int): ByteBuf {
        UnsafeByteBufUtil.getBytes(this, addr(index), index, out, length)
        return this
    }

    protected override fun _setByte(index: Int, value: Int) {
        UnsafeByteBufUtil.setByte(addr(index), value)
    }

    protected override fun _setShort(index: Int, value: Int) {
        UnsafeByteBufUtil.setShort(addr(index), value)
    }

    protected override fun _setShortLE(index: Int, value: Int) {
        UnsafeByteBufUtil.setShortLE(addr(index), value)
    }

    protected override fun _setMedium(index: Int, value: Int) {
        UnsafeByteBufUtil.setMedium(addr(index), value)
    }

    protected override fun _setMediumLE(index: Int, value: Int) {
        UnsafeByteBufUtil.setMediumLE(addr(index), value)
    }

    protected override fun _setInt(index: Int, value: Int) {
        UnsafeByteBufUtil.setInt(addr(index), value)
    }

    protected override fun _setIntLE(index: Int, value: Int) {
        UnsafeByteBufUtil.setIntLE(addr(index), value)
    }

    protected override fun _setLong(index: Int, value: Long) {
        UnsafeByteBufUtil.setLong(addr(index), value)
    }

    protected override fun _setLongLE(index: Int, value: Long) {
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
    override fun setBytes(index: Int, `in`: InputStream, length: Int): Int =
        UnsafeByteBufUtil.setBytes(this, addr(index), index, `in`, length)

    override fun copy(index: Int, length: Int): ByteBuf =
        UnsafeByteBufUtil.copy(this, addr(index), index, length)

    override fun hasArray(): Boolean = false

    override fun array(): ByteArray =
        throw UnsupportedOperationException("direct buffer")

    override fun arrayOffset(): Int =
        throw UnsupportedOperationException("direct buffer")

    override fun hasMemoryAddress(): Boolean = true

    override fun memoryAddress(): Long {
        ensureAccessible()
        return memoryAddress
    }

    internal override fun _memoryAddress(): Long = memoryAddress

    private fun addr(index: Int): Long = memoryAddress + index

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

    companion object {
        @JvmField
        internal val RECYCLER: Recycler<PooledUnsafeDirectByteBuf> =
            object : Recycler<PooledUnsafeDirectByteBuf>() {
                override fun newObject(handle: Handle<PooledUnsafeDirectByteBuf>): PooledUnsafeDirectByteBuf =
                    PooledUnsafeDirectByteBuf(handle, 0)
            }

        @JvmStatic
        internal fun newInstance(maxCapacity: Int): PooledUnsafeDirectByteBuf {
            val buf = RECYCLER.get()
            buf.reuse(maxCapacity)
            return buf
        }
    }
}
