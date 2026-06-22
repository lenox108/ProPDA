package forpdateam.ru.forpda.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import forpdateam.ru.forpda.model.NetworkStateProvider

/**
 * Отслеживание сети через [ConnectivityManager.NetworkCallback] вместо устаревшего CONNECTIVITY_ACTION.
 *
 * При смене/потере сети опционально дёргает [onNetworkLost] — например, чтобы сбросить
 * DNS-кеш (см. [forpdateam.ru.forpda.client.CachedDns.clearCache]). Без этого после
 * Wi-Fi → mobile приложение до 30 секунд долбится в мёртвые адреса.
 */
class NetworkConnectivityTracker(
        context: Context,
        private val networkState: NetworkStateProvider,
        private val onNetworkLost: () -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            pushState()
        }

        override fun onLost(network: Network) {
            pushState()
            runCatching { onNetworkLost() }
                .onFailure { android.util.Log.w("NetworkTracker", "onNetworkLost hook failed", it) }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            pushState()
        }
    }

    fun start() {
        pushState()
        cm.registerDefaultNetworkCallback(callback)
    }

    fun stop() {
        runCatching { cm.unregisterNetworkCallback(callback) }
    }

    private fun pushState() {
        networkState.setState(isConnected())
    }

    private fun isConnected(): Boolean {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
