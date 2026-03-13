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

import io.netty.util.internal.ObjectUtil
import io.netty.util.internal.PlatformDependent
import io.netty.util.internal.SystemPropertyUtil
import io.netty.util.internal.ThreadExecutorMap
import io.netty.util.internal.logging.InternalLoggerFactory
import org.jetbrains.annotations.Async.Schedule

import java.util.LinkedHashSet
import java.util.Queue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater
import java.util.concurrent.atomic.AtomicLongFieldUpdater
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater
import java.util.concurrent.locks.ReentrantLock

/**
 * Abstract base class for [OrderedEventExecutor]'s that execute all its submitted tasks in a single thread.
 */
abstract class SingleThreadEventExecutor : AbstractScheduledEventExecutor, OrderedEventExecutor {

    private val taskQueue: Queue<Runnable>

    @Volatile
    @JvmField
    var thread: Thread? = null

    @Suppress("unused")
    @Volatile
    @JvmField
    var threadProperties: ThreadProperties? = null

    private val executor: Executor

    @Volatile
    private var interrupted: Boolean = false

    private val processingLock = ReentrantLock()
    private val threadLock = CountDownLatch(1)
    private val shutdownHooks = LinkedHashSet<Runnable>()
    private val addTaskWakesUp: Boolean
    private val maxPendingTasks: Int
    private val rejectedExecutionHandler: RejectedExecutionHandler
    private val supportSuspension: Boolean

    // A running total of nanoseconds this executor has spent in an "active" state.
    @Volatile
    @JvmField
    var accumulatedActiveTimeNanos: Long = 0

    // Timestamp of the last recorded activity (tasks + I/O).
    @Volatile
    private var lastActivityTimeNanos: Long = 0

    /**
     * Tracks the number of consecutive monitor cycles this executor's
     * utilization has been below the scale-down threshold.
     */
    @Volatile
    @JvmField
    var consecutiveIdleCycles: Int = 0

    /**
     * Tracks the number of consecutive monitor cycles this executor's
     * utilization has been above the scale-up threshold.
     */
    @Volatile
    @JvmField
    var consecutiveBusyCycles: Int = 0

    private var lastExecutionTime: Long = 0

    @Suppress("FieldMayBeFinal", "unused")
    @Volatile
    @JvmField
    var state: Int = ST_NOT_STARTED

    @Volatile
    private var gracefulShutdownQuietPeriod: Long = 0

    @Volatile
    private var gracefulShutdownTimeout: Long = 0

    private var gracefulShutdownStartTime: Long = 0

    private val terminationFuture: Promise<*> = DefaultPromise<Void>(GlobalEventExecutor.INSTANCE)

    /**
     * Create a new instance
     *
     * @param parent            the [EventExecutorGroup] which is the parent of this instance and belongs to it
     * @param threadFactory     the [ThreadFactory] which will be used for the used [Thread]
     * @param addTaskWakesUp    `true` if and only if invocation of [addTask] will wake up the
     *                          executor thread
     */
    protected constructor(
        parent: EventExecutorGroup?, threadFactory: ThreadFactory, addTaskWakesUp: Boolean
    ) : this(parent, ThreadPerTaskExecutor(threadFactory), addTaskWakesUp)

    /**
     * Create a new instance
     *
     * @param parent            the [EventExecutorGroup] which is the parent of this instance and belongs to it
     * @param threadFactory     the [ThreadFactory] which will be used for the used [Thread]
     * @param addTaskWakesUp    `true` if and only if invocation of [addTask] will wake up the
     *                          executor thread
     * @param maxPendingTasks   the maximum number of pending tasks before new tasks will be rejected.
     * @param rejectedHandler   the [RejectedExecutionHandler] to use.
     */
    protected constructor(
        parent: EventExecutorGroup?, threadFactory: ThreadFactory,
        addTaskWakesUp: Boolean, maxPendingTasks: Int, rejectedHandler: RejectedExecutionHandler
    ) : this(parent, ThreadPerTaskExecutor(threadFactory), addTaskWakesUp, maxPendingTasks, rejectedHandler)

    /**
     * Create a new instance
     *
     * @param parent            the [EventExecutorGroup] which is the parent of this instance and belongs to it
     * @param threadFactory     the [ThreadFactory] which will be used for the used [Thread]
     * @param addTaskWakesUp    `true` if and only if invocation of [addTask] will wake up the
     *                          executor thread
     * @param supportSuspension `true` if suspension of this [SingleThreadEventExecutor] is supported.
     * @param maxPendingTasks   the maximum number of pending tasks before new tasks will be rejected.
     * @param rejectedHandler   the [RejectedExecutionHandler] to use.
     */
    protected constructor(
        parent: EventExecutorGroup?, threadFactory: ThreadFactory,
        addTaskWakesUp: Boolean, supportSuspension: Boolean,
        maxPendingTasks: Int, rejectedHandler: RejectedExecutionHandler
    ) : this(
        parent, ThreadPerTaskExecutor(threadFactory), addTaskWakesUp, supportSuspension,
        maxPendingTasks, rejectedHandler
    )

    /**
     * Create a new instance
     *
     * @param parent            the [EventExecutorGroup] which is the parent of this instance and belongs to it
     * @param executor          the [Executor] which will be used for executing
     * @param addTaskWakesUp    `true` if and only if invocation of [addTask] will wake up the
     *                          executor thread
     */
    protected constructor(
        parent: EventExecutorGroup?, executor: Executor, addTaskWakesUp: Boolean
    ) : this(parent, executor, addTaskWakesUp, DEFAULT_MAX_PENDING_EXECUTOR_TASKS, RejectedExecutionHandlers.reject())

