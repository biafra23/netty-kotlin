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
package io.netty.util.internal

abstract class TypeParameterMatcher internal constructor() {

    companion object {
        private val NOOP: TypeParameterMatcher = object : TypeParameterMatcher() {
            override fun match(msg: Any): Boolean = true
        }

        @JvmStatic
        fun get(parameterType: Class<*>): TypeParameterMatcher {
            val getCache = InternalThreadLocalMap.get().typeParameterMatcherGetCache()

            var matcher = getCache[parameterType]
            if (matcher == null) {
                matcher = if (parameterType == Any::class.java) {
                    NOOP
                } else {
                    ReflectiveMatcher(parameterType)
                }
                getCache[parameterType] = matcher
            }

            return matcher
        }

        @JvmStatic
        fun find(
            `object`: Any, parametrizedSuperclass: Class<*>, typeParamName: String
        ): TypeParameterMatcher {
            val findCache = InternalThreadLocalMap.get().typeParameterMatcherFindCache()
            val thisClass = `object`.javaClass

            var map = findCache[thisClass]
            if (map == null) {
                map = HashMap<String, TypeParameterMatcher>()
                findCache[thisClass] = map
            }

            var matcher = map[typeParamName]
            if (matcher == null) {
                matcher = get(ReflectionUtil.resolveTypeParameter(`object`, parametrizedSuperclass, typeParamName))
                map[typeParamName] = matcher
            }

            return matcher
        }
    }

    abstract fun match(msg: Any): Boolean

    private class ReflectiveMatcher(private val type: Class<*>) : TypeParameterMatcher() {
        override fun match(msg: Any): Boolean = type.isInstance(msg)
    }
}
