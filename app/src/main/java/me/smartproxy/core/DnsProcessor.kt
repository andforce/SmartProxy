package me.smartproxy.core

import android.util.Log
import me.smartproxy.dns.DnsPacket
import me.smartproxy.tcpip.IPHeader
import me.smartproxy.tcpip.UDPHeader
import java.nio.ByteBuffer

class DnsProcessor(buffer: ByteArray, private val vpnLocalIpInt: Int) {
    private val udpHeader = UDPHeader(buffer, 20)
    private val dnsBuffer: ByteBuffer =
        (ByteBuffer.wrap(buffer).position(28) as ByteBuffer).slice()

    fun processUdpPacket(header: IPHeader) {
        udpHeader.m_Offset = header.headerLength
        if (header.sourceIP == vpnLocalIpInt && udpHeader.destinationPort.toInt() == 53) {
            dnsBuffer.clear()
            dnsBuffer.limit(header.dataLength - 8)
            val dnsPacket = DnsPacket.FromBytes(dnsBuffer)
            if (dnsPacket != null && dnsPacket.Header.QuestionCount > 0) {
                DnsProxyHelper.onDnsRequestReceived(
                    header,
                    udpHeader,
                    dnsPacket
                )
            }
        } else {
            Log.e(
                TAG,
                "onIPPacketReceived, UDP: 收到非本地数据包, $header $udpHeader"
            )
        }
    }

    companion object {
        const val TAG = "DnsProcessor"
    }

}