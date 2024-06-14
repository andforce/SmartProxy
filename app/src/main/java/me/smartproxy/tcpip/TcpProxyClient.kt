package me.smartproxy.tcpip

import android.os.ParcelFileDescriptor
import android.util.Log
import me.smartproxy.core.HttpHostHeaderParser
import me.smartproxy.core.NatSessionManager
import me.smartproxy.core.TcpProxyHelper
import me.smartproxy.core.VpnHelper.Companion.TAG
import java.io.FileOutputStream
import java.io.IOException

class TcpProxyClient(pfd: ParcelFileDescriptor, buffer: ByteArray, private val vpnLocalIpInt: Int) {

    private var vpnOutputStream: FileOutputStream? = null
    private val tcpHeader: TCPHeader

    private var receivedBytes: Long = 0
    private var sentBytes: Long = 0


    init {
        vpnOutputStream = FileOutputStream(pfd.fileDescriptor)
        tcpHeader = TCPHeader(buffer, 20)
    }


    fun onTCPPacketReceived(ipHeader: IPHeader, size: Int) {
        val tcpHeader = tcpHeader

        tcpHeader.offset = ipHeader.headerLength
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

                    CommonMethods.computeTCPChecksum(ipHeader, tcpHeader)
                    vpnOutputStream?.write(ipHeader.data, ipHeader.offset, size)
                    vpnOutputStream?.flush()
                    receivedBytes += size.toLong()
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
                        val dataOffset = tcpHeader.offset + tcpHeader.headerLength
                        val host = HttpHostHeaderParser.parseHost(
                            tcpHeader.data,
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

                    CommonMethods.computeTCPChecksum(ipHeader, tcpHeader)
                    vpnOutputStream?.write(ipHeader.data, ipHeader.offset, size)
                    vpnOutputStream?.flush()
                    session.bytesSent += tcpDataSize //注意顺序
                    sentBytes += size.toLong()
                }
            }
        } else {
            Log.e(TAG, "onIPPacketReceived, TCP: 收到非本地数据包, $ipHeader $tcpHeader")
        }
    }


    fun sendUDPPacket(ipHeader: IPHeader, udpHeader: UDPHeader?) {
        try {
            CommonMethods.computeUDPChecksum(ipHeader, udpHeader)
            vpnOutputStream?.write(ipHeader.data, ipHeader.offset, ipHeader.totalLength)
            vpnOutputStream?.flush()
        } catch (e: IOException) {
            Log.e(TAG, "sendUDPPacket: ", e)
        }
    }

    fun stop() {
        Log.e(TAG, "TcpProxyClient stopped.")
        vpnOutputStream?.use {
            vpnOutputStream = null
        }
    }
}