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
/**
 * Copyright (c) 2004-2011 QOS.ch
 * All rights reserved.
 *
 * Permission is hereby granted, free  of charge, to any person obtaining
 * a  copy  of this  software  and  associated  documentation files  (the
 * "Software"), to  deal in  the Software without  restriction, including
 * without limitation  the rights to  use, copy, modify,  merge, publish,
 * distribute,  sublicense, and/or sell  copies of  the Software,  and to
 * permit persons to whom the Software  is furnished to do so, subject to
 * the following conditions:
 *
 * The  above  copyright  notice  and  this permission  notice  shall  be
 * included in all copies or substantial portions of the Software.
 *
 * THE  SOFTWARE IS  PROVIDED  "AS  IS", WITHOUT  WARRANTY  OF ANY  KIND,
 * EXPRESS OR  IMPLIED, INCLUDING  BUT NOT LIMITED  TO THE  WARRANTIES OF
 * MERCHANTABILITY,    FITNESS    FOR    A   PARTICULAR    PURPOSE    AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE,  ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */
package io.netty.util.internal.logging

// contributors: lizongbo: proposed special treatment of array parameter values
// Joern Huxhorn: pointed out double[] omission, suggested deep array copy

/**
 * Formats messages according to very simple substitution rules. Substitutions
 * can be made 1, 2 or more arguments.
 *
 *
 *
 * For example,
 *
 * ```
 * MessageFormatter.format("Hi {}.", "there")
 * ```
 *
 * will return the string "Hi there.".
 *
 * The {} pair is called the *formatting anchor*. It serves to designate
 * the location where arguments need to be substituted within the message
 * pattern.
 *
 * In case your message contains the '{' or the '}' character, you do not have
 * to do anything special unless the '}' character immediately follows '{'. For
 * example,
 *
 * ```
 * MessageFormatter.format("Set {1,2,3} is not equal to {}.", "1,2")
 * ```
 *
 * will return the string "Set {1,2,3} is not equal to 1,2.".
 *
 *
 * If for whatever reason you need to place the string "{}" in the message
 * without its *formatting anchor* meaning, then you need to escape the
 * '{' character with '\', that is the backslash character. Only the '{'
 * character should be escaped. There is no need to escape the '}' character.
 * For example,
 *
 * ```
 * MessageFormatter.format("Set \\{} is not equal to {}.", "1,2")
 * ```
 *
 * will return the string "Set {} is not equal to 1,2.".
 *
 *
 * The escaping behavior just described can be overridden by escaping the escape
 * character '\'. Calling
 *
 * ```
 * MessageFormatter.format("File name is C:\\\\{}.", "file.zip")
 * ```
 *
 * will return the string "File name is C:\file.zip".
 *
 *
 * The formatting conventions are different than those of [java.text.MessageFormat]
 * which ships with the Java platform. This is justified by the fact that
 * SLF4J's implementation is 10 times faster than that of [java.text.MessageFormat].
 * This local performance difference is both measurable and significant in the
 * larger context of the complete logging processing chain.
 *
 *
 * See also [format], and [arrayFormat] methods for more details.
 */
object MessageFormatter {
    private const val DELIM_STR = "{}"
    private const val ESCAPE_CHAR = '\\'

    /**
     * Performs single argument substitution for the 'messagePattern' passed as
     * parameter.
     *
     * For example,
     *
     * ```
     * MessageFormatter.format("Hi {}.", "there")
     * ```
     *
     * will return the string "Hi there.".
     *
     * @param messagePattern The message pattern which will be parsed and formatted
     * @param arg            The argument to be substituted in place of the formatting anchor
     * @return The formatted message
     */
    @JvmStatic
    fun format(messagePattern: String?, arg: Any?): FormattingTuple {
        return arrayFormat(messagePattern, arrayOf(arg))
    }

