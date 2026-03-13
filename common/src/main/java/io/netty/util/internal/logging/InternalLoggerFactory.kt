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

package io.netty.util.internal.logging

import io.netty.util.internal.ObjectUtil

/**
 * Creates an [InternalLogger] or changes the default factory
 * implementation.  This factory allows you to choose what logging framework
 * Netty should use.  The default factory is [Slf4JLoggerFactory].  If SLF4J
 * is not available, [Log4JLoggerFactory] is used.  If Log4J is not available,
 * [JdkLoggerFactory] is used.  You can change it to your preferred
 * logging framework before other Netty classes are loaded:
 * ```
 * InternalLoggerFactory.setDefaultFactory(Log4JLoggerFactory.INSTANCE)
 * ```
 * Please note that the new default factory is effective only for the classes
 * which were loaded after the default factory is changed.  Therefore,
 * [setDefaultFactory] should be called as early
 * as possible and shouldn't be called more than once.
 */
abstract class InternalLoggerFactory {

    companion object {
        @Volatile
        private var defaultFactory: InternalLoggerFactory? = null

        @Suppress("UnusedCatchParameter")
        private fun newDefaultFactory(name: String): InternalLoggerFactory {
            return useSlf4JLoggerFactory(name)
                ?: useLog4J2LoggerFactory(name)
                ?: useLog4JLoggerFactory(name)
                ?: useJdkLoggerFactory(name)
        }

        private fun useSlf4JLoggerFactory(name: String): InternalLoggerFactory? {
            return try {
                val f = Slf4JLoggerFactory.getInstanceWithNopCheck()
                f.newInstance(name).debug("Using SLF4J as the default logging framework")
                f
            } catch (ignore: LinkageError) {
                null
            } catch (ignore: Exception) {
                null
            }
        }

        private fun useLog4J2LoggerFactory(name: String): InternalLoggerFactory? {
            return try {
                val f = Log4J2LoggerFactory.INSTANCE
                f.newInstance(name).debug("Using Log4J2 as the default logging framework")
                f
            } catch (ignore: LinkageError) {
                null
            } catch (ignore: Exception) {
                null
            }
        }

        private fun useLog4JLoggerFactory(name: String): InternalLoggerFactory? {
            return try {
                val f = Log4JLoggerFactory.INSTANCE
                f.newInstance(name).debug("Using Log4J as the default logging framework")
                f
            } catch (ignore: LinkageError) {
                null
            } catch (ignore: Exception) {
                null
            }
        }

        private fun useJdkLoggerFactory(name: String): InternalLoggerFactory {
            val f = JdkLoggerFactory.INSTANCE
            f.newInstance(name).debug("Using java.util.logging as the default logging framework")
            return f
        }

        /**
         * Returns the default factory.  The initial default factory is
         * [JdkLoggerFactory].
         */
        @JvmStatic
        fun getDefaultFactory(): InternalLoggerFactory {
            if (defaultFactory == null) {
                defaultFactory = newDefaultFactory(InternalLoggerFactory::class.java.name)
            }
            return defaultFactory!!
        }

        /**
         * Changes the default factory.
         */
        @JvmStatic
        fun setDefaultFactory(defaultFactory: InternalLoggerFactory) {
            Companion.defaultFactory = ObjectUtil.checkNotNull(defaultFactory, "defaultFactory")
        }

        /**
         * Creates a new logger instance with the name of the specified class.
         */
        @JvmStatic
        fun getInstance(clazz: Class<*>): InternalLogger {
            return getInstance(clazz.name)
        }

        /**
         * Creates a new logger instance with the specified name.
         */
        @JvmStatic
        fun getInstance(name: String): InternalLogger {
            return getDefaultFactory().newInstance(name)
        }
    }

    /**
     * Creates a new logger instance with the specified name.
     */
    protected abstract fun newInstance(name: String): InternalLogger
}
