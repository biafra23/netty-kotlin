/*
 * Copyright 2014 The Netty Project
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
package io.netty.util.concurrent

import io.netty.util.internal.InternalThreadLocalMap
import io.netty.util.internal.InternalThreadLocalMap.Companion.UNSET
import io.netty.util.internal.InternalThreadLocalMap.Companion.VARIABLES_TO_REMOVE_INDEX
import io.netty.util.internal.PlatformDependent
import java.util.Collections
import java.util.IdentityHashMap

/**
 * A special variant of [ThreadLocal] that yields higher access performance when accessed from a
 * [FastThreadLocalThread].
 *
 * Internally, a [FastThreadLocal] uses a constant index in an array, instead of using hash code and hash table,
 * to look for a variable. Although seemingly very subtle, it yields slight performance advantage over using a hash
 * table, and it is useful when accessed frequently.
 *
 * To take advantage of this thread-local variable, your thread must be a [FastThreadLocalThread] or its subtype.
 * By default, all threads created by [DefaultThreadFactory] are [FastThreadLocalThread] due to this reason.
 *
 * Note that the fast path is only possible on threads that extend [FastThreadLocalThread], because it requires
 * a special field to store the necessary state. An access by any other kind of thread falls back to a regular
 * [ThreadLocal].
 *
 * @param V the type of the thread-local variable
 * @see ThreadLocal
 */
open class FastThreadLocal<V> {

    private val index: Int = InternalThreadLocalMap.nextVariableIndex()

    /**
     * Returns the current value for the current thread
     */
    @Suppress("UNCHECKED_CAST")
    fun get(): V {
        val threadLocalMap = InternalThreadLocalMap.get()
        val v = threadLocalMap.indexedVariable(index)
        if (v !== UNSET) {
            return v as V
        }
        return initialize(threadLocalMap)
    }

    /**
     * Returns the current value for the current thread if it exists, `null` otherwise.
     */
    @Suppress("UNCHECKED_CAST")
    fun getIfExists(): V? {
        val threadLocalMap = InternalThreadLocalMap.getIfSet() ?: return null
        val v = threadLocalMap.indexedVariable(index)
        if (v !== UNSET) {
            return v as V
        }
        return null
    }

    /**
     * Returns the current value for the specified thread local map.
     * The specified thread local map must be for the current thread.
     */
    @Suppress("UNCHECKED_CAST")
    fun get(threadLocalMap: InternalThreadLocalMap): V {
        val v = threadLocalMap.indexedVariable(index)
        if (v !== UNSET) {
            return v as V
        }
        return initialize(threadLocalMap)
    }

    @Suppress("UNCHECKED_CAST")
    private fun initialize(threadLocalMap: InternalThreadLocalMap): V {
        var v: V? = null
        try {
            v = initialValue()
            if (v === UNSET) {
                throw IllegalArgumentException("InternalThreadLocalMap.UNSET can not be initial value.")
            }
        } catch (e: Exception) {
            PlatformDependent.throwException(e)
        }

        threadLocalMap.setIndexedVariable(index, v)
        addToVariablesToRemove(threadLocalMap, this)
        return v as V
    }

    /**
     * Set the value for the current thread.
     */
    fun set(value: V) {
        getAndSet(value)
    }

    /**
     * Set the value for the specified thread local map. The specified thread local map must be for the current thread.
     */
    fun set(threadLocalMap: InternalThreadLocalMap, value: V) {
        getAndSet(threadLocalMap, value)
    }

    /**
     * Set the value for the current thread and returns the old value.
     */
    open fun getAndSet(value: V): V? {
        if (value !== UNSET) {
            val threadLocalMap = InternalThreadLocalMap.get()
            return setKnownNotUnset(threadLocalMap, value)
        }
        return removeAndGet(InternalThreadLocalMap.getIfSet())
    }

    /**
     * Set the value for the specified thread local map. The specified thread local map must be for the current thread.
     */
    open fun getAndSet(threadLocalMap: InternalThreadLocalMap, value: V): V? {
        if (value !== UNSET) {
            return setKnownNotUnset(threadLocalMap, value)
        }
        return removeAndGet(threadLocalMap)
    }

    /**
     * @see InternalThreadLocalMap.setIndexedVariable
     */
    @Suppress("UNCHECKED_CAST")
    private fun setKnownNotUnset(threadLocalMap: InternalThreadLocalMap, value: V): V? {
        val old = threadLocalMap.getAndSetIndexedVariable(index, value) as V
        if (old === UNSET) {
            addToVariablesToRemove(threadLocalMap, this)
            return null
        }
        return old
    }

    /**
     * Returns `true` if and only if this thread-local variable is set.
     */
    fun isSet(): Boolean {
        return isSet(InternalThreadLocalMap.getIfSet())
    }

