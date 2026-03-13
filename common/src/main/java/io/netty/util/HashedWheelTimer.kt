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
package io.netty.util

import io.netty.util.concurrent.ImmediateExecutor
import io.netty.util.internal.MathUtil
import io.netty.util.internal.ObjectUtil.checkNotNull
import io.netty.util.internal.ObjectUtil.checkPositive
import io.netty.util.internal.PlatformDependent
import io.netty.util.internal.StringUtil.simpleClassName
import io.netty.util.internal.logging.InternalLoggerFactory

import java.util.Collections
import java.util.HashSet
import java.util.Queue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater
import java.util.concurrent.atomic.AtomicLong

/**
 * A [Timer] optimized for approximated I/O timeout scheduling.
 *
 * <h3>Tick Duration</h3>
 *
 * As described with 'approximated', this timer does not execute the scheduled
 * [TimerTask] on time.  [HashedWheelTimer], on every tick, will
 * check if there are any [TimerTask]s behind the schedule and execute
 * them.
 *
 * You can increase or decrease the accuracy of the execution timing by
 * specifying smaller or larger tick duration in the constructor.  In most
 * network applications, I/O timeout does not need to be accurate.  Therefore,
 * the default tick duration is 100 milliseconds and you will not need to try
 * different configurations in most cases.
 *
 * <h3>Ticks per Wheel (Wheel Size)</h3>
 *
 * [HashedWheelTimer] maintains a data structure called 'wheel'.
 * To put simply, a wheel is a hash table of [TimerTask]s whose hash
 * function is 'dead line of the task'.  The default number of ticks per wheel
 * (i.e. the size of the wheel) is 512.  You could specify a larger value
 * if you are going to schedule a lot of timeouts.
 *
 * <h3>Do not create many instances.</h3>
 *
 * [HashedWheelTimer] creates a new thread whenever it is instantiated and
 * started.  Therefore, you should make sure to create only one instance and
 * share it across your application.  One of the common mistakes, that makes
 * your application unresponsive, is to create a new instance for every connection.
 *
 * <h3>Implementation Details</h3>
 *
 * [HashedWheelTimer] is based on
 * [George Varghese](https://cseweb.ucsd.edu/users/varghese/) and
 * Tony Lauck's paper,
 * ['Hashed and Hierarchical Timing Wheels: data structures to efficiently implement a
 * timer facility'](https://cseweb.ucsd.edu/users/varghese/PAPERS/twheel.ps.Z).  More comprehensive slides are located
 * [here](https://www.cse.wustl.edu/~cdgill/courses/cs6874/TimingWheels.ppt).
 */
open class HashedWheelTimer : Timer {

    private val leak: ResourceLeakTracker<HashedWheelTimer>?
    private val worker: Worker = Worker()
    private val workerThread: Thread

    @Suppress("unused")
    @Volatile
    @JvmField
    var workerState: Int = 0 // 0 - init, 1 - started, 2 - shut down

    private val tickDuration: Long
    private val wheel: Array<HashedWheelBucket>
    private val mask: Int
    private val startTimeInitialized: CountDownLatch = CountDownLatch(1)
    private val timeouts: Queue<HashedWheelTimeout> = PlatformDependent.newMpscQueue()
    private val cancelledTimeouts: Queue<HashedWheelTimeout> = PlatformDependent.newMpscQueue()
    private val pendingTimeoutsCount: AtomicLong = AtomicLong(0)
    private val maxPendingTimeouts: Long
    private val taskExecutor: Executor

    @Volatile
    private var startTime: Long = 0

    /**
     * Creates a new timer with the default thread factory
     * ([Executors.defaultThreadFactory]), default tick duration, and
     * default number of ticks per wheel.
     */
    constructor() : this(Executors.defaultThreadFactory())

    /**
     * Creates a new timer with the default thread factory
     * ([Executors.defaultThreadFactory]) and default number of ticks
     * per wheel.
     *
     * @param tickDuration the duration between tick
     * @param unit         the time unit of the `tickDuration`
     * @throws NullPointerException     if `unit` is `null`
     * @throws IllegalArgumentException if `tickDuration` is <= 0
     */
    constructor(tickDuration: Long, unit: TimeUnit) : this(Executors.defaultThreadFactory(), tickDuration, unit)

