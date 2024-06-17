package me.smartproxy.tunnel

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.Selector
import java.nio.channels.SocketChannel

class RawTunnel : Tunnel {
    constructor(serverAddress: InetSocketAddress?, selector: Selector?) : super(
        serverAddress,
        selector
    )

    constructor(innerChannel: SocketChannel?, selector: Selector?) : super(innerChannel, selector)

    @Throws(Exception::class)
    override fun onConnected(buffer: ByteBuffer) {
        onTunnelEstablished()
    }

    @Throws(Exception::class)
    override fun beforeSend(buffer: ByteBuffer) {
    }

    @Throws(Exception::class)
    override fun afterReceived(buffer: ByteBuffer) {
    }

    override fun isTunnelEstablished(): Boolean {
        return true
    }

    override fun onDispose() {
    }
}
