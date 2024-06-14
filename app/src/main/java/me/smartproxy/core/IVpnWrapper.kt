package me.smartproxy.core

import java.net.DatagramSocket
import java.net.Socket

interface IVpnWrapper {
    fun protect(socket: Int): Boolean

    fun protect(socket: Socket): Boolean

    fun protect(socket: DatagramSocket): Boolean
}