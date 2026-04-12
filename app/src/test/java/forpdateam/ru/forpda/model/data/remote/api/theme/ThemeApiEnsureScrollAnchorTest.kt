package forpdateam.ru.forpda.model.data.remote.api.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.entity.remote.theme.ThemePost
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemeApiEnsureScrollAnchorTest {

    @Test
    fun usesDataPostWhenNoEntryAnchor() {
        val page = ThemePage()
        page.url = "https://4pda.to/forum/index.php"
        val html = """<div data-post="999888777" class=""><span>текст</span></div>"""
        ThemeApi.ensureScrollAnchorForPostedPage(page, html)
        assertEquals("entry999888777", page.anchor)
    }

    @Test
    fun prefersLastEntryAnchorOverDataPost() {
        val page = ThemePage()
        page.url = "https://4pda.to/forum/index.php"
        val html = """<a name="entry100"></a>...<div data-post="200"></div><a name="entry300"></a>"""
        ThemeApi.ensureScrollAnchorForPostedPage(page, html)
        assertEquals("entry300", page.anchor)
    }

    @Test
    fun usesPFromUrlBeforeHtml() {
        val page = ThemePage()
        page.url = "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=555"
        val html = """<a name="entry999"></a>"""
        ThemeApi.ensureScrollAnchorForPostedPage(page, html)
        assertEquals("entry555", page.anchor)
    }

    @Test
    fun usesLastPostWhenNoUrlOrHtmlAnchors() {
        val page = ThemePage()
        page.url = "https://4pda.to/forum/index.php?showtopic=1"
        val post = ThemePost()
        post.id = 42
        page.posts.add(post)
        ThemeApi.ensureScrollAnchorForPostedPage(page, "<div>empty</div>")
        assertEquals("entry42", page.anchor)
    }
}
