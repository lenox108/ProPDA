package forpdateam.ru.forpda.ui

import android.content.Context
import biz.source_code.miniTemplator.MiniTemplator
import forpdateam.ru.forpda.common.DayNightHelper
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Composes the HTML shell for rendered articles, themes, QMS
 * messages, etc. Delegates CSS composition to [TemplateCssComposer]
 * and asset loading to [TemplateAssetLoader]; only orchestration
 * and the palette query delegation live here.
 *
 * Extracted from a single god-class as part of §1.1 of
 * REFACTOR_PLAN.md. Public API is preserved — the previous
 * direct method bodies are now thin facades.
 */
class TemplateManager(
        private val context: Context,
        private val dayNightHelper: DayNightHelper,
        private val mainPreferencesHolder: MainPreferencesHolder
) {

    companion object {
        const val TEMPLATE_THEME = "theme"
        const val TEMPLATE_SEARCH = "search"
        const val TEMPLATE_QMS_CHAT = "qms_chat"
        const val TEMPLATE_QMS_CHAT_MESS = "qms_chat_mess"
        const val TEMPLATE_NEWS = "news"
        const val TEMPLATE_FORUM_RULES = "forum_rules"
        const val TEMPLATE_ANNOUNCE = "announce"
    }

    private val staticStrings = mutableMapOf<String, String>()
    private val paletteResolver = TemplatePaletteResolver(mainPreferencesHolder, dayNightHelper)
    private val assetLoader = TemplateAssetLoader(context)
    private val cssComposer = TemplateCssComposer(mainPreferencesHolder, dayNightHelper, paletteResolver)

    fun setStaticStrings(strings: Map<String, String>) {
        staticStrings.clear()
        staticStrings.putAll(strings)
    }

    fun getStaticString(key: String): String? = staticStrings[key]

    fun observeThemeTypeFlow(): Flow<String> = dayNightHelper
            .isNightFlow
            .map { if (it) "dark" else "light" }
            .distinctUntilChanged()

    fun getThemeType(): String {
        return if (dayNightHelper.isNight()) "dark" else "light"
    }

    fun isSepiaReading(): Boolean = paletteResolver.isSepiaReading()

    fun isSepiaBlue(): Boolean = paletteResolver.isSepiaBlue()

    fun isMinimalReader(): Boolean = paletteResolver.isMinimalReader()

    fun isAmoled(): Boolean = paletteResolver.isAmoled()

    /**
     * Inline CSS overrides for WebView templates. Composed by
     * [TemplateCssComposer] and applied on top of the base CSS
     * files; icon fonts are left untouched.
     */
    fun getThemeOverridesCss(): String = cssComposer.compose()

    fun fillStaticStrings(template: MiniTemplator): MiniTemplator = template.apply {
        variables.forEach { entry ->
            staticStrings[entry.key]?.let {
                setVariable(entry.key, it)
            }
        }
    }

    fun getTemplate(name: String): MiniTemplator = assetLoader.getTemplate(name)
}
