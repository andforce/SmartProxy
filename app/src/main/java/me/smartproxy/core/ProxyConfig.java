package me.smartproxy.core;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import me.smartproxy.ui.utils.Logger;
import java.util.TimerTask;

import me.smartproxy.tcpip.CommonMethods;
import me.smartproxy.tunnel.Config;


public class ProxyConfig {

    public final static int FAKE_NETWORK_MASK = CommonMethods.ipStringToInt("255.255.0.0");
    public final static int FAKE_NETWORK_IP = CommonMethods.ipStringToInt("10.231.0.0");

    private ArrayList<IPAddress> ipList = new ArrayList<>();
    private ArrayList<IPAddress> dnsList = new ArrayList<>();
    private ArrayList<IPAddress> routeList = new ArrayList<>();
    private ArrayList<Config> proxyList = new ArrayList<>();
    private HashMap<String, Boolean> domainMap = new HashMap<>();

    private int dnsTtl;
    private String welcomeInfo;
    private String sessionName;
    private String userAgent;
    private boolean outsideChinaUseProxy = true;
    private boolean isolateHttpHostHeader = true;
    private int mtu;

    private Timer m_Timer;

    public ArrayList<IPAddress> getIpList() {
        return ipList;
    }

    public void setIpList(ArrayList<IPAddress> ipList) {
        this.ipList = ipList;
    }

    public ArrayList<IPAddress> getDnsList() {
        return dnsList;
    }

    public void setDnsList(ArrayList<IPAddress> dnsList) {
        this.dnsList = dnsList;
    }

    public ArrayList<IPAddress> getRouteList() {
        return routeList;
    }

    public void setRouteList(ArrayList<IPAddress> routeList) {
        this.routeList = routeList;
    }

    public ArrayList<Config> getProxyList() {
        return proxyList;
    }

    public void setProxyList(ArrayList<Config> proxyList) {
        this.proxyList = proxyList;
    }

    public HashMap<String, Boolean> getDomainMap() {
        return domainMap;
    }

    public void setDomainMap(HashMap<String, Boolean> domainMap) {
        this.domainMap = domainMap;
    }

    public int getDnsTtl() {
        return dnsTtl;
    }

    public void setDnsTtl(int dnsTtl) {
        this.dnsTtl = dnsTtl;
    }

    public String getWelcomeInfo() {
        return welcomeInfo;
    }

    public void setWelcomeInfo(String welcomeInfo) {
        this.welcomeInfo = welcomeInfo;
    }

    public void setSessionName(String sessionName) {
        this.sessionName = sessionName;
    }

