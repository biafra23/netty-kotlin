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
package io.netty.util.concurrent

import io.netty.util.internal.InternalThreadLocalMap
import io.netty.util.internal.LongLongHashMap
import io.netty.util.internal.logging.InternalLoggerFactory
import java.util.concurrent.atomic.AtomicReference

/**
 * A special [Thread] that provides fast access to [FastThreadLocal] variables.
 */
open class FastThreadLocalThread : Thread {

    // This will be set to true if we have a chance to wrap the Runnable.
    private val cleanupFastThreadLocals: Boolean

    private var threadLocalMapField: InternalThreadLocalMap? = null

    constructor() : super() {
        cleanupFastThreadLocals = false
    }

    constructor(target: Runnable) : super(FastThreadLocalRunnable.wrap(target)) {
        cleanupFastThreadLocals = true
    }

    constructor(group: ThreadGroup?, target: Runnable) : super(group, FastThreadLocalRunnable.wrap(target)) {
        cleanupFastThreadLocals = true
    }

    constructor(name: String) : super(name) {
        cleanupFastThreadLocals = false
    }

    constructor(group: ThreadGroup?, name: String) : super(group, name) {
        cleanupFastThreadLocals = false
    }

    constructor(target: Runnable, name: String) : super(FastThreadLocalRunnable.wrap(target), name) {
        cleanupFastThreadLocals = true
    }

    constructor(group: ThreadGroup?, target: Runnable, name: String) : super(group, FastThreadLocalRunnable.wrap(target), name) {
        cleanupFastThreadLocals = true
    }

    constructor(group: ThreadGroup?, target: Runnable, name: String, stackSize: Long) : super(group, FastThreadLocalRunnable.wrap(target), name, stackSize) {
        cleanupFastThreadLocals = true
    }

    /**
     * Returns the internal data structure that keeps the thread-local variables bound to this thread.
     * Note that this method is for internal use only, and thus is subject to change at any time.
     */
    fun threadLocalMap(): InternalThreadLocalMap? {
        if (this !== currentThread() && logger.isWarnEnabled()) {
            logger.warn(RuntimeException("It's not thread-safe to get 'threadLocalMap' " +
                    "which doesn't belong to the caller thread"))
        }
        return threadLocalMapField
    }

    /**
     * Sets the internal data structure that keeps the thread-local variables bound to this thread.
     * Note that this method is for internal use only, and thus is subject to change at any time.
     */
    fun setThreadLocalMap(threadLocalMap: InternalThreadLocalMap?) {
        if (this !== currentThread() && logger.isWarnEnabled()) {
            logger.warn(RuntimeException("It's not thread-safe to set 'threadLocalMap' " +
                    "which doesn't belong to the caller thread"))
        }
        this.threadLocalMapField = threadLocalMap
    }

    /**
     * Returns `true` if [FastThreadLocal.removeAll] will be called once [run] completes.
     *
     * @deprecated Use [FastThreadLocalThread.currentThreadWillCleanupFastThreadLocals] instead
     */
    @Deprecated("Use currentThreadWillCleanupFastThreadLocals() instead")
    open fun willCleanupFastThreadLocals(): Boolean {
        return cleanupFastThreadLocals
    }

    /**
     * Query whether this thread is allowed to perform blocking calls or not.
     * [FastThreadLocalThread]s are often used in event-loops, where blocking calls are forbidden in order to
     * prevent event-loop stalls, so this method returns `false` by default.
     *
     * Subclasses of [FastThreadLocalThread] can override this method if they are not meant to be used for
     * running event-loops.
     *
     * @return `false`, unless overridden by a subclass.
     */
    open fun permitBlockingCalls(): Boolean {
        return false
    }

    /**
     * Immutable, thread-safe helper class that wraps [LongLongHashMap]
     */
    private class FallbackThreadSet private constructor(private val map: LongLongHashMap) {

        constructor() : this(LongLongHashMap(EMPTY_VALUE))

        fun contains(threadId: Long): Boolean {
            val key = threadId ushr 6
            val bit = 1L shl (threadId.toInt() and 63)
            val bitmap = map.get(key)
            return (bitmap and bit) != 0L
        }

