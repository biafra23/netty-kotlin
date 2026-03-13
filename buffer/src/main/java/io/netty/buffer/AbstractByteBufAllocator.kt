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

import io.netty.util.ResourceLeakDetector
import io.netty.util.ResourceLeakTracker
import io.netty.util.internal.MathUtil
import io.netty.util.internal.ObjectUtil.checkPositiveOrZero
import io.netty.util.internal.PlatformDependent
import io.netty.util.internal.StringUtil

/**
 * Skeletal [ByteBufAllocator] implementation to extend.
 */
abstract class AbstractByteBufAllocator
/**
 * Create new instance
 *
 * @param preferDirect `true` if [buffer] should try to allocate a direct buffer rather than
 *                     a heap buffer
 */
@JvmOverloads
protected constructor(preferDirect: Boolean = false) : ByteBufAllocator {

    private val directByDefault: Boolean = preferDirect && PlatformDependent.canReliabilyFreeDirectBuffers()
    private val emptyBuf: ByteBuf = EmptyByteBuf(this)

    override fun buffer(): ByteBuf =
        if (directByDefault) directBuffer() else heapBuffer()

    override fun buffer(initialCapacity: Int): ByteBuf =
        if (directByDefault) directBuffer(initialCapacity) else heapBuffer(initialCapacity)

    override fun buffer(initialCapacity: Int, maxCapacity: Int): ByteBuf =
        if (directByDefault) directBuffer(initialCapacity, maxCapacity) else heapBuffer(initialCapacity, maxCapacity)

    override fun ioBuffer(): ByteBuf =
        if (PlatformDependent.canReliabilyFreeDirectBuffers() || isDirectBufferPooled()) {
            directBuffer(DEFAULT_INITIAL_CAPACITY)
        } else {
            heapBuffer(DEFAULT_INITIAL_CAPACITY)
        }

    override fun ioBuffer(initialCapacity: Int): ByteBuf =
        if (PlatformDependent.canReliabilyFreeDirectBuffers() || isDirectBufferPooled()) {
            directBuffer(initialCapacity)
        } else {
            heapBuffer(initialCapacity)
        }

    override fun ioBuffer(initialCapacity: Int, maxCapacity: Int): ByteBuf =
        if (PlatformDependent.canReliabilyFreeDirectBuffers() || isDirectBufferPooled()) {
            directBuffer(initialCapacity, maxCapacity)
        } else {
            heapBuffer(initialCapacity, maxCapacity)
        }

    override fun heapBuffer(): ByteBuf =
        heapBuffer(DEFAULT_INITIAL_CAPACITY, DEFAULT_MAX_CAPACITY)

    override fun heapBuffer(initialCapacity: Int): ByteBuf =
        heapBuffer(initialCapacity, DEFAULT_MAX_CAPACITY)

    override fun heapBuffer(initialCapacity: Int, maxCapacity: Int): ByteBuf {
        if (initialCapacity == 0 && maxCapacity == 0) {
            return emptyBuf
        }
        validate(initialCapacity, maxCapacity)
        return newHeapBuffer(initialCapacity, maxCapacity)
    }

    override fun directBuffer(): ByteBuf =
        directBuffer(DEFAULT_INITIAL_CAPACITY, DEFAULT_MAX_CAPACITY)

    override fun directBuffer(initialCapacity: Int): ByteBuf =
        directBuffer(initialCapacity, DEFAULT_MAX_CAPACITY)

    override fun directBuffer(initialCapacity: Int, maxCapacity: Int): ByteBuf {
        if (initialCapacity == 0 && maxCapacity == 0) {
            return emptyBuf
        }
        validate(initialCapacity, maxCapacity)
        return newDirectBuffer(initialCapacity, maxCapacity)
    }

    override fun compositeBuffer(): CompositeByteBuf =
        if (directByDefault) compositeDirectBuffer() else compositeHeapBuffer()

    override fun compositeBuffer(maxNumComponents: Int): CompositeByteBuf =
        if (directByDefault) compositeDirectBuffer(maxNumComponents) else compositeHeapBuffer(maxNumComponents)

    override fun compositeHeapBuffer(): CompositeByteBuf =
        compositeHeapBuffer(DEFAULT_MAX_COMPONENTS)

    override fun compositeHeapBuffer(maxNumComponents: Int): CompositeByteBuf =
        toLeakAwareBuffer(CompositeByteBuf(this, false, maxNumComponents))

    override fun compositeDirectBuffer(): CompositeByteBuf =
        compositeDirectBuffer(DEFAULT_MAX_COMPONENTS)

    override fun compositeDirectBuffer(maxNumComponents: Int): CompositeByteBuf =
        toLeakAwareBuffer(CompositeByteBuf(this, true, maxNumComponents))

    /**
     * Create a heap [ByteBuf] with the given initialCapacity and maxCapacity.
     */
    protected abstract fun newHeapBuffer(initialCapacity: Int, maxCapacity: Int): ByteBuf

    /**
     * Create a direct [ByteBuf] with the given initialCapacity and maxCapacity.
     */
    protected abstract fun newDirectBuffer(initialCapacity: Int, maxCapacity: Int): ByteBuf

    override fun toString(): String =
        "${StringUtil.simpleClassName(this)}(directByDefault: $directByDefault)"

    override fun calculateNewCapacity(minNewCapacity: Int, maxCapacity: Int): Int {
        checkPositiveOrZero(minNewCapacity, "minNewCapacity")
        if (minNewCapacity > maxCapacity) {
            throw IllegalArgumentException(
                "minNewCapacity: $minNewCapacity (expected: not greater than maxCapacity($maxCapacity)"
            )
        }
        val threshold = CALCULATE_THRESHOLD // 4 MiB page

        if (minNewCapacity == threshold) {
            return threshold
        }

        // If over threshold, do not double but just increase by threshold.
        if (minNewCapacity > threshold) {
            var newCapacity = minNewCapacity / threshold * threshold
            newCapacity = if (newCapacity > maxCapacity - threshold) {
                maxCapacity
            } else {
                newCapacity + threshold
            }
            return newCapacity
        }

        // 64 <= newCapacity is a power of 2 <= threshold
        val newCapacity = MathUtil.findNextPositivePowerOfTwo(Math.max(minNewCapacity, 64))
        return Math.min(newCapacity, maxCapacity)
    }

    companion object {
        @JvmField
        internal val DEFAULT_INITIAL_CAPACITY = 256
        @JvmField
        internal val DEFAULT_MAX_CAPACITY = Int.MAX_VALUE
        @JvmField
        internal val DEFAULT_MAX_COMPONENTS = 16
        @JvmField
        internal val CALCULATE_THRESHOLD = 1048576 * 4 // 4 MiB page

        init {
            ResourceLeakDetector.addExclusions(AbstractByteBufAllocator::class.java, "toLeakAwareBuffer")
        }

        @JvmStatic
        protected fun toLeakAwareBuffer(buf: ByteBuf): ByteBuf {
            val leak: ResourceLeakTracker<ByteBuf>? = AbstractByteBuf.leakDetector.track(buf)
            return if (leak != null) {
                if (AbstractByteBuf.leakDetector.isRecordEnabled()) {
                    AdvancedLeakAwareByteBuf(buf, leak)
                } else {
                    SimpleLeakAwareByteBuf(buf, leak)
                }
            } else {
                buf
            }
        }

        @JvmStatic
        protected fun toLeakAwareBuffer(buf: CompositeByteBuf): CompositeByteBuf {
            val leak: ResourceLeakTracker<ByteBuf>? = AbstractByteBuf.leakDetector.track(buf)
            return if (leak != null) {
                if (AbstractByteBuf.leakDetector.isRecordEnabled()) {
                    AdvancedLeakAwareCompositeByteBuf(buf, leak)
                } else {
                    SimpleLeakAwareCompositeByteBuf(buf, leak)
                }
            } else {
                buf
            }
        }

        private fun validate(initialCapacity: Int, maxCapacity: Int) {
            checkPositiveOrZero(initialCapacity, "initialCapacity")
            if (initialCapacity > maxCapacity) {
                throw IllegalArgumentException(
                    "initialCapacity: $initialCapacity (expected: not greater than maxCapacity($maxCapacity)"
                )
            }
        }
    }
}
