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

import io.netty.buffer.CompositeByteBuf.ByteWrapper
import io.netty.util.internal.ObjectUtil
import io.netty.util.CharsetUtil
import io.netty.util.internal.PlatformDependent

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.util.Arrays

/**
 * Creates a new [ByteBuf] by allocating new space or by wrapping
 * or copying existing byte arrays, byte buffers and a string.
 *
 * ### Use static import
 * This classes is intended to be used with Java 5 static import statement:
 *
 * ```
 * import static io.netty.buffer.Unpooled.*;
 *
 * ByteBuf heapBuffer    = buffer(128);
 * ByteBuf directBuffer  = directBuffer(256);
 * ByteBuf wrappedBuffer = wrappedBuffer(new byte[128], new byte[256]);
 * ByteBuf copiedBuffer  = copiedBuffer(ByteBuffer.allocate(128));
 * ```
 *
 * ### Allocating a new buffer
 *
 * Three buffer types are provided out of the box.
 *
 *  * [buffer(int)][buffer] allocates a new fixed-capacity heap buffer.
 *  * [directBuffer(int)][directBuffer] allocates a new fixed-capacity direct buffer.
 *
 * ### Creating a wrapped buffer
 *
 * Wrapped buffer is a buffer which is a view of one or more existing
 * byte arrays and byte buffers. Any changes in the content of the original
 * array or buffer will be visible in the wrapped buffer. Various wrapper
 * methods are provided and their name is all `wrappedBuffer()`.
 * You might want to take a look at the methods that accept varargs closely if
 * you want to create a buffer which is composed of more than one array to
 * reduce the number of memory copy.
 *
 * ### Creating a copied buffer
 *
 * Copied buffer is a deep copy of one or more existing byte arrays, byte
 * buffers or a string. Unlike a wrapped buffer, there's no shared data
 * between the original data and the copied buffer. Various copy methods are
 * provided and their name is all `copiedBuffer()`. It is also convenient
 * to use this operation to merge multiple buffers into one buffer.
 */
object Unpooled {

    private val ALLOC: ByteBufAllocator = UnpooledByteBufAllocator.DEFAULT

    /**
     * Big endian byte order.
     */
    @JvmField
    val BIG_ENDIAN: ByteOrder = ByteOrder.BIG_ENDIAN

    /**
     * Little endian byte order.
     */
    @JvmField
    val LITTLE_ENDIAN: ByteOrder = ByteOrder.LITTLE_ENDIAN

    /**
     * A buffer whose capacity is `0`.
     */
    @JvmField
    @Suppress("checkstyle:StaticFinalBuffer") // EmptyByteBuf is not writeable or readable.
    val EMPTY_BUFFER: ByteBuf = ALLOC.buffer(0, 0)

    init {
        assert(EMPTY_BUFFER is EmptyByteBuf) { "EMPTY_BUFFER must be an EmptyByteBuf." }
    }

    /**
     * Creates a new big-endian Java heap buffer with reasonably small initial capacity, which
     * expands its capacity boundlessly on demand.
     */
    @JvmStatic
    fun buffer(): ByteBuf = ALLOC.heapBuffer()

    /**
     * Creates a new big-endian direct buffer with reasonably small initial capacity, which
     * expands its capacity boundlessly on demand.
     */
    @JvmStatic
    fun directBuffer(): ByteBuf = ALLOC.directBuffer()

    /**
     * Creates a new big-endian Java heap buffer with the specified `capacity`, which
     * expands its capacity boundlessly on demand. The new buffer's `readerIndex` and
     * `writerIndex` are `0`.
     */
    @JvmStatic
    fun buffer(initialCapacity: Int): ByteBuf = ALLOC.heapBuffer(initialCapacity)

    /**
     * Creates a new big-endian direct buffer with the specified `capacity`, which
     * expands its capacity boundlessly on demand. The new buffer's `readerIndex` and
     * `writerIndex` are `0`.
     */
    @JvmStatic
    fun directBuffer(initialCapacity: Int): ByteBuf = ALLOC.directBuffer(initialCapacity)

