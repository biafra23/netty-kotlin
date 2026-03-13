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

import java.io.Serializable
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * [java.util.logging](https://java.sun.com/javase/6/docs/technotes/guides/logging/index.html)
 * logger.
 */
internal class JdkLogger(
    @Transient private val logger: Logger
) : AbstractInternalLogger(logger.name), Serializable {

    override fun isTraceEnabled(): Boolean = logger.isLoggable(Level.FINEST)

    override fun trace(msg: String) {
        if (logger.isLoggable(Level.FINEST)) {
            log(SELF, Level.FINEST, msg, null)
        }
    }

    override fun trace(format: String, arg: Any) {
        if (logger.isLoggable(Level.FINEST)) {
            val ft = MessageFormatter.format(format, arg)
            log(SELF, Level.FINEST, ft.message, ft.throwable)
        }
    }

    override fun trace(format: String, argA: Any, argB: Any) {
        if (logger.isLoggable(Level.FINEST)) {
            val ft = MessageFormatter.format(format, argA, argB)
            log(SELF, Level.FINEST, ft.message, ft.throwable)
        }
    }

    override fun trace(format: String, vararg argArray: Any) {
        if (logger.isLoggable(Level.FINEST)) {
            val ft = MessageFormatter.arrayFormat(format, argArray)
            log(SELF, Level.FINEST, ft.message, ft.throwable)
        }
    }

    override fun trace(msg: String, t: Throwable) {
        if (logger.isLoggable(Level.FINEST)) {
            log(SELF, Level.FINEST, msg, t)
        }
    }

    override fun isDebugEnabled(): Boolean = logger.isLoggable(Level.FINE)

    override fun debug(msg: String) {
        if (logger.isLoggable(Level.FINE)) {
            log(SELF, Level.FINE, msg, null)
        }
    }

    override fun debug(format: String, arg: Any) {
        if (logger.isLoggable(Level.FINE)) {
            val ft = MessageFormatter.format(format, arg)
            log(SELF, Level.FINE, ft.message, ft.throwable)
        }
    }

    override fun debug(format: String, argA: Any, argB: Any) {
        if (logger.isLoggable(Level.FINE)) {
            val ft = MessageFormatter.format(format, argA, argB)
            log(SELF, Level.FINE, ft.message, ft.throwable)
        }
    }

    override fun debug(format: String, vararg argArray: Any) {
        if (logger.isLoggable(Level.FINE)) {
            val ft = MessageFormatter.arrayFormat(format, argArray)
            log(SELF, Level.FINE, ft.message, ft.throwable)
        }
    }

    override fun debug(msg: String, t: Throwable) {
        if (logger.isLoggable(Level.FINE)) {
            log(SELF, Level.FINE, msg, t)
        }
    }

    override fun isInfoEnabled(): Boolean = logger.isLoggable(Level.INFO)

    override fun info(msg: String) {
        if (logger.isLoggable(Level.INFO)) {
            log(SELF, Level.INFO, msg, null)
        }
    }

    override fun info(format: String, arg: Any) {
        if (logger.isLoggable(Level.INFO)) {
            val ft = MessageFormatter.format(format, arg)
            log(SELF, Level.INFO, ft.message, ft.throwable)
        }
    }

    override fun info(format: String, argA: Any, argB: Any) {
        if (logger.isLoggable(Level.INFO)) {
            val ft = MessageFormatter.format(format, argA, argB)
            log(SELF, Level.INFO, ft.message, ft.throwable)
        }
    }

    override fun info(format: String, vararg argArray: Any) {
        if (logger.isLoggable(Level.INFO)) {
            val ft = MessageFormatter.arrayFormat(format, argArray)
            log(SELF, Level.INFO, ft.message, ft.throwable)
        }
    }

    override fun info(msg: String, t: Throwable) {
        if (logger.isLoggable(Level.INFO)) {
            log(SELF, Level.INFO, msg, t)
        }
    }

    override fun isWarnEnabled(): Boolean = logger.isLoggable(Level.WARNING)

    override fun warn(msg: String) {
        if (logger.isLoggable(Level.WARNING)) {
            log(SELF, Level.WARNING, msg, null)
        }
    }

    override fun warn(format: String, arg: Any) {
        if (logger.isLoggable(Level.WARNING)) {
            val ft = MessageFormatter.format(format, arg)
            log(SELF, Level.WARNING, ft.message, ft.throwable)
        }
    }

    override fun warn(format: String, argA: Any, argB: Any) {
        if (logger.isLoggable(Level.WARNING)) {
            val ft = MessageFormatter.format(format, argA, argB)
            log(SELF, Level.WARNING, ft.message, ft.throwable)
        }
    }

    override fun warn(format: String, vararg argArray: Any) {
        if (logger.isLoggable(Level.WARNING)) {
            val ft = MessageFormatter.arrayFormat(format, argArray)
            log(SELF, Level.WARNING, ft.message, ft.throwable)
        }
    }

    override fun warn(msg: String, t: Throwable) {
        if (logger.isLoggable(Level.WARNING)) {
            log(SELF, Level.WARNING, msg, t)
        }
    }

    override fun isErrorEnabled(): Boolean = logger.isLoggable(Level.SEVERE)

    override fun error(msg: String) {
        if (logger.isLoggable(Level.SEVERE)) {
            log(SELF, Level.SEVERE, msg, null)
        }
    }

    override fun error(format: String, arg: Any) {
        if (logger.isLoggable(Level.SEVERE)) {
            val ft = MessageFormatter.format(format, arg)
            log(SELF, Level.SEVERE, ft.message, ft.throwable)
        }
    }

    override fun error(format: String, argA: Any, argB: Any) {
        if (logger.isLoggable(Level.SEVERE)) {
            val ft = MessageFormatter.format(format, argA, argB)
            log(SELF, Level.SEVERE, ft.message, ft.throwable)
        }
    }

    override fun error(format: String, vararg arguments: Any) {
        if (logger.isLoggable(Level.SEVERE)) {
            val ft = MessageFormatter.arrayFormat(format, arguments)
            log(SELF, Level.SEVERE, ft.message, ft.throwable)
        }
    }

    override fun error(msg: String, t: Throwable) {
        if (logger.isLoggable(Level.SEVERE)) {
            log(SELF, Level.SEVERE, msg, t)
        }
    }

    /**
     * Log the message at the specified level with the specified throwable if any.
     * This method creates a LogRecord and fills in caller date before calling
     * this instance's JDK14 logger.
     *
     * See bug report #13 for more details.
     */
    private fun log(callerFQCN: String, level: Level, msg: String?, t: Throwable?) {
        // millis and thread are filled by the constructor
        val record = LogRecord(level, msg)
        record.loggerName = name()
        record.thrown = t
        fillCallerData(callerFQCN, record)
        logger.log(record)
    }

    companion object {
        private const val serialVersionUID = -1767272577989225979L

        @JvmField
        internal val SELF: String = JdkLogger::class.java.name

        @JvmField
        internal val SUPER: String = AbstractInternalLogger::class.java.name

        /**
         * Fill in caller data if possible.
         *
         * @param record
         *          The record to update
         */
        private fun fillCallerData(callerFQCN: String, record: LogRecord) {
            val steArray = Throwable().stackTrace

            var selfIndex = -1
            for (i in steArray.indices) {
                val className = steArray[i].className
                if (className == callerFQCN || className == SUPER) {
                    selfIndex = i
                    break
                }
            }

            var found = -1
            for (i in selfIndex + 1 until steArray.size) {
                val className = steArray[i].className
                if (className != callerFQCN && className != SUPER) {
                    found = i
                    break
                }
            }

            if (found != -1) {
                val ste = steArray[found]
                // setting the class name has the side effect of setting
                // the needToInferCaller variable to false.
                record.sourceClassName = ste.className
                record.sourceMethodName = ste.methodName
            }
        }
    }
}
