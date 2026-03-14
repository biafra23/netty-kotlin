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

import io.netty.util.internal.ObjectUtil
import java.util.concurrent.TimeUnit

/**
 * A skeletal [Future] implementation which represents a [Future] which has been completed already.
 */
abstract class CompleteFuture<V>
/**
 * Creates a new instance.
 *
 * @param executor the [EventExecutor] associated with this future
 */
protected constructor(private val executor: EventExecutor?) : AbstractFuture<V>() {

    /**
     * Return the [EventExecutor] which is used by this [CompleteFuture].
     */
    protected open fun executor(): EventExecutor? = executor

    override fun addListener(listener: GenericFutureListener<out Future<in V>>): Future<V> {
        DefaultPromise.notifyListener(executor()!!, this, ObjectUtil.checkNotNull(listener, "listener"))
        return this
    }

    @SafeVarargs
    override fun addListeners(vararg listeners: GenericFutureListener<out Future<in V>>): Future<V> {
        for (l in ObjectUtil.checkNotNull(listeners, "listeners")) {
            if (l == null) {
                break
            }
            DefaultPromise.notifyListener(executor()!!, this, l)
        }
        return this
    }

    override fun removeListener(listener: GenericFutureListener<out Future<in V>>): Future<V> {
        // NOOP
        return this
    }

    @SafeVarargs
    override fun removeListeners(vararg listeners: GenericFutureListener<out Future<in V>>): Future<V> {
        // NOOP
        return this
    }

    @Throws(InterruptedException::class)
    override fun await(): Future<V> {
        if (Thread.interrupted()) {
            throw InterruptedException()
        }
        return this
    }

    @Throws(InterruptedException::class)
    override fun await(timeout: Long, unit: TimeUnit): Boolean {
        if (Thread.interrupted()) {
            throw InterruptedException()
        }
        return true
    }

    @Throws(InterruptedException::class)
    override fun sync(): Future<V> {
        return this
    }

    override fun syncUninterruptibly(): Future<V> {
        return this
    }

    @Throws(InterruptedException::class)
    override fun await(timeoutMillis: Long): Boolean {
        if (Thread.interrupted()) {
            throw InterruptedException()
        }
        return true
    }

    override fun awaitUninterruptibly(): Future<V> {
        return this
    }

    override fun awaitUninterruptibly(timeout: Long, unit: TimeUnit): Boolean {
        return true
    }

    override fun awaitUninterruptibly(timeoutMillis: Long): Boolean {
        return true
    }

    override fun isDone(): Boolean {
        return true
    }

    override fun isCancellable(): Boolean {
        return false
    }

    override fun isCancelled(): Boolean {
        return false
    }

    /**
     * {@inheritDoc}
     *
     * @param mayInterruptIfRunning this value has no effect in this implementation.
     */
    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        return false
    }
}
