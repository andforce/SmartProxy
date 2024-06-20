package me.smartproxy.core

import android.util.SparseArray
import me.smartproxy.dns.LocalDnsServer
import me.smartproxy.tcpip.CommonMethods

object NatSessionManager : Pool<NatSession>() {
    private const val MAX_SESSION_COUNT: Int = 60

    private const val SESSION_TIMEOUT_NS: Long = 60 * 1000000000L

    private val sessionsCacheTCP: SparseArray<NatSession> = SparseArray()

    fun getSessionOrNull(portKey: Int): NatSession? {
        return sessionsCacheTCP[portKey]
    }

    fun getSessionCount(): Int {
        return sessionsCacheTCP.size()
    }

    private fun clearExpiredSessions() {
        val now = System.nanoTime()
        for (i in sessionsCacheTCP.size() - 1 downTo 0) {
            val session = sessionsCacheTCP.valueAt(i)
            if (now - session.lastNanoTime > SESSION_TIMEOUT_NS) {
                sessionsCacheTCP.removeAt(i)

                // Clear the session and recycle it
                session.clear().also {
                    recycle(session)
                }
            }
        }
    }


    fun createSession(portKey: Int, remoteIP: Int, remotePort: Short): NatSession {
        if (sessionsCacheTCP.size() > MAX_SESSION_COUNT) {
            clearExpiredSessions() //清理过期的会话。
        }

        val session = take().apply {
            this.lastNanoTime = System.nanoTime()
            this.remoteIP = remoteIP
            this.remotePort = remotePort
            if (ProxyConfig.isFakeIP(remoteIP)) {
                this.remoteHost = LocalDnsServer.reverseLookup(remoteIP)
            }
            if (this.remoteHost.isNullOrEmpty()) {
                this.remoteHost = CommonMethods.ipIntToString(remoteIP)
            }
            this.bytesSent = 0
            this.packetSent = 0
        }.also {
            sessionsCacheTCP.put(portKey, it)
        }

        return session
    }

    override fun create(): NatSession {
        return NatSession()
    }
}