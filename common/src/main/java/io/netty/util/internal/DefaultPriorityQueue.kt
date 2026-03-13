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

import io.netty.util.internal.PriorityQueueNode.Companion.INDEX_NOT_IN_QUEUE
import java.util.AbstractQueue
import java.util.Arrays
import java.util.Comparator
import java.util.NoSuchElementException

/**
 * A priority queue which uses natural ordering of elements. Elements are also required to be of type
 * [PriorityQueueNode] for the purpose of maintaining the index in the priority queue.
 * @param T The object that is maintained in the queue.
 */
class DefaultPriorityQueue<T : PriorityQueueNode>(
    private val comparator: Comparator<T>,
    initialSize: Int
) : AbstractQueue<T>(), PriorityQueue<T> {

    private var queue: Array<T?>
    override var size: Int = 0
        private set

    init {
        @Suppress("UNCHECKED_CAST")
        queue = if (initialSize != 0) {
            arrayOfNulls<PriorityQueueNode>(initialSize) as Array<T?>
        } else {
            EMPTY_ARRAY as Array<T?>
        }
    }

    override fun isEmpty(): Boolean = size == 0

    override fun contains(element: T?): Boolean {
        if (element !is PriorityQueueNode) {
            return false
        }
        val node = element as PriorityQueueNode
        return contains(node, node.priorityQueueIndex(this))
    }

    override fun containsTyped(node: T): Boolean {
        return contains(node, node.priorityQueueIndex(this))
    }

    override fun clear() {
        for (i in 0 until size) {
            val node = queue[i]
            if (node != null) {
                node.priorityQueueIndex(this, INDEX_NOT_IN_QUEUE)
                queue[i] = null
            }
        }
        size = 0
    }

    override fun clearIgnoringIndexes() {
        size = 0
    }

    override fun offer(e: T): Boolean {
        if (e.priorityQueueIndex(this) != INDEX_NOT_IN_QUEUE) {
            throw IllegalArgumentException("e.priorityQueueIndex(): " + e.priorityQueueIndex(this) +
                    " (expected: " + INDEX_NOT_IN_QUEUE + ") + e: " + e)
        }

        // Check that the array capacity is enough to hold values by doubling capacity.
        if (size >= queue.size) {
            // Use a policy which allows for a 0 initial capacity. Same policy as JDK's priority queue, double when
            // "small", then grow by 50% when "large".
            queue = Arrays.copyOf(queue, queue.size + (if (queue.size < 64) queue.size + 2 else queue.size ushr 1))
        }

        bubbleUp(size++, e)
        return true
    }

    override fun poll(): T? {
        if (size == 0) {
            return null
        }
        val result = queue[0]!!
        result.priorityQueueIndex(this, INDEX_NOT_IN_QUEUE)

        val last = queue[--size]!!
        queue[size] = null
        if (size != 0) { // Make sure we don't add the last element back.
            bubbleDown(0, last)
        }

        return result
    }

    override fun peek(): T? = if (size == 0) null else queue[0]

    @Suppress("UNCHECKED_CAST")
    override fun remove(element: T?): Boolean {
        val node: T
        try {
            node = element as T
        } catch (e: ClassCastException) {
            return false
        }
        return removeTyped(node)
    }

    override fun removeTyped(node: T): Boolean {
        val i = node.priorityQueueIndex(this)
        if (!contains(node, i)) {
            return false
        }

        node.priorityQueueIndex(this, INDEX_NOT_IN_QUEUE)
        if (--size == 0 || size == i) {
            // If there are no node left, or this is the last node in the array just remove and return.
            queue[i] = null
            return true
        }

        // Move the last element where node currently lives in the array.
        val moved = queue[size]!!
        queue[i] = moved
        queue[size] = null
        // priorityQueueIndex will be updated below in bubbleUp or bubbleDown

        // Make sure the moved node still preserves the min-heap properties.
        if (comparator.compare(node, moved) < 0) {
            bubbleDown(i, moved)
        } else {
            bubbleUp(i, moved)
        }
        return true
    }

    override fun priorityChanged(node: T) {
        val i = node.priorityQueueIndex(this)
        if (!contains(node, i)) {
            return
        }

        // Preserve the min-heap property by comparing the new priority with parents/children in the heap.
        if (i == 0) {
            bubbleDown(i, node)
        } else {
            // Get the parent to see if min-heap properties are violated.
            val iParent = (i - 1) ushr 1
            val parent = queue[iParent]!!
            if (comparator.compare(node, parent) < 0) {
                bubbleUp(i, node)
            } else {
                bubbleDown(i, node)
            }
        }
    }

    override fun toArray(): Array<Any?> {
        @Suppress("UNCHECKED_CAST")
        return Arrays.copyOf(queue as Array<Any?>, size)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <X> toArray(a: Array<X>): Array<X> {
        if (a.size < size) {
            return Arrays.copyOf(queue, size, a.javaClass) as Array<X>
        }
        System.arraycopy(queue, 0, a, 0, size)
        if (a.size > size) {
            a[size] = null as X
        }
        return a
    }

    /**
     * This iterator does not return elements in any particular order.
     */
    override fun iterator(): MutableIterator<T> = PriorityQueueIterator()

    private inner class PriorityQueueIterator : MutableIterator<T> {
        private var index: Int = 0

        override fun hasNext(): Boolean = index < size

        override fun next(): T {
            if (index >= size) {
                throw NoSuchElementException()
            }
            return queue[index++]!!
        }

        override fun remove() {
            throw UnsupportedOperationException("remove")
        }
    }

    private fun contains(node: PriorityQueueNode, i: Int): Boolean {
        return i >= 0 && i < size && node == queue[i]
    }

    private fun bubbleDown(k: Int, node: T) {
        var k = k
        val half = size ushr 1
        while (k < half) {
            // Compare node to the children of index k.
            var iChild = (k shl 1) + 1
            var child = queue[iChild]!!

            // Make sure we get the smallest child to compare against.
            val rightChild = iChild + 1
            if (rightChild < size && comparator.compare(child, queue[rightChild]!!) > 0) {
                iChild = rightChild
                child = queue[iChild]!!
            }
            // If the bubbleDown node is less than or equal to the smallest child then we will preserve the min-heap
            // property by inserting the bubbleDown node here.
            if (comparator.compare(node, child) <= 0) {
                break
            }

            // Bubble the child up.
            queue[k] = child
            child.priorityQueueIndex(this, k)

            // Move down k down the tree for the next iteration.
            k = iChild
        }

        // We have found where node should live and still satisfy the min-heap property, so put it in the queue.
        queue[k] = node
        node.priorityQueueIndex(this, k)
    }

    private fun bubbleUp(k: Int, node: T) {
        var k = k
        while (k > 0) {
            val iParent = (k - 1) ushr 1
            val parent = queue[iParent]!!

            // If the bubbleUp node is less than the parent, then we have found a spot to insert and still maintain
            // min-heap properties.
            if (comparator.compare(node, parent) >= 0) {
                break
            }

            // Bubble the parent down.
            queue[k] = parent
            parent.priorityQueueIndex(this, k)

            // Move k up the tree for the next iteration.
            k = iParent
        }

        // We have found where node should live and still satisfy the min-heap property, so put it in the queue.
        queue[k] = node
        node.priorityQueueIndex(this, k)
    }

    companion object {
        private val EMPTY_ARRAY = emptyArray<PriorityQueueNode?>()
    }
}
