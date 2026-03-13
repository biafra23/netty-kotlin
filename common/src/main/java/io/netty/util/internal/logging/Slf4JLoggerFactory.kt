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
import org.slf4j.LoggerFactory
import org.slf4j.helpers.NOPLoggerFactory
import org.slf4j.spi.LocationAwareLogger

/**
 * Logger factory which creates a [SLF4J](https://www.slf4j.org/) logger.
 */
open class Slf4JLoggerFactory : InternalLoggerFactory {

    companion object {
        @JvmField
        @Suppress("DEPRECATION")
        val INSTANCE: InternalLoggerFactory = Slf4JLoggerFactory()

        @JvmStatic
        fun wrapLogger(logger: Logger): InternalLogger =
            if (logger is LocationAwareLogger) LocationAwareSlf4JLogger(logger)
            else Slf4JLogger(logger)

        internal fun getInstanceWithNopCheck(): InternalLoggerFactory = NopInstanceHolder.INSTANCE_WITH_NOP_CHECK
    }

    /**
     * @deprecated Use [INSTANCE] instead.
     */
    @Deprecated("Use INSTANCE instead")
    constructor()

    internal constructor(failIfNOP: Boolean) {
        assert(failIfNOP) // Should be always called with true.
        if (LoggerFactory.getILoggerFactory() is NOPLoggerFactory) {
            throw NoClassDefFoundError("NOPLoggerFactory not supported")
        }
    }

    override fun newInstance(name: String): InternalLogger = wrapLogger(LoggerFactory.getLogger(name))

    private object NopInstanceHolder {
        val INSTANCE_WITH_NOP_CHECK: InternalLoggerFactory = Slf4JLoggerFactory(true)
    }
}
