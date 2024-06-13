package me.smartproxy.core

import android.content.Context
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.smartproxy.R
import me.smartproxy.dns.DNSUtils
import me.smartproxy.tcpip.CommonMethods

object VpnEstablishHelper {

    private const val TAG = "VpnEstablishHelper"
    suspend fun buildProxyConfig(context: Context, configUrl: String): ProxyConfig {
        return withContext(Dispatchers.IO) {
            val config = ProxyConfig()

            kotlin.runCatching {
                //加载配置文件
                if (VpnHelper.IS_ENABLE_REMOTE_PROXY) {
                    ChinaIpMaskManager.loadFromFile(context.resources.openRawResource(R.raw.ipmask)) //加载中国的IP段，用于IP分流。

                    try {
                        ProxyConfigHelper.loadFromUrl(configUrl)
                        if (config.defaultProxy == null) {
                            throw Exception("Invalid config file.")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load config from url: ${e.message}")
                    }
                }
            }

            config
        }
    }
    suspend fun establishVPN(config: ProxyConfig, service: VpnService): ParcelFileDescriptor? {
        return withContext(Dispatchers.IO) {
            val builder: VpnService.Builder = service.Builder()
            builder.setMtu(config.mtu)

            Log.d(VpnHelper.TAG, "setMtu: " + config.mtu)

            val ipAddress = config.defaultLocalIP
            builder.addAddress(ipAddress.Address, ipAddress.PrefixLength)
            Log.d(
                VpnHelper.TAG,
                "addAddress: " + ipAddress.Address + "/" + ipAddress.PrefixLength
            )

            for (dns in config.dnsList) {
                builder.addDnsServer(dns.Address)
                Log.d(VpnHelper.TAG, "addDnsServer: " + dns.Address)
            }

            if (config.routeList.isNotEmpty()) {
                for (routeAddress in config.routeList) {
                    builder.addRoute(routeAddress.Address, routeAddress.PrefixLength)
                    Log.d(
                        VpnHelper.TAG,
                        "addRoute: " + routeAddress.Address + "/" + routeAddress.PrefixLength
                    )
                }
                builder.addRoute(CommonMethods.ipIntToString(ProxyConfig.FAKE_NETWORK_IP), 16)

                Log.d(
                    VpnHelper.TAG,
                    "addRoute: " + CommonMethods.ipIntToString(ProxyConfig.FAKE_NETWORK_IP) + "/16"
                )
            } else {
                builder.addRoute("0.0.0.0", 0)
                Log.d(VpnHelper.TAG, "addDefaultRoute:0.0.0.0/0")
            }

            val dnsMap = DNSUtils.findAndroidPropDNS(service.applicationContext)
            for ((name, host) in dnsMap) {
                Log.d(VpnHelper.TAG, "findAndroidPropDNS: $name -> $host")

                if (DNSUtils.isIPv4Address(host)) {
                    builder.addRoute(host, 32)
                    Log.d(VpnHelper.TAG, "addRoute by DNS: $host/32")
                } else {
                    Log.d(
                        VpnHelper.TAG,
                        "addRoute by DNS, 暂时忽略 IPv6 类型的DNS: $host"
                    )
                }
            }

            builder.setSession(config.sessionName)
            builder.establish()
        }
    }
}