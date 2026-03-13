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

import io.netty.util.AsciiString
import io.netty.util.ByteProcessor
import io.netty.util.CharsetUtil
import io.netty.util.IllegalReferenceCountException
import io.netty.util.ResourceLeakDetector
import io.netty.util.ResourceLeakDetectorFactory
import io.netty.util.internal.ObjectUtil
import io.netty.util.internal.PlatformDependent
import io.netty.util.internal.StringUtil
import io.netty.util.internal.SystemPropertyUtil
import io.netty.util.internal.logging.InternalLoggerFactory
import io.netty.util.internal.MathUtil.isOutOfBounds
import io.netty.util.internal.ObjectUtil.checkPositiveOrZero
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.channels.GatheringByteChannel
import java.nio.channels.ScatteringByteChannel
import java.nio.charset.Charset

/**
 * A skeletal implementation of a buffer.
 */
abstract class AbstractByteBuf protected constructor(maxCapacity: Int) : ByteBuf() {

    @JvmField
    var readerIndex: Int = 0
    @JvmField
    var writerIndex: Int = 0
    private var markedReaderIndex: Int = 0
    private var markedWriterIndex: Int = 0
    private var maxCapacity: Int = 0

    init {
        checkPositiveOrZero(maxCapacity, "maxCapacity")
        this.maxCapacity = maxCapacity
    }

    override fun isReadOnly(): Boolean = false

    @Suppress("DEPRECATION")
    override fun asReadOnly(): ByteBuf {
        if (isReadOnly()) {
            return this
        }
        return Unpooled.unmodifiableBuffer(this)
    }

    override fun maxCapacity(): Int = maxCapacity

    protected fun maxCapacity(maxCapacity: Int) {
        this.maxCapacity = maxCapacity
    }

    override fun readerIndex(): Int = readerIndex

    override fun readerIndex(readerIndex: Int): ByteBuf {
        if (checkBounds) {
            checkIndexBounds(readerIndex, writerIndex, capacity())
        }
        this.readerIndex = readerIndex
        return this
    }

    override fun writerIndex(): Int = writerIndex

    override fun writerIndex(writerIndex: Int): ByteBuf {
        if (checkBounds) {
            checkIndexBounds(readerIndex, writerIndex, capacity())
        }
        this.writerIndex = writerIndex
        return this
    }

    override fun setIndex(readerIndex: Int, writerIndex: Int): ByteBuf {
        if (checkBounds) {
            checkIndexBounds(readerIndex, writerIndex, capacity())
        }
        setIndex0(readerIndex, writerIndex)
        return this
    }

    override fun clear(): ByteBuf {
        writerIndex = 0
        readerIndex = 0
        return this
    }

    override fun isReadable(): Boolean = writerIndex > readerIndex

    override fun isReadable(size: Int): Boolean = writerIndex - readerIndex >= size

    override fun isWritable(): Boolean = capacity() > writerIndex

    override fun isWritable(size: Int): Boolean = capacity() - writerIndex >= size

    override fun readableBytes(): Int = writerIndex - readerIndex

    override fun writableBytes(): Int = capacity() - writerIndex

    override fun maxWritableBytes(): Int = maxCapacity() - writerIndex

    override fun markReaderIndex(): ByteBuf {
        markedReaderIndex = readerIndex
        return this
    }

    override fun resetReaderIndex(): ByteBuf {
        readerIndex(markedReaderIndex)
        return this
    }

    override fun markWriterIndex(): ByteBuf {
        markedWriterIndex = writerIndex
        return this
    }

    override fun resetWriterIndex(): ByteBuf {
        writerIndex(markedWriterIndex)
        return this
    }

    override fun discardReadBytes(): ByteBuf {
        if (readerIndex == 0) {
            ensureAccessible()
            return this
        }

        if (readerIndex != writerIndex) {
            setBytes(0, this, readerIndex, writerIndex - readerIndex)
            writerIndex -= readerIndex
            adjustMarkers(readerIndex)
            readerIndex = 0
        } else {
            ensureAccessible()
            adjustMarkers(readerIndex)
            readerIndex = 0
            writerIndex = 0
        }
        return this
    }

    override fun discardSomeReadBytes(): ByteBuf {
        if (readerIndex > 0) {
            if (readerIndex == writerIndex) {
                ensureAccessible()
                adjustMarkers(readerIndex)
                readerIndex = 0
                writerIndex = 0
                return this
            }

            if (readerIndex >= capacity().ushr(1)) {
                setBytes(0, this, readerIndex, writerIndex - readerIndex)
                writerIndex -= readerIndex
                adjustMarkers(readerIndex)
                readerIndex = 0
                return this
            }
        }
        ensureAccessible()
        return this
    }

    protected fun adjustMarkers(decrement: Int) {
        if (markedReaderIndex <= decrement) {
            markedReaderIndex = 0
            if (markedWriterIndex <= decrement) {
                markedWriterIndex = 0
            } else {
                markedWriterIndex -= decrement
            }
        } else {
            markedReaderIndex -= decrement
            markedWriterIndex -= decrement
        }
    }

