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

import java.util.concurrent.atomic.AtomicLong

/**
 * Base implementation of [Constant].
 */
abstract class AbstractConstant<T : AbstractConstant<T>> protected constructor(
    private val id: Int,
    private val name: String
) : Constant<T> {

    private val uniquifier: Long = uniqueIdGenerator.getAndIncrement()

    override fun name(): String = name

    override fun id(): Int = id

    override fun toString(): String = name()

    override fun hashCode(): Int = super.hashCode()

    override fun equals(other: Any?): Boolean = super.equals(other)

    override fun compareTo(other: T): Int {
        if (this === other) {
            return 0
        }

        val otherConstant = other as AbstractConstant<T>
        val returnCode = hashCode() - otherConstant.hashCode()
        if (returnCode != 0) {
            return returnCode
        }

        if (uniquifier < otherConstant.uniquifier) {
            return -1
        }
        if (uniquifier > otherConstant.uniquifier) {
            return 1
        }

        throw Error("failed to compare two different constants")
    }

    companion object {
        private val uniqueIdGenerator = AtomicLong()
    }
}