    /**
     * Create a new instance
     *
     * @param parent            the [EventExecutorGroup] which is the parent of this instance and belongs to it
     * @param executor          the [Executor] which will be used for executing
     * @param addTaskWakesUp    `true` if and only if invocation of [addTask] will wake up the
     *                          executor thread
     * @param maxPendingTasks   the maximum number of pending tasks before new tasks will be rejected.
     * @param rejectedHandler   the [RejectedExecutionHandler] to use.
     */
    protected constructor(
        parent: EventExecutorGroup?, executor: Executor,
        addTaskWakesUp: Boolean, maxPendingTasks: Int,
        rejectedHandler: RejectedExecutionHandler
    ) : this(parent, executor, addTaskWakesUp, false, maxPendingTasks, rejectedHandler)

    /**
     * Create a new instance
     *
     * @param parent            the [EventExecutorGroup] which is the parent of this instance and belongs to it
     * @param executor          the [Executor] which will be used for executing
     * @param addTaskWakesUp    `true` if and only if invocation of [addTask] will wake up the
     *                          executor thread
     * @param supportSuspension `true` if suspension of this [SingleThreadEventExecutor] is supported.
     * @param maxPendingTasks   the maximum number of pending tasks before new tasks will be rejected.
     * @param rejectedHandler   the [RejectedExecutionHandler] to use.
     */
    protected constructor(
        parent: EventExecutorGroup?, executor: Executor,
        addTaskWakesUp: Boolean, supportSuspension: Boolean,
        maxPendingTasks: Int, rejectedHandler: RejectedExecutionHandler
    ) : super(parent) {
        this.addTaskWakesUp = addTaskWakesUp
        this.supportSuspension = supportSuspension
        this.maxPendingTasks = Math.max(16, maxPendingTasks)
        this.executor = ThreadExecutorMap.apply(executor, this)
        taskQueue = newTaskQueue(this.maxPendingTasks)
        rejectedExecutionHandler = ObjectUtil.checkNotNull(rejectedHandler, "rejectedHandler")
        lastActivityTimeNanos = ticker().nanoTime()
    }

    protected constructor(
        parent: EventExecutorGroup?, executor: Executor,
        addTaskWakesUp: Boolean, taskQueue: Queue<Runnable>,
        rejectedHandler: RejectedExecutionHandler
    ) : this(parent, executor, addTaskWakesUp, false, taskQueue, rejectedHandler)

    protected constructor(
        parent: EventExecutorGroup?, executor: Executor,
        addTaskWakesUp: Boolean, supportSuspension: Boolean,
        taskQueue: Queue<Runnable>, rejectedHandler: RejectedExecutionHandler
    ) : super(parent) {
        this.addTaskWakesUp = addTaskWakesUp
        this.supportSuspension = supportSuspension
        this.maxPendingTasks = DEFAULT_MAX_PENDING_EXECUTOR_TASKS
        this.executor = ThreadExecutorMap.apply(executor, this)
        this.taskQueue = ObjectUtil.checkNotNull(taskQueue, "taskQueue")
        this.rejectedExecutionHandler = ObjectUtil.checkNotNull(rejectedHandler, "rejectedHandler")
    }

    /**
     * @deprecated Please use and override [newTaskQueue(Int)].
     */
    @Deprecated("Please use and override newTaskQueue(int)")
    protected open fun newTaskQueue(): Queue<Runnable> {
        return newTaskQueue(maxPendingTasks)
    }

    /**
     * Create a new [Queue] which will holds the tasks to execute. This default implementation will return a
     * [LinkedBlockingQueue] but if your sub-class of [SingleThreadEventExecutor] will not do any blocking
     * calls on the this [Queue] it may make sense to override this and return some more performant
     * implementation that does not support blocking operations at all.
     */
    protected open fun newTaskQueue(maxPendingTasks: Int): Queue<Runnable> {
        return LinkedBlockingQueue<Runnable>(maxPendingTasks)
    }

    /**
     * Interrupt the current running [Thread].
     */
    protected fun interruptThread() {
        val currentThread = thread
        if (currentThread == null) {
            interrupted = true
        } else {
            currentThread.interrupt()
        }
    }

    /**
     * @see Queue.poll
     */
    protected fun pollTask(): Runnable? {
        assert(inEventLoop())
        return pollTaskFrom(taskQueue)
    }

    /**
     * Take the next [Runnable] from the task queue and so will block if no task is currently present.
     *
     * Be aware that this method will throw an [UnsupportedOperationException] if the task queue, which was
     * created via [newTaskQueue], does not implement [BlockingQueue].
     *
     * @return `null` if the executor thread has been interrupted or waken up.
     */
    protected fun takeTask(): Runnable? {
        assert(inEventLoop())
        if (taskQueue !is BlockingQueue<*>) {
            throw UnsupportedOperationException()
        }

        @Suppress("UNCHECKED_CAST")
        val taskQueue = this.taskQueue as BlockingQueue<Runnable>
        while (true) {
            val scheduledTask = peekScheduledTask()
            if (scheduledTask == null) {
                var task: Runnable? = null
                try {
                    task = taskQueue.take()
                    if (task === WAKEUP_TASK) {
                        task = null
                    }
                } catch (e: InterruptedException) {
                    // Ignore
                }
                return task
            } else {
                val delayNanos = scheduledTask.delayNanos()
                var task: Runnable? = null
                if (delayNanos > 0) {
                    try {
                        task = taskQueue.poll(delayNanos, TimeUnit.NANOSECONDS)
                    } catch (e: InterruptedException) {
                        // Waken up.
                        return null
                    }
                }
                if (task == null) {
                    // We need to fetch the scheduled tasks now as otherwise there may be a chance that
                    // scheduled tasks are never executed if there is always one task in the taskQueue.
                    // This is for example true for the read task of OIO Transport
                    // See https://github.com/netty/netty/issues/1614
                    fetchFromScheduledTaskQueue()
                    task = taskQueue.poll()
                }

                if (task != null) {
                    if (task === WAKEUP_TASK) {
                        return null
                    }
                    return task
                }
            }
        }
    }

