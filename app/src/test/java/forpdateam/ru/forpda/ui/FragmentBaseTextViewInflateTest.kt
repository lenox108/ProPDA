package forpdateam.ru.forpda.ui

import android.app.Activity
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import androidx.test.core.app.ApplicationProvider
import forpdateam.ru.forpda.R
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression test for the prod `fragment_base.xml:118` TextView InflateException.
 *
 * Background. The crash signature in production is:
 *   TextView.readTextAppearance -> TypedArray.getColorStateList -> UnsupportedOperationException
 *   ("Failed to resolve attribute at index N: TypedValue{t=0x2/d=0x...}")
 * The inheritance chain eventually falls through to `Theme.AppCompat.Empty`,
 * which has no concrete value for the attribute being read.
 *
 * Previous commit 9428b01 replaced upstream `HarmonizedColors.applyToContextIfAvailable`
 * with a custom `ThemeOverlay.ForPDA.HarmonizedError` (parent = MaterialYouAccent),
 * but the crash still reproduced on real devices because the actual root cause
 * was different: layout XML uses `?attr/default_text_color` /
 * `?attr/second_text_color` directly (e.g. fragment_base.xml:118 toolbar_title),
 * and `MaterialYouSurface` was remapping those to `?attr/colorOnSurface` /
 * `?attr/colorOnSurfaceVariant` (TYPE_ATTRIBUTE), which `TypedArray.getColorStateList`
 * cannot dereference through (only 1 level of resolution; throws on the second
 * `?attr/...` step).
 *
 * The same crash shape also fires for M3 widgets (CollapsingToolbarLayout) that
 * read `android:textColor` from `?android:attr/textColorPrimary` in the resolved
 * TextAppearance style.
 *
 * `default_text_color`/`second_text_color` (the two attrs the original crash
 * above was about) are ALSO fully retired now, by the same `concurrent-dreaming-
 * wren` Этап C migration: every leaf consumer (layout/code) was repointed at
 * `?attr/colorOnSurface`/`?attr/colorOnSurfaceVariant` directly, and every
 * remaining internal style-cascade reference (TabLayout, PopupMenu, PopupOverlay,
 * RadioButton, MyTextViewStyle, MessagePanelAdvancedTabText, smart_nav_fab_icon)
 * was repointed at the same framework roles (or, where the target itself IS a
 * framework role being set inside a separately-applied overlay like
 * `PopupOverlay`, at the literal `@color/...` token) — never left as a
 * `custom_attr` → `?attr/m3role` redirect, which would reintroduce this exact
 * crash class. `icon_toolbar` is the one exception: its leaf consumers were
 * migrated the same way, but the attr itself stays alive — it has ~30 internal
 * cascade references (ToolbarStyle, AppBarOverlay, ActionModeStyle, …) and an
 * explicit prod-verified warning in `styles.xml` against chaining it further
 * (CollapsingToolbarLayout inflate crash), so it was deliberately left as-is.
 *
 * Fix (invariant): when Material You is enabled, EVERY custom slot (those NOT
 * named after a framework Material 3 role) MUST resolve to a CONCRETE
 * `@color/...` in the runtime theme — never to a dangling `?attr/...`
 * (TYPE_ATTRIBUTE). This holds via two complementary sources:
 *   - the framework `textColor*` trio is pinned CONCRETE directly in
 *     `MaterialYouSurface`.
 *   - All the other chrome/typography slots still left as custom attrs
 *     (`background_for_lists`, `chrome_plane_background`, `contrast_text_color`,
 *     `icon_base`, `icon_toolbar`,
 *     `status_bar_color`) are NOT set by the overlay — they inherit the same
 *     CONCRETE `@color/light_*` / `@color/dark_*` values from the base
 *     `DayNightAppTheme`.
 * Either way, none of them may end up TYPE_ATTRIBUTE — this test asserts that
 * on the assembled activity theme, which guards BOTH the overlay pins AND the
 * base fallthrough.
 *
 * Only the framework Material-3 role slots are KEPT as `?attr/...`:
 * `android:colorBackground` → `colorSurface`, `colorOnBackground` →
 * `colorOnSurface`. The AOSP inflater resolves framework attrs directly,
 * so those don't go through the broken 2-level TypedArray path. As part of
 * the THEMES_OVERHAUL consumer-side migration (`concurrent-dreaming-wren`
 * plan, Этап C), custom slots are being retired one at a time: their XML/code
 * CONSUMERS are repointed directly at the matching M3 role (e.g.
 * `?attr/divider_line` → `?attr/colorOutlineVariant`, `?attr/cards_background`
 * → `?attr/colorSurface`) and the custom attr is then deleted from
 * `attrs.xml`/the theme files instead of being kept as a redirect — a
 * redirect (`custom_attr` → `?attr/m3role`) would reintroduce the exact
 * 2-level TYPE_ATTRIBUTE crash this test guards against. Retired slots are
 * removed from the invariant lists below as they're migrated.
 *
 * Trade-off: the static slots lose dynamic M3 wallpaper tracking. M3
 * framework roles (`colorPrimary`, `colorSurface`, `colorOnSurface`, …)
 * still track the wallpaper via the upstream `DynamicColors.Light` overlay
 * from MaterialYouApplier; the loss only affects our custom chrome slots
 * that haven't been migrated yet.
 *
 * The first test below is the canonical regression test: it actually inflates
 * fragment_base.xml against the production theme chain and asserts no
 * InflateException is thrown. The second test is the JUnit fallback the
 * reviewer requested: it verifies that the override slots resolve to concrete
 * color ints (not TYPE_ATTRIBUTE), which is the shape that would crash
 * TypedArray.getColorStateList in production.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class FragmentBaseTextViewInflateTest {

    /**
     * Mirrors the production MainActivity.onCreate flow:
     *  1. setTheme(DayNightAppTheme_NoActionBar)
     *  2. MaterialYouApplier.applyIfEnabled (with MY enabled, palette SYSTEM)
     *  3. super.onCreate
     *
     * The fragment_base.xml layout is then inflated against this activity theme.
     */
    class ThemedActivity : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.DayNightAppTheme_NoActionBar)
            MaterialYouApplier.applyIfEnabled(this)
            super.onCreate(savedInstanceState)
        }
    }

    private fun writeMaterialYouEnabled(enabled: Boolean) {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getSharedPreferences("main_mirror", android.content.Context.MODE_PRIVATE)
                .edit()
                .putBoolean("use_material_you", enabled)
                .apply()
    }

    private fun writeUiPalette(palette: String) {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getSharedPreferences("main_mirror", android.content.Context.MODE_PRIVATE)
                .edit()
                .putString("ui_palette", palette)
                .apply()
    }

    /**
     * Canonical regression test: inflates the prod layout (`fragment_base.xml`)
     * against the production activity theme chain with Material You enabled.
     *
     * Before the fix, this throws the EXACT prod InflateException:
     *   Binary XML file line #118 in layout/fragment_base: Error inflating class
     *     androidx.appcompat.widget.AppCompatTextView
     *   Caused by: java.lang.UnsupportedOperationException: Failed to resolve
     *     attribute at index 5: TypedValue{t=0x2/d=0x7f040195 a=-1}, theme=...
     * With the fix, it inflates cleanly.
     */
    @Test
    fun `fragment_base inflates without InflateException when MY is enabled`() {
        writeMaterialYouEnabled(true)
        writeUiPalette("SYSTEM")

        val activity = Robolectric.buildActivity(ThemedActivity::class.java).setup().get()
        val view = LayoutInflater.from(activity).inflate(R.layout.fragment_base, null, false)
        assertNotNull("fragment_base must inflate to a non-null view", view)
    }

    /**
     * Sanity check: the canonical Material You regression (applier chains
     * correctly) still holds. `icon_toolbar` is now CONCRETE
     * `@color/light_icon_toolbar` (was `?attr/colorOnSurface` before this
     * fix — see the hydra-crash MentionsFragment.onViewCreated →
     * Toolbar.getOverflowIcon → ActionMenuPresenter.OverflowMenuButton
     * (ImageView).&lt;init&gt; → TypedArray.getColorStateList(0x7f040272)
     * → UnsupportedOperationException).
     *
     * `icon_toolbar` deliberately does NOT track wallpaper anymore — it
     * became a static @color/... for the same reason default_text_color
     * became static in 65c64b8: TypedArray.getColorStateList cannot do
     * a 2-level `?attr/...` dereference. This test guards against any
     * regression that reintroduces TYPE_ATTRIBUTE for `icon_toolbar`.
     */
    @Test
    fun `icon_toolbar is concrete in activity theme when MY is enabled`() {
        writeMaterialYouEnabled(true)
        writeUiPalette("SYSTEM")

        val activity = Robolectric.buildActivity(ThemedActivity::class.java).setup().get()

        val ta = activity.theme.obtainStyledAttributes(intArrayOf(R.attr.icon_toolbar))
        try {
            val tv = TypedValue()
            assertTrue("?icon_toolbar must resolve", ta.getValue(0, tv))
            assertNotEquals(
                "?icon_toolbar must NOT stay as TYPE_ATTRIBUTE " +
                    "(this is the EXACT prod crash signature: " +
                    "MentionsFragment.onViewCreated → Toolbar.getOverflowIcon → " +
                    "ActionMenuPresenter\$OverflowMenuButton (ImageView).<init> → " +
                    "TypedArray.getColorStateList(0x7f040272) on TYPE_ATTRIBUTE → " +
                    "UnsupportedOperationException). Got type=${tv.type}, " +
                    "data=0x${Integer.toHexString(tv.data)}",
                TypedValue.TYPE_ATTRIBUTE, tv.type
            )
        } finally {
            ta.recycle()
        }
    }

    /**
     * Verifies that the slots we made CONCRETE in MaterialYouSurface
     * (default_text_color / second_text_color / link_color / framework
     * textColor*) are now TYPE_INT_COLOR or TYPE_REFERENCE — never
     * TYPE_ATTRIBUTE. This is the shape that TypedArray.getColorStateList
     * requires: it throws UnsupportedOperationException on TYPE_ATTRIBUTE
     * (see AOSP TypedArray.java:600).
     *
     * Before the fix, default_text_color was `?attr/colorOnSurface` (TYPE_ATTRIBUTE)
     * and the InflateException fired on fragment_base.xml:118
     * (`android:textColor="?attr/default_text_color"`).
     *
     * IMPLEMENTATION NOTE: this test deliberately uses
     * `obtainStyledAttributes(int[])` + `TypedArray.getValue(index, outValue)`
     * rather than `theme.resolveAttribute(..., resolveRefs=true)`. The reason
     * matters for catching regressions of the 4a26ff1 class:
     *
     * - `theme.resolveAttribute(..., resolveRefs=true)` ALWAYS walks references
     *   to a concrete value (it never returns TYPE_ATTRIBUTE, see Android
     *   Resources.Theme#resolveAttribute Javadoc). That means the previous
     *   version of this test could not detect a duplicate `?attr/colorOnSurface`
     *   in `MaterialYouSurface` for `default_text_color` — Robolectric would
     *   happily walk through both levels of `?attr/...` and return a concrete
     *   color int, hiding the underlying TYPE_ATTRIBUTE that the production
     *   `TypedArray.getColorStateList` cannot dereference.
     *
     * - `obtainStyledAttributes(int[])` returns a TypedArray whose `getValue`
     *   method exposes the raw TypedValue the platform stored in the theme.
     *   This is the SAME path the real TextView.<init> takes, so the assertion
     *   here matches what production sees. If the underlying TypedValue is
     *   TYPE_ATTRIBUTE, the test will fail with a precise error pointing at
     *   the broken attribute.
     *
     * This catches a regression where `MaterialYouSurface` accidentally ends
     * up with a `?attr/colorOnSurface` (or similar) entry for one of the
     * textColor slots — aapt2 will record BOTH the concrete color and the
     * `?attr/...` reference in resources.arsc, and at runtime the platform
     * applies them in source order. The net effect is that the theme
     * resolves `?attr/default_text_color` to a TYPE_ATTRIBUTE, which crashes
     * `TypedArray.getColorStateList` on real devices but NOT in Robolectric's
     * `resolveAttribute(..., true)` path.
     */
    @Test
    fun `MY overlay slots are concrete in activity theme when MY is enabled`() {
        writeMaterialYouEnabled(true)
        writeUiPalette("SYSTEM")

        val activity = Robolectric.buildActivity(ThemedActivity::class.java).setup().get()
        val theme = activity.theme

        // Every custom attr that MaterialYouSurface (and HarmonizedError) sets
        // MUST resolve to a concrete color int (TYPE_INT_COLOR or a 1-level
        // @color reference) — never TYPE_ATTRIBUTE. Any of these attrs can be
        // read via TypedArray.getColorStateList by some widget in the framework
        // (TextView, ImageView, Toolbar, CardView, AppBarLayout,
        // CollapsingToolbarLayout, ActionMenuPresenter$OverflowMenuButton, …),
        // and each of them throws UnsupportedOperationException on
        // TYPE_ATTRIBUTE. The two M3-ROLE slots that are KEPT as ?attr/... by
        // design (android:colorBackground → colorSurface, colorOnBackground →
        // colorOnSurface) are framework attrs that the AOSP inflater resolves
        // directly, so they are excluded.
        val criticalAttrIds = intArrayOf(
                android.R.attr.textColor,
                android.R.attr.textColorPrimary,
                android.R.attr.textColorSecondary,
                android.R.attr.textColorLink,
                android.R.attr.textColorHint,
                R.attr.background_for_lists,
                R.attr.chrome_plane_background,
                R.attr.contrast_text_color,
                R.attr.icon_base,
                R.attr.icon_toolbar,
                R.attr.status_bar_color,
        )
        val criticalAttrNames = mapOf(
                android.R.attr.textColor to "android:textColor",
                android.R.attr.textColorPrimary to "android:textColorPrimary",
                android.R.attr.textColorSecondary to "android:textColorSecondary",
                android.R.attr.textColorLink to "android:textColorLink",
                android.R.attr.textColorHint to "android:textColorHint",
                R.attr.background_for_lists to "background_for_lists",
                R.attr.chrome_plane_background to "chrome_plane_background",
                R.attr.contrast_text_color to "contrast_text_color",
                R.attr.icon_base to "icon_base",
                R.attr.icon_toolbar to "icon_toolbar",
                R.attr.status_bar_color to "status_bar_color",
        )

        val unresolved = mutableListOf<String>()
        val ta = theme.obtainStyledAttributes(criticalAttrIds)
        try {
            for (i in criticalAttrIds.indices) {
                val tv = TypedValue()
                ta.getValue(i, tv)
                if (tv.type == TypedValue.TYPE_ATTRIBUTE) {
                    unresolved.add(
                            "${criticalAttrNames[criticalAttrIds[i]]}: " +
                                    "TYPE_ATTRIBUTE survived into the runtime theme " +
                                    "(data=0x${Integer.toHexString(tv.data)}); " +
                                    "TypedArray.getColorStateList will throw " +
                                    "UnsupportedOperationException. Each of these custom " +
                                    "attrs is read by some framework widget via " +
                                    "getColorStateList (TextView, ImageView, Toolbar, " +
                                    "CardView, AppBarLayout, CollapsingToolbarLayout, " +
                                    "ActionMenuPresenter\$OverflowMenuButton, …) and ALL " +
                                    "of them crash on TYPE_ATTRIBUTE. " +
                                    "Likely cause: MaterialYouSurface has a duplicate " +
                                    "?attr/... entry for this slot — aapt2 records both, " +
                                    "and the platform applies them in source order."
                    )
                }
            }
        } finally {
            ta.recycle()
        }
        assertTrue(
                "MY overlay slots that would crash TypedArray.getColorStateList: " +
                        unresolved.joinToString("; "),
                unresolved.isEmpty()
        )
    }

    /**
     * Second-line guard: inspects the SOURCE XML of
     * `ThemeOverlay.ForPDA.MaterialYouSurface` (both day and night) to ensure
     * NO custom slot is defined as a `?attr/...` reference. The set below is
     * the list of custom (non-M3-role) slots that MUST NEVER be `?attr/...` in
     * MaterialYouSurface — every one of them is read via
     * `TypedArray.getColorStateList` by some framework widget (TextView,
     * ImageView, Toolbar, CardView, AppBarLayout, CollapsingToolbarLayout,
     * ActionMenuPresenter.OverflowMenuButton, …), so each one MUST be a
     * concrete `@color/...`. NB: most of these are no longer SET by the overlay
     * at all — they inherit concrete `@color/...` from the base
     * `DayNightAppTheme` (the no-op duplicates were removed). The guard is kept
     * over the FULL set so that re-introducing any of them as a `?attr/...`
     * entry (the recurring hydra bug) fails the build immediately. M3-role
     * attrs (`colorPrimary`, `colorOnSurface`, `colorSurface`,
     * `android:colorBackground`, `colorOnBackground`, …) are deliberately OUT
     * of this list — the AOSP inflater resolves those directly via the framework.
     *
     * This is a SOURCE-LEVEL assertion (it walks the on-disk XML, not the
     * compiled resources.arsc) so the build fails fast if anyone
     * reintroduces a `?attr/colorOnSurface` (or similar) entry for any of
     * these slots — the EXACT hydra bug pattern that recurs on different
     * attrs after each partial fix (see 7f1de68 → 4a26ff1 → 65c64b8 →
     * this commit for icon_toolbar/0x7f040272 on
     * ActionMenuPresenter.OverflowMenuButton).
     */
    @Test
    fun `MaterialYouSurface source XML has no broken textColor slot references`() {
        val forbidden = setOf(
            "background_for_lists",
            "chrome_plane_background",
            "contrast_text_color",
            "icon_base",
            "icon_toolbar",
            "status_bar_color",
        )
        // The test JVM's CWD is `app/` when running under gradle :app:test*, so
        // we need to look up the project root via the user.dir system property
        // (which is set by the JVM launcher to the project root, NOT the
        // gradle CWD) — or walk up from any test resource we know exists.
        val projectRoot = locateProjectRoot()
        val sources = listOf(
                "app/src/main/res/values/styles.xml" to "ThemeOverlay.ForPDA.MaterialYouSurface",
                "app/src/main/res/values-night/styles.xml" to "ThemeOverlay.ForPDA.MaterialYouSurface"
        )
        val badEntries = mutableListOf<String>()
        for ((relPath, styleName) in sources) {
            val file = java.io.File(projectRoot, relPath)
            if (!file.exists()) {
                badEntries.add("${file.absolutePath}: file not found")
                continue
            }
            val text = file.readText()
            val idx = text.indexOf("name=\"$styleName\"")
            if (idx < 0) {
                badEntries.add("$relPath: style $styleName not found")
                continue
            }
            val endIdx = text.indexOf("</style>", idx)
            if (endIdx < 0) {
                badEntries.add("$relPath: style $styleName has no </style>")
                continue
            }
            val block = text.substring(idx, endIdx)
            for (attrName in forbidden) {
                val pattern = Regex("name=\"$attrName\"[^>]*>\\s*\\?attr/[^<]+")
                val match = pattern.find(block)
                if (match != null) {
                    val matchedText = match.value.replace("\n", " ").replace("\\s+".toRegex(), " ")
                    badEntries.add("$relPath: $styleName has '$matchedText' — custom slots must be CONCRETE @color/... (not ?attr/...). TypedArray.getColorStateList cannot do a 2-level ?attr deref and throws UnsupportedOperationException. Each of these slots is read by some framework widget (TextView, ImageView, Toolbar, CardView, AppBarLayout, CollapsingToolbarLayout, ActionMenuPresenter\$OverflowMenuButton, …)")
                }
            }
        }
        assertTrue(
                "MaterialYouSurface must not have any custom slot defined as a ?attr/... reference: " +
                        badEntries.joinToString("; "),
                badEntries.isEmpty()
        )
    }

    /**
     * Locates the project root by walking up from the JVM's user.dir until we
     * find `app/src/main/res/values/styles.xml`. The JVM's user.dir is set
     * to the project root by gradle for the test task, but the gradle WORKING
     * directory (the CWD of the gradle process) is `app/` for `:app:test*`
     * tasks. This helper works in both cases.
     */
    private fun locateProjectRoot(): java.io.File {
        val userDir = System.getProperty("user.dir") ?: "."
        val candidates = listOf(
                java.io.File(userDir),
                java.io.File("."),
                java.io.File(".."),
                java.io.File("../.."),
                java.io.File("../../..")
        )
        for (cand in candidates) {
            try {
                val probe = java.io.File(cand, "app/src/main/res/values/styles.xml")
                if (probe.exists()) return cand.canonicalFile
            } catch (_: Exception) {}
        }
        return java.io.File(".").canonicalFile
    }
}
