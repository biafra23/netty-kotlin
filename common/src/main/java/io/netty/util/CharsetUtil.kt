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
package io.netty.util

import io.netty.util.internal.InternalThreadLocalMap
import io.netty.util.internal.ObjectUtil.checkNotNull
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CharsetEncoder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * A utility class that provides various common operations and constants
 * related with [Charset] and its relevant classes.
 */
public object CharsetUtil {

    /**
     * 16-bit UTF (UCS Transformation Format) whose byte order is identified by
     * an optional byte-order mark
     */
    @JvmField
    public val UTF_16: Charset = StandardCharsets.UTF_16

    /**
     * 16-bit UTF (UCS Transformation Format) whose byte order is big-endian
     */
    @JvmField
    public val UTF_16BE: Charset = StandardCharsets.UTF_16BE

    /**
     * 16-bit UTF (UCS Transformation Format) whose byte order is little-endian
     */
    @JvmField
    public val UTF_16LE: Charset = StandardCharsets.UTF_16LE

    /**
     * 8-bit UTF (UCS Transformation Format)
     */
    @JvmField
    public val UTF_8: Charset = StandardCharsets.UTF_8

    /**
     * ISO Latin Alphabet No. 1, as known as ISO-LATIN-1
     */
    @JvmField
    public val ISO_8859_1: Charset = StandardCharsets.ISO_8859_1

    /**
     * 7-bit ASCII, as known as ISO646-US or the Basic Latin block of the
     * Unicode character set
     */
    @JvmField
    public val US_ASCII: Charset = StandardCharsets.US_ASCII

    private val CHARSETS: Array<Charset> = arrayOf(UTF_16, UTF_16BE, UTF_16LE, UTF_8, ISO_8859_1, US_ASCII)

    @JvmStatic
    public fun values(): Array<Charset> = CHARSETS

    /**
     * @deprecated Use [encoder].
     */
    @JvmStatic
    @Deprecated("Use encoder(Charset) instead.", replaceWith = ReplaceWith("encoder(charset)"))
    public fun getEncoder(charset: Charset): CharsetEncoder = encoder(charset)

    /**
     * Returns a new [CharsetEncoder] for the [Charset] with specified error actions.
     *
     * @param charset The specified charset
     * @param malformedInputAction The encoder's action for malformed-input errors
     * @param unmappableCharacterAction The encoder's action for unmappable-character errors
     * @return The encoder for the specified `charset`
     */
    @JvmStatic
    public fun encoder(
        charset: Charset,
        malformedInputAction: CodingErrorAction,
        unmappableCharacterAction: CodingErrorAction
    ): CharsetEncoder {
        checkNotNull(charset, "charset")
        val e = charset.newEncoder()
        e.onMalformedInput(malformedInputAction).onUnmappableCharacter(unmappableCharacterAction)
        return e
    }

    /**
     * Returns a new [CharsetEncoder] for the [Charset] with the specified error action.
     *
     * @param charset The specified charset
     * @param codingErrorAction The encoder's action for malformed-input and unmappable-character errors
     * @return The encoder for the specified `charset`
     */
    @JvmStatic
    public fun encoder(charset: Charset, codingErrorAction: CodingErrorAction): CharsetEncoder {
        return encoder(charset, codingErrorAction, codingErrorAction)
    }

    /**
     * Returns a cached thread-local [CharsetEncoder] for the specified [Charset].
     *
     * @param charset The specified charset
     * @return The encoder for the specified `charset`
     */
    @JvmStatic
    public fun encoder(charset: Charset): CharsetEncoder {
        checkNotNull(charset, "charset")

        val map = InternalThreadLocalMap.get().charsetEncoderCache()
        var e = map[charset]
        if (e != null) {
            e.reset().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE)
            return e
        }

        e = encoder(charset, CodingErrorAction.REPLACE, CodingErrorAction.REPLACE)
        map[charset] = e
        return e
    }

    /**
     * @deprecated Use [decoder].
     */
    @JvmStatic
    @Deprecated("Use decoder(Charset) instead.", replaceWith = ReplaceWith("decoder(charset)"))
    public fun getDecoder(charset: Charset): CharsetDecoder = decoder(charset)

    /**
     * Returns a new [CharsetDecoder] for the [Charset] with specified error actions.
     *
     * @param charset The specified charset
     * @param malformedInputAction The decoder's action for malformed-input errors
     * @param unmappableCharacterAction The decoder's action for unmappable-character errors
     * @return The decoder for the specified `charset`
     */
    @JvmStatic
    public fun decoder(
        charset: Charset,
        malformedInputAction: CodingErrorAction,
        unmappableCharacterAction: CodingErrorAction
    ): CharsetDecoder {
        checkNotNull(charset, "charset")
        val d = charset.newDecoder()
        d.onMalformedInput(malformedInputAction).onUnmappableCharacter(unmappableCharacterAction)
        return d
    }

    /**
     * Returns a new [CharsetDecoder] for the [Charset] with the specified error action.
     *
     * @param charset The specified charset
     * @param codingErrorAction The decoder's action for malformed-input and unmappable-character errors
     * @return The decoder for the specified `charset`
     */
    @JvmStatic
    public fun decoder(charset: Charset, codingErrorAction: CodingErrorAction): CharsetDecoder {
        return decoder(charset, codingErrorAction, codingErrorAction)
    }

    /**
     * Returns a cached thread-local [CharsetDecoder] for the specified [Charset].
     *
     * @param charset The specified charset
     * @return The decoder for the specified `charset`
     */
    @JvmStatic
    public fun decoder(charset: Charset): CharsetDecoder {
        checkNotNull(charset, "charset")

        val map = InternalThreadLocalMap.get().charsetDecoderCache()
        var d = map[charset]
        if (d != null) {
            d.reset().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE)
            return d
        }

        d = decoder(charset, CodingErrorAction.REPLACE, CodingErrorAction.REPLACE)
        map[charset] = d
        return d
    }
}
