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

import io.netty.util.internal.DefaultPriorityQueue
import io.netty.util.internal.PriorityQueueNode
import java.util.concurrent.Callable
import java.util.concurrent.Delayed
import java.util.concurrent.TimeUnit

@Suppress("ComparableImplementedButEqualsNotOverridden")
internal class ScheduledFutureTask<V> : PromiseTask<V>, ScheduledFuture<V>, PriorityQueueNode {

    // set once when added to priority queue
    private var id: Long = 0

    private var deadlineNanos: Long
    /* 0 - no repeat, >0 - repeat at fixed rate, <0 - repeat with fixed delay */
    private val periodNanos: Long

    private var queueIndex: Int = PriorityQueueNode.INDEX_NOT_IN_QUEUE

    constructor(executor: AbstractScheduledEventExecutor, runnable: Runnable, nanoTime: Long) : super(executor, runnable) {
        deadlineNanos = nanoTime
        periodNanos = 0
    }

    constructor(executor: AbstractScheduledEventExecutor, runnable: Runnable, nanoTime: Long, period: Long) : super(executor, runnable) {
        deadlineNanos = nanoTime
        periodNanos = validatePeriod(period)
    }

    constructor(executor: AbstractScheduledEventExecutor, callable: Callable<V>, nanoTime: Long, period: Long) : super(executor, callable) {
        deadlineNanos = nanoTime
        periodNanos = validatePeriod(period)
    }

    constructor(executor: AbstractScheduledEventExecutor, callable: Callable<V>, nanoTime: Long) : super(executor, callable) {
        deadlineNanos = nanoTime
        periodNanos = 0
    }

    fun setId(id: Long): ScheduledFutureTask<V> {
        if (this.id == 0L) {
            this.id = id
        }
        return this
    }

    fun getId(): Long = id

    override fun executor(): EventExecutor = super.executor()!!

    fun deadlineNanos(): Long = deadlineNanos

    fun setConsumed() {
        // Optimization to avoid checking system clock again
        // after deadline has passed and task has been dequeued
        if (periodNanos == 0L) {
            assert(scheduledExecutor().currentTimeNanosInternal() >= deadlineNanos)
            deadlineNanos = 0L
        }
    }

    fun delayNanos(): Long {
        if (deadlineNanos == 0L) {
            return 0L
        }
        return delayNanos(scheduledExecutor().currentTimeNanosInternal())
    }

    fun delayNanos(currentTimeNanos: Long): Long {
        return deadlineToDelayNanos(currentTimeNanos, deadlineNanos)
    }

    override fun getDelay(unit: TimeUnit): Long {
        return unit.convert(delayNanos(), TimeUnit.NANOSECONDS)
    }

    override operator fun compareTo(other: Delayed): Int {
        if (this === other) {
            return 0
        }

        val that = other as ScheduledFutureTask<*>
        val d = deadlineNanos() - that.deadlineNanos()
        return when {
            d < 0 -> -1
            d > 0 -> 1
            id < that.id -> -1
            else -> {
                assert(id != that.id)
                1
            }
        }
    }

    override fun run() {
        assert(executor().inEventLoop())
        try {
            if (delayNanos() > 0L) {
                // Not yet expired, need to add or remove from queue
                if (isCancelled) {
                    scheduledExecutor().scheduledTaskQueue().removeTyped(this)
                } else {
                    scheduledExecutor().scheduleFromEventLoop(this)
                }
                return
            }
            if (periodNanos == 0L) {
                if (setUncancellableInternal()) {
                    val result = runTask()
                    setSuccessInternal(result)
                }
            } else {
                // check if is done as it may was cancelled
                if (!isCancelled) {
                    runTask()
                    if (!executor().isShutdown) {
                        if (periodNanos > 0) {
                            deadlineNanos += periodNanos
                        } else {
                            deadlineNanos = scheduledExecutor().currentTimeNanosInternal() - periodNanos
                        }
                        if (!isCancelled) {
                            scheduledExecutor().scheduleFromEventLoop(this)
                        }
                    }
                }
            }
        } catch (cause: Throwable) {
            setFailureInternal(cause)
        }
    }

    private fun scheduledExecutor(): AbstractScheduledEventExecutor {
        return executor() as AbstractScheduledEventExecutor
    }

    /**
     * {@inheritDoc}
     *
     * @param mayInterruptIfRunning this value has no effect in this implementation.
     */
    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        val canceled = super.cancel(mayInterruptIfRunning)
        if (canceled) {
            scheduledExecutor().removeScheduled(this)
        }
        return canceled
    }

    fun cancelWithoutRemove(mayInterruptIfRunning: Boolean): Boolean {
        return super.cancel(mayInterruptIfRunning)
    }

    override fun toStringBuilder(): StringBuilder {
        val buf = super.toStringBuilder()
        buf.setCharAt(buf.length - 1, ',')
        return buf.append(" deadline: ")
            .append(deadlineNanos)
            .append(", period: ")
            .append(periodNanos)
            .append(')')
    }

    override fun priorityQueueIndex(queue: DefaultPriorityQueue<*>): Int {
        return queueIndex
    }

    override fun priorityQueueIndex(queue: DefaultPriorityQueue<*>, i: Int) {
        queueIndex = i
    }

    companion object {
        @JvmStatic
        fun deadlineToDelayNanos(currentTimeNanos: Long, deadlineNanos: Long): Long {
            return if (deadlineNanos == 0L) 0L else Math.max(0L, deadlineNanos - currentTimeNanos)
        }

        private fun validatePeriod(period: Long): Long {
            if (period == 0L) {
                throw IllegalArgumentException("period: 0 (expected: != 0)")
            }
            return period
        }
    }
}
