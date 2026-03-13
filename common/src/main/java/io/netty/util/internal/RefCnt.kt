/*
 * Copyright 2025 The Netty Project
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
import io.netty.util.internal.ObjectUtil.checkPositive
import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater

/**
 * Monomorphic reference counter implementation that always uses the most efficient available atomic updater.
 * This implementation is easier for the JIT compiler to optimize,
 * compared to when [ReferenceCountUpdater] is used.
 */
@Suppress("deprecation")
class RefCnt {

    /*
     * Implementation notes:
     *
     * For the updated int field:
     *   Even => "real" refcount is (refCnt >>> 1)
     *   Odd  => "real" refcount is 0
     *
     * This field is package-private so that the AtomicRefCnt implementation can reach it, even on native-image.
     */
    @Volatile
    @JvmField
    internal var value: Int = 0

    init {
        when (REF_CNT_IMPL) {
            UNSAFE -> UnsafeRefCnt.init(this)
            VAR_HANDLE -> VarHandleRefCnt.init(this)
            else -> AtomicRefCnt.init(this)
        }
    }

    companion object {
        private const val UNSAFE = 0
        private const val VAR_HANDLE = 1
        private const val ATOMIC_UPDATER = 2
        private val REF_CNT_IMPL: Int

        init {
            REF_CNT_IMPL = when {
                PlatformDependent.hasUnsafe() -> UNSAFE
                PlatformDependent.hasVarHandle() -> VAR_HANDLE
                else -> ATOMIC_UPDATER
            }
        }

        /**
         * Returns the current reference count of the given [RefCnt] instance with a load acquire semantic.
         *
         * @param ref the target RefCnt instance
         * @return the reference count
         */
        @JvmStatic
        fun refCnt(ref: RefCnt): Int = when (REF_CNT_IMPL) {
            UNSAFE -> UnsafeRefCnt.refCnt(ref)
            VAR_HANDLE -> VarHandleRefCnt.refCnt(ref)
            else -> AtomicRefCnt.refCnt(ref)
        }

        /**
         * Increases the reference count of the given [RefCnt] instance by 1.
         *
         * @param ref the target RefCnt instance
         */
        @JvmStatic
        fun retain(ref: RefCnt) {
            when (REF_CNT_IMPL) {
                UNSAFE -> UnsafeRefCnt.retain(ref)
                VAR_HANDLE -> VarHandleRefCnt.retain(ref)
                else -> AtomicRefCnt.retain(ref)
            }
        }

        /**
         * Increases the reference count of the given [RefCnt] instance by the specified increment.
         *
         * @param ref       the target RefCnt instance
         * @param increment the amount to increase the reference count by
         * @throws IllegalArgumentException if increment is not positive
         */
        @JvmStatic
        fun retain(ref: RefCnt, increment: Int) {
            when (REF_CNT_IMPL) {
                UNSAFE -> UnsafeRefCnt.retain(ref, increment)
                VAR_HANDLE -> VarHandleRefCnt.retain(ref, increment)
                else -> AtomicRefCnt.retain(ref, increment)
            }
        }

        /**
         * Decreases the reference count of the given [RefCnt] instance by 1.
         *
         * @param ref the target RefCnt instance
         * @return true if the reference count became 0 and the object should be deallocated
         */
        @JvmStatic
        fun release(ref: RefCnt): Boolean = when (REF_CNT_IMPL) {
            UNSAFE -> UnsafeRefCnt.release(ref)
            VAR_HANDLE -> VarHandleRefCnt.release(ref)
            else -> AtomicRefCnt.release(ref)
        }

        /**
         * Decreases the reference count of the given [RefCnt] instance by the specified decrement.
         *
         * @param ref       the target RefCnt instance
         * @param decrement the amount to decrease the reference count by
         * @return true if the reference count became 0 and the object should be deallocated
         * @throws IllegalArgumentException if decrement is not positive
         */
        @JvmStatic
        fun release(ref: RefCnt, decrement: Int): Boolean = when (REF_CNT_IMPL) {
            UNSAFE -> UnsafeRefCnt.release(ref, decrement)
            VAR_HANDLE -> VarHandleRefCnt.release(ref, decrement)
            else -> AtomicRefCnt.release(ref, decrement)
        }

        /**
         * Returns `true` if and only if the given reference counter is alive.
         * This method is useful to check if the object is alive without incurring the cost of a volatile read.
         *
         * @param ref the target RefCnt instance
         * @return `true` if alive
         */
        @JvmStatic
        fun isLiveNonVolatile(ref: RefCnt): Boolean = when (REF_CNT_IMPL) {
            UNSAFE -> UnsafeRefCnt.isLiveNonVolatile(ref)
            VAR_HANDLE -> VarHandleRefCnt.isLiveNonVolatile(ref)
            else -> AtomicRefCnt.isLiveNonVolatile(ref)
        }

        /**
         * **WARNING:**
         * An unsafe operation that sets the reference count of the given [RefCnt] instance directly.
         *
         * @param ref    the target RefCnt instance
         * @param refCnt new reference count
         */
        @JvmStatic
        fun setRefCnt(ref: RefCnt, refCnt: Int) {
            when (REF_CNT_IMPL) {
                UNSAFE -> UnsafeRefCnt.setRefCnt(ref, refCnt)
                VAR_HANDLE -> VarHandleRefCnt.setRefCnt(ref, refCnt)
                else -> AtomicRefCnt.setRefCnt(ref, refCnt)
            }
        }

        /**
         * Resets the reference count of the given [RefCnt] instance to 1.
         *
         * **Warning:** This method uses release memory semantics, meaning the change may not be
         * immediately visible to other threads. It should only be used in quiescent states where no other
         * threads are accessing the reference count.
         *
         * @param ref the target RefCnt instance
         */
        @JvmStatic
        fun resetRefCnt(ref: RefCnt) {
            when (REF_CNT_IMPL) {
                UNSAFE -> UnsafeRefCnt.resetRefCnt(ref)
                VAR_HANDLE -> VarHandleRefCnt.resetRefCnt(ref)
                else -> AtomicRefCnt.resetRefCnt(ref)
            }
        }

        @JvmStatic
        internal fun throwIllegalRefCountOnRelease(decrement: Int, curr: Int) {
            throw IllegalReferenceCountException(curr ushr 1, -(decrement ushr 1))
        }
    }

