/*
 * Copyright 2019 The Netty Project
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

import io.netty.util.concurrent.EventExecutor
import io.netty.util.concurrent.FastThreadLocal
import java.util.concurrent.Executor
import java.util.concurrent.ThreadFactory

/**
 * Allow to retrieve the [EventExecutor] for the calling [Thread].
 */
object ThreadExecutorMap {

    private val mappings = FastThreadLocal<EventExecutor>()

    /**
     * Returns the current [EventExecutor] that uses the [Thread], or `null` if none / unknown.
     */
    @JvmStatic
    fun currentExecutor(): EventExecutor? = mappings.get()

    /**
     * Set the current [EventExecutor] that is used by the [Thread].
     */
    @JvmStatic
    fun setCurrentExecutor(executor: EventExecutor?): EventExecutor? {
        if (executor == null) {
            val old = mappings.get()
            mappings.remove()
            return old
        }
        return mappings.getAndSet(executor)
    }

    /**
     * Decorate the given [Executor] and ensure [currentExecutor] will return [eventExecutor]
     * when called from within the [Runnable] during execution.
     */
    @JvmStatic
    fun apply(executor: Executor, eventExecutor: EventExecutor): Executor {
        ObjectUtil.checkNotNull(executor, "executor")
        ObjectUtil.checkNotNull(eventExecutor, "eventExecutor")
        return Executor { command -> executor.execute(apply(command, eventExecutor)) }
    }

    /**
     * Decorate the given [Runnable] and ensure [currentExecutor] will return [eventExecutor]
     * when called from within the [Runnable] during execution.
     */
    @JvmStatic
    fun apply(command: Runnable, eventExecutor: EventExecutor): Runnable {
        ObjectUtil.checkNotNull(command, "command")
        ObjectUtil.checkNotNull(eventExecutor, "eventExecutor")
        return Runnable {
            val old = setCurrentExecutor(eventExecutor)
            try {
                command.run()
            } finally {
                setCurrentExecutor(old)
            }
        }
    }

    /**
     * Decorate the given [ThreadFactory] and ensure [currentExecutor] will return [eventExecutor]
     * when called from within the [Runnable] during execution.
     */
    @JvmStatic
    fun apply(threadFactory: ThreadFactory, eventExecutor: EventExecutor): ThreadFactory {
        ObjectUtil.checkNotNull(threadFactory, "threadFactory")
        ObjectUtil.checkNotNull(eventExecutor, "eventExecutor")
        return ThreadFactory { r -> threadFactory.newThread(apply(r, eventExecutor)) }
    }
}
