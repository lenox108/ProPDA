package forpdateam.ru.forpda.ui

import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.SchemeVibrant
import com.google.android.material.color.utilities.TonalPalette
import com.google.android.material.color.utilities.Variant
import forpdateam.ru.forpda.common.Preferences.Main.AccentStyle

/**
 * Единственный источник правды: seed-цвет курируемой палитры + стиль акцента →
 * `DynamicScheme`. Общий для генератора ресурсов (`AccentPaletteGenerator`, пишет
 * `colors_accents.xml`) и для живого превью в
 * [forpdateam.ru.forpda.ui.views.dialog.AccentPickerDialog], чтобы свотч, превью и
 * реально применённая тема не могли разъехаться.
 *
 * Три стиля различаются ХРОМОЙ primary (тон всегда = тону подписи) и характером
 * вторичных ролей:
 * - TONAL — приглушённый (SchemeTonalSpot, хрома primary ≈ 36);
 * - VIBRANT — сочный (SchemeVibrant, хрома выжата до предела гаммы);
 * - EXPRESSIVE — внутреннее legacy-имя видимого режима «Однотонный»: primary
 *   заметно ярче приглушённого, secondary/tertiary отличаются насыщенностью, но
 *   сохраняют hue подписанного акцента.
 *
 * Почему это НЕ `SchemeExpressive`: системной палитре из обоев допустимы повороты
 * hue, а ручной грид обещает конкретный цвет названием. Раньше у «Синего»
 * secondary становился розовым (#F0B8C7 / container #633B47), поэтому выбранный
 * акцент визуально не соответствовал подписи. Material You этим кодом не затронут.
 */
object AccentSchemes {

    fun scheme(seed: Int, style: AccentStyle, isDark: Boolean): DynamicScheme {
        val hct = Hct.fromInt(seed)
        return when (style) {
            AccentStyle.TONAL -> SchemeTonalSpot(hct, isDark, 0.0)
            AccentStyle.VIBRANT -> SchemeVibrant(hct, isDark, 0.0)
            AccentStyle.EXPRESSIVE -> expressive(hct, isDark)
        }
    }

    /**
     * Однотонная схема для ручного акцента.
     *
     * [Variant.EXPRESSIVE] сохраняем как внутренний контракт тона контейнеров и
     * совместимость с сохранённым enum-значением, но все три accent-палитры строим
     * сами из hue seed'а. Нейтрали берём у TonalSpot: они не входят в accent-overlay,
     * однако нужны [DynamicScheme] для корректного вычисления ролей.
     */
    private fun expressive(seed: Hct, isDark: Boolean): DynamicScheme {
        val base = SchemeTonalSpot(seed, isDark, 0.0)
        return DynamicScheme(
                seed,
                Variant.EXPRESSIVE,
                isDark,
                0.0,
                TonalPalette.fromHueAndChroma(seed.hue, EXPRESSIVE_PRIMARY_CHROMA),
                TonalPalette.fromHueAndChroma(seed.hue, EXPRESSIVE_SECONDARY_CHROMA),
                TonalPalette.fromHueAndChroma(seed.hue, EXPRESSIVE_TERTIARY_CHROMA),
                base.neutralPalette,
                base.neutralVariantPalette,
        )
    }

    /**
     * Между приглушённым (≈36) и сочным. Выше не поднимаем: Vibrant выжимает хрому в
     * границу гаммы, а у зелёного она всего ≈58 — на 58 однотонный слился бы с ним.
     */
    private const val EXPRESSIVE_PRIMARY_CHROMA = 48.0
    private const val EXPRESSIVE_SECONDARY_CHROMA = 24.0
    private const val EXPRESSIVE_TERTIARY_CHROMA = 36.0
}
