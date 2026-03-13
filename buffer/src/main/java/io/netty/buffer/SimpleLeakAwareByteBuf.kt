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

import io.netty.util.IllegalReferenceCountException
import io.netty.util.ResourceLeakDetector
import io.netty.util.ResourceLeakTracker
import io.netty.util.internal.ObjectUtil
import io.netty.util.internal.ThrowableUtil
import java.nio.ByteOrder

internal open class SimpleLeakAwareByteBuf : WrappedByteBuf {

    /**
     * This object is associated with the [ResourceLeakTracker]. When [ResourceLeakTracker.close]
     * is called this object will be used as the argument. It is also assumed that this object is used when
     * [ResourceLeakDetector.track] is called to create [leak].
     */
    private val trackedByteBuf: ByteBuf

    @JvmField
    val leak: ResourceLeakTracker<ByteBuf>

    internal constructor(wrapped: ByteBuf, trackedByteBuf: ByteBuf, leak: ResourceLeakTracker<ByteBuf>) : super(wrapped) {
        this.trackedByteBuf = ObjectUtil.checkNotNull(trackedByteBuf, "trackedByteBuf")
        this.leak = ObjectUtil.checkNotNull(leak, "leak")
    }

    internal constructor(wrapped: ByteBuf, leak: ResourceLeakTracker<ByteBuf>) : this(wrapped, wrapped, leak)

    override fun slice(): ByteBuf {
        return newSharedLeakAwareByteBuf(super.slice())
    }

    override fun retainedSlice(): ByteBuf {
        try {
            return unwrappedDerived(super.retainedSlice())
        } catch (irce: IllegalReferenceCountException) {
            ThrowableUtil.addSuppressed(irce, leak.getCloseStackTraceIfAny())
            throw irce
        }
    }

    override fun retainedSlice(index: Int, length: Int): ByteBuf {
        try {
            return unwrappedDerived(super.retainedSlice(index, length))
        } catch (irce: IllegalReferenceCountException) {
            ThrowableUtil.addSuppressed(irce, leak.getCloseStackTraceIfAny())
            throw irce
        }
    }

    override fun retainedDuplicate(): ByteBuf {
        try {
            return unwrappedDerived(super.retainedDuplicate())
        } catch (irce: IllegalReferenceCountException) {
            ThrowableUtil.addSuppressed(irce, leak.getCloseStackTraceIfAny())
            throw irce
        }
    }

    override fun readRetainedSlice(length: Int): ByteBuf {
        try {
            return unwrappedDerived(super.readRetainedSlice(length))
        } catch (irce: IllegalReferenceCountException) {
            ThrowableUtil.addSuppressed(irce, leak.getCloseStackTraceIfAny())
            throw irce
        }
    }

    override fun slice(index: Int, length: Int): ByteBuf {
        return newSharedLeakAwareByteBuf(super.slice(index, length))
    }

    override fun duplicate(): ByteBuf {
        return newSharedLeakAwareByteBuf(super.duplicate())
    }

    override fun readSlice(length: Int): ByteBuf {
        return newSharedLeakAwareByteBuf(super.readSlice(length))
    }

    override fun asReadOnly(): ByteBuf {
        return newSharedLeakAwareByteBuf(super.asReadOnly())
    }

    override fun touch(): ByteBuf = this

    override fun touch(hint: Any?): ByteBuf = this

    override fun retain(): ByteBuf {
        try {
            return super.retain()
        } catch (irce: IllegalReferenceCountException) {
            ThrowableUtil.addSuppressed(irce, leak.getCloseStackTraceIfAny())
            throw irce
        }
    }

    override fun retain(increment: Int): ByteBuf {
        try {
            return super.retain(increment)
        } catch (irce: IllegalReferenceCountException) {
            ThrowableUtil.addSuppressed(irce, leak.getCloseStackTraceIfAny())
            throw irce
        }
    }

    override fun release(): Boolean {
        try {
            if (super.release()) {
                closeLeak()
                return true
            }
            return false
        } catch (irce: IllegalReferenceCountException) {
            ThrowableUtil.addSuppressed(irce, leak.getCloseStackTraceIfAny())
            throw irce
        }
    }

    override fun release(decrement: Int): Boolean {
        try {
            if (super.release(decrement)) {
                closeLeak()
                return true
            }
            return false
        } catch (irce: IllegalReferenceCountException) {
            ThrowableUtil.addSuppressed(irce, leak.getCloseStackTraceIfAny())
            throw irce
        }
    }

    private fun closeLeak() {
        // Close the ResourceLeakTracker with the tracked ByteBuf as argument. This must be the same that was used when
        // calling DefaultResourceLeak.track(...).
        val closed = leak.close(trackedByteBuf)
        assert(closed)
    }

    override fun order(endianness: ByteOrder): ByteBuf {
        return if (order() == endianness) {
            this
        } else {
            newSharedLeakAwareByteBuf(super.order(endianness))
        }
    }

    private fun unwrappedDerived(derived: ByteBuf): ByteBuf {
        // We only need to unwrap SwappedByteBuf implementations as these will be the only ones that may end up in
        // the AbstractLeakAwareByteBuf implementations beside slices / duplicates and "real" buffers.
        val unwrappedDerived = unwrapSwapped(derived)

        if (unwrappedDerived is AbstractPooledDerivedByteBuf) {
            // Update the parent to point to this buffer so we correctly close the ResourceLeakTracker.
            unwrappedDerived.parent(this)

            // force tracking of derived buffers (see issue #13414)
            return newLeakAwareByteBuf(derived, AbstractByteBuf.leakDetector.trackForcibly(derived))
        }
        return newSharedLeakAwareByteBuf(derived)
    }

    private fun newSharedLeakAwareByteBuf(wrapped: ByteBuf): SimpleLeakAwareByteBuf {
        return newLeakAwareByteBuf(wrapped, trackedByteBuf, leak)
    }

    private fun newLeakAwareByteBuf(
        wrapped: ByteBuf,
        leakTracker: ResourceLeakTracker<ByteBuf>
    ): SimpleLeakAwareByteBuf {
        return newLeakAwareByteBuf(wrapped, wrapped, leakTracker)
    }

    protected open fun newLeakAwareByteBuf(
        buf: ByteBuf,
        trackedByteBuf: ByteBuf,
        leakTracker: ResourceLeakTracker<ByteBuf>
    ): SimpleLeakAwareByteBuf {
        return SimpleLeakAwareByteBuf(buf, trackedByteBuf, leakTracker)
    }

    companion object {
        @Suppress("DEPRECATION")
        private fun unwrapSwapped(buf: ByteBuf): ByteBuf {
            var result = buf
            if (result is SwappedByteBuf) {
                do {
                    result = result.unwrap()!!
                } while (result is SwappedByteBuf)
                return result
            }
            return result
        }
    }
}