    /**
     * Performs a two argument substitution for the 'messagePattern' passed as
     * parameter.
     *
     * For example,
     *
     * ```
     * MessageFormatter.format("Hi {}. My name is {}.", "Alice", "Bob")
     * ```
     *
     * will return the string "Hi Alice. My name is Bob.".
     *
     * @param messagePattern The message pattern which will be parsed and formatted
     * @param argA           The argument to be substituted in place of the first formatting
     *                       anchor
     * @param argB           The argument to be substituted in place of the second formatting
     *                       anchor
     * @return The formatted message
     */
    @JvmStatic
    fun format(messagePattern: String?, argA: Any?, argB: Any?): FormattingTuple {
        return arrayFormat(messagePattern, arrayOf(argA, argB))
    }

    /**
     * Same principle as the [format] methods except that any number of
     * arguments can be passed in an array.
     *
     * @param messagePattern The message pattern which will be parsed and formatted
     * @param argArray       An array of arguments to be substituted in place of formatting
     *                       anchors
     * @return The formatted message
     */
    @JvmStatic
    fun arrayFormat(messagePattern: String?, argArray: Array<out Any?>?): FormattingTuple {
        if (argArray == null || argArray.isEmpty()) {
            return FormattingTuple(messagePattern, null)
        }

        val lastArrIdx = argArray.size - 1
        val lastEntry = argArray[lastArrIdx]
        val throwable = if (lastEntry is Throwable) lastEntry else null

        if (messagePattern == null) {
            return FormattingTuple(null, throwable)
        }

        var j = messagePattern.indexOf(DELIM_STR)
        if (j == -1) {
            // this is a simple string
            return FormattingTuple(messagePattern, throwable)
        }

        val sbuf = StringBuilder(messagePattern.length + 50)
        var i = 0
        var L = 0
        do {
            var notEscaped = j == 0 || messagePattern[j - 1] != ESCAPE_CHAR
            if (notEscaped) {
                // normal case
                sbuf.append(messagePattern, i, j)
            } else {
                sbuf.append(messagePattern, i, j - 1)
                // check that escape char is not is escaped: "abc x:\\{}"
                notEscaped = j >= 2 && messagePattern[j - 2] == ESCAPE_CHAR
            }

            i = j + 2
            if (notEscaped) {
                deeplyAppendParameter(sbuf, argArray[L], null)
                L++
                if (L > lastArrIdx) {
                    break
                }
            } else {
                sbuf.append(DELIM_STR)
            }
            j = messagePattern.indexOf(DELIM_STR, i)
        } while (j != -1)

        // append the characters following the last {} pair.
        sbuf.append(messagePattern, i, messagePattern.length)
        return FormattingTuple(sbuf.toString(), if (L <= lastArrIdx) throwable else null)
    }

    // special treatment of array values was suggested by 'lizongbo'
    private fun deeplyAppendParameter(sbuf: StringBuilder, o: Any?, seenSet: MutableSet<Array<Any?>>?) {
        if (o == null) {
            sbuf.append("null")
            return
        }
        val objClass = o.javaClass
        if (!objClass.isArray) {
            if (Number::class.java.isAssignableFrom(objClass)) {
                // Prevent String instantiation for some number types
                when (objClass) {
                    java.lang.Long::class.java -> sbuf.append((o as Long))
                    java.lang.Integer::class.java, java.lang.Short::class.java, java.lang.Byte::class.java ->
                        sbuf.append((o as Number).toInt())
                    java.lang.Double::class.java -> sbuf.append((o as Double))
                    java.lang.Float::class.java -> sbuf.append((o as Float))
                    else -> safeObjectAppend(sbuf, o)
                }
            } else {
                safeObjectAppend(sbuf, o)
            }
        } else {
            // check for primitive array types because they
            // unfortunately cannot be cast to Object[]
            sbuf.append('[')
            when (objClass) {
                BooleanArray::class.java -> booleanArrayAppend(sbuf, o as BooleanArray)
                ByteArray::class.java -> byteArrayAppend(sbuf, o as ByteArray)
                CharArray::class.java -> charArrayAppend(sbuf, o as CharArray)
                ShortArray::class.java -> shortArrayAppend(sbuf, o as ShortArray)
                IntArray::class.java -> intArrayAppend(sbuf, o as IntArray)
                LongArray::class.java -> longArrayAppend(sbuf, o as LongArray)
                FloatArray::class.java -> floatArrayAppend(sbuf, o as FloatArray)
                DoubleArray::class.java -> doubleArrayAppend(sbuf, o as DoubleArray)
                else -> objectArrayAppend(sbuf, o as Array<Any?>, seenSet)
            }
            sbuf.append(']')
        }
    }

