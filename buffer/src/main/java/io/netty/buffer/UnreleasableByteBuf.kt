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

import java.nio.ByteOrder

/**
 * A [ByteBuf] implementation that wraps another buffer to prevent a user from increasing or decreasing the
 * wrapped buffer's reference count.
 */
internal class UnreleasableByteBuf(buf: ByteBuf) : WrappedByteBuf(if (buf is UnreleasableByteBuf) buf.unwrap() else buf) {

    private var swappedBuf: SwappedByteBuf? = null

    @Suppress("DEPRECATION")
    override fun order(endianness: ByteOrder): ByteBuf {
        if (ObjectUtil.checkNotNull(endianness, "endianness") == order()) {
            return this
        }

        var swappedBuf = this.swappedBuf
        if (swappedBuf == null) {
            swappedBuf = SwappedByteBuf(this)
            this.swappedBuf = swappedBuf
        }
        return swappedBuf
    }

    override fun asReadOnly(): ByteBuf {
        return if (buf.isReadOnly()) this else UnreleasableByteBuf(buf.asReadOnly())
    }

    override fun readSlice(length: Int): ByteBuf {
        return UnreleasableByteBuf(buf.readSlice(length))
    }

    override fun readRetainedSlice(length: Int): ByteBuf {
        // We could call buf.readSlice(..), and then call buf.release(). However this creates a leak in unit tests
        // because the release method on UnreleasableByteBuf will never allow the leak record to be cleaned up.
        // So we just use readSlice(..) because the end result should be logically equivalent.
        return readSlice(length)
    }

    override fun slice(): ByteBuf {
        return UnreleasableByteBuf(buf.slice())
    }

    override fun retainedSlice(): ByteBuf {
        // We could call buf.retainedSlice(), and then call buf.release(). However this creates a leak in unit tests
        // because the release method on UnreleasableByteBuf will never allow the leak record to be cleaned up.
        // So we just use slice() because the end result should be logically equivalent.
        return slice()
    }

    override fun slice(index: Int, length: Int): ByteBuf {
        return UnreleasableByteBuf(buf.slice(index, length))
    }

    override fun retainedSlice(index: Int, length: Int): ByteBuf {
        // We could call buf.retainedSlice(..), and then call buf.release(). However this creates a leak in unit tests
        // because the release method on UnreleasableByteBuf will never allow the leak record to be cleaned up.
        // So we just use slice(..) because the end result should be logically equivalent.
        return slice(index, length)
    }

    override fun duplicate(): ByteBuf {
        return UnreleasableByteBuf(buf.duplicate())
    }

    override fun retainedDuplicate(): ByteBuf {
        // We could call buf.retainedDuplicate(), and then call buf.release(). However this creates a leak in unit tests
        // because the release method on UnreleasableByteBuf will never allow the leak record to be cleaned up.
        // So we just use duplicate() because the end result should be logically equivalent.
        return duplicate()
    }

    override fun retain(increment: Int): ByteBuf {
        return this
    }

    override fun retain(): ByteBuf {
        return this
    }

    override fun touch(): ByteBuf {
        return this
    }

    override fun touch(hint: Any?): ByteBuf {
        return this
    }

    override fun release(): Boolean {
        return false
    }

    override fun release(decrement: Int): Boolean {
        return false
    }
}
