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

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Default implementation which uses simple round-robin to choose next [EventExecutor].
 */
class DefaultEventExecutorChooserFactory private constructor() : EventExecutorChooserFactory {

    override fun newChooser(executors: Array<EventExecutor>): EventExecutorChooserFactory.EventExecutorChooser {
        return if (isPowerOfTwo(executors.size)) {
            PowerOfTwoEventExecutorChooser(executors)
        } else {
            GenericEventExecutorChooser(executors)
        }
    }

    private class PowerOfTwoEventExecutorChooser(
        private val executors: Array<EventExecutor>
    ) : EventExecutorChooserFactory.EventExecutorChooser {
        private val idx = AtomicInteger()

        override fun next(): EventExecutor {
            return executors[idx.getAndIncrement() and executors.size - 1]
        }
    }

    private class GenericEventExecutorChooser(
        private val executors: Array<EventExecutor>
    ) : EventExecutorChooserFactory.EventExecutorChooser {
        // Use a 'long' counter to avoid non-round-robin behaviour at the 32-bit overflow boundary.
        // The 64-bit long solves this by placing the overflow so far into the future, that no system
        // will encounter this in practice.
        private val idx = AtomicLong()

        override fun next(): EventExecutor {
            return executors[Math.abs(idx.getAndIncrement() % executors.size).toInt()]
        }
    }

    companion object {
        @JvmField
        val INSTANCE = DefaultEventExecutorChooserFactory()

        private fun isPowerOfTwo(`val`: Int): Boolean {
            return (`val` and -`val`) == `val`
        }
    }
}