    /**
     * Creates a new big-endian Java heap buffer with the specified
     * `initialCapacity`, that may grow up to `maxCapacity`.
     * The new buffer's `readerIndex` and `writerIndex` are `0`.
     */
    @JvmStatic
    fun buffer(initialCapacity: Int, maxCapacity: Int): ByteBuf =
        ALLOC.heapBuffer(initialCapacity, maxCapacity)

    /**
     * Creates a new big-endian direct buffer with the specified
     * `initialCapacity`, that may grow up to `maxCapacity`.
     * The new buffer's `readerIndex` and `writerIndex` are `0`.
     */
    @JvmStatic
    fun directBuffer(initialCapacity: Int, maxCapacity: Int): ByteBuf =
        ALLOC.directBuffer(initialCapacity, maxCapacity)

    /**
     * Creates a new big-endian buffer which wraps the specified `array`.
     * A modification on the specified array's content will be visible to the
     * returned buffer.
     */
    @JvmStatic
    fun wrappedBuffer(array: ByteArray): ByteBuf {
        if (array.isEmpty()) {
            return EMPTY_BUFFER
        }
        return UnpooledHeapByteBuf(ALLOC, array, array.size)
    }

    /**
     * Creates a new big-endian buffer which wraps the sub-region of the
     * specified `array`. A modification on the specified array's
     * content will be visible to the returned buffer.
     */
    @JvmStatic
    fun wrappedBuffer(array: ByteArray, offset: Int, length: Int): ByteBuf {
        if (length == 0) {
            return EMPTY_BUFFER
        }

        if (offset == 0 && length == array.size) {
            return wrappedBuffer(array)
        }

        return wrappedBuffer(array).slice(offset, length)
    }

    /**
     * Creates a new buffer which wraps the specified NIO buffer's current
     * slice. A modification on the specified buffer's content will be
     * visible to the returned buffer.
     */
    @JvmStatic
    fun wrappedBuffer(buffer: ByteBuffer): ByteBuf {
        if (!buffer.hasRemaining()) {
            return EMPTY_BUFFER
        }
        if (!buffer.isDirect && buffer.hasArray()) {
            return wrappedBuffer(
                buffer.array(),
                buffer.arrayOffset() + buffer.position(),
                buffer.remaining()
            ).order(buffer.order())
        } else if (PlatformDependent.hasUnsafe()) {
            if (buffer.isReadOnly) {
                return if (buffer.isDirect) {
                    ReadOnlyUnsafeDirectByteBuf(ALLOC, buffer)
                } else {
                    ReadOnlyByteBufferBuf(ALLOC, buffer)
                }
            } else {
                return UnpooledUnsafeDirectByteBuf(ALLOC, buffer, buffer.remaining())
            }
        } else {
            if (buffer.isReadOnly) {
                return ReadOnlyByteBufferBuf(ALLOC, buffer)
            } else {
                return UnpooledDirectByteBuf(ALLOC, buffer, buffer.remaining())
            }
        }
    }

    /**
     * Creates a new buffer which wraps the specified memory address. If `doFree` is true the
     * memoryAddress will automatically be freed once the reference count of the [ByteBuf] reaches `0`.
     */
    @JvmStatic
    fun wrappedBuffer(memoryAddress: Long, size: Int, doFree: Boolean): ByteBuf {
        return WrappedUnpooledUnsafeDirectByteBuf(ALLOC, memoryAddress, size, doFree)
    }

    /**
     * Creates a new buffer which wraps the specified buffer's readable bytes.
     * A modification on the specified buffer's content will be visible to the
     * returned buffer.
     * @param buffer The buffer to wrap. Reference count ownership of this variable is transferred to this method.
     * @return The readable portion of the `buffer`, or an empty buffer if there is no readable portion.
     * The caller is responsible for releasing this buffer.
     */
    @JvmStatic
    fun wrappedBuffer(buffer: ByteBuf): ByteBuf {
        if (buffer.isReadable()) {
            return buffer.slice()
        } else {
            buffer.release()
            return EMPTY_BUFFER
        }
    }

