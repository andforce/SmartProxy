package me.smartproxy.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.smartproxy.tcpip.TcpProxyServer

object TcpProxyHelper {

    private var tcpProxy: TcpProxyServer? = null
    suspend fun startTcpProxy(config: ProxyConfig) {
        withContext(Dispatchers.IO) {
            if (tcpProxy == null) {
                tcpProxy = TcpProxyServer(config, 0)
            }
            tcpProxy?.start()
        }
    }

    fun isRunning(): Boolean {
        return tcpProxy?.stopped == false
    }

    fun stopTcpProxy() {
        tcpProxy?.stop()
        tcpProxy = null
    }

    fun getPort() = tcpProxy?.tcpServerPort ?: 0
}