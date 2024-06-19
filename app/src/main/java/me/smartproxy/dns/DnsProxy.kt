package me.smartproxy.dns

import android.util.SparseArray
import me.smartproxy.core.LocalVpnService
import me.smartproxy.core.ProxyConfig
import me.smartproxy.core.QueryState
import me.smartproxy.core.getOrNull
import me.smartproxy.core.viewmodel.LocalVpnViewModel
import me.smartproxy.tcpip.CommonMethods
import me.smartproxy.tcpip.IPData
import me.smartproxy.tcpip.IPHeader
import me.smartproxy.tcpip.UDPHeader
import me.smartproxy.ui.utils.Logger
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

    fun start() {
        try {
            client?.let { client->
                val service = LocalVpnService::class.getOrNull()
                val protect = service?.protect(client)

                Logger.e(TAG, "DNS Proxy, protect result: $protect")

                val receiveBuffer = ByteArray(2000)
                val ipHeader = IPHeader(receiveBuffer, 0)
                ipHeader.defaultHeader()
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
                        DnsPacket.takeFromPoll(dnsBuffer)?.let {
                            onDnsRequestReceived(ipHeader, udpHeader, it)
                            // 放入池中
                            DnsPacket.recycle(it)
                        } ?: run {
                            Logger.e(TAG, "Parse DNS Packet Error.")
                        }
                    } catch (e: Exception) {
                        Logger.e(TAG, "Parse DNS Packet Error: ", e)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "DnsResolver Error: ", e)
        } finally {
            Logger.e(TAG, "DnsResolver Thread Exited.")
            this.stop()
        }
    }

    private fun getFirstIP(dnsPacket: DnsPacket): Int {
        for (i in 0 until dnsPacket.dnsHeader.resourceCount) {
            val resource = dnsPacket.resources[i]
            if (resource.type.toInt() == 1) {
                return CommonMethods.readInt(resource.data, 0)
            }
        }
        return 0
    }

    private fun tamperDnsResponse(rawPacket: ByteArray, dnsPacket: DnsPacket, newIP: Int) {
        val question = dnsPacket.questions[0]

        dnsPacket.dnsHeader.resourceCount = 1.toShort()
        dnsPacket.dnsHeader.aResourceCount = 0.toShort()
        dnsPacket.dnsHeader.eResourceCount = 0.toShort()

        val rPointer = ResourcePointer(rawPacket, question.offset + question.length)
        rPointer.setDomain(0xC00C.toShort())
        rPointer.type = question.type
        rPointer.setClass(question.clazz)
        rPointer.ttl = config.dnsTTL
        rPointer.dataLength = 4.toShort()
        rPointer.ip = newIP

        dnsPacket.size = 12 + question.length + 16
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
        if (dnsPacket.dnsHeader.questionCount > 0) {
            val question = dnsPacket.questions[0]
            if (question.type.toInt() == 1) {
                val realIP = getFirstIP(dnsPacket)
                if (config.needProxy(question.domain, realIP)) {
                    val fakeIP = getOrCreateFakeIP(question.domain)
                    tamperDnsResponse(rawPacket, dnsPacket, fakeIP)
                    Logger.d(
                        TAG,
                        "FakeDns: " + question.domain + "=>" + CommonMethods.ipIntToString(realIP) + "(" + CommonMethods.ipIntToString(
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
            state = queryArray[dnsPacket.dnsHeader.ID.toInt()]
            if (state != null) {
                queryArray.remove(dnsPacket.dnsHeader.ID.toInt())
            }
        }

        if (state != null) {
            //DNS污染，默认污染海外网站
            dnsPollution(udpHeader.data, dnsPacket)

            dnsPacket.dnsHeader.ID = state!!.clientQueryID
            ipHeader.sourceIP = state!!.remoteIP
            ipHeader.destinationIP = state!!.clientIP
            ipHeader.protocol = IPData.UDP
            ipHeader.totalLength = 20 + 8 + dnsPacket.size
            udpHeader.sourcePort = state!!.remotePort
            udpHeader.destinationPort = state!!.clientPort
            udpHeader.totalLength = 8 + dnsPacket.size

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
        val question = dnsPacket.questions[0]
        Logger.d(TAG, "DNS Qeury " + question.domain)
        if (question.type.toInt() == 1) {
            if (config.needProxy(question.domain, getIPFromCache(question.domain))) {
                val fakeIP = getOrCreateFakeIP(question.domain)
                tamperDnsResponse(ipHeader.data, dnsPacket, fakeIP)

                Logger.d(
                    TAG,
                    "interceptDns FakeDns: " + question.domain + "=>" + CommonMethods.ipIntToString(
                        fakeIP
                    )
                )

                val sourceIP = ipHeader.sourceIP
                val sourcePort = udpHeader.sourcePort
                ipHeader.sourceIP = ipHeader.destinationIP
                ipHeader.destinationIP = sourceIP
                ipHeader.totalLength = 20 + 8 + dnsPacket.size
                udpHeader.sourcePort = udpHeader.destinationPort
                udpHeader.destinationPort = sourcePort
                udpHeader.totalLength = 8 + dnsPacket.size
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
            if ((now - state.queryNanoTime) > timeOutNS) {
                queryArray.removeAt(i)
            }
        }
    }

    fun onDnsRequestReceived(ipHeader: IPHeader, udpHeader: UDPHeader, dnsPacket: DnsPacket) {
        if (!interceptDns(ipHeader, udpHeader, dnsPacket)) {
            //转发DNS
            val state = QueryState()
            state.clientQueryID = dnsPacket.dnsHeader.ID
            state.queryNanoTime = System.nanoTime()
            state.clientIP = ipHeader.sourceIP
            state.clientPort = udpHeader.sourcePort
            state.remoteIP = ipHeader.destinationIP
            state.remotePort = udpHeader.destinationPort

            // 转换QueryID
            queryID++ // 增加ID
            dnsPacket.dnsHeader.ID = queryID

            synchronized(queryArray) {
                clearExpiredQueries() //清空过期的查询，减少内存开销。
                queryArray.put(queryID.toInt(), state) // 关联数据
            }

            val remoteAddress = InetSocketAddress(
                CommonMethods.ipIntToInet4Address(state.remoteIP),
                state.remotePort.toInt()
            )
            val packet = DatagramPacket(udpHeader.data, udpHeader.offset + 8, dnsPacket.size)
            packet.socketAddress = remoteAddress

            try {
                client!!.send(packet)
            } catch (e: IOException) {
                Logger.e(TAG, "onDnsRequestReceived Error: ", e)
            }
        }
    }

    companion object {
        private const val TAG = "DnsProxy"
        private val IPDomainMaps = ConcurrentHashMap<Int, String>()
        private val DomainIPMaps = ConcurrentHashMap<String, Int>()
        fun reverseLookup(ip: Int): String? {
            return IPDomainMaps[ip]
        }
    }
}
