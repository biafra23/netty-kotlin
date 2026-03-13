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

import io.netty.util.NetUtilInitializations.NetworkIfaceAndInetAddress
import io.netty.util.internal.BoundedInputStream
import io.netty.util.internal.PlatformDependent
import io.netty.util.internal.StringUtil
import io.netty.util.internal.SystemPropertyUtil
import io.netty.util.internal.logging.InternalLoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.UnknownHostException
import java.security.AccessController
import java.security.PrivilegedAction
import java.util.Arrays

/**
 * A class that holds a number of network-related constants.
 *
 * This class borrowed some of its methods from a modified fork of the
 * [Inet6Util class](https://svn.apache.org/repos/asf/harmony/enhanced/java/branches/java6/classlib/modules/luni/src/main/java/org/apache/harmony/luni/util/Inet6Util.java)
 * which was part of Apache Harmony.
 */
object NetUtil {

    /**
     * The [Inet4Address] that represents the IPv4 loopback address '127.0.0.1'
     */
    @JvmField
    val LOCALHOST4: Inet4Address

    /**
     * The [Inet6Address] that represents the IPv6 loopback address '::1'
     */
    @JvmField
    val LOCALHOST6: Inet6Address

    /**
     * The [InetAddress] that represents the loopback address. If IPv6 stack is available, it will refer to
     * [LOCALHOST6].  Otherwise, [LOCALHOST4].
     */
    @JvmField
    val LOCALHOST: InetAddress

    /**
     * The loopback [NetworkInterface] of the current machine
     */
    @JvmField
    val LOOPBACK_IF: NetworkInterface?

    /**
     * An unmodifiable Collection of all the interfaces on this machine.
     */
    @JvmField
    val NETWORK_INTERFACES: Collection<NetworkInterface>

    /**
     * The SOMAXCONN value of the current machine.  If failed to get the value, `200` is used as a
     * default value for Windows and `128` for others.
     */
    @JvmField
    val SOMAXCONN: Int

    /**
     * This defines how many words (represented as ints) are needed to represent an IPv6 address
     */
    private const val IPV6_WORD_COUNT = 8

    /**
     * The maximum number of characters for an IPV6 string with no scope
     */
    private const val IPV6_MAX_CHAR_COUNT = 39

    /**
     * Number of bytes needed to represent an IPV6 value
     */
    private const val IPV6_BYTE_COUNT = 16

    /**
     * Maximum amount of value adding characters in between IPV6 separators
     */
    private const val IPV6_MAX_CHAR_BETWEEN_SEPARATOR = 4

    /**
     * Minimum number of separators that must be present in an IPv6 string
     */
    private const val IPV6_MIN_SEPARATORS = 2

    /**
     * Maximum number of separators that must be present in an IPv6 string
     */
    private const val IPV6_MAX_SEPARATORS = 8

    /**
     * Maximum amount of value adding characters in between IPV4 separators
     */
    private const val IPV4_MAX_CHAR_BETWEEN_SEPARATOR = 3

    /**
     * Number of separators that must be present in an IPv4 string
     */
    private const val IPV4_SEPARATORS = 3

    /**
     * `true` if IPv4 should be used even if the system supports both IPv4 and IPv6.
     */
    private val IPV4_PREFERRED = SystemPropertyUtil.getBoolean("java.net.preferIPv4Stack", false)

    /**
     * `true` if an IPv6 address should be preferred when a host has both an IPv4 address and an IPv6 address.
     */
    private val IPV6_ADDRESSES_PREFERRED: Boolean

    /**
     * The logger being used by this class
     */
    private val logger = InternalLoggerFactory.getInstance(NetUtil::class.java)

    init {
        val prefer = SystemPropertyUtil.get("java.net.preferIPv6Addresses", "false")
        IPV6_ADDRESSES_PREFERRED = "true".equals(prefer?.trim(), ignoreCase = true)
        logger.debug("-Djava.net.preferIPv4Stack: {}", IPV4_PREFERRED)
        logger.debug("-Djava.net.preferIPv6Addresses: {}", prefer ?: "null")

        NETWORK_INTERFACES = NetUtilInitializations.networkInterfaces()

        // Create IPv4 loopback address.
        LOCALHOST4 = NetUtilInitializations.createLocalhost4()

        // Create IPv6 loopback address.
        LOCALHOST6 = NetUtilInitializations.createLocalhost6()

        val loopback: NetworkIfaceAndInetAddress =
            NetUtilInitializations.determineLoopback(NETWORK_INTERFACES, LOCALHOST4, LOCALHOST6)
        LOOPBACK_IF = loopback.iface()
        LOCALHOST = loopback.address()

        // As a SecurityManager may prevent reading the somaxconn file we wrap this in a privileged block.
        //
        // See https://github.com/netty/netty/issues/3680
        SOMAXCONN = AccessController.doPrivileged(SoMaxConnAction())
    }

