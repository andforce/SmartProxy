package me.smartproxy.tunnel.shadowsocks

import android.net.Uri
import android.util.Base64
import me.smartproxy.tunnel.Config
import java.net.InetSocketAddress

class ShadowsocksConfig : Config() {
    var encryptMethod: String? = null
    var password: String? = null

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        return this.toString() == other.toString()
    }

    override fun toString(): String {
        return "ss://$encryptMethod:$password@$socketAddress"
    }

    override fun hashCode(): Int {
        var result = encryptMethod?.hashCode() ?: 0
        result = 31 * result + (password?.hashCode() ?: 0)
        return result
    }

    companion object {
        @Throws(Exception::class)
        fun parse(proxyInfo: String): ShadowsocksConfig {
            var proxyInfoFix = proxyInfo
            val config = ShadowsocksConfig()
            var uri = Uri.parse(proxyInfoFix)
            if (uri.port == -1) {
                val base64String = uri.host
                proxyInfoFix = "ss://" + String(
                    Base64.decode(
                        base64String!!.toByteArray(charset("ASCII")),
                        Base64.DEFAULT
                    )
                )
                uri = Uri.parse(proxyInfoFix)
            }

            val userInfoString = uri.userInfo
            if (userInfoString != null) {
                val userStrings = userInfoString.split(":").toTypedArray()
                config.encryptMethod = userStrings[0]
                if (userStrings.size >= 2) {
                    config.password = userStrings[1]
                }
            }
            config.socketAddress = InetSocketAddress(uri.host, uri.port)
            config.encryptor = EncryptorFactory.createEncryptorByConfig(config)
            return config
        }
    }
}
