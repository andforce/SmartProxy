package me.smartproxy.core;

import android.util.Log;
import android.util.SparseArray;

import org.koin.java.KoinJavaComponent;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

import me.smartproxy.core.viewmodel.LocalVpnViewModel;
import me.smartproxy.dns.DnsPacket;
import me.smartproxy.dns.Question;
import me.smartproxy.dns.Resource;
import me.smartproxy.dns.ResourcePointer;
import me.smartproxy.tcpip.CommonMethods;
import me.smartproxy.tcpip.IPHeader;
import me.smartproxy.tcpip.UDPHeader;


public class DnsProxy {
    private static final String TAG = "DnsProxy";
    private final LocalVpnViewModel localVpnViewModel = KoinJavaComponent.get(LocalVpnViewModel.class);

    private ProxyConfig m_Config;
    public DnsProxy(ProxyConfig config) throws IOException {
        m_Config = config;
        m_QueryArray = new SparseArray<QueryState>();
        m_Client = new DatagramSocket(0);
    }

    private static class QueryState {
        public short ClientQueryID;
        public long QueryNanoTime;
        public int ClientIP;
        public short ClientPort;
        public int RemoteIP;
        public short RemotePort;
    }

    public boolean Stopped;
    private static final ConcurrentHashMap<Integer, String> IPDomainMaps = new ConcurrentHashMap<Integer, String>();
    private static final ConcurrentHashMap<String, Integer> DomainIPMaps = new ConcurrentHashMap<String, Integer>();
    private final long QUERY_TIMEOUT_NS = 10 * 1000000000L;
    private DatagramSocket m_Client;
    private short m_QueryID;
    private SparseArray<QueryState> m_QueryArray;

    public static String reverseLookup(int ip) {
        return IPDomainMaps.get(ip);
    }


    public void stop() {
        Stopped = true;
        if (m_Client != null) {
            m_Client.close();
            m_Client = null;
        }
    }

