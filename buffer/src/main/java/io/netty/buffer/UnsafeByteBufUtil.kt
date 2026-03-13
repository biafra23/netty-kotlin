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

import io.netty.util.internal.MathUtil.isOutOfBounds
import io.netty.util.internal.ObjectUtil.checkNotNull
import io.netty.util.internal.PlatformDependent
import io.netty.util.internal.PlatformDependent.BIG_ENDIAN_NATIVE_ORDER
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ReadOnlyBufferException

/**
 * All operations get and set as [java.nio.ByteOrder.BIG_ENDIAN].
 */
internal object UnsafeByteBufUtil {
    private val UNALIGNED: Boolean = PlatformDependent.isUnaligned()
    private val USE_VAR_HANDLE: Boolean = PlatformDependent.useVarHandleForMultiByteAccess()
    private const val ZERO: Byte = 0
    private const val MAX_HAND_ROLLED_SET_ZERO_BYTES: Int = 64

    @JvmStatic
    fun getByte(address: Long): Byte {
        return PlatformDependent.getByte(address)
    }

    @JvmStatic
    fun getShort(address: Long): Short {
        if (UNALIGNED) {
            val v = PlatformDependent.getShort(address)
            return if (BIG_ENDIAN_NATIVE_ORDER) v else java.lang.Short.reverseBytes(v)
        }
        return ((PlatformDependent.getByte(address).toInt() shl 8) or
                (PlatformDependent.getByte(address + 1).toInt() and 0xff)).toShort()
    }

    @JvmStatic
    fun getShortLE(address: Long): Short {
        if (UNALIGNED) {
            val v = PlatformDependent.getShort(address)
            return if (BIG_ENDIAN_NATIVE_ORDER) java.lang.Short.reverseBytes(v) else v
        }
        return ((PlatformDependent.getByte(address).toInt() and 0xff) or
                (PlatformDependent.getByte(address + 1).toInt() shl 8)).toShort()
    }

    @JvmStatic
    fun getUnsignedMedium(address: Long): Int {
        if (UNALIGNED) {
            return ((PlatformDependent.getByte(address).toInt() and 0xff) shl 16) or
                    ((if (BIG_ENDIAN_NATIVE_ORDER) PlatformDependent.getShort(address + 1)
                    else java.lang.Short.reverseBytes(PlatformDependent.getShort(address + 1))).toInt() and 0xffff)
        }
        return ((PlatformDependent.getByte(address).toInt() and 0xff) shl 16) or
                ((PlatformDependent.getByte(address + 1).toInt() and 0xff) shl 8) or
                (PlatformDependent.getByte(address + 2).toInt() and 0xff)
    }

    @JvmStatic
    fun getUnsignedMediumLE(address: Long): Int {
        if (UNALIGNED) {
            return (PlatformDependent.getByte(address).toInt() and 0xff) or
                    (((if (BIG_ENDIAN_NATIVE_ORDER) java.lang.Short.reverseBytes(PlatformDependent.getShort(address + 1))
                    else PlatformDependent.getShort(address + 1)).toInt() and 0xffff) shl 8)
        }
        return (PlatformDependent.getByte(address).toInt() and 0xff) or
                ((PlatformDependent.getByte(address + 1).toInt() and 0xff) shl 8) or
                ((PlatformDependent.getByte(address + 2).toInt() and 0xff) shl 16)
    }

    @JvmStatic
    fun getInt(address: Long): Int {
        if (UNALIGNED) {
            val v = PlatformDependent.getInt(address)
            return if (BIG_ENDIAN_NATIVE_ORDER) v else Integer.reverseBytes(v)
        }
        return (PlatformDependent.getByte(address).toInt() shl 24) or
                ((PlatformDependent.getByte(address + 1).toInt() and 0xff) shl 16) or
                ((PlatformDependent.getByte(address + 2).toInt() and 0xff) shl 8) or
                (PlatformDependent.getByte(address + 3).toInt() and 0xff)
    }

    @JvmStatic
    fun getIntLE(address: Long): Int {
        if (UNALIGNED) {
            val v = PlatformDependent.getInt(address)
            return if (BIG_ENDIAN_NATIVE_ORDER) Integer.reverseBytes(v) else v
        }
        return (PlatformDependent.getByte(address).toInt() and 0xff) or
                ((PlatformDependent.getByte(address + 1).toInt() and 0xff) shl 8) or
                ((PlatformDependent.getByte(address + 2).toInt() and 0xff) shl 16) or
                (PlatformDependent.getByte(address + 3).toInt() shl 24)
    }

