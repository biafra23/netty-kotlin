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
 * Utility class for heap buffers.
 */
internal object HeapByteBufUtil {

    @JvmStatic
    fun getByte(memory: ByteArray, index: Int): Byte = memory[index]

    @JvmStatic
    fun getShort(memory: ByteArray, index: Int): Short {
        if (PlatformDependent.hasVarHandle()) {
            return VarHandleByteBufferAccess.getShortBE(memory, index)
        }
        return getShort0(memory, index)
    }

    private fun getShort0(memory: ByteArray, index: Int): Short =
        (memory[index].toInt() shl 8 or (memory[index + 1].toInt() and 0xFF)).toShort()

    @JvmStatic
    fun getShortLE(memory: ByteArray, index: Int): Short {
        if (PlatformDependent.hasVarHandle()) {
            return VarHandleByteBufferAccess.getShortLE(memory, index)
        }
        return (memory[index].toInt() and 0xFF or (memory[index + 1].toInt() shl 8)).toShort()
    }

    @JvmStatic
    fun getUnsignedMedium(memory: ByteArray, index: Int): Int =
        (memory[index].toInt() and 0xFF shl 16) or
        (memory[index + 1].toInt() and 0xFF shl 8) or
        (memory[index + 2].toInt() and 0xFF)

    @JvmStatic
    fun getUnsignedMediumLE(memory: ByteArray, index: Int): Int =
        (memory[index].toInt() and 0xFF) or
        (memory[index + 1].toInt() and 0xFF shl 8) or
        (memory[index + 2].toInt() and 0xFF shl 16)

    @JvmStatic
    fun getInt(memory: ByteArray, index: Int): Int {
        if (PlatformDependent.hasVarHandle()) {
            return VarHandleByteBufferAccess.getIntBE(memory, index)
        }
        return getInt0(memory, index)
    }

    private fun getInt0(memory: ByteArray, index: Int): Int =
        (memory[index].toInt() and 0xFF shl 24) or
        (memory[index + 1].toInt() and 0xFF shl 16) or
        (memory[index + 2].toInt() and 0xFF shl 8) or
        (memory[index + 3].toInt() and 0xFF)

    @JvmStatic
    fun getIntLE(memory: ByteArray, index: Int): Int {
        if (PlatformDependent.hasVarHandle()) {
            return VarHandleByteBufferAccess.getIntLE(memory, index)
        }
        return getIntLE0(memory, index)
    }

    private fun getIntLE0(memory: ByteArray, index: Int): Int =
        (memory[index].toInt() and 0xFF) or
        (memory[index + 1].toInt() and 0xFF shl 8) or
        (memory[index + 2].toInt() and 0xFF shl 16) or
        (memory[index + 3].toInt() and 0xFF shl 24)

    @JvmStatic
    fun getLong(memory: ByteArray, index: Int): Long {
        if (PlatformDependent.hasVarHandle()) {
            return VarHandleByteBufferAccess.getLongBE(memory, index)
        }
        return getLong0(memory, index)
    }

    private fun getLong0(memory: ByteArray, index: Int): Long =
        (memory[index].toLong() and 0xFF shl 56) or
        (memory[index + 1].toLong() and 0xFF shl 48) or
        (memory[index + 2].toLong() and 0xFF shl 40) or
        (memory[index + 3].toLong() and 0xFF shl 32) or
        (memory[index + 4].toLong() and 0xFF shl 24) or
        (memory[index + 5].toLong() and 0xFF shl 16) or
        (memory[index + 6].toLong() and 0xFF shl 8) or
        (memory[index + 7].toLong() and 0xFF)

    @JvmStatic
    fun getLongLE(memory: ByteArray, index: Int): Long {
        if (PlatformDependent.hasVarHandle()) {
            return VarHandleByteBufferAccess.getLongLE(memory, index)
        }
        return getLongLE0(memory, index)
    }

    private fun getLongLE0(memory: ByteArray, index: Int): Long =
        (memory[index].toLong() and 0xFF) or
        (memory[index + 1].toLong() and 0xFF shl 8) or
        (memory[index + 2].toLong() and 0xFF shl 16) or
        (memory[index + 3].toLong() and 0xFF shl 24) or
        (memory[index + 4].toLong() and 0xFF shl 32) or
        (memory[index + 5].toLong() and 0xFF shl 40) or
        (memory[index + 6].toLong() and 0xFF shl 48) or
        (memory[index + 7].toLong() and 0xFF shl 56)

