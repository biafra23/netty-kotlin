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
package io.netty.util.internal.logging

import io.netty.util.internal.ObjectUtil
import io.netty.util.internal.StringUtil
import java.io.ObjectStreamException
import java.io.Serializable

/**
 * A skeletal implementation of [InternalLogger]. This class implements
 * all methods that have a [InternalLogLevel] parameter by default to call
 * specific logger methods such as [info] or [isInfoEnabled].
 */
abstract class AbstractInternalLogger protected constructor(name: String) : InternalLogger, Serializable {

    private val name: String = ObjectUtil.checkNotNull(name, "name")

    override fun name(): String = name

    override fun isEnabled(level: InternalLogLevel): Boolean = when (level) {
        InternalLogLevel.TRACE -> isTraceEnabled()
        InternalLogLevel.DEBUG -> isDebugEnabled()
        InternalLogLevel.INFO -> isInfoEnabled()
        InternalLogLevel.WARN -> isWarnEnabled()
        InternalLogLevel.ERROR -> isErrorEnabled()
    }

    override fun trace(t: Throwable?) {
        trace(EXCEPTION_MESSAGE, t)
    }

    override fun debug(t: Throwable?) {
        debug(EXCEPTION_MESSAGE, t)
    }

    override fun info(t: Throwable?) {
        info(EXCEPTION_MESSAGE, t)
    }

    override fun warn(t: Throwable?) {
        warn(EXCEPTION_MESSAGE, t)
    }

    override fun error(t: Throwable?) {
        error(EXCEPTION_MESSAGE, t)
    }

    override fun log(level: InternalLogLevel, msg: String?, cause: Throwable?) {
        when (level) {
            InternalLogLevel.TRACE -> trace(msg, cause)
            InternalLogLevel.DEBUG -> debug(msg, cause)
            InternalLogLevel.INFO -> info(msg, cause)
            InternalLogLevel.WARN -> warn(msg, cause)
            InternalLogLevel.ERROR -> error(msg, cause)
        }
    }

    override fun log(level: InternalLogLevel, cause: Throwable?) {
        when (level) {
            InternalLogLevel.TRACE -> trace(cause)
            InternalLogLevel.DEBUG -> debug(cause)
            InternalLogLevel.INFO -> info(cause)
            InternalLogLevel.WARN -> warn(cause)
            InternalLogLevel.ERROR -> error(cause)
        }
    }

    override fun log(level: InternalLogLevel, msg: String?) {
        when (level) {
            InternalLogLevel.TRACE -> trace(msg)
            InternalLogLevel.DEBUG -> debug(msg)
            InternalLogLevel.INFO -> info(msg)
            InternalLogLevel.WARN -> warn(msg)
            InternalLogLevel.ERROR -> error(msg)
        }
    }

    override fun log(level: InternalLogLevel, format: String?, arg: Any?) {
        when (level) {
            InternalLogLevel.TRACE -> trace(format, arg)
            InternalLogLevel.DEBUG -> debug(format, arg)
            InternalLogLevel.INFO -> info(format, arg)
            InternalLogLevel.WARN -> warn(format, arg)
            InternalLogLevel.ERROR -> error(format, arg)
        }
    }

    override fun log(level: InternalLogLevel, format: String?, argA: Any?, argB: Any?) {
        when (level) {
            InternalLogLevel.TRACE -> trace(format, argA, argB)
            InternalLogLevel.DEBUG -> debug(format, argA, argB)
            InternalLogLevel.INFO -> info(format, argA, argB)
            InternalLogLevel.WARN -> warn(format, argA, argB)
            InternalLogLevel.ERROR -> error(format, argA, argB)
        }
    }

    override fun log(level: InternalLogLevel, format: String?, vararg arguments: Any?) {
        when (level) {
            InternalLogLevel.TRACE -> trace(format, *arguments)
            InternalLogLevel.DEBUG -> debug(format, *arguments)
            InternalLogLevel.INFO -> info(format, *arguments)
            InternalLogLevel.WARN -> warn(format, *arguments)
            InternalLogLevel.ERROR -> error(format, *arguments)
        }
    }

    @Throws(ObjectStreamException::class)
    protected fun readResolve(): Any = InternalLoggerFactory.getInstance(name())

    override fun toString(): String = StringUtil.simpleClassName(this) + '(' + name() + ')'

    companion object {
        private const val serialVersionUID = -6382972526573193470L

        internal const val EXCEPTION_MESSAGE: String = "Unexpected exception:"
    }
}
