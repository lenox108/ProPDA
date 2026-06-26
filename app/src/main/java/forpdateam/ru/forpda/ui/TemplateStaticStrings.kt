package forpdateam.ru.forpda.ui

import biz.source_code.miniTemplator.MiniTemplator

/**
 * Owns the localized "static string" variable map that
 * [TemplateManager] injects into WebView templates.
 *
 * Extracted from [TemplateManager] as part of the god-class
 * decomposition (finding L10 of the audit roadmap), mirroring how
 * [TemplateAssetLoader] already owns the template cache. Behaviour
 * is byte-identical: the map is replaced wholesale on
 * [setStaticStrings], looked up by key on [getStaticString], and
 * applied to every declared template variable on [fillInto].
 *
 * No external dependencies — the state is the map itself.
 */
class TemplateStaticStrings {

    private val staticStrings = mutableMapOf<String, String>()

    /**
     * Replaces the entire static-string map with [strings]. The
     * previous contents are discarded, matching the original
     * clear-then-putAll semantics.
     */
    fun setStaticStrings(strings: Map<String, String>) {
        staticStrings.clear()
        staticStrings.putAll(strings)
    }

    fun getStaticString(key: String): String? = staticStrings[key]

    /**
     * Sets every template variable that has a matching static
     * string, leaving variables without a mapping untouched.
     * Returns the same [template] instance for call-site chaining.
     */
    fun fillInto(template: MiniTemplator): MiniTemplator = template.apply {
        variables.forEach { entry ->
            staticStrings[entry.key]?.let {
                setVariable(entry.key, it)
            }
        }
    }
}
