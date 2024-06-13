package me.smartproxy.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TcpProxyHelper {

    private var tcpProxy: TcpProxyServer? = null
    suspend fun startTcpProxy(config: ProxyConfig) {
        withContext(Dispatchers.IO) {
//            TcpProxyServer(config, 0).start()
            if (tcpProxy == null) {
                tcpProxy = TcpProxyServer(config, 0)
            }
            tcpProxy?.start()
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