    /**
     * Creates a new timer with the default thread factory
     * ([Executors.defaultThreadFactory]).
     *
     * @param tickDuration  the duration between tick
     * @param unit          the time unit of the `tickDuration`
     * @param ticksPerWheel the size of the wheel
     * @throws NullPointerException     if `unit` is `null`
     * @throws IllegalArgumentException if either of `tickDuration` and `ticksPerWheel` is <= 0
     */
    constructor(tickDuration: Long, unit: TimeUnit, ticksPerWheel: Int) :
        this(Executors.defaultThreadFactory(), tickDuration, unit, ticksPerWheel)

    /**
     * Creates a new timer with the default tick duration and default number of
     * ticks per wheel.
     *
     * @param threadFactory a [ThreadFactory] that creates a
     *                      background [Thread] which is dedicated to
     *                      [TimerTask] execution.
     * @throws NullPointerException if `threadFactory` is `null`
     */
    constructor(threadFactory: ThreadFactory) : this(threadFactory, 100, TimeUnit.MILLISECONDS)

    /**
     * Creates a new timer with the default number of ticks per wheel.
     *
     * @param threadFactory a [ThreadFactory] that creates a
     *                      background [Thread] which is dedicated to
     *                      [TimerTask] execution.
     * @param tickDuration  the duration between tick
     * @param unit          the time unit of the `tickDuration`
     * @throws NullPointerException     if either of `threadFactory` and `unit` is `null`
     * @throws IllegalArgumentException if `tickDuration` is <= 0
     */
    constructor(
        threadFactory: ThreadFactory,
        tickDuration: Long,
        unit: TimeUnit
    ) : this(threadFactory, tickDuration, unit, 512)

    /**
     * Creates a new timer.
     *
     * @param threadFactory a [ThreadFactory] that creates a
     *                      background [Thread] which is dedicated to
     *                      [TimerTask] execution.
     * @param tickDuration  the duration between tick
     * @param unit          the time unit of the `tickDuration`
     * @param ticksPerWheel the size of the wheel
     * @throws NullPointerException     if either of `threadFactory` and `unit` is `null`
     * @throws IllegalArgumentException if either of `tickDuration` and `ticksPerWheel` is <= 0
     */
    constructor(
        threadFactory: ThreadFactory,
        tickDuration: Long,
        unit: TimeUnit,
        ticksPerWheel: Int
    ) : this(threadFactory, tickDuration, unit, ticksPerWheel, true)

    /**
     * Creates a new timer.
     *
     * @param threadFactory a [ThreadFactory] that creates a
     *                      background [Thread] which is dedicated to
     *                      [TimerTask] execution.
     * @param tickDuration  the duration between tick
     * @param unit          the time unit of the `tickDuration`
     * @param ticksPerWheel the size of the wheel
     * @param leakDetection `true` if leak detection should be enabled always,
     *                      if false it will only be enabled if the worker thread is not
     *                      a daemon thread.
     * @throws NullPointerException     if either of `threadFactory` and `unit` is `null`
     * @throws IllegalArgumentException if either of `tickDuration` and `ticksPerWheel` is <= 0
     */
    constructor(
        threadFactory: ThreadFactory,
        tickDuration: Long,
        unit: TimeUnit,
        ticksPerWheel: Int,
        leakDetection: Boolean
    ) : this(threadFactory, tickDuration, unit, ticksPerWheel, leakDetection, -1)

