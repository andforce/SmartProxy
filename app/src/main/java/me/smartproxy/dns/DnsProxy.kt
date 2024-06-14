package me.smartproxy.dns

import android.net.VpnService
import android.util.Log
import android.util.SparseArray
import me.smartproxy.core.ProxyConfig
import me.smartproxy.core.QueryState
import me.smartproxy.core.viewmodel.LocalVpnViewModel
import me.smartproxy.tcpip.CommonMethods
import me.smartproxy.tcpip.IPHeader
import me.smartproxy.tcpip.UDPHeader
import org.koin.java.KoinJavaComponent.get
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

class DnsProxy(private val config: ProxyConfig) {
    private val localVpnViewModel = get<LocalVpnViewModel>(LocalVpnViewModel::class.java)

    var stopped: Boolean = false
    private var client: DatagramSocket?
    private var queryID: Short = 0
    private val queryArray = SparseArray<QueryState>()

    init {
        client = DatagramSocket(0)
    }

    fun stop() {
        stopped = true
        client?.close()
        client = null
    }

    fun start(service: VpnService) {
        try {
            client?.let { client->
                val protect = localVpnViewModel.protect(service, client)

                Log.e(TAG, "DNS Proxy, protect result: $protect")

                val receiveBuffer = ByteArray(2000)
                val ipHeader = IPHeader(receiveBuffer, 0)
                ipHeader.Default()
                val udpHeader = UDPHeader(receiveBuffer, 20)

                var dnsBuffer = ByteBuffer.wrap(receiveBuffer)
                dnsBuffer.position(28)
                dnsBuffer = dnsBuffer.slice()

                val packet = DatagramPacket(receiveBuffer, 28, receiveBuffer.size - 28)

                while (this.client != null && client.isClosed.not()) {
                    packet.length = receiveBuffer.size - 28
                    client.receive(packet)

                    dnsBuffer.clear()
                    dnsBuffer.limit(packet.length)
                    try {
                        val dnsPacket = DnsPacket.FromBytes(dnsBuffer)
                        if (dnsPacket != null) {
                            onDnsResponseReceived(ipHeader, udpHeader, dnsPacket)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Parse DNS Packet Error: ", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "DnsResolver Error: ", e)
        } finally {
            Log.e(TAG, "DnsResolver Thread Exited.")
            this.stop()
        }
    }

    private fun getFirstIP(dnsPacket: DnsPacket): Int {
        for (i in 0 until dnsPacket.Header.ResourceCount) {
            val resource = dnsPacket.Resources[i]
            if (resource.Type.toInt() == 1) {
                return CommonMethods.readInt(resource.Data, 0)
            }
        }
        return 0
    }

    private fun tamperDnsResponse(rawPacket: ByteArray, dnsPacket: DnsPacket, newIP: Int) {
        val question = dnsPacket.Questions[0]

        dnsPacket.Header.resourceCount = 1.toShort()
        dnsPacket.Header.aResourceCount = 0.toShort()
        dnsPacket.Header.eResourceCount = 0.toShort()

        val rPointer = ResourcePointer(rawPacket, question.Offset() + question.Length())
        rPointer.setDomain(0xC00C.toShort())
        rPointer.type = question.Type
        rPointer.setClass(question.Class)
        rPointer.ttl = config.dnsTTL
        rPointer.dataLength = 4.toShort()
        rPointer.ip = newIP

        dnsPacket.Size = 12 + question.Length() + 16
    }

    private fun getOrCreateFakeIP(domainString: String): Int {
        var fakeIP = DomainIPMaps[domainString]

        if (fakeIP == null) {
            var hashIP = domainString.hashCode()
            do {
                fakeIP = ProxyConfig.FAKE_NETWORK_IP or (hashIP and 0x0000FFFF)
                hashIP++
            } while (IPDomainMaps.containsKey(fakeIP))

            DomainIPMaps[domainString] = fakeIP!!
            IPDomainMaps[fakeIP] = domainString
        }
        return fakeIP
    }

    private fun dnsPollution(rawPacket: ByteArray, dnsPacket: DnsPacket): Boolean {
        if (dnsPacket.Header.QuestionCount > 0) {
            val question = dnsPacket.Questions[0]
            if (question.Type.toInt() == 1) {
                val realIP = getFirstIP(dnsPacket)
                if (config.needProxy(question.Domain, realIP)) {
                    val fakeIP = getOrCreateFakeIP(question.Domain)
                    tamperDnsResponse(rawPacket, dnsPacket, fakeIP)
                    Log.d(
                        TAG,
                        "FakeDns: " + question.Domain + "=>" + CommonMethods.ipIntToString(realIP) + "(" + CommonMethods.ipIntToString(
                            fakeIP
                        ) + ")"
                    )
                    return true
                }
            }
        }
        return false
    }

    private fun onDnsResponseReceived(
        ipHeader: IPHeader,
        udpHeader: UDPHeader,
        dnsPacket: DnsPacket
    ) {
        var state: QueryState?
        synchronized(queryArray) {
            state = queryArray[dnsPacket.Header.ID.toInt()]
            if (state != null) {
                queryArray.remove(dnsPacket.Header.ID.toInt())
            }
        }

        if (state != null) {
            //DNS污染，默认污染海外网站
            dnsPollution(udpHeader.m_Data, dnsPacket)

            dnsPacket.Header.id = state!!.ClientQueryID
            ipHeader.sourceIP = state!!.RemoteIP
            ipHeader.destinationIP = state!!.ClientIP
            ipHeader.protocol = IPHeader.UDP
            ipHeader.totalLength = 20 + 8 + dnsPacket.Size
            udpHeader.sourcePort = state!!.RemotePort
            udpHeader.destinationPort = state!!.ClientPort
            udpHeader.totalLength = 8 + dnsPacket.Size

            localVpnViewModel.sendUDPPacket(ipHeader, udpHeader)
        }
    }

    private fun getIPFromCache(domain: String): Int {
        val ip = DomainIPMaps[domain]
        return ip ?: 0
    }

    private fun interceptDns(
        ipHeader: IPHeader,
        udpHeader: UDPHeader,
        dnsPacket: DnsPacket
    ): Boolean {
        val question = dnsPacket.Questions[0]
        Log.d(TAG, "DNS Qeury " + question.Domain)
        if (question.Type.toInt() == 1) {
            if (config.needProxy(question.Domain, getIPFromCache(question.Domain))) {
                val fakeIP = getOrCreateFakeIP(question.Domain)
                tamperDnsResponse(ipHeader.m_Data, dnsPacket, fakeIP)

                Log.d(
                    TAG,
                    "interceptDns FakeDns: " + question.Domain + "=>" + CommonMethods.ipIntToString(
                        fakeIP
                    )
                )

                val sourceIP = ipHeader.sourceIP
                val sourcePort = udpHeader.sourcePort
                ipHeader.sourceIP = ipHeader.destinationIP
                ipHeader.destinationIP = sourceIP
                ipHeader.totalLength = 20 + 8 + dnsPacket.Size
                udpHeader.sourcePort = udpHeader.destinationPort
                udpHeader.destinationPort = sourcePort
                udpHeader.totalLength = 8 + dnsPacket.Size
                localVpnViewModel.sendUDPPacket(ipHeader, udpHeader)
                return true
            }
        }
        return false
    }

    private fun clearExpiredQueries() {
        val now = System.nanoTime()
        val timeOutNS = 10 * 1000000000L

        for (i in queryArray.size() - 1 downTo 0) {
            val state = queryArray.valueAt(i)
            if ((now - state.QueryNanoTime) > timeOutNS) {
                queryArray.removeAt(i)
            }
        }
    }

    fun onDnsRequestReceived(ipHeader: IPHeader, udpHeader: UDPHeader, dnsPacket: DnsPacket) {
        if (!interceptDns(ipHeader, udpHeader, dnsPacket)) {
            //转发DNS
            val state = QueryState()
            state.ClientQueryID = dnsPacket.Header.ID
            state.QueryNanoTime = System.nanoTime()
            state.ClientIP = ipHeader.sourceIP
            state.ClientPort = udpHeader.sourcePort
            state.RemoteIP = ipHeader.destinationIP
            state.RemotePort = udpHeader.destinationPort

            // 转换QueryID
            queryID++ // 增加ID
            dnsPacket.Header.id = queryID

            synchronized(queryArray) {
                clearExpiredQueries() //清空过期的查询，减少内存开销。
                queryArray.put(queryID.toInt(), state) // 关联数据
            }

            val remoteAddress = InetSocketAddress(
                CommonMethods.ipIntToInet4Address(state.RemoteIP),
                state.RemotePort.toInt()
            )
            val packet = DatagramPacket(udpHeader.m_Data, udpHeader.m_Offset + 8, dnsPacket.Size)
            packet.socketAddress = remoteAddress

            try {
                client!!.send(packet)
            } catch (e: IOException) {
                Log.e(TAG, "onDnsRequestReceived Error: ", e)
            }
        }
    }

    companion object {
        private const val TAG = "DnsProxy"
        private val IPDomainMaps = ConcurrentHashMap<Int, String>()
        private val DomainIPMaps = ConcurrentHashMap<String, Int>()
        @JvmStatic
        fun reverseLookup(ip: Int): String? {
            return IPDomainMaps[ip]
        }
    }
}
