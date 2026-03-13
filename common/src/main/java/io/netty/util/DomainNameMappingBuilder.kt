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

package io.netty.util

import io.netty.util.internal.ObjectUtil.checkNotNull
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Builder for immutable [DomainNameMapping] instances.
 *
 * @param V concrete type of value objects
 * @deprecated Use [DomainWildcardMappingBuilder]
 */
@Deprecated("Use DomainWildcardMappingBuilder")
class DomainNameMappingBuilder<V> {

    private val defaultValue: V
    private val map: MutableMap<String, V>

    /**
     * Constructor with default initial capacity of the map holding the mappings
     *
     * @param defaultValue the default value for [DomainNameMapping.map] to return
     *                     when nothing matches the input
     */
    constructor(defaultValue: V) : this(4, defaultValue)

    /**
     * Constructor with initial capacity of the map holding the mappings
     *
     * @param initialCapacity initial capacity for the internal map
     * @param defaultValue    the default value for [DomainNameMapping.map] to return
     *                        when nothing matches the input
     */
    constructor(initialCapacity: Int, defaultValue: V) {
        this.defaultValue = checkNotNull(defaultValue, "defaultValue")
        map = LinkedHashMap(initialCapacity)
    }

    /**
     * Adds a mapping that maps the specified (optionally wildcard) host name to the specified output value.
     * Null values are forbidden for both hostnames and values.
     *
     * [DNS wildcard](https://en.wikipedia.org/wiki/Wildcard_DNS_record) is supported as hostname.
     * For example, you can use `*.netty.io` to match `netty.io` and `downloads.netty.io`.
     *
     * @param hostname the host name (optionally wildcard)
     * @param output   the output value that will be returned by [DomainNameMapping.map]
     *                 when the specified host name matches the specified input host name
     */
    fun add(hostname: String, output: V): DomainNameMappingBuilder<V> {
        map[checkNotNull(hostname, "hostname")] = checkNotNull(output, "output")
        return this
    }

    /**
     * Creates a new instance of immutable [DomainNameMapping]
     * Attempts to add new mappings to the result object will cause [UnsupportedOperationException] to be thrown
     *
     * @return new [DomainNameMapping] instance
     */
    fun build(): DomainNameMapping<V> {
        return ImmutableDomainNameMapping(defaultValue, map)
    }

    /**
     * Immutable mapping from domain name pattern to its associated value object.
     * Mapping is represented by two arrays: keys and values. Key domainNamePatterns[i] is associated with values[i].
     *
     * @param V concrete type of value objects
     */
    private class ImmutableDomainNameMapping<V>(
        defaultValue: V,
        map: Map<String, V>
    ) : DomainNameMapping<V>(null, defaultValue) {

        private val domainNamePatterns: Array<String>
        private val values: Array<Any?>
        private val map: Map<String, V>

        init {
            val mappings = map.entries
            val numberOfMappings = mappings.size
            domainNamePatterns = Array(numberOfMappings) { "" }
            values = arrayOfNulls(numberOfMappings)

            val mapCopy = LinkedHashMap<String, V>(map.size)
            var index = 0
            for ((key, value) in mappings) {
                val hostname = normalizeHostname(key)
                domainNamePatterns[index] = hostname
                values[index] = value
                mapCopy[hostname] = value
                ++index
            }

            this.map = Collections.unmodifiableMap(mapCopy)
        }

        @Deprecated("Immutable DomainNameMapping does not support modification after initial creation")
        override fun add(hostname: String, output: V): DomainNameMapping<V> {
            throw UnsupportedOperationException(
                "Immutable DomainNameMapping does not support modification after initial creation"
            )
        }

        override fun map(hostname: String): V {
            @Suppress("NAME_SHADOWING")
            val hostname = normalizeHostname(hostname)

            for (index in domainNamePatterns.indices) {
                if (matches(domainNamePatterns[index], hostname)) {
                    @Suppress("UNCHECKED_CAST")
                    return values[index] as V
                }
            }

            return defaultValue
        }

        override fun asMap(): Map<String, V> {
            return map
        }

        override fun toString(): String {
            val defaultValueStr = defaultValue.toString()

            val numberOfMappings = domainNamePatterns.size
            if (numberOfMappings == 0) {
                return REPR_HEADER + defaultValueStr + REPR_MAP_OPENING + REPR_MAP_CLOSING
            }

            val pattern0 = domainNamePatterns[0]
            val value0 = values[0].toString()
            val oneMappingLength = pattern0.length + value0.length + 3 // 2 for separator ", " and 1 for '='
            val estimatedBufferSize = estimateBufferSize(defaultValueStr.length, numberOfMappings, oneMappingLength)

            val sb = StringBuilder(estimatedBufferSize)
                .append(REPR_HEADER).append(defaultValueStr).append(REPR_MAP_OPENING)

            appendMapping(sb, pattern0, value0)
            for (index in 1 until numberOfMappings) {
                sb.append(", ")
                appendMapping(sb, index)
            }

            return sb.append(REPR_MAP_CLOSING).toString()
        }

        private fun appendMapping(sb: StringBuilder, mappingIndex: Int): StringBuilder {
            return appendMapping(sb, domainNamePatterns[mappingIndex], values[mappingIndex].toString())
        }

        companion object {
            private const val REPR_HEADER = "ImmutableDomainNameMapping(default: "
            private const val REPR_MAP_OPENING = ", map: {"
            private const val REPR_MAP_CLOSING = "})"
            private val REPR_CONST_PART_LENGTH =
                REPR_HEADER.length + REPR_MAP_OPENING.length + REPR_MAP_CLOSING.length

            /**
             * Estimates the length of string representation of the given instance.
             */
            private fun estimateBufferSize(
                defaultValueLength: Int,
                numberOfMappings: Int,
                estimatedMappingLength: Int
            ): Int {
                return REPR_CONST_PART_LENGTH + defaultValueLength +
                    (estimatedMappingLength * numberOfMappings * 1.10).toInt()
            }

            private fun appendMapping(sb: StringBuilder, domainNamePattern: String, value: String): StringBuilder {
                return sb.append(domainNamePattern).append('=').append(value)
            }
        }
    }
}
