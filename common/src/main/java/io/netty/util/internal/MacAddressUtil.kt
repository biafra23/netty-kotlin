/*
 * Copyright 2016 The Netty Project
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
package io.netty.util.internal

import io.netty.util.NetUtil
import io.netty.util.internal.EmptyArrays.EMPTY_BYTES
import io.netty.util.internal.logging.InternalLoggerFactory
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.Arrays
import java.util.LinkedHashMap
import java.util.concurrent.ThreadLocalRandom

object MacAddressUtil {

    private val logger = InternalLoggerFactory.getInstance(MacAddressUtil::class.java)

    private const val EUI64_MAC_ADDRESS_LENGTH = 8
    private const val EUI48_MAC_ADDRESS_LENGTH = 6

    /**
     * Obtains the best MAC address found on local network interfaces.
     * Generally speaking, an active network interface used on public
     * networks is better than a local network interface.
     *
     * @return byte array containing a MAC. null if no MAC can be found.
     */
    @JvmStatic
    fun bestAvailableMac(): ByteArray? {
        // Find the best MAC address available.
        var bestMacAddr = EMPTY_BYTES
        var bestInetAddr: InetAddress = NetUtil.LOCALHOST4

        // Retrieve the list of available network interfaces.
        val ifaces = LinkedHashMap<NetworkInterface, InetAddress>()
        for (iface in NetUtil.NETWORK_INTERFACES) {
            // Use the interface with proper INET addresses only.
            val addrs = SocketUtils.addressesFromNetworkInterface(iface)
            if (addrs.hasMoreElements()) {
                val a = addrs.nextElement()
                if (!a.isLoopbackAddress) {
                    ifaces[iface] = a
                }
            }
        }

        for ((iface, inetAddr) in ifaces) {
            if (iface.isVirtual) {
                continue
            }

            val macAddr: ByteArray
            try {
                macAddr = SocketUtils.hardwareAddressFromNetworkInterface(iface) ?: continue
            } catch (e: SocketException) {
                logger.debug("Failed to get the hardware address of a network interface: {}", iface, e)
                continue
            }

            var replace = false
            var res = compareAddresses(bestMacAddr, macAddr)
            if (res < 0) {
                // Found a better MAC address.
                replace = true
            } else if (res == 0) {
                // Two MAC addresses are of pretty much same quality.
                res = compareAddresses(bestInetAddr, inetAddr)
                if (res < 0) {
                    // Found a MAC address with better INET address.
                    replace = true
                } else if (res == 0) {
                    // Cannot tell the difference.  Choose the longer one.
                    if (bestMacAddr.size < macAddr.size) {
                        replace = true
                    }
                }
            }

            if (replace) {
                bestMacAddr = macAddr
                bestInetAddr = inetAddr
            }
        }

        if (bestMacAddr === EMPTY_BYTES) {
            return null
        }

        if (bestMacAddr.size == EUI48_MAC_ADDRESS_LENGTH) { // EUI-48 - convert to EUI-64
            val newAddr = ByteArray(EUI64_MAC_ADDRESS_LENGTH)
            System.arraycopy(bestMacAddr, 0, newAddr, 0, 3)
            newAddr[3] = 0xFF.toByte()
            newAddr[4] = 0xFE.toByte()
            System.arraycopy(bestMacAddr, 3, newAddr, 5, 3)
            bestMacAddr = newAddr
        } else {
            // Unknown
            bestMacAddr = Arrays.copyOf(bestMacAddr, EUI64_MAC_ADDRESS_LENGTH)
        }

        return bestMacAddr
    }

    /**
     * Returns the result of [bestAvailableMac] if non-null otherwise returns a random EUI-64 MAC address.
     */
    @JvmStatic
    fun defaultMachineId(): ByteArray {
        var bestMacAddr = bestAvailableMac()
        if (bestMacAddr == null) {
            bestMacAddr = ByteArray(EUI64_MAC_ADDRESS_LENGTH)
            ThreadLocalRandom.current().nextBytes(bestMacAddr)
            logger.warn(
                "Failed to find a usable hardware address from the network interfaces; using random bytes: {}",
                formatAddress(bestMacAddr)
            )
        }
        return bestMacAddr
    }

    /**
     * Parse a EUI-48, MAC-48, or EUI-64 MAC address from a [String] and return it as a [ByteArray].
     * @param value The string representation of the MAC address.
     * @return The byte representation of the MAC address.
     */
    @JvmStatic
    fun parseMAC(value: String): ByteArray {
        val machineId: ByteArray
        val separator: Char
        when (value.length) {
            17 -> {
                separator = value[2]
                validateMacSeparator(separator)
                machineId = ByteArray(EUI48_MAC_ADDRESS_LENGTH)
            }
            23 -> {
                separator = value[2]
                validateMacSeparator(separator)
                machineId = ByteArray(EUI64_MAC_ADDRESS_LENGTH)
            }
            else -> throw IllegalArgumentException("value is not supported [MAC-48, EUI-48, EUI-64]")
        }

        val end = machineId.size - 1
        var j = 0
        for (i in 0 until end) {
            val sIndex = j + 2
            machineId[i] = StringUtil.decodeHexByte(value, j)
            if (value[sIndex] != separator) {
                throw IllegalArgumentException("expected separator '$separator but got '${value[sIndex]}' at index: $sIndex")
            }
            j += 3
        }

        machineId[end] = StringUtil.decodeHexByte(value, j)

        return machineId
    }

    private fun validateMacSeparator(separator: Char) {
        if (separator != ':' && separator != '-') {
            throw IllegalArgumentException("unsupported separator: $separator (expected: [:-])")
        }
    }

    /**
     * @param addr byte array of a MAC address.
     * @return hex formatted MAC address.
     */
    @JvmStatic
    fun formatAddress(addr: ByteArray): String {
        val buf = StringBuilder(24)
        for (b in addr) {
            buf.append(String.format("%02x:", b.toInt() and 0xff))
        }
        return buf.substring(0, buf.length - 1)
    }

    /**
     * @return positive - current is better, 0 - cannot tell from MAC addr, negative - candidate is better.
     */
    // visible for testing
    @JvmStatic
    fun compareAddresses(current: ByteArray, candidate: ByteArray?): Int {
        if (candidate == null || candidate.size < EUI48_MAC_ADDRESS_LENGTH) {
            return 1
        }

        // Must not be filled with only 0 and 1.
        var onlyZeroAndOne = true
        for (b in candidate) {
            if (b.toInt() != 0 && b.toInt() != 1) {
                onlyZeroAndOne = false
                break
            }
        }

        if (onlyZeroAndOne) {
            return 1
        }

        // Must not be a multicast address
        if (candidate[0].toInt() and 1 != 0) {
            return 1
        }

        // Prefer globally unique address.
        if (candidate[0].toInt() and 2 == 0) {
            return if (current.isNotEmpty() && current[0].toInt() and 2 == 0) {
                // Both current and candidate are globally unique addresses.
                0
            } else {
                // Only candidate is globally unique.
                -1
            }
        } else {
            return if (current.isNotEmpty() && current[0].toInt() and 2 == 0) {
                // Only current is globally unique.
                1
            } else {
                // Both current and candidate are non-unique.
                0
            }
        }
    }

    /**
     * @return positive - current is better, 0 - cannot tell, negative - candidate is better
     */
    private fun compareAddresses(current: InetAddress, candidate: InetAddress): Int {
        return scoreAddress(current) - scoreAddress(candidate)
    }

    private fun scoreAddress(addr: InetAddress): Int {
        if (addr.isAnyLocalAddress || addr.isLoopbackAddress) {
            return 0
        }
        if (addr.isMulticastAddress) {
            return 1
        }
        if (addr.isLinkLocalAddress) {
            return 2
        }
        if (addr.isSiteLocalAddress) {
            return 3
        }
        return 4
    }
}