    public String getSessionName() {
        if (VpnHelper.IS_ENABLE_REMOTE_PROXY) {
            if (sessionName == null) {
                InetSocketAddress socketAddress = getDefaultProxy().getSocketAddress();
                if (socketAddress != null) {
                    sessionName = socketAddress.getHostName();
                }
            }
            return sessionName;
        } else {
            return "m_session_name";
        }
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public boolean isOutsideChinaUseProxy() {
        return outsideChinaUseProxy;
    }

    public void setOutsideChinaUseProxy(boolean outsideChinaUseProxy) {
        this.outsideChinaUseProxy = outsideChinaUseProxy;
    }

    public boolean isIsolateHttpHostHeader() {
        return isolateHttpHostHeader;
    }

    public void setIsolateHttpHostHeader(boolean isolateHttpHostHeader) {
        this.isolateHttpHostHeader = isolateHttpHostHeader;
    }

    public void setMtu(int mtu) {
        this.mtu = mtu;
    }

    private final TimerTask m_Task = new TimerTask() {
        @Override
        public void run() {

            //定时更新dns缓存
            try {
                for (int i = 0; i < proxyList.size(); i++) {
                    try {
                        Config config = proxyList.get(0);
                        InetSocketAddress inetSocketAddress = config.getSocketAddress();
                        if (inetSocketAddress == null) {
                            continue;
                        }
                        InetAddress address = InetAddress.getByName(inetSocketAddress.getHostName());
                        if (!address.equals(config.getSocketAddress().getAddress())) {
                            config.setSocketAddress(new InetSocketAddress(address, config.getSocketAddress().getPort()));

                        }
                    } catch (Exception e) {
                        Logger.e("ProxyConfig", "update dns cache error", e);
                    }
                }
            } catch (Exception e) {
                Logger.e("ProxyConfig", "update dns cache error", e);
            }
        }
    };


    public static boolean isFakeIP(int ip) {
        return (ip & ProxyConfig.FAKE_NETWORK_MASK) == ProxyConfig.FAKE_NETWORK_IP;
    }

    public Config getDefaultProxy() {
        if (!proxyList.isEmpty()) {
            return proxyList.get(0);
        } else {
            return null;
        }
    }

    public Config getDefaultTunnelConfig(InetSocketAddress destAddress) {
        return getDefaultProxy();
    }

    public IPAddress getDefaultLocalIP() {
        if (!ipList.isEmpty()) {
            return ipList.get(0);
        } else {
            return new IPAddress("10.8.0.2", 32);
        }
    }

    public int getDnsTTL() {
        if (dnsTtl < 30) {
            dnsTtl = 30;
        }
        return dnsTtl;
    }

    public String getUserAgent() {
        if (userAgent == null || userAgent.isEmpty()) {
            userAgent = System.getProperty("http.agent");
        }
        return userAgent;
    }

    public int getMtu() {
        if (mtu > 1400 && mtu <= 20000) {
            return mtu;
        } else {
            return 20000;
        }
    }

    private Boolean getDomainState(String domain) {
        domain = domain.toLowerCase();
        while (!domain.isEmpty()) {
            Boolean stateBoolean = domainMap.get(domain);
            if (stateBoolean != null) {
                return stateBoolean;
            } else {
                int start = domain.indexOf('.') + 1;
                if (start > 0 && start < domain.length()) {
                    domain = domain.substring(start);
                } else {
                    return null;
                }
            }
        }
        return null;
    }

    public boolean needProxy(String host, int ip) {
        if (!VpnHelper.IS_ENABLE_REMOTE_PROXY) {
            return false;
        }
        if (host != null) {
            Boolean stateBoolean = getDomainState(host);
            if (stateBoolean != null) {
                return stateBoolean;
            }
        }

        if (isFakeIP(ip)) {
            return true;
        }

        if (outsideChinaUseProxy && ip != 0) {
            return !ChinaIpMaskManager.INSTANCE.isIPInChina(ip);
        }
        return false;
    }

    public void clear() {
        ipList.clear();
        dnsList.clear();
        routeList.clear();
        proxyList.clear();
        domainMap.clear();
    }

    public void addIPAddressToList(String[] items, int offset) {
        addIPAddressToList(items, offset, ipList);
    }

    public void addDnsToList(String[] items, int offset) {
        addIPAddressToList(items, offset, dnsList);
    }

    public void addRouteToList(String[] items, int offset) {
        addIPAddressToList(items, offset, routeList);
    }

    private void addIPAddressToList(String[] items, int offset, ArrayList<IPAddress> list) {
        for (int i = offset; i < items.length; i++) {
            String item = items[i].trim().toLowerCase();
            if (item.startsWith("#")) {
                break;
            } else {
                IPAddress ip = new IPAddress(item);
                if (!list.contains(ip)) {
                    list.add(ip);
                }
            }
        }
    }

    public void addDomainMap(String key, boolean value) {
        domainMap.put(key, value);
    }

    public void addProxyConfig(Config config) {
        if (!proxyList.contains(config)) {
            proxyList.add(config);
            InetSocketAddress inetSocketAddress = config.getSocketAddress();
            if (inetSocketAddress != null) {
                domainMap.put(inetSocketAddress.getHostName(), false);
            }
        }
    }

    public void startTimer() {
        if (m_Timer != null) {
            m_Timer.cancel();
            m_Timer = null;
        }

        m_Timer = new Timer();
        m_Timer.schedule(m_Task, 120000, 120000);//每两分钟刷新一次。
    }

    public void stopTimer() {
        m_Timer.cancel();
        m_Timer = null;
    }
}
