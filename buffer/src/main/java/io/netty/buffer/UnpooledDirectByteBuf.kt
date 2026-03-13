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

import io.netty.util.internal.CleanableDirectBuffer
import io.netty.util.internal.ObjectUtil
import io.netty.util.internal.ObjectUtil.checkPositiveOrZero
import io.netty.util.internal.PlatformDependent

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.ClosedChannelException
import java.nio.channels.FileChannel
import java.nio.channels.GatheringByteChannel
import java.nio.channels.ScatteringByteChannel

/**
 * A NIO [ByteBuffer] based buffer. It is recommended to use
 * [UnpooledByteBufAllocator.directBuffer], [Unpooled.directBuffer] and
 * [Unpooled.wrappedBuffer] instead of calling the constructor explicitly.
 */
open class UnpooledDirectByteBuf : AbstractReferenceCountedByteBuf {

    private val alloc: ByteBufAllocator

    @JvmField
    var cleanable: CleanableDirectBuffer? = null

    @JvmField
    var buffer: ByteBuffer? = null // accessed by UnpooledUnsafeNoCleanerDirectByteBuf.reallocateDirect()

    private var tmpNioBuf: ByteBuffer? = null
    private var _capacity: Int = 0
    private var doNotFree: Boolean = false

    /**
     * Creates a new direct buffer.
     *
     * @param initialCapacity the initial capacity of the underlying direct buffer
     * @param maxCapacity     the maximum capacity of the underlying direct buffer
     */
    constructor(alloc: ByteBufAllocator, initialCapacity: Int, maxCapacity: Int) : super(maxCapacity) {
        ObjectUtil.checkNotNull(alloc, "alloc")
        checkPositiveOrZero(initialCapacity, "initialCapacity")
        checkPositiveOrZero(maxCapacity, "maxCapacity")
        require(initialCapacity <= maxCapacity) {
            "initialCapacity($initialCapacity) > maxCapacity($maxCapacity)"
        }

        this.alloc = alloc
        setByteBuffer(allocateDirectBuffer(initialCapacity), false)
    }

    /**
     * Creates a new direct buffer by wrapping the specified initial buffer.
     *
     * @param maxCapacity the maximum capacity of the underlying direct buffer
     */
    internal constructor(alloc: ByteBufAllocator, initialBuffer: ByteBuffer, maxCapacity: Int)
            : this(alloc, initialBuffer, maxCapacity, false, true)

    internal constructor(
        alloc: ByteBufAllocator, initialBuffer: ByteBuffer,
        maxCapacity: Int, doFree: Boolean, slice: Boolean
    ) : super(maxCapacity) {
        ObjectUtil.checkNotNull(alloc, "alloc")
        ObjectUtil.checkNotNull(initialBuffer, "initialBuffer")
        require(initialBuffer.isDirect) { "initialBuffer is not a direct buffer." }
        require(!initialBuffer.isReadOnly) { "initialBuffer is a read-only buffer." }

        val initialCapacity = initialBuffer.remaining()
        require(initialCapacity <= maxCapacity) {
            "initialCapacity($initialCapacity) > maxCapacity($maxCapacity)"
        }

        this.alloc = alloc
        doNotFree = !doFree
        setByteBuffer(
            (if (slice) initialBuffer.slice() else initialBuffer).order(ByteOrder.BIG_ENDIAN),
            false
        )
        writerIndex(initialCapacity)
    }

    /**
     * Allocate a new direct [ByteBuffer] with the given initialCapacity.
     * @deprecated Use [allocateDirectBuffer] instead.
     */
    @Deprecated("Use allocateDirectBuffer(int) instead.")
    protected open fun allocateDirect(initialCapacity: Int): ByteBuffer {
        return ByteBuffer.allocateDirect(initialCapacity)
    }

    /**
     * Free a direct [ByteBuffer]
     * @deprecated Use [allocateDirectBuffer] instead.
     */
    @Deprecated("Use allocateDirectBuffer(int) instead.")
    protected open fun freeDirect(buffer: ByteBuffer) {
        PlatformDependent.freeDirectBuffer(buffer)
    }

    protected open fun allocateDirectBuffer(capacity: Int): CleanableDirectBuffer {
        return PlatformDependent.allocateDirect(capacity)
    }

