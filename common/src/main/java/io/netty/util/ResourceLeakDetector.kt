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

package io.netty.util

import io.netty.util.internal.EmptyArrays
import io.netty.util.internal.ObjectUtil
import io.netty.util.internal.StringUtil.EMPTY_STRING
import io.netty.util.internal.StringUtil.NEWLINE
import io.netty.util.internal.StringUtil.simpleClassName
import io.netty.util.internal.SystemPropertyUtil
import io.netty.util.internal.logging.InternalLoggerFactory

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.Arrays
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater

open class ResourceLeakDetector<T> {

    /**
     * Represents the level of resource leak detection.
     */
    enum class Level {
        /**
         * Disables resource leak detection.
         */
        DISABLED,

        /**
         * Enables simplistic sampling resource leak detection which reports there is a leak or not,
         * at the cost of small overhead (default).
         */
        SIMPLE,

        /**
         * Enables advanced sampling resource leak detection which reports where the leaked object was accessed
         * recently at the cost of high overhead.
         */
        ADVANCED,

        /**
         * Enables paranoid resource leak detection which reports where the leaked object was accessed recently,
         * at the cost of the highest possible overhead (for testing purposes only).
         */
        PARANOID;

        companion object {
            /**
             * Returns level based on string value. Accepts also string that represents ordinal number of enum.
             *
             * @param levelStr - level string : DISABLED, SIMPLE, ADVANCED, PARANOID. Ignores case.
             * @return corresponding level or SIMPLE level in case of no match.
             */
            @JvmStatic
            fun parseLevel(levelStr: String): Level {
                val trimmedLevelStr = levelStr.trim()
                for (l in entries) {
                    if (trimmedLevelStr.equals(l.name, ignoreCase = true) ||
                        trimmedLevelStr == l.ordinal.toString()
                    ) {
                        return l
                    }
                }
                return DEFAULT_LEVEL
            }
        }
    }

    /** the collection of active resources */
    private val allLeaks: MutableSet<DefaultResourceLeak<*>> = ConcurrentHashMap.newKeySet()

    private val refQueue: ReferenceQueue<Any> = ReferenceQueue()
    private val reportedLeaks: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val resourceType: String
    private val samplingInterval: Int

    /**
     * Will be notified once a leak is detected.
     */
    @Volatile
    private var leakListener: LeakListener? = null

    /**
     * @deprecated use [ResourceLeakDetectorFactory.newResourceLeakDetector].
     */
    @Deprecated("use ResourceLeakDetectorFactory.newResourceLeakDetector(Class, int, long)")
    constructor(resourceType: Class<*>) : this(simpleClassName(resourceType))

    /**
     * @deprecated use [ResourceLeakDetectorFactory.newResourceLeakDetector].
     */
    @Deprecated("use ResourceLeakDetectorFactory.newResourceLeakDetector(Class, int, long)")
    constructor(resourceType: String) : this(resourceType, DEFAULT_SAMPLING_INTERVAL, Long.MAX_VALUE)

    /**
     * @deprecated Use [ResourceLeakDetector(Class, Int)].
     *
     * This should not be used directly by users of [ResourceLeakDetector].
     * Please use [ResourceLeakDetectorFactory.newResourceLeakDetector]
     *
     * @param maxActive This is deprecated and will be ignored.
     */
    @Deprecated("Use ResourceLeakDetector(Class, int) instead")
    constructor(
        resourceType: Class<*>,
        samplingInterval: Int,
        @Suppress("UNUSED_PARAMETER") maxActive: Long
    ) : this(resourceType, samplingInterval)

    /**
     * This should not be used directly by users of [ResourceLeakDetector].
     * Please use [ResourceLeakDetectorFactory.newResourceLeakDetector]
     */
    @Suppress("DEPRECATION")
    constructor(resourceType: Class<*>, samplingInterval: Int) : this(
        simpleClassName(resourceType),
        samplingInterval,
        Long.MAX_VALUE
    )

