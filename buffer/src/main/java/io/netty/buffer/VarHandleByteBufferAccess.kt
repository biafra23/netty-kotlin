/*
 * Copyright 2025 The Netty Project
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
import java.nio.ByteBuffer

/**
 * Centralizes all ByteBuffer VarHandle get/set calls so classes like UnpooledDirectByteBuf
 * don't directly reference signature-polymorphic methods. This allows avoiding class verification
 * failures on older Android runtimes by not loading this class when VarHandle is disabled.
 *
 * Methods here must only be called when PlatformDependent.hasVarHandle() is true.
 */
internal object VarHandleByteBufferAccess {

    // ---- ByteBuffer accessors ----

    // short (big endian)
    @JvmStatic
    fun getShortBE(buffer: ByteBuffer, index: Int): Short {
        return PlatformDependent.shortBeByteBufferView().get(buffer, index) as Short
    }

    @JvmStatic
    fun setShortBE(buffer: ByteBuffer, index: Int, value: Int) {
        PlatformDependent.shortBeByteBufferView().set(buffer, index, value.toShort())
    }

    // short (little endian)
    @JvmStatic
    fun getShortLE(buffer: ByteBuffer, index: Int): Short {
        return PlatformDependent.shortLeByteBufferView().get(buffer, index) as Short
    }

    @JvmStatic
    fun setShortLE(buffer: ByteBuffer, index: Int, value: Int) {
        PlatformDependent.shortLeByteBufferView().set(buffer, index, value.toShort())
    }

    // int (big endian)
    @JvmStatic
    fun getIntBE(buffer: ByteBuffer, index: Int): Int {
        return PlatformDependent.intBeByteBufferView().get(buffer, index) as Int
    }

    @JvmStatic
    fun setIntBE(buffer: ByteBuffer, index: Int, value: Int) {
        PlatformDependent.intBeByteBufferView().set(buffer, index, value)
    }

    // int (little endian)
    @JvmStatic
    fun getIntLE(buffer: ByteBuffer, index: Int): Int {
        return PlatformDependent.intLeByteBufferView().get(buffer, index) as Int
    }

    @JvmStatic
    fun setIntLE(buffer: ByteBuffer, index: Int, value: Int) {
        PlatformDependent.intLeByteBufferView().set(buffer, index, value)
    }

    // long (big endian)
    @JvmStatic
    fun getLongBE(buffer: ByteBuffer, index: Int): Long {
        return PlatformDependent.longBeByteBufferView().get(buffer, index) as Long
    }

    @JvmStatic
    fun setLongBE(buffer: ByteBuffer, index: Int, value: Long) {
        PlatformDependent.longBeByteBufferView().set(buffer, index, value)
    }

    // long (little endian)
    @JvmStatic
    fun getLongLE(buffer: ByteBuffer, index: Int): Long {
        return PlatformDependent.longLeByteBufferView().get(buffer, index) as Long
    }

    @JvmStatic
    fun setLongLE(buffer: ByteBuffer, index: Int, value: Long) {
        PlatformDependent.longLeByteBufferView().set(buffer, index, value)
    }

    // ---- byte[] (heap array) accessors ----

    // short (big endian)
    @JvmStatic
    fun getShortBE(memory: ByteArray, index: Int): Short {
        return PlatformDependent.shortBeArrayView().get(memory, index) as Short
    }

    @JvmStatic
    fun setShortBE(memory: ByteArray, index: Int, value: Int) {
        PlatformDependent.shortBeArrayView().set(memory, index, value.toShort())
    }

    // short (little endian)
    @JvmStatic
    fun getShortLE(memory: ByteArray, index: Int): Short {
        return PlatformDependent.shortLeArrayView().get(memory, index) as Short
    }

    @JvmStatic
    fun setShortLE(memory: ByteArray, index: Int, value: Int) {
        PlatformDependent.shortLeArrayView().set(memory, index, value.toShort())
    }

    // int (big endian)
    @JvmStatic
    fun getIntBE(memory: ByteArray, index: Int): Int {
        return PlatformDependent.intBeArrayView().get(memory, index) as Int
    }

    @JvmStatic
    fun setIntBE(memory: ByteArray, index: Int, value: Int) {
        PlatformDependent.intBeArrayView().set(memory, index, value)
    }

    // int (little endian)
    @JvmStatic
    fun getIntLE(memory: ByteArray, index: Int): Int {
        return PlatformDependent.intLeArrayView().get(memory, index) as Int
    }

    @JvmStatic
    fun setIntLE(memory: ByteArray, index: Int, value: Int) {
        PlatformDependent.intLeArrayView().set(memory, index, value)
    }

    // long (big endian)
    @JvmStatic
    fun getLongBE(memory: ByteArray, index: Int): Long {
        return PlatformDependent.longBeArrayView().get(memory, index) as Long
    }

    @JvmStatic
    fun setLongBE(memory: ByteArray, index: Int, value: Long) {
        PlatformDependent.longBeArrayView().set(memory, index, value)
    }

    // long (little endian)
    @JvmStatic
    fun getLongLE(memory: ByteArray, index: Int): Long {
        return PlatformDependent.longLeArrayView().get(memory, index) as Long
    }

    @JvmStatic
    fun setLongLE(memory: ByteArray, index: Int, value: Long) {
        PlatformDependent.longLeArrayView().set(memory, index, value)
    }
}