    // Called after a capacity reduction
    protected fun trimIndicesToCapacity(newCapacity: Int) {
        if (writerIndex() > newCapacity) {
            setIndex0(Math.min(readerIndex(), newCapacity), newCapacity)
        }
    }

    override fun ensureWritable(minWritableBytes: Int): ByteBuf {
        ensureWritable0(checkPositiveOrZero(minWritableBytes, "minWritableBytes"))
        return this
    }

    internal fun ensureWritable0(minWritableBytes: Int) {
        val writerIndex = writerIndex()
        val targetCapacity = writerIndex + minWritableBytes
        // using non-short-circuit & to reduce branching - this is a hot path and targetCapacity should rarely overflow
        if (targetCapacity >= 0 && targetCapacity <= capacity()) {
            ensureAccessible()
            return
        }
        if (checkBounds && (targetCapacity < 0 || targetCapacity > maxCapacity)) {
            ensureAccessible()
            throw IndexOutOfBoundsException(
                "writerIndex($writerIndex) + minWritableBytes($minWritableBytes) exceeds maxCapacity($maxCapacity): $this"
            )
        }

        // Normalize the target capacity to the power of 2.
        val fastWritable = maxFastWritableBytes()
        val newCapacity = if (fastWritable >= minWritableBytes) writerIndex + fastWritable
        else alloc().calculateNewCapacity(targetCapacity, maxCapacity)

        // Adjust to the new capacity.
        capacity(newCapacity)
    }

    override fun ensureWritable(minWritableBytes: Int, force: Boolean): Int {
        ensureAccessible()
        checkPositiveOrZero(minWritableBytes, "minWritableBytes")

        if (minWritableBytes <= writableBytes()) {
            return 0
        }

        val maxCapacity = maxCapacity()
        val writerIndex = writerIndex()
        if (minWritableBytes > maxCapacity - writerIndex) {
            if (!force || capacity() == maxCapacity) {
                return 1
            }

            capacity(maxCapacity)
            return 3
        }

        val fastWritable = maxFastWritableBytes()
        val newCapacity = if (fastWritable >= minWritableBytes) writerIndex + fastWritable
        else alloc().calculateNewCapacity(writerIndex + minWritableBytes, maxCapacity)

        // Adjust to the new capacity.
        capacity(newCapacity)
        return 2
    }

    @Suppress("DEPRECATION")
    override fun order(endianness: ByteOrder): ByteBuf {
        if (endianness == order()) {
            return this
        }
        ObjectUtil.checkNotNull(endianness, "endianness")
        return newSwappedByteBuf()
    }

    /**
     * Creates a new [SwappedByteBuf] for this [ByteBuf] instance.
     */
    internal open fun newSwappedByteBuf(): SwappedByteBuf = SwappedByteBuf(this)

    override fun getByte(index: Int): Byte {
        checkIndex(index)
        return _getByte(index)
    }

    protected abstract fun _getByte(index: Int): Byte

    override fun getBoolean(index: Int): Boolean = getByte(index).toInt() != 0

    override fun getUnsignedByte(index: Int): Short = (getByte(index).toInt() and 0xFF).toShort()

    override fun getShort(index: Int): Short {
        checkIndex(index, 2)
        return _getShort(index)
    }

    protected abstract fun _getShort(index: Int): Short

    override fun getShortLE(index: Int): Short {
        checkIndex(index, 2)
        return _getShortLE(index)
    }

    protected abstract fun _getShortLE(index: Int): Short

    override fun getUnsignedShort(index: Int): Int = getShort(index).toInt() and 0xFFFF

    override fun getUnsignedShortLE(index: Int): Int = getShortLE(index).toInt() and 0xFFFF

    override fun getUnsignedMedium(index: Int): Int {
        checkIndex(index, 3)
        return _getUnsignedMedium(index)
    }

    protected abstract fun _getUnsignedMedium(index: Int): Int

    override fun getUnsignedMediumLE(index: Int): Int {
        checkIndex(index, 3)
        return _getUnsignedMediumLE(index)
    }

    protected abstract fun _getUnsignedMediumLE(index: Int): Int

    override fun getMedium(index: Int): Int {
        var value = getUnsignedMedium(index)
        if (value and 0x800000 != 0) {
            value = value or 0xff000000.toInt()
        }
        return value
    }

    override fun getMediumLE(index: Int): Int {
        var value = getUnsignedMediumLE(index)
        if (value and 0x800000 != 0) {
            value = value or 0xff000000.toInt()
        }
        return value
    }

    override fun getInt(index: Int): Int {
        checkIndex(index, 4)
        return _getInt(index)
    }

    protected abstract fun _getInt(index: Int): Int

    override fun getIntLE(index: Int): Int {
        checkIndex(index, 4)
        return _getIntLE(index)
    }

    protected abstract fun _getIntLE(index: Int): Int

    override fun getUnsignedInt(index: Int): Long = getInt(index).toLong() and 0xFFFFFFFFL

    override fun getUnsignedIntLE(index: Int): Long = getIntLE(index).toLong() and 0xFFFFFFFFL

