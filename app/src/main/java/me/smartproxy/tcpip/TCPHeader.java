package me.smartproxy.tcpip;


import androidx.annotation.NonNull;

public class TCPHeader {

    public static final int FIN = 1;
    public static final int SYN = 2;
    public static final int RST = 4;
    public static final int PSH = 8;
    public static final int ACK = 16;
    public static final int URG = 32;

    private static final short offset_src_port = 0; // 16位源端口
    private static final short offset_dest_port = 2; // 16位目的端口
    private static final int offset_seq = 4; // 32位序列号
    private static final int offset_ack = 8; // 32位确认号
    private static final byte offset_lenres = 12; // 4位首部长度/4位保留字
    private static final byte offset_flag = 13; // 6位标志位
    private static final short offset_win = 14; // 16位窗口大小
    private static final short offset_crc = 16; // 16位校验和
    private static final short offset_urp = 18; // 16位紧急数据偏移量

    private byte[] data;
    private int offset;

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public TCPHeader(byte[] data, int offset) {
        this.data = data;
        this.offset = offset;
    }

    public int getHeaderLength() {
        int lenres = data[offset + offset_lenres] & 0xFF;
        return (lenres >> 4) * 4;
    }

    public short getSourcePort() {
        return CommonMethods.readShort(data, offset + offset_src_port);
    }

    public int getSourcePortInt() {
        return getSourcePort() & 0xFFFF;
    }

    public void setSourcePort(short value) {
        CommonMethods.writeShort(data, offset + offset_src_port, value);
    }

    public short getDestinationPort() {
        return CommonMethods.readShort(data, offset + offset_dest_port);
    }

    public int getDestinationPortInt() {
        return getDestinationPort() & 0xFFFF;
    }

    public void setDestinationPort(short value) {
        CommonMethods.writeShort(data, offset + offset_dest_port, value);
    }

    public byte getFlags() {
        return data[offset + offset_flag];
    }

    public short getCrc() {
        return CommonMethods.readShort(data, offset + offset_crc);
    }

    public void setCrc(short value) {
        CommonMethods.writeShort(data, offset + offset_crc, value);
    }

    public int getSeqID() {
        return CommonMethods.readInt(data, offset + offset_seq);
    }

    public int getAckID() {
        return CommonMethods.readInt(data, offset + offset_ack);
    }

    private String parseAction() {
        StringBuilder sb = new StringBuilder();
        if ((getFlags() & SYN) == SYN) {
            sb.append("SYN ");
        }
        if ((getFlags() & ACK) == ACK) {
            sb.append("ACK ");
        }
        if ((getFlags() & PSH) == PSH) {
            sb.append("PSH ");
        }
        if ((getFlags() & RST) == RST) {
            sb.append("RST ");
        }
        if ((getFlags() & FIN) == FIN) {
            sb.append("FIN ");
        }
        if ((getFlags() & URG) == URG) {
            sb.append("URG ");
        }
        return sb.toString().trim();
    }

    public String debugInfo() {
        return "TCP: [" + parseAction() + "] SeqID:" + getSeqID() + " <---> AckID:" + getAckID();
    }

    @NonNull
    @Override
    public String toString() {
        return "TCP: " + parseAction() + getSourcePortInt() + " -> " + getDestinationPortInt() + " SeqID:" + getSeqID() + " <---> AckID:" + getAckID();
    }
}
