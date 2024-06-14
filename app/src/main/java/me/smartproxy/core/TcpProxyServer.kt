package me.smartproxy.core

import android.net.VpnService
import android.util.Log
import me.smartproxy.tcpip.CommonMethods
import me.smartproxy.tunnel.Tunnel
import java.net.InetSocketAddress
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

class TcpProxyServer(private val config: ProxyConfig, port: Int) {
    var Stopped: Boolean = false
    var Port: Short = 0

    private var m_Selector: Selector?
    private var m_ServerSocketChannel: ServerSocketChannel?

    init {
        m_Selector = Selector.open()
        m_ServerSocketChannel = ServerSocketChannel.open()

        m_ServerSocketChannel?.let {
            it.configureBlocking(false)
            it.socket().bind(InetSocketAddress(port))
            it.register(m_Selector, SelectionKey.OP_ACCEPT)
            this.Port = it.socket().localPort.toShort()
            Log.d(TAG, "AsyncTcpServer listen on " + (Port.toInt() and 0xFFFF) + " success.")
        } ?: run {
            Log.e(TAG, "AsyncTcpServer listen on 0 failed.")
        }
    }

    fun stop() {
        this.Stopped = true
        m_Selector?.let {
            try {
                it.close()
                m_Selector = null
            } catch (e: Exception) {
                Log.e(TAG, "stop, m_Selector: ", e)
            }
        }

        m_ServerSocketChannel?.let {
            try {
                it.close()
                m_ServerSocketChannel = null
            } catch (e: Exception) {
                Log.e(TAG, "stop, m_ServerSocketChannel: ", e)
            }
        }
    }

    fun start(service: VpnService) {
        m_Selector?.let { selector ->
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
                                    onAccepted(service)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "inner run: ", e)
                            }
                        }
                        keyIterator.remove()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "outer run: ", e)
            } finally {
                this.stop()
                Log.d(TAG, "TcpServer thread exited.")
            }
        }
    }

    private fun getDestAddress(localChannel: SocketChannel): InetSocketAddress? {
        val portKey = localChannel.socket().port.toShort()
        val session = NatSessionManager.getSession(portKey.toInt())
        if (session != null) {
            if (config.needProxy(session.RemoteHost, session.RemoteIP)) {
                Log.d(
                    TAG,
                    String.format(
                        "%d/%d:[PROXY] %s=>%s:%d",
                        NatSessionManager.getSessionCount(),
                        Tunnel.SessionCount,
                        session.RemoteHost,
                        CommonMethods.ipIntToString(session.RemoteIP),
                        session.RemotePort.toInt() and 0xFFFF
                    )
                )
                return InetSocketAddress.createUnresolved(
                    session.RemoteHost,
                    session.RemotePort.toInt() and 0xFFFF
                )
            } else {
                return InetSocketAddress(
                    localChannel.socket().inetAddress,
                    session.RemotePort.toInt() and 0xFFFF
                )
            }
        }
        return null
    }

    private fun onAccepted(service: VpnService) {
        var localTunnel: Tunnel? = null
        try {
            m_ServerSocketChannel?.let { socketChannel->
                val localChannel = socketChannel.accept()
                localTunnel = TunnelFactory.wrap(localChannel, m_Selector)

                val destAddress = getDestAddress(localChannel)
                if (destAddress != null) {
                    val remoteTunnel =
                        TunnelFactory.createTunnelByConfig(config, destAddress, m_Selector)
                    remoteTunnel.setBrotherTunnel(localTunnel) //关联兄弟
                    localTunnel?.setBrotherTunnel(remoteTunnel) //关联兄弟
                    remoteTunnel.connect(service, destAddress) //开始连接
                } else {
                    localTunnel?.dispose()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onAccepted: ", e)
            localTunnel?.dispose()
        }
    }

    companion object {
        private const val TAG = "TcpProxyServer"
    }
}
