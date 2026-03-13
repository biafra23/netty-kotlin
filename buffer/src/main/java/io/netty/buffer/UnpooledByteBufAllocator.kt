/*
 * Copyright 2012 The Netty Project
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

import io.netty.util.internal.CleanableDirectBuffer
import io.netty.util.internal.PlatformDependent
import io.netty.util.internal.StringUtil
import java.nio.ByteBuffer
import java.util.concurrent.atomic.LongAdder

/**
 * Simplistic [ByteBufAllocator] implementation that does not pool anything.
 */
class UnpooledByteBufAllocator : AbstractByteBufAllocator, ByteBufAllocatorMetricProvider {

    private val metric = UnpooledByteBufAllocatorMetric()
    private val disableLeakDetector: Boolean
    private val noCleaner: Boolean

    /**
     * Create a new instance which uses leak-detection for direct buffers.
     *
     * @param preferDirect `true` if [buffer] should try to allocate a direct buffer rather than
     *                     a heap buffer
     */
    @JvmOverloads
    constructor(preferDirect: Boolean, disableLeakDetector: Boolean = false) : this(
        preferDirect, disableLeakDetector, PlatformDependent.useDirectBufferNoCleaner()
    )

    /**
     * Create a new instance
     *
     * @param preferDirect `true` if [buffer] should try to allocate a direct buffer rather than
     *                     a heap buffer
     * @param disableLeakDetector `true` if the leak-detection should be disabled completely for this
     *                            allocator. This can be useful if the user just want to depend on the GC to handle
     *                            direct buffers when not explicit released.
     * @param tryNoCleaner `true` if we should try to use [PlatformDependent.allocateDirectNoCleaner]
     *                            to allocate direct memory.
     */
    constructor(preferDirect: Boolean, disableLeakDetector: Boolean, tryNoCleaner: Boolean) : super(preferDirect) {
        this.disableLeakDetector = disableLeakDetector
        noCleaner = tryNoCleaner && PlatformDependent.hasUnsafe() &&
            PlatformDependent.hasDirectBufferNoCleanerConstructor()
    }

    override fun newHeapBuffer(initialCapacity: Int, maxCapacity: Int): ByteBuf =
        if (PlatformDependent.hasUnsafe()) {
            InstrumentedUnpooledUnsafeHeapByteBuf(this, initialCapacity, maxCapacity)
        } else {
            InstrumentedUnpooledHeapByteBuf(this, initialCapacity, maxCapacity)
        }

    override fun newDirectBuffer(initialCapacity: Int, maxCapacity: Int): ByteBuf {
        val buf: ByteBuf = if (PlatformDependent.hasUnsafe()) {
            if (noCleaner) {
                InstrumentedUnpooledUnsafeNoCleanerDirectByteBuf(this, initialCapacity, maxCapacity)
            } else {
                InstrumentedUnpooledUnsafeDirectByteBuf(this, initialCapacity, maxCapacity)
            }
        } else {
            InstrumentedUnpooledDirectByteBuf(this, initialCapacity, maxCapacity)
        }
        return if (disableLeakDetector) buf else toLeakAwareBuffer(buf)
    }

    override fun compositeHeapBuffer(maxNumComponents: Int): CompositeByteBuf {
        val buf = CompositeByteBuf(this, false, maxNumComponents)
        return if (disableLeakDetector) buf else toLeakAwareBuffer(buf)
    }

    override fun compositeDirectBuffer(maxNumComponents: Int): CompositeByteBuf {
        val buf = CompositeByteBuf(this, true, maxNumComponents)
        return if (disableLeakDetector) buf else toLeakAwareBuffer(buf)
    }

    override fun isDirectBufferPooled(): Boolean = false

    override fun metric(): ByteBufAllocatorMetric = metric

    internal fun incrementDirect(amount: Int) {
        metric.directCounter.add(amount.toLong())
    }

    internal fun decrementDirect(amount: Int) {
        metric.directCounter.add(-amount.toLong())
    }

    internal fun incrementHeap(amount: Int) {
        metric.heapCounter.add(amount.toLong())
    }

    internal fun decrementHeap(amount: Int) {
        metric.heapCounter.add(-amount.toLong())
    }

    private class InstrumentedUnpooledUnsafeHeapByteBuf(
        alloc: UnpooledByteBufAllocator, initialCapacity: Int, maxCapacity: Int
    ) : UnpooledUnsafeHeapByteBuf(alloc, initialCapacity, maxCapacity) {

        override fun allocateArray(initialCapacity: Int): ByteArray {
            val bytes = super.allocateArray(initialCapacity)
            (alloc() as UnpooledByteBufAllocator).incrementHeap(bytes.size)
            return bytes
        }

        override fun freeArray(array: ByteArray) {
            val length = array.size
            super.freeArray(array)
            (alloc() as UnpooledByteBufAllocator).decrementHeap(length)
        }
    }