    override fun getLong(index: Int): Long {
        checkIndex(index, 8)
        return _getLong(index)
    }

    protected abstract fun _getLong(index: Int): Long

    override fun getLongLE(index: Int): Long {
        checkIndex(index, 8)
        return _getLongLE(index)
    }

    protected abstract fun _getLongLE(index: Int): Long

    override fun getChar(index: Int): Char = getShort(index).toInt().toChar()

    override fun getFloat(index: Int): Float = Float.fromBits(getInt(index))

    override fun getDouble(index: Int): Double = Double.fromBits(getLong(index))

    override fun getBytes(index: Int, dst: ByteArray): ByteBuf {
        getBytes(index, dst, 0, dst.size)
        return this
    }

    override fun getBytes(index: Int, dst: ByteBuf): ByteBuf {
        getBytes(index, dst, dst.writableBytes())
        return this
    }

    override fun getBytes(index: Int, dst: ByteBuf, length: Int): ByteBuf {
        getBytes(index, dst, dst.writerIndex(), length)
        dst.writerIndex(dst.writerIndex() + length)
        return this
    }

    override fun getCharSequence(index: Int, length: Int, charset: Charset): CharSequence {
        return if (CharsetUtil.US_ASCII == charset || CharsetUtil.ISO_8859_1 == charset) {
            // ByteBufUtil.getBytes(...) will return a new copy which the AsciiString uses directly
            AsciiString(ByteBufUtil.getBytes(this, index, length, true), false)
        } else {
            toString(index, length, charset)
        }
    }

    override fun readCharSequence(length: Int, charset: Charset): CharSequence {
        val sequence = getCharSequence(readerIndex, length, charset)
        readerIndex += length
        return sequence
    }

    override fun readString(length: Int, charset: Charset): String {
        val string = toString(readerIndex, length, charset)
        readerIndex += length
        return string
    }

    override fun setByte(index: Int, value: Int): ByteBuf {
        checkIndex(index)
        _setByte(index, value)
        return this
    }

    protected abstract fun _setByte(index: Int, value: Int)

    override fun setBoolean(index: Int, value: Boolean): ByteBuf {
        setByte(index, if (value) 1 else 0)
        return this
    }

    override fun setShort(index: Int, value: Int): ByteBuf {
        checkIndex(index, 2)
        _setShort(index, value)
        return this
    }

    protected abstract fun _setShort(index: Int, value: Int)

    override fun setShortLE(index: Int, value: Int): ByteBuf {
        checkIndex(index, 2)
        _setShortLE(index, value)
        return this
    }

    protected abstract fun _setShortLE(index: Int, value: Int)

    override fun setChar(index: Int, value: Int): ByteBuf {
        setShort(index, value)
        return this
    }

    override fun setMedium(index: Int, value: Int): ByteBuf {
        checkIndex(index, 3)
        _setMedium(index, value)
        return this
    }

    protected abstract fun _setMedium(index: Int, value: Int)

    override fun setMediumLE(index: Int, value: Int): ByteBuf {
        checkIndex(index, 3)
        _setMediumLE(index, value)
        return this
    }

    protected abstract fun _setMediumLE(index: Int, value: Int)

    override fun setInt(index: Int, value: Int): ByteBuf {
        checkIndex(index, 4)
        _setInt(index, value)
        return this
    }

    protected abstract fun _setInt(index: Int, value: Int)

    override fun setIntLE(index: Int, value: Int): ByteBuf {
        checkIndex(index, 4)
        _setIntLE(index, value)
        return this
    }

    protected abstract fun _setIntLE(index: Int, value: Int)

    override fun setFloat(index: Int, value: Float): ByteBuf {
        setInt(index, java.lang.Float.floatToRawIntBits(value))
        return this
    }

    override fun setLong(index: Int, value: Long): ByteBuf {
        checkIndex(index, 8)
        _setLong(index, value)
        return this
    }

    protected abstract fun _setLong(index: Int, value: Long)

    override fun setLongLE(index: Int, value: Long): ByteBuf {
        checkIndex(index, 8)
        _setLongLE(index, value)
        return this
    }

    protected abstract fun _setLongLE(index: Int, value: Long)

    // Internal bridge methods for cross-instance access from non-subclass Kotlin callers (e.g. ByteBufUtil)
    internal fun invoke_getByte(index: Int): Byte = _getByte(index)
    internal fun invoke_getShort(index: Int): Short = _getShort(index)
    internal fun invoke_getShortLE(index: Int): Short = _getShortLE(index)
    internal fun invoke_getUnsignedMedium(index: Int): Int = _getUnsignedMedium(index)
    internal fun invoke_getUnsignedMediumLE(index: Int): Int = _getUnsignedMediumLE(index)
    internal fun invoke_getInt(index: Int): Int = _getInt(index)
    internal fun invoke_getIntLE(index: Int): Int = _getIntLE(index)
    internal fun invoke_getLong(index: Int): Long = _getLong(index)
    internal fun invoke_getLongLE(index: Int): Long = _getLongLE(index)
    internal fun invoke_setByte(index: Int, value: Int) = _setByte(index, value)
    internal fun invoke_setShort(index: Int, value: Int) = _setShort(index, value)
    internal fun invoke_setShortLE(index: Int, value: Int) = _setShortLE(index, value)
    internal fun invoke_setMedium(index: Int, value: Int) = _setMedium(index, value)
    internal fun invoke_setMediumLE(index: Int, value: Int) = _setMediumLE(index, value)
    internal fun invoke_setInt(index: Int, value: Int) = _setInt(index, value)
    internal fun invoke_setIntLE(index: Int, value: Int) = _setIntLE(index, value)
    internal fun invoke_setLong(index: Int, value: Long) = _setLong(index, value)
    internal fun invoke_setLongLE(index: Int, value: Long) = _setLongLE(index, value)

