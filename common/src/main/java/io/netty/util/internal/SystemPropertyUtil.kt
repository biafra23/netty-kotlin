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
package io.netty.util.internal

import io.netty.util.internal.ObjectUtil.checkNonEmpty
import io.netty.util.internal.logging.InternalLoggerFactory
import java.security.AccessController
import java.security.PrivilegedAction

object SystemPropertyUtil {

    private val logger = InternalLoggerFactory.getInstance(SystemPropertyUtil::class.java)

    @JvmStatic
    fun contains(key: String): Boolean = get(key) != null

    @JvmStatic
    @JvmOverloads
    fun get(key: String, def: String? = null): String? {
        checkNonEmpty(key, "key")
        var value: String? = null
        try {
            value = if (System.getSecurityManager() == null) {
                System.getProperty(key)
            } else {
                AccessController.doPrivileged(PrivilegedAction { System.getProperty(key) })
            }
        } catch (e: SecurityException) {
            logger.warn("Unable to retrieve a system property '{}'; default values will be used.", key, e)
        }
        return value ?: def
    }

    @JvmStatic
    fun getBoolean(key: String, def: Boolean): Boolean {
        val value = get(key)?.trim()?.lowercase() ?: return def
        if (value.isEmpty()) {
            return def
        }
        return when (value) {
            "true", "yes", "1" -> true
            "false", "no", "0" -> false
            else -> {
                logger.warn(
                    "Unable to parse the boolean system property '{}':{} - using the default value: {}",
                    key, value, def
                )
                def
            }
        }
    }

    @JvmStatic
    fun getInt(key: String, def: Int): Int {
        val value = get(key)?.trim() ?: return def
        return try {
            value.toInt()
        } catch (e: Exception) {
            logger.warn(
                "Unable to parse the integer system property '{}':{} - using the default value: {}",
                key, value, def
            )
            def
        }
    }

    @JvmStatic
    fun getLong(key: String, def: Long): Long {
        val value = get(key)?.trim() ?: return def
        return try {
            value.toLong()
        } catch (e: Exception) {
            logger.warn(
                "Unable to parse the long integer system property '{}':{} - using the default value: {}",
                key, value, def
            )
            def
        }
    }
}
