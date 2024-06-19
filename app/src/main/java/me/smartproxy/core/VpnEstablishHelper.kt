package me.smartproxy.core

import android.app.Application
import android.net.VpnService
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.smartproxy.R
import me.smartproxy.dns.DNSUtils
import me.smartproxy.tcpip.CommonMethods
import me.smartproxy.ui.utils.Logger
import org.koin.java.KoinJavaComponent

object VpnEstablishHelper {

    private const val TAG = "VpnEstablishHelper"
    suspend fun buildProxyConfig(configUrl: String): ProxyConfig {
        return withContext(Dispatchers.IO) {
            val config = ProxyConfig()

            kotlin.runCatching {
                //加载配置文件
                if (VpnHelper.IS_ENABLE_REMOTE_PROXY) {
                    val context: Application = KoinJavaComponent.get(Application::class.java)
                    ChinaIpMaskManager.loadFromFile(context.resources.openRawResource(R.raw.ipmask)) //加载中国的IP段，用于IP分流。

                    try {
                        ProxyConfigHelper.loadFromUrl(configUrl)
                        if (config.defaultProxy == null) {
                            throw Exception("Invalid config file.")
                        }
                    } catch (e: Exception) {
                        Logger.e(TAG, "Failed to load config from url: ${e.message}")
                    }
                }
            }

            config
        }
    }
    suspend fun establishVPN(config: ProxyConfig): ParcelFileDescriptor? {
        return withContext(Dispatchers.IO) {
            val service = LocalVpnService::class.getOrNull()
            if (service == null) {
                Logger.e(TAG, "establishVPN: LocalVpnService is null")
                return@withContext null
            }

            val builder: VpnService.Builder = service.Builder()
            builder.setMtu(config.mtu)

            Logger.d(VpnHelper.TAG, "setMtu: " + config.mtu)

            val ipAddress = config.defaultLocalIP
            builder.addAddress(ipAddress.address, ipAddress.prefixLength)
            Logger.d(
                VpnHelper.TAG,
                "addAddress: " + ipAddress.address + "/" + ipAddress.prefixLength
            )

            for (dns in config.dnsList) {
                builder.addDnsServer(dns.address)
                Logger.d(VpnHelper.TAG, "addDnsServer: " + dns.address)
            }

            val dnsMap = DNSUtils.findAndroidPropDNS(service.applicationContext)
            for ((name, host) in dnsMap) {
                //Logger.d(VpnHelper.TAG, "findAndroidPropDNS: $name -> $host")

                if (DNSUtils.isIPv4Address(host)) {
                    builder.addDnsServer(host)
                    Logger.d(VpnHelper.TAG, "addDnsServer: $host")

                    builder.addRoute(host, 32)
                    Logger.d(VpnHelper.TAG, "addRoute by DNS: $host/32")
                } else {
                    Logger.d(
                        VpnHelper.TAG,
                        "addRoute by DNS, 暂时忽略 IPv6 类型的DNS: $host"
                    )
                }
            }


            if (config.routeList.isNotEmpty()) {
                for (routeAddress in config.routeList) {
                    builder.addRoute(routeAddress.address, routeAddress.prefixLength)
                    Logger.d(
                        VpnHelper.TAG,
                        "addRoute: " + routeAddress.address + "/" + routeAddress.prefixLength
                    )
                }
                builder.addRoute(CommonMethods.ipIntToString(ProxyConfig.FAKE_NETWORK_IP), 16)

                Logger.d(
                    VpnHelper.TAG,
                    "addRoute: " + CommonMethods.ipIntToString(ProxyConfig.FAKE_NETWORK_IP) + "/16"
                )
            } else {
                builder.addRoute("0.0.0.0", 0)
                Logger.d(VpnHelper.TAG, "addDefaultRoute:0.0.0.0/0")
            }

            builder.setSession(config.sessionName)
            builder.establish()
        }
    }
}