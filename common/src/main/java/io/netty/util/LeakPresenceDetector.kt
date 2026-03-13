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
package io.netty.util

import io.netty.util.internal.SystemPropertyUtil
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.LongAdder
import java.util.function.Supplier

/**
 * Alternative leak detector implementation for reliable and performant detection in tests.
 *
 * ### Background
 *
 * The standard [ResourceLeakDetector] produces no "false positives", but this comes with tradeoffs. You either
 * get many false negatives because only a small sample of buffers is instrumented, or you turn on paranoid detection
 * which carries a somewhat heavy performance cost with each allocation. Additionally, paranoid detection enables
 * detailed recording of buffer access operations with heavy performance impact. Avoiding false negatives is necessary
 * for (unit, fuzz...) testing if bugs should lead to reliable test failures, but the performance impact can be
 * prohibitive for some tests.
 *
 * ### The presence detector
 *
 * The *leak presence detector* takes a different approach. It foregoes detailed tracking of allocation and
 * modification stack traces. In return every resource is counted, so there are no false negatives where a leak would
 * not be detected.
 *
 * The presence detector also does not wait for an unclosed resource to be garbage collected before it's reported as
 * leaked. This ensures that leaks are detected promptly and can be directly associated with a particular test, but it
 * can lead to false positives. Tests that use the presence detector must shut down completely *before* checking
 * for resource leaks. There are also complications with static fields, described below.
 *
 * ### Resource Scopes
 *
 * A resource scope manages all resources of a set of threads over time. On allocation, a resource is assigned to a
 * scope through the [currentScope] method. When [check] is called, or the scope is
 * [closed][ResourceScope.close], all resources in that scope must have been released.
 *
 * By default, there is only a single "global" scope, and when [check] is called, all resources in the entire
 * JVM must have been released. To enable parallel test execution, it may be necessary to use separate scopes for
 * separate tests instead, so that one test can check for its own leaks while another test is still in progress. You
 * can override [currentScope] to implement this for your test framework.
 *
 * ### Static Fields
 *
 * While the presence detector requires that *all* resources be closed after a test, some resources kept in static
 * fields cannot be released, or there would be false positives. To avoid this, resources created inside static
 * initializers, specifically when the allocation stack trace contains a `<clinit>` method, *are not
 * tracked*.
 *
 * Because the presence detector does not normally capture or introspect allocation stack traces, additional
 * cooperation is required. Any static initializer must be wrapped in a [staticInitializer] call,
 * which will temporarily enable stack trace introspection.
 *
 * Since stack traces are not captured by default, it can be difficult to tell apart a real leak from a missed static
 * initializer. You can temporarily turn on allocation stack trace capture using the
 * `-Dio.netty.util.LeakPresenceDetector.trackCreationStack=true` system property.
 *
 * @param T The resource type to detect
 */
open class LeakPresenceDetector<T> : ResourceLeakDetector<T> {

    /**
     * Create a new detector for the given resource type.
     *
     * @param resourceType The resource type
     */
    constructor(resourceType: Class<*>) : super(resourceType, 0)

    /**
     * This constructor should not be used directly, it is called reflectively by [ResourceLeakDetectorFactory].
     *
     * @param resourceType The resource type
     * @param samplingInterval Ignored
     */
    @Deprecated("Use primary constructor instead")
    @Suppress("unused")
    constructor(resourceType: Class<*>, samplingInterval: Int) : this(resourceType)

    /**
     * This constructor should not be used directly, it is called reflectively by [ResourceLeakDetectorFactory].
     *
     * @param resourceType The resource type
     * @param samplingInterval Ignored
     * @param maxActive Ignored
     */
    @Suppress("unused")
    constructor(resourceType: Class<*>, samplingInterval: Int, maxActive: Long) : this(resourceType)

    /**
     * Get the resource scope for the current thread. This is used to assign resources to scopes, and it is used by
     * [check] to tell which scope to check for open resources. By default, the global scope is returned.
     *
     * @return The resource scope to use
     */
    protected open fun currentScope(): ResourceScope = GLOBAL

    final override fun track(obj: T): ResourceLeakTracker<T>? {
        if (inStaticInitializerFast()) {
            return null
        }
        return trackForcibly(obj)
    }

    final override fun trackForcibly(obj: T): ResourceLeakTracker<T> =
        PresenceTracker(currentScope())

    final override fun isRecordEnabled(): Boolean = false

    internal class PresenceTracker<T>(private val scope: ResourceScope) : AtomicBoolean(false), ResourceLeakTracker<T> {

        init {
            scope.checkOpen()
            scope.openResourceCounter.increment()
            if (TRACK_CREATION_STACK) {
                scope.creationStacks!!.put(this, LeakCreation())
            }
        }

        override fun record() {}

        override fun record(hint: Any?) {}

        override fun close(trackedObject: T): Boolean {
            if (compareAndSet(false, true)) {
                scope.openResourceCounter.decrement()
                if (TRACK_CREATION_STACK) {
                    scope.creationStacks!!.remove(this)
                }
                scope.checkOpen()
                return true
            }
            return false
        }
    }

