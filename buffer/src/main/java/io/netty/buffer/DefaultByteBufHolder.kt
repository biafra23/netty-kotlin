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

import io.netty.util.internal.ObjectUtil
import io.netty.util.internal.StringUtil

/**
 * Default implementation of a [ByteBufHolder] that holds its data in a [ByteBuf].
 */
open class DefaultByteBufHolder(data: ByteBuf) : ByteBufHolder {

    private val data: ByteBuf = ObjectUtil.checkNotNull(data, "data")

    override fun content(): ByteBuf = ByteBufUtil.ensureAccessible(data)

    /**
     * This method calls `replace(content().copy())` by default.
     */
    override fun copy(): ByteBufHolder = replace(data.copy())

    /**
     * This method calls `replace(content().duplicate())` by default.
     */
    override fun duplicate(): ByteBufHolder = replace(data.duplicate())

    /**
     * This method calls `replace(content().retainedDuplicate())` by default.
     */
    override fun retainedDuplicate(): ByteBufHolder = replace(data.retainedDuplicate())

    /**
     * Override this method to return a new instance of this object whose content is set to the specified
     * [content]. The default implementation of [copy], [duplicate] and
     * [retainedDuplicate] invokes this method to create a copy.
     */
    override fun replace(content: ByteBuf): ByteBufHolder = DefaultByteBufHolder(content)

    override fun refCnt(): Int = data.refCnt()

    override fun retain(): ByteBufHolder {
        data.retain()
        return this
    }

    override fun retain(increment: Int): ByteBufHolder {
        data.retain(increment)
        return this
    }

    override fun touch(): ByteBufHolder {
        data.touch()
        return this
    }

    override fun touch(hint: Any?): ByteBufHolder {
        data.touch(hint)
        return this
    }

    override fun release(): Boolean = data.release()

    override fun release(decrement: Int): Boolean = data.release(decrement)

    /**
     * Return [ByteBuf.toString] without checking the reference count first. This is useful to implement
     * [toString].
     */
    protected fun contentToString(): String = data.toString()

    override fun toString(): String = "${StringUtil.simpleClassName(this)}(${contentToString()})"

    /**
     * This implementation of the `equals` operation is restricted to
     * work only with instances of the same class. The reason for that is that
     * Netty library already has a number of classes that extend [DefaultByteBufHolder] and
     * override `equals` method with an additional comparison logic and we
     * need the symmetric property of the `equals` operation to be preserved.
     *
     * @param other the reference object with which to compare.
     * @return `true` if this object is the same as the obj argument; `false` otherwise.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other != null && javaClass == other.javaClass) {
            return data == (other as DefaultByteBufHolder).data
        }
        return false
    }

    override fun hashCode(): Int = data.hashCode()
}