    public void start() {
        try {
            byte[] receiveBuffer = new byte[2000];
            IPHeader ipHeader = new IPHeader(receiveBuffer, 0);
            ipHeader.Default();
            UDPHeader udpHeader = new UDPHeader(receiveBuffer, 20);

            ByteBuffer dnsBuffer = ByteBuffer.wrap(receiveBuffer);
            dnsBuffer.position(28);
            dnsBuffer = dnsBuffer.slice();

            DatagramPacket packet = new DatagramPacket(receiveBuffer, 28, receiveBuffer.length - 28);

            while (m_Client != null && !m_Client.isClosed()) {

                packet.setLength(receiveBuffer.length - 28);
                m_Client.receive(packet);

                dnsBuffer.clear();
                dnsBuffer.limit(packet.getLength());
                try {
                    DnsPacket dnsPacket = DnsPacket.FromBytes(dnsBuffer);
                    if (dnsPacket != null) {
                        onDnsResponseReceived(ipHeader, udpHeader, dnsPacket);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "DnsResolver Error: ", e);
        } finally {
            Log.e(TAG, "DnsResolver Thread Exited.");
            this.stop();
        }
    }

    private int getFirstIP(DnsPacket dnsPacket) {
        for (int i = 0; i < dnsPacket.Header.ResourceCount; i++) {
            Resource resource = dnsPacket.Resources[i];
            if (resource.Type == 1) {
                int ip = CommonMethods.readInt(resource.Data, 0);
                return ip;
            }
        }
        return 0;
    }

    private void tamperDnsResponse(byte[] rawPacket, DnsPacket dnsPacket, int newIP) {
        Question question = dnsPacket.Questions[0];

        dnsPacket.Header.setResourceCount((short) 1);
        dnsPacket.Header.setAResourceCount((short) 0);
        dnsPacket.Header.setEResourceCount((short) 0);

        ResourcePointer rPointer = new ResourcePointer(rawPacket, question.Offset() + question.Length());
        rPointer.setDomain((short) 0xC00C);
        rPointer.setType(question.Type);
        rPointer.setClass(question.Class);
        rPointer.setTTL(m_Config.getDnsTTL());
        rPointer.setDataLength((short) 4);
        rPointer.setIP(newIP);

        dnsPacket.Size = 12 + question.Length() + 16;
    }

    private int getOrCreateFakeIP(String domainString) {
        Integer fakeIP = DomainIPMaps.get(domainString);
        if (fakeIP == null) {
            int hashIP = domainString.hashCode();
            do {
                fakeIP = ProxyConfig.FAKE_NETWORK_IP | (hashIP & 0x0000FFFF);
                hashIP++;
            } while (IPDomainMaps.containsKey(fakeIP));

            DomainIPMaps.put(domainString, fakeIP);
            IPDomainMaps.put(fakeIP, domainString);
        }
        return fakeIP;
    }

    private boolean dnsPollution(byte[] rawPacket, DnsPacket dnsPacket) {
        if (dnsPacket.Header.QuestionCount > 0) {
            Question question = dnsPacket.Questions[0];
            if (question.Type == 1) {
                int realIP = getFirstIP(dnsPacket);
                if (m_Config.needProxy(question.Domain, realIP)) {
                    int fakeIP = getOrCreateFakeIP(question.Domain);
                    tamperDnsResponse(rawPacket, dnsPacket, fakeIP);
                    Log.d(TAG, "FakeDns: " + question.Domain + "=>" + CommonMethods.ipIntToString(realIP) + "(" + CommonMethods.ipIntToString(fakeIP) + ")");
                    return true;
                }
            }
        }
        return false;
    }

    private void onDnsResponseReceived(IPHeader ipHeader, UDPHeader udpHeader, DnsPacket dnsPacket) {
        QueryState state = null;
        synchronized (m_QueryArray) {
            state = m_QueryArray.get(dnsPacket.Header.ID);
            if (state != null) {
                m_QueryArray.remove(dnsPacket.Header.ID);
            }
        }

        if (state != null) {
            //DNS污染，默认污染海外网站
            dnsPollution(udpHeader.m_Data, dnsPacket);

            dnsPacket.Header.setID(state.ClientQueryID);
            ipHeader.setSourceIP(state.RemoteIP);
            ipHeader.setDestinationIP(state.ClientIP);
            ipHeader.setProtocol(IPHeader.UDP);
            ipHeader.setTotalLength(20 + 8 + dnsPacket.Size);
            udpHeader.setSourcePort(state.RemotePort);
            udpHeader.setDestinationPort(state.ClientPort);
            udpHeader.setTotalLength(8 + dnsPacket.Size);

            localVpnViewModel.sendUDPPacket(ipHeader, udpHeader);
        }
    }

    private int getIPFromCache(String domain) {
        Integer ip = DomainIPMaps.get(domain);
        if (ip == null) {
            return 0;
        } else {
            return ip;
        }
    }

    private boolean interceptDns(IPHeader ipHeader, UDPHeader udpHeader, DnsPacket dnsPacket) {
        Question question = dnsPacket.Questions[0];
        Log.d(TAG, "DNS Qeury " + question.Domain);
        if (question.Type == 1) {
            if (m_Config.needProxy(question.Domain, getIPFromCache(question.Domain))) {
                int fakeIP = getOrCreateFakeIP(question.Domain);
                tamperDnsResponse(ipHeader.m_Data, dnsPacket, fakeIP);

                Log.d(TAG, "interceptDns FakeDns: " + question.Domain + "=>" + CommonMethods.ipIntToString(fakeIP));

                int sourceIP = ipHeader.getSourceIP();
                short sourcePort = udpHeader.getSourcePort();
                ipHeader.setSourceIP(ipHeader.getDestinationIP());
                ipHeader.setDestinationIP(sourceIP);
                ipHeader.setTotalLength(20 + 8 + dnsPacket.Size);
                udpHeader.setSourcePort(udpHeader.getDestinationPort());
                udpHeader.setDestinationPort(sourcePort);
                udpHeader.setTotalLength(8 + dnsPacket.Size);
                localVpnViewModel.sendUDPPacket(ipHeader, udpHeader);
                return true;
            }
        }
        return false;
    }

    private void clearExpiredQueries() {
        long now = System.nanoTime();
        for (int i = m_QueryArray.size() - 1; i >= 0; i--) {
            QueryState state = m_QueryArray.valueAt(i);
            if ((now - state.QueryNanoTime) > QUERY_TIMEOUT_NS) {
                m_QueryArray.removeAt(i);
            }
        }
    }

    public void onDnsRequestReceived(IPHeader ipHeader, UDPHeader udpHeader, DnsPacket dnsPacket) {
        if (!interceptDns(ipHeader, udpHeader, dnsPacket)) {
            //转发DNS
            QueryState state = new QueryState();
            state.ClientQueryID = dnsPacket.Header.ID;
            state.QueryNanoTime = System.nanoTime();
            state.ClientIP = ipHeader.getSourceIP();
            state.ClientPort = udpHeader.getSourcePort();
            state.RemoteIP = ipHeader.getDestinationIP();
            state.RemotePort = udpHeader.getDestinationPort();

            // 转换QueryID
            m_QueryID++;// 增加ID
            dnsPacket.Header.setID(m_QueryID);

            synchronized (m_QueryArray) {
                clearExpiredQueries();//清空过期的查询，减少内存开销。
                m_QueryArray.put(m_QueryID, state);// 关联数据
            }

            InetSocketAddress remoteAddress = new InetSocketAddress(CommonMethods.ipIntToInet4Address(state.RemoteIP), state.RemotePort);
            DatagramPacket packet = new DatagramPacket(udpHeader.m_Data, udpHeader.m_Offset + 8, dnsPacket.Size);
            packet.setSocketAddress(remoteAddress);

            try {
                if (localVpnViewModel.protect(m_Client)) {
                    m_Client.send(packet);
                } else {
                    Log.e(TAG, "VPN protect udp socket failed.");
                }
            } catch (IOException e) {
                Log.e(TAG, "onDnsRequestReceived Error: ", e);
            }
        }
    }
}
