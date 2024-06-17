package me.smartproxy.tunnel.shadowsocks;

import java.util.HashMap;

import me.smartproxy.tunnel.IEncryptor;

public class EncryptorFactory {

    private static final HashMap<String, IEncryptor> EncryptorCache = new HashMap<String, IEncryptor>();

    public static IEncryptor createEncryptorByConfig(ShadowsocksConfig config) throws Exception {
        if ("table".equals(config.getEncryptMethod())) {
            IEncryptor tableEncryptor = EncryptorCache.get(config.toString());
            if (tableEncryptor == null) {
                tableEncryptor = new TableEncryptor(config.getPassword());
                if (EncryptorCache.size() > 2) {
                    EncryptorCache.clear();
                }
                EncryptorCache.put(config.toString(), tableEncryptor);
            }
            return tableEncryptor;
        }
        throw new Exception(String.format("Does not support the '%s' method. Only 'table' encrypt method was supported.", config.getEncryptMethod()));
    }
}
