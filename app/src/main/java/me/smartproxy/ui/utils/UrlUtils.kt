package me.smartproxy.ui.utils

import android.net.Uri
import me.smartproxy.core.VpnHelper
import java.io.File

object UrlUtils {
    fun isValidUrl(url: String?): Boolean {
        if (!VpnHelper.IS_ENABLE_REMOTE_PROXY) {
            return true
        }
        try {
            if (url == null || url.isEmpty()) return false

            if (url.startsWith("/")) { //file path
                val file = File(url)
                if (!file.exists()) {
                    return false
                }
                if (!file.canRead()) {
                    return false
                }
            } else { //url
                val uri = Uri.parse(url)
                if ("http" != uri.scheme && "https" != uri.scheme) {
                    return false
                }
                return uri.host != null
            }
            return true
        } catch (e: Exception) {
            return false
        }
    }
}