    /**
     * @deprecated use [ResourceLeakDetectorFactory.newResourceLeakDetector].
     *
     * @param maxActive This is deprecated and will be ignored.
     */
    @Deprecated("use ResourceLeakDetectorFactory.newResourceLeakDetector(Class, int, long)")
    constructor(
        resourceType: String,
        samplingInterval: Int,
        @Suppress("UNUSED_PARAMETER") maxActive: Long
    ) {
        this.resourceType = ObjectUtil.checkNotNull(resourceType, "resourceType")
        this.samplingInterval = samplingInterval
    }

    /**
     * Creates a new [ResourceLeak] which is expected to be closed via [ResourceLeak.close] when the
     * related resource is deallocated.
     *
     * @return the [ResourceLeak] or `null`
     * @deprecated use [track]
     */
    @Deprecated("use track(Object)")
    fun open(obj: T): ResourceLeak? {
        return track0(obj, false)
    }

    /**
     * Creates a new [ResourceLeakTracker] which is expected to be closed via
     * [ResourceLeakTracker.close] when the related resource is deallocated.
     *
     * @return the [ResourceLeakTracker] or `null`
     */
    open fun track(obj: T): ResourceLeakTracker<T>? {
        return track0(obj, false)
    }

    /**
     * Creates a new [ResourceLeakTracker] which is expected to be closed via
     * [ResourceLeakTracker.close] when the related resource is deallocated.
     *
     * Unlike [track], this method always returns a tracker, regardless
     * of the detection settings.
     *
     * @return the [ResourceLeakTracker]
     */
    open fun trackForcibly(obj: T): ResourceLeakTracker<T> {
        return track0(obj, true)!!
    }

    /**
     * Check whether [ResourceLeakTracker.record] does anything for this detector.
     *
     * @return `true` if [ResourceLeakTracker.record] should be called
     */
    open fun isRecordEnabled(): Boolean {
        val level = getLevel()
        return (level == Level.ADVANCED || level == Level.PARANOID) && TARGET_RECORDS > 0
    }

    private fun track0(obj: T, force: Boolean): DefaultResourceLeak<T>? {
        val level = ResourceLeakDetector.level
        if (force ||
            level == Level.PARANOID ||
            (level != Level.DISABLED && ThreadLocalRandom.current().nextInt(samplingInterval) == 0)
        ) {
            reportLeak()
            return DefaultResourceLeak(obj as Any, refQueue, allLeaks, getInitialHint(resourceType))
        }
        return null
    }

    private fun clearRefQueue() {
        while (true) {
            val ref = refQueue.poll() as? DefaultResourceLeak<*> ?: break
            ref.dispose()
        }
    }

    /**
     * When the return value is `true`, [reportTracedLeak] and [reportUntracedLeak]
     * will be called once a leak is detected, otherwise not.
     *
     * @return `true` to enable leak reporting.
     */
    protected open fun needReport(): Boolean {
        return logger.isErrorEnabled()
    }

    private fun reportLeak() {
        if (!needReport()) {
            clearRefQueue()
            return
        }

        // Detect and report previous leaks.
        while (true) {
            val ref = refQueue.poll() as? DefaultResourceLeak<*> ?: break

            if (!ref.dispose()) {
                continue
            }

            val records = ref.getReportAndClearRecords()
            if (reportedLeaks.add(records)) {
                if (records.isEmpty()) {
                    reportUntracedLeak(resourceType)
                } else {
                    reportTracedLeak(resourceType, records)
                }

                val listener = leakListener
                listener?.onLeak(resourceType, records)
            }
        }
    }

    /**
     * This method is called when a traced leak is detected. It can be overridden for tracking how many times leaks
     * have been detected.
     */
    protected open fun reportTracedLeak(resourceType: String, records: String) {
        logger.error(
            "LEAK: {}.release() was not called before it's garbage-collected. " +
                "See https://netty.io/wiki/reference-counted-objects.html for more information.{}",
            resourceType, records
        )
    }

    /**
     * This method is called when an untraced leak is detected. It can be overridden for tracking how many times leaks
     * have been detected.
     */
    protected open fun reportUntracedLeak(resourceType: String) {
        logger.error(
            "LEAK: {}.release() was not called before it's garbage-collected. " +
                "Enable advanced leak reporting to find out where the leak occurred. " +
                "To enable advanced leak reporting, " +
                "specify the JVM option '-D{}={}' or call {}.setLevel() " +
                "See https://netty.io/wiki/reference-counted-objects.html for more information.",
            resourceType, PROP_LEVEL, Level.ADVANCED.name.lowercase(), simpleClassName(this)
        )
    }

