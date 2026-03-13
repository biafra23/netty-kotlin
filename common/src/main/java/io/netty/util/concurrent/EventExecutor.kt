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
package io.netty.util.concurrent

import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

/**
 * The [EventExecutor] is a special [EventExecutorGroup] which comes
 * with some handy methods to see if a [Thread] is executed in a event loop.
 * Besides this, it also extends the [EventExecutorGroup] to allow for a generic
 * way to access methods.
 */
interface EventExecutor : EventExecutorGroup, ThreadAwareExecutor {

    /**
     * Return the [EventExecutorGroup] which is the parent of this [EventExecutor].
     */
    fun parent(): EventExecutorGroup

    override fun isExecutorThread(thread: Thread): Boolean {
        return inEventLoop(thread)
    }

    /**
     * Calls [inEventLoop] with [Thread.currentThread] as argument.
     */
    fun inEventLoop(): Boolean {
        return inEventLoop(Thread.currentThread())
    }

    /**
     * Return `true` if the given [Thread] is executed in the event loop,
     * `false` otherwise.
     */
    fun inEventLoop(thread: Thread): Boolean

    /**
     * Return a new [Promise].
     */
    fun <V> newPromise(): Promise<V> {
        return DefaultPromise(this)
    }

    /**
     * Create a new [ProgressivePromise].
     */
    fun <V> newProgressivePromise(): ProgressivePromise<V> {
        return DefaultProgressivePromise(this)
    }

    /**
     * Create a new [Future] which is marked as succeeded already. So [Future.isSuccess]
     * will return `true`. All [FutureListener] added to it will be notified directly. Also
     * every call of blocking methods will just return without blocking.
     */
    fun <V> newSucceededFuture(result: V): Future<V> {
        return SucceededFuture(this, result)
    }

    /**
     * Create a new [Future] which is marked as failed already. So [Future.isSuccess]
     * will return `false`. All [FutureListener] added to it will be notified directly. Also
     * every call of blocking methods will just return without blocking.
     */
    fun <V> newFailedFuture(cause: Throwable): Future<V> {
        return FailedFuture(this, cause)
    }

    /**
     * Returns `true` if the [EventExecutor] is considered suspended.
     *
     * @return `true` if suspended, `false` otherwise.
     */
    fun isSuspended(): Boolean {
        return false
    }

    /**
     * Try to suspend this [EventExecutor] and return `true` if suspension was successful.
     * Suspending an [EventExecutor] will allow it to free up resources, like for example a [Thread] that
     * is backing the [EventExecutor]. Once an [EventExecutor] was suspended it will be started again
     * by submitting work to it via one of the following methods:
     *
     *  * [execute]
     *  * [schedule(Runnable, long, TimeUnit)][schedule]
     *  * [schedule(Callable, long, TimeUnit)][schedule]
     *  * [scheduleAtFixedRate]
     *  * [scheduleWithFixedDelay]
     *
     * Even if this method returns `true` it might take some time for the [EventExecutor] to fully suspend
     * itself.
     *
     * @return `true` if suspension was successful, otherwise `false`.
     */
    fun trySuspend(): Boolean {
        return false
    }
}