    @JvmStatic
    fun getLong(address: Long): Long {
        if (UNALIGNED) {
            val v = PlatformDependent.getLong(address)
            return if (BIG_ENDIAN_NATIVE_ORDER) v else java.lang.Long.reverseBytes(v)
        }
        return (PlatformDependent.getByte(address).toLong() shl 56) or
                ((PlatformDependent.getByte(address + 1).toLong() and 0xffL) shl 48) or
                ((PlatformDependent.getByte(address + 2).toLong() and 0xffL) shl 40) or
                ((PlatformDependent.getByte(address + 3).toLong() and 0xffL) shl 32) or
                ((PlatformDependent.getByte(address + 4).toLong() and 0xffL) shl 24) or
                ((PlatformDependent.getByte(address + 5).toLong() and 0xffL) shl 16) or
                ((PlatformDependent.getByte(address + 6).toLong() and 0xffL) shl 8) or
                (PlatformDependent.getByte(address + 7).toLong() and 0xffL)
    }

    @JvmStatic
    fun getLongLE(address: Long): Long {
        if (UNALIGNED) {
            val v = PlatformDependent.getLong(address)
            return if (BIG_ENDIAN_NATIVE_ORDER) java.lang.Long.reverseBytes(v) else v
        }
        return (PlatformDependent.getByte(address).toLong() and 0xffL) or
                ((PlatformDependent.getByte(address + 1).toLong() and 0xffL) shl 8) or
                ((PlatformDependent.getByte(address + 2).toLong() and 0xffL) shl 16) or
                ((PlatformDependent.getByte(address + 3).toLong() and 0xffL) shl 24) or
                ((PlatformDependent.getByte(address + 4).toLong() and 0xffL) shl 32) or
                ((PlatformDependent.getByte(address + 5).toLong() and 0xffL) shl 40) or
                ((PlatformDependent.getByte(address + 6).toLong() and 0xffL) shl 48) or
                (PlatformDependent.getByte(address + 7).toLong() shl 56)
    }

    @JvmStatic
    fun setByte(address: Long, value: Int) {
        PlatformDependent.putByte(address, value.toByte())
    }

    @JvmStatic
    fun setShort(address: Long, value: Int) {
        if (UNALIGNED) {
            PlatformDependent.putShort(
                address, if (BIG_ENDIAN_NATIVE_ORDER) value.toShort() else java.lang.Short.reverseBytes(value.toShort()))
        } else {
            PlatformDependent.putByte(address, (value ushr 8).toByte())
            PlatformDependent.putByte(address + 1, value.toByte())
        }
    }

    @JvmStatic
    fun setShortLE(address: Long, value: Int) {
        if (UNALIGNED) {
            PlatformDependent.putShort(
                address, if (BIG_ENDIAN_NATIVE_ORDER) java.lang.Short.reverseBytes(value.toShort()) else value.toShort())
        } else {
            PlatformDependent.putByte(address, value.toByte())
            PlatformDependent.putByte(address + 1, (value ushr 8).toByte())
        }
    }

    @JvmStatic
    fun setMedium(address: Long, value: Int) {
        PlatformDependent.putByte(address, (value ushr 16).toByte())
        if (UNALIGNED) {
            PlatformDependent.putShort(address + 1,
                if (BIG_ENDIAN_NATIVE_ORDER) value.toShort()
                else java.lang.Short.reverseBytes(value.toShort()))
        } else {
            PlatformDependent.putByte(address + 1, (value ushr 8).toByte())
            PlatformDependent.putByte(address + 2, value.toByte())
        }
    }

    @JvmStatic
    fun setMediumLE(address: Long, value: Int) {
        PlatformDependent.putByte(address, value.toByte())
        if (UNALIGNED) {
            PlatformDependent.putShort(address + 1,
                if (BIG_ENDIAN_NATIVE_ORDER) java.lang.Short.reverseBytes((value ushr 8).toShort())
                else (value ushr 8).toShort())
        } else {
            PlatformDependent.putByte(address + 1, (value ushr 8).toByte())
            PlatformDependent.putByte(address + 2, (value ushr 16).toByte())
        }
    }

