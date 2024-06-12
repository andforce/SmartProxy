package me.smartproxy.core

import android.content.Intent
import android.net.VpnService
import android.util.Log
import me.smartproxy.core.viewmodel.LocalVpnViewModel
import org.koin.java.KoinJavaComponent

class LocalVpnService : VpnService() {
    companion object {
        private const val TAG = "LocalVpnService"
    }


    private val vpnViewModel: LocalVpnViewModel by lazy {
        KoinJavaComponent.get(LocalVpnViewModel::class.java)
    }

    private val m_VpnHelper: VpnHelper = VpnHelper(this, this)

    init {
        vpnViewModel.bindHelper(m_VpnHelper)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VPNService created.")

        m_VpnHelper.start()
    }


    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        Log.e(TAG, "VPNService destoried.")
        m_VpnHelper.stop()
    }
}
