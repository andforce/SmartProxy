package me.smartproxy.tunnel.httpconnect;

import android.net.Uri;

import java.net.InetSocketAddress;
import java.util.Locale;

import me.smartproxy.tunnel.Config;

public class HttpConnectConfig extends Config {
    public String userName;
    public String password;

    public static HttpConnectConfig parse(String proxyInfo) {
        HttpConnectConfig config = new HttpConnectConfig();
        Uri uri = Uri.parse(proxyInfo);
        String userInfoString = uri.getUserInfo();
        if (userInfoString != null) {
            String[] userStrings = userInfoString.split(":");
            config.userName = userStrings[0];
            if (userStrings.length >= 2) {
                config.password = userStrings[1];
            }
        }
        config.socketAddress = new InetSocketAddress(uri.getHost(), uri.getPort());
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
        return String.format(Locale.ENGLISH, "http://%s:%s@%s", userName, password, socketAddress);
    }
}
