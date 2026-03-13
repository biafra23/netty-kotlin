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

import io.netty.util.ByteProcessor
import io.netty.util.IllegalReferenceCountException
import io.netty.util.ReferenceCountUtil
import io.netty.util.internal.EmptyArrays
import io.netty.util.internal.ObjectUtil
import io.netty.util.internal.RecyclableArrayList

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.channels.GatheringByteChannel
import java.nio.channels.ScatteringByteChannel
import java.util.Arrays
import java.util.Collections
import java.util.ConcurrentModificationException
import java.util.NoSuchElementException

/**
 * A virtual buffer which shows multiple buffers as a single merged buffer.  It is recommended to use
 * [ByteBufAllocator.compositeBuffer] or [Unpooled.wrappedBuffer] instead of calling the
 * constructor explicitly.
 */
open class CompositeByteBuf : AbstractReferenceCountedByteBuf, Iterable<ByteBuf> {

    private val alloc: ByteBufAllocator
    private val direct: Boolean
    private val maxNumComponents: Int

    private var componentCount: Int = 0
    private var components: Array<Component?>? = null // resized when needed

    private var freed: Boolean = false

    private constructor(alloc: ByteBufAllocator, direct: Boolean, maxNumComponents: Int, initSize: Int)
        : super(AbstractByteBufAllocator.DEFAULT_MAX_CAPACITY) {

        this.alloc = ObjectUtil.checkNotNull(alloc, "alloc")
        if (maxNumComponents < 1) {
            throw IllegalArgumentException(
                "maxNumComponents: $maxNumComponents (expected: >= 1)"
            )
        }

        this.direct = direct
        this.maxNumComponents = maxNumComponents
        components = newCompArray(initSize, maxNumComponents)
    }

    constructor(alloc: ByteBufAllocator, direct: Boolean, maxNumComponents: Int)
        : this(alloc, direct, maxNumComponents, 0)

    constructor(alloc: ByteBufAllocator, direct: Boolean, maxNumComponents: Int, vararg buffers: ByteBuf)
        : this(alloc, direct, maxNumComponents, buffers, 0)

    internal constructor(alloc: ByteBufAllocator, direct: Boolean, maxNumComponents: Int,
                         buffers: Array<out ByteBuf>, offset: Int)
        : this(alloc, direct, maxNumComponents, buffers.size - offset) {

        addComponents0(false, 0, buffers, offset)
        consolidateIfNeeded()
        setIndex0(0, capacity())
    }

    constructor(alloc: ByteBufAllocator, direct: Boolean, maxNumComponents: Int, buffers: Iterable<ByteBuf>)
        : this(alloc, direct, maxNumComponents,
               if (buffers is Collection<*>) (buffers as Collection<ByteBuf>).size else 0) {

        addComponents(false, 0, buffers)
        setIndex(0, capacity())
    }

    // support passing arrays of other types instead of having to copy to a ByteBuf[] first
    internal interface ByteWrapper<T> {
        fun wrap(bytes: T): ByteBuf
        fun isEmpty(bytes: T): Boolean
    }

    @Suppress("UNCHECKED_CAST")
    internal constructor(alloc: ByteBufAllocator, direct: Boolean, maxNumComponents: Int,
                         wrapper: ByteWrapper<*>, buffers: Array<*>, offset: Int)
        : this(alloc, direct, maxNumComponents, buffers.size - offset) {

        addComponents0(false, 0, wrapper as ByteWrapper<Any>, buffers as Array<Any>, offset)
        consolidateIfNeeded()
        setIndex(0, capacity())
    }

    // Special constructor used by WrappedCompositeByteBuf
    internal constructor(alloc: ByteBufAllocator) : super(Int.MAX_VALUE) {
        this.alloc = alloc
        direct = false
        maxNumComponents = 0
        components = null
    }

    /**
     * Add the given [ByteBuf].
     *
     * Be aware that this method does not increase the `writerIndex` of the [CompositeByteBuf].
     * If you need to have it increased use [addComponent(boolean, ByteBuf)][addComponent].
     *
     * [ByteBuf.release] ownership of `buffer` is transferred to this [CompositeByteBuf].
     * @param buffer the [ByteBuf] to add. [ByteBuf.release] ownership is transferred to this
     * [CompositeByteBuf].
     */
    open fun addComponent(buffer: ByteBuf): CompositeByteBuf {
        return addComponent(false, buffer)
    }

    /**
     * Add the given [ByteBuf]s.
     *
     * Be aware that this method does not increase the `writerIndex` of the [CompositeByteBuf].
     * If you need to have it increased use [addComponents(boolean, ByteBuf[])][addComponents].
     *
     * [ByteBuf.release] ownership of all [ByteBuf] objects in `buffers` is transferred to this
     * [CompositeByteBuf].
     * @param buffers the [ByteBuf]s to add. [ByteBuf.release] ownership of all [ByteBuf] objects
     * is transferred to this [CompositeByteBuf].
     */
    open fun addComponents(vararg buffers: ByteBuf): CompositeByteBuf {
        return addComponents(false, *buffers)
    }

    /**
     * Add the given [ByteBuf]s.
     *
     * Be aware that this method does not increase the `writerIndex` of the [CompositeByteBuf].
     * If you need to have it increased use [addComponents(boolean, Iterable)][addComponents].
     *
     * [ByteBuf.release] ownership of all [ByteBuf] objects in `buffers` is transferred to this
     * [CompositeByteBuf].
     * @param buffers the [ByteBuf]s to add. [ByteBuf.release] ownership of all [ByteBuf] objects
     * is transferred to this [CompositeByteBuf].
     */
    open fun addComponents(buffers: Iterable<ByteBuf>): CompositeByteBuf {
        return addComponents(false, buffers)
    }

    /**
     * Add the given [ByteBuf] on the specific index.
     *
     * Be aware that this method does not increase the `writerIndex` of the [CompositeByteBuf].
     * If you need to have it increased use [addComponent(boolean, int, ByteBuf)][addComponent].
     *
     * [ByteBuf.release] ownership of `buffer` is transferred to this [CompositeByteBuf].
     * @param cIndex the index on which the [ByteBuf] will be added.
     * @param buffer the [ByteBuf] to add. [ByteBuf.release] ownership is transferred to this
     * [CompositeByteBuf].
     */
    open fun addComponent(cIndex: Int, buffer: ByteBuf): CompositeByteBuf {
        return addComponent(false, cIndex, buffer)
    }

    /**
     * Add the given [ByteBuf] and increase the `writerIndex` if `increaseWriterIndex` is
     * `true`.
     *
     * [ByteBuf.release] ownership of `buffer` is transferred to this [CompositeByteBuf].
     * @param buffer the [ByteBuf] to add. [ByteBuf.release] ownership is transferred to this
     * [CompositeByteBuf].
     */
    open fun addComponent(increaseWriterIndex: Boolean, buffer: ByteBuf): CompositeByteBuf {
        return addComponent(increaseWriterIndex, componentCount, buffer)
    }

    /**
     * Add the given [ByteBuf]s and increase the `writerIndex` if `increaseWriterIndex` is
     * `true`.
     *
     * [ByteBuf.release] ownership of all [ByteBuf] objects in `buffers` is transferred to this
     * [CompositeByteBuf].
     * @param buffers the [ByteBuf]s to add. [ByteBuf.release] ownership of all [ByteBuf] objects
     * is transferred to this [CompositeByteBuf].
     */
    open fun addComponents(increaseWriterIndex: Boolean, vararg buffers: ByteBuf): CompositeByteBuf {
        ObjectUtil.checkNotNull(buffers, "buffers")
        addComponents0(increaseWriterIndex, componentCount, buffers, 0)
        consolidateIfNeeded()
        return this
    }

    /**
     * Add the given [ByteBuf]s and increase the `writerIndex` if `increaseWriterIndex` is
     * `true`.
     *
     * [ByteBuf.release] ownership of all [ByteBuf] objects in `buffers` is transferred to this
     * [CompositeByteBuf].
     * @param buffers the [ByteBuf]s to add. [ByteBuf.release] ownership of all [ByteBuf] objects
     * is transferred to this [CompositeByteBuf].
     */
    open fun addComponents(increaseWriterIndex: Boolean, buffers: Iterable<ByteBuf>): CompositeByteBuf {
        return addComponents(increaseWriterIndex, componentCount, buffers)
    }

    /**
     * Add the given [ByteBuf] on the specific index and increase the `writerIndex`
     * if `increaseWriterIndex` is `true`.
     *
     * [ByteBuf.release] ownership of `buffer` is transferred to this [CompositeByteBuf].
     * @param cIndex the index on which the [ByteBuf] will be added.
     * @param buffer the [ByteBuf] to add. [ByteBuf.release] ownership is transferred to this
     * [CompositeByteBuf].
     */
    open fun addComponent(increaseWriterIndex: Boolean, cIndex: Int, buffer: ByteBuf): CompositeByteBuf {
        ObjectUtil.checkNotNull(buffer, "buffer")
        addComponent0(increaseWriterIndex, cIndex, buffer)
        consolidateIfNeeded()
        return this
    }