    private class SoMaxConnAction : PrivilegedAction<Int> {
        override fun run(): Int {
            // Determine the default somaxconn (server socket backlog) value of the platform.
            // The known defaults:
            // - Windows NT Server 4.0+: 200
            // - Mac OS X: 128
            // - Linux kernel > 5.4 : 4096
            var somaxconn: Int
            if (PlatformDependent.isWindows()) {
                somaxconn = 200
            } else if (PlatformDependent.isOsx()) {
                somaxconn = 128
            } else {
                somaxconn = 4096
            }
            val file = File("/proc/sys/net/core/somaxconn")
            try {
                // file.exists() may throw a SecurityException if a SecurityManager is used, so execute it in the
                // try / catch block.
                // See https://github.com/netty/netty/issues/4936
                if (file.exists()) {
                    BufferedReader(
                        InputStreamReader(
                            BoundedInputStream(FileInputStream(file))
                        )
                    ).use { `in` ->
                        somaxconn = Integer.parseInt(`in`.readLine())
                        if (logger.isDebugEnabled()) {
                            logger.debug("{}: {}", file, somaxconn)
                        }
                    }
                } else {
                    // Try to get from sysctl
                    var tmp: Int? = null
                    if (SystemPropertyUtil.getBoolean("io.netty.net.somaxconn.trySysctl", false)) {
                        tmp = sysctlGetInt("kern.ipc.somaxconn")
                        if (tmp == null) {
                            tmp = sysctlGetInt("kern.ipc.soacceptqueue")
                            if (tmp != null) {
                                somaxconn = tmp
                            }
                        } else {
                            somaxconn = tmp
                        }
                    }

                    if (tmp == null) {
                        logger.debug(
                            "Failed to get SOMAXCONN from sysctl and file {}. Default: {}", file,
                            somaxconn
                        )
                    }
                }
            } catch (e: Exception) {
                if (logger.isDebugEnabled()) {
                    logger.debug(
                        "Failed to get SOMAXCONN from sysctl and file {}. Default: {}",
                        file, somaxconn, e
                    )
                }
            }
            return somaxconn
        }
    }

    /**
     * This will execute [sysctl](https://www.freebsd.org/cgi/man.cgi?sysctl(8)) with the `sysctlKey`
     * which is expected to return the numeric value for `sysctlKey`.
     * @param sysctlKey The key which the return value corresponds to.
     * @return The [sysctl](https://www.freebsd.org/cgi/man.cgi?sysctl(8)) value for `sysctlKey`.
     */
    @Throws(IOException::class)
    private fun sysctlGetInt(sysctlKey: String): Int? {
        val process = ProcessBuilder("sysctl", sysctlKey).start()
        try {
            // Suppress warnings about resource leaks since the buffered reader is closed below
            val `is` = process.inputStream
            val isr = InputStreamReader(BoundedInputStream(`is`))
            BufferedReader(isr).use { br ->
                val line = br.readLine()
                if (line != null && line.startsWith(sysctlKey)) {
                    for (i in line.length - 1 downTo sysctlKey.length + 1) {
                        if (!Character.isDigit(line[i])) {
                            return Integer.valueOf(line.substring(i + 1))
                        }
                    }
                }
                return null
            }
        } finally {
            process.destroy()
        }
    }

    /**
     * Returns `true` if IPv4 should be used even if the system supports both IPv4 and IPv6. Setting this
     * property to `true` will disable IPv6 support. The default value of this property is `false`.
     *
     * @see [Java SE networking properties](https://docs.oracle.com/javase/8/docs/api/java/net/doc-files/net-properties.html)
     */
    @JvmStatic
    fun isIpV4StackPreferred(): Boolean {
        return IPV4_PREFERRED
    }

    /**
     * Returns `true` if an IPv6 address should be preferred when a host has both an IPv4 address and an IPv6
     * address. The default value of this property is `false`.
     *
     * @see [Java SE networking properties](https://docs.oracle.com/javase/8/docs/api/java/net/doc-files/net-properties.html)
     */
    @JvmStatic
    fun isIpV6AddressesPreferred(): Boolean {
        return IPV6_ADDRESSES_PREFERRED
    }

    /**
     * Creates an byte[] based on an ipAddressString. No error handling is performed here.
     */
    @JvmStatic
    fun createByteArrayFromIpAddressString(ipAddressString: String): ByteArray? {
        if (isValidIpV4Address(ipAddressString)) {
            return validIpV4ToBytes(ipAddressString)
        }

        if (isValidIpV6Address(ipAddressString)) {
            @Suppress("NAME_SHADOWING")
            var ipAddressString = ipAddressString
            if (ipAddressString[0] == '[') {
                ipAddressString = ipAddressString.substring(1, ipAddressString.length - 1)
            }

            val percentPos = ipAddressString.indexOf('%')
            if (percentPos >= 0) {
                ipAddressString = ipAddressString.substring(0, percentPos)
            }

            return getIPv6ByName(ipAddressString, true)
        }
        return null
    }

