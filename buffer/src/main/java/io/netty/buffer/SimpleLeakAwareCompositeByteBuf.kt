/*
 * Copyright 2016 The Netty Project
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

import io.netty.util.ResourceLeakTracker
import io.netty.util.internal.ObjectUtil
import java.nio.ByteOrder

internal open class SimpleLeakAwareCompositeByteBuf(
    wrapped: CompositeByteBuf,
    @JvmField val leak: ResourceLeakTracker<ByteBuf>
) : WrappedCompositeByteBuf(wrapped) {

    init {
        ObjectUtil.checkNotNull(leak, "leak")
    }

    override fun release(): Boolean {
        // Call unwrap() before just in case that super.release() will change the ByteBuf instance that is returned
        // by unwrap().
        val unwrapped = unwrap()
        if (super.release()) {
            closeLeak(unwrapped)
            return true
        }
        return false
    }

    override fun release(decrement: Int): Boolean {
        // Call unwrap() before just in case that super.release() will change the ByteBuf instance that is returned
        // by unwrap().
        val unwrapped = unwrap()
        if (super.release(decrement)) {
            closeLeak(unwrapped)
            return true
        }
        return false
    }

    private fun closeLeak(trackedByteBuf: ByteBuf) {
        // Close the ResourceLeakTracker with the tracked ByteBuf as argument. This must be the same that was used when
        // calling DefaultResourceLeak.track(...).
        val closed = leak.close(trackedByteBuf)
        assert(closed)
    }

    override fun order(endianness: ByteOrder): ByteBuf {
        return if (order() == endianness) {
            this
        } else {
            newLeakAwareByteBuf(super.order(endianness))
        }
    }

    override fun slice(): ByteBuf {
        return newLeakAwareByteBuf(super.slice())
    }

    override fun retainedSlice(): ByteBuf {
        return newLeakAwareByteBuf(super.retainedSlice())
    }

    override fun slice(index: Int, length: Int): ByteBuf {
        return newLeakAwareByteBuf(super.slice(index, length))
    }

    override fun retainedSlice(index: Int, length: Int): ByteBuf {
        return newLeakAwareByteBuf(super.retainedSlice(index, length))
    }

    override fun duplicate(): ByteBuf {
        return newLeakAwareByteBuf(super.duplicate())
    }

    override fun retainedDuplicate(): ByteBuf {
        return newLeakAwareByteBuf(super.retainedDuplicate())
    }

    override fun readSlice(length: Int): ByteBuf {
        return newLeakAwareByteBuf(super.readSlice(length))
    }

    override fun readRetainedSlice(length: Int): ByteBuf {
        return newLeakAwareByteBuf(super.readRetainedSlice(length))
    }

    override fun asReadOnly(): ByteBuf {
        return newLeakAwareByteBuf(super.asReadOnly())
    }

    private fun newLeakAwareByteBuf(wrapped: ByteBuf): SimpleLeakAwareByteBuf {
        return newLeakAwareByteBuf(wrapped, unwrap(), leak)
    }

    protected open fun newLeakAwareByteBuf(
        wrapped: ByteBuf,
        trackedByteBuf: ByteBuf,
        leakTracker: ResourceLeakTracker<ByteBuf>
    ): SimpleLeakAwareByteBuf {
        return SimpleLeakAwareByteBuf(wrapped, trackedByteBuf, leakTracker)
    }
}