    private fun fetchFromScheduledTaskQueue(): Boolean {
        return fetchFromScheduledTaskQueue(taskQueue)
    }

    /**
     * @return `true` if at least one scheduled task was executed.
     */
    private fun executeExpiredScheduledTasks(): Boolean {
        val queue = scheduledTaskQueue ?: return false
        if (queue.isEmpty()) {
            return false
        }
        val nanoTime = ticker().nanoTime()
        var scheduledTask: Runnable? = pollScheduledTask(nanoTime) ?: return false
        do {
            safeExecute(scheduledTask!!)
            scheduledTask = pollScheduledTask(nanoTime)
        } while (scheduledTask != null)
        return true
    }

    /**
     * @see Queue.peek
     */
    protected fun peekTask(): Runnable? {
        assert(inEventLoop())
        return taskQueue.peek()
    }

    /**
     * @see Queue.isEmpty
     */
    protected open fun hasTasks(): Boolean {
        assert(inEventLoop())
        return !taskQueue.isEmpty()
    }

    /**
     * Return the number of tasks that are pending for processing.
     */
    open fun pendingTasks(): Int {
        return taskQueue.size
    }

    /**
     * Add a task to the task queue, or throws a [RejectedExecutionException] if this instance was shutdown
     * before.
     */
    protected fun addTask(task: Runnable) {
        ObjectUtil.checkNotNull(task, "task")
        if (!offerTask(task)) {
            reject(task)
        }
    }

    internal fun offerTask(task: Runnable): Boolean {
        if (isShutdown) {
            reject()
        }
        return taskQueue.offer(task)
    }

    /**
     * @see Queue.remove
     */
    protected fun removeTask(task: Runnable): Boolean {
        return taskQueue.remove(ObjectUtil.checkNotNull(task, "task"))
    }

    /**
     * Poll all tasks from the task queue and run them via [Runnable.run] method.
     *
     * @return `true` if and only if at least one task was run
     */
    protected fun runAllTasks(): Boolean {
        assert(inEventLoop())
        var fetchedAll: Boolean
        var ranAtLeastOne = false

        do {
            fetchedAll = fetchFromScheduledTaskQueue(taskQueue)
            if (runAllTasksFrom(taskQueue)) {
                ranAtLeastOne = true
            }
        } while (!fetchedAll) // keep on processing until we fetched all scheduled tasks.

        if (ranAtLeastOne) {
            lastExecutionTime = ticker().nanoTime()
        }
        afterRunningAllTasks()
        return ranAtLeastOne
    }

    /**
     * Execute all expired scheduled tasks and all current tasks in the executor queue until both queues are empty,
     * or [maxDrainAttempts] has been exceeded.
     * @param maxDrainAttempts The maximum amount of times this method attempts to drain from queues. This is to prevent
     *                         continuous task execution and scheduling from preventing the EventExecutor thread to
     *                         make progress and return to the selector mechanism to process inbound I/O events.
     * @return `true` if at least one task was run.
     */
    protected fun runScheduledAndExecutorTasks(maxDrainAttempts: Int): Boolean {
        assert(inEventLoop())
        var ranAtLeastOneTask: Boolean
        var drainAttempt = 0
        do {
            // We must run the taskQueue tasks first, because the scheduled tasks from outside the EventLoop are queued
            // here because the taskQueue is thread safe and the scheduledTaskQueue is not thread safe.
            ranAtLeastOneTask = runExistingTasksFrom(taskQueue) or executeExpiredScheduledTasks()
        } while (ranAtLeastOneTask && ++drainAttempt < maxDrainAttempts)

        if (drainAttempt > 0) {
            lastExecutionTime = ticker().nanoTime()
        }
        afterRunningAllTasks()

        return drainAttempt > 0
    }

    /**
     * Runs all tasks from the passed [taskQueue].
     *
     * @param taskQueue To poll and execute all tasks.
     *
     * @return `true` if at least one task was executed.
     */
    protected fun runAllTasksFrom(taskQueue: Queue<Runnable>): Boolean {
        var task: Runnable? = pollTaskFrom(taskQueue) ?: return false
        while (true) {
            safeExecute(task!!)
            task = pollTaskFrom(taskQueue)
            if (task == null) {
                return true
            }
        }
    }

    /**
     * What ever tasks are present in [taskQueue] when this method is invoked will be [Runnable.run].
     * @param taskQueue the task queue to drain.
     * @return `true` if at least [Runnable.run] was called.
     */
    private fun runExistingTasksFrom(taskQueue: Queue<Runnable>): Boolean {
        var task: Runnable? = pollTaskFrom(taskQueue) ?: return false
        var remaining = Math.min(maxPendingTasks, taskQueue.size)
        safeExecute(task!!)
        // Use taskQueue.poll() directly rather than pollTaskFrom() since the latter may
        // silently consume more than one item from the queue (skips over WAKEUP_TASK instances)
        while (remaining-- > 0) {
            task = taskQueue.poll()
            if (task == null) break
            safeExecute(task)
        }
        return true
    }

