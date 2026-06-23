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
 * Fix: in `MaterialYouSurface` (and `MaterialYouSurface` night), override
 * `android:textColor*` and our `default_text_color` / `second_text_color` /
 * `link_color` to CONCRETE `@color/...` references. This loses dynamic M3
 * tracking of those particular slots (TypedArray.getColorStateList only does
 * 1 level of deref; the M3 dynamic chain has 3 levels), but eliminates the
 * InflateException. The remaining M3 dynamic surfaces (backgrounds, accents,
 * toolbar, cards) still track the wallpaper via M3 dynamic roles.
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
     * correctly, icon_toolbar tracks M3 dynamic color) still holds.
     * This is the existing test in `MaterialYouApplierTest` re-asserted here
     * to ensure the MaterialYouSurface fix didn't accidentally break the
     * dynamic-tracking contract for `icon_toolbar` (which is still set to
     * `?attr/colorOnSurface` in the overlay — only the framework textColor
     * slots and our own default_text_color / second_text_color / link_color
     * are made concrete).
     */
    @Test
    fun `icon_toolbar still tracks M3 colorOnSurface when MY is enabled`() {
        writeMaterialYouEnabled(true)
        writeUiPalette("SYSTEM")

        val activity = Robolectric.buildActivity(ThemedActivity::class.java).setup().get()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val baseline = ctx.resources.getColor(R.color.light_icon_toolbar, ctx.theme)

        val tv = TypedValue()
        val resolved = activity.theme.resolveAttribute(
                R.attr.icon_toolbar, tv, /* resolveRefs = */ true
        )
        assertTrue("?icon_toolbar must resolve on the activity theme", resolved)
        assertNotEquals(
                "?icon_toolbar must NOT stay as TYPE_ATTRIBUTE",
                TypedValue.TYPE_ATTRIBUTE, tv.type
        )
        assertNotEquals(
                "?icon_toolbar must differ from the static @color/light_icon_toolbar — applier likely not wired up (got #${Integer.toHexString(tv.data)} vs baseline #${Integer.toHexString(baseline)})",
                baseline, tv.data
        )
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

        val criticalAttrIds = intArrayOf(
                R.attr.default_text_color,
                R.attr.second_text_color,
                R.attr.link_color,
                android.R.attr.textColor,
                android.R.attr.textColorPrimary,
                android.R.attr.textColorSecondary,
                android.R.attr.textColorLink,
                android.R.attr.textColorHint,
        )
        val criticalAttrNames = mapOf(
                R.attr.default_text_color to "default_text_color",
                R.attr.second_text_color to "second_text_color",
                R.attr.link_color to "link_color",
                android.R.attr.textColor to "android:textColor",
                android.R.attr.textColorPrimary to "android:textColorPrimary",
                android.R.attr.textColorSecondary to "android:textColorSecondary",
                android.R.attr.textColorLink to "android:textColorLink",
                android.R.attr.textColorHint to "android:textColorHint",
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
                                    "UnsupportedOperationException at fragment_base.xml:118 " +
                                    "(toolbar_title) and at any M3 widget that reads " +
                                    "android:textColor from a TextAppearance style. " +
                                    "This is the EXACT prod crash signature " +
                                    "(d=0x7f040195 for default_text_color). " +
                                    "Likely cause: MaterialYouSurface has a duplicate " +
                                    "?attr/colorOnSurface* entry for this slot — " +
                                    "aapt2 records both, and the platform applies them in " +
                                    "source order, so the later entry (which can be a " +
                                    "?attr/... reference) wins in the runtime theme."
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
     * NO slot is defined as a `?attr/...` reference where the slot is one of
     * the textColor slots that the prod crash touches.
     *
     * This is a SOURCE-LEVEL assertion (it walks the on-disk XML, not the
     * compiled resources.arsc), and runs as a `@Test` so the build fails
     * with a precise error if anyone reintroduces a `?attr/colorOnSurface`
     * entry for one of the textColor slots — the exact bug that 4a26ff1
     * tried to fix but shipped with a duplicate `?attr/colorOnSurface`
     * entry that the source XML still contained. The `obtainStyledAttributes`
     * test above catches the bug at runtime, this test catches it at the
     * source so the regression cannot be reintroduced.
     */
    @Test
    fun `MaterialYouSurface source XML has no broken textColor slot references`() {
        val forbidden = setOf("default_text_color", "second_text_color", "link_color")
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
                    badEntries.add("$relPath: $styleName has '$matchedText' — textColor slots must be CONCRETE @color/... (not ?attr/colorOnSurface*), otherwise TypedArray.getColorStateList will throw UnsupportedOperationException at fragment_base.xml:118 with d=0x7f040195")
                }
            }
        }
        assertTrue(
                "MaterialYouSurface must not have any textColor slot defined as a ?attr/... reference: " +
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
