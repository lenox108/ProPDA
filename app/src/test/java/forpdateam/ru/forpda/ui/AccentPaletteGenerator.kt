package forpdateam.ru.forpda.ui

import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeExpressive
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.SchemeVibrant
import org.junit.Ignore
import org.junit.Test
import java.io.File

/**
 * ГЕНЕРАТОР (не обычный тест — помечен @Ignore, запускается вручную).
 *
 * Единственный источник правды для акцент-палитр «смены цвета». Использует ТОЧНЫЙ
 * Material 3 алгоритм (`SchemeTonalSpot` + `MaterialDynamicColors` из
 * material-color-utilities, бандл Material 1.12) — тот же, что генерит Material You
 * из обоев — и эмитит `colors_accents.xml` (light) + `values-night/colors_accents.xml`
 * (dark). Работает на JVM, offline, значит палитры одинаковы на ВСЕХ API (26+),
 * а не только там, где доступен wallpaper-based DynamicColors (API 31+).
 *
 * Как перегенерировать (например, поменяв seed'ы или добавив палитру):
 *   ./gradlew :app:testStableDebugUnitTest --tests "*AccentPaletteGenerator*" \
 *       -Dforpda.generateAccents=true
 * (без флага тест — no-op, чтобы не переписывать ресурсы на каждом прогоне CI).
 *
 * Перекрываем только «акцентные» M3-роли (primary/secondary/tertiary + контейнеры +
 * inversePrimary). Поверхности/фон остаются НЕЙТРАЛЬНЫМИ (из базовой темы) — акцент
 * красит кнопки/FAB/ссылки/чипы/переключатели/выделение, но не «замыливает» фоны.
 */
@Ignore("Manual generator; run with -Dforpda.generateAccents=true to rewrite colors_accents.xml")
class AccentPaletteGenerator {

    /** name (для ресурсов) → seed-цвет (ARGB). Порядок = порядок в гриде выбора. */
    private val seeds: List<Pair<String, Int>> = listOf(
            "blue" to 0xFF0B57D0.toInt(),
            "indigo" to 0xFF4355B9.toInt(),
            "violet" to 0xFF7B4FCF.toInt(),
            "purple" to 0xFF9C27B0.toInt(),
            "pink" to 0xFFC2185B.toInt(),
            "red" to 0xFFD32F2F.toInt(),
            "deeporange" to 0xFFE64A19.toInt(),
            "orange" to 0xFFEF6C00.toInt(),
            "amber" to 0xFFFF8F00.toInt(),
            "green" to 0xFF2E7D32.toInt(),
            "teal" to 0xFF00796B.toInt(),
            "cyan" to 0xFF0097A7.toInt(),
    )

    /** role suffix → селектор DynamicColor из MaterialDynamicColors. */
    private val roles: List<Pair<String, (MaterialDynamicColors) -> com.google.android.material.color.utilities.DynamicColor>> = listOf(
            "primary" to { m -> m.primary() },
            "on_primary" to { m -> m.onPrimary() },
            "primary_container" to { m -> m.primaryContainer() },
            "on_primary_container" to { m -> m.onPrimaryContainer() },
            "inverse_primary" to { m -> m.inversePrimary() },
            "secondary" to { m -> m.secondary() },
            "on_secondary" to { m -> m.onSecondary() },
            "secondary_container" to { m -> m.secondaryContainer() },
            "on_secondary_container" to { m -> m.onSecondaryContainer() },
            "tertiary" to { m -> m.tertiary() },
            "on_tertiary" to { m -> m.onTertiary() },
            "tertiary_container" to { m -> m.tertiaryContainer() },
            "on_tertiary_container" to { m -> m.onTertiaryContainer() },
    )

    @Test
    fun generate() {
        if (System.getProperty("forpda.generateAccents") != "true") return
        val root = locateProjectRoot()
        writeFile(File(root, "app/src/main/res/values/colors_accents.xml"), isDark = false)
        writeFile(File(root, "app/src/main/res/values-night/colors_accents.xml"), isDark = true)
    }

    private fun writeFile(file: File, isDark: Boolean) {
        val mdc = MaterialDynamicColors()
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<!--\n")
        sb.append("    СГЕНЕРИРОВАНО AccentPaletteGenerator (material-color-utilities, SchemeTonalSpot).\n")
        sb.append("    НЕ РЕДАКТИРОВАТЬ ВРУЧНУЮ — перегенерировать через генератор (см. его KDoc).\n")
        sb.append("    Вариант: ${if (isDark) "DARK (values-night)" else "LIGHT (values)"}.\n")
        sb.append("-->\n")
        sb.append("<resources>\n")
        // Три набора стилей акцента (см. AccentStyle): приглушённый TonalSpot (по
        // умолчанию), сочный Vibrant (инфикс `_vibrant_`) и экспрессивный
        // Expressive (инфикс `_expressive_`, M3 Expressive — сдвинутые оттенки).
        for ((name, seed) in seeds) {
            val tonal: DynamicScheme = SchemeTonalSpot(Hct.fromInt(seed), isDark, 0.0)
            val vibrant: DynamicScheme = SchemeVibrant(Hct.fromInt(seed), isDark, 0.0)
            val expressive: DynamicScheme = SchemeExpressive(Hct.fromInt(seed), isDark, 0.0)
            sb.append("    <!-- ${name} (seed #${String.format("%06X", seed and 0xFFFFFF)}) -->\n")
            for ((suffix, selector) in roles) {
                val t = selector(mdc).getArgb(tonal)
                sb.append("    <color name=\"accent_${name}_${suffix}\">#${String.format("%06X", t and 0xFFFFFF)}</color>\n")
            }
            for ((suffix, selector) in roles) {
                val v = selector(mdc).getArgb(vibrant)
                sb.append("    <color name=\"accent_${name}_vibrant_${suffix}\">#${String.format("%06X", v and 0xFFFFFF)}</color>\n")
            }
            for ((suffix, selector) in roles) {
                val e = selector(mdc).getArgb(expressive)
                sb.append("    <color name=\"accent_${name}_expressive_${suffix}\">#${String.format("%06X", e and 0xFFFFFF)}</color>\n")
            }
        }
        sb.append("</resources>\n")
        file.parentFile?.mkdirs()
        file.writeText(sb.toString())
    }

    private fun locateProjectRoot(): File {
        val userDir = System.getProperty("user.dir") ?: "."
        val candidates = listOf(File(userDir), File("."), File(".."), File("../.."), File("../../.."))
        for (cand in candidates) {
            val probe = File(cand, "app/src/main/res/values/styles.xml")
            if (probe.exists()) return cand.canonicalFile
        }
        return File(".").canonicalFile
    }
}
