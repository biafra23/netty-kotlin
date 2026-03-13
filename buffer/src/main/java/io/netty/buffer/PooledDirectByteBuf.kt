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
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

internal class PooledDirectByteBuf private constructor(
    recyclerHandle: Handle<PooledDirectByteBuf>,
    maxCapacity: Int
) : PooledByteBuf<ByteBuffer>(recyclerHandle, maxCapacity) {

    override fun newInternalNioBuffer(memory: ByteBuffer): ByteBuffer = memory.duplicate()

    override fun isDirect(): Boolean = true

    override fun _getByte(index: Int): Byte = memory!!.get(idx(index))

    override fun _getShort(index: Int): Short = memory!!.getShort(idx(index))

    override fun _getShortLE(index: Int): Short = ByteBufUtil.swapShort(_getShort(index))

    override fun _getUnsignedMedium(index: Int): Int {
        val index = idx(index)
        return (memory!!.get(index).toInt() and 0xff shl 16) or
               (memory!!.get(index + 1).toInt() and 0xff shl 8) or
               (memory!!.get(index + 2).toInt() and 0xff)
    }

    override fun _getUnsignedMediumLE(index: Int): Int {
        val index = idx(index)
        return (memory!!.get(index).toInt() and 0xff) or
               (memory!!.get(index + 1).toInt() and 0xff shl 8) or
               (memory!!.get(index + 2).toInt() and 0xff shl 16)
    }

    override fun _getInt(index: Int): Int = memory!!.getInt(idx(index))

    override fun _getIntLE(index: Int): Int = ByteBufUtil.swapInt(_getInt(index))

    override fun _getLong(index: Int): Long = memory!!.getLong(idx(index))

    override fun _getLongLE(index: Int): Long = ByteBufUtil.swapLong(_getLong(index))

    override fun getBytes(index: Int, dst: ByteBuf, dstIndex: Int, length: Int): ByteBuf {
        checkDstIndex(index, length, dstIndex, dst.capacity())
        if (dst.hasArray()) {
            getBytes(index, dst.array(), dst.arrayOffset() + dstIndex, length)
        } else if (dst.nioBufferCount() > 0) {
            var index = index
            for (bb in dst.nioBuffers(dstIndex, length)) {
                val bbLen = bb.remaining()
                getBytes(index, bb)
                index += bbLen
            }
        } else {
            dst.setBytes(dstIndex, this, index, length)
        }
        return this
    }

    override fun getBytes(index: Int, dst: ByteArray, dstIndex: Int, length: Int): ByteBuf {
        checkDstIndex(index, length, dstIndex, dst.size)
        _internalNioBuffer(index, length, true).get(dst, dstIndex, length)
        return this
    }

    override fun readBytes(dst: ByteArray, dstIndex: Int, length: Int): ByteBuf {
        checkDstIndex(length, dstIndex, dst.size)
        _internalNioBuffer(readerIndex, length, false).get(dst, dstIndex, length)
        readerIndex += length
        return this
    }

    override fun getBytes(index: Int, dst: ByteBuffer): ByteBuf {
        dst.put(duplicateInternalNioBuffer(index, dst.remaining()))
        return this
    }

    override fun readBytes(dst: ByteBuffer): ByteBuf {
        val length = dst.remaining()
        checkReadableBytes(length)
        dst.put(_internalNioBuffer(readerIndex, length, false))
        readerIndex += length
        return this
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: OutputStream, length: Int): ByteBuf {
        getBytes(index, out, length, false)
        return this
    }

    @Throws(IOException::class)
    private fun getBytes(index: Int, out: OutputStream, length: Int, internal: Boolean) {
        checkIndex(index, length)
        if (length == 0) {
            return
        }
        ByteBufUtil.readBytes(alloc(), if (internal) internalNioBuffer() else memory!!.duplicate(), idx(index), length, out)
    }

    @Throws(IOException::class)
    override fun readBytes(out: OutputStream, length: Int): ByteBuf {
        checkReadableBytes(length)
        getBytes(readerIndex, out, length, true)
        readerIndex += length
        return this
    }

    override fun _setByte(index: Int, value: Int) {
        memory!!.put(idx(index), value.toByte())
    }

    override fun _setShort(index: Int, value: Int) {
        memory!!.putShort(idx(index), value.toShort())
    }

    override fun _setShortLE(index: Int, value: Int) {
        _setShort(index, ByteBufUtil.swapShort(value.toShort()).toInt())
    }

    override fun _setMedium(index: Int, value: Int) {
        val index = idx(index)
        memory!!.put(index, (value ushr 16).toByte())
        memory!!.put(index + 1, (value ushr 8).toByte())
        memory!!.put(index + 2, value.toByte())
    }

    override fun _setMediumLE(index: Int, value: Int) {
        val index = idx(index)
        memory!!.put(index, value.toByte())
        memory!!.put(index + 1, (value ushr 8).toByte())
        memory!!.put(index + 2, (value ushr 16).toByte())
    }

    override fun _setInt(index: Int, value: Int) {
        memory!!.putInt(idx(index), value)
    }

    override fun _setIntLE(index: Int, value: Int) {
        _setInt(index, ByteBufUtil.swapInt(value))
    }

    override fun _setLong(index: Int, value: Long) {
        memory!!.putLong(idx(index), value)
    }

    override fun _setLongLE(index: Int, value: Long) {
        _setLong(index, ByteBufUtil.swapLong(value))
    }

    override fun setBytes(index: Int, src: ByteBuf, srcIndex: Int, length: Int): ByteBuf {
        checkSrcIndex(index, length, srcIndex, src.capacity())
        if (src.hasArray()) {
            setBytes(index, src.array(), src.arrayOffset() + srcIndex, length)
        } else if (src.nioBufferCount() > 0) {
            var index = index
            for (bb in src.nioBuffers(srcIndex, length)) {
                val bbLen = bb.remaining()
                setBytes(index, bb)
                index += bbLen
            }
        } else {
            src.getBytes(srcIndex, this, index, length)
        }
        return this
    }

    override fun setBytes(index: Int, src: ByteArray, srcIndex: Int, length: Int): ByteBuf {
        checkSrcIndex(index, length, srcIndex, src.size)
        _internalNioBuffer(index, length, false).put(src, srcIndex, length)
        return this
    }

    override fun setBytes(index: Int, src: ByteBuffer): ByteBuf {
        val length = src.remaining()
        checkIndex(index, length)
        val tmpBuf = internalNioBuffer()
        val src = if (src === tmpBuf) src.duplicate() else src

        val index = idx(index)
        tmpBuf.limit(index + length).position(index)
        tmpBuf.put(src)
        return this
    }

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: InputStream, length: Int): Int {
        checkIndex(index, length)
        val tmp = ByteBufUtil.threadLocalTempArray(length)
        val readBytes = `in`.read(tmp, 0, length)
        if (readBytes <= 0) {
            return readBytes
        }
        val tmpBuf = internalNioBuffer()
        tmpBuf.position(idx(index))
        tmpBuf.put(tmp, 0, readBytes)
        return readBytes
    }

    override fun copy(index: Int, length: Int): ByteBuf {
        checkIndex(index, length)
        val copy = alloc().directBuffer(length, maxCapacity())
        return copy.writeBytes(this, index, length)
    }

    override fun hasArray(): Boolean = false

    override fun array(): ByteArray = throw UnsupportedOperationException("direct buffer")

    override fun arrayOffset(): Int = throw UnsupportedOperationException("direct buffer")

    override fun hasMemoryAddress(): Boolean {
        val chunk = this.chunk
        return chunk != null && chunk.cleanable!!.hasMemoryAddress()
    }

    override fun memoryAddress(): Long {
        ensureAccessible()
        if (!hasMemoryAddress()) {
            throw UnsupportedOperationException()
        }
        return chunk!!.cleanable!!.memoryAddress() + offset
    }

    override fun _memoryAddress(): Long =
        if (hasMemoryAddress()) chunk!!.cleanable!!.memoryAddress() + offset else 0L

    companion object {
        private val RECYCLER = object : Recycler<PooledDirectByteBuf>() {
            override fun newObject(handle: Handle<PooledDirectByteBuf>): PooledDirectByteBuf {
                return PooledDirectByteBuf(handle, 0)
            }
        }

        @JvmStatic
        fun newInstance(maxCapacity: Int): PooledDirectByteBuf {
            val buf = RECYCLER.get()
            buf.reuse(maxCapacity)
            return buf
        }
    }
}