    /**
     * Creates a new timer.
     *
     * @param threadFactory        a [ThreadFactory] that creates a
     *                             background [Thread] which is dedicated to
     *                             [TimerTask] execution.
     * @param tickDuration         the duration between tick
     * @param unit                 the time unit of the `tickDuration`
     * @param ticksPerWheel        the size of the wheel
     * @param leakDetection        `true` if leak detection should be enabled always,
     *                             if false it will only be enabled if the worker thread is not
     *                             a daemon thread.
     * @param maxPendingTimeouts   The maximum number of pending timeouts after which call to
     *                             `newTimeout` will result in
     *                             [java.util.concurrent.RejectedExecutionException]
     *                             being thrown. No maximum pending timeouts limit is assumed if
     *                             this value is 0 or negative.
     * @throws NullPointerException     if either of `threadFactory` and `unit` is `null`
     * @throws IllegalArgumentException if either of `tickDuration` and `ticksPerWheel` is <= 0
     */
    constructor(
        threadFactory: ThreadFactory,
        tickDuration: Long,
        unit: TimeUnit,
        ticksPerWheel: Int,
        leakDetection: Boolean,
        maxPendingTimeouts: Long
    ) : this(threadFactory, tickDuration, unit, ticksPerWheel, leakDetection, maxPendingTimeouts, ImmediateExecutor.INSTANCE)

    /**
     * Creates a new timer.
     *
     * @param threadFactory        a [ThreadFactory] that creates a
     *                             background [Thread] which is dedicated to
     *                             [TimerTask] execution.
     * @param tickDuration         the duration between tick
     * @param unit                 the time unit of the `tickDuration`
     * @param ticksPerWheel        the size of the wheel
     * @param leakDetection        `true` if leak detection should be enabled always,
     *                             if false it will only be enabled if the worker thread is not
     *                             a daemon thread.
     * @param maxPendingTimeouts   The maximum number of pending timeouts after which call to
     *                             `newTimeout` will result in
     *                             [java.util.concurrent.RejectedExecutionException]
     *                             being thrown. No maximum pending timeouts limit is assumed if
     *                             this value is 0 or negative.
     * @param taskExecutor         The [Executor] that is used to execute the submitted [TimerTask]s.
     *                             The caller is responsible to shutdown the [Executor] once it is not needed
     *                             anymore.
     * @throws NullPointerException     if either of `threadFactory` and `unit` is `null`
     * @throws IllegalArgumentException if either of `tickDuration` and `ticksPerWheel` is <= 0
     */
    constructor(
        threadFactory: ThreadFactory,
        tickDuration: Long,
        unit: TimeUnit,
        ticksPerWheel: Int,
        leakDetection: Boolean,
        maxPendingTimeouts: Long,
        taskExecutor: Executor
    ) {
        checkNotNull(threadFactory, "threadFactory")
        checkNotNull(unit, "unit")
        checkPositive(tickDuration, "tickDuration")
        checkPositive(ticksPerWheel, "ticksPerWheel")
        this.taskExecutor = checkNotNull(taskExecutor, "taskExecutor")

        // Normalize ticksPerWheel to power of two and initialize the wheel.
        wheel = createWheel(ticksPerWheel)
        mask = wheel.size - 1

        // Convert tickDuration to nanos.
        val duration = unit.toNanos(tickDuration)

        // Prevent overflow.
        if (duration >= Long.MAX_VALUE / wheel.size) {
            throw IllegalArgumentException(
                "tickDuration: $tickDuration (expected: 0 < tickDuration in nanos < ${Long.MAX_VALUE / wheel.size}"
            )
        }

        if (duration < MILLISECOND_NANOS) {
            logger.warn("Configured tickDuration {} smaller than {}, using 1ms.", tickDuration, MILLISECOND_NANOS)
            this.tickDuration = MILLISECOND_NANOS
        } else {
            this.tickDuration = duration
        }

        workerThread = threadFactory.newThread(worker)

        leak = if (leakDetection || !workerThread.isDaemon) leakDetector.track(this) else null

        this.maxPendingTimeouts = maxPendingTimeouts

        if (INSTANCE_COUNTER.incrementAndGet() > INSTANCE_COUNT_LIMIT &&
            WARNED_TOO_MANY_INSTANCES.compareAndSet(false, true)
        ) {
            reportTooManyInstances()
        }
    }

    @Suppress("deprecation")
    @Throws(Throwable::class)
    protected fun finalize() {
        try {
            // This object is going to be GCed and it is assumed the ship has sailed to do a proper shutdown. If
            // we have not yet shutdown then we want to make sure we decrement the active instance count.
            if (WORKER_STATE_UPDATER.getAndSet(this, WORKER_STATE_SHUTDOWN) != WORKER_STATE_SHUTDOWN) {
                INSTANCE_COUNTER.decrementAndGet()
            }
        } finally {
            // Note: Kotlin does not have super.finalize() by default, but we call it for parity.
            @Suppress("DEPRECATION")
            (this as Any).javaClass.superclass?.getDeclaredMethod("finalize")
            // In practice, for a direct subclass of Any, super.finalize() is a no-op.
        }
    }