    internal open fun setByteBuffer(cleanableDirectBuffer: CleanableDirectBuffer, tryFree: Boolean) {
        if (tryFree) {
            val oldCleanable = cleanable
            val oldBuffer = buffer
            if (oldBuffer != null) {
                if (doNotFree) {
                    doNotFree = false
                } else {
                    if (oldCleanable != null) {
                        oldCleanable.clean()
                    } else {
                        @Suppress("DEPRECATION")
                        freeDirect(oldBuffer)
                    }
                }
            }
        }

        cleanable = cleanableDirectBuffer
        buffer = cleanableDirectBuffer.buffer()
        tmpNioBuf = null
        _capacity = buffer!!.remaining()
    }

    internal open fun setByteBuffer(buffer: ByteBuffer, tryFree: Boolean) {
        if (tryFree) {
            val oldBuffer = this.buffer
            if (oldBuffer != null) {
                if (doNotFree) {
                    doNotFree = false
                } else {
                    @Suppress("DEPRECATION")
                    freeDirect(oldBuffer)
                }
            }
        }

        this.buffer = buffer
        tmpNioBuf = null
        _capacity = buffer.remaining()
    }

    override fun isDirect(): Boolean = true

    override fun capacity(): Int = _capacity

    override fun capacity(newCapacity: Int): ByteBuf {
        checkNewCapacity(newCapacity)
        val oldCapacity = _capacity
        if (newCapacity == oldCapacity) {
            return this
        }
        val bytesToCopy: Int
        if (newCapacity > oldCapacity) {
            bytesToCopy = oldCapacity
        } else {
            trimIndicesToCapacity(newCapacity)
            bytesToCopy = newCapacity
        }
        val oldBuffer = buffer!!
        val newBuffer = allocateDirectBuffer(newCapacity)
        oldBuffer.position(0).limit(bytesToCopy)
        newBuffer.buffer().position(0).limit(bytesToCopy)
        newBuffer.buffer().put(oldBuffer).clear()
        setByteBuffer(newBuffer, true)
        return this
    }

    override fun alloc(): ByteBufAllocator = alloc

    override fun order(): ByteOrder = ByteOrder.BIG_ENDIAN

    override fun hasArray(): Boolean = false

    override fun array(): ByteArray {
        throw UnsupportedOperationException("direct buffer")
    }

    override fun arrayOffset(): Int {
        throw UnsupportedOperationException("direct buffer")
    }

    override fun hasMemoryAddress(): Boolean {
        val cleanable = this.cleanable
        return cleanable != null && cleanable.hasMemoryAddress()
    }

    override fun memoryAddress(): Long {
        ensureAccessible()
        check(hasMemoryAddress())
        return cleanable!!.memoryAddress()
    }

    override fun getByte(index: Int): Byte {
        ensureAccessible()
        return _getByte(index)
    }

    override fun _getByte(index: Int): Byte = buffer!!.get(index)

    override fun getShortLE(index: Int): Short {
        ensureAccessible()
        return _getShortLE(index)
    }

    override fun getShort(index: Int): Short {
        ensureAccessible()
        return _getShort(index)
    }

    override fun _getShort(index: Int): Short {
        if (PlatformDependent.hasVarHandle()) {
            return VarHandleByteBufferAccess.getShortBE(buffer!!, index)
        }
        return buffer!!.getShort(index)
    }

    override fun _getShortLE(index: Int): Short {
        if (PlatformDependent.hasVarHandle()) {
            return VarHandleByteBufferAccess.getShortLE(buffer!!, index)
        }
        return ByteBufUtil.swapShort(buffer!!.getShort(index))
    }

    override fun getUnsignedMedium(index: Int): Int {
        ensureAccessible()
        return _getUnsignedMedium(index)
    }

    override fun _getUnsignedMedium(index: Int): Int {
        return (getByte(index).toInt() and 0xff shl 16) or
                (getByte(index + 1).toInt() and 0xff shl 8) or
                (getByte(index + 2).toInt() and 0xff)
    }

    override fun _getUnsignedMediumLE(index: Int): Int {
        return (getByte(index).toInt() and 0xff) or
                (getByte(index + 1).toInt() and 0xff shl 8) or
                (getByte(index + 2).toInt() and 0xff shl 16)
    }

    override fun getIntLE(index: Int): Int {
        ensureAccessible()
        return _getIntLE(index)
    }

    override fun getInt(index: Int): Int {
        ensureAccessible()
        return _getInt(index)
    }

    override fun _getInt(index: Int): Int {
        if (PlatformDependent.hasVarHandle()) {
            return VarHandleByteBufferAccess.getIntBE(buffer!!, index)
        }
        return buffer!!.getInt(index)
    }