    override fun setDouble(index: Int, value: Double): ByteBuf {
        setLong(index, java.lang.Double.doubleToRawLongBits(value))
        return this
    }

    override fun setBytes(index: Int, src: ByteArray): ByteBuf {
        setBytes(index, src, 0, src.size)
        return this
    }

    override fun setBytes(index: Int, src: ByteBuf): ByteBuf {
        setBytes(index, src, src.readableBytes())
        return this
    }

    override fun setBytes(index: Int, src: ByteBuf, length: Int): ByteBuf {
        checkIndex(index, length)
        ObjectUtil.checkNotNull(src, "src")
        if (checkBounds) {
            checkReadableBounds(src, length)
        }

        setBytes(index, src, src.readerIndex(), length)
        src.readerIndex(src.readerIndex() + length)
        return this
    }

    override fun setZero(index: Int, length: Int): ByteBuf {
        if (length == 0) {
            return this
        }

        checkIndex(index, length)

        var idx = index
        val nLong = length.ushr(3)
        val nBytes = length and 7
        for (i in nLong downTo 1) {
            _setLong(idx, 0)
            idx += 8
        }
        when {
            nBytes == 4 -> {
                _setInt(idx, 0)
                // Not need to update the index as we will not use it after this.
            }
            nBytes < 4 -> {
                for (i in nBytes downTo 1) {
                    _setByte(idx, 0)
                    idx++
                }
            }
            else -> {
                _setInt(idx, 0)
                idx += 4
                for (i in (nBytes - 4) downTo 1) {
                    _setByte(idx, 0)
                    idx++
                }
            }
        }
        return this
    }

    override fun setCharSequence(index: Int, sequence: CharSequence, charset: Charset): Int {
        return setCharSequence0(index, sequence, charset, false)
    }

    private fun setCharSequence0(index: Int, sequence: CharSequence, charset: Charset, expand: Boolean): Int {
        if (charset == CharsetUtil.UTF_8) {
            val length = ByteBufUtil.utf8MaxBytes(sequence)
            if (expand) {
                ensureWritable0(length)
                checkIndex0(index, length)
            } else {
                checkIndex(index, length)
            }
            return ByteBufUtil.writeUtf8(this, index, length, sequence, sequence.length)
        }
        if (charset == CharsetUtil.US_ASCII || charset == CharsetUtil.ISO_8859_1) {
            val length = sequence.length
            if (expand) {
                ensureWritable0(length)
                checkIndex0(index, length)
            } else {
                checkIndex(index, length)
            }
            return ByteBufUtil.writeAscii(this, index, sequence, length)
        }
        val bytes = sequence.toString().toByteArray(charset)
        if (expand) {
            ensureWritable0(bytes.size)
            // setBytes(...) will take care of checking the indices.
        }
        setBytes(index, bytes)
        return bytes.size
    }

    override fun readByte(): Byte {
        checkReadableBytes0(1)
        val i = readerIndex
        val b = _getByte(i)
        readerIndex = i + 1
        return b
    }

    override fun readBoolean(): Boolean = readByte().toInt() != 0

    override fun readUnsignedByte(): Short = (readByte().toInt() and 0xFF).toShort()

    override fun readShort(): Short {
        checkReadableBytes0(2)
        val v = _getShort(readerIndex)
        readerIndex += 2
        return v
    }

    override fun readShortLE(): Short {
        checkReadableBytes0(2)
        val v = _getShortLE(readerIndex)
        readerIndex += 2
        return v
    }

    override fun readUnsignedShort(): Int = readShort().toInt() and 0xFFFF

    override fun readUnsignedShortLE(): Int = readShortLE().toInt() and 0xFFFF

    override fun readMedium(): Int {
        var value = readUnsignedMedium()
        if (value and 0x800000 != 0) {
            value = value or 0xff000000.toInt()
        }
        return value
    }

    override fun readMediumLE(): Int {
        var value = readUnsignedMediumLE()
        if (value and 0x800000 != 0) {
            value = value or 0xff000000.toInt()
        }
        return value
    }

    override fun readUnsignedMedium(): Int {
        checkReadableBytes0(3)
        val v = _getUnsignedMedium(readerIndex)
        readerIndex += 3
        return v
    }

    override fun readUnsignedMediumLE(): Int {
        checkReadableBytes0(3)
        val v = _getUnsignedMediumLE(readerIndex)
        readerIndex += 3
        return v
    }

