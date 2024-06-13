package me.smartproxy.core

import android.net.VpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

open class CoroutinesService : VpnService() {
    val serviceScope = CoroutineScope(Dispatchers.IO)
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}