    @JvmStatic
    fun setInt(address: Long, value: Int) {
        if (UNALIGNED) {
            PlatformDependent.putInt(address, if (BIG_ENDIAN_NATIVE_ORDER) value else Integer.reverseBytes(value))
        } else {
            PlatformDependent.putByte(address, (value ushr 24).toByte())
            PlatformDependent.putByte(address + 1, (value ushr 16).toByte())
            PlatformDependent.putByte(address + 2, (value ushr 8).toByte())
            PlatformDependent.putByte(address + 3, value.toByte())
        }
    }

    @JvmStatic
    fun setIntLE(address: Long, value: Int) {
        if (UNALIGNED) {
            PlatformDependent.putInt(address, if (BIG_ENDIAN_NATIVE_ORDER) Integer.reverseBytes(value) else value)
        } else {
            PlatformDependent.putByte(address, value.toByte())
            PlatformDependent.putByte(address + 1, (value ushr 8).toByte())
            PlatformDependent.putByte(address + 2, (value ushr 16).toByte())
            PlatformDependent.putByte(address + 3, (value ushr 24).toByte())
        }
    }

    @JvmStatic
    fun setLong(address: Long, value: Long) {
        if (UNALIGNED) {
            PlatformDependent.putLong(address, if (BIG_ENDIAN_NATIVE_ORDER) value else java.lang.Long.reverseBytes(value))
        } else {
            PlatformDependent.putByte(address, (value ushr 56).toByte())
            PlatformDependent.putByte(address + 1, (value ushr 48).toByte())
            PlatformDependent.putByte(address + 2, (value ushr 40).toByte())
            PlatformDependent.putByte(address + 3, (value ushr 32).toByte())
            PlatformDependent.putByte(address + 4, (value ushr 24).toByte())
            PlatformDependent.putByte(address + 5, (value ushr 16).toByte())
            PlatformDependent.putByte(address + 6, (value ushr 8).toByte())
            PlatformDependent.putByte(address + 7, value.toByte())
        }
    }

    @JvmStatic
    fun setLongLE(address: Long, value: Long) {
        if (UNALIGNED) {
            PlatformDependent.putLong(address, if (BIG_ENDIAN_NATIVE_ORDER) java.lang.Long.reverseBytes(value) else value)
        } else {
            PlatformDependent.putByte(address, value.toByte())
            PlatformDependent.putByte(address + 1, (value ushr 8).toByte())
            PlatformDependent.putByte(address + 2, (value ushr 16).toByte())
            PlatformDependent.putByte(address + 3, (value ushr 24).toByte())
            PlatformDependent.putByte(address + 4, (value ushr 32).toByte())
            PlatformDependent.putByte(address + 5, (value ushr 40).toByte())
            PlatformDependent.putByte(address + 6, (value ushr 48).toByte())
            PlatformDependent.putByte(address + 7, (value ushr 56).toByte())
        }
    }

    @JvmStatic
    fun getByte(array: ByteArray, index: Int): Byte {
        return PlatformDependent.getByte(array, index)
    }

    @JvmStatic
    fun getShort(array: ByteArray, index: Int): Short {
        if (UNALIGNED) {
            val v = PlatformDependent.getShort(array, index)
            return if (BIG_ENDIAN_NATIVE_ORDER) v else java.lang.Short.reverseBytes(v)
        }
        if (USE_VAR_HANDLE) {
            return VarHandleByteBufferAccess.getShortBE(array, index)
        }
        return ((PlatformDependent.getByte(array, index).toInt() shl 8) or
                (PlatformDependent.getByte(array, index + 1).toInt() and 0xff)).toShort()
    }

    @JvmStatic
    fun getShortLE(array: ByteArray, index: Int): Short {
        if (UNALIGNED) {
            val v = PlatformDependent.getShort(array, index)
            return if (BIG_ENDIAN_NATIVE_ORDER) java.lang.Short.reverseBytes(v) else v
        }
        if (USE_VAR_HANDLE) {
            return VarHandleByteBufferAccess.getShortLE(array, index)
        }
        return ((PlatformDependent.getByte(array, index).toInt() and 0xff) or
                (PlatformDependent.getByte(array, index + 1).toInt() shl 8)).toShort()
    }

    @JvmStatic
    fun getUnsignedMedium(array: ByteArray, index: Int): Int {
        if (UNALIGNED) {
            return ((PlatformDependent.getByte(array, index).toInt() and 0xff) shl 16) or
                    ((if (BIG_ENDIAN_NATIVE_ORDER) PlatformDependent.getShort(array, index + 1)
                    else java.lang.Short.reverseBytes(PlatformDependent.getShort(array, index + 1))).toInt() and 0xffff)
        }
        return ((PlatformDependent.getByte(array, index).toInt() and 0xff) shl 16) or
                ((PlatformDependent.getByte(array, index + 1).toInt() and 0xff) shl 8) or
                (PlatformDependent.getByte(array, index + 2).toInt() and 0xff)
    }

