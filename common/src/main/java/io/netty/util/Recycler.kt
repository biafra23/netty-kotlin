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
package io.netty.util

import io.netty.util.concurrent.FastThreadLocal
import io.netty.util.concurrent.FastThreadLocalThread
import io.netty.util.internal.ObjectPool
import io.netty.util.internal.PlatformDependent
import io.netty.util.internal.PlatformDependent.newFixedMpmcQueue
import io.netty.util.internal.PlatformDependent.newMpscQueue
import io.netty.util.internal.SystemPropertyUtil
import io.netty.util.internal.UnstableApi
import io.netty.util.internal.logging.InternalLoggerFactory
import org.jctools.queues.MessagePassingQueue
import org.jetbrains.annotations.VisibleForTesting
import java.util.ArrayDeque
import java.util.Objects
import java.util.Queue
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater
import kotlin.math.max
import kotlin.math.min

/**
 * Light-weight object pool based on a thread-local stack.
 *
 * @param T the type of the pooled object
 */
abstract class Recycler<T> {

    private val localPool: LocalPool<*, T>?
    private val threadLocalPool: FastThreadLocal<LocalPool<*, T>>?

    /**
     * USE IT CAREFULLY!
     *
     * This is creating a shareable [Recycler] which `get()` can be called concurrently from different
     * [Thread]s.
     *
     * Usually [Recycler]s uses some form of thread-local storage, but this constructor is disabling it
     * and using a single pool of instances instead, sized as `maxCapacity`.
     *
     * This is NOT enforcing pooled instances states to be validated if `unguarded = true`:
     * it means that [Handle.recycle] is not checking that `object` is the same which was
     * recycled and assume no other recycling happens concurrently
     * (similar to what [EnhancedHandle.unguardedRecycle] does).
     */
    protected constructor(maxCapacity: Int, unguarded: Boolean) {
        var cap = maxCapacity
        if (cap <= 0) {
            cap = 0
        } else {
            cap = max(4, cap)
        }
        threadLocalPool = null
        @Suppress("UNCHECKED_CAST")
        if (cap == 0) {
            localPool = NOOP_LOCAL_POOL as LocalPool<*, T>
        } else {
            localPool = if (unguarded) UnguardedLocalPool(cap) else GuardedLocalPool(cap)
        }
    }

    /**
     * USE IT CAREFULLY!
     *
     * This is NOT enforcing pooled instances states to be validated if `unguarded = true`:
     * it means that [Handle.recycle] is not checking that `object` is the same which was
     * recycled and assume no other recycling happens concurrently
     * (similar to what [EnhancedHandle.unguardedRecycle] does).
     */
    protected constructor(unguarded: Boolean) : this(
        DEFAULT_MAX_CAPACITY_PER_THREAD, RATIO, DEFAULT_QUEUE_CHUNK_SIZE_PER_THREAD, unguarded
    )

    /**
     * USE IT CAREFULLY!
     *
     * This is NOT enforcing pooled instances states to be validated if `unguarded = true` as stated by
     * [Recycler(Boolean)] and allows to pin the recycler to a specific [Thread], if `owner`
     * is not `null`.
     *
     * Since this method has been introduced for performance-sensitive cases it doesn't validate if [get] is
     * called from the `owner` [Thread]: it assumes [get] to never happen concurrently.
     */
    protected constructor(owner: Thread, unguarded: Boolean) : this(
        DEFAULT_MAX_CAPACITY_PER_THREAD, RATIO, DEFAULT_QUEUE_CHUNK_SIZE_PER_THREAD, owner, unguarded
    )

    protected constructor(maxCapacityPerThread: Int) : this(
        maxCapacityPerThread, RATIO, DEFAULT_QUEUE_CHUNK_SIZE_PER_THREAD
    )

    protected constructor() : this(DEFAULT_MAX_CAPACITY_PER_THREAD)

    /**
     * USE IT CAREFULLY!
     *
     * This is NOT enforcing pooled instances states to be validated if `unguarded = true` as stated by
     * [Recycler(Boolean)], but it allows to tune the chunk size used for local pooling.
     */
    protected constructor(chunksSize: Int, maxCapacityPerThread: Int, unguarded: Boolean) : this(
        maxCapacityPerThread, RATIO, chunksSize, unguarded
    )