    override fun _getIntLE(index: Int): Int {
        if (PlatformDependent.hasVarHandle()) {
            return VarHandleByteBufferAccess.getIntLE(buffer!!, index)
        }
        return ByteBufUtil.swapInt(buffer!!.getInt(index))
    }

    override fun getLongLE(index: Int): Long {
        ensureAccessible()
        return _getLongLE(index)
    }

    override fun getLong(index: Int): Long {
        ensureAccessible()
        return _getLong(index)
    }

    override fun _getLong(index: Int): Long {
        if (PlatformDependent.hasVarHandle()) {
            return VarHandleByteBufferAccess.getLongBE(buffer!!, index)
        }
        return buffer!!.getLong(index)
    }

    override fun _getLongLE(index: Int): Long {
        if (PlatformDependent.hasVarHandle()) {
            return VarHandleByteBufferAccess.getLongLE(buffer!!, index)
        }
        return ByteBufUtil.swapLong(buffer!!.getLong(index))
    }

    override fun getBytes(index: Int, dst: ByteBuf, dstIndex: Int, length: Int): ByteBuf {
        checkDstIndex(index, length, dstIndex, dst.capacity())
        if (dst.hasArray()) {
            getBytes(index, dst.array(), dst.arrayOffset() + dstIndex, length)
        } else if (dst.nioBufferCount() > 0) {
            var idx = index
            for (bb in dst.nioBuffers(dstIndex, length)) {
                val bbLen = bb.remaining()
                getBytes(idx, bb)
                idx += bbLen
            }
        } else {
            dst.setBytes(dstIndex, this, index, length)
        }
        return this
    }

    override fun getBytes(index: Int, dst: ByteArray, dstIndex: Int, length: Int): ByteBuf {
        getBytes(index, dst, dstIndex, length, false)
        return this
    }

    internal open fun getBytes(index: Int, dst: ByteArray, dstIndex: Int, length: Int, internal: Boolean) {
        checkDstIndex(index, length, dstIndex, dst.size)

        val tmpBuf: ByteBuffer = if (internal) {
            internalNioBuffer(index, length)
        } else {
            buffer!!.duplicate().clear().position(index).limit(index + length) as ByteBuffer
        }
        tmpBuf.get(dst, dstIndex, length)
    }

    override fun readBytes(dst: ByteArray, dstIndex: Int, length: Int): ByteBuf {
        checkReadableBytes(length)
        getBytes(readerIndex, dst, dstIndex, length, true)
        readerIndex += length
        return this
    }

    override fun getBytes(index: Int, dst: ByteBuffer): ByteBuf {
        getBytes(index, dst, false)
        return this
    }

    internal open fun getBytes(index: Int, dst: ByteBuffer, internal: Boolean) {
        checkIndex(index, dst.remaining())

        val tmpBuf: ByteBuffer = if (internal) {
            internalNioBuffer(index, dst.remaining())
        } else {
            buffer!!.duplicate().clear().position(index).limit(index + dst.remaining()) as ByteBuffer
        }
        dst.put(tmpBuf)
    }

    override fun readBytes(dst: ByteBuffer): ByteBuf {
        val length = dst.remaining()
        checkReadableBytes(length)
        getBytes(readerIndex, dst, true)
        readerIndex += length
        return this
    }

    override fun setByte(index: Int, value: Int): ByteBuf {
        ensureAccessible()
        _setByte(index, value)
        return this
    }

    override fun _setByte(index: Int, value: Int) {
        buffer!!.put(index, (value and 0xFF).toByte())
    }

    override fun setShortLE(index: Int, value: Int): ByteBuf {
        ensureAccessible()
        _setShortLE(index, value)
        return this
    }

    override fun setShort(index: Int, value: Int): ByteBuf {
        ensureAccessible()
        _setShort(index, value)
        return this
    }

    override fun _setShort(index: Int, value: Int) {
        if (PlatformDependent.hasVarHandle()) {
            VarHandleByteBufferAccess.setShortBE(buffer!!, index, value)
            return
        }
        buffer!!.putShort(index, (value and 0xFFFF).toShort())
    }

    override fun _setShortLE(index: Int, value: Int) {
        if (PlatformDependent.hasVarHandle()) {
            VarHandleByteBufferAccess.setShortLE(buffer!!, index, value)
            return
        }
        buffer!!.putShort(index, ByteBufUtil.swapShort(value.toShort()))
    }