    private object AtomicRefCnt {
        private val UPDATER: AtomicIntegerFieldUpdater<RefCnt> =
            AtomicIntegerFieldUpdater.newUpdater(RefCnt::class.java, "value")

        fun init(instance: RefCnt) {
            UPDATER.set(instance, 2)
        }

        fun refCnt(instance: RefCnt): Int = UPDATER.get(instance) ushr 1

        fun retain(instance: RefCnt) {
            retain0(instance, 2)
        }

        fun retain(instance: RefCnt, increment: Int) {
            retain0(instance, checkPositive(increment, "increment") shl 1)
        }

        private fun retain0(instance: RefCnt, increment: Int) {
            val oldRef = UPDATER.getAndAdd(instance, increment)
            if ((oldRef and -0x7fffffff) != 0 || oldRef > Int.MAX_VALUE - increment) {
                UPDATER.getAndAdd(instance, -increment)
                throw IllegalReferenceCountException(0, increment ushr 1)
            }
        }

        fun release(instance: RefCnt): Boolean = release0(instance, 2)

        fun release(instance: RefCnt, decrement: Int): Boolean =
            release0(instance, checkPositive(decrement, "decrement") shl 1)

        private fun release0(instance: RefCnt, decrement: Int): Boolean {
            var curr: Int
            var next: Int
            do {
                curr = instance.value
                if (curr == decrement) {
                    next = 1
                } else {
                    if (curr < decrement || (curr and 1) == 1) {
                        throwIllegalRefCountOnRelease(decrement, curr)
                    }
                    next = curr - decrement
                }
            } while (!UPDATER.compareAndSet(instance, curr, next))
            return (next and 1) == 1
        }

        fun setRefCnt(instance: RefCnt, refCnt: Int) {
            val rawRefCnt = if (refCnt > 0) refCnt shl 1 else 1
            UPDATER.lazySet(instance, rawRefCnt)
        }

        fun resetRefCnt(instance: RefCnt) {
            UPDATER.lazySet(instance, 2)
        }

        fun isLiveNonVolatile(instance: RefCnt): Boolean {
            val rawCnt = instance.value
            return if (rawCnt == 2) true else (rawCnt and 1) == 0
        }
    }

    private object VarHandleRefCnt {
        private val VH: VarHandle =
            PlatformDependent.findVarHandleOfIntField(MethodHandles.lookup(), RefCnt::class.java, "value")

        fun init(instance: RefCnt) {
            VH.set(instance, 2)
            VarHandle.storeStoreFence()
        }

        fun refCnt(instance: RefCnt): Int = (VH.getAcquire(instance) as Int) ushr 1

        fun retain(instance: RefCnt) {
            retain0(instance, 2)
        }

