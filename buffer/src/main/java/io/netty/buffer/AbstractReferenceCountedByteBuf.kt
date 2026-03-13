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

import io.netty.util.internal.RefCnt

/**
 * Abstract base class for [ByteBuf] implementations that count references.
 */
abstract class AbstractReferenceCountedByteBuf protected constructor(maxCapacity: Int) : AbstractByteBuf(maxCapacity) {

    // this is setting the ref cnt to the initial value
    private val refCnt = RefCnt()

    override fun isAccessible(): Boolean {
        // Try to do non-volatile read for performance as the ensureAccessible() is racy anyway and only provide
        // a best-effort guard.
        return RefCnt.isLiveNonVolatile(refCnt)
    }

    override fun refCnt(): Int = RefCnt.refCnt(refCnt)

    /**
     * An unsafe operation intended for use by a subclass that sets the reference count of the buffer directly.
     */
    protected fun setRefCnt(count: Int) {
        RefCnt.setRefCnt(refCnt, count)
    }

    /**
     * An unsafe operation intended for use by a subclass that resets the reference count of the buffer to 1.
     */
    protected fun resetRefCnt() {
        RefCnt.resetRefCnt(refCnt)
    }

    override fun retain(): ByteBuf {
        RefCnt.retain(refCnt)
        return this
    }

    override fun retain(increment: Int): ByteBuf {
        RefCnt.retain(refCnt, increment)
        return this
    }

    override fun touch(): ByteBuf = this

    override fun touch(hint: Any?): ByteBuf = this

    override fun release(): Boolean = handleRelease(RefCnt.release(refCnt))

    override fun release(decrement: Int): Boolean = handleRelease(RefCnt.release(refCnt, decrement))

    private fun handleRelease(result: Boolean): Boolean {
        if (result) {
            deallocate()
        }
        return result
    }

    /**
     * Called once [refCnt] equals 0.
     */
    protected abstract fun deallocate()
}
