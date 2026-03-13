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
package io.netty.util

interface HashingStrategy<T> {
    fun hashCode(obj: T): Int
    fun equals(a: T, b: T): Boolean

    companion object {
        @JvmField
        val JAVA_HASHER: HashingStrategy<Any?> = object : HashingStrategy<Any?> {
            override fun hashCode(obj: Any?): Int = obj?.hashCode() ?: 0
            override fun equals(a: Any?, b: Any?): Boolean = (a == b) || (a != null && a == b)
        }

        /**
         * Returns the default [HashingStrategy] that uses Java's [Object.hashCode] and [Object.equals],
         * cast to the requested type parameter.
         */
        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun <T> javaHasher(): HashingStrategy<T> = JAVA_HASHER as HashingStrategy<T>
    }
}
