package me.smartproxy.core

class QueryState {
    @JvmField
    var ClientQueryID: Short = 0
    @JvmField
    var QueryNanoTime: Long = 0
    @JvmField
    var ClientIP: Int = 0
    @JvmField
    var ClientPort: Short = 0
    @JvmField
    var RemoteIP: Int = 0
    @JvmField
    var RemotePort: Short = 0
}
