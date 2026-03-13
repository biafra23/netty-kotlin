/*
 * Copyright 2017 The Netty Project
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
package io.netty.util.internal

import io.netty.util.concurrent.FastThreadLocalThread

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.security.AccessController
import java.security.PrivilegedAction
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Allows a way to register some [Runnable] that will executed once there are no references to an [Object]
 * anymore.
 */
object ObjectCleaner {

    private val REFERENCE_QUEUE_POLL_TIMEOUT_MS: Int =
        Math.max(500, SystemPropertyUtil.getInt("io.netty.util.internal.ObjectCleaner.refQueuePollTimeout", 10000))

    // Package-private for testing
    @JvmField
    val CLEANER_THREAD_NAME: String = ObjectCleaner::class.java.simpleName + "Thread"

    // This will hold a reference to the AutomaticCleanerReference which will be removed once we called cleanup()
    private val LIVE_SET: MutableSet<AutomaticCleanerReference> = ConcurrentHashMap.newKeySet()
    private val REFERENCE_QUEUE: ReferenceQueue<Any> = ReferenceQueue()
    private val CLEANER_RUNNING: AtomicBoolean = AtomicBoolean(false)

    private val CLEANER_TASK: Runnable = Runnable {
        var interrupted = false
        while (true) {
            // Keep on processing as long as the LIVE_SET is not empty and once it becomes empty
            // See if we can let this thread complete.
            while (LIVE_SET.isNotEmpty()) {
                val reference: AutomaticCleanerReference?
                try {
                    reference = REFERENCE_QUEUE.remove(REFERENCE_QUEUE_POLL_TIMEOUT_MS.toLong())
                            as? AutomaticCleanerReference
                } catch (ex: InterruptedException) {
                    // Just consume and move on
                    interrupted = true
                    continue
                }
                if (reference != null) {
                    try {
                        reference.cleanup()
                    } catch (ignored: Throwable) {
                        // ignore exceptions, and don't log in case the logger throws an exception, blocks, or has
                        // other unexpected side effects.
                    }
                    LIVE_SET.remove(reference)
                }
            }
            CLEANER_RUNNING.set(false)

            // Its important to first access the LIVE_SET and then CLEANER_RUNNING to ensure correct
            // behavior in multi-threaded environments.
            if (LIVE_SET.isEmpty() || !CLEANER_RUNNING.compareAndSet(false, true)) {
                // There was nothing added after we set STARTED to false or some other cleanup Thread
                // was started already so its safe to let this Thread complete now.
                break
            }
        }
        if (interrupted) {
            // As we caught the InterruptedException above we should mark the Thread as interrupted.
            Thread.currentThread().interrupt()
        }
    }

    /**
     * Register the given [Object] for which the [Runnable] will be executed once there are no references
     * to the object anymore.
     *
     * This should only be used if there are no other ways to execute some cleanup once the Object is not reachable
     * anymore because it is not a cheap way to handle the cleanup.
     */
    @JvmStatic
    fun register(`object`: Any, cleanupTask: Runnable) {
        val reference = AutomaticCleanerReference(
            `object`,
            ObjectUtil.checkNotNull(cleanupTask, "cleanupTask")
        )
        // Its important to add the reference to the LIVE_SET before we access CLEANER_RUNNING to ensure correct
        // behavior in multi-threaded environments.
        LIVE_SET.add(reference)

        // Check if there is already a cleaner running.
        if (CLEANER_RUNNING.compareAndSet(false, true)) {
            val cleanupThread = FastThreadLocalThread(CLEANER_TASK)
            cleanupThread.priority = Thread.MIN_PRIORITY
            // Set to null to ensure we not create classloader leaks by holding a strong reference to the inherited
            // classloader.
            // See:
            // - https://github.com/netty/netty/issues/7290
            // - https://bugs.openjdk.java.net/browse/JDK-7008595
            AccessController.doPrivileged(PrivilegedAction<Void?> {
                cleanupThread.contextClassLoader = null
                null
            })
            cleanupThread.name = CLEANER_THREAD_NAME

            // Mark this as a daemon thread to ensure that we the JVM can exit if this is the only thread that is
            // running.
            cleanupThread.isDaemon = true
            cleanupThread.start()
        }
    }

    @JvmStatic
    fun getLiveSetCount(): Int {
        return LIVE_SET.size
    }

    private class AutomaticCleanerReference(
        referent: Any,
        private val cleanupTask: Runnable
    ) : WeakReference<Any>(referent, REFERENCE_QUEUE) {

        fun cleanup() {
            cleanupTask.run()
        }

        override fun get(): Nothing? {
            return null
        }

        override fun clear() {
            LIVE_SET.remove(this)
            super.clear()
        }
    }
}
