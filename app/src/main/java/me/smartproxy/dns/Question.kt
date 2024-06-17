package me.smartproxy.dns

import java.nio.ByteBuffer

class Question {
    var domain: String = ""
    var type: Short = 0
    var clazz: Short = 0
    var length = 0
    var offset = 0

    fun toBytes(buffer: ByteBuffer) {
        this.offset = buffer.position()
        DnsPacket.writeDomain(this.domain, buffer)
        buffer.putShort(this.type)
        buffer.putShort(this.clazz)
        this.length = buffer.position() - this.offset
    }
}

fun fromBytes(buffer: ByteBuffer): Question {
    val q = Question()
    q.offset = buffer.arrayOffset() + buffer.position()
    q.domain = DnsPacket.readDomain(buffer, buffer.arrayOffset())
    q.type = buffer.getShort()
    q.clazz = buffer.getShort()
    q.length = buffer.arrayOffset() + buffer.position() - q.offset
    return q
}