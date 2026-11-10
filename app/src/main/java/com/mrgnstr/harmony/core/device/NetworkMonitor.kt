package com.mrgnstr.harmony.core.device

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether the device has a working internet connection (a validated default network), kept up to date for the whole process. */
@Singleton
class NetworkMonitor @Inject constructor(@ApplicationContext context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val _online = MutableStateFlow(current())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    init {
        // A callback that can't be registered (no service, a security exception) leaves the app treating the device as online.
        runCatching {
            connectivity?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { _online.value = caps.usable() }
                override fun onLost(network: Network) { _online.value = false }
            })
        }
    }

    private fun current(): Boolean {
        val cm = connectivity ?: return true
        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull() ?: return false
        return caps.usable()
    }

    private fun NetworkCapabilities.usable() =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
