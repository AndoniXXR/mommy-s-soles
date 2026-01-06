package com.e621.client.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Network monitor to detect network type and quality
 * Based on T2's NetworkState implementation for handling mobile data vs WiFi
 */
object NetworkMonitor {
    
    data class NetworkState(
        val isConnected: Boolean = false,
        val isValidated: Boolean = false,
        val isMetered: Boolean = true,  // Assume metered by default (safer for mobile data)
        val isWifi: Boolean = false,
        val isCellular: Boolean = false,
        val connectionQuality: ConnectionQuality = ConnectionQuality.UNKNOWN
    )
    
    enum class ConnectionQuality {
        EXCELLENT,  // WiFi or fast mobile
        GOOD,       // 4G/LTE
        MODERATE,   // 3G
        POOR,       // 2G or slow connection
        UNKNOWN
    }
    
    private val _networkState = MutableStateFlow(NetworkState())
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()
    
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    /**
     * Initialize the network monitor
     * Should be called from Application.onCreate()
     */
    fun init(context: Context) {
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        // Get initial state
        updateNetworkState()
        
        // Register callback for network changes
        registerNetworkCallback()
    }
    
    private fun registerNetworkCallback() {
        val cm = connectivityManager ?: return
        
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateNetworkState()
            }
            
            override fun onLost(network: Network) {
                _networkState.value = NetworkState(isConnected = false)
            }
            
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                updateNetworkStateFromCapabilities(networkCapabilities)
            }
        }
        
        try {
            cm.registerNetworkCallback(networkRequest, networkCallback!!)
        } catch (e: Exception) {
            // Fallback to polling if callback registration fails
        }
    }
    
    private fun updateNetworkState() {
        val cm = connectivityManager ?: return
        
        val activeNetwork = cm.activeNetwork
        if (activeNetwork == null) {
            _networkState.value = NetworkState(isConnected = false)
            return
        }
        
        val capabilities = cm.getNetworkCapabilities(activeNetwork)
        if (capabilities == null) {
            _networkState.value = NetworkState(isConnected = false)
            return
        }
        
        updateNetworkStateFromCapabilities(capabilities)
    }
    
    private fun updateNetworkStateFromCapabilities(capabilities: NetworkCapabilities) {
        val isConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        
        // Estimate connection quality
        val quality = estimateConnectionQuality(capabilities, isWifi, isCellular)
        
        _networkState.value = NetworkState(
            isConnected = isConnected,
            isValidated = isValidated,
            isMetered = isMetered,
            isWifi = isWifi,
            isCellular = isCellular,
            connectionQuality = quality
        )
    }
    
    private fun estimateConnectionQuality(
        capabilities: NetworkCapabilities,
        isWifi: Boolean,
        isCellular: Boolean
    ): ConnectionQuality {
        // Check bandwidth if available (API 21+)
        val downstreamBandwidth = capabilities.linkDownstreamBandwidthKbps
        
        return when {
            isWifi -> ConnectionQuality.EXCELLENT
            downstreamBandwidth >= 10000 -> ConnectionQuality.EXCELLENT  // 10+ Mbps
            downstreamBandwidth >= 2000 -> ConnectionQuality.GOOD        // 2-10 Mbps (4G)
            downstreamBandwidth >= 500 -> ConnectionQuality.MODERATE     // 500 Kbps - 2 Mbps (3G)
            downstreamBandwidth > 0 -> ConnectionQuality.POOR            // < 500 Kbps (2G)
            isCellular -> ConnectionQuality.MODERATE                     // Unknown cellular, assume moderate
            else -> ConnectionQuality.UNKNOWN
        }
    }
    
    /**
     * Check if currently on a metered connection (mobile data)
     */
    fun isMetered(): Boolean = _networkState.value.isMetered
    
    /**
     * Check if connected to WiFi
     */
    fun isWifi(): Boolean = _networkState.value.isWifi
    
    /**
     * Check if connected
     */
    fun isConnected(): Boolean = _networkState.value.isConnected
    
    /**
     * Get current connection quality
     */
    fun getConnectionQuality(): ConnectionQuality = _networkState.value.connectionQuality
    
    /**
     * Get recommended image quality based on network
     * Returns: 0 = low, 1 = medium, 2 = high
     */
    fun getRecommendedImageQuality(): Int {
        return when (_networkState.value.connectionQuality) {
            ConnectionQuality.EXCELLENT -> 2  // High quality
            ConnectionQuality.GOOD -> 1       // Medium quality
            ConnectionQuality.MODERATE -> 0   // Low quality
            ConnectionQuality.POOR -> 0       // Low quality
            ConnectionQuality.UNKNOWN -> {
                // If unknown, use metered status
                if (_networkState.value.isMetered) 0 else 1
            }
        }
    }
    
    /**
     * Get recommended video quality based on network
     * Returns: 0 = original, 1 = 720p, 2 = 480p
     */
    fun getRecommendedVideoQuality(): Int {
        return when (_networkState.value.connectionQuality) {
            ConnectionQuality.EXCELLENT -> 0  // Original
            ConnectionQuality.GOOD -> 1       // 720p
            ConnectionQuality.MODERATE -> 2   // 480p
            ConnectionQuality.POOR -> 2       // 480p
            ConnectionQuality.UNKNOWN -> {
                if (_networkState.value.isMetered) 2 else 1
            }
        }
    }
    
    /**
     * Cleanup - call from Application.onTerminate() if needed
     */
    fun release() {
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
        networkCallback = null
    }
}
