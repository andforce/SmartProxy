package me.smartproxy.tunnel.httpconnect

import android.net.Uri
import me.smartproxy.tunnel.Config
import java.net.InetSocketAddress
import java.util.Locale

class HttpConnectConfig : Config() {
    var userName: String? = null
    var password: String? = null

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        return this.toString() == other.toString()
    }

    override fun toString(): String {
        return String.format(Locale.ENGLISH, "http://%s:%s@%s", userName, password, socketAddress)
    }

    override fun hashCode(): Int {
        var result = userName?.hashCode() ?: 0
        result = 31 * result + (password?.hashCode() ?: 0)
        return result
    }

    companion object {
        fun parse(proxyInfo: String?): HttpConnectConfig {
            val config = HttpConnectConfig()
            val uri = Uri.parse(proxyInfo)
            val userInfoString = uri.userInfo
            if (userInfoString != null) {
                val userStrings = userInfoString.split(":").toTypedArray()
                config.userName = userStrings[0]
                if (userStrings.size >= 2) {
                    config.password = userStrings[1]
                }
            }
            config.socketAddress = InetSocketAddress(uri.host, uri.port)
            return config
        }
    }
}
