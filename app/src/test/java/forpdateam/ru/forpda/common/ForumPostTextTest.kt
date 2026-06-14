package forpdateam.ru.forpda.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumPostTextTest {

    @Test
    fun appendEditedMarkerInline_insertsBeforeClosingParagraph() {
        val html = """<p class="content">Hello world</p>"""
        val marker = """<span class="edited">✎</span>"""
        val out = appendEditedMarkerInline(html, marker)

        assertEquals("""<p class="content">Hello world<span class="edited">✎</span></p>""", out)
    }

    @Test
    fun appendEditedMarkerInline_appendsWhenNoBlockTag() {
        val out = appendEditedMarkerInline("Plain text", "<span>✎</span>")

        assertEquals("Plain text<span>✎</span>", out)
    }

    @Test
    fun stripHtmlQuoteBlocks_removesPostBlockQuoteWithBlockBody() {
        val html = """<div class="post_body">AAA<div class="post-block quote"><div class="block-body">OLDQUOTE</div></div>BBB</div>"""
        val out = stripHtmlQuoteBlocks(html)
        assertTrue(out.contains("AAA"))
        assertTrue(out.contains("BBB"))
        assertFalse(out.contains("OLDQUOTE"))
    }

    @Test
    fun stripHtmlQuoteBlocks_removesBlockquote() {
        val html = """<p>x</p><blockquote>q</blockquote><p>y</p>"""
        val out = stripHtmlQuoteBlocks(html)
        assertFalse(out.contains("blockquote"))
        assertTrue(out.contains("x"))
        assertTrue(out.contains("y"))
    }

    @Test
    fun stripBbcodeQuotes_removesInnermostFirst() {
        val text = """[quote]outer [quote]inner[/quote] tail[/quote]end"""
        val out = stripBbcodeQuotes(text)
        assertEquals("end", out)
    }

    @Test
    fun stripBbcodeQuotes_plainTextUnchanged() {
        val text = "hello world"
        assertEquals("hello world", stripBbcodeQuotes(text))
    }

    @Test
    fun renderBbcodeLineBreakTagsInPostHtml_convertsBrTags() {
        val html = """<p>строка1[br][br]строка2[BR /]строка3</p>"""
        val out = renderBbcodeLineBreakTagsInPostHtml(html)
        assertEquals("""<p>строка1<br><br>строка2<br>строка3</p>""", out)
    }
}