    override fun setMediumLE(index: Int, value: Int): ByteBuf {
        ensureAccessible()
        _setMediumLE(index, value)
        return this
    }

    override fun setMedium(index: Int, value: Int): ByteBuf {
        ensureAccessible()
        _setMedium(index, value)
        return this
    }

    override fun _setMedium(index: Int, value: Int) {
        setByte(index, (value ushr 16).toByte().toInt())
        setByte(index + 1, (value ushr 8).toByte().toInt())
        setByte(index + 2, value.toByte().toInt())
    }

    override fun _setMediumLE(index: Int, value: Int) {
        setByte(index, value.toByte().toInt())
        setByte(index + 1, (value ushr 8).toByte().toInt())
        setByte(index + 2, (value ushr 16).toByte().toInt())
    }

    override fun setIntLE(index: Int, value: Int): ByteBuf {
        ensureAccessible()
        _setIntLE(index, value)
        return this
    }

    override fun setInt(index: Int, value: Int): ByteBuf {
        ensureAccessible()
        _setInt(index, value)
        return this
    }

    override fun _setInt(index: Int, value: Int) {
        if (PlatformDependent.hasVarHandle()) {
            VarHandleByteBufferAccess.setIntBE(buffer!!, index, value)
            return
        }
        buffer!!.putInt(index, value)
    }

    override fun _setIntLE(index: Int, value: Int) {
        if (PlatformDependent.hasVarHandle()) {
            VarHandleByteBufferAccess.setIntLE(buffer!!, index, value)
            return
        }
        buffer!!.putInt(index, ByteBufUtil.swapInt(value))
    }

    override fun setLong(index: Int, value: Long): ByteBuf {
        ensureAccessible()
        _setLong(index, value)
        return this
    }

    override fun setLongLE(index: Int, value: Long): ByteBuf {
        ensureAccessible()
        _setLongLE(index, value)
        return this
    }

    override fun _setLong(index: Int, value: Long) {
        if (PlatformDependent.hasVarHandle()) {
            VarHandleByteBufferAccess.setLongBE(buffer!!, index, value)
            return
        }
        buffer!!.putLong(index, value)
    }

    override fun _setLongLE(index: Int, value: Long) {
        if (PlatformDependent.hasVarHandle()) {
            VarHandleByteBufferAccess.setLongLE(buffer!!, index, value)
            return
        }
        buffer!!.putLong(index, ByteBufUtil.swapLong(value))
    }

    override fun setBytes(index: Int, src: ByteBuf, srcIndex: Int, length: Int): ByteBuf {
        checkSrcIndex(index, length, srcIndex, src.capacity())
        if (src.nioBufferCount() > 0) {
            var idx = index
            for (bb in src.nioBuffers(srcIndex, length)) {
                val bbLen = bb.remaining()
                setBytes(idx, bb)
                idx += bbLen
            }
        } else {
            src.getBytes(srcIndex, this, index, length)
        }
        return this
    }

    override fun setBytes(index: Int, src: ByteArray, srcIndex: Int, length: Int): ByteBuf {
        checkSrcIndex(index, length, srcIndex, src.size)
        val tmpBuf = internalNioBuffer(index, length)
        tmpBuf.put(src, srcIndex, length)
        return this
    }

    override fun setBytes(index: Int, src: ByteBuffer): ByteBuf {
        ensureAccessible()
        val srcToUse = if (src === tmpNioBuf) src.duplicate() else src
        val tmpBuf = internalNioBuffer(index, srcToUse.remaining())
        tmpBuf.put(srcToUse)
        return this
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: OutputStream, length: Int): ByteBuf {
        getBytes(index, out, length, false)
        return this
    }

    @Throws(IOException::class)
    internal open fun getBytes(index: Int, out: OutputStream, length: Int, internal: Boolean) {
        ensureAccessible()
        if (length == 0) {
            return
        }
        ByteBufUtil.readBytes(
            alloc(), if (internal) _internalNioBuffer() else buffer!!.duplicate(), index, length, out
        )
    }

    @Throws(IOException::class)
    override fun readBytes(out: OutputStream, length: Int): ByteBuf {
        checkReadableBytes(length)
        getBytes(readerIndex, out, length, true)
        readerIndex += length
        return this
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: GatheringByteChannel, length: Int): Int {
        return getBytes(index, out, length, false)
    }

