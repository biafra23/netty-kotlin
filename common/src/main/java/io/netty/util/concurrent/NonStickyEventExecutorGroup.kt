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
import io.netty.util.internal.PlatformDependent
import io.netty.util.internal.UnstableApi
import java.util.Queue
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * [EventExecutorGroup] which will preserve [Runnable] execution order but makes no guarantees about what
 * [EventExecutor] (and therefore [Thread]) will be used to execute the [Runnable]s.
 *
 * The [EventExecutorGroup.next] for the wrapped [EventExecutorGroup] must **NOT** return
 * executors of type [OrderedEventExecutor].
 */
@UnstableApi
class NonStickyEventExecutorGroup : EventExecutorGroup {
    private val group: EventExecutorGroup
    private val maxTaskExecutePerRun: Int

    /**
     * Creates a new instance. Be aware that the given [EventExecutorGroup] **MUST NOT** contain
     * any [OrderedEventExecutor]s.
     */
    constructor(group: EventExecutorGroup) : this(group, 1024)

    /**
     * Creates a new instance. Be aware that the given [EventExecutorGroup] **MUST NOT** contain
     * any [OrderedEventExecutor]s.
     */
    constructor(group: EventExecutorGroup, maxTaskExecutePerRun: Int) {
        this.group = verify(group)
        this.maxTaskExecutePerRun = ObjectUtil.checkPositive(maxTaskExecutePerRun, "maxTaskExecutePerRun")
    }

    private fun newExecutor(executor: EventExecutor): NonStickyOrderedEventExecutor {
        return NonStickyOrderedEventExecutor(executor, maxTaskExecutePerRun)
    }

    override fun isShuttingDown(): Boolean {
        return group.isShuttingDown()
    }

    override fun shutdownGracefully(): Future<*> {
        return group.shutdownGracefully()
    }

    override fun shutdownGracefully(quietPeriod: Long, timeout: Long, unit: TimeUnit): Future<*> {
        return group.shutdownGracefully(quietPeriod, timeout, unit)
    }

    override fun terminationFuture(): Future<*> {
        return group.terminationFuture()
    }

    @Suppress("DEPRECATION")
    override fun shutdown() {
        group.shutdown()
    }

    @Suppress("DEPRECATION")
    override fun shutdownNow(): List<Runnable> {
        return group.shutdownNow()
    }

    override fun next(): EventExecutor {
        return newExecutor(group.next())
    }

    override fun iterator(): MutableIterator<EventExecutor> {
        val itr = group.iterator()
        return object : MutableIterator<EventExecutor> {
            override fun hasNext(): Boolean {
                return itr.hasNext()
            }

            override fun next(): EventExecutor {
                return newExecutor(itr.next())
            }

            override fun remove() {
                (itr as MutableIterator<EventExecutor>).remove()
            }
        }
    }

    override fun submit(task: Runnable): Future<*> {
        return group.submit(task)
    }

    override fun <T> submit(task: Runnable, result: T): Future<T> {
        return group.submit(task, result)
    }

    override fun <T> submit(task: Callable<T>): Future<T> {
        return group.submit(task)
    }

    override fun schedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> {
        return group.schedule(command, delay, unit)
    }

    override fun <V> schedule(callable: Callable<V>, delay: Long, unit: TimeUnit): ScheduledFuture<V> {
        return group.schedule(callable, delay, unit)
    }

    override fun scheduleAtFixedRate(
        command: Runnable, initialDelay: Long, period: Long, unit: TimeUnit
    ): ScheduledFuture<*> {
        return group.scheduleAtFixedRate(command, initialDelay, period, unit)
    }

    override fun scheduleWithFixedDelay(
        command: Runnable, initialDelay: Long, delay: Long, unit: TimeUnit
    ): ScheduledFuture<*> {
        return group.scheduleWithFixedDelay(command, initialDelay, delay, unit)
    }

    override fun isShutdown(): Boolean {
        return group.isShutdown
    }

    override fun isTerminated(): Boolean {
        return group.isTerminated
    }

