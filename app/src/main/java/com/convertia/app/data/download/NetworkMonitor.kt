package com.convertia.app.data.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Se a rede atual é ilimitada (Wi-Fi, cabo). Rede limitada (dados móveis, hotspot) faz os
 * downloads esperarem quando "Somente em Wi-Fi" está ligado.
 */
class NetworkMonitor(context: Context) {

    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val _unmetered = MutableStateFlow(currentlyUnmetered())
    val unmetered: StateFlow<Boolean> = _unmetered.asStateFlow()

    init {
        manager?.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    _unmetered.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                }

                override fun onLost(network: Network) {
                    _unmetered.value = currentlyUnmetered()
                }
            },
        )
    }

    private fun currentlyUnmetered(): Boolean {
        val capabilities = manager?.activeNetwork?.let { manager.getNetworkCapabilities(it) } ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
