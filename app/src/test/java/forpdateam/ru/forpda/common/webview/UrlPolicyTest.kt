package forpdateam.ru.forpda.common.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {

    @Test
    fun classify_blocksNullBlankAndControlCharacters() {
        assertBlocked(null)
        assertBlocked("")
        assertBlocked("   ")
        assertBlocked("https://4pda.to/\njavascript:alert(1)")
        assertBlocked("https://4pda.to/\u0000path")
    }

    @Test
    fun classify_blocksDangerousSchemes() {
        assertBlocked("javascript:alert(1)")
        assertBlocked(" javaScript:alert(1)")
        assertBlocked("file:///sdcard/Download/a.txt")
        assertBlocked("data:text/html,<script>alert(1)</script>")
        assertBlocked("content://com.example/item")
    }

    @Test
    fun classify_blocksEncodedNewlineSchemeTricks() {
        assertBlocked("%0Ajavascript:alert(1)")
        assertBlocked("https://4pda.to/%0d%0ajavascript:alert(1)")
        assertBlocked(" https://4pda.to/%09javascript:alert(1) ")
    }

    @Test
    fun classify_blocksEncodedDangerousSchemePrefixes() {
        assertBlocked("javascript%3Aalert(1)")
        assertBlocked("%6a%61%76%61%73%63%72%69%70%74:alert(1)")
        assertBlocked("%66%69%6c%65:///sdcard/Download/a.txt")
        assertBlocked("%64%61%74%61:text/html,<script>alert(1)</script>")
        assertBlocked("%63%6f%6e%74%65%6e%74://com.example/item")
    }

    @Test
    fun classify_marksFourPdaHostsInternal() {
        assertInternal("https://4pda.to/forum/index.php?showtopic=1")
        assertInternal("http://4pda.to/forum/")
        assertInternal("https://www.4pda.to/news/")
        assertInternal("https://forum.4pda.to/topic")
        assertInternal("https://4pda.ru/forum/index.php?showtopic=1")
        assertInternal("https://www.4pda.ru/forum/index.php?showtopic=1")
    }

    @Test
    fun classify_marksHttpHttpsNonFourPdaExternal() {
        assertExternal("https://example.com/path")
        assertExternal("http://example.com/path")
    }

    @Test
    fun classify_allowsMailtoAsExternal() {
        assertExternal("mailto:support@example.com")
    }

    @Test
    fun classify_blocksUnknownSchemesFromContent() {
        assertBlocked("intent://scan/#Intent;scheme=zxing;end")
        assertBlocked("market://details?id=com.example")
        assertBlocked("tg://resolve?domain=test")
    }

    private fun assertInternal(url: String) {
        val decision = UrlPolicy.classify(url)
        assertTrue(decision is UrlDecision.Internal)
        assertEquals(url, (decision as UrlDecision.Internal).normalizedUrl)
    }

    private fun assertExternal(url: String) {
        val decision = UrlPolicy.classify(url)
        assertTrue(decision is UrlDecision.External)
        assertEquals(url, (decision as UrlDecision.External).normalizedUrl)
    }

    private fun assertBlocked(url: String?) {
        assertTrue(UrlPolicy.classify(url) is UrlDecision.Blocked)
    }
}