    private class InstrumentedUnpooledHeapByteBuf(
        alloc: UnpooledByteBufAllocator, initialCapacity: Int, maxCapacity: Int
    ) : UnpooledHeapByteBuf(alloc, initialCapacity, maxCapacity) {

        override fun allocateArray(initialCapacity: Int): ByteArray {
            val bytes = super.allocateArray(initialCapacity)
            (alloc() as UnpooledByteBufAllocator).incrementHeap(bytes.size)
            return bytes
        }

        override fun freeArray(array: ByteArray) {
            val length = array.size
            super.freeArray(array)
            (alloc() as UnpooledByteBufAllocator).decrementHeap(length)
        }
    }

    private class InstrumentedUnpooledUnsafeNoCleanerDirectByteBuf(
        alloc: UnpooledByteBufAllocator, initialCapacity: Int, maxCapacity: Int
    ) : UnpooledUnsafeNoCleanerDirectByteBuf(alloc, initialCapacity, maxCapacity) {

        override fun allocateDirectBuffer(capacity: Int): CleanableDirectBuffer {
            val buffer = super.allocateDirectBuffer(capacity)
            return DecrementingCleanableDirectBuffer(alloc(), buffer)
        }

        override fun reallocateDirect(oldBuffer: CleanableDirectBuffer, initialCapacity: Int): CleanableDirectBuffer {
            val capacity = oldBuffer.buffer().capacity()
            val buffer = super.reallocateDirect(oldBuffer, initialCapacity)
            return DecrementingCleanableDirectBuffer(alloc(), buffer, buffer.buffer().capacity() - capacity)
        }
    }

    private class InstrumentedUnpooledUnsafeDirectByteBuf(
        alloc: UnpooledByteBufAllocator, initialCapacity: Int, maxCapacity: Int
    ) : UnpooledUnsafeDirectByteBuf(alloc, initialCapacity, maxCapacity) {

        override fun allocateDirectBuffer(capacity: Int): CleanableDirectBuffer {
            val buffer = super.allocateDirectBuffer(capacity)
            return DecrementingCleanableDirectBuffer(alloc(), buffer)
        }

        override fun allocateDirect(initialCapacity: Int): ByteBuffer {
            throw UnsupportedOperationException()
        }

        override fun freeDirect(buffer: ByteBuffer) {
            throw UnsupportedOperationException()
        }
    }

    private class InstrumentedUnpooledDirectByteBuf(
        alloc: UnpooledByteBufAllocator, initialCapacity: Int, maxCapacity: Int
    ) : UnpooledDirectByteBuf(alloc, initialCapacity, maxCapacity) {

        override fun allocateDirectBuffer(initialCapacity: Int): CleanableDirectBuffer {
            val buffer = super.allocateDirectBuffer(initialCapacity)
            return DecrementingCleanableDirectBuffer(alloc(), buffer)
        }

        override fun allocateDirect(initialCapacity: Int): ByteBuffer {
            throw UnsupportedOperationException()
        }

        override fun freeDirect(buffer: ByteBuffer) {
            throw UnsupportedOperationException()
        }
    }

    private class DecrementingCleanableDirectBuffer : CleanableDirectBuffer {
        private val alloc: UnpooledByteBufAllocator
        private val delegate: CleanableDirectBuffer

        constructor(alloc: ByteBufAllocator, delegate: CleanableDirectBuffer) :
            this(alloc, delegate, delegate.buffer().capacity())

        constructor(alloc: ByteBufAllocator, delegate: CleanableDirectBuffer, capacityConsumed: Int) {
            this.alloc = alloc as UnpooledByteBufAllocator
            this.alloc.incrementDirect(capacityConsumed)
            this.delegate = delegate
        }

        override fun buffer(): ByteBuffer = delegate.buffer()

        override fun clean() {
            val capacity = delegate.buffer().capacity()
            delegate.clean()
            alloc.decrementDirect(capacity)
        }

        override fun hasMemoryAddress(): Boolean = delegate.hasMemoryAddress()

        override fun memoryAddress(): Long = delegate.memoryAddress()
    }

    private class UnpooledByteBufAllocatorMetric : ByteBufAllocatorMetric {
        val directCounter = LongAdder()
        val heapCounter = LongAdder()

        override fun usedHeapMemory(): Long = heapCounter.sum()

        override fun usedDirectMemory(): Long = directCounter.sum()

        override fun toString(): String =
            "${StringUtil.simpleClassName(this)}(usedHeapMemory: ${usedHeapMemory()}; usedDirectMemory: ${usedDirectMemory()})"
    }

    companion object {
        /**
         * Default instance which uses leak-detection for direct buffers.
         */
        @JvmField
        val DEFAULT = UnpooledByteBufAllocator(PlatformDependent.directBufferPreferred())
    }
}
