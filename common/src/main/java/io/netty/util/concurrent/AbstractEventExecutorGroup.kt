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

import java.util.Collections
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Abstract base class for [EventExecutorGroup] implementations.
 */
abstract class AbstractEventExecutorGroup : EventExecutorGroup {
    override fun submit(task: Runnable): Future<*> {
        return next().submit(task)
    }

    override fun <T> submit(task: Runnable, result: T): Future<T> {
        return next().submit(task, result)
    }

    override fun <T> submit(task: Callable<T>): Future<T> {
        return next().submit(task)
    }

    override fun schedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> {
        return next().schedule(command, delay, unit)
    }

    override fun <V> schedule(callable: Callable<V>, delay: Long, unit: TimeUnit): ScheduledFuture<V> {
        return next().schedule(callable, delay, unit)
    }

    override fun scheduleAtFixedRate(
        command: Runnable, initialDelay: Long, period: Long, unit: TimeUnit
    ): ScheduledFuture<*> {
        return next().scheduleAtFixedRate(command, initialDelay, period, unit)
    }

    override fun scheduleWithFixedDelay(
        command: Runnable, initialDelay: Long, delay: Long, unit: TimeUnit
    ): ScheduledFuture<*> {
        return next().scheduleWithFixedDelay(command, initialDelay, delay, unit)
    }

    override fun shutdownGracefully(): Future<*> {
        return shutdownGracefully(
            AbstractEventExecutor.DEFAULT_SHUTDOWN_QUIET_PERIOD,
            AbstractEventExecutor.DEFAULT_SHUTDOWN_TIMEOUT,
            TimeUnit.SECONDS
        )
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

    @Throws(InterruptedException::class)
    override fun <T> invokeAll(tasks: Collection<Callable<T>>): List<java.util.concurrent.Future<T>> {
        return next().invokeAll(tasks)
    }

    @Throws(InterruptedException::class)
    override fun <T> invokeAll(
        tasks: Collection<Callable<T>>, timeout: Long, unit: TimeUnit
    ): List<java.util.concurrent.Future<T>> {
        return next().invokeAll(tasks, timeout, unit)
    }

    @Throws(InterruptedException::class, ExecutionException::class)
    override fun <T> invokeAny(tasks: Collection<Callable<T>>): T {
        return next().invokeAny(tasks)
    }

    @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
    override fun <T> invokeAny(tasks: Collection<Callable<T>>, timeout: Long, unit: TimeUnit): T {
        return next().invokeAny(tasks, timeout, unit)
    }

    override fun execute(command: Runnable) {
        next().execute(command)
    }
}