    @Throws(InterruptedException::class)
    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
        return group.awaitTermination(timeout, unit)
    }

    @Throws(InterruptedException::class)
    override fun <T> invokeAll(
        tasks: MutableCollection<out Callable<T>>
    ): MutableList<java.util.concurrent.Future<T>> {
        return group.invokeAll(tasks)
    }

    @Throws(InterruptedException::class)
    override fun <T> invokeAll(
        tasks: MutableCollection<out Callable<T>>, timeout: Long, unit: TimeUnit
    ): MutableList<java.util.concurrent.Future<T>> {
        return group.invokeAll(tasks, timeout, unit)
    }

    @Throws(InterruptedException::class, ExecutionException::class)
    override fun <T> invokeAny(tasks: MutableCollection<out Callable<T>>): T {
        return group.invokeAny(tasks)
    }

    @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
    override fun <T> invokeAny(
        tasks: MutableCollection<out Callable<T>>, timeout: Long, unit: TimeUnit
    ): T {
        return group.invokeAny(tasks, timeout, unit)
    }

    override fun execute(command: Runnable) {
        group.execute(command)
    }

    private class NonStickyOrderedEventExecutor(
        private val executor: EventExecutor,
        private val maxTaskExecutePerRun: Int
    ) : AbstractEventExecutor(executor), Runnable, OrderedEventExecutor {

        private val tasks: Queue<Runnable> = PlatformDependent.newMpscQueue()
        private val state = AtomicInteger()
        private val executingThread = AtomicReference<Thread?>()

        override fun run() {
            if (!state.compareAndSet(SUBMITTED, RUNNING)) {
                return
            }
            val current = Thread.currentThread()
            executingThread.set(current)
            while (true) {
                var i = 0
                try {
                    while (i < maxTaskExecutePerRun) {
                        val task = tasks.poll() ?: break
                        safeExecute(task)
                        i++
                    }
                } finally {
                    if (i == maxTaskExecutePerRun) {
                        try {
                            state.set(SUBMITTED)
                            // Only set executingThread to null if no other thread did update it yet.
                            executingThread.compareAndSet(current, null)
                            executor.execute(this)
                            return // done
                        } catch (ignore: Throwable) {
                            // Restore executingThread since we're continuing to execute tasks.
                            executingThread.set(current)
                            // Reset the state back to running as we will keep on executing tasks.
                            state.set(RUNNING)
                            // if an error happened we should just ignore it and let the loop run again as there is not
                            // much else we can do. Most likely this was triggered by a full task queue. In this case
                            // we just will run more tasks and try again later.
                        }
                    } else {
                        state.set(NONE)
                        // After setting the state to NONE, look at the tasks queue one more time.
                        // If it is empty, then we can return from this method.
                        // Otherwise, it means the producer thread has called execute(Runnable)
                        // and enqueued a task in between the tasks.poll() above and the state.set(NONE) here.
                        // There are two possible scenarios when this happens
                        //
                        // 1. The producer thread sees state == NONE, hence the compareAndSet(NONE, SUBMITTED)
                        //    is successfully setting the state to SUBMITTED. This mean the producer
                        //    will call / has called executor.execute(this). In this case, we can just return.
                        // 2. The producer thread don't see the state change, hence the compareAndSet(NONE, SUBMITTED)
                        //    returns false. In this case, the producer thread won't call executor.execute.
                        //    In this case, we need to change the state to RUNNING and keeps running.
                        //
                        // The above cases can be distinguished by performing a
                        // compareAndSet(NONE, RUNNING). If it returns "false", it is case 1; otherwise it is case 2.
                        if (tasks.isEmpty() || !state.compareAndSet(NONE, RUNNING)) {
                            // Only set executingThread to null if no other thread did update it yet.
                            executingThread.compareAndSet(current, null)
                            return // done
                        }
                    }
                }
            }
        }

        override fun inEventLoop(thread: Thread): Boolean {
            return executingThread.get() === thread
        }

        override fun isShuttingDown(): Boolean {
            return executor.isShutdown
        }

        override fun shutdownGracefully(quietPeriod: Long, timeout: Long, unit: TimeUnit): Future<*> {
            return executor.shutdownGracefully(quietPeriod, timeout, unit)
        }

        override fun terminationFuture(): Future<*> {
            return executor.terminationFuture()
        }

        @Deprecated("Use shutdownGracefully instead.")
        override fun shutdown() {
            @Suppress("DEPRECATION")
            executor.shutdown()
        }

        override fun isShutdown(): Boolean {
            return executor.isShutdown
        }

        override fun isTerminated(): Boolean {
            return executor.isTerminated
        }

        @Throws(InterruptedException::class)
        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
            return executor.awaitTermination(timeout, unit)
        }

        override fun execute(command: Runnable) {
            if (!tasks.offer(command)) {
                throw RejectedExecutionException()
            }
            if (state.compareAndSet(NONE, SUBMITTED)) {
                // Actually it could happen that the runnable was picked up in between but we not care to much and just
                // execute ourself. At worst this will be a NOOP when run() is called.
                executor.execute(this)
            }
        }

        companion object {
            private const val NONE = 0
            private const val SUBMITTED = 1
            private const val RUNNING = 2
        }
    }

    companion object {
        private fun verify(group: EventExecutorGroup): EventExecutorGroup {
            val executors = ObjectUtil.checkNotNull(group, "group").iterator()
            while (executors.hasNext()) {
                val executor = executors.next()
                if (executor is OrderedEventExecutor) {
                    throw IllegalArgumentException(
                        "EventExecutorGroup $group contains OrderedEventExecutors: $executor"
                    )
                }
            }
            return group
        }
    }
}
