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
 * Special [Future] which is writable.
 */
interface Promise<V> : Future<V> {

    /**
     * Marks this future as a success and notifies all
     * listeners.
     *
     * If it is success or failed already it will throw an [IllegalStateException].
     */
    fun setSuccess(result: V): Promise<V>

    /**
     * Marks this future as a success and notifies all
     * listeners.
     *
     * @return `true` if and only if successfully marked this future as
     *         a success. Otherwise `false` because this future is
     *         already marked as either a success or a failure.
     */
    fun trySuccess(result: V): Boolean

    /**
     * Marks this future as a failure and notifies all
     * listeners.
     *
     * If it is success or failed already it will throw an [IllegalStateException].
     */
    fun setFailure(cause: Throwable): Promise<V>

    /**
     * Marks this future as a failure and notifies all
     * listeners.
     *
     * @return `true` if and only if successfully marked this future as
     *         a failure. Otherwise `false` because this future is
     *         already marked as either a success or a failure.
     */
    fun tryFailure(cause: Throwable): Boolean

    /**
     * Make this future impossible to cancel.
     *
     * @return `true` if and only if successfully marked this future as uncancellable or it is already done
     *         without being cancelled. `false` if this future has been cancelled already.
     */
    fun setUncancellable(): Boolean

    override fun addListener(listener: GenericFutureListener<out Future<in V>>): Promise<V>

    override fun addListeners(vararg listeners: GenericFutureListener<out Future<in V>>): Promise<V>

    override fun removeListener(listener: GenericFutureListener<out Future<in V>>): Promise<V>

    override fun removeListeners(vararg listeners: GenericFutureListener<out Future<in V>>): Promise<V>

    @Throws(InterruptedException::class)
    override fun await(): Promise<V>

    override fun awaitUninterruptibly(): Promise<V>

    @Throws(InterruptedException::class)
    override fun sync(): Promise<V>

    override fun syncUninterruptibly(): Promise<V>
}
