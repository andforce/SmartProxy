package me.smartproxy.core

class NatSession {
    var lastNanoTime: Long = 0
    var remoteIP: Int = 0
    var remotePort: Short = 0
    var remoteHost: String? = null

    var bytesSent: Int = 0
    var packetSent: Int = 0

    fun clear() {
        lastNanoTime = 0
        remoteIP = 0
        remotePort = 0
        remoteHost = null
        bytesSent = 0
        packetSent = 0
    }
}