    override fun readInt(): Int {
        checkReadableBytes0(4)
        val v = _getInt(readerIndex)
        readerIndex += 4
        return v
    }

    override fun readIntLE(): Int {
        checkReadableBytes0(4)
        val v = _getIntLE(readerIndex)
        readerIndex += 4
        return v
    }

    override fun readUnsignedInt(): Long = readInt().toLong() and 0xFFFFFFFFL

    override fun readUnsignedIntLE(): Long = readIntLE().toLong() and 0xFFFFFFFFL

    override fun readLong(): Long {
        checkReadableBytes0(8)
        val v = _getLong(readerIndex)
        readerIndex += 8
        return v
    }

    override fun readLongLE(): Long {
        checkReadableBytes0(8)
        val v = _getLongLE(readerIndex)
        readerIndex += 8
        return v
    }

    override fun readChar(): Char = readShort().toInt().toChar()

    override fun readFloat(): Float = Float.fromBits(readInt())

    override fun readDouble(): Double = Double.fromBits(readLong())

    override fun readBytes(length: Int): ByteBuf {
        checkReadableBytes(length)
        if (length == 0) {
            return Unpooled.EMPTY_BUFFER
        }

        val buf = alloc().buffer(length, maxCapacity)
        buf.writeBytes(this, readerIndex, length)
        readerIndex += length
        return buf
    }

    override fun readSlice(length: Int): ByteBuf {
        checkReadableBytes(length)
        val slice = slice(readerIndex, length)
        readerIndex += length
        return slice
    }

    override fun readRetainedSlice(length: Int): ByteBuf {
        checkReadableBytes(length)
        val slice = retainedSlice(readerIndex, length)
        readerIndex += length
        return slice
    }

    override fun readBytes(dst: ByteArray, dstIndex: Int, length: Int): ByteBuf {
        checkReadableBytes(length)
        getBytes(readerIndex, dst, dstIndex, length)
        readerIndex += length
        return this
    }

    override fun readBytes(dst: ByteArray): ByteBuf {
        readBytes(dst, 0, dst.size)
        return this
    }

    override fun readBytes(dst: ByteBuf): ByteBuf {
        readBytes(dst, dst.writableBytes())
        return this
    }

    override fun readBytes(dst: ByteBuf, length: Int): ByteBuf {
        if (checkBounds) {
            if (length > dst.writableBytes()) {
                throw IndexOutOfBoundsException(
                    "length($length) exceeds dst.writableBytes(${dst.writableBytes()}) where dst is: $dst"
                )
            }
        }
        readBytes(dst, dst.writerIndex(), length)
        dst.writerIndex(dst.writerIndex() + length)
        return this
    }

    override fun readBytes(dst: ByteBuf, dstIndex: Int, length: Int): ByteBuf {
        checkReadableBytes(length)
        getBytes(readerIndex, dst, dstIndex, length)
        readerIndex += length
        return this
    }

    override fun readBytes(dst: ByteBuffer): ByteBuf {
        val length = dst.remaining()
        checkReadableBytes(length)
        getBytes(readerIndex, dst)
        readerIndex += length
        return this
    }

    @Throws(IOException::class)
    override fun readBytes(out: GatheringByteChannel, length: Int): Int {
        checkReadableBytes(length)
        val readBytes = getBytes(readerIndex, out, length)
        readerIndex += readBytes
        return readBytes
    }

    @Throws(IOException::class)
    override fun readBytes(out: FileChannel, position: Long, length: Int): Int {
        checkReadableBytes(length)
        val readBytes = getBytes(readerIndex, out, position, length)
        readerIndex += readBytes
        return readBytes
    }

    @Throws(IOException::class)
    override fun readBytes(out: OutputStream, length: Int): ByteBuf {
        checkReadableBytes(length)
        getBytes(readerIndex, out, length)
        readerIndex += length
        return this
    }

    override fun skipBytes(length: Int): ByteBuf {
        checkReadableBytes(length)
        readerIndex += length
        return this
    }

    override fun writeBoolean(value: Boolean): ByteBuf {
        writeByte(if (value) 1 else 0)
        return this
    }

    override fun writeByte(value: Int): ByteBuf {
        ensureWritable0(1)
        _setByte(writerIndex++, value)
        return this
    }

    override fun writeShort(value: Int): ByteBuf {
        ensureWritable0(2)
        _setShort(writerIndex, value)
        writerIndex += 2
        return this
    }

    override fun writeShortLE(value: Int): ByteBuf {
        ensureWritable0(2)
        _setShortLE(writerIndex, value)
        writerIndex += 2
        return this
    }

    override fun writeMedium(value: Int): ByteBuf {
        ensureWritable0(3)
        _setMedium(writerIndex, value)
        writerIndex += 3
        return this
    }

    override fun writeMediumLE(value: Int): ByteBuf {
        ensureWritable0(3)
        _setMediumLE(writerIndex, value)
        writerIndex += 3
        return this
    }

    override fun writeInt(value: Int): ByteBuf {
        ensureWritable0(4)
        _setInt(writerIndex, value)
        writerIndex += 4
        return this
    }

