package me.smartproxy.dns

import me.smartproxy.tcpip.IPHeader
import me.smartproxy.tcpip.UDPHeader
import me.smartproxy.ui.utils.Logger
import java.nio.ByteBuffer

class DnsProcessor(buffer: ByteArray, private val vpnLocalIpInt: Int) {
    private val udpHeader = UDPHeader(buffer, 20)
    private val dnsBuffer: ByteBuffer =
        (ByteBuffer.wrap(buffer).position(28) as ByteBuffer).slice()

    fun processUdpPacket(header: IPHeader, dnsProxy: DnsProxy?) {
        udpHeader.offset = header.headerLength

        Logger.e(
            TAG,
            "processUdpPacket, UDP: 收到数据包, $header $udpHeader")

        if (header.sourceIP == vpnLocalIpInt && udpHeader.destinationPort.toInt() == 53) {
            dnsBuffer.clear()
            dnsBuffer.limit(header.dataLength - 8)
            val dnsPacket = DnsPacket.takeFromPoll(dnsBuffer)
            Logger.e(
                TAG,
                "processUdpPacket, UDP: 收到DNS数据包, $dnsPacket"
            )
            dnsPacket?.let {
                if (dnsPacket.dnsHeader.questionCount > 0) {
                    Logger.e(
                        TAG,
                        "processUdpPacket, UDP: DNS数据包无问题, onDnsRequestReceived(), questionCount is ${dnsPacket.dnsHeader.questionCount}"
                    )

                    dnsProxy?.onDnsRequestReceived(header, udpHeader, dnsPacket)
                } else {
                    Logger.e(
                        TAG,
                        "processUdpPacket, UDP: DNS数据包无问题, questionCount is 0"
                    )
                }
            }
        } else {
            Logger.e(
                TAG,
                "processUdpPacket, UDP: 收到非本地数据包, $header $udpHeader"
            )
        }
    }

    companion object {
        const val TAG = "DnsProcessor"
    }

}