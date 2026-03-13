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
package io.netty.util.internal

import java.nio.ByteBuffer

/**
 * Encapsulates a direct [ByteBuffer] and its mechanism for immediate deallocation, if any.
 */
interface CleanableDirectBuffer {
    /**
     * Get the buffer instance.
     *
     * Note: the buffer must not be accessed after the [clean] method has been called.
     *
     * @return The [ByteBuffer] instance.
     */
    fun buffer(): ByteBuffer

    /**
     * Deallocate the buffer. This method can only be called once per instance,
     * and all usages of the buffer must have ceased before this method is called,
     * and the buffer must not be accessed again after this method has been called.
     */
    fun clean()

    /**
     * @return `true` if the [native memory address][memoryAddress] is available,
     * otherwise `false`.
     */
    fun hasMemoryAddress(): Boolean = false

    /**
     * Get the native memory address, but only if [hasMemoryAddress] returns true,
     * otherwise this may return an unspecified value or throw an exception.
     * @return The native memory address of this buffer, if available.
     */
    fun memoryAddress(): Long = 0
}
