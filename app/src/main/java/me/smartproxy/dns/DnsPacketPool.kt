package me.smartproxy.dns

import me.smartproxy.core.Pool

object DnsPacketPool : Pool<DnsPacket>() {
    override fun create(): DnsPacket {
        return DnsPacket()
    }
}