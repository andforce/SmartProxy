package me.smartproxy.core;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import me.smartproxy.R;
import me.smartproxy.core.ProxyConfig.IPAddress;
import me.smartproxy.dns.DNSUtils;
import me.smartproxy.dns.DnsPacket;
import me.smartproxy.tcpip.CommonMethods;
import me.smartproxy.tcpip.IPHeader;
import me.smartproxy.tcpip.TCPHeader;
import me.smartproxy.tcpip.UDPHeader;
import me.smartproxy.ui.MainActivity;

public class LocalVpnService extends VpnService {

    private static final String TAG = "LocalVpnService";
    public static LocalVpnService Instance;
    public static String ConfigUrl;
    public static boolean IsRunning = false;

    private static int ID;
    private static int LOCAL_IP;
    private static final ConcurrentHashMap<onStatusChangedListener, Object> m_OnStatusChangedListeners = new ConcurrentHashMap<>();

    private Thread m_VPNThread;
    private ParcelFileDescriptor m_VPNInterface;
    private TcpProxyServer m_TcpProxyServer;
    private DnsProxy m_DnsProxy;
    private FileOutputStream m_VPNOutputStream;

    private final byte[] m_Packet;
    private final IPHeader m_IPHeader;
    private final TCPHeader m_TCPHeader;
    private final UDPHeader m_UDPHeader;
    private final ByteBuffer m_DNSBuffer;
    private final Handler m_Handler;
    private long m_SentBytes;
    private long m_ReceivedBytes;

    public static final boolean IS_ENABLE_REMOTE_PROXY = false;

