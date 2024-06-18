package me.smartproxy.core

import android.util.SparseIntArray
import me.smartproxy.tcpip.CommonMethods
import me.smartproxy.ui.utils.Logger
import java.io.InputStream

object ChinaIpMaskManager {
    private const val TAG = "ChinaIpMaskManager"

    private val ChinaIpMaskDict = SparseIntArray(3000)
    private val MaskDict = SparseIntArray()

    fun isIPInChina(ip: Int): Boolean {
        for (i in 0 until MaskDict.size()) {
            val mask = MaskDict.keyAt(i)
            val networkIP = ip and mask
            val mask2 = ChinaIpMaskDict[networkIP]
            if (mask2 == mask) {
                return true
            }
        }
        return false
    }

    fun loadFromFile(inputStream: InputStream) {
        var count: Int
        val buffer = ByteArray(4096)
        runCatching {
            inputStream.use {
                while ((inputStream.read(buffer).also { count = it }) > 0) {
                    var i = 0
                    while (i < count) {
                        val ip = CommonMethods.readInt(buffer, i)
                        val mask = CommonMethods.readInt(buffer, i + 4)
                        ChinaIpMaskDict.put(ip, mask)
                        MaskDict.put(mask, mask)
                        i += 8
                    }
                }
                Logger.d(TAG, "loadFromFile: ChinaIpMask records count: ${ChinaIpMaskDict.size()}")
            }
        }.onFailure {
            Logger.e(TAG, "loadFromFile: ", it)
        }
    }
}
