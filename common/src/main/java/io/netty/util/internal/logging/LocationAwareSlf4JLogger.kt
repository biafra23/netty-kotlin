/*
 * Copyright 2017 The Netty Project
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

import org.slf4j.helpers.FormattingTuple
import org.slf4j.helpers.MessageFormatter
import org.slf4j.spi.LocationAwareLogger
import org.slf4j.spi.LocationAwareLogger.DEBUG_INT
import org.slf4j.spi.LocationAwareLogger.ERROR_INT
import org.slf4j.spi.LocationAwareLogger.INFO_INT
import org.slf4j.spi.LocationAwareLogger.TRACE_INT
import org.slf4j.spi.LocationAwareLogger.WARN_INT

/**
 * [SLF4J](https://www.slf4j.org/) logger which is location aware and so will log the correct
 * origin of the logging event by filter out the wrapper itself.
 */
internal class LocationAwareSlf4JLogger(
    @Transient private val logger: LocationAwareLogger
) : AbstractInternalLogger(logger.name) {

    // IMPORTANT: All our log methods first check if the log level is enabled before call the wrapped
    // LocationAwareLogger.log(...) method. This is done to reduce GC creation that is caused by varargs.

    companion object {
        @JvmField
        val FQCN: String = LocationAwareSlf4JLogger::class.java.name

        private const val serialVersionUID: Long = -8292030083201538180L
    }

    private fun log(level: Int, message: String?) {
        logger.log(null, FQCN, level, message, null, null)
    }

    private fun log(level: Int, message: String?, cause: Throwable?) {
        logger.log(null, FQCN, level, message, null, cause)
    }

    private fun log(level: Int, tuple: FormattingTuple) {
        logger.log(null, FQCN, level, tuple.message, null, tuple.throwable)
    }

    override fun isTraceEnabled(): Boolean = logger.isTraceEnabled

    override fun trace(msg: String?) {
        if (isTraceEnabled()) {
            log(TRACE_INT, msg)
        }
    }

    override fun trace(format: String?, arg: Any?) {
        if (isTraceEnabled()) {
            log(TRACE_INT, MessageFormatter.format(format, arg))
        }
    }

    override fun trace(format: String?, argA: Any?, argB: Any?) {
        if (isTraceEnabled()) {
            log(TRACE_INT, MessageFormatter.format(format, argA, argB))
        }
    }

    override fun trace(format: String?, vararg argArray: Any?) {
        if (isTraceEnabled()) {
            log(TRACE_INT, MessageFormatter.arrayFormat(format, argArray))
        }
    }

    override fun trace(msg: String?, t: Throwable?) {
        if (isTraceEnabled()) {
            log(TRACE_INT, msg, t)
        }
    }

    override fun isDebugEnabled(): Boolean = logger.isDebugEnabled

    override fun debug(msg: String?) {
        if (isDebugEnabled()) {
            log(DEBUG_INT, msg)
        }
    }

    override fun debug(format: String?, arg: Any?) {
        if (isDebugEnabled()) {
            log(DEBUG_INT, MessageFormatter.format(format, arg))
        }
    }

    override fun debug(format: String?, argA: Any?, argB: Any?) {
        if (isDebugEnabled()) {
            log(DEBUG_INT, MessageFormatter.format(format, argA, argB))
        }
    }

    override fun debug(format: String?, vararg argArray: Any?) {
        if (isDebugEnabled()) {
            log(DEBUG_INT, MessageFormatter.arrayFormat(format, argArray))
        }
    }

    override fun debug(msg: String?, t: Throwable?) {
        if (isDebugEnabled()) {
            log(DEBUG_INT, msg, t)
        }
    }

    override fun isInfoEnabled(): Boolean = logger.isInfoEnabled

    override fun info(msg: String?) {
        if (isInfoEnabled()) {
            log(INFO_INT, msg)
        }
    }

    override fun info(format: String?, arg: Any?) {
        if (isInfoEnabled()) {
            log(INFO_INT, MessageFormatter.format(format, arg))
        }
    }

    override fun info(format: String?, argA: Any?, argB: Any?) {
        if (isInfoEnabled()) {
            log(INFO_INT, MessageFormatter.format(format, argA, argB))
        }
    }

    override fun info(format: String?, vararg argArray: Any?) {
        if (isInfoEnabled()) {
            log(INFO_INT, MessageFormatter.arrayFormat(format, argArray))
        }
    }

    override fun info(msg: String?, t: Throwable?) {
        if (isInfoEnabled()) {
            log(INFO_INT, msg, t)
        }
    }

    override fun isWarnEnabled(): Boolean = logger.isWarnEnabled

    override fun warn(msg: String?) {
        if (isWarnEnabled()) {
            log(WARN_INT, msg)
        }
    }

    override fun warn(format: String?, arg: Any?) {
        if (isWarnEnabled()) {
            log(WARN_INT, MessageFormatter.format(format, arg))
        }
    }

    override fun warn(format: String?, vararg argArray: Any?) {
        if (isWarnEnabled()) {
            log(WARN_INT, MessageFormatter.arrayFormat(format, argArray))
        }
    }

    override fun warn(format: String?, argA: Any?, argB: Any?) {
        if (isWarnEnabled()) {
            log(WARN_INT, MessageFormatter.format(format, argA, argB))
        }
    }

    override fun warn(msg: String?, t: Throwable?) {
        if (isWarnEnabled()) {
            log(WARN_INT, msg, t)
        }
    }

    override fun isErrorEnabled(): Boolean = logger.isErrorEnabled

    override fun error(msg: String?) {
        if (isErrorEnabled()) {
            log(ERROR_INT, msg)
        }
    }

    override fun error(format: String?, arg: Any?) {
        if (isErrorEnabled()) {
            log(ERROR_INT, MessageFormatter.format(format, arg))
        }
    }

    override fun error(format: String?, argA: Any?, argB: Any?) {
        if (isErrorEnabled()) {
            log(ERROR_INT, MessageFormatter.format(format, argA, argB))
        }
    }

    override fun error(format: String?, vararg argArray: Any?) {
        if (isErrorEnabled()) {
            log(ERROR_INT, MessageFormatter.arrayFormat(format, argArray))
        }
    }

    override fun error(msg: String?, t: Throwable?) {
        if (isErrorEnabled()) {
            log(ERROR_INT, msg, t)
        }
    }
}
