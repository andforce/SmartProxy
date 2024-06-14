package me.smartproxy.core

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
import me.smartproxy.tcpip.UDPHeader
import org.koin.java.KoinJavaComponent
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramSocket
import java.net.Socket
import java.nio.ByteBuffer

class VpnHelper {

    companion object {
        const val TAG = "VpnHelper"
        const val IS_ENABLE_REMOTE_PROXY: Boolean = false
    }

    private val viewModel: LocalVpnViewModel by lazy {
        KoinJavaComponent.get(LocalVpnViewModel::class.java)
    }

    private var vpnLocalIpInt = 0

    private var isRunning = false


    private var tcpClient: TcpProxyClient? = null

    private var m_VPNOutputStream: FileOutputStream? = null

    private val m_Packet = ByteArray(20000)
    private val m_IPHeader = IPHeader(m_Packet, 0)

    private val m_UDPHeader = UDPHeader(m_Packet, 20)
    private val m_DNSBuffer: ByteBuffer =
        (ByteBuffer.wrap(m_Packet).position(28) as ByteBuffer).slice()

    private var proxyConfig: ProxyConfig? = null

    suspend fun startDnsProxy(config: ProxyConfig) {
        withContext(Dispatchers.IO) {
            DnsProxyHelper.startDnsProxy(config)
        }
    }

    suspend fun startTcpProxy(config: ProxyConfig) {
        withContext(Dispatchers.IO) {
            TcpProxyHelper.startTcpProxy(config)
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
                tcpClient = TcpProxyClient(vpnInterface, m_Packet, vpnLocalIpInt)

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
                            when (m_IPHeader.protocol) {
                                IPHeader.TCP -> {
                                    tcpClient?.onTCPPacketReceived(m_IPHeader, size)
                                }

                                IPHeader.UDP -> {
                                    // 转发DNS数据包：
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

                                else -> {
                                    Log.d(TAG, "onIPPacketReceived, 不支持的协议: $m_IPHeader")
                                }
                            }
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

    fun protect(service: VpnService, mClient: DatagramSocket): Boolean {
        return service.protect(mClient)
    }

    fun protect(service: VpnService, socket: Socket): Boolean {
        return service.protect(socket)
    }
}