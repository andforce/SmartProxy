package me.smartproxy.core

import android.content.Intent
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.smartproxy.core.viewmodel.LocalVpnViewModel
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.java.KoinJavaComponent.get

class LocalVpnService : CoroutinesService() {
    companion object {
        private const val TAG = "LocalVpnService"
    }

    private lateinit var koinScope: Scope

    private val vpnViewModel = get<LocalVpnViewModel>(LocalVpnViewModel::class.java)

    private val vpnHelper: VpnHelper = VpnHelper(this)

    init {
        vpnViewModel.bindHelper(vpnHelper)
    }

    override fun onCreate() {
        // 把当前Service实例绑定到Koin的Scope中
        koinScope = GlobalContext.get().getOrCreateScope(this.javaClass.name, named(this.javaClass.name))
        koinScope.declare(this)

        val localVpnService: LocalVpnService = GlobalContext.get().getScope(this.javaClass.name).get()

        super.onCreate()
        Log.d(TAG, "VPNService created: $localVpnService")

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
                vpnHelper.startTcpProxy(this@LocalVpnService, config)
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
        GlobalContext.get().deleteScope(koinScope.id)
    }
}
