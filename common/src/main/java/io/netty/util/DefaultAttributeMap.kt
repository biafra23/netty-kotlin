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
package io.netty.util

import io.netty.util.internal.ObjectUtil
import java.util.Arrays
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater

/**
 * Default [AttributeMap] implementation which not exibit any blocking behaviour on attribute lookup while using a
 * copy-on-write approach on the modify path.
 * Attributes lookup and remove exibit `O(logn)` time worst-case
 * complexity, hence `attribute::set(null)` is to be preferred to `remove`.
 */
open class DefaultAttributeMap : AttributeMap {

    @Volatile
    @JvmField
    internal var attributes: Array<DefaultAttribute<*>> = EMPTY_ATTRIBUTES

    @Suppress("UNCHECKED_CAST")
    override fun <T> attr(key: AttributeKey<T>): Attribute<T> {
        ObjectUtil.checkNotNull(key, "key")
        var newAttribute: DefaultAttribute<T>? = null
        while (true) {
            val attributes = this.attributes
            val index = searchAttributeByKey(attributes, key)
            val newAttributes: Array<DefaultAttribute<*>>
            if (index >= 0) {
                val attribute = attributes[index]
                assert(attribute.key() == key)
                if (!attribute.isRemoved()) {
                    return attribute as Attribute<T>
                }
                // let's try replace the removed attribute with a new one
                if (newAttribute == null) {
                    newAttribute = DefaultAttribute(this, key)
                }
                val count = attributes.size
                newAttributes = Arrays.copyOf(attributes, count)
                newAttributes[index] = newAttribute
            } else {
                if (newAttribute == null) {
                    newAttribute = DefaultAttribute(this, key)
                }
                val count = attributes.size
                newAttributes = arrayOfNulls<DefaultAttribute<*>>(count + 1) as Array<DefaultAttribute<*>>
                orderedCopyOnInsert(attributes, count, newAttributes, newAttribute)
            }
            if (ATTRIBUTES_UPDATER.compareAndSet(this, attributes, newAttributes)) {
                return newAttribute
            }
        }
    }

    override fun <T> hasAttr(key: AttributeKey<T>): Boolean {
        ObjectUtil.checkNotNull(key, "key")
        return searchAttributeByKey(attributes, key) >= 0
    }

    private fun <T> removeAttributeIfMatch(key: AttributeKey<T>, value: DefaultAttribute<T>) {
        while (true) {
            val attributes = this.attributes
            val index = searchAttributeByKey(attributes, key)
            if (index < 0) {
                return
            }
            val attribute = attributes[index]
            assert(attribute.key() == key)
            if (attribute !== value) {
                return
            }
            val count = attributes.size
            val newCount = count - 1
            val newAttributes = if (newCount == 0) EMPTY_ATTRIBUTES else arrayOfNulls<DefaultAttribute<*>>(newCount) as Array<DefaultAttribute<*>>
            // perform 2 bulk copies
            System.arraycopy(attributes, 0, newAttributes, 0, index)
            val remaining = count - index - 1
            if (remaining > 0) {
                System.arraycopy(attributes, index + 1, newAttributes, index, remaining)
            }
            if (ATTRIBUTES_UPDATER.compareAndSet(this, attributes, newAttributes)) {
                return
            }
        }
    }

    internal class DefaultAttribute<T>(
        attributeMap: DefaultAttributeMap,
        @JvmField val key: AttributeKey<T>
    ) : AtomicReference<T>(), Attribute<T> {

        @Volatile
        @JvmField
        var attributeMap: DefaultAttributeMap? = attributeMap

        override fun key(): AttributeKey<T> = key

        fun isRemoved(): Boolean = attributeMap == null

        override fun setIfAbsent(value: T?): T? {
            while (!compareAndSet(null, value)) {
                val old = get()
                if (old != null) {
                    return old
                }
            }
            return null
        }

        override fun getAndRemove(): T? {
            val attributeMap = this.attributeMap
            val removed = attributeMap != null && MAP_UPDATER.compareAndSet(this, attributeMap, null)
            val oldValue = getAndSet(null)
            if (removed) {
                attributeMap!!.removeAttributeIfMatch(key, this)
            }
            return oldValue
        }

        override fun remove() {
            val attributeMap = this.attributeMap
            val removed = attributeMap != null && MAP_UPDATER.compareAndSet(this, attributeMap, null)
            set(null)
            if (removed) {
                attributeMap!!.removeAttributeIfMatch(key, this)
            }
        }

        companion object {
            @JvmStatic
            private val MAP_UPDATER: AtomicReferenceFieldUpdater<DefaultAttribute<*>, DefaultAttributeMap> =
                AtomicReferenceFieldUpdater.newUpdater(
                    DefaultAttribute::class.java as Class<DefaultAttribute<*>>,
                    DefaultAttributeMap::class.java,
                    "attributeMap"
                )

            private const val serialVersionUID = -2661411462200283011L
        }
    }

    companion object {
        @JvmStatic
        private val EMPTY_ATTRIBUTES: Array<DefaultAttribute<*>> = emptyArray()

        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        private val ATTRIBUTES_UPDATER: AtomicReferenceFieldUpdater<DefaultAttributeMap, Array<DefaultAttribute<*>>> =
            AtomicReferenceFieldUpdater.newUpdater(
                DefaultAttributeMap::class.java,
                EMPTY_ATTRIBUTES.javaClass as Class<Array<DefaultAttribute<*>>>,
                "attributes"
            )

        /**
         * Similarly to `Arrays::binarySearch` it perform a binary search optimized for this use case, in order to
         * save polymorphic calls (on comparator side) and unnecessary class checks.
         */
        @JvmStatic
        private fun searchAttributeByKey(sortedAttributes: Array<DefaultAttribute<*>>, key: AttributeKey<*>): Int {
            var low = 0
            var high = sortedAttributes.size - 1

            while (low <= high) {
                val mid = (low + high).ushr(1)
                val midVal = sortedAttributes[mid]
                val midValKey = midVal.key
                if (midValKey === key) {
                    return mid
                }
                val midValKeyId = midValKey.id()
                val keyId = key.id()
                assert(midValKeyId != keyId)
                val searchRight = midValKeyId < keyId
                if (searchRight) {
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }

            return -(low + 1)
        }

        @JvmStatic
        private fun orderedCopyOnInsert(
            sortedSrc: Array<DefaultAttribute<*>>,
            srcLength: Int,
            copy: Array<DefaultAttribute<*>>,
            toInsert: DefaultAttribute<*>
        ) {
            // let's walk backward, because as a rule of thumb, toInsert.key.id() tends to be higher for new keys
            val id = toInsert.key.id()
            var i: Int = srcLength - 1
            while (i >= 0) {
                val attribute = sortedSrc[i]
                val attributeKeyId = attribute.key.id()
                assert(attributeKeyId != id)
                if (attributeKeyId < id) {
                    break
                }
                copy[i + 1] = attribute
                i--
            }
            copy[i + 1] = toInsert
            val toCopy = i + 1
            if (toCopy > 0) {
                System.arraycopy(sortedSrc, 0, copy, 0, toCopy)
            }
        }
    }
}
