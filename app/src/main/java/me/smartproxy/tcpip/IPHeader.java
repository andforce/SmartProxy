package me.smartproxy.tcpip;

import java.util.Locale;

public class IPHeader {

    public byte[] data;
    public int offset;

    public IPHeader(byte[] data, int offset) {
        this.data = data;
        this.offset = offset;
    }

    public void defaultHeader() {
        setHeaderLength(20);
        setTos((byte) 0);
        setTotalLength(0);
        setIdentification(0);
        setFlagsAndOffset((short) 0);
        setTTL((byte) 64);
    }

    public int getDataLength() {
        return this.getTotalLength() - this.getHeaderLength();
    }

    public int getHeaderLength() {
        return (data[offset + IPData.offset_ver_ihl] & 0x0F) * 4;
    }

    public void setHeaderLength(int value) {
        data[offset +  IPData.offset_ver_ihl] = (byte) ((4 << 4) | (value / 4));
    }

    public byte getTos() {
        return data[offset +  IPData.offset_tos];
    }

    public void setTos(byte value) {
        data[offset +  IPData.offset_tos] = value;
    }

    public int getTotalLength() {
        return CommonMethods.readShort(data, offset +  IPData.offset_tlen) & 0xFFFF;
    }

    public void setTotalLength(int value) {
        CommonMethods.writeShort(data, offset +  IPData.offset_tlen, (short) value);
    }

    public int getIdentification() {
        return CommonMethods.readShort(data, offset +  IPData.offset_identification) & 0xFFFF;
    }

    public void setIdentification(int value) {
        CommonMethods.writeShort(data, offset +  IPData.offset_identification, (short) value);
    }

    public short getFlagsAndOffset() {
        return CommonMethods.readShort(data, offset +  IPData.offset_flags_fo);
    }

    public void setFlagsAndOffset(short value) {
        CommonMethods.writeShort(data, offset +  IPData.offset_flags_fo, value);
    }

    public byte getTTL() {
        return data[offset +  IPData.offset_ttl];
    }

    public void setTTL(byte value) {
        data[offset +  IPData.offset_ttl] = value;
    }

    public byte getProtocol() {
        return data[offset +  IPData.offset_proto];
    }

    public void setProtocol(byte value) {
        data[offset +  IPData.offset_proto] = value;
    }

    public short getCrc() {
        return CommonMethods.readShort(data, offset +  IPData.offset_crc);
    }

    public void setCrc(short value) {
        CommonMethods.writeShort(data, offset +  IPData.offset_crc, value);
    }

    public int getSourceIP() {
        return CommonMethods.readInt(data, offset +  IPData.offset_src_ip);
    }

    public void setSourceIP(int value) {
        CommonMethods.writeInt(data, offset +  IPData.offset_src_ip, value);
    }

    public int getDestinationIP() {
        return CommonMethods.readInt(data, offset +  IPData.offset_dest_ip);
    }

    public void setDestinationIP(int value) {
        CommonMethods.writeInt(data, offset +  IPData.offset_dest_ip, value);
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "%s->%s Pro=%s,HLen=%d", CommonMethods.ipIntToString(getSourceIP()), CommonMethods.ipIntToString(getDestinationIP()), getProtocol(), getHeaderLength());
    }

}
