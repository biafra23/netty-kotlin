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
import io.netty.util.internal.PlatformDependent.BIG_ENDIAN_NATIVE_ORDER
import java.nio.ByteOrder

/**
 * Special [SwappedByteBuf] for [ByteBuf]s that is using unsafe.
 */
internal abstract class AbstractUnsafeSwappedByteBuf(buf: AbstractByteBuf) : SwappedByteBuf(buf) {
    private val nativeByteOrder: Boolean
    private val wrapped: AbstractByteBuf

    init {
        assert(PlatformDependent.isUnaligned())
        wrapped = buf
        nativeByteOrder = BIG_ENDIAN_NATIVE_ORDER == (order() == ByteOrder.BIG_ENDIAN)
    }

    override fun getLong(index: Int): Long {
        wrapped.checkIndex(index, 8)
        val v = _getLong(wrapped, index)
        return if (nativeByteOrder) v else java.lang.Long.reverseBytes(v)
    }

    override fun getFloat(index: Int): Float {
        return java.lang.Float.intBitsToFloat(getInt(index))
    }

    override fun getDouble(index: Int): Double {
        return java.lang.Double.longBitsToDouble(getLong(index))
    }

    override fun getChar(index: Int): Char {
        return getShort(index).toInt().toChar()
    }

    override fun getUnsignedInt(index: Int): Long {
        return getInt(index).toLong() and 0xFFFFFFFFL
    }

    override fun getInt(index: Int): Int {
        wrapped.checkIndex(index, 4)
        val v = _getInt(wrapped, index)
        return if (nativeByteOrder) v else Integer.reverseBytes(v)
    }

    override fun getUnsignedShort(index: Int): Int {
        return getShort(index).toInt() and 0xFFFF
    }

    override fun getShort(index: Int): Short {
        wrapped.checkIndex(index, 2)
        val v = _getShort(wrapped, index)
        return if (nativeByteOrder) v else java.lang.Short.reverseBytes(v)
    }

    override fun setShort(index: Int, value: Int): ByteBuf {
        wrapped.checkIndex(index, 2)
        _setShort(wrapped, index, if (nativeByteOrder) value.toShort() else java.lang.Short.reverseBytes(value.toShort()))
        return this
    }

    override fun setInt(index: Int, value: Int): ByteBuf {
        wrapped.checkIndex(index, 4)
        _setInt(wrapped, index, if (nativeByteOrder) value else Integer.reverseBytes(value))
        return this
    }

    override fun setLong(index: Int, value: Long): ByteBuf {
        wrapped.checkIndex(index, 8)
        _setLong(wrapped, index, if (nativeByteOrder) value else java.lang.Long.reverseBytes(value))
        return this
    }

    override fun setChar(index: Int, value: Int): ByteBuf {
        setShort(index, value)
        return this
    }

    override fun setFloat(index: Int, value: Float): ByteBuf {
        setInt(index, java.lang.Float.floatToRawIntBits(value))
        return this
    }

    override fun setDouble(index: Int, value: Double): ByteBuf {
        setLong(index, java.lang.Double.doubleToRawLongBits(value))
        return this
    }

    override fun writeShort(value: Int): ByteBuf {
        wrapped.ensureWritable0(2)
        _setShort(wrapped, wrapped.writerIndex, if (nativeByteOrder) value.toShort() else java.lang.Short.reverseBytes(value.toShort()))
        wrapped.writerIndex += 2
        return this
    }

    override fun writeInt(value: Int): ByteBuf {
        wrapped.ensureWritable0(4)
        _setInt(wrapped, wrapped.writerIndex, if (nativeByteOrder) value else Integer.reverseBytes(value))
        wrapped.writerIndex += 4
        return this
    }

    override fun writeLong(value: Long): ByteBuf {
        wrapped.ensureWritable0(8)
        _setLong(wrapped, wrapped.writerIndex, if (nativeByteOrder) value else java.lang.Long.reverseBytes(value))
        wrapped.writerIndex += 8
        return this
    }

    override fun writeChar(value: Int): ByteBuf {
        writeShort(value)
        return this
    }

    override fun writeFloat(value: Float): ByteBuf {
        writeInt(java.lang.Float.floatToRawIntBits(value))
        return this
    }

    override fun writeDouble(value: Double): ByteBuf {
        writeLong(java.lang.Double.doubleToRawLongBits(value))
        return this
    }

    protected abstract fun _getShort(wrapped: AbstractByteBuf, index: Int): Short
    protected abstract fun _getInt(wrapped: AbstractByteBuf, index: Int): Int
    protected abstract fun _getLong(wrapped: AbstractByteBuf, index: Int): Long
    protected abstract fun _setShort(wrapped: AbstractByteBuf, index: Int, value: Short)
    protected abstract fun _setInt(wrapped: AbstractByteBuf, index: Int, value: Int)
    protected abstract fun _setLong(wrapped: AbstractByteBuf, index: Int, value: Long)
}
