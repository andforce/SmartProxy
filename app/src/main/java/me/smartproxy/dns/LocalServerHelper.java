package me.smartproxy.dns;

import android.util.Log;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.regex.Matcher;

import me.smartproxy.core.ProxyConfig;
import me.smartproxy.core.UDPNatSessionManager;
import me.smartproxy.tcpip.CommonMethods;
import me.smartproxy.tcpip.IPHeader;
import me.smartproxy.tcpip.UDPHeader;


public class LocalServerHelper {

    private static final String TAG = "LocalServerHelper";

//    public static final String vpnLocalIP = "198.198.198.100";
//    int vpnLocalIPInt = CommonMethods.ipStringToInt(vpnLocalIP);


    private final ProxyConfig config;
    private int vpnLocalIpInt = 0;

    private ByteBuffer m_DNSBuffer;
    private UDPHeader m_UDPHeader;


    private FileOutputStream vpnOutput;
    public LocalServerHelper(ProxyConfig config, FileOutputStream vpnOutput, byte[] buffer) {
        this.vpnOutput = vpnOutput;
        this.config = config;
        vpnLocalIpInt = CommonMethods.ipStringToInt(config.getDefaultLocalIP().getAddress());

        m_UDPHeader = new UDPHeader(buffer, 20);
        m_DNSBuffer = ((ByteBuffer) ByteBuffer.wrap(buffer).position(28)).slice();
    }


//    UDPServer udpServer;
//
//    private FileOutputStream vpnOutput;
//
//    public void createServer(LocalVpnService vpnService, FileOutputStream fos, byte[] buffer) {
//
//        this.vpnOutput = fos;
//
//        udpServer = new UDPServer(vpnService, vpnLocalIP, vpnOutput);
//        udpServer.start();
//    }


    public void onUDP(int udpServerPort, IPHeader ipHeader) {
        onUDPPacketReceived(udpServerPort, ipHeader, m_UDPHeader, m_DNSBuffer);
    }

