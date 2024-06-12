package me.smartproxy.core

import android.content.Context
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import me.smartproxy.R
import me.smartproxy.dns.DNSUtils.findAndroidPropDNS
import me.smartproxy.dns.DNSUtils.isIPv4Address
import me.smartproxy.dns.DnsPacket
import me.smartproxy.tcpip.CommonMethods
import me.smartproxy.tcpip.IPHeader
import me.smartproxy.tcpip.TCPHeader
import me.smartproxy.tcpip.UDPHeader
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramSocket
import java.net.Socket
import java.nio.ByteBuffer

class VpnHelper(private val context: Context, private val service: LocalVpnService) {

    companion object {
        const val TAG = "VpnHelper"
        const val IS_ENABLE_REMOTE_PROXY: Boolean = false
    }

    private var LOCAL_IP = 0
    
    var IsRunning = false
    
    private var m_SentBytes: Long = 0
    private var m_ReceivedBytes: Long = 0
    
    private var m_VPNThread: Thread? = null
    private var m_VPNInterface: ParcelFileDescriptor? = null
    private var m_TcpProxyServer: TcpProxyServer? = null
    private var m_DnsProxy: DnsProxy? = null
    private var m_VPNOutputStream: FileOutputStream? = null
    
    
    private val m_Packet = ByteArray(20000)
    private val m_IPHeader = IPHeader(m_Packet, 0)
    private val m_TCPHeader = TCPHeader(m_Packet, 20)
    private val m_UDPHeader = UDPHeader(m_Packet, 20)
    private val m_DNSBuffer: ByteBuffer =
        (ByteBuffer.wrap(m_Packet).position(28) as ByteBuffer).slice()

    private fun establishVPN(): ParcelFileDescriptor? {
        val builder: VpnService.Builder = service.Builder()
        builder.setMtu(ProxyConfig.Instance.mtu)
        if (ProxyConfig.IS_DEBUG) {
            Log.d(TAG, "setMtu: " + ProxyConfig.Instance.mtu)
        }

        val ipAddress = ProxyConfig.Instance.defaultLocalIP
        LOCAL_IP = CommonMethods.ipStringToInt(ipAddress.Address)
        builder.addAddress(ipAddress.Address, ipAddress.PrefixLength)
        if (ProxyConfig.IS_DEBUG) {
            Log.d(
                TAG,
                "addAddress: " + ipAddress.Address + "/" + ipAddress.PrefixLength
            )
        }

        for (dns in ProxyConfig.Instance.dnsList) {
            builder.addDnsServer(dns.Address)
            if (ProxyConfig.IS_DEBUG) {
                Log.d(TAG, "addDnsServer: " + dns.Address)
            }
        }

        if (ProxyConfig.Instance.routeList.isNotEmpty()) {
            for (routeAddress in ProxyConfig.Instance.routeList) {
                builder.addRoute(routeAddress.Address, routeAddress.PrefixLength)
                if (ProxyConfig.IS_DEBUG) {
                    Log.d(
                        TAG,
                        "addRoute: " + routeAddress.Address + "/" + routeAddress.PrefixLength
                    )
                }
            }
            builder.addRoute(CommonMethods.ipIntToString(ProxyConfig.FAKE_NETWORK_IP), 16)

            if (ProxyConfig.IS_DEBUG) {
                Log.d(
                    TAG,
                    "addRoute: " + CommonMethods.ipIntToString(ProxyConfig.FAKE_NETWORK_IP) + "/16"
                )
            }
        } else {
            builder.addRoute("0.0.0.0", 0)
            if (ProxyConfig.IS_DEBUG) {
                Log.d(TAG, "addDefaultRoute:0.0.0.0/0")
            }
        }

        val dnsMap = findAndroidPropDNS(context)
        for ((name, host) in dnsMap) {
            Log.d(TAG, "findAndroidPropDNS: $name -> $host")

            if (isIPv4Address(host)) {
                builder.addRoute(host, 32)
                if (ProxyConfig.IS_DEBUG) {
                    Log.d(TAG, "addRoute by DNS: $host/32")
                }
            } else {
                Log.d(
                    TAG,
                    "addRoute by DNS, 暂时忽略 IPv6 类型的DNS: $host"
                )
            }
        }

        builder.setSession(ProxyConfig.Instance.sessionName)
        return builder.establish()
    }
    
