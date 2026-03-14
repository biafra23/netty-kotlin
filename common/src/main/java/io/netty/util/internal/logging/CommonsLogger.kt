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
/**
 * Copyright (c) 2004-2011 QOS.ch
 * All rights reserved.
 *
 * Permission is hereby granted, free  of charge, to any person obtaining
 * a  copy  of this  software  and  associated  documentation files  (the
 * "Software"), to  deal in  the Software without  restriction, including
 * without limitation  the rights to  use, copy, modify,  merge, publish,
 * distribute,  sublicense, and/or sell  copies of  the Software,  and to
 * permit persons to whom the Software  is furnished to do so, subject to
 * the following conditions:
 *
 * The  above  copyright  notice  and  this permission  notice  shall  be
 * included in all copies or substantial portions of the Software.
 *
 * THE  SOFTWARE IS  PROVIDED  "AS  IS", WITHOUT  WARRANTY  OF ANY  KIND,
 * EXPRESS OR  IMPLIED, INCLUDING  BUT NOT LIMITED  TO THE  WARRANTIES OF
 * MERCHANTABILITY,    FITNESS    FOR    A   PARTICULAR    PURPOSE    AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE,  ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */
package io.netty.util.internal.logging

import io.netty.util.internal.ObjectUtil
import org.apache.commons.logging.Log

/**
 * [Apache Commons Logging](https://commons.apache.org/logging/)
 * logger.
 *
 * @deprecated Please use [Log4J2Logger] or [Log4JLogger] or
 * [Slf4JLogger].
 */
