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

import io.netty.util.internal.ObjectUtil
import io.netty.util.internal.StringUtil
import io.netty.util.internal.logging.InternalLoggerFactory

/**
 * Collection of method to handle objects that may implement [ReferenceCounted].
 */
object ReferenceCountUtil {

    private val logger = InternalLoggerFactory.getInstance(ReferenceCountUtil::class.java)

    init {
        ResourceLeakDetector.addExclusions(ReferenceCountUtil::class.java, "touch")
    }

    /**
     * Try to call [ReferenceCounted.retain] if the specified message implements [ReferenceCounted].
     * If the specified message doesn't implement [ReferenceCounted], this method does nothing.
     */
    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T> retain(msg: T): T {
        if (msg is ReferenceCounted) {
            return (msg as ReferenceCounted).retain() as T
        }
        return msg
    }

    /**
     * Try to call [ReferenceCounted.retain] if the specified message implements [ReferenceCounted].
     * If the specified message doesn't implement [ReferenceCounted], this method does nothing.
     */
    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T> retain(msg: T, increment: Int): T {
        ObjectUtil.checkPositive(increment, "increment")
        if (msg is ReferenceCounted) {
            return (msg as ReferenceCounted).retain(increment) as T
        }
        return msg
    }

    /**
     * Tries to call [ReferenceCounted.touch] if the specified message implements [ReferenceCounted].
     * If the specified message doesn't implement [ReferenceCounted], this method does nothing.
     */
    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T> touch(msg: T): T {
        if (msg is ReferenceCounted) {
            return (msg as ReferenceCounted).touch() as T
        }
        return msg
    }

    /**
     * Tries to call [ReferenceCounted.touch] if the specified message implements
     * [ReferenceCounted].  If the specified message doesn't implement [ReferenceCounted],
     * this method does nothing.
     */
    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T> touch(msg: T, hint: Any?): T {
        if (msg is ReferenceCounted) {
            return (msg as ReferenceCounted).touch(hint) as T
        }
        return msg
    }

    /**
     * Try to call [ReferenceCounted.release] if the specified message implements [ReferenceCounted].
     * If the specified message doesn't implement [ReferenceCounted], this method does nothing.
     */
    @JvmStatic
    fun release(msg: Any?): Boolean {
        if (msg is ReferenceCounted) {
            return msg.release()
        }
        return false
    }

    /**
     * Try to call [ReferenceCounted.release] if the specified message implements [ReferenceCounted].
     * If the specified message doesn't implement [ReferenceCounted], this method does nothing.
     */
    @JvmStatic
    fun release(msg: Any?, decrement: Int): Boolean {
        ObjectUtil.checkPositive(decrement, "decrement")
        if (msg is ReferenceCounted) {
            return msg.release(decrement)
        }
        return false
    }

    /**
     * Try to call [ReferenceCounted.release] if the specified message implements [ReferenceCounted].
     * If the specified message doesn't implement [ReferenceCounted], this method does nothing.
     * Unlike [release] this method catches an exception raised by [ReferenceCounted.release]
     * and logs it, rather than rethrowing it to the caller.  It is usually recommended to use [release]
     * instead, unless you absolutely need to swallow an exception.
     */
    @JvmStatic
    fun safeRelease(msg: Any?) {
        try {
            release(msg)
        } catch (t: Throwable) {
            logger.warn("Failed to release a message: {}", msg ?: "null", t)
        }
    }

    /**
     * Try to call [ReferenceCounted.release] if the specified message implements [ReferenceCounted].
     * If the specified message doesn't implement [ReferenceCounted], this method does nothing.
     * Unlike [release] this method catches an exception raised by [ReferenceCounted.release]
     * and logs it, rather than rethrowing it to the caller.  It is usually recommended to use
     * [release] instead, unless you absolutely need to swallow an exception.
     */
    @JvmStatic
    fun safeRelease(msg: Any?, decrement: Int) {
        try {
            ObjectUtil.checkPositive(decrement, "decrement")
            release(msg, decrement)
        } catch (t: Throwable) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to release a message: {} (decrement: {})", msg ?: "null", decrement, t)
            }
        }
    }

    /**
     * Schedules the specified object to be released when the caller thread terminates. Note that this operation is
     * intended to simplify reference counting of ephemeral objects during unit tests. Do not use it beyond the
     * intended use case.
     *
     * @deprecated this may introduce a lot of memory usage so it is generally preferable to manually release objects.
     */
    @JvmStatic
    @Deprecated("this may introduce a lot of memory usage so it is generally preferable to manually release objects.")
    fun <T> releaseLater(msg: T): T = releaseLater(msg, 1)

    /**
     * Schedules the specified object to be released when the caller thread terminates. Note that this operation is
     * intended to simplify reference counting of ephemeral objects during unit tests. Do not use it beyond the
     * intended use case.
     *
     * @deprecated this may introduce a lot of memory usage so it is generally preferable to manually release objects.
     */
    @JvmStatic
    @Deprecated("this may introduce a lot of memory usage so it is generally preferable to manually release objects.")
    fun <T> releaseLater(msg: T, decrement: Int): T {
        ObjectUtil.checkPositive(decrement, "decrement")
        if (msg is ReferenceCounted) {
            ThreadDeathWatcher.watch(Thread.currentThread(), ReleasingTask(msg as ReferenceCounted, decrement))
        }
        return msg
    }

    /**
     * Returns reference count of a [ReferenceCounted] object. If object is not type of
     * [ReferenceCounted], `-1` is returned.
     */
    @JvmStatic
    fun refCnt(msg: Any?): Int =
        if (msg is ReferenceCounted) msg.refCnt() else -1

    /**
     * Releases the objects when the thread that called [releaseLater] has been terminated.
     */
    private class ReleasingTask(
        private val obj: ReferenceCounted,
        private val decrement: Int
    ) : Runnable {

        override fun run() {
            try {
                if (!obj.release(decrement)) {
                    logger.warn("Non-zero refCnt: {}", this)
                } else {
                    logger.debug("Released: {}", this)
                }
            } catch (ex: Exception) {
                logger.warn("Failed to release an object: {}", obj, ex)
            }
        }

        override fun toString(): String =
            "${StringUtil.simpleClassName(obj)}.release($decrement) refCnt: ${obj.refCnt()}"
    }
}
