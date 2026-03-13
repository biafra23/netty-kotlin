/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.buffer.search

import io.netty.util.internal.PlatformDependent
import java.util.ArrayDeque
import java.util.Arrays

/**
 * Implements [Aho-Corasick](https://en.wikipedia.org/wiki/Aho%E2%80%93Corasick_algorithm)
 * string search algorithm.
 * Use static [AbstractMultiSearchProcessorFactory.newAhoCorasicSearchProcessorFactory]
 * to create an instance of this factory.
 * Use [AhoCorasicSearchProcessorFactory.newSearchProcessor] to get an instance of
 * [io.netty.util.ByteProcessor] implementation for performing the actual search.
 * @see AbstractMultiSearchProcessorFactory
 */
class AhoCorasicSearchProcessorFactory internal constructor(vararg needles: ByteArray) :
    AbstractMultiSearchProcessorFactory() {

    private val jumpTable: IntArray
    private val matchForNeedleId: IntArray

    private class Context {
        lateinit var jumpTable: IntArray
        lateinit var matchForNeedleId: IntArray
    }

    class Processor internal constructor(
        private val jumpTable: IntArray,
        private val matchForNeedleId: IntArray
    ) : MultiSearchProcessor {

        private var currentPosition: Long = 0

        override fun process(value: Byte): Boolean {
            currentPosition = PlatformDependent.getInt(jumpTable, currentPosition or (value.toLong() and 0xffL)).toLong()
            if (currentPosition < 0) {
                currentPosition = -currentPosition
                return false
            }
            return true
        }

        override fun getFoundNeedleId(): Int {
            return matchForNeedleId[currentPosition.toInt() shr BITS_PER_SYMBOL]
        }

        override fun reset() {
            currentPosition = 0
        }
    }

    companion object {
        const val BITS_PER_SYMBOL: Int = 8
        const val ALPHABET_SIZE: Int = 1 shl BITS_PER_SYMBOL

        private fun buildTrie(needles: Array<out ByteArray>): Context {
            val jumpTableBuilder = ArrayList<Int>(ALPHABET_SIZE)
            for (i in 0 until ALPHABET_SIZE) {
                jumpTableBuilder.add(-1)
            }

            val matchForBuilder = ArrayList<Int>()
            matchForBuilder.add(-1)

            for (needleId in needles.indices) {
                val needle = needles[needleId]
                var currentPosition = 0

                for (ch0 in needle) {
                    val ch = ch0.toInt() and 0xff
                    val next = currentPosition + ch

                    if (jumpTableBuilder[next] == -1) {
                        jumpTableBuilder[next] = jumpTableBuilder.size
                        for (i in 0 until ALPHABET_SIZE) {
                            jumpTableBuilder.add(-1)
                        }
                        matchForBuilder.add(-1)
                    }

                    currentPosition = jumpTableBuilder[next]
                }

                matchForBuilder[currentPosition shr BITS_PER_SYMBOL] = needleId
            }

            val context = Context()

            context.jumpTable = IntArray(jumpTableBuilder.size)
            for (i in jumpTableBuilder.indices) {
                context.jumpTable[i] = jumpTableBuilder[i]
            }

            context.matchForNeedleId = IntArray(matchForBuilder.size)
            for (i in matchForBuilder.indices) {
                context.matchForNeedleId[i] = matchForBuilder[i]
            }

            return context
        }
    }

    init {
        for (needle in needles) {
            require(needle.isNotEmpty()) { "Needle must be non empty" }
        }

        val context = buildTrie(needles)
        jumpTable = context.jumpTable
        matchForNeedleId = context.matchForNeedleId

        linkSuffixes()

        for (i in jumpTable.indices) {
            if (matchForNeedleId[jumpTable[i] shr BITS_PER_SYMBOL] >= 0) {
                jumpTable[i] = -jumpTable[i]
            }
        }
    }

    private fun linkSuffixes() {
        val queue: ArrayDeque<Int> = ArrayDeque()
        queue.add(0)

        val suffixLinks = IntArray(matchForNeedleId.size)
        Arrays.fill(suffixLinks, -1)

        while (queue.isNotEmpty()) {
            val v = queue.remove()
            val vPosition = v shr BITS_PER_SYMBOL
            val u = if (suffixLinks[vPosition] == -1) 0 else suffixLinks[vPosition]

            if (matchForNeedleId[vPosition] == -1) {
                matchForNeedleId[vPosition] = matchForNeedleId[u shr BITS_PER_SYMBOL]
            }

            for (ch in 0 until ALPHABET_SIZE) {
                val vIndex = v or ch
                val uIndex = u or ch

                val jumpV = jumpTable[vIndex]
                val jumpU = jumpTable[uIndex]

                if (jumpV != -1) {
                    suffixLinks[jumpV shr BITS_PER_SYMBOL] = if (v > 0 && jumpU != -1) jumpU else 0
                    queue.add(jumpV)
                } else {
                    jumpTable[vIndex] = if (jumpU != -1) jumpU else 0
                }
            }
        }
    }

    /**
     * Returns a new [Processor].
     */
    override fun newSearchProcessor(): Processor {
        return Processor(jumpTable, matchForNeedleId)
    }
}
