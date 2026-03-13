/*
 * Copyright 2014 The Netty Project
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

import io.netty.util.internal.ObjectUtil.checkNotNull
import io.netty.util.internal.ObjectUtil.checkNotNullWithIAE
import io.netty.util.internal.PromiseNotificationUtil
import io.netty.util.internal.logging.InternalLoggerFactory

/**
 * [GenericFutureListener] implementation which takes other [Promise]s
 * and notifies them on completion.
 *
 * @param V the type of value returned by the future
 * @param F the type of future
 */
open class PromiseNotifier<V, F : Future<V>> : GenericFutureListener<F> {

    private val promises: Array<out Promise<in V>>
    private val logNotifyFailure: Boolean

    /**
     * Create a new instance.
     *
     * @param promises the [Promise]s to notify once this [GenericFutureListener] is notified.
     */
    @SafeVarargs
    constructor(vararg promises: Promise<in V>) : this(true, *promises)

    /**
     * Create a new instance.
     *
     * @param logNotifyFailure `true` if logging should be done in case notification fails.
     * @param promises the [Promise]s to notify once this [GenericFutureListener] is notified.
     */
    @SafeVarargs
    constructor(logNotifyFailure: Boolean, vararg promises: Promise<in V>) {
        checkNotNull(promises, "promises")
        for (promise in promises) {
            checkNotNullWithIAE(promise, "promise")
        }
        this.promises = promises.clone()
        this.logNotifyFailure = logNotifyFailure
    }

    @Throws(Exception::class)
    override fun operationComplete(future: F) {
        val internalLogger = if (logNotifyFailure) logger else null
        if (future.isSuccess()) {
            val result = future.get()
            for (p in promises) {
                PromiseNotificationUtil.trySuccess(p, result, internalLogger)
            }
        } else if (future.isCancelled) {
            for (p in promises) {
                PromiseNotificationUtil.tryCancel(p, internalLogger)
            }
        } else {
            val cause = future.cause()!!
            for (p in promises) {
                PromiseNotificationUtil.tryFailure(p, cause, internalLogger)
            }
        }
    }

    companion object {
        private val logger = InternalLoggerFactory.getInstance(PromiseNotifier::class.java)

        /**
         * Link the [Future] and [Promise] such that if the [Future] completes the [Promise]
         * will be notified. Cancellation is propagated both ways such that if the [Future] is cancelled
         * the [Promise] is cancelled and vise-versa.
         *
         * @param future the [Future] which will be used to listen to for notifying the [Promise].
         * @param promise the [Promise] which will be notified
         * @param V the type of the value.
         * @param F the type of the [Future]
         * @return the passed in [Future]
         */
        @JvmStatic
        fun <V, F : Future<V>> cascade(future: F, promise: Promise<in V>): F {
            return cascade(true, future, promise)
        }

        /**
         * Link the [Future] and [Promise] such that if the [Future] completes the [Promise]
         * will be notified. Cancellation is propagated both ways such that if the [Future] is cancelled
         * the [Promise] is cancelled and vise-versa.
         *
         * @param logNotifyFailure `true` if logging should be done in case notification fails.
         * @param future the [Future] which will be used to listen to for notifying the [Promise].
         * @param promise the [Promise] which will be notified
         * @param V the type of the value.
         * @param F the type of the [Future]
         * @return the passed in [Future]
         */
        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun <V, F : Future<V>> cascade(logNotifyFailure: Boolean, future: F, promise: Promise<in V>): F {
            @Suppress("UNCHECKED_CAST")
            (promise as Future<Any?>).addListener(object : GenericFutureListener<Future<Any?>> {
                override fun operationComplete(f: Future<Any?>) {
                    if (f.isCancelled()) {
                        future.cancel(false)
                    }
                }
            })
            future.addListener(object : PromiseNotifier<V, Future<V>>(logNotifyFailure, promise) {
                @Throws(Exception::class)
                override fun operationComplete(f: Future<V>) {
                    if (promise.isCancelled && f.isCancelled) {
                        // Just return if we propagate a cancel from the promise to the future and both are notified already
                        return
                    }
                    super.operationComplete(future)
                }
            } as GenericFutureListener<Future<V>>)
            return future
        }
    }
}
