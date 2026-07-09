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

    @Test
    fun stripAttachmentSpoiler_removesBlockWithYoTitle() {
        val html = """<div class="post-block spoil"><div class="block-title">Прикреплённые файлы</div><div class="block-body">t</div></div>"""
        assertFalse(stripAttachmentSpoilerBlocksFromPostHtml(html).contains("spoil"))
    }

    /** Обычный спойлер пользователя с attach-картинкой: `alt="Прикрепленное изображение"` — не повод его сносить. */
    @Test
    fun stripAttachmentSpoiler_keepsUserSpoilerHoldingAttachImage() {
        val html = """<div class="post-block spoil close"><div class="block-title">скрины</div><div class="block-body"><a href="https://4pda.to/forum/dl/post/1/a.jpg"><img src="https://4pda.to/s/t.jpg" class="attach" alt="Прикрепленное изображение" /></a></div></div>"""
        val out = stripAttachmentSpoilerBlocksFromPostHtml(html)
        assertTrue(out.contains("скрины"))
        assertTrue(out.contains("https://4pda.to/s/t.jpg"))
    }
}
