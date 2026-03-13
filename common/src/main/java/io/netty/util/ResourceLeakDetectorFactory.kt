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

import io.netty.util.internal.ObjectUtil
import io.netty.util.internal.PlatformDependent
import io.netty.util.internal.SystemPropertyUtil
import io.netty.util.internal.logging.InternalLoggerFactory
import java.lang.reflect.Constructor

/**
 * This static factory should be used to load [ResourceLeakDetector]s as needed
 */
public abstract class ResourceLeakDetectorFactory {

    /**
     * Returns a new instance of a [ResourceLeakDetector] with the given resource class.
     *
     * @param resource the resource class used to initialize the [ResourceLeakDetector]
     * @param T the type of the resource class
     * @return a new instance of [ResourceLeakDetector]
     */
    public fun <T> newResourceLeakDetector(resource: Class<T>): ResourceLeakDetector<T> {
        return newResourceLeakDetector(resource, ResourceLeakDetector.SAMPLING_INTERVAL)
    }

    /**
     * @deprecated Use [newResourceLeakDetector(Class, Int)] instead.
     *
     * Returns a new instance of a [ResourceLeakDetector] with the given resource class.
     *
     * @param resource the resource class used to initialize the [ResourceLeakDetector]
     * @param samplingInterval the interval on which sampling takes place
     * @param maxActive This is deprecated and will be ignored.
     * @param T the type of the resource class
     * @return a new instance of [ResourceLeakDetector]
     */
    @Deprecated("Use newResourceLeakDetector(Class, Int) instead.")
    public abstract fun <T> newResourceLeakDetector(
        resource: Class<T>, samplingInterval: Int, maxActive: Long
    ): ResourceLeakDetector<T>

    /**
     * Returns a new instance of a [ResourceLeakDetector] with the given resource class.
     *
     * @param resource the resource class used to initialize the [ResourceLeakDetector]
     * @param samplingInterval the interval on which sampling takes place
     * @param T the type of the resource class
     * @return a new instance of [ResourceLeakDetector]
     */
    @Suppress("DEPRECATION")
    public open fun <T> newResourceLeakDetector(resource: Class<T>, samplingInterval: Int): ResourceLeakDetector<T> {
        ObjectUtil.checkPositive(samplingInterval, "samplingInterval")
        return newResourceLeakDetector(resource, samplingInterval, Long.MAX_VALUE)
    }

    /**
     * Default implementation that loads custom leak detector via system property
     */
    private class DefaultResourceLeakDetectorFactory : ResourceLeakDetectorFactory() {
        private val obsoleteCustomClassConstructor: Constructor<*>?
        private val customClassConstructor: Constructor<*>?

        init {
            var customLeakDetector: String?
            try {
                customLeakDetector = SystemPropertyUtil.get("io.netty.customResourceLeakDetector")
            } catch (cause: Throwable) {
                logger.error("Could not access System property: io.netty.customResourceLeakDetector", cause)
                customLeakDetector = null
            }
            if (customLeakDetector == null) {
                obsoleteCustomClassConstructor = null
                customClassConstructor = null
            } else {
                obsoleteCustomClassConstructor = obsoleteCustomClassConstructor(customLeakDetector)
                customClassConstructor = customClassConstructor(customLeakDetector)
            }
        }

        @Suppress("DEPRECATION")
        override fun <T> newResourceLeakDetector(
            resource: Class<T>, samplingInterval: Int, maxActive: Long
        ): ResourceLeakDetector<T> {
            if (obsoleteCustomClassConstructor != null) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val leakDetector = obsoleteCustomClassConstructor.newInstance(
                        resource, samplingInterval, maxActive
                    ) as ResourceLeakDetector<T>
                    logger.debug(
                        "Loaded custom ResourceLeakDetector: {}",
                        obsoleteCustomClassConstructor.declaringClass.name
                    )
                    return leakDetector
                } catch (t: Throwable) {
                    logger.error(
                        "Could not load custom resource leak detector provided: {} with the given resource: {}",
                        obsoleteCustomClassConstructor.declaringClass.name, resource, t
                    )
                }
            }

