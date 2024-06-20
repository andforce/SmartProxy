package me.smartproxy.dns

import me.smartproxy.core.Pool
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

    companion object {
        const val A_RECORD: Short = 1      // A记录查询，即查询域名对应的IPv4地址。
        const val NS_RECORD: Short = 2     // 表示NS记录查询，即查询域名服务器。
        const val CNAME_RECORD: Short = 5  // 表示CNAME记录查询，即查询规范名称。

        fun recycle(q: Question) {
            q.domain = ""
            q.type = 0
            q.clazz = 0
            q.length = 0
            q.offset = 0
            QuestionPool.recycle(q)
        }

        fun takeFromPoll(buffer: ByteBuffer): Question {
            val q = QuestionPool.take()
            q.offset = buffer.arrayOffset() + buffer.position()
            q.domain = DnsPacket.readDomain(buffer, buffer.arrayOffset())
            q.type = buffer.getShort()
            q.clazz = buffer.getShort()
            q.length = buffer.arrayOffset() + buffer.position() - q.offset
            return q
        }
    }
}

object QuestionPool : Pool<Question>() {
    override fun create(): Question {
        return Question()
    }
}