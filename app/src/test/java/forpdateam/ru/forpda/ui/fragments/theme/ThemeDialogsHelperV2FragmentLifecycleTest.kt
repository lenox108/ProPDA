package forpdateam.ru.forpda.ui.fragments.theme

import forpdateam.ru.forpda.ui.fragments.search.SearchFragment
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Regression guard for a production crash:
 *
 *   java.lang.IllegalStateException: Can't access the Fragment View's
 *   LifecycleOwner for ThemeFragmentWeb ... when getView() is null i.e.,
 *   before onCreateView() or after onDestroyView()
 *       at forpdateam.ru.forpda.ui.fragments.theme.ThemeFragment.onCreate
 *       at androidx.fragment.app.Fragment.performCreate
 *       ...
 *       at forpdateam.ru.forpda.presentation.TabRouter.navigateTo
 *       at forpdateam.ru.forpda.presentation.favorites.FavoritesViewModel.onItemClick
 *
 * Root cause: [ThemeDialogsHelper_V2] requires a `scope` bound to the host
 * view's lifecycle (see its KDoc), but the helper was being constructed from
 * `ThemeFragment.onCreate` and `SearchFragment.onCreate` — at which point
 * `viewLifecycleOwner` does not exist yet and `Fragment.lifecycleScope` (in
 * AndroidX 1.6+) internally calls `getViewLifecycleOwner()` and throws.
 *
 * The fix moves construction to `onViewCreated()` and makes the field
 * nullable. This test pins the source-level invariant: the `onCreate` of
 * the affected Fragments must NOT touch `viewLifecycleOwner` and must NOT
 * call the `ThemeDialogsHelper_V2` constructor.
 */
class ThemeDialogsHelperV2FragmentLifecycleTest {

    @Test
    fun themeFragment_onCreate_doesNotTouchViewLifecycleOrConstructHelper() {
        val body = readSource(
            "src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeFragment.kt"
        )
        val onCreate = extractOverrideFunction(body, "onCreate")
        assertNoViewLifecycleAccess("ThemeFragment.onCreate", onCreate)
        assertNoHelperConstruction("ThemeFragment.onCreate", onCreate)
    }

    @Test
    fun searchFragment_onCreate_doesNotTouchViewLifecycleOrConstructHelper() {
        val body = readSource(
            "src/main/java/forpdateam/ru/forpda/ui/fragments/search/SearchFragment.kt"
        )
        val onCreate = extractOverrideFunction(body, "onCreate")
        assertNoViewLifecycleAccess("SearchFragment.onCreate", onCreate)
        assertNoHelperConstruction("SearchFragment.onCreate", onCreate)
    }

    @Test
    fun themeFragment_declaresDialogsHelperAsNullable() {
        // If the field is `lateinit` again, the safe-call sites would not
        // compile, so this test would catch a future revert of the fix.
        val body = readSource(
            "src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeFragment.kt"
        )
        val regex = Regex(
            """\bdialogsHelper\s*:\s*ThemeDialogsHelper_V2\b(?!\s*\?)"""
        )
        assertFalse(
            "ThemeFragment.dialogsHelper must be declared nullable " +
                "(ThemeDialogsHelper_V2?) so that the helper can be created " +
                "lazily in onViewCreated() and safely absent between " +
                "onDestroyView() and the next onViewCreated().",
            regex.containsMatchIn(body)
        )
    }

    @Test
    fun searchFragment_declaresDialogsHelperAsNullable() {
        val body = readSource(
            "src/main/java/forpdateam/ru/forpda/ui/fragments/search/SearchFragment.kt"
        )
        val regex = Regex(
            """\bdialogsHelper\s*:\s*ThemeDialogsHelper_V2\b(?!\s*\?)"""
        )
        assertFalse(
            "SearchFragment.dialogsHelper must be declared nullable " +
                "(ThemeDialogsHelper_V2?) so that the helper can be created " +
                "lazily in onViewCreated() and safely absent between " +
                "onDestroyView() and the next onViewCreated().",
            regex.containsMatchIn(body)
        )
    }

