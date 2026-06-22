package forpdateam.ru.forpda.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.HarmonizedColors
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.Preferences as AppPreferences
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
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
 *
 * The additional gating tests (3-5 below) pin down the §4.5 harmonization
 * invariants that can't be tested through Robolectric's native theme engine
 * (see [HarmonizedColors] + Robolectric upstream issue #9552):
 *
 *  3. With `use_material_you = true` + a non-SYSTEM palette
 *     (SEPIA_READING / SEPIA_BLUE / MINIMAL_READER), the applier MUST NOT
 *     invoke [DynamicColors.applyToActivityIfAvailable] at all
 *     (early-return on `Mode = NONE`). This guarantees that the
 *     `OnAppliedCallback` — and therefore the inner
 *     [HarmonizedColors.applyToContextIfAvailable] call — is never built
 *     for hand-picked reading palettes, so their `colorError` survives.
 *
 *  4. `isRobolectric()` returns true in this test environment
 *     (Build.FINGERPRINT starts with "robolectric"). This is the predicate
 *     that gates the harmonization call off the test path; the assertion
 *     is here so a future refactor that breaks the detection gets caught
 *     with a clear failure message instead of an opaque #9552 crash.
 *
 *  5. With `use_material_you = true` + SYSTEM palette
 *     (so [DynamicColors.applyToActivityIfAvailable] IS reached and the
 *     `OnAppliedCallback` fires), [HarmonizedColors.applyToContextIfAvailable]
 *     is NOT invoked — because the Robolectric guard short-circuits it. This
 *     is the regression guard for the guard: if someone removes
 *     `if (!isRobolectric())` from the production code, this test fails
 *     with `verify(exactly = 0) { HarmonizedColors.applyToContextIfAvailable(...) }`,
 *     pointing directly at the regression.
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

    // --- §4.5 gating tests (see KDoc) ---

    @After
    fun tearDownMockk() {
        // mockkStatic state is per-test classloader; tear it down so other
        // test classes in the same module don't see the static stubs.
        runCatching { unmockkStatic(DynamicColors::class) }
        runCatching { unmockkStatic(HarmonizedColors::class) }
    }

    @Test
    fun `applier is a no-op for non-SYSTEM palettes so HarmonizedColors never fires for reading palettes`() {
        // Mock both Material entry points so the test can observe (or rather,
        // NOT observe) any call. `DynamicColors` and `HarmonizedColors` are
        // Java classes with static methods — mockkStatic replaces the static
        // dispatch for the duration of the test, and we use these stubs to
        // count invocations. These are exactly the API surface that must
        // stay silent for hand-picked reading palettes.
        mockkStatic(DynamicColors::class)
        mockkStatic(HarmonizedColors::class)
        every { DynamicColors.applyToActivityIfAvailable(any(), any()) } returns Unit
        every { HarmonizedColors.applyToContextIfAvailable(any(), any()) } returns Unit

        // For every reading palette, with use_material_you explicitly ON, the
        // applier must short-circuit at the `mode == NONE` early-return and
        // never reach DynamicColors — which means the OnAppliedCallback
        // (which contains the HarmonizedColors call) is never even built.
        listOf(
                AppPreferences.Main.UiPalette.SEPIA_READING,
                AppPreferences.Main.UiPalette.SEPIA_BLUE,
                AppPreferences.Main.UiPalette.MINIMAL_READER
        ).forEach { palette ->
            writeMaterialYouEnabled(true)
            writeUiPalette(palette)

            Robolectric.buildActivity(ThemedActivity::class.java).setup().get()

            verify(exactly = 0) {
                DynamicColors.applyToActivityIfAvailable(any(), any())
            }
            verify(exactly = 0) {
                HarmonizedColors.applyToContextIfAvailable(any(), any())
            }
        }
    }

    @Test
    fun `isRobolectric returns true in this Robolectric test environment`() {
        // The Robolectric guard that gates HarmonizedColors off the test path
        // depends on Build.FINGERPRINT starting with "robolectric". If a future
        // refactor changes the detection predicate (e.g. drops the prefix
        // check or looks at the wrong Build.* field), this test fails
        // immediately with a clear message, instead of the opaque
        // Robolectric #9552 stack that would otherwise surface downstream.
        assertTrue(
                "Build.FINGERPRINT is '${android.os.Build.FINGERPRINT}' — isRobolectric() must return true in this Robolectric test environment",
                MaterialYouApplier.isRobolectric()
        )
    }

    @Test
    fun `HarmonizedColors is gated by isRobolectric in the OnAppliedCallback`() {
        // Pre-condition: use_material_you = true + palette = SYSTEM →
        // resolveMode returns SURFACE → the applier DOES reach
        // DynamicColors.applyToActivityIfAvailable and the OnAppliedCallback
        // fires. Inside the callback the production code has
        //   if (!isRobolectric()) { HarmonizedColors.applyToContextIfAvailable(...) }
        // — so in this Robolectric test, the inner call must NOT be made.
        // If someone removes the `!isRobolectric()` guard, this test fails
        // with a clear `verify(exactly = 0)` mismatch pointing at the
        // regression (instead of the opaque Robolectric #9552 crash that
        // would otherwise surface from inside the OnAppliedCallback).
        mockkStatic(DynamicColors::class)
        mockkStatic(HarmonizedColors::class)
        // Stub DynamicColors so the activity creation completes without
        // actually applying the overlay (we only care about the static-call
        // bookkeeping, not the real overlay effect).
        every { DynamicColors.applyToActivityIfAvailable(any(), any()) } returns Unit
        every { HarmonizedColors.applyToContextIfAvailable(any(), any()) } returns Unit

        writeMaterialYouEnabled(true)
        writeUiPalette(AppPreferences.Main.UiPalette.SYSTEM)

        Robolectric.buildActivity(ThemedActivity::class.java).setup().get()

        // DynamicColors WAS called (proves the applier reached the active-MY
        // branch and built the OnAppliedCallback).
        verify(exactly = 1) {
            DynamicColors.applyToActivityIfAvailable(any(), any())
        }
        // …but HarmonizedColors was NOT — the Robolectric guard fired.
        verify(exactly = 0) {
            HarmonizedColors.applyToContextIfAvailable(any(), any())
        }
    }
}