    /**
     * A resource scope keeps track of the resources for a particular set of threads. Different scopes can be checked
     * for leaks separately, to enable parallel test execution.
     */
    class ResourceScope(
        @JvmField val name: String
    ) : Closeable {

        @JvmField
        val openResourceCounter: LongAdder = LongAdder()

        @JvmField
        internal val creationStacks: MutableMap<PresenceTracker<*>, Throwable>? =
            if (TRACK_CREATION_STACK) ConcurrentHashMap() else null

        @JvmField
        var closed: Int = 1

        fun checkOpen() {
            if (closed == 0) {
                throw AllocationProhibitedException("Resource scope '$name' already closed")
            }
        }

        fun check() {
            val n = openResourceCounter.sumThenReset()
            if (n != 0L) {
                val msg = StringBuilder("Possible memory leak detected for resource scope '")
                    .append(name).append("'. ")
                if (n < 0) {
                    msg.append(
                        "Resource count was negative: A resource previously reported as a leak was released " +
                                "after all. Please ensure that that resource is released before its test finishes."
                    )
                    throw IllegalStateException(msg.toString())
                }
                if (TRACK_CREATION_STACK) {
                    msg.append("Creation stack traces:")
                    val ise = IllegalStateException(msg.toString())
                    var i = 0
                    for (t in creationStacks!!.values) {
                        ise.addSuppressed(t)
                        if (i++ > 5) {
                            break
                        }
                    }
                    creationStacks.clear()
                    throw ise
                }
                msg.append(
                    "Please use paranoid leak detection to get more information, or set " +
                            "-D" + TRACK_CREATION_STACK_PROPERTY + "=true"
                )
                throw IllegalStateException(msg.toString())
            }
        }

        /**
         * Check whether there are any open resources left, and [close] would throw.
         *
         * @return `true` if there are open resources
         */
        fun hasOpenResources(): Boolean = openResourceCounter.sum() > 0

        /**
         * Close this scope. Closing a scope will prevent new resources from being allocated (or released) in this
         * scope. The call also throws an exception if there are any resources left open.
         */
        override fun close() {
            if (--closed == 0) {
                check()
            }
        }

        fun retain() {
            if (closed == 0) {
                throw IllegalStateException("Scope already closed")
            }
            closed++
        }
    }

    private class LeakCreation : Throwable() {
        val thread: Thread = Thread.currentThread()
        private var _message: String? = null

        @get:Synchronized
        override val message: String?
            get() {
                if (_message == null) {
                    _message = if (inStaticInitializerSlow(stackTrace)) {
                        "Resource created in static initializer. Please wrap the static initializer in " +
                                "LeakPresenceDetector.staticInitializer so that this resource is excluded."
                    } else {
                        "Resource created outside static initializer on thread '${thread.name}' (" +
                                "${thread.state}), likely leak."
                    }
                }
                return _message
            }
    }

    /**
     * Special exception type to show that an allocation is prohibited at the moment, for example because the
     * [ResourceScope] is closed, or because the current thread cannot be associated with a particular scope.
     *
     * Some code in Netty will treat this exception specially to avoid allocation loops.
     */
    class AllocationProhibitedException(s: String) : IllegalStateException(s)

    companion object {
        private const val TRACK_CREATION_STACK_PROPERTY = "io.netty.util.LeakPresenceDetector.trackCreationStack"

        @JvmStatic
        private val TRACK_CREATION_STACK: Boolean =
            SystemPropertyUtil.getBoolean(TRACK_CREATION_STACK_PROPERTY, false)

        @JvmStatic
        private val GLOBAL: ResourceScope = ResourceScope("global")

        @JvmStatic
        private var staticInitializerCount: Int = 0

        @JvmStatic
        private fun inStaticInitializerSlow(stackTrace: Array<StackTraceElement>): Boolean {
            for (element in stackTrace) {
                if (element.methodName == "<clinit>") {
                    return true
                }
            }
            return false
        }

        @JvmStatic
        private fun inStaticInitializerFast(): Boolean {
            // This plain field access is safe. The worst that can happen is that we see non-zero where we shouldn't.
            return staticInitializerCount != 0 && inStaticInitializerSlow(Thread.currentThread().stackTrace)
        }

        /**
         * Wrap a static initializer so that any resources created inside the block will not be tracked.
         *
         * Note that technically, this method does not actually care what happens inside the block. Instead, it turns on
         * stack trace introspection at the start of the block, and turns it back off at the end. Any allocation in that
         * interval will be checked to see whether it is part of a static initializer, and if it is, it will not be
         * tracked.
         *
         * @param supplier A code block to run
         * @return The value returned by the `supplier`
         * @param R The supplier return type
         */
        @JvmStatic
        fun <R> staticInitializer(supplier: Supplier<R>): R {
            if (!inStaticInitializerSlow(Thread.currentThread().stackTrace)) {
                throw IllegalStateException("Not in static initializer.")
            }
            synchronized(LeakPresenceDetector::class.java) {
                staticInitializerCount++
            }
            try {
                return supplier.get()
            } finally {
                synchronized(LeakPresenceDetector::class.java) {
                    staticInitializerCount--
                }
            }
        }

        /**
         * Check the current leak presence detector scope for open resources. If any resources remain unclosed, an
         * exception is thrown.
         *
         * @throws IllegalStateException If there is a leak, or if the leak detector is not a [LeakPresenceDetector].
         */
        @JvmStatic
        fun check() {
            // for LeakPresenceDetector, this is cheap.
            val detector = ResourceLeakDetectorFactory.instance()
                .newResourceLeakDetector(Any::class.java)

            if (detector !is LeakPresenceDetector<*>) {
                throw IllegalStateException(
                    "LeakPresenceDetector not in use. Please register it using " +
                            "-Dio.netty.customResourceLeakDetector=" + LeakPresenceDetector::class.java.name
                )
            }

            @Suppress("UNCHECKED_CAST")
            (detector as LeakPresenceDetector<Any>).currentScope().check()
        }
    }
}
