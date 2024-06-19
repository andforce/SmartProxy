package me.smartproxy

import android.app.Application
import android.util.Log
import me.smartproxy.core.viewmodel.LocalVpnViewModel
import me.smartproxy.core.viewmodel.TcpViewModel
import me.smartproxy.core.viewmodel.UdpViewModel
import me.smartproxy.dns.ConfigReader
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ConfigReader.initHost(this)

        val hosts = ConfigReader.readHost(this)

        Log.d("App", "hosts: $hosts")

        startKoin {
            androidContext(this@App)

            modules(module {
                single { LocalVpnViewModel(get()) }
                single { UdpViewModel() }
                single { TcpViewModel() }
            })
        }
    }
}