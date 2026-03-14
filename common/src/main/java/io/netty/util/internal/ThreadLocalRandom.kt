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

/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * https://creativecommons.org/publicdomain/zero/1.0/
 */
package io.netty.util.internal

import io.netty.util.internal.ObjectUtil.checkPositive
import io.netty.util.internal.logging.InternalLoggerFactory
import java.security.SecureRandom
import java.util.Random
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * A random number generator isolated to the current thread.
 *
 * //since 1.7
 * //author Doug Lea
 */
@Deprecated("Use java.util.concurrent.ThreadLocalRandom instead")
@Suppress("all")
class ThreadLocalRandom internal constructor() : Random(newSeed()) {

    /**
     * The random seed. We can't use super.seed.
     */
    private var rnd: Long = 0

    /**
     * Initialization flag to permit calls to setSeed to succeed only
     * while executing the Random constructor.
     */
    @JvmField
    internal var initialized: Boolean = false

    // Padding to help avoid memory contention among seed updates in
    // different TLRs in the common case that they are located near
    // each other.
    @Suppress("unused")
    private var pad0: Long = 0
    @Suppress("unused")
    private var pad1: Long = 0
    @Suppress("unused")
    private var pad2: Long = 0
    @Suppress("unused")
    private var pad3: Long = 0
    @Suppress("unused")
    private var pad4: Long = 0
    @Suppress("unused")
    private var pad5: Long = 0
    @Suppress("unused")
    private var pad6: Long = 0
    @Suppress("unused")
    private var pad7: Long = 0

    init {
        initialized = true
    }

    /**
     * Throws [UnsupportedOperationException]. Setting seeds in
     * this generator is not supported.
     *
     * @throws UnsupportedOperationException always
     */
    override fun setSeed(seed: Long) {
        if (initialized) {
            throw UnsupportedOperationException()
        }
        rnd = (seed xor multiplier) and seedMask
    }

    override fun next(bits: Int): Int {
        rnd = (rnd * multiplier + addend) and seedMask
        return (rnd ushr (48 - bits)).toInt()
    }

    /**
     * Returns a pseudorandom, uniformly distributed value between the
     * given least value (inclusive) and bound (exclusive).
     *
     * @param least the least value returned
     * @param bound the upper bound (exclusive)
     * @throws IllegalArgumentException if least greater than or equal to bound
     * @return the next value
     */
    override fun nextInt(least: Int, bound: Int): Int {
        if (least >= bound) {
            throw IllegalArgumentException()
        }
        return nextInt(bound - least) + least
    }

    /**
     * Returns a pseudorandom, uniformly distributed value
     * between 0 (inclusive) and the specified value (exclusive).
     *
     * @param n the bound on the random number to be returned. Must be positive.
     * @return the next value
     * @throws IllegalArgumentException if n is not positive
     */
    override fun nextLong(n: Long): Long {
        checkPositive(n, "n")

        @Suppress("NAME_SHADOWING")
        var n = n
        var offset: Long = 0
        while (n >= Int.MAX_VALUE.toLong()) {
            val bits = next(2)
            val half = n ushr 1
            val nextn = if (bits and 2 == 0) half else n - half
            if (bits and 1 == 0) {
                offset += n - nextn
            }
            n = nextn
        }
        return offset + nextInt(n.toInt())
    }

    /**
     * Returns a pseudorandom, uniformly distributed value between the
     * given least value (inclusive) and bound (exclusive).
     *
     * @param least the least value returned
     * @param bound the upper bound (exclusive)
     * @return the next value
     * @throws IllegalArgumentException if least greater than or equal to bound
     */
    override fun nextLong(least: Long, bound: Long): Long {
        if (least >= bound) {
            throw IllegalArgumentException()
        }
        return nextLong(bound - least) + least
    }

    /**
     * Returns a pseudorandom, uniformly distributed `double` value
     * between 0 (inclusive) and the specified value (exclusive).
     *
     * @param n the bound on the random number to be returned. Must be positive.
     * @return the next value
     * @throws IllegalArgumentException if n is not positive
     */
    override fun nextDouble(n: Double): Double {
        checkPositive(n, "n")
        return nextDouble() * n
    }