    /**
     * Creates a new big-endian composite buffer which wraps the specified
     * arrays without copying them. A modification on the specified arrays'
     * content will be visible to the returned buffer.
     */
    @JvmStatic
    fun wrappedBuffer(vararg arrays: ByteArray): ByteBuf {
        return wrappedBuffer(arrays.size, *arrays)
    }

    /**
     * Creates a new big-endian composite buffer which wraps the readable bytes of the
     * specified buffers without copying them. A modification on the content
     * of the specified buffers will be visible to the returned buffer.
     * @param buffers The buffers to wrap. Reference count ownership of all variables is transferred to this method.
     * @return The readable portion of the `buffers`. The caller is responsible for releasing this buffer.
     */
    @JvmStatic
    fun wrappedBuffer(vararg buffers: ByteBuf): ByteBuf {
        return wrappedBuffer(buffers.size, *buffers)
    }

    /**
     * Creates a new big-endian composite buffer which wraps the slices of the specified
     * NIO buffers without copying them. A modification on the content of the
     * specified buffers will be visible to the returned buffer.
     */
    @JvmStatic
    fun wrappedBuffer(vararg buffers: ByteBuffer): ByteBuf {
        return wrappedBuffer(buffers.size, *buffers)
    }

    @JvmStatic
    internal fun <T> wrappedBuffer(maxNumComponents: Int, wrapper: ByteWrapper<T>, array: Array<T>): ByteBuf {
        when (array.size) {
            0 -> {}
            1 -> {
                if (!wrapper.isEmpty(array[0])) {
                    return wrapper.wrap(array[0])
                }
            }
            else -> {
                for (i in array.indices) {
                    val bytes = array[i]
                    if (bytes == null) {
                        return EMPTY_BUFFER
                    }
                    if (!wrapper.isEmpty(bytes)) {
                        return CompositeByteBuf(ALLOC, false, maxNumComponents, wrapper, array, i)
                    }
                }
            }
        }

        return EMPTY_BUFFER
    }

    /**
     * Creates a new big-endian composite buffer which wraps the specified
     * arrays without copying them. A modification on the specified arrays'
     * content will be visible to the returned buffer.
     */
    @JvmStatic
    fun wrappedBuffer(maxNumComponents: Int, vararg arrays: ByteArray): ByteBuf {
        return wrappedBuffer(maxNumComponents, CompositeByteBuf.BYTE_ARRAY_WRAPPER, arrays as Array<ByteArray>)
    }

    /**
     * Creates a new big-endian composite buffer which wraps the readable bytes of the
     * specified buffers without copying them. A modification on the content
     * of the specified buffers will be visible to the returned buffer.
     * @param maxNumComponents Advisement as to how many independent buffers are allowed to exist before
     * consolidation occurs.
     * @param buffers The buffers to wrap. Reference count ownership of all variables is transferred to this method.
     * @return The readable portion of the `buffers`. The caller is responsible for releasing this buffer.
     */
    @JvmStatic
    fun wrappedBuffer(maxNumComponents: Int, vararg buffers: ByteBuf): ByteBuf {
        when (buffers.size) {
            0 -> {}
            1 -> {
                val buffer = buffers[0]
                if (buffer.isReadable()) {
                    return wrappedBuffer(buffer.order(BIG_ENDIAN))
                } else {
                    buffer.release()
                }
            }
            else -> {
                for (i in buffers.indices) {
                    val buf = buffers[i]
                    if (buf.isReadable()) {
                        return CompositeByteBuf(ALLOC, false, maxNumComponents, buffers, i)
                    }
                    buf.release()
                }
            }
        }
        return EMPTY_BUFFER
    }

