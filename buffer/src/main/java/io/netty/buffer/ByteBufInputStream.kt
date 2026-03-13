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

import io.netty.util.internal.ObjectUtil
import io.netty.util.internal.StringUtil
import java.io.DataInput
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/**
 * An [InputStream] which reads data from a [ByteBuf].
 *
 * A read operation against this stream will occur at the `readerIndex`
 * of its underlying buffer and the `readerIndex` will increase during
 * the read operation. Please note that it only reads up to the number of
 * readable bytes determined at the moment of construction. Therefore,
 * updating [ByteBuf.writerIndex] will not affect the return
 * value of [available].
 *
 * This stream implements [DataInput] for your convenience.
 * The endianness of the stream is not always big endian but depends on
 * the endianness of the underlying buffer.
 *
 * @see ByteBufOutputStream
 */
class ByteBufInputStream : InputStream, DataInput {

    private val buffer: ByteBuf
    private val startIndex: Int
    private val endIndex: Int
    private var closed: Boolean = false
    /**
     * To preserve backwards compatibility (which didn't transfer ownership) we support a conditional flag which
     * indicates if [buffer] should be released when this [InputStream] is closed.
     * However in future releases ownership should always be transferred and callers of this class should call
     * [io.netty.util.ReferenceCounted.retain] if necessary.
     */
    private val releaseOnClose: Boolean

    private var lineBuf: StringBuilder? = null

    /**
     * Creates a new stream which reads data from the specified `buffer`
     * starting at the current `readerIndex` and ending at the current
     * `writerIndex`.
     * @param buffer The buffer which provides the content for this [InputStream].
     */
    constructor(buffer: ByteBuf) : this(buffer, buffer.readableBytes())

    /**
     * Creates a new stream which reads data from the specified `buffer`
     * starting at the current `readerIndex` and ending at
     * `readerIndex + length`.
     * @param buffer The buffer which provides the content for this [InputStream].
     * @param length The length of the buffer to use for this [InputStream].
     * @throws IndexOutOfBoundsException
     *         if `readerIndex + length` is greater than `writerIndex`
     */
    constructor(buffer: ByteBuf, length: Int) : this(buffer, length, false)

    /**
     * Creates a new stream which reads data from the specified `buffer`
     * starting at the current `readerIndex` and ending at the current
     * `writerIndex`.
     * @param buffer The buffer which provides the content for this [InputStream].
     * @param releaseOnClose `true` means that when [close] is called then [ByteBuf.release] will
     *                       be called on `buffer`.
     */
    constructor(buffer: ByteBuf, releaseOnClose: Boolean) : this(buffer, buffer.readableBytes(), releaseOnClose)

    /**
     * Creates a new stream which reads data from the specified `buffer`
     * starting at the current `readerIndex` and ending at
     * `readerIndex + length`.
     * @param buffer The buffer which provides the content for this [InputStream].
     * @param length The length of the buffer to use for this [InputStream].
     * @param releaseOnClose `true` means that when [close] is called then [ByteBuf.release] will
     *                       be called on `buffer`.
     * @throws IndexOutOfBoundsException
     *         if `readerIndex + length` is greater than `writerIndex`
     */
    constructor(buffer: ByteBuf, length: Int, releaseOnClose: Boolean) {
        ObjectUtil.checkNotNull(buffer, "buffer")
        if (length < 0) {
            if (releaseOnClose) {
                buffer.release()
            }
            ObjectUtil.checkPositiveOrZero(length, "length")
        }
        if (length > buffer.readableBytes()) {
            if (releaseOnClose) {
                buffer.release()
            }
            throw IndexOutOfBoundsException(
                "Too many bytes to be read - Needs $length, maximum is ${buffer.readableBytes()}"
            )
        }

        this.releaseOnClose = releaseOnClose
        this.buffer = buffer
        startIndex = buffer.readerIndex()
        endIndex = startIndex + length
        buffer.markReaderIndex()
    }

    /**
     * Returns the number of read bytes by this stream so far.
     */
    fun readBytes(): Int {
        return buffer.readerIndex() - startIndex
    }

    @Throws(IOException::class)
    override fun close() {
        try {
            super.close()
        } finally {
            // The Closable interface says "If the stream is already closed then invoking this method has no effect."
            if (releaseOnClose && !closed) {
                closed = true
                buffer.release()
            }
        }
    }