    /**
     * Creates an [InetAddress] based on an ipAddressString or might return null if it can't be parsed.
     * No error handling is performed here.
     */
    @JvmStatic
    fun createInetAddressFromIpAddressString(ipAddressString: String): InetAddress? {
        if (isValidIpV4Address(ipAddressString)) {
            val bytes = validIpV4ToBytes(ipAddressString)
            try {
                return InetAddress.getByAddress(bytes)
            } catch (e: UnknownHostException) {
                // Should never happen!
                throw IllegalStateException(e)
            }
        }

        if (isValidIpV6Address(ipAddressString)) {
            @Suppress("NAME_SHADOWING")
            var ipAddressString = ipAddressString
            if (ipAddressString[0] == '[') {
                ipAddressString = ipAddressString.substring(1, ipAddressString.length - 1)
            }

            val percentPos = ipAddressString.indexOf('%')
            if (percentPos >= 0) {
                try {
                    val scopeId = Integer.parseInt(ipAddressString.substring(percentPos + 1))
                    ipAddressString = ipAddressString.substring(0, percentPos)
                    val bytes = getIPv6ByName(ipAddressString, true) ?: return null
                    try {
                        return Inet6Address.getByAddress(null, bytes, scopeId)
                    } catch (e: UnknownHostException) {
                        // Should never happen!
                        throw IllegalStateException(e)
                    }
                } catch (e: NumberFormatException) {
                    return null
                }
            }
            val bytes = getIPv6ByName(ipAddressString, true) ?: return null
            try {
                return InetAddress.getByAddress(bytes)
            } catch (e: UnknownHostException) {
                // Should never happen!
                throw IllegalStateException(e)
            }
        }
        return null
    }

    private fun decimalDigit(str: String, pos: Int): Int {
        return str[pos] - '0'
    }

    private fun ipv4WordToByte(ip: String, from: Int, toExclusive: Int): Byte {
        var from = from
        var ret = decimalDigit(ip, from)
        from++
        if (from == toExclusive) {
            return ret.toByte()
        }
        ret = ret * 10 + decimalDigit(ip, from)
        from++
        if (from == toExclusive) {
            return ret.toByte()
        }
        return (ret * 10 + decimalDigit(ip, from)).toByte()
    }

    // visible for tests
    @JvmStatic
    fun validIpV4ToBytes(ip: String): ByteArray {
        var i: Int = ip.indexOf('.', 1)
        val b0 = ipv4WordToByte(ip, 0, i)
        val from1 = i + 1
        i = ip.indexOf('.', from1 + 1)
        val b1 = ipv4WordToByte(ip, from1, i)
        val from2 = i + 1
        i = ip.indexOf('.', from2 + 1)
        val b2 = ipv4WordToByte(ip, from2, i)
        val b3 = ipv4WordToByte(ip, i + 1, ip.length)
        return byteArrayOf(b0, b1, b2, b3)
    }

    /**
     * Convert [Inet4Address] into `int`
     */
    @JvmStatic
    fun ipv4AddressToInt(ipAddress: Inet4Address): Int {
        val octets = ipAddress.address

        return (octets[0].toInt() and 0xff shl 24) or
            (octets[1].toInt() and 0xff shl 16) or
            (octets[2].toInt() and 0xff shl 8) or
            (octets[3].toInt() and 0xff)
    }

    /**
     * Converts a 32-bit integer into an IPv4 address.
     */
    @JvmStatic
    fun intToIpAddress(i: Int): String {
        val buf = StringBuilder(15)
        buf.append(i shr 24 and 0xff)
        buf.append('.')
        buf.append(i shr 16 and 0xff)
        buf.append('.')
        buf.append(i shr 8 and 0xff)
        buf.append('.')
        buf.append(i and 0xff)
        return buf.toString()
    }

    /**
     * Converts 4-byte or 16-byte data into an IPv4 or IPv6 string respectively.
     *
     * @throws IllegalArgumentException if `length` is not `4` nor `16`
     */
    @JvmStatic
    fun bytesToIpAddress(bytes: ByteArray): String {
        return bytesToIpAddress(bytes, 0, bytes.size)
    }

    /**
     * Converts 4-byte or 16-byte data into an IPv4 or IPv6 string respectively.
     *
     * @throws IllegalArgumentException if `length` is not `4` nor `16`
     */
    @JvmStatic
    fun bytesToIpAddress(bytes: ByteArray, offset: Int, length: Int): String {
        return when (length) {
            4 -> StringBuilder(15)
                .append(bytes[offset].toInt() and 0xff)
                .append('.')
                .append(bytes[offset + 1].toInt() and 0xff)
                .append('.')
                .append(bytes[offset + 2].toInt() and 0xff)
                .append('.')
                .append(bytes[offset + 3].toInt() and 0xff).toString()
            16 -> toAddressString(bytes, offset, false)
            else -> throw IllegalArgumentException("length: $length (expected: 4 or 16)")
        }
    }

    @JvmStatic
    fun isValidIpV6Address(ip: String): Boolean {
        return isValidIpV6Address(ip as CharSequence)
    }

