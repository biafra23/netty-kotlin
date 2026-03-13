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
import io.netty.util.internal.SystemPropertyUtil
import io.netty.util.internal.ThreadExecutorMap
import io.netty.util.internal.ThrowableUtil
import io.netty.util.internal.logging.InternalLoggerFactory
import org.jetbrains.annotations.Async.Schedule
import java.security.AccessController
import java.security.PrivilegedAction
import java.util.Queue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single-thread singleton [EventExecutor].  It starts the thread automatically and stops it when there is no
 * task pending in the task queue for `io.netty.globalEventExecutor.quietPeriodSeconds` second
 * (default is 1 second).  Please note it is not scalable to schedule large number of tasks to this executor;
 * use a dedicated executor.
 */
class GlobalEventExecutor private constructor() : AbstractScheduledEventExecutor(), OrderedEventExecutor {

    internal val taskQueue: BlockingQueue<Runnable> = LinkedBlockingQueue()
    internal val quietPeriodTask: ScheduledFutureTask<Void?> = ScheduledFutureTask(
        this, Executors.callable(Runnable {
            // NOOP
        }, null as Void?),
        // note: the getCurrentTimeNanos() call here only works because this is a final class, otherwise the method
        // could be overridden leading to unsafe initialization here!
        deadlineNanos(getCurrentTimeNanos(), SCHEDULE_QUIET_PERIOD_INTERVAL),
        -SCHEDULE_QUIET_PERIOD_INTERVAL
    )

    // because the GlobalEventExecutor is a singleton, tasks submitted to it can come from arbitrary threads and this
    // can trigger the creation of a thread from arbitrary thread groups; for this reason, the thread factory must not
    // be sticky about its thread group
    // visible for testing
    @JvmField
    val threadFactory: ThreadFactory
    private val taskRunner = TaskRunner()
    private val started = AtomicBoolean()
    @Volatile
    @JvmField
    var thread: Thread? = null

    private val terminationFuture: Future<*>

    init {
        scheduleFromEventLoop(quietPeriodTask)
        threadFactory = ThreadExecutorMap.apply(
            DefaultThreadFactory(
                DefaultThreadFactory.toPoolName(javaClass), false, Thread.NORM_PRIORITY, null
            ), this
        )

        val terminationFailure = UnsupportedOperationException()
        ThrowableUtil.unknownStackTrace(terminationFailure, GlobalEventExecutor::class.java, "terminationFuture")
        terminationFuture = FailedFuture<Any>(this, terminationFailure)
    }

    /**
     * Take the next [Runnable] from the task queue and so will block if no task is currently present.
     *
     * @return `null` if the executor thread has been interrupted or waken up.
     */
    internal fun takeTask(): Runnable? {
        val taskQueue = this.taskQueue
        while (true) {
            val scheduledTask = peekScheduledTask()
            if (scheduledTask == null) {
                var task: Runnable? = null
                try {
                    task = taskQueue.take()
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
                    return task
                }
            }
        }
    }

    private fun fetchFromScheduledTaskQueue() {
        val nanoTime = getCurrentTimeNanos()
        var scheduledTask: ScheduledFutureTask<*>?
        while (true) {
            scheduledTask = pollScheduledTask(nanoTime) as ScheduledFutureTask<*>?
            if (scheduledTask == null) break
            if (scheduledTask.isCancelled) {
                continue
            }
            taskQueue.add(scheduledTask)
        }
    }

    /**
     * Return the number of tasks that are pending for processing.
     */
    fun pendingTasks(): Int {
        return taskQueue.size
    }

    /**
     * Add a task to the task queue, or throws a [RejectedExecutionException] if this instance was shutdown
     * before.
     */
    private fun addTask(task: Runnable) {
        taskQueue.add(ObjectUtil.checkNotNull(task, "task"))
    }

    override fun inEventLoop(thread: Thread): Boolean {
        return thread === this.thread
    }

    override fun shutdownGracefully(quietPeriod: Long, timeout: Long, unit: TimeUnit): Future<*> {
        return terminationFuture()
    }

    override fun terminationFuture(): Future<*> {
        return terminationFuture
    }

    @Deprecated("Use shutdownGracefully instead.")
    override fun shutdown() {
        throw UnsupportedOperationException()
    }

    override fun isShuttingDown(): Boolean {
        return false
    }

    override fun isShutdown(): Boolean {
        return false
    }

