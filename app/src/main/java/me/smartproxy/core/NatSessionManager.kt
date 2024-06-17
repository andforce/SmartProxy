package me.smartproxy.core

import android.util.SparseArray
import me.smartproxy.dns.DnsProxy.Companion.reverseLookup
import me.smartproxy.tcpip.CommonMethods

object NatSessionManager {
    private const val MAX_SESSION_COUNT: Int = 60

    private const val SESSION_TIMEOUT_NS: Long = 60 * 1000000000L

    private val Sessions: SparseArray<NatSession> = SparseArray()

    fun getSession(portKey: Int): NatSession? {
        return Sessions[portKey]
    }

    fun getSessionCount(): Int {
        return Sessions.size()
    }


    private fun clearExpiredSessions() {
        val now = System.nanoTime()
        for (i in Sessions.size() - 1 downTo 0) {
            val session = Sessions.valueAt(i)
            if (now - session.lastNanoTime > SESSION_TIMEOUT_NS) {
                Sessions.removeAt(i)
            }
        }
    }


    fun createSession(portKey: Int, remoteIP: Int, remotePort: Short): NatSession {
        if (Sessions.size() > MAX_SESSION_COUNT) {
            clearExpiredSessions() //清理过期的会话。
        }

        val session = NatSession()
        session.lastNanoTime = System.nanoTime()
        session.remoteIP = remoteIP
        session.remotePort = remotePort

        if (ProxyConfig.isFakeIP(remoteIP)) {
            session.remoteHost = reverseLookup(remoteIP)
        }

        if (session.remoteHost == null) {
            session.remoteHost = CommonMethods.ipIntToString(remoteIP)
        }
        Sessions.put(portKey, session)
        return session
    }
}