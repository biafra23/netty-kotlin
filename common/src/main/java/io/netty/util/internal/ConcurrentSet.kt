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

import java.io.Serializable
import java.util.AbstractSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * @deprecated For removal in Netty 4.2. Please use [ConcurrentHashMap.newKeySet] instead
 */
@Deprecated("For removal in Netty 4.2. Please use ConcurrentHashMap.newKeySet() instead")
class ConcurrentSet<E> : AbstractSet<E>(), Serializable {

    private val map: ConcurrentMap<E, Boolean> = ConcurrentHashMap()

    override val size: Int
        get() = map.size

    override fun contains(element: E): Boolean = map.containsKey(element)

    override fun add(element: E): Boolean = map.putIfAbsent(element, java.lang.Boolean.TRUE) == null

    override fun remove(element: E): Boolean = map.remove(element) != null

    override fun clear() {
        map.clear()
    }

    override fun iterator(): MutableIterator<E> = map.keys.iterator()

    companion object {
        private const val serialVersionUID = -6761513279741915432L
    }
}
