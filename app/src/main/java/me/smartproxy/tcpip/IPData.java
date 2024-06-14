package me.smartproxy.tcpip;

public class IPData {
    public static final short IP = 0x0800;
    public static final byte ICMP = 1;
    public static final byte TCP = 6;
    public static final byte UDP = 17;


    public static final byte offset_ver_ihl = 0; // 0: Version (4 bits) + Internet header length (4// bits)
    public static final byte offset_tos = 1; // 1: Type of service
    public static final short offset_tlen = 2; // 2: Total length
    public static final short offset_identification = 4; // :4 Identification
    public static final short offset_flags_fo = 6; // 6: Flags (3 bits) + Fragment offset (13 bits)
    public static final byte offset_ttl = 8; // 8: Time to live
    public static final byte offset_proto = 9; // 9: Protocol
    public static final short offset_crc = 10; // 10: Header checksum
    public static final int offset_src_ip = 12; // 12: Source address
    public static final int offset_dest_ip = 16; // 16: Destination address
    public static final int offset_op_pad = 20; // 20: Option + Padding
}
