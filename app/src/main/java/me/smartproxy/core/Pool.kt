package me.smartproxy.core

abstract class Pool<T> {
    private val pool = mutableListOf<T>()

    fun take(): T {
        return if (pool.isEmpty()) {
            create()
        } else {
            pool.removeFirst()
        }
    }

    fun recycle(item: T) {
        pool.add(item)
    }

    abstract fun create(): T
}