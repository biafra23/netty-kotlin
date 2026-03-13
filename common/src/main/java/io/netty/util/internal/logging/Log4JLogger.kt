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

import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * [Apache Log4J](https://logging.apache.org/log4j/1.2/index.html)
 * logger.
 */
internal class Log4JLogger(
    @Transient private val logger: Logger
) : AbstractInternalLogger(logger.name) {

    companion object {
        private const val serialVersionUID = 2851357342488183058L

        /**
         * Following the pattern discussed in pages 162 through 168 of "The complete
         * log4j manual".
         */
        @JvmField
        val FQCN: String = Log4JLogger::class.java.name
    }

    // Does the log4j version in use recognize the TRACE level?
    // The trace level was introduced in log4j 1.2.12.
    val traceCapable: Boolean = isTraceCapable()

    private fun isTraceCapable(): Boolean {
        return try {
            logger.isTraceEnabled
            true
        } catch (ignored: NoSuchMethodError) {
            false
        }
    }

    /**
     * Is this logger instance enabled for the TRACE level?
     *
     * @return True if this Logger is enabled for level TRACE, false otherwise.
     */
    override fun isTraceEnabled(): Boolean =
        if (traceCapable) logger.isTraceEnabled else logger.isDebugEnabled

    /**
     * Log a message object at level TRACE.
     *
     * @param msg - the message object to be logged
     */
    override fun trace(msg: String) {
        logger.log(FQCN, if (traceCapable) Level.TRACE else Level.DEBUG, msg, null)
    }

    /**
     * Log a message at level TRACE according to the specified format and
     * argument.
     *
     * This form avoids superfluous object creation when the logger is disabled
     * for level TRACE.
     *
     * @param format the format string
     * @param arg the argument
     */
    override fun trace(format: String, arg: Any) {
        if (isTraceEnabled()) {
            val ft = MessageFormatter.format(format, arg)
            logger.log(FQCN, if (traceCapable) Level.TRACE else Level.DEBUG,
                ft.message, ft.throwable)
        }
    }

    /**
     * Log a message at level TRACE according to the specified format and
     * arguments.
     *
     * This form avoids superfluous object creation when the logger is disabled
     * for the TRACE level.
     *
     * @param format the format string
     * @param argA the first argument
     * @param argB the second argument
     */
    override fun trace(format: String, argA: Any, argB: Any) {
        if (isTraceEnabled()) {
            val ft = MessageFormatter.format(format, argA, argB)
            logger.log(FQCN, if (traceCapable) Level.TRACE else Level.DEBUG,
                ft.message, ft.throwable)
        }
    }

    /**
     * Log a message at level TRACE according to the specified format and
     * arguments.
     *
     * This form avoids superfluous object creation when the logger is disabled
     * for the TRACE level.
     *
     * @param format the format string
     * @param arguments an array of arguments
     */
    override fun trace(format: String, vararg arguments: Any) {
        if (isTraceEnabled()) {
            val ft = MessageFormatter.arrayFormat(format, arguments)
            logger.log(FQCN, if (traceCapable) Level.TRACE else Level.DEBUG,
                ft.message, ft.throwable)
        }
    }

    /**
     * Log an exception (throwable) at level TRACE with an accompanying message.
     *
     * @param msg the message accompanying the exception
     * @param t the exception (throwable) to log
     */
    override fun trace(msg: String, t: Throwable) {
        logger.log(FQCN, if (traceCapable) Level.TRACE else Level.DEBUG, msg, t)
    }

    /**
     * Is this logger instance enabled for the DEBUG level?
     *
     * @return True if this Logger is enabled for level DEBUG, false otherwise.
     */
    override fun isDebugEnabled(): Boolean = logger.isDebugEnabled

    /**
     * Log a message object at level DEBUG.
     *
     * @param msg - the message object to be logged
     */
    override fun debug(msg: String) {
        logger.log(FQCN, Level.DEBUG, msg, null)
    }

    /**
     * Log a message at level DEBUG according to the specified format and
     * argument.
     *
     * This form avoids superfluous object creation when the logger is disabled
     * for level DEBUG.
     *
     * @param format the format string
     * @param arg the argument
     */
    override fun debug(format: String, arg: Any) {
        if (logger.isDebugEnabled) {
            val ft = MessageFormatter.format(format, arg)
            logger.log(FQCN, Level.DEBUG, ft.message, ft.throwable)
        }
    }

    /**
     * Log a message at level DEBUG according to the specified format and
     * arguments.
     *
     * This form avoids superfluous object creation when the logger is disabled
     * for the DEBUG level.
     *
     * @param format the format string
     * @param argA the first argument
     * @param argB the second argument
     */
    override fun debug(format: String, argA: Any, argB: Any) {
        if (logger.isDebugEnabled) {
            val ft = MessageFormatter.format(format, argA, argB)
            logger.log(FQCN, Level.DEBUG, ft.message, ft.throwable)
        }
    }

    /**
     * Log a message at level DEBUG according to the specified format and
     * arguments.
     *
     * This form avoids superfluous object creation when the logger is disabled
     * for the DEBUG level.
     *
     * @param format the format string
     * @param arguments an array of arguments
     */
    override fun debug(format: String, vararg arguments: Any) {
        if (logger.isDebugEnabled) {
            val ft = MessageFormatter.arrayFormat(format, arguments)
            logger.log(FQCN, Level.DEBUG, ft.message, ft.throwable)
        }
    }

    /**
     * Log an exception (throwable) at level DEBUG with an accompanying message.
     *
     * @param msg the message accompanying the exception
     * @param t the exception (throwable) to log
     */
    override fun debug(msg: String, t: Throwable) {
        logger.log(FQCN, Level.DEBUG, msg, t)
    }

    /**
     * Is this logger instance enabled for the INFO level?
     *
     * @return True if this Logger is enabled for the INFO level, false otherwise.
     */
    override fun isInfoEnabled(): Boolean = logger.isInfoEnabled

    /**
     * Log a message object at the INFO level.
     *
     * @param msg - the message object to be logged
     */
    override fun info(msg: String) {
        logger.log(FQCN, Level.INFO, msg, null)
    }

    /**
     * Log a message at level INFO according to the specified format and argument.
     *
     * This form avoids superfluous object creation when the logger is disabled
     * for the INFO level.
     *
     * @param format the format string
     * @param arg the argument
     */
    override fun info(format: String, arg: Any) {
        if (logger.isInfoEnabled) {
            val ft = MessageFormatter.format(format, arg)
            logger.log(FQCN, Level.INFO, ft.message, ft.throwable)
        }
    }

    /**
     * Log a message at the INFO level according to the specified format and
     * arguments.
     *
     * This form avoids superfluous object creation when the logger is disabled
     * for the INFO level.
     *
     * @param format the format string
     * @param argA the first argument
     * @param argB the second argument
     */
    override fun info(format: String, argA: Any, argB: Any) {
        if (logger.isInfoEnabled) {
            val ft = MessageFormatter.format(format, argA, argB)
            logger.log(FQCN, Level.INFO, ft.message, ft.throwable)
        }
    }

    /**
     * Log a message at level INFO according to the specified format and
     * arguments.
     *
     * This form avoids superfluous object creation when the logger is disabled
     * for the INFO level.
     *
     * @param format the format string
     * @param argArray an array of arguments
     */
    override fun info(format: String, vararg argArray: Any) {
        if (logger.isInfoEnabled) {
            val ft = MessageFormatter.arrayFormat(format, argArray)
            logger.log(FQCN, Level.INFO, ft.message, ft.throwable)
        }
    }

    /**
     * Log an exception (throwable) at the INFO level with an accompanying
     * message.
     *
     * @param msg the message accompanying the exception
     * @param t the exception (throwable) to log
     */
    override fun info(msg: String, t: Throwable) {
        logger.log(FQCN, Level.INFO, msg, t)
    }

    /**
     * Is this logger instance enabled for the WARN level?
     *
     * @return True if this Logger is enabled for the WARN level, false otherwise.
     */
    override fun isWarnEnabled(): Boolean = logger.isEnabledFor(Level.WARN)

    /**
     * Log a message object at the WARN level.
     *
     * @param msg - the message object to be logged
     */
    override fun warn(msg: String) {
        logger.log(FQCN, Level.WARN, msg, null)
    }

    /**
     * Log a message at the WARN level according to the specified format and
     * argument.
     *
     * This form avoids superfluous object creation when the logger is disabled
     * for the WARN level.
     *
     * @param format the format string
     * @param arg the argument
     */
    override fun warn(format: String, arg: Any) {
        if (logger.isEnabledFor(Level.WARN)) {
            val ft = MessageFormatter.format(format, arg)
            logger.log(FQCN, Level.WARN, ft.message, ft.throwable)
        }
    }

    /**
     * Log a message at the WARN level according to the specified format and
     * arguments.
     *
     * This form avoids superfluous object creation when the logger is disabled
     * for the WARN level.
     *
     * @param format the format string
     * @param argA the first argument
     * @param argB the second argument
     */
    override fun warn(format: String, argA: Any, argB: Any) {
        if (logger.isEnabledFor(Level.WARN)) {
            val ft = MessageFormatter.format(format, argA, argB)
            logger.log(FQCN, Level.WARN, ft.message, ft.throwable)
        }
    }

    /**
     * Log a message at level WARN according to the specified format and
     * arguments.
     *
     * This form avoids superfluous object creation when the logger is disabled
     * for the WARN level.
     *
     * @param format the format string
     * @param argArray an array of arguments
     */
    override fun warn(format: String, vararg argArray: Any) {
        if (logger.isEnabledFor(Level.WARN)) {
            val ft = MessageFormatter.arrayFormat(format, argArray)
            logger.log(FQCN, Level.WARN, ft.message, ft.throwable)
        }
    }

    /**
     * Log an exception (throwable) at the WARN level with an accompanying
     * message.
     *
     * @param msg the message accompanying the exception
     * @param t the exception (throwable) to log
     */
    override fun warn(msg: String, t: Throwable) {
        logger.log(FQCN, Level.WARN, msg, t)
    }

    /**
     * Is this logger instance enabled for level ERROR?
     *
     * @return True if this Logger is enabled for level ERROR, false otherwise.
     */
    override fun isErrorEnabled(): Boolean = logger.isEnabledFor(Level.ERROR)

    /**
     * Log a message object at the ERROR level.
     *
     * @param msg - the message object to be logged
     */
    override fun error(msg: String) {
        logger.log(FQCN, Level.ERROR, msg, null)
    }

    /**
     * Log a message at the ERROR level according to the specified format and
     * argument.
     *
     * This form avoids superfluous object creation when the logger is disabled
     * for the ERROR level.
     *
     * @param format the format string
     * @param arg the argument
     */
    override fun error(format: String, arg: Any) {
        if (logger.isEnabledFor(Level.ERROR)) {
            val ft = MessageFormatter.format(format, arg)
            logger.log(FQCN, Level.ERROR, ft.message, ft.throwable)
        }
    }

    /**
     * Log a message at the ERROR level according to the specified format and
     * arguments.
     *
     * This form avoids superfluous object creation when the logger is disabled
     * for the ERROR level.
     *
     * @param format the format string
     * @param argA the first argument
     * @param argB the second argument
     */
    override fun error(format: String, argA: Any, argB: Any) {
        if (logger.isEnabledFor(Level.ERROR)) {
            val ft = MessageFormatter.format(format, argA, argB)
            logger.log(FQCN, Level.ERROR, ft.message, ft.throwable)
        }
    }

    /**
     * Log a message at level ERROR according to the specified format and
     * arguments.
     *
     * This form avoids superfluous object creation when the logger is disabled
     * for the ERROR level.
     *
     * @param format the format string
     * @param argArray an array of arguments
     */
    override fun error(format: String, vararg argArray: Any) {
        if (logger.isEnabledFor(Level.ERROR)) {
            val ft = MessageFormatter.arrayFormat(format, argArray)
            logger.log(FQCN, Level.ERROR, ft.message, ft.throwable)
        }
    }

    /**
     * Log an exception (throwable) at the ERROR level with an accompanying
     * message.
     *
     * @param msg the message accompanying the exception
     * @param t the exception (throwable) to log
     */
    override fun error(msg: String, t: Throwable) {
        logger.log(FQCN, Level.ERROR, msg, t)
    }
}
