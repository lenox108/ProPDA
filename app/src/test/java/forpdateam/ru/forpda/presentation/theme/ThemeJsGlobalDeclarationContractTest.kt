package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * PROBLEM 0 regression guard (field log 24_06-20-37-22_428.log line 10969):
 *
 *   E CustomWebChromeClient: WebConsole:
 *     "Uncaught ReferenceError: elemToActivation is not defined", [theme.js], (2435)
 *
 * `doScroll` READS `elemToActivation` (the post container carrying the legacy
 * `.active` class) on the very first scroll — before any code path has assigned
 * it. An implicit global that is read before its first write throws a
 * ReferenceError, which aborts the whole JS action batch (highlight apply,
 * scroll, etc.). The same class of bug previously shipped twice
 * ([themeAnchorScrollGeneration], [themeAnchorRetryPendingName]).
 *
 * The fix declares every mutable top-level global that is read-before-write
 * up-front with `var X = <default>;`. This tripwire pins those declarations so
 * we never reintroduce the "assigned but never declared" pattern that the field
 * keeps catching. Verified exhaustively by a static scan (no implicit globals
 * remain) and a Node VM harness that runs the cold scroll/activation batch with
 * no ReferenceError.
 */
class ThemeJsGlobalDeclarationContractTest {

    private fun readThemeJs(): String {
        val path: Path = listOf(
                Path.of("src/main/assets/forpda/scripts/modules/theme.js"),
                Path.of("app/src/main/assets/forpda/scripts/modules/theme.js"),
        ).first { Files.exists(it) }
        return Files.newInputStream(path).bufferedReader().readText()
    }

    /**
     * Every mutable top-level global that is read before its first assignment
     * MUST have an explicit `var <name>` declaration so the first read yields
     * `undefined` instead of throwing a ReferenceError.
     */
    private val requiredVarDeclarations = listOf(
            "elemToActivation",
            "anchorElem",
            "themeAnchorScrollGeneration",
            "themeAnchorRetryPendingName",
    )

    @Test
    fun jsSource_declaresAllReadBeforeWriteGlobals() {
        val js = readThemeJs()
        for (name in requiredVarDeclarations) {
            assertTrue(
                    "theme.js must declare top-level global `$name` with `var $name` " +
                            "(read-before-write would otherwise throw ReferenceError, cf. log line 10969)",
                    Regex("(?m)^\\s*var\\s+$name\\b").containsMatchIn(js),
            )
        }
    }

    /**
     * `elemToActivation` specifically: the declaration must exist AND precede the
     * `doScroll` read at the top of the file, not be created lazily by the first
     * assignment inside a function.
     */
    @Test
    fun jsSource_elemToActivationDeclaredBeforeDoScrollRead() {
        val js = readThemeJs()
        val declIndex = Regex("(?m)^\\s*var\\s+elemToActivation\\b").find(js)?.range?.first ?: -1
        assertTrue("elemToActivation must be declared with var", declIndex >= 0)
        val doScrollIndex = js.indexOf("function doScroll(")
        assertTrue("doScroll must exist", doScrollIndex >= 0)
        assertTrue(
                "var elemToActivation must be declared before function doScroll so the " +
                        "first `if (elemToActivation)` read does not ReferenceError",
                declIndex < doScrollIndex,
        )
    }
}
