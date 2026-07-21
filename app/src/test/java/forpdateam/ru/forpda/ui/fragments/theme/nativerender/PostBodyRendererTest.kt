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
    fun offtopFont_inlineInFlow_becomesSmallGreyText_notASeparateBlock() {
        // 4pda renders [offtop] as an INLINE `<font style="font-size:9px;color:gray;">` in the message flow
        // (not a spoiler/block). It must stay inline text — but rewritten to <small> so Html.fromHtml renders
        // it de-emphasised (it ignores the CSS font-size), keeping the muted grey.
        val blocks = renderer.render(fixture("offtop_basic.html"))
        val text = blocks.filterIsInstance<BodyBlock.Text>().single()
        // font-size:9px is dropped (Html.fromHtml can't apply it) and the font became a <small>.
        assertFalse("CSS font-size must be gone", text.html.contains("font-size"))
        assertTrue("offtop wrapped in <small> so it renders small", text.html.contains("<small"))
        // Named `gray` isn't parsed by Html.fromHtml's USE_CSS_COLORS → we emit a hex muted grey instead.
        assertTrue("muted grey preserved as hex", text.html.contains("color:#808080"))
        assertFalse("named gray replaced", text.html.contains("color:gray"))
        // Content (incl. nested bold) and the surrounding prose all survive in the one native text block.
        assertTrue(text.html.contains("оффтоп"))
        assertTrue(text.html.contains("<b>жирный внутри</b>"))
        assertTrue(text.html.contains("обычный текст"))
    }

    @Test
    fun offtopFont_insideUserSpoiler_keepsSpoiler_bodyRenderedSmall() {
        // A user spoiler that CONTAINS an offtop must stay a collapsible Spoiler (we must NOT unwrap it); the
        // offtop font inside is still normalised to small grey.
        val html = "<div class=\"post-block spoil close\"><div class=\"block-title\">заголовок</div>" +
                "<div class=\"block-body\">до <font style=\"font-size:9px;color:gray;\">офф</font> после</div></div>"
        val blocks = renderer.render(html)
        val spoiler = blocks.filterIsInstance<BodyBlock.Spoiler>().single()
        val inner = spoiler.inner.filterIsInstance<BodyBlock.Text>().single()
        assertTrue(inner.html.contains("<small"))
        assertFalse(inner.html.contains("font-size"))
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
    fun downloadButtonsInsideSpoilerOl_allSurviveAsFileAttachments() {
        // EXACT live structure of post 239158/p144065357 «Паки Тем GO Ex»: a collapsed spoiler whose
        // block-body holds an <ol> of <a class="ipb-attach attach-file"> download buttons (each wrapping an
        // animated gif). The spoiler must render with ALL file chips inside.
        val gif = "https://4pda.to/s/Zy0hRMDmDtR8z09yu8z22rW0oH7HPbiK40M5cDUEPz.gif"
        val html = "<div class=\"post-block spoil close\"><div class=\"block-title\">Паки Тем GO Ex</div>" +
                "<div class=\"block-body\"><ol>" +
                "<li><a class=\"ipb-attach attach-file\" href=\"https://4pda.to/forum/dl/post/1634154/Themes.rar\"><img data-src=\"$gif\">Themes.rar</a></li>" +
                "<li><a class=\"ipb-attach attach-file\" href=\"https://4pda.to/forum/dl/post/1634192/Official_Themes.rar\"><img data-src=\"$gif\">Official_Themes.rar</a></li>" +
                "<li><a class=\"ipb-attach attach-file\" href=\"https://4pda.to/forum/dl/post/1634195/PREMIUM_Themes.zip\"><img data-src=\"$gif\">PREMIUM_Themes.zip</a></li>" +
                "<li><a class=\"ipb-attach attach-file\" href=\"https://4pda.to/forum/dl/post/1634517/Theme.zip\"><img data-src=\"$gif\">Theme.zip</a></li>" +
                "</ol></div></div>"
        val blocks = renderer.render(html)
        val spoiler = blocks.filterIsInstance<BodyBlock.Spoiler>().single()
        val files = spoiler.inner.filterIsInstance<BodyBlock.FileAttachment>()
        assertEquals("all 4 download buttons survive inside the spoiler", 4, files.size)
        assertTrue(files.any { it.name.contains("Themes.rar") })
        assertTrue(files.any { it.url.contains("Theme.zip") })
        // Each button's animated gif is rendered as a tappable image whose tap-target is the download href.
        val gifs = spoiler.inner.filterIsInstance<BodyBlock.Image>()
        assertEquals("all 4 download gifs render as images", 4, gifs.size)
        assertTrue("gif image points at the gif", gifs.all { it.imageUrl == gif })
        assertTrue("gif tap downloads the file", gifs.all { it.linkUrl?.contains("/dl/post/") == true })
        // Marked so the view layer can size at load time: keep a wide «СКАЧАТЬ» banner, hide a tiny mime glyph.
        assertTrue("attach-file inner img flagged as attachmentButton", gifs.all { it.attachmentButton })
    }

    @Test
    fun attachFileMimeGlyph_isFlaggedAttachmentButton_soViewCanHideIt() {
        // A PLAIN file attachment (PDF): 4pda wraps a tiny square file-type mime glyph inside the download
        // link. The legacy WebView hid it (`.ipb-attach.attach-file img{display:none}`); the native renderer
        // must flag it attachmentButton so the view layer collapses it, leaving only the file chip.
        val glyph = "https://4pda.to/forum/style_images/mime_types/pdf.png"
        val html = "<a class=\"ipb-attach attach-file\" href=\"https://4pda.to/forum/dl/post/55221/4PDA-1.pdf\">" +
                "<img src=\"$glyph\">4PDA-1.pdf</a> ( 1.34 МБ )"
        val blocks = renderer.render(html)
        val chip = blocks.filterIsInstance<BodyBlock.FileAttachment>().single()
        assertEquals("4PDA-1.pdf", chip.name)
        val img = blocks.filterIsInstance<BodyBlock.Image>().single()
        assertEquals(glyph, img.imageUrl)
        assertTrue("mime glyph flagged attachmentButton", img.attachmentButton)
    }

    @Test
    fun multipleDownloadButtons_allBecomeFileAttachments_noneDropped() {
        // A container (e.g. a spoiler <ol>) with SEVERAL download buttons — each an
        // <a class="ipb-attach attach-file"> wrapping an animated gif — must yield ALL file links, not
        // just the first (regression: post 239158/p144035585 «Каталог украшательств», rest vanished).
        val html = "<ol>" +
                "<li><a class=\"ipb-attach attach-file\" href=\"https://4pda.to/forum/dl/post/1/Themes.rar\"><img src=\"\">Themes.rar</a></li>" +
                "<li><a class=\"ipb-attach attach-file\" href=\"https://4pda.to/forum/dl/post/2/Official.rar\"><img src=\"\">Official.rar</a></li>" +
                "<li><a class=\"ipb-attach attach-file\" href=\"https://4pda.to/forum/dl/post/3/Premium.zip\"><img src=\"\">Premium.zip</a></li>" +
                "</ol>"
        val files = renderer.render(html).filterIsInstance<BodyBlock.FileAttachment>()
        assertEquals(3, files.size)
        assertTrue(files.any { it.name.contains("Themes.rar") })
        assertTrue(files.any { it.url.contains("Premium.zip") })
    }

    @Test
    fun looseLinkedImageThumbnails_renderAsInlineImages_notDropped() {
        // EXACT live RAW-HTML structure of post 239158/p144065357 «[Themes] One UI HD - Icon Pack» (the
        // server markup the app fetches, BEFORE 4pda's blocks.js runs): the icon-pack preview screenshot
        // and the animated «СКАЧАТЬ» gif button are BARE <img class="linked-image"> dropped loose in the
        // post text (the gif wrapped in a source-post link), followed by a «что нового» spoiler. They are
        // NOT inside an ipb-attach table, so they never hit the ATTACHMENT path. Regression: isContentImage
        // excluded .linked-image, so both landed in an inline Text run and Html.fromHtml (no ImageGetter)
        // silently DROPPED them — the post showed only its title + spoiler («в приложении картинок нет»).
        val png = "https://4pda.to/s/YJ9SaPhXN4sHtxam83HPECKS.png"
        val gif = "https://4pda.to/s/4Cv4OVavhY9HtxaGOhLJV76n.gif"
        val srcPost = "https://4pda.to/forum/index.php?showtopic=239158&view=findpost&p=119890221"
        val html = "<img loading=\"lazy\" src=\"$png\" class=\"linked-image\" alt=\"Прикрепленное изображение\" /> " +
                "<span style=\"color:darkblue\"><b>[Themes] One UI HD - Icon Pack</b></span> " +
                "<a title=\"Ссылка\" href=\"$srcPost\" target=\"_blank\"><img loading=\"lazy\" src=\"$gif\" class=\"linked-image\" /></a>" +
                "<br /><div class=\"post-block spoil close\"><div class=\"block-title\">что нового</div><div class=\"block-body\">- 45 New Icons</div></div>"
        val blocks = renderer.render(html)
        val images = blocks.filterIsInstance<BodyBlock.Image>()
        assertEquals("screenshot + gif button both peeled as inline images", 2, images.size)
        val screenshot = images.single { it.imageUrl == png }
        assertEquals("bare screenshot thumbnail has no tap link", null, screenshot.linkUrl)
        assertTrue(screenshot.inline)
        val button = images.single { it.imageUrl == gif }
        assertEquals("gif button links to its source post", srcPost, button.linkUrl)
        // The «что нового» spoiler still renders as its own native block alongside the images.
        assertEquals(1, blocks.filterIsInstance<BodyBlock.Spoiler>().size)
    }

    @Test
    fun quoteSnapbackArrowTwin_isNotRenderedAsImage() {
        // EXACT live structure (message-search body, Lenox30 quoting VIkToRSaNe): 4pda emits its quote
        // «snapback» arrow TWICE — once as a real service icon `<a act=findpost><img alt="*" src=snap.gif>`
        // and once as a BARE `<img alt="Изображение" src=snap.gif>` in a plain div. The generic alt slips
        // past isServiceIcon and the duplicate ballooned into a big blurry red arrow. It shares its src with
        // the alt="*" icon, so it must be recognised as service and NOT peeled into an Image block — while a
        // genuine screenshot (a UNIQUE src, also alt="Изображение") survives.
        val snap = "https://4pda.to/s/Zy0h09PCbOrY2EKEHjYrvR8HNEq0UKeqZkUWbRHrPA3qVomOYM.gif"
        val real = "https://4pda.to/s/Zy0hUivpz0GB6fWRe3mRUJIhUyeEhYyBsSwqVMatbEHr.png"
        val html = "<div class=\"post-block quote\"><div class=\"block-title\">VIkToRSaNe " +
                "<a href=\"/forum/index.php?act=findpost&pid=144165802\"><img alt=\"*\" src=\"$snap\"></a></div>" +
                "<div class=\"block-body\"><div><img alt=\"Изображение\" src=\"$snap\"></div> " +
                "v.wasko, что сделал <div><img alt=\"Изображение\" src=\"$real\"></div></div></div>"
        val quote = renderer.render(html).filterIsInstance<BodyBlock.Quote>().single()
        val images = quote.inner.filterIsInstance<BodyBlock.Image>()
        assertTrue("the snapback-arrow twin is not an image block", images.none { it.imageUrl == snap })
        assertTrue("the genuine screenshot still renders", images.any { it.imageUrl == real })
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

    // --- Inline content images (banners / previews / animated «UPDATE» gifs peeled from post text) ---

    @Test
    fun inlineLinkedImage_becomesInlineImageBlock_splittingSurroundingText() {
        val html = "<div>Смотрите обновление: " +
                "<a href=\"https://4pda.to/forum/topic\"><img src=\"https://i.imgur.com/update.gif\" width=\"320\" height=\"80\" /></a>" +
                " уже вышло!</div>"
        val blocks = renderer.render(html)
        val image = blocks.filterIsInstance<BodyBlock.Image>().single()
        assertEquals("https://i.imgur.com/update.gif", image.imageUrl)
        assertEquals("https://4pda.to/forum/topic", image.linkUrl) // enclosing <a> is the tap link
        assertTrue("inline flag drives full-width sizing", image.inline)
        assertTrue(blocks.any { it is BodyBlock.Text && it.html.contains("Смотрите обновление") })
        assertTrue(blocks.any { it is BodyBlock.Text && it.html.contains("уже вышло") })
    }

    @Test
    fun topLevelBareImage_isInlineImageBlock() {
        val blocks = renderer.render("<img alt=\"Изображение\" src=\"https://4pda.to/s/banner.png\" width=\"600\" height=\"200\" /> подпись")
        val image = blocks.filterIsInstance<BodyBlock.Image>().single()
        assertEquals("https://4pda.to/s/banner.png", image.imageUrl)
        assertTrue(image.inline)
        assertEquals(600, image.displayWidthPx)
    }

    @Test
    fun quoteSnapbackArrow_isNotExploded_staysInlineText() {
        // 4pda prefixes a «reply-to» header with a tiny <img alt="*"> snapback arrow linking to the quoted
        // post. It is a service glyph, not content — peeling it into a block Image blew a ~13px arrow up into
        // a big pixelated icon (user report). It must stay inline (not become an Image block).
        val html = "<a href=\"https://4pda.to/forum/index.php?act=findpost&pid=143179849\" title=\"Перейти к сообщению\">" +
                "<img src=\"https://4pda.to/s/Zy0h09PCbOrY2EKEHjYrvR8HNEq0UKeqZkUWbRHrPA3qVomOYM.gif\" alt=\"*\" border=\"0\" /></a>" +
                "<b>Lenox30,</b> текст ответа"
        val blocks = renderer.render(html)
        assertTrue("snapback arrow must NOT become a block Image", blocks.none { it is BodyBlock.Image })
        assertTrue(blocks.any { it is BodyBlock.Text && it.html.contains("Lenox30") })
    }

    @Test
    fun replyToSnapback_makesNickTappable_wrappingItInFindpostLink() {
        // «Ответ без цитаты»: snapback-стрелка (service-иконка, вырезается) + жирный ник. Раньше от якоря
        // оставался пустой <a> и ник был некликабельным — тап «в никуда». Теперь ник переносится ВНУТРЬ
        // якоря, чтобы тап по имени якорил к посту (как заголовок цитаты).
        val snap = "https://4pda.to/s/Zy0h09PCbOrY2EKEHjYrvR8HNEq0UKeqZkUWbRHrPA3qVomOYM.gif"
        val href = "https://4pda.to/forum/index.php?act=findpost&pid=143179849"
        val html = "<a href=\"$href\" title=\"Перейти к сообщению\"><img src=\"$snap\" alt=\"*\" border=\"0\" /></a>" +
                "<b>Lenox30,</b> текст ответа"
        val blocks = renderer.render(html)
        val text = blocks.filterIsInstance<BodyBlock.Text>().single()
        // The nick now lives inside the snapback anchor → a real tappable findpost link.
        assertTrue("nick must be wrapped in the findpost anchor",
                Regex("<a[^>]*href=\"[^\"]*findpost[^\"]*\"[^>]*>(?:(?!</a>).)*Lenox30").containsMatchIn(text.html))
        // The bare arrow <img> is stripped, not left as an empty placeholder.
        assertFalse("snapback arrow img must be stripped from inline text", text.html.contains("<img"))
    }

    @Test
    fun attachFileIndicatorGif_isNotExploded_andLeavesNoPlaceholderBox() {
        // 4pda drops a tiny «Прикрепленный файл» indicator gif inline before an attach block. It shares the
        // /s/ path with real content thumbnails, so it must be told apart by its alt (NOT the path) — else it
        // ballooned into a big pixelated arrow on every search-result post (user report). It must not become a
        // block Image, and must be stripped from the inline text so Html.fromHtml leaves no placeholder box.
        val html = "текст <img src=\"https://4pda.to/s/Zy0hRMDmDtR8z09.gif\" alt=\"Прикрепленный файл\" style=\"margin-right:3px;\"/> ещё"
        val blocks = renderer.render(html)
        assertTrue("attach-file indicator must NOT become a block Image", blocks.none { it is BodyBlock.Image })
        assertTrue("indicator <img> must be stripped from inline text",
                blocks.filterIsInstance<BodyBlock.Text>().none { it.html.contains("<img", ignoreCase = true) })
        assertTrue(blocks.any { it is BodyBlock.Text && it.html.contains("текст") && it.html.contains("ещё") })
    }

    @Test
    fun linkedImageThumbnailOnSPath_staysContentImage() {
        // The real attach thumbnail lives on the SAME /s/ path but carries alt="Прикрепленное изображение" —
        // it must stay a content Image (guard against over-eager service-icon matching).
        val html = "<img src=\"https://4pda.to/s/YJ9SaPhXN4.png\" class=\"linked-image\" alt=\"Прикрепленное изображение\" />"
        val blocks = renderer.render(html)
        assertTrue("content thumbnail must remain an Image block",
                blocks.any { it is BodyBlock.Image && it.imageUrl.contains("YJ9SaPhXN4.png") })
    }

    @Test
    fun smileyImage_isNotExploded_staysInlineText() {
        val html = "Спасибо <img alt=\":)\" src=\"https://4pda.to/forum/style_emoticons/happy.gif\" width=\"18\" height=\"18\"> всем"
        val blocks = renderer.render(html)
        assertTrue("a smiley <img> must never become a block Image", blocks.none { it is BodyBlock.Image })
        assertTrue(blocks.any { it is BodyBlock.Text })
    }

    @Test
    fun attachmentImages_stayAttachmentPath_notInline() {
        val blocks = renderer.render(fixture("attachment_image_table.html"))
        val imgs = blocks.filterIsInstance<BodyBlock.Image>()
        assertTrue(imgs.isNotEmpty())
        assertTrue("attachment pictures render as thumbnails, not inline", imgs.none { it.inline })
    }

    // --- 4pda hat/spoiler entries delivered HTML-escaped (arrive as literal "<a …>…</a>" text) ---

    @Test
    fun escapedAnchorAndImageMarkup_isReparsedIntoRealLinkAndImage() {
        val html = "&lt;img src=\"https://4pda.to/s/x.gif\"&gt; " +
                "&lt;a title=\"Ссылка\" href=\"https://4pda.to/forum/index.php?showtopic=1&amp;view=findpost&amp;p=2\"&gt;" +
                "Большой Jailbreak&lt;/a&gt;&lt;br&gt;"
        val blocks = renderer.render(html)
        // The escaped <img> becomes an inline Image and the escaped <a> becomes a real link (Text w/ <a href>).
        assertTrue(blocks.any { it is BodyBlock.Image && it.imageUrl.contains("x.gif") })
        assertTrue(blocks.any { it is BodyBlock.Text && it.html.contains("<a") && it.html.contains("href") })
        // Nothing leaks as raw escaped markup.
        assertTrue(blocks.filterIsInstance<BodyBlock.Text>().none { it.html.contains("&lt;a") })
    }

    @Test
    fun plainProseWithEscapedBrTag_isLeftLiteral_notReparsed() {
        val blocks = renderer.render("тег &lt;br&gt; переносит строку")
        val text = blocks.filterIsInstance<BodyBlock.Text>().single()
        assertTrue(text.html.contains("&lt;br&gt;")) // stays escaped → shown literally, no phantom line break
        assertTrue(blocks.none { it is BodyBlock.Image })
    }

    // --- Uniform block spacing: strip the stray <br>/newline authors put around quote/spoiler blocks ---

    @Test
    fun brBetweenTwoBlocks_isDropped_soSpacingIsUniform() {
        // Репорт: между блоками спойлер/цитирование иногда лишний интервал — автор ставит блок с новой
        // строки/через <br>, и эта «пустая строка» ложится ПОВЕРХ собственного отступа блока. Голый <br>
        // между двумя блоками не должен превращаться в Text-блок с фантомной пустой строкой.
        val spoiler = { title: String, body: String ->
            "<div class=\"post-block spoil close\"><div class=\"block-title\">$title</div>" +
                    "<div class=\"block-body\">$body</div></div>"
        }
        val blocks = renderer.render(spoiler("A", "aaa") + "<br>\n" + spoiler("B", "bbb"))
        assertEquals("два спойлера и НИ одного пустого Text между ними", 2, blocks.size)
        assertTrue(blocks.all { it is BodyBlock.Spoiler })
    }

    @Test
    fun brAtEdgesOfInlineRun_isTrimmed_butInnerBrKept() {
        // Ведущий/замыкающий <br> вокруг прозы у границы блока убираются (это отступ автора), а <br>
        // ВНУТРИ абзаца — намеренный перенос строки — сохраняется.
        val blocks = renderer.render(
                "<br><br>абзац один<br>абзац два<br> " +
                        "<div class=\"post-block quote\"><div class=\"block-title\">Кто-то</div>" +
                        "<div class=\"block-body\">цитата</div></div>"
        )
        val text = blocks.filterIsInstance<BodyBlock.Text>().single()
        // Ведущие и замыкающие <br> сняты; внутренний перенос между абзацами сохранён.
        assertEquals("абзац один<br>абзац два", text.html)
        assertEquals(1, blocks.filterIsInstance<BodyBlock.Quote>().size)
    }

    @Test
    fun qmsBareAttachImg_isANativeImage_notAWebFallback() {
        // Живая разметка личного сообщения с вложением (снята с 4pda): вложение — САМ <img
        // class="ipb-attach attach-img">, без таблицы/якоря, как в постах форума. Раньше он не
        // подходил ни под один селектор attachment-картинок и уезжал в WebView-фолбэк — в пузыре
        // вместо картинки была пустая серая полоса.
        val blocks = renderer.render(
                """<img class="ipb-attach attach-img" attach_id="35815726" s="0" loading="lazy" src="https://4pda.to/s/abc.png" alt="Прикрепленное изображение">
                   <br> STROKA-ODIN <br> STROKA-DVA"""
        )
        val image = blocks.filterIsInstance<BodyBlock.Image>().single()
        assertEquals("https://4pda.to/s/abc.png", image.imageUrl)
        assertTrue(blocks.none { it is BodyBlock.WebFallback })
        val texts = blocks.filterIsInstance<BodyBlock.Text>().joinToString(" ") { it.html }
        assertTrue(texts.contains("STROKA-ODIN"))
        assertTrue(texts.contains("STROKA-DVA"))
    }

    @Test
    fun wrapperHoldingProseAndAttachTable_keepsBothTexts() {
        // Репорт (личка QMS): «если к сообщению прикреплена картинка и есть текст, то текст обрезается».
        // 4pda кладёт attach-таблицу в ТОТ ЖЕ div, где набранный текст; узел классифицировался как
        // ATTACHMENT по потомку, и выкусывание вложения выбрасывало всю прозу обёртки.
        val blocks = renderer.render(
                """<div>Текст до картинки
                   <table id="ipb-attach-table-7"><tr><td><a href="/full.jpg"><img class="linked-image" src="/thumb.jpg"></a></td></tr></table>
                   текст после картинки</div>"""
        )
        assertTrue(blocks.any { it is BodyBlock.Image && it.imageUrl == "/thumb.jpg" })
        val texts = blocks.filterIsInstance<BodyBlock.Text>().joinToString(" ") { it.html }
        assertTrue("текст ДО вложения должен остаться", texts.contains("Текст до картинки"))
        assertTrue("текст ПОСЛЕ вложения должен остаться", texts.contains("текст после картинки"))
    }

    @Test
    fun wrapperHoldingProseAndAttachAnchor_keepsProse() {
        val blocks = renderer.render(
                """<div class="attach">Подпись <a href="/f.jpg" class="ipb-attach attach-image"><img class="linked-image" src="/f.jpg"></a> хвост</div>"""
        )
        assertTrue(blocks.any { it is BodyBlock.Image && it.imageUrl == "/f.jpg" })
        val texts = blocks.filterIsInstance<BodyBlock.Text>().joinToString(" ") { it.html }
        assertTrue(texts.contains("Подпись"))
        assertTrue(texts.contains("хвост"))
    }

    @Test
    fun wrapperHoldingOnlyAttachment_staysASingleImageBlock() {
        // Обёртка без собственного текста peel-ится как раньше — прежнее поведение не меняем.
        val blocks = renderer.render(
                """Before media
                   <div class="attach"><a href="/f.jpg" class="ipb-attach attach-image"><img class="linked-image" src="/f.jpg"></a></div>
                   After media"""
        )
        assertEquals(1, blocks.count { it is BodyBlock.Image })
        val texts = blocks.filterIsInstance<BodyBlock.Text>().joinToString(" ") { it.html }
        assertTrue(texts.contains("Before media"))
        assertTrue(texts.contains("After media"))
    }

    private fun fixture(name: String): String {
        val path = "renderer/postbody/$name"
        return javaClass.classLoader?.getResource(path)?.readText()
                ?: error("Missing test resource $path")
    }
}