    /**
     * Precondition is that `buffer != null`.
     */
    private fun addComponent0(increaseWriterIndex: Boolean, cIndex: Int, buffer: ByteBuf): Int {
        assert(buffer != null)
        var wasAdded = false
        try {
            checkComponentIndex(cIndex)

            // No need to consolidate - just add a component to the list.
            val c = newComponent(ensureAccessible(buffer), 0)
            val readableBytes = c.length()

            // Check if we would overflow.
            // See https://github.com/netty/netty/issues/10194
            checkForOverflow(capacity(), readableBytes)

            addComp(cIndex, c)
            wasAdded = true
            if (readableBytes > 0 && cIndex < componentCount - 1) {
                updateComponentOffsets(cIndex)
            } else if (cIndex > 0) {
                c.reposition(components!![cIndex - 1]!!.endOffset)
            }
            if (increaseWriterIndex) {
                writerIndex += readableBytes
            }
            return cIndex
        } finally {
            if (!wasAdded) {
                buffer.release()
            }
        }
    }

    @Suppress("deprecation")
    private fun newComponent(buf: ByteBuf, offset: Int): Component {
        val srcIndex = buf.readerIndex()
        val len = buf.readableBytes()

        // unpeel any intermediate outer layers (UnreleasableByteBuf, LeakAwareByteBufs, SwappedByteBuf)
        var unwrapped: ByteBuf = buf
        @Suppress("UNUSED_VARIABLE")
        var unwrappedIndex = srcIndex
        while (unwrapped is WrappedByteBuf || unwrapped is SwappedByteBuf) {
            unwrapped = unwrapped.unwrap()!!
        }

        // unwrap if already sliced
        if (unwrapped is AbstractUnpooledSlicedByteBuf) {
            unwrappedIndex += unwrapped.idx(0)
            unwrapped = unwrapped.unwrap()!!
        } else if (unwrapped is PooledSlicedByteBuf) {
            unwrappedIndex += unwrapped.adjustment
            unwrapped = unwrapped.unwrap()!!
        } else if (unwrapped is DuplicatedByteBuf || unwrapped is PooledDuplicatedByteBuf) {
            unwrapped = unwrapped.unwrap()!!
        }

        // We don't need to slice later to expose the internal component if the readable range
        // is already the entire buffer
        val slice: ByteBuf? = if (buf.capacity() == len) buf else null

        return Component(buf.order(ByteOrder.BIG_ENDIAN), srcIndex,
            unwrapped.order(ByteOrder.BIG_ENDIAN), unwrappedIndex, offset, len, slice)
    }

    /**
     * Add the given [ByteBuf]s on the specific index
     *
     * Be aware that this method does not increase the `writerIndex` of the [CompositeByteBuf].
     * If you need to have it increased you need to handle it by your own.
     *
     * [ByteBuf.release] ownership of all [ByteBuf] objects in `buffers` is transferred to this
     * [CompositeByteBuf].
     * @param cIndex the index on which the [ByteBuf] will be added.
     * @param buffers the [ByteBuf]s to add. [ByteBuf.release] ownership of all [ByteBuf] objects
     * is transferred to this [CompositeByteBuf].
     */
    open fun addComponents(cIndex: Int, vararg buffers: ByteBuf): CompositeByteBuf {
        ObjectUtil.checkNotNull(buffers, "buffers")
        addComponents0(false, cIndex, buffers, 0)
        consolidateIfNeeded()
        return this
    }

    private fun addComponents0(increaseWriterIndex: Boolean,
                               cIndex: Int, buffers: Array<out ByteBuf>, arrOffset: Int): CompositeByteBuf {
        val len = buffers.size
        val count = len - arrOffset

        var readableBytes = 0
        val capacity = capacity()
        for (i in arrOffset until buffers.size) {
            val b: ByteBuf? = buffers[i]
            if (b == null) {
                break
            }
            readableBytes += b.readableBytes()

            // Check if we would overflow.
            // See https://github.com/netty/netty/issues/10194
            checkForOverflow(capacity, readableBytes)
        }
        // only set ci after we've shifted so that finally block logic is always correct
        var ci = Int.MAX_VALUE
        var arrOff = arrOffset
        try {
            checkComponentIndex(cIndex)
            shiftComps(cIndex, count) // will increase componentCount
            var nextOffset = if (cIndex > 0) components!![cIndex - 1]!!.endOffset else 0
            ci = cIndex
            while (arrOff < len) {
                val b: ByteBuf? = buffers[arrOff]
                if (b == null) {
                    break
                }
                val c = newComponent(ensureAccessible(b), nextOffset)
                components!![ci] = c
                nextOffset = c.endOffset
                arrOff++
                ci++
            }
            return this
        } finally {
            // ci is now the index following the last successfully added component
            if (ci < componentCount) {
                if (ci < cIndex + count) {
                    // we bailed early
                    removeCompRange(ci, cIndex + count)
                    while (arrOff < len) {
                        ReferenceCountUtil.safeRelease(buffers[arrOff])
                        ++arrOff
                    }
                }
                updateComponentOffsets(ci) // only need to do this here for components after the added ones
            }
            if (increaseWriterIndex && ci > cIndex && ci <= componentCount) {
                writerIndex += components!![ci - 1]!!.endOffset - components!![cIndex]!!.offset
            }
        }
    }

    private fun <T> addComponents0(increaseWriterIndex: Boolean, cIndex: Int,
                                   wrapper: ByteWrapper<T>, buffers: Array<out T>, offset: Int): Int {
        var cIdx = cIndex
        checkComponentIndex(cIdx)

        // No need for consolidation
        for (i in offset until buffers.size) {
            val b: T? = buffers[i]
            if (b == null) {
                break
            }
            if (!wrapper.isEmpty(b)) {
                cIdx = addComponent0(increaseWriterIndex, cIdx, wrapper.wrap(b)) + 1
                val size = componentCount
                if (cIdx > size) {
                    cIdx = size
                }
            }
        }
        return cIdx
    }

    /**
     * Add the given [ByteBuf]s on the specific index
     *
     * Be aware that this method does not increase the `writerIndex` of the [CompositeByteBuf].
     * If you need to have it increased you need to handle it by your own.
     *
     * [ByteBuf.release] ownership of all [ByteBuf] objects in `buffers` is transferred to this
     * [CompositeByteBuf].
     * @param cIndex the index on which the [ByteBuf] will be added.
     * @param buffers the [ByteBuf]s to add. [ByteBuf.release] ownership of all [ByteBuf] objects
     * is transferred to this [CompositeByteBuf].
     */
    open fun addComponents(cIndex: Int, buffers: Iterable<ByteBuf>): CompositeByteBuf {
        return addComponents(false, cIndex, buffers)
    }

    /**
     * Add the given [ByteBuf] and increase the `writerIndex` if `increaseWriterIndex` is
     * `true`. If the provided buffer is a [CompositeByteBuf] itself, a "shallow copy" of its
     * readable components will be performed. Thus the actual number of new components added may vary
     * and in particular will be zero if the provided buffer is not readable.
     *
     * [ByteBuf.release] ownership of `buffer` is transferred to this [CompositeByteBuf].
     * @param buffer the [ByteBuf] to add. [ByteBuf.release] ownership is transferred to this
     * [CompositeByteBuf].
     */
    open fun addFlattenedComponents(increaseWriterIndex: Boolean, buffer: ByteBuf): CompositeByteBuf {
        ObjectUtil.checkNotNull(buffer, "buffer")
        val ridx = buffer.readerIndex()
        val widx = buffer.writerIndex()
        if (ridx == widx) {
            buffer.release()
            return this
        }
        if (buffer !is CompositeByteBuf) {
            addComponent0(increaseWriterIndex, componentCount, buffer)
            consolidateIfNeeded()
            return this
        }
        val from: CompositeByteBuf
        if (buffer is WrappedCompositeByteBuf) {
            from = buffer.unwrap() as CompositeByteBuf
        } else {
            from = buffer
        }
        from.checkIndex(ridx, widx - ridx)
        val fromComponents = from.components!!
        val compCountBefore = componentCount
        val writerIndexBefore = writerIndex
        @Suppress("NAME_SHADOWING")
        var buffer: ByteBuf? = buffer
        try {
            var cidx = from.toComponentIndex0(ridx)
            var newOffset = capacity()
            while (true) {
                val component = fromComponents[cidx]!!
                val compOffset = component.offset
                val fromIdx = Math.max(ridx, compOffset)
                val toIdx = Math.min(widx, component.endOffset)
                val len = toIdx - fromIdx
                if (len > 0) { // skip empty components
                    addComp(componentCount, Component(
                        component.srcBuf.retain(), component.srcIdx(fromIdx),
                        component.buf, component.idx(fromIdx), newOffset, len, null))
                }
                if (widx == toIdx) {
                    break
                }
                newOffset += len
                cidx++
            }
            if (increaseWriterIndex) {
                writerIndex = writerIndexBefore + (widx - ridx)
            }
            consolidateIfNeeded()
            buffer!!.release()
            buffer = null
            return this
        } finally {
            if (buffer != null) {
                // if we did not succeed, attempt to rollback any components that were added
                if (increaseWriterIndex) {
                    writerIndex = writerIndexBefore
                }
                for (cidx in componentCount - 1 downTo compCountBefore) {
                    components!![cidx]!!.free()
                    removeComp(cidx)
                }
            }
        }
    }

