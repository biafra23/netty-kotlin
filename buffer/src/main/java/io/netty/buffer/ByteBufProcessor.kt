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
package io.netty.buffer

import io.netty.util.ByteProcessor

/**
 * @deprecated Use [ByteProcessor].
 */
@Deprecated("Use ByteProcessor")
interface ByteBufProcessor : ByteProcessor {

    companion object {
        /**
         * @deprecated Use [ByteProcessor.FIND_NUL].
         */
        @JvmField
        @Deprecated("Use ByteProcessor.FIND_NUL")
        val FIND_NUL: ByteBufProcessor = object : ByteBufProcessor {
            @Throws(Exception::class)
            override fun process(value: Byte): Boolean = value.toInt() != 0
        }

        /**
         * @deprecated Use [ByteProcessor.FIND_NON_NUL].
         */
        @JvmField
        @Deprecated("Use ByteProcessor.FIND_NON_NUL")
        val FIND_NON_NUL: ByteBufProcessor = object : ByteBufProcessor {
            @Throws(Exception::class)
            override fun process(value: Byte): Boolean = value.toInt() == 0
        }

        /**
         * @deprecated Use [ByteProcessor.FIND_CR].
         */
        @JvmField
        @Deprecated("Use ByteProcessor.FIND_CR")
        val FIND_CR: ByteBufProcessor = object : ByteBufProcessor {
            @Throws(Exception::class)
            override fun process(value: Byte): Boolean = value != '\r'.code.toByte()
        }

        /**
         * @deprecated Use [ByteProcessor.FIND_NON_CR].
         */
        @JvmField
        @Deprecated("Use ByteProcessor.FIND_NON_CR")
        val FIND_NON_CR: ByteBufProcessor = object : ByteBufProcessor {
            @Throws(Exception::class)
            override fun process(value: Byte): Boolean = value == '\r'.code.toByte()
        }

        /**
         * @deprecated Use [ByteProcessor.FIND_LF].
         */
        @JvmField
        @Deprecated("Use ByteProcessor.FIND_LF")
        val FIND_LF: ByteBufProcessor = object : ByteBufProcessor {
            @Throws(Exception::class)
            override fun process(value: Byte): Boolean = value != '\n'.code.toByte()
        }

        /**
         * @deprecated Use [ByteProcessor.FIND_NON_LF].
         */
        @JvmField
        @Deprecated("Use ByteProcessor.FIND_NON_LF")
        val FIND_NON_LF: ByteBufProcessor = object : ByteBufProcessor {
            @Throws(Exception::class)
            override fun process(value: Byte): Boolean = value == '\n'.code.toByte()
        }

        /**
         * @deprecated Use [ByteProcessor.FIND_CRLF].
         */
        @JvmField
        @Deprecated("Use ByteProcessor.FIND_CRLF")
        val FIND_CRLF: ByteBufProcessor = object : ByteBufProcessor {
            @Throws(Exception::class)
            override fun process(value: Byte): Boolean =
                value != '\r'.code.toByte() && value != '\n'.code.toByte()
        }

        /**
         * @deprecated Use [ByteProcessor.FIND_NON_CRLF].
         */
        @JvmField
        @Deprecated("Use ByteProcessor.FIND_NON_CRLF")
        val FIND_NON_CRLF: ByteBufProcessor = object : ByteBufProcessor {
            @Throws(Exception::class)
            override fun process(value: Byte): Boolean =
                value == '\r'.code.toByte() || value == '\n'.code.toByte()
        }

        /**
         * @deprecated Use [ByteProcessor.FIND_LINEAR_WHITESPACE].
         */
        @JvmField
        @Deprecated("Use ByteProcessor.FIND_LINEAR_WHITESPACE")
        val FIND_LINEAR_WHITESPACE: ByteBufProcessor = object : ByteBufProcessor {
            @Throws(Exception::class)
            override fun process(value: Byte): Boolean =
                value != ' '.code.toByte() && value != '\t'.code.toByte()
        }

        /**
         * @deprecated Use [ByteProcessor.FIND_NON_LINEAR_WHITESPACE].
         */
        @JvmField
        @Deprecated("Use ByteProcessor.FIND_NON_LINEAR_WHITESPACE")
        val FIND_NON_LINEAR_WHITESPACE: ByteBufProcessor = object : ByteBufProcessor {
            @Throws(Exception::class)
            override fun process(value: Byte): Boolean =
                value == ' '.code.toByte() || value == '\t'.code.toByte()
        }
    }
}
