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
package io.netty.util

/**
 * A handle associated with a [TimerTask] that is returned by a
 * [Timer].
 */
interface Timeout {

    /**
     * Returns the [Timer] that created this handle.
     */
    fun timer(): Timer

    /**
     * Returns the [TimerTask] which is associated with this handle.
     */
    fun task(): TimerTask

    /**
     * Returns `true` if and only if the [TimerTask] associated
     * with this handle has been expired.
     */
    val isExpired: Boolean

    /**
     * Returns `true` if and only if the [TimerTask] associated
     * with this handle has been cancelled.
     */
    val isCancelled: Boolean

    /**
     * Attempts to cancel the [TimerTask] associated with this handle.
     * If the task has been executed or cancelled already, it will return with
     * no side effect.
     *
     * @return True if the cancellation completed successfully, otherwise false
     */
    fun cancel(): Boolean
}
