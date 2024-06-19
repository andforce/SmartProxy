package me.smartproxy.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.smartproxy.core.viewmodel.LocalVpnViewModel
import me.smartproxy.dns.DnsProcessor
import me.smartproxy.dns.DnsProxy
import me.smartproxy.dns.LocalServerHelper
import me.smartproxy.dns.UDPServer
import me.smartproxy.tcpip.CommonMethods
import me.smartproxy.tcpip.IPData
import me.smartproxy.tcpip.IPHeader
import me.smartproxy.tcpip.TcpProxyClient
import me.smartproxy.tcpip.TcpProxyServer
import me.smartproxy.tcpip.UDPHeader
import me.smartproxy.ui.utils.Logger
import org.koin.java.KoinJavaComponent
import java.io.FileInputStream
import java.io.FileOutputStream

class VpnHelper {

    companion object {
        const val TAG = "VpnHelper"
        const val IS_ENABLE_REMOTE_PROXY: Boolean = false
        const val DNS_SERVER = false
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
    private var tcpProxy: TcpProxyServer? = null

    private var dnsProxy: DnsProxy? = null
    private var dnsServer: UDPServer? = null
    private var dnsServerHelper: LocalServerHelper? = null

    suspend fun startDnsProxy(config: ProxyConfig, fos: FileOutputStream) {
        if (DNS_SERVER) {
            val server = LocalVpnService::class.getOrNull()
            dnsServer = UDPServer(server, config.defaultLocalIP.address, fos)
            dnsServerHelper = LocalServerHelper(config, fos, packetBuffer)
        } else {
            dnsProxy = DnsProxy(config)
        }

        withContext(Dispatchers.IO) {
            if (DNS_SERVER) {
                dnsServer?.start()
            } else {
                dnsProxy?.start()
            }
        }
    }

    suspend fun startTcpProxy(config: ProxyConfig) {
        tcpProxy = TcpProxyServer(config, 0)
        withContext(Dispatchers.IO) {
            tcpProxy?.start()
        }
    }

    suspend fun startProcessPacket(config: ProxyConfig, fis: FileInputStream, fos: FileOutputStream) =
        withContext(Dispatchers.IO) {
            Logger.d(TAG, "VPNService work thread is running...")

            proxyConfig = config

            proxyConfig?.startTimer()


            vpnLocalIpInt = CommonMethods.ipStringToInt(config.defaultLocalIP.address)

            isRunning = true

            // 更新状态
            viewModel.updateVpnStatus(1)

            runCatching {
                tcpProxy?.let { tcpProxy ->
                    tcpClient = TcpProxyClient(fos, packetBuffer, vpnLocalIpInt, tcpProxy.tcpServerPort)
                    dnsProcessor = DnsProcessor(packetBuffer, vpnLocalIpInt)

                    fis.use { fis ->
                        var size: Int
                        while (isRunning) {
                            size = fis.read(packetBuffer)
                            if (size <= 0) {
                                delay(10)
                                continue
                            }
                            when (ipHeader.protocol) {
                                IPData.TCP -> {
                                    tcpClient?.processTcpPacket(ipHeader, size)
                                }

                                IPData.UDP -> {
                                    // 转发DNS数据包：
                                    if (DNS_SERVER) {
                                        dnsServerHelper?.onUDP(dnsServer?.port ?: -100, ipHeader)
                                    } else {
                                        dnsProcessor?.processUdpPacket(ipHeader, dnsProxy)
                                    }
                                }

                                else -> {
                                    Logger.i(TAG, "startProcessPacket, 不支持的协议: $ipHeader")
                                }
                            }
                        }

                        stop("while read stopped.")
                    }
                } ?: run {
                    Logger.e(TAG, "tcpProxy: ", Exception("tcpProxy is null."))
                    stop("tcpProxy is null.")
                }
            }.onFailure {
                Logger.e(TAG, "startProcessPacket: ", it)
                stop("startProcessPacket failed.")
            }
        }

    private fun stop(reason: String) {
        Logger.e(TAG, "VPNService stopped: $reason")

        isRunning = false

        tcpClient?.stop()

        tcpProxy?.stop()
        tcpProxy = null

        if (DNS_SERVER) {
            dnsServer?.stop()
            dnsServer = null
        } else {
            dnsProxy?.stop()
            dnsProxy = null
        }

        viewModel.updateVpnStatus(0)
    }

    fun tryStop() {
        if (DNS_SERVER) {
            dnsServer?.stop()
            dnsServer = null
        } else {
            dnsProxy?.stop()
            dnsProxy = null
        }

        tcpProxy?.stop()
        tcpProxy = null

        this.proxyConfig?.stopTimer()
        stop("tryStop()")
    }

    fun sendUDPPacket(ipHeader: IPHeader, udpHeader: UDPHeader?) {
        tcpClient?.sendUDPPacket(ipHeader, udpHeader)
    }
}