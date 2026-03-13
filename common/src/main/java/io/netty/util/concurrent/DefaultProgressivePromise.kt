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

import io.netty.util.internal.ObjectUtil.checkPositiveOrZero

open class DefaultProgressivePromise<V> : DefaultPromise<V>, ProgressivePromise<V> {

    /**
     * Creates a new instance.
     *
     * It is preferable to use [EventExecutor.newProgressivePromise] to create a new progressive promise
     *
     * @param executor the [EventExecutor] which is used to notify the promise when it progresses or it is complete
     */
    constructor(executor: EventExecutor) : super(executor)

    protected constructor() : super() { /* only for subclasses */ }

    override fun setProgress(progress: Long, total: Long): ProgressivePromise<V> {
        var t = total
        if (t < 0) {
            // total unknown
            t = -1 // normalize
            checkPositiveOrZero(progress, "progress")
        } else if (progress < 0 || progress > t) {
            throw IllegalArgumentException(
                "progress: $progress (expected: 0 <= progress <= total ($t))"
            )
        }

        if (isDone) {
            throw IllegalStateException("complete already")
        }

        notifyProgressiveListeners(progress, t)
        return this
    }

    override fun tryProgress(progress: Long, total: Long): Boolean {
        var t = total
        if (t < 0) {
            t = -1
            if (progress < 0 || isDone) {
                return false
            }
        } else if (progress < 0 || progress > t || isDone) {
            return false
        }

        notifyProgressiveListeners(progress, t)
        return true
    }

    override fun addListener(listener: GenericFutureListener<out Future<in V>>): ProgressivePromise<V> {
        super.addListener(listener)
        return this
    }

    @SafeVarargs
    override fun addListeners(vararg listeners: GenericFutureListener<out Future<in V>>): ProgressivePromise<V> {
        super.addListeners(*listeners)
        return this
    }

    override fun removeListener(listener: GenericFutureListener<out Future<in V>>): ProgressivePromise<V> {
        super.removeListener(listener)
        return this
    }

    @SafeVarargs
    override fun removeListeners(vararg listeners: GenericFutureListener<out Future<in V>>): ProgressivePromise<V> {
        super.removeListeners(*listeners)
        return this
    }

    @Throws(InterruptedException::class)
    override fun sync(): ProgressivePromise<V> {
        super.sync()
        return this
    }

    override fun syncUninterruptibly(): ProgressivePromise<V> {
        super.syncUninterruptibly()
        return this
    }

    @Throws(InterruptedException::class)
    override fun await(): ProgressivePromise<V> {
        super.await()
        return this
    }

    override fun awaitUninterruptibly(): ProgressivePromise<V> {
        super.awaitUninterruptibly()
        return this
    }

    override fun setSuccess(result: V): ProgressivePromise<V> {
        super.setSuccess(result)
        return this
    }

    override fun setFailure(cause: Throwable): ProgressivePromise<V> {
        super.setFailure(cause)
        return this
    }
}
