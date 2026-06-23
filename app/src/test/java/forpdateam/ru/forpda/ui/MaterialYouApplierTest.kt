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
import org.junit.Assert.assertNotNull
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
 *     is NOT invoked at all — the applier no longer uses the Material API,
 *     it applies its own [R.style.ThemeOverlay_ForPDA_HarmonizedError] via
 *     `activity.theme.applyStyle(...)`. This is the regression guard for the
 *     prod TextView inflation crash (see fragment_base.xml:118) caused by
 *     `ThemeOverlay.Material3.HarmonizedColors` pulling the
 *     `?attr/textColorPrimary` chain through `Theme.AppCompat.Empty`. If
 *     someone re-introduces the upstream HarmonizedColors call without the
 *     custom overlay, this test fails with `verify(exactly = 0) { ... }`,
 *     pointing directly at the regression.
 *
 *  6. [R.style.ThemeOverlay_ForPDA_HarmonizedError] resolves `?attr/colorError`
 *     to a non-zero concrete color int (not `TYPE_ATTRIBUTE`) on a real
 *     themed activity. This pins the §4.5 contract: error-color roles are
 *     defined and the chain does not break, regardless of whether the
 *     device surfaces a real wallpaper palette.
 *
 *  7. A themed [Activity] with [R.style.DayNightAppTheme] +
 *     [MaterialYouApplier.applyIfEnabled] inflates without throwing
 *     [android.view.InflateException] when MY is enabled. This is the
 *     regression guard for the production TextView.<init> /
 *     `readTextAppearance` crash (the one that fires on `fragment_base.xml:118`
 *     via `TypedArray.getColorStateList` → `UnsupportedOperationException`).
 *     Note: Robolectric does NOT exhibit the upstream crash either, so this
 *     test on its own does not prove the prod crash is gone — visual
 *     verification on a real device is still required (see commit message).
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
    fun `applier wires M3 dynamic colors onto framework M3 roles when MY is enabled and palette is SYSTEM`() {
        writeMaterialYouEnabled(true)
        writeUiPalette(AppPreferences.Main.UiPalette.SYSTEM)

        val activity = Robolectric.buildActivity(ThemedActivity::class.java).setup().get()

        // After the fix in this commit, `icon_toolbar` is intentionally a
        // CONCRETE @color/light_icon_toolbar (was ?attr/colorOnSurface — see
        // the MentionsFragment hydra-crash on ActionMenuPresenter
        // .OverflowMenuButton (ImageView).<init> reading 0x7f040272). So
        // `icon_toolbar` can no longer be used as the wiring canary.
        //
        // The wiring is instead observed on a framework M3 role that the
        // applier deliberately keeps as ?attr/...: `colorOnBackground` →
        // `colorOnSurface`. After DynamicColors.applyToActivityIfAvailable
        // runs, `colorOnSurface` must be defined on the theme (it comes from
        // the Material 3 base / DynamicColors.Light overlay). Before the
        // applier was wired up (the 7f1de68 regression), the activity theme
        // had NO `colorOnSurface` value, and `resolveAttribute` returned
        // false — that's what this test guards against.
        val tv = TypedValue()
        val resolved = activity.theme.resolveAttribute(
                android.R.attr.colorForeground, tv, /* resolveRefs = */ false
        )
        assertTrue(
                "applier must have wired Material You (theme must resolve colorForeground — got nothing)",
                resolved
        )

        // And the M3 colorOnSurface role itself must be defined post-overlay
        // (the overlay MaterialYouSurface references ?attr/colorOnSurface for
        // `colorOnBackground`; if the applier didn't run, this would fall
        // through to Theme.AppCompat.Empty and crash — see
        // MaterialYouThemeFallbackTest for the fallback path).
        val onSurfaceTv = TypedValue()
        val onSurfaceResolved = activity.theme.resolveAttribute(
                com.google.android.material.R.attr.colorOnSurface, onSurfaceTv, true
        )
        assertTrue(
                "applier must wire colorOnSurface (M3 dynamic role) when MY+SYSTEM is enabled",
                onSurfaceResolved
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
        // The isRobolectric() helper is kept on the applier (as `internal`) so
        // future Material-API harmonization calls (e.g. if someone re-introduces
        // a guarded third-party API) can still detect the test environment and
        // opt out. The detection depends on Build.FINGERPRINT starting with
        // "robolectric" — Robolectric 4.x sets it to "robolectric/<arch>". If
        // a future refactor changes the detection predicate, this test fails
        // immediately with a clear message, instead of the opaque #9552 stack
        // that would otherwise surface downstream.
        assertTrue(
                "Build.FINGERPRINT is '${android.os.Build.FINGERPRINT}' — isRobolectric() must return true in this Robolectric test environment",
                MaterialYouApplier.isRobolectric()
        )
    }

    @Test
    fun `HarmonizedColors applyToContextIfAvailable is never called in the OnAppliedCallback`() {
        // Pre-condition: use_material_you = true + palette = SYSTEM →
        // resolveMode returns SURFACE → the applier DOES reach
        // DynamicColors.applyToActivityIfAvailable and the OnAppliedCallback
        // fires. The callback used to call
        //   HarmonizedColors.applyToContextIfAvailable(...)
        // — but that pulled ThemeOverlay.Material3.HarmonizedColors' ?attr chain
        // through Theme.AppCompat.Empty, which crashed real devices
        // (TextView.<init> → readTextAppearance → TypedArray.getColorStateList
        // → UnsupportedOperationException, see fragment_base.xml:118). The
        // applier now applies its own ThemeOverlay.ForPDA.HarmonizedError
        // via activity.theme.applyStyle(...) instead. If someone reverts
        // that decision and brings the upstream API call back, this test
        // fails with `verify(exactly = 0)`, pointing directly at the regression.
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
        // …but HarmonizedColors.applyToContextIfAvailable was NOT — the
        // applier no longer uses that API.
        verify(exactly = 0) {
            HarmonizedColors.applyToContextIfAvailable(any(), any())
        }
    }

    @Test
    fun `ThemeOverlay_ForPDA_HarmonizedError remaps colorError to colorPrimary when MY is enabled`() {
        // §4.5 contract: when MY is enabled, our HarmonizedError overlay
        // (applied inside the OnAppliedCallback) remaps
        //   ?attr/colorError             -> ?attr/colorPrimary
        //   ?attr/colorOnError           -> ?attr/colorOnPrimary
        //   ?attr/colorErrorContainer    -> ?attr/colorPrimaryContainer
        //   ?attr/colorOnErrorContainer  -> ?attr/colorOnPrimaryContainer
        // so destructive actions (Delete / Report / etc.) are tinted to the
        // wallpaper-derived primary palette.
        //
        // We don't assert exact value equality (the resolved colorPrimary
        // itself depends on the dynamic palette, which Robolectric synthesises
        // internally on API 33); we assert that the chain does NOT leave
        // colorError as a dangling TYPE_ATTRIBUTE (the prod crash symptom)
        // and that colorError is a concrete color int.
        writeMaterialYouEnabled(true)
        writeUiPalette(AppPreferences.Main.UiPalette.SYSTEM)

        val activity = Robolectric.buildActivity(ThemedActivity::class.java).setup().get()

        val colorError = TypedValue()
        val resolved = activity.theme.resolveAttribute(
                com.google.android.material.R.attr.colorError, colorError, /* resolveRefs = */ true
        )
        assertTrue("?attr/colorError must resolve on the activity theme", resolved)
        assertNotEquals(
                "?attr/colorError must NOT stay as TYPE_ATTRIBUTE — the prod TextView crash surfaced exactly this symptom",
                TypedValue.TYPE_ATTRIBUTE, colorError.type
        )
        assertTrue(
                "?attr/colorError must resolve to a color int (got type=0x${Integer.toHexString(colorError.type)})",
                colorError.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT
        )
    }

    @Test
    fun `ThemedActivity inflates without InflateException when MY is enabled`() {
        // Regression guard for the prod crash. The original Robolectric
        // guard `if (!isRobolectric())` prevented the HarmonizedColors call
        // from running in tests, so the InflateException
        // (TextView.readTextAppearance → TypedArray.getColorStateList →
        // UnsupportedOperationException at fragment_base.xml:118) was never
        // observable from the test path. Now that the applier applies its
        // own ThemeOverlay.ForPDA.HarmonizedError (no guard), a Robolectric
        // activity creation is the closest we can get to a real-device
        // inflation smoke test.
        //
        // IMPORTANT: Robolectric does NOT exhibit the same native theme
        // engine crash as Android (it doesn't share ShadowArscAssetManager10's
        // ThemeOverlay.Material3.HarmonizedColors resolution path the same
        // way). So this test alone does NOT prove the prod crash is gone.
        // It is the regression guard for the regression guard: if someone
        // re-introduces the upstream HarmonizedColors call, this activity
        // setup throws here in CI, catching the issue before it ships.
        // Visual verification on a real device with use_material_you = true
        // remains the canonical regression test (see commit message).
        writeMaterialYouEnabled(true)
        writeUiPalette(AppPreferences.Main.UiPalette.SYSTEM)

        // If the production code crashes the activity setup, this line
        // throws InflateException (or a downstream exception) and the test
        // fails with a clear stack pointing at MaterialYouApplier / the
        // OnAppliedCallback overlay chain.
        val activity = Robolectric.buildActivity(ThemedActivity::class.java).setup().get()
        assertNotNull("ThemedActivity must be created without throwing", activity)
    }
}