    // TODO optimize further, similar to ByteBuf[] version
    // (difference here is that we don't know *always* know precise size increase in advance,
    // but we do in the most common case that the Iterable is a Collection)
    private fun addComponents(increaseIndex: Boolean, cIndex: Int, buffers: Iterable<ByteBuf>): CompositeByteBuf {
        if (buffers is ByteBuf) {
            // If buffers also implements ByteBuf (e.g. CompositeByteBuf), it has to go to addComponent(ByteBuf).
            return addComponent(increaseIndex, cIndex, buffers as ByteBuf)
        }
        ObjectUtil.checkNotNull(buffers, "buffers")
        val it = buffers.iterator()
        var cIdx = cIndex
        try {
            checkComponentIndex(cIdx)

            // No need for consolidation
            while (it.hasNext()) {
                val b: ByteBuf? = it.next()
                if (b == null) {
                    break
                }
                cIdx = addComponent0(increaseIndex, cIdx, b) + 1
                cIdx = Math.min(cIdx, componentCount)
            }
        } finally {
            while (it.hasNext()) {
                ReferenceCountUtil.safeRelease(it.next())
            }
        }
        consolidateIfNeeded()
        return this
    }

    /**
     * This should only be called as last operation from a method as this may adjust the underlying
     * array of components and so affect the index etc.
     */
    private fun consolidateIfNeeded() {
        // Consolidate if the number of components will exceed the allowed maximum by the current
        // operation.
        val size = componentCount
        if (size > maxNumComponents) {
            consolidate0(0, size)
        }
    }

    private fun checkComponentIndex(cIndex: Int) {
        ensureAccessible()
        if (cIndex < 0 || cIndex > componentCount) {
            throw IndexOutOfBoundsException(String.format(
                "cIndex: %d (expected: >= 0 && <= numComponents(%d))",
                cIndex, componentCount))
        }
    }

    private fun checkComponentIndex(cIndex: Int, numComponents: Int) {
        ensureAccessible()
        if (cIndex < 0 || cIndex + numComponents > componentCount) {
            throw IndexOutOfBoundsException(String.format(
                "cIndex: %d, numComponents: %d " +
                "(expected: cIndex >= 0 && cIndex + numComponents <= totalNumComponents(%d))",
                cIndex, numComponents, componentCount))
        }
    }

    private fun updateComponentOffsets(cIndex: Int) {
        val size = componentCount
        if (size <= cIndex) {
            return
        }

        var nextIndex = if (cIndex > 0) components!![cIndex - 1]!!.endOffset else 0
        var i = cIndex
        while (i < size) {
            val c = components!![i]!!
            c.reposition(nextIndex)
            nextIndex = c.endOffset
            i++
        }
    }

    /**
     * Remove the [ByteBuf] from the given index.
     *
     * @param cIndex the index on from which the [ByteBuf] will be remove
     */
    open fun removeComponent(cIndex: Int): CompositeByteBuf {
        checkComponentIndex(cIndex)
        val comp = components!![cIndex]!!
        if (lastAccessed === comp) {
            lastAccessed = null
        }
        comp.free()
        removeComp(cIndex)
        if (comp.length() > 0) {
            // Only need to call updateComponentOffsets if the length was > 0
            updateComponentOffsets(cIndex)
        }
        return this
    }

    /**
     * Remove the number of [ByteBuf]s starting from the given index.
     *
     * @param cIndex the index on which the [ByteBuf]s will be started to removed
     * @param numComponents the number of components to remove
     */
    open fun removeComponents(cIndex: Int, numComponents: Int): CompositeByteBuf {
        checkComponentIndex(cIndex, numComponents)

        if (numComponents == 0) {
            return this
        }
        val endIndex = cIndex + numComponents
        var needsUpdate = false
        for (i in cIndex until endIndex) {
            val c = components!![i]!!
            if (c.length() > 0) {
                needsUpdate = true
            }
            if (lastAccessed === c) {
                lastAccessed = null
            }
            c.free()
        }
        removeCompRange(cIndex, endIndex)

        if (needsUpdate) {
            // Only need to call updateComponentOffsets if the length was > 0
            updateComponentOffsets(cIndex)
        }
        return this
    }

    override fun iterator(): MutableIterator<ByteBuf> {
        ensureAccessible()
        return if (componentCount == 0) EMPTY_ITERATOR else CompositeByteBufIterator()
    }

    @Throws(Exception::class)
    override fun forEachByteAsc0(start: Int, end: Int, processor: ByteProcessor): Int {
        if (end <= start) {
            return -1
        }
        var i = toComponentIndex0(start)
        var length = end - start
        var s = start
        while (length > 0) {
            val c = components!![i]!!
            if (c.offset == c.endOffset) {
                i++
                continue // empty
            }
            val buf = c.buf
            val localStart = c.idx(s)
            val localLength = Math.min(length, c.endOffset - s)
            // avoid additional checks in AbstractByteBuf case
            val result = if (buf is AbstractByteBuf)
                buf.forEachByteAsc0(localStart, localStart + localLength, processor)
            else
                buf.forEachByte(localStart, localLength, processor)
            if (result != -1) {
                return result - c.adjustment
            }
            s += localLength
            length -= localLength
            i++
        }
        return -1
    }

    @Throws(Exception::class)
    override fun forEachByteDesc0(rStart: Int, rEnd: Int, processor: ByteProcessor): Int {
        if (rEnd > rStart) { // rStart *and* rEnd are inclusive
            return -1
        }
        var i = toComponentIndex0(rStart)
        var length = 1 + rStart - rEnd
        while (length > 0) {
            val c = components!![i]!!
            if (c.offset == c.endOffset) {
                i--
                continue // empty
            }
            val buf = c.buf
            val localRStart = c.idx(length + rEnd)
            val localLength = Math.min(length, localRStart)
            val localIndex = localRStart - localLength
            // avoid additional checks in AbstractByteBuf case
            val result = if (buf is AbstractByteBuf)
                buf.forEachByteDesc0(localRStart - 1, localIndex, processor)
            else
                buf.forEachByteDesc(localIndex, localLength, processor)

            if (result != -1) {
                return result - c.adjustment
            }
            length -= localLength
            i--
        }
        return -1
    }

    /**
     * Same with [slice] except that this method returns a list.
     */
    open fun decompose(offset: Int, length: Int): List<ByteBuf> {
        checkIndex(offset, length)
        if (length == 0) {
            return emptyList()
        }

        var componentId = toComponentIndex0(offset)
        var bytesToSlice = length
        // The first component
        val firstC = components!![componentId]!!

        // It's important to use srcBuf and NOT buf as we need to return the "original" source buffer and not the
        // unwrapped one as otherwise we could loose the ability to correctly update the reference count on the
        // returned buffer.
        var slice = firstC.srcBuf.slice(firstC.srcIdx(offset), Math.min(firstC.endOffset - offset, bytesToSlice))
        bytesToSlice -= slice.readableBytes()

        if (bytesToSlice == 0) {
            return listOf(slice)
        }

        val sliceList = ArrayList<ByteBuf>(componentCount - componentId)
        sliceList.add(slice)

        // Add all the slices until there is nothing more left and then return the List.
        do {
            componentId++
            val component = components!![componentId]!!
            // It's important to use srcBuf and NOT buf as we need to return the "original" source buffer and not the
            // unwrapped one as otherwise we could loose the ability to correctly update the reference count on the
            // returned buffer.
            slice = component.srcBuf.slice(component.srcIdx(component.offset),
                Math.min(component.length(), bytesToSlice))
            bytesToSlice -= slice.readableBytes()
            sliceList.add(slice)
        } while (bytesToSlice > 0)

        return sliceList
    }

    override fun isDirect(): Boolean {
        val size = componentCount
        if (size == 0) {
            return false
        }
        for (i in 0 until size) {
            if (!components!![i]!!.buf.isDirect()) {
                return false
            }
        }
        return true
    }

    override fun hasArray(): Boolean {
        return when (componentCount) {
            0 -> true
            1 -> components!![0]!!.buf.hasArray()
            else -> false
        }
    }

    override fun array(): ByteArray {
        return when (componentCount) {
            0 -> EmptyArrays.EMPTY_BYTES
            1 -> components!![0]!!.buf.array()
            else -> throw UnsupportedOperationException()
        }
    }

    override fun arrayOffset(): Int {
        return when (componentCount) {
            0 -> 0
            1 -> {
                val c = components!![0]!!
                c.idx(c.buf.arrayOffset())
            }
            else -> throw UnsupportedOperationException()
        }
    }

