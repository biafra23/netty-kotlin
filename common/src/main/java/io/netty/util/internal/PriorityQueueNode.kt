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
package io.netty.util.internal

/**
 * Provides methods for [DefaultPriorityQueue] to maintain internal state. These methods should generally not be
 * used outside the scope of [DefaultPriorityQueue].
 */
interface PriorityQueueNode {

    /**
     * Get the last value set by [priorityQueueIndex] for the value corresponding to [queue].
     *
     * Throwing exceptions from this method will result in undefined behavior.
     */
    fun priorityQueueIndex(queue: DefaultPriorityQueue<*>): Int

    /**
     * Used by [DefaultPriorityQueue] to maintain state for an element in the queue.
     *
     * Throwing exceptions from this method will result in undefined behavior.
     * @param queue The queue for which the index is being set.
     * @param i The index as used by [DefaultPriorityQueue].
     */
    fun priorityQueueIndex(queue: DefaultPriorityQueue<*>, i: Int)

    companion object {
        /**
         * This should be used to initialize the storage returned by [priorityQueueIndex].
         */
        const val INDEX_NOT_IN_QUEUE: Int = -1
    }
}