    @JvmStatic
    fun getUnsignedMediumLE(array: ByteArray, index: Int): Int {
        if (UNALIGNED) {
            return (PlatformDependent.getByte(array, index).toInt() and 0xff) or
                    (((if (BIG_ENDIAN_NATIVE_ORDER) java.lang.Short.reverseBytes(PlatformDependent.getShort(array, index + 1))
                    else PlatformDependent.getShort(array, index + 1)).toInt() and 0xffff) shl 8)
        }
        return (PlatformDependent.getByte(array, index).toInt() and 0xff) or
                ((PlatformDependent.getByte(array, index + 1).toInt() and 0xff) shl 8) or
                ((PlatformDependent.getByte(array, index + 2).toInt() and 0xff) shl 16)
    }

    @JvmStatic
    fun getInt(array: ByteArray, index: Int): Int {
        if (UNALIGNED) {
            val v = PlatformDependent.getInt(array, index)
            return if (BIG_ENDIAN_NATIVE_ORDER) v else Integer.reverseBytes(v)
        }
        if (USE_VAR_HANDLE) {
            return VarHandleByteBufferAccess.getIntBE(array, index)
        }
        return (PlatformDependent.getByte(array, index).toInt() shl 24) or
                ((PlatformDependent.getByte(array, index + 1).toInt() and 0xff) shl 16) or
                ((PlatformDependent.getByte(array, index + 2).toInt() and 0xff) shl 8) or
                (PlatformDependent.getByte(array, index + 3).toInt() and 0xff)
    }

    @JvmStatic
    fun getIntLE(array: ByteArray, index: Int): Int {
        if (UNALIGNED) {
            val v = PlatformDependent.getInt(array, index)
            return if (BIG_ENDIAN_NATIVE_ORDER) Integer.reverseBytes(v) else v
        }
        if (USE_VAR_HANDLE) {
            return VarHandleByteBufferAccess.getIntLE(array, index)
        }
        return (PlatformDependent.getByte(array, index).toInt() and 0xff) or
                ((PlatformDependent.getByte(array, index + 1).toInt() and 0xff) shl 8) or
                ((PlatformDependent.getByte(array, index + 2).toInt() and 0xff) shl 16) or
                (PlatformDependent.getByte(array, index + 3).toInt() shl 24)
    }

    @JvmStatic
    fun getLong(array: ByteArray, index: Int): Long {
        if (UNALIGNED) {
            val v = PlatformDependent.getLong(array, index)
            return if (BIG_ENDIAN_NATIVE_ORDER) v else java.lang.Long.reverseBytes(v)
        }
        if (USE_VAR_HANDLE) {
            return VarHandleByteBufferAccess.getLongBE(array, index)
        }
        return (PlatformDependent.getByte(array, index).toLong() shl 56) or
                ((PlatformDependent.getByte(array, index + 1).toLong() and 0xffL) shl 48) or
                ((PlatformDependent.getByte(array, index + 2).toLong() and 0xffL) shl 40) or
                ((PlatformDependent.getByte(array, index + 3).toLong() and 0xffL) shl 32) or
                ((PlatformDependent.getByte(array, index + 4).toLong() and 0xffL) shl 24) or
                ((PlatformDependent.getByte(array, index + 5).toLong() and 0xffL) shl 16) or
                ((PlatformDependent.getByte(array, index + 6).toLong() and 0xffL) shl 8) or
                (PlatformDependent.getByte(array, index + 7).toLong() and 0xffL)
    }

    @JvmStatic
    fun getLongLE(array: ByteArray, index: Int): Long {
        if (UNALIGNED) {
            val v = PlatformDependent.getLong(array, index)
            return if (BIG_ENDIAN_NATIVE_ORDER) java.lang.Long.reverseBytes(v) else v
        }
        if (USE_VAR_HANDLE) {
            return VarHandleByteBufferAccess.getLongLE(array, index)
        }
        return (PlatformDependent.getByte(array, index).toLong() and 0xffL) or
                ((PlatformDependent.getByte(array, index + 1).toLong() and 0xffL) shl 8) or
                ((PlatformDependent.getByte(array, index + 2).toLong() and 0xffL) shl 16) or
                ((PlatformDependent.getByte(array, index + 3).toLong() and 0xffL) shl 24) or
                ((PlatformDependent.getByte(array, index + 4).toLong() and 0xffL) shl 32) or
                ((PlatformDependent.getByte(array, index + 5).toLong() and 0xffL) shl 40) or
                ((PlatformDependent.getByte(array, index + 6).toLong() and 0xffL) shl 48) or
                (PlatformDependent.getByte(array, index + 7).toLong() shl 56)
    }