    /**
     * Starts the background thread explicitly.  The background thread will
     * start automatically on demand even if you did not call this method.
     *
     * @throws IllegalStateException if this timer has been
     *                               [stopped][stop] already
     */
    fun start() {
        when (WORKER_STATE_UPDATER.get(this)) {
            WORKER_STATE_INIT -> {
                if (WORKER_STATE_UPDATER.compareAndSet(this, WORKER_STATE_INIT, WORKER_STATE_STARTED)) {
                    workerThread.start()
                }
            }
            WORKER_STATE_STARTED -> {
                // Already started
            }
            WORKER_STATE_SHUTDOWN -> throw IllegalStateException("cannot be started once stopped")
            else -> throw Error("Invalid WorkerState: ${WORKER_STATE_UPDATER.get(this)}")
        }

        // Wait until the startTime is initialized by the worker.
        while (startTime == 0L) {
            try {
                startTimeInitialized.await()
            } catch (ignore: InterruptedException) {
                // Ignore - it will be ready very soon.
            }
        }
    }

    override fun stop(): Set<Timeout> {
        if (Thread.currentThread() === workerThread) {
            throw IllegalStateException(
                "${HashedWheelTimer::class.java.simpleName}.stop() cannot be called from ${TimerTask::class.java.simpleName}"
            )
        }

        if (!WORKER_STATE_UPDATER.compareAndSet(this, WORKER_STATE_STARTED, WORKER_STATE_SHUTDOWN)) {
            // workerState can be 0 or 2 at this moment - let it always be 2.
            if (WORKER_STATE_UPDATER.getAndSet(this, WORKER_STATE_SHUTDOWN) != WORKER_STATE_SHUTDOWN) {
                INSTANCE_COUNTER.decrementAndGet()
                if (leak != null) {
                    val closed = leak.close(this)
                    assert(closed)
                }
            }

            return Collections.emptySet()
        }

        try {
            var interrupted = false
            while (workerThread.isAlive) {
                workerThread.interrupt()
                try {
                    workerThread.join(100)
                } catch (ignored: InterruptedException) {
                    interrupted = true
                }
            }

            if (interrupted) {
                Thread.currentThread().interrupt()
            }
        } finally {
            INSTANCE_COUNTER.decrementAndGet()
            if (leak != null) {
                val closed = leak.close(this)
                assert(closed)
            }
        }
        val unprocessed = worker.unprocessedTimeouts()
        val cancelled = HashSet<Timeout>(unprocessed.size)
        for (timeout in unprocessed) {
            if (timeout.cancel()) {
                cancelled.add(timeout)
            }
        }
        return cancelled
    }

    override fun newTimeout(task: TimerTask, delay: Long, unit: TimeUnit): Timeout {
        checkNotNull(task, "task")
        checkNotNull(unit, "unit")

        val pendingTimeoutsCountVal = pendingTimeoutsCount.incrementAndGet()

        if (maxPendingTimeouts > 0 && pendingTimeoutsCountVal > maxPendingTimeouts) {
            pendingTimeoutsCount.decrementAndGet()
            throw RejectedExecutionException(
                "Number of pending timeouts ($pendingTimeoutsCountVal) is greater than or equal to maximum allowed pending " +
                    "timeouts ($maxPendingTimeouts)"
            )
        }

        start()

        // Add the timeout to the timeout queue which will be processed on the next tick.
        // During processing all the queued HashedWheelTimeouts will be added to the correct HashedWheelBucket.
        var deadline = System.nanoTime() + unit.toNanos(delay) - startTime

        // Guard against overflow.
        if (delay > 0 && deadline < 0) {
            deadline = Long.MAX_VALUE
        }
        val timeout = HashedWheelTimeout(this, task, deadline)
        timeouts.add(timeout)
        return timeout
    }

