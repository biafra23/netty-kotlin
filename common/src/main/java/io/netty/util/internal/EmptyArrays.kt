/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at:
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

import io.netty.util.AsciiString
import java.nio.ByteBuffer
import java.security.cert.Certificate
import java.security.cert.X509Certificate

object EmptyArrays {
    @JvmField val EMPTY_INTS: IntArray = intArrayOf()
    @JvmField val EMPTY_BYTES: ByteArray = byteArrayOf()
    @JvmField val EMPTY_CHARS: CharArray = charArrayOf()
    @JvmField val EMPTY_OBJECTS: Array<Any> = emptyArray()
    @JvmField val EMPTY_CLASSES: Array<Class<*>> = emptyArray()
    @JvmField val EMPTY_STRINGS: Array<String> = emptyArray()
    @JvmField val EMPTY_ASCII_STRINGS: Array<AsciiString> = emptyArray()
    @JvmField val EMPTY_STACK_TRACE: Array<StackTraceElement> = emptyArray()
    @JvmField val EMPTY_BYTE_BUFFERS: Array<ByteBuffer> = emptyArray()
    @JvmField val EMPTY_CERTIFICATES: Array<Certificate> = emptyArray()
    @JvmField val EMPTY_X509_CERTIFICATES: Array<X509Certificate> = emptyArray()
    @JvmField val EMPTY_JAVAX_X509_CERTIFICATES: Array<javax.security.cert.X509Certificate> = emptyArray()
    @JvmField val EMPTY_MAP_ENTRY: Array<Map.Entry<*, *>> = emptyArray()
    @JvmField val EMPTY_THROWABLES: Array<Throwable> = emptyArray()
}
