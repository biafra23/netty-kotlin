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

import java.util.Arrays

internal class DefaultFutureListeners(
    first: GenericFutureListener<out Future<*>>,
    second: GenericFutureListener<out Future<*>>
) {
    private var _listeners: Array<GenericFutureListener<out Future<*>>?>
    private var _size: Int
    private var _progressiveSize: Int = 0 // the number of progressive listeners

    init {
        @Suppress("UNCHECKED_CAST")
        _listeners = arrayOfNulls<GenericFutureListener<out Future<*>>>(2)
        _listeners[0] = first
        _listeners[1] = second
        _size = 2
        if (first is GenericProgressiveFutureListener<*>) {
            _progressiveSize++
        }
        if (second is GenericProgressiveFutureListener<*>) {
            _progressiveSize++
        }
    }

    fun add(l: GenericFutureListener<out Future<*>>) {
        var listeners = _listeners
        val size = _size
        if (size == listeners.size) {
            _listeners = Arrays.copyOf(listeners, size shl 1)
            listeners = _listeners
        }
        listeners[size] = l
        _size = size + 1

        if (l is GenericProgressiveFutureListener<*>) {
            _progressiveSize++
        }
    }

    fun remove(l: GenericFutureListener<out Future<*>>) {
        val listeners = _listeners
        var size = _size
        for (i in 0 until size) {
            if (listeners[i] === l) {
                val listenersToMove = size - i - 1
                if (listenersToMove > 0) {
                    System.arraycopy(listeners, i + 1, listeners, i, listenersToMove)
                }
                listeners[--size] = null
                _size = size

                if (l is GenericProgressiveFutureListener<*>) {
                    _progressiveSize--
                }
                return
            }
        }
    }

    fun listeners(): Array<GenericFutureListener<out Future<*>>?> = _listeners

    fun size(): Int = _size

    fun progressiveSize(): Int = _progressiveSize
}
