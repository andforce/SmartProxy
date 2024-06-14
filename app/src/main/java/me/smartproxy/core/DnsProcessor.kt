package me.smartproxy.core

import android.util.Log
import me.smartproxy.core.VpnHelper.Companion.TAG
import me.smartproxy.dns.DnsPacket
import me.smartproxy.tcpip.IPHeader
import me.smartproxy.tcpip.UDPHeader
import java.nio.ByteBuffer

class DnsProcessor(buffer: ByteArray, private val vpnLocalIpInt: Int) {
    private val m_UDPHeader = UDPHeader(buffer, 20)
    private val m_DNSBuffer: ByteBuffer =
        (ByteBuffer.wrap(buffer).position(28) as ByteBuffer).slice()

    fun processUdpPacket(m_IPHeader: IPHeader) {
        m_UDPHeader.m_Offset = m_IPHeader.headerLength
        if (m_IPHeader.sourceIP == vpnLocalIpInt && m_UDPHeader.destinationPort.toInt() == 53) {
            m_DNSBuffer.clear()
            m_DNSBuffer.limit(m_IPHeader.dataLength - 8)
            val dnsPacket = DnsPacket.FromBytes(m_DNSBuffer)
            if (dnsPacket != null && dnsPacket.Header.QuestionCount > 0) {
                DnsProxyHelper.onDnsRequestReceived(
                    m_IPHeader,
                    m_UDPHeader,
                    dnsPacket
                )
            }
        } else {
            Log.e(
                TAG,
                "onIPPacketReceived, UDP: 收到非本地数据包, $m_IPHeader $m_UDPHeader"
            )
        }
    }

}