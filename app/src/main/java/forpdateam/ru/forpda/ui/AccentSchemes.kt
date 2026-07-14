package forpdateam.ru.forpda.ui

import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeExpressive
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
 * - EXPRESSIVE — «живой»: primary заметно ярче приглушённого, но не кричит как
 *   Vibrant, а secondary/tertiary уведены в неожиданные тона (у синего — оливковый
 *   и зелёный). Именно они красят ссылки (`colorSecondary`) и tertiary-контейнеры,
 *   так что стиль виден не только на кнопке.
 *
 * ВАЖНО, почему EXPRESSIVE не берётся из `SchemeExpressive` как есть. Эта схема
 * строит primary-палитру как `hue(seed) + 240°` — осознанный «неожиданный» сдвиг.
 * Для акцента из обоев (у них нет имени) это нормально, но у нас грид ПОДПИСАННЫХ
 * цветов: «Синий» давал primary #2E6A3A (зелёный), «Зелёный» — #94483F (кирпичный).
 * Поэтому primary-палитру мы задаём сами по тону seed'а, а у SchemeExpressive
 * забираем ровно то, ради чего он нужен, — повёрнутые secondary/tertiary и нейтрали.
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

    /** SchemeExpressive со своей primary-палитрой: тон подписи + повышенная хрома. */
    private fun expressive(seed: Hct, isDark: Boolean): DynamicScheme {
        val base = SchemeExpressive(seed, isDark, 0.0)
        return DynamicScheme(
                seed,
                Variant.EXPRESSIVE,
                isDark,
                0.0,
                TonalPalette.fromHueAndChroma(seed.hue, EXPRESSIVE_PRIMARY_CHROMA),
                base.secondaryPalette,
                base.tertiaryPalette,
                base.neutralPalette,
                base.neutralVariantPalette,
        )
    }

    /**
     * Между приглушённым (≈36) и сочным. Выше не поднимаем: Vibrant выжимает хрому в
     * границу гаммы, а у зелёного она всего ≈58 — на 58 Expressive слился бы с ним.
     */
    private const val EXPRESSIVE_PRIMARY_CHROMA = 48.0
}
