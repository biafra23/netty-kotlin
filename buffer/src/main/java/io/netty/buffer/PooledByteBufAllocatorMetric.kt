/*
 * Copyright 2017 The Netty Project
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

import io.netty.util.internal.StringUtil

/**
 * Exposed metric for [PooledByteBufAllocator].
 */
@Suppress("deprecation")
class PooledByteBufAllocatorMetric internal constructor(
    private val allocator: PooledByteBufAllocator
) : ByteBufAllocatorMetric {

    /**
     * Return the number of heap arenas.
     */
    fun numHeapArenas(): Int = allocator.numHeapArenas()

    /**
     * Return the number of direct arenas.
     */
    fun numDirectArenas(): Int = allocator.numDirectArenas()

    /**
     * Return a [List] of all heap [PoolArenaMetric]s that are provided by this pool.
     */
    fun heapArenas(): List<PoolArenaMetric> = allocator.heapArenas()

    /**
     * Return a [List] of all direct [PoolArenaMetric]s that are provided by this pool.
     */
    fun directArenas(): List<PoolArenaMetric> = allocator.directArenas()

    /**
     * Return the number of thread local caches used by this [PooledByteBufAllocator].
     */
    fun numThreadLocalCaches(): Int = allocator.numThreadLocalCaches()

    /**
     * Return the size of the tiny cache.
     *
     * @deprecated Tiny caches have been merged into small caches.
     */
    @Deprecated("Tiny caches have been merged into small caches.")
    fun tinyCacheSize(): Int = allocator.tinyCacheSize()

    /**
     * Return the size of the small cache.
     */
    fun smallCacheSize(): Int = allocator.smallCacheSize()

    /**
     * Return the size of the normal cache.
     */
    fun normalCacheSize(): Int = allocator.normalCacheSize()

    /**
     * Return the chunk size for an arena.
     */
    fun chunkSize(): Int = allocator.chunkSize()

    override fun usedHeapMemory(): Long = allocator.usedHeapMemory()

    override fun usedDirectMemory(): Long = allocator.usedDirectMemory()

    override fun toString(): String {
        return "${StringUtil.simpleClassName(this)}(" +
            "usedHeapMemory: ${usedHeapMemory()}; " +
            "usedDirectMemory: ${usedDirectMemory()}; " +
            "numHeapArenas: ${numHeapArenas()}; " +
            "numDirectArenas: ${numDirectArenas()}; " +
            "smallCacheSize: ${smallCacheSize()}; " +
            "normalCacheSize: ${normalCacheSize()}; " +
            "numThreadLocalCaches: ${numThreadLocalCaches()}; " +
            "chunkSize: ${chunkSize()})"
    }
}
