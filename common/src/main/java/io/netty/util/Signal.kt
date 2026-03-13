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

/**
 * A special [Error] which is used to signal some state or request by throwing it.
 * [Signal] has an empty stack trace and has no cause to save the instantiation overhead.
 */
class Signal private constructor(id: Int, name: String) : Error(), Constant<Signal> {

    private val constant: SignalConstant = SignalConstant(id, name)

    /**
     * Check if the given [Signal] is the same as this instance. If not an [IllegalStateException] will
     * be thrown.
     */
    fun expect(signal: Signal) {
        if (this !== signal) {
            throw IllegalStateException("unexpected signal: $signal")
        }
    }

    // Suppress a warning since the method doesn't need synchronization
    override fun initCause(cause: Throwable?): Throwable = this

    // Suppress a warning since the method doesn't need synchronization
    override fun fillInStackTrace(): Throwable = this

    override fun id(): Int = constant.id()

    override fun name(): String = constant.name()

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)

    override fun compareTo(other: Signal): Int {
        if (this === other) {
            return 0
        }
        return constant.compareTo(other.constant)
    }

    override fun toString(): String = name()

    private class SignalConstant(id: Int, name: String) : AbstractConstant<SignalConstant>(id, name)

    companion object {
        private const val serialVersionUID = -221145131122459977L

        private val pool: ConstantPool<Signal> = object : ConstantPool<Signal>() {
            override fun newConstant(id: Int, name: String): Signal = Signal(id, name)
        }

        /**
         * Returns the [Signal] of the specified name.
         */
        @JvmStatic
        fun valueOf(name: String): Signal = pool.valueOf(name)

        /**
         * Shortcut of [valueOf(firstNameComponent.getName() + "#" + secondNameComponent)][.valueOf].
         */
        @JvmStatic
        fun valueOf(firstNameComponent: Class<*>, secondNameComponent: String): Signal =
            pool.valueOf(firstNameComponent, secondNameComponent)
    }
}