    @JvmStatic
    fun setByte(array: ByteArray, index: Int, value: Int) {
        PlatformDependent.putByte(array, index, value.toByte())
    }

    @JvmStatic
    fun setShort(array: ByteArray, index: Int, value: Int) {
        if (UNALIGNED) {
            PlatformDependent.putShort(array, index,
                if (BIG_ENDIAN_NATIVE_ORDER) value.toShort() else java.lang.Short.reverseBytes(value.toShort()))
        } else if (USE_VAR_HANDLE) {
            VarHandleByteBufferAccess.setShortBE(array, index, value)
        } else {
            PlatformDependent.putByte(array, index, (value ushr 8).toByte())
            PlatformDependent.putByte(array, index + 1, value.toByte())
        }
    }

    @JvmStatic
    fun setShortLE(array: ByteArray, index: Int, value: Int) {
        if (UNALIGNED) {
            PlatformDependent.putShort(array, index,
                if (BIG_ENDIAN_NATIVE_ORDER) java.lang.Short.reverseBytes(value.toShort()) else value.toShort())
        } else if (USE_VAR_HANDLE) {
            VarHandleByteBufferAccess.setShortLE(array, index, value)
        } else {
            PlatformDependent.putByte(array, index, value.toByte())
            PlatformDependent.putByte(array, index + 1, (value ushr 8).toByte())
        }
    }

    @JvmStatic
    fun setMedium(array: ByteArray, index: Int, value: Int) {
        PlatformDependent.putByte(array, index, (value ushr 16).toByte())
        if (UNALIGNED) {
            PlatformDependent.putShort(array, index + 1,
                if (BIG_ENDIAN_NATIVE_ORDER) value.toShort()
                else java.lang.Short.reverseBytes(value.toShort()))
        } else {
            PlatformDependent.putByte(array, index + 1, (value ushr 8).toByte())
            PlatformDependent.putByte(array, index + 2, value.toByte())
        }
    }

    @JvmStatic
    fun setMediumLE(array: ByteArray, index: Int, value: Int) {
        PlatformDependent.putByte(array, index, value.toByte())
        if (UNALIGNED) {
            PlatformDependent.putShort(array, index + 1,
                if (BIG_ENDIAN_NATIVE_ORDER) java.lang.Short.reverseBytes((value ushr 8).toShort())
                else (value ushr 8).toShort())
        } else {
            PlatformDependent.putByte(array, index + 1, (value ushr 8).toByte())
            PlatformDependent.putByte(array, index + 2, (value ushr 16).toByte())
        }
    }

    @JvmStatic
    fun setInt(array: ByteArray, index: Int, value: Int) {
        if (UNALIGNED) {
            PlatformDependent.putInt(array, index, if (BIG_ENDIAN_NATIVE_ORDER) value else Integer.reverseBytes(value))
        } else if (USE_VAR_HANDLE) {
            VarHandleByteBufferAccess.setIntBE(array, index, value)
        } else {
            PlatformDependent.putByte(array, index, (value ushr 24).toByte())
            PlatformDependent.putByte(array, index + 1, (value ushr 16).toByte())
            PlatformDependent.putByte(array, index + 2, (value ushr 8).toByte())
            PlatformDependent.putByte(array, index + 3, value.toByte())
        }
    }

    @JvmStatic
    fun setIntLE(array: ByteArray, index: Int, value: Int) {
        if (UNALIGNED) {
            PlatformDependent.putInt(array, index, if (BIG_ENDIAN_NATIVE_ORDER) Integer.reverseBytes(value) else value)
        } else if (USE_VAR_HANDLE) {
            VarHandleByteBufferAccess.setIntLE(array, index, value)
        } else {
            PlatformDependent.putByte(array, index, value.toByte())
            PlatformDependent.putByte(array, index + 1, (value ushr 8).toByte())
            PlatformDependent.putByte(array, index + 2, (value ushr 16).toByte())
            PlatformDependent.putByte(array, index + 3, (value ushr 24).toByte())
        }
    }

