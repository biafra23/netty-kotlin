/*
 * Copyright 2016 The Netty Project
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

import io.netty.util.internal.logging.InternalLoggerFactory
import java.util.Collections
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Delayed
import java.util.concurrent.ExecutionException
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.RunnableScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

/**
 * [EventExecutor] implementation which makes no guarantees about the ordering of task execution that
 * are submitted because there may be multiple threads executing these tasks.
 * This implementation is most useful for protocols that do not need strict ordering.
 *
 * **Because it provides no ordering, care should be taken when using it!**
 *
 * @deprecated The behavior of this event executor deviates from the typical Netty execution model
 * and can cause subtle issues as a result.
 * Applications that wish to process messages with greater parallelism, should instead do explicit
 * off-loading to their own thread-pools.
 */
@Deprecated("Deviates from typical Netty execution model. Off-load to your own thread pools instead.")
class UnorderedThreadPoolEventExecutor : ScheduledThreadPoolExecutor, EventExecutor {

    private val terminationFuture: Promise<*> = GlobalEventExecutor.INSTANCE.newPromise<Any?>()
    private val executorSet: Set<EventExecutor> = Collections.singleton(this)
    private val eventLoopThreads: MutableSet<Thread> = ConcurrentHashMap.newKeySet()

    /**
     * Calls [UnorderedThreadPoolEventExecutor] constructor
     * using [DefaultThreadFactory].
     */
    constructor(corePoolSize: Int) : this(
        corePoolSize,
        DefaultThreadFactory(UnorderedThreadPoolEventExecutor::class.java) as ThreadFactory
    )

    /**
     * See [ScheduledThreadPoolExecutor] constructor.
     */
    constructor(corePoolSize: Int, threadFactory: ThreadFactory) : super(corePoolSize, threadFactory) {
        setThreadFactory(AccountingThreadFactory(threadFactory, eventLoopThreads))
    }

    /**
     * Calls [UnorderedThreadPoolEventExecutor] constructor using [DefaultThreadFactory].
     */
    constructor(corePoolSize: Int, handler: RejectedExecutionHandler) : this(
        corePoolSize,
        DefaultThreadFactory(UnorderedThreadPoolEventExecutor::class.java) as ThreadFactory,
        handler
    )

    /**
     * See [ScheduledThreadPoolExecutor] constructor.
     */
    constructor(
        corePoolSize: Int, threadFactory: ThreadFactory, handler: RejectedExecutionHandler
    ) : super(corePoolSize, threadFactory, handler) {
        setThreadFactory(AccountingThreadFactory(threadFactory, eventLoopThreads))
    }

    override fun next(): EventExecutor {
        return this
    }

    override fun parent(): EventExecutorGroup {
        return this
    }

    override fun inEventLoop(): Boolean {
        return inEventLoop(Thread.currentThread())
    }

    override fun inEventLoop(thread: Thread): Boolean {
        return eventLoopThreads.contains(thread)
    }

    override fun <V> newPromise(): Promise<V> {
        return DefaultPromise(this)
    }

    override fun <V> newProgressivePromise(): ProgressivePromise<V> {
        return DefaultProgressivePromise(this)
    }

    override fun <V> newSucceededFuture(result: V): Future<V> {
        return SucceededFuture(this, result)
    }

    override fun <V> newFailedFuture(cause: Throwable): Future<V> {
        return FailedFuture(this, cause)
    }

    override fun isShuttingDown(): Boolean {
        return isShutdown
    }

    override fun shutdownNow(): MutableList<Runnable> {
        val tasks = super.shutdownNow()
        @Suppress("UNCHECKED_CAST")
        (terminationFuture as Promise<Any?>).trySuccess(null)
        return tasks
    }

    override fun shutdown() {
        super.shutdown()
        @Suppress("UNCHECKED_CAST")
        (terminationFuture as Promise<Any?>).trySuccess(null)
    }

    override fun shutdownGracefully(): Future<*> {
        return shutdownGracefully(2, 15, TimeUnit.SECONDS)
    }

    override fun shutdownGracefully(quietPeriod: Long, timeout: Long, unit: TimeUnit): Future<*> {
        // TODO: At the moment this just calls shutdown but we may be able to do something more smart here which
        //       respects the quietPeriod and timeout.
        shutdown()
        return terminationFuture()
    }

    override fun terminationFuture(): Future<*> {
        return terminationFuture
    }

    override fun iterator(): MutableIterator<EventExecutor> {
        return executorSet.iterator() as MutableIterator<EventExecutor>
    }

