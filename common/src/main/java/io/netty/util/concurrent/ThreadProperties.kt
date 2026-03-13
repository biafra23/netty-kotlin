/*
 * Copyright 2015 The Netty Project
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
package io.netty.util.concurrent

/**
 * Expose details for a [Thread].
 */
interface ThreadProperties {
    /**
     * @see Thread.getState
     */
    fun state(): Thread.State

    /**
     * @see Thread.getPriority
     */
    fun priority(): Int

    /**
     * @see Thread.isInterrupted
     */
    fun isInterrupted(): Boolean

    /**
     * @see Thread.isDaemon
     */
    fun isDaemon(): Boolean

    /**
     * @see Thread.getName
     */
    fun name(): String

    /**
     * @see Thread.getId
     */
    fun id(): Long

    /**
     * @see Thread.getStackTrace
     */
    fun stackTrace(): Array<StackTraceElement>

    /**
     * @see Thread.isAlive
     */
    fun isAlive(): Boolean
}
