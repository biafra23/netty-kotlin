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

package io.netty.util.internal

import io.netty.util.concurrent.FastThreadLocal
import io.netty.util.concurrent.FastThreadLocalThread
import io.netty.util.internal.logging.InternalLogger
import io.netty.util.internal.logging.InternalLoggerFactory

import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CharsetEncoder
import java.util.Arrays
import java.util.BitSet
import java.util.IdentityHashMap
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * The internal data structure that stores the thread-local variables for Netty and all [FastThreadLocal]s.
 * Note that this class is for internal use only and is subject to change at any time. Use [FastThreadLocal]
 * unless you know what you are doing.
 */
class InternalThreadLocalMap private constructor() : UnpaddedInternalThreadLocalMap() {

    /** Used by [FastThreadLocal] */
    private var indexedVariables: Array<Any?> = newIndexedVariableTable()

    // Core thread-locals
    private var futureListenerStackDepth_: Int = 0
    private var localChannelReaderStackDepth_: Int = 0
    private var handlerSharableCache: MutableMap<Class<*>, Boolean>? = null
    private var typeParameterMatcherGetCache: MutableMap<Class<*>, TypeParameterMatcher>? = null
    private var typeParameterMatcherFindCache: MutableMap<Class<*>, MutableMap<String, TypeParameterMatcher>>? = null

    // String-related thread-locals
    private var stringBuilder_: StringBuilder? = null
    private var charsetEncoderCache_: MutableMap<Charset, CharsetEncoder>? = null
    private var charsetDecoderCache_: MutableMap<Charset, CharsetDecoder>? = null

    // ArrayList-related thread-locals
    private var arrayList_: ArrayList<Any?>? = null

    private var cleanerFlags: BitSet? = null

    /** @deprecated These padding fields will be removed in the future. */
    @JvmField var rp1: Long = 0
    @JvmField var rp2: Long = 0
    @JvmField var rp3: Long = 0
    @JvmField var rp4: Long = 0
    @JvmField var rp5: Long = 0
    @JvmField var rp6: Long = 0
    @JvmField var rp7: Long = 0
    @JvmField var rp8: Long = 0

    fun size(): Int {
        var count = 0

        if (futureListenerStackDepth_ != 0) count++
        if (localChannelReaderStackDepth_ != 0) count++
        if (handlerSharableCache != null) count++
        if (typeParameterMatcherGetCache != null) count++
        if (typeParameterMatcherFindCache != null) count++
        if (stringBuilder_ != null) count++
        if (charsetEncoderCache_ != null) count++
        if (charsetDecoderCache_ != null) count++
        if (arrayList_ != null) count++

        val v = indexedVariable(VARIABLES_TO_REMOVE_INDEX)
        if (v != null && v !== UNSET) {
            @Suppress("UNCHECKED_CAST")
            val variablesToRemove = v as Set<FastThreadLocal<*>>
            count += variablesToRemove.size
        }

        return count
    }

    fun stringBuilder(): StringBuilder {
        var sb = stringBuilder_
        if (sb == null) {
            return StringBuilder(STRING_BUILDER_INITIAL_SIZE).also { stringBuilder_ = it }
        }
        if (sb.capacity() > STRING_BUILDER_MAX_SIZE) {
            sb.setLength(STRING_BUILDER_INITIAL_SIZE)
            sb.trimToSize()
        }
        sb.setLength(0)
        return sb
    }

    fun charsetEncoderCache(): MutableMap<Charset, CharsetEncoder> {
        var cache = charsetEncoderCache_
        if (cache == null) {
            cache = IdentityHashMap()
            charsetEncoderCache_ = cache
        }
        return cache
    }

    fun charsetDecoderCache(): MutableMap<Charset, CharsetDecoder> {
        var cache = charsetDecoderCache_
        if (cache == null) {
            cache = IdentityHashMap()
            charsetDecoderCache_ = cache
        }
        return cache
    }

    fun <E> arrayList(): ArrayList<E> {
        return arrayList(DEFAULT_ARRAY_LIST_INITIAL_CAPACITY)
    }

    @Suppress("UNCHECKED_CAST")
    fun <E> arrayList(minCapacity: Int): ArrayList<E> {
        val list = arrayList_ as ArrayList<E>?
        if (list == null) {
            val newList = ArrayList<Any?>(minCapacity)
            arrayList_ = newList
            return newList as ArrayList<E>
        }
        list.clear()
        list.ensureCapacity(minCapacity)
        return list
    }

