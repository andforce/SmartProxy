package me.smartproxy.dns;

import java.nio.ByteBuffer;

public class DnsPacket {
    public DnsHeader dnsHeader;
    public Question[] questions;
    public Resource[] resources;
    public Resource[] aResources;
    public Resource[] eResources;

    public int size;

    public static DnsPacket fromBytes(ByteBuffer buffer) {
        if (buffer.limit() < 12)
            return null;
        if (buffer.limit() > 512)
            return null;

        DnsPacket packet = new DnsPacket();
        packet.size = buffer.limit();
        packet.dnsHeader = DnsHeader.Companion.fromBytes(buffer);

        if (packet.dnsHeader.getQuestionCount() > 2
                || packet.dnsHeader.getResourceCount() > 50
                || packet.dnsHeader.getAResourceCount() > 50
                || packet.dnsHeader.getEResourceCount() > 50) {
            return null;
        }

        packet.questions = new Question[packet.dnsHeader.getQuestionCount()];
        packet.resources = new Resource[packet.dnsHeader.getResourceCount()];
        packet.aResources = new Resource[packet.dnsHeader.getAResourceCount()];
        packet.eResources = new Resource[packet.dnsHeader.getEResourceCount()];

        for (int i = 0; i < packet.questions.length; i++) {
            packet.questions[i] = Question.Companion.fromBytes(buffer);
        }

        for (int i = 0; i < packet.resources.length; i++) {
            packet.resources[i] = Resource.Companion.fromBytes(buffer);
        }

        for (int i = 0; i < packet.aResources.length; i++) {
            packet.aResources[i] = Resource.Companion.fromBytes(buffer);
        }

        for (int i = 0; i < packet.eResources.length; i++) {
            packet.eResources[i] = Resource.Companion.fromBytes(buffer);
        }

        return packet;
    }

    public void toBytes(ByteBuffer buffer) {
        dnsHeader.setQuestionCount((short) 0);
        dnsHeader.setResourceCount((short) 0);
        dnsHeader.setAResourceCount((short) 0);
        dnsHeader.setEResourceCount((short) 0);

        if (questions != null) {
            dnsHeader.setQuestionCount((short) questions.length);
        }

        if (resources != null) {
            dnsHeader.setResourceCount((short) resources.length);
        }
        if (aResources != null) {
            dnsHeader.setAResourceCount((short) aResources.length);
        }
        if (eResources != null) {
            dnsHeader.setEResourceCount((short) eResources.length);
        }

        this.dnsHeader.toBytes(buffer);

        for (int i = 0; i < dnsHeader.getQuestionCount(); i++) {
            this.questions[i].toBytes(buffer);
        }

        for (int i = 0; i < dnsHeader.getResourceCount(); i++) {
            this.resources[i].toBytes(buffer);
        }

        for (int i = 0; i < dnsHeader.getAResourceCount(); i++) {
            this.aResources[i].toBytes(buffer);
        }

        for (int i = 0; i < dnsHeader.getEResourceCount(); i++) {
            this.eResources[i].toBytes(buffer);
        }
    }

    public static String readDomain(ByteBuffer buffer, int dnsHeaderOffset) {
        StringBuilder sb = new StringBuilder();
        int len = 0;
        while (buffer.hasRemaining() && (len = (buffer.get() & 0xFF)) > 0) {
            if ((len & 0xc0) == 0xc0)// pointer 高2位为11表示是指针。如：1100 0000
            {
                // 指针的取值是前一字节的后6位加后一字节的8位共14位的值。
                int pointer = buffer.get() & 0xFF;// 低8位
                pointer |= (len & 0x3F) << 8;// 高6位

                ByteBuffer newBuffer = ByteBuffer.wrap(buffer.array(), dnsHeaderOffset + pointer, dnsHeaderOffset + buffer.limit());
                sb.append(readDomain(newBuffer, dnsHeaderOffset));
                return sb.toString();
            } else {
                while (len > 0 && buffer.hasRemaining()) {
                    sb.append((char) (buffer.get() & 0xFF));
                    len--;
                }
                sb.append('.');
            }
        }

        if (len == 0 && sb.length() > 0) {
            sb.deleteCharAt(sb.length() - 1);//去掉末尾的点（.）
        }
        return sb.toString();
    }

    public static void writeDomain(String domain, ByteBuffer buffer) {
        if (domain == null || domain == "") {
            buffer.put((byte) 0);
            return;
        }

        String[] arr = domain.split("\\.");
        for (String item : arr) {
            if (arr.length > 1) {
                buffer.put((byte) item.length());
            }

            for (int i = 0; i < item.length(); i++) {
                buffer.put((byte) item.codePointAt(i));
            }
        }
    }
}
