package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import forpdateam.ru.forpda.ui.fragments.theme.nativerender.BodyBlock.WebFallback.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden-corpus tests for [PostBodyRenderer] (roadmap `native-topic-renderer.md`, §5 Фаза 0/1).
 * Fixtures live in `app/src/test/resources/renderer/postbody/` — see `PROVENANCE.md` there
 * for the authenticity of each. Pure JVM (Jsoup only), no Robolectric.
 */
class PostBodyRendererTest {

    private val renderer = PostBodyRenderer()

    @Test
    fun emptyAndBlank_produceNoBlocks() {
        assertEquals(emptyList<BodyBlock>(), renderer.render(null))
        assertEquals(emptyList<BodyBlock>(), renderer.render(""))
        assertEquals(emptyList<BodyBlock>(), renderer.render("   \n  "))
    }

    @Test
    fun pureText_isASingleNativeTextBlock() {
        val blocks = renderer.render(fixture("text_inline_basic.html"))
        // No complex blocks → the whole body coalesces into exactly one Text block.
        assertEquals(1, blocks.size)
        val text = blocks.single()
        assertTrue(text is BodyBlock.Text)
        // Inline markup is preserved verbatim for later Spannable conversion.
        assertTrue((text as BodyBlock.Text).html.contains("<b>Жирный</b>"))
        assertTrue(text.html.contains("<ul>"))
    }

    @Test
    fun nestedQuote_isNativeQuote_withAuthorDateAndNestedInner() {
        val blocks = renderer.render(fixture("quote_nested.html"))
        // "Согласен…" (text) + native quote + "Мой ответ…" (text) = 3 blocks.
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is BodyBlock.Text)
        assertTrue(blocks[2] is BodyBlock.Text)
        val quote = blocks[1] as BodyBlock.Quote
        assertEquals("OuterNick", quote.author)
        assertEquals("21.05.2026, 14:30", quote.date)
        // The inner content is recursively rendered: some text + the NESTED quote (native, not fallback).
        val nested = quote.inner.filterIsInstance<BodyBlock.Quote>().single()
        assertEquals("InnerNick", nested.author)
        assertTrue(nested.inner.any { it is BodyBlock.Text && it.html.contains("Вложенная цитата") })
    }

    @Test
    fun spoiler_isOneSpoilerFallback() {
        val blocks = renderer.render(fixture("spoiler_basic.html"))
        assertTrue("expected at least one spoiler fallback", blocks.any {
            it is BodyBlock.WebFallback && it.kind == Kind.SPOILER
        })
        // The leading "<span>Шаблоны…</span>" text renders natively.
        assertTrue(blocks.first() is BodyBlock.Text)
    }

    @Test
    fun code_isOneCodeFallback() {
        val blocks = renderer.render(fixture("code_block.html"))
        assertTrue(blocks.any { it is BodyBlock.WebFallback && it.kind == Kind.CODE })
    }

    @Test
    fun attachmentImageTable_isNativeImageBlock_withDimensionsAndLink() {
        val blocks = renderer.render(fixture("attachment_image_table.html"))
        // Фаза 2: a single-image attachment is peeled out to a native Image block (not fallback).
        val image = blocks.filterIsInstance<BodyBlock.Image>().single()
        assertTrue(image.imageUrl.startsWith("https://4pda.to/s/"))
        // Dimensions come from the img width/height attrs (376x500) → aspect reserved before load.
        assertEquals(376, image.displayWidthPx)
        assertEquals(500, image.displayHeightPx)
        // Tap target is the enclosing dl/post attachment link.
        assertTrue(image.linkUrl?.contains("/forum/dl/post/") == true)
    }

    @Test
    fun attachmentImageLinked_staysSpoilerFallback_forNow() {
        // img.linked-image lives inside a .post-block.spoil "Прикрепленные файлы": the top-level
        // node is a SPOILER, so Фаза-2 single-image extraction (ATTACHMENT-kind only) does not
        // apply — it stays a fallback. Gallery-in-spoiler is a later, separate step; here we only
        // guarantee content is never dropped.
        val blocks = renderer.render(fixture("attachment_image_linked.html"))
        assertFalse("content must never be dropped", blocks.isEmpty())
        assertTrue(blocks.any { it is BodyBlock.WebFallback && it.kind == Kind.SPOILER })
    }

    @Test
    fun attachmentFilePlain_isAttachmentFallback() {
        val blocks = renderer.render(fixture("attachment_file_plain.html"))
        assertTrue(blocks.any { it is BodyBlock.WebFallback && it.kind == Kind.ATTACHMENT })
    }

    @Test
    fun poll_isPollFallback() {
        val blocks = renderer.render(fixture("poll_UNVERIFIED.html"))
        assertTrue(blocks.any { it is BodyBlock.WebFallback && it.kind == Kind.POLL })
    }

    @Test
    fun topicHat_mixesNativeTextWithBlockFallbacks_andNeverDropsContent() {
        val blocks = renderer.render(fixture("topic_hat.html"))
        // The hat has native intro text plus multiple complex blocks (attach table, spoilers/code).
        assertTrue("hat should have native text runs", blocks.any { it is BodyBlock.Text })
        assertTrue("hat should have fallback blocks", blocks.any { it is BodyBlock.WebFallback })
        assertTrue("hat's attach image is a native Image block", blocks.any { it is BodyBlock.Image })
        assertTrue("hat has spoilers", blocks.any {
            it is BodyBlock.WebFallback && it.kind == Kind.SPOILER
        })
    }

    @Test
    fun malformedHtml_doesNotThrow_andKeepsContent() {
        // Graceful degradation is a hard requirement (§6): never crash, never lose content.
        val blocks = renderer.render(fixture("malformed.html"))
        assertFalse(blocks.isEmpty())
        val recombined = blocks.joinToString("") {
            when (it) {
                is BodyBlock.Text -> it.html
                is BodyBlock.WebFallback -> it.html
                is BodyBlock.Image -> it.imageUrl
                is BodyBlock.Quote -> it.inner.filterIsInstance<BodyBlock.Text>().joinToString("") { t -> t.html }
            }
        }
        assertTrue("recognisable text survives", recombined.contains("Незакрытый жирный текст"))
    }

    private fun assertFallback(block: BodyBlock, kind: Kind) {
        assertTrue("expected WebFallback but was $block", block is BodyBlock.WebFallback)
        assertEquals(kind, (block as BodyBlock.WebFallback).kind)
    }

    private fun fixture(name: String): String {
        val path = "renderer/postbody/$name"
        return javaClass.classLoader?.getResource(path)?.readText()
                ?: error("Missing test resource $path")
    }
}
