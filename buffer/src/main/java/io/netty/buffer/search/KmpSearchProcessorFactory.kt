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

/**
 * Implements
 * [Knuth-Morris-Pratt](https://en.wikipedia.org/wiki/Knuth%E2%80%93Morris%E2%80%93Pratt_algorithm)
 * string search algorithm.
 * Use static [AbstractSearchProcessorFactory.newKmpSearchProcessorFactory]
 * to create an instance of this factory.
 * Use [KmpSearchProcessorFactory.newSearchProcessor] to get an instance of [io.netty.util.ByteProcessor]
 * implementation for performing the actual search.
 * @see AbstractSearchProcessorFactory
 */
class KmpSearchProcessorFactory internal constructor(needle: ByteArray) : AbstractSearchProcessorFactory() {

    private val jumpTable: IntArray
    private val needle: ByteArray

    class Processor internal constructor(
        private val needle: ByteArray,
        private val jumpTable: IntArray
    ) : SearchProcessor {

        private var currentPosition: Long = 0

        override fun process(value: Byte): Boolean {
            while (currentPosition > 0 && PlatformDependent.getByte(needle, currentPosition) != value) {
                currentPosition = PlatformDependent.getInt(jumpTable, currentPosition).toLong()
            }
            if (PlatformDependent.getByte(needle, currentPosition) == value) {
                currentPosition++
            }
            if (currentPosition == needle.size.toLong()) {
                currentPosition = PlatformDependent.getInt(jumpTable, currentPosition).toLong()
                return false
            }

            return true
        }

        override fun reset() {
            currentPosition = 0
        }
    }

    init {
        this.needle = needle.clone()
        this.jumpTable = IntArray(needle.size + 1)

        var j = 0
        for (i in 1 until needle.size) {
            while (j > 0 && needle[j] != needle[i]) {
                j = jumpTable[j]
            }
            if (needle[j] == needle[i]) {
                j++
            }
            jumpTable[i + 1] = j
        }
    }

    /**
     * Returns a new [Processor].
     */
    override fun newSearchProcessor(): Processor {
        return Processor(needle, jumpTable)
    }
}
