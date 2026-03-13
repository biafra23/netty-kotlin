/*
 * Copyright 2013 The Netty Project
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

import io.netty.util.Recycler
import io.netty.util.internal.ObjectPool.Handle

import java.util.ArrayList
import java.util.RandomAccess

/**
 * A simple list which is recyclable. This implementation does not allow `null` elements to be added.
 */
class RecyclableArrayList private constructor(
    private val handle: Handle<RecyclableArrayList>,
    initialCapacity: Int
) : ArrayList<Any>(initialCapacity) {

    private constructor(handle: Handle<RecyclableArrayList>) : this(handle, DEFAULT_INITIAL_CAPACITY)

    private var insertSinceRecycled: Boolean = false

    override fun addAll(elements: Collection<Any>): Boolean {
        checkNullElements(elements)
        if (super.addAll(elements)) {
            insertSinceRecycled = true
            return true
        }
        return false
    }

    override fun addAll(index: Int, elements: Collection<Any>): Boolean {
        checkNullElements(elements)
        if (super.addAll(index, elements)) {
            insertSinceRecycled = true
            return true
        }
        return false
    }

    override fun add(element: Any): Boolean {
        if (super.add(ObjectUtil.checkNotNull(element, "element"))) {
            insertSinceRecycled = true
            return true
        }
        return false
    }

    override fun add(index: Int, element: Any) {
        super.add(index, ObjectUtil.checkNotNull(element, "element"))
        insertSinceRecycled = true
    }

    override fun set(index: Int, element: Any): Any {
        val old = super.set(index, ObjectUtil.checkNotNull(element, "element"))
        insertSinceRecycled = true
        return old
    }

    /**
     * Returns `true` if any elements where added or set. This will be reset once [recycle] was called.
     */
    fun insertSinceRecycled(): Boolean {
        return insertSinceRecycled
    }

    /**
     * Clear and recycle this instance.
     */
    fun recycle(): Boolean {
        clear()
        insertSinceRecycled = false
        handle.recycle(this)
        return true
    }

    companion object {
        private const val serialVersionUID = -8605125654176467947L

        private const val DEFAULT_INITIAL_CAPACITY = 8

        private val RECYCLER: Recycler<RecyclableArrayList> =
            object : Recycler<RecyclableArrayList>() {
                override fun newObject(handle: Handle<RecyclableArrayList>): RecyclableArrayList {
                    return RecyclableArrayList(handle)
                }
            }

        /**
         * Create a new empty [RecyclableArrayList] instance
         */
        @JvmStatic
        fun newInstance(): RecyclableArrayList {
            return newInstance(DEFAULT_INITIAL_CAPACITY)
        }

        /**
         * Create a new empty [RecyclableArrayList] instance with the given capacity.
         */
        @JvmStatic
        fun newInstance(minCapacity: Int): RecyclableArrayList {
            val ret = RECYCLER.get()
            ret.ensureCapacity(minCapacity)
            return ret
        }

        private fun checkNullElements(c: Collection<*>) {
            if (c is RandomAccess && c is List<*>) {
                // produce less garbage
                val list = c as List<*>
                val size = list.size
                for (i in 0 until size) {
                    if (list[i] == null) {
                        throw IllegalArgumentException("c contains null values")
                    }
                }
            } else {
                for (element in c) {
                    if (element == null) {
                        throw IllegalArgumentException("c contains null values")
                    }
                }
            }
        }
    }
}
