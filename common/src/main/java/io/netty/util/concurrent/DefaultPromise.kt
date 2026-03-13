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

import io.netty.util.internal.InternalThreadLocalMap
import io.netty.util.internal.ObjectUtil.checkNotNull
import io.netty.util.internal.PlatformDependent
import io.netty.util.internal.StringUtil
import io.netty.util.internal.SystemPropertyUtil
import io.netty.util.internal.ThrowableUtil
import io.netty.util.internal.logging.InternalLoggerFactory
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater

open class DefaultPromise<V> : AbstractFuture<V>, Promise<V> {

    @Volatile
    @JvmField
    var result: Any? = null

    private val executor: EventExecutor?

    /**
     * One or more listeners. Can be a [GenericFutureListener] or a [DefaultFutureListeners].
     * If `null`, it means either 1) no listeners were added yet or 2) all listeners were notified.
     *
     * Threading - synchronized(this). We must support adding listeners when there is no EventExecutor.
     */
    private var listener: GenericFutureListener<out Future<*>>? = null
    private var listeners: DefaultFutureListeners? = null

    /**
     * Threading - synchronized(this). We are required to hold the monitor to use Java's underlying wait()/notifyAll().
     */
    private var waiters: Short = 0

    /**
     * Threading - synchronized(this). We must prevent concurrent notification and FIFO listener notification if the
     * executor changes.
     */
    private var notifyingListeners: Boolean = false

    /**
     * Creates a new instance.
     *
     * It is preferable to use [EventExecutor.newPromise] to create a new promise
     *
     * @param executor
     *        the [EventExecutor] which is used to notify the promise once it is complete.
     *        It is assumed this executor will protect against [StackOverflowError] exceptions.
     *        The executor may be used to avoid [StackOverflowError] by executing a [Runnable] if the stack
     *        depth exceeds a threshold.
     */
    constructor(executor: EventExecutor) {
        this.executor = checkNotNull(executor, "executor")
    }

    /**
     * See [executor] for expectations of the executor.
     */
    protected constructor() {
        // only for subclasses
        executor = null
    }

    @Suppress("UNCHECKED_CAST")
    override fun setSuccess(result: V): Promise<V> {
        if (setSuccess0(result)) {
            return this
        }
        throw IllegalStateException("complete already: $this")
    }

    @Suppress("UNCHECKED_CAST")
    override fun trySuccess(result: V): Boolean {
        return setSuccess0(result)
    }

    override fun setFailure(cause: Throwable): Promise<V> {
        if (setFailure0(cause)) {
            return this
        }
        throw IllegalStateException("complete already: $this", cause)
    }

    override fun tryFailure(cause: Throwable): Boolean {
        return setFailure0(cause)
    }

    override fun setUncancellable(): Boolean {
        if (RESULT_UPDATER.compareAndSet(this, null, UNCANCELLABLE)) {
            return true
        }
        val result = this.result
        return !isDone0(result) || !isCancelled0(result)
    }

    override fun isSuccess(): Boolean {
        val result = this.result
        return result != null && result !== UNCANCELLABLE && result !is CauseHolder
    }

    override fun isCancellable(): Boolean {
        return result == null
    }

    private class LeanCancellationException : CancellationException() {
        companion object {
            private const val serialVersionUID: Long = 2794674970981187807L
        }

        // Suppress a warning since the method doesn't need synchronization
        override fun fillInStackTrace(): Throwable {
            stackTrace = CANCELLATION_STACK
            return this
        }

        override fun toString(): String {
            return CancellationException::class.java.name
        }
    }

    override fun cause(): Throwable? {
        return cause0(result)
    }

    private fun cause0(result: Any?): Throwable? {
        if (result !is CauseHolder) {
            return null
        }
        if (result === CANCELLATION_CAUSE_HOLDER) {
            val ce = LeanCancellationException()
            if (RESULT_UPDATER.compareAndSet(this, CANCELLATION_CAUSE_HOLDER, CauseHolder(ce))) {
                return ce
            }
            @Suppress("NAME_SHADOWING")
            val result = this.result
            return (result as CauseHolder).cause
        }
        return result.cause
    }

