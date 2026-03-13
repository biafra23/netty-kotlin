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

import io.netty.util.internal.ObjectUtil
import io.netty.util.internal.logging.InternalLoggerFactory
import java.util.ArrayDeque
import java.util.Queue
import java.util.concurrent.TimeUnit

/**
 * Executes [Runnable] objects in the caller's thread. If the [execute] is reentrant it will be
 * queued until the original [Runnable] finishes execution.
 *
 * All [Throwable] objects thrown from [execute] will be swallowed and logged. This is to ensure
 * that all queued [Runnable] objects have the chance to be run.
 */
class ImmediateEventExecutor private constructor() : AbstractEventExecutor() {

    private val terminationFuture: Future<*> = FailedFuture<Any>(
        GlobalEventExecutor.INSTANCE, UnsupportedOperationException()
    )

    override fun inEventLoop(): Boolean = true

    override fun inEventLoop(thread: Thread): Boolean = true

    override fun shutdownGracefully(quietPeriod: Long, timeout: Long, unit: TimeUnit): Future<*> {
        return terminationFuture()
    }

    override fun terminationFuture(): Future<*> = terminationFuture

    @Deprecated("Use shutdownGracefully instead.")
    override fun shutdown() { }

    override fun isShuttingDown(): Boolean = false

    override fun isShutdown(): Boolean = false

    override fun isTerminated(): Boolean = false

    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = false

    override fun execute(command: Runnable) {
        ObjectUtil.checkNotNull(command, "command")
        if (!RUNNING.get()) {
            RUNNING.set(true)
            try {
                command.run()
            } catch (cause: Throwable) {
                logger.info("Throwable caught while executing Runnable {}", command, cause)
            } finally {
                val delayedRunnables = DELAYED_RUNNABLES.get()
                var runnable = delayedRunnables.poll()
                while (runnable != null) {
                    try {
                        runnable.run()
                    } catch (cause: Throwable) {
                        logger.info("Throwable caught while executing Runnable {}", runnable, cause)
                    }
                    runnable = delayedRunnables.poll()
                }
                RUNNING.set(false)
            }
        } else {
            DELAYED_RUNNABLES.get().add(command)
        }
    }

    override fun <V> newPromise(): Promise<V> {
        return ImmediatePromise(this)
    }

    override fun <V> newProgressivePromise(): ProgressivePromise<V> {
        return ImmediateProgressivePromise(this)
    }

    internal open class ImmediatePromise<V>(executor: EventExecutor) : DefaultPromise<V>(executor) {
        override fun checkDeadLock() {
            // No check
        }
    }

    internal open class ImmediateProgressivePromise<V>(executor: EventExecutor) : DefaultProgressivePromise<V>(executor) {
        override fun checkDeadLock() {
            // No check
        }
    }

    companion object {
        private val logger = InternalLoggerFactory.getInstance(ImmediateEventExecutor::class.java)

        @JvmField
        val INSTANCE: ImmediateEventExecutor = ImmediateEventExecutor()

        /**
         * A Runnable will be queued if we are executing a Runnable. This is to prevent a [StackOverflowError].
         */
        private val DELAYED_RUNNABLES: FastThreadLocal<Queue<Runnable>> = object : FastThreadLocal<Queue<Runnable>>() {
            override fun initialValue(): Queue<Runnable> = ArrayDeque()
        }

        /**
         * Set to `true` if we are executing a runnable.
         */
        private val RUNNING: FastThreadLocal<Boolean> = object : FastThreadLocal<Boolean>() {
            override fun initialValue(): Boolean = false
        }
    }
}
