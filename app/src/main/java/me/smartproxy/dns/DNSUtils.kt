package me.smartproxy.dns

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.StringTokenizer
import java.util.regex.Pattern

object DNSUtils {
    private const val REG_V4 =
        "((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)"
    private const val REG_V6 = "([\\da-fA-F]{1,4}:){7}[\\da-fA-F]{1,4}|:((:[\\da-fA-F]1,4)1,6|:)"


    fun isIPv4Address(ip: String): Boolean {
        val pattern = Pattern.compile(REG_V4)
        val matcher = pattern.matcher(ip)
        return matcher.matches()
    }

    // [vendor.net.seth_lte8.dns1]: [183.230.126.225]
    //[vendor.net.seth_lte8.dns2]: [183.230.126.224]
    //[vendor.net.seth_lte8.ipv6_dns1]: [2409:8060:20ea:0201:0000:0000:0000:0001]
    //[vendor.net.seth_lte8.ipv6_dns2]: [2409:8060:20ea:0101:0000:0000:0000:0001]
    fun findAndroidPropDNS(context: Context): Map<String, String> {

        val dns: MutableMap<String, String> = HashMap()

        try {
            var line: String?
            val p = Runtime.getRuntime().exec("getprop")
            p.inputStream.use { `in` ->
                InputStreamReader(`in`).use { isr ->
                    BufferedReader(isr).use { br ->
                        while ((br.readLine().also { line = it }) != null) {
                            val t = StringTokenizer(line, ":")
                            val name = t.nextToken()
                            if (name.contains(".dns")) {
                                val v = t.nextToken()
                                // 正则表达式查找出IP地址
                                val m = Pattern.compile(REG_V4).matcher(v)
                                if (m.find()) {
                                    dns[name] = m.group()
                                }
                            } else if (name.contains("_dns")) {
                                val v = t.nextToken()
                                // 正则表达式查找出IP地址
                                val m = Pattern.compile(REG_V6).matcher(v)
                                if (m.find()) {
                                    dns[name] = m.group()
                                }
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            // ignore resolutely
        }


        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        for (network in connectivityManager.allNetworks) {
            val networkInfo = connectivityManager.getNetworkInfo(network)
            networkInfo?.let {
                if (networkInfo.isConnected) {
                    val linkProperties = connectivityManager.getLinkProperties(network)
                    linkProperties?.let {
                        Log.d("DnsInfo", "iface = " + linkProperties.interfaceName)
                        val addressList = linkProperties.dnsServers
                        for (address in addressList) {
                            address?.hostAddress?.let { host ->
                                Log.d("DnsInfo", "dns = $host")
                                if (address is java.net.Inet6Address) {
                                    val key = "ipv6_dns_$host"
                                    dns[key] = host
                                    Log.d("DnsInfo", "key = $key, $host")
                                } else {
                                    val key = "dns_$host"
                                    dns[key] = host
                                    Log.d("DnsInfo", "key = $key, $host")
                                }
                            }
                        }
                    }
                }
            }
        }
        return dns
    }
}