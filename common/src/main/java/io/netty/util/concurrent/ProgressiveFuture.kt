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

/**
 * A [Future] which is used to indicate the progress of an operation.
 */
interface ProgressiveFuture<V> : Future<V> {

    override fun addListener(listener: GenericFutureListener<out Future<in V>>): ProgressiveFuture<V>

    override fun addListeners(vararg listeners: GenericFutureListener<out Future<in V>>): ProgressiveFuture<V>

    override fun removeListener(listener: GenericFutureListener<out Future<in V>>): ProgressiveFuture<V>

    override fun removeListeners(vararg listeners: GenericFutureListener<out Future<in V>>): ProgressiveFuture<V>

    @Throws(InterruptedException::class)
    override fun sync(): ProgressiveFuture<V>

    override fun syncUninterruptibly(): ProgressiveFuture<V>

    @Throws(InterruptedException::class)
    override fun await(): ProgressiveFuture<V>

    override fun awaitUninterruptibly(): ProgressiveFuture<V>
}
