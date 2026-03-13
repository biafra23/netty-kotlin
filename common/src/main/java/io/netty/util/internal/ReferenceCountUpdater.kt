/*
 * Copyright 2019 The Netty Project
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
package io.netty.util.internal

import io.netty.util.IllegalReferenceCountException
import io.netty.util.ReferenceCounted
import io.netty.util.internal.ObjectUtil.checkPositive

/**
 * Common logic for [ReferenceCounted] implementations
 * @deprecated Instead of extending this class, prefer instead to include a [RefCnt] field and delegate to that.
 * This approach has better compatibility with Graal Native Image.
 */
@Deprecated("Use RefCnt field delegation instead for better Graal Native Image compatibility")
abstract class ReferenceCountUpdater<T : ReferenceCounted> protected constructor() {
    /*
     * Implementation notes:
     *
     * For the updated int field:
     *   Even => "real" refcount is (refCnt >>> 1)
     *   Odd  => "real" refcount is 0
     */

    protected abstract fun safeInitializeRawRefCnt(refCntObj: T, value: Int)

    protected abstract fun getAndAddRawRefCnt(refCntObj: T, increment: Int): Int

    protected abstract fun getRawRefCnt(refCnt: T): Int

    protected abstract fun getAcquireRawRefCnt(refCnt: T): Int

    protected abstract fun setReleaseRawRefCnt(refCnt: T, value: Int)

    protected abstract fun casRawRefCnt(refCnt: T, expected: Int, value: Int): Boolean

    fun initialValue(): Int = 2

    fun setInitialValue(instance: T) {
        safeInitializeRawRefCnt(instance, initialValue())
    }

    fun refCnt(instance: T): Int = realRefCnt(getAcquireRawRefCnt(instance))

    fun isLiveNonVolatile(instance: T): Boolean {
        val rawCnt = getRawRefCnt(instance)
        if (rawCnt == 2) {
            return true
        }
        return (rawCnt and 1) == 0
    }

    /**
     * An unsafe operation that sets the reference count directly
     */
    fun setRefCnt(instance: T, refCnt: Int) {
        val rawRefCnt = if (refCnt > 0) refCnt shl 1 else 1 // overflow OK here
        setReleaseRawRefCnt(instance, rawRefCnt)
    }

    /**
     * Resets the reference count to 1
     */
    fun resetRefCnt(instance: T) {
        // no need of a volatile set, it should happen in a quiescent state
        setReleaseRawRefCnt(instance, initialValue())
    }

    fun retain(instance: T): T = retain0(instance, 2)

    fun retain(instance: T, increment: Int): T = retain0(instance, checkPositive(increment, "increment") shl 1)

    private fun retain0(instance: T, increment: Int): T {
        val oldRef = getAndAddRawRefCnt(instance, increment)
        // oldRef & 0x80000001 stands for oldRef < 0 || oldRef is odd
        // NOTE: we're optimizing for inlined and constant folded increment here -> which will make
        // Integer.MAX_VALUE - increment to be computed at compile time
        if ((oldRef and -0x7fffffff) != 0 || oldRef > Int.MAX_VALUE - increment) {
            getAndAddRawRefCnt(instance, -increment)
            throw IllegalReferenceCountException(0, increment ushr 1)
        }
        return instance
    }

    fun release(instance: T): Boolean = release0(instance, 2)

    fun release(instance: T, decrement: Int): Boolean = release0(instance, checkPositive(decrement, "decrement") shl 1)

    private fun release0(instance: T, decrement: Int): Boolean {
        var curr: Int
        var next: Int
        do {
            curr = getRawRefCnt(instance)
            if (curr == decrement) {
                next = 1
            } else {
                if (curr < decrement || (curr and 1) == 1) {
                    throwIllegalRefCountOnRelease(decrement, curr)
                }
                next = curr - decrement
            }
        } while (!casRawRefCnt(instance, curr, next))
        return (next and 1) == 1
    }

    enum class UpdaterType {
        Unsafe,
        VarHandle,
        Atomic
    }

    companion object {
        private fun realRefCnt(rawCnt: Int): Int = rawCnt ushr 1

        private fun throwIllegalRefCountOnRelease(decrement: Int, curr: Int) {
            throw IllegalReferenceCountException(curr ushr 1, -(decrement ushr 1))
        }

        @JvmStatic
        fun <T : ReferenceCounted> updaterTypeOf(clz: Class<T>, fieldName: String): UpdaterType {
            val fieldOffset = getUnsafeOffset(clz, fieldName)
            if (fieldOffset >= 0) {
                return UpdaterType.Unsafe
            }
            if (PlatformDependent.hasVarHandle()) {
                return UpdaterType.VarHandle
            }
            return UpdaterType.Atomic
        }

        @JvmStatic
        fun getUnsafeOffset(clz: Class<out ReferenceCounted>, fieldName: String): Long {
            try {
                if (PlatformDependent.hasUnsafe()) {
                    return PlatformDependent.objectFieldOffset(clz.getDeclaredField(fieldName))
                }
            } catch (ignore: Throwable) {
                // fall-back
            }
            return -1
        }
    }
}