    @JvmStatic
    fun isValidIpV6Address(ip: CharSequence): Boolean {
        var end = ip.length
        if (end < 2) {
            return false
        }

        // strip "[]"
        var start: Int
        var c = ip[0]
        if (c == '[') {
            end--
            if (ip[end] != ']') {
                // must have a close ]
                return false
            }
            start = 1
            c = ip[1]
        } else {
            start = 0
        }

        var colons: Int
        var compressBegin: Int
        if (c == ':') {
            // an IPv6 address can start with "::" or with a number
            if (ip[start + 1] != ':') {
                return false
            }
            colons = 2
            compressBegin = start
            start += 2
        } else {
            colons = 0
            compressBegin = -1
        }

        var wordLen = 0
        loop@ for (i in start until end) {
            c = ip[i]
            if (isValidHexChar(c)) {
                if (wordLen < 4) {
                    wordLen++
                    continue
                }
                return false
            }

            when (c) {
                ':' -> {
                    if (colons > 7) {
                        return false
                    }
                    if (ip[i - 1] == ':') {
                        if (compressBegin >= 0) {
                            return false
                        }
                        compressBegin = i - 1
                    } else {
                        wordLen = 0
                    }
                    colons++
                }
                '.' -> {
                    // case for the last 32-bits represented as IPv4 x:x:x:x:x:x:d.d.d.d

                    // check a normal case (6 single colons)
                    if (compressBegin < 0 && colons != 6 ||
                        // a special case ::1:2:3:4:5:d.d.d.d allows 7 colons with an
                        // IPv4 ending, otherwise 7 :'s is bad
                        (colons == 7 && compressBegin >= start || colons > 7)
                    ) {
                        return false
                    }

                    // Verify this address is of the correct structure to contain an IPv4 address.
                    // It must be IPv4-Mapped or IPv4-Compatible
                    // (see https://tools.ietf.org/html/rfc4291#section-2.5.5).
                    val ipv4Start = i - wordLen
                    var j = ipv4Start - 2 // index of character before the previous ':'.
                    if (isValidIPv4MappedChar(ip[j])) {
                        if (!isValidIPv4MappedChar(ip[j - 1]) ||
                            !isValidIPv4MappedChar(ip[j - 2]) ||
                            !isValidIPv4MappedChar(ip[j - 3])
                        ) {
                            return false
                        }
                        j -= 5
                    }

                    while (j >= start) {
                        val tmpChar = ip[j]
                        if (tmpChar != '0' && tmpChar != ':') {
                            return false
                        }
                        --j
                    }

                    // 7 - is minimum IPv4 address length
                    var ipv4End = AsciiString.indexOf(ip, '%', ipv4Start + 7)
                    if (ipv4End < 0) {
                        ipv4End = end
                    }
                    return isValidIpV4Address(ip, ipv4Start, ipv4End)
                }
                '%' -> {
                    // strip the interface name/index after the percent sign
                    end = i
                    break@loop
                }
                else -> return false
            }
        }

        // normal case without compression
        if (compressBegin < 0) {
            return colons == 7 && wordLen > 0
        }

        return compressBegin + 2 == end ||
            // 8 colons is valid only if compression in start or end
            wordLen > 0 && (colons < 8 || compressBegin <= start)
    }

    private fun isValidIpV4Word(word: CharSequence, from: Int, toExclusive: Int): Boolean {
        val len = toExclusive - from
        var c0: Char = '\u0000'
        val c1: Char
        val c2: Char
        if (len < 1 || len > 3 || word[from].also { c0 = it } < '0') {
            return false
        }
        if (len == 3) {
            c1 = word[from + 1]
            c2 = word[from + 2]
            return c1 >= '0' && c2 >= '0' &&
                (c0 <= '1' && c1 <= '9' && c2 <= '9' ||
                    c0 == '2' && c1 <= '5' && (c2 <= '5' || c1 < '5' && c2 <= '9'))
        }
        return c0 <= '9' && (len == 1 || isValidNumericChar(word[from + 1]))
    }

    private fun isValidHexChar(c: Char): Boolean {
        return c in '0'..'9' || c in 'A'..'F' || c in 'a'..'f'
    }

    private fun isValidNumericChar(c: Char): Boolean {
        return c in '0'..'9'
    }

    private fun isValidIPv4MappedChar(c: Char): Boolean {
        return c == 'f' || c == 'F'
    }

    private fun isValidIPv4MappedSeparators(b0: Byte, b1: Byte, mustBeZero: Boolean): Boolean {
        // We allow IPv4 Mapped (https://tools.ietf.org/html/rfc4291#section-2.5.5.1)
        // and IPv4 compatible (https://tools.ietf.org/html/rfc4291#section-2.5.5.1).
        // The IPv4 compatible is deprecated, but it allows parsing of plain IPv4 addressed into IPv6-Mapped addresses.
        return b0 == b1 && (b0 == 0.toByte() || !mustBeZero && b1 == (-1).toByte())
    }

    private fun isValidIPv4Mapped(bytes: ByteArray, currentIndex: Int, compressBegin: Int, compressLength: Int): Boolean {
        val mustBeZero = compressBegin + compressLength >= 14
        return currentIndex <= 12 && currentIndex >= 2 && (!mustBeZero || compressBegin < 12) &&
            isValidIPv4MappedSeparators(bytes[currentIndex - 1], bytes[currentIndex - 2], mustBeZero) &&
            PlatformDependent.isZero(bytes, 0, currentIndex - 3)
    }

    /**
     * Takes a [CharSequence] and parses it to see if it is a valid IPV4 address.
     *
     * @return true, if the string represents an IPV4 address in dotted
     *         notation, false otherwise
     */
    @JvmStatic
    fun isValidIpV4Address(ip: CharSequence): Boolean {
        return isValidIpV4Address(ip, 0, ip.length)
    }

