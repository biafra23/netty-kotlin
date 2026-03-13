/*
 * Copyright 2015 The Netty Project
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
import io.netty.util.internal.ObjectUtil
import io.netty.util.internal.PriorityQueue
import java.util.Comparator
import java.util.Objects
import java.util.Queue
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

/**
 * Abstract base class for [EventExecutor]s that want to support scheduling.
 */
abstract class AbstractScheduledEventExecutor : AbstractEventExecutor {

    @JvmField
    internal var scheduledTaskQueue: PriorityQueue<ScheduledFutureTask<*>>? = null

    @JvmField
    internal var nextTaskId: Long = 0

    protected constructor()

    protected constructor(parent: EventExecutorGroup?) : super(parent)

    override fun ticker(): Ticker {
        return Ticker.systemTicker()
    }

    /**
     * Get the current time in nanoseconds by this executor's clock.
     *
     * @deprecated Please use (or override) [ticker] instead.
     */
    @Deprecated("Please use (or override) ticker() instead.")
    protected open fun getCurrentTimeNanos(): Long {
        return ticker().nanoTime()
    }

    /**
     * Internal accessor for [getCurrentTimeNanos] for same-package callers.
     */
    internal fun currentTimeNanosInternal(): Long {
        @Suppress("DEPRECATION")
        return getCurrentTimeNanos()
    }

    /**
     * @deprecated Use the non-static [ticker] instead.
     */
    @Deprecated("Use the non-static ticker() instead.")
    protected fun delayNanos(currentTimeNanos: Long, scheduledPurgeInterval: Long): Long {
        @Suppress("VARIABLE_SHADOWING")
        val adjustedNanos = currentTimeNanos - ticker().initialNanoTime()

        val scheduledTask = peekScheduledTask()
        if (scheduledTask == null) {
            return scheduledPurgeInterval
        }

        return scheduledTask.delayNanos(adjustedNanos)
    }

    internal fun scheduledTaskQueue(): PriorityQueue<ScheduledFutureTask<*>> {
        if (scheduledTaskQueue == null) {
            scheduledTaskQueue = DefaultPriorityQueue(
                SCHEDULED_FUTURE_TASK_COMPARATOR,
                // Use same initial capacity as java.util.PriorityQueue
                11
            )
        }
        return scheduledTaskQueue!!
    }

    /**
     * Cancel all scheduled tasks.
     *
     * This method MUST be called only when [inEventLoop] is `true`.
     */
    protected open fun cancelScheduledTasks() {
        assert(inEventLoop())
        val scheduledTaskQueue = this.scheduledTaskQueue
        if (isNullOrEmpty(scheduledTaskQueue)) {
            return
        }

        val scheduledTasks = scheduledTaskQueue!!.toTypedArray()

        for (task in scheduledTasks) {
            (task as ScheduledFutureTask<*>).cancelWithoutRemove(false)
        }

        scheduledTaskQueue.clearIgnoringIndexes()
    }

    /**
     * @see .pollScheduledTask
     */
    protected fun pollScheduledTask(): Runnable? {
        @Suppress("DEPRECATION")
        return pollScheduledTask(getCurrentTimeNanos())
    }

    /**
     * Fetch scheduled tasks from the internal queue and add these to the given [Queue].
     */
    protected fun fetchFromScheduledTaskQueue(taskQueue: Queue<Runnable>): Boolean {
        assert(inEventLoop())
        Objects.requireNonNull(taskQueue, "taskQueue")
        if (scheduledTaskQueue == null || scheduledTaskQueue!!.isEmpty()) {
            return true
        }
        @Suppress("DEPRECATION")
        val nanoTime = getCurrentTimeNanos()
        while (true) {
            val scheduledTask = pollScheduledTask(nanoTime) as? ScheduledFutureTask<*> ?: return true
            if (scheduledTask.isCancelled) {
                continue
            }
            if (!taskQueue.offer(scheduledTask)) {
                // No space left in the task queue add it back to the scheduledTaskQueue so we pick it up again.
                scheduledTaskQueue!!.add(scheduledTask)
                return false
            }
        }
    }

