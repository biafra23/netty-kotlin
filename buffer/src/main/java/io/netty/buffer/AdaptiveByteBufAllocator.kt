/*
 * Copyright 2024 The Netty Project
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
import io.netty.util.internal.SystemPropertyUtil
import io.netty.util.internal.logging.InternalLoggerFactory

/**
 * An auto-tuning pooling [ByteBufAllocator], that follows an anti-generational hypothesis.
 *
 * **Note:** this allocator is **experimental**. It is recommended to roll out usage slowly,
 * and to carefully monitor application performance in the process.
 *
 * See the [AdaptivePoolingAllocator] class documentation for implementation details.
 */
class AdaptiveByteBufAllocator : AbstractByteBufAllocator,
    ByteBufAllocatorMetricProvider, ByteBufAllocatorMetric {

    private val direct: AdaptivePoolingAllocator
    private val heap: AdaptivePoolingAllocator

    constructor() : this(!PlatformDependent.isExplicitNoPreferDirect())

    constructor(preferDirect: Boolean) : this(
        preferDirect, DEFAULT_USE_CACHED_MAGAZINES_FOR_NON_EVENT_LOOP_THREADS
    )

    constructor(preferDirect: Boolean, useCacheForNonEventLoopThreads: Boolean) : super(preferDirect) {
        direct = AdaptivePoolingAllocator(DirectChunkAllocator(this), useCacheForNonEventLoopThreads)
        heap = AdaptivePoolingAllocator(HeapChunkAllocator(this), useCacheForNonEventLoopThreads)
    }

    override fun newHeapBuffer(initialCapacity: Int, maxCapacity: Int): ByteBuf =
        toLeakAwareBuffer(heap.allocate(initialCapacity, maxCapacity))

    override fun newDirectBuffer(initialCapacity: Int, maxCapacity: Int): ByteBuf =
        toLeakAwareBuffer(direct.allocate(initialCapacity, maxCapacity))

    override fun isDirectBufferPooled(): Boolean = true

    override fun usedHeapMemory(): Long = heap.usedMemory()

    override fun usedDirectMemory(): Long = direct.usedMemory()

    override fun metric(): ByteBufAllocatorMetric = this

    private class HeapChunkAllocator(
        private val allocator: ByteBufAllocator
    ) : AdaptivePoolingAllocator.ChunkAllocator {

        override fun allocate(initialCapacity: Int, maxCapacity: Int): AbstractByteBuf =
            if (PlatformDependent.hasUnsafe()) {
                UnpooledUnsafeHeapByteBuf(allocator, initialCapacity, maxCapacity)
            } else {
                UnpooledHeapByteBuf(allocator, initialCapacity, maxCapacity)
            }
    }

    private class DirectChunkAllocator(
        private val allocator: ByteBufAllocator
    ) : AdaptivePoolingAllocator.ChunkAllocator {

        override fun allocate(initialCapacity: Int, maxCapacity: Int): AbstractByteBuf =
            if (PlatformDependent.hasUnsafe()) {
                UnsafeByteBufUtil.newUnsafeDirectByteBuf(allocator, initialCapacity, maxCapacity)
            } else {
                UnpooledDirectByteBuf(allocator, initialCapacity, maxCapacity)
            }
    }

    companion object {
        private val logger = InternalLoggerFactory.getInstance(AdaptiveByteBufAllocator::class.java)
        private val DEFAULT_USE_CACHED_MAGAZINES_FOR_NON_EVENT_LOOP_THREADS: Boolean

        init {
            DEFAULT_USE_CACHED_MAGAZINES_FOR_NON_EVENT_LOOP_THREADS = SystemPropertyUtil.getBoolean(
                "io.netty.allocator.useCachedMagazinesForNonEventLoopThreads", false
            )
            logger.debug(
                "-Dio.netty.allocator.useCachedMagazinesForNonEventLoopThreads: {}",
                DEFAULT_USE_CACHED_MAGAZINES_FOR_NON_EVENT_LOOP_THREADS
            )
        }
    }
}
