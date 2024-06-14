package me.smartproxy.core

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.smartproxy.dns.DnsPacket
import me.smartproxy.tcpip.IPHeader
import me.smartproxy.tcpip.UDPHeader
import org.koin.core.context.GlobalContext

object DnsProxyHelper {

    private var dnsProxy: DnsProxy? = null
    suspend fun startDnsProxy(config: ProxyConfig) {
        val service1: LocalVpnService = GlobalContext.get().getScope(LocalVpnService::class.java.name).get()

        Log.d("DnsProxyHelper", "startDnsProxy: $service1")
        withContext(Dispatchers.IO) {
            if (dnsProxy == null) {
                dnsProxy = DnsProxy(config)
            }
            dnsProxy?.start(service1)
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