/*
 * Copyright 2014 The Netty Project
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

import io.netty.util.internal.ObjectUtil.checkNotNull
import io.netty.util.internal.StringUtil
import io.netty.util.internal.StringUtil.commonSuffixOfLength
import java.net.IDN
import java.util.Collections
import java.util.LinkedHashMap
import java.util.Locale

/**
 * Maps a domain name to its associated value object.
 *
 * DNS wildcard is supported as hostname, so you can use `*.netty.io` to match both `netty.io`
 * and `downloads.netty.io`.
 *
 * @deprecated Use [DomainWildcardMappingBuilder]
 */
@Deprecated("Use DomainWildcardMappingBuilder")
open class DomainNameMapping<V> : Mapping<String, V> {

    @JvmField
    val defaultValue: V
    private val map: Map<String, V>?
    private val unmodifiableMap: Map<String, V>?

    /**
     * Creates a default, order-sensitive mapping. If your hostnames are in conflict, the mapping
     * will choose the one you add first.
     *
     * @param defaultValue the default value for [map] to return when nothing matches the input
     * @deprecated use [DomainNameMappingBuilder] to create and fill the mapping instead
     */
    @Deprecated("use DomainNameMappingBuilder to create and fill the mapping instead")
    constructor(defaultValue: V) : this(4, defaultValue)

    /**
     * Creates a default, order-sensitive mapping. If your hostnames are in conflict, the mapping
     * will choose the one you add first.
     *
     * @param initialCapacity initial capacity for the internal map
     * @param defaultValue    the default value for [map] to return when nothing matches the input
     * @deprecated use [DomainNameMappingBuilder] to create and fill the mapping instead
     */
    @Deprecated("use DomainNameMappingBuilder to create and fill the mapping instead")
    constructor(initialCapacity: Int, defaultValue: V) : this(LinkedHashMap<String, V>(initialCapacity), defaultValue)

    internal constructor(map: Map<String, V>?, defaultValue: V) {
        this.defaultValue = checkNotNull(defaultValue, "defaultValue")
        this.map = map
        unmodifiableMap = if (map != null) Collections.unmodifiableMap(map) else null
    }

    /**
     * Adds a mapping that maps the specified (optionally wildcard) host name to the specified output value.
     *
     * [DNS wildcard](https://en.wikipedia.org/wiki/Wildcard_DNS_record) is supported as hostname.
     * For example, you can use `*.netty.io` to match `netty.io` and `downloads.netty.io`.
     *
     * @param hostname the host name (optionally wildcard)
     * @param output   the output value that will be returned by [map] when the specified host name
     *                 matches the specified input host name
     * @deprecated use [DomainNameMappingBuilder] to create and fill the mapping instead
     */
    @Deprecated("use DomainNameMappingBuilder to create and fill the mapping instead")
    open fun add(hostname: String, output: V): DomainNameMapping<V> {
        (map as MutableMap<String, V>).put(
            normalizeHostname(checkNotNull(hostname, "hostname")),
            checkNotNull(output, "output")
        )
        return this
    }

    override fun map(hostname: String): V {
        @Suppress("NAME_SHADOWING")
        val hostname = normalizeHostname(hostname)

        for ((key, value) in map!!.entries) {
            if (matches(key, hostname)) {
                return value
            }
        }
        return defaultValue
    }

    /**
     * Returns a read-only [Map] of the domain mapping patterns and their associated value objects.
     */
    open fun asMap(): Map<String, V>? {
        return unmodifiableMap
    }

    override fun toString(): String {
        return StringUtil.simpleClassName(this) + "(default: " + defaultValue + ", map: " + map + ')'
    }

    companion object {
        /**
         * Simple function to match [DNS wildcard](https://en.wikipedia.org/wiki/Wildcard_DNS_record).
         */
        @JvmStatic
        fun matches(template: String, hostName: String): Boolean {
            if (template.startsWith("*.")) {
                return template.regionMatches(2, hostName, 0, hostName.length) ||
                    commonSuffixOfLength(hostName, template, template.length - 1)
            }
            return template == hostName
        }

        /**
         * IDNA ASCII conversion and case normalization
         */
        @JvmStatic
        fun normalizeHostname(hostname: String): String {
            @Suppress("NAME_SHADOWING")
            var hostname = hostname
            if (needsNormalization(hostname)) {
                hostname = IDN.toASCII(hostname, IDN.ALLOW_UNASSIGNED)
            }
            return hostname.lowercase(Locale.US)
        }

        private fun needsNormalization(hostname: String): Boolean {
            val length = hostname.length
            for (i in 0 until length) {
                val c = hostname[i].code
                if (c > 0x7F) {
                    return true
                }
            }
            return false
        }
    }
}
