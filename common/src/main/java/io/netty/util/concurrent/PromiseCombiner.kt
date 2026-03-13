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

/**
 * A promise combiner monitors the outcome of a number of discrete futures, then notifies a final, aggregate promise
 * when all of the combined futures are finished. The aggregate promise will succeed if and only if all of the combined
 * futures succeed. If any of the combined futures fail, the aggregate promise will fail. The cause failure for the
 * aggregate promise will be the failure for one of the failed combined futures; if more than one of the combined
 * futures fails, exactly which cause of failure will be assigned to the aggregate promise is undefined.
 *
 * Callers may populate a promise combiner with any number of futures to be combined via the
 * [add] and [addAll] methods. When all futures to be combined have been added, callers must
 * provide an aggregate promise to be notified when all combined promises have finished via the [finish] method.
 *
 * This implementation is **NOT** thread-safe and all methods must be called
 * from the [EventExecutor] thread.
 */
class PromiseCombiner {
    private var expectedCount: Int = 0
    private var doneCount: Int = 0
    private var aggregatePromise: Promise<Void>? = null
    private var cause: Throwable? = null
    private val listener: GenericFutureListener<Future<*>> = object : GenericFutureListener<Future<*>> {
        override fun operationComplete(future: Future<*>) {
            if (executor.inEventLoop()) {
                operationComplete0(future)
            } else {
                executor.execute { operationComplete0(future) }
            }
        }

        private fun operationComplete0(future: Future<*>) {
            assert(executor.inEventLoop())
            ++doneCount
            if (!future.isSuccess() && cause == null) {
                cause = future.cause()
            }
            if (doneCount == expectedCount && aggregatePromise != null) {
                tryPromise()
            }
        }
    }

    private val executor: EventExecutor

    /**
     * Deprecated use [PromiseCombiner(EventExecutor)][PromiseCombiner].
     */
    @Deprecated("Use PromiseCombiner(EventExecutor) instead.")
    constructor() : this(ImmediateEventExecutor.INSTANCE)

    /**
     * The [EventExecutor] to use for notifications. You must call [add], [addAll]
     * and [finish] from within the [EventExecutor] thread.
     *
     * @param executor the [EventExecutor] to use for notifications.
     */
    constructor(executor: EventExecutor) {
        this.executor = ObjectUtil.checkNotNull(executor, "executor")
    }

    /**
     * Adds a new promise to be combined. New promises may be added until an aggregate promise is added via the
     * [finish] method.
     *
     * @param promise the promise to add to this promise combiner
     *
     * @deprecated Replaced by [add(Future)][add].
     */
    @Deprecated("Replaced by add(Future).")
    fun add(promise: Promise<*>) {
        @Suppress("UNCHECKED_CAST")
        add(promise as Future<*>)
    }

    /**
     * Adds a new future to be combined. New futures may be added until an aggregate promise is added via the
     * [finish] method.
     *
     * @param future the future to add to this promise combiner
     */
    fun add(future: Future<*>) {
        checkAddAllowed()
        checkInEventLoop()
        ++expectedCount
        @Suppress("UNCHECKED_CAST")
        (future as Future<Any?>).addListener(listener as GenericFutureListener<out Future<in Any?>>)
    }

    /**
     * Adds new promises to be combined. New promises may be added until an aggregate promise is added via the
     * [finish] method.
     *
     * @param promises the promises to add to this promise combiner
     *
     * @deprecated Replaced by [addAll(Future[])][addAll].
     */
    @Deprecated("Replaced by addAll(vararg Future).")
    fun addAll(vararg promises: Promise<*>) {
        @Suppress("UNCHECKED_CAST")
        addAll(*(promises as Array<out Future<*>>))
    }

    /**
     * Adds new futures to be combined. New futures may be added until an aggregate promise is added via the
     * [finish] method.
     *
     * @param futures the futures to add to this promise combiner
     */
    fun addAll(vararg futures: Future<*>) {
        for (future in futures) {
            add(future)
        }
    }

    /**
     * Sets the promise to be notified when all combined futures have finished. If all combined futures succeed,
     * then the aggregate promise will succeed. If one or more combined futures fails, then the aggregate promise will
     * fail with the cause of one of the failed futures. If more than one combined future fails, then exactly which
     * failure will be assigned to the aggregate promise is undefined.
     *
     * After this method is called, no more futures may be added via the [add] or [addAll] methods.
     *
     * @param aggregatePromise the promise to notify when all combined futures have finished
     */
    fun finish(aggregatePromise: Promise<Void>) {
        ObjectUtil.checkNotNull(aggregatePromise, "aggregatePromise")
        checkInEventLoop()
        if (this.aggregatePromise != null) {
            throw IllegalStateException("Already finished")
        }
        this.aggregatePromise = aggregatePromise
        if (doneCount == expectedCount) {
            tryPromise()
        }
    }

    private fun checkInEventLoop() {
        if (!executor.inEventLoop()) {
            throw IllegalStateException("Must be called from EventExecutor thread")
        }
    }

    private fun tryPromise(): Boolean {
        val promise = aggregatePromise!!
        @Suppress("UNCHECKED_CAST")
        return if (cause == null) (promise as Promise<Void?>).trySuccess(null) else promise.tryFailure(cause!!)
    }

    private fun checkAddAllowed() {
        if (aggregatePromise != null) {
            throw IllegalStateException("Adding promises is not allowed after finished adding")
        }
    }
}
