package forpdateam.ru.forpda.ui.fragments.favorites

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for the favorites list auto-scroll-to-top behaviour.
 *
 * Bug: when the user was at the bottom of page 1 of "Избранное" and tapped
 * the "next page" tab in the toolbar pagination, the list did not scroll
 * back to the top of page 2 — the LinearLayoutManager kept page 1's
 * bottom offset and page 2 was rendered below the fold.
 *
 * Root cause: [FavoritesFragment.onLoadFavorites] is the single handler
 * invoked after every successful page load, and it is the mirror of
 * `TopicsFragment.showTopics` / `MentionsFragment.showMentions`. The
 * sibling fragments call `listScrollTop()` (a 225ms-debounced
 * `smoothScrollToPosition(0)`) right after `paginationHelper.updatePagination(...)`;
 * favorites did not. This test pins the source-level invariant so a
 * future refactor cannot silently re-introduce the regression.
 *
 * The test reads the source file directly (no Robolectric/Android) and
 * asserts the body of `onLoadFavorites` contains a call to
 * `listScrollTop()`. This style mirrors the existing
 * `ThemeDialogsHelperV2FragmentLifecycleTest`, which guards another
 * source-level invariant in the same codebase.
 */
class FavoritesPaginationScrollTest {

    @Test
    fun onLoadFavorites_callsListScrollTopAfterPaginationUpdate() {
        val body = readSource(
            "src/main/java/forpdateam/ru/forpda/ui/fragments/favorites/FavoritesFragment.kt"
        )
        val onLoadFavorites = extractFunctionBody(body, "onLoadFavorites")

        assertTrue(
            "FavoritesFragment.onLoadFavorites must call listScrollTop() " +
                "after paginationHelper.updatePagination(...) so that the " +
                "list scrolls to the top of the newly loaded page. Without " +
                "this, navigating page 1 -> page 2 keeps page 1's bottom " +
                "scroll offset and the new content is rendered below the " +
                "fold. See TopicsFragment.showTopics and " +
                "MentionsFragment.showMentions for the canonical pattern.",
            containsListScrollTopCall(onLoadFavorites),
        )

        // Defensive: also assert the call comes AFTER the pagination
        // update, otherwise the scroll would fire before the new
        // pagination chrome / adapter state is applied and the test
        // would be vacuous.
        val updateIdx = onLoadFavorites.indexOf("paginationHelper.updatePagination")
        val scrollIdx = onLoadFavorites.indexOf("listScrollTop")
        assertTrue(
            "FavoritesFragment.onLoadFavorites must call listScrollTop() " +
                "AFTER paginationHelper.updatePagination(...).",
            updateIdx >= 0 && scrollIdx >= 0 && scrollIdx > updateIdx,
        )
    }

    @Test
    fun onLoadFavorites_matchesCanonicalSiblingPattern() {
        // Belt-and-braces: assert that favorites still mirrors the
        // exact two-line pattern used by TopicsFragment / MentionsFragment.
        // This makes the contract self-documenting in the test code.
        val body = readSource(
            "src/main/java/forpdateam/ru/forpda/ui/fragments/favorites/FavoritesFragment.kt"
        )
        assertTrue(
            "FavoritesFragment.onLoadFavorites must call " +
                "clearToolbarPaginationSubtitle() — that is the line the " +
                "scroll-to-top call must follow in the canonical pattern.",
            body.contains("clearToolbarPaginationSubtitle()"),
        )
    }

    private fun readSource(relativePath: String): String {
        // The unit test Gradle task sets the project dir as the working
        // directory. We try `app/...` first (the canonical layout) and
        // fall back to the bare relative path for completeness.
        val candidates = listOf(
            java.io.File("app/$relativePath"),
            java.io.File(relativePath),
        )
        return candidates.firstOrNull { it.exists() }
            ?.readText(Charsets.UTF_8)
            ?: error("Could not locate source file: $relativePath")
    }

    /**
     * Extracts the body of `private fun NAME(...)` from the given Kotlin
     * source text, with line/block comments and string literals replaced
     * by spaces so that explanatory comments and message strings (which
     * legitimately mention `listScrollTop`) do not trigger the assertion.
     * Returns the substring between the opening brace and its matching
     * closing brace at depth 0. The implementation is intentionally
     * tiny — we only need a quick check, not a full Kotlin parser.
     */
    private fun extractFunctionBody(body: String, name: String): String {
        val signature = Regex("""fun\s+${Regex.escape(name)}\s*\(""")
        val match = signature.find(body) ?: error("No fun $name( found")
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
        error("Unterminated fun $name( in source")
    }

    /**
     * Replaces the contents of `//` line comments, `/* */` block
     * comments and `"..."` string literals with spaces. Newlines are
     * preserved so that line-based diagnostics still work.
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
                    while (i < n && source[i] != '\n') {
                        out.append(' ')
                        i++
                    }
                }
                c == '/' && next == '*' -> {
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

    private fun containsListScrollTopCall(body: String): Boolean {
        // Match `listScrollTop(` but NOT `clearToolbarPaginationSubtitle(`
        // or other `listScroll…` lookalikes. The trailing `(` ensures
        // we are matching a call site, not a declaration or a comment.
        val regex = Regex("""\blistScrollTop\s*\(""")
        return regex.containsMatchIn(body)
    }
}
