package me.smartproxy.core;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import me.smartproxy.tcpip.CommonMethods;
import me.smartproxy.tunnel.Config;


public class ProxyConfig {

    public static String ConfigUrl;

    public final static int FAKE_NETWORK_MASK = CommonMethods.ipStringToInt("255.255.0.0");
    public final static int FAKE_NETWORK_IP = CommonMethods.ipStringToInt("10.231.0.0");

    private ArrayList<IPAddress> m_IpList = new ArrayList<>();
    private ArrayList<IPAddress> m_DnsList = new ArrayList<>();
    private ArrayList<IPAddress> m_RouteList = new ArrayList<>();
    private ArrayList<Config> m_ProxyList = new ArrayList<>();
    private HashMap<String, Boolean> m_DomainMap = new HashMap<>();

    private int m_dns_ttl;
    private String m_welcome_info;
    private String m_session_name;
    private String m_user_agent;
    private boolean m_outside_china_use_proxy = true;
    private boolean m_isolate_http_host_header = true;
    private int m_mtu;

    private Timer m_Timer;

    public ProxyConfig() {
        m_Timer = new Timer();
        m_Timer.schedule(m_Task, 120000, 120000);//每两分钟刷新一次。
    }

    public ArrayList<IPAddress> getM_IpList() {
        return m_IpList;
    }

    public void setM_IpList(ArrayList<IPAddress> m_IpList) {
        this.m_IpList = m_IpList;
    }

    public ArrayList<IPAddress> getM_DnsList() {
        return m_DnsList;
    }

    public void setM_DnsList(ArrayList<IPAddress> m_DnsList) {
        this.m_DnsList = m_DnsList;
    }

    public ArrayList<IPAddress> getM_RouteList() {
        return m_RouteList;
    }

    public void setM_RouteList(ArrayList<IPAddress> m_RouteList) {
        this.m_RouteList = m_RouteList;
    }

    public ArrayList<Config> getM_ProxyList() {
        return m_ProxyList;
    }

    public void setM_ProxyList(ArrayList<Config> m_ProxyList) {
        this.m_ProxyList = m_ProxyList;
    }

    public HashMap<String, Boolean> getM_DomainMap() {
        return m_DomainMap;
    }

    public void setM_DomainMap(HashMap<String, Boolean> m_DomainMap) {
        this.m_DomainMap = m_DomainMap;
    }

    public int getM_dns_ttl() {
        return m_dns_ttl;
    }

    public void setM_dns_ttl(int m_dns_ttl) {
        this.m_dns_ttl = m_dns_ttl;
    }

    public String getM_welcome_info() {
        return m_welcome_info;
    }

    public void setM_welcome_info(String m_welcome_info) {
        this.m_welcome_info = m_welcome_info;
    }

    public String getM_session_name() {
        return m_session_name;
    }

    public void setM_session_name(String m_session_name) {
        this.m_session_name = m_session_name;
    }

    public String getM_user_agent() {
        return m_user_agent;
    }

    public void setM_user_agent(String m_user_agent) {
        this.m_user_agent = m_user_agent;
    }

    public boolean isM_outside_china_use_proxy() {
        return m_outside_china_use_proxy;
    }

    public void setM_outside_china_use_proxy(boolean m_outside_china_use_proxy) {
        this.m_outside_china_use_proxy = m_outside_china_use_proxy;
    }

    public boolean isM_isolate_http_host_header() {
        return m_isolate_http_host_header;
    }

    public void setM_isolate_http_host_header(boolean m_isolate_http_host_header) {
        this.m_isolate_http_host_header = m_isolate_http_host_header;
    }

    public int getM_mtu() {
        return m_mtu;
    }

    public void setM_mtu(int m_mtu) {
        this.m_mtu = m_mtu;
    }

    private TimerTask m_Task = new TimerTask() {
        @Override
        public void run() {

            //定时更新dns缓存
            try {
                for (int i = 0; i < m_ProxyList.size(); i++) {
                    try {
                        Config config = m_ProxyList.get(0);
                        InetAddress address = InetAddress.getByName(config.ServerAddress.getHostName());
                        if (address != null && !address.equals(config.ServerAddress.getAddress())) {
                            config.ServerAddress = new InetSocketAddress(address, config.ServerAddress.getPort());
                        }
                    } catch (Exception e) {
                    }
                }
            } catch (Exception e) {

            }
        }
    };


    public static boolean isFakeIP(int ip) {
        return (ip & ProxyConfig.FAKE_NETWORK_MASK) == ProxyConfig.FAKE_NETWORK_IP;
    }

    public Config getDefaultProxy() {
        if (!m_ProxyList.isEmpty()) {
            return m_ProxyList.get(0);
        } else {
            return null;
        }
    }

    public Config getDefaultTunnelConfig(InetSocketAddress destAddress) {
        return getDefaultProxy();
    }

    public IPAddress getDefaultLocalIP() {
        if (!m_IpList.isEmpty()) {
            return m_IpList.get(0);
        } else {
            return new IPAddress("10.8.0.2", 32);
        }
    }

    public ArrayList<IPAddress> getDnsList() {
        return m_DnsList;
    }

    public ArrayList<IPAddress> getRouteList() {
        return m_RouteList;
    }

    public int getDnsTTL() {
        if (m_dns_ttl < 30) {
            m_dns_ttl = 30;
        }
        return m_dns_ttl;
    }

    public String getWelcomeInfo() {
        return m_welcome_info;
    }

    public String getSessionName() {
        if (VpnHelper.IS_ENABLE_REMOTE_PROXY) {
            if (m_session_name == null) {
                m_session_name = getDefaultProxy().ServerAddress.getHostName();
            }
            return m_session_name;
        } else {
            return "m_session_name";
        }
    }

    public String getUserAgent() {
        if (m_user_agent == null || m_user_agent.isEmpty()) {
            m_user_agent = System.getProperty("http.agent");
        }
        return m_user_agent;
    }

    public int getMTU() {
        if (m_mtu > 1400 && m_mtu <= 20000) {
            return m_mtu;
        } else {
            return 20000;
        }
    }

    private Boolean getDomainState(String domain) {
        domain = domain.toLowerCase();
        while (!domain.isEmpty()) {
            Boolean stateBoolean = m_DomainMap.get(domain);
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
                return stateBoolean.booleanValue();
            }
        }

        if (isFakeIP(ip))
            return true;

        if (m_outside_china_use_proxy && ip != 0) {
            return !ChinaIpMaskManager.isIPInChina(ip);
        }
        return false;
    }

    public void clear() {
        m_IpList.clear();
        m_DnsList.clear();
        m_RouteList.clear();
        m_ProxyList.clear();
        m_DomainMap.clear();
    }

    public void addIPAddressToList(String[] items, int offset) {
        addIPAddressToList(items, offset, m_IpList);
    }

    public void addDnsToList(String[] items, int offset) {
        addIPAddressToList(items, offset, m_DnsList);
    }

    public void addRouteToList(String[] items, int offset) {
        addIPAddressToList(items, offset, m_RouteList);
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
        m_DomainMap.put(key, value);
    }

    public void addProxyConfig(Config config) {
        if (!m_ProxyList.contains(config)) {
            m_ProxyList.add(config);
            m_DomainMap.put(config.ServerAddress.getHostName(), false);
        }
    }

}
