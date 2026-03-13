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

import io.netty.util.internal.UnstableApi
import io.netty.util.internal.logging.InternalLoggerFactory
import org.jetbrains.annotations.Async.Execute
import java.util.Collections
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.Callable
import java.util.concurrent.RunnableFuture
import java.util.concurrent.TimeUnit

/**
 * Abstract base class for [EventExecutor] implementations.
 */
abstract class AbstractEventExecutor
@JvmOverloads
protected constructor(private val parent: EventExecutorGroup? = null) : AbstractExecutorService(), EventExecutor {

    override fun parent(): EventExecutorGroup {
        return parent!!
    }

    override fun next(): EventExecutor {
        return this
    }

    override fun iterator(): MutableIterator<EventExecutor> {
        return selfCollection.iterator()
    }

    override fun shutdownGracefully(): Future<*> {
        return shutdownGracefully(DEFAULT_SHUTDOWN_QUIET_PERIOD, DEFAULT_SHUTDOWN_TIMEOUT, TimeUnit.SECONDS)
    }

    /**
     * @deprecated [shutdownGracefully] or [shutdownGracefully] instead.
     */
    @Deprecated("Use shutdownGracefully(long, long, TimeUnit) or shutdownGracefully() instead.")
    abstract override fun shutdown()

    /**
     * @deprecated [shutdownGracefully] or [shutdownGracefully] instead.
     */
    @Deprecated("Use shutdownGracefully(long, long, TimeUnit) or shutdownGracefully() instead.")
    override fun shutdownNow(): List<Runnable> {
        @Suppress("DEPRECATION")
        shutdown()
        return Collections.emptyList()
    }

    override fun submit(task: Runnable): Future<*> {
        return super<AbstractExecutorService>.submit(task) as Future<*>
    }

    override fun <T> submit(task: Runnable, result: T): Future<T> {
        return super<AbstractExecutorService>.submit(task, result) as Future<T>
    }

    override fun <T> submit(task: Callable<T>): Future<T> {
        return super<AbstractExecutorService>.submit(task) as Future<T>
    }

    final override fun <T> newTaskFor(runnable: Runnable, value: T): RunnableFuture<T> {
        return PromiseTask(this, runnable, value)
    }

    final override fun <T> newTaskFor(callable: Callable<T>): RunnableFuture<T> {
        return PromiseTask(this, callable)
    }

    override fun schedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> {
        throw UnsupportedOperationException()
    }

    override fun <V> schedule(callable: Callable<V>, delay: Long, unit: TimeUnit): ScheduledFuture<V> {
        throw UnsupportedOperationException()
    }

    override fun scheduleAtFixedRate(
        command: Runnable, initialDelay: Long, period: Long, unit: TimeUnit
    ): ScheduledFuture<*> {
        throw UnsupportedOperationException()
    }

    override fun scheduleWithFixedDelay(
        command: Runnable, initialDelay: Long, delay: Long, unit: TimeUnit
    ): ScheduledFuture<*> {
        throw UnsupportedOperationException()
    }

    /**
     * Like [execute] but does not guarantee the task will be run until either
     * a non-lazy task is executed or the executor is shut down.
     *
     * The default implementation just delegates to [execute].
     */
    @UnstableApi
    open fun lazyExecute(task: Runnable) {
        execute(task)
    }

    /**
     * @deprecated override [SingleThreadEventExecutor.wakesUpForTask] to re-create this behaviour
     */
    @Deprecated("Override SingleThreadEventExecutor.wakesUpForTask to re-create this behaviour")
    interface LazyRunnable : Runnable

    companion object {
        private val logger = InternalLoggerFactory.getInstance(AbstractEventExecutor::class.java)

        @JvmStatic
        val DEFAULT_SHUTDOWN_QUIET_PERIOD: Long = 2

        @JvmStatic
        val DEFAULT_SHUTDOWN_TIMEOUT: Long = 15

        /**
         * Try to execute the given [Runnable] and just log if it throws a [Throwable].
         */
        @JvmStatic
        fun safeExecute(task: Runnable) {
            try {
                runTask(task)
            } catch (t: Throwable) {
                logger.warn("A task raised an exception. Task: {}", task, t)
            }
        }

        @JvmStatic
        fun runTask(@Execute task: Runnable) {
            task.run()
        }
    }

    private val selfCollection: MutableCollection<EventExecutor> = Collections.singleton(this)
}
