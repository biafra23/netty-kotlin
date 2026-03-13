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

import io.netty.util.internal.ObjectUtil
import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * A factory that creates auto-scaling [EventExecutorChooserFactory.EventExecutorChooser] instances.
 * This chooser implements a dynamic, utilization-based auto-scaling strategy.
 */
class AutoScalingEventExecutorChooserFactory(
    minThreads: Int,
    maxThreads: Int,
    utilizationWindow: Long,
    windowUnit: TimeUnit,
    scaleDownThreshold: Double,
    scaleUpThreshold: Double,
    maxRampUpStep: Int,
    maxRampDownStep: Int,
    scalingPatienceCycles: Int
) : EventExecutorChooserFactory {

    /**
     * A container for the utilization metric of a single EventExecutor.
     */
    class AutoScalingUtilizationMetric internal constructor(
        private val executor: EventExecutor
    ) {
        private val utilizationBits = AtomicLong()

        /**
         * Returns the most recently calculated utilization for the associated executor.
         * @return a value from 0.0 to 1.0.
         */
        fun utilization(): Double {
            return Double.fromBits(utilizationBits.get())
        }

        /**
         * Returns the [EventExecutor] this metric belongs too.
         * @return the executor.
         */
        fun executor(): EventExecutor {
            return executor
        }

        internal fun setUtilization(utilization: Double) {
            val bits = utilization.toRawBits()
            utilizationBits.lazySet(bits)
        }
    }

    private val minChildren: Int
    private val maxChildren: Int
    private val utilizationCheckPeriodNanos: Long
    private val scaleDownThreshold: Double
    private val scaleUpThreshold: Double
    private val maxRampUpStep: Int
    private val maxRampDownStep: Int
    private val scalingPatienceCycles: Int

    init {
        this.minChildren = ObjectUtil.checkPositiveOrZero(minThreads, "minThreads")
        this.maxChildren = ObjectUtil.checkPositive(maxThreads, "maxThreads")
        if (minThreads > maxThreads) {
            throw IllegalArgumentException(
                String.format("minThreads: %d must not be greater than maxThreads: %d", minThreads, maxThreads)
            )
        }
        this.utilizationCheckPeriodNanos = ObjectUtil.checkNotNull(windowUnit, "windowUnit")
            .toNanos(ObjectUtil.checkPositive(utilizationWindow, "utilizationWindow"))
        this.scaleDownThreshold = ObjectUtil.checkInRange(scaleDownThreshold, 0.0, 1.0, "scaleDownThreshold")
        this.scaleUpThreshold = ObjectUtil.checkInRange(scaleUpThreshold, 0.0, 1.0, "scaleUpThreshold")
        if (scaleDownThreshold >= scaleUpThreshold) {
            throw IllegalArgumentException(
                "scaleDownThreshold must be less than scaleUpThreshold: $scaleDownThreshold >= $scaleUpThreshold"
            )
        }
        this.maxRampUpStep = ObjectUtil.checkPositive(maxRampUpStep, "maxRampUpStep")
        this.maxRampDownStep = ObjectUtil.checkPositive(maxRampDownStep, "maxRampDownStep")
        this.scalingPatienceCycles = ObjectUtil.checkPositiveOrZero(scalingPatienceCycles, "scalingPatienceCycles")
    }

    override fun newChooser(executors: Array<EventExecutor>): EventExecutorChooserFactory.EventExecutorChooser {
        return AutoScalingEventExecutorChooser(executors)
    }

    /**
     * An immutable snapshot of the chooser's state. All state transitions
     * are managed by atomically swapping this object.
     */
    private class AutoScalingState(
        val activeChildrenCount: Int,
        val nextWakeUpIndex: Long,
        val activeExecutors: Array<EventExecutor>
    ) {
        val activeExecutorsChooser: EventExecutorChooserFactory.EventExecutorChooser =
            DefaultEventExecutorChooserFactory.INSTANCE.newChooser(activeExecutors)
    }

    private inner class AutoScalingEventExecutorChooser(
        private val executors: Array<EventExecutor>
    ) : EventExecutorChooserFactory.ObservableEventExecutorChooser {
        private val allExecutorsChooser: EventExecutorChooserFactory.EventExecutorChooser =
            DefaultEventExecutorChooserFactory.INSTANCE.newChooser(executors)
        private val state: AtomicReference<AutoScalingState>
        private val utilizationMetrics: List<AutoScalingUtilizationMetric>

        init {
            val metrics = ArrayList<AutoScalingUtilizationMetric>(executors.size)
            for (executor in executors) {
                metrics.add(AutoScalingUtilizationMetric(executor))
            }
            utilizationMetrics = Collections.unmodifiableList(metrics)

            val initialState = AutoScalingState(maxChildren, 0L, executors)
            state = AtomicReference(initialState)

            val utilizationMonitoringTask = GlobalEventExecutor.INSTANCE.scheduleAtFixedRate(
                UtilizationMonitor(), utilizationCheckPeriodNanos, utilizationCheckPeriodNanos,
                TimeUnit.NANOSECONDS
            )

            if (executors.isNotEmpty()) {
                executors[0].terminationFuture().addListener(object : GenericFutureListener<Future<Any?>> {
                    override fun operationComplete(future: Future<Any?>) {
                        utilizationMonitoringTask.cancel(false)
                    }
                })
            }
        }

        /**
         * This method is only responsible for picking from the active executors list.
         * The monitor handles all scaling decisions.
         */
        override fun next(): EventExecutor {
            // Get a snapshot of the current state.
            val currentState = this.state.get()

            if (currentState.activeExecutors.isEmpty()) {
                // This is only reachable if minChildren is 0 and the monitor has just suspended the last active thread.
                tryScaleUpBy(1)
                return allExecutorsChooser.next()
            }
            return currentState.activeExecutorsChooser.next()
        }

        /**
         * Tries to increase the active thread count by waking up suspended executors.
         */
        private fun tryScaleUpBy(amount: Int) {
            if (amount <= 0) {
                return
            }

            while (true) {
                val oldState = state.get()
                if (oldState.activeChildrenCount >= maxChildren) {
                    return
                }

                val canAdd = Math.min(amount, maxChildren - oldState.activeChildrenCount)
                val wokenUp = ArrayList<EventExecutor>(canAdd)
                val startIndex = oldState.nextWakeUpIndex

                for (i in executors.indices) {
                    val child = executors[Math.abs((startIndex + i) % executors.size).toInt()]

                    if (wokenUp.size >= canAdd) {
                        break // We have woken up all the threads we reserved.
                    }
                    if (child is SingleThreadEventExecutor) {
                        if (child.isSuspended()) {
                            child.execute(NO_OOP_TASK)
                            wokenUp.add(child)
                        }
                    }
                }

                if (wokenUp.isEmpty()) {
                    return
                }

                // Create the new state.
                val newActiveList = ArrayList<EventExecutor>(oldState.activeExecutors.size + wokenUp.size)
                Collections.addAll(newActiveList, *oldState.activeExecutors)
                newActiveList.addAll(wokenUp)

                val newState = AutoScalingState(
                    oldState.activeChildrenCount + wokenUp.size,
                    startIndex + wokenUp.size,
                    newActiveList.toTypedArray()
                )

                if (state.compareAndSet(oldState, newState)) {
                    return
                }
                // CAS failed, another thread changed the state. Loop again to retry.
            }
        }

        override fun activeExecutorCount(): Int {
            return state.get().activeChildrenCount
        }

        override fun executorUtilizations(): List<AutoScalingUtilizationMetric> {
            return utilizationMetrics
        }

        private inner class UtilizationMonitor : Runnable {
            private val consistentlyIdleChildren = ArrayList<SingleThreadEventExecutor>(maxChildren)
            private var lastCheckTimeNanos: Long = 0

            override fun run() {
                if (executors.isEmpty() || executors[0].isShuttingDown()) {
                    return
                }

                // Calculate the actual elapsed time since the last run.
                val now = executors[0].ticker().nanoTime()
                val totalTime: Long

                if (lastCheckTimeNanos == 0L) {
                    totalTime = utilizationCheckPeriodNanos
                } else {
                    totalTime = now - lastCheckTimeNanos
                }

                // Always update the timestamp for the next cycle.
                lastCheckTimeNanos = now

                if (totalTime <= 0) {
                    return
                }

                var consistentlyBusyChildren = 0
                consistentlyIdleChildren.clear()

                val currentState = state.get()

                for (i in executors.indices) {
                    val child = executors[i]
                    if (child !is SingleThreadEventExecutor) {
                        continue
                    }

                    var utilization = 0.0
                    if (!child.isSuspended()) {
                        var activeTime = child.getAndResetAccumulatedActiveTimeNanos()

                        if (activeTime == 0L) {
                            val lastActivity = child.getLastActivityTimeNanos()
                            val idleTime = now - lastActivity

                            if (idleTime < totalTime) {
                                activeTime = totalTime - idleTime
                            }
                        }

                        utilization = Math.min(1.0, activeTime.toDouble() / totalTime)

                        if (utilization < scaleDownThreshold) {
                            val idleCycles = child.getAndIncrementIdleCycles()
                            child.resetBusyCycles()
                            if (idleCycles >= scalingPatienceCycles &&
                                child.numOfRegisteredChannelsInternal() <= 0
                            ) {
                                consistentlyIdleChildren.add(child)
                            }
                        } else if (utilization > scaleUpThreshold) {
                            val busyCycles = child.getAndIncrementBusyCycles()
                            child.resetIdleCycles()
                            if (busyCycles >= scalingPatienceCycles) {
                                consistentlyBusyChildren++
                            }
                        } else {
                            child.resetIdleCycles()
                            child.resetBusyCycles()
                        }
                    }

                    utilizationMetrics[i].setUtilization(utilization)
                }

                val currentActive = currentState.activeChildrenCount

                // Make scaling decisions based on stable states.
                if (consistentlyBusyChildren > 0 && currentActive < maxChildren) {
                    var threadsToAdd = Math.min(consistentlyBusyChildren, maxRampUpStep)
                    threadsToAdd = Math.min(threadsToAdd, maxChildren - currentActive)
                    if (threadsToAdd > 0) {
                        tryScaleUpBy(threadsToAdd)
                        return // Exit to avoid conflicting scale down logic in the same cycle.
                    }
                }

                var changed = false
                if (consistentlyIdleChildren.isNotEmpty() && currentActive > minChildren) {
                    var threadsToRemove = Math.min(consistentlyIdleChildren.size, maxRampDownStep)
                    threadsToRemove = Math.min(threadsToRemove, currentActive - minChildren)

                    for (i in 0 until threadsToRemove) {
                        val childToSuspend = consistentlyIdleChildren[i]
                        if (childToSuspend.trySuspend()) {
                            childToSuspend.resetBusyCycles()
                            childToSuspend.resetIdleCycles()
                            changed = true
                        }
                    }
                }

                if (changed || currentActive != currentState.activeExecutors.size) {
                    rebuildActiveExecutors()
                }
            }

            /**
             * Atomically updates the state by creating a new snapshot with the current set of active executors.
             */
            private fun rebuildActiveExecutors() {
                while (true) {
                    val oldState = state.get()
                    val active = ArrayList<EventExecutor>(oldState.activeChildrenCount)
                    for (executor in executors) {
                        if (!executor.isSuspended()) {
                            active.add(executor)
                        }
                    }
                    val newActiveExecutors = active.toTypedArray()

                    val newState = AutoScalingState(
                        newActiveExecutors.size, oldState.nextWakeUpIndex, newActiveExecutors
                    )

                    if (state.compareAndSet(oldState, newState)) {
                        break
                    }
                }
            }
        }
    }

    companion object {
        private val NO_OOP_TASK = Runnable { }
    }
}
