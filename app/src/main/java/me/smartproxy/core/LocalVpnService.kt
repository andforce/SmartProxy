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
import java.io.FileInputStream
import java.io.FileOutputStream

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

        Logger.d(TAG, "created()")

        serviceScope.launch {
            val config = VpnEstablishHelper.buildProxyConfig("")
            Logger.d(TAG, "config: $config")

            val pfd = VpnEstablishHelper.establishVPN(config)
            Logger.d(TAG, "pfd: $pfd")
            pfd?.use {
                val fis = FileInputStream(it.fileDescriptor)
                val fos = FileOutputStream(it.fileDescriptor)

                launch {
                    Logger.d(TAG, "startDnsProxy pre")
                    vpnViewModel.startDnsProxy(config, fos)
                    Logger.d(TAG, "startDnsProxy end")
                }

                launch {
                    Logger.d(TAG, "startTcpProxy pre")
                    vpnViewModel.startTcpProxy(config)
                    Logger.d(TAG, "startTcpProxy end")
                }

                // 等待DNS和TCP代理启动
                delay(100)

                Logger.d(TAG, "startProcessPacket pre")
                vpnViewModel.startProcessPacket(config, fis, fos)
            } ?: run {
                Logger.e(TAG, "failed to establishVPN() pfd is null")
                vpnViewModel.tryStop()
            }
        }
    }


    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Logger.d(TAG, "onStartCommand")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        GlobalContext.get().deleteScope(koinScope.id)
        Logger.e(TAG, "destroyed")
    }
}
