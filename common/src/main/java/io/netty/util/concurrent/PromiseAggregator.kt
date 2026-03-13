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

import io.netty.util.internal.ObjectUtil
import java.util.LinkedHashSet

/**
 * @deprecated Use [PromiseCombiner].
 *
 * [GenericFutureListener] implementation which consolidates multiple [Future]s
 * into one, by listening to individual [Future]s and producing an aggregated result
 * (success/failure) when all [Future]s have completed.
 *
 * @param V the type of value returned by the [Future]
 * @param F the type of [Future]
 */
@Deprecated("Use PromiseCombiner instead.")
open class PromiseAggregator<V, F : Future<V>> @JvmOverloads constructor(
    private val aggregatePromise: Promise<*> = throw IllegalArgumentException(),
    private val failPending: Boolean = true
) : GenericFutureListener<F> {

    private var pendingPromises: MutableSet<Promise<V>>? = null

    init {
        ObjectUtil.checkNotNull(aggregatePromise, "aggregatePromise")
    }

    /**
     * Add the given [Promise]s to the aggregator.
     */
    @SafeVarargs
    fun add(vararg promises: Promise<V>?): PromiseAggregator<V, F> {
        ObjectUtil.checkNotNull(promises, "promises")
        if (promises.isEmpty()) {
            return this
        }
        synchronized(this) {
            if (pendingPromises == null) {
                val size = if (promises.size > 1) promises.size else 2
                pendingPromises = LinkedHashSet(size)
            }
            for (p in promises) {
                if (p == null) {
                    continue
                }
                pendingPromises!!.add(p)
                @Suppress("UNCHECKED_CAST")
                p.addListener(this as GenericFutureListener<Future<V>>)
            }
        }
        return this
    }

    @Synchronized
    @Throws(Exception::class)
    override fun operationComplete(future: F) {
        if (pendingPromises == null) {
            @Suppress("UNCHECKED_CAST")
            (aggregatePromise as Promise<Any?>).setSuccess(null)
        } else {
            pendingPromises!!.remove(future as Promise<V>)
            if (!future.isSuccess()) {
                val cause = future.cause()!!
                aggregatePromise.setFailure(cause)
                if (failPending) {
                    for (pendingFuture in pendingPromises!!) {
                        pendingFuture.setFailure(cause)
                    }
                }
            } else {
                if (pendingPromises!!.isEmpty()) {
                    @Suppress("UNCHECKED_CAST")
                    (aggregatePromise as Promise<Any?>).setSuccess(null)
                }
            }
        }
    }
}