    /**
     * Returns the number of pending timeouts of this [Timer].
     */
    fun pendingTimeouts(): Long {
        return pendingTimeoutsCount.get()
    }

    private inner class Worker : Runnable {
        private val unprocessedTimeouts: MutableSet<Timeout> = HashSet()

        private var tick: Long = 0

        override fun run() {
            // Initialize the startTime.
            startTime = System.nanoTime()
            if (startTime == 0L) {
                // We use 0 as an indicator for the uninitialized value here, so make sure it's not 0 when initialized.
                startTime = 1
            }

            // Notify the other threads waiting for the initialization at start().
            startTimeInitialized.countDown()

            do {
                val deadline = waitForNextTick()
                if (deadline > 0) {
                    val idx = (tick and mask.toLong()).toInt()
                    processCancelledTasks()
                    val bucket = wheel[idx]
                    transferTimeoutsToBuckets()
                    bucket.expireTimeouts(deadline)
                    tick++
                }
            } while (WORKER_STATE_UPDATER.get(this@HashedWheelTimer) == WORKER_STATE_STARTED)

            // Fill the unprocessedTimeouts so we can return them from stop() method.
            for (bucket in wheel) {
                bucket.clearTimeouts(unprocessedTimeouts)
            }
            while (true) {
                val timeout = timeouts.poll() ?: break
                if (!timeout.isCancelled) {
                    unprocessedTimeouts.add(timeout)
                }
            }
            processCancelledTasks()
        }

        private fun transferTimeoutsToBuckets() {
            // transfer only max. 100000 timeouts per tick to prevent a thread to stale the workerThread when it just
            // adds new timeouts in a loop.
            for (i in 0 until 100000) {
                val timeout = timeouts.poll() ?: break // all processed
                if (timeout.state() == HashedWheelTimeout.ST_CANCELLED) {
                    // Was cancelled in the meantime.
                    continue
                }

                val calculated = timeout.deadline / tickDuration
                timeout.remainingRounds = (calculated - tick) / wheel.size

                val ticks = Math.max(calculated, tick) // Ensure we don't schedule for past.
                val stopIndex = (ticks and mask.toLong()).toInt()

                val bucket = wheel[stopIndex]
                bucket.addTimeout(timeout)
            }
        }

        private fun processCancelledTasks() {
            while (true) {
                val timeout = cancelledTimeouts.poll() ?: break // all processed
                try {
                    timeout.removeAfterCancellation()
                } catch (t: Throwable) {
                    if (logger.isWarnEnabled()) {
                        logger.warn("An exception was thrown while process a cancellation task", t)
                    }
                }
            }
        }

        /**
         * calculate goal nanoTime from startTime and current tick number,
         * then wait until that goal has been reached.
         * @return Long.MIN_VALUE if received a shutdown request,
         * current time otherwise (with Long.MIN_VALUE changed by +1)
         */
        private fun waitForNextTick(): Long {
            val deadline = tickDuration * (tick + 1)

            while (true) {
                val currentTime = System.nanoTime() - startTime
                var sleepTimeMs = (deadline - currentTime + 999999) / 1000000

                if (sleepTimeMs <= 0) {
                    return if (currentTime == Long.MIN_VALUE) {
                        -Long.MAX_VALUE
                    } else {
                        currentTime
                    }
                }

                // Check if we run on windows, as if thats the case we will need
                // to round the sleepTime as workaround for a bug that only affect
                // the JVM if it runs on windows.
                //
                // See https://github.com/netty/netty/issues/356
                if (PlatformDependent.isWindows()) {
                    sleepTimeMs = sleepTimeMs / 10 * 10
                    if (sleepTimeMs == 0L) {
                        sleepTimeMs = 1
                    }
                }

                try {
                    Thread.sleep(sleepTimeMs)
                } catch (ignored: InterruptedException) {
                    if (WORKER_STATE_UPDATER.get(this@HashedWheelTimer) == WORKER_STATE_SHUTDOWN) {
                        return Long.MIN_VALUE
                    }
                }
            }
        }

        fun unprocessedTimeouts(): Set<Timeout> {
            return Collections.unmodifiableSet(unprocessedTimeouts)
        }
    }

