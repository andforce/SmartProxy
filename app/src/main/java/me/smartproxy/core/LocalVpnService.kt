package me.smartproxy.core

import android.content.Intent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.smartproxy.core.viewmodel.LocalVpnViewModel
import me.smartproxy.ui.utils.Logger
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.java.KoinJavaComponent.get

class LocalVpnService : CoroutinesService() {
    companion object {
        private const val TAG = "LocalVpnService"
    }

    private var koinScope: Scope

    private val vpnViewModel = get<LocalVpnViewModel>(LocalVpnViewModel::class.java)

    init {
        val name = this.javaClass.name
        koinScope = GlobalContext.get().getOrCreateScope(name, named(name))
        koinScope.declare(this)
    }

    override fun onCreate() {
        super.onCreate()

        Logger.d(TAG, "VPNService created()")

        serviceScope.launch {
            val config = VpnEstablishHelper.buildProxyConfig("")
            Logger.d(TAG, "VPNService config: $config")

            val pfd = VpnEstablishHelper.establishVPN(config)
            Logger.d(TAG, "VPNService pfd: $pfd")
            pfd?.let {
                launch {
                    Logger.d(TAG, "VPNService startDnsProxy pre")
                    vpnViewModel.startDnsProxy(config)
                    Logger.d(TAG, "VPNService startDnsProxy end")
                }

                launch {
                    Logger.d(TAG, "VPNService startTcpProxy pre")
                    vpnViewModel.startTcpProxy(config)
                    Logger.d(TAG, "VPNService startTcpProxy end")
                }

                // 等待DNS和TCP代理启动
                delay(500)

                Logger.d(TAG, "VPNService startProcessPacket pre")
                vpnViewModel.startProcessPacket(config, pfd)
            } ?: run {
                Logger.e(TAG, "VPNService failed to establishVPN() pfd is null")
                vpnViewModel.tryStop()
            }
        }
    }


    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Logger.d(TAG, "VPNService onStartCommand")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        GlobalContext.get().deleteScope(koinScope.id)
        Logger.e(TAG, "VPNService destroyed")
    }
}
