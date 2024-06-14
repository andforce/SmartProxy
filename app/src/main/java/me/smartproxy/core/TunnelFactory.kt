package me.smartproxy.core

import me.smartproxy.tunnel.RawTunnel
import me.smartproxy.tunnel.Tunnel
import me.smartproxy.tunnel.httpconnect.HttpConnectConfig
import me.smartproxy.tunnel.httpconnect.HttpConnectTunnel
import me.smartproxy.tunnel.shadowsocks.ShadowsocksConfig
import me.smartproxy.tunnel.shadowsocks.ShadowsocksTunnel
import java.net.InetSocketAddress
import java.nio.channels.Selector
import java.nio.channels.SocketChannel

object TunnelFactory {
    fun wrap(channel: SocketChannel, selector: Selector): Tunnel {
        return RawTunnel(channel, selector)
    }

    @Throws(Exception::class)
    fun createTunnelByConfig(
        c: ProxyConfig,
        destAddress: InetSocketAddress,
        selector: Selector
    ): Tunnel {
        if (destAddress.isUnresolved) {
            val config = c.getDefaultTunnelConfig(destAddress)
            if (config is HttpConnectConfig) {
                return HttpConnectTunnel(c, config, selector)
            } else if (config is ShadowsocksConfig) {
                return ShadowsocksTunnel(config, selector)
            }
            throw Exception("The config is unknow.")
        } else {
            return RawTunnel(destAddress, selector)
        }
    }
}
