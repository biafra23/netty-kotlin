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
import java.io.DataOutput
import java.io.DataOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * An [OutputStream] which writes data to a [ByteBuf].
 *
 * A write operation against this stream will occur at the `writerIndex`
 * of its underlying buffer and the `writerIndex` will increase during
 * the write operation.
 *
 * This stream implements [DataOutput] for your convenience.
 * The endianness of the stream is not always big endian but depends on
 * the endianness of the underlying buffer.
 *
 * @see ByteBufInputStream
 */
class ByteBufOutputStream : OutputStream, DataOutput {

    private val buffer: ByteBuf
    private val startIndex: Int
    private var utf8out: DataOutputStream? = null // lazily-instantiated
    private var closed: Boolean = false
    private val releaseOnClose: Boolean

    /**
     * Creates a new stream which writes data to the specified `buffer`.
     */
    constructor(buffer: ByteBuf) : this(buffer, false)

    /**
     * Creates a new stream which writes data to the specified `buffer`.
     *
     * @param buffer Writes data to the buffer for this [OutputStream].
     * @param releaseOnClose `true` means that when [close] is called then [ByteBuf.release] will
     *                       be called on `buffer`.
     */
    constructor(buffer: ByteBuf, releaseOnClose: Boolean) {
        this.releaseOnClose = releaseOnClose
        this.buffer = ObjectUtil.checkNotNull(buffer, "buffer")
        startIndex = buffer.writerIndex()
    }

    /**
     * Returns the number of written bytes by this stream so far.
     */
    fun writtenBytes(): Int {
        return buffer.writerIndex() - startIndex
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        buffer.writeBytes(b, off, len)
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray) {
        buffer.writeBytes(b)
    }

    @Throws(IOException::class)
    override fun write(b: Int) {
        buffer.writeByte(b)
    }

    @Throws(IOException::class)
    override fun writeBoolean(v: Boolean) {
        buffer.writeBoolean(v)
    }

    @Throws(IOException::class)
    override fun writeByte(v: Int) {
        buffer.writeByte(v)
    }

    @Throws(IOException::class)
    override fun writeBytes(s: String) {
        // We don't use `ByteBuf.writeCharSequence` here, because `writeBytes` is specified to only write the
        // lower-order by of multibyte characters (exactly one byte per character in the string), while
        // `writeCharSequence` will instead write a '?' replacement character.
        val length = s.length
        buffer.ensureWritable(length)
        val offset = buffer.writerIndex()
        for (i in 0 until length) {
            buffer.setByte(offset + i, s[i].code.toByte().toInt())
        }
        buffer.writerIndex(offset + length)
    }

    @Throws(IOException::class)
    override fun writeChar(v: Int) {
        buffer.writeChar(v)
    }

    @Throws(IOException::class)
    override fun writeChars(s: String) {
        for (i in 0 until s.length) {
            buffer.writeChar(s[i].code)
        }
    }

    @Throws(IOException::class)
    override fun writeDouble(v: Double) {
        buffer.writeDouble(v)
    }

    @Throws(IOException::class)
    override fun writeFloat(v: Float) {
        buffer.writeFloat(v)
    }

    @Throws(IOException::class)
    override fun writeInt(v: Int) {
        buffer.writeInt(v)
    }

    @Throws(IOException::class)
    override fun writeLong(v: Long) {
        buffer.writeLong(v)
    }

    @Throws(IOException::class)
    override fun writeShort(v: Int) {
        buffer.writeShort(v.toShort().toInt())
    }

    @Throws(IOException::class)
    override fun writeUTF(s: String) {
        var out = utf8out
        if (out == null) {
            if (closed) {
                throw IOException("The stream is closed")
            }
            // Suppress a warning since the stream is closed in the close() method
            out = DataOutputStream(this)
            utf8out = out
        }
        out.writeUTF(s)
    }

    /**
     * Returns the buffer where this stream is writing data.
     */
    fun buffer(): ByteBuf {
        return buffer
    }

    @Throws(IOException::class)
    override fun close() {
        if (closed) {
            return
        }
        closed = true

        try {
            super.close()
        } finally {
            utf8out?.close()
            if (releaseOnClose) {
                buffer.release()
            }
        }
    }
}