    /**
     * Poll all tasks from the task queue and run them via [Runnable.run] method. This method stops running
     * the tasks in the task queue and returns if it ran longer than [timeoutNanos].
     */
    @Suppress("NonAtomicOperationOnVolatileField")
    protected fun runAllTasks(timeoutNanos: Long): Boolean {
        fetchFromScheduledTaskQueue(taskQueue)
        var task: Runnable? = pollTask()
        if (task == null) {
            afterRunningAllTasks()
            return false
        }

        val deadline = if (timeoutNanos > 0) ticker().nanoTime() + timeoutNanos else 0
        var runTasks: Long = 0
        var lastExecutionTime: Long

        val workStartTime = ticker().nanoTime()
        while (true) {
            safeExecute(task!!)

            runTasks++

            // Check timeout every 64 tasks because nanoTime() is relatively expensive.
            // XXX: Hard-coded value - will make it configurable if it is really a problem.
            if ((runTasks and 0x3F) == 0L) {
                lastExecutionTime = ticker().nanoTime()
                if (lastExecutionTime >= deadline) {
                    break
                }
            }

            task = pollTask()
            if (task == null) {
                lastExecutionTime = ticker().nanoTime()
                break
            }
        }

        val workEndTime = ticker().nanoTime()
        accumulatedActiveTimeNanos += workEndTime - workStartTime
        lastActivityTimeNanos = workEndTime

        afterRunningAllTasks()
        this.lastExecutionTime = lastExecutionTime
        return true
    }

    /**
     * Invoked before returning from [runAllTasks] and [runAllTasks].
     */
    protected open fun afterRunningAllTasks() {}

    /**
     * Returns the amount of time left until the scheduled task with the closest dead line is executed.
     */
    protected fun delayNanos(currentTimeNanos: Long): Long {
        @Suppress("NAME_SHADOWING")
        var currentTimeNanos = currentTimeNanos
        currentTimeNanos -= ticker().initialNanoTime()

        val scheduledTask = peekScheduledTask()
        if (scheduledTask == null) {
            return SCHEDULE_PURGE_INTERVAL
        }

        return scheduledTask.delayNanos(currentTimeNanos)
    }

    /**
     * Returns the absolute point in time (relative to [getCurrentTimeNanos]) at which the next
     * closest scheduled task should run.
     */
    protected fun deadlineNanos(): Long {
        val scheduledTask = peekScheduledTask()
        if (scheduledTask == null) {
            return ticker().nanoTime() + SCHEDULE_PURGE_INTERVAL
        }
        return scheduledTask.deadlineNanos()
    }

    /**
     * Updates the internal timestamp that tells when a submitted task was executed most recently.
     * [runAllTasks] and [runAllTasks] updates this timestamp automatically, and thus there's
     * usually no need to call this method. However, if you take the tasks manually using [takeTask] or
     * [pollTask], you have to call this method at the end of task execution loop for accurate quiet period
     * checks.
     */
    protected fun updateLastExecutionTime() {
        val now = ticker().nanoTime()
        lastExecutionTime = now
        lastActivityTimeNanos = now
    }

    /**
     * Returns the number of registered channels for auto-scaling related decisions.
     * This is intended to be used by [MultithreadEventExecutorGroup] for dynamic scaling.
     *
     * @return The number of registered channels, or `-1` if not applicable.
     */
    protected open fun getNumOfRegisteredChannels(): Int {
        return -1
    }

    /**
     * Internal accessor for [getNumOfRegisteredChannels] for same-package callers.
     */
    internal fun numOfRegisteredChannelsInternal(): Int {
        return getNumOfRegisteredChannels()
    }

    /**
     * Adds the given duration to the total active time for the current measurement window.
     *
     * **Note:** This method is not thread-safe and must only be called from the
     * [event loop thread][inEventLoop].
     *
     * @param nanos The active time in nanoseconds to add.
     */
    @Suppress("NonAtomicOperationOnVolatileField")
    protected fun reportActiveIoTime(nanos: Long) {
        assert(inEventLoop())
        if (nanos > 0) {
            accumulatedActiveTimeNanos += nanos
            lastActivityTimeNanos = ticker().nanoTime()
        }
    }

    /**
     * Returns the accumulated active time since the last call and resets the counter.
     */
    internal fun getAndResetAccumulatedActiveTimeNanos(): Long {
        return ACCUMULATED_ACTIVE_TIME_NANOS_UPDATER.getAndSet(this, 0)
    }

    /**
     * Returns the timestamp of the last known activity (tasks + I/O).
     */
    internal fun getLastActivityTimeNanos(): Long {
        return lastActivityTimeNanos
    }

    /**
     * Atomically increments the counter for consecutive monitor cycles where utilization was below the
     * scale-down threshold. This is used by the auto-scaling monitor to track sustained idleness.
     *
     * @return The number of consecutive idle cycles before the increment.
     */
    internal fun getAndIncrementIdleCycles(): Int {
        return CONSECUTIVE_IDLE_CYCLES_UPDATER.getAndIncrement(this)
    }

    /**
     * Resets the counter for consecutive idle cycles to zero. This is typically called when the
     * executor's utilization is no longer considered idle, breaking the streak.
     */
    internal fun resetIdleCycles() {
        CONSECUTIVE_IDLE_CYCLES_UPDATER.set(this, 0)
    }

