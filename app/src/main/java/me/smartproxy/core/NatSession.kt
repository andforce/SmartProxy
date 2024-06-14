package me.smartproxy.core

class NatSession {
    @JvmField
    var RemoteIP: Int = 0
    @JvmField
    var RemotePort: Short = 0
    @JvmField
    var RemoteHost: String? = null
    var BytesSent: Int = 0
    var PacketSent: Int = 0
    @JvmField
    var LastNanoTime: Long = 0
}
