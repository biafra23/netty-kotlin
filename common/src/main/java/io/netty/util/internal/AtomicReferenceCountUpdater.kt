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

import io.netty.util.ReferenceCounted
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater

@Suppress("DEPRECATION")
abstract class AtomicReferenceCountUpdater<T : ReferenceCounted> protected constructor() : ReferenceCountUpdater<T>() {

    protected abstract fun updater(): AtomicIntegerFieldUpdater<T>

    override fun safeInitializeRawRefCnt(refCntObj: T, value: Int) {
        updater().set(refCntObj, value)
    }

    override fun getAndAddRawRefCnt(refCntObj: T, increment: Int): Int {
        return updater().getAndAdd(refCntObj, increment)
    }

    override fun getRawRefCnt(refCnt: T): Int {
        return updater().get(refCnt)
    }

    override fun getAcquireRawRefCnt(refCnt: T): Int {
        return updater().get(refCnt)
    }

    override fun setReleaseRawRefCnt(refCnt: T, value: Int) {
        updater().lazySet(refCnt, value)
    }

    override fun casRawRefCnt(refCnt: T, expected: Int, value: Int): Boolean {
        return updater().compareAndSet(refCnt, expected, value)
    }
}