    fun start() {
        try {
            m_TcpProxyServer = TcpProxyServer(0)
            m_TcpProxyServer?.start()

            m_DnsProxy = DnsProxy()
            m_DnsProxy?.start()
        } catch (e: Exception) {
            Log.e(TAG, "VPNService error: ", e)
        }

        IsRunning = true

        // Start a new session by creating a new thread.
        m_VPNThread = Thread({
            try {
                Log.d(TAG, "VPNService work thread is runing...")

                ChinaIpMaskManager.loadFromFile(context.resources.openRawResource(R.raw.ipmask)) //加载中国的IP段，用于IP分流。
                waitUntilPrepared() //检查是否准备完毕。

                //加载配置文件
                if (IS_ENABLE_REMOTE_PROXY) {
                    try {
                        ProxyConfig.Instance.loadFromUrl(ProxyConfig.ConfigUrl)
                        if (ProxyConfig.Instance.defaultProxy == null) {
                            throw Exception("Invalid config file.")
                        }
                    } catch (e: Exception) {
                        var errString = e.message
                        if (errString == null || errString.isEmpty()) {
                            errString = e.toString()
                        }

                        IsRunning = false
                    }
                }

                establishVPN()?.let {vpnInterface->
                    m_VPNInterface = vpnInterface

                    FileOutputStream(vpnInterface.fileDescriptor).use { fos->
                        m_VPNOutputStream = fos

                        FileInputStream(vpnInterface.fileDescriptor).use {fis->
                            var size: Int
                            while (IsRunning) {
                                size = fis.read(m_Packet)
                                if (size <= 0) {
                                    Thread.sleep(10)
                                    continue
                                }
                                if (m_DnsProxy?.Stopped == true || m_TcpProxyServer?.Stopped == true) {
                                    fis.close()
                                    throw Exception("LocalServer stopped.")
                                }
                                onIPPacketReceived(m_IPHeader, size)
                            }

                            disconnectVPN()
                        }
                    }

                } ?: run {
                    throw Exception("VPNInterface is null.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fatal error: ", e)
            } finally {
                dispose()
            }
        }, "VPNServiceThread")
        m_VPNThread?.start()
    }

    fun stop() {
        IsRunning = false
        if (m_VPNThread != null) {
            m_VPNThread?.interrupt()
        }
    }

    fun sendUDPPacket(ipHeader: IPHeader, udpHeader: UDPHeader?) {
        try {
            CommonMethods.ComputeUDPChecksum(ipHeader, udpHeader)
            m_VPNOutputStream?.write(ipHeader.m_Data, ipHeader.m_Offset, ipHeader.totalLength)
        } catch (e: IOException) {
            Log.e(TAG, "sendUDPPacket: ", e)
        }
    }


    fun disconnectVPN() {
        try {
            if (m_VPNInterface != null) {
                m_VPNInterface?.close()
                m_VPNInterface = null
            }
        } catch (e: java.lang.Exception) {
            // ignore
        }
        this.m_VPNOutputStream = null
    }

    @Synchronized
    private fun dispose() {
        // 断开VPN
        disconnectVPN()

        // 停止TcpServer
        if (m_TcpProxyServer != null) {
            m_TcpProxyServer!!.stop()
            m_TcpProxyServer = null
        }

        // 停止DNS解析器
        if (m_DnsProxy != null) {
            m_DnsProxy!!.stop()
            m_DnsProxy = null
        }

        service.stopSelf()
        IsRunning = false
        System.exit(0)
    }

    @Throws(IOException::class)
    fun onIPPacketReceived(ipHeader: IPHeader, size: Int) {
        when (ipHeader.protocol) {
            IPHeader.TCP -> {
                val tcpHeader = m_TCPHeader
                tcpHeader.m_Offset = ipHeader.headerLength
                if (ipHeader.sourceIP == LOCAL_IP) {
                    // 收到本地 TcpProxyServer 服务器数据
                    if (tcpHeader.sourcePort == m_TcpProxyServer!!.Port) {
                        val session =
                            NatSessionManager.getSession(tcpHeader.destinationPort.toInt())
                        if (session != null) {
                            Log.d(
                                "LocalVpnService",
                                "onIPPacketReceived: 收到本地 TcpProxyServer 服务器数据, $ipHeader $tcpHeader"
                            )
                            ipHeader.sourceIP = ipHeader.destinationIP
                            tcpHeader.sourcePort = session.RemotePort
                            ipHeader.destinationIP = LOCAL_IP

                            CommonMethods.ComputeTCPChecksum(ipHeader, tcpHeader)
                            m_VPNOutputStream?.write(ipHeader.m_Data, ipHeader.m_Offset, size)
                            m_ReceivedBytes += size.toLong()
                        } else {
                            Log.d(
                                TAG,
                                "onIPPacketReceived: NoSession, $ipHeader $tcpHeader"
                            )
                        }
                    } else {
                        // 添加端口映射

                        val portKey = tcpHeader.sourcePort.toInt()
                        var session = NatSessionManager.getSession(portKey)
                        if (session == null || session.RemoteIP != ipHeader.destinationIP || session.RemotePort != tcpHeader.destinationPort) {
                            session = NatSessionManager.createSession(
                                portKey,
                                ipHeader.destinationIP,
                                tcpHeader.destinationPort
                            )
                        }

                        session?.let {
                            session.LastNanoTime = System.nanoTime()
                            session.PacketSent++ //注意顺序

                            val tcpDataSize = ipHeader.dataLength - tcpHeader.headerLength
                            if (session.PacketSent == 2 && tcpDataSize == 0) {
                                return  //丢弃tcp握手的第二个ACK报文。因为客户端发数据的时候也会带上ACK，这样可以在服务器Accept之前分析出HOST信息。
                            }

                            //分析数据，找到host
                            if (session.BytesSent == 0 && tcpDataSize > 10) {
                                val dataOffset = tcpHeader.m_Offset + tcpHeader.headerLength
                                val host = HttpHostHeaderParser.parseHost(
                                    tcpHeader.m_Data,
                                    dataOffset,
                                    tcpDataSize
                                )
                                if (host != null) {
                                    session.RemoteHost = host
                                }
                            }

                            // 转发给本地 TcpProxyServer 服务器
                            Log.d(
                                "LocalVpnService",
                                "onIPPacketReceived: 转发给本地 TcpProxyServer 服务器, $ipHeader $tcpHeader"
                            )
                            ipHeader.sourceIP = ipHeader.destinationIP
                            ipHeader.destinationIP = LOCAL_IP
                            tcpHeader.destinationPort = m_TcpProxyServer!!.Port

                            CommonMethods.ComputeTCPChecksum(ipHeader, tcpHeader)
                            m_VPNOutputStream?.write(ipHeader.m_Data, ipHeader.m_Offset, size)
                            session.BytesSent += tcpDataSize //注意顺序
                            m_SentBytes += size.toLong()
                        }
                    }
                }
            }

            IPHeader.UDP -> {
                // 转发DNS数据包：
                val udpHeader = m_UDPHeader
                udpHeader.m_Offset = ipHeader.headerLength
                if (ipHeader.sourceIP == LOCAL_IP && udpHeader.destinationPort.toInt() == 53) {
                    m_DNSBuffer.clear()
                    m_DNSBuffer.limit(ipHeader.dataLength - 8)
                    val dnsPacket = DnsPacket.FromBytes(m_DNSBuffer)
                    if (dnsPacket != null && dnsPacket.Header.QuestionCount > 0) {
                        m_DnsProxy!!.onDnsRequestReceived(ipHeader, udpHeader, dnsPacket)
                    }
                }
            }
        }
    }

    private fun waitUntilPrepared() {
        while (VpnService.prepare(service) != null) {
            try {
                Thread.sleep(100)
            } catch (e: InterruptedException) {
                Log.e(TAG, "waitUntilPreapred: ", e)
            }
        }
    }

    fun protect(mClient: DatagramSocket): Boolean? {
        return service.protect(mClient)
    }

    fun protect(socket: Socket): Boolean? {
        return service.protect(socket)
    }
}