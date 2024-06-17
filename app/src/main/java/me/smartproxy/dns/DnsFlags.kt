package me.smartproxy.dns

class DnsFlags {
    var QR: Boolean = false //1 bits
    var OpCode: Int = 0 //4 bits
    var AA: Boolean = false //1 bits
    var TC: Boolean = false //1 bits
    var RD: Boolean = false //1 bits
    var RA: Boolean = false //1 bits
    var Zero: Int = 0 //3 bits
    var Rcode: Int = 0 //4 bits

    fun toShort(): Short {
        var fValues = 0
        fValues = fValues or ((if (this.QR) 1 else 0) shl 7)
        fValues = fValues or ((this.OpCode and 0x0F) shl 3)
        fValues = fValues or ((if (this.AA) 1 else 0) shl 2)
        fValues = fValues or ((if (this.TC) 1 else 0) shl 1)
        fValues = fValues or if (this.RD) 1 else 0
        fValues = fValues or ((if (this.RA) 1 else 0) shl 15)
        fValues = fValues or ((this.Zero and 0x07) shl 12)
        fValues = fValues or ((this.Rcode and 0x0F) shl 8)
        return fValues.toShort()
    }
}

fun parse(value: Short): DnsFlags {
    val fValues = value.toInt() and 0xFFFF
    val flags = DnsFlags()
    flags.QR = ((fValues shr 7) and 0x01) == 1
    flags.OpCode = (fValues shr 3) and 0x0F
    flags.AA = ((fValues shr 2) and 0x01) == 1
    flags.TC = ((fValues shr 1) and 0x01) == 1
    flags.RD = (fValues and 0x01) == 1
    flags.RA = (fValues shr 15) == 1
    flags.Zero = (fValues shr 12) and 0x07
    flags.Rcode = ((fValues shr 8) and 0xF)
    return flags
}