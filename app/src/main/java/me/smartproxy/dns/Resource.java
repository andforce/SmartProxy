package me.smartproxy.dns;

import java.nio.ByteBuffer;

public class Resource {
    public String domain;
    public short type;
    public short clazz;
    public int ttl;
    public short dataLength;
    public byte[] data;

    private int offset;

    public int Offset() {
        return offset;
    }

    private int length;

    public int Length() {
        return length;
    }

    public static Resource fromBytes(ByteBuffer buffer) {

        Resource r = new Resource();
        r.offset = buffer.arrayOffset() + buffer.position();
        r.domain = DnsPacket.readDomain(buffer, buffer.arrayOffset());
        r.type = buffer.getShort();
        r.clazz = buffer.getShort();
        r.ttl = buffer.getInt();
        r.dataLength = buffer.getShort();
        r.data = new byte[r.dataLength & 0xFFFF];
        buffer.get(r.data);
        r.length = buffer.arrayOffset() + buffer.position() - r.offset;
        return r;
    }

    public void toBytes(ByteBuffer buffer) {
        if (this.data == null) {
            this.data = new byte[0];
        }
        this.dataLength = (short) this.data.length;

        this.offset = buffer.position();
        DnsPacket.writeDomain(this.domain, buffer);
        buffer.putShort(this.type);
        buffer.putShort(this.clazz);
        buffer.putInt(this.ttl);

        buffer.putShort(this.dataLength);
        buffer.put(this.data);
        this.length = buffer.position() - this.offset;
    }


}
