package me.smartproxy.core

import android.content.Intent
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.smartproxy.core.viewmodel.LocalVpnViewModel
import org.koin.java.KoinJavaComponent.get

class LocalVpnService : CoroutinesService() {
    companion object {
        private const val TAG = "LocalVpnService"
    }


    private val vpnViewModel = get<LocalVpnViewModel>(LocalVpnViewModel::class.java)

    private val vpnHelper: VpnHelper = VpnHelper(this)

    init {
        vpnViewModel.bindHelper(vpnHelper)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VPNService created.")

        serviceScope.launch {
            val config = VpnEstablishHelper.buildProxyConfig(this@LocalVpnService, "")
            Log.d(TAG, "VPNService config: $config")

            val pfd = VpnEstablishHelper.establishVPN(config, this@LocalVpnService)
            Log.d(TAG, "VPNService pfd: $pfd")

            launch {
                Log.d(TAG, "VPNService startDnsProxy pre")
                vpnHelper.startDnsProxy(config)
                Log.d(TAG, "VPNService startDnsProxy end")
            }

            launch {
                Log.d(TAG, "VPNService startTcpProxy pre")
                vpnHelper.startTcpProxy(config)
                Log.d(TAG, "VPNService startTcpProxy end")
            }

            // 等待DNS和TCP代理启动
            delay(1000)

            pfd?.let {
                Log.d(TAG, "VPNService startProcessPacket pre")
                vpnHelper.startProcessPacket(config, pfd)
            }?.onFailure {
                Log.e(TAG, "VPNService failed to establish VPN: $it")
            }

            Log.d(TAG, "VPNService startProcessPacket end")
        }
    }


    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.d(TAG, "VPNService onStartCommand")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        Log.e(TAG, "VPNService destroyed")
    }
}
