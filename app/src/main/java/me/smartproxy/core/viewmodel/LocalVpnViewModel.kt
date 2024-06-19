package me.smartproxy.core.viewmodel

import android.app.Application
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import me.smartproxy.core.LocalVpnService
import me.smartproxy.core.ProxyConfig
import me.smartproxy.core.VpnHelper
import me.smartproxy.tcpip.IPHeader
import me.smartproxy.tcpip.UDPHeader
import java.io.FileInputStream
import java.io.FileOutputStream

class LocalVpnViewModel(private val context: Application) : ViewModel() {

    private val helper = VpnHelper()

    private val _vpnStatusStateFlow = MutableStateFlow(-1)
    val vpnStatusStateFlow: StateFlow<Int> = _vpnStatusStateFlow

    private val _vpnStatusSharedFlow = MutableSharedFlow<Int>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val vpnStatusSharedFlow: SharedFlow<Int> = _vpnStatusSharedFlow

    fun updateVpnStatus(status: Int) {
        _vpnStatusStateFlow.value = status
        _vpnStatusSharedFlow.tryEmit(status)
    }

    fun isRunning(): Boolean {
        return _vpnStatusStateFlow.value == 1
    }

    fun tryStop() {
        helper.tryStop()
        context.stopService(Intent(context, LocalVpnService::class.java))
    }

    fun sendUDPPacket(ipHeader: IPHeader, udpHeader: UDPHeader) {
        helper.sendUDPPacket(ipHeader, udpHeader)
    }

    private val _requestResult = MutableSharedFlow<Int>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val vpnPermissionRequestResult: SharedFlow<Int> = _requestResult

    fun updateRequestResult(it: Int) {
        _requestResult.tryEmit(it)
    }

    suspend fun startDnsProxy(config: ProxyConfig, fos: FileOutputStream) {
        helper.startDnsProxy(config, fos)
    }

    suspend fun startTcpProxy(config: ProxyConfig) {
        helper.startTcpProxy(config)
    }

    suspend fun startProcessPacket(config: ProxyConfig, fis: FileInputStream, fos: FileOutputStream) = helper.startProcessPacket(config, fis, fos)
}