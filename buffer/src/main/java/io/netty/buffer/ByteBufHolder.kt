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

import io.netty.util.ReferenceCounted

/**
 * A packet which is send or receive.
 */
interface ByteBufHolder : ReferenceCounted {

    /**
     * Return the data which is held by this [ByteBufHolder].
     */
    fun content(): ByteBuf

    /**
     * Creates a deep copy of this [ByteBufHolder].
     */
    fun copy(): ByteBufHolder

    /**
     * Duplicates this [ByteBufHolder]. Be aware that this will not automatically call [retain].
     */
    fun duplicate(): ByteBufHolder

    /**
     * Duplicates this [ByteBufHolder]. This method returns a retained duplicate unlike [duplicate].
     *
     * @see ByteBuf.retainedDuplicate
     */
    fun retainedDuplicate(): ByteBufHolder

    /**
     * Returns a new [ByteBufHolder] which contains the specified [content].
     */
    fun replace(content: ByteBuf): ByteBufHolder

    override fun retain(): ByteBufHolder

    override fun retain(increment: Int): ByteBufHolder

    override fun touch(): ByteBufHolder

    override fun touch(hint: Any?): ByteBufHolder
}
