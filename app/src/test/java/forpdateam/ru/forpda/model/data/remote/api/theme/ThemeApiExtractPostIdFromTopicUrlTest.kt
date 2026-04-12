package forpdateam.ru.forpda.model.data.remote.api.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeApiExtractPostIdFromTopicUrlTest {

    @Test
    fun extractsLastPWhenMultiple() {
        assertEquals(
                "222",
                ThemeApi.extractPostIdFromTopicUrl(
                        "https://4pda.to/forum/index.php?showtopic=1&p=111&p=222"
                )
        )
    }

    @Test
    fun extractsHighlightWhenNoP() {
        assertEquals(
                "98765",
                ThemeApi.extractPostIdFromTopicUrl(
                        "https://4pda.to/forum/index.php?showtopic=1&st=400&highlight=98765"
                )
        )
    }

    @Test
    fun extractsEntryFragment() {
        assertEquals(
                "555",
                ThemeApi.extractPostIdFromTopicUrl(
                        "https://4pda.to/forum/index.php?showtopic=1&st=20#entry555"
                )
        )
    }

    @Test
    fun pPreferredOverHighlight() {
        assertEquals(
                "100",
                ThemeApi.extractPostIdFromTopicUrl(
                        "https://4pda.to/forum/index.php?showtopic=1&highlight=200&p=100"
                )
        )
    }

    @Test
    fun returnsNullForPlainTopicUrl() {
        assertNull(
                ThemeApi.extractPostIdFromTopicUrl(
                        "https://4pda.to/forum/index.php?showtopic=1&st=40"
                )
        )
    }

    @Test
    fun scrollExtractorPrefersEntryFragmentOverHighlight() {
        assertEquals(
                "142880340",
                ThemeApi.extractScrollPostIdFromFinalTopicUrl(
                        "https://4pda.to/forum/index.php?showtopic=1&st=20740&highlight=135617646#entry142880340"
                )
        )
    }

    @Test
    fun scrollExtractorUsesHighlightWhenNoFragment() {
        assertEquals(
                "98765",
                ThemeApi.extractScrollPostIdFromFinalTopicUrl(
                        "https://4pda.to/forum/index.php?showtopic=1&st=400&highlight=98765"
                )
        )
    }

    @Test
    fun scrollExtractorPPriorityOverHighlightWhenNoFragment() {
        assertEquals(
                "100",
                ThemeApi.extractScrollPostIdFromFinalTopicUrl(
                        "https://4pda.to/forum/index.php?showtopic=1&highlight=200&p=100"
                )
        )
    }
}
