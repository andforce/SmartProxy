package me.smartproxy.dns

import me.smartproxy.tcpip.CommonMethods
import java.nio.ByteBuffer

class DnsHeader(var Data: ByteArray, var Offset: Int) {
    var ID: Short
        set(value) {
            CommonMethods.writeShort(Data, Offset + offset_ID, value)
        }
        get() {
            return CommonMethods.readShort(Data, Offset + offset_ID)
        }
    lateinit var Flags: DnsFlags

    var QuestionCount: Short
        set(value) {
            CommonMethods.writeShort(Data, Offset + offset_QuestionCount, value)
        }
        get() {
            return CommonMethods.readShort(Data, Offset + offset_QuestionCount)
        }

    var ResourceCount: Short
        set(value) {
            CommonMethods.writeShort(Data, Offset + offset_ResourceCount, value)
        }
        get() {
            return CommonMethods.readShort(Data, Offset + offset_ResourceCount)
        }

    var AResourceCount: Short
        set(value) {
            CommonMethods.writeShort(Data, Offset + offset_AResourceCount, value)
        }
        get() {
            return CommonMethods.readShort(Data, Offset + offset_AResourceCount)
        }

    var EResourceCount: Short
        set(value) {
            CommonMethods.writeShort(Data, Offset + offset_EResourceCount, value)
        }
        get() {
            return CommonMethods.readShort(Data, Offset + offset_EResourceCount)
        }

    fun toBytes(buffer: ByteBuffer) {
        buffer.putShort(this.ID)
        buffer.putShort(Flags.toShort())
        buffer.putShort(this.QuestionCount)
        buffer.putShort(this.ResourceCount)
        buffer.putShort(this.AResourceCount)
        buffer.putShort(this.EResourceCount)
    }


    companion object {
        fun fromBytes(buffer: ByteBuffer): DnsHeader {
            val header = DnsHeader(buffer.array(), buffer.arrayOffset() + buffer.position())
            header.ID = buffer.getShort()
            header.Flags = parse(buffer.getShort())
            header.QuestionCount = buffer.getShort()
            header.ResourceCount = buffer.getShort()
            header.AResourceCount = buffer.getShort()
            header.EResourceCount = buffer.getShort()
            return header
        }

        private const val offset_ID: Short = 0
        private const val offset_Flags: Short = 2
        private const val offset_QuestionCount: Short = 4
        private const val offset_ResourceCount: Short = 6
        private const val offset_AResourceCount: Short = 8
        private const val offset_EResourceCount: Short = 10
    }
}
