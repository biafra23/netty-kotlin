/*
 * Copyright 2025 The Netty Project
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

import io.netty.util.internal.MathUtil
import io.netty.util.internal.ObjectUtil
import java.util.Objects
import java.util.concurrent.atomic.AtomicIntegerArray
import java.util.concurrent.atomic.AtomicLongFieldUpdater
import java.util.function.IntConsumer
import java.util.function.IntSupplier

/**
 * A multi-producer (concurrent and thread-safe `offer` and `fill`),
 * single-consumer (single-threaded `poll` and `drain`) queue of primitive integers.
 */
interface MpscIntQueue {
    /**
     * Offer the given value to the queue. This will throw an exception if the given value is the "empty" value.
     * @param value The value to add to the queue.
     * @return `true` if the value was added to the queue,
     * or `false` if the value could not be added because the queue is full.
     */
    fun offer(value: Int): Boolean

    /**
     * Remove and return the next value from the queue, or return the "empty" value if the queue is empty.
     * @return The next value or the "empty" value.
     */
    fun poll(): Int

    /**
     * Remove up to the given limit of elements from the queue, and pass them to the consumer in order.
     * @param limit The maximum number of elements to dequeue.
     * @param consumer The consumer to pass the removed elements to.
     * @return The actual number of elements removed.
     */
    fun drain(limit: Int, consumer: IntConsumer): Int

    /**
     * Add up to the given limit of elements to this queue, from the given supplier.
     * @param limit The maximum number of elements to enqueue.
     * @param supplier The supplier to obtain the elements from.
     * @return The actual number of elements added.
     */
    fun fill(limit: Int, supplier: IntSupplier): Int

    /**
     * Query if the queue is empty or not.
     *
     * This method is inherently racy and the result may be out of date by the time the method returns.
     * @return `true` if the queue was observed to be empty, otherwise `false`.
     */
    fun isEmpty(): Boolean

    /**
     * Query the number of elements currently in the queue.
     *
     * This method is inherently racy and the result may be out of date by the time the method returns.
     * @return An estimate of the number of elements observed in the queue.
     */
    fun size(): Int