    /**
     * Atomically increments the counter for consecutive monitor cycles where utilization was above the
     * scale-up threshold. This is used by the auto-scaling monitor to track a sustained high load.
     *
     * @return The number of consecutive busy cycles before the increment.
     */
    internal fun getAndIncrementBusyCycles(): Int {
        return CONSECUTIVE_BUSY_CYCLES_UPDATER.getAndIncrement(this)
    }

    /**
     * Resets the counter for consecutive busy cycles to zero. This is typically called when the
     * executor's utilization is no longer considered busy, breaking the streak.
     */
    internal fun resetBusyCycles() {
        CONSECUTIVE_BUSY_CYCLES_UPDATER.set(this, 0)
    }

    /**
     * Returns `true` if this [SingleThreadEventExecutor] supports suspension.
     */
    protected open fun isSuspensionSupported(): Boolean {
        return supportSuspension
    }

    /**
     * Run the tasks in the [taskQueue]
     */
    protected abstract fun run()

    /**
     * Do nothing, sub-classes may override
     */
    protected open fun cleanup() {
        // NOOP
    }

    protected open fun wakeup(inEventLoop: Boolean) {
        if (!inEventLoop) {
            // Use offer as we actually only need this to unblock the thread and if offer fails we do not care as there
            // is already something in the queue.
            taskQueue.offer(WAKEUP_TASK)
        }
    }

    internal fun invokeWakeup(inEventLoop: Boolean) {
        wakeup(inEventLoop)
    }

    override fun inEventLoop(thread: Thread): Boolean {
        return thread === this.thread
    }

    /**
     * Add a [Runnable] which will be executed on shutdown of this instance
     */
    fun addShutdownHook(task: Runnable) {
        if (inEventLoop()) {
            shutdownHooks.add(task)
        } else {
            execute(Runnable { shutdownHooks.add(task) })
        }
    }

    /**
     * Remove a previous added [Runnable] as a shutdown hook
     */
    fun removeShutdownHook(task: Runnable) {
        if (inEventLoop()) {
            shutdownHooks.remove(task)
        } else {
            execute(Runnable { shutdownHooks.remove(task) })
        }
    }

    private fun runShutdownHooks(): Boolean {
        var ran = false
        // Note shutdown hooks can add / remove shutdown hooks.
        while (shutdownHooks.isNotEmpty()) {
            val copy = ArrayList<Runnable>(shutdownHooks)
            shutdownHooks.clear()
            for (task in copy) {
                try {
                    runTask(task)
                } catch (t: Throwable) {
                    logger.warn("Shutdown hook raised an exception.", t)
                } finally {
                    ran = true
                }
            }
        }

        if (ran) {
            lastExecutionTime = ticker().nanoTime()
        }

        return ran
    }

    private fun shutdown0(quietPeriod: Long, timeout: Long, shutdownState: Int) {
        if (isShuttingDown()) {
            return
        }

        val inEventLoop = inEventLoop()
        var wakeup: Boolean
        var oldState: Int
        while (true) {
            if (isShuttingDown()) {
                return
            }
            val newState: Int
            wakeup = true
            oldState = state
            if (inEventLoop) {
                newState = shutdownState
            } else {
                when (oldState) {
                    ST_NOT_STARTED, ST_STARTED, ST_SUSPENDING, ST_SUSPENDED -> newState = shutdownState
                    else -> {
                        newState = oldState
                        wakeup = false
                    }
                }
            }
            if (STATE_UPDATER.compareAndSet(this, oldState, newState)) {
                break
            }
        }
        if (quietPeriod != -1L) {
            gracefulShutdownQuietPeriod = quietPeriod
        }
        if (timeout != -1L) {
            gracefulShutdownTimeout = timeout
        }

        if (ensureThreadStarted(oldState)) {
            return
        }

        if (wakeup) {
            taskQueue.offer(WAKEUP_TASK)
            if (!addTaskWakesUp) {
                wakeup(inEventLoop)
            }
        }
    }

    override fun shutdownGracefully(quietPeriod: Long, timeout: Long, unit: TimeUnit): Future<*> {
        ObjectUtil.checkPositiveOrZero(quietPeriod, "quietPeriod")
        if (timeout < quietPeriod) {
            throw IllegalArgumentException(
                "timeout: $timeout (expected >= quietPeriod ($quietPeriod))"
            )
        }
        ObjectUtil.checkNotNull(unit, "unit")

        shutdown0(unit.toNanos(quietPeriod), unit.toNanos(timeout), ST_SHUTTING_DOWN)
        return terminationFuture()
    }

    override fun terminationFuture(): Future<*> {
        return terminationFuture
    }

    @Deprecated("Use shutdownGracefully instead")
    override fun shutdown() {
        shutdown0(-1, -1, ST_SHUTDOWN)
    }

    override fun isShuttingDown(): Boolean {
        return state >= ST_SHUTTING_DOWN
    }

    override fun isShutdown(): Boolean {
        return state >= ST_SHUTDOWN
    }

    override fun isTerminated(): Boolean {
        return state == ST_TERMINATED
    }

    override fun isSuspended(): Boolean {
        val currentState = state
        return currentState == ST_SUSPENDED || currentState == ST_SUSPENDING
    }

    override fun trySuspend(): Boolean {
        if (supportSuspension) {
            if (STATE_UPDATER.compareAndSet(this, ST_STARTED, ST_SUSPENDING)) {
                wakeup(inEventLoop())
                return true
            } else if (STATE_UPDATER.compareAndSet(this, ST_NOT_STARTED, ST_SUSPENDED)) {
                return true
            }
            val currentState = state
            return currentState == ST_SUSPENDED || currentState == ST_SUSPENDING
        }
        return false
    }

