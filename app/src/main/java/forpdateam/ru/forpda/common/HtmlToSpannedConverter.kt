package forpdateam.ru.forpda.common

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.*
import android.text.style.*
import android.content.res.Resources
import org.xml.sax.*
import java.io.StringReader
import java.util.Locale
import java.util.regex.Pattern
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.Html.Companion.FROM_HTML_OPTION_USE_CSS_COLORS

internal class HtmlToSpannedConverter(
    private val mSource: String,
    private val mImageGetter: Html.ImageGetter?,
    private val mTagHandler: Html.TagHandler?,
    private val mReader: XMLReader,
    private val mFlags: Int
) : ContentHandler {

    companion object {
        private val HEADING_SIZES = floatArrayOf(1.5f, 1.4f, 1.3f, 1.2f, 1.1f, 1f)
        private var sTextAlignPattern: Pattern? = null
        private var sForegroundColorPattern: Pattern? = null
        private var sBackgroundColorPattern: Pattern? = null
        private var sTextDecorationPattern: Pattern? = null
        private var sFontFamilyPattern: Pattern? = null

        private fun getTextAlignPattern(): Pattern =
            sTextAlignPattern ?: Pattern.compile("(?:\\s+|\\A)text-align\\s*:\\s*(\\S*)\\b", Pattern.CASE_INSENSITIVE)
                .also { sTextAlignPattern = it }
        private fun getForegroundColorPattern(): Pattern =
            sForegroundColorPattern ?: Pattern.compile("(?:\\s+|\\A)color\\s*:\\s*(\\S*)\\b", Pattern.CASE_INSENSITIVE)
                .also { sForegroundColorPattern = it }
        private fun getBackgroundColorPattern(): Pattern =
            sBackgroundColorPattern ?: Pattern.compile("(?:\\s+|\\A)background(?:-color)?\\s*:\\s*(\\S*)\\b", Pattern.CASE_INSENSITIVE)
                .also { sBackgroundColorPattern = it }
        private fun getTextDecorationPattern(): Pattern =
            sTextDecorationPattern ?: Pattern.compile("(?:\\s+|\\A)text-decoration\\s*:\\s*(\\S*)\\b", Pattern.CASE_INSENSITIVE)
                .also { sTextDecorationPattern = it }
        private fun getFontFamilyPattern(): Pattern =
            sFontFamilyPattern ?: Pattern.compile("(?:\\s+|\\A)font-family\\s*:\\s*(\\S*)\\b", Pattern.CASE_INSENSITIVE)
                .also { sFontFamilyPattern = it }
    }

    private val mSpannableStringBuilder = SpannableStringBuilder()

    fun convert(): Spanned {
        mReader.contentHandler = this
        try { mReader.parse(InputSource(StringReader(mSource))) }
        catch (e: java.io.IOException) { throw RuntimeException(e) }
        catch (e: SAXException) { throw RuntimeException(e) }
        val paragraphSpans = mSpannableStringBuilder.getSpans(0, mSpannableStringBuilder.length, ParagraphStyle::class.java)
        for (span in paragraphSpans) {
            var start = mSpannableStringBuilder.getSpanStart(span)
            var end = mSpannableStringBuilder.getSpanEnd(span)
            if (end - 2 >= 0 && mSpannableStringBuilder[end - 1] == '\n' && mSpannableStringBuilder[end - 2] == '\n') end--
            if (end == start) mSpannableStringBuilder.removeSpan(span)
            else mSpannableStringBuilder.setSpan(span, start, end, Spannable.SPAN_PARAGRAPH)
        }
        return mSpannableStringBuilder
    }

    private fun handleStartTag(tag: String, attributes: Attributes) {
        when {
            tag.equals("br", ignoreCase = true) -> {}
            tag.equals("p", ignoreCase = true) -> { startBlockElement(mSpannableStringBuilder, attributes, getMarginParagraph()); startCssStyle(mSpannableStringBuilder, attributes) }
            tag.equals("ul", ignoreCase = true) -> startBlockElement(mSpannableStringBuilder, attributes, getMarginList())
            tag.equals("li", ignoreCase = true) -> startLi(mSpannableStringBuilder, attributes, getMarginListItem())
            tag.equals("div", ignoreCase = true) -> startBlockElement(mSpannableStringBuilder, attributes, getMarginDiv())
            tag.equals("span", ignoreCase = true) -> startCssStyle(mSpannableStringBuilder, attributes)
            tag.equals("strong", ignoreCase = true) || tag.equals("b", ignoreCase = true) -> start(mSpannableStringBuilder, Bold())
            tag.equals("em", ignoreCase = true) || tag.equals("cite", ignoreCase = true) || tag.equals("dfn", ignoreCase = true) || tag.equals("i", ignoreCase = true) -> start(mSpannableStringBuilder, Italic())
            tag.equals("big", ignoreCase = true) -> start(mSpannableStringBuilder, Big())
            tag.equals("small", ignoreCase = true) -> start(mSpannableStringBuilder, Small())
            tag.equals("font", ignoreCase = true) -> startFont(mSpannableStringBuilder, attributes)
            tag.equals("blockquote", ignoreCase = true) -> startBlockquote(mSpannableStringBuilder, attributes)
            tag.equals("tt", ignoreCase = true) -> start(mSpannableStringBuilder, Monospace())
            tag.equals("a", ignoreCase = true) -> startA(mSpannableStringBuilder, attributes)
            tag.equals("u", ignoreCase = true) -> start(mSpannableStringBuilder, Underline())
            tag.equals("del", ignoreCase = true) || tag.equals("s", ignoreCase = true) || tag.equals("strike", ignoreCase = true) -> start(mSpannableStringBuilder, Strikethrough())
            tag.equals("sup", ignoreCase = true) -> start(mSpannableStringBuilder, Super())
            tag.equals("sub", ignoreCase = true) -> start(mSpannableStringBuilder, Sub())
            tag.length == 2 && tag[0].lowercaseChar() == 'h' && tag[1] in '1'..'6' -> startHeading(mSpannableStringBuilder, attributes, tag[1] - '1')
            tag.equals("img", ignoreCase = true) -> startImg(mSpannableStringBuilder, attributes, mImageGetter)
            else -> mTagHandler?.handleTag(true, tag, mSpannableStringBuilder, mReader)
        }
    }

    private fun handleEndTag(tag: String) {
        when {
            tag.equals("br", ignoreCase = true) -> handleBr(mSpannableStringBuilder)
            tag.equals("p", ignoreCase = true) -> { endCssStyle(mSpannableStringBuilder); endBlockElement(mSpannableStringBuilder) }
            tag.equals("ul", ignoreCase = true) || tag.equals("div", ignoreCase = true) -> endBlockElement(mSpannableStringBuilder)
            tag.equals("li", ignoreCase = true) -> { endCssStyle(mSpannableStringBuilder); endBlockElement(mSpannableStringBuilder); end(mSpannableStringBuilder, Bullet::class.java, BulletSpan()) }
            tag.equals("span", ignoreCase = true) -> endCssStyle(mSpannableStringBuilder)
            tag.equals("strong", ignoreCase = true) || tag.equals("b", ignoreCase = true) -> end(mSpannableStringBuilder, Bold::class.java, StyleSpan(Typeface.BOLD))
            tag.equals("em", ignoreCase = true) || tag.equals("cite", ignoreCase = true) || tag.equals("dfn", ignoreCase = true) || tag.equals("i", ignoreCase = true) -> end(mSpannableStringBuilder, Italic::class.java, StyleSpan(Typeface.ITALIC))
            tag.equals("big", ignoreCase = true) -> end(mSpannableStringBuilder, Big::class.java, RelativeSizeSpan(1.25f))
            tag.equals("small", ignoreCase = true) -> end(mSpannableStringBuilder, Small::class.java, RelativeSizeSpan(0.875f))
            tag.equals("font", ignoreCase = true) -> endFont(mSpannableStringBuilder)
            tag.equals("blockquote", ignoreCase = true) -> { endBlockElement(mSpannableStringBuilder); end(mSpannableStringBuilder, Blockquote::class.java, QuoteSpan()) }
            tag.equals("tt", ignoreCase = true) -> end(mSpannableStringBuilder, Monospace::class.java, TypefaceSpan("monospace"))
            tag.equals("a", ignoreCase = true) -> endA(mSpannableStringBuilder)
            tag.equals("u", ignoreCase = true) -> end(mSpannableStringBuilder, Underline::class.java, UnderlineSpan())
            tag.equals("del", ignoreCase = true) || tag.equals("s", ignoreCase = true) || tag.equals("strike", ignoreCase = true) -> end(mSpannableStringBuilder, Strikethrough::class.java, StrikethroughSpan())
            tag.equals("sup", ignoreCase = true) -> end(mSpannableStringBuilder, Super::class.java, SuperscriptSpan())
            tag.equals("sub", ignoreCase = true) -> end(mSpannableStringBuilder, Sub::class.java, SubscriptSpan())
            tag.length == 2 && tag[0].lowercaseChar() == 'h' && tag[1] in '1'..'6' -> endHeading(mSpannableStringBuilder)
            else -> mTagHandler?.handleTag(false, tag, mSpannableStringBuilder, mReader)
        }
    }

    private fun getMarginParagraph() = getMargin(Html.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH)
    private fun getMarginHeading() = getMargin(Html.FROM_HTML_SEPARATOR_LINE_BREAK_HEADING)
    private fun getMarginListItem() = getMargin(Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST_ITEM)
    private fun getMarginList() = getMargin(Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST)
    private fun getMarginDiv() = getMargin(Html.FROM_HTML_SEPARATOR_LINE_BREAK_DIV)
    private fun getMarginBlockquote() = getMargin(Html.FROM_HTML_SEPARATOR_LINE_BREAK_BLOCKQUOTE)
    private fun getMargin(flag: Int) = if ((flag and mFlags) != 0) 1 else 2

    private fun appendNewlines(text: Editable, minNewline: Int) {
        val len = text.length; if (len == 0) return
        var existingNewlines = 0; var i = len - 1
        while (i >= 0 && text[i] == '\n') { existingNewlines++; i-- }
        for (j in existingNewlines until minNewline) text.append("\n")
    }
    private fun startBlockElement(text: Editable, attributes: Attributes, margin: Int) {
        if (margin > 0) { appendNewlines(text, margin); start(text, Newline(margin)) }
        val style = attributes.getValue("", "style")
        if (style != null) { val m = getTextAlignPattern().matcher(style); if (m.find()) { val alignment = m.group(1); when { alignment.equals("start", ignoreCase = true) -> start(text, Alignment(Layout.Alignment.ALIGN_NORMAL)); alignment.equals("center", ignoreCase = true) -> start(text, Alignment(Layout.Alignment.ALIGN_CENTER)); alignment.equals("end", ignoreCase = true) -> start(text, Alignment(Layout.Alignment.ALIGN_OPPOSITE)) } } }
    }
    private fun endBlockElement(text: Editable) {
        val n = getLast(text, Newline::class.java); if (n != null) { appendNewlines(text, n.mNumNewlines); text.removeSpan(n) }
        val a = getLast(text, Alignment::class.java); if (a != null) setSpanFromMark(text, a, AlignmentSpan.Standard(a.mAlignment))
    }
    private fun handleBr(text: Editable) { text.append('\n') }
    private fun startLi(text: Editable, attributes: Attributes, marginListItem: Int) { startBlockElement(text, attributes, marginListItem); start(text, Bullet()); startCssStyle(text, attributes) }
    private fun startBlockquote(text: Editable, attributes: Attributes) { startBlockElement(text, attributes, getMarginBlockquote()); start(text, Blockquote()) }
    private fun startHeading(text: Editable, attributes: Attributes, level: Int) { startBlockElement(text, attributes, getMarginHeading()); start(text, Heading(level)) }
    private fun endHeading(text: Editable) { val h = getLast(text, Heading::class.java); if (h != null) setSpanFromMark(text, h, RelativeSizeSpan(HEADING_SIZES[h.mLevel]), StyleSpan(Typeface.BOLD)); endBlockElement(text) }
    private fun <T> getLast(text: Spanned, kind: Class<T>): T? { val objs = text.getSpans(0, text.length, kind); return if (objs.isEmpty()) null else objs[objs.size - 1] }
    private fun setSpanFromMark(text: Spannable, mark: Any, vararg spans: Any) { val where = text.getSpanStart(mark); text.removeSpan(mark); val len = text.length; if (where != len) for (span in spans) text.setSpan(span, where, len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    private fun start(text: Editable, mark: Any) { val len = text.length; text.setSpan(mark, len, len, Spannable.SPAN_INCLUSIVE_EXCLUSIVE) }
    private fun <T> end(text: Editable, kind: Class<T>, repl: Any) { val obj = getLast(text, kind); if (obj != null) setSpanFromMark(text, obj, repl) }
    private fun startCssStyle(text: Editable, attributes: Attributes) {
        val style = attributes.getValue("", "style") ?: return
        var m = getForegroundColorPattern().matcher(style); if (m.find()) { val c = getHtmlColor(m.group(1)); if (c != -1) start(text, Foreground(c or 0xFF000000.toInt())) }
        m = getBackgroundColorPattern().matcher(style); if (m.find()) { val c = getHtmlColor(m.group(1)); if (c != -1) start(text, Background(c or 0xFF000000.toInt())) }
        m = getTextDecorationPattern().matcher(style); if (m.find()) { if (m.group(1).equals("line-through", ignoreCase = true)) start(text, Strikethrough()) }
        m = getFontFamilyPattern().matcher(style); if (m.find()) start(text, Font(m.group(1)))
    }
    private fun endCssStyle(text: Editable) {
        val font = getLast(text, Font::class.java); if (font != null && font.mFace.equals("fontello", ignoreCase = true)) setSpanFromMark(text, font, AssetsTypefaceSpan(null, "fontello/fontello.ttf"))
        val s = getLast(text, Strikethrough::class.java); if (s != null) setSpanFromMark(text, s, StrikethroughSpan())
        val b = getLast(text, Background::class.java); if (b != null) setSpanFromMark(text, b, BackgroundColorSpan(b.mBackgroundColor))
        val f = getLast(text, Foreground::class.java); if (f != null) setSpanFromMark(text, f, ForegroundColorSpan(f.mForegroundColor))
    }
    private fun startImg(text: Editable, attributes: Attributes, img: Html.ImageGetter?) {
        val src = attributes.getValue("", "src"); var d: Drawable? = null; if (img != null) d = img.getDrawable(src)
        if (d == null) {
            try {
                d = ContextImageLookup.requireDrawable(R.drawable.adf)
                d?.setBounds(0, 0, d.intrinsicWidth, d.intrinsicHeight)
            } catch (_: Exception) {}
        }
        if (d == null) return
        val len = text.length; text.append("\uFFFC"); text.setSpan(ImageSpan(d, src), len, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    private fun startFont(text: Editable, attributes: Attributes) {
        val color = attributes.getValue("", "color"); val face = attributes.getValue("", "face")
        if (!TextUtils.isEmpty(color)) { val c = getHtmlColor(color); if (c != -1) start(text, Foreground(c or 0xFF000000.toInt())) }
        if (!TextUtils.isEmpty(face)) start(text, Font(face))
    }
    private fun endFont(text: Editable) {
        val font = getLast(text, Font::class.java); if (font != null) setSpanFromMark(text, font, TypefaceSpan(font.mFace))
        val fg = getLast(text, Foreground::class.java); if (fg != null) setSpanFromMark(text, fg, ForegroundColorSpan(fg.mForegroundColor))
    }
    private fun startA(text: Editable, attributes: Attributes) { start(text, Href(attributes.getValue("", "href"))) }
    private fun endA(text: Editable) { val h = getLast(text, Href::class.java); if (h?.mHref != null) setSpanFromMark(text, h, URLSpan(h.mHref)) }
    private fun getHtmlColor(color: String): Int {
        if ((mFlags and Html.FROM_HTML_OPTION_USE_CSS_COLORS) == Html.FROM_HTML_OPTION_USE_CSS_COLORS) {
            var i: Int? = Html.getColorMap()[color.lowercase(Locale.ROOT)]; if (i != null) return i; i = null
            try { i = Color.parseColor(color) } catch (_: Exception) {}
            if (i != null) return i
        }
        return Color.TRANSPARENT
    }

    override fun setDocumentLocator(locator: Locator?) {}
    override fun startDocument() {}
    override fun endDocument() {}
    override fun startPrefixMapping(prefix: String?, uri: String?) {}
    override fun endPrefixMapping(prefix: String?) {}
    override fun startElement(uri: String?, localName: String, qName: String?, attributes: Attributes) { handleStartTag(localName, attributes) }
    override fun endElement(uri: String?, localName: String, qName: String?) { handleEndTag(localName) }
    override fun characters(ch: CharArray, start: Int, length: Int) {
        val sb = StringBuilder()
        for (i in 0 until length) {
            val c = ch[i + start]
            if (c == ' ' || c == '\n') { val len = sb.length; val pred = if (len == 0) { val sbLen = mSpannableStringBuilder.length; if (sbLen == 0) '\n' else mSpannableStringBuilder[sbLen - 1] } else sb[len - 1]; if (pred != ' ' && pred != '\n') sb.append(' ') }
            else sb.append(c)
        }
        mSpannableStringBuilder.append(sb)
    }
    override fun ignorableWhitespace(ch: CharArray?, start: Int, length: Int) {}
    override fun processingInstruction(target: String?, data: String?) {}
    override fun skippedEntity(name: String?) {}

    private class Bold
    private class Italic
    private class Underline
    private class Strikethrough
    private class Big
    private class Small
    private class Monospace
    private class Blockquote
    private class Super
    private class Sub
    private class Bullet
    private class Font(val mFace: String)
    private class Href(val mHref: String?)
    private class Foreground(val mForegroundColor: Int)
    private class Background(val mBackgroundColor: Int)
    private class Heading(val mLevel: Int)
    private class Newline(val mNumNewlines: Int)
    private class Alignment(val mAlignment: Layout.Alignment)
}
