package me.smartproxy.core

class IPAddress {
    val address: String
    val prefixLength: Int

    constructor(address: String, prefixLength: Int) {
        this.address = address
        this.prefixLength = prefixLength
    }

    constructor(addressStr: String) {
        val arrStrings =
            addressStr.split("/").dropLastWhile { it.isEmpty() }.toTypedArray()
        val address = arrStrings[0]
        var prefixLength = 32
        if (arrStrings.size > 1) {
            prefixLength = arrStrings[1].toInt()
        }
        this.address = address
        this.prefixLength = prefixLength
    }

    override fun toString(): String {
        return "$address/$prefixLength"
    }

    override fun equals(o: Any?): Boolean {
        return if (o == null) {
            false
        } else {
            this.toString() == o.toString()
        }
    }

    override fun hashCode(): Int {
        var result = address.hashCode()
        result = 31 * result + prefixLength
        return result
    }
}
