package forpdateam.ru.forpda.common.bbcode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BbcodePreviewRendererTest {

    @Test
    fun renderToHtml_boldLabel_hidesBbcodeTags() {
        val html = BbcodePreviewRenderer.renderToHtml("[b]Type:[/b] Other")

        assertEquals("<b>Type:</b> Other", html)
        assertNoRawSupportedTags(html)
    }

    @Test
    fun renderToHtml_commonInlineTags_renderAsHtml() {
        val html = BbcodePreviewRenderer.renderToHtml("[i]one[/i] [u]two[/u] [s]three[/s] [strike]four[/strike]")

        assertEquals("<i>one</i> <u>two</u> <s>three</s> <s>four</s>", html)
        assertNoRawSupportedTags(html)
    }

    @Test
    fun renderToHtml_urlTags_renderLinks() {
        val html = BbcodePreviewRenderer.renderToHtml("[url]https://example.com?a=1&b=2[/url] [url=https://4pda.to]Forum[/url]")

        assertEquals(
            """<a href="https://example.com?a=1&amp;b=2">https://example.com?a=1&amp;b=2</a> <a href="https://4pda.to">Forum</a>""",
            html
        )
        assertNoRawSupportedTags(html)
    }

    @Test
    fun renderToHtml_quoteWithNestedBold_rendersBlockAndNestedFormatting() {
        val html = BbcodePreviewRenderer.renderToHtml("[quote name=\"User\" date=\"today\" post=\"1\"]Hello [b]world[/b][/quote]")

        assertEquals("<blockquote><b>User</b><br>Hello <b>world</b></blockquote>", html)
        assertNoRawSupportedTags(html)
    }

    @Test
    fun renderToHtml_spoilerWithTitle_rendersBlock() {
        val html = BbcodePreviewRenderer.renderToHtml("[spoiler=Details]Hidden [i]text[/i][/spoiler]")

        assertEquals("<b>Details</b><br><blockquote>Hidden <i>text</i></blockquote>", html)
        assertNoRawSupportedTags(html)
    }

    @Test
    fun renderToHtml_offtopTag_rendersDeEmphasisedBlockNotRawBrackets() {
        val html = BbcodePreviewRenderer.renderToHtml("before [offtop]side [b]note[/b][/offtop] after")

        assertEquals(
            """before <small><font color="#888888">side <b>note</b></font></small> after""",
            html
        )
        assertFalse(html.contains("offtop", ignoreCase = true))
        assertFalse(html.contains("["))
        assertNoRawSupportedTags(html)
    }

    @Test
    fun renderToHtml_hideTag_rendersLabelledBlock() {
        val html = BbcodePreviewRenderer.renderToHtml("[hide]secret[/hide]")

        assertEquals("<b>Hide</b><br><blockquote>secret</blockquote>", html)
        assertFalse(html.contains("[hide", ignoreCase = true))
        assertNoRawSupportedTags(html)
    }

    @Test
    fun renderToHtml_subSupBackgroundCurTags_renderOrStripBbcode() {
        val html = BbcodePreviewRenderer.renderToHtml(
            "H[sub]2[/sub]O x[sup]2[/sup] [background=yellow]bg[/background] [cur]curator[/cur]"
        )

        assertEquals("H<sub>2</sub>O x<sup>2</sup> bg curator", html)
        assertFalse(html.contains("["))
    }

    @Test
    fun renderToHtml_codeBlock_escapesContentAndDoesNotRenderNestedBbcode() {
        val html = BbcodePreviewRenderer.renderToHtml("[code]<tag>[b]raw[/b]</tag>[/code]")

        assertEquals("<tt>&lt;tag&gt;[b]raw[/b]&lt;/tag&gt;</tt>", html)
    }

    @Test
    fun renderToHtml_lineBreaks_renderAsBreaks() {
        val html = BbcodePreviewRenderer.renderToHtml("one\r\ntwo\rthree")

        assertEquals("one&#10;two&#10;three", html)
    }

    @Test
    fun renderToHtml_serviceTags_hideInternalMarkers() {
        val html = BbcodePreviewRenderer.renderToHtml(
            "[snapback]1[/snapback] [b]D.K,[/b] text [size=1]Добавлено [mergetime]177[/mergetime][/size]"
        )

        assertEquals(
                " <b>D.K,</b> text <small>&#1044;&#1086;&#1073;&#1072;&#1074;&#1083;&#1077;&#1085;&#1086; </small>",
                html
        )
        assertFalse(html.contains("snapback", ignoreCase = true))
        assertFalse(html.contains("mergetime", ignoreCase = true))
        assertFalse(html.contains("size", ignoreCase = true))
    }

    @Test
    fun renderToHtml_presentationTags_renderOrStripBbcode() {
        val html = BbcodePreviewRenderer.renderToHtml(
            "[color=red]red[/color] [font=Arial]font[/font] [center]center[/center] [list][*]one[*]two[/list]"
        )

        assertEquals("""<font color="red">red</font> font <div align="center">center</div> <br>&bull; one<br>&bull; two""", html)
        assertNoRawSupportedTags(html)
    }

    private fun assertNoRawSupportedTags(html: String) {
        assertFalse(
            html.contains(
                Regex("""(?i)\[/?(?:b|i|u|s|strike|url|quote|spoiler|offtop|hide|code|snapback|mergetime|size|color|background|font|left|center|right|sub|sup|cur|list|\*)(?:[=\]\s])""")
            )
        )
        assertTrue(html.isNotBlank())
    }
}
