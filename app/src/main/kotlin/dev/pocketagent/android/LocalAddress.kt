package dev.pocketagent.android

import java.net.Inet4Address
import java.net.NetworkInterface

/** The phone's address on the local network (Wi-Fi), or null when it has none. */
object LocalAddress {
    fun find(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    } catch (e: java.net.SocketException) {
        null
    }
}
