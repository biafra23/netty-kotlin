/*
 * Copyright 2016 The Netty Project
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

import java.io.ByteArrayOutputStream
import java.io.PrintStream

object ThrowableUtil {

    @JvmStatic
    fun <T : Throwable> unknownStackTrace(cause: T, clazz: Class<*>, method: String): T {
        cause.stackTrace = arrayOf(StackTraceElement(clazz.name, method, null, -1))
        return cause
    }

    @JvmStatic
    @Deprecated("No longer needed.", replaceWith = ReplaceWith(""))
    fun stackTraceToString(cause: Throwable): String {
        ByteArrayOutputStream().use { out ->
            val pout = PrintStream(out)
            cause.printStackTrace(pout)
            pout.flush()
            return out.toString()
        }
    }

    @JvmStatic
    @Deprecated("Always returns true.", replaceWith = ReplaceWith("true"))
    fun haveSuppressed(): Boolean = true

    @JvmStatic
    fun addSuppressed(target: Throwable, suppressed: Throwable?) {
        if (suppressed != null) {
            target.addSuppressed(suppressed)
        }
    }

    @JvmStatic
    fun addSuppressedAndClear(target: Throwable, suppressed: MutableList<Throwable>) {
        addSuppressed(target, suppressed as List<Throwable>)
        suppressed.clear()
    }

    @JvmStatic
    fun addSuppressed(target: Throwable, suppressed: List<Throwable>) {
        for (t in suppressed) {
            addSuppressed(target, t)
        }
    }

    @JvmStatic
    fun getSuppressed(source: Throwable): Array<Throwable> = source.suppressed

    @JvmStatic
    fun interruptAndAttachAsyncStackTrace(thread: Thread, cause: Throwable) {
        val stackTrace = thread.stackTrace
        val asyncIE = InterruptedException("Asynchronous interruption: $thread")
        thread.interrupt()
        asyncIE.stackTrace = stackTrace
        addSuppressed(cause, asyncIE)
    }
}
