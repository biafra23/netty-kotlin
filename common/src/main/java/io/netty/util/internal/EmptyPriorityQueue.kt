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

import java.util.Collections
import java.util.NoSuchElementException

class EmptyPriorityQueue<T> private constructor() : PriorityQueue<T> {

    override fun removeTyped(node: T): Boolean = false

    override fun containsTyped(node: T): Boolean = false

    override fun priorityChanged(node: T) {}

    override val size: Int get() = 0

    override fun isEmpty(): Boolean = true

    override fun contains(element: T?): Boolean = false

    override fun iterator(): MutableIterator<T> = Collections.emptyList<T>().iterator() as MutableIterator<T>

    // Note: toArray() is not overridable from Kotlin for java.util.Collection implementations.
    // These are standalone methods that shadow the Java defaults.
    fun toArray(): Array<Any?> = arrayOfNulls(0)

    @Suppress("UNCHECKED_CAST")
    fun <T1> toArray(a: Array<T1>): Array<T1> {
        if (a.isNotEmpty()) {
            a[0] = null as T1
        }
        return a
    }

    override fun add(element: T): Boolean = false

    override fun remove(element: T?): Boolean = false

    override fun containsAll(elements: Collection<T?>): Boolean = false

    override fun addAll(elements: Collection<T>): Boolean = false

    override fun removeAll(elements: Collection<T?>): Boolean = false

    override fun retainAll(elements: Collection<T?>): Boolean = false

    override fun clear() {}

    override fun clearIgnoringIndexes() {}

    override fun equals(other: Any?): Boolean {
        return other is PriorityQueue<*> && other.isEmpty()
    }

    override fun hashCode(): Int = 0

    override fun offer(t: T): Boolean = false

    override fun remove(): T = throw NoSuchElementException()

    override fun poll(): T? = null

    override fun element(): T = throw NoSuchElementException()

    override fun peek(): T? = null

    override fun toString(): String = EmptyPriorityQueue::class.java.simpleName

    companion object {
        private val INSTANCE: PriorityQueue<Any> = EmptyPriorityQueue()

        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun <V> instance(): EmptyPriorityQueue<V> = INSTANCE as EmptyPriorityQueue<V>
    }
}