    override fun writeIntLE(value: Int): ByteBuf {
        ensureWritable0(4)
        _setIntLE(writerIndex, value)
        writerIndex += 4
        return this
    }

    override fun writeLong(value: Long): ByteBuf {
        ensureWritable0(8)
        _setLong(writerIndex, value)
        writerIndex += 8
        return this
    }

    override fun writeLongLE(value: Long): ByteBuf {
        ensureWritable0(8)
        _setLongLE(writerIndex, value)
        writerIndex += 8
        return this
    }

    override fun writeChar(value: Int): ByteBuf {
        writeShort(value)
        return this
    }

    override fun writeFloat(value: Float): ByteBuf {
        writeInt(java.lang.Float.floatToRawIntBits(value))
        return this
    }

    override fun writeDouble(value: Double): ByteBuf {
        writeLong(java.lang.Double.doubleToRawLongBits(value))
        return this
    }

    override fun writeBytes(src: ByteArray, srcIndex: Int, length: Int): ByteBuf {
        ensureWritable(length)
        setBytes(writerIndex, src, srcIndex, length)
        writerIndex += length
        return this
    }

    override fun writeBytes(src: ByteArray): ByteBuf {
        writeBytes(src, 0, src.size)
        return this
    }

    override fun writeBytes(src: ByteBuf): ByteBuf {
        writeBytes(src, src.readableBytes())
        return this
    }

    override fun writeBytes(src: ByteBuf, length: Int): ByteBuf {
        if (checkBounds) {
            checkReadableBounds(src, length)
        }
        writeBytes(src, src.readerIndex(), length)
        src.readerIndex(src.readerIndex() + length)
        return this
    }

    override fun writeBytes(src: ByteBuf, srcIndex: Int, length: Int): ByteBuf {
        ensureWritable(length)
        setBytes(writerIndex, src, srcIndex, length)
        writerIndex += length
        return this
    }

    override fun writeBytes(src: ByteBuffer): ByteBuf {
        val length = src.remaining()
        ensureWritable0(length)
        setBytes(writerIndex, src)
        writerIndex += length
        return this
    }

    @Throws(IOException::class)
    override fun writeBytes(`in`: InputStream, length: Int): Int {
        ensureWritable(length)
        val writtenBytes = setBytes(writerIndex, `in`, length)
        if (writtenBytes > 0) {
            writerIndex += writtenBytes
        }
        return writtenBytes
    }

    @Throws(IOException::class)
    override fun writeBytes(`in`: ScatteringByteChannel, length: Int): Int {
        ensureWritable(length)
        val writtenBytes = setBytes(writerIndex, `in`, length)
        if (writtenBytes > 0) {
            writerIndex += writtenBytes
        }
        return writtenBytes
    }

    @Throws(IOException::class)
    override fun writeBytes(`in`: FileChannel, position: Long, length: Int): Int {
        ensureWritable(length)
        val writtenBytes = setBytes(writerIndex, `in`, position, length)
        if (writtenBytes > 0) {
            writerIndex += writtenBytes
        }
        return writtenBytes
    }

    override fun writeZero(length: Int): ByteBuf {
        if (length == 0) {
            return this
        }

        ensureWritable(length)
        var wIndex = writerIndex
        checkIndex0(wIndex, length)

        val nLong = length.ushr(3)
        val nBytes = length and 7
        for (i in nLong downTo 1) {
            _setLong(wIndex, 0)
            wIndex += 8
        }
        when {
            nBytes == 4 -> {
                _setInt(wIndex, 0)
                wIndex += 4
            }
            nBytes < 4 -> {
                for (i in nBytes downTo 1) {
                    _setByte(wIndex, 0)
                    wIndex++
                }
            }
            else -> {
                _setInt(wIndex, 0)
                wIndex += 4
                for (i in (nBytes - 4) downTo 1) {
                    _setByte(wIndex, 0)
                    wIndex++
                }
            }
        }
        writerIndex = wIndex
        return this
    }

    override fun writeCharSequence(sequence: CharSequence, charset: Charset): Int {
        val written = setCharSequence0(writerIndex, sequence, charset, true)
        writerIndex += written
        return written
    }

    override fun copy(): ByteBuf = copy(readerIndex, readableBytes())

    override fun duplicate(): ByteBuf {
        ensureAccessible()
        return UnpooledDuplicatedByteBuf(this)
    }

    override fun retainedDuplicate(): ByteBuf = duplicate().retain()

    override fun slice(): ByteBuf = slice(readerIndex, readableBytes())

    override fun retainedSlice(): ByteBuf = slice().retain()

    override fun slice(index: Int, length: Int): ByteBuf {
        ensureAccessible()
        return UnpooledSlicedByteBuf(this, index, length)
    }

    override fun retainedSlice(index: Int, length: Int): ByteBuf = slice(index, length).retain()

    override fun nioBuffer(): ByteBuffer = nioBuffer(readerIndex, readableBytes())

    override fun nioBuffers(): Array<ByteBuffer> = nioBuffers(readerIndex, readableBytes())

