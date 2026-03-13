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
package io.netty.util.concurrent

import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Abstract [Future] implementation which does not allow for cancellation.
 */
abstract class AbstractFuture<V> : Future<V> {

    @Throws(InterruptedException::class, ExecutionException::class)
    override fun get(): V? {
        await()

        val cause = cause()
        if (cause == null) {
            return getNow()
        }
        if (cause is CancellationException) {
            throw cause
        }
        throw ExecutionException(cause)
    }

    @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
    override fun get(timeout: Long, unit: TimeUnit): V? {
        if (await(timeout, unit)) {
            val cause = cause()
            if (cause == null) {
                return getNow()
            }
            if (cause is CancellationException) {
                throw cause
            }
            throw ExecutionException(cause)
        }
        throw TimeoutException("timeout after $timeout ${unit.name.lowercase(Locale.ENGLISH)}")
    }
}
