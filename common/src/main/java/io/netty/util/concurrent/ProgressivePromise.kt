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
package io.netty.util.concurrent

/**
 * Special [ProgressiveFuture] which is writable.
 */
interface ProgressivePromise<V> : Promise<V>, ProgressiveFuture<V> {

    /**
     * Sets the current progress of the operation and notifies the listeners that implement
     * [GenericProgressiveFutureListener].
     */
    fun setProgress(progress: Long, total: Long): ProgressivePromise<V>

    /**
     * Tries to set the current progress of the operation and notifies the listeners that implement
     * [GenericProgressiveFutureListener]. If the operation is already complete or the progress is out of range,
     * this method does nothing but returning `false`.
     */
    fun tryProgress(progress: Long, total: Long): Boolean

    override fun setSuccess(result: V): ProgressivePromise<V>

    override fun setFailure(cause: Throwable): ProgressivePromise<V>

    override fun addListener(listener: GenericFutureListener<out Future<in V>>): ProgressivePromise<V>

    override fun addListeners(vararg listeners: GenericFutureListener<out Future<in V>>): ProgressivePromise<V>

    override fun removeListener(listener: GenericFutureListener<out Future<in V>>): ProgressivePromise<V>

    override fun removeListeners(vararg listeners: GenericFutureListener<out Future<in V>>): ProgressivePromise<V>

    @Throws(InterruptedException::class)
    override fun await(): ProgressivePromise<V>

    override fun awaitUninterruptibly(): ProgressivePromise<V>

    @Throws(InterruptedException::class)
    override fun sync(): ProgressivePromise<V>

    override fun syncUninterruptibly(): ProgressivePromise<V>
}
