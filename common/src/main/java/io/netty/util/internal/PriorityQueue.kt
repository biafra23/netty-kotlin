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
package io.netty.util.internal

import java.util.Queue

interface PriorityQueue<T> : Queue<T> {
    /**
     * Same as [remove] but typed using generics.
     */
    fun removeTyped(node: T): Boolean

    /**
     * Same as [contains] but typed using generics.
     */
    fun containsTyped(node: T): Boolean

    /**
     * Notify the queue that the priority for [node] has changed. The queue will adjust to ensure the priority
     * queue properties are maintained.
     * @param node An object which is in this queue and the priority may have changed.
     */
    fun priorityChanged(node: T)

    /**
     * Removes all of the elements from this [PriorityQueue] without calling
     * [PriorityQueueNode.priorityQueueIndex] or explicitly removing references to them to
     * allow them to be garbage collected. This should only be used when it is certain that the nodes will not be
     * re-inserted into this or any other [PriorityQueue] and it is known that the [PriorityQueue] itself
     * will be garbage collected after this call.
     */
    fun clearIgnoringIndexes()
}
