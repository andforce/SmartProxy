package me.smartproxy.dns

import me.smartproxy.tcpip.CommonMethods
import java.nio.ByteBuffer

class DnsPacket {
    lateinit var dnsHeader: DnsHeader
    var questions = mutableListOf<Question>()
    var resources = mutableListOf<Resource>()
    var aResources = mutableListOf<Resource>()
    var eResources = mutableListOf<Resource>()

    var size: Int = 0

    private fun clear() {
        questions.clear()
        resources.clear()
        aResources.clear()
        eResources.clear()
        size = 0
    }

    private fun readIpFormData() : String {
        val sb = StringBuilder()
        sb.append('[')

        for (i in 0 until dnsHeader.resourceCount) {
            val resource = resources[i]
            if (resource.type == Question.A_RECORD) {
                val ipInt = CommonMethods.readInt(resource.data, 0)
                val result =  "ip $i : ${CommonMethods.ipIntToString(ipInt)}"
                sb.append(result).append(", ")
            }
        }
        sb.append(']')
        return sb.toString()
    }

    override fun toString(): String {
        return "DnsPacket{" +
                "dnsHeader=" + dnsHeader +
                ", questions=" + questions +
                ", resources=" + readIpFormData() +
                ", aResources=" + aResources +
                ", eResources=" + eResources +
                ", size=" + size +
                '}'

    }

    companion object {

        fun recycle(packet: DnsPacket) {

            packet.questions.forEach {
                Question.recycle(it)
            }
            packet.resources.forEach {
                Resource.recycle(it)
            }
            packet.aResources.forEach {
                Resource.recycle(it)
            }
            packet.eResources.forEach {
                Resource.recycle(it)
            }

            packet.dnsHeader.let {
                DnsHeader.recycle(it)
            }

            packet.clear().also {
                DnsPacketPool.recycle(packet)
            }
        }

        fun takeFromPoll(buffer: ByteBuffer): DnsPacket? {
            if (buffer.limit() < 12) {
                // DNS header at least 12 bytes
                return null
            }
            if (buffer.limit() > 512) {
                // DNS packet at most 512 bytes
                return null
            }

            val packet = DnsPacketPool.take()
            packet.size = buffer.limit()
            packet.dnsHeader = DnsHeader.takeFromPoll(buffer)

            val questionCount = packet.dnsHeader.questionCount.toInt()
            val resourceCount = packet.dnsHeader.resourceCount.toInt()
            val aResourceCount = packet.dnsHeader.aResourceCount.toInt()
            val eResourceCount = packet.dnsHeader.eResourceCount.toInt()

            if (questionCount > 2
                || resourceCount > 50
                || aResourceCount > 50
                || eResourceCount > 50) {
                return null
            }

            for (i in 0 until questionCount) {
                packet.questions.add(Question.takeFromPoll(buffer))
            }

            for (i in 0 until resourceCount) {
                packet.resources.add(Resource.takeFromPoll(buffer))
            }

            for (i in 0 until aResourceCount) {
                packet.aResources.add(Resource.takeFromPoll(buffer))
            }

            for (i in 0 until eResourceCount) {
                packet.eResources[i] = Resource.takeFromPoll(buffer)
            }

            return packet
        }

        fun readDomain(buffer: ByteBuffer, dnsHeaderOffset: Int): String {
            val sb = StringBuilder()
            var len = 0
            while (buffer.hasRemaining() && ((buffer.get().toInt() and 0xFF).also {
                    len = it
                }) > 0) {
                if ((len and 0xc0) == 0xc0) // pointer 高2位为11表示是指针。如：1100 0000
                {
                    // 指针的取值是前一字节的后6位加后一字节的8位共14位的值。
                    var pointer = buffer.get().toInt() and 0xFF // 低8位
                    pointer = pointer or ((len and 0x3F) shl 8) // 高6位

                    val newBuffer = ByteBuffer.wrap(
                        buffer.array(),
                        dnsHeaderOffset + pointer,
                        dnsHeaderOffset + buffer.limit()
                    )
                    sb.append(readDomain(newBuffer, dnsHeaderOffset))
                    return sb.toString()
                } else {
                    while (len > 0 && buffer.hasRemaining()) {
                        sb.append((buffer.get().toInt() and 0xFF).toChar())
                        len--
                    }
                    sb.append('.')
                }
            }

            if (len == 0 && sb.isNotEmpty()) {
                sb.deleteCharAt(sb.length - 1) //去掉末尾的点（.）
            }
            return sb.toString()
        }

        fun writeDomain(domain: String?, buffer: ByteBuffer) {
            if (domain == null || domain === "") {
                buffer.put(0.toByte())
                return
            }

            val arr = domain.split(".").dropLastWhile { it.isEmpty() }.toTypedArray()
            for (item in arr) {
                if (arr.size > 1) {
                    buffer.put(item.length.toByte())
                }

                for (i in item.indices) {
                    buffer.put(item.codePointAt(i).toByte())
                }
            }
        }
    }
}