    /**
     * @deprecated This method will no longer be invoked by [ResourceLeakDetector].
     */
    @Deprecated("This method will no longer be invoked by ResourceLeakDetector")
    protected open fun reportInstancesLeak(resourceType: String) {
    }

    /**
     * Create a hint object to be attached to an object tracked by this record. Similar to the additional information
     * supplied to [ResourceLeakTracker.record], will be printed alongside the stack trace of the
     * creation of the resource.
     */
    protected open fun getInitialHint(resourceType: String): Any? {
        return null
    }

    /**
     * Set leak listener. Previous listener will be replaced.
     */
    fun setLeakListener(leakListener: LeakListener?) {
        this.leakListener = leakListener
    }

    interface LeakListener {
        /**
         * Will be called once a leak is detected.
         */
        fun onLeak(resourceType: String, records: String)
    }

    @Suppress("DEPRECATION")
    private class DefaultResourceLeak<T>(
        referent: Any,
        refQueue: ReferenceQueue<Any>,
        private val allLeaks: MutableSet<DefaultResourceLeak<*>>,
        initialHint: Any?
    ) : WeakReference<Any>(referent, refQueue), ResourceLeakTracker<T>, ResourceLeak {

        @Suppress("unused")
        @Volatile
        private var head: TraceRecord? = null

        @Suppress("unused")
        @Volatile
        private var droppedRecords: Int = 0

        private val trackedHash: Int = System.identityHashCode(referent)

        init {
            assert(referent != null)
            allLeaks.add(this)
            // Create a new Record so we always have the creation stacktrace included.
            headUpdater.set(this, if (initialHint == null) {
                TraceRecord(TraceRecord.BOTTOM)
            } else {
                TraceRecord(TraceRecord.BOTTOM, initialHint)
            })
        }

        override fun record() {
            record0(null)
        }

        override fun record(hint: Any?) {
            record0(hint)
        }

        /**
         * This method works by exponentially backing off as more records are present in the stack. Each record has a
         * 1 / 2^n chance of dropping the top most record and replacing it with itself. This has a number of convenient
         * properties:
         *
         *  1. The current record is always recorded. This is due to the compare and swap dropping the top most
         *     record, rather than the to-be-pushed record.
         *  2. The very last access will always be recorded. This comes as a property of 1.
         *  3. It is possible to retain more records than the target, based upon the probability distribution.
         *  4. It is easy to keep a precise record of the number of elements in the stack, since each element has to
         *     know how tall the stack is.
         *
         * In this particular implementation, there are also some advantages. A thread local random is used to decide
         * if something should be recorded. This means that if there is a deterministic access pattern, it is now
         * possible to see what other accesses occur, rather than always dropping them. Second, after
         * [TARGET_RECORDS] accesses, backoff occurs. This matches typical access patterns,
         * where there are either a high number of accesses (i.e. a cached buffer), or low (an ephemeral buffer), but
         * not many in between.
         *
         * The use of atomics avoids serializing a high number of accesses, when most of the records will be thrown
         * away. High contention only happens when there are very few existing records, which is only likely when the
         * object isn't shared! If this is a problem, the loop can be aborted and the record dropped, because another
         * thread won the race.
         */
        private fun record0(hint: Any?) {
            // Check TARGET_RECORDS > 0 here to avoid similar check before remove from and add to lastRecords
            if (TARGET_RECORDS > 0) {
                var oldHead: TraceRecord?
                var prevHead: TraceRecord?
                var newHead: TraceRecord
                var dropped: Boolean
                do {
                    oldHead = headUpdater.get(this)
                    prevHead = oldHead
                    if (prevHead == null || oldHead!!.pos == TraceRecord.CLOSE_MARK_POS) {
                        // already closed.
                        return
                    }
                    val numElements = oldHead.pos + 1
                    if (numElements >= TARGET_RECORDS) {
                        val backOffFactor = Math.min(numElements - TARGET_RECORDS, 30)
                        dropped = ThreadLocalRandom.current().nextInt(1 shl backOffFactor) != 0
                        if (dropped) {
                            prevHead = oldHead.next
                        }
                    } else {
                        dropped = false
                    }
                    newHead = if (hint != null) TraceRecord(prevHead!!, hint) else TraceRecord(prevHead!!)
                } while (!headUpdater.compareAndSet(this, oldHead, newHead))
                if (dropped) {
                    droppedRecordsUpdater.incrementAndGet(this)
                }
            }
        }

        fun dispose(): Boolean {
            clear()
            return allLeaks.remove(this)
        }

        override fun close(): Boolean {
            if (allLeaks.remove(this)) {
                // Call clear so the reference is not even enqueued.
                clear()
                headUpdater.set(this, if (TRACK_CLOSE) TraceRecord(true) else null)
                return true
            }
            return false
        }

        override fun close(trackedObject: T): Boolean {
            // Ensure that the object that was tracked is the same as the one that was passed to close(...).
            assert(trackedHash == System.identityHashCode(trackedObject))

            try {
                return close()
            } finally {
                // This method will do `synchronized(trackedObject)` and we should be sure this will not cause deadlock.
                // It should not, because somewhere up the callstack should be a (successful) `trackedObject.release`,
                // therefore it is unreasonable that anyone else, anywhere, is holding a lock on the trackedObject.
                // (Unreasonable but possible, unfortunately.)
                reachabilityFence0(trackedObject)
            }
        }

        override fun getCloseStackTraceIfAny(): Throwable? {
            val head = headUpdater.get(this)
            if (head != null && head.pos == TraceRecord.CLOSE_MARK_POS) {
                return head
            }
            return null
        }

        override fun toString(): String {
            val oldHead = headUpdater.get(this)
            return generateReport(oldHead)
        }

        fun getReportAndClearRecords(): String {
            val oldHead = headUpdater.getAndSet(this, null)
            return generateReport(oldHead)
        }

        private fun generateReport(oldHead: TraceRecord?): String {
            if (oldHead == null) {
                // Already closed
                return EMPTY_STRING
            }

            val dropped = droppedRecordsUpdater.get(this)
            var duped = 0

            val present = oldHead.pos + 1
            // Guess about 2 kilobytes per stack trace
            val buf = StringBuilder(present * 2048).append(NEWLINE)
            buf.append("Recent access records: ").append(NEWLINE)

            var i = 1
            val seen = HashSet<String>(present)
            var current: TraceRecord? = oldHead
            while (current !== TraceRecord.BOTTOM) {
                val s = current!!.toString()
                if (seen.add(s)) {
                    if (current.next === TraceRecord.BOTTOM) {
                        buf.append("Created at:").append(NEWLINE).append(s)
                    } else {
                        buf.append('#').append(i++).append(':').append(NEWLINE).append(s)
                    }
                } else {
                    duped++
                }
                current = current.next
            }

            if (duped > 0) {
                buf.append(": ")
                    .append(duped)
                    .append(" leak records were discarded because they were duplicates")
                    .append(NEWLINE)
            }

            if (dropped > 0) {
                buf.append(": ")
                    .append(dropped)
                    .append(" leak records were discarded because the leak record count is targeted to ")
                    .append(TARGET_RECORDS)
                    .append(". Use system property ")
                    .append(PROP_TARGET_RECORDS)
                    .append(" to increase the limit.")
                    .append(NEWLINE)
            }

            buf.setLength(buf.length - NEWLINE.length)
            return buf.toString()
        }

        companion object {
            @Suppress("UNCHECKED_CAST")
            private val headUpdater: AtomicReferenceFieldUpdater<DefaultResourceLeak<*>, TraceRecord> =
                AtomicReferenceFieldUpdater.newUpdater(
                    DefaultResourceLeak::class.java,
                    TraceRecord::class.java,
                    "head"
                ) as AtomicReferenceFieldUpdater<DefaultResourceLeak<*>, TraceRecord>

            @Suppress("UNCHECKED_CAST")
            private val droppedRecordsUpdater: AtomicIntegerFieldUpdater<DefaultResourceLeak<*>> =
                AtomicIntegerFieldUpdater.newUpdater(
                    DefaultResourceLeak::class.java,
                    "droppedRecords"
                ) as AtomicIntegerFieldUpdater<DefaultResourceLeak<*>>

            /**
             * Ensures that the object referenced by the given reference remains
             * strongly reachable, regardless of any prior actions of the program that might otherwise cause
             * the object to become unreachable; thus, the referenced object is not
             * reclaimable by garbage collection at least until after the invocation of
             * this method.
             *
             * Recent versions of the JDK have a nasty habit of prematurely deciding objects are unreachable.
             * see: https://stackoverflow.com/questions/26642153/finalize-called-on-strongly-reachable-object-in-java-8
             * The Java 9 method Reference.reachabilityFence offers a solution to this problem.
             *
             * This method is always implemented as a synchronization on [ref], not as
             * `Reference.reachabilityFence` for consistency across platforms and to allow building on JDK 6-8.
             * **It is the caller's responsibility to ensure that this synchronization will not cause deadlock.**
             *
             * @param ref the reference. If `null`, this method has no effect.
             * @see java.lang.ref.Reference.reachabilityFence
             */
            private fun reachabilityFence0(ref: Any?) {
                if (ref != null) {
                    synchronized(ref) {
                        // Empty synchronized is ok: https://stackoverflow.com/a/31933260/1151521
                    }
                }
            }
        }
    }

