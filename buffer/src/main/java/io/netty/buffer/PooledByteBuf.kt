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

import io.netty.util.Recycler.EnhancedHandle
import io.netty.util.internal.ObjectPool.Handle
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.ClosedChannelException
import java.nio.channels.FileChannel
import java.nio.channels.GatheringByteChannel
import java.nio.channels.ScatteringByteChannel

abstract class PooledByteBuf<T> @Suppress("UNCHECKED_CAST") protected constructor(
    recyclerHandle: Handle<out PooledByteBuf<T>>,
    maxCapacity: Int
) : AbstractReferenceCountedByteBuf(maxCapacity) {

    private val recyclerHandle: EnhancedHandle<PooledByteBuf<T>> =
        recyclerHandle as EnhancedHandle<PooledByteBuf<T>>

    @JvmField internal var chunk: PoolChunk<T>? = null
    @JvmField internal var handle: Long = 0
    @JvmField internal var memory: T? = null
    @JvmField internal var offset: Int = 0
    @JvmField internal var length: Int = 0
    @JvmField internal var maxLength: Int = 0
    @JvmField internal var cache: PoolThreadCache? = null
    @JvmField internal var tmpNioBuf: ByteBuffer? = null
    private var allocator: ByteBufAllocator? = null

    internal open fun init(
        chunk: PoolChunk<T>, nioBuffer: ByteBuffer?,
        handle: Long, offset: Int, length: Int, maxLength: Int, cache: PoolThreadCache, threadLocal: Boolean
    ) {
        init0(chunk, nioBuffer, handle, offset, length, maxLength, cache, true, threadLocal)
    }

    internal open fun initUnpooled(chunk: PoolChunk<T>, length: Int) {
        init0(
            chunk, null, 0, 0, length, length, null, false,
            false /* unpooled buffers are never allocated out of the thread-local cache */
        )
    }

    private fun init0(
        chunk: PoolChunk<T>, nioBuffer: ByteBuffer?, handle: Long, offset: Int, length: Int, maxLength: Int,
        cache: PoolThreadCache?, pooled: Boolean, threadLocal: Boolean
    ) {
        assert(handle >= 0)
        assert(chunk != null)
        assert(
            !PoolChunk.isSubpage(handle) ||
            chunk.arena.sizeClass.size2SizeIdx(maxLength) <= chunk.arena.sizeClass.smallMaxSizeIdx
        ) { "Allocated small sub-page handle for a buffer size that isn't \"small.\"" }

        chunk.incrementPinnedMemory(maxLength)
        this.chunk = chunk
        memory = chunk.memory
        tmpNioBuf = nioBuffer
        allocator = chunk.arena.parent
        this.cache = cache
        this.handle = handle
        this.offset = offset
        this.length = length
        this.maxLength = maxLength
        PooledByteBufAllocator.onAllocateBuffer(this, pooled, threadLocal)
    }

    /**
     * Method must be called before reuse this [PooledByteBufAllocator]
     */
    internal fun reuse(maxCapacity: Int) {
        maxCapacity(maxCapacity)
        resetRefCnt()
        setIndex0(0, 0)
        discardMarks()
    }

    override fun capacity(): Int = length

    override fun maxFastWritableBytes(): Int = Math.min(maxLength, maxCapacity()) - writerIndex

    override fun capacity(newCapacity: Int): ByteBuf {
        if (newCapacity == length) {
            ensureAccessible()
            return this
        }
        checkNewCapacity(newCapacity)
        if (!chunk!!.unpooled) {
            // If the request capacity does not require reallocation, just update the length of the memory.
            if (newCapacity > length) {
                if (newCapacity <= maxLength) {
                    length = newCapacity
                    return this
                }
            } else if (newCapacity > maxLength ushr 1 &&
                (maxLength > 512 || newCapacity > maxLength - 16)
            ) {
                // here newCapacity < length
                length = newCapacity
                trimIndicesToCapacity(newCapacity)
                return this
            }
        }

        // Reallocation required.
        PooledByteBufAllocator.onReallocateBuffer(this, newCapacity)
        chunk!!.arena.reallocate(this, newCapacity)
        return this
    }

    override fun alloc(): ByteBufAllocator = allocator!!

    override fun order(): ByteOrder = ByteOrder.BIG_ENDIAN

    override fun unwrap(): ByteBuf? = null

    override fun retainedDuplicate(): ByteBuf =
        PooledDuplicatedByteBuf.newInstance(this, this, readerIndex(), writerIndex())

    override fun retainedSlice(): ByteBuf {
        val index = readerIndex()
        return retainedSlice(index, writerIndex() - index)
    }

    override fun retainedSlice(index: Int, length: Int): ByteBuf =
        PooledSlicedByteBuf.newInstance(this, this, index, length)

    internal fun internalNioBuffer(): ByteBuffer {
        var tmpNioBuf = this.tmpNioBuf
        if (tmpNioBuf == null) {
            tmpNioBuf = newInternalNioBuffer(memory!!)
            this.tmpNioBuf = tmpNioBuf
        } else {
            tmpNioBuf.clear()
        }
        return tmpNioBuf
    }

    internal abstract fun newInternalNioBuffer(memory: T): ByteBuffer

    override fun deallocate() {
        if (handle >= 0) {
            PooledByteBufAllocator.onDeallocateBuffer(this)
            val handle = this.handle
            this.handle = -1
            memory = null
            chunk!!.arena.free(chunk!!, tmpNioBuf, handle, maxLength, cache)
            tmpNioBuf = null
            chunk = null
            cache = null
            this.recyclerHandle.unguardedRecycle(this)
        }
    }

    internal fun trimIndicesToCapacityInternal(newCapacity: Int) {
        trimIndicesToCapacity(newCapacity)
    }

    internal fun idx(index: Int): Int = offset + index

    fun _internalNioBuffer(index: Int, length: Int, duplicate: Boolean): ByteBuffer {
        val index = idx(index)
        val buffer = if (duplicate) newInternalNioBuffer(memory!!) else internalNioBuffer()
        buffer.limit(index + length).position(index)
        return buffer
    }

    internal open fun duplicateInternalNioBuffer(index: Int, length: Int): ByteBuffer {
        checkIndex(index, length)
        return _internalNioBuffer(index, length, true)
    }

    override fun internalNioBuffer(index: Int, length: Int): ByteBuffer {
        checkIndex(index, length)
        return _internalNioBuffer(index, length, false)
    }

    override fun nioBufferCount(): Int = 1

    override fun nioBuffer(index: Int, length: Int): ByteBuffer =
        duplicateInternalNioBuffer(index, length).slice()

    override fun nioBuffers(index: Int, length: Int): Array<ByteBuffer> =
        arrayOf(nioBuffer(index, length))

    override fun isContiguous(): Boolean = true

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: GatheringByteChannel, length: Int): Int =
        out.write(duplicateInternalNioBuffer(index, length))

    @Throws(IOException::class)
    override fun readBytes(out: GatheringByteChannel, length: Int): Int {
        checkReadableBytes(length)
        val readBytes = out.write(_internalNioBuffer(readerIndex, length, false))
        readerIndex += readBytes
        return readBytes
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: FileChannel, position: Long, length: Int): Int =
        out.write(duplicateInternalNioBuffer(index, length), position)

    @Throws(IOException::class)
    override fun readBytes(out: FileChannel, position: Long, length: Int): Int {
        checkReadableBytes(length)
        val readBytes = out.write(_internalNioBuffer(readerIndex, length, false), position)
        readerIndex += readBytes
        return readBytes
    }

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: ScatteringByteChannel, length: Int): Int {
        try {
            return `in`.read(internalNioBuffer(index, length))
        } catch (ignored: ClosedChannelException) {
            return -1
        }
    }

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: FileChannel, position: Long, length: Int): Int {
        try {
            return `in`.read(internalNioBuffer(index, length), position)
        } catch (ignored: ClosedChannelException) {
            return -1
        }
    }
}
