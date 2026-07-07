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
    fun spoiler_isNativeSpoiler_collapsedByDefault_withTitleAndInner() {
        val blocks = renderer.render(fixture("spoiler_basic.html"))
        // The leading "<span>Шаблоны…</span>" text renders natively.
        assertTrue(blocks.first() is BodyBlock.Text)
        val spoiler = blocks.filterIsInstance<BodyBlock.Spoiler>().single()
        assertEquals("Копируем содержимое в свое сообщение и заполняем", spoiler.title)
        // Fixture class is "spoil close" → collapsed initially.
        assertTrue(!spoiler.initiallyOpen)
        // Inner content is rendered (the "Шаблон для модификаций:" text).
        assertTrue(spoiler.inner.any { it is BodyBlock.Text && it.html.contains("Шаблон для модификаций") })
    }

    @Test
    fun code_isNativeCode_withDecodedTextAndLineBreaks() {
        val blocks = renderer.render(fixture("code_block.html"))
        val code = blocks.filterIsInstance<BodyBlock.Code>().single()
        // HTML entities are decoded (&#91; → [) and <br> became newlines.
        assertTrue("brackets decoded", code.text.contains("[CENTER]"))
        assertTrue("not raw entity", !code.text.contains("&#91;"))
        assertTrue("has line breaks", code.text.contains("\n"))
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
    fun gallery_twoImages_areTwoNativeImageBlocks() {
        val blocks = renderer.render(fixture("gallery_two_images.html"))
        val images = blocks.filterIsInstance<BodyBlock.Image>()
        assertEquals(2, images.size)
        assertTrue(images[0].imageUrl.endsWith("one_thumb.jpg"))
        assertTrue(images[1].imageUrl.endsWith("two_thumb.jpg"))
        // Each image's tap target is its own enclosing <a> (via img.closest), not a shared one.
        assertTrue(images[0].linkUrl?.endsWith("/1/one.jpg") == true)
        assertTrue(images[1].linkUrl?.endsWith("/2/two.jpg") == true)
    }

    @Test
    fun attachmentImageLinked_isNativeSpoiler_containingTheImage() {
        // img.linked-image lives inside a .post-block.spoil "Прикрепленные файлы": the spoiler is now
        // native; the linked image rides as inline HTML in the spoiler's inner content (gallery-in-
        // spoiler as native images is a later step). Here we only guarantee content is never dropped.
        val blocks = renderer.render(fixture("attachment_image_linked.html"))
        assertFalse("content must never be dropped", blocks.isEmpty())
        val spoiler = blocks.filterIsInstance<BodyBlock.Spoiler>().single()
        assertEquals("Прикрепленные файлы", spoiler.title)
    }

    @Test
    fun attachmentFilePlain_isNativeFileAttachment() {
        val blocks = renderer.render(fixture("attachment_file_plain.html"))
        val file = blocks.filterIsInstance<BodyBlock.FileAttachment>().single()
        assertEquals("firmware.zip", file.name)
        assertTrue(file.url.contains("/forum/dl/post/"))
    }

    @Test
    fun table_isNativeTable_withRowsAndCells() {
        val blocks = renderer.render(fixture("table_basic.html"))
        // Leading + trailing text run natively; the table is a native Table block (not fallback).
        assertTrue(blocks.first() is BodyBlock.Text)
        val table = blocks.filterIsInstance<BodyBlock.Table>().single()
        // 3 rows (header + 2 data), 2 cells each.
        assertEquals(3, table.rows.size)
        assertTrue(table.rows.all { it.size == 2 })
        assertEquals("Модель", table.rows[0][0])
        assertEquals("7300 мА·ч", table.rows[1][1])
        // No content is dropped: no TABLE fallback remains.
        assertFalse(blocks.any { it is BodyBlock.WebFallback && it.kind == Kind.TABLE })
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
        assertTrue("hat's attach image is a native Image block", blocks.any { it is BodyBlock.Image })
        assertTrue("hat's spoilers are native", blocks.any { it is BodyBlock.Spoiler })
        // Native code blocks are nested INSIDE the spoilers now.
        val codeNestedInSpoiler = blocks.filterIsInstance<BodyBlock.Spoiler>()
                .flatMap { it.inner }
                .any { it is BodyBlock.Code }
        assertTrue("hat has a native code block nested in a spoiler", codeNestedInSpoiler)
    }

    @Test
    fun malformedHtml_doesNotThrow_andKeepsContent() {
        // Graceful degradation is a hard requirement (§6): never crash, never lose content.
        val blocks = renderer.render(fixture("malformed.html"))
        assertFalse(blocks.isEmpty())
        val recombined = blocks.joinToString("") {
            when (it) {
                is BodyBlock.Text -> it.html
                is BodyBlock.EditNote -> it.html
                is BodyBlock.WebFallback -> it.html
                is BodyBlock.Image -> it.imageUrl
                is BodyBlock.Quote -> it.inner.filterIsInstance<BodyBlock.Text>().joinToString("") { t -> t.html }
                is BodyBlock.Spoiler -> it.inner.filterIsInstance<BodyBlock.Text>().joinToString("") { t -> t.html }
                is BodyBlock.Code -> it.text
                is BodyBlock.FileAttachment -> it.name
                is BodyBlock.Table -> it.rows.joinToString(" ") { row -> row.joinToString(" ") }
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
