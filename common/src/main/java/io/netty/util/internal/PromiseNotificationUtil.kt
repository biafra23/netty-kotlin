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
package io.netty.util.internal

import io.netty.util.concurrent.Promise
import io.netty.util.internal.logging.InternalLogger

/**
 * Internal utilities to notify [Promise]s.
 */
object PromiseNotificationUtil {

    /**
     * Try to cancel the [Promise] and log if [logger] is not `null` in case this fails.
     */
    @JvmStatic
    fun tryCancel(p: Promise<*>, logger: InternalLogger?) {
        if (!p.cancel(false) && logger != null) {
            val err = p.cause()
            if (err == null) {
                logger.warn("Failed to cancel promise because it has succeeded already: {}", p)
            } else {
                logger.warn(
                    "Failed to cancel promise because it has failed already: {}, unnotified cause:",
                    p, err
                )
            }
        }
    }

    /**
     * Try to mark the [Promise] as success and log if [logger] is not `null` in case this fails.
     */
    @JvmStatic
    fun <V> trySuccess(p: Promise<in V>, result: V, logger: InternalLogger?) {
        if (!p.trySuccess(result) && logger != null) {
            val err = p.cause()
            if (err == null) {
                logger.warn("Failed to mark a promise as success because it has succeeded already: {}", p)
            } else {
                logger.warn(
                    "Failed to mark a promise as success because it has failed already: {}, unnotified cause:",
                    p, err
                )
            }
        }
    }

    /**
     * Try to mark the [Promise] as failure and log if [logger] is not `null` in case this fails.
     */
    @JvmStatic
    fun tryFailure(p: Promise<*>, cause: Throwable, logger: InternalLogger?) {
        if (!p.tryFailure(cause) && logger != null) {
            val err = p.cause()
            if (err == null) {
                logger.warn("Failed to mark a promise as failure because it has succeeded already: {}", p, cause)
            } else if (logger.isWarnEnabled()) {
                logger.warn(
                    "Failed to mark a promise as failure because it has failed already: {}, unnotified cause:",
                    p, cause
                )
            }
        }
    }
}
