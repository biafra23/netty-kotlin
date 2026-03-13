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
import io.netty.util.Recycler
import io.netty.util.internal.ObjectPool.Handle
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.GatheringByteChannel
import java.nio.channels.ScatteringByteChannel

internal class PooledSlicedByteBuf private constructor(
    handle: Handle<PooledSlicedByteBuf>
) : AbstractPooledDerivedByteBuf(handle) {

    @JvmField
    var adjustment: Int = 0

    override fun capacity(): Int = maxCapacity()

    override fun capacity(newCapacity: Int): ByteBuf =
        throw UnsupportedOperationException("sliced buffer")

    override fun arrayOffset(): Int = idx(unwrap().arrayOffset())

    override fun memoryAddress(): Long = unwrap().memoryAddress() + adjustment

    override fun nioBuffer(index: Int, length: Int): ByteBuffer {
        checkIndex0(index, length)
        return unwrap().nioBuffer(idx(index), length)
    }

    override fun nioBuffers(index: Int, length: Int): Array<ByteBuffer> {
        checkIndex0(index, length)
        return unwrap().nioBuffers(idx(index), length)
    }

    override fun copy(index: Int, length: Int): ByteBuf {
        checkIndex0(index, length)
        return unwrap().copy(idx(index), length)
    }

    override fun slice(index: Int, length: Int): ByteBuf {
        checkIndex0(index, length)
        return super.slice(idx(index), length)
    }

    override fun retainedSlice(index: Int, length: Int): ByteBuf {
        checkIndex0(index, length)
        return newInstance0(unwrap(), this, idx(index), length)
    }

    override fun duplicate(): ByteBuf =
        slice(0, capacity()).setIndex(readerIndex(), writerIndex())

    override fun retainedDuplicate(): ByteBuf =
        retainedSlice(0, capacity()).setIndex(readerIndex(), writerIndex())

    override fun getByte(index: Int): Byte {
        checkIndex(index, 1)
        return _getByte(index)
    }

    override fun _getByte(index: Int): Byte = unwrap().getByte(idx(index))

    override fun getShort(index: Int): Short {
        checkIndex(index, 2)
        return _getShort(index)
    }

    override fun _getShort(index: Int): Short = unwrap().getShort(idx(index))

    override fun getShortLE(index: Int): Short {
        checkIndex(index, 2)
        return _getShortLE(index)
    }

    override fun _getShortLE(index: Int): Short = unwrap().getShortLE(idx(index))

    override fun getUnsignedMedium(index: Int): Int {
        checkIndex(index, 3)
        return _getUnsignedMedium(index)
    }

    override fun _getUnsignedMedium(index: Int): Int = unwrap().getUnsignedMedium(idx(index))

    override fun getUnsignedMediumLE(index: Int): Int {
        checkIndex(index, 3)
        return _getUnsignedMediumLE(index)
    }

    override fun _getUnsignedMediumLE(index: Int): Int = unwrap().getUnsignedMediumLE(idx(index))

    override fun getInt(index: Int): Int {
        checkIndex(index, 4)
        return _getInt(index)
    }

    override fun _getInt(index: Int): Int = unwrap().getInt(idx(index))

    override fun getIntLE(index: Int): Int {
        checkIndex(index, 4)
        return _getIntLE(index)
    }

    override fun _getIntLE(index: Int): Int = unwrap().getIntLE(idx(index))

    override fun getLong(index: Int): Long {
        checkIndex(index, 8)
        return _getLong(index)
    }

    override fun _getLong(index: Int): Long = unwrap().getLong(idx(index))

    override fun getLongLE(index: Int): Long {
        checkIndex(index, 8)
        return _getLongLE(index)
    }

    override fun _getLongLE(index: Int): Long = unwrap().getLongLE(idx(index))

    override fun getBytes(index: Int, dst: ByteBuf, dstIndex: Int, length: Int): ByteBuf {
        checkIndex0(index, length)
        unwrap().getBytes(idx(index), dst, dstIndex, length)
        return this
    }

    override fun getBytes(index: Int, dst: ByteArray, dstIndex: Int, length: Int): ByteBuf {
        checkIndex0(index, length)
        unwrap().getBytes(idx(index), dst, dstIndex, length)
        return this
    }

    override fun getBytes(index: Int, dst: ByteBuffer): ByteBuf {
        checkIndex0(index, dst.remaining())
        unwrap().getBytes(idx(index), dst)
        return this
    }

    override fun setByte(index: Int, value: Int): ByteBuf {
        checkIndex(index, 1)
        _setByte(index, value)
        return this
    }

    override fun _setByte(index: Int, value: Int) {
        unwrap().setByte(idx(index), value)
    }

    override fun setShort(index: Int, value: Int): ByteBuf {
        checkIndex(index, 2)
        _setShort(index, value)
        return this
    }

    override fun _setShort(index: Int, value: Int) {
        unwrap().setShort(idx(index), value)
    }

    override fun setShortLE(index: Int, value: Int): ByteBuf {
        checkIndex(index, 2)
        _setShortLE(index, value)
        return this
    }

    override fun _setShortLE(index: Int, value: Int) {
        unwrap().setShortLE(idx(index), value)
    }

    override fun setMedium(index: Int, value: Int): ByteBuf {
        checkIndex(index, 3)
        _setMedium(index, value)
        return this
    }

    override fun _setMedium(index: Int, value: Int) {
        unwrap().setMedium(idx(index), value)
    }

    override fun setMediumLE(index: Int, value: Int): ByteBuf {
        checkIndex(index, 3)
        _setMediumLE(index, value)
        return this
    }

    override fun _setMediumLE(index: Int, value: Int) {
        unwrap().setMediumLE(idx(index), value)
    }

    override fun setInt(index: Int, value: Int): ByteBuf {
        checkIndex(index, 4)
        _setInt(index, value)
        return this
    }

    override fun _setInt(index: Int, value: Int) {
        unwrap().setInt(idx(index), value)
    }

    override fun setIntLE(index: Int, value: Int): ByteBuf {
        checkIndex(index, 4)
        _setIntLE(index, value)
        return this
    }

    override fun _setIntLE(index: Int, value: Int) {
        unwrap().setIntLE(idx(index), value)
    }

    override fun setLong(index: Int, value: Long): ByteBuf {
        checkIndex(index, 8)
        _setLong(index, value)
        return this
    }

    override fun _setLong(index: Int, value: Long) {
        unwrap().setLong(idx(index), value)
    }

    override fun setLongLE(index: Int, value: Long): ByteBuf {
        checkIndex(index, 8)
        _setLongLE(index, value)
        return this
    }

    override fun _setLongLE(index: Int, value: Long) {
        unwrap().setLongLE(idx(index), value)
    }

    override fun setBytes(index: Int, src: ByteArray, srcIndex: Int, length: Int): ByteBuf {
        checkIndex0(index, length)
        unwrap().setBytes(idx(index), src, srcIndex, length)
        return this
    }

    override fun setBytes(index: Int, src: ByteBuf, srcIndex: Int, length: Int): ByteBuf {
        checkIndex0(index, length)
        unwrap().setBytes(idx(index), src, srcIndex, length)
        return this
    }

    override fun setBytes(index: Int, src: ByteBuffer): ByteBuf {
        checkIndex0(index, src.remaining())
        unwrap().setBytes(idx(index), src)
        return this
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: OutputStream, length: Int): ByteBuf {
        checkIndex0(index, length)
        unwrap().getBytes(idx(index), out, length)
        return this
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: GatheringByteChannel, length: Int): Int {
        checkIndex0(index, length)
        return unwrap().getBytes(idx(index), out, length)
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: FileChannel, position: Long, length: Int): Int {
        checkIndex0(index, length)
        return unwrap().getBytes(idx(index), out, position, length)
    }

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: InputStream, length: Int): Int {
        checkIndex0(index, length)
        return unwrap().setBytes(idx(index), `in`, length)
    }

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: ScatteringByteChannel, length: Int): Int {
        checkIndex0(index, length)
        return unwrap().setBytes(idx(index), `in`, length)
    }

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: FileChannel, position: Long, length: Int): Int {
        checkIndex0(index, length)
        return unwrap().setBytes(idx(index), `in`, position, length)
    }

    override fun forEachByte(index: Int, length: Int, processor: ByteProcessor): Int {
        checkIndex0(index, length)
        val ret = unwrap().forEachByte(idx(index), length, processor)
        return if (ret < adjustment) -1 else ret - adjustment
    }

    override fun forEachByteDesc(index: Int, length: Int, processor: ByteProcessor): Int {
        checkIndex0(index, length)
        val ret = unwrap().forEachByteDesc(idx(index), length, processor)
        return if (ret < adjustment) -1 else ret - adjustment
    }

    private fun idx(index: Int): Int = index + adjustment

    companion object {
        private val RECYCLER = object : Recycler<PooledSlicedByteBuf>() {
            override fun newObject(handle: Handle<PooledSlicedByteBuf>): PooledSlicedByteBuf {
                return PooledSlicedByteBuf(handle)
            }
        }

        @JvmStatic
        fun newInstance(unwrapped: AbstractByteBuf, wrapped: ByteBuf,
                        index: Int, length: Int): PooledSlicedByteBuf {
            AbstractUnpooledSlicedByteBuf.checkSliceOutOfBounds(index, length, unwrapped)
            return newInstance0(unwrapped, wrapped, index, length)
        }

        @JvmStatic
        private fun newInstance0(unwrapped: AbstractByteBuf, wrapped: ByteBuf,
                                 adjustment: Int, length: Int): PooledSlicedByteBuf {
            val slice = RECYCLER.get()
            slice.init<PooledSlicedByteBuf>(unwrapped, wrapped, 0, length, length)
            slice.discardMarks()
            slice.adjustment = adjustment
            return slice
        }
    }
}