    private fun readSource(relativePath: String): String {
        // Resolve the source file from the test working directory. The unit
        // test Gradle task sets the project dir as the working directory.
        val candidates = listOf(
            java.io.File("app/$relativePath"),
            java.io.File(relativePath),
        )
        return candidates.firstOrNull { it.exists() }
            ?.readText(Charsets.UTF_8)
            ?: error("Could not locate source file: $relativePath")
    }

    /**
     * Extracts the body of an `override fun NAME(...)` function from the
     * given Kotlin source text, with line and block comments and string
     * literals stripped so that explanatory comments and message strings
     * (which legitimately mention "viewLifecycleOwner" or
     * "ThemeDialogsHelper_V2") do not trigger the assertions. Returns the
     * substring between the opening brace and its matching closing brace
     * at depth 0. The implementation is intentionally tiny — we only need
     * a quick check, not a full Kotlin parser.
     */
    private fun extractOverrideFunction(body: String, name: String): String {
        val signature = Regex("""override\s+fun\s+${Regex.escape(name)}\s*\(""")
        val match = signature.find(body) ?: error("No override fun $name( found")
        var depth = 0
        var started = false
        val start = match.range.last
        for (i in start until body.length) {
            val c = body[i]
            if (c == '{') {
                if (!started) started = true
                depth++
            } else if (c == '}') {
                depth--
                if (started && depth == 0) {
                    return stripCommentsAndStrings(body.substring(start, i + 1))
                }
            }
        }
        error("Unterminated override fun $name( in source")
    }

    /**
     * Replaces the contents of `//` line comments, `/* */` block comments
     * and `"..."` string literals with spaces. Newlines are preserved so
     * that line-based diagnostics still work.
     */
    private fun stripCommentsAndStrings(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        val n = source.length
        while (i < n) {
            val c = source[i]
            val next = if (i + 1 < n) source[i + 1] else ' '
            when {
                c == '/' && next == '/' -> {
                    // Line comment: copy up to (but not including) the newline.
                    while (i < n && source[i] != '\n') {
                        out.append(' ')
                        i++
                    }
                }
                c == '/' && next == '*' -> {
                    // Block comment.
                    out.append(' ').append(' ')
                    i += 2
                    while (i < n - 1 && !(source[i] == '*' && source[i + 1] == '/')) {
                        out.append(if (source[i] == '\n') '\n' else ' ')
                        i++
                    }
                    if (i < n - 1) {
                        out.append(' ').append(' ')
                        i += 2
                    }
                }
                c == '"' -> {
                    // String literal: handle basic \" and \\ escapes; we only
                    // need to skip the contents, not parse them.
                    out.append(' ')
                    i++
                    while (i < n && source[i] != '"') {
                        if (source[i] == '\\' && i + 1 < n) {
                            out.append(' ').append(' ')
                            i += 2
                        } else {
                            out.append(if (source[i] == '\n') '\n' else ' ')
                            i++
                        }
                    }
                    if (i < n) {
                        out.append(' ')
                        i++
                    }
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }

    private fun assertNoViewLifecycleAccess(label: String, body: String) {
        val access = Regex("""\bviewLifecycleOwner\b""")
        assertFalse(
            "$label must not reference viewLifecycleOwner — the view " +
                "lifecycle does not exist before onCreateView().",
            access.containsMatchIn(body)
        )
    }

    private fun assertNoHelperConstruction(label: String, body: String) {
        val ctor = Regex("""\bThemeDialogsHelper_V2\s*\(""")
        assertFalse(
            "$label must not construct ThemeDialogsHelper_V2 — that helper " +
                "requires a view-lifecycle-bound scope and must be created " +
                "from onViewCreated().",
            ctor.containsMatchIn(body)
        )
    }
}
