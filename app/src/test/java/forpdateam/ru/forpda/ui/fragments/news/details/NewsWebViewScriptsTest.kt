package forpdateam.ru.forpda.ui.fragments.news.details

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NewsWebViewScriptsTest {

    @Test
    fun pollBind_isSelfInvokingAndProbesRoot() {
        val js = NewsWebViewScripts.pollBind()
        assertTrue(js.startsWith("(function(){"))
        assertTrue(js.trimEnd().endsWith("})();"))
        assertTrue(js.contains("news-poll-normalized"))
        assertTrue(js.contains("pollRootFound"))
    }

    @Test
    fun bindCommentsSection_inlinesGenerationAndQuotesDomState() {
        val js = NewsWebViewScripts.bindCommentsSection(collapsed = true, domState = "loaded", generation = 77)
        assertTrue(js.contains("var collapsed=true;"))
        assertTrue(js.contains("var generation=77;"))
        // domState должен быть заквочен как JS-строка, а не вставлен голым.
        assertTrue(js.contains("var domState=\"loaded\";"))
    }

    @Test
    fun bindCommentsSection_escapesHostileDomState() {
        val js = NewsWebViewScripts.bindCommentsSection(collapsed = false, domState = "a\"b", generation = 1)
        // Кавычка внутри значения не должна разрывать JS-литерал.
        assertFalse(js.contains("var domState=\"a\"b\";"))
        assertTrue(js.contains("""var domState="a\"b";"""))
    }

    @Test
    fun inlineCommentsState_quotesStateAndMessage_andHandlesNullHtml() {
        val js = NewsWebViewScripts.inlineCommentsState(
                state = "loaded",
                message = "",
                html = null,
                scrollToCommentId = 15,
        )
        assertTrue(js.contains("newsInlineCommentsSetState(\"loaded\",\"\",null,15)"))
        assertTrue(js.contains("\"loaded\" === \"loaded\""))
    }

    @Test
    fun inlineCommentsState_quotesHtmlPayloadWhenPresent() {
        val js = NewsWebViewScripts.inlineCommentsState(
                state = "loaded",
                message = "",
                html = "<article>x</article>",
                scrollToCommentId = 0,
                canLoadMore = true,
                totalCount = 9,
                renderedCount = 4,
        )
        // JSONObject.quote HTML-безопасно экранирует "</" → "<\/", поэтому проверяем открывающий тег.
        assertTrue(js.contains("<article>x"))
        assertTrue(js.contains("newsInlineCommentsUpdateLoadMore(true, 9, 4)"))
    }
}
