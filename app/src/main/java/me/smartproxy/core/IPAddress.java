package me.smartproxy.core;

import androidx.annotation.NonNull;

import java.util.Locale;

public class IPAddress {
    public final String Address;
    public final int PrefixLength;

    public IPAddress(String address, int prefixLength) {
        this.Address = address;
        this.PrefixLength = prefixLength;
    }

    public IPAddress(String ipAddresString) {
        String[] arrStrings = ipAddresString.split("/");
        String address = arrStrings[0];
        int prefixLength = 32;
        if (arrStrings.length > 1) {
            prefixLength = Integer.parseInt(arrStrings[1]);
        }
        this.Address = address;
        this.PrefixLength = prefixLength;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "%s/%d", Address, PrefixLength);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        } else {
            return this.toString().equals(o.toString());
        }
    }
}
