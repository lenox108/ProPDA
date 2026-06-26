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
        // Strip CSS comments before extracting the rule body so that the
        // explanatory comments above the rule (which intentionally mention
        // historical decisions like the removed `color-mix` tint by name) do
        // not trigger the assertions about the rule's *declarations*.
        val cssNoComments = css.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        // The rule must carry a visible declaration (box-shadow ring) — not
        // merely declare the selector.
        val ruleBody = cssNoComments.substringAfter("$selector {", "")
                .substringBefore("}")
                .lowercase()
        assertTrue(
                "$cssRelativePath: $selector must apply a visible box-shadow: $ruleBody",
                ruleBody.contains("box-shadow"),
        )
        // Several theme override modes (sepia/minimal/amoled, see
        // TemplateCssComposer) declare `.post_container { box-shadow: ... !important }`.
        // Without `!important` on the highlight, that override wins the cascade
        // regardless of specificity and the highlight is invisible. Pin it.
        assertTrue(
                "$cssRelativePath: $selector must use !important to beat theme overrides: $ruleBody",
                ruleBody.contains("!important"),
        )
        // Inset box-shadow only — the post wrapper has `overflow:hidden` +
        // `border-radius`, so an `outline` (rectangular, drawn outside the
        // box) gets clipped at the rounded corners and at the horizontal
        // edges. Pin that no outline leaks into the rule.
        assertTrue(
                "$cssRelativePath: $selector must NOT use outline (it gets clipped by the post's overflow:hidden + border-radius): $ruleBody",
                !ruleBody.contains("outline:"),
        )
        // The accent must be resolved from a CSS variable (palette-aware),
        // not a single hard-coded RGB. Without this, the ring colour is fixed
        // regardless of sepia / amoled / minimal / light / dark mode.
        assertTrue(
                "$cssRelativePath: $selector must use var(--ppda-accent, var(--surface-accent, ...)) so the colour adapts to the active palette: $ruleBody",
                ruleBody.contains("--ppda-accent"),
        )
        // Box-shadow MUST be `inset` — we want a ring drawn INSIDE the post
        // border-radius, not a drop-shadow outside (an outer drop-shadow would
        // also escape the rounded corners).
        assertTrue(
                "$cssRelativePath: $selector box-shadow must be inset (so the ring is drawn inside the post's border-radius, not outside it): $ruleBody",
                ruleBody.contains("inset"),
        )
        // CRITICAL — the highlight must NOT set a `background` declaration. The
        // earlier design used the `color-mix()` CSS function to tint the post
        // card, but users reported it as "the whole post flashes" (the tint
        // recoloured the entire post body, not just the ring). Removing the
        // background tint also removes the `color-mix()` dependency that
        // broke on Android WebView builds predating Chromium 111 — those
        // discarded the entire declaration and left the post card with no
        // visible highlight at all. The ring is now a pure box-shadow only;
        // the post keeps its base background from the surrounding CSS.
        assertTrue(
                "$cssRelativePath: $selector must NOT set background (whole-card tint is the 'whole block flashes' regression): $ruleBody",
                !ruleBody.contains("background:"),
        )
        // The CSS function call `color-mix(` must not appear inside the
        // rule's declarations. (The literal word may legitimately appear in
        // the surrounding comments — the rule-body substring checked here
        // already has CSS comments stripped above.)
        assertTrue(
                "$cssRelativePath: $selector must NOT use the color-mix() CSS function (no background tint anymore; the ring is box-shadow only): $ruleBody",
                !ruleBody.contains("color-mix("),
        )
        // The VISIBLE 4-sided contour is now drawn by a transparent-fill
        // bordered `::before` pseudo-element. A 1px palette `.post_container {
        // box-shadow: inset 0 0 0 1px <border> !important }` rule
        // (sepia/sepia-blue/amoled) kept WINNING the box-shadow property on
        // device (log: boxShadow=[rgb(53,65,72) 0 0 0 1px inset, ...]), so the
        // box-shadow ring was invisible AND showed only top/bottom on full-bleed
        // posts. `border` on `::before` is a different property/box that no
        // palette box-shadow can override, and `inset:0 + border` draws on all
        // four sides inside the wrapper's overflow:hidden. Pin it.
        assertPseudoRingRule(cssRelativePath, cssNoComments)
    }

    private fun assertPseudoRingRule(cssRelativePath: String, cssNoComments: String) {
        val pseudoSelector = ".post_container.ppda_highlight_post::before"
        assertTrue(
                "$cssRelativePath must contain a transparent bordered ring rule for $pseudoSelector",
                cssNoComments.contains(pseudoSelector),
        )
        val ringBody = cssNoComments.substringAfter("$pseudoSelector {", "")
                .substringBefore("}")
                .lowercase()
        // The ring contour is a `border` (NOT box-shadow): a different property
        // than the palette `.post_container { box-shadow ... }` so it cannot be
        // overridden by it.
        assertTrue(
                "$cssRelativePath: $pseudoSelector must draw the contour with a border: $ringBody",
                ringBody.contains("border:") || ringBody.contains("border "),
        )
        // Colour must be palette-adaptive via --ppda-accent.
        assertTrue(
                "$cssRelativePath: $pseudoSelector border colour must use var(--ppda-accent, ...) so it adapts per palette: $ringBody",
                ringBody.contains("--ppda-accent"),
        )
        // inset:0 (left/top/right/bottom: 0) keeps the ring inside the wrapper
        // so overflow:hidden never clips the left/right sides.
        assertTrue(
                "$cssRelativePath: $pseudoSelector must be positioned with left/top/right/bottom 0 (so all 4 sides are inside overflow:hidden): $ringBody",
                ringBody.contains("left: 0") && ringBody.contains("right: 0") &&
                        ringBody.contains("top: 0") && ringBody.contains("bottom: 0"),
        )
        // Transparent fill — explicitly NOT the legacy black/opacity overlay
        // flash. The ring communicates highlight via its border only.
        assertTrue(
                "$cssRelativePath: $pseudoSelector must have a transparent fill (no block tint / legacy flash): $ringBody",
                ringBody.contains("background: transparent"),
        )
        assertTrue(
                "$cssRelativePath: $pseudoSelector must NOT use the color-mix() CSS function: $ringBody",
                !ringBody.contains("color-mix("),
        )
    }
}
