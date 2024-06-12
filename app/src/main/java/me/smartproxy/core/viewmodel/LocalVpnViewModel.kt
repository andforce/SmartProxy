package me.smartproxy.core.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import me.smartproxy.core.VpnHelper
import me.smartproxy.tcpip.IPHeader
import me.smartproxy.tcpip.UDPHeader
import java.net.DatagramSocket
import java.net.Socket

class LocalVpnViewModel : ViewModel() {

    private var helper: VpnHelper? = null

    fun bindHelper(helper: VpnHelper) {
        this.helper = helper
    }

    private val _vpnStatusStateFlow = MutableStateFlow(-1)
    val vpnStatusStateFlow: StateFlow<Int> = _vpnStatusStateFlow

    private val _vpnStatus = MutableSharedFlow<Int>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val vpnStatus: SharedFlow<Int> = _vpnStatus

    fun updateVpnStatus(status: Int) {
        _vpnStatus.tryEmit(status)
    }

    fun isRunning(): Boolean {
        return _vpnStatusStateFlow.value == 1
    }

    fun sendUDPPacket(ipHeader: IPHeader, udpHeader: UDPHeader) {
        helper?.sendUDPPacket(ipHeader, udpHeader)
    }

    fun protect(m_Client: DatagramSocket): Boolean {
        return helper?.protect(m_Client) ?: false
    }

    fun protect(socket: Socket): Boolean {
        return helper?.protect(socket) ?: false
    }

    private val _requestResult = MutableSharedFlow<Int>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val vpnPermissionRequestResult: SharedFlow<Int> = _requestResult

    fun updateRequestResult(it: Int) {
        _requestResult.tryEmit(it)
    }
}