        fun add(threadId: Long): FallbackThreadSet {
            val key = threadId ushr 6
            val bit = 1L shl (threadId.toInt() and 63)
            val newMap = LongLongHashMap(map)
            val oldBitmap = newMap.get(key)
            val newBitmap = oldBitmap or bit
            newMap.put(key, newBitmap)
            return FallbackThreadSet(newMap)
        }

        fun remove(threadId: Long): FallbackThreadSet {
            val key = threadId ushr 6
            val bit = 1L shl (threadId.toInt() and 63)
            val oldBitmap = map.get(key)
            if ((oldBitmap and bit) == 0L) {
                return this
            }
            val newMap = LongLongHashMap(map)
            val newBitmap = oldBitmap and bit.inv()
            if (newBitmap != EMPTY_VALUE) {
                newMap.put(key, newBitmap)
            } else {
                newMap.remove(key)
            }
            return FallbackThreadSet(newMap)
        }

        companion object {
            @JvmField
            val EMPTY = FallbackThreadSet()
            private const val EMPTY_VALUE = 0L
        }
    }

    companion object {
        private val logger = InternalLoggerFactory.getInstance(FastThreadLocalThread::class.java)

        /**
         * Set of thread IDs that are treated like [FastThreadLocalThread].
         */
        private val fallbackThreads = AtomicReference(FallbackThreadSet.EMPTY)

        /**
         * Returns `true` if [FastThreadLocal.removeAll] will be called once [Thread.run] completes.
         *
         * @deprecated Use [FastThreadLocalThread.currentThreadWillCleanupFastThreadLocals] instead
         */
        @Deprecated("Use currentThreadWillCleanupFastThreadLocals() instead")
        @JvmStatic
        fun willCleanupFastThreadLocals(thread: Thread): Boolean {
            return thread is FastThreadLocalThread && thread.willCleanupFastThreadLocals()
        }

        /**
         * Returns `true` if [FastThreadLocal.removeAll] will be called once [Thread.run] completes.
         */
        @JvmStatic
        fun currentThreadWillCleanupFastThreadLocals(): Boolean {
            // intentionally doesn't accept a thread parameter to work with ScopedValue in the future
            val currentThread = currentThread()
            if (currentThread is FastThreadLocalThread) {
                return currentThread.willCleanupFastThreadLocals()
            }
            return isFastThreadLocalVirtualThread()
        }

        /**
         * Returns `true` if this thread supports [FastThreadLocal].
         */
        @JvmStatic
        fun currentThreadHasFastThreadLocal(): Boolean {
            // intentionally doesn't accept a thread parameter to work with ScopedValue in the future
            return currentThread() is FastThreadLocalThread || isFastThreadLocalVirtualThread()
        }

        private fun isFastThreadLocalVirtualThread(): Boolean {
            return fallbackThreads.get().contains(currentThread().id)
        }

        /**
         * Run the given task with [FastThreadLocal] support. This call should wrap the runnable for any thread that
         * is long-running enough to make treating it as a [FastThreadLocalThread] reasonable, but that can't
         * actually extend this class (e.g. because it's a virtual thread). Netty will use optimizations for recyclers and
         * allocators as if this was a [FastThreadLocalThread].
         *
         * This method will clean up any [FastThreadLocal]s at the end, and
         * [currentThreadWillCleanupFastThreadLocals] will return `true`.
         *
         * At the moment, [FastThreadLocal] uses normal [ThreadLocal] as the backing storage here, but in
         * the future this may be replaced with scoped values, if semantics can be preserved and performance is good.
         *
         * @param runnable The task to run
         */
        @JvmStatic
        fun runWithFastThreadLocal(runnable: Runnable) {
            val current = currentThread()
            if (current is FastThreadLocalThread) {
                throw IllegalStateException("Caller is a real FastThreadLocalThread")
            }
            val id = current.id
            fallbackThreads.updateAndGet { set ->
                if (set.contains(id)) {
                    throw IllegalStateException("Reentrant call to run()")
                }
                set.add(id)
            }

            try {
                runnable.run()
            } finally {
                fallbackThreads.getAndUpdate { set -> set.remove(id) }
                FastThreadLocal.removeAll()
            }
        }
    }
}