    override fun isTerminated(): Boolean {
        return false
    }

    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
        return false
    }

    /**
     * Waits until the worker thread of this executor has no tasks left in its task queue and terminates itself.
     * Because a new worker thread will be started again when a new task is submitted, this operation is only useful
     * when you want to ensure that the worker thread is terminated **after** your application is shut
     * down and there's no chance of submitting a new task afterwards.
     *
     * @return `true` if and only if the worker thread has been terminated
     */
    @Throws(InterruptedException::class)
    fun awaitInactivity(timeout: Long, unit: TimeUnit): Boolean {
        ObjectUtil.checkNotNull(unit, "unit")

        val thread = this.thread
            ?: throw IllegalStateException("thread was not started")
        thread.join(unit.toMillis(timeout))
        return !thread.isAlive
    }

    override fun execute(task: Runnable) {
        execute0(task)
    }

    private fun execute0(@Schedule task: Runnable) {
        addTask(ObjectUtil.checkNotNull(task, "task"))
        if (!inEventLoop()) {
            startThread()
        }
    }

    private fun startThread() {
        if (started.compareAndSet(false, true)) {
            val callingThread = Thread.currentThread()
            val parentCCL = AccessController.doPrivileged(PrivilegedAction {
                callingThread.contextClassLoader
            })
            // Avoid calling classloader leaking through Thread.inheritedAccessControlContext.
            setContextClassLoader(callingThread, null)
            try {
                val t = threadFactory.newThread(taskRunner)
                // Set to null to ensure we not create classloader leaks by holds a strong reference to the inherited
                // classloader.
                // See:
                // - https://github.com/netty/netty/issues/7290
                // - https://bugs.openjdk.java.net/browse/JDK-7008595
                setContextClassLoader(t, null)

                // Set the thread before starting it as otherwise inEventLoop() may return false and so produce
                // an assert error.
                // See https://github.com/netty/netty/issues/4357
                thread = t
                t.start()
            } finally {
                setContextClassLoader(callingThread, parentCCL)
            }
        }
    }

    internal inner class TaskRunner : Runnable {
        override fun run() {
            while (true) {
                val task = takeTask()
                if (task != null) {
                    try {
                        runTask(task)
                    } catch (t: Throwable) {
                        logger.warn("Unexpected exception from the global event executor: ", t)
                    }

                    if (task !== quietPeriodTask) {
                        continue
                    }
                }

                val scheduledTaskQueue: Queue<ScheduledFutureTask<*>>? = this@GlobalEventExecutor.scheduledTaskQueue
                // Terminate if there is no task in the queue (except the noop task).
                if (taskQueue.isEmpty() && (scheduledTaskQueue == null || scheduledTaskQueue.size == 1)) {
                    // Mark the current thread as stopped.
                    // The following CAS must always success and must be uncontended,
                    // because only one thread should be running at the same time.
                    val stopped = started.compareAndSet(true, false)
                    assert(stopped)

                    // Check if there are pending entries added by execute() or schedule*() while we do CAS above.
                    // Do not check scheduledTaskQueue because it is not thread-safe and can only be mutated from a
                    // TaskRunner actively running tasks.
                    if (taskQueue.isEmpty()) {
                        // A) No new task was added and thus there's nothing to handle
                        //    -> safe to terminate because there's nothing left to do
                        // B) A new thread started and handled all the new tasks.
                        //    -> safe to terminate the new thread will take care the rest
                        break
                    }

                    // There are pending tasks added again.
                    if (!started.compareAndSet(false, true)) {
                        // startThread() started a new thread and set 'started' to true.
                        // -> terminate this thread so that the new thread reads from taskQueue exclusively.
                        break
                    }

                    // New tasks were added, but this worker was faster to set 'started' to true.
                    // i.e. a new worker thread was not started by startThread().
                    // -> keep this thread alive to handle the newly added entries.
                }
            }
        }
    }

    companion object {
        private val logger = InternalLoggerFactory.getInstance(GlobalEventExecutor::class.java)

        private val SCHEDULE_QUIET_PERIOD_INTERVAL: Long

        init {
            var quietPeriod = SystemPropertyUtil.getInt("io.netty.globalEventExecutor.quietPeriodSeconds", 1)
            if (quietPeriod <= 0) {
                quietPeriod = 1
            }
            logger.debug("-Dio.netty.globalEventExecutor.quietPeriodSeconds: {}", quietPeriod)

            SCHEDULE_QUIET_PERIOD_INTERVAL = TimeUnit.SECONDS.toNanos(quietPeriod.toLong())
        }

        @JvmField
        val INSTANCE = GlobalEventExecutor()

        private fun setContextClassLoader(t: Thread, cl: ClassLoader?) {
            AccessController.doPrivileged(PrivilegedAction<Void?> {
                t.contextClassLoader = cl
                null
            })
        }
    }
}
