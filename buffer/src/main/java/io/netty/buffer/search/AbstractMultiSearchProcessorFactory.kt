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
 * Base class for precomputed factories that create [MultiSearchProcessor]s.
 *
 * The purpose of [MultiSearchProcessor] is to perform efficient simultaneous search for multiple `needles`
 * in the `haystack`, while scanning every byte of the input sequentially, only once. While it can also be used
 * to search for just a single `needle`, using a [SearchProcessorFactory] would be more efficient for
 * doing that.
 *
 * See the documentation of [AbstractSearchProcessorFactory] for a comprehensive description of common usage.
 * In addition to the functionality provided by [SearchProcessor], [MultiSearchProcessor] adds
 * a method to get the index of the `needle` found at the current position of the [MultiSearchProcessor] -
 * [MultiSearchProcessor.getFoundNeedleId].
 *
 * **Note:** in some cases one `needle` can be a suffix of another `needle`, eg. `{"BC", "ABC"}`,
 * and there can potentially be multiple `needles` found ending at the same position of the `haystack`.
 * In such case [MultiSearchProcessor.getFoundNeedleId] returns the index of the longest matching `needle`
 * in the array of `needles`.
 */
abstract class AbstractMultiSearchProcessorFactory : MultiSearchProcessorFactory {

    companion object {
        /**
         * Creates a [MultiSearchProcessorFactory] based on
         * [Aho-Corasick](https://en.wikipedia.org/wiki/Aho%E2%80%93Corasick_algorithm)
         * string search algorithm.
         *
         * Precomputation (this method) time is linear in the size of input (`O(|needles|)`).
         *
         * The factory allocates and retains an array of 256 * X ints plus another array of X ints, where X
         * is the sum of lengths of each entry of `needles` minus the sum of lengths of repeated
         * prefixes of the `needles`.
         *
         * Search (the actual application of [MultiSearchProcessor]) time is linear in the size of
         * [io.netty.buffer.ByteBuf] on which the search is performed (`O(|haystack|)`).
         * Every byte of [io.netty.buffer.ByteBuf] is processed only once, sequentially, regardless of
         * the number of `needles` being searched for.
         *
         * @param needles a varargs array of arrays of bytes to search for
         * @return a new instance of [AhoCorasicSearchProcessorFactory] precomputed for the given `needles`
         */
        @JvmStatic
        fun newAhoCorasicSearchProcessorFactory(vararg needles: ByteArray): AhoCorasicSearchProcessorFactory {
            return AhoCorasicSearchProcessorFactory(*needles)
        }
    }
}
