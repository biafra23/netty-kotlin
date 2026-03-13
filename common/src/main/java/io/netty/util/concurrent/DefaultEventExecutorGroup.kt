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

import java.util.concurrent.Executor
import java.util.concurrent.ThreadFactory

/**
 * Default implementation of [MultithreadEventExecutorGroup] which will use [DefaultEventExecutor] instances
 * to handle the tasks.
 */
open class DefaultEventExecutorGroup : MultithreadEventExecutorGroup {

    /**
     * @see .DefaultEventExecutorGroup
     */
    constructor(nThreads: Int) : this(nThreads, null)

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param threadFactory     the ThreadFactory to use, or `null` if the default should be used.
     */
    constructor(nThreads: Int, threadFactory: ThreadFactory?) : this(
        nThreads, threadFactory,
        SingleThreadEventExecutor.DEFAULT_MAX_PENDING_EXECUTOR_TASKS,
        RejectedExecutionHandlers.reject()
    )

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param threadFactory     the ThreadFactory to use, or `null` if the default should be used.
     * @param maxPendingTasks   the maximum number of pending tasks before new tasks will be rejected.
     * @param rejectedHandler   the [RejectedExecutionHandler] to use.
     */
    constructor(
        nThreads: Int,
        threadFactory: ThreadFactory?,
        maxPendingTasks: Int,
        rejectedHandler: RejectedExecutionHandler
    ) : super(nThreads, threadFactory, maxPendingTasks, rejectedHandler)

    override fun newChild(executor: Executor, vararg args: Any?): EventExecutor {
        return DefaultEventExecutor(this, executor, args[0] as Int, args[1] as RejectedExecutionHandler)
    }
}