    /**
     * USE IT CAREFULLY!
     *
     * This is NOT enforcing pooled instances states to be validated if `unguarded = true` and allows pinning
     * the recycler to a specific [Thread], as stated by [Recycler(Thread, Boolean)].
     * It also allows tuning the chunk size used for local pooling and the max capacity per thread.
     *
     * @throws IllegalArgumentException if `owner` is `null`.
     */
    protected constructor(chunkSize: Int, maxCapacityPerThread: Int, owner: Thread, unguarded: Boolean) : this(
        maxCapacityPerThread, RATIO, chunkSize, owner, unguarded
    )

    /**
     * @deprecated Use one of the following instead:
     * [Recycler()], [Recycler(Int)], [Recycler(Int, Int, Int)].
     */
    @Deprecated("Use Recycler(), Recycler(Int), or Recycler(Int, Int, Int) instead.")
    @Suppress("unused") // Parameters we can't remove due to compatibility.
    protected constructor(maxCapacityPerThread: Int, @Suppress("UNUSED_PARAMETER") maxSharedCapacityFactor: Int) : this(
        maxCapacityPerThread, RATIO, DEFAULT_QUEUE_CHUNK_SIZE_PER_THREAD
    )

    /**
     * @deprecated Use one of the following instead:
     * [Recycler()], [Recycler(Int)], [Recycler(Int, Int, Int)].
     */
    @Deprecated("Use Recycler(), Recycler(Int), or Recycler(Int, Int, Int) instead.")
    @Suppress("unused") // Parameters we can't remove due to compatibility.
    protected constructor(
        maxCapacityPerThread: Int,
        @Suppress("UNUSED_PARAMETER") maxSharedCapacityFactor: Int,
        ratio: Int,
        @Suppress("UNUSED_PARAMETER") maxDelayedQueuesPerThread: Int
    ) : this(maxCapacityPerThread, ratio, DEFAULT_QUEUE_CHUNK_SIZE_PER_THREAD)

    /**
     * @deprecated Use one of the following instead:
     * [Recycler()], [Recycler(Int)], [Recycler(Int, Int, Int)].
     */
    @Deprecated("Use Recycler(), Recycler(Int), or Recycler(Int, Int, Int) instead.")
    @Suppress("unused") // Parameters we can't remove due to compatibility.
    protected constructor(
        maxCapacityPerThread: Int,
        @Suppress("UNUSED_PARAMETER") maxSharedCapacityFactor: Int,
        ratio: Int,
        @Suppress("UNUSED_PARAMETER") maxDelayedQueuesPerThread: Int,
        @Suppress("UNUSED_PARAMETER") delayedQueueRatio: Int
    ) : this(maxCapacityPerThread, ratio, DEFAULT_QUEUE_CHUNK_SIZE_PER_THREAD)

    protected constructor(maxCapacityPerThread: Int, interval: Int, chunkSize: Int) : this(
        maxCapacityPerThread, interval, chunkSize, true, null, false
    )

    /**
     * USE IT CAREFULLY!
     *
     * This is NOT enforcing pooled instances states to be validated if `unguarded = true`
     * as stated by [Recycler(Boolean)].
     */
    protected constructor(maxCapacityPerThread: Int, interval: Int, chunkSize: Int, unguarded: Boolean) : this(
        maxCapacityPerThread, interval, chunkSize, true, null, unguarded
    )

    /**
     * USE IT CAREFULLY!
     *
     * This is NOT enforcing pooled instances states to be validated if `unguarded = true`
     * as stated by [Recycler(Boolean)].
     */
    protected constructor(
        maxCapacityPerThread: Int, interval: Int, chunkSize: Int, owner: Thread, unguarded: Boolean
    ) : this(maxCapacityPerThread, interval, chunkSize, false, owner, unguarded)