    override fun toString(charset: Charset): String = toString(readerIndex, readableBytes(), charset)

    override fun toString(index: Int, length: Int, charset: Charset): String =
        ByteBufUtil.decodeString(this, index, length, charset)

    override fun indexOf(fromIndex: Int, toIndex: Int, value: Byte): Int {
        return if (fromIndex <= toIndex) {
            ByteBufUtil.firstIndexOf(this, fromIndex, toIndex, value)
        } else {
            ByteBufUtil.lastIndexOf(this, fromIndex, toIndex, value)
        }
    }

    override fun bytesBefore(value: Byte): Int = bytesBefore(readerIndex(), readableBytes(), value)

    override fun bytesBefore(length: Int, value: Byte): Int {
        checkReadableBytes(length)
        return bytesBefore(readerIndex(), length, value)
    }

    override fun bytesBefore(index: Int, length: Int, value: Byte): Int {
        val endIndex = indexOf(index, index + length, value)
        return if (endIndex < 0) -1 else endIndex - index
    }

    override fun forEachByte(processor: ByteProcessor): Int {
        ensureAccessible()
        return try {
            forEachByteAsc0(readerIndex, writerIndex, processor)
        } catch (e: Exception) {
            PlatformDependent.throwException(e)
            -1
        }
    }

    override fun forEachByte(index: Int, length: Int, processor: ByteProcessor): Int {
        checkIndex(index, length)
        return try {
            forEachByteAsc0(index, index + length, processor)
        } catch (e: Exception) {
            PlatformDependent.throwException(e)
            -1
        }
    }

    @Throws(Exception::class)
    internal open fun forEachByteAsc0(start: Int, end: Int, processor: ByteProcessor): Int {
        var s = start
        while (s < end) {
            if (!processor.process(_getByte(s))) {
                return s
            }
            ++s
        }
        return -1
    }

    override fun forEachByteDesc(processor: ByteProcessor): Int {
        ensureAccessible()
        return try {
            forEachByteDesc0(writerIndex - 1, readerIndex, processor)
        } catch (e: Exception) {
            PlatformDependent.throwException(e)
            -1
        }
    }

    override fun forEachByteDesc(index: Int, length: Int, processor: ByteProcessor): Int {
        checkIndex(index, length)
        return try {
            forEachByteDesc0(index + length - 1, index, processor)
        } catch (e: Exception) {
            PlatformDependent.throwException(e)
            -1
        }
    }

    @Throws(Exception::class)
    internal open fun forEachByteDesc0(rStart: Int, rEnd: Int, processor: ByteProcessor): Int {
        var s = rStart
        while (s >= rEnd) {
            if (!processor.process(_getByte(s))) {
                return s
            }
            --s
        }
        return -1
    }

    override fun hashCode(): Int = ByteBufUtil.hashCode(this)

    override fun equals(other: Any?): Boolean = other is ByteBuf && ByteBufUtil.equals(this, other)

    override fun compareTo(other: ByteBuf): Int = ByteBufUtil.compare(this, other)

    override fun toString(): String {
        if (refCnt() == 0) {
            return "${StringUtil.simpleClassName(this)}(freed)"
        }

        val buf = StringBuilder()
            .append(StringUtil.simpleClassName(this))
            .append("(ridx: ").append(readerIndex)
            .append(", widx: ").append(writerIndex)
            .append(", cap: ").append(capacity())
        if (maxCapacity != Int.MAX_VALUE) {
            buf.append('/').append(maxCapacity)
        }

        val unwrapped = unwrap()
        if (unwrapped != null) {
            buf.append(", unwrapped: ").append(unwrapped)
        }
        buf.append(')')
        return buf.toString()
    }

    internal fun checkIndex(index: Int) {
        checkIndex(index, 1)
    }

    internal fun checkIndex(index: Int, fieldLength: Int) {
        ensureAccessible()
        checkIndex0(index, fieldLength)
    }

    internal fun checkIndex0(index: Int, fieldLength: Int) {
        if (checkBounds) {
            checkRangeBoundsTrustedCapacity("index", index, fieldLength, capacity())
        }
    }

    protected fun checkSrcIndex(index: Int, length: Int, srcIndex: Int, srcCapacity: Int) {
        checkIndex(index, length)
        if (checkBounds) {
            checkRangeBounds("srcIndex", srcIndex, length, srcCapacity)
        }
    }

    protected fun checkDstIndex(index: Int, length: Int, dstIndex: Int, dstCapacity: Int) {
        checkIndex(index, length)
        if (checkBounds) {
            checkRangeBounds("dstIndex", dstIndex, length, dstCapacity)
        }
    }

    protected fun checkDstIndex(length: Int, dstIndex: Int, dstCapacity: Int) {
        checkReadableBytes(length)
        if (checkBounds) {
            checkRangeBounds("dstIndex", dstIndex, length, dstCapacity)
        }
    }

    /**
     * Throws an [IndexOutOfBoundsException] if the current
     * [readable bytes][readableBytes] of this buffer is less
     * than the specified value.
     */
    protected fun checkReadableBytes(minimumReadableBytes: Int) {
        checkReadableBytes0(checkPositiveOrZero(minimumReadableBytes, "minimumReadableBytes"))
    }