    /**
     * This implementation is based on MpscAtomicUnpaddedArrayQueue from JCTools.
     */
    class MpscAtomicIntegerArrayQueue(
        capacity: Int,
        private val emptyValue: Int
    ) : AtomicIntegerArray(MathUtil.safeFindNextPositivePowerOfTwo(capacity)), MpscIntQueue {

        private val mask: Int

        @Volatile
        private var producerIndex: Long = 0
        @Volatile
        private var producerLimit: Long = 0
        @Volatile
        private var consumerIndex: Long = 0

        init {
            if (emptyValue != 0) {
                val end = length() - 1
                for (i in 0 until end) {
                    lazySet(i, emptyValue)
                }
                getAndSet(end, emptyValue) // 'getAndSet' acts as a full barrier, giving us initialization safety.
            }
            mask = length() - 1
        }

        override fun offer(value: Int): Boolean {
            if (value == emptyValue) {
                throw IllegalArgumentException("Cannot offer the \"empty\" value: $emptyValue")
            }
            // use a cached view on consumer index (potentially updated in loop)
            val mask = this.mask
            var producerLimit = this.producerLimit
            var pIndex: Long
            do {
                pIndex = producerIndex
                if (pIndex >= producerLimit) {
                    val cIndex = consumerIndex
                    producerLimit = cIndex + mask + 1
                    if (pIndex >= producerLimit) {
                        // FULL :(
                        return false
                    } else {
                        // update producer limit to the next index that we must recheck the consumer index
                        // this is racy, but the race is benign
                        PRODUCER_LIMIT.lazySet(this, producerLimit)
                    }
                }
            } while (!PRODUCER_INDEX.compareAndSet(this, pIndex, pIndex + 1))
            /*
             * NOTE: the new producer index value is made visible BEFORE the element in the array. If we relied on
             * the index visibility to poll() we would need to handle the case where the element is not visible.
             */
            // Won CAS, move on to storing
            val offset = (pIndex and mask.toLong()).toInt()
            lazySet(offset, value)
            // AWESOME :)
            return true
        }

        override fun poll(): Int {
            val cIndex = consumerIndex
            val offset = (cIndex and mask.toLong()).toInt()
            // If we can't see the next available element we can't poll
            var value = get(offset)
            if (emptyValue == value) {
                /*
                 * NOTE: Queue may not actually be empty in the case of a producer (P1) being interrupted after
                 * winning the CAS on offer but before storing the element in the queue. Other producers may go on
                 * to fill up the queue after this element.
                 */
                if (cIndex != producerIndex) {
                    do {
                        value = get(offset)
                    } while (emptyValue == value)
                } else {
                    return emptyValue
                }
            }
            lazySet(offset, emptyValue)
            CONSUMER_INDEX.lazySet(this, cIndex + 1)
            return value
        }

        override fun drain(limit: Int, consumer: IntConsumer): Int {
            Objects.requireNonNull(consumer, "consumer")
            ObjectUtil.checkPositiveOrZero(limit, "limit")
            if (limit == 0) {
                return 0
            }
            val mask = this.mask
            val cIndex = consumerIndex // Note: could be weakened to plain-load.
            for (i in 0 until limit) {
                val index = cIndex + i
                val offset = (index and mask.toLong()).toInt()
                val value = get(offset)
                if (emptyValue == value) {
                    return i
                }
                lazySet(offset, emptyValue) // Note: could be weakened to plain-store.
                // ordered store -> atomic and ordered for size()
                CONSUMER_INDEX.lazySet(this, index + 1)
                consumer.accept(value)
            }
            return limit
        }

        override fun fill(limit: Int, supplier: IntSupplier): Int {
            Objects.requireNonNull(supplier, "supplier")
            ObjectUtil.checkPositiveOrZero(limit, "limit")
            if (limit == 0) {
                return 0
            }
            val mask = this.mask
            val capacity = (mask + 1).toLong()
            var producerLimit = this.producerLimit
            var pIndex: Long
            var actualLimit: Int
            do {
                pIndex = producerIndex
                var available = producerLimit - pIndex
                if (available <= 0) {
                    val cIndex = consumerIndex
                    producerLimit = cIndex + capacity
                    available = producerLimit - pIndex
                    if (available <= 0) {
                        // FULL :(
                        return 0
                    } else {
                        // update producer limit to the next index that we must recheck the consumer index
                        PRODUCER_LIMIT.lazySet(this, producerLimit)
                    }
                }
                actualLimit = minOf(available.toInt(), limit)
            } while (!PRODUCER_INDEX.compareAndSet(this, pIndex, pIndex + actualLimit))
            // right, now we claimed a few slots and can fill them with goodness
            for (i in 0 until actualLimit) {
                // Won CAS, move on to storing
                val offset = ((pIndex + i) and mask.toLong()).toInt()
                lazySet(offset, supplier.asInt)
            }
            return actualLimit
        }

        override fun isEmpty(): Boolean {
            // Load consumer index before producer index, so our check is conservative.
            val cIndex = consumerIndex
            val pIndex = producerIndex
            return cIndex >= pIndex
        }

        override fun size(): Int {
            // Loop until we get a consistent read of both the consumer and producer indices.
            var after = consumerIndex
            val size: Long
            while (true) {
                val before = after
                val pIndex = producerIndex
                after = consumerIndex
                if (before == after) {
                    size = pIndex - after
                    break
                }
            }
            return if (size < 0) 0 else if (size > Int.MAX_VALUE) Int.MAX_VALUE else size.toInt()
        }

        companion object {
            private const val serialVersionUID = 8740338425124821455L

            private val PRODUCER_INDEX: AtomicLongFieldUpdater<MpscAtomicIntegerArrayQueue> =
                AtomicLongFieldUpdater.newUpdater(MpscAtomicIntegerArrayQueue::class.java, "producerIndex")
            private val PRODUCER_LIMIT: AtomicLongFieldUpdater<MpscAtomicIntegerArrayQueue> =
                AtomicLongFieldUpdater.newUpdater(MpscAtomicIntegerArrayQueue::class.java, "producerLimit")
            private val CONSUMER_INDEX: AtomicLongFieldUpdater<MpscAtomicIntegerArrayQueue> =
                AtomicLongFieldUpdater.newUpdater(MpscAtomicIntegerArrayQueue::class.java, "consumerIndex")
        }
    }

    companion object {
        /**
         * Create a new queue instance of the given size.
         *
         * Note: the size of the queue may be rounded up to nearest power-of-2.
         *
         * @param size The required fixed size of the queue.
         * @param emptyValue The special value that the queue should use to signal the "empty" case.
         * This value will be returned from [poll] when the queue is empty,
         * and giving this value to [offer] will cause an exception to be thrown.
         * @return The queue instance.
         */
        @JvmStatic
        fun create(size: Int, emptyValue: Int): MpscIntQueue {
            return MpscAtomicIntegerArrayQueue(size, emptyValue)
        }
    }
}
