package forpdateam.ru.forpda

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Regression for AUDIT-L04: the global `App.instance!!` singleton was
 * removed in favour of Hilt-injected `@ApplicationContext`. This test
 * guards against any future re-introduction of the pattern in production
 * code by scanning `app/src/main` for `App.instance` (excluding the
 * well-known migration target `ContextImageLookup` whose KDoc still
 * mentions the old name as historical context).
 *
 * The test is intentionally source-only — it does not instantiate
 * anything. If a contributor re-adds `App.instance = ...` /
 * `App.instance!!` / `val instance: App get() = _instance!!` to
 * production code, this test fails.
 */
class AppInstanceSingletonRemovalTest {

    @Test
    fun noProductionCodeReferencesAppInstance() {
        val violations = mutableListOf<String>()
        val mainSrc = File("src/main/java")
        require(mainSrc.isDirectory) { "expected $mainSrc to be a directory" }

        mainSrc.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val text = file.readText()
                    // Match `App.instance` (property access) — exclude the
                    // companion-object declaration site (App.kt itself, where
                    // the singleton was defined) and the historical-reference
                    // KDoc in ContextImageLookup.
                    if (file.name == "App.kt") return@forEach
                    if (file.name == "ContextImageLookup.kt") return@forEach
                    Regex("""App\.instance\b""").findAll(text).forEach { m ->
                        violations += "${file.path}:${lineOf(text, m.range.first)}: ${m.value}"
                    }
                }

        assertEquals(
                "App.instance is forbidden in production code. Use Hilt " +
                        "@ApplicationContext Context or inject the relevant repository.",
                emptyList<String>(),
                violations
        )
    }

    private fun lineOf(text: String, offset: Int): Int =
            text.substring(0, offset).count { it == '\n' } + 1
}
