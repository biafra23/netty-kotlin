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

import io.netty.util.internal.ObjectUtil
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport

/**
 * Expose helper methods which create different [RejectedExecutionHandler]s.
 */
class RejectedExecutionHandlers private constructor() {

    companion object {
        private val REJECT = object : RejectedExecutionHandler {
            override fun rejected(task: Runnable, executor: SingleThreadEventExecutor) {
                throw RejectedExecutionException()
            }
        }

        /**
         * Returns a [RejectedExecutionHandler] that will always just throw a [RejectedExecutionException].
         */
        @JvmStatic
        fun reject(): RejectedExecutionHandler {
            return REJECT
        }

        /**
         * Tries to backoff when the task can not be added due restrictions for an configured amount of time. This
         * is only done if the task was added from outside of the event loop which means
         * [EventExecutor.inEventLoop] returns `false`.
         */
        @JvmStatic
        fun backoff(retries: Int, backoffAmount: Long, unit: TimeUnit): RejectedExecutionHandler {
            ObjectUtil.checkPositive(retries, "retries")
            val backOffNanos = unit.toNanos(backoffAmount)
            return object : RejectedExecutionHandler {
                override fun rejected(task: Runnable, executor: SingleThreadEventExecutor) {
                    if (!executor.inEventLoop()) {
                        for (i in 0 until retries) {
                            // Try to wake up the executor so it will empty its task queue.
                            executor.invokeWakeup(false)

                            LockSupport.parkNanos(backOffNanos)
                            if (executor.offerTask(task)) {
                                return
                            }
                        }
                    }
                    // Either we tried to add the task from within the EventLoop or we was not able to add it even with
                    // backoff.
                    throw RejectedExecutionException()
                }
            }
        }
    }
}
