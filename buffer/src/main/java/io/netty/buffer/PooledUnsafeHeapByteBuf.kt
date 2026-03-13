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

import io.netty.util.Recycler
import io.netty.util.internal.ObjectPool.Handle
import io.netty.util.internal.PlatformDependent

internal class PooledUnsafeHeapByteBuf(
    recyclerHandle: Handle<out PooledUnsafeHeapByteBuf>,
    maxCapacity: Int
) : PooledHeapByteBuf(recyclerHandle, maxCapacity) {

    override fun _getByte(index: Int): Byte = UnsafeByteBufUtil.getByte(memory!!, idx(index))

    override fun _getShort(index: Int): Short = UnsafeByteBufUtil.getShort(memory!!, idx(index))

    override fun _getShortLE(index: Int): Short = UnsafeByteBufUtil.getShortLE(memory!!, idx(index))

    override fun _getUnsignedMedium(index: Int): Int = UnsafeByteBufUtil.getUnsignedMedium(memory!!, idx(index))

    override fun _getUnsignedMediumLE(index: Int): Int = UnsafeByteBufUtil.getUnsignedMediumLE(memory!!, idx(index))

    override fun _getInt(index: Int): Int = UnsafeByteBufUtil.getInt(memory!!, idx(index))

    override fun _getIntLE(index: Int): Int = UnsafeByteBufUtil.getIntLE(memory!!, idx(index))

    override fun _getLong(index: Int): Long = UnsafeByteBufUtil.getLong(memory!!, idx(index))

    override fun _getLongLE(index: Int): Long = UnsafeByteBufUtil.getLongLE(memory!!, idx(index))

    override fun _setByte(index: Int, value: Int) {
        UnsafeByteBufUtil.setByte(memory!!, idx(index), value)
    }

    override fun _setShort(index: Int, value: Int) {
        UnsafeByteBufUtil.setShort(memory!!, idx(index), value)
    }

    override fun _setShortLE(index: Int, value: Int) {
        UnsafeByteBufUtil.setShortLE(memory!!, idx(index), value)
    }

    override fun _setMedium(index: Int, value: Int) {
        UnsafeByteBufUtil.setMedium(memory!!, idx(index), value)
    }

    override fun _setMediumLE(index: Int, value: Int) {
        UnsafeByteBufUtil.setMediumLE(memory!!, idx(index), value)
    }

    override fun _setInt(index: Int, value: Int) {
        UnsafeByteBufUtil.setInt(memory!!, idx(index), value)
    }

    override fun _setIntLE(index: Int, value: Int) {
        UnsafeByteBufUtil.setIntLE(memory!!, idx(index), value)
    }

    override fun _setLong(index: Int, value: Long) {
        UnsafeByteBufUtil.setLong(memory!!, idx(index), value)
    }

    override fun _setLongLE(index: Int, value: Long) {
        UnsafeByteBufUtil.setLongLE(memory!!, idx(index), value)
    }

    override fun setZero(index: Int, length: Int): ByteBuf {
        checkIndex(index, length)
        UnsafeByteBufUtil.setZero(memory!!, idx(index), length)
        return this
    }

    override fun writeZero(length: Int): ByteBuf {
        ensureWritable(length)
        val wIndex = writerIndex
        UnsafeByteBufUtil.setZero(memory!!, idx(wIndex), length)
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

    companion object {
        private val RECYCLER = object : Recycler<PooledUnsafeHeapByteBuf>() {
            override fun newObject(handle: Handle<PooledUnsafeHeapByteBuf>): PooledUnsafeHeapByteBuf {
                return PooledUnsafeHeapByteBuf(handle, 0)
            }
        }

        @JvmStatic
        fun newUnsafeInstance(maxCapacity: Int): PooledUnsafeHeapByteBuf {
            val buf = RECYCLER.get()
            buf.reuse(maxCapacity)
            return buf
        }
    }
}
