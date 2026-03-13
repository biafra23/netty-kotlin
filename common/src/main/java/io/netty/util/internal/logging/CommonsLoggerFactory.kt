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

import org.apache.commons.logging.LogFactory

/**
 * Logger factory which creates an
 * [Apache Commons Logging](https://commons.apache.org/logging/)
 * logger.
 *
 * @deprecated Please use [Log4J2LoggerFactory] or [Log4JLoggerFactory] or
 * [Slf4JLoggerFactory].
 */
@Deprecated("Please use Log4J2LoggerFactory or Log4JLoggerFactory or Slf4JLoggerFactory.")
open class CommonsLoggerFactory
/**
 * @deprecated Use [INSTANCE] instead.
 */
@Deprecated("Use INSTANCE instead.")
constructor() : InternalLoggerFactory() {

    override fun newInstance(name: String): InternalLogger =
        CommonsLogger(LogFactory.getLog(name), name)

    companion object {
        @JvmField
        val INSTANCE: InternalLoggerFactory = CommonsLoggerFactory()
    }
}
