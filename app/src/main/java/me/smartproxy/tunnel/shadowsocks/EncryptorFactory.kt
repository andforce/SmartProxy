package me.smartproxy.tunnel.shadowsocks

import me.smartproxy.tunnel.IEncryptor

object EncryptorFactory {
    private val EncryptorCache = HashMap<String, IEncryptor>()

    @Throws(Exception::class)
    fun createEncryptorByConfig(config: ShadowsocksConfig): IEncryptor {
        if ("table" == config.encryptMethod) {
            var tableEncryptor = EncryptorCache[config.toString()]
            if (tableEncryptor == null) {
                tableEncryptor = TableEncryptor(config.password)
                if (EncryptorCache.size > 2) {
                    EncryptorCache.clear()
                }
                EncryptorCache[config.toString()] = tableEncryptor
            }
            return tableEncryptor
        }
        throw Exception(
            "Does not support the '${config.encryptMethod}' method. Only 'table' encrypt method was supported.",
        )
    }
}