    override fun hasMemoryAddress(): Boolean {
        return when (componentCount) {
            0 -> Unpooled.EMPTY_BUFFER.hasMemoryAddress()
            1 -> components!![0]!!.buf.hasMemoryAddress()
            else -> false
        }
    }

    override fun memoryAddress(): Long {
        return when (componentCount) {
            0 -> Unpooled.EMPTY_BUFFER.memoryAddress()
            1 -> {
                val c = components!![0]!!
                c.buf.memoryAddress() + c.adjustment
            }
            else -> throw UnsupportedOperationException()
        }
    }

    override fun capacity(): Int {
        val size = componentCount
        return if (size > 0) components!![size - 1]!!.endOffset else 0
    }

    override fun capacity(newCapacity: Int): CompositeByteBuf {
        checkNewCapacity(newCapacity)

        val size = componentCount
        val oldCapacity = capacity()
        if (newCapacity > oldCapacity) {
            val paddingLength = newCapacity - oldCapacity
            val padding = allocBuffer(paddingLength).setIndex(0, paddingLength)
            addComponent0(false, size, padding)
            if (componentCount >= maxNumComponents) {
                // FIXME: No need to create a padding buffer and consolidate.
                // Just create a big single buffer and put the current content there.
                consolidateIfNeeded()
            }
        } else if (newCapacity < oldCapacity) {
            lastAccessed = null
            var i = size - 1
            var bytesToTrim = oldCapacity - newCapacity
            while (i >= 0) {
                val c = components!![i]!!
                val cLength = c.length()
                if (bytesToTrim < cLength) {
                    // Trim the last component
                    c.endOffset -= bytesToTrim
                    val slice = c.slice
                    if (slice != null) {
                        // We must replace the cached slice with a derived one to ensure that
                        // it can later be released properly in the case of PooledSlicedByteBuf.
                        c.slice = slice.slice(0, c.length())
                    }
                    break
                }
                c.free()
                bytesToTrim -= cLength
                i--
            }
            removeCompRange(i + 1, size)

            if (readerIndex() > newCapacity) {
                setIndex0(newCapacity, newCapacity)
            } else if (writerIndex > newCapacity) {
                writerIndex = newCapacity
            }
        }
        return this
    }

    override fun alloc(): ByteBufAllocator {
        return alloc
    }

    @Suppress("deprecation")
    override fun order(): ByteOrder {
        return ByteOrder.BIG_ENDIAN
    }

    /**
     * Return the current number of [ByteBuf]'s that are composed in this instance
     */
    open fun numComponents(): Int {
        return componentCount
    }

    /**
     * Return the max number of [ByteBuf]'s that are composed in this instance
     */
    open fun maxNumComponents(): Int {
        return maxNumComponents
    }

    /**
     * Return the index for the given offset
     */
    open fun toComponentIndex(offset: Int): Int {
        checkIndex(offset)
        return toComponentIndex0(offset)
    }

    private fun toComponentIndex0(offset: Int): Int {
        val size = componentCount
        if (offset == 0) { // fast-path zero offset
            for (i in 0 until size) {
                if (components!![i]!!.endOffset > 0) {
                    return i
                }
            }
        }
        if (size <= 2) { // fast-path for 1 and 2 component count
            return if (size == 1 || offset < components!![0]!!.endOffset) 0 else 1
        }
        var low = 0
        var high = size
        while (low <= high) {
            val mid = (low + high).ushr(1)
            val c = components!![mid]!!
            if (offset >= c.endOffset) {
                low = mid + 1
            } else if (offset < c.offset) {
                high = mid - 1
            } else {
                return mid
            }
        }

        throw Error("should not reach here")
    }

    open fun toByteIndex(cIndex: Int): Int {
        checkComponentIndex(cIndex)
        return components!![cIndex]!!.offset
    }

    override fun getByte(index: Int): Byte {
        val c = findComponent(index)
        return c.buf.getByte(c.idx(index))
    }

    protected override fun _getByte(index: Int): Byte {
        val c = findComponent0(index)
        return c.buf.getByte(c.idx(index))
    }

    protected override fun _getShort(index: Int): Short {
        val c = findComponent0(index)
        return if (index + 2 <= c.endOffset) {
            c.buf.getShort(c.idx(index))
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            ((_getByte(index).toInt() and 0xff shl 8) or (_getByte(index + 1).toInt() and 0xff)).toShort()
        } else {
            ((_getByte(index).toInt() and 0xff) or (_getByte(index + 1).toInt() and 0xff shl 8)).toShort()
        }
    }

    protected override fun _getShortLE(index: Int): Short {
        val c = findComponent0(index)
        return if (index + 2 <= c.endOffset) {
            c.buf.getShortLE(c.idx(index))
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            ((_getByte(index).toInt() and 0xff) or (_getByte(index + 1).toInt() and 0xff shl 8)).toShort()
        } else {
            ((_getByte(index).toInt() and 0xff shl 8) or (_getByte(index + 1).toInt() and 0xff)).toShort()
        }
    }

    protected override fun _getUnsignedMedium(index: Int): Int {
        val c = findComponent0(index)
        return if (index + 3 <= c.endOffset) {
            c.buf.getUnsignedMedium(c.idx(index))
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            (_getShort(index).toInt() and 0xffff shl 8) or (_getByte(index + 2).toInt() and 0xff)
        } else {
            (_getShort(index).toInt() and 0xFFFF) or ((_getByte(index + 2).toInt() and 0xFF) shl 16)
        }
    }

    protected override fun _getUnsignedMediumLE(index: Int): Int {
        val c = findComponent0(index)
        return if (index + 3 <= c.endOffset) {
            c.buf.getUnsignedMediumLE(c.idx(index))
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            (_getShortLE(index).toInt() and 0xffff) or ((_getByte(index + 2).toInt() and 0xff) shl 16)
        } else {
            ((_getShortLE(index).toInt() and 0xffff) shl 8) or (_getByte(index + 2).toInt() and 0xff)
        }
    }

    protected override fun _getInt(index: Int): Int {
        val c = findComponent0(index)
        return if (index + 4 <= c.endOffset) {
            c.buf.getInt(c.idx(index))
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            (_getShort(index).toInt() and 0xffff shl 16) or (_getShort(index + 2).toInt() and 0xffff)
        } else {
            (_getShort(index).toInt() and 0xFFFF) or ((_getShort(index + 2).toInt() and 0xFFFF) shl 16)
        }
    }

    protected override fun _getIntLE(index: Int): Int {
        val c = findComponent0(index)
        return if (index + 4 <= c.endOffset) {
            c.buf.getIntLE(c.idx(index))
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            (_getShortLE(index).toInt() and 0xffff) or ((_getShortLE(index + 2).toInt() and 0xffff) shl 16)
        } else {
            ((_getShortLE(index).toInt() and 0xffff) shl 16) or (_getShortLE(index + 2).toInt() and 0xffff)
        }
    }

    protected override fun _getLong(index: Int): Long {
        val c = findComponent0(index)
        return if (index + 8 <= c.endOffset) {
            c.buf.getLong(c.idx(index))
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            (_getInt(index).toLong() and 0xffffffffL shl 32) or (_getInt(index + 4).toLong() and 0xffffffffL)
        } else {
            (_getInt(index).toLong() and 0xFFFFFFFFL) or ((_getInt(index + 4).toLong() and 0xFFFFFFFFL) shl 32)
        }
    }

    protected override fun _getLongLE(index: Int): Long {
        val c = findComponent0(index)
        return if (index + 8 <= c.endOffset) {
            c.buf.getLongLE(c.idx(index))
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            (_getIntLE(index).toLong() and 0xffffffffL) or ((_getIntLE(index + 4).toLong() and 0xffffffffL) shl 32)
        } else {
            ((_getIntLE(index).toLong() and 0xffffffffL) shl 32) or (_getIntLE(index + 4).toLong() and 0xffffffffL)
        }
    }

    override fun getBytes(index: Int, dst: ByteArray, dstIndex: Int, length: Int): CompositeByteBuf {
        checkDstIndex(index, length, dstIndex, dst.size)
        if (length == 0) {
            return this
        }

        var i = toComponentIndex0(index)
        var idx = index
        var dstIdx = dstIndex
        var remaining = length
        while (remaining > 0) {
            val c = components!![i]!!
            val localLength = Math.min(remaining, c.endOffset - idx)
            c.buf.getBytes(c.idx(idx), dst, dstIdx, localLength)
            idx += localLength
            dstIdx += localLength
            remaining -= localLength
            i++
        }
        return this
    }

    override fun getBytes(index: Int, dst: ByteBuffer): CompositeByteBuf {
        val limit = dst.limit()
        val length = dst.remaining()

        checkIndex(index, length)
        if (length == 0) {
            return this
        }

        var i = toComponentIndex0(index)
        var idx = index
        var remaining = length
        try {
            while (remaining > 0) {
                val c = components!![i]!!
                val localLength = Math.min(remaining, c.endOffset - idx)
                dst.limit(dst.position() + localLength)
                c.buf.getBytes(c.idx(idx), dst)
                idx += localLength
                remaining -= localLength
                i++
            }
        } finally {
            dst.limit(limit)
        }
        return this
    }

