package me.smartproxy.core

import android.net.VpnService
import org.koin.core.context.GlobalContext
import org.koin.java.KoinJavaComponent
import kotlin.reflect.KClass

inline fun <reified T : VpnService> KoinJavaComponent.getOrNull(): T? {
    return try {
        val localVpnService = GlobalContext.get().getScope(T::class.java.name).get<T>()
        return localVpnService
    } catch (e: Exception) {
        null
    }
}

inline fun <reified T : VpnService> T.getOrNull(): T? {
    return try {
        val localVpnService = GlobalContext.get().getScope(this.javaClass.name).get<T>()
        return localVpnService
    } catch (e: Exception) {
        null
    }
}


inline fun <reified T : VpnService> KClass<T>.getOrNull(): T? {
    return try {
        val localVpnService = GlobalContext.get().getScope(this.javaObjectType.name).get<T>()
        return localVpnService
    } catch (e: Exception) {
        null
    }
}


inline fun <reified T : VpnService> Class<T>.getOrNull(): T? {
    return try {
        val localVpnService = GlobalContext.get().getScope(this.name).get<T>()
        return localVpnService
    } catch (e: Exception) {
        null
    }
}