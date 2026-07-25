package forpdateam.ru.forpda.model.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Type-preserving snapshot used by the portable settings backup.
 *
 * DataStore intentionally has no generic import/export API, so values are restored through
 * keys reconstructed from the value type. Preferences DataStore supports exactly these types.
 */
internal suspend fun DataStore<Preferences>.exportBackupValues(
    excludedKeys: Set<String> = emptySet(),
): Map<String, Any> = data.first().asMap()
    .filterKeys { it.name !in excludedKeys }
    .mapKeys { it.key.name }

internal suspend fun DataStore<Preferences>.restoreBackupValues(
    values: Map<String, Any>,
    preservedKeys: Set<String> = emptySet(),
) {
    edit { preferences ->
        val preserved = preferences.asMap()
            .filterKeys { it.name in preservedKeys }
            .mapKeys { it.key.name }
        preferences.clear()
        (values + preserved).forEach { (name, value) ->
            when (value) {
                is String -> preferences[stringPreferencesKey(name)] = value
                is Boolean -> preferences[booleanPreferencesKey(name)] = value
                is Int -> preferences[intPreferencesKey(name)] = value
                is Long -> preferences[longPreferencesKey(name)] = value
                is Float -> preferences[floatPreferencesKey(name)] = value
                is Double -> preferences[doublePreferencesKey(name)] = value
                is Set<*> -> preferences[stringSetPreferencesKey(name)] =
                    value.filterIsInstance<String>().toSet()
                else -> error("Unsupported DataStore value for key $name: ${value::class.java.name}")
            }
        }
    }
}
