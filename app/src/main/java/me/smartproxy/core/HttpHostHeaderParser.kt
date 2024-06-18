package me.smartproxy.core

import android.util.Log
import me.smartproxy.tcpip.CommonMethods

object HttpHostHeaderParser {

    private const val TAG = "HttpHostHeaderParser"

    fun parseHost(buffer: ByteArray, offset: Int, count: Int): String? {
        try {
            when (buffer[offset]) {
                'G'.code.toByte(),
                'H'.code.toByte(),
                'P'.code.toByte(),
                'D'.code.toByte(),
                'O'.code.toByte(),
                'T'.code.toByte(),
                'C'.code.toByte() -> return getHttpHost(
                    buffer,
                    offset,
                    count
                )

                0x16.toByte() -> return getSNI(buffer, offset, count)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }


    private fun getHttpHost(buffer: ByteArray, offset: Int, count: Int): String? {
        val headerString = String(buffer, offset, count)
        val headerLines = headerString.split("\r\n").toTypedArray()
        val requestLine = headerLines[0]
        if (requestLine.startsWith("GET")
            || requestLine.startsWith("POST")
            || requestLine.startsWith("HEAD")
            || requestLine.startsWith("OPTIONS")
        ) {
            for (i in 1 until headerLines.size) {
                val nameValueStrings = headerLines[i].split(":")
                if (nameValueStrings.size == 2) {
                    val name = nameValueStrings[0].lowercase().trim { it <= ' ' }
                    val value = nameValueStrings[1].trim { it <= ' ' }
                    if ("host" == name) {
                        return value
                    }
                }
            }
        }
        return null
    }


    private fun getSNI(buffer: ByteArray, offset: Int, count: Int): String? {
        var offsetValue = offset
        val limit = offsetValue + count
        if (count > 43 && buffer[offsetValue].toInt() == 0x16) { //TLS Client Hello
            offsetValue += 43 //skip 43 bytes header

            //read sessionID:
            if (offsetValue + 1 > limit) return null
            val sessionIDLength = buffer[offsetValue++].toInt() and 0xFF
            offsetValue += sessionIDLength

            //read cipher suites:
            if (offsetValue + 2 > limit) return null
            val cipherSuitesLength = CommonMethods.readShort(buffer, offsetValue).toInt() and 0xFFFF
            offsetValue += 2
            offsetValue += cipherSuitesLength

            //read Compression method:
            if (offsetValue + 1 > limit) return null
            val compressionMethodLength = buffer[offsetValue++].toInt() and 0xFF
            offsetValue += compressionMethodLength

            if (offsetValue == limit) {
                Log.e(TAG, "TLS Client Hello packet doesn't contains SNI info.(offset == limit)")
                return null
            }

            //read Extensions:
            if (offsetValue + 2 > limit) return null
            val extensionsLength = CommonMethods.readShort(buffer, offsetValue).toInt() and 0xFFFF
            offsetValue += 2

            if (offsetValue + extensionsLength > limit) {
                Log.e(TAG, "TLS Client Hello packet is incomplete.")
                return null
            }

            while (offsetValue + 4 <= limit) {
                val type0 = buffer[offsetValue++].toInt() and 0xFF
                val type1 = buffer[offsetValue++].toInt() and 0xFF
                var length = CommonMethods.readShort(buffer, offsetValue).toInt() and 0xFFFF
                offsetValue += 2

                if (type0 == 0x00 && type1 == 0x00 && length > 5) { //have SNI
                    offsetValue += 5 //skip SNI header.
                    length -= 5 //SNI size;
                    if (offsetValue + length > limit) return null
                    val serverName = String(buffer, offsetValue, length)
                    Log.d(TAG, "SNI: $serverName")

                    return serverName
                } else {
                    offsetValue += length
                }
            }

            Log.e(TAG, "TLS Client Hello packet doesn't contains Host field info.")
        } else {
            Log.e(TAG, "Bad TLS Client Hello packet.")
        }
        return null
    }
}