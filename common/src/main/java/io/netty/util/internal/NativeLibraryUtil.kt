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

/**
 * A Utility to Call the [System.load] or [System.loadLibrary].
 * Because the [System.load] and [System.loadLibrary] are both
 * CallerSensitive, it will load the native library into its caller's [ClassLoader].
 * In OSGi environment, we need this helper to delegate the calling to [System.load]
 * and it should be as simple as possible. It will be injected into the native library's
 * ClassLoader when it is undefined. And therefore, when the defined new helper is invoked,
 * the native library would be loaded into the native library's ClassLoader, not the
 * caller's ClassLoader.
 */
internal object NativeLibraryUtil {

    /**
     * Delegate the calling to [System.load] or [System.loadLibrary].
     * @param libName The native library path or name
     * @param absolute Whether the native library will be loaded by path or by name
     */
    @JvmStatic
    fun loadLibrary(libName: String, absolute: Boolean) {
        if (absolute) {
            System.load(libName)
        } else {
            System.loadLibrary(libName)
        }
    }
}
