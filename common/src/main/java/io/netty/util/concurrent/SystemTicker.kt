/*
 * Copyright 2025 The Netty Project
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

import java.util.Objects
import java.util.concurrent.TimeUnit

internal class SystemTicker : Ticker {

    companion object {
        @JvmField
        val INSTANCE = SystemTicker()
        private val START_TIME = System.nanoTime()
    }

    override fun initialNanoTime(): Long {
        return START_TIME
    }

    override fun nanoTime(): Long {
        return System.nanoTime() - START_TIME
    }

    @Throws(InterruptedException::class)
    override fun sleep(delay: Long, unit: TimeUnit) {
        Objects.requireNonNull(unit, "unit")
        unit.sleep(delay)
    }
}