    @JvmStatic
    fun setLong(array: ByteArray, index: Int, value: Long) {
        if (UNALIGNED) {
            PlatformDependent.putLong(array, index, if (BIG_ENDIAN_NATIVE_ORDER) value else java.lang.Long.reverseBytes(value))
        } else if (USE_VAR_HANDLE) {
            VarHandleByteBufferAccess.setLongBE(array, index, value)
        } else {
            PlatformDependent.putByte(array, index, (value ushr 56).toByte())
            PlatformDependent.putByte(array, index + 1, (value ushr 48).toByte())
            PlatformDependent.putByte(array, index + 2, (value ushr 40).toByte())
            PlatformDependent.putByte(array, index + 3, (value ushr 32).toByte())
            PlatformDependent.putByte(array, index + 4, (value ushr 24).toByte())
            PlatformDependent.putByte(array, index + 5, (value ushr 16).toByte())
            PlatformDependent.putByte(array, index + 6, (value ushr 8).toByte())
            PlatformDependent.putByte(array, index + 7, value.toByte())
        }
    }

    @JvmStatic
    fun setLongLE(array: ByteArray, index: Int, value: Long) {
        if (UNALIGNED) {
            PlatformDependent.putLong(array, index, if (BIG_ENDIAN_NATIVE_ORDER) java.lang.Long.reverseBytes(value) else value)
        } else if (USE_VAR_HANDLE) {
            VarHandleByteBufferAccess.setLongLE(array, index, value)
        } else {
            PlatformDependent.putByte(array, index, value.toByte())
            PlatformDependent.putByte(array, index + 1, (value ushr 8).toByte())
            PlatformDependent.putByte(array, index + 2, (value ushr 16).toByte())
            PlatformDependent.putByte(array, index + 3, (value ushr 24).toByte())
            PlatformDependent.putByte(array, index + 4, (value ushr 32).toByte())
            PlatformDependent.putByte(array, index + 5, (value ushr 40).toByte())
            PlatformDependent.putByte(array, index + 6, (value ushr 48).toByte())
            PlatformDependent.putByte(array, index + 7, (value ushr 56).toByte())
        }
    }

    private fun batchSetZero(data: ByteArray, index: Int, length: Int) {
        var idx = index
        val longBatches = length ushr 3
        for (i in 0 until longBatches) {
            PlatformDependent.putLong(data, idx, ZERO.toLong())
            idx += 8
        }
        val remaining = length and 0x07
        for (i in 0 until remaining) {
            PlatformDependent.putByte(data, idx + i, ZERO)
        }
    }

    @JvmStatic
    fun setZero(array: ByteArray, index: Int, length: Int) {
        if (length == 0) {
            return
        }
        // fast-path for small writes to avoid thread-state change JDK's handling
        if (UNALIGNED && length <= MAX_HAND_ROLLED_SET_ZERO_BYTES) {
            batchSetZero(array, index, length)
        } else {
            PlatformDependent.setMemory(array, index, length.toLong(), ZERO)
        }
    }

    @JvmStatic
    fun copy(buf: AbstractByteBuf, addr: Long, index: Int, length: Int): ByteBuf {
        buf.checkIndex(index, length)
        val copy = buf.alloc().directBuffer(length, buf.maxCapacity())
        if (length != 0) {
            if (copy.hasMemoryAddress()) {
                PlatformDependent.copyMemory(addr, copy.memoryAddress(), length.toLong())
                copy.setIndex(0, length)
            } else {
                copy.writeBytes(buf, index, length)
            }
        }
        return copy
    }

    @Throws(IOException::class)
    @JvmStatic
    fun setBytes(buf: AbstractByteBuf, addr: Long, index: Int, `in`: InputStream, length: Int): Int {
        buf.checkIndex(index, length)
        val tmpBuf = buf.alloc().heapBuffer(length)
        try {
            val tmp = tmpBuf.array()
            val offset = tmpBuf.arrayOffset()
            val readBytes = `in`.read(tmp, offset, length)
            if (readBytes > 0) {
                PlatformDependent.copyMemory(tmp, offset, addr, readBytes.toLong())
            }
            return readBytes
        } finally {
            tmpBuf.release()
        }
    }

