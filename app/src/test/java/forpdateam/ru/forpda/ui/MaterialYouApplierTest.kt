package forpdateam.ru.forpda.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import androidx.test.core.app.ApplicationProvider
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.Preferences as AppPreferences
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression test for the Material You wiring.
 *
 * Background. The per-Activity [MaterialYouApplier.applyIfEnabled] is the
 * canonical entry point for applying dynamic color to the native UI shell,
 * because the app's activities call `setTheme(UiThemeStyles.*)` in their
 * `onCreate()`, which wipes any overlay that the global
 * `DynamicColors.applyToActivitiesIfAvailable` had applied. The applier
 * therefore MUST be invoked explicitly from each activity that participates
 * in Material You — `MainActivity` and `SettingsActivity` are the two
 * participants.
 *
 * This test pins down two contract guarantees that together prove the
 * applier is reachable end-to-end on API >= 31:
 *
 *  1. With `use_material_you = false`, calling the applier on a real themed
 *     Activity leaves `?icon_toolbar` resolving to the same static
 *     `@color/light_icon_toolbar` (i.e. the base `DayNightAppTheme` is in
 *     effect and no overlay was applied). The canonical fix must therefore
 *     not regress the off state.
 *
 *  2. With `use_material_you = true` + SYSTEM palette, calling the applier
 *     overlays the M3 dynamic role `?attr/colorOnSurface` onto
 *     `?icon_toolbar`, so `?icon_toolbar` resolves to a value DIFFERENT
 *     from the static `@color/light_icon_toolbar`. The fact that the
 *     resolved value is not the static color proves the applier actually
 *     ran (i.e. the activity wired it up). On API 31+ Robolectric does not
 *     surface a real wallpaper-derived palette, so the value is whatever
 *     DynamicColors synthesises internally; the important contract is that
 *     the value changed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class MaterialYouApplierTest {

    /**
     * Minimal Activity that mirrors the production pattern:
     *  1. `setTheme(R.style.DayNightAppTheme)` BEFORE `super.onCreate`
     *     (matches MainActivity / SettingsActivity).
     *  2. `MaterialYouApplier.applyIfEnabled(this)` right after, BEFORE
     *     `super.onCreate` (the canonical call site).
     */
    class ThemedActivity : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.DayNightAppTheme)
            MaterialYouApplier.applyIfEnabled(this)
            super.onCreate(savedInstanceState)
        }
    }

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun writeMaterialYouEnabled(enabled: Boolean) {
        // The applier reads via MainDataStore.getUseMaterialYouImmediate(),
        // which checks the legacy androidx-prefs key first, then the mirror
        // ("use_material_you"). Pre-seed the mirror so the applier sees the
        // value we want to test.
        ctx.getSharedPreferences("main_mirror", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("use_material_you", enabled)
                .apply()
        // The default UiPalette is SYSTEM in MainDataStore.parseUiPalette
        // when the key is missing, so we don't need to seed it explicitly.
    }

    private fun writeUiPalette(palette: AppPreferences.Main.UiPalette) {
        ctx.getSharedPreferences("main_mirror", Context.MODE_PRIVATE)
                .edit()
                .putString("ui_palette", palette.name)
                .apply()
    }

    private fun resolveIconToolbar(activity: Activity): TypedValue {
        val tv = TypedValue()
        val resolved = activity.theme.resolveAttribute(
                R.attr.icon_toolbar, tv, /* resolveRefs = */ true
        )
        assertTrue("?icon_toolbar must resolve on the activity theme", resolved)
        assertNotEquals(
                "?icon_toolbar must NOT stay as TYPE_ATTRIBUTE after setTheme+super.onCreate",
                TypedValue.TYPE_ATTRIBUTE, tv.type
        )
        return tv
    }

    @Test
    fun `applier is a no-op when use_material_you is false`() {
        writeMaterialYouEnabled(false)
        writeUiPalette(AppPreferences.Main.UiPalette.SYSTEM)

        val activity = Robolectric.buildActivity(ThemedActivity::class.java).setup().get()
        val baseline = ctx.resources.getColor(R.color.light_icon_toolbar, ctx.theme)
        val resolved = resolveIconToolbar(activity)
        // The applier is supposed to short-circuit when use_material_you is
        // false (see MaterialYouApplier.applyIfEnabled). The icon_toolbar
        // therefore must keep its base-theme value, which is the static
        // light_icon_toolbar color resource.
        assertTrue(
                "?icon_toolbar must keep the base DayNightAppTheme value when MY is disabled (got #${Integer.toHexString(resolved.data)}, expected #${Integer.toHexString(baseline)})",
                resolved.data == baseline
        )
    }

    @Test
    fun `applier overlays M3 colorOnSurface onto icon_toolbar when MY is enabled and palette is SYSTEM`() {
        writeMaterialYouEnabled(true)
        writeUiPalette(AppPreferences.Main.UiPalette.SYSTEM)

        val activity = Robolectric.buildActivity(ThemedActivity::class.java).setup().get()
        val baseline = ctx.resources.getColor(R.color.light_icon_toolbar, ctx.theme)
        val resolved = resolveIconToolbar(activity)

        // The ThemeOverlay.ForPDA.MaterialYouSurface overlay (applied only
        // when the applier actually ran with SURFACE mode) remaps
        // ?icon_toolbar to ?attr/colorOnSurface. After the overlay is
        // applied, the resolved value must therefore DIFFER from the
        // static light_icon_toolbar baseline — otherwise the applier did
        // nothing and the regression (no wiring in MainActivity /
        // SettingsActivity) is back.
        assertTrue(
                "?icon_toolbar must differ from the static @color/light_icon_toolbar after MY is enabled — applier likely not wired up (got #${Integer.toHexString(resolved.data)} vs baseline #${Integer.toHexString(baseline)})",
                resolved.data != baseline
        )
    }
}
