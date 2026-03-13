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

import io.netty.util.Recycler
import io.netty.util.internal.ObjectPool.Handle
import io.netty.util.internal.PlatformDependent
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

open class PooledHeapByteBuf(
    recyclerHandle: Handle<out PooledHeapByteBuf>,
    maxCapacity: Int
) : PooledByteBuf<ByteArray>(recyclerHandle, maxCapacity) {

    final override fun isDirect(): Boolean = false

    override fun _getByte(index: Int): Byte = HeapByteBufUtil.getByte(memory!!, idx(index))

    override fun _getShort(index: Int): Short = HeapByteBufUtil.getShort(memory!!, idx(index))

    override fun _getShortLE(index: Int): Short = HeapByteBufUtil.getShortLE(memory!!, idx(index))

    override fun _getUnsignedMedium(index: Int): Int = HeapByteBufUtil.getUnsignedMedium(memory!!, idx(index))

    override fun _getUnsignedMediumLE(index: Int): Int = HeapByteBufUtil.getUnsignedMediumLE(memory!!, idx(index))

    override fun _getInt(index: Int): Int = HeapByteBufUtil.getInt(memory!!, idx(index))

    override fun _getIntLE(index: Int): Int = HeapByteBufUtil.getIntLE(memory!!, idx(index))

    override fun _getLong(index: Int): Long = HeapByteBufUtil.getLong(memory!!, idx(index))

    override fun _getLongLE(index: Int): Long = HeapByteBufUtil.getLongLE(memory!!, idx(index))

    final override fun getBytes(index: Int, dst: ByteBuf, dstIndex: Int, length: Int): ByteBuf {
        checkDstIndex(index, length, dstIndex, dst.capacity())
        if (dst.hasMemoryAddress() && PlatformDependent.hasUnsafe()) {
            PlatformDependent.copyMemory(memory!!, idx(index), dst.memoryAddress() + dstIndex, length.toLong())
        } else if (dst.hasArray()) {
            getBytes(index, dst.array(), dst.arrayOffset() + dstIndex, length)
        } else {
            dst.setBytes(dstIndex, memory!!, idx(index), length)
        }
        return this
    }

    final override fun getBytes(index: Int, dst: ByteArray, dstIndex: Int, length: Int): ByteBuf {
        checkDstIndex(index, length, dstIndex, dst.size)
        System.arraycopy(memory!!, idx(index), dst, dstIndex, length)
        return this
    }

    final override fun getBytes(index: Int, dst: ByteBuffer): ByteBuf {
        val length = dst.remaining()
        checkIndex(index, length)
        dst.put(memory!!, idx(index), length)
        return this
    }

    @Throws(IOException::class)
    final override fun getBytes(index: Int, out: OutputStream, length: Int): ByteBuf {
        checkIndex(index, length)
        out.write(memory!!, idx(index), length)
        return this
    }

    override fun _setByte(index: Int, value: Int) {
        HeapByteBufUtil.setByte(memory!!, idx(index), value)
    }

    override fun _setShort(index: Int, value: Int) {
        HeapByteBufUtil.setShort(memory!!, idx(index), value)
    }

    override fun _setShortLE(index: Int, value: Int) {
        HeapByteBufUtil.setShortLE(memory!!, idx(index), value)
    }

    override fun _setMedium(index: Int, value: Int) {
        HeapByteBufUtil.setMedium(memory!!, idx(index), value)
    }

    override fun _setMediumLE(index: Int, value: Int) {
        HeapByteBufUtil.setMediumLE(memory!!, idx(index), value)
    }

    override fun _setInt(index: Int, value: Int) {
        HeapByteBufUtil.setInt(memory!!, idx(index), value)
    }

    override fun _setIntLE(index: Int, value: Int) {
        HeapByteBufUtil.setIntLE(memory!!, idx(index), value)
    }

    override fun _setLong(index: Int, value: Long) {
        HeapByteBufUtil.setLong(memory!!, idx(index), value)
    }

    override fun _setLongLE(index: Int, value: Long) {
        HeapByteBufUtil.setLongLE(memory!!, idx(index), value)
    }

    final override fun setBytes(index: Int, src: ByteBuf, srcIndex: Int, length: Int): ByteBuf {
        checkSrcIndex(index, length, srcIndex, src.capacity())
        if (src.hasMemoryAddress() && PlatformDependent.hasUnsafe()) {
            PlatformDependent.copyMemory(src.memoryAddress() + srcIndex, memory!!, idx(index), length.toLong())
        } else if (src.hasArray()) {
            setBytes(index, src.array(), src.arrayOffset() + srcIndex, length)
        } else {
            src.getBytes(srcIndex, memory!!, idx(index), length)
        }
        return this
    }

    final override fun setBytes(index: Int, src: ByteArray, srcIndex: Int, length: Int): ByteBuf {
        checkSrcIndex(index, length, srcIndex, src.size)
        System.arraycopy(src, srcIndex, memory!!, idx(index), length)
        return this
    }

    final override fun setBytes(index: Int, src: ByteBuffer): ByteBuf {
        val length = src.remaining()
        checkIndex(index, length)
        src.get(memory!!, idx(index), length)
        return this
    }

    @Throws(IOException::class)
    final override fun setBytes(index: Int, `in`: InputStream, length: Int): Int {
        checkIndex(index, length)
        return `in`.read(memory!!, idx(index), length)
    }

    final override fun copy(index: Int, length: Int): ByteBuf {
        checkIndex(index, length)
        val copy = alloc().heapBuffer(length, maxCapacity())
        return copy.writeBytes(memory!!, idx(index), length)
    }

    final override fun duplicateInternalNioBuffer(index: Int, length: Int): ByteBuffer {
        checkIndex(index, length)
        return ByteBuffer.wrap(memory!!, idx(index), length).slice()
    }

    final override fun hasArray(): Boolean = true

    final override fun array(): ByteArray {
        ensureAccessible()
        return memory!!
    }

    final override fun arrayOffset(): Int = offset

    final override fun hasMemoryAddress(): Boolean = false

    final override fun memoryAddress(): Long = throw UnsupportedOperationException()

    override fun newInternalNioBuffer(memory: ByteArray): ByteBuffer = ByteBuffer.wrap(memory)

    companion object {
        private val RECYCLER = object : Recycler<PooledHeapByteBuf>() {
            override fun newObject(handle: Handle<PooledHeapByteBuf>): PooledHeapByteBuf {
                return PooledHeapByteBuf(handle, 0)
            }
        }

        @JvmStatic
        fun newInstance(maxCapacity: Int): PooledHeapByteBuf {
            val buf = RECYCLER.get()
            buf.reuse(maxCapacity)
            return buf
        }
    }
}
