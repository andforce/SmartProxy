package me.smartproxy.dns

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.smartproxy.core.ProxyConfig
import me.smartproxy.tcpip.IPHeader
import me.smartproxy.tcpip.UDPHeader

object DnsProxyHelper {

    private var dnsProxy: DnsProxy? = null
    suspend fun startDnsProxy(config: ProxyConfig) {

        Log.d("DnsProxyHelper", "startDnsProxy")
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