    override fun getBytes(index: Int, dst: ByteBuf, dstIndex: Int, length: Int): CompositeByteBuf {
        checkDstIndex(index, length, dstIndex, dst.capacity())
        if (length == 0) {
            return this
        }

        var i = toComponentIndex0(index)
        var idx = index
        var dstIdx = dstIndex
        var remaining = length
        while (remaining > 0) {
            val c = components!![i]!!
            val localLength = Math.min(remaining, c.endOffset - idx)
            c.buf.getBytes(c.idx(idx), dst, dstIdx, localLength)
            idx += localLength
            dstIdx += localLength
            remaining -= localLength
            i++
        }
        return this
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: GatheringByteChannel, length: Int): Int {
        val count = nioBufferCount()
        return if (count == 1) {
            out.write(internalNioBuffer(index, length))
        } else {
            val writtenBytes = out.write(nioBuffers(index, length))
            if (writtenBytes > Int.MAX_VALUE) {
                Int.MAX_VALUE
            } else {
                writtenBytes.toInt()
            }
        }
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: FileChannel, position: Long, length: Int): Int {
        val count = nioBufferCount()
        return if (count == 1) {
            out.write(internalNioBuffer(index, length), position)
        } else {
            var writtenBytes = 0L
            for (buf in nioBuffers(index, length)) {
                writtenBytes += out.write(buf, position + writtenBytes)
            }
            if (writtenBytes > Int.MAX_VALUE) {
                Int.MAX_VALUE
            } else {
                writtenBytes.toInt()
            }
        }
    }

    @Throws(IOException::class)
    override fun getBytes(index: Int, out: OutputStream, length: Int): CompositeByteBuf {
        checkIndex(index, length)
        if (length == 0) {
            return this
        }

        var i = toComponentIndex0(index)
        var idx = index
        var remaining = length
        while (remaining > 0) {
            val c = components!![i]!!
            val localLength = Math.min(remaining, c.endOffset - idx)
            c.buf.getBytes(c.idx(idx), out, localLength)
            idx += localLength
            remaining -= localLength
            i++
        }
        return this
    }

    override fun setByte(index: Int, value: Int): CompositeByteBuf {
        val c = findComponent(index)
        c.buf.setByte(c.idx(index), value)
        return this
    }

    protected override fun _setByte(index: Int, value: Int) {
        val c = findComponent0(index)
        c.buf.setByte(c.idx(index), value)
    }

    override fun setShort(index: Int, value: Int): CompositeByteBuf {
        checkIndex(index, 2)
        _setShort(index, value)
        return this
    }

    protected override fun _setShort(index: Int, value: Int) {
        val c = findComponent0(index)
        if (index + 2 <= c.endOffset) {
            c.buf.setShort(c.idx(index), value)
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            _setByte(index, (value.ushr(8)).toByte().toInt())
            _setByte(index + 1, value.toByte().toInt())
        } else {
            _setByte(index, value.toByte().toInt())
            _setByte(index + 1, (value.ushr(8)).toByte().toInt())
        }
    }

    protected override fun _setShortLE(index: Int, value: Int) {
        val c = findComponent0(index)
        if (index + 2 <= c.endOffset) {
            c.buf.setShortLE(c.idx(index), value)
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            _setByte(index, value.toByte().toInt())
            _setByte(index + 1, (value.ushr(8)).toByte().toInt())
        } else {
            _setByte(index, (value.ushr(8)).toByte().toInt())
            _setByte(index + 1, value.toByte().toInt())
        }
    }

    override fun setMedium(index: Int, value: Int): CompositeByteBuf {
        checkIndex(index, 3)
        _setMedium(index, value)
        return this
    }

    protected override fun _setMedium(index: Int, value: Int) {
        val c = findComponent0(index)
        if (index + 3 <= c.endOffset) {
            c.buf.setMedium(c.idx(index), value)
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            _setShort(index, (value shr 8).toShort().toInt())
            _setByte(index + 2, value.toByte().toInt())
        } else {
            _setShort(index, value.toShort().toInt())
            _setByte(index + 2, (value.ushr(16)).toByte().toInt())
        }
    }

    protected override fun _setMediumLE(index: Int, value: Int) {
        val c = findComponent0(index)
        if (index + 3 <= c.endOffset) {
            c.buf.setMediumLE(c.idx(index), value)
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            _setShortLE(index, value.toShort().toInt())
            _setByte(index + 2, (value.ushr(16)).toByte().toInt())
        } else {
            _setShortLE(index, (value shr 8).toShort().toInt())
            _setByte(index + 2, value.toByte().toInt())
        }
    }

    override fun setInt(index: Int, value: Int): CompositeByteBuf {
        checkIndex(index, 4)
        _setInt(index, value)
        return this
    }

    protected override fun _setInt(index: Int, value: Int) {
        val c = findComponent0(index)
        if (index + 4 <= c.endOffset) {
            c.buf.setInt(c.idx(index), value)
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            _setShort(index, (value.ushr(16)).toShort().toInt())
            _setShort(index + 2, value.toShort().toInt())
        } else {
            _setShort(index, value.toShort().toInt())
            _setShort(index + 2, (value.ushr(16)).toShort().toInt())
        }
    }

    protected override fun _setIntLE(index: Int, value: Int) {
        val c = findComponent0(index)
        if (index + 4 <= c.endOffset) {
            c.buf.setIntLE(c.idx(index), value)
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            _setShortLE(index, value.toShort().toInt())
            _setShortLE(index + 2, (value.ushr(16)).toShort().toInt())
        } else {
            _setShortLE(index, (value.ushr(16)).toShort().toInt())
            _setShortLE(index + 2, value.toShort().toInt())
        }
    }

    override fun setLong(index: Int, value: Long): CompositeByteBuf {
        checkIndex(index, 8)
        _setLong(index, value)
        return this
    }

    protected override fun _setLong(index: Int, value: Long) {
        val c = findComponent0(index)
        if (index + 8 <= c.endOffset) {
            c.buf.setLong(c.idx(index), value)
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            _setInt(index, (value.ushr(32)).toInt())
            _setInt(index + 4, value.toInt())
        } else {
            _setInt(index, value.toInt())
            _setInt(index + 4, (value.ushr(32)).toInt())
        }
    }

    protected override fun _setLongLE(index: Int, value: Long) {
        val c = findComponent0(index)
        if (index + 8 <= c.endOffset) {
            c.buf.setLongLE(c.idx(index), value)
        } else if (order() == ByteOrder.BIG_ENDIAN) {
            _setIntLE(index, value.toInt())
            _setIntLE(index + 4, (value.ushr(32)).toInt())
        } else {
            _setIntLE(index, (value.ushr(32)).toInt())
            _setIntLE(index + 4, value.toInt())
        }
    }

    override fun setBytes(index: Int, src: ByteArray, srcIndex: Int, length: Int): CompositeByteBuf {
        checkSrcIndex(index, length, srcIndex, src.size)
        if (length == 0) {
            return this
        }

        var i = toComponentIndex0(index)
        var idx = index
        var srcIdx = srcIndex
        var remaining = length
        while (remaining > 0) {
            val c = components!![i]!!
            val localLength = Math.min(remaining, c.endOffset - idx)
            c.buf.setBytes(c.idx(idx), src, srcIdx, localLength)
            idx += localLength
            srcIdx += localLength
            remaining -= localLength
            i++
        }
        return this
    }

    override fun setBytes(index: Int, src: ByteBuffer): CompositeByteBuf {
        val limit = src.limit()
        val length = src.remaining()

        checkIndex(index, length)
        if (length == 0) {
            return this
        }

        var i = toComponentIndex0(index)
        var idx = index
        var remaining = length
        try {
            while (remaining > 0) {
                val c = components!![i]!!
                val localLength = Math.min(remaining, c.endOffset - idx)
                src.limit(src.position() + localLength)
                c.buf.setBytes(c.idx(idx), src)
                idx += localLength
                remaining -= localLength
                i++
            }
        } finally {
            src.limit(limit)
        }
        return this
    }

