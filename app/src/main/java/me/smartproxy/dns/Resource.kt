package me.smartproxy.dns

import me.smartproxy.core.Pool
import java.nio.ByteBuffer

class Resource {
    var domain: String = ""
    var type: Short = 0
    var clazz: Short = 0
    var ttl: Int = 0
    var dataLength: Short = 0
    var data: ByteArray = ByteArray(0)

    var offset = 0

    var length = 0


    fun toBytes(buffer: ByteBuffer) {

        this.dataLength = data.size.toShort()

        this.offset = buffer.position()
        DnsPacket.writeDomain(this.domain, buffer)
        buffer.putShort(this.type)
        buffer.putShort(this.clazz)
        buffer.putInt(this.ttl)

        buffer.putShort(this.dataLength)
        buffer.put(this.data)
        this.length = buffer.position() - this.offset
    }


    companion object {
        fun recycle(r: Resource) {
            ResourcePool.recycle(r)
        }

        fun takeFromPoll(buffer: ByteBuffer): Resource {
            val r = ResourcePool.take()
            r.offset = buffer.arrayOffset() + buffer.position()
            r.domain = DnsPacket.readDomain(buffer, buffer.arrayOffset())
            r.type = buffer.getShort()
            r.clazz = buffer.getShort()
            r.ttl = buffer.getInt()
            r.dataLength = buffer.getShort()
            r.data = ByteArray(r.dataLength.toInt() and 0xFFFF)
            buffer.get(r.data)
            r.length = buffer.arrayOffset() + buffer.position() - r.offset
            return r
        }
    }
}


object ResourcePool : Pool<Resource>() {
    override fun create(): Resource {
        return Resource()
    }
}