    private class HashedWheelTimeout(
        private val timer: HashedWheelTimer,
        private val task: TimerTask,
        @JvmField val deadline: Long
    ) : Timeout, Runnable {

        @Suppress("unused")
        @Volatile
        @JvmField
        var state: Int = ST_INIT

        // remainingRounds will be calculated and set by Worker.transferTimeoutsToBuckets() before the
        // HashedWheelTimeout will be added to the correct HashedWheelBucket.
        @JvmField
        var remainingRounds: Long = 0

        // This will be used to chain timeouts in HashedWheelTimerBucket via a double-linked-list.
        // As only the workerThread will act on it there is no need for synchronization / volatile.
        @JvmField
        var next: HashedWheelTimeout? = null
        @JvmField
        var prev: HashedWheelTimeout? = null

        // The bucket to which the timeout was added
        @JvmField
        var bucket: HashedWheelBucket? = null

        override fun timer(): Timer = timer

        override fun task(): TimerTask = task

        override fun cancel(): Boolean {
            // only update the state it will be removed from HashedWheelBucket on next tick.
            if (!compareAndSetState(ST_INIT, ST_CANCELLED)) {
                return false
            }
            // If a task should be canceled we put this to another queue which will be processed on each tick.
            // So this means that we will have a GC latency of max. 1 tick duration which is good enough. This way
            // we can make again use of our MpscLinkedQueue and so minimize the locking / overhead as much as possible.
            timer.cancelledTimeouts.add(this)
            return true
        }

        private fun remove() {
            val bucket = this.bucket
            if (bucket != null) {
                bucket.remove(this)
            }
            timer.pendingTimeoutsCount.decrementAndGet()
        }

        fun removeAfterCancellation() {
            remove()
            task.cancelled(this)
        }

        fun compareAndSetState(expected: Int, state: Int): Boolean {
            return STATE_UPDATER.compareAndSet(this, expected, state)
        }

        fun state(): Int = state

        override val isCancelled: Boolean
            get() = state() == ST_CANCELLED

        override val isExpired: Boolean
            get() = state() == ST_EXPIRED

        fun expire() {
            if (!compareAndSetState(ST_INIT, ST_EXPIRED)) {
                return
            }

            try {
                remove()
                timer.taskExecutor.execute(this)
            } catch (t: Throwable) {
                if (logger.isWarnEnabled()) {
                    logger.warn(
                        "An exception was thrown while submit ${TimerTask::class.java.simpleName} for execution.", t
                    )
                }
            }
        }

        override fun run() {
            try {
                task.run(this)
            } catch (t: Throwable) {
                if (logger.isWarnEnabled()) {
                    logger.warn("An exception was thrown by ${TimerTask::class.java.simpleName}.", t)
                }
            }
        }

        override fun toString(): String {
            val currentTime = System.nanoTime()
            val remaining = deadline - currentTime + timer.startTime

            val buf = StringBuilder(192)
                .append(simpleClassName(this))
                .append('(')
                .append("deadline: ")
            if (remaining > 0) {
                buf.append(remaining)
                    .append(" ns later")
            } else if (remaining < 0) {
                buf.append(-remaining)
                    .append(" ns ago")
            } else {
                buf.append("now")
            }

            if (isCancelled) {
                buf.append(", cancelled")
            }

            return buf.append(", task: ")
                .append(task())
                .append(')')
                .toString()
        }

        companion object {
            const val ST_INIT: Int = 0
            const val ST_CANCELLED: Int = 1
            const val ST_EXPIRED: Int = 2

            @JvmStatic
            private val STATE_UPDATER: AtomicIntegerFieldUpdater<HashedWheelTimeout> =
                AtomicIntegerFieldUpdater.newUpdater(HashedWheelTimeout::class.java, "state")
        }
    }

    /**
     * Bucket that stores HashedWheelTimeouts. These are stored in a linked-list like datastructure to allow easy
     * removal of HashedWheelTimeouts in the middle. Also the HashedWheelTimeout act as nodes themself and so no
     * extra object creation is needed.
     */
    private class HashedWheelBucket {
        // Used for the linked-list datastructure
        private var head: HashedWheelTimeout? = null
        private var tail: HashedWheelTimeout? = null