    /**
     * Creates a new big-endian composite buffer which wraps the slices of the specified
     * NIO buffers without copying them. A modification on the content of the
     * specified buffers will be visible to the returned buffer.
     */
    @JvmStatic
    fun wrappedBuffer(maxNumComponents: Int, vararg buffers: ByteBuffer): ByteBuf {
        return wrappedBuffer(maxNumComponents, CompositeByteBuf.BYTE_BUFFER_WRAPPER, buffers as Array<ByteBuffer>)
    }

    /**
     * Returns a new big-endian composite buffer with no components.
     */
    @JvmStatic
    fun compositeBuffer(): CompositeByteBuf {
        return compositeBuffer(AbstractByteBufAllocator.DEFAULT_MAX_COMPONENTS)
    }

    /**
     * Returns a new big-endian composite buffer with no components.
     */
    @JvmStatic
    fun compositeBuffer(maxNumComponents: Int): CompositeByteBuf {
        return CompositeByteBuf(ALLOC, false, maxNumComponents)
    }

    /**
     * Creates a new big-endian buffer whose content is a copy of the
     * specified `array`. The new buffer's `readerIndex` and
     * `writerIndex` are `0` and `array.length` respectively.
     */
    @JvmStatic
    fun copiedBuffer(array: ByteArray): ByteBuf {
        if (array.isEmpty()) {
            return EMPTY_BUFFER
        }
        return wrappedBuffer(array.clone())
    }

    /**
     * Creates a new big-endian buffer whose content is a copy of the
     * specified `array`'s sub-region. The new buffer's
     * `readerIndex` and `writerIndex` are `0` and
     * the specified `length` respectively.
     */
    @JvmStatic
    fun copiedBuffer(array: ByteArray, offset: Int, length: Int): ByteBuf {
        if (length == 0) {
            return EMPTY_BUFFER
        }
        val copy = PlatformDependent.allocateUninitializedArray(length)
        System.arraycopy(array, offset, copy, 0, length)
        return wrappedBuffer(copy)
    }

    /**
     * Creates a new buffer whose content is a copy of the specified
     * `buffer`'s current slice. The new buffer's `readerIndex`
     * and `writerIndex` are `0` and `buffer.remaining`
     * respectively.
     */
    @JvmStatic
    fun copiedBuffer(buffer: ByteBuffer): ByteBuf {
        val length = buffer.remaining()
        if (length == 0) {
            return EMPTY_BUFFER
        }
        val copy = PlatformDependent.allocateUninitializedArray(length)
        // Duplicate the buffer so we not adjust the position during our get operation.
        // See https://github.com/netty/netty/issues/3896
        val duplicate = buffer.duplicate()
        duplicate.get(copy)
        return wrappedBuffer(copy).order(duplicate.order())
    }

    /**
     * Creates a new buffer whose content is a copy of the specified
     * `buffer`'s readable bytes. The new buffer's `readerIndex`
     * and `writerIndex` are `0` and `buffer.readableBytes`
     * respectively.
     */
    @JvmStatic
    fun copiedBuffer(buffer: ByteBuf): ByteBuf {
        val readable = buffer.readableBytes()
        if (readable > 0) {
            val copy = buffer(readable)
            copy.writeBytes(buffer, buffer.readerIndex(), readable)
            return copy
        } else {
            return EMPTY_BUFFER
        }
    }

    /**
     * Creates a new big-endian buffer whose content is a merged copy of
     * the specified `arrays`. The new buffer's `readerIndex`
     * and `writerIndex` are `0` and the sum of all arrays'
     * `length` respectively.
     */
    @JvmStatic
    fun copiedBuffer(vararg arrays: ByteArray): ByteBuf {
        when (arrays.size) {
            0 -> return EMPTY_BUFFER
            1 -> return if (arrays[0].isEmpty()) {
                EMPTY_BUFFER
            } else {
                copiedBuffer(arrays[0])
            }
        }

        // Merge the specified arrays into one array.
        var length = 0
        for (a in arrays) {
            if (Int.MAX_VALUE - length < a.size) {
                throw IllegalArgumentException(
                    "The total length of the specified arrays is too big."
                )
            }
            length += a.size
        }

        if (length == 0) {
            return EMPTY_BUFFER
        }

        val mergedArray = PlatformDependent.allocateUninitializedArray(length)
        var j = 0
        for (i in arrays.indices) {
            val a = arrays[i]
            System.arraycopy(a, 0, mergedArray, j, a.size)
            j += a.size
        }

        return wrappedBuffer(mergedArray)
    }

