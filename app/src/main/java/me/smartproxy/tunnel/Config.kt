package me.smartproxy.tunnel

import java.net.InetSocketAddress

abstract class Config {
    var socketAddress: InetSocketAddress? = null
    var encryptor: IEncryptor? = null
}