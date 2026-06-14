package forpdateam.ru.forpda.ui.fragments.news.details

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ArticleCommentsAdapterTest {

    @Test
    fun `comment content is unchanged for edited comments`() {
        val renderedText = ArticleCommentsAdapter.formatCommentContent("Body text", true)

        assertEquals("Body text", renderedText.toString())
    }

    @Test
    fun `unedited comment content is unchanged`() {
        val renderedText = ArticleCommentsAdapter.formatCommentContent("Body text", false)

        assertEquals("Body text", renderedText.toString())
    }

    @Test
    fun `edited marker is shown in metadata row`() {
        assertEquals(View.VISIBLE, ArticleCommentsAdapter.editedMarkerVisibility(true))
        assertEquals(View.GONE, ArticleCommentsAdapter.editedMarkerVisibility(false))
    }
}