    public LocalVpnService() {
        ID++;
        m_Handler = new Handler();
        m_Packet = new byte[20000];
        m_IPHeader = new IPHeader(m_Packet, 0);
        m_TCPHeader = new TCPHeader(m_Packet, 20);
        m_UDPHeader = new UDPHeader(m_Packet, 20);
        m_DNSBuffer = ((ByteBuffer) ByteBuffer.wrap(m_Packet).position(28)).slice();
        Instance = this;

        Log.d(TAG, "New VPNService(" + ID + ")");
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "VPNService created.");
        // Start a new session by creating a new thread.
        m_VPNThread = new Thread(new Runnable() {
            @Override
            public synchronized void run() {
                try {
                    Log.d(TAG, "VPNService work thread is runing...");

                    ProxyConfig.AppInstallID = getAppInstallID();//获取安装ID
                    ProxyConfig.AppVersion = getVersionName();//获取版本号
                    Log.d(TAG, "AppInstallID: " + ProxyConfig.AppInstallID);
                    writeLog("Android version: %s", Build.VERSION.RELEASE);
                    writeLog("App version: %s", ProxyConfig.AppVersion);


                    ChinaIpMaskManager.loadFromFile(getResources().openRawResource(R.raw.ipmask));//加载中国的IP段，用于IP分流。
                    waitUntilPreapred();//检查是否准备完毕。

                    m_TcpProxyServer = new TcpProxyServer(0);
                    m_TcpProxyServer.start();
                    writeLog("LocalTcpServer started.");

                    m_DnsProxy = new DnsProxy();
                    m_DnsProxy.start();
                    writeLog("LocalDnsProxy started.");

                    while (true) {
                        if (IsRunning) {
                            //加载配置文件
                            writeLog("Load config from %s ...", ConfigUrl);
                            if (IS_ENABLE_REMOTE_PROXY) {
                                try {
                                    ProxyConfig.Instance.loadFromUrl(ConfigUrl);
                                    if (ProxyConfig.Instance.getDefaultProxy() == null) {
                                        throw new Exception("Invalid config file.");
                                    }
                                    writeLog("PROXY %s", ProxyConfig.Instance.getDefaultProxy());
                                } catch (Exception e) {
                                    String errString = e.getMessage();
                                    if (errString == null || errString.isEmpty()) {
                                        errString = e.toString();
                                    }

                                    IsRunning = false;
                                    onStatusChanged(errString, false);
                                    continue;
                                }


                                writeLog("Load config success.");
                                String welcomeInfoString = ProxyConfig.Instance.getWelcomeInfo();
                                if (welcomeInfoString != null && !welcomeInfoString.isEmpty()) {
                                    writeLog("%s", ProxyConfig.Instance.getWelcomeInfo());
                                }
                            }

                            runVPN();
                        } else {
                            Thread.sleep(100);
                        }
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "Fatal error: ", e);
                } catch (Exception e) {
                    writeLog("Fatal error: %s", e.toString());
                    Log.e(TAG, "Fatal error: ", e);
                } finally {
                    writeLog("SmartProxy terminated.");
                    dispose();
                }
            }
        }, "VPNServiceThread");
        m_VPNThread.start();
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        IsRunning = true;
        return super.onStartCommand(intent, flags, startId);
    }

    public interface onStatusChangedListener {
        void onStatusChanged(String status, Boolean isRunning);

        void onLogReceived(String logString);
    }

    public static void addOnStatusChangedListener(onStatusChangedListener listener) {
        if (!m_OnStatusChangedListeners.containsKey(listener)) {
            m_OnStatusChangedListeners.put(listener, 1);
        }
    }

    public static void removeOnStatusChangedListener(onStatusChangedListener listener) {
        m_OnStatusChangedListeners.remove(listener);
    }

    private void onStatusChanged(final String status, final boolean isRunning) {
        m_Handler.post(() -> {
            for (Map.Entry<onStatusChangedListener, Object> entry : m_OnStatusChangedListeners.entrySet()) {
                entry.getKey().onStatusChanged(status, isRunning);
            }
        });
    }

    public void writeLog(final String format, Object... args) {
        final String logString = String.format(format, args);
        m_Handler.post(() -> {
            for (Map.Entry<onStatusChangedListener, Object> entry : m_OnStatusChangedListeners.entrySet()) {
                entry.getKey().onLogReceived(logString);
            }
        });
    }

    public void sendUDPPacket(IPHeader ipHeader, UDPHeader udpHeader) {
        try {
            CommonMethods.ComputeUDPChecksum(ipHeader, udpHeader);
            this.m_VPNOutputStream.write(ipHeader.m_Data, ipHeader.m_Offset, ipHeader.getTotalLength());
        } catch (IOException e) {
            Log.e(TAG, "sendUDPPacket: ", e);
        }
    }

    String getAppInstallID() {
        SharedPreferences preferences = getSharedPreferences("SmartProxy", MODE_PRIVATE);
        String appInstallID = preferences.getString("AppInstallID", null);
        if (appInstallID == null || appInstallID.isEmpty()) {
            appInstallID = UUID.randomUUID().toString();
            Editor editor = preferences.edit();
            editor.putString("AppInstallID", appInstallID);
            editor.apply();
        }
        return appInstallID;
    }

    String getVersionName() {
        try {
            PackageManager packageManager = getPackageManager();
            // getPackageName()是你当前类的包名，0代表是获取版本信息
            PackageInfo packInfo = packageManager.getPackageInfo(getPackageName(), 0);
            return packInfo.versionName;
        } catch (Exception e) {
            return "0.0";
        }
    }

    private void runVPN() throws Exception {
        this.m_VPNInterface = establishVPN();
        this.m_VPNOutputStream = new FileOutputStream(m_VPNInterface.getFileDescriptor());
        FileInputStream in = new FileInputStream(m_VPNInterface.getFileDescriptor());
        int size = 0;
        while (size != -1 && IsRunning) {
            while ((size = in.read(m_Packet)) > 0 && IsRunning) {
                if (m_DnsProxy.Stopped || m_TcpProxyServer.Stopped) {
                    in.close();
                    throw new Exception("LocalServer stopped.");
                }
                onIPPacketReceived(m_IPHeader, size);
            }
            Thread.sleep(100);
        }
        in.close();
        disconnectVPN();
    }

    void onIPPacketReceived(IPHeader ipHeader, int size) throws IOException {
        switch (ipHeader.getProtocol()) {
            case IPHeader.TCP:
                TCPHeader tcpHeader = m_TCPHeader;
                tcpHeader.m_Offset = ipHeader.getHeaderLength();
                if (ipHeader.getSourceIP() == LOCAL_IP) {
                    // 收到本地 TcpProxyServer 服务器数据
                    if (tcpHeader.getSourcePort() == m_TcpProxyServer.Port) {
                        NatSession session = NatSessionManager.getSession(tcpHeader.getDestinationPort());
                        if (session != null) {
                            Log.d("LocalVpnService", "onIPPacketReceived: 收到本地 TcpProxyServer 服务器数据, " + ipHeader + " " + tcpHeader);
                            ipHeader.setSourceIP(ipHeader.getDestinationIP());
                            tcpHeader.setSourcePort(session.RemotePort);
                            ipHeader.setDestinationIP(LOCAL_IP);

                            CommonMethods.ComputeTCPChecksum(ipHeader, tcpHeader);
                            m_VPNOutputStream.write(ipHeader.m_Data, ipHeader.m_Offset, size);
                            m_ReceivedBytes += size;
                        } else {
                            Log.d(TAG, "onIPPacketReceived: NoSession, " + ipHeader + " " + tcpHeader);
                        }
                    } else {

                        // 添加端口映射
                        int portKey = tcpHeader.getSourcePort();
                        NatSession session = NatSessionManager.getSession(portKey);
                        if (session == null || session.RemoteIP != ipHeader.getDestinationIP() || session.RemotePort != tcpHeader.getDestinationPort()) {
                            session = NatSessionManager.createSession(portKey, ipHeader.getDestinationIP(), tcpHeader.getDestinationPort());
                        }

                        session.LastNanoTime = System.nanoTime();
                        session.PacketSent++;//注意顺序

                        int tcpDataSize = ipHeader.getDataLength() - tcpHeader.getHeaderLength();
                        if (session.PacketSent == 2 && tcpDataSize == 0) {
                            return;//丢弃tcp握手的第二个ACK报文。因为客户端发数据的时候也会带上ACK，这样可以在服务器Accept之前分析出HOST信息。
                        }

                        //分析数据，找到host
                        if (session.BytesSent == 0 && tcpDataSize > 10) {
                            int dataOffset = tcpHeader.m_Offset + tcpHeader.getHeaderLength();
                            String host = HttpHostHeaderParser.parseHost(tcpHeader.m_Data, dataOffset, tcpDataSize);
                            if (host != null) {
                                session.RemoteHost = host;
                            }
                        }

                        // 转发给本地 TcpProxyServer 服务器
                        Log.d("LocalVpnService", "onIPPacketReceived: 转发给本地 TcpProxyServer 服务器, " + ipHeader + " " + tcpHeader);
                        ipHeader.setSourceIP(ipHeader.getDestinationIP());
                        ipHeader.setDestinationIP(LOCAL_IP);
                        tcpHeader.setDestinationPort(m_TcpProxyServer.Port);

                        CommonMethods.ComputeTCPChecksum(ipHeader, tcpHeader);
                        m_VPNOutputStream.write(ipHeader.m_Data, ipHeader.m_Offset, size);
                        session.BytesSent += tcpDataSize;//注意顺序
                        m_SentBytes += size;
                    }
                }
                break;
            case IPHeader.UDP:
                // 转发DNS数据包：
                UDPHeader udpHeader = m_UDPHeader;
                udpHeader.m_Offset = ipHeader.getHeaderLength();
                if (ipHeader.getSourceIP() == LOCAL_IP && udpHeader.getDestinationPort() == 53) {
                    m_DNSBuffer.clear();
                    m_DNSBuffer.limit(ipHeader.getDataLength() - 8);
                    DnsPacket dnsPacket = DnsPacket.FromBytes(m_DNSBuffer);
                    if (dnsPacket != null && dnsPacket.Header.QuestionCount > 0) {
                        m_DnsProxy.onDnsRequestReceived(ipHeader, udpHeader, dnsPacket);
                    }
                }
                break;
        }
    }

    private void waitUntilPreapred() {
        while (prepare(this) != null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Log.e(TAG, "waitUntilPreapred: ", e);
            }
        }
    }

    private ParcelFileDescriptor establishVPN() throws Exception {
        Builder builder = new Builder();
        builder.setMtu(ProxyConfig.Instance.getMTU());
        if (ProxyConfig.IS_DEBUG) {
            Log.d(TAG, "setMtu: " + ProxyConfig.Instance.getMTU());
        }

        IPAddress ipAddress = ProxyConfig.Instance.getDefaultLocalIP();
        LOCAL_IP = CommonMethods.ipStringToInt(ipAddress.Address);
        builder.addAddress(ipAddress.Address, ipAddress.PrefixLength);
        if (ProxyConfig.IS_DEBUG) {
            Log.d(TAG, "addAddress: " + ipAddress.Address + "/" + ipAddress.PrefixLength);
        }

        for (ProxyConfig.IPAddress dns : ProxyConfig.Instance.getDnsList()) {
            builder.addDnsServer(dns.Address);
            if (ProxyConfig.IS_DEBUG) {
                Log.d(TAG, "addDnsServer: " + dns.Address);
            }
        }

        if (!ProxyConfig.Instance.getRouteList().isEmpty()) {
            for (ProxyConfig.IPAddress routeAddress : ProxyConfig.Instance.getRouteList()) {
                builder.addRoute(routeAddress.Address, routeAddress.PrefixLength);
                if (ProxyConfig.IS_DEBUG) {
                    Log.d(TAG, "addRoute: " + routeAddress.Address + "/" + routeAddress.PrefixLength);
                }
            }
            builder.addRoute(CommonMethods.ipIntToString(ProxyConfig.FAKE_NETWORK_IP), 16);

            if (ProxyConfig.IS_DEBUG) {
                Log.d(TAG, "addRoute: " + CommonMethods.ipIntToString(ProxyConfig.FAKE_NETWORK_IP) + "/16");
            }
        } else {
            builder.addRoute("0.0.0.0", 0);
            if (ProxyConfig.IS_DEBUG) {
                Log.d(TAG, "addDefaultRoute:0.0.0.0/0");
            }
        }

        Map<String, String> dnsMap = DNSUtils.INSTANCE.findAndroidPropDNS(this);
        for (Map.Entry<String, String> entry : dnsMap.entrySet()) {
            String host = entry.getValue();
            String name = entry.getKey();
            Log.d(TAG, "findAndroidPropDNS: " + name + " -> " + host);

            if (DNSUtils.INSTANCE.isIPv4Address(host)) {
                builder.addRoute(host, 32);
                if (ProxyConfig.IS_DEBUG) {
                    Log.d(TAG, "addRoute by DNS: " + host + "/32");
                }
            } else {
                Log.d(TAG, "addRoute by DNS, 暂时忽略 IPv6 类型的DNS: " + host);
            }
        }

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        builder.setConfigureIntent(pendingIntent);

        builder.setSession(ProxyConfig.Instance.getSessionName());
        ParcelFileDescriptor pfdDescriptor = builder.establish();
        onStatusChanged(ProxyConfig.Instance.getSessionName() + getString(R.string.vpn_connected_status), true);
        return pfdDescriptor;
    }

    public void disconnectVPN() {
        try {
            if (m_VPNInterface != null) {
                m_VPNInterface.close();
                m_VPNInterface = null;
            }
        } catch (Exception e) {
            // ignore
        }
        onStatusChanged(ProxyConfig.Instance.getSessionName() + getString(R.string.vpn_disconnected_status), false);
        this.m_VPNOutputStream = null;
    }

    private synchronized void dispose() {
        // 断开VPN
        disconnectVPN();

        // 停止TcpServer
        if (m_TcpProxyServer != null) {
            m_TcpProxyServer.stop();
            m_TcpProxyServer = null;
            writeLog("LocalTcpServer stopped.");
        }

        // 停止DNS解析器
        if (m_DnsProxy != null) {
            m_DnsProxy.stop();
            m_DnsProxy = null;
            writeLog("LocalDnsProxy stopped.");
        }

        stopSelf();
        IsRunning = false;
        System.exit(0);
    }

    @Override
    public void onDestroy() {
        Log.e(TAG, "VPNService destoried.");
        if (m_VPNThread != null) {
            m_VPNThread.interrupt();
        }
    }

}
