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
package io.netty.util.internal

import io.netty.util.Recycler
import io.netty.util.ReferenceCountUtil
import io.netty.util.concurrent.Promise
import io.netty.util.internal.ObjectPool.Handle

/**
 * Some pending write which should be picked up later.
 */
class PendingWrite private constructor(private val handle: Handle<PendingWrite>) {

    private var msg: Any? = null
    private var promise: Promise<Void?>? = null

    companion object {
        private val RECYCLER: Recycler<PendingWrite> = object : Recycler<PendingWrite>() {
            override fun newObject(handle: Handle<PendingWrite>): PendingWrite {
                return PendingWrite(handle)
            }
        }

        /**
         * Create a new empty [PendingWrite] instance
         */
        @JvmStatic
        fun newInstance(msg: Any, promise: Promise<Void?>): PendingWrite {
            val pending = RECYCLER.get()
            pending.msg = msg
            pending.promise = promise
            return pending
        }
    }

    /**
     * Clear and recycle this instance.
     */
    fun recycle(): Boolean {
        msg = null
        promise = null
        handle.recycle(this)
        return true
    }

    /**
     * Fails the underlying [Promise] with the given cause and recycle this instance.
     */
    fun failAndRecycle(cause: Throwable): Boolean {
        ReferenceCountUtil.release(msg)
        promise?.setFailure(cause)
        return recycle()
    }

    /**
     * Mark the underlying [Promise] successfully and recycle this instance.
     */
    fun successAndRecycle(): Boolean {
        promise?.setSuccess(null)
        return recycle()
    }

    fun msg(): Any? = msg

    fun promise(): Promise<Void?>? = promise

    /**
     * Recycle this instance and return the [Promise].
     */
    fun recycleAndGet(): Promise<Void?>? {
        val promise = this.promise
        recycle()
        return promise
    }
}
