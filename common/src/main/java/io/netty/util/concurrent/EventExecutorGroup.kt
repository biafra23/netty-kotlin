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
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * The [EventExecutorGroup] is responsible for providing the [EventExecutor]s to use
 * via its [next] method. Besides this, it is also responsible for handling their
 * life-cycle and allows shutting them down in a global fashion.
 */
interface EventExecutorGroup : ScheduledExecutorService, Iterable<EventExecutor> {

    /**
     * Returns `true` if and only if all [EventExecutor]s managed by this [EventExecutorGroup]
     * are being [shut down gracefully][shutdownGracefully] or was [shut down][isShutdown].
     */
    fun isShuttingDown(): Boolean

    /**
     * Shortcut method for [shutdownGracefully] with sensible default values.
     *
     * @return the [terminationFuture]
     */
    fun shutdownGracefully(): Future<*>

    /**
     * Signals this executor that the caller wants the executor to be shut down. Once this method is called,
     * [isShuttingDown] starts to return `true`, and the executor prepares to shut itself down.
     * Unlike [shutdown], graceful shutdown ensures that no tasks are submitted for *'the quiet period'*
     * (usually a couple seconds) before it shuts itself down. If a task is submitted during the quiet period,
     * it is guaranteed to be accepted and the quiet period will start over.
     *
     * @param quietPeriod the quiet period as described in the documentation
     * @param timeout     the maximum amount of time to wait until the executor is [shut down][shutdown]
     *                    regardless if a task was submitted during the quiet period
     * @param unit        the unit of `quietPeriod` and `timeout`
     *
     * @return the [terminationFuture]
     */
    fun shutdownGracefully(quietPeriod: Long, timeout: Long, unit: TimeUnit): Future<*>

    /**
     * Returns the [Future] which is notified when all [EventExecutor]s managed by this
     * [EventExecutorGroup] have been terminated.
     */
    fun terminationFuture(): Future<*>

    /**
     * @deprecated [shutdownGracefully] or [shutdownGracefully] instead.
     */
    @Deprecated("Use shutdownGracefully(long, long, TimeUnit) or shutdownGracefully() instead.")
    override fun shutdown()

    /**
     * @deprecated [shutdownGracefully] or [shutdownGracefully] instead.
     */
    @Deprecated("Use shutdownGracefully(long, long, TimeUnit) or shutdownGracefully() instead.")
    override fun shutdownNow(): List<Runnable>

    /**
     * Returns one of the [EventExecutor]s managed by this [EventExecutorGroup].
     */
    fun next(): EventExecutor

    override fun iterator(): Iterator<EventExecutor>

    override fun submit(task: Runnable): Future<*>

    override fun <T> submit(task: Runnable, result: T): Future<T>

    override fun <T> submit(task: Callable<T>): Future<T>

    /**
     * The ticker for this executor. Usually the [schedule] methods will follow the
     * [system ticker][Ticker.systemTicker] (i.e. [System.nanoTime]), but especially for testing it is
     * sometimes useful to have more control over the ticker. In that case, this method will be overridden. Code that
     * schedules tasks on this executor should use this ticker in order to stay consistent with the executor (e.g. not
     * be surprised by scheduled tasks running "early").
     *
     * @return The ticker for this scheduler
     */
    fun ticker(): Ticker {
        return Ticker.systemTicker()
    }

    override fun schedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*>

    override fun <V> schedule(callable: Callable<V>, delay: Long, unit: TimeUnit): ScheduledFuture<V>

    override fun scheduleAtFixedRate(command: Runnable, initialDelay: Long, period: Long, unit: TimeUnit): ScheduledFuture<*>

    override fun scheduleWithFixedDelay(command: Runnable, initialDelay: Long, delay: Long, unit: TimeUnit): ScheduledFuture<*>
}
