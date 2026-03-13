/*
 * Copyright 2019 The Netty Project
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

/**
 * Light-weight object pool.
 *
 * @param T the type of the pooled object
 */
abstract class ObjectPool<T> internal constructor() {

    /**
     * Get a [Object] from the [ObjectPool]. The returned [Object] may be created via
     * [ObjectCreator.newObject] if no pooled [Object] is ready to be reused.
     *
     * @deprecated For removal. Please use [Recycler.get] instead.
     */
    @Deprecated("For removal. Please use Recycler.get() instead.")
    abstract fun get(): T

    /**
     * Handle for a pooled [Object] that will be used to notify the [ObjectPool] once it can
     * reuse the pooled [Object] again.
     */
    interface Handle<T> {
        /**
         * Recycle the [Object] if possible and so make it ready to be reused.
         */
        fun recycle(self: T)
    }

    /**
     * Creates a new Object which references the given [Handle] and calls [Handle.recycle] once
     * it can be re-used.
     *
     * @param T the type of the pooled object
     *
     * @deprecated For removal. Please use [Recycler] instead.
     */
    @Deprecated("For removal. Please use Recycler() instead.")
    interface ObjectCreator<T> {
        /**
         * Creates and returns a new [Object] that can be used and later recycled via
         * [Handle.recycle].
         *
         * @param handle can NOT be null.
         */
        fun newObject(handle: Handle<T>): T
    }

    @Deprecated("For removal. Please use Recycler() instead.")
    private class RecyclerObjectPool<T>(creator: ObjectCreator<T>) : ObjectPool<T>() {
        private val recycler: Recycler<T> = object : Recycler<T>() {
            override fun newObject(handle: Handle<T>): T {
                return creator.newObject(handle)
            }
        }

        override fun get(): T = recycler.get()
    }

    companion object {
        /**
         * Creates a new [ObjectPool] which will use the given [ObjectCreator] to create the [Object]
         * that should be pooled.
         *
         * @deprecated For removal. Please use [Recycler] instead.
         */
        @JvmStatic
        @Deprecated("For removal. Please use Recycler() instead.")
        fun <T> newPool(creator: ObjectCreator<T>): ObjectPool<T> {
            return RecyclerObjectPool(ObjectUtil.checkNotNull(creator, "creator"))
        }
    }
}