    protected fun checkNewCapacity(newCapacity: Int) {
        ensureAccessible()
        if (checkBounds && (newCapacity < 0 || newCapacity > maxCapacity())) {
            throw IllegalArgumentException("newCapacity: $newCapacity (expected: 0-${maxCapacity()})")
        }
    }

    private fun checkReadableBytes0(minimumReadableBytes: Int) {
        ensureAccessible()
        if (checkBounds && readerIndex > writerIndex - minimumReadableBytes) {
            throw IndexOutOfBoundsException(
                "readerIndex($readerIndex) + length($minimumReadableBytes) exceeds writerIndex($writerIndex): $this"
            )
        }
    }

    /**
     * Should be called by every method that tries to access the buffers content to check
     * if the buffer was released before.
     */
    protected fun ensureAccessible() {
        if (checkAccessible && !isAccessible()) {
            throw IllegalReferenceCountException(0)
        }
    }

    internal fun setIndex0(readerIndex: Int, writerIndex: Int) {
        this.readerIndex = readerIndex
        this.writerIndex = writerIndex
    }

    internal fun discardMarks() {
        markedWriterIndex = 0
        markedReaderIndex = 0
    }

    /**
     * Obtain the memory address without checking [ensureAccessible] first, if possible.
     */
    internal open fun _memoryAddress(): Long = if (isAccessible() && hasMemoryAddress()) memoryAddress() else 0L

    internal open fun _internalNioBuffer(): ByteBuffer = internalNioBuffer(0, maxFastWritableBytes())

    companion object {
        private val logger = InternalLoggerFactory.getInstance(AbstractByteBuf::class.java)
        private const val LEGACY_PROP_CHECK_ACCESSIBLE = "io.netty.buffer.bytebuf.checkAccessible"
        private const val PROP_CHECK_ACCESSIBLE = "io.netty.buffer.checkAccessible"
        private const val PROP_CHECK_BOUNDS = "io.netty.buffer.checkBounds"

        @JvmStatic
        internal val checkAccessible: Boolean // accessed from CompositeByteBuf

        @JvmStatic
        private val checkBounds: Boolean

        init {
            checkAccessible = if (SystemPropertyUtil.contains(PROP_CHECK_ACCESSIBLE)) {
                SystemPropertyUtil.getBoolean(PROP_CHECK_ACCESSIBLE, true)
            } else {
                SystemPropertyUtil.getBoolean(LEGACY_PROP_CHECK_ACCESSIBLE, true)
            }
            checkBounds = SystemPropertyUtil.getBoolean(PROP_CHECK_BOUNDS, true)
            if (logger.isDebugEnabled()) {
                logger.debug("-D{}: {}", PROP_CHECK_ACCESSIBLE, checkAccessible)
                logger.debug("-D{}: {}", PROP_CHECK_BOUNDS, checkBounds)
            }
        }

        @JvmField
        val leakDetector: ResourceLeakDetector<ByteBuf> =
            ResourceLeakDetectorFactory.instance().newResourceLeakDetector(ByteBuf::class.java)

        private fun checkIndexBounds(readerIndex: Int, writerIndex: Int, capacity: Int) {
            if (readerIndex < 0 || readerIndex > writerIndex || writerIndex > capacity) {
                throw IndexOutOfBoundsException(
                    "readerIndex: $readerIndex, writerIndex: $writerIndex (expected: 0 <= readerIndex <= writerIndex <= capacity($capacity))"
                )
            }
        }

        private fun checkReadableBounds(src: ByteBuf, length: Int) {
            if (length > src.readableBytes()) {
                throw IndexOutOfBoundsException(
                    "length($length) exceeds src.readableBytes(${src.readableBytes()}) where src is: $src"
                )
            }
        }

        private fun checkRangeBoundsTrustedCapacity(indexName: String, index: Int, fieldLength: Int, capacity: Int) {
            if (isOutOfBoundsTrustedCapacity(index, fieldLength, capacity)) {
                rangeBoundsCheckFailed(indexName, index, fieldLength, capacity)
            }
        }

        /**
         * This is a simplified version of MathUtil.isOutOfBounds that does not check for capacity negative values.
         */
        private fun isOutOfBoundsTrustedCapacity(index: Int, fieldLength: Int, capacity: Int): Boolean {
            // keep these as branches since would make it easier to be constant-folded
            return index < 0 || fieldLength < 0 || index + fieldLength < 0 || index + fieldLength > capacity
        }

        private fun checkRangeBounds(indexName: String, index: Int, fieldLength: Int, capacity: Int) {
            if (isOutOfBounds(index, fieldLength, capacity)) {
                rangeBoundsCheckFailed(indexName, index, fieldLength, capacity)
            }
        }

        private fun rangeBoundsCheckFailed(indexName: String, index: Int, fieldLength: Int, capacity: Int) {
            throw IndexOutOfBoundsException(
                "$indexName: $index, length: $fieldLength (expected: range(0, $capacity))"
            )
        }
    }
}
