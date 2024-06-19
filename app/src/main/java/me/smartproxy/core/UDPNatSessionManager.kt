package me.smartproxy.core

import android.util.SparseArray
import me.smartproxy.dns.DnsProxy
import me.smartproxy.tcpip.CommonMethods
import java.util.concurrent.ConcurrentHashMap

object UDPNatSessionManager : Pool<NatSession>() {
    private const val MAX_SESSION_COUNT: Int = 60

    private const val SESSION_TIMEOUT_NS: Long = 60 * 1000000000L

    private val sessionsCache: SparseArray<NatSession> = SparseArray()
    private val sUDPNATSessions: ConcurrentHashMap<NatSession, Short> = ConcurrentHashMap<NatSession, Short>()

    fun getSessionOrNull(portKey: Int): NatSession? {
        return sessionsCache[portKey]
    }

    fun getPort(session: NatSession) : Short{
        return sUDPNATSessions[session] ?: -1
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

                sUDPNATSessions.remove(session)

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

        val session = take().apply {
            this.lastNanoTime = System.nanoTime()
            this.remoteIP = remoteIP
            this.remotePort = remotePort
            if (ProxyConfig.isFakeIP(remoteIP)) {
                this.remoteHost = DnsProxy.reverseLookup(remoteIP)
            }
            if (this.remoteHost.isNullOrEmpty()) {
                this.remoteHost = CommonMethods.ipIntToString(remoteIP)
            }
            this.bytesSent = 0
            this.packetSent = 0
        }.also {
            sessionsCache.put(portKey, it)
            sUDPNATSessions[it] = portKey.toShort()
        }

        return session
    }

    override fun create(): NatSession {
        return NatSession()
    }
}