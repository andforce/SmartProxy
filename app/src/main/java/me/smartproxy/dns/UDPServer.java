package me.smartproxy.dns;

import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Locale;

import me.smartproxy.core.LocalVpnService;
import me.smartproxy.core.NatSession;
import me.smartproxy.core.UDPNatSessionManager;
import me.smartproxy.tcpip.CommonMethods;
import me.smartproxy.tcpip.IPData;
import me.smartproxy.tcpip.IPHeader;
import me.smartproxy.tcpip.UDPHeader;


public class UDPServer implements Runnable {
    private static final String TAG = "UDPServer";
    public static final String udpServerLocalIP = "10.8.0.100";
    public static final int udpServerLocalIPInt = CommonMethods.ipStringToInt(udpServerLocalIP);
    public int port;
    public String vpnLocalIP;

    final int MAX_LENGTH = 1024 * 20;
    byte[] receMsgs = new byte[MAX_LENGTH];

    DatagramSocket udpDatagramSocket;
    DatagramPacket datagramPacket;
    DatagramPacket sendPacket;

    Thread udpThread;

    private FileOutputStream vpnOutput;

    public void start() {
        udpThread = new Thread(this);
        udpThread.setName("UDPServer - Thread");
        udpThread.start();
    }

    public void stop() {
        udpDatagramSocket.close();
        udpThread.interrupt();
    }

    public UDPServer(LocalVpnService vpnService, String vpnLocalIP, FileOutputStream vpnOutput) {
        this.vpnOutput = vpnOutput;
        this.vpnLocalIP = vpnLocalIP;
        try {
            udpDatagramSocket = new DatagramSocket();// 填写参数，可以固定端口，如果去掉之后，会随机分配端口
            vpnService.protect(udpDatagramSocket);
            port = udpDatagramSocket.getLocalPort();

            datagramPacket = new DatagramPacket(receMsgs, 28, receMsgs.length - 28);

            SocketAddress socketAddress = udpDatagramSocket.getLocalSocketAddress();
            Log.d(TAG, "UDP服务器启动, 地址为: ==============>\t" + socketAddress);
        } catch (SocketException e) {
            Log.e(TAG, "创建udpDatagramSocket失败", e);
        }
    }


    private void service() {
        Log.d(TAG, "UDP服务器启动, 端口为: " + port);
        try {
            while (udpThread != null && !udpThread.isInterrupted()) {
                Log.d(TAG, "阻塞等待，UDP消息");
                udpDatagramSocket.receive(datagramPacket);

                InetSocketAddress socketAddress = (InetSocketAddress) datagramPacket.getSocketAddress();
                int socketPort = datagramPacket.getPort();

                String hostAddress = socketAddress.getAddress().getHostAddress();
                if (hostAddress == null || hostAddress.isEmpty()) {
                    Log.e(TAG, "hostAddress为空");
                    continue;
                }

                Log.d(TAG, "收到udp消息: " + socketAddress);
                if (udpServerLocalIP.equals(hostAddress)) {
                    Log.d(TAG, "UDPServer收到本地消息" + socketAddress);
                    NatSession session = UDPNatSessionManager.INSTANCE.getSessionOrNull((short) socketPort);
                    if (session == null) {
                        Log.d(TAG, "NatSessionManager中未找到session" + socketPort);
                        continue;
                    }
                    Log.d(TAG, "NatSessionManager中找到session" + socketPort);
                    sendPacket = new DatagramPacket(receMsgs, 28, datagramPacket.getLength(), CommonMethods.ipIntToInet4Address(session.getRemoteIP()), session.getRemotePort());
                    Log.d(TAG, "构建数据包发送到远端目标服务器：remote:" + CommonMethods.ipIntToInet4Address(session.getRemoteIP()) + ":" + (session.getRemotePort() & 0xFFFF));
                    udpDatagramSocket.send(sendPacket);
                } else {
                    Log.d(TAG, "UDPServer收到外部消息: " + socketAddress);
                    //如果消息来自外部, 转进来
                    NatSession session = new NatSession();
                    session.setRemoteIP(CommonMethods.ipStringToInt(hostAddress));
                    session.setRemotePort((short) socketPort);
                    short port = UDPNatSessionManager.INSTANCE.getPort(session);
                    if (port == -1) {
                        Log.d(TAG, "收到外部UDP消息, 未在Session中找到");
                        continue;
                    }
                    Log.d(TAG, "收到外部UDP消息, 在Session中找到, port: " + (port & 0xFFFF));

                    IPHeader ipHeader = new IPHeader(receMsgs, 0);
                    UDPHeader udpHeader = new UDPHeader(receMsgs, 20);

                    Log.d(TAG, String.format(Locale.ENGLISH, "第二次NAT:: sourceIP:sourcePort, %s:%s -> %s:%s",
                            CommonMethods.ipIntToString(ipHeader.getSourceIP()), udpHeader.getSourcePortInt(),
                            CommonMethods.ipIntToString(UDPServer.udpServerLocalIPInt), udpHeader.getSourcePortInt()));

                    Log.d(TAG, String.format(Locale.ENGLISH, "第二次NAT:: dstIP:dstPort, %s:%s -> %s:%s",
                            CommonMethods.ipIntToString(ipHeader.getDestinationIP()), udpHeader.getDestinationPortInt(),
                            CommonMethods.ipIntToString(CommonMethods.ipStringToInt(vpnLocalIP)), (port & 0xFFFF)));

                    Log.d(TAG, String.format(Locale.ENGLISH, "第二次NAT:: 最终：%s:%s -> %s:%s",
                            CommonMethods.ipIntToString(UDPServer.udpServerLocalIPInt), udpHeader.getSourcePortInt(),
                            CommonMethods.ipIntToString(CommonMethods.ipStringToInt(vpnLocalIP)), (port & 0xFFFF)));

                    ipHeader.setSourceIP(session.getRemoteIP());
                    ipHeader.setDestinationIP(CommonMethods.ipStringToInt(vpnLocalIP));

                    ipHeader.setTos((byte) 0);
                    ipHeader.setIdentification(0);
                    ipHeader.setFlagsAndOffset((short) 0);
                    ipHeader.setTotalLength(20 + 8 + datagramPacket.getLength());
                    ipHeader.setHeaderLength(20);
                    ipHeader.setProtocol(IPData.UDP);
                    ipHeader.setTTL((byte) 30);

                    udpHeader.setDestinationPort((short) port);
                    udpHeader.setSourcePort(session.getRemotePort());
                    udpHeader.setTotalLength(8 + datagramPacket.getLength());

                    Log.d(TAG, "UDP, 把数据写回VPNService，此时发出UDP的App应该会收到消息");

                    //vpnService.sendUDPPacket(ipHeader, udpHeader);
                    sendUDPPacket(ipHeader, udpHeader);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "UDP服务异常", e);
        } finally {
            // 关闭socket
            Log.d(TAG, "udpServer已关闭");
            if (udpDatagramSocket != null) {
                udpDatagramSocket.close();
            }
        }
    }

    private void sendUDPPacket(IPHeader ipHeader, UDPHeader udpHeader) {
        try {
            CommonMethods.computeUDPChecksum(ipHeader, udpHeader);
            this.vpnOutput.write(ipHeader.getData(), ipHeader.getOffset(), ipHeader.getTotalLength());
            this.vpnOutput.flush();
        } catch (IOException e) {
            Log.e(TAG, "发送UDP数据包失败:" + e);
        }
    }

    @Override
    public void run() {
        service();
    }
}
