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

/**
 * Base class for precomputed factories that create [SearchProcessor]s.
 *
 * Different factories implement different search algorithms with performance characteristics that
 * depend on a use case, so it is advisable to benchmark a concrete use case with different algorithms
 * before choosing one of them.
 *
 * A concrete instance of [AbstractSearchProcessorFactory] is built for searching for a concrete sequence of bytes
 * (the `needle`), it contains precomputed data needed to perform the search, and is meant to be reused
 * whenever searching for the same `needle`.
 *
 * **Note:** implementations of [SearchProcessor] scan the [io.netty.buffer.ByteBuf] sequentially,
 * one byte after another, without doing any random access. As a result, when using [SearchProcessor]
 * with such methods as [io.netty.buffer.ByteBuf.forEachByte], these methods return the index of the last byte
 * of the found byte sequence within the [io.netty.buffer.ByteBuf] (which might feel counterintuitive,
 * and different from [io.netty.buffer.ByteBufUtil.indexOf] which returns the index of the first byte
 * of found sequence).
 */
abstract class AbstractSearchProcessorFactory : SearchProcessorFactory {

    companion object {
        /**
         * Creates a [SearchProcessorFactory] based on
         * [Knuth-Morris-Pratt](https://en.wikipedia.org/wiki/Knuth%E2%80%93Morris%E2%80%93Pratt_algorithm)
         * string search algorithm. It is a reasonable default choice among the provided algorithms.
         *
         * Precomputation (this method) time is linear in the size of input (`O(|needle|)`).
         *
         * The factory allocates and retains an int array of size `needle.length + 1`, and retains a reference
         * to the `needle` itself.
         *
         * Search (the actual application of [SearchProcessor]) time is linear in the size of
         * [io.netty.buffer.ByteBuf] on which the search is performed (`O(|haystack|)`).
         * Every byte of [io.netty.buffer.ByteBuf] is processed only once, sequentially.
         *
         * @param needle an array of bytes to search for
         * @return a new instance of [KmpSearchProcessorFactory] precomputed for the given `needle`
         */
        @JvmStatic
        fun newKmpSearchProcessorFactory(needle: ByteArray): KmpSearchProcessorFactory {
            return KmpSearchProcessorFactory(needle)
        }

        /**
         * Creates a [SearchProcessorFactory] based on Bitap string search algorithm.
         * It is a jump free algorithm that has very stable performance (the contents of the inputs have a minimal
         * effect on it). The limitation is that the `needle` can be no more than 64 bytes long.
         *
         * Precomputation (this method) time is linear in the size of the input (`O(|needle|)`).
         *
         * The factory allocates and retains a long[256] array.
         *
         * Search (the actual application of [SearchProcessor]) time is linear in the size of
         * [io.netty.buffer.ByteBuf] on which the search is performed (`O(|haystack|)`).
         * Every byte of [io.netty.buffer.ByteBuf] is processed only once, sequentially.
         *
         * @param needle an array **of no more than 64 bytes** to search for
         * @return a new instance of [BitapSearchProcessorFactory] precomputed for the given `needle`
         */
        @JvmStatic
        fun newBitapSearchProcessorFactory(needle: ByteArray): BitapSearchProcessorFactory {
            return BitapSearchProcessorFactory(needle)
        }
    }
}
