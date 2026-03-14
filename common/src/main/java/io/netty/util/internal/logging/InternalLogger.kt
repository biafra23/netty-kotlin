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

/**
 * *Internal-use-only* logger used by Netty. **DO NOT**
 * access this class outside of Netty.
 */
interface InternalLogger {

    /**
     * Return the name of this [InternalLogger] instance.
     *
     * @return name of this logger instance
     */
    fun name(): String

    // --- TRACE ---

    /**
     * Is the logger instance enabled for the TRACE level?
     *
     * @return True if this Logger is enabled for the TRACE level,
     *         false otherwise.
     */
    fun isTraceEnabled(): Boolean

    /**
     * Log a message at the TRACE level.
     *
     * @param msg the message string to be logged
     */
    fun trace(msg: String?)

    /**
     * Log a message at the TRACE level according to the specified format
     * and argument.
     *
     * This form avoids superfluous object creation when the logger
     * is disabled for the TRACE level.
     *
     * @param format the format string
     * @param arg    the argument
     */
    fun trace(format: String?, arg: Any?)

    /**
     * Log a message at the TRACE level according to the specified format
     * and arguments.
     *
     * This form avoids superfluous object creation when the logger
     * is disabled for the TRACE level.
     *
     * @param format the format string
     * @param argA   the first argument
     * @param argB   the second argument
     */
    fun trace(format: String?, argA: Any?, argB: Any?)

    /**
     * Log a message at the TRACE level according to the specified format
     * and arguments.
     *
     * This form avoids superfluous string concatenation when the logger
     * is disabled for the TRACE level. However, this variant incurs the hidden
     * (and relatively small) cost of creating an `Object[]` before invoking the method,
     * even if this logger is disabled for TRACE. The variants taking
     * [one][trace] and [two][trace] arguments exist solely in order to avoid this hidden cost.
     *
     * @param format    the format string
     * @param arguments a list of 3 or more arguments
     */
    fun trace(format: String?, vararg arguments: Any?)

    /**
     * Log an exception (throwable) at the TRACE level with an
     * accompanying message.
     *
     * @param msg the message accompanying the exception
     * @param t   the exception (throwable) to log
     */
    fun trace(msg: String?, t: Throwable?)

    /**
     * Log an exception (throwable) at the TRACE level.
     *
     * @param t the exception (throwable) to log
     */
    fun trace(t: Throwable?)

    // --- DEBUG ---

    /**
     * Is the logger instance enabled for the DEBUG level?
     *
     * @return True if this Logger is enabled for the DEBUG level,
     *         false otherwise.
     */
    fun isDebugEnabled(): Boolean

    /**
     * Log a message at the DEBUG level.
     *
     * @param msg the message string to be logged
     */
    fun debug(msg: String?)

    /**
     * Log a message at the DEBUG level according to the specified format
     * and argument.
     *
     * This form avoids superfluous object creation when the logger
     * is disabled for the DEBUG level.
     *
     * @param format the format string
     * @param arg    the argument
     */
    fun debug(format: String?, arg: Any?)

    /**
     * Log a message at the DEBUG level according to the specified format
     * and arguments.
     *
     * This form avoids superfluous object creation when the logger
     * is disabled for the DEBUG level.
     *
     * @param format the format string
     * @param argA   the first argument
     * @param argB   the second argument
     */
    fun debug(format: String?, argA: Any?, argB: Any?)

    /**
     * Log a message at the DEBUG level according to the specified format
     * and arguments.
     *
     * This form avoids superfluous string concatenation when the logger
     * is disabled for the DEBUG level. However, this variant incurs the hidden
     * (and relatively small) cost of creating an `Object[]` before invoking the method,
     * even if this logger is disabled for DEBUG. The variants taking
     * [one][debug] and [two][debug] arguments exist solely in order to avoid this hidden cost.
     *
     * @param format    the format string
     * @param arguments a list of 3 or more arguments
     */
    fun debug(format: String?, vararg arguments: Any?)

