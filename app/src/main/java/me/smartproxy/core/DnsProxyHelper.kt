package me.smartproxy.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.smartproxy.dns.DnsPacket
import me.smartproxy.tcpip.IPHeader
import me.smartproxy.tcpip.UDPHeader

object DnsProxyHelper {

    private var dnsProxy: DnsProxy? = null
    suspend fun startDnsProxy(config: ProxyConfig) {
        withContext(Dispatchers.IO) {
            if (dnsProxy == null) {
                dnsProxy = DnsProxy(config)
            }
            dnsProxy?.start()
        }
    }

    fun isRunning(): Boolean {
        return dnsProxy?.Stopped == false
    }

    fun stopDnsProxy() {
        dnsProxy?.stop()
        dnsProxy = null
    }

    fun onDnsRequestReceived(ipHeader: IPHeader?, udpHeader: UDPHeader?, dnsPacket: DnsPacket?) {
        dnsProxy?.onDnsRequestReceived(ipHeader, udpHeader, dnsPacket)
    }
}