    /**
     * Returns a pseudorandom, uniformly distributed value between the
     * given least value (inclusive) and bound (exclusive).
     *
     * @param least the least value returned
     * @param bound the upper bound (exclusive)
     * @return the next value
     * @throws IllegalArgumentException if least greater than or equal to bound
     */
    override fun nextDouble(least: Double, bound: Double): Double {
        if (least >= bound) {
            throw IllegalArgumentException()
        }
        return nextDouble() * (bound - least) + least
    }

    companion object {
        private val logger = InternalLoggerFactory.getInstance(ThreadLocalRandom::class.java)

        private val seedUniquifier = AtomicLong()

        @Volatile
        @JvmStatic
        private var initialSeedUniquifier: Long = 0

        private val seedGeneratorThread: Thread?
        private val seedQueue: LinkedBlockingQueue<Long>?
        private val seedGeneratorStartTime: Long

        @Volatile
        @JvmStatic
        private var seedGeneratorEndTime: Long = 0

        // same constants as Random, but must be redeclared because private
        private const val multiplier = 0x5DEECE66DL
        private const val addend = 0xBL
        private const val seedMask = (1L shl 48) - 1

        private const val serialVersionUID = -5851777807851030925L

        init {
            initialSeedUniquifier = SystemPropertyUtil.getLong("io.netty.initialSeedUniquifier", 0)
            if (initialSeedUniquifier == 0L) {
                val secureRandom = SystemPropertyUtil.getBoolean("java.util.secureRandomSeed", false)
                if (secureRandom) {
                    seedQueue = LinkedBlockingQueue<Long>()
                    seedGeneratorStartTime = System.nanoTime()

                    // Try to generate a real random number from /dev/random.
                    // Get from a different thread to avoid blocking indefinitely on a machine without much entropy.
                    seedGeneratorThread = object : Thread("initialSeedUniquifierGenerator") {
                        override fun run() {
                            val random = SecureRandom() // Get the real random seed from /dev/random
                            val seed = random.generateSeed(8)
                            seedGeneratorEndTime = System.nanoTime()
                            val s = (seed[0].toLong() and 0xff shl 56) or
                                    (seed[1].toLong() and 0xff shl 48) or
                                    (seed[2].toLong() and 0xff shl 40) or
                                    (seed[3].toLong() and 0xff shl 32) or
                                    (seed[4].toLong() and 0xff shl 24) or
                                    (seed[5].toLong() and 0xff shl 16) or
                                    (seed[6].toLong() and 0xff shl 8) or
                                    (seed[7].toLong() and 0xff)
                            seedQueue.add(s)
                        }
                    }
                    seedGeneratorThread.isDaemon = true
                    seedGeneratorThread.uncaughtExceptionHandler =
                        Thread.UncaughtExceptionHandler { t, e ->
                            logger.debug("An exception has been raised by {}", t.name, e)
                        }
                    seedGeneratorThread.start()
                } else {
                    initialSeedUniquifier = mix64(System.currentTimeMillis()) xor mix64(System.nanoTime())
                    seedGeneratorThread = null
                    seedQueue = null
                    seedGeneratorStartTime = 0L
                }
            } else {
                seedGeneratorThread = null
                seedQueue = null
                seedGeneratorStartTime = 0L
            }
        }

        @JvmStatic
        fun setInitialSeedUniquifier(initialSeedUniquifier: Long) {
            ThreadLocalRandom.initialSeedUniquifier = initialSeedUniquifier
        }

        @JvmStatic
        fun getInitialSeedUniquifier(): Long {
            // Use the value set via the setter.
            var initialSeedUniquifier = ThreadLocalRandom.initialSeedUniquifier
            if (initialSeedUniquifier != 0L) {
                return initialSeedUniquifier
            }

            synchronized(ThreadLocalRandom::class.java) {
                initialSeedUniquifier = ThreadLocalRandom.initialSeedUniquifier
                if (initialSeedUniquifier != 0L) {
                    return initialSeedUniquifier
                }

                // Get the random seed from the generator thread with timeout.
                val timeoutSeconds = 3L
                val deadLine = seedGeneratorStartTime + TimeUnit.SECONDS.toNanos(timeoutSeconds)
                var interrupted = false
                while (true) {
                    val waitTime = deadLine - System.nanoTime()
                    try {
                        val seed: Long? = if (waitTime <= 0) {
                            seedQueue!!.poll()
                        } else {
                            seedQueue!!.poll(waitTime, TimeUnit.NANOSECONDS)
                        }

                        if (seed != null) {
                            initialSeedUniquifier = seed
                            break
                        }
                    } catch (e: InterruptedException) {
                        interrupted = true
                        logger.warn("Failed to generate a seed from SecureRandom due to an InterruptedException.")
                        break
                    }

                    if (waitTime <= 0) {
                        seedGeneratorThread!!.interrupt()
                        logger.warn(
                            "Failed to generate a seed from SecureRandom within {} seconds. " +
                                    "Not enough entropy?", timeoutSeconds
                        )
                        break
                    }
                }

                // Just in case the initialSeedUniquifier is zero or some other constant
                initialSeedUniquifier = initialSeedUniquifier xor 0x3255ecdc33bae119L // just a meaningless random number
                initialSeedUniquifier = initialSeedUniquifier xor java.lang.Long.reverse(System.nanoTime())

                ThreadLocalRandom.initialSeedUniquifier = initialSeedUniquifier

                if (interrupted) {
                    // Restore the interrupt status because we don't know how to/don't need to handle it here.
                    Thread.currentThread().interrupt()

                    // Interrupt the generator thread if it's still running,
                    // in the hope that the SecureRandom provider raises an exception on interruption.
                    seedGeneratorThread!!.interrupt()
                }

                if (seedGeneratorEndTime == 0L) {
                    seedGeneratorEndTime = System.nanoTime()
                }

                return initialSeedUniquifier
            }
        }

        @JvmStatic
        private fun newSeed(): Long {
            while (true) {
                val current = seedUniquifier.get()
                val actualCurrent = if (current != 0L) current else getInitialSeedUniquifier()

                // L'Ecuyer, "Tables of Linear Congruential Generators of Different Sizes and Good Lattice Structure", 1999
                val next = actualCurrent * 181783497276652981L

                if (seedUniquifier.compareAndSet(current, next)) {
                    if (current == 0L && logger.isDebugEnabled()) {
                        if (seedGeneratorEndTime != 0L) {
                            logger.debug(
                                String.format(
                                    "-Dio.netty.initialSeedUniquifier: 0x%016x (took %d ms)",
                                    actualCurrent,
                                    TimeUnit.NANOSECONDS.toMillis(seedGeneratorEndTime - seedGeneratorStartTime)
                                )
                            )
                        } else {
                            logger.debug(String.format("-Dio.netty.initialSeedUniquifier: 0x%016x", actualCurrent))
                        }
                    }
                    return next xor System.nanoTime()
                }
            }
        }

        // Borrowed from
        // http://gee.cs.oswego.edu/cgi-bin/viewcvs.cgi/jsr166/src/main/java/util/concurrent/ThreadLocalRandom.java
        @JvmStatic
        private fun mix64(z: Long): Long {
            @Suppress("NAME_SHADOWING")
            var z = z
            z = (z xor (z ushr 33)) * -0xae502812aa7333L // 0xff51afd7ed558ccdL
            z = (z xor (z ushr 33)) * -0x3b314601e57a13adL // 0xc4ceb9fe1a85ec53L
            return z xor (z ushr 33)
        }

        /**
         * Returns the current thread's [ThreadLocalRandom].
         *
         * @return the current thread's [ThreadLocalRandom]
         */
        @JvmStatic
        fun current(): ThreadLocalRandom = InternalThreadLocalMap.get().random()
    }
}
