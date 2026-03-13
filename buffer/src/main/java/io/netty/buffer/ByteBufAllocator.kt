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

/**
 * Implementations are responsible to allocate buffers. Implementations of this interface are expected to be
 * thread-safe.
 */
interface ByteBufAllocator {

    companion object {
        @JvmField
        val DEFAULT: ByteBufAllocator = ByteBufUtil.DEFAULT_ALLOCATOR
    }

    /**
     * Allocate a [ByteBuf]. If it is a direct or heap buffer
     * depends on the actual implementation.
     */
    fun buffer(): ByteBuf

    /**
     * Allocate a [ByteBuf] with the given initial capacity.
     * If it is a direct or heap buffer depends on the actual implementation.
     */
    fun buffer(initialCapacity: Int): ByteBuf

    /**
     * Allocate a [ByteBuf] with the given initial capacity and the given
     * maximal capacity. If it is a direct or heap buffer depends on the actual
     * implementation.
     */
    fun buffer(initialCapacity: Int, maxCapacity: Int): ByteBuf

    /**
     * Allocate a [ByteBuf], preferably a direct buffer which is suitable for I/O.
     */
    fun ioBuffer(): ByteBuf

    /**
     * Allocate a [ByteBuf], preferably a direct buffer which is suitable for I/O.
     */
    fun ioBuffer(initialCapacity: Int): ByteBuf

    /**
     * Allocate a [ByteBuf], preferably a direct buffer which is suitable for I/O.
     */
    fun ioBuffer(initialCapacity: Int, maxCapacity: Int): ByteBuf

    /**
     * Allocate a heap [ByteBuf].
     */
    fun heapBuffer(): ByteBuf

    /**
     * Allocate a heap [ByteBuf] with the given initial capacity.
     */
    fun heapBuffer(initialCapacity: Int): ByteBuf

    /**
     * Allocate a heap [ByteBuf] with the given initial capacity and the given
     * maximal capacity.
     */
    fun heapBuffer(initialCapacity: Int, maxCapacity: Int): ByteBuf

    /**
     * Allocate a direct [ByteBuf].
     */
    fun directBuffer(): ByteBuf

    /**
     * Allocate a direct [ByteBuf] with the given initial capacity.
     */
    fun directBuffer(initialCapacity: Int): ByteBuf

    /**
     * Allocate a direct [ByteBuf] with the given initial capacity and the given
     * maximal capacity.
     */
    fun directBuffer(initialCapacity: Int, maxCapacity: Int): ByteBuf

    /**
     * Allocate a [CompositeByteBuf].
     * If it is a direct or heap buffer depends on the actual implementation.
     */
    fun compositeBuffer(): CompositeByteBuf

    /**
     * Allocate a [CompositeByteBuf] with the given maximum number of components that can be stored in it.
     * If it is a direct or heap buffer depends on the actual implementation.
     */
    fun compositeBuffer(maxNumComponents: Int): CompositeByteBuf

    /**
     * Allocate a heap [CompositeByteBuf].
     */
    fun compositeHeapBuffer(): CompositeByteBuf

    /**
     * Allocate a heap [CompositeByteBuf] with the given maximum number of components that can be stored in it.
     */
    fun compositeHeapBuffer(maxNumComponents: Int): CompositeByteBuf

    /**
     * Allocate a direct [CompositeByteBuf].
     */
    fun compositeDirectBuffer(): CompositeByteBuf

    /**
     * Allocate a direct [CompositeByteBuf] with the given maximum number of components that can be stored in it.
     */
    fun compositeDirectBuffer(maxNumComponents: Int): CompositeByteBuf

    /**
     * Returns `true` if direct [ByteBuf]'s are pooled.
     */
    fun isDirectBufferPooled(): Boolean

    /**
     * Calculate the new capacity of a [ByteBuf] that is used when a [ByteBuf] needs to expand by the
     * [minNewCapacity] with [maxCapacity] as upper-bound.
     */
    fun calculateNewCapacity(minNewCapacity: Int, maxCapacity: Int): Int
}
