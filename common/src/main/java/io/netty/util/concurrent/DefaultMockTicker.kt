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

import io.netty.util.internal.ObjectUtil.checkPositiveOrZero
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

/**
 * The default [MockTicker] implementation.
 */
internal class DefaultMockTicker : MockTicker {

    // The lock is fair, so waiters get to process condition signals in the order they (the waiters) queued up.
    private val lock = ReentrantLock(true)
    private val tickCondition = lock.newCondition()
    private val sleeperCondition = lock.newCondition()
    private val nanoTimeValue = AtomicLong()
    private val sleepers: MutableSet<Thread> = Collections.newSetFromMap(IdentityHashMap())

    override fun nanoTime(): Long {
        return nanoTimeValue.get()
    }

    @Throws(InterruptedException::class)
    override fun sleep(delay: Long, unit: TimeUnit) {
        checkPositiveOrZero(delay, "delay")
        java.util.Objects.requireNonNull(unit, "unit")

        if (delay == 0L) {
            return
        }

        val delayNanos = unit.toNanos(delay)
        lock.lockInterruptibly()
        try {
            val startTimeNanos = nanoTime()
            sleepers.add(Thread.currentThread())
            sleeperCondition.signalAll()
            do {
                tickCondition.await()
            } while (nanoTime() - startTimeNanos < delayNanos)
        } finally {
            sleepers.remove(Thread.currentThread())
            lock.unlock()
        }
    }

    /**
     * Wait for the given thread to enter the [sleep] method, and block.
     */
    @Throws(InterruptedException::class)
    fun awaitSleepingThread(thread: Thread) {
        lock.lockInterruptibly()
        try {
            while (!sleepers.contains(thread)) {
                sleeperCondition.await()
            }
        } finally {
            lock.unlock()
        }
    }

    override fun advance(amount: Long, unit: TimeUnit) {
        checkPositiveOrZero(amount, "amount")
        java.util.Objects.requireNonNull(unit, "unit")

        if (amount == 0L) {
            return
        }

        val amountNanos = unit.toNanos(amount)
        lock.lock()
        try {
            nanoTimeValue.addAndGet(amountNanos)
            tickCondition.signalAll()
        } finally {
            lock.unlock()
        }
    }
}
