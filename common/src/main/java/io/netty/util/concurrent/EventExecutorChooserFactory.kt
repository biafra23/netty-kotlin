/*
 * Copyright 2016 The Netty Project
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

import io.netty.util.concurrent.AutoScalingEventExecutorChooserFactory.AutoScalingUtilizationMetric

/**
 * Factory that creates new [EventExecutorChooser][EventExecutorChooserFactory.EventExecutorChooser]s.
 */
interface EventExecutorChooserFactory {

    /**
     * Returns a new [EventExecutorChooser].
     */
    fun newChooser(executors: Array<EventExecutor>): EventExecutorChooser

    /**
     * Chooses the next [EventExecutor] to use.
     */
    interface EventExecutorChooser {

        /**
         * Returns the new [EventExecutor] to use.
         */
        fun next(): EventExecutor
    }

    /**
     * An [EventExecutorChooser] that exposes metrics for observation.
     */
    interface ObservableEventExecutorChooser : EventExecutorChooser {

        /**
         * Returns the current number of active [EventExecutor]s.
         * @return the number of active executors.
         */
        fun activeExecutorCount(): Int

        /**
         * Returns a list containing the last calculated utilization for each
         * [EventExecutor] in the group.
         *
         * @return an unmodifiable view of the executor utilizations.
         */
        fun executorUtilizations(): List<AutoScalingUtilizationMetric>
    }
}