            val resourceLeakDetector = ResourceLeakDetector<T>(resource, samplingInterval, maxActive)
            logger.debug("Loaded default ResourceLeakDetector: {}", resourceLeakDetector)
            return resourceLeakDetector
        }

        override fun <T> newResourceLeakDetector(
            resource: Class<T>, samplingInterval: Int
        ): ResourceLeakDetector<T> {
            if (customClassConstructor != null) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val leakDetector = customClassConstructor.newInstance(
                        resource, samplingInterval
                    ) as ResourceLeakDetector<T>
                    logger.debug(
                        "Loaded custom ResourceLeakDetector: {}",
                        customClassConstructor.declaringClass.name
                    )
                    return leakDetector
                } catch (t: Throwable) {
                    logger.error(
                        "Could not load custom resource leak detector provided: {} with the given resource: {}",
                        customClassConstructor.declaringClass.name, resource, t
                    )
                }
            }

            val resourceLeakDetector = ResourceLeakDetector<T>(resource, samplingInterval)
            logger.debug("Loaded default ResourceLeakDetector: {}", resourceLeakDetector)
            return resourceLeakDetector
        }

        companion object {
            private fun obsoleteCustomClassConstructor(customLeakDetector: String): Constructor<*>? {
                try {
                    val detectorClass = Class.forName(
                        customLeakDetector, true,
                        PlatformDependent.getSystemClassLoader()
                    )
                    if (ResourceLeakDetector::class.java.isAssignableFrom(detectorClass)) {
                        return detectorClass.getConstructor(
                            Class::class.java, Int::class.javaPrimitiveType, Long::class.javaPrimitiveType
                        )
                    } else {
                        logger.error("Class {} does not inherit from ResourceLeakDetector.", customLeakDetector)
                    }
                } catch (t: Throwable) {
                    logger.error(
                        "Could not load custom resource leak detector class provided: {}",
                        customLeakDetector, t
                    )
                }
                return null
            }

            private fun customClassConstructor(customLeakDetector: String): Constructor<*>? {
                try {
                    val detectorClass = Class.forName(
                        customLeakDetector, true,
                        PlatformDependent.getSystemClassLoader()
                    )
                    if (ResourceLeakDetector::class.java.isAssignableFrom(detectorClass)) {
                        return detectorClass.getConstructor(
                            Class::class.java, Int::class.javaPrimitiveType
                        )
                    } else {
                        logger.error("Class {} does not inherit from ResourceLeakDetector.", customLeakDetector)
                    }
                } catch (t: Throwable) {
                    logger.error(
                        "Could not load custom resource leak detector class provided: {}",
                        customLeakDetector, t
                    )
                }
                return null
            }
        }
    }

    public companion object {
        private val logger = InternalLoggerFactory.getInstance(ResourceLeakDetectorFactory::class.java)

        @Volatile
        private var factoryInstance: ResourceLeakDetectorFactory = DefaultResourceLeakDetectorFactory()

        /**
         * Get the singleton instance of this factory class.
         *
         * @return the current [ResourceLeakDetectorFactory]
         */
        @JvmStatic
        public fun instance(): ResourceLeakDetectorFactory = factoryInstance

        /**
         * Set the factory's singleton instance. This has to be called before the static initializer of the
         * [ResourceLeakDetector] is called by all the callers of this factory. That is, before initializing a
         * Netty Bootstrap.
         *
         * @param factory the instance that will become the current [ResourceLeakDetectorFactory]'s singleton
         */
        @JvmStatic
        public fun setResourceLeakDetectorFactory(factory: ResourceLeakDetectorFactory) {
            factoryInstance = ObjectUtil.checkNotNull(factory, "factory")
        }
    }
}
