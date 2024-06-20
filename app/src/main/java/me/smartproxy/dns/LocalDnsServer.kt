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

class LocalDnsServer(private val config: ProxyConfig, buffer: ByteArray, private val vpnLocalIpInt: Int) {

    private val udpHeader = UDPHeader(buffer, 20)
    private val dnsBuffer: ByteBuffer =
        (ByteBuffer.wrap(buffer).position(28) as ByteBuffer).slice()


    private val localVpnViewModel = get<LocalVpnViewModel>(LocalVpnViewModel::class.java)

    var stopped: Boolean = false
    private var datagramSocket: DatagramSocket?
    private var queryID: Short = 0
    private val queryArray = SparseArray<QueryState>()

    init {
        datagramSocket = DatagramSocket(0)
    }

    fun stop() {
        stopped = true
        datagramSocket?.close()
        datagramSocket = null
    }

    fun start() {
        try {
            datagramSocket?.let { dSocket->
                val service = LocalVpnService::class.getOrNull()
                val protect = service?.protect(dSocket)

                Logger.d(TAG, "DNS Proxy, protect result: $protect")

                val receiveBuffer = ByteArray(20000)
                val ipHeader = IPHeader(receiveBuffer, 0)
                ipHeader.defaultHeader()
                val udpHeader = UDPHeader(receiveBuffer, 20)

                var dnsBuffer = ByteBuffer.wrap(receiveBuffer)
                dnsBuffer.position(28)
                dnsBuffer = dnsBuffer.slice()

                val packet = DatagramPacket(receiveBuffer, 28, receiveBuffer.size - 28)

                while (dSocket.isClosed.not()) {
                    packet.length = receiveBuffer.size - 28
                    Logger.d(TAG, "阻塞等待，接受 UDP 数据包...")
                    dSocket.receive(packet)

                    dnsBuffer.clear()
                    dnsBuffer.limit(packet.length)
                    try {
                        DnsPacket.takeFromPoll(dnsBuffer)?.let {
                            Logger.d(TAG, "从远端真实的DNS服务器接收到 UDP 数据包，开始处理。 $ipHeader $udpHeader $it")
                            onReceiveUdpFromRemoteServer(ipHeader, udpHeader, it)
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

    private fun onReceiveUdpFromRemoteServer(
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

        state?.let {
            //DNS污染，默认污染海外网站
            dnsPollution(udpHeader.data, dnsPacket)

            dnsPacket.dnsHeader.ID = it.clientQueryID

            ipHeader.sourceIP = it.remoteIP
            ipHeader.destinationIP = it.clientIP
            ipHeader.protocol = IPData.UDP
            ipHeader.totalLength = 20 + 8 + dnsPacket.size

            udpHeader.sourcePort = it.remotePort
            udpHeader.destinationPort = it.clientPort
            udpHeader.totalLength = 8 + dnsPacket.size


            Logger.d(TAG, "DNS back sendUDPPacket() ${ipHeader.debugInfo(udpHeader.sourcePortInt, udpHeader.destinationPortInt)} $udpHeader")

            localVpnViewModel.sendUDPPacket(ipHeader, udpHeader)
        } ?: run {
            Logger.d(TAG, "DNS back state is null.")
        }
    }

    fun processUdpPacket(header: IPHeader) {
        udpHeader.offset = header.headerLength

        Logger.i(
            TAG,
            "processUdpPacket(), 从ips读取到UDP包, ${header.debugInfo(udpHeader.sourcePortInt, udpHeader.destinationPortInt)} $udpHeader")

        if (header.sourceIP == vpnLocalIpInt && udpHeader.destinationPort.toInt() == 53) {
            dnsBuffer.clear()
            dnsBuffer.limit(header.dataLength - 8)
            val dnsPacket = DnsPacket.takeFromPoll(dnsBuffer)
            Logger.i(
                TAG,
                "processUdpPacket(), 构建 DnsPacket.takeFromPoll"
            )
            dnsPacket?.let {
                if (dnsPacket.dnsHeader.questionCount > 0) {
                    Logger.i(
                        TAG,
                        "processUdpPacket(), 交给 onReadUdpFromVPNInputStream() 处理"
                    )

                    this.onReadUdpFromVPNInputStream(header, udpHeader, dnsPacket)
                } else {
                    Logger.e(
                        TAG,
                        "processUdpPacket(), UDP: DNS数据包无问题, questionCount is 0"
                    )
                }
            } ?: run {
                Logger.e(
                    TAG,
                    "processUdpPacket(), UDP: DNS数据包解析失败"
                )
            }
        } else {
            Logger.e(
                TAG,
                "processUdpPacket(), UDP: 收到非本地数据包, $header $udpHeader"
            )
        }
    }

    private fun getFirstIP(dnsPacket: DnsPacket): Int {

        for (i in 0 until dnsPacket.dnsHeader.resourceCount) {
            val resource = dnsPacket.resources[i]
            if (resource.type == Question.A_RECORD) {
                val ipInt = CommonMethods.readInt(resource.data, 0)
                Logger.d(TAG, "getFirstIP(), ip $i : ${CommonMethods.ipIntToString(ipInt)}")
            }
        }

        for (i in 0 until dnsPacket.dnsHeader.resourceCount) {
            val resource = dnsPacket.resources[i]
            if (resource.type == Question.A_RECORD) {
                return CommonMethods.readInt(resource.data, 0)
            }
        }
        return 0
    }

    private fun modifyDnsResponse(rawPacket: ByteArray, dnsPacket: DnsPacket, fakeIP: Int) {
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
        rPointer.ip = fakeIP

        dnsPacket.size = 12 + question.length + 16

        Logger.d(TAG, "modifyDnsResponse(), 开始修改 fakeIP: ${question.domain}=>${CommonMethods.ipIntToString(fakeIP)}")
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

            Logger.d(TAG, "getOrCreateFakeIP(), create new FakeDns: $domainString=>" + CommonMethods.ipIntToString(fakeIP))
        } else {
            Logger.d(TAG, "getOrCreateFakeIP(), FakeDns: $domainString=>" + CommonMethods.ipIntToString(fakeIP))
        }
        return fakeIP
    }

    private fun dnsPollution(rawPacket: ByteArray, dnsPacket: DnsPacket): Boolean {
        Logger.d(TAG, "开始处理DNS污染问题。。。")

        if (dnsPacket.dnsHeader.questionCount > 0) {
            val question = dnsPacket.questions[0]
            val isARecord = question.type == Question.A_RECORD

            Logger.d(TAG, "dnsPollution, isARecord:$isARecord,  查询的域名:${question.domain}")

            if (isARecord) {
                val realIP = getFirstIP(dnsPacket)
                val isNeedProxy = config.needProxy(question.domain, realIP)

                Logger.d(TAG, "dnsPollution, real DNS IP: ${CommonMethods.ipIntToString(realIP)}, isNeedProxy: $isNeedProxy")

                if (isNeedProxy) {
                    val fakeIP = getOrCreateFakeIP(question.domain)
                    modifyDnsResponse(rawPacket, dnsPacket, fakeIP)
                    Logger.d(
                        TAG,
                        "FakeDns: " + question.domain + "=>" + CommonMethods.ipIntToString(realIP) + "(" + CommonMethods.ipIntToString(
                            fakeIP
                        ) + ")"
                    )
                    return true
                }
            }
        } else {
            Logger.d(TAG, "dnsPollution, questionCount is 0")
        }
        return false
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
        val isARecord = question.type == Question.A_RECORD
        Logger.d(TAG, "interceptDns() " + question.domain + " type: " + question.type + " isARecord: " + isARecord)

        if (isARecord) {
            val needProxy = config.needProxy(question.domain, getIPFromCache(question.domain))
            Logger.d(TAG, "interceptDns() " + question.domain + " needProxy: " + needProxy)

            if (needProxy) {

                val fakeIP = getOrCreateFakeIP(question.domain)
                modifyDnsResponse(ipHeader.data, dnsPacket, fakeIP)
                Logger.d(
                    TAG,
                    "interceptDns() FakeDns: " + question.domain + "=>" + CommonMethods.ipIntToString(
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

                Logger.d(TAG, "interceptDns() 把构建的UDP包发给 VPN，sendUDPPacket() ${ipHeader.debugInfo(udpHeader.sourcePortInt, udpHeader.destinationPortInt)} $udpHeader")
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

    private fun onReadUdpFromVPNInputStream(ipHeader: IPHeader, udpHeader: UDPHeader, dnsPacket: DnsPacket) {

        Logger.d(TAG, "onReadUdpFromVPNInputStream(), 进入 interceptDns(), 看是否需要拦截。 ${ipHeader.debugInfo(udpHeader.sourcePortInt, udpHeader.destinationPortInt)} $udpHeader")

        val intercept = interceptDns(ipHeader, udpHeader, dnsPacket)

        Logger.d(TAG, "onReadUdpFromVPNInputStream(), intercept: $intercept")

        if (!intercept) {
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
                Logger.d(TAG, "onReadUdpFromVPNInputStream(), 通过 DatagramSocket 发送到远端真实的DNS服务器, 并等待数据返回。 ${ipHeader.debugInfo(udpHeader.sourcePortInt, udpHeader.destinationPortInt)} $udpHeader")
                datagramSocket?.send(packet)

            } catch (e: IOException) {
                Logger.e(TAG, "DNS Request Error: ", e)
            }
        }
    }

    companion object {
        private const val TAG = "LocalDnsServer"
        private val IPDomainMaps = ConcurrentHashMap<Int, String>()
        private val DomainIPMaps = ConcurrentHashMap<String, Int>()
        fun reverseLookup(ip: Int): String? {
            return IPDomainMaps[ip]
        }
    }
}
