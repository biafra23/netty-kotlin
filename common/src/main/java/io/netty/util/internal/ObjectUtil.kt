/*
 * Copyright 2014 The Netty Project
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

object ObjectUtil {

    private const val FLOAT_ZERO: Float = 0.0F
    private const val DOUBLE_ZERO: Double = 0.0
    private const val LONG_ZERO: Long = 0L
    private const val INT_ZERO: Int = 0
    private const val SHORT_ZERO: Short = 0

    @JvmStatic
    fun <T> checkNotNull(arg: T?, text: String): T {
        if (arg == null) {
            throw NullPointerException(text)
        }
        return arg
    }

    @JvmStatic
    fun <T> deepCheckNotNull(text: String, vararg varargs: T?): Array<out T?> {
        if (varargs == null) {
            throw NullPointerException(text)
        }
        for (element in varargs) {
            if (element == null) {
                throw NullPointerException(text)
            }
        }
        return varargs
    }

    @JvmStatic
    @Throws(IllegalArgumentException::class)
    fun <T> checkNotNullWithIAE(arg: T?, paramName: String): T {
        if (arg == null) {
            throw IllegalArgumentException("Param '$paramName' must not be null")
        }
        return arg
    }

    @JvmStatic
    @Throws(IllegalArgumentException::class)
    fun <T> checkNotNullArrayParam(value: T?, index: Int, name: String): T {
        if (value == null) {
            throw IllegalArgumentException("Array index $index of parameter '$name' must not be null")
        }
        return value
    }

    @JvmStatic
    fun checkPositive(i: Int, name: String): Int {
        if (i <= INT_ZERO) {
            throw IllegalArgumentException("$name : $i (expected: > 0)")
        }
        return i
    }

    @JvmStatic
    fun checkPositive(l: Long, name: String): Long {
        if (l <= LONG_ZERO) {
            throw IllegalArgumentException("$name : $l (expected: > 0)")
        }
        return l
    }

    @JvmStatic
    fun checkPositive(d: Double, name: String): Double {
        if (d <= DOUBLE_ZERO) {
            throw IllegalArgumentException("$name : $d (expected: > 0)")
        }
        return d
    }

    @JvmStatic
    fun checkPositive(f: Float, name: String): Float {
        if (f <= FLOAT_ZERO) {
            throw IllegalArgumentException("$name : $f (expected: > 0)")
        }
        return f
    }

    @JvmStatic
    fun checkPositive(s: Short, name: String): Short {
        if (s <= SHORT_ZERO) {
            throw IllegalArgumentException("$name : $s (expected: > 0)")
        }
        return s
    }

    @JvmStatic
    fun checkPositiveOrZero(i: Int, name: String): Int {
        if (i < INT_ZERO) {
            throw IllegalArgumentException("$name : $i (expected: >= 0)")
        }
        return i
    }

    @JvmStatic
    fun checkPositiveOrZero(l: Long, name: String): Long {
        if (l < LONG_ZERO) {
            throw IllegalArgumentException("$name : $l (expected: >= 0)")
        }
        return l
    }

    @JvmStatic
    fun checkPositiveOrZero(d: Double, name: String): Double {
        if (d < DOUBLE_ZERO) {
            throw IllegalArgumentException("$name : $d (expected: >= 0)")
        }
        return d
    }

    @JvmStatic
    fun checkPositiveOrZero(f: Float, name: String): Float {
        if (f < FLOAT_ZERO) {
            throw IllegalArgumentException("$name : $f (expected: >= 0)")
        }
        return f
    }

    @JvmStatic
    fun checkInRange(i: Int, start: Int, end: Int, name: String): Int {
        if (i < start || i > end) {
            throw IllegalArgumentException("$name: $i (expected: $start-$end)")
        }
        return i
    }

    @JvmStatic
    fun checkInRange(l: Long, start: Long, end: Long, name: String): Long {
        if (l < start || l > end) {
            throw IllegalArgumentException("$name: $l (expected: $start-$end)")
        }
        return l
    }

    @JvmStatic
    fun checkInRange(d: Double, start: Double, end: Double, name: String): Double {
        if (d < start || d > end) {
            throw IllegalArgumentException("$name: $d (expected: $start-$end)")
        }
        return d
    }

    @JvmStatic
    fun <T> checkNonEmpty(array: Array<T>, name: String): Array<T> {
        if (checkNotNull(array, name).isEmpty()) {
            throw IllegalArgumentException("Param '$name' must not be empty")
        }
        return array
    }

    @JvmStatic
    fun checkNonEmpty(array: ByteArray, name: String): ByteArray {
        if (checkNotNull(array, name).isEmpty()) {
            throw IllegalArgumentException("Param '$name' must not be empty")
        }
        return array
    }

    @JvmStatic
    fun checkNonEmpty(array: CharArray, name: String): CharArray {
        if (checkNotNull(array, name).isEmpty()) {
            throw IllegalArgumentException("Param '$name' must not be empty")
        }
        return array
    }

    @JvmStatic
    fun <T : Collection<*>> checkNonEmpty(collection: T, name: String): T {
        if (checkNotNull(collection, name).isEmpty()) {
            throw IllegalArgumentException("Param '$name' must not be empty")
        }
        return collection
    }

    @JvmStatic
    fun checkNonEmpty(value: String, name: String): String {
        if (checkNotNull(value, name).isEmpty()) {
            throw IllegalArgumentException("Param '$name' must not be empty")
        }
        return value
    }

    @JvmStatic
    fun <K, V, T : Map<K, V>> checkNonEmpty(value: T, name: String): T {
        if (checkNotNull(value, name).isEmpty()) {
            throw IllegalArgumentException("Param '$name' must not be empty")
        }
        return value
    }

    @JvmStatic
    fun checkNonEmpty(value: CharSequence, name: String): CharSequence {
        if (checkNotNull(value, name).isEmpty()) {
            throw IllegalArgumentException("Param '$name' must not be empty")
        }
        return value
    }

    @JvmStatic
    fun checkNonEmptyAfterTrim(value: String, name: String): String {
        val trimmed = checkNotNull(value, name).trim()
        return checkNonEmpty(trimmed, name)
    }

    @JvmStatic
    fun intValue(wrapper: Int?, defaultValue: Int): Int {
        return wrapper ?: defaultValue
    }

    @JvmStatic
    fun longValue(wrapper: Long?, defaultValue: Long): Long {
        return wrapper ?: defaultValue
    }
}