    fun futureListenerStackDepth(): Int = futureListenerStackDepth_

    fun setFutureListenerStackDepth(futureListenerStackDepth: Int) {
        this.futureListenerStackDepth_ = futureListenerStackDepth
    }

    /**
     * @deprecated Use [java.util.concurrent.ThreadLocalRandom.current] instead.
     */
    @Deprecated("Use java.util.concurrent.ThreadLocalRandom.current() instead.")
    fun random(): ThreadLocalRandom = ThreadLocalRandom()

    fun typeParameterMatcherGetCache(): MutableMap<Class<*>, TypeParameterMatcher> {
        var cache = typeParameterMatcherGetCache
        if (cache == null) {
            cache = IdentityHashMap()
            typeParameterMatcherGetCache = cache
        }
        return cache
    }

    fun typeParameterMatcherFindCache(): MutableMap<Class<*>, MutableMap<String, TypeParameterMatcher>> {
        var cache = typeParameterMatcherFindCache
        if (cache == null) {
            cache = IdentityHashMap()
            typeParameterMatcherFindCache = cache
        }
        return cache
    }

    @Deprecated("Deprecated in Java")
    fun counterHashCode(): IntegerHolder = IntegerHolder()

    @Deprecated("Deprecated in Java")
    fun setCounterHashCode(counterHashCode: IntegerHolder) {
        // No-op.
    }

    fun handlerSharableCache(): MutableMap<Class<*>, Boolean> {
        var cache = handlerSharableCache
        if (cache == null) {
            // Start with small capacity to keep memory overhead as low as possible.
            cache = WeakHashMap(HANDLER_SHARABLE_CACHE_INITIAL_CAPACITY)
            handlerSharableCache = cache
        }
        return cache
    }

    fun localChannelReaderStackDepth(): Int = localChannelReaderStackDepth_

    fun setLocalChannelReaderStackDepth(localChannelReaderStackDepth: Int) {
        this.localChannelReaderStackDepth_ = localChannelReaderStackDepth
    }

    fun indexedVariable(index: Int): Any? {
        val lookup = indexedVariables
        return if (index < lookup.size) lookup[index] else UNSET
    }

    /**
     * @return `true` if and only if a new thread-local variable has been created
     */
    fun setIndexedVariable(index: Int, value: Any?): Boolean {
        return getAndSetIndexedVariable(index, value) === UNSET
    }

    /**
     * @return [InternalThreadLocalMap.UNSET] if and only if a new thread-local variable has been created.
     */
    fun getAndSetIndexedVariable(index: Int, value: Any?): Any? {
        val lookup = indexedVariables
        if (index < lookup.size) {
            val oldValue = lookup[index]
            lookup[index] = value
            return oldValue
        }
        expandIndexedVariableTableAndSet(index, value)
        return UNSET
    }

    private fun expandIndexedVariableTableAndSet(index: Int, value: Any?) {
        val oldArray = indexedVariables
        val oldCapacity = oldArray.size
        val newCapacity: Int
        if (index < ARRAY_LIST_CAPACITY_EXPAND_THRESHOLD) {
            var nc = index
            nc = nc or (nc ushr 1)
            nc = nc or (nc ushr 2)
            nc = nc or (nc ushr 4)
            nc = nc or (nc ushr 8)
            nc = nc or (nc ushr 16)
            nc++
            newCapacity = nc
        } else {
            newCapacity = ARRAY_LIST_CAPACITY_MAX_SIZE
        }

        val newArray = Arrays.copyOf(oldArray, newCapacity)
        Arrays.fill(newArray, oldCapacity, newArray.size, UNSET)
        newArray[index] = value
        indexedVariables = newArray
    }

    fun removeIndexedVariable(index: Int): Any? {
        val lookup = indexedVariables
        return if (index < lookup.size) {
            val v = lookup[index]
            lookup[index] = UNSET
            v
        } else {
            UNSET
        }
    }

    fun isIndexedVariableSet(index: Int): Boolean {
        val lookup = indexedVariables
        return index < lookup.size && lookup[index] !== UNSET
    }

    @Deprecated("Deprecated in Java")
    fun isCleanerFlagSet(index: Int): Boolean {
        return cleanerFlags != null && cleanerFlags!!.get(index)
    }

    @Deprecated("Deprecated in Java")
    fun setCleanerFlag(index: Int) {
        if (cleanerFlags == null) {
            cleanerFlags = BitSet()
        }
        cleanerFlags!!.set(index)
    }

