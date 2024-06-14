package me.smartproxy.dns;

import java.nio.ByteBuffer;

public class Question {
    public String domain;
    public short type;
    public short clazz;

    private int offset;

    public int Offset() {
        return offset;
    }

    private int length;

    public int Length() {
        return length;
    }

    public static Question fromBytes(ByteBuffer buffer) {
        Question q = new Question();
        q.offset = buffer.arrayOffset() + buffer.position();
        q.domain = DnsPacket.readDomain(buffer, buffer.arrayOffset());
        q.type = buffer.getShort();
        q.clazz = buffer.getShort();
        q.length = buffer.arrayOffset() + buffer.position() - q.offset;
        return q;
    }

    public void toBytes(ByteBuffer buffer) {
        this.offset = buffer.position();
        DnsPacket.writeDomain(this.domain, buffer);
        buffer.putShort(this.type);
        buffer.putShort(this.clazz);
        this.length = buffer.position() - this.offset;
    }
}