    @Throws(IOException::class)
    private fun getBytes(index: Int, out: GatheringByteChannel, length: Int, internal: Boolean): Int {
        ensureAccessible()
        if (length == 0) {
            return 0
        }

        val tmpBuf: ByteBuffer = if (internal) {
            internalNioBuffer(index, length)
        } else {
            buffer!!.duplicate().clear().position(index).limit(index + length) as ByteBuffer
        }
        return out.write(tmpBuf)
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: FileChannel, position: Long, length: Int): Int {
        return getBytes(index, out, position, length, false)
    }

    @Throws(IOException::class)
    private fun getBytes(index: Int, out: FileChannel, position: Long, length: Int, internal: Boolean): Int {
        ensureAccessible()
        if (length == 0) {
            return 0
        }

        val tmpBuf: ByteBuffer = if (internal) {
            internalNioBuffer(index, length)
        } else {
            buffer!!.duplicate().clear().position(index).limit(index + length) as ByteBuffer
        }
        return out.write(tmpBuf, position)
    }

    @Throws(IOException::class)
    override fun readBytes(out: GatheringByteChannel, length: Int): Int {
        checkReadableBytes(length)
        val readBytes = getBytes(readerIndex, out, length, true)
        readerIndex += readBytes
        return readBytes
    }

    @Throws(IOException::class)
    override fun readBytes(out: FileChannel, position: Long, length: Int): Int {
        checkReadableBytes(length)
        val readBytes = getBytes(readerIndex, out, position, length, true)
        readerIndex += readBytes
        return readBytes
    }

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: InputStream, length: Int): Int {
        ensureAccessible()
        val buf = buffer!!
        if (buf.hasArray()) {
            return `in`.read(buf.array(), buf.arrayOffset() + index, length)
        } else {
            val tmp = ByteBufUtil.threadLocalTempArray(length)
            val readBytes = `in`.read(tmp, 0, length)
            if (readBytes <= 0) {
                return readBytes
            }
            val tmpBuf = internalNioBuffer(index, readBytes)
            tmpBuf.put(tmp, 0, readBytes)
            return readBytes
        }
    }

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: ScatteringByteChannel, length: Int): Int {
        ensureAccessible()
        val tmpBuf = internalNioBuffer(index, length)
        return try {
            `in`.read(tmpBuf)
        } catch (ignored: ClosedChannelException) {
            -1
        }
    }

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: FileChannel, position: Long, length: Int): Int {
        ensureAccessible()
        val tmpBuf = internalNioBuffer(index, length)
        return try {
            `in`.read(tmpBuf, position)
        } catch (ignored: ClosedChannelException) {
            -1
        }
    }

    override fun nioBufferCount(): Int = 1

    override fun nioBuffers(index: Int, length: Int): Array<ByteBuffer> {
        return arrayOf(nioBuffer(index, length))
    }

    final override fun isContiguous(): Boolean = true

    override fun copy(index: Int, length: Int): ByteBuf {
        ensureAccessible()
        val src: ByteBuffer
        try {
            src = buffer!!.duplicate().clear().position(index).limit(index + length) as ByteBuffer
        } catch (ignored: IllegalArgumentException) {
            throw IndexOutOfBoundsException("Too many bytes to read - Need ${index + length}")
        }

        return alloc().directBuffer(length, maxCapacity()).writeBytes(src)
    }

    override fun internalNioBuffer(index: Int, length: Int): ByteBuffer {
        checkIndex(index, length)
        return _internalNioBuffer().clear().position(index).limit(index + length) as ByteBuffer
    }

    override fun _internalNioBuffer(): ByteBuffer {
        var tmpNioBuf = this.tmpNioBuf
        if (tmpNioBuf == null) {
            tmpNioBuf = buffer!!.duplicate()
            this.tmpNioBuf = tmpNioBuf
        }
        return tmpNioBuf
    }

    override fun nioBuffer(index: Int, length: Int): ByteBuffer {
        checkIndex(index, length)
        return PlatformDependent.offsetSlice(buffer!!, index, length)
    }

    override fun deallocate() {
        val buffer = this.buffer ?: return

        this.buffer = null

        if (!doNotFree) {
            val cleanable = this.cleanable
            if (cleanable != null) {
                cleanable.clean()
                this.cleanable = null
            } else {
                @Suppress("DEPRECATION")
                freeDirect(buffer)
            }
        }
    }

    override fun unwrap(): ByteBuf? = null
}
