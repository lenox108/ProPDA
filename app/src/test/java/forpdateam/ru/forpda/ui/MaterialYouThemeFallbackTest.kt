package forpdateam.ru.forpda.ui

import android.app.Activity
import android.os.Bundle
import android.util.TypedValue
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
 * Pre-Android-12 hardening for the canonical Material You refactor.
 *
 * Background. The `MaterialYouSurface` overlay (see `values/styles.xml`)
 * remaps `icon_toolbar` to the M3 dynamic role `?attr/colorOnSurface*`. On
 * API >= 31 the overlay is applied per-Activity by
 * [MaterialYouApplier.applyIfEnabled]; on API < 31 it is **not** applied (the
 * applier early-returns on `Build.VERSION.SDK_INT < S`), and the activity
 * uses the base `DayNightAppTheme` directly. (`cards_background` and
 * `default_text_color` were both retired by the Этап C
 * `concurrent-dreaming-wren` consumer-side migration — their leaf consumers
 * now read `?attr/colorSurface` / `?attr/colorOnSurface` directly; see the
 * `colorSurface` / `colorOnSurface` tests below.)
 *
 * Both code paths must hold for the canonical refactor:
 *  - Pre-31 path: `?icon_toolbar` / `?attr/colorSurface` / `?attr/colorOnSurface`
 *    resolve to **concrete ints** in the base `DayNightAppTheme`
 *    (e.g. `@color/light_icon_toolbar`). Otherwise
 *    `Toolbar.<init> / TextView.<init> / CardView.<init>` would call
 *    `TypedArray.getColorStateList(...)` on a `TYPE_ATTRIBUTE` value and
 *    throw `UnsupportedOperationException` (the very class of crash the
 *    775b344 / 7f1de68 / dde6287 chain was patching).
 *  - Material-You path: `?attr/colorOnSurface` / `?attr/colorSurfaceContainer*`
 *    must be **concrete ints** in `Theme.Material3.DayNight` (verified here
 *    on API 28 — the M3 library back-ports these roles to pre-31 themes).
 *
 * This test exercises the BASE theme path (the pre-31 / non-Material-You
 * case) — the Material You path is only reachable in production on devices
 * that already passed the `MaterialYouApplier` SDK gate, so the M3 role
 * resolution is verified by checking the underlying `Theme.Material3.DayNight`
 * attr resolution itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class MaterialYouThemeFallbackTest {

    /**
     * A real Activity themed with the base app theme. Material3-parented theme
     * attributes (`?attr/colorOnSurface`, `?icon_toolbar`, …) only resolve once
     * a themed Activity has been created and laid out by Robolectric — the bare
     * application context does not inflate the AAR theme overlays. This mirrors
     * the production pre-31 (non-Material-You) path where the Activity runs on
     * `DayNightAppTheme` directly.
     */
    class ThemedActivity : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.DayNightAppTheme)
            super.onCreate(savedInstanceState)
        }
    }

    private val ctx: android.content.Context by lazy {
        Robolectric.buildActivity(ThemedActivity::class.java).setup().get()
    }

    /**
     * Android has no single `TypedValue.TYPE_INT_COLOR`; a resolved color int can
     * be any of the four ARGB8/RGB8/ARGB4/RGB4 subtypes in the inclusive
     * [TypedValue.TYPE_FIRST_COLOR_INT]..[TypedValue.TYPE_LAST_COLOR_INT] range.
     * The tests only care that the attribute resolved to a concrete color int
     * (not a dangling `TYPE_ATTRIBUTE`), so we assert membership in that range.
     */
    private fun assertResolvedToColorInt(message: String, tv: TypedValue) {
        assertTrue(
                "$message — type=0x${Integer.toHexString(tv.type)} is not a color int",
                tv.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT
        )
    }

    @Test
    fun `base DayNightAppTheme defines icon_toolbar as concrete int on pre-31`() {
        val tv = TypedValue()
        val resolved = ctx.theme.resolveAttribute(R.attr.icon_toolbar, tv, /* resolveRefs = */ true)
        assertTrue("?icon_toolbar must resolve on the base theme", resolved)
        assertNotEquals(
                "icon_toolbar must NOT stay as TYPE_ATTRIBUTE — Toolbar.<init> would crash",
                TypedValue.TYPE_ATTRIBUTE, tv.type
        )
        // A concrete color int is the expected final form for the base theme.
        assertResolvedToColorInt(
                "icon_toolbar must resolve to a concrete color int (@color/light_icon_toolbar)",
                tv
        )
        assertNotEquals("icon_toolbar must not be transparent black (0)", 0, tv.data)
    }

    // `default_text_color` (the legacy custom attr TextView consumers used to read)
    // was retired by the Этап C `concurrent-dreaming-wren` consumer-side migration —
    // every leaf TextView consumer now reads `?attr/colorOnSurface` directly. The
    // `colorOnSurface as concrete int on pre-31` test below already covers this
    // invariant under the new role; no separate test is needed.

    @Test
    fun `base DayNightAppTheme defines colorSurface as concrete int on pre-31`() {
        // `cards_background` (the legacy custom attr CardView consumers used to read)
        // was retired by the Этап C consumer-side migration — every CardView
        // consumer now reads `?attr/colorSurface` directly. This test takes over
        // the original invariant under the new role.
        val tv = TypedValue()
        val resolved = ctx.theme.resolveAttribute(
                com.google.android.material.R.attr.colorSurface, tv, /* resolveRefs = */ true
        )
        assertTrue("?attr/colorSurface must resolve on the base theme", resolved)
        assertNotEquals(
                "colorSurface must NOT stay as TYPE_ATTRIBUTE — CardView.<init> would crash",
                TypedValue.TYPE_ATTRIBUTE, tv.type
        )
        assertResolvedToColorInt(
                "colorSurface must resolve to a concrete color int (@color/light_card_background)",
                tv
        )
        assertNotEquals(0, tv.data)
    }

    @Test
    fun `base DayNightAppTheme defines colorOnSurface as concrete int on pre-31`() {
        // The M3 base theme back-ports colorOnSurface / colorSurfaceContainer* /
        // colorOnSurfaceVariant / colorOutlineVariant as concrete @color/* —
        // this is what makes the Material You overlay safe on API >= 31. We
        // verify it here on API 28 (pre-31) to confirm the back-port covers
        // our minimum supported level.
        val tv = TypedValue()
        val resolved = ctx.theme.resolveAttribute(
                com.google.android.material.R.attr.colorOnSurface, tv, true
        )
        assertTrue("?attr/colorOnSurface must resolve via Theme.Material3.DayNight", resolved)
        assertNotEquals(TypedValue.TYPE_ATTRIBUTE, tv.type)
        assertResolvedToColorInt(
                "colorOnSurface must resolve to a concrete color int on pre-31 (M3 back-port)",
                tv
        )
    }

    @Test
    fun `base DayNightAppTheme defines colorSurfaceContainer as concrete int on pre-31`() {
        val tv = TypedValue()
        val resolved = ctx.theme.resolveAttribute(
                com.google.android.material.R.attr.colorSurfaceContainer, tv, true
        )
        assertTrue("?attr/colorSurfaceContainer must resolve via Theme.Material3.DayNight", resolved)
        assertNotEquals(TypedValue.TYPE_ATTRIBUTE, tv.type)
        assertResolvedToColorInt(
                "colorSurfaceContainer must resolve to a concrete color int on pre-31 (M3 back-port)",
                tv
        )
    }

    @Test
    fun `base DayNightAppTheme defines colorSurfaceContainerHigh as concrete int on pre-31`() {
        val tv = TypedValue()
        val resolved = ctx.theme.resolveAttribute(
                com.google.android.material.R.attr.colorSurfaceContainerHigh, tv, true
        )
        assertTrue("?attr/colorSurfaceContainerHigh must resolve", resolved)
        assertResolvedToColorInt(
                "colorSurfaceContainerHigh must resolve to a concrete color int on pre-31",
                tv
        )
    }

    @Test
    fun `base DayNightAppTheme defines colorOnSurfaceVariant as concrete int on pre-31`() {
        val tv = TypedValue()
        val resolved = ctx.theme.resolveAttribute(
                com.google.android.material.R.attr.colorOnSurfaceVariant, tv, true
        )
        assertTrue("?attr/colorOnSurfaceVariant must resolve", resolved)
        assertResolvedToColorInt(
                "colorOnSurfaceVariant must resolve to a concrete color int on pre-31",
                tv
        )
    }

    @Test
    fun `base DayNightAppTheme defines colorOutlineVariant as concrete int on pre-31`() {
        val tv = TypedValue()
        val resolved = ctx.theme.resolveAttribute(
                com.google.android.material.R.attr.colorOutlineVariant, tv, true
        )
        assertTrue("?attr/colorOutlineVariant must resolve", resolved)
        assertResolvedToColorInt(
                "colorOutlineVariant must resolve to a concrete color int on pre-31",
                tv
        )
    }

    @Test
    fun `base DayNightAppTheme defines colorPrimary as concrete int on pre-31`() {
        // The 12% primary ripple (m3_ripple_color.xml) needs ?attr/colorPrimary
        // to resolve to a concrete int at inflation on every API level. Verified here
        // for the base theme (pre-31 path).
        val tv = TypedValue()
        val resolved = ctx.theme.resolveAttribute(
                com.google.android.material.R.attr.colorPrimary, tv, true
        )
        assertTrue("?attr/colorPrimary must resolve", resolved)
        assertResolvedToColorInt(
                "colorPrimary must resolve to a concrete color int on pre-31",
                tv
        )
    }

    @Test
    fun `m3_ripple_color inflates and references colorPrimary without crash on pre-31`() {
        // The colorControlHighlight slot reads the CSL via getColorStateList. Verify
        // that the ripple selector inflates on API 28 — its <item> with
        // android:color="?attr/colorPrimary" must resolve without crashing.
        val csl = ctx.resources.getColorStateList(R.color.m3_ripple_color, ctx.theme)
        assertNotNull("m3_ripple_color must inflate to a non-null ColorStateList", csl)
        // A non-trivial selector should have at least the default item.
        assertTrue("m3_ripple_color must expose a default color", csl!!.defaultColor != 0 || csl.isStateful)
    }
}