    override fun setBytes(index: Int, src: ByteBuf, srcIndex: Int, length: Int): CompositeByteBuf {
        checkSrcIndex(index, length, srcIndex, src.capacity())
        if (length == 0) {
            return this
        }

        var i = toComponentIndex0(index)
        var idx = index
        var srcIdx = srcIndex
        var remaining = length
        while (remaining > 0) {
            val c = components!![i]!!
            val localLength = Math.min(remaining, c.endOffset - idx)
            c.buf.setBytes(c.idx(idx), src, srcIdx, localLength)
            idx += localLength
            srcIdx += localLength
            remaining -= localLength
            i++
        }
        return this
    }

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: InputStream, length: Int): Int {
        checkIndex(index, length)
        if (length == 0) {
            return `in`.read(EmptyArrays.EMPTY_BYTES)
        }

        var i = toComponentIndex0(index)
        var idx = index
        var remaining = length
        var readBytes = 0
        do {
            val c = components!![i]!!
            val localLength = Math.min(remaining, c.endOffset - idx)
            if (localLength == 0) {
                // Skip empty buffer
                i++
                continue
            }
            val localReadBytes = c.buf.setBytes(c.idx(idx), `in`, localLength)
            if (localReadBytes < 0) {
                if (readBytes == 0) {
                    return -1
                } else {
                    break
                }
            }

            idx += localReadBytes
            remaining -= localReadBytes
            readBytes += localReadBytes
            if (localReadBytes == localLength) {
                i++
            }
        } while (remaining > 0)

        return readBytes
    }

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: ScatteringByteChannel, length: Int): Int {
        checkIndex(index, length)
        if (length == 0) {
            return `in`.read(EMPTY_NIO_BUFFER)
        }

        var i = toComponentIndex0(index)
        var idx = index
        var remaining = length
        var readBytes = 0
        do {
            val c = components!![i]!!
            val localLength = Math.min(remaining, c.endOffset - idx)
            if (localLength == 0) {
                // Skip empty buffer
                i++
                continue
            }
            val localReadBytes = c.buf.setBytes(c.idx(idx), `in`, localLength)

            if (localReadBytes == 0) {
                break
            }

            if (localReadBytes < 0) {
                if (readBytes == 0) {
                    return -1
                } else {
                    break
                }
            }

            idx += localReadBytes
            remaining -= localReadBytes
            readBytes += localReadBytes
            if (localReadBytes == localLength) {
                i++
            }
        } while (remaining > 0)

        return readBytes
    }

    @Throws(IOException::class)
    override fun setBytes(index: Int, `in`: FileChannel, position: Long, length: Int): Int {
        checkIndex(index, length)
        if (length == 0) {
            return `in`.read(EMPTY_NIO_BUFFER, position)
        }

        var i = toComponentIndex0(index)
        var idx = index
        var remaining = length
        var readBytes = 0
        do {
            val c = components!![i]!!
            val localLength = Math.min(remaining, c.endOffset - idx)
            if (localLength == 0) {
                // Skip empty buffer
                i++
                continue
            }
            val localReadBytes = c.buf.setBytes(c.idx(idx), `in`, position + readBytes, localLength)

            if (localReadBytes == 0) {
                break
            }

            if (localReadBytes < 0) {
                if (readBytes == 0) {
                    return -1
                } else {
                    break
                }
            }

            idx += localReadBytes
            remaining -= localReadBytes
            readBytes += localReadBytes
            if (localReadBytes == localLength) {
                i++
            }
        } while (remaining > 0)

        return readBytes
    }

    override fun copy(index: Int, length: Int): ByteBuf {
        checkIndex(index, length)
        val dst = allocBuffer(length)
        if (length != 0) {
            copyTo(index, length, toComponentIndex0(index), dst)
        }
        return dst
    }

    private fun copyTo(index: Int, length: Int, componentId: Int, dst: ByteBuf) {
        var dstIndex = 0
        var i = componentId
        var idx = index
        var remaining = length

        while (remaining > 0) {
            val c = components!![i]!!
            val localLength = Math.min(remaining, c.endOffset - idx)
            c.buf.getBytes(c.idx(idx), dst, dstIndex, localLength)
            idx += localLength
            dstIndex += localLength
            remaining -= localLength
            i++
        }

        dst.writerIndex(dst.capacity())
    }

    /**
     * Return a duplicate of the [ByteBuf] on the specified component index.
     *
     * Note that this method returns a shallow duplicate of the underlying component buffer.
     * The returned buffer's `readerIndex` and `writerIndex` will be independent of the
     * composite buffer's indices and will not be adjusted to reflect the component's view within
     * the composite buffer.
     *
     * If you need a buffer that represents the component's readable view as seen from the composite
     * buffer, use [componentSlice] instead.
     *
     * @param cIndex the index for which the [ByteBuf] should be returned
     * @return a duplicate of the underlying [ByteBuf] on the specified index
     */
    open fun component(cIndex: Int): ByteBuf {
        checkComponentIndex(cIndex)
        return components!![cIndex]!!.duplicate()
    }

    /**
     * Return a slice of the [ByteBuf] on the specified component index.
     *
     * This method provides a view of the component that reflects its state within the composite buffer.
     * The returned buffer's readable bytes will correspond to the bytes that this component
     * contributes to the composite buffer's capacity. The slice will have its own independent
     * `readerIndex` and `writerIndex`, starting at `0`.
     *
     * @param cIndex the index for which the sliced [ByteBuf] should be returned
     * @return a sliced [ByteBuf] representing the component's view
     */
    open fun componentSlice(cIndex: Int): ByteBuf {
        checkComponentIndex(cIndex)
        return components!![cIndex]!!.slice()
    }

    /**
     * Return the [ByteBuf] on the specified index
     *
     * @param offset the offset for which the [ByteBuf] should be returned
     * @return the [ByteBuf] on the specified index
     */
    open fun componentAtOffset(offset: Int): ByteBuf {
        return findComponent(offset).duplicate()
    }

    /**
     * Return the internal [ByteBuf] on the specified index. Note that updating the indexes of the returned
     * buffer will lead to an undefined behavior of this buffer.
     *
     * @param cIndex the index for which the [ByteBuf] should be returned
     */
    open fun internalComponent(cIndex: Int): ByteBuf {
        checkComponentIndex(cIndex)
        return components!![cIndex]!!.slice()
    }

    /**
     * Return the internal [ByteBuf] on the specified offset. Note that updating the indexes of the returned
     * buffer will lead to an undefined behavior of this buffer.
     *
     * @param offset the offset for which the [ByteBuf] should be returned
     */
    open fun internalComponentAtOffset(offset: Int): ByteBuf {
        return findComponent(offset).slice()
    }

    // weak cache - check it first when looking for component
    private var lastAccessed: Component? = null

    private fun findComponent(offset: Int): Component {
        val la = lastAccessed
        if (la != null && offset >= la.offset && offset < la.endOffset) {
            ensureAccessible()
            return la
        }
        checkIndex(offset)
        return findIt(offset)
    }

    private fun findComponent0(offset: Int): Component {
        val la = lastAccessed
        if (la != null && offset >= la.offset && offset < la.endOffset) {
            return la
        }
        return findIt(offset)
    }

    private fun findIt(offset: Int): Component {
        var low = 0
        var high = componentCount
        while (low <= high) {
            val mid = (low + high).ushr(1)
            val c = components!![mid]
                ?: throw IllegalStateException("No component found for offset. " +
                    "Composite buffer layout might be outdated, e.g. from a discardReadBytes call.")
            if (offset >= c.endOffset) {
                low = mid + 1
            } else if (offset < c.offset) {
                high = mid - 1
            } else {
                lastAccessed = c
                return c
            }
        }

        throw Error("should not reach here")
    }

    override fun nioBufferCount(): Int {
        val size = componentCount
        return when (size) {
            0 -> 1
            1 -> components!![0]!!.buf.nioBufferCount()
            else -> {
                var count = 0
                for (i in 0 until size) {
                    count += components!![i]!!.buf.nioBufferCount()
                }
                count
            }
        }
    }

    override fun internalNioBuffer(index: Int, length: Int): ByteBuffer {
        return when (componentCount) {
            0 -> EMPTY_NIO_BUFFER
            1 -> components!![0]!!.internalNioBuffer(index, length)
            else -> throw UnsupportedOperationException()
        }
    }

    override fun nioBuffer(index: Int, length: Int): ByteBuffer {
        checkIndex(index, length)

        when (componentCount) {
            0 -> return EMPTY_NIO_BUFFER
            1 -> {
                val c = components!![0]!!
                val buf = c.buf
                if (buf.nioBufferCount() == 1) {
                    return buf.nioBuffer(c.idx(index), length)
                }
            }
        }

        val buffers = nioBuffers(index, length)

        if (buffers.size == 1) {
            return buffers[0]
        }

        val merged = ByteBuffer.allocate(length).order(order())
        for (buf in buffers) {
            merged.put(buf)
        }

        merged.flip()
        return merged
    }

    override fun nioBuffers(index: Int, length: Int): Array<ByteBuffer> {
        checkIndex(index, length)
        if (length == 0) {
            return arrayOf(EMPTY_NIO_BUFFER)
        }

        val buffers = RecyclableArrayList.newInstance(componentCount)
        try {
            var i = toComponentIndex0(index)
            var idx = index
            var remaining = length
            while (remaining > 0) {
                val c = components!![i]!!
                val s = c.buf
                val localLength = Math.min(remaining, c.endOffset - idx)
                when (s.nioBufferCount()) {
                    0 -> throw UnsupportedOperationException()
                    1 -> buffers.add(s.nioBuffer(c.idx(idx), localLength))
                    else -> Collections.addAll(buffers, *s.nioBuffers(c.idx(idx), localLength))
                }

                idx += localLength
                remaining -= localLength
                i++
            }

            return buffers.toArray(EmptyArrays.EMPTY_BYTE_BUFFERS)
        } finally {
            buffers.recycle()
        }
    }

    /**
     * Consolidate the composed [ByteBuf]s
     */
    open fun consolidate(): CompositeByteBuf {
        ensureAccessible()
        consolidate0(0, componentCount)
        return this
    }

    /**
     * Consolidate the composed [ByteBuf]s
     *
     * @param cIndex the index on which to start to compose
     * @param numComponents the number of components to compose
     */
    open fun consolidate(cIndex: Int, numComponents: Int): CompositeByteBuf {
        checkComponentIndex(cIndex, numComponents)
        consolidate0(cIndex, numComponents)
        return this
    }

    private fun consolidate0(cIndex: Int, numComponents: Int) {
        if (numComponents <= 1) {
            return
        }

        val endCIndex = cIndex + numComponents
        val startOffset = if (cIndex != 0) components!![cIndex]!!.offset else 0
        val capacity = components!![endCIndex - 1]!!.endOffset - startOffset
        val consolidated = allocBuffer(capacity)

        for (i in cIndex until endCIndex) {
            components!![i]!!.transferTo(consolidated)
        }
        lastAccessed = null
        removeCompRange(cIndex + 1, endCIndex)
        components!![cIndex] = newComponent(consolidated, 0)
        if (cIndex != 0 || numComponents != componentCount) {
            updateComponentOffsets(cIndex)
        }
    }

    /**
     * Discard all [ByteBuf]s which are read.
     */
    open fun discardReadComponents(): CompositeByteBuf {
        ensureAccessible()
        val readerIndex = readerIndex()
        if (readerIndex == 0) {
            return this
        }

        // Discard everything if (readerIndex = writerIndex = capacity).
        val writerIndex = writerIndex()
        if (readerIndex == writerIndex && writerIndex == capacity()) {
            for (i in 0 until componentCount) {
                components!![i]!!.free()
            }
            lastAccessed = null
            clearComps()
            setIndex(0, 0)
            adjustMarkers(readerIndex)
            return this
        }

        // Remove read components.
        var firstComponentId = 0
        var c: Component? = null
        while (firstComponentId < componentCount) {
            c = components!![firstComponentId]!!
            if (c.endOffset > readerIndex) {
                break
            }
            c.free()
            firstComponentId++
        }
        if (firstComponentId == 0) {
            return this // Nothing to discard
        }
        val la = lastAccessed
        if (la != null && la.endOffset <= readerIndex) {
            lastAccessed = null
        }
        removeCompRange(0, firstComponentId)

        // Update indexes and markers.
        val offset = c!!.offset
        updateComponentOffsets(0)
        setIndex(readerIndex - offset, writerIndex - offset)
        adjustMarkers(offset)
        return this
    }

    override fun discardReadBytes(): CompositeByteBuf {
        ensureAccessible()
        val readerIndex = readerIndex()
        if (readerIndex == 0) {
            return this
        }

        // Discard everything if (readerIndex = writerIndex = capacity).
        val writerIndex = writerIndex()
        if (readerIndex == writerIndex && writerIndex == capacity()) {
            for (i in 0 until componentCount) {
                components!![i]!!.free()
            }
            lastAccessed = null
            clearComps()
            setIndex(0, 0)
            adjustMarkers(readerIndex)
            return this
        }

        var firstComponentId = 0
        var c: Component? = null
        while (firstComponentId < componentCount) {
            c = components!![firstComponentId]!!
            if (c.endOffset > readerIndex) {
                break
            }
            c.free()
            firstComponentId++
        }

        // Replace the first readable component with a new slice.
        val trimmedBytes = readerIndex - c!!.offset
        c.offset = 0
        c.endOffset -= readerIndex
        c.srcAdjustment += readerIndex
        c.adjustment += readerIndex
        val slice = c.slice
        if (slice != null) {
            // We must replace the cached slice with a derived one to ensure that
            // it can later be released properly in the case of PooledSlicedByteBuf.
            c.slice = slice.slice(trimmedBytes, c.length())
        }
        val la = lastAccessed
        if (la != null && la.endOffset <= readerIndex) {
            lastAccessed = null
        }

        removeCompRange(0, firstComponentId)

        // Update indexes and markers.
        updateComponentOffsets(0)
        setIndex(0, writerIndex - readerIndex)
        adjustMarkers(readerIndex)
        return this
    }

    private fun allocBuffer(capacity: Int): ByteBuf {
        return if (direct) alloc().directBuffer(capacity) else alloc().heapBuffer(capacity)
    }

    override fun toString(): String {
        var result = super.toString()
        result = result.substring(0, result.length - 1)
        return "$result, components=$componentCount)"
    }

    private class Component(
        val srcBuf: ByteBuf, // the originally added buffer
        srcOffset: Int,
        val buf: ByteBuf, // srcBuf unwrapped zero or more times
        bufOffset: Int,
        var offset: Int, // offset of this component within this CompositeByteBuf
        len: Int,
        var slice: ByteBuf? // cached slice, may be null
    ) {
        var srcAdjustment: Int = srcOffset - offset // index of the start of this CompositeByteBuf relative to srcBuf
        var adjustment: Int = bufOffset - offset // index of the start of this CompositeByteBuf relative to buf
        var endOffset: Int = offset + len // end offset of this component within this CompositeByteBuf

        fun srcIdx(index: Int): Int {
            return index + srcAdjustment
        }

        fun idx(index: Int): Int {
            return index + adjustment
        }

        fun length(): Int {
            return endOffset - offset
        }

        fun reposition(newOffset: Int) {
            val move = newOffset - offset
            endOffset += move
            srcAdjustment -= move
            adjustment -= move
            offset = newOffset
        }

        // copy then release
        fun transferTo(dst: ByteBuf) {
            dst.writeBytes(buf, idx(offset), length())
            free()
        }

        fun slice(): ByteBuf {
            var s = slice
            if (s == null) {
                s = srcBuf.slice(srcIdx(offset), length())
                slice = s
            }
            return s
        }

        fun duplicate(): ByteBuf {
            return srcBuf.duplicate()
        }

        fun internalNioBuffer(index: Int, length: Int): ByteBuffer {
            // Some buffers override this so we must use srcBuf
            return srcBuf.internalNioBuffer(srcIdx(index), length)
        }

        fun free() {
            slice = null
            // Release the original buffer since it may have a different
            // refcount to the unwrapped buf (e.g. if PooledSlicedByteBuf)
            srcBuf.release()
        }
    }

    override fun readerIndex(readerIndex: Int): CompositeByteBuf {
        super.readerIndex(readerIndex)
        return this
    }

    override fun writerIndex(writerIndex: Int): CompositeByteBuf {
        super.writerIndex(writerIndex)
        return this
    }

    override fun setIndex(readerIndex: Int, writerIndex: Int): CompositeByteBuf {
        super.setIndex(readerIndex, writerIndex)
        return this
    }

    override fun clear(): CompositeByteBuf {
        super.clear()
        return this
    }

    override fun markReaderIndex(): CompositeByteBuf {
        super.markReaderIndex()
        return this
    }

    override fun resetReaderIndex(): CompositeByteBuf {
        super.resetReaderIndex()
        return this
    }

    override fun markWriterIndex(): CompositeByteBuf {
        super.markWriterIndex()
        return this
    }

    override fun resetWriterIndex(): CompositeByteBuf {
        super.resetWriterIndex()
        return this
    }

    override fun ensureWritable(minWritableBytes: Int): CompositeByteBuf {
        super.ensureWritable(minWritableBytes)
        return this
    }

    override fun getBytes(index: Int, dst: ByteBuf): CompositeByteBuf {
        return getBytes(index, dst, dst.writableBytes())
    }

    override fun getBytes(index: Int, dst: ByteBuf, length: Int): CompositeByteBuf {
        getBytes(index, dst, dst.writerIndex(), length)
        dst.writerIndex(dst.writerIndex() + length)
        return this
    }

    override fun getBytes(index: Int, dst: ByteArray): CompositeByteBuf {
        return getBytes(index, dst, 0, dst.size)
    }

    override fun setBoolean(index: Int, value: Boolean): CompositeByteBuf {
        return setByte(index, if (value) 1 else 0)
    }

    override fun setChar(index: Int, value: Int): CompositeByteBuf {
        return setShort(index, value)
    }

    override fun setFloat(index: Int, value: Float): CompositeByteBuf {
        return setInt(index, java.lang.Float.floatToRawIntBits(value))
    }

    override fun setDouble(index: Int, value: Double): CompositeByteBuf {
        return setLong(index, java.lang.Double.doubleToRawLongBits(value))
    }

    override fun setBytes(index: Int, src: ByteBuf): CompositeByteBuf {
        super.setBytes(index, src, src.readableBytes())
        return this
    }

    override fun setBytes(index: Int, src: ByteBuf, length: Int): CompositeByteBuf {
        super.setBytes(index, src, length)
        return this
    }

    override fun setBytes(index: Int, src: ByteArray): CompositeByteBuf {
        return setBytes(index, src, 0, src.size)
    }

    override fun setZero(index: Int, length: Int): CompositeByteBuf {
        super.setZero(index, length)
        return this
    }

    override fun readBytes(dst: ByteBuf): CompositeByteBuf {
        super.readBytes(dst, dst.writableBytes())
        return this
    }

    override fun readBytes(dst: ByteBuf, length: Int): CompositeByteBuf {
        super.readBytes(dst, length)
        return this
    }

    override fun readBytes(dst: ByteBuf, dstIndex: Int, length: Int): CompositeByteBuf {
        super.readBytes(dst, dstIndex, length)
        return this
    }

    override fun readBytes(dst: ByteArray): CompositeByteBuf {
        super.readBytes(dst, 0, dst.size)
        return this
    }

    override fun readBytes(dst: ByteArray, dstIndex: Int, length: Int): CompositeByteBuf {
        super.readBytes(dst, dstIndex, length)
        return this
    }

    override fun readBytes(dst: ByteBuffer): CompositeByteBuf {
        super.readBytes(dst)
        return this
    }

    @Throws(IOException::class)
    override fun readBytes(out: OutputStream, length: Int): CompositeByteBuf {
        super.readBytes(out, length)
        return this
    }

    override fun skipBytes(length: Int): CompositeByteBuf {
        super.skipBytes(length)
        return this
    }

    override fun writeBoolean(value: Boolean): CompositeByteBuf {
        writeByte(if (value) 1 else 0)
        return this
    }

    override fun writeByte(value: Int): CompositeByteBuf {
        ensureWritable0(1)
        _setByte(writerIndex++, value)
        return this
    }

    override fun writeShort(value: Int): CompositeByteBuf {
        super.writeShort(value)
        return this
    }

    override fun writeMedium(value: Int): CompositeByteBuf {
        super.writeMedium(value)
        return this
    }

    override fun writeInt(value: Int): CompositeByteBuf {
        super.writeInt(value)
        return this
    }

    override fun writeLong(value: Long): CompositeByteBuf {
        super.writeLong(value)
        return this
    }

    override fun writeChar(value: Int): CompositeByteBuf {
        super.writeShort(value)
        return this
    }

    override fun writeFloat(value: Float): CompositeByteBuf {
        super.writeInt(java.lang.Float.floatToRawIntBits(value))
        return this
    }

    override fun writeDouble(value: Double): CompositeByteBuf {
        super.writeLong(java.lang.Double.doubleToRawLongBits(value))
        return this
    }

    override fun writeBytes(src: ByteBuf): CompositeByteBuf {
        super.writeBytes(src, src.readableBytes())
        return this
    }

    override fun writeBytes(src: ByteBuf, length: Int): CompositeByteBuf {
        super.writeBytes(src, length)
        return this
    }

    override fun writeBytes(src: ByteBuf, srcIndex: Int, length: Int): CompositeByteBuf {
        super.writeBytes(src, srcIndex, length)
        return this
    }

    override fun writeBytes(src: ByteArray): CompositeByteBuf {
        super.writeBytes(src, 0, src.size)
        return this
    }

    override fun writeBytes(src: ByteArray, srcIndex: Int, length: Int): CompositeByteBuf {
        super.writeBytes(src, srcIndex, length)
        return this
    }

    override fun writeBytes(src: ByteBuffer): CompositeByteBuf {
        super.writeBytes(src)
        return this
    }

    override fun writeZero(length: Int): CompositeByteBuf {
        super.writeZero(length)
        return this
    }

    override fun retain(increment: Int): CompositeByteBuf {
        super.retain(increment)
        return this
    }

    override fun retain(): CompositeByteBuf {
        super.retain()
        return this
    }

    override fun touch(): CompositeByteBuf {
        return this
    }

    override fun touch(hint: Any?): CompositeByteBuf {
        return this
    }

    override fun nioBuffers(): Array<ByteBuffer> {
        return nioBuffers(readerIndex(), readableBytes())
    }

    override fun discardSomeReadBytes(): CompositeByteBuf {
        return discardReadComponents()
    }

    protected override fun deallocate() {
        if (freed) {
            return
        }

        freed = true
        // We're not using foreach to avoid creating an iterator.
        // see https://github.com/netty/netty/issues/2642
        for (i in 0 until componentCount) {
            components!![i]!!.free()
        }
    }

    /** Bridge for same-package callers (e.g. WrappedCompositeByteBuf) that need to call deallocate on another instance. */
    internal fun invokeDeallocate() = deallocate()

    override fun isAccessible(): Boolean {
        return !freed
    }

    override fun unwrap(): ByteBuf? {
        return null
    }

    private inner class CompositeByteBufIterator : MutableIterator<ByteBuf> {
        private val size = numComponents()
        private var index = 0

        override fun hasNext(): Boolean {
            return size > index
        }

        override fun next(): ByteBuf {
            if (size != numComponents()) {
                throw ConcurrentModificationException()
            }
            if (!hasNext()) {
                throw NoSuchElementException()
            }
            try {
                return components!![index++]!!.slice()
            } catch (e: IndexOutOfBoundsException) {
                throw ConcurrentModificationException()
            }
        }

        override fun remove() {
            throw UnsupportedOperationException("Read-Only")
        }
    }

    // Component array manipulation - range checking omitted

    private fun clearComps() {
        removeCompRange(0, componentCount)
    }

    private fun removeComp(i: Int) {
        removeCompRange(i, i + 1)
    }

    private fun removeCompRange(from: Int, to: Int) {
        if (from >= to) {
            return
        }
        val size = componentCount
        assert(from >= 0 && to <= size)
        if (to < size) {
            System.arraycopy(components!!, to, components!!, from, size - to)
        }
        val newSize = size - to + from
        for (i in newSize until size) {
            components!![i] = null
        }
        componentCount = newSize
    }

    private fun addComp(i: Int, c: Component) {
        shiftComps(i, 1)
        components!![i] = c
    }

    private fun shiftComps(i: Int, count: Int) {
        val size = componentCount
        val newSize = size + count
        assert(i >= 0 && i <= size && count > 0)
        if (newSize > components!!.size) {
            // grow the array
            val newArrSize = Math.max(size + (size shr 1), newSize)
            val newArr: Array<Component?>
            if (i == size) {
                newArr = Arrays.copyOf(components, newArrSize)
            } else {
                newArr = arrayOfNulls(newArrSize)
                if (i > 0) {
                    System.arraycopy(components!!, 0, newArr, 0, i)
                }
                if (i < size) {
                    System.arraycopy(components!!, i, newArr, i + count, size - i)
                }
            }
            components = newArr
        } else if (i < size) {
            System.arraycopy(components!!, i, components!!, i + count, size - i)
        }
        componentCount = newSize
    }

    /**
     * Decreases the reference count by the specified `decrement` and deallocates this object if the reference
     * count reaches at `0`. At this point it will also decrement the reference count of each internal
     * component by `1`.
     *
     * @param decrement the number by which the reference count should be decreased
     * @return `true` if and only if the reference count became `0` and this object has been deallocated
     */
    override fun release(decrement: Int): Boolean {
        return super.release(decrement)
    }

    companion object {
        @JvmField
        val EMPTY_NIO_BUFFER: ByteBuffer = Unpooled.EMPTY_BUFFER.nioBuffer()

        @JvmStatic
        private val EMPTY_ITERATOR: MutableIterator<ByteBuf> =
            Collections.emptyList<ByteBuf>().iterator() as MutableIterator<ByteBuf>

        @JvmStatic
        private fun newCompArray(initComponents: Int, maxNumComponents: Int): Array<Component?> {
            val capacityGuess = Math.min(AbstractByteBufAllocator.DEFAULT_MAX_COMPONENTS, maxNumComponents)
            return arrayOfNulls(Math.max(initComponents, capacityGuess))
        }

        @JvmStatic
        private fun checkForOverflow(capacity: Int, readableBytes: Int) {
            if (capacity + readableBytes < 0) {
                throw IllegalArgumentException("Can't increase by $readableBytes as capacity($capacity)" +
                    " would overflow ${Int.MAX_VALUE}")
            }
        }

        @JvmStatic
        private fun ensureAccessible(buf: ByteBuf): ByteBuf {
            if (AbstractByteBuf.checkAccessible && !buf.isAccessible()) {
                throw IllegalReferenceCountException(0)
            }
            return buf
        }

        @JvmField
        internal val BYTE_ARRAY_WRAPPER: ByteWrapper<ByteArray> = object : ByteWrapper<ByteArray> {
            override fun wrap(bytes: ByteArray): ByteBuf {
                return Unpooled.wrappedBuffer(bytes)
            }

            override fun isEmpty(bytes: ByteArray): Boolean {
                return bytes.isEmpty()
            }
        }

        @JvmField
        internal val BYTE_BUFFER_WRAPPER: ByteWrapper<ByteBuffer> = object : ByteWrapper<ByteBuffer> {
            override fun wrap(bytes: ByteBuffer): ByteBuf {
                return Unpooled.wrappedBuffer(bytes)
            }

            override fun isEmpty(bytes: ByteBuffer): Boolean {
                return !bytes.hasRemaining()
            }
        }
    }
}
