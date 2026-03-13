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
package io.netty.buffer

/**
 * A derived buffer which exposes its parent's sub-region only.  It is
 * recommended to use [ByteBuf.slice] and
 * [ByteBuf.slice] instead of calling the constructor
 * explicitly.
 *
 * @deprecated Do not use.
 */
@Deprecated("Do not use.")
open class SlicedByteBuf(buffer: ByteBuf, index: Int, length: Int) :
    AbstractUnpooledSlicedByteBuf(buffer, index, length) {

    private var length: Int = 0

    override fun initLength(length: Int) {
        this.length = length
    }

    override fun length(): Int = length

    override fun capacity(): Int = length
}
