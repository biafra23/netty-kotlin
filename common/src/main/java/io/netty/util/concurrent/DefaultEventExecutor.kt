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
 * Default [SingleThreadEventExecutor] implementation which just execute all submitted task in a
 * serial fashion.
 */
class DefaultEventExecutor : SingleThreadEventExecutor {

    constructor() : this(null as EventExecutorGroup?)

    constructor(threadFactory: ThreadFactory) : this(null, threadFactory)

    constructor(executor: Executor) : this(null, executor)

    constructor(parent: EventExecutorGroup?) : this(parent, DefaultThreadFactory(DefaultEventExecutor::class.java))

    constructor(parent: EventExecutorGroup?, threadFactory: ThreadFactory) : super(parent, threadFactory, true)

    constructor(parent: EventExecutorGroup?, executor: Executor) : super(parent, executor, true)

    constructor(
        parent: EventExecutorGroup?,
        threadFactory: ThreadFactory,
        maxPendingTasks: Int,
        rejectedExecutionHandler: RejectedExecutionHandler
    ) : super(parent, threadFactory, true, maxPendingTasks, rejectedExecutionHandler)

    constructor(
        parent: EventExecutorGroup?,
        executor: Executor,
        maxPendingTasks: Int,
        rejectedExecutionHandler: RejectedExecutionHandler
    ) : super(parent, executor, true, maxPendingTasks, rejectedExecutionHandler)

    override fun run() {
        while (true) {
            val task = takeTask()
            if (task != null) {
                runTask(task)
                updateLastExecutionTime()
            }

            if (confirmShutdown()) {
                break
            }
        }
    }
}
