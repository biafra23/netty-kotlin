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

package io.netty.util

import io.netty.util.internal.PlatformDependent
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.HashSet
import java.util.TreeMap

/**
 * Retrieves the version information of available Netty artifacts.
 *
 * This class retrieves the version information from `META-INF/io.netty.versions.properties`, which is
 * generated in build time.  Note that it may not be possible to retrieve the information completely, depending on
 * your environment, such as the specified [ClassLoader], the current [SecurityManager].
 */
public class Version private constructor(
    private val artifactId: String,
    private val artifactVersion: String,
    private val buildTimeMillis: Long,
    private val commitTimeMillis: Long,
    private val shortCommitHash: String,
    private val longCommitHash: String,
    private val repositoryStatus: String
) {

    public fun artifactId(): String = artifactId

    public fun artifactVersion(): String = artifactVersion

    public fun buildTimeMillis(): Long = buildTimeMillis

    public fun commitTimeMillis(): Long = commitTimeMillis

    public fun shortCommitHash(): String = shortCommitHash

    public fun longCommitHash(): String = longCommitHash

    public fun repositoryStatus(): String = repositoryStatus

    override fun toString(): String {
        return "$artifactId-$artifactVersion.$shortCommitHash" +
            if ("clean" == repositoryStatus) "" else " (repository: $repositoryStatus)"
    }

    public companion object {
        private const val PROP_VERSION = ".version"
        private const val PROP_BUILD_DATE = ".buildDate"
        private const val PROP_COMMIT_DATE = ".commitDate"
        private const val PROP_SHORT_COMMIT_HASH = ".shortCommitHash"
        private const val PROP_LONG_COMMIT_HASH = ".longCommitHash"
        private const val PROP_REPO_STATUS = ".repoStatus"

        /**
         * Retrieves the version information of Netty artifacts using the current
         * [context class loader][Thread.getContextClassLoader].
         *
         * @return A [Map] whose keys are Maven artifact IDs and whose values are [Version]s
         */
        @JvmStatic
        public fun identify(): Map<String, Version> = identify(null)

        /**
         * Retrieves the version information of Netty artifacts using the specified [ClassLoader].
         *
         * @return A [Map] whose keys are Maven artifact IDs and whose values are [Version]s
         */
        @JvmStatic
        public fun identify(classLoader: ClassLoader?): Map<String, Version> {
            val cl = classLoader ?: PlatformDependent.getContextClassLoader()

            // Collect all properties.
            val props = java.util.Properties()
            try {
                val resources = cl.getResources("META-INF/io.netty.versions.properties")
                while (resources.hasMoreElements()) {
                    val url = resources.nextElement()
                    val inStream = url.openStream()
                    try {
                        props.load(inStream)
                    } finally {
                        try {
                            inStream.close()
                        } catch (_: Exception) {
                            // Ignore.
                        }
                    }
                }
            } catch (_: Exception) {
                // Not critical. Just ignore.
            }

            // Collect all artifactIds.
            val artifactIds = HashSet<String>()
            for (o in props.keys) {
                val k = o as String

                val dotIndex = k.indexOf('.')
                if (dotIndex <= 0) {
                    continue
                }

                val artifactId = k.substring(0, dotIndex)

                // Skip the entries without required information.
                if (!props.containsKey(artifactId + PROP_VERSION) ||
                    !props.containsKey(artifactId + PROP_BUILD_DATE) ||
                    !props.containsKey(artifactId + PROP_COMMIT_DATE) ||
                    !props.containsKey(artifactId + PROP_SHORT_COMMIT_HASH) ||
                    !props.containsKey(artifactId + PROP_LONG_COMMIT_HASH) ||
                    !props.containsKey(artifactId + PROP_REPO_STATUS)
                ) {
                    continue
                }

                artifactIds.add(artifactId)
            }

            val versions = TreeMap<String, Version>()
            for (artifactId in artifactIds) {
                versions[artifactId] = Version(
                    artifactId,
                    props.getProperty(artifactId + PROP_VERSION),
                    parseIso8601(props.getProperty(artifactId + PROP_BUILD_DATE)),
                    parseIso8601(props.getProperty(artifactId + PROP_COMMIT_DATE)),
                    props.getProperty(artifactId + PROP_SHORT_COMMIT_HASH),
                    props.getProperty(artifactId + PROP_LONG_COMMIT_HASH),
                    props.getProperty(artifactId + PROP_REPO_STATUS)
                )
            }

            return versions
        }

        private fun parseIso8601(value: String?): Long {
            return try {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").parse(value).time
            } catch (_: ParseException) {
                0L
            }
        }

        /**
         * Prints the version information to [System.err].
         */
        @JvmStatic
        public fun main(args: Array<String>) {
            for (v in identify().values) {
                System.err.println(v)
            }
        }
    }
}