        /**
         * Add [HashedWheelTimeout] to this bucket.
         */
        fun addTimeout(timeout: HashedWheelTimeout) {
            assert(timeout.bucket == null)
            timeout.bucket = this
            if (head == null) {
                head = timeout
                tail = timeout
            } else {
                tail!!.next = timeout
                timeout.prev = tail
                tail = timeout
            }
        }

        /**
         * Expire all [HashedWheelTimeout]s for the given `deadline`.
         */
        fun expireTimeouts(deadline: Long) {
            var timeout = head

            // process all timeouts
            while (timeout != null) {
                val next = timeout.next
                if (timeout.remainingRounds <= 0) {
                    if (timeout.deadline <= deadline) {
                        timeout.expire()
                    } else {
                        // The timeout was placed into a wrong slot. This should never happen.
                        throw IllegalStateException(
                            "timeout.deadline (${timeout.deadline}) > deadline ($deadline)"
                        )
                    }
                } else if (!timeout.isCancelled) {
                    timeout.remainingRounds--
                }
                timeout = next
            }
        }

        fun remove(timeout: HashedWheelTimeout): HashedWheelTimeout? {
            val prev = timeout.prev
            val next = timeout.next

            // remove timeout that was either processed or cancelled by updating the linked-list
            if (prev != null) {
                prev.next = next
            }
            if (next != null) {
                next.prev = prev
            }

            if (timeout === head) {
                head = next
            }
            if (timeout === tail) {
                tail = prev
            }
            // null out prev, next and bucket to allow for GC.
            timeout.prev = null
            timeout.next = null
            timeout.bucket = null
            return next
        }

        /**
         * Clear this bucket and return all not expired / cancelled [Timeout]s.
         */
        fun clearTimeouts(set: MutableSet<Timeout>) {
            while (true) {
                val timeout = pollTimeout() ?: return
                if (timeout.isExpired || timeout.isCancelled) {
                    continue
                }
                set.add(timeout)
            }
        }

        private fun pollTimeout(): HashedWheelTimeout? {
            val head = this.head ?: return null
            val next = head.next
            if (next == null) {
                this.head = null
                tail = null
            } else {
                this.head = next
                next.prev = null
            }

            // null out prev and next to allow for GC.
            head.next = null
            head.prev = null
            head.bucket = null
            return head
        }
    }

    companion object {
        @JvmStatic
        val logger = InternalLoggerFactory.getInstance(HashedWheelTimer::class.java)

        @JvmStatic
        private val INSTANCE_COUNTER = AtomicInteger()
        @JvmStatic
        private val WARNED_TOO_MANY_INSTANCES = AtomicBoolean()
        @JvmStatic
        private val INSTANCE_COUNT_LIMIT = 64
        @JvmStatic
        private val MILLISECOND_NANOS = TimeUnit.MILLISECONDS.toNanos(1)
        @JvmStatic
        private val leakDetector: ResourceLeakDetector<HashedWheelTimer> = ResourceLeakDetectorFactory.instance()
            .newResourceLeakDetector(HashedWheelTimer::class.java, 1)

        @JvmStatic
        private val WORKER_STATE_UPDATER: AtomicIntegerFieldUpdater<HashedWheelTimer> =
            AtomicIntegerFieldUpdater.newUpdater(HashedWheelTimer::class.java, "workerState")

        const val WORKER_STATE_INIT: Int = 0
        const val WORKER_STATE_STARTED: Int = 1
        const val WORKER_STATE_SHUTDOWN: Int = 2

        @JvmStatic
        private fun createWheel(ticksPerWheel: Int): Array<HashedWheelBucket> {
            val normalizedTicksPerWheel = MathUtil.findNextPositivePowerOfTwo(ticksPerWheel)
            return Array(normalizedTicksPerWheel) { HashedWheelBucket() }
        }

        @JvmStatic
        private fun reportTooManyInstances() {
            if (logger.isErrorEnabled()) {
                val resourceType = simpleClassName(HashedWheelTimer::class.java)
                logger.error(
                    "You are creating too many $resourceType instances. " +
                        "$resourceType is a shared resource that must be reused across the JVM, " +
                        "so that only a few instances are created."
                )
            }
        }
    }
}
