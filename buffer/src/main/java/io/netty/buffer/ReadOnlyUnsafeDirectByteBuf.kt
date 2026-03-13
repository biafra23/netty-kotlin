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

import io.netty.util.internal.ObjectUtil
import io.netty.util.internal.PlatformDependent
import java.nio.ByteBuffer

/**
 * Read-only ByteBuf which wraps a read-only direct ByteBuffer and use unsafe for best performance.
 */
internal class ReadOnlyUnsafeDirectByteBuf(
    allocator: ByteBufAllocator,
    byteBuffer: ByteBuffer
) : ReadOnlyByteBufferBuf(allocator, byteBuffer) {

    private val memoryAddress: Long = PlatformDependent.directBufferAddress(buffer)

    override fun _getByte(index: Int): Byte {
        return UnsafeByteBufUtil.getByte(addr(index))
    }

    override fun _getShort(index: Int): Short {
        return UnsafeByteBufUtil.getShort(addr(index))
    }

    override fun _getUnsignedMedium(index: Int): Int {
        return UnsafeByteBufUtil.getUnsignedMedium(addr(index))
    }

    override fun _getInt(index: Int): Int {
        return UnsafeByteBufUtil.getInt(addr(index))
    }

    override fun _getLong(index: Int): Long {
        return UnsafeByteBufUtil.getLong(addr(index))
    }

    override fun getBytes(index: Int, dst: ByteBuf, dstIndex: Int, length: Int, internal: Boolean): ByteBuf {
        checkIndex(index, length)
        ObjectUtil.checkNotNull(dst, "dst")
        if (dstIndex < 0 || dstIndex > dst.capacity() - length) {
            throw IndexOutOfBoundsException("dstIndex: $dstIndex")
        }

        if (dst.hasMemoryAddress()) {
            PlatformDependent.copyMemory(addr(index), dst.memoryAddress() + dstIndex, length.toLong())
        } else if (dst.hasArray()) {
            PlatformDependent.copyMemory(addr(index), dst.array(), dst.arrayOffset() + dstIndex, length.toLong())
        } else {
            dst.setBytes(dstIndex, this, index, length)
        }
        return this
    }

    override fun getBytes(index: Int, dst: ByteArray, dstIndex: Int, length: Int, internal: Boolean): ByteBuf {
        checkIndex(index, length)
        ObjectUtil.checkNotNull(dst, "dst")
        if (dstIndex < 0 || dstIndex > dst.size - length) {
            throw IndexOutOfBoundsException(
                "dstIndex: $dstIndex, length: $length (expected: range(0, ${dst.size}))"
            )
        }

        if (length != 0) {
            PlatformDependent.copyMemory(addr(index), dst, dstIndex, length.toLong())
        }
        return this
    }

    override fun copy(index: Int, length: Int): ByteBuf {
        checkIndex(index, length)
        val copy = alloc().directBuffer(length, maxCapacity())
        if (length != 0) {
            if (copy.hasMemoryAddress()) {
                PlatformDependent.copyMemory(addr(index), copy.memoryAddress(), length.toLong())
                copy.setIndex(0, length)
            } else {
                copy.writeBytes(this, index, length)
            }
        }
        return copy
    }

    override fun hasMemoryAddress(): Boolean = true

    override fun memoryAddress(): Long = memoryAddress

    private fun addr(index: Int): Long = memoryAddress + index
}
