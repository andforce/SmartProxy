package me.smartproxy.core;

import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import me.smartproxy.tcpip.CommonMethods;
import me.smartproxy.tunnel.Tunnel;

public class TcpProxyServer implements Runnable {

    private static final String TAG = "TcpProxyServer";

    public boolean Stopped;
    public short Port;

    Selector m_Selector;
    ServerSocketChannel m_ServerSocketChannel;
    Thread m_ServerThread;

    ProxyConfig m_Config;

    public TcpProxyServer(ProxyConfig config, int port) throws IOException {
        this.m_Config = config;
        m_Selector = Selector.open();
        m_ServerSocketChannel = ServerSocketChannel.open();
        m_ServerSocketChannel.configureBlocking(false);
        m_ServerSocketChannel.socket().bind(new InetSocketAddress(port));
        m_ServerSocketChannel.register(m_Selector, SelectionKey.OP_ACCEPT);
        this.Port = (short) m_ServerSocketChannel.socket().getLocalPort();
        Log.d(TAG, "AsyncTcpServer listen on " + (this.Port & 0xFFFF) + " success.");

    }

    public void start() {
        m_ServerThread = new Thread(this);
        m_ServerThread.setName("TcpProxyServerThread");
        m_ServerThread.start();
    }

    public void stop() {
        this.Stopped = true;
        if (m_Selector != null) {
            try {
                m_Selector.close();
                m_Selector = null;
            } catch (Exception e) {
                Log.e(TAG, "stop, m_Selector: ", e);
            }
        }

        if (m_ServerSocketChannel != null) {
            try {
                m_ServerSocketChannel.close();
                m_ServerSocketChannel = null;
            } catch (Exception e) {
                Log.e(TAG, "stop, m_ServerSocketChannel: ", e);
            }
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                m_Selector.select();
                Iterator<SelectionKey> keyIterator = m_Selector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    if (key.isValid()) {
                        try {
                            if (key.isReadable()) {
                                ((Tunnel) key.attachment()).onReadable(key);
                            } else if (key.isWritable()) {
                                ((Tunnel) key.attachment()).onWritable(key);
                            } else if (key.isConnectable()) {
                                ((Tunnel) key.attachment()).onConnectable();
                            } else if (key.isAcceptable()) {
                                onAccepted(key);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "inner run: ", e);
                        }
                    }
                    keyIterator.remove();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "outer run: ", e);
        } finally {
            this.stop();
            Log.d(TAG, "TcpServer thread exited.");
        }
    }

    InetSocketAddress getDestAddress(SocketChannel localChannel) {
        short portKey = (short) localChannel.socket().getPort();
        NatSession session = NatSessionManager.getSession(portKey);
        if (session != null) {
            if (m_Config.needProxy(session.RemoteHost, session.RemoteIP)) {
                Log.d(TAG, String.format("%d/%d:[PROXY] %s=>%s:%d", NatSessionManager.getSessionCount(), Tunnel.SessionCount, session.RemoteHost, CommonMethods.ipIntToString(session.RemoteIP), session.RemotePort & 0xFFFF));
                return InetSocketAddress.createUnresolved(session.RemoteHost, session.RemotePort & 0xFFFF);
            } else {
                return new InetSocketAddress(localChannel.socket().getInetAddress(), session.RemotePort & 0xFFFF);
            }
        }
        return null;
    }

    void onAccepted(SelectionKey key) {
        Tunnel localTunnel = null;
        try {
            SocketChannel localChannel = m_ServerSocketChannel.accept();
            localTunnel = TunnelFactory.wrap(localChannel, m_Selector);

            InetSocketAddress destAddress = getDestAddress(localChannel);
            if (destAddress != null) {
                Tunnel remoteTunnel = TunnelFactory.createTunnelByConfig(m_Config, destAddress, m_Selector);
                remoteTunnel.setBrotherTunnel(localTunnel);//关联兄弟
                localTunnel.setBrotherTunnel(remoteTunnel);//关联兄弟
                remoteTunnel.connect(destAddress);//开始连接
            } else {
                localTunnel.dispose();
            }
        } catch (Exception e) {
            Log.e(TAG, "onAccepted: ", e);
            if (localTunnel != null) {
                localTunnel.dispose();
            }
        }
    }

}
