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
package io.netty.buffer

import java.nio.ByteBuffer

/**
 * Abstract base class for [ByteBuf] implementations that wrap another
 * [ByteBuf].
 *
 * @deprecated Do not use.
 */
@Deprecated("Do not use.")
abstract class AbstractDerivedByteBuf protected constructor(maxCapacity: Int) : AbstractByteBuf(maxCapacity) {

    final override fun isAccessible(): Boolean = isAccessible0()

    open fun isAccessible0(): Boolean = unwrap()!!.isAccessible()

    final override fun refCnt(): Int = refCnt0()

    internal open fun refCnt0(): Int = unwrap()!!.refCnt()

    final override fun retain(): ByteBuf = retain0()

    internal open fun retain0(): ByteBuf {
        unwrap()!!.retain()
        return this
    }

    final override fun retain(increment: Int): ByteBuf = retain0(increment)

    internal open fun retain0(increment: Int): ByteBuf {
        unwrap()!!.retain(increment)
        return this
    }

    final override fun touch(): ByteBuf = touch0()

    internal open fun touch0(): ByteBuf {
        unwrap()!!.touch()
        return this
    }

    final override fun touch(hint: Any?): ByteBuf = touch0(hint)

    internal open fun touch0(hint: Any?): ByteBuf {
        unwrap()!!.touch(hint)
        return this
    }

    final override fun release(): Boolean = release0()

    internal open fun release0(): Boolean = unwrap()!!.release()

    final override fun release(decrement: Int): Boolean = release0(decrement)

    internal open fun release0(decrement: Int): Boolean = unwrap()!!.release(decrement)

    override fun isReadOnly(): Boolean = unwrap()!!.isReadOnly()

    override fun internalNioBuffer(index: Int, length: Int): ByteBuffer = nioBuffer(index, length)

    override fun nioBuffer(index: Int, length: Int): ByteBuffer = unwrap()!!.nioBuffer(index, length)

    override fun isContiguous(): Boolean = unwrap()!!.isContiguous()
}
