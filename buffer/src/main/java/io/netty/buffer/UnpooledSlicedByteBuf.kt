/*
 * Copyright 2015 The Netty Project
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

/**
 * A special [AbstractUnpooledSlicedByteBuf] that can make optimizations because it knows the sliced buffer is of
 * type [AbstractByteBuf].
 */
internal open class UnpooledSlicedByteBuf(
    buffer: AbstractByteBuf, index: Int, length: Int
) : AbstractUnpooledSlicedByteBuf(buffer, index, length) {

    override fun capacity(): Int = maxCapacity()

    override fun unwrap(): AbstractByteBuf = super.unwrap() as AbstractByteBuf

    override fun _getByte(index: Int): Byte = unwrap().invoke_getByte(idx(index))

    override fun _getShort(index: Int): Short = unwrap().invoke_getShort(idx(index))

    override fun _getShortLE(index: Int): Short = unwrap().invoke_getShortLE(idx(index))

    override fun _getUnsignedMedium(index: Int): Int = unwrap().invoke_getUnsignedMedium(idx(index))

    override fun _getUnsignedMediumLE(index: Int): Int = unwrap().invoke_getUnsignedMediumLE(idx(index))

    override fun _getInt(index: Int): Int = unwrap().invoke_getInt(idx(index))

    override fun _getIntLE(index: Int): Int = unwrap().invoke_getIntLE(idx(index))

    override fun _getLong(index: Int): Long = unwrap().invoke_getLong(idx(index))

    override fun _getLongLE(index: Int): Long = unwrap().invoke_getLongLE(idx(index))

    override fun _setByte(index: Int, value: Int) {
        unwrap().invoke_setByte(idx(index), value)
    }

    override fun _setShort(index: Int, value: Int) {
        unwrap().invoke_setShort(idx(index), value)
    }

    override fun _setShortLE(index: Int, value: Int) {
        unwrap().invoke_setShortLE(idx(index), value)
    }

    override fun _setMedium(index: Int, value: Int) {
        unwrap().invoke_setMedium(idx(index), value)
    }

    override fun _setMediumLE(index: Int, value: Int) {
        unwrap().invoke_setMediumLE(idx(index), value)
    }

    override fun _setInt(index: Int, value: Int) {
        unwrap().invoke_setInt(idx(index), value)
    }

    override fun _setIntLE(index: Int, value: Int) {
        unwrap().invoke_setIntLE(idx(index), value)
    }

    override fun _setLong(index: Int, value: Long) {
        unwrap().invoke_setLong(idx(index), value)
    }

    override fun _setLongLE(index: Int, value: Long) {
        unwrap().invoke_setLongLE(idx(index), value)
    }
}
