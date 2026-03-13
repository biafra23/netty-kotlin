/*
 * Copyright 2014 The Netty Project
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

import io.netty.util.concurrent.DefaultThreadFactory
import io.netty.util.internal.ObjectUtil
import io.netty.util.internal.StringUtil
import io.netty.util.internal.SystemPropertyUtil
import io.netty.util.internal.logging.InternalLoggerFactory
import java.security.AccessController
import java.security.PrivilegedAction
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Checks if a thread is alive periodically and runs a task when a thread dies.
 *
 * This thread starts a daemon thread to check the state of the threads being watched and to invoke their
 * associated [Runnable]s.  When there is no thread to watch (i.e. all threads are dead), the daemon thread
 * will terminate itself, and a new daemon thread will be started again when a new watch is added.
 *
 * @deprecated will be removed in the next major release
 */
@Deprecated("will be removed in the next major release")
object ThreadDeathWatcher {

    private val logger = InternalLoggerFactory.getInstance(ThreadDeathWatcher::class.java)

    // visible for testing
    @JvmField
    val threadFactory: java.util.concurrent.ThreadFactory

    // Use a MPMC queue as we may end up checking isEmpty() from multiple threads which may not be allowed to do
    // concurrently depending on the implementation of it in a MPSC queue.
    private val pendingEntries = ConcurrentLinkedQueue<Entry>()
    private val watcher = Watcher()
    private val started = AtomicBoolean()

    @Volatile
    private var watcherThread: Thread? = null

    init {
        var poolName = "threadDeathWatcher"
        val serviceThreadPrefix = SystemPropertyUtil.get("io.netty.serviceThreadPrefix")
        if (!StringUtil.isNullOrEmpty(serviceThreadPrefix)) {
            poolName = serviceThreadPrefix + poolName
        }
        // because the ThreadDeathWatcher is a singleton, tasks submitted to it can come from arbitrary threads and
        // this can trigger the creation of a thread from arbitrary thread groups; for this reason, the thread factory
        // must not be sticky about its thread group
        threadFactory = DefaultThreadFactory(poolName, true, Thread.MIN_PRIORITY, null)
    }

    /**
     * Schedules the specified [task] to run when the specified [thread] dies.
     *
     * @param thread the [Thread] to watch
     * @param task the [Runnable] to run when the [thread] dies
     *
     * @throws IllegalArgumentException if the specified [thread] is not alive
     */
    @JvmStatic
    fun watch(thread: Thread, task: Runnable) {
        ObjectUtil.checkNotNull(thread, "thread")
        ObjectUtil.checkNotNull(task, "task")

        if (!thread.isAlive) {
            throw IllegalArgumentException("thread must be alive.")
        }

        schedule(thread, task, true)
    }

    /**
     * Cancels the task scheduled via [watch].
     */
    @JvmStatic
    fun unwatch(thread: Thread, task: Runnable) {
        schedule(
            ObjectUtil.checkNotNull(thread, "thread"),
            ObjectUtil.checkNotNull(task, "task"),
            false
        )
    }

    private fun schedule(thread: Thread, task: Runnable, isWatch: Boolean) {
        pendingEntries.add(Entry(thread, task, isWatch))

        if (started.compareAndSet(false, true)) {
            val watcherThread = threadFactory.newThread(watcher)
            // Set to null to ensure we not create classloader leaks by holds a strong reference to the inherited
            // classloader.
            // See:
            // - https://github.com/netty/netty/issues/7290
            // - https://bugs.openjdk.java.net/browse/JDK-7008595
            AccessController.doPrivileged(PrivilegedAction<Void?> {
                watcherThread.contextClassLoader = null
                null
            })

            watcherThread.start()
            this.watcherThread = watcherThread
        }
    }

    /**
     * Waits until the thread of this watcher has no threads to watch and terminates itself.
     * Because a new watcher thread will be started again on [watch],
     * this operation is only useful when you want to ensure that the watcher thread is terminated
     * **after** your application is shut down and there's no chance of calling
     * [watch] afterwards.
     *
     * @return `true` if and only if the watcher thread has been terminated
     */
    @JvmStatic
    @Throws(InterruptedException::class)
    fun awaitInactivity(timeout: Long, unit: TimeUnit): Boolean {
        ObjectUtil.checkNotNull(unit, "unit")

        val watcherThread = this.watcherThread
        if (watcherThread != null) {
            watcherThread.join(unit.toMillis(timeout))
            return !watcherThread.isAlive
        } else {
            return true
        }
    }

    private class Watcher : Runnable {

        private val watchees = ArrayList<Entry>()

        override fun run() {
            while (true) {
                fetchWatchees()
                notifyWatchees()

                // Try once again just in case notifyWatchees() triggered watch() or unwatch().
                fetchWatchees()
                notifyWatchees()

                try {
                    Thread.sleep(1000)
                } catch (_: InterruptedException) {
                    // Ignore the interrupt; do not terminate until all tasks are run.
                }

                if (watchees.isEmpty() && pendingEntries.isEmpty()) {

                    // Mark the current worker thread as stopped.
                    // The following CAS must always success and must be uncontended,
                    // because only one watcher thread should be running at the same time.
                    val stopped = started.compareAndSet(true, false)
                    assert(stopped)

                    // Check if there are pending entries added by watch() while we do CAS above.
                    if (pendingEntries.isEmpty()) {
                        // A) watch() was not invoked and thus there's nothing to handle
                        //    -> safe to terminate because there's nothing left to do
                        // B) a new watcher thread started and handled them all
                        //    -> safe to terminate the new watcher thread will take care the rest
                        break
                    }

                    // There are pending entries again, added by watch()
                    if (!started.compareAndSet(false, true)) {
                        // watch() started a new watcher thread and set 'started' to true.
                        // -> terminate this thread so that the new watcher reads from pendingEntries exclusively.
                        break
                    }

                    // watch() added an entry, but this worker was faster to set 'started' to true.
                    // i.e. a new watcher thread was not started
                    // -> keep this thread alive to handle the newly added entries.
                }
            }
        }

        private fun fetchWatchees() {
            while (true) {
                val e = pendingEntries.poll() ?: break

                if (e.isWatch) {
                    watchees.add(e)
                } else {
                    watchees.remove(e)
                }
            }
        }

        private fun notifyWatchees() {
            val watchees = this.watchees
            var i = 0
            while (i < watchees.size) {
                val e = watchees[i]
                if (!e.thread.isAlive) {
                    watchees.removeAt(i)
                    try {
                        e.task.run()
                    } catch (t: Throwable) {
                        logger.warn("Thread death watcher task raised an exception:", t)
                    }
                } else {
                    i++
                }
            }
        }
    }

    private data class Entry(
        val thread: Thread,
        val task: Runnable,
        val isWatch: Boolean
    ) {
        override fun hashCode(): Int = thread.hashCode() xor task.hashCode()

        override fun equals(other: Any?): Boolean {
            if (other === this) return true
            if (other !is Entry) return false
            return thread === other.thread && task === other.task
        }
    }
}
