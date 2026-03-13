/*
 * Copyright 2020 The Netty Project
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

import io.netty.buffer.PoolThreadCache.Companion.log2

/**
 * SizeClasses requires `pageShifts` to be defined prior to inclusion,
 * and it in turn defines:
 *
 *   LOG2_SIZE_CLASS_GROUP: Log of size class count for each size doubling.
 *   LOG2_MAX_LOOKUP_SIZE: Log of max size class in the lookup table.
 *   sizeClasses: Complete table of [index, log2Group, log2Delta, nDelta, isMultiPageSize,
 *                 isSubPage, log2DeltaLookup] tuples.
 */
internal class SizeClasses(
    @JvmField val pageSize: Int,
    @JvmField val pageShifts: Int,
    @JvmField val chunkSize: Int,
    @JvmField val directMemoryCacheAlignment: Int
) : SizeClassesMetric {

    @JvmField val nSizes: Int
    @JvmField val nSubpages: Int
    @JvmField val nPSizes: Int
    @JvmField val lookupMaxSize: Int
    @JvmField val smallMaxSizeIdx: Int

    private val pageIdx2sizeTab: IntArray
    // lookup table for sizeIdx < nSizes
    private val sizeIdx2sizeTab: IntArray
    // lookup table used for size <= lookupMaxClass
    // spacing is 1 << LOG2_QUANTUM, so the size of array is lookupMaxClass >> LOG2_QUANTUM
    private val size2idxTab: IntArray

    init {
        val group = log2(chunkSize) - LOG2_QUANTUM - LOG2_SIZE_CLASS_GROUP + 1

        // generate size classes
        // [index, log2Group, log2Delta, nDelta, isMultiPageSize, isSubPage, log2DeltaLookup]
        val sizeClasses = Array(group shl LOG2_SIZE_CLASS_GROUP) { ShortArray(7) }

        var normalMaxSize = -1
        var nSizes = 0
        var size = 0

        var log2Group = LOG2_QUANTUM
        var log2Delta = LOG2_QUANTUM
        val ndeltaLimit = 1 shl LOG2_SIZE_CLASS_GROUP

        // First small group, nDelta start at 0.
        // first size class is 1 << LOG2_QUANTUM
        for (nDelta in 0 until ndeltaLimit) {
            val sizeClass = newSizeClass(nSizes, log2Group, log2Delta, nDelta, pageShifts)
            sizeClasses[nSizes] = sizeClass
            size = sizeOf(sizeClass, directMemoryCacheAlignment)
            nSizes++
        }

        log2Group += LOG2_SIZE_CLASS_GROUP

        // All remaining groups, nDelta start at 1.
        while (size < chunkSize) {
            var nDelta = 1
            while (nDelta <= ndeltaLimit && size < chunkSize) {
                val sizeClass = newSizeClass(nSizes, log2Group, log2Delta, nDelta, pageShifts)
                sizeClasses[nSizes] = sizeClass
                size = sizeOf(sizeClass, directMemoryCacheAlignment)
                normalMaxSize = size
                nSizes++
                nDelta++
            }
            log2Group++
            log2Delta++
        }

        // chunkSize must be normalMaxSize
        assert(chunkSize == normalMaxSize)

        var smallMaxSizeIdx = 0
        var lookupMaxSize = 0
        var nPSizes = 0
        var nSubpages = 0
        for (idx in 0 until nSizes) {
            val sz = sizeClasses[idx]
            if (sz[PAGESIZE_IDX] == yes) {
                nPSizes++
            }
            if (sz[SUBPAGE_IDX] == yes) {
                nSubpages++
                smallMaxSizeIdx = idx
            }
            if (sz[LOG2_DELTA_LOOKUP_IDX] != no) {
                lookupMaxSize = sizeOf(sz, directMemoryCacheAlignment)
            }
        }
        this.smallMaxSizeIdx = smallMaxSizeIdx
        this.lookupMaxSize = lookupMaxSize
        this.nPSizes = nPSizes
        this.nSubpages = nSubpages
        this.nSizes = nSizes

        // generate lookup tables
        this.sizeIdx2sizeTab = newIdx2SizeTab(sizeClasses, nSizes, directMemoryCacheAlignment)
        this.pageIdx2sizeTab = newPageIdx2sizeTab(sizeClasses, nSizes, nPSizes, directMemoryCacheAlignment)
        this.size2idxTab = newSize2idxTab(lookupMaxSize, sizeClasses)
    }

    override fun sizeIdx2size(sizeIdx: Int): Int {
        return sizeIdx2sizeTab[sizeIdx]
    }

    override fun sizeIdx2sizeCompute(sizeIdx: Int): Int {
        val group = sizeIdx shr LOG2_SIZE_CLASS_GROUP
        val mod = sizeIdx and ((1 shl LOG2_SIZE_CLASS_GROUP) - 1)

        val groupSize = if (group == 0) 0
        else (1 shl (LOG2_QUANTUM + LOG2_SIZE_CLASS_GROUP - 1)) shl group

        val shift = if (group == 0) 1 else group
        val lgDelta = shift + LOG2_QUANTUM - 1
        val modSize = (mod + 1) shl lgDelta

        return groupSize + modSize
    }

    override fun pageIdx2size(pageIdx: Int): Long {
        return pageIdx2sizeTab[pageIdx].toLong()
    }

    override fun pageIdx2sizeCompute(pageIdx: Int): Long {
        val group = pageIdx shr LOG2_SIZE_CLASS_GROUP
        val mod = pageIdx and ((1 shl LOG2_SIZE_CLASS_GROUP) - 1)

        val groupSize: Long = if (group == 0) 0L
        else (1L shl (pageShifts + LOG2_SIZE_CLASS_GROUP - 1)) shl group

        val shift = if (group == 0) 1 else group
        val log2Delta = shift + pageShifts - 1
        val modSize = (mod + 1) shl log2Delta

        return groupSize + modSize
    }

    override fun size2SizeIdx(size: Int): Int {
        var size = size
        if (size == 0) {
            return 0
        }
        if (size > chunkSize) {
            return nSizes
        }

        size = alignSizeIfNeeded(size, directMemoryCacheAlignment)

        if (size <= lookupMaxSize) {
            // size-1 / MIN_TINY
            return size2idxTab[(size - 1) shr LOG2_QUANTUM]
        }

        val x = log2((size shl 1) - 1)
        val shift = if (x < LOG2_SIZE_CLASS_GROUP + LOG2_QUANTUM + 1)
            0 else x - (LOG2_SIZE_CLASS_GROUP + LOG2_QUANTUM)

        val group = shift shl LOG2_SIZE_CLASS_GROUP

        val log2Delta = if (x < LOG2_SIZE_CLASS_GROUP + LOG2_QUANTUM + 1)
            LOG2_QUANTUM else x - LOG2_SIZE_CLASS_GROUP - 1

        val mod = ((size - 1) shr log2Delta) and ((1 shl LOG2_SIZE_CLASS_GROUP) - 1)

        return group + mod
    }

    override fun pages2pageIdx(pages: Int): Int {
        return pages2pageIdxCompute(pages, false)
    }

    override fun pages2pageIdxFloor(pages: Int): Int {
        return pages2pageIdxCompute(pages, true)
    }

    private fun pages2pageIdxCompute(pages: Int, floor: Boolean): Int {
        val pageSize = pages shl pageShifts
        if (pageSize > chunkSize) {
            return nPSizes
        }

        val x = log2((pageSize shl 1) - 1)

        val shift = if (x < LOG2_SIZE_CLASS_GROUP + pageShifts)
            0 else x - (LOG2_SIZE_CLASS_GROUP + pageShifts)

        val group = shift shl LOG2_SIZE_CLASS_GROUP

        val log2Delta = if (x < LOG2_SIZE_CLASS_GROUP + pageShifts + 1)
            pageShifts else x - LOG2_SIZE_CLASS_GROUP - 1

        val mod = ((pageSize - 1) shr log2Delta) and ((1 shl LOG2_SIZE_CLASS_GROUP) - 1)

        var pageIdx = group + mod

        if (floor && pageIdx2sizeTab[pageIdx] > (pages shl pageShifts)) {
            pageIdx--
        }

        return pageIdx
    }

    override fun normalizeSize(size: Int): Int {
        var size = size
        if (size == 0) {
            return sizeIdx2sizeTab[0]
        }
        size = alignSizeIfNeeded(size, directMemoryCacheAlignment)
        if (size <= lookupMaxSize) {
            val ret = sizeIdx2sizeTab[size2idxTab[(size - 1) shr LOG2_QUANTUM]]
            assert(ret == normalizeSizeCompute(size))
            return ret
        }
        return normalizeSizeCompute(size)
    }

    companion object {
        @JvmField
        val LOG2_QUANTUM: Int = 4

        private const val LOG2_SIZE_CLASS_GROUP = 2
        private const val LOG2_MAX_LOOKUP_SIZE = 12

        private const val LOG2GROUP_IDX = 1
        private const val LOG2DELTA_IDX = 2
        private const val NDELTA_IDX = 3
        private const val PAGESIZE_IDX = 4
        private const val SUBPAGE_IDX = 5
        private const val LOG2_DELTA_LOOKUP_IDX = 6

        private const val no: Short = 0
        private const val yes: Short = 1

        // calculate size class
        private fun newSizeClass(
            index: Int, log2Group: Int, log2Delta: Int, nDelta: Int, pageShifts: Int
        ): ShortArray {
            val isMultiPageSize: Short
            if (log2Delta >= pageShifts) {
                isMultiPageSize = yes
            } else {
                val pageSize = 1 shl pageShifts
                val size = calculateSize(log2Group, nDelta, log2Delta)
                isMultiPageSize = if (size == size / pageSize * pageSize) yes else no
            }

            val log2Ndelta = if (nDelta == 0) 0 else log2(nDelta)

            val remove: Byte = if ((1 shl log2Ndelta) < nDelta) yes.toByte() else no.toByte()

            val log2Size = if (log2Delta + log2Ndelta == log2Group) log2Group + 1 else log2Group
            val removeFinal: Byte = if (log2Size == log2Group) yes.toByte() else remove

            val isSubpage: Short = if (log2Size < pageShifts + LOG2_SIZE_CLASS_GROUP) yes else no

            val log2DeltaLookup = if (log2Size < LOG2_MAX_LOOKUP_SIZE ||
                log2Size == LOG2_MAX_LOOKUP_SIZE && removeFinal == no.toByte()
            ) log2Delta else no.toInt()

            return shortArrayOf(
                index.toShort(), log2Group.toShort(), log2Delta.toShort(),
                nDelta.toShort(), isMultiPageSize, isSubpage, log2DeltaLookup.toShort()
            )
        }

        private fun newIdx2SizeTab(
            sizeClasses: Array<ShortArray>, nSizes: Int, directMemoryCacheAlignment: Int
        ): IntArray {
            val sizeIdx2sizeTab = IntArray(nSizes)
            for (i in 0 until nSizes) {
                sizeIdx2sizeTab[i] = sizeOf(sizeClasses[i], directMemoryCacheAlignment)
            }
            return sizeIdx2sizeTab
        }

        private fun calculateSize(log2Group: Int, nDelta: Int, log2Delta: Int): Int {
            return (1 shl log2Group) + (nDelta shl log2Delta)
        }

        private fun sizeOf(sizeClass: ShortArray, directMemoryCacheAlignment: Int): Int {
            val log2Group = sizeClass[LOG2GROUP_IDX].toInt()
            val log2Delta = sizeClass[LOG2DELTA_IDX].toInt()
            val nDelta = sizeClass[NDELTA_IDX].toInt()

            val size = calculateSize(log2Group, nDelta, log2Delta)

            return alignSizeIfNeeded(size, directMemoryCacheAlignment)
        }

        private fun newPageIdx2sizeTab(
            sizeClasses: Array<ShortArray>, nSizes: Int, nPSizes: Int,
            directMemoryCacheAlignment: Int
        ): IntArray {
            val pageIdx2sizeTab = IntArray(nPSizes)
            var pageIdx = 0
            for (i in 0 until nSizes) {
                val sizeClass = sizeClasses[i]
                if (sizeClass[PAGESIZE_IDX] == yes) {
                    pageIdx2sizeTab[pageIdx++] = sizeOf(sizeClass, directMemoryCacheAlignment)
                }
            }
            return pageIdx2sizeTab
        }

        private fun newSize2idxTab(lookupMaxSize: Int, sizeClasses: Array<ShortArray>): IntArray {
            val size2idxTab = IntArray(lookupMaxSize shr LOG2_QUANTUM)
            var idx = 0
            var size = 0

            var i = 0
            while (size <= lookupMaxSize) {
                val log2Delta = sizeClasses[i][LOG2DELTA_IDX].toInt()
                var times = 1 shl (log2Delta - LOG2_QUANTUM)

                while (size <= lookupMaxSize && times-- > 0) {
                    size2idxTab[idx++] = i
                    size = (idx + 1) shl LOG2_QUANTUM
                }
                i++
            }
            return size2idxTab
        }

        // Round size up to the nearest multiple of alignment.
        private fun alignSizeIfNeeded(size: Int, directMemoryCacheAlignment: Int): Int {
            if (directMemoryCacheAlignment <= 0) {
                return size
            }
            val delta = size and (directMemoryCacheAlignment - 1)
            return if (delta == 0) size else size + directMemoryCacheAlignment - delta
        }

        private fun normalizeSizeCompute(size: Int): Int {
            val x = log2((size shl 1) - 1)
            val log2Delta = if (x < LOG2_SIZE_CLASS_GROUP + LOG2_QUANTUM + 1)
                LOG2_QUANTUM else x - LOG2_SIZE_CLASS_GROUP - 1
            val delta = 1 shl log2Delta
            val deltaMask = delta - 1
            return (size + deltaMask) and deltaMask.inv()
        }
    }
}