    /**
     * Log an exception (throwable) at the DEBUG level with an
     * accompanying message.
     *
     * @param msg the message accompanying the exception
     * @param t   the exception (throwable) to log
     */
    fun debug(msg: String?, t: Throwable?)

    /**
     * Log an exception (throwable) at the DEBUG level.
     *
     * @param t the exception (throwable) to log
     */
    fun debug(t: Throwable?)

    // --- INFO ---

    /**
     * Is the logger instance enabled for the INFO level?
     *
     * @return True if this Logger is enabled for the INFO level,
     *         false otherwise.
     */
    fun isInfoEnabled(): Boolean

    /**
     * Log a message at the INFO level.
     *
     * @param msg the message string to be logged
     */
    fun info(msg: String?)

    /**
     * Log a message at the INFO level according to the specified format
     * and argument.
     *
     * This form avoids superfluous object creation when the logger
     * is disabled for the INFO level.
     *
     * @param format the format string
     * @param arg    the argument
     */
    fun info(format: String?, arg: Any?)

    /**
     * Log a message at the INFO level according to the specified format
     * and arguments.
     *
     * This form avoids superfluous object creation when the logger
     * is disabled for the INFO level.
     *
     * @param format the format string
     * @param argA   the first argument
     * @param argB   the second argument
     */
    fun info(format: String?, argA: Any?, argB: Any?)

    /**
     * Log a message at the INFO level according to the specified format
     * and arguments.
     *
     * This form avoids superfluous string concatenation when the logger
     * is disabled for the INFO level. However, this variant incurs the hidden
     * (and relatively small) cost of creating an `Object[]` before invoking the method,
     * even if this logger is disabled for INFO. The variants taking
     * [one][info] and [two][info] arguments exist solely in order to avoid this hidden cost.
     *
     * @param format    the format string
     * @param arguments a list of 3 or more arguments
     */
    fun info(format: String?, vararg arguments: Any?)

    /**
     * Log an exception (throwable) at the INFO level with an
     * accompanying message.
     *
     * @param msg the message accompanying the exception
     * @param t   the exception (throwable) to log
     */
    fun info(msg: String?, t: Throwable?)

    /**
     * Log an exception (throwable) at the INFO level.
     *
     * @param t the exception (throwable) to log
     */
    fun info(t: Throwable?)

    // --- WARN ---

    /**
     * Is the logger instance enabled for the WARN level?
     *
     * @return True if this Logger is enabled for the WARN level,
     *         false otherwise.
     */
    fun isWarnEnabled(): Boolean

    /**
     * Log a message at the WARN level.
     *
     * @param msg the message string to be logged
     */
    fun warn(msg: String?)

    /**
     * Log a message at the WARN level according to the specified format
     * and argument.
     *
     * This form avoids superfluous object creation when the logger
     * is disabled for the WARN level.
     *
     * @param format the format string
     * @param arg    the argument
     */
    fun warn(format: String?, arg: Any?)

    /**
     * Log a message at the WARN level according to the specified format
     * and arguments.
     *
     * This form avoids superfluous object creation when the logger
     * is disabled for the WARN level.
     *
     * @param format the format string
     * @param argA   the first argument
     * @param argB   the second argument
     */
    fun warn(format: String?, argA: Any?, argB: Any?)

    /**
     * Log a message at the WARN level according to the specified format
     * and arguments.
     *
     * This form avoids superfluous string concatenation when the logger
     * is disabled for the WARN level. However, this variant incurs the hidden
     * (and relatively small) cost of creating an `Object[]` before invoking the method,
     * even if this logger is disabled for WARN. The variants taking
     * [one][warn] and [two][warn] arguments exist solely in order to avoid this hidden cost.
     *
     * @param format    the format string
     * @param arguments a list of 3 or more arguments
     */
    fun warn(format: String?, vararg arguments: Any?)

    /**
     * Log an exception (throwable) at the WARN level with an
     * accompanying message.
     *
     * @param msg the message accompanying the exception
     * @param t   the exception (throwable) to log
     */
    fun warn(msg: String?, t: Throwable?)

    /**
     * Log an exception (throwable) at the WARN level.
     *
     * @param t the exception (throwable) to log
     */
    fun warn(t: Throwable?)

