package me.smartproxy.tcpip;

import androidx.annotation.NonNull;

public class UDPHeader {
    private static final short offset_src_port = 0; // Source port
    private static final short offset_dest_port = 2; // Destination port
    private static final short offset_tlen = 4; // Datagram length
    private static final short offset_crc = 6; // Checksum

    private byte[] data;
    private int offset;

    public UDPHeader(byte[] data, int offset) {
        this.data = data;
        this.offset = offset;
    }

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

    public short getSourcePort() {
        return CommonMethods.readShort(data, offset + offset_src_port);
    }

    public void setSourcePort(short value) {
        CommonMethods.writeShort(data, offset + offset_src_port, value);
    }

    public short getDestinationPort() {
        return CommonMethods.readShort(data, offset + offset_dest_port);
    }

    public void setDestinationPort(short value) {
        CommonMethods.writeShort(data, offset + offset_dest_port, value);
    }

    public int getTotalLength() {
        return CommonMethods.readShort(data, offset + offset_tlen) & 0xFFFF;
    }

    public void setTotalLength(int value) {
        CommonMethods.writeShort(data, offset + offset_tlen, (short) value);
    }

    public short getCrc() {
        return CommonMethods.readShort(data, offset + offset_crc);
    }

    public void setCrc(short value) {
        CommonMethods.writeShort(data, offset + offset_crc, value);
    }

    @Override
    @NonNull
    public String toString() {
        return getSourcePort() + "->" + getDestinationPort();
    }
}