    override fun addListener(listener: GenericFutureListener<out Future<in V>>): Promise<V> {
        checkNotNull(listener, "listener")

        synchronized(this) {
            addListener0(listener)
        }

        if (isDone) {
            notifyListeners()
        }

        return this
    }

    @Suppress("UNCHECKED_CAST")
    override fun addListeners(vararg listeners: GenericFutureListener<out Future<in V>>): Promise<V> {
        checkNotNull(listeners, "listeners")

        synchronized(this) {
            for (listener in listeners) {
                if (listener == null) {
                    break
                }
                addListener0(listener)
            }
        }

        if (isDone) {
            notifyListeners()
        }

        return this
    }

    override fun removeListener(listener: GenericFutureListener<out Future<in V>>): Promise<V> {
        checkNotNull(listener, "listener")

        synchronized(this) {
            removeListener0(listener)
        }

        return this
    }

    @Suppress("UNCHECKED_CAST")
    override fun removeListeners(vararg listeners: GenericFutureListener<out Future<in V>>): Promise<V> {
        checkNotNull(listeners, "listeners")

        synchronized(this) {
            for (listener in listeners) {
                if (listener == null) {
                    break
                }
                removeListener0(listener)
            }
        }

        return this
    }

    @Throws(InterruptedException::class)
    override fun await(): Promise<V> {
        if (isDone) {
            return this
        }

        if (Thread.interrupted()) {
            throw InterruptedException(toString())
        }

        checkDeadLock()

        synchronized(this) {
            while (!isDone) {
                incWaiters()
                try {
                    (this as Object).wait()
                } finally {
                    decWaiters()
                }
            }
        }
        return this
    }

