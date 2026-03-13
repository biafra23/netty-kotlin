/*
* Copyright 2014 The Netty Project
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
 * Special [SwappedByteBuf] for [ByteBuf]s that use unsafe to access the byte array.
 */
internal class UnsafeHeapSwappedByteBuf(buf: AbstractByteBuf) : AbstractUnsafeSwappedByteBuf(buf) {

    override fun _getLong(wrapped: AbstractByteBuf, index: Int): Long {
        return PlatformDependent.getLong(wrapped.array(), idx(wrapped, index))
    }

    override fun _getInt(wrapped: AbstractByteBuf, index: Int): Int {
        return PlatformDependent.getInt(wrapped.array(), idx(wrapped, index))
    }

    override fun _getShort(wrapped: AbstractByteBuf, index: Int): Short {
        return PlatformDependent.getShort(wrapped.array(), idx(wrapped, index))
    }

    override fun _setShort(wrapped: AbstractByteBuf, index: Int, value: Short) {
        PlatformDependent.putShort(wrapped.array(), idx(wrapped, index), value)
    }

    override fun _setInt(wrapped: AbstractByteBuf, index: Int, value: Int) {
        PlatformDependent.putInt(wrapped.array(), idx(wrapped, index), value)
    }

    override fun _setLong(wrapped: AbstractByteBuf, index: Int, value: Long) {
        PlatformDependent.putLong(wrapped.array(), idx(wrapped, index), value)
    }

    companion object {
        @JvmStatic
        private fun idx(wrapped: ByteBuf, index: Int): Int {
            return wrapped.arrayOffset() + index
        }
    }
}
