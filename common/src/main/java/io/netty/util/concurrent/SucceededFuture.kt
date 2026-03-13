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
package io.netty.util.concurrent

/**
 * The [CompleteFuture] which is succeeded already. It is
 * recommended to use [EventExecutor.newSucceededFuture] instead of
 * calling the constructor of this future.
 */
class SucceededFuture<V>(executor: EventExecutor, private val result: V) : CompleteFuture<V>(executor) {

    override fun cause(): Throwable? = null

    override fun isSuccess(): Boolean = true

    override fun getNow(): V = result
}