    /**
     * Creates a new buffer whose content is a merged copy of the specified
     * `buffers`' readable bytes. The new buffer's `readerIndex`
     * and `writerIndex` are `0` and the sum of all buffers'
     * `readableBytes` respectively.
     *
     * @throws IllegalArgumentException
     * if the specified buffers' endianness are different from each
     * other
     */
    @JvmStatic
    fun copiedBuffer(vararg buffers: ByteBuf): ByteBuf {
        when (buffers.size) {
            0 -> return EMPTY_BUFFER
            1 -> return copiedBuffer(buffers[0])
        }

        // Merge the specified buffers into one buffer.
        var order: ByteOrder? = null
        var length = 0
        for (b in buffers) {
            val bLen = b.readableBytes()
            if (bLen <= 0) {
                continue
            }
            if (Int.MAX_VALUE - length < bLen) {
                throw IllegalArgumentException(
                    "The total length of the specified buffers is too big."
                )
            }
            length += bLen
            if (order != null) {
                if (order != b.order()) {
                    throw IllegalArgumentException("inconsistent byte order")
                }
            } else {
                order = b.order()
            }
        }

        if (length == 0) {
            return EMPTY_BUFFER
        }

        val mergedArray = PlatformDependent.allocateUninitializedArray(length)
        var j = 0
        for (i in buffers.indices) {
            val b = buffers[i]
            val bLen = b.readableBytes()
            b.getBytes(b.readerIndex(), mergedArray, j, bLen)
            j += bLen
        }

        return wrappedBuffer(mergedArray).order(order!!)
    }

    /**
     * Creates a new buffer whose content is a merged copy of the specified
     * `buffers`' slices. The new buffer's `readerIndex` and
     * `writerIndex` are `0` and the sum of all buffers'
     * `remaining` respectively.
     *
     * @throws IllegalArgumentException
     * if the specified buffers' endianness are different from each
     * other
     */
    @JvmStatic
    fun copiedBuffer(vararg buffers: ByteBuffer): ByteBuf {
        when (buffers.size) {
            0 -> return EMPTY_BUFFER
            1 -> return copiedBuffer(buffers[0])
        }

        // Merge the specified buffers into one buffer.
        var order: ByteOrder? = null
        var length = 0
        for (b in buffers) {
            val bLen = b.remaining()
            if (bLen <= 0) {
                continue
            }
            if (Int.MAX_VALUE - length < bLen) {
                throw IllegalArgumentException(
                    "The total length of the specified buffers is too big."
                )
            }
            length += bLen
            if (order != null) {
                if (order != b.order()) {
                    throw IllegalArgumentException("inconsistent byte order")
                }
            } else {
                order = b.order()
            }
        }

        if (length == 0) {
            return EMPTY_BUFFER
        }

        val mergedArray = PlatformDependent.allocateUninitializedArray(length)
        var j = 0
        for (i in buffers.indices) {
            // Duplicate the buffer so we not adjust the position during our get operation.
            // See https://github.com/netty/netty/issues/3896
            val b = buffers[i].duplicate()
            val bLen = b.remaining()
            b.get(mergedArray, j, bLen)
            j += bLen
        }

        return wrappedBuffer(mergedArray).order(order!!)
    }

