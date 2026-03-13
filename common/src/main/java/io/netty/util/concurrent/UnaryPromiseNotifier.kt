/*
 * Copyright 2016 The Netty Project
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
import io.netty.util.internal.logging.InternalLoggerFactory

/**
 * @deprecated use [PromiseNotifier.cascade].
 */
@Deprecated("Use PromiseNotifier.cascade(boolean, Future, Promise) instead.")
class UnaryPromiseNotifier<T>(promise: Promise<in T>) : FutureListener<T> {

    private val promise: Promise<in T> = ObjectUtil.checkNotNull(promise, "promise")

    @Throws(Exception::class)
    override fun operationComplete(future: Future<T>) {
        cascadeTo(future, promise)
    }

    companion object {
        private val logger = InternalLoggerFactory.getInstance(UnaryPromiseNotifier::class.java)

        @JvmStatic
        fun <X> cascadeTo(completedFuture: Future<X>, promise: Promise<in X>) {
            if (completedFuture.isSuccess()) {
                @Suppress("UNCHECKED_CAST")
            if (!promise.trySuccess(completedFuture.getNow() as X)) {
                    logger.warn("Failed to mark a promise as success because it is done already: {}", promise)
                }
            } else if (completedFuture.isCancelled) {
                if (!promise.cancel(false)) {
                    logger.warn("Failed to cancel a promise because it is done already: {}", promise)
                }
            } else {
                if (!promise.tryFailure(completedFuture.cause()!!)) {
                    logger.warn(
                        "Failed to mark a promise as failure because it's done already: {}", promise,
                        completedFuture.cause()!!
                    )
                }
            }
        }
    }
}
