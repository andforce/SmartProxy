package me.smartproxy.core

class NatSession {
    var remoteIP: Int = 0
    var remotePort: Short = 0
    var remoteHost: String? = null
    var bytesSent: Int = 0
    var packetSent: Int = 0
    var lastNanoTime: Long = 0
}
