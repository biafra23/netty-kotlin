/*
 * Copyright 2012 The Netty Project
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

/**
 * Key which can be used to access [Attribute] out of the [AttributeMap]. Be aware that it is not be
 * possible to have multiple keys with the same name.
 *
 * @param T the type of the [Attribute] which can be accessed via this [AttributeKey].
 */
class AttributeKey<T> private constructor(id: Int, name: String) : AbstractConstant<AttributeKey<T>>(id, name) {

    companion object {
        private val pool = object : ConstantPool<AttributeKey<Any>>() {
            override fun newConstant(id: Int, name: String): AttributeKey<Any> {
                return AttributeKey(id, name)
            }
        }

        /**
         * Returns the singleton instance of the [AttributeKey] which has the specified [name].
         */
        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun <T> valueOf(name: String): AttributeKey<T> {
            return pool.valueOf(name) as AttributeKey<T>
        }

        /**
         * Returns `true` if a [AttributeKey] exists for the given [name].
         */
        @JvmStatic
        fun exists(name: String): Boolean {
            return pool.exists(name)
        }

        /**
         * Creates a new [AttributeKey] for the given [name] or fail with an
         * [IllegalArgumentException] if a [AttributeKey] for the given [name] exists.
         */
        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun <T> newInstance(name: String): AttributeKey<T> {
            return pool.newInstance(name) as AttributeKey<T>
        }

        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun <T> valueOf(firstNameComponent: Class<*>, secondNameComponent: String): AttributeKey<T> {
            return pool.valueOf(firstNameComponent, secondNameComponent) as AttributeKey<T>
        }
    }
}
