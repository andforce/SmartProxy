package me.smartproxy.core.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class LocalVpnViewModel : ViewModel() {

    private val _vpnStatus = MutableSharedFlow<Int>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val vpnStatus: SharedFlow<Int> = _vpnStatus

    fun updateVpnStatus(status: Int) {
        _vpnStatus.tryEmit(status)
    }
}