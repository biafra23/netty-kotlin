/*
 * Copyright 2020 The Netty Project
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
import java.util.LinkedHashMap

/**
 * Builder that allows to build [Mapping]s that support
 * [DNS wildcard](https://tools.ietf.org/search/rfc6125#section-6.4) matching.
 * @param V the type of the value that we map to.
 */
open class DomainWildcardMappingBuilder<V> {

    private val defaultValue: V
    private val map: MutableMap<String, V>

    /**
     * Constructor with default initial capacity of the map holding the mappings
     *
     * @param defaultValue the default value for [Mapping.map] to return
     *                     when nothing matches the input
     */
    constructor(defaultValue: V) : this(4, defaultValue)

    /**
     * Constructor with initial capacity of the map holding the mappings
     *
     * @param initialCapacity initial capacity for the internal map
     * @param defaultValue    the default value for [Mapping.map] to return
     *                        when nothing matches the input
     */
    constructor(initialCapacity: Int, defaultValue: V) {
        this.defaultValue = checkNotNull(defaultValue, "defaultValue")
        map = LinkedHashMap(initialCapacity)
    }

    /**
     * Adds a mapping that maps the specified (optionally wildcard) host name to the specified output value.
     * `null` values are forbidden for both hostnames and values.
     *
     * [DNS wildcard](https://tools.ietf.org/search/rfc6125#section-6.4) is supported as hostname. The
     * wildcard will only match one sub-domain deep and only when wildcard is used as the most-left label.
     *
     * For example:
     *
     * *.netty.io will match xyz.netty.io but NOT abc.xyz.netty.io
     *
     * @param hostname the host name (optionally wildcard)
     * @param output   the output value that will be returned by [Mapping.map]
     *                 when the specified host name matches the specified input host name
     */
    fun add(hostname: String, output: V): DomainWildcardMappingBuilder<V> {
        map[normalizeHostName(hostname)] = checkNotNull(output, "output")
        return this
    }

    private fun normalizeHostName(hostname: String): String {
        checkNotNull(hostname, "hostname")
        if (hostname.isEmpty() || hostname[0] == '.') {
            throw IllegalArgumentException("Hostname '$hostname' not valid")
        }
        @Suppress("NAME_SHADOWING")
        val hostname = ImmutableDomainWildcardMapping.normalize(checkNotNull(hostname, "hostname"))
        if (hostname[0] == '*') {
            if (hostname.length < 3 || hostname[1] != '.') {
                throw IllegalArgumentException("Wildcard Hostname '$hostname'not valid")
            }
            return hostname.substring(1)
        }
        return hostname
    }

    /**
     * Creates a new instance of an immutable [Mapping].
     *
     * @return new [Mapping] instance
     */
    fun build(): Mapping<String, V> {
        return ImmutableDomainWildcardMapping(defaultValue, map)
    }

    private class ImmutableDomainWildcardMapping<V>(
        private val defaultValue: V,
        map: Map<String, V>
    ) : Mapping<String, V> {

        private val map: Map<String, V> = LinkedHashMap(map)

        override fun map(hostname: String): V {
            @Suppress("NAME_SHADOWING")
            val hostname = normalize(hostname)

            // Let's try an exact match first
            val value = map[hostname]
            if (value != null) {
                return value
            }

            // No exact match, let's try a wildcard match.
            val idx = hostname.indexOf('.')
            if (idx != -1) {
                val wildcardValue = map[hostname.substring(idx)]
                if (wildcardValue != null) {
                    return wildcardValue
                }
            }

            return defaultValue
        }

        override fun toString(): String {
            val sb = StringBuilder()
            sb.append(REPR_HEADER).append(defaultValue).append(REPR_MAP_OPENING).append('{')

            for ((key, value) in map.entries) {
                var hostname = key
                if (hostname[0] == '.') {
                    hostname = "*$hostname"
                }
                sb.append(hostname).append('=').append(value).append(", ")
            }
            sb.setLength(sb.length - 2)
            return sb.append('}').append(REPR_MAP_CLOSING).toString()
        }

        companion object {
            private const val REPR_HEADER = "ImmutableDomainWildcardMapping(default: "
            private const val REPR_MAP_OPENING = ", map: "
            private const val REPR_MAP_CLOSING = ")"

            @Suppress("DEPRECATION")
            @JvmStatic
            fun normalize(hostname: String): String {
                return DomainNameMapping.normalizeHostname(hostname)
            }
        }
    }
}
