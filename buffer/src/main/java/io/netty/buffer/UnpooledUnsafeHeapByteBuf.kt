/*
 * Copyright 2015 The Netty Project
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

import io.netty.util.internal.PlatformDependent

/**
 * Big endian Java heap buffer implementation. It is recommended to use
 * [UnpooledByteBufAllocator.heapBuffer], [Unpooled.buffer] and
 * [Unpooled.wrappedBuffer] instead of calling the constructor explicitly.
 */
internal open class UnpooledUnsafeHeapByteBuf(
    alloc: ByteBufAllocator, initialCapacity: Int, maxCapacity: Int
) : UnpooledHeapByteBuf(alloc, initialCapacity, maxCapacity) {

    override fun allocateArray(initialCapacity: Int): ByteArray {
        return PlatformDependent.allocateUninitializedArray(initialCapacity)
    }

    override fun getByte(index: Int): Byte {
        checkIndex(index)
        return _getByte(index)
    }

    override fun _getByte(index: Int): Byte = UnsafeByteBufUtil.getByte(array, index)

    override fun getShort(index: Int): Short {
        checkIndex(index, 2)
        return _getShort(index)
    }

    override fun _getShort(index: Int): Short = UnsafeByteBufUtil.getShort(array, index)

    override fun getShortLE(index: Int): Short {
        checkIndex(index, 2)
        return _getShortLE(index)
    }

    override fun _getShortLE(index: Int): Short = UnsafeByteBufUtil.getShortLE(array, index)

    override fun getUnsignedMedium(index: Int): Int {
        checkIndex(index, 3)
        return _getUnsignedMedium(index)
    }

    override fun _getUnsignedMedium(index: Int): Int = UnsafeByteBufUtil.getUnsignedMedium(array, index)

    override fun getUnsignedMediumLE(index: Int): Int {
        checkIndex(index, 3)
        return _getUnsignedMediumLE(index)
    }

    override fun _getUnsignedMediumLE(index: Int): Int = UnsafeByteBufUtil.getUnsignedMediumLE(array, index)

    override fun getInt(index: Int): Int {
        checkIndex(index, 4)
        return _getInt(index)
    }

    override fun _getInt(index: Int): Int = UnsafeByteBufUtil.getInt(array, index)

    override fun getIntLE(index: Int): Int {
        checkIndex(index, 4)
        return _getIntLE(index)
    }

    override fun _getIntLE(index: Int): Int = UnsafeByteBufUtil.getIntLE(array, index)

    override fun getLong(index: Int): Long {
        checkIndex(index, 8)
        return _getLong(index)
    }

    override fun _getLong(index: Int): Long = UnsafeByteBufUtil.getLong(array, index)

    override fun getLongLE(index: Int): Long {
        checkIndex(index, 8)
        return _getLongLE(index)
    }

    override fun _getLongLE(index: Int): Long = UnsafeByteBufUtil.getLongLE(array, index)

    override fun setByte(index: Int, value: Int): ByteBuf {
        checkIndex(index)
        _setByte(index, value)
        return this
    }

    override fun _setByte(index: Int, value: Int) {
        UnsafeByteBufUtil.setByte(array, index, value)
    }

    override fun setShort(index: Int, value: Int): ByteBuf {
        checkIndex(index, 2)
        _setShort(index, value)
        return this
    }

    override fun _setShort(index: Int, value: Int) {
        UnsafeByteBufUtil.setShort(array, index, value)
    }

    override fun setShortLE(index: Int, value: Int): ByteBuf {
        checkIndex(index, 2)
        _setShortLE(index, value)
        return this
    }

    override fun _setShortLE(index: Int, value: Int) {
        UnsafeByteBufUtil.setShortLE(array, index, value)
    }

    override fun setMedium(index: Int, value: Int): ByteBuf {
        checkIndex(index, 3)
        _setMedium(index, value)
        return this
    }

    override fun _setMedium(index: Int, value: Int) {
        UnsafeByteBufUtil.setMedium(array, index, value)
    }

    override fun setMediumLE(index: Int, value: Int): ByteBuf {
        checkIndex(index, 3)
        _setMediumLE(index, value)
        return this
    }

    override fun _setMediumLE(index: Int, value: Int) {
        UnsafeByteBufUtil.setMediumLE(array, index, value)
    }

    override fun setInt(index: Int, value: Int): ByteBuf {
        checkIndex(index, 4)
        _setInt(index, value)
        return this
    }

    override fun _setInt(index: Int, value: Int) {
        UnsafeByteBufUtil.setInt(array, index, value)
    }

    override fun setIntLE(index: Int, value: Int): ByteBuf {
        checkIndex(index, 4)
        _setIntLE(index, value)
        return this
    }

    override fun _setIntLE(index: Int, value: Int) {
        UnsafeByteBufUtil.setIntLE(array, index, value)
    }

    override fun setLong(index: Int, value: Long): ByteBuf {
        checkIndex(index, 8)
        _setLong(index, value)
        return this
    }

    override fun _setLong(index: Int, value: Long) {
        UnsafeByteBufUtil.setLong(array, index, value)
    }

    override fun setLongLE(index: Int, value: Long): ByteBuf {
        checkIndex(index, 8)
        _setLongLE(index, value)
        return this
    }

    override fun _setLongLE(index: Int, value: Long) {
        UnsafeByteBufUtil.setLongLE(array, index, value)
    }

    override fun setZero(index: Int, length: Int): ByteBuf {
        checkIndex(index, length)
        UnsafeByteBufUtil.setZero(array, index, length)
        return this
    }

    override fun writeZero(length: Int): ByteBuf {
        ensureWritable(length)
        val wIndex = writerIndex
        UnsafeByteBufUtil.setZero(array, wIndex, length)
        writerIndex = wIndex + length
        return this
    }

    @Deprecated("Deprecated in Java")
    override fun newSwappedByteBuf(): SwappedByteBuf {
        if (PlatformDependent.isUnaligned()) {
            // Only use if unaligned access is supported otherwise there is no gain.
            return UnsafeHeapSwappedByteBuf(this)
        }
        return super.newSwappedByteBuf()
    }
}
