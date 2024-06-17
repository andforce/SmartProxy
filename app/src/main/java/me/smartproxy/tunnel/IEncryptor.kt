package me.smartproxy.tunnel

import java.nio.ByteBuffer

interface IEncryptor {
    fun encrypt(buffer: ByteBuffer)

    fun decrypt(buffer: ByteBuffer)
}