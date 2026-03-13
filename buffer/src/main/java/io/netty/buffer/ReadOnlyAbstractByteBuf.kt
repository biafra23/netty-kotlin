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

/**
 * Specialized [ReadOnlyByteBuf] sub-type which allows fast access to parent
 * without extra bound-checks.
 */
internal class ReadOnlyAbstractByteBuf(buffer: AbstractByteBuf) : ReadOnlyByteBuf(buffer) {

    init {
        assert(buffer.unwrap() == null || buffer.unwrap() is AbstractByteBuf)
    }

    override fun unwrap(): AbstractByteBuf {
        return super.unwrap() as AbstractByteBuf
    }

    override fun _getByte(index: Int): Byte {
        return unwrap().invoke_getByte(index)
    }

    override fun _getShort(index: Int): Short {
        return unwrap().invoke_getShort(index)
    }

    override fun _getShortLE(index: Int): Short {
        return unwrap().invoke_getShortLE(index)
    }

    override fun _getUnsignedMedium(index: Int): Int {
        return unwrap().invoke_getUnsignedMedium(index)
    }

    override fun _getUnsignedMediumLE(index: Int): Int {
        return unwrap().invoke_getUnsignedMediumLE(index)
    }

    override fun _getInt(index: Int): Int {
        return unwrap().invoke_getInt(index)
    }

    override fun _getIntLE(index: Int): Int {
        return unwrap().invoke_getIntLE(index)
    }

    override fun _getLong(index: Int): Long {
        return unwrap().invoke_getLong(index)
    }

    override fun _getLongLE(index: Int): Long {
        return unwrap().invoke_getLongLE(index)
    }
}
