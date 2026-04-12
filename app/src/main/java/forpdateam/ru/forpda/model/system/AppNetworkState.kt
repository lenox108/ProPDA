package forpdateam.ru.forpda.model.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import forpdateam.ru.forpda.model.NetworkStateProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Created by radiationx on 10.02.18.
 */
class AppNetworkState(
        private val context: Context
) : NetworkStateProvider {

    private fun getLocalState(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private val _state = MutableStateFlow(getLocalState())

    override fun observeState(): Flow<Boolean> = _state.asStateFlow()

    override fun getState(): Boolean {
        val result = getLocalState()
        if (result != _state.value) {
            _state.value = result
        }
        return _state.value
    }

    override fun setState(state: Boolean) {
        _state.update { current ->
            if (current == state) current else state
        }
    }
}