    /**
     * Takes a [String] and parses it to see if it is a valid IPV4 address.
     *
     * @return true, if the string represents an IPV4 address in dotted
     *         notation, false otherwise
     */
    @JvmStatic
    fun isValidIpV4Address(ip: String): Boolean {
        return isValidIpV4Address(ip, 0, ip.length)
    }

    private fun isValidIpV4Address(ip: CharSequence, from: Int, toExcluded: Int): Boolean {
        return when (ip) {
            is String -> isValidIpV4Address(ip, from, toExcluded)
            is AsciiString -> isValidIpV4Address(ip, from, toExcluded)
            else -> isValidIpV4Address0(ip, from, toExcluded)
        }
    }

    @Suppress("DuplicatedCode")
    private fun isValidIpV4Address(ip: String, from: Int, toExcluded: Int): Boolean {
        val len = toExcluded - from
        var i: Int = 0
        var from = from
        return len <= 15 && len >= 7 &&
            ip.indexOf('.', from + 1).also { i = it } > 0 && isValidIpV4Word(ip, from, i) &&
            ip.indexOf('.', (i + 2).also { from = it }).also { i = it } > 0 && isValidIpV4Word(ip, from - 1, i) &&
            ip.indexOf('.', (i + 2).also { from = it }).also { i = it } > 0 && isValidIpV4Word(ip, from - 1, i) &&
            isValidIpV4Word(ip, i + 1, toExcluded)
    }

    @Suppress("DuplicatedCode")
    private fun isValidIpV4Address(ip: AsciiString, from: Int, toExcluded: Int): Boolean {
        val len = toExcluded - from
        var i: Int = 0
        var from = from
        return len <= 15 && len >= 7 &&
            ip.indexOf('.', from + 1).also { i = it } > 0 && isValidIpV4Word(ip, from, i) &&
            ip.indexOf('.', (i + 2).also { from = it }).also { i = it } > 0 && isValidIpV4Word(ip, from - 1, i) &&
            ip.indexOf('.', (i + 2).also { from = it }).also { i = it } > 0 && isValidIpV4Word(ip, from - 1, i) &&
            isValidIpV4Word(ip, i + 1, toExcluded)
    }

    @Suppress("DuplicatedCode")
    private fun isValidIpV4Address0(ip: CharSequence, from: Int, toExcluded: Int): Boolean {
        val len = toExcluded - from
        var i: Int = 0
        var from = from
        return len <= 15 && len >= 7 &&
            AsciiString.indexOf(ip, '.', from + 1).also { i = it } > 0 && isValidIpV4Word(ip, from, i) &&
            AsciiString.indexOf(ip, '.', (i + 2).also { from = it }).also { i = it } > 0 && isValidIpV4Word(ip, from - 1, i) &&
            AsciiString.indexOf(ip, '.', (i + 2).also { from = it }).also { i = it } > 0 && isValidIpV4Word(ip, from - 1, i) &&
            isValidIpV4Word(ip, i + 1, toExcluded)
    }

    /**
     * Returns the [Inet6Address] representation of a [CharSequence] IP address.
     *
     * This method will treat all IPv4 type addresses as "IPv4 mapped" (see [getByName])
     * @param ip [CharSequence] IP address to be converted to a [Inet6Address]
     * @return [Inet6Address] representation of the `ip` or `null` if not a valid IP address.
     */
    @JvmStatic
    fun getByName(ip: CharSequence): Inet6Address? {
        return getByName(ip, true)
    }

    /**
     * Returns the [Inet6Address] representation of a [CharSequence] IP address.
     *
     * The `ipv4Mapped` parameter specifies how IPv4 addresses should be treated.
     * "IPv4 mapped" format as
     * defined in [rfc 4291 section 2](https://tools.ietf.org/html/rfc4291#section-2.5.5) is supported.
     * @param ip [CharSequence] IP address to be converted to a [Inet6Address]
     * @param ipv4Mapped
     * - `true` To allow IPv4 mapped inputs to be translated into [Inet6Address]
     * - `false` Consider IPv4 mapped addresses as invalid.
     * @return [Inet6Address] representation of the `ip` or `null` if not a valid IP address.
     */
    @JvmStatic
    fun getByName(ip: CharSequence, ipv4Mapped: Boolean): Inet6Address? {
        val bytes = getIPv6ByName(ip, ipv4Mapped) ?: return null
        try {
            return Inet6Address.getByAddress(null, bytes, -1)
        } catch (e: UnknownHostException) {
            throw RuntimeException(e) // Should never happen
        }
    }

