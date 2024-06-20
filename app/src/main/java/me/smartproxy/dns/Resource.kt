package me.smartproxy.dns

import me.smartproxy.core.Pool
import java.nio.ByteBuffer

class Resource {
    var domain: String = ""
    var type: Short = 0
    var clazz: Short = 0
    var ttl: Int = 0
    var dataLength: Short = 0
    var data: ByteArray? = null

    var offset = 0

    var length = 0

    companion object {
        fun recycle(r: Resource) {
            r.domain = ""
            r.type = 0
            r.clazz = 0
            r.ttl = 0
            r.dataLength = 0
            r.data = null
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
            r.data = ByteArray(r.dataLength.toInt() and 0xFFFF).also {
                buffer.get(it)
            }
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