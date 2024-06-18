package me.smartproxy.core

import android.util.SparseArray
import me.smartproxy.dns.DnsProxy.Companion.reverseLookup
import me.smartproxy.tcpip.CommonMethods

object NatSessionManager : Pool<NatSession>() {
    private const val MAX_SESSION_COUNT: Int = 60

    private const val SESSION_TIMEOUT_NS: Long = 60 * 1000000000L

    private val sessionsCache: SparseArray<NatSession> = SparseArray()

    fun getSession(portKey: Int): NatSession? {
        return sessionsCache[portKey]
    }

    fun getSessionCount(): Int {
        return sessionsCache.size()
    }

    private fun clearExpiredSessions() {
        val now = System.nanoTime()
        for (i in sessionsCache.size() - 1 downTo 0) {
            val session = sessionsCache.valueAt(i)
            if (now - session.lastNanoTime > SESSION_TIMEOUT_NS) {
                sessionsCache.removeAt(i)

                // Clear the session and recycle it
                session.clear().also {
                    recycle(session)
                }
            }
        }
    }


    fun createSession(portKey: Int, remoteIP: Int, remotePort: Short): NatSession {
        if (sessionsCache.size() > MAX_SESSION_COUNT) {
            clearExpiredSessions() //清理过期的会话。
        }

        val session = take()
        session.lastNanoTime = System.nanoTime()
        session.remoteIP = remoteIP
        session.remotePort = remotePort

        if (ProxyConfig.isFakeIP(remoteIP)) {
            session.remoteHost = reverseLookup(remoteIP)
        }

        if (session.remoteHost == null) {
            session.remoteHost = CommonMethods.ipIntToString(remoteIP)
        }

        session.bytesSent = 0
        session.packetSent = 0


        sessionsCache.put(portKey, session)
        return session
    }

    override fun create(): NatSession {
        return NatSession()
    }
}