    // --- ERROR ---

    /**
     * Is the logger instance enabled for the ERROR level?
     *
     * @return True if this Logger is enabled for the ERROR level,
     *         false otherwise.
     */
    fun isErrorEnabled(): Boolean

    /**
     * Log a message at the ERROR level.
     *
     * @param msg the message string to be logged
     */
    fun error(msg: String?)

    /**
     * Log a message at the ERROR level according to the specified format
     * and argument.
     *
     * This form avoids superfluous object creation when the logger
     * is disabled for the ERROR level.
     *
     * @param format the format string
     * @param arg    the argument
     */
    fun error(format: String?, arg: Any?)

    /**
     * Log a message at the ERROR level according to the specified format
     * and arguments.
     *
     * This form avoids superfluous object creation when the logger
     * is disabled for the ERROR level.
     *
     * @param format the format string
     * @param argA   the first argument
     * @param argB   the second argument
     */
    fun error(format: String?, argA: Any?, argB: Any?)

    /**
     * Log a message at the ERROR level according to the specified format
     * and arguments.
     *
     * This form avoids superfluous string concatenation when the logger
     * is disabled for the ERROR level. However, this variant incurs the hidden
     * (and relatively small) cost of creating an `Object[]` before invoking the method,
     * even if this logger is disabled for ERROR. The variants taking
     * [one][error] and [two][error] arguments exist solely in order to avoid this hidden cost.
     *
     * @param format    the format string
     * @param arguments a list of 3 or more arguments
     */
    fun error(format: String?, vararg arguments: Any?)

    /**
     * Log an exception (throwable) at the ERROR level with an
     * accompanying message.
     *
     * @param msg the message accompanying the exception
     * @param t   the exception (throwable) to log
     */
    fun error(msg: String?, t: Throwable?)

    /**
     * Log an exception (throwable) at the ERROR level.
     *
     * @param t the exception (throwable) to log
     */
    fun error(t: Throwable?)

    // --- Generic level methods ---

    /**
     * Is the logger instance enabled for the specified [level]?
     *
     * @return True if this Logger is enabled for the specified [level],
     *         false otherwise.
     */
    fun isEnabled(level: InternalLogLevel): Boolean

    /**
     * Log a message at the specified [level].
     *
     * @param msg the message string to be logged
     */
    fun log(level: InternalLogLevel, msg: String?)

    /**
     * Log a message at the specified [level] according to the specified format
     * and argument.
     *
     * This form avoids superfluous object creation when the logger
     * is disabled for the specified [level].
     *
     * @param format the format string
     * @param arg    the argument
     */
    fun log(level: InternalLogLevel, format: String?, arg: Any?)

    /**
     * Log a message at the specified [level] according to the specified format
     * and arguments.
     *
     * This form avoids superfluous object creation when the logger
     * is disabled for the specified [level].
     *
     * @param format the format string
     * @param argA   the first argument
     * @param argB   the second argument
     */
    fun log(level: InternalLogLevel, format: String?, argA: Any?, argB: Any?)

    /**
     * Log a message at the specified [level] according to the specified format
     * and arguments.
     *
     * This form avoids superfluous string concatenation when the logger
     * is disabled for the specified [level]. However, this variant incurs the hidden
     * (and relatively small) cost of creating an `Object[]` before invoking the method,
     * even if this logger is disabled for the specified [level]. The variants taking
     * [one][log] and [two][log] arguments exist solely in order to avoid this hidden cost.
     *
     * @param format    the format string
     * @param arguments a list of 3 or more arguments
     */
    fun log(level: InternalLogLevel, format: String?, vararg arguments: Any?)

    /**
     * Log an exception (throwable) at the specified [level] with an
     * accompanying message.
     *
     * @param msg the message accompanying the exception
     * @param t   the exception (throwable) to log
     */
    fun log(level: InternalLogLevel, msg: String?, t: Throwable?)

    /**
     * Log an exception (throwable) at the specified [level].
     *
     * @param t the exception (throwable) to log
     */
    fun log(level: InternalLogLevel, t: Throwable?)
}
