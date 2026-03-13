/*
 * Copyright 2024 The Netty Project
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

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

class BoundedInputStream @JvmOverloads constructor(
    `in`: InputStream,
    private val maxBytesRead: Int = 8 * 1024
) : FilterInputStream(`in`) {

    private var numRead: Int = 0

    init {
        ObjectUtil.checkPositive(maxBytesRead, "maxRead")
    }

    @Throws(IOException::class)
    override fun read(): Int {
        checkMaxBytesRead()
        val b = super.read()
        if (b != -1) {
            numRead++
        }
        return b
    }

    @Throws(IOException::class)
    override fun read(buf: ByteArray, off: Int, len: Int): Int {
        checkMaxBytesRead()
        // Calculate the maximum number of bytes that we should try to read.
        val num = min(len, maxBytesRead - numRead + 1)
        val b = super.read(buf, off, num)
        if (b != -1) {
            numRead += b
        }
        return b
    }

    @Throws(IOException::class)
    private fun checkMaxBytesRead() {
        if (numRead > maxBytesRead) {
            throw IOException("Maximum number of bytes read: $numRead")
        }
    }
}
