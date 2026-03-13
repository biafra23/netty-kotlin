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

package io.netty.util

/**
 * An [IllegalStateException] which is raised when a user attempts to access a [ReferenceCounted] whose
 * reference count has been decreased to 0 (and consequently freed).
 */
open class IllegalReferenceCountException : IllegalStateException {

    constructor() : super()

    constructor(refCnt: Int) : this("refCnt: $refCnt")

    constructor(refCnt: Int, increment: Int) : this(
        "refCnt: $refCnt, " + if (increment > 0) "increment: $increment" else "decrement: ${-increment}"
    )

    constructor(message: String) : super(message)

    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(cause: Throwable) : super(cause)

    companion object {
        private const val serialVersionUID = -2507492394288153468L
    }
}