    /**
     * Return the [Runnable] which is ready to be executed with the given `nanoTime`.
     * You should use [getCurrentTimeNanos] to retrieve the correct `nanoTime`.
     */
    protected fun pollScheduledTask(nanoTime: Long): Runnable? {
        assert(inEventLoop())

        val scheduledTask = peekScheduledTask()
        if (scheduledTask == null || scheduledTask.deadlineNanos() - nanoTime > 0) {
            return null
        }
        scheduledTaskQueue!!.remove()
        scheduledTask.setConsumed()
        return scheduledTask
    }

    /**
     * Return the nanoseconds until the next scheduled task is ready to be run or `-1` if no task is scheduled.
     */
    protected fun nextScheduledTaskNano(): Long {
        val scheduledTask = peekScheduledTask()
        return scheduledTask?.delayNanos() ?: -1
    }

    /**
     * Return the deadline (in nanoseconds) when the next scheduled task is ready to be run or `-1`
     * if no task is scheduled.
     */
    protected fun nextScheduledTaskDeadlineNanos(): Long {
        val scheduledTask = peekScheduledTask()
        return scheduledTask?.deadlineNanos() ?: -1
    }

    internal fun peekScheduledTask(): ScheduledFutureTask<*>? {
        val scheduledTaskQueue: Queue<ScheduledFutureTask<*>>? = this.scheduledTaskQueue
        return scheduledTaskQueue?.peek()
    }

    /**
     * Returns `true` if a scheduled task is ready for processing.
     */
    protected fun hasScheduledTasks(): Boolean {
        val scheduledTask = peekScheduledTask()
        @Suppress("DEPRECATION")
        return scheduledTask != null && scheduledTask.deadlineNanos() <= getCurrentTimeNanos()
    }

    override fun schedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> {
        ObjectUtil.checkNotNull(command, "command")
        ObjectUtil.checkNotNull(unit, "unit")
        var actualDelay = delay
        if (actualDelay < 0) {
            actualDelay = 0
        }
        validateScheduled0(actualDelay, unit)

        @Suppress("DEPRECATION")
        return schedule(
            ScheduledFutureTask<Void>(
                this,
                command,
                deadlineNanos(getCurrentTimeNanos(), unit.toNanos(actualDelay))
            )
        )
    }

    override fun <V> schedule(callable: Callable<V>, delay: Long, unit: TimeUnit): ScheduledFuture<V> {
        ObjectUtil.checkNotNull(callable, "callable")
        ObjectUtil.checkNotNull(unit, "unit")
        var actualDelay = delay
        if (actualDelay < 0) {
            actualDelay = 0
        }
        validateScheduled0(actualDelay, unit)

        @Suppress("DEPRECATION")
        return schedule(
            ScheduledFutureTask(
                this, callable, deadlineNanos(getCurrentTimeNanos(), unit.toNanos(actualDelay))
            )
        )
    }

    override fun scheduleAtFixedRate(
        command: Runnable, initialDelay: Long, period: Long, unit: TimeUnit
    ): ScheduledFuture<*> {
        ObjectUtil.checkNotNull(command, "command")
        ObjectUtil.checkNotNull(unit, "unit")
        if (initialDelay < 0) {
            throw IllegalArgumentException(
                String.format("initialDelay: %d (expected: >= 0)", initialDelay)
            )
        }
        if (period <= 0) {
            throw IllegalArgumentException(
                String.format("period: %d (expected: > 0)", period)
            )
        }
        validateScheduled0(initialDelay, unit)
        validateScheduled0(period, unit)

        @Suppress("DEPRECATION")
        return schedule(
            ScheduledFutureTask<Void>(
                this, command,
                deadlineNanos(getCurrentTimeNanos(), unit.toNanos(initialDelay)),
                unit.toNanos(period)
            )
        )
    }

    override fun scheduleWithFixedDelay(
        command: Runnable, initialDelay: Long, delay: Long, unit: TimeUnit
    ): ScheduledFuture<*> {
        ObjectUtil.checkNotNull(command, "command")
        ObjectUtil.checkNotNull(unit, "unit")
        if (initialDelay < 0) {
            throw IllegalArgumentException(
                String.format("initialDelay: %d (expected: >= 0)", initialDelay)
            )
        }
        if (delay <= 0) {
            throw IllegalArgumentException(
                String.format("delay: %d (expected: > 0)", delay)
            )
        }

        validateScheduled0(initialDelay, unit)
        validateScheduled0(delay, unit)

        @Suppress("DEPRECATION")
        return schedule(
            ScheduledFutureTask<Void>(
                this, command,
                deadlineNanos(getCurrentTimeNanos(), unit.toNanos(initialDelay)),
                -unit.toNanos(delay)
            )
        )
    }