    /**
     * Returns `true` if and only if this thread-local variable is set.
     * The specified thread local map must be for the current thread.
     */
    fun isSet(threadLocalMap: InternalThreadLocalMap?): Boolean {
        return threadLocalMap != null && threadLocalMap.isIndexedVariableSet(index)
    }

    /**
     * Sets the value to uninitialized for the specified thread local map and returns the old value.
     * After this, any subsequent call to get() will trigger a new call to initialValue().
     */
    fun remove() {
        remove(InternalThreadLocalMap.getIfSet())
    }

    /**
     * Sets the value to uninitialized for the specified thread local map.
     * After this, any subsequent call to get() will trigger a new call to initialValue().
     * The specified thread local map must be for the current thread.
     */
    fun remove(threadLocalMap: InternalThreadLocalMap?) {
        removeAndGet(threadLocalMap)
    }

    /**
     * Sets the value to uninitialized for the specified thread local map.
     * After this, any subsequent call to get() will trigger a new call to initialValue().
     * The specified thread local map must be for the current thread.
     */
    @Suppress("UNCHECKED_CAST")
    private fun removeAndGet(threadLocalMap: InternalThreadLocalMap?): V? {
        if (threadLocalMap == null) {
            return null
        }

        val v = threadLocalMap.removeIndexedVariable(index)
        if (v !== UNSET) {
            removeFromVariablesToRemove(threadLocalMap, this)
            try {
                onRemoval(v as V)
            } catch (e: Exception) {
                PlatformDependent.throwException(e)
            }
            return v as V
        }
        return null
    }

    /**
     * Returns the initial value for this thread-local variable.
     */
    @Throws(Exception::class)
    protected open fun initialValue(): V? {
        return null
    }

    /**
     * Invoked when this thread local variable is removed by [remove]. Be aware that [remove]
     * is not guaranteed to be called when the `Thread` completes which means you can not depend on this for
     * cleanup of the resources in the case of `Thread` completion.
     */
    @Throws(Exception::class)
    protected open fun onRemoval(@Suppress("UNUSED_PARAMETER") value: V) {
    }

    companion object {
        /**
         * Removes all [FastThreadLocal] variables bound to the current thread. This operation is useful when you
         * are in a container environment, and you don't want to leave the thread local variables in the threads you do not
         * manage.
         */
        @JvmStatic
        fun removeAll() {
            val threadLocalMap = InternalThreadLocalMap.getIfSet() ?: return

            try {
                val v = threadLocalMap.indexedVariable(VARIABLES_TO_REMOVE_INDEX)
                if (v != null && v !== UNSET) {
                    @Suppress("UNCHECKED_CAST")
                    val variablesToRemove = v as Set<FastThreadLocal<*>>
                    val variablesToRemoveArray = variablesToRemove.toTypedArray()
                    for (tlv in variablesToRemoveArray) {
                        tlv.remove(threadLocalMap)
                    }
                }
            } finally {
                InternalThreadLocalMap.remove()
            }
        }

        /**
         * Returns the number of thread local variables bound to the current thread.
         */
        @JvmStatic
        fun size(): Int {
            val threadLocalMap = InternalThreadLocalMap.getIfSet() ?: return 0
            return threadLocalMap.size()
        }

        /**
         * Destroys the data structure that keeps all [FastThreadLocal] variables accessed from
         * non-[FastThreadLocalThread]s. This operation is useful when you are in a container environment, and you
         * do not want to leave the thread local variables in the threads you do not manage. Call this method when your
         * application is being unloaded from the container.
         */
        @JvmStatic
        fun destroy() {
            InternalThreadLocalMap.destroy()
        }

        @Suppress("UNCHECKED_CAST")
        private fun addToVariablesToRemove(threadLocalMap: InternalThreadLocalMap, variable: FastThreadLocal<*>) {
            val v = threadLocalMap.indexedVariable(VARIABLES_TO_REMOVE_INDEX)
            val variablesToRemove: MutableSet<FastThreadLocal<*>>
            if (v === UNSET || v == null) {
                variablesToRemove = Collections.newSetFromMap(IdentityHashMap<FastThreadLocal<*>, Boolean>())
                threadLocalMap.setIndexedVariable(VARIABLES_TO_REMOVE_INDEX, variablesToRemove)
            } else {
                variablesToRemove = v as MutableSet<FastThreadLocal<*>>
            }

            variablesToRemove.add(variable)
        }

        private fun removeFromVariablesToRemove(
            threadLocalMap: InternalThreadLocalMap, variable: FastThreadLocal<*>
        ) {
            val v = threadLocalMap.indexedVariable(VARIABLES_TO_REMOVE_INDEX)

            if (v === UNSET || v == null) {
                return
            }

            @Suppress("UNCHECKED_CAST")
            val variablesToRemove = v as MutableSet<FastThreadLocal<*>>
            variablesToRemove.remove(variable)
        }
    }
}