    override fun <V> decorateTask(runnable: Runnable, task: RunnableScheduledFuture<V>): RunnableScheduledFuture<V> {
        return if (runnable is NonNotifyRunnable) {
            task
        } else {
            RunnableScheduledFutureTask(this, task, false)
        }
    }

    override fun <V> decorateTask(callable: Callable<V>, task: RunnableScheduledFuture<V>): RunnableScheduledFuture<V> {
        return RunnableScheduledFutureTask(this, task, true)
    }

    override fun schedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> {
        return super.schedule(command, delay, unit) as ScheduledFuture<*>
    }

    override fun <V> schedule(callable: Callable<V>, delay: Long, unit: TimeUnit): ScheduledFuture<V> {
        return super.schedule(callable, delay, unit) as ScheduledFuture<V>
    }

    override fun scheduleAtFixedRate(
        command: Runnable, initialDelay: Long, period: Long, unit: TimeUnit
    ): ScheduledFuture<*> {
        return super.scheduleAtFixedRate(command, initialDelay, period, unit) as ScheduledFuture<*>
    }

    override fun scheduleWithFixedDelay(
        command: Runnable, initialDelay: Long, delay: Long, unit: TimeUnit
    ): ScheduledFuture<*> {
        return super.scheduleWithFixedDelay(command, initialDelay, delay, unit) as ScheduledFuture<*>
    }

    override fun submit(task: Runnable): Future<*> {
        return super<ScheduledThreadPoolExecutor>.submit(task) as Future<*>
    }

    override fun <T> submit(task: Runnable, result: T): Future<T> {
        return super<ScheduledThreadPoolExecutor>.submit(task, result) as Future<T>
    }

    override fun <T> submit(task: Callable<T>): Future<T> {
        return super<ScheduledThreadPoolExecutor>.submit(task) as Future<T>
    }

    override fun execute(command: Runnable) {
        super.schedule(NonNotifyRunnable(command), 0, TimeUnit.NANOSECONDS)
    }

    private class RunnableScheduledFutureTask<V>(
        executor: EventExecutor,
        private val future: RunnableScheduledFuture<V>,
        private val wasCallable: Boolean
    ) : PromiseTask<V>(executor, future), RunnableScheduledFuture<V>, ScheduledFuture<V> {

        @Throws(Throwable::class)
        override fun runTask(): V? {
            val result = super.runTask()
            if (result == null && wasCallable) {
                // If this RunnableScheduledFutureTask wraps a RunnableScheduledFuture that wraps a Callable we need
                // to ensure that we return the correct result by calling future.get().
                //
                // See https://github.com/netty/netty/issues/11072
                assert(future.isDone)
                try {
                    return future.get()
                } catch (e: ExecutionException) {
                    // unwrap exception.
                    throw e.cause!!
                }
            }
            return result
        }

        override fun run() {
            if (!isPeriodic) {
                super.run()
            } else if (!isDone) {
                try {
                    // Its a periodic task so we need to ignore the return value
                    runTask()
                } catch (cause: Throwable) {
                    if (!tryFailureInternal(cause)) {
                        logger.warn("Failure during execution of task", cause)
                    }
                }
            }
        }

        override fun isPeriodic(): Boolean {
            return future.isPeriodic
        }

        override fun getDelay(unit: TimeUnit): Long {
            return future.getDelay(unit)
        }

        override fun compareTo(other: Delayed): Int {
            return future.compareTo(other)
        }
    }

    // This is a special wrapper which we will be used in execute(...) to wrap the submitted Runnable. This is needed as
    // ScheduledThreadPoolExecutor.execute(...) will delegate to submit(...) which will then use decorateTask(...).
    // The problem with this is that decorateTask(...) needs to ensure we only do our own decoration if we not call
    // from execute(...) as otherwise we may end up creating an endless loop because DefaultPromise will call
    // EventExecutor.execute(...) when notify the listeners of the promise.
    //
    // See https://github.com/netty/netty/issues/6507
    private class NonNotifyRunnable(private val task: Runnable) : Runnable {
        override fun run() {
            task.run()
        }
    }

    private class AccountingThreadFactory(
        private val delegate: ThreadFactory,
        private val threads: MutableSet<Thread>
    ) : ThreadFactory {
        override fun newThread(r: Runnable): Thread {
            return delegate.newThread {
                threads.add(Thread.currentThread())
                try {
                    r.run()
                } finally {
                    threads.remove(Thread.currentThread())
                }
            }
        }
    }

    companion object {
        private val logger = InternalLoggerFactory.getInstance(UnorderedThreadPoolEventExecutor::class.java)
    }
}
