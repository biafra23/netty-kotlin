/*
 * Copyright 2017 The Netty Project
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

import java.lang.reflect.AccessibleObject
import java.lang.reflect.Array
import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.TypeVariable

object ReflectionUtil {

    /**
     * Try to call [AccessibleObject.setAccessible] but will catch any [SecurityException] and
     * [java.lang.reflect.InaccessibleObjectException] and return it.
     * The caller must check if it returns `null` and if not handle the returned exception.
     */
    @JvmStatic
    fun trySetAccessible(obj: AccessibleObject, checkAccessible: Boolean): Throwable? {
        if (checkAccessible && !PlatformDependent0.isExplicitTryReflectionSetAccessible()) {
            return UnsupportedOperationException("Reflective setAccessible(true) disabled")
        }
        return try {
            obj.isAccessible = true
            null
        } catch (e: SecurityException) {
            e
        } catch (e: RuntimeException) {
            handleInaccessibleObjectException(e)
        }
    }

    private fun handleInaccessibleObjectException(e: RuntimeException): RuntimeException {
        // JDK 9 can throw an inaccessible object exception here; since Netty compiles
        // against JDK 7 and this exception was only added in JDK 9, we have to weakly
        // check the type
        if ("java.lang.reflect.InaccessibleObjectException" == e.javaClass.name) {
            return e
        }
        throw e
    }

    private fun fail(type: Class<*>, typeParamName: String): Class<*> {
        throw IllegalStateException(
            "cannot determine the type of the type parameter '$typeParamName': $type"
        )
    }

    /**
     * Resolve a type parameter of a class that is a subclass of the given parametrized superclass.
     * @param obj The object to resolve the type parameter for
     * @param parametrizedSuperclass The parametrized superclass
     * @param typeParamName The name of the type parameter to resolve
     * @return The resolved type parameter
     * @throws IllegalStateException if the type parameter could not be resolved
     */
    @JvmStatic
    fun resolveTypeParameter(
        obj: Any,
        parametrizedSuperclass: Class<*>,
        typeParamName: String
    ): Class<*> {
        val thisClass = obj.javaClass
        var currentClass: Class<*>? = thisClass
        var currentParamSuperclass = parametrizedSuperclass
        var currentTypeParamName = typeParamName
        while (true) {
            if (currentClass == null) {
                return fail(thisClass, currentTypeParamName)
            }
            if (currentClass.superclass == currentParamSuperclass) {
                var typeParamIndex = -1
                val typeParams = currentClass.superclass.typeParameters
                for (i in typeParams.indices) {
                    if (currentTypeParamName == typeParams[i].name) {
                        typeParamIndex = i
                        break
                    }
                }

                if (typeParamIndex < 0) {
                    throw IllegalStateException(
                        "unknown type parameter '$currentTypeParamName': $currentParamSuperclass"
                    )
                }

                val genericSuperType = currentClass.genericSuperclass
                if (genericSuperType !is ParameterizedType) {
                    return Any::class.java
                }

                val actualTypeParams = genericSuperType.actualTypeArguments

                var actualTypeParam = actualTypeParams[typeParamIndex]
                if (actualTypeParam is ParameterizedType) {
                    actualTypeParam = actualTypeParam.rawType
                }
                if (actualTypeParam is Class<*>) {
                    return actualTypeParam
                }
                if (actualTypeParam is GenericArrayType) {
                    var componentType = actualTypeParam.genericComponentType
                    if (componentType is ParameterizedType) {
                        componentType = componentType.rawType
                    }
                    if (componentType is Class<*>) {
                        return Array.newInstance(componentType, 0).javaClass
                    }
                }
                if (actualTypeParam is TypeVariable<*>) {
                    val v = actualTypeParam
                    if (v.genericDeclaration !is Class<*>) {
                        return Any::class.java
                    }

                    currentClass = thisClass
                    currentParamSuperclass = v.genericDeclaration as Class<*>
                    currentTypeParamName = v.name
                    if (currentParamSuperclass.isAssignableFrom(thisClass)) {
                        continue
                    }
                    return Any::class.java
                }

                return fail(thisClass, currentTypeParamName)
            }
            currentClass = currentClass.superclass
        }
    }
}
