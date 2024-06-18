package me.smartproxy.tunnel.httpconnect;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;

import me.smartproxy.core.ProxyConfig;
import me.smartproxy.tunnel.Tunnel;
import me.smartproxy.ui.utils.Logger;

public class HttpConnectTunnel extends Tunnel {

    private boolean tunnelEstablished;
    private HttpConnectConfig config;
    private final ProxyConfig proxyConfig;

    public HttpConnectTunnel(ProxyConfig c, HttpConnectConfig config, Selector selector) throws IOException {
        super(config.getSocketAddress(), selector);
        this.proxyConfig = c;
        this.config = config;
    }

    @Override
    protected void onConnected(ByteBuffer buffer) throws Exception {

        String request = "CONNECT " + destAddress.getHostName() + ":" + destAddress.getPort() + " HTTP/1.0\r\nProxy-Connection: keep-alive\r\nUser-Agent: " + proxyConfig.getUserAgent() + "\r\n\r\n";

        buffer.clear();
        buffer.put(request.getBytes());
        buffer.flip();
        if (this.write(buffer, true)) {//发送连接请求到代理服务器
            this.beginReceive();//开始接收代理服务器响应数据
        }
    }

    void trySendPartOfHeader(ByteBuffer buffer) throws Exception {
        int bytesSent = 0;
        if (buffer.remaining() > 10) {
            int pos = buffer.position() + buffer.arrayOffset();
            String firString = new String(buffer.array(), pos, 10).toUpperCase();
            if (firString.startsWith("GET /") || firString.startsWith("POST /")) {
                int limit = buffer.limit();
                buffer.limit(buffer.position() + 10);
                super.write(buffer, false);
                bytesSent = 10 - buffer.remaining();
                buffer.limit(limit);
                Logger.d("HttpConnectTunnel", "Send " + bytesSent + " bytes(" + firString + ") to " + destAddress);
            }
        }
    }


    @Override
    protected void beforeSend(ByteBuffer buffer) throws Exception {
        if (proxyConfig.isIsolateHttpHostHeader()) {
            trySendPartOfHeader(buffer);//尝试发送请求头的一部分，让请求头的host在第二个包里面发送，从而绕过机房的白名单机制。
        }
    }

    @Override
    protected void afterReceived(ByteBuffer buffer) throws Exception {
        if (!tunnelEstablished) {
            //收到代理服务器响应数据
            //分析响应并判断是否连接成功
            String response = new String(buffer.array(), buffer.position(), 12);
            if (response.matches("^HTTP/1.[01] 200$")) {
                buffer.limit(buffer.position());
            } else {
                throw new Exception("Proxy server response an error: " + response);
            }

            tunnelEstablished = true;
            super.onTunnelEstablished();
        }
    }

    @Override
    protected boolean isTunnelEstablished() {
        return tunnelEstablished;
    }

    @Override
    protected void onDispose() {
        config = null;
    }


}
