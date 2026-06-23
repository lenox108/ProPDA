package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Tripwire for the JS-class ↔ CSS-rule contract behind the topic
 * "where I stopped" highlight.
 *
 * Root cause of the visual regression (device log 23_06: native chain emits
 * `js_highlight_applied postAnchorExists=true`, yet nothing is visible):
 * `app/src/main/assets/template_theme.html` adds the class
 * `ppda_highlight_post` to the matched `.post_container`, but NO shipped CSS
 * file defined a rule for that class — so the class was visually a no-op.
 *
 * The `*_themes.css` files are linked directly from the template
 * (`file:///android_asset/forpda/styles/<type>/<type>_themes.css`) and are NOT
 * regenerated from LESS at build time, so the `.css` is the source of truth at
 * runtime. This test pins:
 *  1. the JS still toggles `ppda_highlight_post` / `ppda_highlight_fading`;
 *  2. both the light and dark shipped CSS define a visible rule for the base
 *     highlight class (so the class can never silently become a no-op again);
 *  3. the native fadeout bridge signature `(generationId, delayMs)` matches the
 *     JS function definition.
 *
 * If any of these break, the on-device highlight goes invisible — exactly the
 * regression this guards against.
 */
class TopicHighlightCssContractTest {

    private fun asset(relative: String): String {
        val path = listOf(
                Path.of("src/main/assets/$relative"),
                Path.of("app/src/main/assets/$relative"),
        ).first { Files.exists(it) }
        return Files.newInputStream(path).bufferedReader().readText()
    }

    @Test
    fun template_togglesHighlightClasses() {
        val template = asset("template_theme.html")
        assertTrue(
                "JS must add the base highlight class to the post wrapper",
                template.contains("classList.add(\"ppda_highlight_post\")"),
        )
        assertTrue(
                "JS must add the fading class when the visible window elapses",
                template.contains("classList.add(\"ppda_highlight_fading\")"),
        )
    }

    @Test
    fun template_fadeoutCallSignatureMatchesNativeContract() {
        val template = asset("template_theme.html")
        // Native (ThemeJsApi.scheduleHighlightFadeout) calls
        // window.PPDA_scheduleHighlightFadeout(generationId, delayMs). The JS
        // definition MUST accept (generationId, delayMs) — not the legacy
        // (postId, type, generationId) — or the native call silently no-ops.
        assertTrue(
                "JS fadeout must accept (generationId, delayMs): $template",
                template.contains("PPDA_scheduleHighlightFadeout = function (generationId, delayMs)"),
        )
    }

    @Test
    fun lightThemeCss_definesVisibleHighlightRule() {
        assertVisibleHighlightRule("forpda/styles/light/light_themes.css")
    }

    @Test
    fun darkThemeCss_definesVisibleHighlightRule() {
        assertVisibleHighlightRule("forpda/styles/dark/dark_themes.css")
    }

    private fun assertVisibleHighlightRule(cssRelativePath: String) {
        val css = asset(cssRelativePath)
        val selector = ".post_container.ppda_highlight_post"
        assertTrue(
                "$cssRelativePath must contain a rule for $selector",
                css.contains(selector),
        )
        // The rule must carry a visible declaration (box-shadow ring or
        // background tint) — not merely declare the selector.
        val ruleBody = css.substringAfter("$selector {", "")
                .substringBefore("}")
                .lowercase()
        assertTrue(
                "$cssRelativePath: $selector must apply a visible box-shadow/background: $ruleBody",
                ruleBody.contains("box-shadow") || ruleBody.contains("background"),
        )
    }
}