    /**
     * Creates a new big-endian buffer whose content is the specified
     * `string` encoded in the specified `charset`.
     * The new buffer's `readerIndex` and `writerIndex` are
     * `0` and the length of the encoded string respectively.
     */
    @JvmStatic
    fun copiedBuffer(string: CharSequence, charset: Charset): ByteBuf {
        ObjectUtil.checkNotNull(string, "string")
        if (CharsetUtil.UTF_8 == charset) {
            return copiedBufferUtf8(string)
        }
        if (CharsetUtil.US_ASCII == charset) {
            return copiedBufferAscii(string)
        }
        if (string is CharBuffer) {
            return copiedBuffer(string as CharBuffer, charset)
        }

        return copiedBuffer(CharBuffer.wrap(string), charset)
    }

    private fun copiedBufferUtf8(string: CharSequence): ByteBuf {
        var release = true
        // Mimic the same behavior as other copiedBuffer implementations.
        val byteLength = ByteBufUtil.utf8Bytes(string)
        val buffer = ALLOC.heapBuffer(byteLength)
        try {
            ByteBufUtil.reserveAndWriteUtf8(buffer, string, byteLength)
            release = false
            return buffer
        } finally {
            if (release) {
                buffer.release()
            }
        }
    }

    private fun copiedBufferAscii(string: CharSequence): ByteBuf {
        var release = true
        // Mimic the same behavior as other copiedBuffer implementations.
        val buffer = ALLOC.heapBuffer(string.length)
        try {
            ByteBufUtil.writeAscii(buffer, string)
            release = false
            return buffer
        } finally {
            if (release) {
                buffer.release()
            }
        }
    }

    /**
     * Creates a new big-endian buffer whose content is a subregion of
     * the specified `string` encoded in the specified `charset`.
     * The new buffer's `readerIndex` and `writerIndex` are
     * `0` and the length of the encoded string respectively.
     */
    @JvmStatic
    fun copiedBuffer(
        string: CharSequence, offset: Int, length: Int, charset: Charset
    ): ByteBuf {
        ObjectUtil.checkNotNull(string, "string")
        if (length == 0) {
            return EMPTY_BUFFER
        }

        if (string is CharBuffer) {
            val buf = string
            if (buf.hasArray()) {
                return copiedBuffer(
                    buf.array(),
                    buf.arrayOffset() + buf.position() + offset,
                    length, charset
                )
            }

            val sliced = buf.slice()
            sliced.limit(length)
            sliced.position(offset)
            return copiedBuffer(sliced, charset)
        }

        return copiedBuffer(CharBuffer.wrap(string, offset, offset + length), charset)
    }

    /**
     * Creates a new big-endian buffer whose content is the specified
     * `array` encoded in the specified `charset`.
     * The new buffer's `readerIndex` and `writerIndex` are
     * `0` and the length of the encoded string respectively.
     */
    @JvmStatic
    fun copiedBuffer(array: CharArray, charset: Charset): ByteBuf {
        ObjectUtil.checkNotNull(array, "array")
        return copiedBuffer(array, 0, array.size, charset)
    }

    /**
     * Creates a new big-endian buffer whose content is a subregion of
     * the specified `array` encoded in the specified `charset`.
     * The new buffer's `readerIndex` and `writerIndex` are
     * `0` and the length of the encoded string respectively.
     */
    @JvmStatic
    fun copiedBuffer(array: CharArray, offset: Int, length: Int, charset: Charset): ByteBuf {
        ObjectUtil.checkNotNull(array, "array")
        if (length == 0) {
            return EMPTY_BUFFER
        }
        return copiedBuffer(CharBuffer.wrap(array, offset, length), charset)
    }

    private fun copiedBuffer(buffer: CharBuffer, charset: Charset): ByteBuf {
        return ByteBufUtil.encodeString0(ALLOC, true, buffer, charset, 0)
    }