    override fun awaitUninterruptibly(): Promise<V> {
        if (isDone) {
            return this
        }

        checkDeadLock()

        var interrupted = false
        synchronized(this) {
            while (!isDone) {
                incWaiters()
                try {
                    (this as Object).wait()
                } catch (e: InterruptedException) {
                    // Interrupted while waiting.
                    interrupted = true
                } finally {
                    decWaiters()
                }
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt()
        }

        return this
    }

    @Throws(InterruptedException::class)
    override fun await(timeout: Long, unit: TimeUnit): Boolean {
        return await0(unit.toNanos(timeout), true)
    }

    @Throws(InterruptedException::class)
    override fun await(timeoutMillis: Long): Boolean {
        return await0(TimeUnit.MILLISECONDS.toNanos(timeoutMillis), true)
    }

    override fun awaitUninterruptibly(timeout: Long, unit: TimeUnit): Boolean {
        try {
            return await0(unit.toNanos(timeout), false)
        } catch (e: InterruptedException) {
            // Should not be raised at all.
            throw InternalError()
        }
    }

    override fun awaitUninterruptibly(timeoutMillis: Long): Boolean {
        try {
            return await0(TimeUnit.MILLISECONDS.toNanos(timeoutMillis), false)
        } catch (e: InterruptedException) {
            // Should not be raised at all.
            throw InternalError()
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun getNow(): V? {
        val result = this.result
        return if (result is CauseHolder || result === SUCCESS || result === UNCANCELLABLE) {
            null
        } else {
            result as V?
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(InterruptedException::class, ExecutionException::class)
    override fun get(): V? {
        var result = this.result
        if (!isDone0(result)) {
            await()
            result = this.result
        }
        if (result === SUCCESS || result === UNCANCELLABLE) {
            return null
        }
        val cause = cause0(result)
        if (cause == null) {
            return result as V?
        }
        if (cause is CancellationException) {
            throw cause
        }
        throw ExecutionException(cause)
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
    override fun get(timeout: Long, unit: TimeUnit): V? {
        var result = this.result
        if (!isDone0(result)) {
            if (!await(timeout, unit)) {
                throw TimeoutException("timeout after $timeout ${unit.name.lowercase(Locale.ENGLISH)}")
            }
            result = this.result
        }
        if (result === SUCCESS || result === UNCANCELLABLE) {
            return null
        }
        val cause = cause0(result)
        if (cause == null) {
            return result as V?
        }
        if (cause is CancellationException) {
            throw cause
        }
        throw ExecutionException(cause)
    }

    /**
     * @param mayInterruptIfRunning this value has no effect in this implementation.
     */
    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        if (RESULT_UPDATER.compareAndSet(this, null, CANCELLATION_CAUSE_HOLDER)) {
            if (checkNotifyWaiters()) {
                notifyListeners()
            }
            return true
        }
        return false
    }

    override fun isCancelled(): Boolean {
        return isCancelled0(result)
    }

    override fun isDone(): Boolean {
        return isDone0(result)
    }

    @Throws(InterruptedException::class)
    override fun sync(): Promise<V> {
        await()
        rethrowIfFailed()
        return this
    }

    override fun syncUninterruptibly(): Promise<V> {
        awaitUninterruptibly()
        rethrowIfFailed()
        return this
    }

    override fun toString(): String {
        return toStringBuilder().toString()
    }

    protected open fun toStringBuilder(): StringBuilder {
        val buf = StringBuilder(64)
            .append(StringUtil.simpleClassName(this))
            .append('@')
            .append(Integer.toHexString(hashCode()))

        val result = this.result
        when {
            result === SUCCESS -> buf.append("(success)")
            result === UNCANCELLABLE -> buf.append("(uncancellable)")
            result is CauseHolder -> buf.append("(failure: ").append(result.cause).append(')')
            result != null -> buf.append("(success: ").append(result).append(')')
            else -> buf.append("(incomplete)")
        }

        return buf
    }

    /**
     * Get the executor used to notify listeners when this promise is complete.
     *
     * It is assumed this executor will protect against [StackOverflowError] exceptions.
     * The executor may be used to avoid [StackOverflowError] by executing a [Runnable] if the stack
     * depth exceeds a threshold.
     * @return The executor used to notify listeners when this promise is complete.
     */
    protected open fun executor(): EventExecutor? {
        return executor
    }

    protected open fun checkDeadLock() {
        val e = executor()
        if (e != null && e.inEventLoop()) {
            throw BlockingOperationException(toString())
        }
    }

    private fun notifyListeners() {
        val executor = executor()!!
        if (executor.inEventLoop()) {
            val threadLocals = InternalThreadLocalMap.get()
            val stackDepth = threadLocals.futureListenerStackDepth()
            if (stackDepth < MAX_LISTENER_STACK_DEPTH) {
                threadLocals.setFutureListenerStackDepth(stackDepth + 1)
                try {
                    notifyListenersNow()
                } finally {
                    threadLocals.setFutureListenerStackDepth(stackDepth)
                }
                return
            }
        }

        safeExecute(executor, Runnable { notifyListenersNow() })
    }

    private fun notifyListenersNow() {
        var listener: GenericFutureListener<*>?
        var listeners: DefaultFutureListeners?
        synchronized(this) {
            listener = this.listener
            listeners = this.listeners
            // Only proceed if there are listeners to notify and we are not already notifying listeners.
            if (notifyingListeners || (listener == null && listeners == null)) {
                return
            }
            notifyingListeners = true
            if (listener != null) {
                this.listener = null
            } else {
                this.listeners = null
            }
        }
        while (true) {
            if (listener != null) {
                notifyListener0(this, listener!!)
            } else {
                notifyListeners0(listeners!!)
            }
            synchronized(this) {
                if (this.listener == null && this.listeners == null) {
                    // Nothing can throw from within this method, so setting notifyingListeners back to false does not
                    // need to be in a finally block.
                    notifyingListeners = false
                    return
                }
                listener = this.listener
                listeners = this.listeners
                if (listener != null) {
                    this.listener = null
                } else {
                    this.listeners = null
                }
            }
        }
    }

    private fun notifyListeners0(listeners: DefaultFutureListeners) {
        val a = listeners.listeners()
        val size = listeners.size()
        for (i in 0 until size) {
            @Suppress("UNCHECKED_CAST")
            notifyListener0(this, a[i] as GenericFutureListener<*>)
        }
    }

    private fun addListener0(listener: GenericFutureListener<out Future<in V>>) {
        if (this.listener == null) {
            if (listeners == null) {
                this.listener = listener
            } else {
                listeners!!.add(listener)
            }
        } else {
            assert(listeners == null)
            val currentListener = this.listener
            @Suppress("UNCHECKED_CAST")
            listeners = DefaultFutureListeners(currentListener as GenericFutureListener<out Future<*>>, listener as GenericFutureListener<out Future<*>>)
            this.listener = null
        }
    }

    private fun removeListener0(toRemove: GenericFutureListener<out Future<in V>>) {
        if (listener === toRemove) {
            listener = null
        } else if (listeners != null) {
            listeners!!.remove(toRemove)
            // Removal is rare, no need for compaction
            if (listeners!!.size() == 0) {
                listeners = null
            }
        }
    }

    private fun setSuccess0(result: V?): Boolean {
        return setValue0(result ?: SUCCESS)
    }

    private fun setFailure0(cause: Throwable): Boolean {
        return setValue0(CauseHolder(checkNotNull(cause, "cause")))
    }

    private fun setValue0(objResult: Any): Boolean {
        if (RESULT_UPDATER.compareAndSet(this, null, objResult) ||
            RESULT_UPDATER.compareAndSet(this, UNCANCELLABLE, objResult)) {
            if (checkNotifyWaiters()) {
                notifyListeners()
            }
            return true
        }
        return false
    }

    /**
     * Check if there are any waiters and if so notify these.
     * @return `true` if there are any listeners attached to the promise, `false` otherwise.
     */
    @Synchronized
    private fun checkNotifyWaiters(): Boolean {
        if (waiters > 0) {
            (this as Object).notifyAll()
        }
        return listener != null || listeners != null
    }

    private fun incWaiters() {
        if (waiters == Short.MAX_VALUE) {
            throw IllegalStateException("too many waiters: $this")
        }
        ++waiters
    }

    private fun decWaiters() {
        --waiters
    }

    private fun rethrowIfFailed() {
        val cause = cause() ?: return

        if (cause !is CancellationException && cause.suppressed.isEmpty()) {
            cause.addSuppressed(CompletionException("Rethrowing promise failure cause", null))
        }
        PlatformDependent.throwException(cause)
    }

    @Throws(InterruptedException::class)
    private fun await0(timeoutNanos: Long, interruptable: Boolean): Boolean {
        if (isDone) {
            return true
        }

        if (timeoutNanos <= 0) {
            return isDone
        }

        if (interruptable && Thread.interrupted()) {
            throw InterruptedException(toString())
        }

        checkDeadLock()

        // Start counting time from here instead of the first line of this method,
        // to avoid/postpone performance cost of System.nanoTime().
        val startTime = System.nanoTime()
        synchronized(this) {
            var interrupted = false
            try {
                var waitTime = timeoutNanos
                while (!isDone && waitTime > 0) {
                    incWaiters()
                    try {
                        (this as Object).wait(waitTime / 1000000, (waitTime % 1000000).toInt())
                    } catch (e: InterruptedException) {
                        if (interruptable) {
                            throw e
                        } else {
                            interrupted = true
                        }
                    } finally {
                        decWaiters()
                    }
                    // Check isDone() in advance, try to avoid calculating the elapsed time later.
                    if (isDone) {
                        return true
                    }
                    // Calculate the elapsed time here instead of in the while condition,
                    // try to avoid performance cost of System.nanoTime() in the first loop of while.
                    waitTime = timeoutNanos - (System.nanoTime() - startTime)
                }
                return isDone
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }

    /**
     * Notify all progressive listeners.
     *
     * No attempt is made to ensure notification order if multiple calls are made to this method before
     * the original invocation completes.
     *
     * This will do an iteration over all listeners to get all of type [GenericProgressiveFutureListener]s.
     * @param progress the new progress.
     * @param total the total progress.
     */
    @Suppress("UNCHECKED_CAST")
    internal fun notifyProgressiveListeners(progress: Long, total: Long) {
        val listeners = progressiveListeners() ?: return

        val self = this as ProgressiveFuture<V>

        val executor = executor()!!
        if (executor.inEventLoop()) {
            if (listeners is Array<*>) {
                notifyProgressiveListeners0(
                    self, listeners as Array<GenericProgressiveFutureListener<*>>, progress, total
                )
            } else {
                notifyProgressiveListener0(
                    self, listeners as GenericProgressiveFutureListener<ProgressiveFuture<V>>, progress, total
                )
            }
        } else {
            if (listeners is Array<*>) {
                val array = listeners as Array<GenericProgressiveFutureListener<*>>
                safeExecute(executor, Runnable {
                    notifyProgressiveListeners0(self, array, progress, total)
                })
            } else {
                val l = listeners as GenericProgressiveFutureListener<ProgressiveFuture<V>>
                safeExecute(executor, Runnable {
                    notifyProgressiveListener0(self, l, progress, total)
                })
            }
        }
    }

    /**
     * Returns a [GenericProgressiveFutureListener], an array of [GenericProgressiveFutureListener], or
     * `null`.
     */
    @Synchronized
    private fun progressiveListeners(): Any? {
        val listener = this.listener
        val listeners = this.listeners
        if (listener == null && listeners == null) {
            // No listeners added
            return null
        }

        if (listeners != null) {
            // Copy DefaultFutureListeners into an array of listeners.
            val dfl = listeners
            val progressiveSize = dfl.progressiveSize()
            when (progressiveSize) {
                0 -> return null
                1 -> {
                    for (l in dfl.listeners()) {
                        if (l is GenericProgressiveFutureListener<*>) {
                            return l
                        }
                    }
                    return null
                }
            }

            val array = dfl.listeners()
            val copy = arrayOfNulls<GenericProgressiveFutureListener<*>>(progressiveSize)
            var j = 0
            var i = 0
            while (j < progressiveSize) {
                val l = array[i]
                if (l is GenericProgressiveFutureListener<*>) {
                    copy[j++] = l
                }
                i++
            }

            return copy
        } else if (listener is GenericProgressiveFutureListener<*>) {
            return listener
        } else {
            // Only one listener was added and it's not a progressive listener.
            return null
        }
    }

    private class CauseHolder(val cause: Throwable)

    private class StacklessCancellationException private constructor() : CancellationException() {

        // Override fillInStackTrace() so we not populate the backtrace via a native call and so leak the
        // Classloader.
        override fun fillInStackTrace(): Throwable {
            return this
        }

        companion object {
            private const val serialVersionUID: Long = -2974906711413716191L

            fun newInstance(clazz: Class<*>, method: String): StacklessCancellationException {
                return ThrowableUtil.unknownStackTrace(StacklessCancellationException(), clazz, method)
            }
        }
    }

    companion object {
        /**
         * System property with integer type value, that determine the max reentrancy/recursion level for when
         * listener notifications prompt other listeners to be notified.
         *
         * When the reentrancy/recursion level becomes greater than this number, a new task will instead be scheduled
         * on the event loop, to finish notifying any subsequent listners.
         *
         * The default value is `8`.
         */
        @JvmField
        val PROPERTY_MAX_LISTENER_STACK_DEPTH: String = "io.netty.defaultPromise.maxListenerStackDepth"

        private val logger = InternalLoggerFactory.getInstance(DefaultPromise::class.java)
        private val rejectedExecutionLogger =
            InternalLoggerFactory.getInstance(DefaultPromise::class.java.name + ".rejectedExecution")
        private val MAX_LISTENER_STACK_DEPTH = Math.min(
            8,
            SystemPropertyUtil.getInt(PROPERTY_MAX_LISTENER_STACK_DEPTH, 8)
        )

        @Suppress("rawtypes")
        private val RESULT_UPDATER: AtomicReferenceFieldUpdater<DefaultPromise<*>, Any> =
            AtomicReferenceFieldUpdater.newUpdater(
                DefaultPromise::class.java as Class<DefaultPromise<*>>,
                Any::class.java,
                "result"
            )

        private val SUCCESS: Any = Any()
        private val UNCANCELLABLE: Any = Any()
        private val CANCELLATION_CAUSE_HOLDER: CauseHolder = CauseHolder(
            StacklessCancellationException.newInstance(DefaultPromise::class.java, "cancel(...)")
        )
        private val CANCELLATION_STACK: Array<StackTraceElement> = CANCELLATION_CAUSE_HOLDER.cause.stackTrace

        /**
         * Notify a listener that a future has completed.
         *
         * This method has a fixed depth of [MAX_LISTENER_STACK_DEPTH] that will limit recursion to prevent
         * [StackOverflowError] and will stop notifying listeners added after this threshold is exceeded.
         * @param eventExecutor the executor to use to notify the listener `listener`.
         * @param future the future that is complete.
         * @param listener the listener to notify.
         */
        @JvmStatic
        internal fun notifyListener(
            eventExecutor: EventExecutor,
            future: Future<*>,
            listener: GenericFutureListener<*>
        ) {
            notifyListenerWithStackOverFlowProtection(
                checkNotNull(eventExecutor, "eventExecutor"),
                checkNotNull(future, "future"),
                checkNotNull(listener, "listener")
            )
        }

        /**
         * The logic in this method should be identical to [notifyListeners] but
         * cannot share code because the listener(s) cannot be cached for an instance of [DefaultPromise] since the
         * listener(s) may be changed and is protected by a synchronized operation.
         */
        private fun notifyListenerWithStackOverFlowProtection(
            executor: EventExecutor,
            future: Future<*>,
            listener: GenericFutureListener<*>
        ) {
            if (executor.inEventLoop()) {
                val threadLocals = InternalThreadLocalMap.get()
                val stackDepth = threadLocals.futureListenerStackDepth()
                if (stackDepth < MAX_LISTENER_STACK_DEPTH) {
                    threadLocals.setFutureListenerStackDepth(stackDepth + 1)
                    try {
                        notifyListener0(future, listener)
                    } finally {
                        threadLocals.setFutureListenerStackDepth(stackDepth)
                    }
                    return
                }
            }

            safeExecute(executor, Runnable { notifyListener0(future, listener) })
        }

        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        private fun notifyListener0(future: Future<*>, l: GenericFutureListener<*>) {
            try {
                (l as GenericFutureListener<Future<*>>).operationComplete(future)
            } catch (t: Throwable) {
                if (logger.isWarnEnabled()) {
                    logger.warn(
                        "An exception was thrown by " + l.javaClass.name + ".operationComplete()", t
                    )
                }
            }
        }

        private fun notifyProgressiveListeners0(
            future: ProgressiveFuture<*>,
            listeners: Array<GenericProgressiveFutureListener<*>>,
            progress: Long,
            total: Long
        ) {
            for (l in listeners) {
                @Suppress("SENSELESS_COMPARISON")
                if (l == null) {
                    break
                }
                notifyProgressiveListener0(future, l, progress, total)
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun notifyProgressiveListener0(
            future: ProgressiveFuture<*>,
            l: GenericProgressiveFutureListener<*>,
            progress: Long,
            total: Long
        ) {
            try {
                (l as GenericProgressiveFutureListener<ProgressiveFuture<*>>).operationProgressed(
                    future, progress, total
                )
            } catch (t: Throwable) {
                if (logger.isWarnEnabled()) {
                    logger.warn(
                        "An exception was thrown by " + l.javaClass.name + ".operationProgressed()", t
                    )
                }
            }
        }

        private fun isCancelled0(result: Any?): Boolean {
            return result is CauseHolder && result.cause is CancellationException
        }

        private fun isDone0(result: Any?): Boolean {
            return result != null && result !== UNCANCELLABLE
        }

        private fun safeExecute(executor: EventExecutor, task: Runnable) {
            try {
                executor.execute(task)
            } catch (t: Throwable) {
                rejectedExecutionLogger.error(
                    "Failed to submit a listener notification task. Event loop shut down?", t
                )
            }
        }
    }
}