    private void onUDPPacketReceived(int udpServerPort, IPHeader ipHeader, UDPHeader udpHeader, ByteBuffer dnsBuffer) {


        Log.d(TAG + "_UDP", "从TUN读取到UDP消息:: " + CommonMethods.ipIntToString(ipHeader.getSourceIP()) + ":" + udpHeader.getSourcePortInt() + " -> " + CommonMethods.ipIntToString(ipHeader.getDestinationIP()) + ":" + udpHeader.getDestinationPortInt());

        int dstIP = ipHeader.getDestinationIP();

        //本地报文, 转发给[本地UDP服务器]
//        if (ipHeader.getSourceIP() == intLocalIP  && m_UDPHeader.getSourcePort() != udpServer.port) {
        if (dstIP != UDPServer.udpServerLocalIPInt) {
            try {
                dnsBuffer.clear();
                dnsBuffer.limit(ipHeader.getDataLength() - 8);
                DnsPacket dnsPacket = DnsPacket.Companion.takeFromPoll(dnsBuffer);
                //Short dnsId = dnsPacket.Header.getID();

                if (dnsPacket == null) {
                    Log.e(TAG + "_UDP", "UDP, DNS 解析失败, 丢弃数据包");
                    return;
                }

                boolean isNeedPollution = false;
                Question question = dnsPacket.getQuestions().get(0);
                String ipAddr = ConfigReader.domainIpMap.get(question.getDomain());
                Log.d(TAG + "_UDP", "UDP, DNS 查询的地址是:" + question.getDomain() + " 查询结果：" + ipAddr + ", isNeedPollution:" + isNeedPollution);
                if (ipAddr != null) {
                    isNeedPollution = true;
                } else {
                    Matcher matcher = ConfigReader.patternRootDomain.matcher(question.getDomain());
                    if (matcher.find()) {
                        ipAddr = ConfigReader.rootDomainIpMap.get(matcher.group(1));
                        if (ipAddr != null) {
                            isNeedPollution = true;
                        }
                        Log.d(TAG + "_UDP", "UDP, DNS 查询的地址根目录是: " + matcher.group(1) + ", 查询结果：" + ipAddr + ", isNeedPollution:" + isNeedPollution);
                    }
                }

                short originSourcePort = udpHeader.getSourcePort();
                short dstPort = udpHeader.getDestinationPort();

                if (isNeedPollution) {

                    createDNSResponseToAQuery(udpHeader.getData(), dnsPacket, ipAddr);

                    ipHeader.setTotalLength(20 + 8 + dnsPacket.getSize());
                    udpHeader.setTotalLength(8 + dnsPacket.getSize());

                    ipHeader.setSourceIP(dstIP);
                    udpHeader.setSourcePort(dstPort);
                    ipHeader.setDestinationIP(ipHeader.getSourceIP());
                    udpHeader.setDestinationPort(originSourcePort);

                    CommonMethods.computeUDPChecksum(ipHeader, udpHeader);
                    vpnOutput.write(ipHeader.getData(), ipHeader.getOffset(), ipHeader.getTotalLength());
                    vpnOutput.flush();
                } else {
                    if (UDPNatSessionManager.INSTANCE.getSessionOrNull(originSourcePort) == null) {
                        UDPNatSessionManager.INSTANCE.createSession(originSourcePort, dstIP, dstPort);
                    }

                    //Log.d(TAG, "第一次NAT:" + ipHeader + " udpServer端口:" + udpServer.port + " session: " + originSourcePort + ", 0x" + (originSourcePort & 0xFFFF));
                    Log.d(TAG + "_UDP", "第一次NAT:: udpLocalServer: " + CommonMethods.ipIntToString(UDPServer.udpServerLocalIPInt) + ":" + udpServerPort);

                    Log.d(TAG + "_UDP", String.format(Locale.ENGLISH, "第一次NAT:: sourceIP:sourcePort, %s:%s -> %s:%s",
                            CommonMethods.ipIntToString(ipHeader.getSourceIP()), udpHeader.getSourcePortInt(),
                            CommonMethods.ipIntToString(UDPServer.udpServerLocalIPInt), udpHeader.getSourcePortInt()));

                    Log.d(TAG + "_UDP", String.format(Locale.ENGLISH, "第一次NAT:: dstIP:dstPort, %s:%s -> %s:%s",
                            CommonMethods.ipIntToString(ipHeader.getDestinationIP()), udpHeader.getDestinationPortInt(),
                            config.getDefaultLocalIP().getAddress(), (udpServerPort & 0xFFFF)));

                    Log.d(TAG + "_UDP", String.format(Locale.ENGLISH, "第一次NAT:: 最终：%s:%s -> %s:%s",
                            CommonMethods.ipIntToString(UDPServer.udpServerLocalIPInt), udpHeader.getSourcePortInt(),
                            config.getDefaultLocalIP().getAddress(), (udpServerPort & 0xFFFF)));

                    ipHeader.setSourceIP(UDPServer.udpServerLocalIPInt);    // 7.7.7.7:7777
                    //udpHeader.setSourcePort(originPort);
                    ipHeader.setDestinationIP(vpnLocalIpInt);
                    udpHeader.setDestinationPort((short) udpServerPort);

                    //ipHeader.setProtocol(IPHeader.UDP);
                    CommonMethods.computeUDPChecksum(ipHeader, udpHeader);


                    vpnOutput.write(ipHeader.getData(), ipHeader.getOffset(), ipHeader.getTotalLength());
                    vpnOutput.flush();
                }
            } catch (Exception e) {
                Log.d(TAG + "_UDP", "当前udp包不是DNS报文");
            }
        } else {
            Log.d(TAG + "_UDP", "其它UDP信息,不做处理:" + ipHeader);
            Log.d(TAG + "_UDP", "其它UDP信息,不做处理:" + udpHeader);
            //vpnOutput.write(ipHeader.m_Data, ipHeader.m_Offset, ipHeader.getTotalLength());
        }
    }
    

//    public void stop() {
//
//        Log.d(TAG, "销毁程序调用中...");
//
//        udpServer = null;
//
//        if (vpnOutput != null) {
//            try {
//                vpnOutput.close();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//    }

    private void createDNSResponseToAQuery(byte[] rawData, DnsPacket dnsPacket, String ipAddr) {
        Question question = dnsPacket.getQuestions().get(0);

        dnsPacket.getDnsHeader().setResourceCount((short) 1);
        dnsPacket.getDnsHeader().setAResourceCount((short) 0);
        dnsPacket.getDnsHeader().setEResourceCount((short) 0);

        ResourcePointer rPointer = new ResourcePointer(rawData, question.getOffset() + question.getLength());
        rPointer.setDomain((short) 0xC00C);
        rPointer.setType(question.getType());
        rPointer.setClass(question.getClazz());
        rPointer.setTTL(300);
        rPointer.setDataLength((short) 4);
        rPointer.setIP(CommonMethods.ipStringToInt(ipAddr));

        dnsPacket.setSize(12 + question.getLength() + 16);
    }
}