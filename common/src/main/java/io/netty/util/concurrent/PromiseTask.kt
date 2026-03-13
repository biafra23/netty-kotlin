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

import java.util.concurrent.Callable
import java.util.concurrent.RunnableFuture

internal open class PromiseTask<V> : DefaultPromise<V>, RunnableFuture<V> {

    private class RunnableAdapter<T>(val task: Runnable, val result: T) : Callable<T> {
        override fun call(): T {
            task.run()
            return result
        }

        override fun toString(): String = "Callable(task: $task, result: $result)"
    }

    private class SentinelRunnable(private val name: String) : Runnable {
        override fun run() { } // no-op
        override fun toString(): String = name
    }

    // Strictly of type Callable<V> or Runnable
    private var task: Any

    constructor(executor: EventExecutor, runnable: Runnable, result: V) : super(executor) {
        task = if (result == null) runnable else RunnableAdapter(runnable, result)
    }

    constructor(executor: EventExecutor, runnable: Runnable) : super(executor) {
        task = runnable
    }

    constructor(executor: EventExecutor, callable: Callable<V>) : super(executor) {
        task = callable
    }

    final override fun hashCode(): Int = System.identityHashCode(this)

    final override fun equals(other: Any?): Boolean = this === other

    @Suppress("UNCHECKED_CAST")
    @Throws(Throwable::class)
    internal open fun runTask(): V? {
        val task = this.task
        return if (task is Callable<*>) {
            (task as Callable<V>).call()
        } else {
            (task as Runnable).run()
            null
        }
    }

    override fun run() {
        try {
            if (setUncancellableInternal()) {
                val result = runTask()
                setSuccessInternal(result)
            }
        } catch (e: Throwable) {
            setFailureInternal(e)
        }
    }

    private fun clearTaskAfterCompletion(done: Boolean, result: Runnable): Boolean {
        if (done) {
            // The only time where it might be possible for the sentinel task
            // to be called is in the case of a periodic ScheduledFutureTask,
            // in which case it's a benign race with cancellation and the (null)
            // return value is not used.
            task = result
        }
        return done
    }

    final override fun setFailure(cause: Throwable): Promise<V> {
        throw IllegalStateException()
    }

    protected fun setFailureInternal(cause: Throwable): Promise<V> {
        super.setFailure(cause)
        clearTaskAfterCompletion(true, FAILED)
        return this
    }

    final override fun tryFailure(cause: Throwable): Boolean {
        return false
    }

    protected fun tryFailureInternal(cause: Throwable): Boolean {
        return clearTaskAfterCompletion(super.tryFailure(cause), FAILED)
    }

    final override fun setSuccess(result: V): Promise<V> {
        throw IllegalStateException()
    }

    protected fun setSuccessInternal(result: V?): Promise<V> {
        @Suppress("UNCHECKED_CAST")
        super.setSuccess(result as V)
        clearTaskAfterCompletion(true, COMPLETED)
        return this
    }

    final override fun trySuccess(result: V): Boolean {
        return false
    }

    protected fun trySuccessInternal(result: V): Boolean {
        return clearTaskAfterCompletion(super.trySuccess(result), COMPLETED)
    }

    final override fun setUncancellable(): Boolean {
        throw IllegalStateException()
    }

    protected fun setUncancellableInternal(): Boolean {
        return super.setUncancellable()
    }

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        return clearTaskAfterCompletion(super.cancel(mayInterruptIfRunning), CANCELLED)
    }

    override fun toStringBuilder(): StringBuilder {
        val buf = super.toStringBuilder()
        buf.setCharAt(buf.length - 1, ',')
        return buf.append(" task: ")
            .append(task)
            .append(')')
    }

    companion object {
        private val COMPLETED: Runnable = SentinelRunnable("COMPLETED")
        private val CANCELLED: Runnable = SentinelRunnable("CANCELLED")
        private val FAILED: Runnable = SentinelRunnable("FAILED")
    }
}