    /**
     * Returns `true` if this [SingleThreadEventExecutor] can be suspended at the moment, `false`
     * otherwise.
     *
     * @return if suspension is possible at the moment.
     */
    protected open fun canSuspend(): Boolean {
        return canSuspend(state)
    }

    /**
     * Returns `true` if this [SingleThreadEventExecutor] can be suspended at the moment, `false`
     * otherwise.
     *
     * Subclasses might override this method to add extra checks.
     *
     * @param state the current internal state of the [SingleThreadEventExecutor].
     * @return if suspension is possible at the moment.
     */
    protected open fun canSuspend(state: Int): Boolean {
        assert(inEventLoop())
        return supportSuspension && (state == ST_SUSPENDED || state == ST_SUSPENDING) &&
                !hasTasks() && nextScheduledTaskDeadlineNanos() == -1L
    }

    /**
     * Confirm that the shutdown if the instance should be done now!
     */
    protected open fun confirmShutdown(): Boolean {
        if (!isShuttingDown()) {
            return false
        }

        if (!inEventLoop()) {
            throw IllegalStateException("must be invoked from an event loop")
        }

        cancelScheduledTasks()

        if (gracefulShutdownStartTime == 0L) {
            gracefulShutdownStartTime = ticker().nanoTime()
        }

        if (runAllTasks() || runShutdownHooks()) {
            if (isShutdown) {
                // Executor shut down - no new tasks anymore.
                return true
            }

            // There were tasks in the queue. Wait a little bit more until no tasks are queued for the quiet period or
            // terminate if the quiet period is 0.
            // See https://github.com/netty/netty/issues/4241
            if (gracefulShutdownQuietPeriod == 0L) {
                return true
            }
            taskQueue.offer(WAKEUP_TASK)
            return false
        }

        val nanoTime = ticker().nanoTime()

        if (isShutdown || nanoTime - gracefulShutdownStartTime > gracefulShutdownTimeout) {
            return true
        }

        if (nanoTime - lastExecutionTime <= gracefulShutdownQuietPeriod) {
            // Check if any tasks were added to the queue every 100ms.
            // TODO: Change the behavior of takeTask() so that it returns on timeout.
            taskQueue.offer(WAKEUP_TASK)
            try {
                Thread.sleep(100)
            } catch (e: InterruptedException) {
                // Ignore
            }

            return false
        }

        // No tasks were added for last quiet period - hopefully safe to shut down.
        // (Hopefully because we really cannot make a guarantee that there will be no execute() calls by a user.)
        return true
    }

    @Throws(InterruptedException::class)
    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
        ObjectUtil.checkNotNull(unit, "unit")
        if (inEventLoop()) {
            throw IllegalStateException("cannot await termination of the current thread")
        }

        threadLock.await(timeout, unit)