    private open class TraceRecord : Throwable {
        val hintString: String?
        val next: TraceRecord?
        val pos: Int

        constructor(next: TraceRecord, hint: Any) : super() {
            // This needs to be generated even if toString() is never called as it may change later on.
            hintString = if (hint is ResourceLeakHint) hint.toHintString() else hint.toString()
            this.next = next
            this.pos = next.pos + 1
        }

        constructor(next: TraceRecord) : super() {
            hintString = null
            this.next = next
            this.pos = next.pos + 1
        }

        // Used to terminate the stack
        constructor(closeMarker: Boolean) : super() {
            hintString = null
            next = null
            pos = if (closeMarker) CLOSE_MARK_POS else BOTTOM_POS
        }

        override fun toString(): String {
            val buf = StringBuilder(2048)
            if (hintString != null) {
                buf.append("\tHint: ").append(hintString).append(NEWLINE)
            }

            // Append the stack trace.
            val array = stackTrace
            // Skip the first three elements.
            var i = 3
            outer@ while (i < array.size) {
                val element = array[i]
                // Strip the noisy stack trace elements.
                val exclusions = excludedMethods.get()
                var k = 0
                while (k < exclusions.size) {
                    // Suppress a warning about out of bounds access
                    // since the length of excludedMethods is always even, see addExclusions()
                    if (exclusions[k] == element.className &&
                        exclusions[k + 1] == element.methodName
                    ) {
                        i++
                        continue@outer
                    }
                    k += 2
                }

                buf.append('\t')
                buf.append(element.toString())
                buf.append(NEWLINE)
                i++
            }
            return buf.toString()
        }

