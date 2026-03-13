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

import io.netty.util.internal.EmptyArrays
import io.netty.util.internal.RecyclableArrayList

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ReadOnlyBufferException
import java.nio.channels.FileChannel
import java.nio.channels.GatheringByteChannel
import java.nio.channels.ScatteringByteChannel
import java.util.Collections

/**
 * [ByteBuf] implementation which allows to wrap an array of [ByteBuf] in a read-only mode.
 * This is useful to write an array of [ByteBuf]s.
 */
internal class FixedCompositeByteBuf(
    private val allocator: ByteBufAllocator,
    vararg buffers: ByteBuf
) : AbstractReferenceCountedByteBuf(AbstractByteBufAllocator.DEFAULT_MAX_CAPACITY) {

    private val nioBufferCount: Int
    private val capacity: Int
    private val order: ByteOrder
    private val buffers: Array<ByteBuf>
    private val direct: Boolean

    init {
        if (buffers.isEmpty()) {
            this.buffers = EMPTY
            order = ByteOrder.BIG_ENDIAN
            nioBufferCount = 1
            capacity = 0
            direct = Unpooled.EMPTY_BUFFER.isDirect()
        } else {
            var b = buffers[0]
            this.buffers = arrayOf(*buffers)
            var direct = true
            var nioBufferCount = b.nioBufferCount()
            var capacity = b.readableBytes()
            order = b.order()
            for (i in 1 until buffers.size) {
                b = buffers[i]
                if (buffers[i].order() != order) {
                    throw IllegalArgumentException("All ByteBufs need to have same ByteOrder")
                }
                nioBufferCount += b.nioBufferCount()
                capacity += b.readableBytes()
                if (!b.isDirect()) {
                    direct = false
                }
            }
            this.nioBufferCount = nioBufferCount
            this.capacity = capacity
            this.direct = direct
        }
        setIndex(0, capacity())
    }

    override fun isWritable(): Boolean = false

    override fun isWritable(size: Int): Boolean = false

    override fun discardReadBytes(): ByteBuf = throw ReadOnlyBufferException()

    override fun setBytes(index: Int, src: ByteBuf, srcIndex: Int, length: Int): ByteBuf =
        throw ReadOnlyBufferException()

    override fun setBytes(index: Int, src: ByteArray, srcIndex: Int, length: Int): ByteBuf =
        throw ReadOnlyBufferException()

    override fun setBytes(index: Int, src: ByteBuffer): ByteBuf = throw ReadOnlyBufferException()

    override fun setByte(index: Int, value: Int): ByteBuf = throw ReadOnlyBufferException()

    override fun _setByte(index: Int, value: Int): Unit = throw ReadOnlyBufferException()

    override fun setShort(index: Int, value: Int): ByteBuf = throw ReadOnlyBufferException()

    override fun _setShort(index: Int, value: Int): Unit = throw ReadOnlyBufferException()

    override fun _setShortLE(index: Int, value: Int): Unit = throw ReadOnlyBufferException()

    override fun setMedium(index: Int, value: Int): ByteBuf = throw ReadOnlyBufferException()

    override fun _setMedium(index: Int, value: Int): Unit = throw ReadOnlyBufferException()

    override fun _setMediumLE(index: Int, value: Int): Unit = throw ReadOnlyBufferException()

    override fun setInt(index: Int, value: Int): ByteBuf = throw ReadOnlyBufferException()

    override fun _setInt(index: Int, value: Int): Unit = throw ReadOnlyBufferException()

    override fun _setIntLE(index: Int, value: Int): Unit = throw ReadOnlyBufferException()

    override fun setLong(index: Int, value: Long): ByteBuf = throw ReadOnlyBufferException()

    override fun _setLong(index: Int, value: Long): Unit = throw ReadOnlyBufferException()

    override fun _setLongLE(index: Int, value: Long): Unit = throw ReadOnlyBufferException()

    override fun setBytes(index: Int, `in`: InputStream, length: Int): Int =
        throw ReadOnlyBufferException()

    override fun setBytes(index: Int, `in`: ScatteringByteChannel, length: Int): Int =
        throw ReadOnlyBufferException()

    override fun setBytes(index: Int, `in`: FileChannel, position: Long, length: Int): Int =
        throw ReadOnlyBufferException()

    override fun capacity(): Int = capacity

    override fun maxCapacity(): Int = capacity

    override fun capacity(newCapacity: Int): ByteBuf = throw ReadOnlyBufferException()

    override fun alloc(): ByteBufAllocator = allocator

    override fun order(): ByteOrder = order

    override fun unwrap(): ByteBuf? = null

    override fun isDirect(): Boolean = direct

    private fun findComponent(index: Int): Component {
        var readable = 0
        for (i in buffers.indices) {
            var comp: Component? = null
            var b = buffers[i]
            if (b is Component) {
                comp = b
                b = comp.buf
            }
            readable += b.readableBytes()
            if (index < readable) {
                if (comp == null) {
                    // Create a new component and store it in the array so it not create a new object
                    // on the next access.
                    comp = Component(i, readable - b.readableBytes(), b)
                    buffers[i] = comp
                }
                return comp
            }
        }
        throw IllegalStateException()
    }

    /**
     * Return the [ByteBuf] stored at the given index of the array.
     */
    private fun buffer(i: Int): ByteBuf {
        val b = buffers[i]
        return if (b is Component) b.buf else b
    }

    override fun getByte(index: Int): Byte = _getByte(index)

    override fun _getByte(index: Int): Byte {
        val c = findComponent(index)
        return c.buf.getByte(index - c.offset)
    }

    override fun _getShort(index: Int): Short {
        val c = findComponent(index)
        return if (index + 2 <= c.endOffset) {
            c.buf.getShort(index - c.offset)
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            ((_getByte(index).toInt() and 0xff) shl 8 or (_getByte(index + 1).toInt() and 0xff)).toShort()
        } else {
            (_getByte(index).toInt() and 0xff or ((_getByte(index + 1).toInt() and 0xff) shl 8)).toShort()
        }
    }

    override fun _getShortLE(index: Int): Short {
        val c = findComponent(index)
        return if (index + 2 <= c.endOffset) {
            c.buf.getShortLE(index - c.offset)
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            (_getByte(index).toInt() and 0xff or ((_getByte(index + 1).toInt() and 0xff) shl 8)).toShort()
        } else {
            ((_getByte(index).toInt() and 0xff) shl 8 or (_getByte(index + 1).toInt() and 0xff)).toShort()
        }
    }

    override fun _getUnsignedMedium(index: Int): Int {
        val c = findComponent(index)
        return if (index + 3 <= c.endOffset) {
            c.buf.getUnsignedMedium(index - c.offset)
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            (_getShort(index).toInt() and 0xffff) shl 8 or (_getByte(index + 2).toInt() and 0xff)
        } else {
            _getShort(index).toInt() and 0xFFFF or ((_getByte(index + 2).toInt() and 0xFF) shl 16)
        }
    }

    override fun _getUnsignedMediumLE(index: Int): Int {
        val c = findComponent(index)
        return if (index + 3 <= c.endOffset) {
            c.buf.getUnsignedMediumLE(index - c.offset)
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            _getShortLE(index).toInt() and 0xffff or ((_getByte(index + 2).toInt() and 0xff) shl 16)
        } else {
            (_getShortLE(index).toInt() and 0xffff) shl 8 or (_getByte(index + 2).toInt() and 0xff)
        }
    }

    override fun _getInt(index: Int): Int {
        val c = findComponent(index)
        return if (index + 4 <= c.endOffset) {
            c.buf.getInt(index - c.offset)
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            (_getShort(index).toInt() and 0xffff) shl 16 or (_getShort(index + 2).toInt() and 0xffff)
        } else {
            _getShort(index).toInt() and 0xFFFF or ((_getShort(index + 2).toInt() and 0xFFFF) shl 16)
        }
    }

    override fun _getIntLE(index: Int): Int {
        val c = findComponent(index)
        return if (index + 4 <= c.endOffset) {
            c.buf.getIntLE(index - c.offset)
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            _getShortLE(index).toInt() and 0xFFFF or ((_getShortLE(index + 2).toInt() and 0xFFFF) shl 16)
        } else {
            (_getShortLE(index).toInt() and 0xffff) shl 16 or (_getShortLE(index + 2).toInt() and 0xffff)
        }
    }

    override fun _getLong(index: Int): Long {
        val c = findComponent(index)
        return if (index + 8 <= c.endOffset) {
            c.buf.getLong(index - c.offset)
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            (_getInt(index).toLong() and 0xffffffffL) shl 32 or (_getInt(index + 4).toLong() and 0xffffffffL)
        } else {
            _getInt(index).toLong() and 0xFFFFFFFFL or ((_getInt(index + 4).toLong() and 0xFFFFFFFFL) shl 32)
        }
    }

    override fun _getLongLE(index: Int): Long {
        val c = findComponent(index)
        return if (index + 8 <= c.endOffset) {
            c.buf.getLongLE(index - c.offset)
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            _getIntLE(index).toLong() and 0xffffffffL or ((_getIntLE(index + 4).toLong() and 0xffffffffL) shl 32)
        } else {
            (_getIntLE(index).toLong() and 0xffffffffL) shl 32 or (_getIntLE(index + 4).toLong() and 0xffffffffL)
        }
    }

    override fun getBytes(index: Int, dst: ByteArray, dstIndex: Int, length: Int): ByteBuf {
        checkDstIndex(index, length, dstIndex, dst.size)
        if (length == 0) {
            return this
        }

        var idx = index
        var dstIdx = dstIndex
        var remaining = length
        val c = findComponent(idx)
        var i = c.index
        var adjustment = c.offset
        var s = c.buf
        while (true) {
            val localLength = Math.min(remaining, s.readableBytes() - (idx - adjustment))
            s.getBytes(idx - adjustment, dst, dstIdx, localLength)
            idx += localLength
            dstIdx += localLength
            remaining -= localLength
            adjustment += s.readableBytes()
            if (remaining <= 0) {
                break
            }
            s = buffer(++i)
        }
        return this
    }

    override fun getBytes(index: Int, dst: ByteBuffer): ByteBuf {
        val limit = dst.limit()
        val length = dst.remaining()

        checkIndex(index, length)
        if (length == 0) {
            return this
        }

        try {
            var idx = index
            var remaining = length
            val c = findComponent(idx)
            var i = c.index
            var adjustment = c.offset
            var s = c.buf
            while (true) {
                val localLength = Math.min(remaining, s.readableBytes() - (idx - adjustment))
                dst.limit(dst.position() + localLength)
                s.getBytes(idx - adjustment, dst)
                idx += localLength
                remaining -= localLength
                adjustment += s.readableBytes()
                if (remaining <= 0) {
                    break
                }
                s = buffer(++i)
            }
        } finally {
            dst.limit(limit)
        }
        return this
    }

    override fun getBytes(index: Int, dst: ByteBuf, dstIndex: Int, length: Int): ByteBuf {
        checkDstIndex(index, length, dstIndex, dst.capacity())
        if (length == 0) {
            return this
        }

        var idx = index
        var dstIdx = dstIndex
        var remaining = length
        val c = findComponent(idx)
        var i = c.index
        var adjustment = c.offset
        var s = c.buf
        while (true) {
            val localLength = Math.min(remaining, s.readableBytes() - (idx - adjustment))
            s.getBytes(idx - adjustment, dst, dstIdx, localLength)
            idx += localLength
            dstIdx += localLength
            remaining -= localLength
            adjustment += s.readableBytes()
            if (remaining <= 0) {
                break
            }
            s = buffer(++i)
        }
        return this
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: GatheringByteChannel, length: Int): Int {
        val count = nioBufferCount()
        if (count == 1) {
            return out.write(internalNioBuffer(index, length))
        } else {
            val writtenBytes = out.write(nioBuffers(index, length))
            return if (writtenBytes > Int.MAX_VALUE) {
                Int.MAX_VALUE
            } else {
                writtenBytes.toInt()
            }
        }
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: FileChannel, position: Long, length: Int): Int {
        val count = nioBufferCount()
        if (count == 1) {
            return out.write(internalNioBuffer(index, length), position)
        } else {
            var writtenBytes: Long = 0
            for (buf in nioBuffers(index, length)) {
                writtenBytes += out.write(buf, position + writtenBytes)
            }
            return if (writtenBytes > Int.MAX_VALUE) {
                Int.MAX_VALUE
            } else {
                writtenBytes.toInt()
            }
        }
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: OutputStream, length: Int): ByteBuf {
        checkIndex(index, length)
        if (length == 0) {
            return this
        }

        var idx = index
        var remaining = length
        val c = findComponent(idx)
        var i = c.index
        var adjustment = c.offset
        var s = c.buf
        while (true) {
            val localLength = Math.min(remaining, s.readableBytes() - (idx - adjustment))
            s.getBytes(idx - adjustment, out, localLength)
            idx += localLength
            remaining -= localLength
            adjustment += s.readableBytes()
            if (remaining <= 0) {
                break
            }
            s = buffer(++i)
        }
        return this
    }

    override fun copy(index: Int, length: Int): ByteBuf {
        checkIndex(index, length)
        var release = true
        val buf = alloc().buffer(length)
        try {
            buf.writeBytes(this, index, length)
            release = false
            return buf
        } finally {
            if (release) {
                buf.release()
            }
        }
    }

    override fun nioBufferCount(): Int = nioBufferCount

    override fun nioBuffer(index: Int, length: Int): ByteBuffer {
        checkIndex(index, length)
        if (buffers.size == 1) {
            val buf = buffer(0)
            if (buf.nioBufferCount() == 1) {
                return buf.nioBuffer(index, length)
            }
        }
        val merged = ByteBuffer.allocate(length).order(order())
        val buffers = nioBuffers(index, length)

        for (i in buffers.indices) {
            merged.put(buffers[i])
        }

        merged.flip()
        return merged
    }

    override fun internalNioBuffer(index: Int, length: Int): ByteBuffer {
        if (buffers.size == 1) {
            return buffer(0).internalNioBuffer(index, length)
        }
        throw UnsupportedOperationException()
    }

    override fun nioBuffers(index: Int, length: Int): Array<ByteBuffer> {
        checkIndex(index, length)
        if (length == 0) {
            return EmptyArrays.EMPTY_BYTE_BUFFERS
        }

        val array = RecyclableArrayList.newInstance(buffers.size)
        try {
            var idx = index
            var remaining = length
            val c = findComponent(idx)
            var i = c.index
            var adjustment = c.offset
            var s = c.buf
            while (true) {
                val localLength = Math.min(remaining, s.readableBytes() - (idx - adjustment))
                when (s.nioBufferCount()) {
                    0 -> throw UnsupportedOperationException()
                    1 -> array.add(s.nioBuffer(idx - adjustment, localLength))
                    else -> Collections.addAll(array, *s.nioBuffers(idx - adjustment, localLength))
                }

                idx += localLength
                remaining -= localLength
                adjustment += s.readableBytes()
                if (remaining <= 0) {
                    break
                }
                s = buffer(++i)
            }

            @Suppress("UNCHECKED_CAST")
            return array.toArray(EmptyArrays.EMPTY_BYTE_BUFFERS) as Array<ByteBuffer>
        } finally {
            array.recycle()
        }
    }

    override fun hasArray(): Boolean {
        return when (buffers.size) {
            0 -> true
            1 -> buffer(0).hasArray()
            else -> false
        }
    }

    override fun array(): ByteArray {
        return when (buffers.size) {
            0 -> EmptyArrays.EMPTY_BYTES
            1 -> buffer(0).array()
            else -> throw UnsupportedOperationException()
        }
    }

    override fun arrayOffset(): Int {
        return when (buffers.size) {
            0 -> 0
            1 -> buffer(0).arrayOffset()
            else -> throw UnsupportedOperationException()
        }
    }

    override fun hasMemoryAddress(): Boolean {
        return when (buffers.size) {
            0 -> Unpooled.EMPTY_BUFFER.hasMemoryAddress()
            1 -> buffer(0).hasMemoryAddress()
            else -> false
        }
    }

    override fun memoryAddress(): Long {
        return when (buffers.size) {
            0 -> Unpooled.EMPTY_BUFFER.memoryAddress()
            1 -> buffer(0).memoryAddress()
            else -> throw UnsupportedOperationException()
        }
    }

    override fun deallocate() {
        for (i in buffers.indices) {
            buffer(i).release()
        }
    }

    override fun toString(): String {
        var result = super.toString()
        result = result.substring(0, result.length - 1)
        return "$result, components=${buffers.size})"
    }

    private class Component(
        val index: Int,
        val offset: Int,
        buf: ByteBuf
    ) : WrappedByteBuf(buf) {
        val endOffset: Int = offset + buf.readableBytes()
    }

    companion object {
        private val EMPTY: Array<ByteBuf> = arrayOf(Unpooled.EMPTY_BUFFER)
    }
}
