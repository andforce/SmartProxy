package me.smartproxy.tcpip

object IPData {
    const val IP: Short = 0x0800
    const val ICMP: Byte = 1
    const val TCP: Byte = 6
    const val UDP: Byte = 17


    const val offset_ver_ihl: Byte = 0 // 0: Version (4 bits) + Internet header length (4// bits)
    const val offset_tos: Byte = 1 // 1: Type of service
    const val offset_tlen: Short = 2 // 2: Total length
    const val offset_identification: Short = 4 // :4 Identification
    const val offset_flags_fo: Short = 6 // 6: Flags (3 bits) + Fragment offset (13 bits)
    const val offset_ttl: Byte = 8 // 8: Time to live
    const val offset_proto: Byte = 9 // 9: Protocol
    const val offset_crc: Short = 10 // 10: Header checksum
    const val offset_src_ip: Int = 12 // 12: Source address
    const val offset_dest_ip: Int = 16 // 16: Destination address
    const val offset_op_pad: Int = 20 // 20: Option + Padding
}