    /**
     * Returns the byte array representation of a [CharSequence] IP address.
     *
     * The `ipv4Mapped` parameter specifies how IPv4 addresses should be treated.
     * "IPv4 mapped" format as
     * defined in [rfc 4291 section 2](https://tools.ietf.org/html/rfc4291#section-2.5.5) is supported.
     * @param ip [CharSequence] IP address to be converted to a [Inet6Address]
     * @param ipv4Mapped
     * - `true` To allow IPv4 mapped inputs to be translated into [Inet6Address]
     * - `false` Consider IPv4 mapped addresses as invalid.
     * @return byte array representation of the `ip` or `null` if not a valid IP address.
     */
    // visible for test
    @JvmStatic
    internal fun getIPv6ByName(ip: CharSequence, ipv4Mapped: Boolean): ByteArray? {
        val bytes = ByteArray(IPV6_BYTE_COUNT)
        val ipLength = ip.length
        var compressBegin = 0
        var compressLength = 0
        var currentIndex = 0
        var value = 0
        var begin = -1
        var i = 0
        var ipv6Separators = 0
        var ipv4Separators = 0
        var tmp: Int
        while (i < ipLength) {
            val c = ip[i]
            when (c) {
                ':' -> {
                    ++ipv6Separators
                    if (i - begin > IPV6_MAX_CHAR_BETWEEN_SEPARATOR ||
                        ipv4Separators > 0 || ipv6Separators > IPV6_MAX_SEPARATORS ||
                        currentIndex + 1 >= bytes.size
                    ) {
                        return null
                    }
                    value = value shl ((IPV6_MAX_CHAR_BETWEEN_SEPARATOR - (i - begin)) shl 2)

                    if (compressLength > 0) {
                        compressLength -= 2
                    }

                    // The value integer holds at most 4 bytes from right (most significant) to left (least significant).
                    // The following bit shifting is used to extract and re-order the individual bytes to achieve a
                    // left (most significant) to right (least significant) ordering.
                    bytes[currentIndex++] = (((value and 0xf) shl 4) or ((value shr 4) and 0xf)).toByte()
                    bytes[currentIndex++] = ((((value shr 8) and 0xf) shl 4) or ((value shr 12) and 0xf)).toByte()
                    tmp = i + 1
                    if (tmp < ipLength && ip[tmp] == ':') {
                        ++tmp
                        if (compressBegin != 0 || (tmp < ipLength && ip[tmp] == ':')) {
                            return null
                        }
                        ++ipv6Separators
                        compressBegin = currentIndex
                        compressLength = bytes.size - compressBegin - 2
                        ++i
                    }
                    value = 0
                    begin = -1
                }
                '.' -> {
                    ++ipv4Separators
                    tmp = i - begin // tmp is the length of the current segment.
                    if (tmp > IPV4_MAX_CHAR_BETWEEN_SEPARATOR ||
                        begin < 0 ||
                        ipv4Separators > IPV4_SEPARATORS ||
                        (ipv6Separators > 0 && (currentIndex + compressLength < 12)) ||
                        i + 1 >= ipLength ||
                        currentIndex >= bytes.size ||
                        ipv4Separators == 1 &&
                        // We also parse pure IPv4 addresses as IPv4-Mapped for ease of use.
                        ((!ipv4Mapped || currentIndex != 0 && !isValidIPv4Mapped(
                            bytes, currentIndex,
                            compressBegin, compressLength
                        )) ||
                            (tmp == 3 && (!isValidNumericChar(ip[i - 1]) ||
                                !isValidNumericChar(ip[i - 2]) ||
                                !isValidNumericChar(ip[i - 3])) ||
                                tmp == 2 && (!isValidNumericChar(ip[i - 1]) ||
                                    !isValidNumericChar(ip[i - 2])) ||
                                tmp == 1 && !isValidNumericChar(ip[i - 1])))
                    ) {
                        return null
                    }
                    value = value shl ((IPV4_MAX_CHAR_BETWEEN_SEPARATOR - tmp) shl 2)

                    // The value integer holds at most 3 bytes from right (most significant) to left (least significant).
                    // The following bit shifting is to restructure the bytes to be left (most significant) to
                    // right (least significant) while also accounting for each IPv4 digit is base 10.
                    begin = (value and 0xf) * 100 + ((value shr 4) and 0xf) * 10 + ((value shr 8) and 0xf)
                    if (begin > 255) {
                        return null
                    }
                    bytes[currentIndex++] = begin.toByte()
                    value = 0
                    begin = -1
                }
                else -> {
                    if (!isValidHexChar(c) || (ipv4Separators > 0 && !isValidNumericChar(c))) {
                        return null
                    }
                    if (begin < 0) {
                        begin = i
                    } else if (i - begin > IPV6_MAX_CHAR_BETWEEN_SEPARATOR) {
                        return null
                    }
                    // The value is treated as a sort of array of numbers because we are dealing with
                    // at most 4 consecutive bytes we can use bit shifting to accomplish this.
                    // The most significant byte will be encountered first, and reside in the right most
                    // position of the following integer
                    value += StringUtil.decodeHexNibble(c) shl ((i - begin) shl 2)
                }
            }
            ++i
        }

        val isCompressed = compressBegin > 0
        // Finish up last set of data that was accumulated in the loop (or before the loop)
        if (ipv4Separators > 0) {
            if (begin > 0 && i - begin > IPV4_MAX_CHAR_BETWEEN_SEPARATOR ||
                ipv4Separators != IPV4_SEPARATORS ||
                currentIndex >= bytes.size
            ) {
                return null
            }
            if (!(ipv6Separators == 0 || ipv6Separators >= IPV6_MIN_SEPARATORS &&
                    (!isCompressed && (ipv6Separators == 6 && ip[0] != ':') ||
                        isCompressed && (ipv6Separators < IPV6_MAX_SEPARATORS &&
                            (ip[0] != ':' || compressBegin <= 2))))
            ) {
                return null
            }
            value = value shl ((IPV4_MAX_CHAR_BETWEEN_SEPARATOR - (i - begin)) shl 2)

            // The value integer holds at most 3 bytes from right (most significant) to left (least significant).
            // The following bit shifting is to restructure the bytes to be left (most significant) to
            // right (least significant) while also accounting for each IPv4 digit is base 10.
            begin = (value and 0xf) * 100 + ((value shr 4) and 0xf) * 10 + ((value shr 8) and 0xf)
            if (begin > 255) {
                return null
            }
            bytes[currentIndex++] = begin.toByte()
        } else {
            tmp = ipLength - 1
            if (begin > 0 && i - begin > IPV6_MAX_CHAR_BETWEEN_SEPARATOR ||
                ipv6Separators < IPV6_MIN_SEPARATORS ||
                !isCompressed && (ipv6Separators + 1 != IPV6_MAX_SEPARATORS ||
                    ip[0] == ':' || ip[tmp] == ':') ||
                isCompressed && (ipv6Separators > IPV6_MAX_SEPARATORS ||
                    (ipv6Separators == IPV6_MAX_SEPARATORS &&
                        (compressBegin <= 2 && ip[0] != ':' ||
                            compressBegin >= 14 && ip[tmp] != ':'))) ||
                currentIndex + 1 >= bytes.size ||
                begin < 0 && ip[tmp - 1] != ':' ||
                compressBegin > 2 && ip[0] == ':'
            ) {
                return null
            }
            if (begin >= 0 && i - begin <= IPV6_MAX_CHAR_BETWEEN_SEPARATOR) {
                value = value shl ((IPV6_MAX_CHAR_BETWEEN_SEPARATOR - (i - begin)) shl 2)
            }
            // The value integer holds at most 4 bytes from right (most significant) to left (least significant).
            // The following bit shifting is used to extract and re-order the individual bytes to achieve a
            // left (most significant) to right (least significant) ordering.
            bytes[currentIndex++] = (((value and 0xf) shl 4) or ((value shr 4) and 0xf)).toByte()
            bytes[currentIndex++] = ((((value shr 8) and 0xf) shl 4) or ((value shr 12) and 0xf)).toByte()
        }

        if (currentIndex < bytes.size) {
            val toBeCopiedLength = currentIndex - compressBegin
            val targetIndex = bytes.size - toBeCopiedLength
            System.arraycopy(bytes, compressBegin, bytes, targetIndex, toBeCopiedLength)
            // targetIndex is also the `toIndex` to fill 0
            Arrays.fill(bytes, compressBegin, targetIndex, 0.toByte())
        }

        if (ipv4Separators > 0) {
            // We only support IPv4-Mapped addresses [1] because IPv4-Compatible addresses are deprecated [2].
            // [1] https://tools.ietf.org/html/rfc4291#section-2.5.5.2
            // [2] https://tools.ietf.org/html/rfc4291#section-2.5.5.1
            bytes[10] = 0xff.toByte()
            bytes[11] = 0xff.toByte()
        }

        return bytes
    }