    @Throws(IOException::class)
    override fun available(): Int {
        return endIndex - buffer.readerIndex()
    }

    // Suppress a warning since the class is not thread-safe
    override fun mark(readlimit: Int) {
        buffer.markReaderIndex()
    }

    override fun markSupported(): Boolean {
        return true
    }

    @Throws(IOException::class)
    override fun read(): Int {
        val available = available()
        if (available == 0) {
            return -1
        }
        return buffer.readByte().toInt() and 0xff
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val available = available()
        if (available == 0) {
            return -1
        }

        val toRead = Math.min(available, len)
        buffer.readBytes(b, off, toRead)
        return toRead
    }

    // Suppress a warning since the class is not thread-safe
    @Throws(IOException::class)
    override fun reset() {
        buffer.resetReaderIndex()
    }

    @Throws(IOException::class)
    override fun skip(n: Long): Long {
        return if (n > Int.MAX_VALUE) {
            skipBytes(Int.MAX_VALUE).toLong()
        } else {
            skipBytes(n.toInt()).toLong()
        }
    }

    @Throws(IOException::class)
    override fun readBoolean(): Boolean {
        checkAvailable(1)
        return read() != 0
    }

    @Throws(IOException::class)
    override fun readByte(): Byte {
        checkAvailable(1)
        return buffer.readByte()
    }

    @Throws(IOException::class)
    override fun readChar(): Char {
        return readShort().toInt().toChar()
    }

    @Throws(IOException::class)
    override fun readDouble(): Double {
        return java.lang.Double.longBitsToDouble(readLong())
    }

    @Throws(IOException::class)
    override fun readFloat(): Float {
        return java.lang.Float.intBitsToFloat(readInt())
    }

    @Throws(IOException::class)
    override fun readFully(b: ByteArray) {
        readFully(b, 0, b.size)
    }

    @Throws(IOException::class)
    override fun readFully(b: ByteArray, off: Int, len: Int) {
        checkAvailable(len)
        buffer.readBytes(b, off, len)
    }

    @Throws(IOException::class)
    override fun readInt(): Int {
        checkAvailable(4)
        return buffer.readInt()
    }

    @Throws(IOException::class)
    override fun readLine(): String? {
        var available = available()
        if (available == 0) {
            return null
        }

        lineBuf?.setLength(0)

        loop@ do {
            val c = buffer.readUnsignedByte().toInt()
            available--
            when (c) {
                '\n'.code -> break@loop
                '\r'.code -> {
                    if (available > 0 && buffer.getUnsignedByte(buffer.readerIndex()).toInt().toChar() == '\n') {
                        buffer.skipBytes(1)
                        available--
                    }
                    break@loop
                }
                else -> {
                    if (lineBuf == null) {
                        lineBuf = StringBuilder()
                    }
                    lineBuf!!.append(c.toChar())
                }
            }
        } while (available > 0)

        return if (lineBuf != null && lineBuf!!.length > 0) lineBuf.toString() else StringUtil.EMPTY_STRING
    }

    @Throws(IOException::class)
    override fun readLong(): Long {
        checkAvailable(8)
        return buffer.readLong()
    }

    @Throws(IOException::class)
    override fun readShort(): Short {
        checkAvailable(2)
        return buffer.readShort()
    }

    @Throws(IOException::class)
    override fun readUTF(): String {
        return DataInputStream.readUTF(this)
    }

    @Throws(IOException::class)
    override fun readUnsignedByte(): Int {
        return readByte().toInt() and 0xff
    }

    @Throws(IOException::class)
    override fun readUnsignedShort(): Int {
        return readShort().toInt() and 0xffff
    }

    @Throws(IOException::class)
    override fun skipBytes(n: Int): Int {
        val nBytes = Math.min(available(), n)
        buffer.skipBytes(nBytes)
        return nBytes
    }

    @Throws(IOException::class)
    private fun checkAvailable(fieldSize: Int) {
        if (fieldSize < 0) {
            throw IndexOutOfBoundsException("fieldSize cannot be a negative number")
        }
        if (fieldSize > available()) {
            throw EOFException("fieldSize is too long! Length is $fieldSize, but maximum is ${available()}")
        }
    }
}