    @Suppress("DEPRECATION")
    private fun validateScheduled0(amount: Long, unit: TimeUnit) {
        validateScheduled(amount, unit)
    }

    /**
     * Sub-classes may override this to restrict the maximal amount of time someone can use to schedule a task.
     *
     * @deprecated will be removed in the future.
     */
    @Deprecated("Will be removed in the future.")
    protected open fun validateScheduled(amount: Long, unit: TimeUnit) {
        // NOOP
    }

    internal fun scheduleFromEventLoop(task: ScheduledFutureTask<*>) {
        // nextTaskId a long and so there is no chance it will overflow back to 0
        if (task.getId() == 0L) {
            task.setId(++nextTaskId)
        }
        scheduledTaskQueue().add(task)
    }

    private fun <V> schedule(task: ScheduledFutureTask<V>): ScheduledFuture<V> {
        if (inEventLoop()) {
            scheduleFromEventLoop(task)
        } else {
            val deadlineNanos = task.deadlineNanos()
            // task will add itself to scheduled task queue when run if not expired
            if (beforeScheduledTaskSubmitted(deadlineNanos)) {
                execute(task)
            } else {
                lazyExecute(task)
                // Second hook after scheduling to facilitate race-avoidance
                if (afterScheduledTaskSubmitted(deadlineNanos)) {
                    execute(WAKEUP_TASK)
                }
            }
        }

        return task
    }

    internal fun removeScheduled(task: ScheduledFutureTask<*>) {
        assert(task.isCancelled)
        if (inEventLoop()) {
            scheduledTaskQueue().removeTyped(task)
        } else {
            // task will remove itself from scheduled task queue when it runs
            scheduleRemoveScheduled(task)
        }
    }

    internal open fun scheduleRemoveScheduled(task: ScheduledFutureTask<*>) {
        // task will remove itself from scheduled task queue when it runs
        lazyExecute(task)
    }

    /**
     * Called from arbitrary non-[EventExecutor] threads prior to scheduled task submission.
     * Returns `true` if the [EventExecutor] thread should be woken immediately to
     * process the scheduled task (if not already awake).
     */
    protected open fun beforeScheduledTaskSubmitted(deadlineNanos: Long): Boolean {
        return true
    }

    /**
     * See [beforeScheduledTaskSubmitted]. Called only after that method returns false.
     */
    protected open fun afterScheduledTaskSubmitted(deadlineNanos: Long): Boolean {
        return true
    }

    companion object {
        private val SCHEDULED_FUTURE_TASK_COMPARATOR = Comparator<ScheduledFutureTask<*>> { o1, o2 ->
            o1.compareTo(o2)
        }

        @JvmField
        val WAKEUP_TASK: Runnable = Runnable { } // Do nothing

        @JvmStatic
        fun deadlineNanos(nanoTime: Long, delay: Long): Long {
            val deadlineNanos = nanoTime + delay
            // Guard against overflow
            return if (deadlineNanos < 0) Long.MAX_VALUE else deadlineNanos
        }

        /**
         * @deprecated Use the non-static [ticker] instead.
         */
        @Deprecated("Use the non-static ticker() instead.")
        @JvmStatic
        protected fun nanoTime(): Long {
            return Ticker.systemTicker().nanoTime()
        }

        /**
         * @deprecated Use the non-static [ticker] instead.
         */
        @Deprecated("Use the non-static ticker() instead.")
        @JvmStatic
        internal fun defaultCurrentTimeNanos(): Long {
            return Ticker.systemTicker().nanoTime()
        }

        /**
         * @deprecated Use [ticker] instead
         */
        @Deprecated("Use ticker() instead")
        @JvmStatic
        protected fun deadlineToDelayNanos(deadlineNanos: Long): Long {
            return ScheduledFutureTask.deadlineToDelayNanos(defaultCurrentTimeNanos(), deadlineNanos)
        }

        /**
         * @deprecated Use [ticker] instead
         */
        @Deprecated("Use ticker() instead")
        @JvmStatic
        protected fun initialNanoTime(): Long {
            return Ticker.systemTicker().initialNanoTime()
        }

        private fun isNullOrEmpty(queue: Queue<ScheduledFutureTask<*>>?): Boolean {
            return queue == null || queue.isEmpty()
        }
    }
}
