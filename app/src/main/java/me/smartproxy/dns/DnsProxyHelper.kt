package me.smartproxy.dns

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.smartproxy.core.ProxyConfig
import me.smartproxy.tcpip.IPHeader
import me.smartproxy.tcpip.UDPHeader
import me.smartproxy.ui.utils.Logger

object DnsProxyHelper {

    private var dnsProxy: DnsProxy? = null
    suspend fun startDnsProxy(config: ProxyConfig) {

        Logger.d("DnsProxyHelper", "startDnsProxy")
        withContext(Dispatchers.IO) {
            if (dnsProxy == null) {
                dnsProxy = DnsProxy(config)
            }
            dnsProxy?.start()
        }
    }

    fun isRunning(): Boolean {
        return dnsProxy?.stopped == false
    }

    fun stopDnsProxy() {
        dnsProxy?.stop()
        dnsProxy = null
    }

    fun onDnsRequestReceived(ipHeader: IPHeader, udpHeader: UDPHeader, dnsPacket: DnsPacket) {
        dnsProxy?.onDnsRequestReceived(ipHeader, udpHeader, dnsPacket)
    }
}