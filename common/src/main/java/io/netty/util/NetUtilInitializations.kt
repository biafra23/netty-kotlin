/*
 * Copyright 2020 The Netty Project
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

import io.netty.util.internal.PlatformDependent
import io.netty.util.internal.SocketUtils
import io.netty.util.internal.logging.InternalLoggerFactory
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.Collections

internal object NetUtilInitializations {
    /**
     * The logger being used by this class
     */
    private val logger = InternalLoggerFactory.getInstance(NetUtilInitializations::class.java)

    @JvmStatic
    fun createLocalhost4(): Inet4Address {
        val LOCALHOST4_BYTES = byteArrayOf(127, 0, 0, 1)

        var localhost4: Inet4Address? = null
        try {
            localhost4 = InetAddress.getByAddress("localhost", LOCALHOST4_BYTES) as Inet4Address
        } catch (e: Exception) {
            // We should not get here as long as the length of the address is correct.
            PlatformDependent.throwException(e)
        }

        return localhost4!!
    }

    @JvmStatic
    fun createLocalhost6(): Inet6Address {
        val LOCALHOST6_BYTES = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)

        var localhost6: Inet6Address? = null
        try {
            localhost6 = InetAddress.getByAddress("localhost", LOCALHOST6_BYTES) as Inet6Address
        } catch (e: Exception) {
            // We should not get here as long as the length of the address is correct.
            PlatformDependent.throwException(e)
        }

        return localhost6!!
    }

    @JvmStatic
    fun networkInterfaces(): Collection<NetworkInterface> {
        val networkInterfaces = ArrayList<NetworkInterface>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    networkInterfaces.add(interfaces.nextElement())
                }
            }
        } catch (e: SocketException) {
            logger.warn("Failed to retrieve the list of available network interfaces", e)
        } catch (e: NullPointerException) {
            if (!PlatformDependent.isAndroid()) {
                throw e
            }
            // Might happen on earlier version of Android.
            // See https://developer.android.com/reference/java/net/NetworkInterface#getNetworkInterfaces()
        }
        return Collections.unmodifiableList(networkInterfaces)
    }

    @JvmStatic
    fun determineLoopback(
        networkInterfaces: Collection<NetworkInterface>,
        localhost4: Inet4Address,
        localhost6: Inet6Address
    ): NetworkIfaceAndInetAddress {
        // Retrieve the list of available network interfaces.
        val ifaces = ArrayList<NetworkInterface>()
        for (iface in networkInterfaces) {
            // Use the interface with proper INET addresses only.
            if (SocketUtils.addressesFromNetworkInterface(iface).hasMoreElements()) {
                ifaces.add(iface)
            }
        }

        // Find the first loopback interface available from its INET address (127.0.0.1 or ::1)
        // Note that we do not use NetworkInterface.isLoopback() in the first place because it takes long time
        // on a certain environment. (e.g. Windows with -Djava.net.preferIPv4Stack=true)
        var loopbackIface: NetworkInterface? = null
        var loopbackAddr: InetAddress? = null
        loop@ for (iface in ifaces) {
            val i = SocketUtils.addressesFromNetworkInterface(iface)
            while (i.hasMoreElements()) {
                val addr = i.nextElement()
                if (addr.isLoopbackAddress) {
                    // Found
                    loopbackIface = iface
                    loopbackAddr = addr
                    break@loop
                }
            }
        }

        // If failed to find the loopback interface from its INET address, fall back to isLoopback().
        if (loopbackIface == null) {
            try {
                for (iface in ifaces) {
                    if (iface.isLoopback) {
                        val i = SocketUtils.addressesFromNetworkInterface(iface)
                        if (i.hasMoreElements()) {
                            // Found the one with INET address.
                            loopbackIface = iface
                            loopbackAddr = i.nextElement()
                            break
                        }
                    }
                }

                if (loopbackIface == null) {
                    logger.warn("Failed to find the loopback interface")
                }
            } catch (e: SocketException) {
                logger.warn("Failed to find the loopback interface", e)
            }
        }

        if (loopbackIface != null) {
            // Found the loopback interface with an INET address.
            logger.debug(
                "Loopback interface: {} ({}, {})",
                loopbackIface.name, loopbackIface.displayName, loopbackAddr!!.hostAddress
            )
        } else {
            // Could not find the loopback interface, but we can't leave LOCALHOST as null.
            // Use LOCALHOST6 or LOCALHOST4, preferably the IPv6 one.
            if (loopbackAddr == null) {
                try {
                    if (NetworkInterface.getByInetAddress(localhost6) != null) {
                        logger.debug("Using hard-coded IPv6 localhost address: {}", localhost6)
                        loopbackAddr = localhost6
                    }
                } catch (_: Exception) {
                    // Ignore
                } finally {
                    if (loopbackAddr == null) {
                        logger.debug("Using hard-coded IPv4 localhost address: {}", localhost4)
                        loopbackAddr = localhost4
                    }
                }
            }
        }

        return NetworkIfaceAndInetAddress(loopbackIface, loopbackAddr!!)
    }

    class NetworkIfaceAndInetAddress internal constructor(
        private val iface: NetworkInterface?,
        private val address: InetAddress
    ) {
        fun iface(): NetworkInterface? {
            return iface
        }

        fun address(): InetAddress {
            return address
        }
    }
}
