package forpdateam.ru.forpda.model.data.remote.api.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemeApiStripIntentParamsTest {

    @Test
    fun stripsViewGetnewpost() {
        val out = ThemeApi.stripTopicIntentParams(
                "https://4pda.to/forum/index.php?showtopic=1121632&view=getnewpost"
        )
        assertEquals("https://4pda.to/forum/index.php?showtopic=1121632", out)
    }

    @Test
    fun stripsPAndHighlightAndAnchor() {
        val out = ThemeApi.stripTopicIntentParams(
                "https://4pda.to/forum/index.php?showtopic=1&p=222&highlight=33&anchor=entry44"
        )
        assertEquals("https://4pda.to/forum/index.php?showtopic=1", out)
    }

    @Test
    fun preservesNonZeroSt() {
        val out = ThemeApi.stripTopicIntentParams(
                "https://4pda.to/forum/index.php?showtopic=10&st=40&view=getnewpost"
        )
        assertEquals("https://4pda.to/forum/index.php?showtopic=10&st=40", out)
    }

    @Test
    fun dropsZeroSt() {
        val out = ThemeApi.stripTopicIntentParams(
                "https://4pda.to/forum/index.php?showtopic=10&st=0&view=getnewpost"
        )
        assertEquals("https://4pda.to/forum/index.php?showtopic=10", out)
    }

    @Test
    fun dropsFragment() {
        val out = ThemeApi.stripTopicIntentParams(
                "https://4pda.to/forum/index.php?showtopic=10&view=getnewpost#entry55"
        )
        assertEquals("https://4pda.to/forum/index.php?showtopic=10", out)
    }

    @Test
    fun returnsNullWhenAlreadyBare() {
        assertNull(ThemeApi.stripTopicIntentParams("https://4pda.to/forum/index.php?showtopic=10"))
    }

    @Test
    fun returnsNullWithoutTopicId() {
        assertNull(ThemeApi.stripTopicIntentParams("https://4pda.to/forum/index.php?showforum=10"))
    }
}