        fun retain(instance: RefCnt, increment: Int) {
            retain0(instance, checkPositive(increment, "increment") shl 1)
        }

        private fun retain0(instance: RefCnt, increment: Int) {
            val oldRef = VH.getAndAdd(instance, increment) as Int
            if ((oldRef and -0x7fffffff) != 0 || oldRef > Int.MAX_VALUE - increment) {
                VH.getAndAdd(instance, -increment)
                throw IllegalReferenceCountException(0, increment ushr 1)
            }
        }

        fun release(instance: RefCnt): Boolean = release0(instance, 2)

        fun release(instance: RefCnt, decrement: Int): Boolean =
            release0(instance, checkPositive(decrement, "decrement") shl 1)

        private fun release0(instance: RefCnt, decrement: Int): Boolean {
            var curr: Int
            var next: Int
            do {
                curr = VH.get(instance) as Int
                if (curr == decrement) {
                    next = 1
                } else {
                    if (curr < decrement || (curr and 1) == 1) {
                        throwIllegalRefCountOnRelease(decrement, curr)
                    }
                    next = curr - decrement
                }
            } while (!(VH.compareAndSet(instance, curr, next) as Boolean))
            return (next and 1) == 1
        }

        fun setRefCnt(instance: RefCnt, refCnt: Int) {
            val rawRefCnt = if (refCnt > 0) refCnt shl 1 else 1
            VH.setRelease(instance, rawRefCnt)
        }

        fun resetRefCnt(instance: RefCnt) {
            VH.setRelease(instance, 2)
        }

        fun isLiveNonVolatile(instance: RefCnt): Boolean {
            val rawCnt = VH.get(instance) as Int
            return if (rawCnt == 2) true else (rawCnt and 1) == 0
        }
    }

    private object UnsafeRefCnt {
        private val VALUE_OFFSET: Long = getUnsafeOffset(RefCnt::class.java, "value")

        private fun getUnsafeOffset(clz: Class<*>, fieldName: String): Long {
            try {
                if (PlatformDependent.hasUnsafe()) {
                    return PlatformDependent.objectFieldOffset(clz.getDeclaredField(fieldName))
                }
            } catch (_: Throwable) {
                // fall-back
            }
            return -1
        }

        fun init(instance: RefCnt) {
            PlatformDependent.safeConstructPutInt(instance, VALUE_OFFSET, 2)
        }

        fun refCnt(instance: RefCnt): Int =
            PlatformDependent.getVolatileInt(instance, VALUE_OFFSET) ushr 1

        fun retain(instance: RefCnt) {
            retain0(instance, 2)
        }

        fun retain(instance: RefCnt, increment: Int) {
            retain0(instance, checkPositive(increment, "increment") shl 1)
        }

        private fun retain0(instance: RefCnt, increment: Int) {
            val oldRef = PlatformDependent.getAndAddInt(instance, VALUE_OFFSET, increment)
            if ((oldRef and -0x7fffffff) != 0 || oldRef > Int.MAX_VALUE - increment) {
                PlatformDependent.getAndAddInt(instance, VALUE_OFFSET, -increment)
                throw IllegalReferenceCountException(0, increment ushr 1)
            }
        }

        fun release(instance: RefCnt): Boolean = release0(instance, 2)

        fun release(instance: RefCnt, decrement: Int): Boolean =
            release0(instance, checkPositive(decrement, "decrement") shl 1)

        private fun release0(instance: RefCnt, decrement: Int): Boolean {
            var curr: Int
            var next: Int
            do {
                curr = PlatformDependent.getInt(instance, VALUE_OFFSET)
                if (curr == decrement) {
                    next = 1
                } else {
                    if (curr < decrement || (curr and 1) == 1) {
                        throwIllegalRefCountOnRelease(decrement, curr)
                    }
                    next = curr - decrement
                }
            } while (!PlatformDependent.compareAndSwapInt(instance, VALUE_OFFSET, curr, next))
            return (next and 1) == 1
        }

        fun setRefCnt(instance: RefCnt, refCnt: Int) {
            val rawRefCnt = if (refCnt > 0) refCnt shl 1 else 1
            PlatformDependent.putOrderedInt(instance, VALUE_OFFSET, rawRefCnt)
        }

        fun resetRefCnt(instance: RefCnt) {
            PlatformDependent.putOrderedInt(instance, VALUE_OFFSET, 2)
        }

        fun isLiveNonVolatile(instance: RefCnt): Boolean {
            val rawCnt = PlatformDependent.getInt(instance, VALUE_OFFSET)
            return if (rawCnt == 2) true else (rawCnt and 1) == 0
        }
    }
}
