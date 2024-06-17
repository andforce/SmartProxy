package me.smartproxy.tunnel.shadowsocks;

import java.nio.ByteBuffer;
import java.nio.channels.Selector;

import me.smartproxy.tunnel.IEncryptor;
import me.smartproxy.tunnel.Tunnel;

public class ShadowsocksTunnel extends Tunnel {

    private IEncryptor encryptor;
    private ShadowsocksConfig config;
    private boolean tunnelEstablished;

    public ShadowsocksTunnel(ShadowsocksConfig config, Selector selector) throws Exception {
        super(config.getSocketAddress(), selector);
        if (config.getEncryptor() == null) {
            throw new Exception("Error: The Encryptor for ShadowsocksTunnel is null.");
        }
        this.config = config;
        encryptor = config.getEncryptor();
    }

    @Override
    protected void onConnected(ByteBuffer buffer) throws Exception {

        //构造socks5请求（跳过前3个字节）
        buffer.clear();
        buffer.put((byte) 0x03);//domain
        byte[] domainBytes = destAddress.getHostName().getBytes();
        buffer.put((byte) domainBytes.length);//domain length;
        buffer.put(domainBytes);
        buffer.putShort((short) destAddress.getPort());
        buffer.flip();

        encryptor.encrypt(buffer);
        if (write(buffer, true)) {
            tunnelEstablished = true;
            onTunnelEstablished();
        } else {
            tunnelEstablished = true;
            this.beginReceive();
        }
    }

    @Override
    protected boolean isTunnelEstablished() {
        return tunnelEstablished;
    }

    @Override
    protected void beforeSend(ByteBuffer buffer) throws Exception {
        encryptor.encrypt(buffer);
    }

    @Override
    protected void afterReceived(ByteBuffer buffer) throws Exception {
        encryptor.decrypt(buffer);
    }

    @Override
    protected void onDispose() {
        config = null;
        encryptor = null;
    }

}
