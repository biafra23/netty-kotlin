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

interface Attribute<T> {
    fun key(): AttributeKey<T>
    fun get(): T?
    fun set(value: T?)
    fun getAndSet(value: T?): T?
    fun setIfAbsent(value: T?): T?
    @Deprecated("please consider using getAndSet(null)")
    fun getAndRemove(): T?
    fun compareAndSet(oldValue: T?, newValue: T?): Boolean
    @Deprecated("please consider using set(null)")
    fun remove()
}
