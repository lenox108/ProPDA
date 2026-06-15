package forpdateam.ru.forpda.ui

import android.content.Context
import biz.source_code.miniTemplator.MiniTemplator
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

/**
 * Asset loading + caching for the WebView templates used by
 * [TemplateManager]. Extracted from [TemplateManager] as part of
 * the god-class decomposition (§1.1 of REFACTOR_PLAN.md).
 *
 * Templates are loaded from `assets/template_<name>.html` exactly
 * once and cached in-memory; the previous TemplateManager did the
 * same, this class only owns that responsibility now.
 */
class TemplateAssetLoader(context: Context) {

    private val assets = context.assets
    private val templates = mutableMapOf<String, MiniTemplator>()

    /**
     * Returns a previously-loaded [MiniTemplator] for the given
     * [name] (e.g. "theme", "news", "qms_chat"), loading and
     * caching it on first access. On error, returns a placeholder
     * template containing "Template error!" so the WebView still
     * has something to render.
     */
    fun getTemplate(name: String): MiniTemplator = templates[name]
            ?: findTemplate(name).apply { templates[name] = this }

    private fun findTemplate(name: String): MiniTemplator = try {
        val stream = assets.open("template_$name.html")
        MiniTemplator.Builder().build(stream, Charset.forName("utf-8"))
    } catch (ex: Exception) {
        Timber.e(ex, "Template load error")
        MiniTemplator.Builder().build(
                ByteArrayInputStream("Template error!".toByteArray(Charset.forName("utf-8"))),
                Charset.forName("utf-8")
        )
    }
}
