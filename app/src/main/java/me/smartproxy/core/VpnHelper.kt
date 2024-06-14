package me.smartproxy.core

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.smartproxy.core.viewmodel.LocalVpnViewModel
import me.smartproxy.dns.DnsPacket
import me.smartproxy.tcpip.CommonMethods
import me.smartproxy.tcpip.IPHeader
import me.smartproxy.tcpip.TCPHeader
import me.smartproxy.tcpip.UDPHeader
import org.koin.java.KoinJavaComponent
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramSocket
import java.net.Socket
import java.nio.ByteBuffer

class VpnHelper(private val context: Context) {

    companion object {
        const val TAG = "VpnHelper"
        const val IS_ENABLE_REMOTE_PROXY: Boolean = false
    }

    private val viewModel: LocalVpnViewModel by lazy {
        KoinJavaComponent.get(LocalVpnViewModel::class.java)
    }

    private var vpnLocalIpInt = 0

    private var isRunning = false

    private var m_SentBytes: Long = 0
    private var m_ReceivedBytes: Long = 0

    private var m_VPNOutputStream: FileOutputStream? = null

    private val m_Packet = ByteArray(20000)
    private val m_IPHeader = IPHeader(m_Packet, 0)
    private val m_TCPHeader = TCPHeader(m_Packet, 20)
    private val m_UDPHeader = UDPHeader(m_Packet, 20)
    private val m_DNSBuffer: ByteBuffer =
        (ByteBuffer.wrap(m_Packet).position(28) as ByteBuffer).slice()

    private var proxyConfig: ProxyConfig? = null

    suspend fun startDnsProxy(config: ProxyConfig) {
        withContext(Dispatchers.IO) {
            DnsProxyHelper.startDnsProxy(config)
        }
    }

    suspend fun startTcpProxy(service: VpnService, config: ProxyConfig) {
        withContext(Dispatchers.IO) {
            TcpProxyHelper.startTcpProxy(service, config)
        }
    }

    suspend fun startProcessPacket(config: ProxyConfig, pfd: ParcelFileDescriptor) =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "VPNService work thread is running...")

            proxyConfig = config

            proxyConfig?.startTimer()


            vpnLocalIpInt = CommonMethods.ipStringToInt(config.defaultLocalIP.Address)

            isRunning = true

            // 更新状态
            viewModel.updateVpnStatus(1)

            pfd.use { vpnInterface ->

                m_VPNOutputStream = FileOutputStream(vpnInterface.fileDescriptor)

                FileInputStream(vpnInterface.fileDescriptor).use { fis ->
                    var size: Int
                    kotlin.runCatching {
                        while (isRunning) {
                            size = fis.read(m_Packet)
                            if (size <= 0) {
                                delay(10)
                                continue
                            }
                            onIPPacketReceived(m_IPHeader, size)
                        }

                        stop("while read stopped.")

                    }.onFailure {
                        Log.e(TAG, "while read: ", it)
                        stop("while read failed, or onIPPacketReceived() IOException.")
                    }
                }.onFailure {
                    Log.e(TAG, "FileInputStream: ", it)
                    stop("FileInputStream failed.")
                }

            }.onFailure {
                Log.e(TAG, "ParcelFileDescriptor: ", it)
                stop("ParcelFileDescriptor failed.")
            }
        }

    private fun stop(reason: String) {
        Log.e(TAG, "VPNService stopped: $reason")

        isRunning = false

        m_VPNOutputStream?.use {
            m_VPNOutputStream = null
        }

        TcpProxyHelper.stopTcpProxy()

        DnsProxyHelper.stopDnsProxy()

        viewModel.updateVpnStatus(0)
    }

    fun tryStop() {
        DnsProxyHelper.stopDnsProxy()
        TcpProxyHelper.stopTcpProxy()

        this.proxyConfig?.stopTimer()
        stop("tryStop()")
        //service.stopSelf()
        context.stopService(Intent(context, LocalVpnService::class.java))
    }

    fun sendUDPPacket(ipHeader: IPHeader, udpHeader: UDPHeader?) {
        try {
            CommonMethods.ComputeUDPChecksum(ipHeader, udpHeader)
            m_VPNOutputStream?.write(ipHeader.m_Data, ipHeader.m_Offset, ipHeader.totalLength)
            m_VPNOutputStream?.flush()
        } catch (e: IOException) {
            Log.e(TAG, "sendUDPPacket: ", e)
        }
    }

    @Throws(IOException::class)
    private fun onIPPacketReceived(ipHeader: IPHeader, size: Int) {
        when (ipHeader.protocol) {
            IPHeader.TCP -> {
                val tcpHeader = m_TCPHeader
                tcpHeader.m_Offset = ipHeader.headerLength
                if (ipHeader.sourceIP == vpnLocalIpInt) {
                    // 收到本地 TcpProxyServer 服务器数据
                    if (tcpHeader.sourcePort == TcpProxyHelper.getPort()) {
                        val session =
                            NatSessionManager.getSession(tcpHeader.destinationPort.toInt())
                        if (session != null) {
                            Log.d(
                                "LocalVpnService",
                                "onIPPacketReceived: 收到本地 TcpProxyServer 服务器数据, $ipHeader $tcpHeader"
                            )
                            ipHeader.sourceIP = ipHeader.destinationIP
                            tcpHeader.sourcePort = session.RemotePort
                            ipHeader.destinationIP = vpnLocalIpInt

                            CommonMethods.ComputeTCPChecksum(ipHeader, tcpHeader)
                            m_VPNOutputStream?.write(ipHeader.m_Data, ipHeader.m_Offset, size)
                            m_VPNOutputStream?.flush()
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
                            ipHeader.destinationIP = vpnLocalIpInt
                            tcpHeader.destinationPort = TcpProxyHelper.getPort()

                            CommonMethods.ComputeTCPChecksum(ipHeader, tcpHeader)
                            m_VPNOutputStream?.write(ipHeader.m_Data, ipHeader.m_Offset, size)
                            m_VPNOutputStream?.flush()
                            session.BytesSent += tcpDataSize //注意顺序
                            m_SentBytes += size.toLong()
                        }
                    }
                } else {
                    Log.e(TAG, "onIPPacketReceived, TCP: 收到非本地数据包, $ipHeader $tcpHeader")
                }
            }

            IPHeader.UDP -> {
                // 转发DNS数据包：
                val udpHeader = m_UDPHeader
                udpHeader.m_Offset = ipHeader.headerLength
                if (ipHeader.sourceIP == vpnLocalIpInt && udpHeader.destinationPort.toInt() == 53) {
                    m_DNSBuffer.clear()
                    m_DNSBuffer.limit(ipHeader.dataLength - 8)
                    val dnsPacket = DnsPacket.FromBytes(m_DNSBuffer)
                    if (dnsPacket != null && dnsPacket.Header.QuestionCount > 0) {
                        DnsProxyHelper.onDnsRequestReceived(ipHeader, udpHeader, dnsPacket)
                    }
                } else {
                    Log.e(TAG, "onIPPacketReceived, UDP: 收到非本地数据包, $ipHeader $udpHeader")
                }
            }

            else -> {
                Log.d(TAG, "onIPPacketReceived, 不支持的协议: $ipHeader")
            }
        }
    }

    fun protect(service: VpnService, mClient: DatagramSocket): Boolean {
        return service.protect(mClient)
    }

    fun protect(service: VpnService, socket: Socket): Boolean {
        return service.protect(socket)
    }
}