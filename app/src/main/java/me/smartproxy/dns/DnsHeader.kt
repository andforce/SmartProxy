package me.smartproxy.dns

import me.smartproxy.core.Pool
import me.smartproxy.tcpip.CommonMethods
import java.nio.ByteBuffer

class DnsHeader {
    lateinit var data: ByteArray

    var offset: Int = 0

    var ID: Short
        set(value) {
            CommonMethods.writeShort(data, offset + offset_ID, value)
        }
        get() {
            return CommonMethods.readShort(data, offset + offset_ID)
        }
    lateinit var Flags: DnsFlags

    var questionCount: Short
        set(value) {
            CommonMethods.writeShort(data, offset + offset_QuestionCount, value)
        }
        get() {
            return CommonMethods.readShort(data, offset + offset_QuestionCount)
        }

    var resourceCount: Short
        set(value) {
            CommonMethods.writeShort(data, offset + offset_ResourceCount, value)
        }
        get() {
            return CommonMethods.readShort(data, offset + offset_ResourceCount)
        }

    var aResourceCount: Short
        set(value) {
            CommonMethods.writeShort(data, offset + offset_AResourceCount, value)
        }
        get() {
            return CommonMethods.readShort(data, offset + offset_AResourceCount)
        }

    var eResourceCount: Short
        set(value) {
            CommonMethods.writeShort(data, offset + offset_EResourceCount, value)
        }
        get() {
            return CommonMethods.readShort(data, offset + offset_EResourceCount)
        }

    fun toBytes(buffer: ByteBuffer) {
        buffer.putShort(this.ID)
        buffer.putShort(Flags.toShort())
        buffer.putShort(this.questionCount)
        buffer.putShort(this.resourceCount)
        buffer.putShort(this.aResourceCount)
        buffer.putShort(this.eResourceCount)
    }


    companion object {
        fun recycle(header: DnsHeader) {
            DnsHeaderPool.recycle(header)
        }

        fun takeFromPoll(buffer: ByteBuffer): DnsHeader {
            val header = DnsHeaderPool.take()
            header.data = buffer.array()
            header.offset = buffer.arrayOffset() + buffer.position()
            header.ID = buffer.getShort()
            header.Flags = parse(buffer.getShort())
            header.questionCount = buffer.getShort()
            header.resourceCount = buffer.getShort()
            header.aResourceCount = buffer.getShort()
            header.eResourceCount = buffer.getShort()
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

object DnsHeaderPool : Pool<DnsHeader>() {
    override fun create(): DnsHeader {
        return DnsHeader()
    }
}