    /**
     * Creates a read-only buffer which disallows any modification operations
     * on the specified `buffer`. The new buffer has the same
     * `readerIndex` and `writerIndex` with the specified
     * `buffer`.
     *
     * @deprecated Use [ByteBuf.asReadOnly].
     */
    @Deprecated("Use ByteBuf.asReadOnly().")
    @JvmStatic
    fun unmodifiableBuffer(buffer: ByteBuf): ByteBuf {
        val endianness = buffer.order()
        if (endianness == BIG_ENDIAN) {
            return newReadyOnlyBuffer(buffer)
        }

        return newReadyOnlyBuffer(buffer.order(BIG_ENDIAN)).order(LITTLE_ENDIAN)
    }

    private fun newReadyOnlyBuffer(buffer: ByteBuf): ReadOnlyByteBuf {
        // We can only use ReadOnlyAbstractByteBuf if we either have nothing to unwrap or the unwrapped buffer is of
        // type AbstractByteBuf. Otherwise we will produce a CCE later.
        return if (buffer is AbstractByteBuf && (
                    buffer.unwrap() == null || buffer.unwrap() is AbstractByteBuf)
        ) {
            ReadOnlyAbstractByteBuf(buffer as AbstractByteBuf)
        } else {
            ReadOnlyByteBuf(buffer)
        }
    }

    /**
     * Creates a new 4-byte big-endian buffer that holds the specified 32-bit integer.
     */
    @JvmStatic
    fun copyInt(value: Int): ByteBuf {
        val buf = buffer(4)
        buf.writeInt(value)
        return buf
    }

    /**
     * Create a big-endian buffer that holds a sequence of the specified 32-bit integers.
     */
    @JvmStatic
    fun copyInt(vararg values: Int): ByteBuf {
        if (values.isEmpty()) {
            return EMPTY_BUFFER
        }
        val buffer = buffer(values.size * 4)
        for (v in values) {
            buffer.writeInt(v)
        }
        return buffer
    }

    /**
     * Creates a new 2-byte big-endian buffer that holds the specified 16-bit integer.
     */
    @JvmStatic
    fun copyShort(value: Int): ByteBuf {
        val buf = buffer(2)
        buf.writeShort(value)
        return buf
    }

    /**
     * Create a new big-endian buffer that holds a sequence of the specified 16-bit integers.
     */
    @JvmStatic
    fun copyShort(vararg values: Short): ByteBuf {
        if (values.isEmpty()) {
            return EMPTY_BUFFER
        }
        val buffer = buffer(values.size * 2)
        for (v in values) {
            buffer.writeShort(v.toInt())
        }
        return buffer
    }

    /**
     * Create a new big-endian buffer that holds a sequence of the specified 16-bit integers.
     */
    @JvmStatic
    fun copyShort(vararg values: Int): ByteBuf {
        if (values.isEmpty()) {
            return EMPTY_BUFFER
        }
        val buffer = buffer(values.size * 2)
        for (v in values) {
            buffer.writeShort(v)
        }
        return buffer
    }

    /**
     * Creates a new 3-byte big-endian buffer that holds the specified 24-bit integer.
     */
    @JvmStatic
    fun copyMedium(value: Int): ByteBuf {
        val buf = buffer(3)
        buf.writeMedium(value)
        return buf
    }

    /**
     * Create a new big-endian buffer that holds a sequence of the specified 24-bit integers.
     */
    @JvmStatic
    fun copyMedium(vararg values: Int): ByteBuf {
        if (values.isEmpty()) {
            return EMPTY_BUFFER
        }
        val buffer = buffer(values.size * 3)
        for (v in values) {
            buffer.writeMedium(v)
        }
        return buffer
    }

    /**
     * Creates a new 8-byte big-endian buffer that holds the specified 64-bit integer.
     */
    @JvmStatic
    fun copyLong(value: Long): ByteBuf {
        val buf = buffer(8)
        buf.writeLong(value)
        return buf
    }

    /**
     * Create a new big-endian buffer that holds a sequence of the specified 64-bit integers.
     */
    @JvmStatic
    fun copyLong(vararg values: Long): ByteBuf {
        if (values.isEmpty()) {
            return EMPTY_BUFFER
        }
        val buffer = buffer(values.size * 8)
        for (v in values) {
            buffer.writeLong(v)
        }
        return buffer
    }

