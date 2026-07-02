package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Контракт-харнесс для строковых/числовых констант, которые ОДНОВРЕМЕННО живут в
 * Kotlin-enum'ах и в `theme.js`. Именно этот класс багов чинился снова и снова:
 *
 *  - `theme.js` явно документирует прошлый silent-break: «Раньше здесь сравнивались
 *    "0"/"1"/"2" … сломана память позиции скролла при BACK/REFRESH» — Kotlin слал
 *    имя enum-константы, а JS сравнивал с числами, и условия НИКОГДА не срабатывали.
 *  - unread-anchor guard: JS `UNREAD_ANCHOR_GUARD_MAX_MS` обязан зеркалить Kotlin
 *    `ThemeUnreadHybridAnchorGuardPolicy.ANCHOR_GUARD_MAX_BLOCK_MS`.
 *
 * Unit-тесты отдельных policy насыщены, но САМ мост «строка Kotlin ↔ строка JS»
 * не был запинен: переименование на одной стороне тихо ломало якоря/скролл без
 * падения тестов. Этот харнесс читает реальный `theme.js` и падает, если контракт
 * разошёлся.
 */
class ThemeStateEnumJsContractTest {

    private val themeJs: String by lazy {
        val candidates = listOf(
                Path.of("src/main/assets/forpda/scripts/modules/theme.js"),
                Path.of("app/src/main/assets/forpda/scripts/modules/theme.js"),
        )
        val path = candidates.firstOrNull { Files.exists(it) }
                ?: error("theme.js not found in ${candidates.joinToString()}")
        Files.newInputStream(path).bufferedReader().readText()
    }

    // ---- ThemeLoadAction ↔ JS *_ACTION consts -------------------------------

    @Test
    fun themeLoadAction_stringValuesAreStable() {
        // Эти строки — контракт с JS; менять только синхронно с theme.js.
        assertEquals("NORMAL", ThemeLoadAction.Normal.toString())
        assertEquals("BACK", ThemeLoadAction.Back.toString())
        assertEquals("REFRESH", ThemeLoadAction.Refresh.toString())
        assertEquals("END", ThemeLoadAction.End.toString())
    }

    @Test
    fun themeLoadAction_roundTripsThroughString() {
        for (action in listOf(
                ThemeLoadAction.Normal, ThemeLoadAction.Back,
                ThemeLoadAction.Refresh, ThemeLoadAction.End,
        )) {
            assertEquals(action, ThemeLoadAction.fromString(action.toString()))
        }
    }

    @Test
    fun themeLoadAction_unknownFallsBackToNormal() {
        assertEquals(ThemeLoadAction.Normal, ThemeLoadAction.fromString(""))
        assertEquals(ThemeLoadAction.Normal, ThemeLoadAction.fromString("back")) // регистрозависимо → не Back
        assertEquals(ThemeLoadAction.Normal, ThemeLoadAction.fromString("SOMETHING_ELSE"))
    }

    @Test
    fun themeLoadAction_valuesDeclaredInThemeJs() {
        // theme.js: const BACK_ACTION = "BACK"; ... — каждое значение должно присутствовать.
        for (value in listOf("NORMAL", "BACK", "REFRESH", "END")) {
            assertTrue(
                    "theme.js must reference load-action \"$value\" (JS↔Kotlin contract; " +
                            "see ThemeLoadAction)",
                    themeJs.contains("\"$value\""),
            )
        }
    }

    // ---- InfiniteDirection / InfiniteState ↔ JS -----------------------------

    @Test
    fun infiniteDirection_jsNamesAreStableAndParse() {
        assertEquals("top", ThemeInfiniteScrollController.InfiniteDirection.TOP.jsName)
        assertEquals("bottom", ThemeInfiniteScrollController.InfiniteDirection.BOTTOM.jsName)
        // from() регистронезависим, неизвестное → null.
        assertEquals(
                ThemeInfiniteScrollController.InfiniteDirection.TOP,
                ThemeInfiniteScrollController.InfiniteDirection.from("TOP"),
        )
        assertEquals(
                ThemeInfiniteScrollController.InfiniteDirection.BOTTOM,
                ThemeInfiniteScrollController.InfiniteDirection.from("bottom"),
        )
        assertNull(ThemeInfiniteScrollController.InfiniteDirection.from("sideways"))
    }

    @Test
    fun infiniteDirection_jsNamesPresentInThemeJs() {
        for (dir in ThemeInfiniteScrollController.InfiniteDirection.values()) {
            assertTrue(
                    "theme.js must reference infinite direction \"${dir.jsName}\" " +
                            "(JS↔Kotlin contract; see InfiniteDirection)",
                    themeJs.contains("\"${dir.jsName}\""),
            )
        }
    }

    @Test
    fun infiniteState_jsNamesAreStableAndPresentInThemeJs() {
        assertEquals("idle", ThemeInfiniteScrollController.InfiniteState.IDLE.jsName)
        assertEquals("loading", ThemeInfiniteScrollController.InfiniteState.LOADING.jsName)
        assertEquals("error", ThemeInfiniteScrollController.InfiniteState.ERROR.jsName)
        for (state in ThemeInfiniteScrollController.InfiniteState.values()) {
            assertTrue(
                    "theme.js must reference infinite state \"${state.jsName}\" " +
                            "(JS↔Kotlin contract; see InfiniteState)",
                    themeJs.contains("\"${state.jsName}\""),
            )
        }
    }

    // ---- numeric mirror: unread anchor guard --------------------------------

    @Test
    fun unreadAnchorGuardMs_mirrorsKotlinConstant() {
        val match = Regex("""UNREAD_ANCHOR_GUARD_MAX_MS\s*=\s*(\d+)""").find(themeJs)
                ?: error("theme.js must declare UNREAD_ANCHOR_GUARD_MAX_MS = <ms> " +
                        "(mirror of ThemeUnreadHybridAnchorGuardPolicy.ANCHOR_GUARD_MAX_BLOCK_MS)")
        val jsMs = match.groupValues[1].toLong()
        assertEquals(
                "theme.js UNREAD_ANCHOR_GUARD_MAX_MS must equal Kotlin ANCHOR_GUARD_MAX_BLOCK_MS",
                ThemeUnreadHybridAnchorGuardPolicy.ANCHOR_GUARD_MAX_BLOCK_MS,
                jsMs,
        )
    }
}
