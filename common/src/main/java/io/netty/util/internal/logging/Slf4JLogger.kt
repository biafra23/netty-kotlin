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

import org.slf4j.Logger

/**
 * [SLF4J](https://www.slf4j.org/) logger.
 */
internal class Slf4JLogger(
    @Transient private val logger: Logger
) : AbstractInternalLogger(logger.name) {

    companion object {
        private const val serialVersionUID: Long = 108038972685130825L
    }

    override fun isTraceEnabled(): Boolean = logger.isTraceEnabled

    override fun trace(msg: String) {
        logger.trace(msg)
    }

    override fun trace(format: String, arg: Any) {
        logger.trace(format, arg)
    }

    override fun trace(format: String, argA: Any, argB: Any) {
        logger.trace(format, argA, argB)
    }

    override fun trace(format: String, vararg argArray: Any) {
        logger.trace(format, *argArray)
    }

    override fun trace(msg: String, t: Throwable) {
        logger.trace(msg, t)
    }

    override fun isDebugEnabled(): Boolean = logger.isDebugEnabled

    override fun debug(msg: String) {
        logger.debug(msg)
    }

    override fun debug(format: String, arg: Any) {
        logger.debug(format, arg)
    }

    override fun debug(format: String, argA: Any, argB: Any) {
        logger.debug(format, argA, argB)
    }

    override fun debug(format: String, vararg argArray: Any) {
        logger.debug(format, *argArray)
    }

    override fun debug(msg: String, t: Throwable) {
        logger.debug(msg, t)
    }

    override fun isInfoEnabled(): Boolean = logger.isInfoEnabled

    override fun info(msg: String) {
        logger.info(msg)
    }

    override fun info(format: String, arg: Any) {
        logger.info(format, arg)
    }

    override fun info(format: String, argA: Any, argB: Any) {
        logger.info(format, argA, argB)
    }

    override fun info(format: String, vararg argArray: Any) {
        logger.info(format, *argArray)
    }

    override fun info(msg: String, t: Throwable) {
        logger.info(msg, t)
    }

    override fun isWarnEnabled(): Boolean = logger.isWarnEnabled

    override fun warn(msg: String) {
        logger.warn(msg)
    }

    override fun warn(format: String, arg: Any) {
        logger.warn(format, arg)
    }

    override fun warn(format: String, vararg argArray: Any) {
        logger.warn(format, *argArray)
    }

    override fun warn(format: String, argA: Any, argB: Any) {
        logger.warn(format, argA, argB)
    }

    override fun warn(msg: String, t: Throwable) {
        logger.warn(msg, t)
    }

    override fun isErrorEnabled(): Boolean = logger.isErrorEnabled

    override fun error(msg: String) {
        logger.error(msg)
    }

    override fun error(format: String, arg: Any) {
        logger.error(format, arg)
    }

    override fun error(format: String, argA: Any, argB: Any) {
        logger.error(format, argA, argB)
    }

    override fun error(format: String, vararg argArray: Any) {
        logger.error(format, *argArray)
    }

    override fun error(msg: String, t: Throwable) {
        logger.error(msg, t)
    }
}
