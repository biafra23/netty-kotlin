/*
 * Copyright 2025 The Netty Project
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

import jdk.jfr.DataAmount
import jdk.jfr.Description
import jdk.jfr.MemoryAddress

@Suppress("Since15")
internal abstract class AbstractChunkEvent : AbstractAllocatorEvent() {
    @DataAmount
    @Description("Size of the chunk")
    @JvmField
    var capacity: Int = 0

    @Description("Is this chunk referencing off-heap memory?")
    @JvmField
    var direct: Boolean = false

    @Description("The memory address of the off-heap memory, if available")
    @MemoryAddress
    @JvmField
    var address: Long = 0

    fun fill(chunk: ChunkInfo, allocatorType: Class<out AbstractByteBufAllocator>) {
        this.allocatorType = allocatorType
        capacity = chunk.capacity()
        direct = chunk.isDirect()
        address = chunk.memoryAddress()
    }
}