    /**
     * Returns the [String] representation of an [InetSocketAddress].
     *
     * The output does not include Scope ID.
     * @param addr [InetSocketAddress] to be converted to an address string
     * @return `String` containing the text-formatted IP address
     */
    @JvmStatic
    fun toSocketAddressString(addr: InetSocketAddress): String {
        val port = addr.port.toString()
        val sb: StringBuilder

        if (addr.isUnresolved) {
            val hostname = getHostname(addr)
            sb = newSocketAddressStringBuilder(hostname, port, !isValidIpV6Address(hostname))
        } else {
            val address = addr.address
            val hostString = toAddressString(address)
            sb = newSocketAddressStringBuilder(hostString, port, address is Inet4Address)
        }
        return sb.append(':').append(port).toString()
    }

    /**
     * Returns the [String] representation of a host port combo.
     */
    @JvmStatic
    fun toSocketAddressString(host: String, port: Int): String {
        val portStr = port.toString()
        return newSocketAddressStringBuilder(
            host, portStr, !isValidIpV6Address(host)
        ).append(':').append(portStr).toString()
    }

    private fun newSocketAddressStringBuilder(host: String, port: String, ipv4: Boolean): StringBuilder {
        val hostLen = host.length
        if (ipv4) {
            // Need to include enough space for hostString:port.
            return StringBuilder(hostLen + 1 + port.length).append(host)
        }
        // Need to include enough space for [hostString]:port.
        val stringBuilder = StringBuilder(hostLen + 3 + port.length)
        if (hostLen > 1 && host[0] == '[' && host[hostLen - 1] == ']') {
            return stringBuilder.append(host)
        }
        return stringBuilder.append('[').append(host).append(']')
    }

    /**
     * Returns the [String] representation of an [InetAddress].
     * - Inet4Address results are identical to [InetAddress.getHostAddress]
     * - Inet6Address results adhere to
     * [rfc 5952 section 4](https://tools.ietf.org/html/rfc5952#section-4)
     *
     * The output does not include Scope ID.
     * @param ip [InetAddress] to be converted to an address string
     * @return `String` containing the text-formatted IP address
     */
    @JvmStatic
    fun toAddressString(ip: InetAddress): String {
        return toAddressString(ip, false)
    }

