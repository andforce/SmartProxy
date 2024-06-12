package me.smartproxy.core;

public interface OnStatusChangedListener {
    void onStatusChanged(String status, Boolean isRunning);

    void onLogReceived(String logString);
}