        return isTerminated
    }

    override fun execute(task: Runnable) {
        execute0(task)
    }

    override fun lazyExecute(task: Runnable) {
        lazyExecute0(task)
    }

    private fun execute0(@Schedule task: Runnable) {
        ObjectUtil.checkNotNull(task, "task")
        execute(task, wakesUpForTask(task))
    }

    private fun lazyExecute0(@Schedule task: Runnable) {
        execute(ObjectUtil.checkNotNull(task, "task"), false)
    }

    override fun scheduleRemoveScheduled(task: ScheduledFutureTask<*>) {
        ObjectUtil.checkNotNull(task, "task")
        val currentState = state
        if (supportSuspension && currentState == ST_SUSPENDED) {
            // In the case of scheduling for removal we need to also ensure we will recover the "suspend" state
            // after it if it was set before. Otherwise we will always end up "unsuspending" things on cancellation
            // which is not optimal.
            execute(Runnable {
                task.run()
                if (canSuspend(ST_SUSPENDED)) {
                    // Try suspending again to recover the state before we submitted the new task that will
                    // handle cancellation itself.
                    trySuspend()
                }
            }, true)
        } else {
            // task will remove itself from scheduled task queue when it runs
            execute(task, false)
        }
    }

    private fun execute(task: Runnable, immediate: Boolean) {
        val inEventLoop = inEventLoop()
        addTask(task)
        if (!inEventLoop) {
            startThread()
            if (isShutdown) {
                var reject = false
                try {
                    if (removeTask(task)) {
                        reject = true
                    }
                } catch (e: UnsupportedOperationException) {
                    // The task queue does not support removal so the best thing we can do is to just move on and
                    // hope we will be able to pick-up the task before its completely terminated.
                    // In worst case we will log on termination.
                }
                if (reject) {
                    reject()
                }
            }
        }

        if (!addTaskWakesUp && immediate) {
            wakeup(inEventLoop)
        }
    }

    @Throws(InterruptedException::class, ExecutionException::class)
    override fun <T> invokeAny(tasks: Collection<out Callable<T>>): T {
        throwIfInEventLoop("invokeAny")
        return super.invokeAny(tasks)
    }

    @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
    override fun <T> invokeAny(tasks: Collection<out Callable<T>>, timeout: Long, unit: TimeUnit): T {
        throwIfInEventLoop("invokeAny")
        return super.invokeAny(tasks, timeout, unit)
    }

    @Throws(InterruptedException::class)
    override fun <T> invokeAll(tasks: Collection<out Callable<T>>): List<java.util.concurrent.Future<T>> {
        throwIfInEventLoop("invokeAll")
        return super.invokeAll(tasks)
    }

    @Throws(InterruptedException::class)
    override fun <T> invokeAll(
        tasks: Collection<out Callable<T>>, timeout: Long, unit: TimeUnit
    ): List<java.util.concurrent.Future<T>> {
        throwIfInEventLoop("invokeAll")
        return super.invokeAll(tasks, timeout, unit)
    }

    private fun throwIfInEventLoop(method: String) {
        if (inEventLoop()) {
            throw RejectedExecutionException("Calling $method from within the EventLoop is not allowed")
        }
    }

    /**
     * Returns the [ThreadProperties] of the [Thread] that powers the [SingleThreadEventExecutor].
     * If the [SingleThreadEventExecutor] is not started yet, this operation will start it and block until
     * it is fully started.
     */
    fun threadProperties(): ThreadProperties {
        var threadProperties = this.threadProperties
        if (threadProperties == null) {
            var thread = this.thread
            if (thread == null) {
                assert(!inEventLoop())
                submit(NOOP_TASK).syncUninterruptibly()
                thread = this.thread
                assert(thread != null)
            }

            threadProperties = DefaultThreadProperties(thread!!)
            if (!PROPERTIES_UPDATER.compareAndSet(this, null, threadProperties)) {
                threadProperties = this.threadProperties
            }
        }

        return threadProperties!!
    }

    /**
     * @deprecated override [SingleThreadEventExecutor.wakesUpForTask] to re-create this behaviour
     */
    @Deprecated("override wakesUpForTask to re-create this behaviour")
    protected interface NonWakeupRunnable : LazyRunnable

    /**
     * Can be overridden to control which tasks require waking the [EventExecutor] thread
     * if it is waiting so that they can be run immediately.
     */
    protected open fun wakesUpForTask(task: Runnable): Boolean {
        return true
    }

    /**
     * Offers the task to the associated [RejectedExecutionHandler].
     *
     * @param task to reject.
     */
    protected fun reject(task: Runnable) {
        rejectedExecutionHandler.rejected(task, this)
    }

    // ScheduledExecutorService implementation

    private fun startThread() {
        val currentState = state
        if (currentState == ST_NOT_STARTED || currentState == ST_SUSPENDED) {
            if (STATE_UPDATER.compareAndSet(this, currentState, ST_STARTED)) {
                resetIdleCycles()
                resetBusyCycles()
                var success = false
                try {
                    doStartThread()
                    success = true
                } finally {
                    if (!success) {
                        STATE_UPDATER.compareAndSet(this, ST_STARTED, ST_NOT_STARTED)
                    }
                }
            }
        }
    }

    private fun ensureThreadStarted(oldState: Int): Boolean {
        if (oldState == ST_NOT_STARTED || oldState == ST_SUSPENDED) {
            try {
                doStartThread()
            } catch (cause: Throwable) {
                STATE_UPDATER.set(this, ST_TERMINATED)
                terminationFuture.tryFailure(cause)

                if (cause !is Exception) {
                    // Also rethrow as it may be an OOME for example
                    PlatformDependent.throwException(cause)
                }
                return true
            }
        }
        return false
    }

    private fun doStartThread() {
        executor.execute {
            processingLock.lock()
            assert(thread == null)
            thread = Thread.currentThread()
            if (interrupted) {
                thread!!.interrupt()
                interrupted = false
            }
            var success = false
            var unexpectedException: Throwable? = null
            updateLastExecutionTime()
            var suspend = false
            try {
                while (true) {
                    this@SingleThreadEventExecutor.run()
                    success = true

                    val currentState = state
                    if (canSuspend(currentState)) {
                        if (!STATE_UPDATER.compareAndSet(
                                this@SingleThreadEventExecutor,
                                ST_SUSPENDING, ST_SUSPENDED
                            )
                        ) {
                            // Try again as the CAS failed.
                            continue
                        }

                        if (!canSuspend(ST_SUSPENDED) && STATE_UPDATER.compareAndSet(
                                this@SingleThreadEventExecutor,
                                ST_SUSPENDED, ST_STARTED
                            )
                        ) {
                            // Seems like there was something added to the task queue again in the meantime but we
                            // were able to re-engage this thread as the event loop thread.
                            continue
                        }
                        suspend = true
                    }
                    break
                }
            } catch (t: Throwable) {
                unexpectedException = t
                logger.warn("Unexpected exception from an event executor: ", t)
            } finally {
                val shutdown = !suspend
                if (shutdown) {
                    while (true) {
                        // We are re-fetching the state as it might have been shutdown in the meantime.
                        val oldState = state
                        if (oldState >= ST_SHUTTING_DOWN || STATE_UPDATER.compareAndSet(
                                this@SingleThreadEventExecutor, oldState, ST_SHUTTING_DOWN
                            )
                        ) {
                            break
                        }
                    }
                    if (success && gracefulShutdownStartTime == 0L) {
                        // Check if confirmShutdown() was called at the end of the loop.
                        if (logger.isErrorEnabled()) {
                            logger.error(
                                "Buggy " + EventExecutor::class.java.simpleName + " implementation; " +
                                        SingleThreadEventExecutor::class.java.simpleName + ".confirmShutdown() must " +
                                        "be called before run() implementation terminates."
                            )
                        }
                    }
                }

                try {
                    if (shutdown) {
                        // Run all remaining tasks and shutdown hooks. At this point the event loop
                        // is in ST_SHUTTING_DOWN state still accepting tasks which is needed for
                        // graceful shutdown with quietPeriod.
                        while (true) {
                            if (confirmShutdown()) {
                                break
                            }
                        }

                        // Now we want to make sure no more tasks can be added from this point. This is
                        // achieved by switching the state. Any new tasks beyond this point will be rejected.
                        while (true) {
                            val currentState = state
                            if (currentState >= ST_SHUTDOWN || STATE_UPDATER.compareAndSet(
                                    this@SingleThreadEventExecutor, currentState, ST_SHUTDOWN
                                )
                            ) {
                                break
                            }
                        }

                        // We have the final set of tasks in the queue now, no more can be added, run all remaining.
                        // No need to loop here, this is the final pass.
                        confirmShutdown()
                    }
                } finally {
                    try {
                        if (shutdown) {
                            try {
                                cleanup()
                            } finally {
                                // Lets remove all FastThreadLocals for the Thread as we are about to terminate and
                                // notify the future. The user may block on the future and once it unblocks the JVM
                                // may terminate and start unloading classes.
                                // See https://github.com/netty/netty/issues/6596.
                                FastThreadLocal.removeAll()

                                STATE_UPDATER.set(this@SingleThreadEventExecutor, ST_TERMINATED)
                                threadLock.countDown()
                                val numUserTasks = drainTasks()
                                if (numUserTasks > 0 && logger.isWarnEnabled()) {
                                    logger.warn(
                                        "An event executor terminated with " +
                                                "non-empty task queue ($numUserTasks)"
                                    )
                                }
                                if (unexpectedException == null) {
                                    @Suppress("UNCHECKED_CAST")
                                    (terminationFuture as Promise<Void?>).setSuccess(null)
                                } else {
                                    terminationFuture.setFailure(unexpectedException)
                                }
                            }
                        } else {
                            // Lets remove all FastThreadLocals for the Thread as we are about to terminate it.
                            FastThreadLocal.removeAll()

                            // Reset the stored threadProperties in case of suspension.
                            threadProperties = null
                        }
                    } finally {
                        thread = null
                        // Let the next thread take over if needed.
                        processingLock.unlock()
                    }
                }
            }
        }
    }

    fun drainTasks(): Int {
        var numTasks = 0
        while (true) {
            val runnable = taskQueue.poll() ?: break
            // WAKEUP_TASK should be just discarded as these are added internally.
            // The important bit is that we not have any user tasks left.
            if (WAKEUP_TASK !== runnable) {
                numTasks++
            }
        }
        return numTasks
    }

    private class DefaultThreadProperties(private val t: Thread) : ThreadProperties {
        override fun state(): Thread.State {
            return t.state
        }

        override fun priority(): Int {
            return t.priority
        }

        override fun isInterrupted(): Boolean {
            return t.isInterrupted
        }

        override fun isDaemon(): Boolean {
            return t.isDaemon
        }

        override fun name(): String {
            return t.name
        }

        override fun id(): Long {
            return t.id
        }

        override fun stackTrace(): Array<StackTraceElement> {
            return t.stackTrace
        }

        override fun isAlive(): Boolean {
            return t.isAlive
        }
    }

    companion object {
        @JvmField
        val DEFAULT_MAX_PENDING_EXECUTOR_TASKS: Int = Math.max(
            16,
            SystemPropertyUtil.getInt("io.netty.eventexecutor.maxPendingTasks", Integer.MAX_VALUE)
        )

        private val logger = InternalLoggerFactory.getInstance(SingleThreadEventExecutor::class.java)

        private const val ST_NOT_STARTED = 1
        private const val ST_SUSPENDING = 2
        private const val ST_SUSPENDED = 3
        private const val ST_STARTED = 4
        private const val ST_SHUTTING_DOWN = 5
        private const val ST_SHUTDOWN = 6
        private const val ST_TERMINATED = 7

        private val NOOP_TASK = Runnable { /* Do nothing. */ }

        private val STATE_UPDATER: AtomicIntegerFieldUpdater<SingleThreadEventExecutor> =
            AtomicIntegerFieldUpdater.newUpdater(SingleThreadEventExecutor::class.java, "state")

        private val PROPERTIES_UPDATER: AtomicReferenceFieldUpdater<SingleThreadEventExecutor, ThreadProperties> =
            AtomicReferenceFieldUpdater.newUpdater(
                SingleThreadEventExecutor::class.java, ThreadProperties::class.java, "threadProperties"
            )

        private val ACCUMULATED_ACTIVE_TIME_NANOS_UPDATER: AtomicLongFieldUpdater<SingleThreadEventExecutor> =
            AtomicLongFieldUpdater.newUpdater(SingleThreadEventExecutor::class.java, "accumulatedActiveTimeNanos")

        private val CONSECUTIVE_IDLE_CYCLES_UPDATER: AtomicIntegerFieldUpdater<SingleThreadEventExecutor> =
            AtomicIntegerFieldUpdater.newUpdater(SingleThreadEventExecutor::class.java, "consecutiveIdleCycles")

        private val CONSECUTIVE_BUSY_CYCLES_UPDATER: AtomicIntegerFieldUpdater<SingleThreadEventExecutor> =
            AtomicIntegerFieldUpdater.newUpdater(SingleThreadEventExecutor::class.java, "consecutiveBusyCycles")

        private val SCHEDULE_PURGE_INTERVAL = TimeUnit.SECONDS.toNanos(1)

        @JvmStatic
        protected fun pollTaskFrom(taskQueue: Queue<Runnable>): Runnable? {
            while (true) {
                val task = taskQueue.poll()
                if (task !== WAKEUP_TASK) {
                    return task
                }
            }
        }

        @JvmStatic
        protected fun reject() {
            throw RejectedExecutionException("event executor terminated")
        }
    }
}
