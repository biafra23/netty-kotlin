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
package io.netty.util

import io.netty.util.internal.ObjectUtil.checkNonEmpty
import io.netty.util.internal.ObjectUtil.checkNotNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * A pool of [Constant]s.
 *
 * @param T the type of the constant
 */
abstract class ConstantPool<T : Constant<T>> {

    private val constants: ConcurrentMap<String, T> = ConcurrentHashMap()

    private val nextId = AtomicInteger(1)

    /**
     * Shortcut of [valueOf(firstNameComponent.getName() + "#" + secondNameComponent)][valueOf].
     */
    fun valueOf(firstNameComponent: Class<*>, secondNameComponent: String): T {
        return valueOf(
            checkNotNull(firstNameComponent, "firstNameComponent").name +
                '#' +
                checkNotNull(secondNameComponent, "secondNameComponent")
        )
    }

    /**
     * Returns the [Constant] which is assigned to the specified [name].
     * If there's no such [Constant], a new one will be created and returned.
     * Once created, the subsequent calls with the same [name] will always return the previously created one
     * (i.e. singleton.)
     *
     * @param name the name of the [Constant]
     */
    fun valueOf(name: String): T {
        return getOrCreate(checkNonEmpty(name, "name"))
    }

    /**
     * Get existing constant by name or creates new one if not exists. Threadsafe
     *
     * @param name the name of the [Constant]
     */
    private fun getOrCreate(name: String): T {
        var constant = constants[name]
        if (constant == null) {
            val tempConstant = newConstant(nextId(), name)
            constant = constants.putIfAbsent(name, tempConstant)
            if (constant == null) {
                return tempConstant
            }
        }
        return constant
    }

    /**
     * Returns `true` if a [AttributeKey] exists for the given [name].
     */
    fun exists(name: String): Boolean {
        return constants.containsKey(checkNonEmpty(name, "name"))
    }

    /**
     * Creates a new [Constant] for the given [name] or fail with an
     * [IllegalArgumentException] if a [Constant] for the given [name] exists.
     */
    fun newInstance(name: String): T {
        return createOrThrow(checkNonEmpty(name, "name"))
    }

    /**
     * Creates constant by name or throws exception. Threadsafe
     *
     * @param name the name of the [Constant]
     */
    private fun createOrThrow(name: String): T {
        var constant = constants[name]
        if (constant == null) {
            val tempConstant = newConstant(nextId(), name)
            constant = constants.putIfAbsent(name, tempConstant)
            if (constant == null) {
                return tempConstant
            }
        }
        throw IllegalArgumentException("'$name' is already in use")
    }

    protected abstract fun newConstant(id: Int, name: String): T

    @Deprecated("Use nextId() only if you need manual ID management.")
    fun nextId(): Int {
        return nextId.getAndIncrement()
    }
}
