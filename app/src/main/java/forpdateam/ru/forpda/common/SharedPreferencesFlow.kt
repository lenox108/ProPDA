package forpdateam.ru.forpda.common

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart

/**
 * Реактивное наблюдение за ключом [SharedPreferences] через корутинный [Flow].
 * Заменяет использование RxSharedPreferences для получения потока значений.
 *
 * [loader] должен читать текущее значение по ключу.
 */
private fun <T> SharedPreferences.observeKey(
    key: String,
    loader: SharedPreferences.() -> T
): Flow<T> = callbackFlow {
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
        if (changedKey == key) {
            trySend(loader())
        }
    }
    registerOnSharedPreferenceChangeListener(listener)
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}.onStart { emit(loader()) }.distinctUntilChanged()

fun SharedPreferences.booleanFlow(key: String, default: Boolean): Flow<Boolean> =
    observeKey(key) { getBoolean(key, default) }

fun SharedPreferences.stringFlow(key: String, default: String): Flow<String> =
    observeKey(key) { getString(key, default) ?: default }

fun SharedPreferences.longFlow(key: String, default: Long): Flow<Long> =
    observeKey(key) { getLong(key, default) }

fun SharedPreferences.intFlow(key: String, default: Int): Flow<Int> =
    observeKey(key) { getInt(key, default) }
