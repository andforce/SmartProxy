package me.smartproxy.core

import android.os.Build
import android.util.Log
import me.smartproxy.tunnel.Config
import me.smartproxy.tunnel.httpconnect.HttpConnectConfig
import me.smartproxy.tunnel.shadowsocks.ShadowsocksConfig
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.util.EntityUtils
import java.io.FileInputStream
import java.net.InetSocketAddress
import java.util.Locale
import java.util.regex.Pattern

object ProxyConfigHelper {

    fun loadFromUrl(url: String): ProxyConfig {

        val config = ProxyConfig()
        config.clear()


        var lines: Array<String>? = null
        if (url[0] == '/') {
            lines = readConfigFromFile(url)
        } else {
            lines = downloadConfig(url)
        }

        var lineNumber = 0
        for (line in lines!!) {
            lineNumber++
            val items = line.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
            if (items.size < 2) {
                continue
            }

            val tagString = items[0].lowercase().trim { it <= ' ' }
            try {
                if (!tagString.startsWith("#")) {
                    Log.i("ProxyConfig", line)

                    if (tagString == "ip") {
                        config.addIPAddressToList(items, 1)
                    } else if (tagString == "dns") {
                        config.addDnsToList(items, 1)
                    } else if (tagString == "route") {
                        config.addRouteToList(items, 1)
                    } else if (tagString == "proxy") {
                        addProxyToList(config, items, 1)
                    } else if (tagString == "direct_domain") {
                        addDomainToHashMap(config, items, 1, false)
                    } else if (tagString == "proxy_domain") {
                        addDomainToHashMap(config, items, 1, true)
                    } else if (tagString == "dns_ttl") {
                        //m_dns_ttl = items[1].toInt()
                        config.dnsTtl = items[1].toInt()
                    } else if (tagString == "welcome_info") {
                        val m_welcome_info = line.substring(line.indexOf(" ")).trim { it <= ' ' }
                        config.welcomeInfo = m_welcome_info
                    } else if (tagString == "session_name") {
                        val m_session_name = items[1]
                        config.sessionName = m_session_name
                    } else if (tagString == "user_agent") {
                        val m_user_agent = line.substring(line.indexOf(" ")).trim { it <= ' ' }
                        config.userAgent = m_user_agent
                    } else if (tagString == "outside_china_use_proxy") {
                        val m_outside_china_use_proxy = convertToBool(items[1])
                        config.isOutsideChinaUseProxy = m_outside_china_use_proxy
                    } else if (tagString == "isolate_http_host_header") {
                        val m_isolate_http_host_header = convertToBool(items[1])
                        config.isIsolateHttpHostHeader = m_isolate_http_host_header
                    } else if (tagString == "mtu") {
                        val m_mtu = items[1].toInt()
                        config.setMtu(m_mtu)
                    }
                }
            } catch (e: Exception) {
                throw Exception(
                    String.format(
                        "SmartProxy config file parse error: line:%d, tag:%s, error:%s",
                        lineNumber,
                        tagString,
                        e
                    )
                )
            }
        }

        //查找默认代理。
        if (config.proxyList.isEmpty()) {
            tryAddProxy(c = config, lines)
        }

        return config
    }


    @Throws(java.lang.Exception::class)
    private fun addProxyToList(c: ProxyConfig, items: Array<String>, offset: Int) {
        for (i in offset until items.size) {
            var proxyString = items[i].trim { it <= ' ' }
            var config: Config? = null
            if (proxyString.startsWith("ss://")) {
                config = ShadowsocksConfig.parse(proxyString)
            } else {
                if (!proxyString.lowercase(Locale.getDefault()).startsWith("http://")) {
                    proxyString = "http://$proxyString"
                }
                config = HttpConnectConfig.parse(proxyString)
            }
            c.addProxyConfig(config)
        }
    }

    private fun addDomainToHashMap(
        c: ProxyConfig,
        items: Array<String>,
        offset: Int,
        state: Boolean
    ) {
        for (i in offset until items.size) {
            var domainString = items[i].lowercase(Locale.getDefault()).trim { it <= ' ' }
            if (domainString[0] == '.') {
                domainString = domainString.substring(1)
            }
            //m_DomainMap.put(domainString, state)
            c.addDomainMap(domainString, state)
        }
    }

    private fun convertToBool(valueString: String): Boolean {
        var valueString: String? = valueString
        if (valueString == null || valueString.isEmpty()) return false
        valueString = valueString.lowercase().trim { it <= ' ' }
        return if (valueString == "on" || valueString == "1" || valueString == "true" || valueString == "yes") {
            true
        } else {
            false
        }
    }


    private fun tryAddProxy(c: ProxyConfig, lines: Array<String>) {
        for (line in lines) {
            val p = Pattern.compile("proxy\\s+([^:]+):(\\d+)", Pattern.CASE_INSENSITIVE)
            val m = p.matcher(line)
            while (m.find()) {
                val config = HttpConnectConfig()
                config.socketAddress = InetSocketAddress(m.group(1), m.group(2).toInt())
                c.addProxyConfig(config)
            }
        }
    }

    @Throws(java.lang.Exception::class)
    private fun downloadConfig(url: String): Array<String> {
        try {
            val client: HttpClient = DefaultHttpClient()
            val requestGet = HttpGet(url)

            requestGet.addHeader("X-Android-MODEL", Build.MODEL)
            requestGet.addHeader("X-Android-SDK_INT", Build.VERSION.SDK_INT.toString())
            requestGet.addHeader("X-Android-RELEASE", Build.VERSION.RELEASE)
            requestGet.setHeader("User-Agent", System.getProperty("http.agent"))
            val response = client.execute(requestGet)

            val configString = EntityUtils.toString(response.entity, "UTF-8")
            val lines = configString.split("\\n".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
            return lines
        } catch (e: java.lang.Exception) {
            throw java.lang.Exception(String.format("Download config file from %s failed.", url))
        }
    }

    @Throws(java.lang.Exception::class)
    private fun readConfigFromFile(path: String): Array<String> {
        val sBuilder = StringBuilder()
        var inputStream: FileInputStream? = null
        try {
            val buffer = ByteArray(8192)
            var count = 0
            inputStream = FileInputStream(path)
            while ((inputStream.read(buffer).also { count = it }) > 0) {
                sBuilder.append(String(buffer, 0, count, charset("UTF-8")))
            }
            return sBuilder.toString().split("\\n".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
        } catch (e: java.lang.Exception) {
            throw java.lang.Exception(String.format("Can't read config file: %s", path))
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close()
                } catch (e2: java.lang.Exception) {
                }
            }
        }
    }
}