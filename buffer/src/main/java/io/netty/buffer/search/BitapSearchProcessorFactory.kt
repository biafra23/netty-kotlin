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
 * Implements [Bitap](https://en.wikipedia.org/wiki/Bitap_algorithm) string search algorithm.
 * Use static [AbstractSearchProcessorFactory.newBitapSearchProcessorFactory]
 * to create an instance of this factory.
 * Use [BitapSearchProcessorFactory.newSearchProcessor] to get an instance of [io.netty.util.ByteProcessor]
 * implementation for performing the actual search.
 * @see AbstractSearchProcessorFactory
 */
class BitapSearchProcessorFactory internal constructor(needle: ByteArray) : AbstractSearchProcessorFactory() {

    private val bitMasks = LongArray(256)
    private val successBit: Long

    class Processor internal constructor(
        private val bitMasks: LongArray,
        private val successBit: Long
    ) : SearchProcessor {

        private var currentMask: Long = 0

        override fun process(value: Byte): Boolean {
            currentMask = ((currentMask shl 1) or 1) and PlatformDependent.getLong(bitMasks, value.toLong() and 0xffL)
            return (currentMask and successBit) == 0L
        }

        override fun reset() {
            currentMask = 0
        }
    }

    init {
        require(needle.size <= 64) { "Maximum supported search pattern length is 64, got ${needle.size}" }

        var bit = 1L
        for (c in needle) {
            bitMasks[c.toInt() and 0xff] = bitMasks[c.toInt() and 0xff] or bit
            bit = bit shl 1
        }

        successBit = 1L shl (needle.size - 1)
    }

    /**
     * Returns a new [Processor].
     */
    override fun newSearchProcessor(): Processor {
        return Processor(bitMasks, successBit)
    }
}
