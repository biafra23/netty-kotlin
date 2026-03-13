/*
 * Copyright 2021 The Netty Project
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

/**
 * Utility which ensures that classes are loaded by the [ClassLoader].
 */
object ClassInitializerUtil {

    /**
     * Preload the given classes and so ensure the [ClassLoader] has these loaded after this method call.
     *
     * @param loadingClass the [Class] that wants to load the classes.
     * @param classes the classes to load.
     */
    @JvmStatic
    fun tryLoadClasses(loadingClass: Class<*>, vararg classes: Class<*>) {
        val loader = PlatformDependent.getClassLoader(loadingClass)
        for (clazz in classes) {
            tryLoadClass(loader, clazz.name)
        }
    }

    private fun tryLoadClass(classLoader: ClassLoader, className: String) {
        try {
            // Load the class and also ensure we init it which means its linked etc.
            Class.forName(className, true, classLoader)
        } catch (_: ClassNotFoundException) {
            // Ignore
        } catch (_: SecurityException) {
            // Ignore
        }
    }
}
