/*
 * Copyright 2015 The Netty Project
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

/**
 * Builder for immutable [DomainNameMapping] instances.
 *
 * @param V concrete type of value objects
 * @deprecated Use [DomainWildcardMappingBuilder] instead.
 */
@Deprecated("Use DomainWildcardMappingBuilder instead.")
class DomainMappingBuilder<V> {

    private val builder: DomainNameMappingBuilder<V>

    /**
     * Constructor with default initial capacity of the map holding the mappings
     *
     * @param defaultValue the default value for [DomainNameMapping.map] to return
     *                     when nothing matches the input
     */
    @Suppress("DEPRECATION")
    constructor(defaultValue: V) {
        builder = DomainNameMappingBuilder(defaultValue)
    }

    /**
     * Constructor with initial capacity of the map holding the mappings
     *
     * @param initialCapacity initial capacity for the internal map
     * @param defaultValue    the default value for [DomainNameMapping.map] to return
     *                        when nothing matches the input
     */
    @Suppress("DEPRECATION")
    constructor(initialCapacity: Int, defaultValue: V) {
        builder = DomainNameMappingBuilder(initialCapacity, defaultValue)
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
    fun add(hostname: String, output: V): DomainMappingBuilder<V> {
        builder.add(hostname, output)
        return this
    }

    /**
     * Creates a new instance of immutable [DomainNameMapping]
     * Attempts to add new mappings to the result object will cause [UnsupportedOperationException] to be thrown
     *
     * @return new [DomainNameMapping] instance
     */
    fun build(): DomainNameMapping<V> {
        return builder.build()
    }
}
