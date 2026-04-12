package forpdateam.ru.forpda.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StripAttachmentsUiFromPostHtmlTest {

    @Test
    fun stripAttachments_removesForPdaAttachmentsBlock() {
        val html = """<p>Hello</p><div class="attachments"><div class="title">Прикрепленные файлы</div><div class="scroll_container"><a href="#">x</a></div></div>"""
        val out = stripEmbeddedAttachmentsUiFromPostHtml(html)
        assertTrue(out.contains("Hello"))
        assertFalse(out.contains("Прикрепленные"))
        assertFalse(out.contains("attachments"))
    }

    @Test
    fun stripAttachments_removesBtnsContainer() {
        val html = """<p>t</p><div class="btns_container"><a class="attach_block" href="#">f</a></div>"""
        val out = stripEmbeddedAttachmentsUiFromPostHtml(html)
        assertTrue(out.contains(">t<"))
        assertFalse(out.contains("btns_container"))
    }

    @Test
    fun stripAttachmentSpoiler_removesBlockWithTitle() {
        val html = """<p>x</p><div class="post-block spoil"><div class="block-title">Прикрепленные файлы</div><div class="block-body">t</div></div><p>y</p>"""
        val out = stripAttachmentSpoilerBlocksFromPostHtml(html)
        assertTrue(out.contains("x") && out.contains("y"))
        assertFalse(out.contains("Прикреплен"))
    }
}
