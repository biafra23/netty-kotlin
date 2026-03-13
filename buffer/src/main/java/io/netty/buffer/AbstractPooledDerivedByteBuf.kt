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

import io.netty.util.IllegalReferenceCountException
import io.netty.util.Recycler.EnhancedHandle
import io.netty.util.internal.ObjectPool.Handle
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Abstract base class for derived [ByteBuf] implementations.
 */
abstract class AbstractPooledDerivedByteBuf @Suppress("UNCHECKED_CAST") internal constructor(
    recyclerHandle: Handle<out AbstractPooledDerivedByteBuf>
) : AbstractReferenceCountedByteBuf(0) {

    private val recyclerHandle: EnhancedHandle<AbstractPooledDerivedByteBuf> =
        recyclerHandle as EnhancedHandle<AbstractPooledDerivedByteBuf>
    private var rootParent: AbstractByteBuf? = null
    /**
     * Deallocations of a pooled derived buffer should always propagate through the entire chain of derived buffers.
     * This is because each pooled derived buffer maintains its own reference count and we should respect each one.
     * If deallocations cause a release of the "root parent" then we may prematurely release the underlying
     * content before all the derived buffers have been released.
     */
    private var parent: ByteBuf? = null

    // Called from within SimpleLeakAwareByteBuf and AdvancedLeakAwareByteBuf.
    fun parent(newParent: ByteBuf) {
        assert(newParent is SimpleLeakAwareByteBuf)
        parent = newParent
    }

    final override fun unwrap(): AbstractByteBuf {
        val rootParent = this.rootParent ?: throw IllegalReferenceCountException()
        return rootParent
    }

    @Suppress("UNCHECKED_CAST")
    fun <U : AbstractPooledDerivedByteBuf> init(
        unwrapped: AbstractByteBuf, wrapped: ByteBuf, readerIndex: Int, writerIndex: Int, maxCapacity: Int
    ): U {
        var wrapped: ByteBuf? = wrapped
        wrapped!!.retain() // Retain up front to ensure the parent is accessible before doing more work.
        parent = wrapped
        rootParent = unwrapped

        try {
            maxCapacity(maxCapacity)
            setIndex0(readerIndex, writerIndex) // It is assumed the bounds checking is done by the caller.
            resetRefCnt()

            val castThis = this as U
            wrapped = null
            return castThis
        } finally {
            if (wrapped != null) {
                parent = null
                rootParent = null
                wrapped.release()
            }
        }
    }

    final override fun deallocate() {
        // We need to first store a reference to the parent before recycle this instance. This is needed as
        // otherwise it is possible that the same AbstractPooledDerivedByteBuf is again obtained and init(...) is
        // called before we actually have a chance to call release(). This leads to call release() on the wrong parent.
        val parent = this.parent!!
        // Remove references to parent and root so that they can be GCed for leak detection [netty/netty#14247]
        this.parent = null
        this.rootParent = null
        recyclerHandle.unguardedRecycle(this)
        parent.release()
    }

    final override fun alloc(): ByteBufAllocator = unwrap().alloc()

    @Deprecated("Deprecated in Java")
    final override fun order(): ByteOrder = unwrap().order()

    override fun isReadOnly(): Boolean = unwrap().isReadOnly()

    final override fun isDirect(): Boolean = unwrap().isDirect()

    override fun hasArray(): Boolean = unwrap().hasArray()

    override fun array(): ByteArray = unwrap().array()

    override fun hasMemoryAddress(): Boolean = unwrap().hasMemoryAddress()

    override fun isContiguous(): Boolean = unwrap().isContiguous()

    final override fun nioBufferCount(): Int = unwrap().nioBufferCount()

    final override fun internalNioBuffer(index: Int, length: Int): ByteBuffer = nioBuffer(index, length)

    final override fun retainedSlice(): ByteBuf {
        val index = readerIndex()
        return retainedSlice(index, writerIndex() - index)
    }

    override fun slice(index: Int, length: Int): ByteBuf {
        ensureAccessible()
        // All reference count methods should be inherited from this object (this is the "parent").
        return PooledNonRetainedSlicedByteBuf(this, unwrap(), index, length)
    }

    fun duplicate0(): ByteBuf {
        ensureAccessible()
        // All reference count methods should be inherited from this object (this is the "parent").
        return PooledNonRetainedDuplicateByteBuf(this, unwrap())
    }

    private class PooledNonRetainedDuplicateByteBuf(
        private val referenceCountDelegate: ByteBuf,
        buffer: AbstractByteBuf
    ) : UnpooledDuplicatedByteBuf(buffer) {

        override fun isAccessible0(): Boolean = referenceCountDelegate.isAccessible()

        override fun refCnt0(): Int = referenceCountDelegate.refCnt()

        override fun retain0(): ByteBuf {
            referenceCountDelegate.retain()
            return this
        }

        override fun retain0(increment: Int): ByteBuf {
            referenceCountDelegate.retain(increment)
            return this
        }

        override fun touch0(): ByteBuf {
            referenceCountDelegate.touch()
            return this
        }

        override fun touch0(hint: Any?): ByteBuf {
            referenceCountDelegate.touch(hint)
            return this
        }

        override fun release0(): Boolean = referenceCountDelegate.release()

        override fun release0(decrement: Int): Boolean = referenceCountDelegate.release(decrement)

        override fun duplicate(): ByteBuf {
            ensureAccessible()
            return PooledNonRetainedDuplicateByteBuf(referenceCountDelegate, this)
        }

        override fun retainedDuplicate(): ByteBuf =
            PooledDuplicatedByteBuf.newInstance(unwrap() as AbstractByteBuf, this, readerIndex(), writerIndex())

        override fun slice(index: Int, length: Int): ByteBuf {
            checkIndex(index, length)
            return PooledNonRetainedSlicedByteBuf(referenceCountDelegate, unwrap() as AbstractByteBuf, index, length)
        }

        override fun retainedSlice(): ByteBuf {
            // Capacity is not allowed to change for a sliced ByteBuf, so length == capacity()
            return retainedSlice(readerIndex(), capacity())
        }

        override fun retainedSlice(index: Int, length: Int): ByteBuf =
            PooledSlicedByteBuf.newInstance(unwrap() as AbstractByteBuf, this, index, length)
    }

    private class PooledNonRetainedSlicedByteBuf(
        private val referenceCountDelegate: ByteBuf,
        buffer: AbstractByteBuf,
        index: Int,
        length: Int
    ) : UnpooledSlicedByteBuf(buffer, index, length) {

        override fun isAccessible0(): Boolean = referenceCountDelegate.isAccessible()

        override fun refCnt0(): Int = referenceCountDelegate.refCnt()

        override fun retain0(): ByteBuf {
            referenceCountDelegate.retain()
            return this
        }

        override fun retain0(increment: Int): ByteBuf {
            referenceCountDelegate.retain(increment)
            return this
        }

        override fun touch0(): ByteBuf {
            referenceCountDelegate.touch()
            return this
        }

        override fun touch0(hint: Any?): ByteBuf {
            referenceCountDelegate.touch(hint)
            return this
        }

        override fun release0(): Boolean = referenceCountDelegate.release()

        override fun release0(decrement: Int): Boolean = referenceCountDelegate.release(decrement)

        override fun duplicate(): ByteBuf {
            ensureAccessible()
            return PooledNonRetainedDuplicateByteBuf(referenceCountDelegate, unwrap() as AbstractByteBuf)
                .setIndex(idx(readerIndex()), idx(writerIndex()))
        }

        override fun retainedDuplicate(): ByteBuf =
            PooledDuplicatedByteBuf.newInstance(unwrap() as AbstractByteBuf, this, idx(readerIndex()), idx(writerIndex()))

        override fun slice(index: Int, length: Int): ByteBuf {
            checkIndex(index, length)
            return PooledNonRetainedSlicedByteBuf(referenceCountDelegate, unwrap() as AbstractByteBuf, idx(index), length)
        }

        override fun retainedSlice(): ByteBuf {
            // Capacity is not allowed to change for a sliced ByteBuf, so length == capacity()
            return retainedSlice(0, capacity())
        }

        override fun retainedSlice(index: Int, length: Int): ByteBuf =
            PooledSlicedByteBuf.newInstance(unwrap() as AbstractByteBuf, this, idx(index), length)
    }
}
