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
package io.netty.buffer

import io.netty.util.ByteProcessor
import io.netty.util.ResourceLeakDetector
import io.netty.util.ResourceLeakTracker
import io.netty.util.internal.SystemPropertyUtil
import io.netty.util.internal.logging.InternalLoggerFactory
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.channels.GatheringByteChannel
import java.nio.channels.ScatteringByteChannel
import java.nio.charset.Charset

internal class AdvancedLeakAwareByteBuf : SimpleLeakAwareByteBuf {

    internal constructor(buf: ByteBuf, leak: ResourceLeakTracker<ByteBuf>) : super(buf, leak)

    internal constructor(wrapped: ByteBuf, trackedByteBuf: ByteBuf, leak: ResourceLeakTracker<ByteBuf>) : super(wrapped, trackedByteBuf, leak)

    override fun order(endianness: ByteOrder): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.order(endianness)
    }

    override fun slice(): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.slice()
    }

    override fun slice(index: Int, length: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.slice(index, length)
    }

    override fun retainedSlice(): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.retainedSlice()
    }

    override fun retainedSlice(index: Int, length: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.retainedSlice(index, length)
    }

    override fun retainedDuplicate(): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.retainedDuplicate()
    }

    override fun readRetainedSlice(length: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.readRetainedSlice(length)
    }

    override fun duplicate(): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.duplicate()
    }

    override fun readSlice(length: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.readSlice(length)
    }

    override fun discardReadBytes(): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.discardReadBytes()
    }

    override fun discardSomeReadBytes(): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.discardSomeReadBytes()
    }

    override fun ensureWritable(minWritableBytes: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.ensureWritable(minWritableBytes)
    }

    override fun ensureWritable(minWritableBytes: Int, force: Boolean): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.ensureWritable(minWritableBytes, force)
    }

    override fun getBoolean(index: Int): Boolean {
        recordLeakNonRefCountingOperation(leak)
        return super.getBoolean(index)
    }

    override fun getByte(index: Int): Byte {
        recordLeakNonRefCountingOperation(leak)
        return super.getByte(index)
    }

    override fun getUnsignedByte(index: Int): Short {
        recordLeakNonRefCountingOperation(leak)
        return super.getUnsignedByte(index)
    }

    override fun getShort(index: Int): Short {
        recordLeakNonRefCountingOperation(leak)
        return super.getShort(index)
    }

    override fun getUnsignedShort(index: Int): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.getUnsignedShort(index)
    }

    override fun getMedium(index: Int): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.getMedium(index)
    }

    override fun getUnsignedMedium(index: Int): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.getUnsignedMedium(index)
    }

    override fun getInt(index: Int): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.getInt(index)
    }

    override fun getUnsignedInt(index: Int): Long {
        recordLeakNonRefCountingOperation(leak)
        return super.getUnsignedInt(index)
    }

    override fun getLong(index: Int): Long {
        recordLeakNonRefCountingOperation(leak)
        return super.getLong(index)
    }

    override fun getChar(index: Int): Char {
        recordLeakNonRefCountingOperation(leak)
        return super.getChar(index)
    }

    override fun getFloat(index: Int): Float {
        recordLeakNonRefCountingOperation(leak)
        return super.getFloat(index)
    }

    override fun getDouble(index: Int): Double {
        recordLeakNonRefCountingOperation(leak)
        return super.getDouble(index)
    }

    override fun getBytes(index: Int, dst: ByteBuf): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.getBytes(index, dst)
    }

    override fun getBytes(index: Int, dst: ByteBuf, length: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.getBytes(index, dst, length)
    }

    override fun getBytes(index: Int, dst: ByteBuf, dstIndex: Int, length: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.getBytes(index, dst, dstIndex, length)
    }

    override fun getBytes(index: Int, dst: ByteArray): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.getBytes(index, dst)
    }

    override fun getBytes(index: Int, dst: ByteArray, dstIndex: Int, length: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.getBytes(index, dst, dstIndex, length)
    }

    override fun getBytes(index: Int, dst: ByteBuffer): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.getBytes(index, dst)
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: OutputStream, length: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.getBytes(index, out, length)
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: GatheringByteChannel, length: Int): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.getBytes(index, out, length)
    }

    override fun getCharSequence(index: Int, length: Int, charset: Charset): CharSequence {
        recordLeakNonRefCountingOperation(leak)
        return super.getCharSequence(index, length, charset)
    }

    override fun setBoolean(index: Int, value: Boolean): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.setBoolean(index, value)
    }

    override fun setByte(index: Int, value: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.setByte(index, value)
    }

    override fun setShort(index: Int, value: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.setShort(index, value)
    }

    override fun setMedium(index: Int, value: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.setMedium(index, value)
    }

    override fun setInt(index: Int, value: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.setInt(index, value)
    }

    override fun setLong(index: Int, value: Long): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.setLong(index, value)
    }

    override fun setChar(index: Int, value: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.setChar(index, value)
    }

    override fun setFloat(index: Int, value: Float): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.setFloat(index, value)
    }

    override fun setDouble(index: Int, value: Double): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.setDouble(index, value)
    }

    override fun setBytes(index: Int, src: ByteBuf): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.setBytes(index, src)
    }

    override fun setBytes(index: Int, src: ByteBuf, length: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.setBytes(index, src, length)
    }

    override fun setBytes(index: Int, src: ByteBuf, srcIndex: Int, length: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.setBytes(index, src, srcIndex, length)
    }

    override fun setBytes(index: Int, src: ByteArray): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.setBytes(index, src)
    }

    override fun setBytes(index: Int, src: ByteArray, srcIndex: Int, length: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.setBytes(index, src, srcIndex, length)
    }

    override fun setBytes(index: Int, src: ByteBuffer): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.setBytes(index, src)
    }

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: InputStream, length: Int): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.setBytes(index, `in`, length)
    }

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: ScatteringByteChannel, length: Int): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.setBytes(index, `in`, length)
    }

    override fun setZero(index: Int, length: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.setZero(index, length)
    }

    override fun setCharSequence(index: Int, sequence: CharSequence, charset: Charset): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.setCharSequence(index, sequence, charset)
    }

    override fun readBoolean(): Boolean {
        recordLeakNonRefCountingOperation(leak)
        return super.readBoolean()
    }

    override fun readByte(): Byte {
        recordLeakNonRefCountingOperation(leak)
        return super.readByte()
    }

    override fun readUnsignedByte(): Short {
        recordLeakNonRefCountingOperation(leak)
        return super.readUnsignedByte()
    }

    override fun readShort(): Short {
        recordLeakNonRefCountingOperation(leak)
        return super.readShort()
    }

    override fun readUnsignedShort(): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.readUnsignedShort()
    }

    override fun readMedium(): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.readMedium()
    }

    override fun readUnsignedMedium(): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.readUnsignedMedium()
    }

    override fun readInt(): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.readInt()
    }

    override fun readUnsignedInt(): Long {
        recordLeakNonRefCountingOperation(leak)
        return super.readUnsignedInt()
    }

    override fun readLong(): Long {
        recordLeakNonRefCountingOperation(leak)
        return super.readLong()
    }

    override fun readChar(): Char {
        recordLeakNonRefCountingOperation(leak)
        return super.readChar()
    }

    override fun readFloat(): Float {
        recordLeakNonRefCountingOperation(leak)
        return super.readFloat()
    }

    override fun readDouble(): Double {
        recordLeakNonRefCountingOperation(leak)
        return super.readDouble()
    }

    override fun readBytes(length: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.readBytes(length)
    }

    override fun readBytes(dst: ByteBuf): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.readBytes(dst)
    }

    override fun readBytes(dst: ByteBuf, length: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.readBytes(dst, length)
    }

    override fun readBytes(dst: ByteBuf, dstIndex: Int, length: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.readBytes(dst, dstIndex, length)
    }

    override fun readBytes(dst: ByteArray): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.readBytes(dst)
    }

    override fun readBytes(dst: ByteArray, dstIndex: Int, length: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.readBytes(dst, dstIndex, length)
    }

    override fun readBytes(dst: ByteBuffer): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.readBytes(dst)
    }

    @Throws(IOException::class)
    override fun readBytes(out: OutputStream, length: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.readBytes(out, length)
    }

    @Throws(IOException::class)
    override fun readBytes(out: GatheringByteChannel, length: Int): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.readBytes(out, length)
    }

    override fun readCharSequence(length: Int, charset: Charset): CharSequence {
        recordLeakNonRefCountingOperation(leak)
        return super.readCharSequence(length, charset)
    }

    override fun readString(length: Int, charset: Charset): String {
        recordLeakNonRefCountingOperation(leak)
        return super.readString(length, charset)
    }

    override fun skipBytes(length: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.skipBytes(length)
    }

    override fun writeBoolean(value: Boolean): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.writeBoolean(value)
    }

    override fun writeByte(value: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.writeByte(value)
    }

    override fun writeShort(value: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.writeShort(value)
    }

    override fun writeMedium(value: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.writeMedium(value)
    }

    override fun writeInt(value: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.writeInt(value)
    }

    override fun writeLong(value: Long): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.writeLong(value)
    }

    override fun writeChar(value: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.writeChar(value)
    }

    override fun writeFloat(value: Float): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.writeFloat(value)
    }

    override fun writeDouble(value: Double): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.writeDouble(value)
    }

    override fun writeBytes(src: ByteBuf): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.writeBytes(src)
    }

    override fun writeBytes(src: ByteBuf, length: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.writeBytes(src, length)
    }

    override fun writeBytes(src: ByteBuf, srcIndex: Int, length: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.writeBytes(src, srcIndex, length)
    }

    override fun writeBytes(src: ByteArray): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.writeBytes(src)
    }

    override fun writeBytes(src: ByteArray, srcIndex: Int, length: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.writeBytes(src, srcIndex, length)
    }

    override fun writeBytes(src: ByteBuffer): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.writeBytes(src)
    }

    @Throws(IOException::class)
    override fun writeBytes(`in`: InputStream, length: Int): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.writeBytes(`in`, length)
    }

    @Throws(IOException::class)
    override fun writeBytes(`in`: ScatteringByteChannel, length: Int): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.writeBytes(`in`, length)
    }

    override fun writeZero(length: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.writeZero(length)
    }

    override fun indexOf(fromIndex: Int, toIndex: Int, value: Byte): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.indexOf(fromIndex, toIndex, value)
    }

    override fun bytesBefore(value: Byte): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.bytesBefore(value)
    }

    override fun bytesBefore(length: Int, value: Byte): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.bytesBefore(length, value)
    }

    override fun bytesBefore(index: Int, length: Int, value: Byte): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.bytesBefore(index, length, value)
    }

    override fun forEachByte(processor: ByteProcessor): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.forEachByte(processor)
    }

    override fun forEachByte(index: Int, length: Int, processor: ByteProcessor): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.forEachByte(index, length, processor)
    }

    override fun forEachByteDesc(processor: ByteProcessor): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.forEachByteDesc(processor)
    }

    override fun forEachByteDesc(index: Int, length: Int, processor: ByteProcessor): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.forEachByteDesc(index, length, processor)
    }

    override fun copy(): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.copy()
    }

    override fun copy(index: Int, length: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.copy(index, length)
    }

    override fun nioBufferCount(): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.nioBufferCount()
    }

    override fun nioBuffer(): ByteBuffer {
        recordLeakNonRefCountingOperation(leak)
        return super.nioBuffer()
    }

    override fun nioBuffer(index: Int, length: Int): ByteBuffer {
        recordLeakNonRefCountingOperation(leak)
        return super.nioBuffer(index, length)
    }

    override fun nioBuffers(): Array<ByteBuffer> {
        recordLeakNonRefCountingOperation(leak)
        return super.nioBuffers()
    }

    override fun nioBuffers(index: Int, length: Int): Array<ByteBuffer> {
        recordLeakNonRefCountingOperation(leak)
        return super.nioBuffers(index, length)
    }

    override fun internalNioBuffer(index: Int, length: Int): ByteBuffer {
        recordLeakNonRefCountingOperation(leak)
        return super.internalNioBuffer(index, length)
    }

    override fun toString(charset: Charset): String {
        recordLeakNonRefCountingOperation(leak)
        return super.toString(charset)
    }

    override fun toString(index: Int, length: Int, charset: Charset): String {
        recordLeakNonRefCountingOperation(leak)
        return super.toString(index, length, charset)
    }

    override fun capacity(newCapacity: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.capacity(newCapacity)
    }

    override fun getShortLE(index: Int): Short {
        recordLeakNonRefCountingOperation(leak)
        return super.getShortLE(index)
    }

    override fun getUnsignedShortLE(index: Int): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.getUnsignedShortLE(index)
    }

    override fun getMediumLE(index: Int): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.getMediumLE(index)
    }

    override fun getUnsignedMediumLE(index: Int): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.getUnsignedMediumLE(index)
    }

    override fun getIntLE(index: Int): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.getIntLE(index)
    }

    override fun getUnsignedIntLE(index: Int): Long {
        recordLeakNonRefCountingOperation(leak)
        return super.getUnsignedIntLE(index)
    }

    override fun getLongLE(index: Int): Long {
        recordLeakNonRefCountingOperation(leak)
        return super.getLongLE(index)
    }

    override fun setShortLE(index: Int, value: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.setShortLE(index, value)
    }

    override fun setIntLE(index: Int, value: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.setIntLE(index, value)
    }

    override fun setMediumLE(index: Int, value: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.setMediumLE(index, value)
    }

    override fun setLongLE(index: Int, value: Long): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.setLongLE(index, value)
    }

    override fun readShortLE(): Short {
        recordLeakNonRefCountingOperation(leak)
        return super.readShortLE()
    }

    override fun readUnsignedShortLE(): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.readUnsignedShortLE()
    }

    override fun readMediumLE(): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.readMediumLE()
    }

    override fun readUnsignedMediumLE(): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.readUnsignedMediumLE()
    }

    override fun readIntLE(): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.readIntLE()
    }

    override fun readUnsignedIntLE(): Long {
        recordLeakNonRefCountingOperation(leak)
        return super.readUnsignedIntLE()
    }

    override fun readLongLE(): Long {
        recordLeakNonRefCountingOperation(leak)
        return super.readLongLE()
    }

    override fun writeShortLE(value: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.writeShortLE(value)
    }

    override fun writeMediumLE(value: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.writeMediumLE(value)
    }

    override fun writeIntLE(value: Int): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.writeIntLE(value)
    }

    override fun writeLongLE(value: Long): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.writeLongLE(value)
    }

    override fun writeCharSequence(sequence: CharSequence, charset: Charset): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.writeCharSequence(sequence, charset)
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: FileChannel, position: Long, length: Int): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.getBytes(index, out, position, length)
    }

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: FileChannel, position: Long, length: Int): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.setBytes(index, `in`, position, length)
    }

    @Throws(IOException::class)
    override fun readBytes(out: FileChannel, position: Long, length: Int): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.readBytes(out, position, length)
    }

    @Throws(IOException::class)
    override fun writeBytes(`in`: FileChannel, position: Long, length: Int): Int {
        recordLeakNonRefCountingOperation(leak)
        return super.writeBytes(`in`, position, length)
    }

    override fun asReadOnly(): ByteBuf {
        recordLeakNonRefCountingOperation(leak)
        return super.asReadOnly()
    }

    override fun retain(): ByteBuf {
        leak.record()
        return super.retain()
    }

    override fun retain(increment: Int): ByteBuf {
        leak.record()
        return super.retain(increment)
    }

    override fun release(): Boolean {
        leak.record()
        return super.release()
    }

    override fun release(decrement: Int): Boolean {
        leak.record()
        return super.release(decrement)
    }

    override fun touch(): ByteBuf {
        leak.record()
        return this
    }

    override fun touch(hint: Any?): ByteBuf {
        leak.record(hint)
        return this
    }

    override fun newLeakAwareByteBuf(
        buf: ByteBuf,
        trackedByteBuf: ByteBuf,
        leakTracker: ResourceLeakTracker<ByteBuf>
    ): AdvancedLeakAwareByteBuf {
        return AdvancedLeakAwareByteBuf(buf, trackedByteBuf, leakTracker)
    }

    companion object {
        // If set to true we will only record stacktraces for touch(...), release(...) and retain(...) calls.
        private const val PROP_ACQUIRE_AND_RELEASE_ONLY = "io.netty.leakDetection.acquireAndReleaseOnly"
        private val ACQUIRE_AND_RELEASE_ONLY: Boolean

        private val logger = InternalLoggerFactory.getInstance(AdvancedLeakAwareByteBuf::class.java)

        init {
            ACQUIRE_AND_RELEASE_ONLY = SystemPropertyUtil.getBoolean(PROP_ACQUIRE_AND_RELEASE_ONLY, false)

            if (logger.isDebugEnabled()) {
                logger.debug("-D{}: {}", PROP_ACQUIRE_AND_RELEASE_ONLY, ACQUIRE_AND_RELEASE_ONLY)
            }

            ResourceLeakDetector.addExclusions(
                AdvancedLeakAwareByteBuf::class.java, "touch", "recordLeakNonRefCountingOperation"
            )
        }

        @JvmStatic
        fun recordLeakNonRefCountingOperation(leak: ResourceLeakTracker<ByteBuf>) {
            if (!ACQUIRE_AND_RELEASE_ONLY) {
                leak.record()
            }
        }
    }
}