        companion object {
            private const val serialVersionUID: Long = 6065153674892850720L
            const val BOTTOM_POS: Int = -1
            const val CLOSE_MARK_POS: Int = -2

            @JvmField
            val BOTTOM: TraceRecord = object : TraceRecord(false) {
                private val serialVersionUID: Long = 7396077602074694571L

                // Override fillInStackTrace() so we not populate the backtrace via a native call and so leak the
                // Classloader.
                // See https://github.com/netty/netty/pull/10691
                override fun fillInStackTrace(): Throwable {
                    return this
                }
            }
        }
    }

    companion object {
        private const val PROP_LEVEL_OLD: String = "io.netty.leakDetectionLevel"
        private const val PROP_LEVEL: String = "io.netty.leakDetection.level"
        private val DEFAULT_LEVEL: Level = Level.SIMPLE

        private const val PROP_TARGET_RECORDS: String = "io.netty.leakDetection.targetRecords"
        private const val DEFAULT_TARGET_RECORDS: Int = 4

        private const val PROP_SAMPLING_INTERVAL: String = "io.netty.leakDetection.samplingInterval"
        // There is a minor performance benefit in TLR if this is a power of 2.
        private const val DEFAULT_SAMPLING_INTERVAL: Int = 128

        private const val PROP_TRACK_CLOSE: String = "io.netty.leakDetection.trackClose"
        private const val DEFAULT_TRACK_CLOSE: Boolean = true

        private val TARGET_RECORDS: Int
        @JvmField
        val SAMPLING_INTERVAL: Int
        private val TRACK_CLOSE: Boolean

        private var level: Level

        private val logger = InternalLoggerFactory.getInstance(ResourceLeakDetector::class.java)

        init {
            val disabled: Boolean
            if (SystemPropertyUtil.get("io.netty.noResourceLeakDetection") != null) {
                disabled = SystemPropertyUtil.getBoolean("io.netty.noResourceLeakDetection", false)
                logger.debug("-Dio.netty.noResourceLeakDetection: {}", disabled)
                logger.warn(
                    "-Dio.netty.noResourceLeakDetection is deprecated. Use '-D{}={}' instead.",
                    PROP_LEVEL, Level.DISABLED.name.lowercase()
                )
            } else {
                disabled = false
            }

            val defaultLevel = if (disabled) Level.DISABLED else DEFAULT_LEVEL

            // First read old property name
            var levelStr: String = SystemPropertyUtil.get(PROP_LEVEL_OLD, defaultLevel.name) ?: defaultLevel.name

            // If new property name is present, use it
            levelStr = SystemPropertyUtil.get(PROP_LEVEL, levelStr) ?: levelStr
            val parsedLevel = Level.parseLevel(levelStr)

            TARGET_RECORDS = SystemPropertyUtil.getInt(PROP_TARGET_RECORDS, DEFAULT_TARGET_RECORDS)
            SAMPLING_INTERVAL = SystemPropertyUtil.getInt(PROP_SAMPLING_INTERVAL, DEFAULT_SAMPLING_INTERVAL)
            TRACK_CLOSE = SystemPropertyUtil.getBoolean(PROP_TRACK_CLOSE, DEFAULT_TRACK_CLOSE)

            level = parsedLevel
            if (logger.isDebugEnabled()) {
                logger.debug("-D{}: {}", PROP_LEVEL, parsedLevel.name.lowercase())
                logger.debug("-D{}: {}", PROP_TARGET_RECORDS, TARGET_RECORDS)
            }
        }

        /**
         * @deprecated Use [setLevel] instead.
         */
        @JvmStatic
        @Deprecated("Use setLevel(Level) instead")
        fun setEnabled(enabled: Boolean) {
            setLevel(if (enabled) Level.SIMPLE else Level.DISABLED)
        }

        /**
         * Returns `true` if resource leak detection is enabled.
         */
        @JvmStatic
        fun isEnabled(): Boolean {
            return getLevel().ordinal > Level.DISABLED.ordinal
        }

        /**
         * Sets the resource leak detection level.
         */
        @JvmStatic
        fun setLevel(level: Level) {
            ResourceLeakDetector.level = ObjectUtil.checkNotNull(level, "level")
        }

        /**
         * Returns the current resource leak detection level.
         */
        @JvmStatic
        fun getLevel(): Level {
            return level
        }

        private val excludedMethods: AtomicReference<Array<String>> =
            AtomicReference(EmptyArrays.EMPTY_STRINGS)

        @JvmStatic
        fun addExclusions(clz: Class<*>, vararg methodNames: String) {
            val nameSet = HashSet(methodNames.asList())
            // Use loop rather than lookup. This avoids knowing the parameters, and doesn't have to handle
            // NoSuchMethodException.
            for (method in clz.declaredMethods) {
                if (nameSet.remove(method.name) && nameSet.isEmpty()) {
                    break
                }
            }
            if (nameSet.isNotEmpty()) {
                throw IllegalArgumentException("Can't find '$nameSet' in ${clz.name}")
            }
            var oldMethods: Array<String>
            var newMethods: Array<String>
            do {
                oldMethods = excludedMethods.get()
                newMethods = Arrays.copyOf(oldMethods, oldMethods.size + 2 * methodNames.size)
                for (i in methodNames.indices) {
                    newMethods[oldMethods.size + i * 2] = clz.name
                    newMethods[oldMethods.size + i * 2 + 1] = methodNames[i]
                }
            } while (!excludedMethods.compareAndSet(oldMethods, newMethods))
        }
    }
}