    @Suppress("UNCHECKED_CAST")
    private constructor(
        maxCapacityPerThread: Int, ratio: Int, chunkSize: Int,
        useThreadLocalStorage: Boolean, owner: Thread?, unguarded: Boolean
    ) {
        val interval = max(0, ratio)
        var cap = maxCapacityPerThread
        var chunk = chunkSize
        if (cap <= 0) {
            cap = 0
            chunk = 0
        } else {
            cap = max(4, cap)
            chunk = max(2, min(chunk, cap shr 1))
        }
        if (cap > 0 && useThreadLocalStorage) {
            val finalMaxCapacityPerThread = cap
            val finalChunkSize = chunk
            threadLocalPool = object : FastThreadLocal<LocalPool<*, T>>() {
                override fun initialValue(): LocalPool<*, T> {
                    return if (unguarded) {
                        UnguardedLocalPool(finalMaxCapacityPerThread, interval, finalChunkSize)
                    } else {
                        GuardedLocalPool(finalMaxCapacityPerThread, interval, finalChunkSize)
                    }
                }

                override fun onRemoval(value: LocalPool<*, T>) {
                    super.onRemoval(value)
                    val handles = value.pooledHandles
                    value.pooledHandles = null
                    value.owner = null
                    handles?.clear()
                }
            }
            localPool = null
        } else {
            threadLocalPool = null
            if (cap == 0) {
                localPool = NOOP_LOCAL_POOL as LocalPool<*, T>
            } else {
                Objects.requireNonNull(owner, "owner")
                localPool = if (unguarded) {
                    UnguardedLocalPool(owner!!, cap, interval, chunk)
                } else {
                    GuardedLocalPool(owner!!, cap, interval, chunk)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun get(): T {
        if (localPool != null) {
            return localPool.getWith(this)
        } else {
            if (!FastThreadLocalThread.currentThreadWillCleanupFastThreadLocals()) {
                return newObject(NOOP_HANDLE as Handle<T>)
            }
            return threadLocalPool!!.get().getWith(this)
        }
    }

    /**
     * @deprecated use [Handle.recycle].
     */
    @Deprecated("Use Handle.recycle(Object) instead.")
    fun recycle(o: T, handle: Handle<T>): Boolean {
        if (handle === NOOP_HANDLE) {
            return false
        }
        handle.recycle(o)
        return true
    }

    @VisibleForTesting
    fun threadLocalSize(): Int {
        if (localPool != null) {
            return localPool.size()
        } else {
            if (!FastThreadLocalThread.currentThreadWillCleanupFastThreadLocals()) {
                return 0
            }
            val pool = threadLocalPool!!.getIfExists() ?: return 0
            return pool.size()
        }
    }

    /**
     * @param handle can NOT be null.
     */
    protected abstract fun newObject(handle: Handle<T>): T

    @Suppress("ClassNameSameAsAncestorName") // Can't change this due to compatibility.
    interface Handle<T> : ObjectPool.Handle<T>

    @UnstableApi
    abstract class EnhancedHandle<T> internal constructor() : Handle<T> {
        abstract fun unguardedRecycle(`object`: Any)
    }

    /**
     * We created this handle to avoid having more than 2 concrete implementations of [EnhancedHandle]
     * i.e. NOOP_HANDLE, [DefaultHandle] and the one used in the LocalPool.
     */
    private class LocalPoolHandle<T>(private val pool: UnguardedLocalPool<T>?) : EnhancedHandle<T>() {

        override fun recycle(`object`: T) {
            pool?.release(`object`)
        }

        @Suppress("UNCHECKED_CAST")
        override fun unguardedRecycle(`object`: Any) {
            pool?.release(`object` as T)
        }
    }

    private class DefaultHandle<T>(private val localPool: GuardedLocalPool<T>) : EnhancedHandle<T>() {

        @Volatile
        private var state: Int = 0 // State is initialised to STATE_CLAIMED (aka. 0) so they can be released.
        private var value: T? = null

        override fun recycle(`object`: T) {
            if (`object` !== value) {
                throw IllegalArgumentException("object does not belong to handle")
            }
            toAvailable()
            localPool.release(this)
        }

        override fun unguardedRecycle(`object`: Any) {
            if (`object` !== value) {
                throw IllegalArgumentException("object does not belong to handle")
            }
            unguardedToAvailable()
            localPool.release(this)
        }

        fun claim(): T {
            assert(state == STATE_AVAILABLE)
            STATE_UPDATER.lazySet(this, STATE_CLAIMED)
            @Suppress("UNCHECKED_CAST")
            return value as T
        }

        fun set(value: T) {
            this.value = value
        }

        private fun toAvailable() {
            val prev = STATE_UPDATER.getAndSet(this, STATE_AVAILABLE)
            if (prev == STATE_AVAILABLE) {
                throw IllegalStateException("Object has been recycled already.")
            }
        }

        private fun unguardedToAvailable() {
            val prev = state
            if (prev == STATE_AVAILABLE) {
                throw IllegalStateException("Object has been recycled already.")
            }
            STATE_UPDATER.lazySet(this, STATE_AVAILABLE)
        }

        companion object {
            private const val STATE_CLAIMED = 0
            private const val STATE_AVAILABLE = 1

            @Suppress("UNCHECKED_CAST")
            private val STATE_UPDATER: AtomicIntegerFieldUpdater<DefaultHandle<*>> =
                AtomicIntegerFieldUpdater.newUpdater(DefaultHandle::class.java, "state")
                    as AtomicIntegerFieldUpdater<DefaultHandle<*>>
        }
    }

    private class GuardedLocalPool<T> : LocalPool<DefaultHandle<T>, T> {

        constructor(maxCapacity: Int) : super(maxCapacity)

        constructor(owner: Thread, maxCapacity: Int, ratioInterval: Int, chunkSize: Int) :
            super(owner, maxCapacity, ratioInterval, chunkSize)

        constructor(maxCapacity: Int, ratioInterval: Int, chunkSize: Int) :
            super(maxCapacity, ratioInterval, chunkSize)

        @Suppress("UNCHECKED_CAST")
        override fun getWith(recycler: Recycler<T>): T {
            var handle = acquire()
            val obj: T
            if (handle == null) {
                handle = if (canAllocatePooled()) DefaultHandle(this) else null
                if (handle != null) {
                    obj = recycler.newObject(handle)
                    handle.set(obj)
                } else {
                    obj = recycler.newObject(NOOP_HANDLE as Handle<T>)
                }
            } else {
                obj = handle.claim()
            }
            return obj
        }
    }

    private class UnguardedLocalPool<T> : LocalPool<T, T> {
        private val handle: EnhancedHandle<T>?

        constructor(maxCapacity: Int) : super(maxCapacity) {
            handle = if (maxCapacity == 0) null else LocalPoolHandle(this)
        }

        constructor(owner: Thread, maxCapacity: Int, ratioInterval: Int, chunkSize: Int) :
            super(owner, maxCapacity, ratioInterval, chunkSize) {
            handle = LocalPoolHandle(this)
        }

        constructor(maxCapacity: Int, ratioInterval: Int, chunkSize: Int) :
            super(maxCapacity, ratioInterval, chunkSize) {
            handle = LocalPoolHandle(this)
        }

        @Suppress("UNCHECKED_CAST")
        override fun getWith(recycler: Recycler<T>): T {
            val obj = acquire()
            if (obj == null) {
                return recycler.newObject(
                    if (canAllocatePooled()) handle!! else NOOP_HANDLE as Handle<T>
                )
            }
            return obj!!
        }
    }

    private abstract class LocalPool<H, T> {
        private val ratioInterval: Int
        private val batch: Array<Any?>?
        private var batchSize: Int
        internal var owner: Thread?
        internal var pooledHandles: MessagePassingQueue<H>?
        private var ratioCounter: Int

        constructor(maxCapacity: Int) {
            // if there's no capacity, we need to never allocate pooled objects.
            // if there's capacity, because there is a shared pool, we always pool them, since we cannot trust the
            // thread unsafe ratio counter.
            this.ratioInterval = if (maxCapacity == 0) -1 else 0
            this.owner = null
            batch = null
            batchSize = 0
            pooledHandles = createExternalMcPool(maxCapacity)
            ratioCounter = 0
        }

        constructor(owner: Thread?, maxCapacity: Int, ratioInterval: Int, chunkSize: Int) {
            this.ratioInterval = ratioInterval
            this.owner = owner
            batch = if (owner != null) arrayOfNulls(chunkSize) else null
            batchSize = 0
            pooledHandles = createExternalScPool(chunkSize, maxCapacity)
            ratioCounter = ratioInterval // Start at interval so the first one will be recycled.
        }

        constructor(maxCapacity: Int, ratioInterval: Int, chunkSize: Int) : this(
            if (!BATCH_FAST_TL_ONLY || FastThreadLocalThread.currentThreadWillCleanupFastThreadLocals()) {
                Thread.currentThread()
            } else {
                null
            },
            maxCapacity, ratioInterval, chunkSize
        )

        @Suppress("UNCHECKED_CAST")
        protected fun acquire(): H? {
            val size = batchSize
            if (size == 0) {
                // it's ok to be racy; at worst we reuse something that won't return back to the pool
                val handles = pooledHandles ?: return null
                return handles.relaxedPoll()
            }
            val top = size - 1
            val h = batch!![top] as H
            batchSize = top
            batch[top] = null
            return h
        }

        @Suppress("UNCHECKED_CAST")
        fun release(handle: H) {
            val owner = this.owner
            if (owner != null && Thread.currentThread() === owner && batchSize < batch!!.size) {
                (batch as Array<Any?>)[batchSize] = handle
                batchSize++
            } else if (owner != null && isTerminated(owner)) {
                pooledHandles = null
                this.owner = null
            } else {
                val handles = pooledHandles
                handles?.relaxedOffer(handle)
            }
        }

        fun canAllocatePooled(): Boolean {
            if (ratioInterval < 0) {
                return false
            }
            if (ratioInterval == 0) {
                return true
            }
            if (++ratioCounter >= ratioInterval) {
                ratioCounter = 0
                return true
            }
            return false
        }

        abstract fun getWith(recycler: Recycler<T>): T

        fun size(): Int {
            val handles = pooledHandles
            val externalSize = handles?.size() ?: 0
            return externalSize + if (batch != null) batchSize else 0
        }

        companion object {
            @Suppress("UNCHECKED_CAST")
            private fun <H> createExternalMcPool(maxCapacity: Int): MessagePassingQueue<H>? {
                if (maxCapacity == 0) {
                    return null
                }
                if (BLOCKING_POOL) {
                    return BlockingMessageQueue<H>(maxCapacity)
                }
                @Suppress("UNCHECKED_CAST")
                return newFixedMpmcQueue<Any>(maxCapacity) as MessagePassingQueue<H>
            }

            @Suppress("UNCHECKED_CAST")
            private fun <H> createExternalScPool(chunkSize: Int, maxCapacity: Int): MessagePassingQueue<H>? {
                if (maxCapacity == 0) {
                    return null
                }
                if (BLOCKING_POOL) {
                    return BlockingMessageQueue<H>(maxCapacity)
                }
                @Suppress("UNCHECKED_CAST")
                return newMpscQueue<Any>(chunkSize, maxCapacity) as MessagePassingQueue<H>
            }

            private fun isTerminated(owner: Thread): Boolean {
                // Do not use `Thread.getState()` in J9 JVM because it's known to have a performance issue.
                // See: https://github.com/netty/netty/issues/13347#issuecomment-1518537895
                return if (PlatformDependent.isJ9Jvm()) !owner.isAlive else owner.state == Thread.State.TERMINATED
            }
        }
    }

    /**
     * This is an implementation of [MessagePassingQueue], similar to what might be returned from
     * [PlatformDependent.newMpscQueue], but intended to be used for debugging purpose.
     * The implementation relies on synchronised monitor locks for thread-safety.
     * The `fill` bulk operation is not supported by this implementation.
     */
    private class BlockingMessageQueue<T>(private val maxCapacity: Int) : MessagePassingQueue<T> {
        private val deque: Queue<T> = ArrayDeque()

        @Synchronized
        override fun offer(e: T): Boolean {
            if (deque.size == maxCapacity) {
                return false
            }
            return deque.offer(e)
        }

        @Synchronized
        override fun poll(): T? = deque.poll()

        @Synchronized
        override fun peek(): T? = deque.peek()

        @Synchronized
        override fun size(): Int = deque.size

        @Synchronized
        override fun clear() = deque.clear()

        @Synchronized
        override fun isEmpty(): Boolean = deque.isEmpty()

        override fun capacity(): Int = maxCapacity

        override fun relaxedOffer(e: T): Boolean = offer(e)

        override fun relaxedPoll(): T? = poll()

        override fun relaxedPeek(): T? = peek()

        override fun drain(c: MessagePassingQueue.Consumer<T>, limit: Int): Int {
            var obj: T?
            var i = 0
            while (i < limit) {
                obj = poll()
                if (obj == null) break
                c.accept(obj)
                i++
            }
            return i
        }

        override fun fill(s: MessagePassingQueue.Supplier<T>, limit: Int): Int {
            throw UnsupportedOperationException()
        }

        override fun drain(c: MessagePassingQueue.Consumer<T>): Int {
            throw UnsupportedOperationException()
        }

        override fun fill(s: MessagePassingQueue.Supplier<T>): Int {
            throw UnsupportedOperationException()
        }

        override fun drain(c: MessagePassingQueue.Consumer<T>, wait: MessagePassingQueue.WaitStrategy, exit: MessagePassingQueue.ExitCondition) {
            throw UnsupportedOperationException()
        }

        override fun fill(s: MessagePassingQueue.Supplier<T>, wait: MessagePassingQueue.WaitStrategy, exit: MessagePassingQueue.ExitCondition) {
            throw UnsupportedOperationException()
        }
    }

    companion object {
        private val logger = InternalLoggerFactory.getInstance(Recycler::class.java)

        private val NOOP_HANDLE: EnhancedHandle<*> = LocalPoolHandle<Any>(null)
        private val NOOP_LOCAL_POOL: UnguardedLocalPool<*> = UnguardedLocalPool<Any>(0)
        private val DEFAULT_INITIAL_MAX_CAPACITY_PER_THREAD = 4 * 1024 // Use 4k instances as default.
        @JvmStatic
        private val DEFAULT_MAX_CAPACITY_PER_THREAD: Int
        private val RATIO: Int
        private val DEFAULT_QUEUE_CHUNK_SIZE_PER_THREAD: Int
        private val BLOCKING_POOL: Boolean
        private val BATCH_FAST_TL_ONLY: Boolean

        init {
            // In the future, we might have different maxCapacity for different object types.
            // e.g. io.netty.recycler.maxCapacity.writeTask
            //      io.netty.recycler.maxCapacity.outboundBuffer
            var maxCapacityPerThread = SystemPropertyUtil.getInt(
                "io.netty.recycler.maxCapacityPerThread",
                SystemPropertyUtil.getInt("io.netty.recycler.maxCapacity", DEFAULT_INITIAL_MAX_CAPACITY_PER_THREAD)
            )
            if (maxCapacityPerThread < 0) {
                maxCapacityPerThread = DEFAULT_INITIAL_MAX_CAPACITY_PER_THREAD
            }

            DEFAULT_MAX_CAPACITY_PER_THREAD = maxCapacityPerThread
            DEFAULT_QUEUE_CHUNK_SIZE_PER_THREAD = SystemPropertyUtil.getInt("io.netty.recycler.chunkSize", 32)

            // By default, we allow one push to a Recycler for each 8th try on handles that were never recycled before.
            // This should help to slowly increase the capacity of the recycler while not be too sensitive to allocation
            // bursts.
            RATIO = max(0, SystemPropertyUtil.getInt("io.netty.recycler.ratio", 8))

            BLOCKING_POOL = SystemPropertyUtil.getBoolean("io.netty.recycler.blocking", false)
            BATCH_FAST_TL_ONLY = SystemPropertyUtil.getBoolean("io.netty.recycler.batchFastThreadLocalOnly", true)

            if (logger.isDebugEnabled()) {
                if (DEFAULT_MAX_CAPACITY_PER_THREAD == 0) {
                    logger.debug("-Dio.netty.recycler.maxCapacityPerThread: disabled")
                    logger.debug("-Dio.netty.recycler.ratio: disabled")
                    logger.debug("-Dio.netty.recycler.chunkSize: disabled")
                    logger.debug("-Dio.netty.recycler.blocking: disabled")
                    logger.debug("-Dio.netty.recycler.batchFastThreadLocalOnly: disabled")
                } else {
                    logger.debug("-Dio.netty.recycler.maxCapacityPerThread: {}", DEFAULT_MAX_CAPACITY_PER_THREAD)
                    logger.debug("-Dio.netty.recycler.ratio: {}", RATIO)
                    logger.debug("-Dio.netty.recycler.chunkSize: {}", DEFAULT_QUEUE_CHUNK_SIZE_PER_THREAD)
                    logger.debug("-Dio.netty.recycler.blocking: {}", BLOCKING_POOL)
                    logger.debug("-Dio.netty.recycler.batchFastThreadLocalOnly: {}", BATCH_FAST_TL_ONLY)
                }
            }
        }

        /**
         * Disassociates the [Recycler] from the current [Thread] if it was pinned,
         * see [Recycler(Thread, Boolean)].
         *
         * Be aware that this method is not thread-safe: it's necessary to allow a [Thread] to
         * be garbage collected even if [Handle]s are still referenced by other objects.
         */
        @JvmStatic
        fun unpinOwner(recycler: Recycler<*>) {
            if (recycler.localPool != null) {
                recycler.localPool.owner = null
            }
        }
    }
}
