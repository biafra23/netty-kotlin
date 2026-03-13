/*
 * Copyright 2015 The Netty Project
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
 * Expose metrics for an arena.
 */
interface PoolArenaMetric : SizeClassesMetric {

    /**
     * Returns the number of thread caches backed by this arena.
     */
    fun numThreadCaches(): Int

    /**
     * Returns the number of tiny sub-pages for the arena.
     *
     * @deprecated Tiny sub-pages have been merged into small sub-pages.
     */
    @Deprecated("Tiny sub-pages have been merged into small sub-pages.")
    fun numTinySubpages(): Int

    /**
     * Returns the number of small sub-pages for the arena.
     */
    fun numSmallSubpages(): Int

    /**
     * Returns the number of chunk lists for the arena.
     */
    fun numChunkLists(): Int

    /**
     * Returns an unmodifiable [List] which holds [PoolSubpageMetric]s for tiny sub-pages.
     *
     * @deprecated Tiny sub-pages have been merged into small sub-pages.
     */
    @Deprecated("Tiny sub-pages have been merged into small sub-pages.")
    fun tinySubpages(): List<PoolSubpageMetric>

    /**
     * Returns an unmodifiable [List] which holds [PoolSubpageMetric]s for small sub-pages.
     */
    fun smallSubpages(): List<PoolSubpageMetric>

    /**
     * Returns an unmodifiable [List] which holds [PoolChunkListMetric]s.
     */
    fun chunkLists(): List<PoolChunkListMetric>

    /**
     * Return the number of allocations done via the arena. This includes all sizes.
     */
    fun numAllocations(): Long

    /**
     * Return the number of tiny allocations done via the arena.
     *
     * @deprecated Tiny allocations have been merged into small allocations.
     */
    @Deprecated("Tiny allocations have been merged into small allocations.")
    fun numTinyAllocations(): Long

    /**
     * Return the number of small allocations done via the arena.
     */
    fun numSmallAllocations(): Long

    /**
     * Return the number of normal allocations done via the arena.
     */
    fun numNormalAllocations(): Long

    /**
     * Return the number of huge allocations done via the arena.
     */
    fun numHugeAllocations(): Long

    /**
     * Return the number of chunks allocations done via the arena, or -1 if not defined.
     */
    fun numChunkAllocations(): Long = -1

    /**
     * Return the number of deallocations done via the arena. This includes all sizes.
     */
    fun numDeallocations(): Long

    /**
     * Return the number of tiny deallocations done via the arena.
     *
     * @deprecated Tiny deallocations have been merged into small deallocations.
     */
    @Deprecated("Tiny deallocations have been merged into small deallocations.")
    fun numTinyDeallocations(): Long

    /**
     * Return the number of small deallocations done via the arena.
     */
    fun numSmallDeallocations(): Long

    /**
     * Return the number of normal deallocations done via the arena.
     */
    fun numNormalDeallocations(): Long

    /**
     * Return the number of huge deallocations done via the arena.
     */
    fun numHugeDeallocations(): Long

    /**
     * Return the number of chunk deallocations done via the arena, or -1 if not defined.
     */
    fun numChunkDeallocations(): Long = -1

    /**
     * Return the number of currently active allocations.
     */
    fun numActiveAllocations(): Long

    /**
     * Return the number of currently active tiny allocations.
     *
     * @deprecated Tiny allocations have been merged into small allocations.
     */
    @Deprecated("Tiny allocations have been merged into small allocations.")
    fun numActiveTinyAllocations(): Long

    /**
     * Return the number of currently active small allocations.
     */
    fun numActiveSmallAllocations(): Long

    /**
     * Return the number of currently active normal allocations.
     */
    fun numActiveNormalAllocations(): Long

    /**
     * Return the number of currently active huge allocations.
     */
    fun numActiveHugeAllocations(): Long

    /**
     * Return the number of currently active chunks, or -1 if not defined.
     */
    fun numActiveChunks(): Long = -1

    /**
     * Return the number of active bytes that are currently allocated by the arena.
     */
    fun numActiveBytes(): Long
}
