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
package io.netty.util.internal.logging

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.spi.ExtendedLogger
import org.apache.logging.log4j.spi.ExtendedLoggerWrapper
import java.security.AccessController
import java.security.PrivilegedAction

internal open class Log4J2Logger(
    logger: Logger
) : ExtendedLoggerWrapper(
    logger as ExtendedLogger, logger.name, logger.getMessageFactory()
), InternalLogger {

    companion object {
        private const val serialVersionUID = 5485418394879791397L

        private val VARARGS_ONLY: Boolean

        init {
            // Older Log4J2 versions have only log methods that takes the format + varargs. So we should not use
            // Log4J2 if the version is too old.
            // See https://github.com/netty/netty/issues/8217
            VARARGS_ONLY = AccessController.doPrivileged(PrivilegedAction {
                try {
                    Logger::class.java.getMethod("debug", String::class.java, Any::class.java)
                    false
                } catch (ignore: NoSuchMethodException) {
                    // Log4J2 version too old.
                    true
                } catch (ignore: SecurityException) {
                    // We could not detect the version so we will use Log4J2 if its on the classpath.
                    false
                }
            })
        }
    }

    init {
        if (VARARGS_ONLY) {
            throw UnsupportedOperationException("Log4J2 version mismatch")
        }
    }

    override fun name(): String = getName()

    override fun trace(t: Throwable) {
        log(Level.TRACE, AbstractInternalLogger.EXCEPTION_MESSAGE, t)
    }

    override fun debug(t: Throwable) {
        log(Level.DEBUG, AbstractInternalLogger.EXCEPTION_MESSAGE, t)
    }

    override fun info(t: Throwable) {
        log(Level.INFO, AbstractInternalLogger.EXCEPTION_MESSAGE, t)
    }

    override fun warn(t: Throwable) {
        log(Level.WARN, AbstractInternalLogger.EXCEPTION_MESSAGE, t)
    }

    override fun error(t: Throwable) {
        log(Level.ERROR, AbstractInternalLogger.EXCEPTION_MESSAGE, t)
    }

    override fun isEnabled(level: InternalLogLevel): Boolean =
        isEnabled(toLevel(level))

    override fun log(level: InternalLogLevel, msg: String) {
        log(toLevel(level), msg)
    }

    override fun log(level: InternalLogLevel, format: String, arg: Any) {
        log(toLevel(level), format, arg)
    }

    override fun log(level: InternalLogLevel, format: String, argA: Any, argB: Any) {
        log(toLevel(level), format, argA, argB)
    }

    override fun log(level: InternalLogLevel, format: String, vararg arguments: Any) {
        log(toLevel(level), format, arguments)
    }

    override fun log(level: InternalLogLevel, msg: String, t: Throwable) {
        log(toLevel(level), msg, t)
    }

    override fun log(level: InternalLogLevel, t: Throwable) {
        log(toLevel(level), AbstractInternalLogger.EXCEPTION_MESSAGE, t)
    }

    private fun toLevel(level: InternalLogLevel): Level = when (level) {
        InternalLogLevel.INFO -> Level.INFO
        InternalLogLevel.DEBUG -> Level.DEBUG
        InternalLogLevel.WARN -> Level.WARN
        InternalLogLevel.ERROR -> Level.ERROR
        InternalLogLevel.TRACE -> Level.TRACE
    }
}