    @JvmStatic
    fun setByte(memory: ByteArray, index: Int, value: Int) {
        memory[index] = (value and 0xFF).toByte()
    }

    @JvmStatic
    fun setShort(memory: ByteArray, index: Int, value: Int) {
        if (PlatformDependent.hasVarHandle()) {
            VarHandleByteBufferAccess.setShortBE(memory, index, value)
            return
        }
        memory[index] = (value ushr 8).toByte()
        memory[index + 1] = value.toByte()
    }

    @JvmStatic
    fun setShortLE(memory: ByteArray, index: Int, value: Int) {
        if (PlatformDependent.hasVarHandle()) {
            VarHandleByteBufferAccess.setShortLE(memory, index, value)
            return
        }
        memory[index] = value.toByte()
        memory[index + 1] = (value ushr 8).toByte()
    }

    @JvmStatic
    fun setMedium(memory: ByteArray, index: Int, value: Int) {
        memory[index] = (value ushr 16).toByte()
        memory[index + 1] = (value ushr 8).toByte()
        memory[index + 2] = value.toByte()
    }

    @JvmStatic
    fun setMediumLE(memory: ByteArray, index: Int, value: Int) {
        memory[index] = value.toByte()
        memory[index + 1] = (value ushr 8).toByte()
        memory[index + 2] = (value ushr 16).toByte()
    }

    @JvmStatic
    fun setInt(memory: ByteArray, index: Int, value: Int) {
        if (PlatformDependent.hasVarHandle()) {
            VarHandleByteBufferAccess.setIntBE(memory, index, value)
            return
        }
        setInt0(memory, index, value)
    }

    private fun setInt0(memory: ByteArray, index: Int, value: Int) {
        memory[index] = (value ushr 24).toByte()
        memory[index + 1] = (value ushr 16).toByte()
        memory[index + 2] = (value ushr 8).toByte()
        memory[index + 3] = value.toByte()
    }

    @JvmStatic
    fun setIntLE(memory: ByteArray, index: Int, value: Int) {
        if (PlatformDependent.hasVarHandle()) {
            VarHandleByteBufferAccess.setIntLE(memory, index, value)
            return
        }
        setIntLE0(memory, index, value)
    }

    private fun setIntLE0(memory: ByteArray, index: Int, value: Int) {
        memory[index] = value.toByte()
        memory[index + 1] = (value ushr 8).toByte()
        memory[index + 2] = (value ushr 16).toByte()
        memory[index + 3] = (value ushr 24).toByte()
    }

    @JvmStatic
    fun setLong(memory: ByteArray, index: Int, value: Long) {
        if (PlatformDependent.hasVarHandle()) {
            VarHandleByteBufferAccess.setLongBE(memory, index, value)
            return
        }
        setLong0(memory, index, value)
    }

    private fun setLong0(memory: ByteArray, index: Int, value: Long) {
        memory[index] = (value ushr 56).toByte()
        memory[index + 1] = (value ushr 48).toByte()
        memory[index + 2] = (value ushr 40).toByte()
        memory[index + 3] = (value ushr 32).toByte()
        memory[index + 4] = (value ushr 24).toByte()
        memory[index + 5] = (value ushr 16).toByte()
        memory[index + 6] = (value ushr 8).toByte()
        memory[index + 7] = value.toByte()
    }

    @JvmStatic
    fun setLongLE(memory: ByteArray, index: Int, value: Long) {
        if (PlatformDependent.hasVarHandle()) {
            VarHandleByteBufferAccess.setLongLE(memory, index, value)
            return
        }
        setLongLE0(memory, index, value)
    }

    private fun setLongLE0(memory: ByteArray, index: Int, value: Long) {
        memory[index] = value.toByte()
        memory[index + 1] = (value ushr 8).toByte()
        memory[index + 2] = (value ushr 16).toByte()
        memory[index + 3] = (value ushr 24).toByte()
        memory[index + 4] = (value ushr 32).toByte()
        memory[index + 5] = (value ushr 40).toByte()
        memory[index + 6] = (value ushr 48).toByte()
        memory[index + 7] = (value ushr 56).toByte()
    }
}
