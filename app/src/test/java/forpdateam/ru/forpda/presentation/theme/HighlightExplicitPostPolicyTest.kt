package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HighlightExplicitPostPolicyTest {

    @Test
    fun scrollAnchor_isNotExplicitHighlight() {
        val page = ThemePage().apply {
            anchorPostId = "143904045"
            url = "https://4pda.to/forum/index.php?showtopic=1103268&st=1260"
        }
        assertNull(
                HighlightExplicitPostPolicy.resolveExplicitPostId(
                        page = page,
                        openedViaFindPost = false,
                        requestUrl = page.url,
                )
        )
    }

    @Test
    fun findPostOpen_isExplicitHighlight() {
        val page = ThemePage().apply {
            anchorPostId = "143904045"
            url = "https://4pda.to/forum/index.php?showtopic=1103268&view=findpost&p=143904045"
        }
        assertEquals(
                143904045L,
                HighlightExplicitPostPolicy.resolveExplicitPostId(
                        page = page,
                        openedViaFindPost = true,
                        requestUrl = page.url,
                )
        )
    }
}
