package me.smartproxy.tcpip

import me.smartproxy.core.LocalVpnService
import me.smartproxy.core.NatSessionManager
import me.smartproxy.core.ProxyConfig
import me.smartproxy.core.TunnelFactory
import me.smartproxy.core.getOrNull
import me.smartproxy.tunnel.Tunnel
import me.smartproxy.ui.utils.Logger
import java.net.InetSocketAddress
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

class TcpProxyServer(private val config: ProxyConfig, port: Int) {
    var stopped: Boolean = false
    var tcpServerPort: Short = 0

    private var selector: Selector?
    private var serverSocketChannel: ServerSocketChannel?

    init {
        selector = Selector.open()
        serverSocketChannel = ServerSocketChannel.open()

        serverSocketChannel?.let {
            it.configureBlocking(false)
            it.socket().bind(InetSocketAddress(port))
            it.register(selector, SelectionKey.OP_ACCEPT)
            this.tcpServerPort = it.socket().localPort.toShort()
            Logger.d(TAG, "AsyncTcpServer listen on " + (tcpServerPort.toInt() and 0xFFFF) + " success.")
        } ?: run {
            Logger.e(TAG, "AsyncTcpServer listen on 0 failed.")
        }
    }

    fun stop() {
        this.stopped = true
        selector?.let {
            try {
                it.close()
                selector = null
            } catch (e: Exception) {
                Logger.e(TAG, "stop, m_Selector: ", e)
            }
        }

        serverSocketChannel?.let {
            try {
                it.close()
                serverSocketChannel = null
            } catch (e: Exception) {
                Logger.e(TAG, "stop, m_ServerSocketChannel: ", e)
            }
        }
    }

    fun start() {
        selector?.let { selector ->
            try {
                while (true) {
                    selector.select()
                    val keyIterator = selector.selectedKeys().iterator()
                    while (keyIterator.hasNext()) {
                        val key = keyIterator.next()
                        if (key.isValid) {
                            try {
                                if (key.isReadable) {
                                    (key.attachment() as Tunnel).onReadable(key)
                                } else if (key.isWritable) {
                                    (key.attachment() as Tunnel).onWritable(key)
                                } else if (key.isConnectable) {
                                    (key.attachment() as Tunnel).onConnectable()
                                } else if (key.isAcceptable) {
                                    onAccepted()
                                }
                            } catch (e: Exception) {
                                Logger.e(TAG, "inner run: ", e)
                            }
                        }
                        keyIterator.remove()
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "outer run: ", e)
            } finally {
                this.stop()
                Logger.d(TAG, "TcpServer thread exited.")
            }
        }
    }

    private fun getDestAddress(localChannel: SocketChannel): InetSocketAddress? {
        val portKey = localChannel.socket().port.toShort()
        val session = NatSessionManager.getSessionOrNull(portKey.toInt())
        if (session != null) {
            if (config.needProxy(session.remoteHost, session.remoteIP)) {
                Logger.d(
                    TAG,
                    "${NatSessionManager.getSessionCount()}/${Tunnel.SessionCount}:[PROXY] ${session.remoteHost}=>${CommonMethods.ipIntToString(session.remoteIP)}:${session.remotePort.toInt() and 0xFFFF}",
                )
                return InetSocketAddress.createUnresolved(
                    session.remoteHost,
                    session.remotePort.toInt() and 0xFFFF
                )
            } else {
                return InetSocketAddress(
                    localChannel.socket().inetAddress,
                    session.remotePort.toInt() and 0xFFFF
                )
            }
        }
        return null
    }

    private fun onAccepted() {
        var localTunnel: Tunnel? = null
        try {
            serverSocketChannel?.let { socketChannel->
                val localChannel = socketChannel.accept()

                selector?.let { s->
                    localTunnel = TunnelFactory.wrap(localChannel, s)

                    val destAddress = getDestAddress(localChannel)
                    if (destAddress != null) {
                        val remoteTunnel =
                            TunnelFactory.createTunnelByConfig(config, destAddress, s)
                        remoteTunnel.setBrotherTunnel(localTunnel) //关联兄弟
                        localTunnel?.setBrotherTunnel(remoteTunnel) //关联兄弟

                        val service = LocalVpnService::class.getOrNull()
                        remoteTunnel.connect(service, destAddress) //开始连接
                    } else {
                        localTunnel?.dispose()
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "onAccepted: ", e)
            localTunnel?.dispose()
        }
    }

    companion object {
        private const val TAG = "TcpProxyServer"
    }
}