    /**
     * Creates a new single-byte big-endian buffer that holds the specified boolean value.
     */
    @JvmStatic
    fun copyBoolean(value: Boolean): ByteBuf {
        val buf = buffer(1)
        buf.writeBoolean(value)
        return buf
    }

    /**
     * Create a new big-endian buffer that holds a sequence of the specified boolean values.
     */
    @JvmStatic
    fun copyBoolean(vararg values: Boolean): ByteBuf {
        if (values.isEmpty()) {
            return EMPTY_BUFFER
        }
        val buffer = buffer(values.size)
        for (v in values) {
            buffer.writeBoolean(v)
        }
        return buffer
    }

    /**
     * Creates a new 4-byte big-endian buffer that holds the specified 32-bit floating point number.
     */
    @JvmStatic
    fun copyFloat(value: Float): ByteBuf {
        val buf = buffer(4)
        buf.writeFloat(value)
        return buf
    }

    /**
     * Create a new big-endian buffer that holds a sequence of the specified 32-bit floating point numbers.
     */
    @JvmStatic
    fun copyFloat(vararg values: Float): ByteBuf {
        if (values.isEmpty()) {
            return EMPTY_BUFFER
        }
        val buffer = buffer(values.size * 4)
        for (v in values) {
            buffer.writeFloat(v)
        }
        return buffer
    }

    /**
     * Creates a new 8-byte big-endian buffer that holds the specified 64-bit floating point number.
     */
    @JvmStatic
    fun copyDouble(value: Double): ByteBuf {
        val buf = buffer(8)
        buf.writeDouble(value)
        return buf
    }

    /**
     * Create a new big-endian buffer that holds a sequence of the specified 64-bit floating point numbers.
     */
    @JvmStatic
    fun copyDouble(vararg values: Double): ByteBuf {
        if (values.isEmpty()) {
            return EMPTY_BUFFER
        }
        val buffer = buffer(values.size * 8)
        for (v in values) {
            buffer.writeDouble(v)
        }
        return buffer
    }

    /**
     * Return a unreleasable view on the given [ByteBuf] which will just ignore release and retain calls.
     */
    @JvmStatic
    fun unreleasableBuffer(buf: ByteBuf): ByteBuf {
        return UnreleasableByteBuf(buf)
    }

    /**
     * Wrap the given [ByteBuf]s in an unmodifiable [ByteBuf]. Be aware the returned [ByteBuf] will
     * not try to slice the given [ByteBuf]s to reduce GC-Pressure.
     *
     * @deprecated Use [wrappedUnmodifiableBuffer].
     */
    @Deprecated("Use wrappedUnmodifiableBuffer(ByteBuf...).")
    @JvmStatic
    fun unmodifiableBuffer(vararg buffers: ByteBuf): ByteBuf {
        return wrappedUnmodifiableBuffer(true, buffers)
    }

    /**
     * Wrap the given [ByteBuf]s in an unmodifiable [ByteBuf]. Be aware the returned [ByteBuf] will
     * not try to slice the given [ByteBuf]s to reduce GC-Pressure.
     *
     * The returned [ByteBuf] may wrap the provided array directly, and so should not be subsequently modified.
     */
    @JvmStatic
    fun wrappedUnmodifiableBuffer(vararg buffers: ByteBuf): ByteBuf {
        return wrappedUnmodifiableBuffer(false, buffers)
    }

    private fun wrappedUnmodifiableBuffer(copy: Boolean, buffers: Array<out ByteBuf>): ByteBuf {
        return when (buffers.size) {
            0 -> EMPTY_BUFFER
            1 -> buffers[0].asReadOnly()
            else -> {
                val bufs = if (copy) {
                    Arrays.copyOf(buffers, buffers.size, Array<ByteBuf>::class.java)
                } else {
                    buffers as Array<ByteBuf>
                }
                FixedCompositeByteBuf(ALLOC, *bufs)
            }
        }
    }
}
