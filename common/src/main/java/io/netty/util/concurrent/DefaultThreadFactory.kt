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
import io.netty.util.internal.StringUtil
import java.util.Locale
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * A [ThreadFactory] implementation with a simple naming rule.
 */
open class DefaultThreadFactory : ThreadFactory {

    private val nextId = AtomicInteger()
    private val prefix: String
    private val daemon: Boolean
    private val priority: Int
    @JvmField
    protected val threadGroup: ThreadGroup?

    constructor(poolType: Class<*>) : this(poolType, false, Thread.NORM_PRIORITY)

    constructor(poolName: String) : this(poolName, false, Thread.NORM_PRIORITY)

    constructor(poolType: Class<*>, daemon: Boolean) : this(poolType, daemon, Thread.NORM_PRIORITY)

    constructor(poolName: String, daemon: Boolean) : this(poolName, daemon, Thread.NORM_PRIORITY)

    constructor(poolType: Class<*>, priority: Int) : this(poolType, false, priority)

    constructor(poolName: String, priority: Int) : this(poolName, false, priority)

    constructor(poolType: Class<*>, daemon: Boolean, priority: Int) : this(toPoolName(poolType), daemon, priority)

    @JvmOverloads
    constructor(
        poolName: String,
        daemon: Boolean,
        priority: Int,
        threadGroup: ThreadGroup? = null
    ) {
        ObjectUtil.checkNotNull(poolName, "poolName")

        if (priority < Thread.MIN_PRIORITY || priority > Thread.MAX_PRIORITY) {
            throw IllegalArgumentException(
                "priority: $priority (expected: Thread.MIN_PRIORITY <= priority <= Thread.MAX_PRIORITY)"
            )
        }

        prefix = poolName + '-' + poolId.incrementAndGet() + '-'
        this.daemon = daemon
        this.priority = priority
        this.threadGroup = threadGroup
    }

    override fun newThread(r: Runnable): Thread {
        val t = newThread(FastThreadLocalRunnable.wrap(r), prefix + nextId.incrementAndGet())
        try {
            if (t.isDaemon != daemon) {
                t.isDaemon = daemon
            }

            if (t.priority != priority) {
                t.priority = priority
            }
        } catch (ignored: Exception) {
            // Doesn't matter even if failed to set.
        }
        return t
    }

    protected open fun newThread(r: Runnable, name: String): Thread {
        return FastThreadLocalThread(threadGroup, r, name)
    }

    companion object {
        private val poolId = AtomicInteger()

        @JvmStatic
        fun toPoolName(poolType: Class<*>): String {
            ObjectUtil.checkNotNull(poolType, "poolType")

            val poolName = StringUtil.simpleClassName(poolType)
            return when (poolName.length) {
                0 -> "unknown"
                1 -> poolName.lowercase(Locale.US)
                else -> {
                    if (Character.isUpperCase(poolName[0]) && Character.isLowerCase(poolName[1])) {
                        Character.toLowerCase(poolName[0]) + poolName.substring(1)
                    } else {
                        poolName
                    }
                }
            }
        }
    }
}
