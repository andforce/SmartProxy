package me.smartproxy.core

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.smartproxy.core.viewmodel.LocalVpnViewModel
import me.smartproxy.dns.DnsProcessor
import me.smartproxy.dns.DnsProxyHelper
import me.smartproxy.tcpip.CommonMethods
import me.smartproxy.tcpip.IPHeader
import me.smartproxy.tcpip.TcpProxyClient
import me.smartproxy.tcpip.UDPHeader
import org.koin.java.KoinJavaComponent
import java.io.FileInputStream
import java.net.DatagramSocket
import java.net.Socket

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
    private var dnsProcessor: DnsProcessor? = null

    private val packetBuffer = ByteArray(20000)
    private val ipHeader = IPHeader(packetBuffer, 0)

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
                tcpClient = TcpProxyClient(vpnInterface, packetBuffer, vpnLocalIpInt)
                dnsProcessor = DnsProcessor(packetBuffer, vpnLocalIpInt)

                FileInputStream(vpnInterface.fileDescriptor).use { fis ->
                    var size: Int
                    kotlin.runCatching {
                        while (isRunning) {
                            size = fis.read(packetBuffer)
                            if (size <= 0) {
                                delay(10)
                                continue
                            }
                            when (ipHeader.protocol) {
                                IPHeader.TCP -> {
                                    tcpClient?.onTCPPacketReceived(ipHeader, size)
                                }

                                IPHeader.UDP -> {
                                    // 转发DNS数据包：
                                    dnsProcessor?.processUdpPacket(ipHeader)
                                }

                                else -> {
                                    Log.d(TAG, "onIPPacketReceived, 不支持的协议: $ipHeader")
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

        tcpClient?.stop()

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
        tcpClient?.sendUDPPacket(ipHeader, udpHeader)
    }

    fun protect(service: VpnService, mClient: DatagramSocket): Boolean {
        return service.protect(mClient)
    }

    fun protect(service: VpnService, socket: Socket): Boolean {
        return service.protect(socket)
    }
}