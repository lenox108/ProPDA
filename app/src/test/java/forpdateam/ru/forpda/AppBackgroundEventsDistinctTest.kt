package forpdateam.ru.forpda

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R5 (perf(events)): [App.setupBackgroundEventsCheck] объединяет три flow
 * через [kotlinx.coroutines.flow.combine] и на каждой эмиссии перепланирует
 * `EventsCheckWorker`. Чтобы избежать лишних ре-планирований при дублях
 * (например, если источник flow поменяется в будущем и начнёт эмитить
 * чаще), каждый из трёх flow обёрнут в [kotlinx.coroutines.flow.distinctUntilChanged].
 *
 * Тест source-level: проверяет, что в методе есть три вызова
 * `.distinctUntilChanged()` ДО [kotlinx.coroutines.flow.combine].
 */
class AppBackgroundEventsDistinctTest {

    @Test
    fun setupBackgroundEventsCheck_wrapsEachFlowInDistinctUntilChanged() {
        val body = readAppSource()
        val method = extractMethodBody(body, "setupBackgroundEventsCheck")
        val distinctCount = Regex("""\.distinctUntilChanged\(\)""").findAll(method).count()
        assertTrue(
            "ожидалось 3 вызова .distinctUntilChanged() в setupBackgroundEventsCheck; " +
                    "найдено $distinctCount\n$method",
            distinctCount >= 3
        )
        // Каждый из трёх flow внутри combine(...) должен иметь свой .distinctUntilChanged().
        val combineOpen = method.indexOf("combine(")
        val combineClose = method.indexOf(") { _, _, _ -> }", combineOpen)
        assertTrue(
            "не нашёл открывающую/закрывающую скобки combine(...) в методе:\n$method",
            combineOpen >= 0 && combineClose > combineOpen
        )
        val combineBlock = method.substring(combineOpen, combineClose)
        for (flow in listOf(
            "mainEnabledFlow().distinctUntilChanged()",
            "bgCheckEnabledFlow().distinctUntilChanged()",
            "bgCheckIntervalMinFlow().distinctUntilChanged()"
        )) {
            assertTrue(
                "в combine-блоке должен встречаться `$flow`; " +
                        "combine-блок:\n$combineBlock",
                combineBlock.contains(flow)
            )
        }
    }

    private fun readAppSource(): String {
        val file = java.io.File("src/main/java/forpdateam/ru/forpda/App.kt")
        check(file.exists()) { "App.kt не найден: ${file.absolutePath}" }
        return file.readText()
    }

    private fun extractMethodBody(body: String, name: String): String {
        val sig = Regex("""\bprivate\s+fun\s+${Regex.escape(name)}\s*\([^)]*\)\s*\{""").find(body)
            ?: error("метод $name не найден")
        val start = sig.range.last + 1
        var depth = 1
        var i = start
        while (i < body.length && depth > 0) {
            when (body[i]) {
                '{' -> depth++
                '}' -> depth--
            }
            i++
        }
        return body.substring(start, i - 1)
    }
}
