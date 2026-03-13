/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.util

import io.netty.util.ByteProcessorUtils.CARRIAGE_RETURN
import io.netty.util.ByteProcessorUtils.HTAB
import io.netty.util.ByteProcessorUtils.LINE_FEED
import io.netty.util.ByteProcessorUtils.SPACE

/**
 * Provides a mechanism to iterate over a collection of bytes.
 */
interface ByteProcessor {
    /**
     * A [ByteProcessor] which finds the first appearance of a specific byte.
     */
    class IndexOfProcessor(private val byteToFind: Byte) : ByteProcessor {
        override fun process(value: Byte): Boolean = value != byteToFind
    }

    /**
     * A [ByteProcessor] which finds the first appearance which is not of a specific byte.
     */
    class IndexNotOfProcessor(private val byteToNotFind: Byte) : ByteProcessor {
        override fun process(value: Byte): Boolean = value == byteToNotFind
    }

    companion object {
        /**
         * Aborts on a `NUL (0x00)`.
         */
        @JvmField
        val FIND_NUL: ByteProcessor = IndexOfProcessor(0)

        /**
         * Aborts on a non-`NUL (0x00)`.
         */
        @JvmField
        val FIND_NON_NUL: ByteProcessor = IndexNotOfProcessor(0)

        /**
         * Aborts on a `CR ('\r')`.
         */
        @JvmField
        val FIND_CR: ByteProcessor = IndexOfProcessor(CARRIAGE_RETURN)

        /**
         * Aborts on a non-`CR ('\r')`.
         */
        @JvmField
        val FIND_NON_CR: ByteProcessor = IndexNotOfProcessor(CARRIAGE_RETURN)

        /**
         * Aborts on a `LF ('\n')`.
         */
        @JvmField
        val FIND_LF: ByteProcessor = IndexOfProcessor(LINE_FEED)

        /**
         * Aborts on a non-`LF ('\n')`.
         */
        @JvmField
        val FIND_NON_LF: ByteProcessor = IndexNotOfProcessor(LINE_FEED)

        /**
         * Aborts on a semicolon `(';')`.
         */
        @JvmField
        val FIND_SEMI_COLON: ByteProcessor = IndexOfProcessor(';'.code.toByte())

        /**
         * Aborts on a comma `(',')`.
         */
        @JvmField
        val FIND_COMMA: ByteProcessor = IndexOfProcessor(','.code.toByte())

        /**
         * Aborts on a ascii space character (`' '`).
         */
        @JvmField
        val FIND_ASCII_SPACE: ByteProcessor = IndexOfProcessor(SPACE)

        /**
         * Aborts on a `CR ('\r')` or a `LF ('\n')`.
         */
        @JvmField
        val FIND_CRLF: ByteProcessor = object : ByteProcessor {
            override fun process(value: Byte): Boolean =
                value != CARRIAGE_RETURN && value != LINE_FEED
        }

        /**
         * Aborts on a byte which is neither a `CR ('\r')` nor a `LF ('\n')`.
         */
        @JvmField
        val FIND_NON_CRLF: ByteProcessor = object : ByteProcessor {
            override fun process(value: Byte): Boolean =
                value == CARRIAGE_RETURN || value == LINE_FEED
        }

        /**
         * Aborts on a linear whitespace (a `' '` or a `'\t'`).
         */
        @JvmField
        val FIND_LINEAR_WHITESPACE: ByteProcessor = object : ByteProcessor {
            override fun process(value: Byte): Boolean =
                value != SPACE && value != HTAB
        }

        /**
         * Aborts on a byte which is not a linear whitespace (neither `' '` nor `'\t'`).
         */
        @JvmField
        val FIND_NON_LINEAR_WHITESPACE: ByteProcessor = object : ByteProcessor {
            override fun process(value: Byte): Boolean =
                value == SPACE || value == HTAB
        }
    }

    /**
     * @return `true` if the processor wants to continue the loop and handle the next byte in the buffer.
     *         `false` if the processor wants to stop handling bytes and abort the loop.
     */
    @Throws(Exception::class)
    fun process(value: Byte): Boolean
}