    /**
     * Returns the [String] representation of an [InetAddress].
     * - Inet4Address results are identical to [InetAddress.getHostAddress]
     * - Inet6Address results adhere to
     * [rfc 5952 section 4](https://tools.ietf.org/html/rfc5952#section-4) if
     * `ipv4Mapped` is false.  If `ipv4Mapped` is true then "IPv4 mapped" format
     * from [rfc 4291 section 2](https://tools.ietf.org/html/rfc4291#section-2.5.5) will be supported.
     * The compressed result will always obey the compression rules defined in
     * [rfc 5952 section 4](https://tools.ietf.org/html/rfc5952#section-4)
     *
     * The output does not include Scope ID.
     * @param ip [InetAddress] to be converted to an address string
     * @param ipv4Mapped
     * - `true` to stray from strict rfc 5952 and support the "IPv4 mapped" format
     * defined in [rfc 4291 section 2](https://tools.ietf.org/html/rfc4291#section-2.5.5) while still
     * following the updated guidelines in
     * [rfc 5952 section 4](https://tools.ietf.org/html/rfc5952#section-4)
     * - `false` to strictly follow rfc 5952
     * @return `String` containing the text-formatted IP address
     */
    @JvmStatic
    fun toAddressString(ip: InetAddress, ipv4Mapped: Boolean): String {
        if (ip is Inet4Address) {
            return ip.getHostAddress()
        }
        if (ip !is Inet6Address) {
            throw IllegalArgumentException("Unhandled type: $ip")
        }

        return toAddressString(ip.getAddress(), 0, ipv4Mapped)
    }

    private fun toAddressString(bytes: ByteArray, offset: Int, ipv4Mapped: Boolean): String {
        val words = IntArray(IPV6_WORD_COUNT)
        for (i in words.indices) {
            val idx = (i shl 1) + offset
            words[i] = ((bytes[idx].toInt() and 0xff) shl 8) or (bytes[idx + 1].toInt() and 0xff)
        }

        // Find longest run of 0s, tie goes to first found instance
        var currentStart = -1
        var currentLength: Int
        var shortestStart = -1
        var shortestLength = 0
        for (i in words.indices) {
            if (words[i] == 0) {
                if (currentStart < 0) {
                    currentStart = i
                }
            } else if (currentStart >= 0) {
                currentLength = i - currentStart
                if (currentLength > shortestLength) {
                    shortestStart = currentStart
                    shortestLength = currentLength
                }
                currentStart = -1
            }
        }
        // If the array ends on a streak of zeros, make sure we account for it
        if (currentStart >= 0) {
            currentLength = words.size - currentStart
            if (currentLength > shortestLength) {
                shortestStart = currentStart
                shortestLength = currentLength
            }
        }
        // Ignore the longest streak if it is only 1 long
        if (shortestLength == 1) {
            shortestLength = 0
            shortestStart = -1
        }

        // Translate to string taking into account longest consecutive 0s
        val shortestEnd = shortestStart + shortestLength
        val b = StringBuilder(IPV6_MAX_CHAR_COUNT)
        if (shortestEnd < 0) { // Optimization when there is no compressing needed
            b.append(Integer.toHexString(words[0]))
            for (i in 1 until words.size) {
                b.append(':')
                b.append(Integer.toHexString(words[i]))
            }
        } else { // General case that can handle compressing (and not compressing)
            // Loop unroll the first index (so we don't constantly check i==0 cases in loop)
            val isIpv4Mapped: Boolean
            if (inRangeEndExclusive(0, shortestStart, shortestEnd)) {
                b.append("::")
                isIpv4Mapped = ipv4Mapped && (shortestEnd == 5 && words[5] == 0xffff)
            } else {
                b.append(Integer.toHexString(words[0]))
                isIpv4Mapped = false
            }
            for (i in 1 until words.size) {
                if (!inRangeEndExclusive(i, shortestStart, shortestEnd)) {
                    if (!inRangeEndExclusive(i - 1, shortestStart, shortestEnd)) {
                        // If the last index was not part of the shortened sequence
                        if (!isIpv4Mapped || i == 6) {
                            b.append(':')
                        } else {
                            b.append('.')
                        }
                    }
                    if (isIpv4Mapped && i > 5) {
                        b.append(words[i] shr 8)
                        b.append('.')
                        b.append(words[i] and 0xff)
                    } else {
                        b.append(Integer.toHexString(words[i]))
                    }
                } else if (!inRangeEndExclusive(i - 1, shortestStart, shortestEnd)) {
                    // If we are in the shortened sequence and the last index was not
                    b.append("::")
                }
            }
        }

        return b.toString()
    }

    /**
     * Returns [InetSocketAddress.getHostString].
     * @param addr The address
     * @return the host string
     */
    @JvmStatic
    fun getHostname(addr: InetSocketAddress): String {
        return addr.hostString
    }

    /**
     * Does a range check on `value` if is within `start` (inclusive) and `end` (exclusive).
     * @param value The value to checked if is within `start` (inclusive) and `end` (exclusive)
     * @param start The start of the range (inclusive)
     * @param end The end of the range (exclusive)
     * @return
     * - `true` if `value` if is within `start` (inclusive) and `end` (exclusive)
     * - `false` otherwise
     */
    private fun inRangeEndExclusive(value: Int, start: Int, end: Int): Boolean {
        return value in start until end
    }
}
