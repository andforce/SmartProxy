package me.smartproxy.core

import android.net.VpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TcpProxyHelper {

    private var tcpProxy: TcpProxyServer? = null
    suspend fun startTcpProxy(service: VpnService, config: ProxyConfig) {
        withContext(Dispatchers.IO) {
            if (tcpProxy == null) {
                tcpProxy = TcpProxyServer(config, 0)
            }
            tcpProxy?.start(service)
        }
    }

    fun isRunning(): Boolean {
        return tcpProxy?.Stopped == false
    }

    fun stopTcpProxy() {
        tcpProxy?.stop()
        tcpProxy = null
    }

    fun getPort() = tcpProxy?.Port ?: 0
}