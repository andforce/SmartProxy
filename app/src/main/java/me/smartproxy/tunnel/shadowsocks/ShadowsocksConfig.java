package me.smartproxy.tunnel.shadowsocks;

import android.net.Uri;
import android.util.Base64;

import java.net.InetSocketAddress;

import me.smartproxy.tunnel.Config;

public class ShadowsocksConfig extends Config {
    public String encryptMethod;
    public String password;

    public static ShadowsocksConfig parse(String proxyInfo) throws Exception {
        ShadowsocksConfig config = new ShadowsocksConfig();
        Uri uri = Uri.parse(proxyInfo);
        if (uri.getPort() == -1) {
            String base64String = uri.getHost();
            proxyInfo = "ss://" + new String(Base64.decode(base64String.getBytes("ASCII"), Base64.DEFAULT));
            uri = Uri.parse(proxyInfo);
        }

        String userInfoString = uri.getUserInfo();
        if (userInfoString != null) {
            String[] userStrings = userInfoString.split(":");
            config.encryptMethod = userStrings[0];
            if (userStrings.length >= 2) {
                config.password = userStrings[1];
            }
        }
        config.setSocketAddress(new InetSocketAddress(uri.getHost(), uri.getPort()));
        config.setEncryptor(EncryptorFactory.createEncryptorByConfig(config));
        return config;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null)
            return false;
        return this.toString().equals(o.toString());
    }

    @Override
    public String toString() {
        return String.format("ss://%s:%s@%s", encryptMethod, password, getSocketAddress());
    }
}
