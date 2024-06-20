package me.smartproxy.tunnel;

import android.net.VpnService;

import org.koin.java.KoinJavaComponent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import me.smartproxy.core.viewmodel.LocalVpnViewModel;
import me.smartproxy.ui.utils.Logger;

public abstract class Tunnel {
    private final LocalVpnViewModel localVpnViewModel = KoinJavaComponent.get(LocalVpnViewModel.class);
    private final static ByteBuffer GL_BUFFER = ByteBuffer.allocate(20000);
    public static long SessionCount;

    protected abstract void onConnected(ByteBuffer buffer) throws Exception;

    protected abstract boolean isTunnelEstablished();

    protected abstract void beforeSend(ByteBuffer buffer) throws Exception;

    protected abstract void afterReceived(ByteBuffer buffer) throws Exception;

    protected abstract void onDispose();

    private SocketChannel innerChannel;
    private ByteBuffer sendRemainBuffer;
    private Selector selector;
    private Tunnel brotherTunnel;
    private boolean disposed;
    private InetSocketAddress serverEP;
    protected InetSocketAddress destAddress;

    public Tunnel(SocketChannel innerChannel, Selector selector) {
        this.innerChannel = innerChannel;
        this.selector = selector;
        SessionCount++;
    }

    public Tunnel(InetSocketAddress serverAddress, Selector selector) throws IOException {
        SocketChannel innerChannel = SocketChannel.open();
        innerChannel.configureBlocking(false);
        this.innerChannel = innerChannel;
        this.selector = selector;
        this.serverEP = serverAddress;
        SessionCount++;
    }

    public void setBrotherTunnel(Tunnel brotherTunnel) {
        this.brotherTunnel = brotherTunnel;
    }

    public void connect(VpnService service, InetSocketAddress destAddress) throws Exception {
        if (service.protect(innerChannel.socket())) {//保护socket不走vpn
            this.destAddress = destAddress;
            innerChannel.register(selector, SelectionKey.OP_CONNECT, this);//注册连接事件
            innerChannel.connect(serverEP);//连接目标
        } else {
            throw new Exception("VPN protect socket failed.");
        }
    }

    protected void beginReceive() throws Exception {
        if (innerChannel.isBlocking()) {
            innerChannel.configureBlocking(false);
        }
        innerChannel.register(selector, SelectionKey.OP_READ, this);//注册读事件
    }


    protected boolean write(ByteBuffer buffer, boolean copyRemainData) throws Exception {

        Logger.i("Tunnel", "write(), 通过innerChannel， 发送到目标服务器" );
        int bytesSent;
        while (buffer.hasRemaining()) {
            bytesSent = innerChannel.write(buffer);
            if (bytesSent == 0) {
                break;//不能再发送了，终止循环
            }
        }

        if (buffer.hasRemaining()) {//数据没有发送完毕
            if (copyRemainData) {//拷贝剩余数据，然后侦听写入事件，待可写入时写入。
                //拷贝剩余数据
                if (sendRemainBuffer == null) {
                    sendRemainBuffer = ByteBuffer.allocate(buffer.capacity());
                }
                sendRemainBuffer.clear();
                sendRemainBuffer.put(buffer);
                sendRemainBuffer.flip();
                innerChannel.register(selector, SelectionKey.OP_WRITE, this);//注册写事件
            }
            return false;
        } else {//发送完毕了
            return true;
        }
    }

    protected void onTunnelEstablished() throws Exception {
        this.beginReceive();//开始接收数据
        brotherTunnel.beginReceive();//兄弟也开始收数据吧
    }

    public void onConnectable() {
        try {
            if (innerChannel.finishConnect()) {//连接成功
                onConnected(GL_BUFFER);//通知子类TCP已连接，子类可以根据协议实现握手等。
            } else {//连接失败
                this.dispose();
            }
        } catch (Exception e) {
            this.dispose();
        }
    }

    public void onReadable(SelectionKey key) {
        try {
            ByteBuffer buffer = GL_BUFFER;
            buffer.clear();
            int bytesRead = innerChannel.read(buffer);
            if (bytesRead > 0) {
                buffer.flip();
                afterReceived(buffer);//先让子类处理，例如解密数据。
                Logger.i("Tunnel", "onReadable(), 先让子类处理，例如解密数据。");

                if (isTunnelEstablished() && buffer.hasRemaining()) {//将读到的数据，转发给兄弟。
                    Logger.i("Tunnel", "onReadable(), 将读到的数据，转发给兄弟。");

                    Logger.i("Tunnel", "onReadable(), 发送之前，先让子类处理，例如做加密等。");
                    brotherTunnel.beforeSend(buffer);//发送之前，先让子类处理，例如做加密等。
                    Logger.i("Tunnel", "onReadable(), 子类处理，完毕，开始发送到目标服务器。");
                    if (!brotherTunnel.write(buffer, true)) {
                        key.cancel();//兄弟吃不消，就取消读取事件。
                        Logger.e("Tunnel", serverEP + " can not read more.");
                    }
                }
            } else if (bytesRead < 0) {
                this.dispose();//连接已关闭，释放资源。
            }
        } catch (Exception e) {
            try {
                Logger.e("Tunnel", "onReadable: innerChannel failed: " + innerChannel.getLocalAddress() + " -> " + innerChannel.getRemoteAddress()  + ": " + e.getLocalizedMessage());
            } catch (IOException ex) {
                // ignore
                Logger.e("Tunnel", "onReadable: innerChannel failed: " +  e.getLocalizedMessage());
            }
            this.dispose();
        }
    }

    public void onWritable(SelectionKey key) {
        try {
            Logger.i("Tunnel", "onWritable(), 发送之前，先让子类处理，例如做加密等。");
            this.beforeSend(sendRemainBuffer);//发送之前，先让子类处理，例如做加密等。

            Logger.i("Tunnel", "onWritable(), 子类处理，完毕，开始发送到目标服务器。");
            if (this.write(sendRemainBuffer, false)) {//如果剩余数据已经发送完毕
                key.cancel();//取消写事件。
                if (isTunnelEstablished()) {
                    brotherTunnel.beginReceive();//这边数据发送完毕，通知兄弟可以收数据了。
                    Logger.i("Tunnel", "onWritable(), 这边数据发送完毕，通知兄弟可以收数据了。");
                } else {
                    this.beginReceive();//开始接收代理服务器响应数据
                    Logger.i("Tunnel", "onWritable(), 开始接收代理服务器响应数据");
                }
            }
        } catch (Exception e) {
            this.dispose();
        }
    }

    public void dispose() {
        disposeInternal(true);
    }

    void disposeInternal(boolean disposeBrother) {
        if (disposed) {
            Logger.d("Tunnel", "Tunnel already disposed.");
        } else {
            try {
                innerChannel.close();
            } catch (Exception e) {
                // ignore
            }

            if (brotherTunnel != null && disposeBrother) {
                brotherTunnel.disposeInternal(false);//把兄弟的资源也释放了。
            }

            innerChannel = null;
            sendRemainBuffer = null;
            selector = null;
            brotherTunnel = null;
            disposed = true;
            SessionCount--;

            onDispose();
        }
    }
}