    private fun safeObjectAppend(sbuf: StringBuilder, o: Any) {
        try {
            val oAsString = o.toString()
            sbuf.append(oAsString)
        } catch (t: Throwable) {
            System.err
                .println("SLF4J: Failed toString() invocation on an object of type [" +
                        o.javaClass.name + ']')
            t.printStackTrace()
            sbuf.append("[FAILED toString()]")
        }
    }

    private fun objectArrayAppend(sbuf: StringBuilder, a: Array<Any?>, seenSet: MutableSet<Array<Any?>>?) {
        if (a.isEmpty()) {
            return
        }
        @Suppress("NAME_SHADOWING")
        val seenSet = seenSet ?: HashSet(a.size)
        if (seenSet.add(a)) {
            deeplyAppendParameter(sbuf, a[0], seenSet)
            for (i in 1 until a.size) {
                sbuf.append(", ")
                deeplyAppendParameter(sbuf, a[i], seenSet)
            }
            // allow repeats in siblings
            seenSet.remove(a)
        } else {
            sbuf.append("...")
        }
    }

    private fun booleanArrayAppend(sbuf: StringBuilder, a: BooleanArray) {
        if (a.isEmpty()) {
            return
        }
        sbuf.append(a[0])
        for (i in 1 until a.size) {
            sbuf.append(", ")
            sbuf.append(a[i])
        }
    }

    private fun byteArrayAppend(sbuf: StringBuilder, a: ByteArray) {
        if (a.isEmpty()) {
            return
        }
        sbuf.append(a[0])
        for (i in 1 until a.size) {
            sbuf.append(", ")
            sbuf.append(a[i])
        }
    }

    private fun charArrayAppend(sbuf: StringBuilder, a: CharArray) {
        if (a.isEmpty()) {
            return
        }
        sbuf.append(a[0])
        for (i in 1 until a.size) {
            sbuf.append(", ")
            sbuf.append(a[i])
        }
    }

    private fun shortArrayAppend(sbuf: StringBuilder, a: ShortArray) {
        if (a.isEmpty()) {
            return
        }
        sbuf.append(a[0])
        for (i in 1 until a.size) {
            sbuf.append(", ")
            sbuf.append(a[i])
        }
    }

    private fun intArrayAppend(sbuf: StringBuilder, a: IntArray) {
        if (a.isEmpty()) {
            return
        }
        sbuf.append(a[0])
        for (i in 1 until a.size) {
            sbuf.append(", ")
            sbuf.append(a[i])
        }
    }

    private fun longArrayAppend(sbuf: StringBuilder, a: LongArray) {
        if (a.isEmpty()) {
            return
        }
        sbuf.append(a[0])
        for (i in 1 until a.size) {
            sbuf.append(", ")
            sbuf.append(a[i])
        }
    }

    private fun floatArrayAppend(sbuf: StringBuilder, a: FloatArray) {
        if (a.isEmpty()) {
            return
        }
        sbuf.append(a[0])
        for (i in 1 until a.size) {
            sbuf.append(", ")
            sbuf.append(a[i])
        }
    }

    private fun doubleArrayAppend(sbuf: StringBuilder, a: DoubleArray) {
        if (a.isEmpty()) {
            return
        }
        sbuf.append(a[0])
        for (i in 1 until a.size) {
            sbuf.append(", ")
            sbuf.append(a[i])
        }
    }
}
