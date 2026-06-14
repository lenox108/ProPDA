package forpdateam.ru.forpda.common

import android.content.Context
import android.os.Build
import android.preference.PreferenceManager
import java.util.Locale

/**
 * Created by radiationx on 09.09.17.
 */
/* Original http://gunhansancar.com/change-language-programmatically-in-android/ */
object LocaleHelper {
    const val SELECTED_LANGUAGE = "Locale.Helper.Selected.Language"
    private const val DEFAULT_LANGUAGE = "default"

    @JvmStatic
    fun onAttach(context: Context): Context =
        setLocale(context, getPersistedData(context, DEFAULT_LANGUAGE), persist = false)

    @JvmStatic
    fun onAttach(context: Context, defaultLanguage: String): Context =
        setLocale(context, getPersistedData(context, defaultLanguage), persist = false)

    @JvmStatic
    fun getLanguage(context: Context): String =
        getPersistedData(context, DEFAULT_LANGUAGE)

    @JvmStatic
    fun setLocale(context: Context, language: String): Context =
        setLocale(context, language, persist = true)

    private fun setLocale(context: Context, language: String, persist: Boolean): Context {
        if (persist) {
            persist(context, language)
        }
        if (language == DEFAULT_LANGUAGE) {
            return context
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            updateResources(context, language)
        } else {
            updateResourcesLegacy(context, language)
        }
    }

    private fun getPersistedData(context: Context, defaultLanguage: String): String =
        PreferenceManager.getDefaultSharedPreferences(context).getString(SELECTED_LANGUAGE, defaultLanguage) ?: defaultLanguage

    private fun persist(context: Context, language: String) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(SELECTED_LANGUAGE, language).apply()
    }

    private fun updateResources(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)
        val configuration = context.resources.configuration
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }

    private fun updateResourcesLegacy(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)
        val resources = context.resources
        val configuration = resources.configuration
        configuration.locale = locale
        configuration.setLayoutDirection(locale)
        resources.updateConfiguration(configuration, resources.displayMetrics)
        return context
    }
}