    @JvmStatic
    fun getBytes(buf: AbstractByteBuf, addr: Long, index: Int, dst: ByteBuf, dstIndex: Int, length: Int) {
        buf.checkIndex(index, length)
        checkNotNull(dst, "dst")
        if (isOutOfBounds(dstIndex, length, dst.capacity())) {
            throw IndexOutOfBoundsException("dstIndex: $dstIndex")
        }

        if (dst.hasMemoryAddress()) {
            PlatformDependent.copyMemory(addr, dst.memoryAddress() + dstIndex, length.toLong())
        } else if (dst.hasArray()) {
            PlatformDependent.copyMemory(addr, dst.array(), dst.arrayOffset() + dstIndex, length.toLong())
        } else {
            dst.setBytes(dstIndex, buf, index, length)
        }
    }

    @JvmStatic
    fun getBytes(buf: AbstractByteBuf, addr: Long, index: Int, dst: ByteArray, dstIndex: Int, length: Int) {
        buf.checkIndex(index, length)
        checkNotNull(dst, "dst")
        if (isOutOfBounds(dstIndex, length, dst.size)) {
            throw IndexOutOfBoundsException("dstIndex: $dstIndex")
        }
        if (length != 0) {
            PlatformDependent.copyMemory(addr, dst, dstIndex, length.toLong())
        }
    }

    @JvmStatic
    fun getBytes(buf: AbstractByteBuf, addr: Long, index: Int, dst: ByteBuffer) {
        buf.checkIndex(index, dst.remaining())
        if (dst.remaining() == 0) {
            return
        }

        if (dst.isDirect) {
            if (dst.isReadOnly) {
                // We need to check if dst is read-only so we not write something in it by using Unsafe.
                throw ReadOnlyBufferException()
            }
            // Copy to direct memory
            val dstAddress = PlatformDependent.directBufferAddress(dst)
            PlatformDependent.copyMemory(addr, dstAddress + dst.position(), dst.remaining().toLong())
            dst.position(dst.position() + dst.remaining())
        } else if (dst.hasArray()) {
            // Copy to array
            PlatformDependent.copyMemory(addr, dst.array(), dst.arrayOffset() + dst.position(), dst.remaining().toLong())
            dst.position(dst.position() + dst.remaining())
        } else {
            dst.put(buf.nioBuffer())
        }
    }

    @JvmStatic
    fun setBytes(buf: AbstractByteBuf, addr: Long, index: Int, src: ByteBuf, srcIndex: Int, length: Int) {
        buf.checkIndex(index, length)
        checkNotNull(src, "src")
        if (isOutOfBounds(srcIndex, length, src.capacity())) {
            throw IndexOutOfBoundsException("srcIndex: $srcIndex")
        }

        if (length != 0) {
            if (src.hasMemoryAddress()) {
                PlatformDependent.copyMemory(src.memoryAddress() + srcIndex, addr, length.toLong())
            } else if (src.hasArray()) {
                PlatformDependent.copyMemory(src.array(), src.arrayOffset() + srcIndex, addr, length.toLong())
            } else {
                src.getBytes(srcIndex, buf, index, length)
            }
        }
    }

    @JvmStatic
    fun setBytes(buf: AbstractByteBuf, addr: Long, index: Int, src: ByteArray, srcIndex: Int, length: Int) {
        buf.checkIndex(index, length)
        // we need to check not null for src as it may cause the JVM crash
        // See https://github.com/netty/netty/issues/10791
        checkNotNull(src, "src")
        if (isOutOfBounds(srcIndex, length, src.size)) {
            throw IndexOutOfBoundsException("srcIndex: $srcIndex")
        }

        if (length != 0) {
            PlatformDependent.copyMemory(src, srcIndex, addr, length.toLong())
        }
    }

    @JvmStatic
    fun setBytes(buf: AbstractByteBuf, addr: Long, index: Int, src: ByteBuffer) {
        val length = src.remaining()
        if (length == 0) {
            return
        }

        if (src.isDirect) {
            buf.checkIndex(index, length)
            // Copy from direct memory
            val srcAddress = PlatformDependent.directBufferAddress(src)
            PlatformDependent.copyMemory(srcAddress + src.position(), addr, length.toLong())
            src.position(src.position() + length)
        } else if (src.hasArray()) {
            buf.checkIndex(index, length)
            // Copy from array
            PlatformDependent.copyMemory(src.array(), src.arrayOffset() + src.position(), addr, length.toLong())
            src.position(src.position() + length)
        } else {
            if (length < 8) {
                setSingleBytes(buf, addr, index, src, length)
            } else {
                // no need to checkIndex: internalNioBuffer is already taking care of it
                assert(buf.nioBufferCount() == 1)
                val internalBuffer = buf.internalNioBuffer(index, length)
                internalBuffer.put(src)
            }
        }
    }

