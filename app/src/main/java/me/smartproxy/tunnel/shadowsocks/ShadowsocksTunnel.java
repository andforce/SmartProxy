package me.smartproxy.tunnel.shadowsocks;

import java.nio.ByteBuffer;
import java.nio.channels.Selector;

import me.smartproxy.tunnel.IEncryptor;
import me.smartproxy.tunnel.Tunnel;

public class ShadowsocksTunnel extends Tunnel {

    private IEncryptor encryptor;
    private ShadowsocksConfig m_Config;
    private boolean mTunnelEstablished;

    public ShadowsocksTunnel(ShadowsocksConfig config, Selector selector) throws Exception {
        super(config.ServerAddress, selector);
        if (config.Encryptor == null) {
            throw new Exception("Error: The Encryptor for ShadowsocksTunnel is null.");
        }
        m_Config = config;
        encryptor = config.Encryptor;
    }

    @Override
    protected void onConnected(ByteBuffer buffer) throws Exception {

        //构造socks5请求（跳过前3个字节）
        buffer.clear();
        buffer.put((byte) 0x03);//domain
        byte[] domainBytes = m_DestAddress.getHostName().getBytes();
        buffer.put((byte) domainBytes.length);//domain length;
        buffer.put(domainBytes);
        buffer.putShort((short) m_DestAddress.getPort());
        buffer.flip();

        encryptor.encrypt(buffer);
        if (write(buffer, true)) {
            mTunnelEstablished = true;
            onTunnelEstablished();
        } else {
            mTunnelEstablished = true;
            this.beginReceive();
        }
    }

    @Override
    protected boolean isTunnelEstablished() {
        return mTunnelEstablished;
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
        m_Config = null;
        encryptor = null;
    }

}
