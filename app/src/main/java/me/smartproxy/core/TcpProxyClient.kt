package me.smartproxy.core

import android.os.ParcelFileDescriptor
import android.util.Log
import me.smartproxy.core.VpnHelper.Companion.TAG
import me.smartproxy.tcpip.CommonMethods
import me.smartproxy.tcpip.IPHeader
import me.smartproxy.tcpip.TCPHeader
import java.io.FileOutputStream

class TcpProxyClient(pfd: ParcelFileDescriptor, buffer: ByteArray, private val vpnLocalIpInt: Int) {

    private var m_VPNOutputStream: FileOutputStream
    private val m_TCPHeader: TCPHeader

    private var m_ReceivedBytes : Long = 0
    private var m_SentBytes: Long = 0


    init {
        m_VPNOutputStream = FileOutputStream(pfd.fileDescriptor)
        m_TCPHeader = TCPHeader(buffer, 20)

    }


    fun onTCPPacketReceived(ipHeader: IPHeader, size: Int) {
        val tcpHeader = m_TCPHeader

        tcpHeader.m_Offset = ipHeader.headerLength
        if (ipHeader.sourceIP == vpnLocalIpInt) {
            // 收到本地 TcpProxyServer 服务器数据
            if (tcpHeader.sourcePort == TcpProxyHelper.getPort()) {
                val session =
                    NatSessionManager.getSession(tcpHeader.destinationPort.toInt())
                if (session != null) {
                    Log.d(
                        TAG,
                        "onIPPacketReceived: 收到本地 TcpProxyServer 服务器数据, $ipHeader $tcpHeader"
                    )
                    ipHeader.sourceIP = ipHeader.destinationIP
                    tcpHeader.sourcePort = session.remotePort
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
                if (session == null || session.remoteIP != ipHeader.destinationIP || session.remotePort != tcpHeader.destinationPort) {
                    session = NatSessionManager.createSession(
                        portKey,
                        ipHeader.destinationIP,
                        tcpHeader.destinationPort
                    )
                }

                session?.let {
                    session.lastNanoTime = System.nanoTime()
                    session.packetSent++ //注意顺序

                    val tcpDataSize = ipHeader.dataLength - tcpHeader.headerLength
                    if (session.packetSent == 2 && tcpDataSize == 0) {
                        return  //丢弃tcp握手的第二个ACK报文。因为客户端发数据的时候也会带上ACK，这样可以在服务器Accept之前分析出HOST信息。
                    }

                    //分析数据，找到host
                    if (session.bytesSent == 0 && tcpDataSize > 10) {
                        val dataOffset = tcpHeader.m_Offset + tcpHeader.headerLength
                        val host = HttpHostHeaderParser.parseHost(
                            tcpHeader.m_Data,
                            dataOffset,
                            tcpDataSize
                        )
                        if (host != null) {
                            session.remoteHost = host
                        }
                    }

                    // 转发给本地 TcpProxyServer 服务器
                    Log.d(
                        TAG,
                        "onIPPacketReceived: 转发给本地 TcpProxyServer 服务器, $ipHeader $tcpHeader"
                    )
                    ipHeader.sourceIP = ipHeader.destinationIP
                    ipHeader.destinationIP = vpnLocalIpInt
                    tcpHeader.destinationPort = TcpProxyHelper.getPort()

                    CommonMethods.ComputeTCPChecksum(ipHeader, tcpHeader)
                    m_VPNOutputStream?.write(ipHeader.m_Data, ipHeader.m_Offset, size)
                    m_VPNOutputStream?.flush()
                    session.bytesSent += tcpDataSize //注意顺序
                    m_SentBytes += size.toLong()
                }
            }
        } else {
            Log.e(TAG, "onIPPacketReceived, TCP: 收到非本地数据包, $ipHeader $tcpHeader")
        }
    }
}