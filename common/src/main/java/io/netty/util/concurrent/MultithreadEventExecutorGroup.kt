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

import io.netty.util.concurrent.AutoScalingEventExecutorChooserFactory.AutoScalingUtilizationMetric
import io.netty.util.concurrent.EventExecutorChooserFactory.ObservableEventExecutorChooser
import io.netty.util.internal.ObjectUtil.checkPositive
import java.util.Collections
import java.util.LinkedHashSet
import java.util.concurrent.Executor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Abstract base class for [EventExecutorGroup] implementations that handles their tasks with multiple threads at
 * the same time.
 */
abstract class MultithreadEventExecutorGroup : AbstractEventExecutorGroup {

    private val children: Array<EventExecutor>
    private val readonlyChildren: Set<EventExecutor>
    private val terminatedChildren = AtomicInteger()
    private val terminationFuture: Promise<*> = DefaultPromise<Any?>(GlobalEventExecutor.INSTANCE)
    private val chooser: EventExecutorChooserFactory.EventExecutorChooser

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param threadFactory     the ThreadFactory to use, or `null` if the default should be used.
     * @param args              arguments which will passed to each [newChild] call
     */
    protected constructor(nThreads: Int, threadFactory: ThreadFactory?, vararg args: Any?)
        : this(nThreads, if (threadFactory == null) null else ThreadPerTaskExecutor(threadFactory), *args)

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param executor          the Executor to use, or `null` if the default should be used.
     * @param args              arguments which will passed to each [newChild] call
     */
    protected constructor(nThreads: Int, executor: Executor?, vararg args: Any?)
        : this(nThreads, executor, DefaultEventExecutorChooserFactory.INSTANCE, *args)

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param executor          the Executor to use, or `null` if the default should be used.
     * @param chooserFactory    the [EventExecutorChooserFactory] to use.
     * @param args              arguments which will passed to each [newChild] call
     */
    @Suppress("SpreadOperator")
    protected constructor(
        nThreads: Int, executor: Executor?,
        chooserFactory: EventExecutorChooserFactory, vararg args: Any?
    ) {
        checkPositive(nThreads, "nThreads")

        @Suppress("NAME_SHADOWING")
        var executor = executor
        if (executor == null) {
            executor = ThreadPerTaskExecutor(newDefaultThreadFactory())
        }

        children = Array(nThreads) { null as EventExecutor? } as Array<EventExecutor>

        for (i in 0 until nThreads) {
            var success = false
            try {
                children[i] = newChild(executor, *args)
                success = true
            } catch (e: Exception) {
                // TODO: Think about if this is a good exception type
                throw IllegalStateException("failed to create a child event loop", e)
            } finally {
                if (!success) {
                    for (j in 0 until i) {
                        children[j].shutdownGracefully()
                    }

                    for (j in 0 until i) {
                        val e = children[j]
                        try {
                            while (!e.isTerminated) {
                                e.awaitTermination(Integer.MAX_VALUE.toLong(), TimeUnit.SECONDS)
                            }
                        } catch (interrupted: InterruptedException) {
                            // Let the caller handle the interruption.
                            Thread.currentThread().interrupt()
                            break
                        }
                    }
                }
            }
        }

        chooser = chooserFactory.newChooser(children)

        val terminationListener = object : GenericFutureListener<Future<Any?>> {
            override fun operationComplete(future: Future<Any?>) {
                if (terminatedChildren.incrementAndGet() == children.size) {
                    @Suppress("UNCHECKED_CAST")
                    (terminationFuture as Promise<Any?>).setSuccess(null)
                }
            }
        }

        for (e in children) {
            e.terminationFuture().addListener(terminationListener)
        }

        val childrenSet = LinkedHashSet<EventExecutor>(children.size)
        Collections.addAll(childrenSet, *children)
        readonlyChildren = Collections.unmodifiableSet(childrenSet)
    }

    protected open fun newDefaultThreadFactory(): ThreadFactory {
        return DefaultThreadFactory(javaClass)
    }

    override fun next(): EventExecutor {
        return chooser.next()
    }

    override fun iterator(): MutableIterator<EventExecutor> {
        return readonlyChildren.iterator() as MutableIterator<EventExecutor>
    }

    /**
     * Return the number of [EventExecutor] this implementation uses. This number is the maps
     * 1:1 to the threads it use.
     */
    fun executorCount(): Int {
        return children.size
    }

    /**
     * Returns the number of currently active threads if the group is using an
     * [ObservableEventExecutorChooser]. Otherwise, for a non-scaling group,
     * this method returns the total number of threads, as all are considered active.
     *
     * @return the count of active threads.
     */
    open fun activeExecutorCount(): Int {
        if (chooser is ObservableEventExecutorChooser) {
            return (chooser as ObservableEventExecutorChooser).activeExecutorCount()
        }
        return executorCount()
    }

    /**
     * Returns a list of real-time utilization metrics if the group was configured
     * with a compatible [EventExecutorChooserFactory], otherwise an empty list.
     *
     * @return A list of [AutoScalingUtilizationMetric] objects.
     */
    open fun executorUtilizations(): List<AutoScalingUtilizationMetric> {
        if (chooser is ObservableEventExecutorChooser) {
            return (chooser as ObservableEventExecutorChooser).executorUtilizations()
        }
        return emptyList()
    }

    /**
     * Create a new EventExecutor which will later then accessible via the [next] method. This method will be
     * called for each thread that will serve this [MultithreadEventExecutorGroup].
     */
    @Throws(Exception::class)
    protected abstract fun newChild(executor: Executor, vararg args: Any?): EventExecutor

    override fun shutdownGracefully(quietPeriod: Long, timeout: Long, unit: TimeUnit): Future<*> {
        for (l in children) {
            l.shutdownGracefully(quietPeriod, timeout, unit)
        }
        return terminationFuture()
    }

    override fun terminationFuture(): Future<*> {
        return terminationFuture
    }

    @Deprecated("Use shutdownGracefully instead.")
    override fun shutdown() {
        for (l in children) {
            @Suppress("DEPRECATION")
            l.shutdown()
        }
    }

    override fun isShuttingDown(): Boolean {
        for (l in children) {
            if (!l.isShuttingDown()) {
                return false
            }
        }
        return true
    }

    override fun isShutdown(): Boolean {
        for (l in children) {
            if (!l.isShutdown) {
                return false
            }
        }
        return true
    }

    override fun isTerminated(): Boolean {
        for (l in children) {
            if (!l.isTerminated()) {
                return false
            }
        }
        return true
    }

    @Throws(InterruptedException::class)
    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
        val deadline = System.nanoTime() + unit.toNanos(timeout)
        loop@ for (l in children) {
            while (true) {
                val timeLeft = deadline - System.nanoTime()
                if (timeLeft <= 0) {
                    break@loop
                }
                if (l.awaitTermination(timeLeft, TimeUnit.NANOSECONDS)) {
                    break
                }
            }
        }
        return isTerminated
    }
}
