package me.smartproxy.core

import android.content.Intent
import android.util.Log
import kotlinx.coroutines.launch
import me.smartproxy.core.viewmodel.LocalVpnViewModel
import org.koin.java.KoinJavaComponent

class LocalVpnService : CoroutinesService() {
    companion object {
        private const val TAG = "LocalVpnService"
    }


    private val vpnViewModel: LocalVpnViewModel by lazy {
        KoinJavaComponent.get(LocalVpnViewModel::class.java)
    }

    private val m_VpnHelper: VpnHelper = VpnHelper(this)

    init {
        vpnViewModel.bindHelper(m_VpnHelper)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VPNService created.")

        serviceScope.launch {
            val config = VpnEstablishHelper.buildProxyConfig(this@LocalVpnService)
            Log.d(TAG, "VPNService config: $config")

            val pfd = VpnEstablishHelper.establishVPN(config, this@LocalVpnService)
            Log.d(TAG, "VPNService pfd: $pfd")

            pfd?.let {
                m_VpnHelper.startProcessPacket(config, pfd)
            }
        }
    }


    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        Log.e(TAG, "VPNService destroyed")
        m_VpnHelper.stop("VPNService destroyed")
    }
}
