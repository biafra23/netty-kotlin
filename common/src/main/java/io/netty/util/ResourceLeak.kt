/*
 * Copyright 2013 The Netty Project
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
 * @deprecated please use [ResourceLeakTracker] as it may lead to false-positives.
 */
@Deprecated("please use ResourceLeakTracker as it may lead to false-positives")
public interface ResourceLeak {
    /**
     * Records the caller's current stack trace so that the [ResourceLeakDetector] can tell where the leaked
     * resource was accessed lastly. This method is a shortcut to [record(null)][record].
     */
    public fun record()

    /**
     * Records the caller's current stack trace and the specified additional arbitrary information
     * so that the [ResourceLeakDetector] can tell where the leaked resource was accessed lastly.
     */
    public fun record(hint: Any?)

    /**
     * Close the leak so that [ResourceLeakDetector] does not warn about leaked resources.
     *
     * @return `true` if called first time, `false` if called already
     */
    public fun close(): Boolean
}