@Deprecated("Please use Log4J2Logger or Log4JLogger or Slf4JLogger.")
internal class CommonsLogger(
    @Transient private val logger: Log,
    name: String
) : AbstractInternalLogger(ObjectUtil.checkNotNull(name, "name")) {

    companion object {
        private const val serialVersionUID = 8647838678388394885L
    }

    /**
     * Delegates to the [Log.isTraceEnabled] method of the underlying
     * [Log] instance.
     */
    override fun isTraceEnabled(): Boolean = logger.isTraceEnabled

    /**
     * Delegates to the [Log.trace] method of the underlying
     * [Log] instance.
     *
     * @param msg - the message object to be logged
     */
    override fun trace(msg: String?) {
        logger.trace(msg)
    }

    /**
     * Delegates to the [Log.trace] method of the underlying
     * [Log] instance.
     *
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level TRACE.
     *
     * @param format the format string
     * @param arg the argument
     */
    override fun trace(format: String?, arg: Any?) {
        if (logger.isTraceEnabled) {
            val ft = MessageFormatter.format(format, arg)
            logger.trace(ft.message, ft.throwable)
        }
    }

    /**
     * Delegates to the [Log.trace] method of the underlying
     * [Log] instance.
     *
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level TRACE.
     *
     * @param format the format string
     * @param argA the first argument
     * @param argB the second argument
     */
    override fun trace(format: String?, argA: Any?, argB: Any?) {
        if (logger.isTraceEnabled) {
            val ft = MessageFormatter.format(format, argA, argB)
            logger.trace(ft.message, ft.throwable)
        }
    }

    /**
     * Delegates to the [Log.trace] method of the underlying
     * [Log] instance.
     *
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level TRACE.
     *
     * @param format the format string
     * @param arguments a list of 3 or more arguments
     */
    override fun trace(format: String?, vararg arguments: Any?) {
        if (logger.isTraceEnabled) {
            val ft = MessageFormatter.arrayFormat(format, arguments)
            logger.trace(ft.message, ft.throwable)
        }
    }

    /**
     * Delegates to the [Log.trace] method of
     * the underlying [Log] instance.
     *
     * @param msg the message accompanying the exception
     * @param t the exception (throwable) to log
     */
    override fun trace(msg: String?, t: Throwable?) {
        logger.trace(msg, t)
    }

    /**
     * Delegates to the [Log.isDebugEnabled] method of the underlying
     * [Log] instance.
     */
    override fun isDebugEnabled(): Boolean = logger.isDebugEnabled

    /**
     * Delegates to the [Log.debug] method of the underlying
     * [Log] instance.
     *
     * @param msg - the message object to be logged
     */
    override fun debug(msg: String?) {
        logger.debug(msg)
    }

    /**
     * Delegates to the [Log.debug] method of the underlying
     * [Log] instance.
     *
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level DEBUG.
     *
     * @param format the format string
     * @param arg the argument
     */
    override fun debug(format: String?, arg: Any?) {
        if (logger.isDebugEnabled) {
            val ft = MessageFormatter.format(format, arg)
            logger.debug(ft.message, ft.throwable)
        }
    }

    /**
     * Delegates to the [Log.debug] method of the underlying
     * [Log] instance.
     *
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level DEBUG.
     *
     * @param format the format string
     * @param argA the first argument
     * @param argB the second argument
     */
    override fun debug(format: String?, argA: Any?, argB: Any?) {
        if (logger.isDebugEnabled) {
            val ft = MessageFormatter.format(format, argA, argB)
            logger.debug(ft.message, ft.throwable)
        }
    }

    /**
     * Delegates to the [Log.debug] method of the underlying
     * [Log] instance.
     *
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level DEBUG.
     *
     * @param format the format string
     * @param arguments a list of 3 or more arguments
     */
    override fun debug(format: String?, vararg arguments: Any?) {
        if (logger.isDebugEnabled) {
            val ft = MessageFormatter.arrayFormat(format, arguments)
            logger.debug(ft.message, ft.throwable)
        }
    }

    /**
     * Delegates to the [Log.debug] method of
     * the underlying [Log] instance.
     *
     * @param msg the message accompanying the exception
     * @param t the exception (throwable) to log
     */
    override fun debug(msg: String?, t: Throwable?) {
        logger.debug(msg, t)
    }

    /**
     * Delegates to the [Log.isInfoEnabled] method of the underlying
     * [Log] instance.
     */
    override fun isInfoEnabled(): Boolean = logger.isInfoEnabled

    /**
     * Delegates to the [Log.debug] method of the underlying
     * [Log] instance.
     *
     * @param msg - the message object to be logged
     */
    override fun info(msg: String?) {
        logger.info(msg)
    }

    /**
     * Delegates to the [Log.info] method of the underlying
     * [Log] instance.
     *
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level INFO.
     *
     * @param format the format string
     * @param arg the argument
     */
    override fun info(format: String?, arg: Any?) {
        if (logger.isInfoEnabled) {
            val ft = MessageFormatter.format(format, arg)
            logger.info(ft.message, ft.throwable)
        }
    }

    /**
     * Delegates to the [Log.info] method of the underlying
     * [Log] instance.
     *
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level INFO.
     *
     * @param format the format string
     * @param argA the first argument
     * @param argB the second argument
     */
    override fun info(format: String?, argA: Any?, argB: Any?) {
        if (logger.isInfoEnabled) {
            val ft = MessageFormatter.format(format, argA, argB)
            logger.info(ft.message, ft.throwable)
        }
    }

    /**
     * Delegates to the [Log.info] method of the underlying
     * [Log] instance.
     *
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level INFO.
     *
     * @param format the format string
     * @param arguments a list of 3 or more arguments
     */
    override fun info(format: String?, vararg arguments: Any?) {
        if (logger.isInfoEnabled) {
            val ft = MessageFormatter.arrayFormat(format, arguments)
            logger.info(ft.message, ft.throwable)
        }
    }

    /**
     * Delegates to the [Log.info] method of
     * the underlying [Log] instance.
     *
     * @param msg the message accompanying the exception
     * @param t the exception (throwable) to log
     */
    override fun info(msg: String?, t: Throwable?) {
        logger.info(msg, t)
    }

    /**
     * Delegates to the [Log.isWarnEnabled] method of the underlying
     * [Log] instance.
     */
    override fun isWarnEnabled(): Boolean = logger.isWarnEnabled

    /**
     * Delegates to the [Log.warn] method of the underlying
     * [Log] instance.
     *
     * @param msg - the message object to be logged
     */
    override fun warn(msg: String?) {
        logger.warn(msg)
    }

    /**
     * Delegates to the [Log.warn] method of the underlying
     * [Log] instance.
     *
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level WARN.
     *
     * @param format the format string
     * @param arg the argument
     */
    override fun warn(format: String?, arg: Any?) {
        if (logger.isWarnEnabled) {
            val ft = MessageFormatter.format(format, arg)
            logger.warn(ft.message, ft.throwable)
        }
    }

    /**
     * Delegates to the [Log.warn] method of the underlying
     * [Log] instance.
     *
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level WARN.
     *
     * @param format the format string
     * @param argA the first argument
     * @param argB the second argument
     */
    override fun warn(format: String?, argA: Any?, argB: Any?) {
        if (logger.isWarnEnabled) {
            val ft = MessageFormatter.format(format, argA, argB)
            logger.warn(ft.message, ft.throwable)
        }
    }

    /**
     * Delegates to the [Log.warn] method of the underlying
     * [Log] instance.
     *
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level WARN.
     *
     * @param format the format string
     * @param arguments a list of 3 or more arguments
     */
    override fun warn(format: String?, vararg arguments: Any?) {
        if (logger.isWarnEnabled) {
            val ft = MessageFormatter.arrayFormat(format, arguments)
            logger.warn(ft.message, ft.throwable)
        }
    }

    /**
     * Delegates to the [Log.warn] method of
     * the underlying [Log] instance.
     *
     * @param msg the message accompanying the exception
     * @param t the exception (throwable) to log
     */
    override fun warn(msg: String?, t: Throwable?) {
        logger.warn(msg, t)
    }

    /**
     * Delegates to the [Log.isErrorEnabled] method of the underlying
     * [Log] instance.
     */
    override fun isErrorEnabled(): Boolean = logger.isErrorEnabled

    /**
     * Delegates to the [Log.error] method of the underlying
     * [Log] instance.
     *
     * @param msg - the message object to be logged
     */
    override fun error(msg: String?) {
        logger.error(msg)
    }

    /**
     * Delegates to the [Log.error] method of the underlying
     * [Log] instance.
     *
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level ERROR.
     *
     * @param format the format string
     * @param arg the argument
     */
    override fun error(format: String?, arg: Any?) {
        if (logger.isErrorEnabled) {
            val ft = MessageFormatter.format(format, arg)
            logger.error(ft.message, ft.throwable)
        }
    }

    /**
     * Delegates to the [Log.error] method of the underlying
     * [Log] instance.
     *
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level ERROR.
     *
     * @param format the format string
     * @param argA the first argument
     * @param argB the second argument
     */
    override fun error(format: String?, argA: Any?, argB: Any?) {
        if (logger.isErrorEnabled) {
            val ft = MessageFormatter.format(format, argA, argB)
            logger.error(ft.message, ft.throwable)
        }
    }

    /**
     * Delegates to the [Log.error] method of the underlying
     * [Log] instance.
     *
     * However, this form avoids superfluous object creation when the logger is disabled
     * for level ERROR.
     *
     * @param format the format string
     * @param arguments a list of 3 or more arguments
     */
    override fun error(format: String?, vararg arguments: Any?) {
        if (logger.isErrorEnabled) {
            val ft = MessageFormatter.arrayFormat(format, arguments)
            logger.error(ft.message, ft.throwable)
        }
    }

    /**
     * Delegates to the [Log.error] method of
     * the underlying [Log] instance.
     *
     * @param msg the message accompanying the exception
     * @param t the exception (throwable) to log
     */
    override fun error(msg: String?, t: Throwable?) {
        logger.error(msg, t)
    }
}