    private fun setSingleBytes(buf: AbstractByteBuf, addr: Long, index: Int,
                               src: ByteBuffer, length: Int) {
        buf.checkIndex(index, length)
        val srcPosition = src.position()
        val srcLimit = src.limit()
        var dstAddr = addr
        for (srcIndex in srcPosition until srcLimit) {
            val value = src.get(srcIndex)
            PlatformDependent.putByte(dstAddr, value)
            dstAddr++
        }
        src.position(srcLimit)
    }

    @Throws(IOException::class)
    @JvmStatic
    fun getBytes(buf: AbstractByteBuf, addr: Long, index: Int, out: OutputStream, length: Int) {
        buf.checkIndex(index, length)
        if (length != 0) {
            val len = Math.min(length, ByteBufUtil.WRITE_CHUNK_SIZE)
            if (len <= ByteBufUtil.MAX_TL_ARRAY_LEN || !buf.alloc().isDirectBufferPooled()) {
                getBytes(addr, ByteBufUtil.threadLocalTempArray(len), 0, len, out, length)
            } else {
                // if direct buffers are pooled chances are good that heap buffers are pooled as well.
                val tmpBuf = buf.alloc().heapBuffer(len)
                try {
                    val tmp = tmpBuf.array()
                    val offset = tmpBuf.arrayOffset()
                    getBytes(addr, tmp, offset, len, out, length)
                } finally {
                    tmpBuf.release()
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun getBytes(inAddr: Long, `in`: ByteArray, inOffset: Int, inLen: Int, out: OutputStream, outLen: Int) {
        var addr = inAddr
        var remaining = outLen
        do {
            val len = Math.min(inLen, remaining)
            PlatformDependent.copyMemory(addr, `in`, inOffset, len.toLong())
            out.write(`in`, inOffset, len)
            remaining -= len
            addr += len
        } while (remaining > 0)
    }

    private fun batchSetZero(addr: Long, length: Int) {
        var address = addr
        val longBatches = length ushr 3
        for (i in 0 until longBatches) {
            PlatformDependent.putLong(address, ZERO.toLong())
            address += 8
        }
        val remaining = length and 0x07
        for (i in 0 until remaining) {
            PlatformDependent.putByte(address + i, ZERO)
        }
    }

    @JvmStatic
    fun setZero(addr: Long, length: Int) {
        var address = addr
        var len = length
        if (len == 0) {
            return
        }
        // fast-path for small writes to avoid thread-state change JDK's handling
        if (len <= MAX_HAND_ROLLED_SET_ZERO_BYTES) {
            if (!UNALIGNED) {
                // write bytes until the address is aligned
                val bytesToGetAligned = zeroTillAligned(address, len)
                address += bytesToGetAligned
                len -= bytesToGetAligned
                if (len == 0) {
                    return
                }
                assert(is8BytesAligned(address))
            }
            batchSetZero(address, len)
        } else {
            PlatformDependent.setMemory(address, len.toLong(), ZERO)
        }
    }

    @JvmStatic
    fun next8bytesAlignedAddr(addr: Long): Long {
        return (addr + 7L) and 7L.inv()
    }

    @JvmStatic
    fun is8BytesAligned(addr: Long): Boolean {
        return (addr and 7L) == 0L
    }

    private fun zeroTillAligned(addr: Long, length: Int): Int {
        val alignedAddr = next8bytesAlignedAddr(addr)
        val bytesToGetAligned = (alignedAddr - addr).toInt()
        val toZero = Math.min(bytesToGetAligned, length)
        for (i in 0 until toZero) {
            PlatformDependent.putByte(addr + i, ZERO)
        }
        return toZero
    }

    @JvmStatic
    fun newUnsafeDirectByteBuf(
            alloc: ByteBufAllocator, initialCapacity: Int, maxCapacity: Int): UnpooledUnsafeDirectByteBuf {
        return if (PlatformDependent.useDirectBufferNoCleaner()) {
            UnpooledUnsafeNoCleanerDirectByteBuf(alloc, initialCapacity, maxCapacity)
        } else {
            UnpooledUnsafeDirectByteBuf(alloc, initialCapacity, maxCapacity)
        }
    }
}