    companion object {
        private val slowThreadLocalMap: ThreadLocal<InternalThreadLocalMap> = ThreadLocal()
        private val nextIndex: AtomicInteger = AtomicInteger()

        // Internal use only.
        @JvmField
        val VARIABLES_TO_REMOVE_INDEX: Int = nextVariableIndex()

        private const val DEFAULT_ARRAY_LIST_INITIAL_CAPACITY = 8
        private const val ARRAY_LIST_CAPACITY_EXPAND_THRESHOLD = 1 shl 30
        // Reference: https://hg.openjdk.java.net/jdk8/jdk8/jdk/file/tip/src/share/classes/java/util/ArrayList.java#l229
        private const val ARRAY_LIST_CAPACITY_MAX_SIZE = Int.MAX_VALUE - 8

        private const val HANDLER_SHARABLE_CACHE_INITIAL_CAPACITY = 4
        private const val INDEXED_VARIABLE_TABLE_INITIAL_SIZE = 32

        private val STRING_BUILDER_INITIAL_SIZE: Int
        private val STRING_BUILDER_MAX_SIZE: Int

        private val logger: InternalLogger

        /** Internal use only. */
        @JvmField
        val UNSET: Any = Any()

        init {
            STRING_BUILDER_INITIAL_SIZE =
                SystemPropertyUtil.getInt("io.netty.threadLocalMap.stringBuilder.initialSize", 1024)
            STRING_BUILDER_MAX_SIZE =
                SystemPropertyUtil.getInt("io.netty.threadLocalMap.stringBuilder.maxSize", 1024 * 4)

            // Ensure the InternalLogger is initialized as last field in this class as InternalThreadLocalMap might be
            // used by the InternalLogger itself. For this its important that all the other static fields are correctly
            // initialized.
            //
            // See https://github.com/netty/netty/issues/12931.
            logger = InternalLoggerFactory.getInstance(InternalThreadLocalMap::class.java)
            logger.debug("-Dio.netty.threadLocalMap.stringBuilder.initialSize: {}", STRING_BUILDER_INITIAL_SIZE)
            logger.debug("-Dio.netty.threadLocalMap.stringBuilder.maxSize: {}", STRING_BUILDER_MAX_SIZE)
        }

        @JvmStatic
        fun getIfSet(): InternalThreadLocalMap? {
            val thread = Thread.currentThread()
            return if (thread is FastThreadLocalThread) {
                thread.threadLocalMap()
            } else {
                slowThreadLocalMap.get()
            }
        }

        @JvmStatic
        fun get(): InternalThreadLocalMap {
            val thread = Thread.currentThread()
            return if (thread is FastThreadLocalThread) {
                fastGet(thread)
            } else {
                slowGet()
            }
        }

        private fun fastGet(thread: FastThreadLocalThread): InternalThreadLocalMap {
            var threadLocalMap = thread.threadLocalMap()
            if (threadLocalMap == null) {
                threadLocalMap = InternalThreadLocalMap()
                thread.setThreadLocalMap(threadLocalMap)
            }
            return threadLocalMap
        }

        private fun slowGet(): InternalThreadLocalMap {
            var ret = slowThreadLocalMap.get()
            if (ret == null) {
                ret = InternalThreadLocalMap()
                slowThreadLocalMap.set(ret)
            }
            return ret
        }

        @JvmStatic
        fun remove() {
            val thread = Thread.currentThread()
            if (thread is FastThreadLocalThread) {
                thread.setThreadLocalMap(null)
            } else {
                slowThreadLocalMap.remove()
            }
        }

        @JvmStatic
        fun destroy() {
            slowThreadLocalMap.remove()
        }

        @JvmStatic
        fun nextVariableIndex(): Int {
            val index = nextIndex.getAndIncrement()
            if (index >= ARRAY_LIST_CAPACITY_MAX_SIZE || index < 0) {
                nextIndex.set(ARRAY_LIST_CAPACITY_MAX_SIZE)
                throw IllegalStateException("too many thread-local indexed variables")
            }
            return index
        }

        @JvmStatic
        fun lastVariableIndex(): Int = nextIndex.get() - 1

        private fun newIndexedVariableTable(): Array<Any?> {
            val array = arrayOfNulls<Any>(INDEXED_VARIABLE_TABLE_INITIAL_SIZE)
            Arrays.fill(array, UNSET)
            return array
        }
    }
}
