package forpdateam.ru.forpda.common

import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.*
import android.text.style.*
import org.ccil.cowan.tagsoup.HTMLSchema
import org.ccil.cowan.tagsoup.Parser
import org.xml.sax.*
import java.util.*

class Html private constructor() {
    interface ImageGetter { fun getDrawable(source: String): Drawable? }
    interface TagHandler { fun handleTag(opening: Boolean, tag: String, output: Editable, xmlReader: XMLReader) }

    companion object {
        const val TO_HTML_PARAGRAPH_LINES_CONSECUTIVE = 0x00000000
        const val TO_HTML_PARAGRAPH_LINES_INDIVIDUAL = 0x00000001
        const val FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH = 0x00000001
        const val FROM_HTML_SEPARATOR_LINE_BREAK_HEADING = 0x00000002
        const val FROM_HTML_SEPARATOR_LINE_BREAK_LIST_ITEM = 0x00000004
        const val FROM_HTML_SEPARATOR_LINE_BREAK_LIST = 0x00000008
        const val FROM_HTML_SEPARATOR_LINE_BREAK_DIV = 0x00000010
        const val FROM_HTML_SEPARATOR_LINE_BREAK_BLOCKQUOTE = 0x00000020
        const val FROM_HTML_OPTION_USE_CSS_COLORS = 0x00000100
        const val FROM_HTML_MODE_LEGACY = 0x00000000
        const val FROM_HTML_MODE_COMPACT = FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH or FROM_HTML_SEPARATOR_LINE_BREAK_HEADING or FROM_HTML_SEPARATOR_LINE_BREAK_LIST_ITEM or FROM_HTML_SEPARATOR_LINE_BREAK_LIST or FROM_HTML_SEPARATOR_LINE_BREAK_DIV or FROM_HTML_SEPARATOR_LINE_BREAK_BLOCKQUOTE
        private const val TO_HTML_PARAGRAPH_FLAG = 0x00000001
        private val sColorMap: MutableMap<String, Int> = HashMap()
        private val schema = HTMLSchema()

        init { initColorMap() }

        private fun initColorMap() {
            val m = sColorMap
            m["aliceblue"]=0xfff0f8ff.toInt();m["antiquewhite"]=0xfffaebd7.toInt();m["aqua"]=0xff00ffff.toInt();m["aquamarine"]=0xff7fffd4.toInt();m["azure"]=0xfff0ffff.toInt();m["beige"]=0xfff5f5dc.toInt();m["bisque"]=0xffffe4c4.toInt();m["black"]=0xff000000.toInt();m["blanchedalmond"]=0xffffebcd.toInt();m["blue"]=0xff0000ff.toInt();m["blueviolet"]=0xff8a2be2.toInt();m["brown"]=0xffa52a2a.toInt();m["burlywood"]=0xffdeb887.toInt();m["cadetblue"]=0xff5f9ea0.toInt();m["chartreuse"]=0xff7fff00.toInt();m["chocolate"]=0xffd2691e.toInt();m["coral"]=0xffff7f50.toInt();m["cornflowerblue"]=0xff6495ed.toInt();m["cornsilk"]=0xfffff8dc.toInt();m["crimson"]=0xffdc143c.toInt();m["cyan"]=0xff00ffff.toInt();m["darkblue"]=0xff00008b.toInt();m["darkcyan"]=0xff008b8b.toInt();m["darkgoldenrod"]=0xffb8860b.toInt();m["darkgray"]=0xffa9a9a9.toInt();m["darkgrey"]=0xffa9a9a9.toInt();m["darkgreen"]=0xff006400.toInt();m["darkkhaki"]=0xffbdb76b.toInt();m["darkmagenta"]=0xff8b008b.toInt();m["darkolivegreen"]=0xff556b2f.toInt();m["darkorange"]=0xffff8c00.toInt();m["darkorchid"]=0xff9932cc.toInt();m["darkred"]=0xff8b0000.toInt();m["darksalmon"]=0xffe9967a.toInt();m["darkseagreen"]=0xff8fbc8f.toInt();m["darkslateblue"]=0xff483d8b.toInt();m["darkslategray"]=0xff2f4f4f.toInt();m["darkslategrey"]=0xff2f4f4f.toInt();m["darkturquoise"]=0xff00ced1.toInt();m["darkviolet"]=0xff9400d3.toInt();m["deeppink"]=0xffff1493.toInt();m["deepskyblue"]=0xff00bfff.toInt();m["dimgray"]=0xff696969.toInt();m["dimgrey"]=0xff696969.toInt();m["dodgerblue"]=0xff1e90ff.toInt();m["firebrick"]=0xffb22222.toInt();m["floralwhite"]=0xfffffaf0.toInt();m["forestgreen"]=0xff228b22.toInt();m["fuchsia"]=0xffff00ff.toInt();m["gainsboro"]=0xffdcdcdc.toInt();m["ghostwhite"]=0xfff8f8ff.toInt();m["gold"]=0xffffd700.toInt();m["goldenrod"]=0xffdaa520.toInt();m["gray"]=0xff808080.toInt();m["grey"]=0xff808080.toInt();m["green"]=0xff008000.toInt();m["greenyellow"]=0xffadff2f.toInt();m["honeydew"]=0xfff0fff0.toInt();m["hotpink"]=0xffff69b4.toInt();m["indianred "]=0xffcd5c5c.toInt();m["indigo "]=0xff4b0082.toInt();m["ivory"]=0xfffffff0.toInt();m["khaki"]=0xfff0e68c.toInt();m["lavender"]=0xffe6e6fa.toInt();m["lavenderblush"]=0xfffff0f5.toInt();m["lawngreen"]=0xff7cfc00.toInt();m["lemonchiffon"]=0xfffffacd.toInt();m["lightblue"]=0xffadd8e6.toInt();m["lightcoral"]=0xfff08080.toInt();m["lightcyan"]=0xffe0ffff.toInt();m["lightgoldenrodyellow"]=0xfffafad2.toInt();m["lightgray"]=0xffd3d3d3.toInt();m["lightgrey"]=0xffd3d3d3.toInt();m["lightgreen"]=0xff90ee90.toInt();m["lightpink"]=0xffffb6c1.toInt();m["lightsalmon"]=0xffffa07a.toInt();m["lightseagreen"]=0xff20b2aa.toInt();m["lightskyblue"]=0xff87cefa.toInt();m["lightslategray"]=0xff778899.toInt();m["lightslategrey"]=0xff778899.toInt();m["lightsteelblue"]=0xffb0c4de.toInt();m["lightyellow"]=0xffffffe0.toInt();m["lime"]=0xff00ff00.toInt();m["limegreen"]=0xff32cd32.toInt();m["linen"]=0xfffaf0e6.toInt();m["magenta"]=0xffff00ff.toInt();m["maroon"]=0xff800000.toInt();m["mediumaquamarine"]=0xff66cdaa.toInt();m["mediumblue"]=0xff0000cd.toInt();m["mediumorchid"]=0xffba55d3.toInt();m["mediumpurple"]=0xff9370db.toInt();m["mediumseagreen"]=0xff3cb371.toInt();m["mediumslateblue"]=0xff7b68ee.toInt();m["mediumspringgreen"]=0xff00fa9a.toInt();m["mediumturquoise"]=0xff48d1cc.toInt();m["mediumvioletred"]=0xffc71585.toInt();m["midnightblue"]=0xff191970.toInt();m["mintcream"]=0xfff5fffa.toInt();m["mistyrose"]=0xffffe4e1.toInt();m["moccasin"]=0xffffe4b5.toInt();m["navajowhite"]=0xffffdead.toInt();m["navy"]=0xff000080.toInt();m["oldlace"]=0xfffdf5e6.toInt();m["olive"]=0xff808000.toInt();m["olivedrab"]=0xff6b8e23.toInt();m["orange"]=0xffffa500.toInt();m["orangered"]=0xffff4500.toInt();m["orchid"]=0xffda70d6.toInt();m["palegoldenrod"]=0xffeee8aa.toInt();m["palegreen"]=0xff98fb98.toInt();m["paleturquoise"]=0xffafeeee.toInt();m["palevioletred"]=0xffdb7093.toInt();m["papayawhip"]=0xffffefd5.toInt();m["peachpuff"]=0xffffdab9.toInt();m["peru"]=0xffcd853f.toInt();m["pink"]=0xffffc0cb.toInt();m["plum"]=0xffdda0dd.toInt();m["powderblue"]=0xffb0e0e6.toInt();m["purple"]=0xff800080.toInt();m["rebeccapurple"]=0xff663399.toInt();m["red"]=0xffff0000.toInt();m["rosybrown"]=0xffbc8f8f.toInt();m["royalblue"]=0xff4169e1.toInt();m["saddlebrown"]=0xff8b4513.toInt();m["salmon"]=0xfffa8072.toInt();m["sandybrown"]=0xfff4a460.toInt();m["seagreen"]=0xff2e8b57.toInt();m["seashell"]=0xfffff5ee.toInt();m["sienna"]=0xffa0522d.toInt();m["silver"]=0xffc0c0c0.toInt();m["skyblue"]=0xff87ceeb.toInt();m["slateblue"]=0xff6a5acd.toInt();m["slategray"]=0xff708090.toInt();m["slategrey"]=0xff708090.toInt();m["snow"]=0xfffffafa.toInt();m["springgreen"]=0xff00ff7f.toInt();m["steelblue"]=0xff4682b4.toInt();m["tan"]=0xffd2b48c.toInt();m["teal"]=0xff008080.toInt();m["thistle"]=0xffd8bfd8.toInt();m["tomato"]=0xffff6347.toInt();m["turquoise"]=0xff40e0d0.toInt();m["violet"]=0xffee82ee.toInt();m["wheat"]=0xfff5deb3.toInt();m["white"]=0xffffffff.toInt();m["whitesmoke"]=0xfff5f5f5.toInt();m["yellow"]=0xffffff00.toInt();m["yellowgreen"]=0xff9acd32.toInt()
        }

        @JvmStatic fun getColorMap(): Map<String, Int> = sColorMap

        @Deprecated("Use fromHtml(String, Int) instead.")
        @JvmStatic fun fromHtml(source: String): Spanned = fromHtml(source, FROM_HTML_MODE_LEGACY, null, null)
        @JvmStatic fun fromHtml(source: String, flags: Int): Spanned = fromHtml(source, flags, null, null)

        @Deprecated("Use fromHtml(String, Int, ImageGetter, TagHandler) instead.")
        @JvmStatic fun fromHtml(source: String, imageGetter: ImageGetter?, tagHandler: TagHandler?): Spanned = fromHtml(source, FROM_HTML_MODE_LEGACY, imageGetter, tagHandler)

        @JvmStatic fun fromHtml(source: String, flags: Int, imageGetter: ImageGetter?, tagHandler: TagHandler?): Spanned {
            val parser = Parser()
            try { parser.setProperty(Parser.schemaProperty, schema) }
            catch (e: SAXNotRecognizedException) { throw RuntimeException(e) }
            catch (e: SAXNotSupportedException) { throw RuntimeException(e) }
            return HtmlToSpannedConverter(source, imageGetter, tagHandler, parser, flags).convert()
        }

        @Deprecated("Use toHtml(Spanned, Int) instead.")
        @JvmStatic fun toHtml(text: Spanned): String = toHtml(text, TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)

        @JvmStatic fun toHtml(text: Spanned, option: Int): String {
            val out = StringBuilder(); withinHtml(out, text, option); return out.toString()
        }

        @JvmStatic fun escapeHtml(text: CharSequence): String {
            val out = StringBuilder(); withinStyle(out, text, 0, text.length); return out.toString()
        }

        private fun withinHtml(out: StringBuilder, text: Spanned, option: Int) {
            if ((option and TO_HTML_PARAGRAPH_FLAG) == TO_HTML_PARAGRAPH_LINES_CONSECUTIVE) { encodeTextAlignmentByDiv(out, text, option); return }
            withinDiv(out, text, 0, text.length, option)
        }

        private fun encodeTextAlignmentByDiv(out: StringBuilder, text: Spanned, option: Int) {
            val len = text.length; var next: Int; var i = 0
            while (i < len) {
                next = text.nextSpanTransition(i, len, ParagraphStyle::class.java)
                val style = text.getSpans(i, next, ParagraphStyle::class.java)
                var elements = " "; var needDiv = false
                for (s in style) { if (s is AlignmentSpan) { needDiv = true; elements = when (s.alignment) { Layout.Alignment.ALIGN_CENTER -> "align=\"center\" $elements"; Layout.Alignment.ALIGN_OPPOSITE -> "align=\"right\" $elements"; else -> "align=\"left\" $elements" } } }
                if (needDiv) out.append("<div ").append(elements).append(">")
                withinDiv(out, text, i, next, option)
                if (needDiv) out.append("</div>")
                i = next
            }
        }

        private fun withinDiv(out: StringBuilder, text: Spanned, start: Int, end: Int, option: Int) {
            var next: Int; var i = start
            while (i < end) { next = text.nextSpanTransition(i, end, QuoteSpan::class.java); val quotes = text.getSpans(i, next, QuoteSpan::class.java); for (q in quotes) out.append("<blockquote>"); withinBlockquote(out, text, i, next, option); for (q in quotes) out.append("</blockquote>\n"); i = next }
        }

        private fun getTextDirection(text: Spanned, start: Int, end: Int): String = " dir=\"ltr\""

        private fun getTextStyles(text: Spanned, start: Int, end: Int, forceNoVerticalMargin: Boolean, includeTextAlign: Boolean): String {
            var margin: String? = null; var textAlign: String? = null
            if (forceNoVerticalMargin) margin = "margin-top:0; margin-bottom:0;"
            if (includeTextAlign) { val spans = text.getSpans(start, end, AlignmentSpan::class.java); for (i in spans.indices.reversed()) { val s = spans[i]; if ((text.getSpanFlags(s) and Spanned.SPAN_PARAGRAPH) == Spanned.SPAN_PARAGRAPH) { textAlign = when (s.alignment) { Layout.Alignment.ALIGN_NORMAL -> "text-align:start;"; Layout.Alignment.ALIGN_CENTER -> "text-align:center;"; Layout.Alignment.ALIGN_OPPOSITE -> "text-align:end;" }; break } } }
            if (margin == null && textAlign == null) return ""
            val style = StringBuilder(" style=\""); if (margin != null && textAlign != null) style.append(margin).append(" ").append(textAlign); else if (margin != null) style.append(margin); else style.append(textAlign); return style.append("\"").toString()
        }

        private fun withinBlockquote(out: StringBuilder, text: Spanned, start: Int, end: Int, option: Int) { if ((option and TO_HTML_PARAGRAPH_FLAG) == TO_HTML_PARAGRAPH_LINES_CONSECUTIVE) withinBlockquoteConsecutive(out, text, start, end) else withinBlockquoteIndividual(out, text, start, end) }

        private fun withinBlockquoteIndividual(out: StringBuilder, text: Spanned, start: Int, end: Int) {
            var isInList = false; var next: Int; var i = start
            while (i <= end) {
                next = TextUtils.indexOf(text, '\n', i, end); if (next < 0) next = end
                if (next == i) { if (isInList) { isInList = false; out.append("</ul>\n") }; out.append("<br>\n") }
                else {
                    var isListItem = false; val ps = text.getSpans(i, next, ParagraphStyle::class.java)
                    for (p in ps) { if ((text.getSpanFlags(p) and Spanned.SPAN_PARAGRAPH) == Spanned.SPAN_PARAGRAPH && p is BulletSpan) { isListItem = true; break } }
                    if (isListItem && !isInList) { isInList = true; out.append("<ul").append(getTextStyles(text, i, next, true, false)).append(">\n") }
                    if (isInList && !isListItem) { isInList = false; out.append("</ul>\n") }
                    val tagType = if (isListItem) "li" else "p"
                    out.append("<").append(tagType).append(getTextDirection(text, i, next)).append(getTextStyles(text, i, next, !isListItem, true)).append(">")
                    withinParagraph(out, text, i, next)
                    out.append("</").append(tagType).append(">\n")
                    if (next == end && isInList) { isInList = false; out.append("</ul>\n") }
                }
                i = next + 1
            }
        }

        private fun withinBlockquoteConsecutive(out: StringBuilder, text: Spanned, start: Int, end: Int) {
            out.append("<p").append(getTextDirection(text, start, end)).append(">"); var next: Int; var i = start
            while (i < end) { next = TextUtils.indexOf(text, '\n', i, end); if (next < 0) next = end; var nl = 0; while (next < end && text[next] == '\n') { nl++; next++ }; withinParagraph(out, text, i, next - nl); if (nl == 1) out.append("<br>\n"); else { for (j in 2 until nl) out.append("<br>"); if (next != end) { out.append("</p>\n"); out.append("<p").append(getTextDirection(text, start, end)).append(">") } }; i = next }
            out.append("</p>\n")
        }

        private fun withinParagraph(out: StringBuilder, text: Spanned, start: Int, end: Int) {
            var next: Int; var i = start
            while (i < end) {
                next = text.nextSpanTransition(i, end, CharacterStyle::class.java); val style = text.getSpans(i, next, CharacterStyle::class.java)
                for (s in style) {
                    if (s is StyleSpan) { val st = s.style; if ((st and Typeface.BOLD) != 0) out.append("<b>"); if ((st and Typeface.ITALIC) != 0) out.append("<i>") }
                    if (s is TypefaceSpan && s.family == "monospace") out.append("<tt>")
                    if (s is SuperscriptSpan) out.append("<sup>"); if (s is SubscriptSpan) out.append("<sub>")
                    if (s is UnderlineSpan) out.append("<u>"); if (s is StrikethroughSpan) out.append("<span style=\"text-decoration:line-through;\">")
                    if (s is URLSpan) out.append("<a href=\"").append(s.url).append("\">")
                    if (s is ImageSpan) { out.append("<img src=\"").append(s.source).append("\">"); i = next }
                    if (s is AbsoluteSizeSpan) { var sizeDip = s.size.toFloat(); if (!s.dip) sizeDip /= Resources.getSystem().displayMetrics.density; out.append(String.format(Locale.ROOT, "<span style=\"font-size:%.0fpx\">", sizeDip)) }
                    if (s is RelativeSizeSpan) out.append(String.format(Locale.ROOT, "<span style=\"font-size:%.2fem\">", s.sizeChange))
                    if (s is ForegroundColorSpan) out.append(String.format("<span style=\"color:#%06X;\">", 0xFFFFFF and s.foregroundColor))
                    if (s is BackgroundColorSpan) out.append(String.format("<span style=\"background-color:#%06X;\">", 0xFFFFFF and s.backgroundColor))
                }
                withinStyle(out, text, i, next)
                for (j in style.indices.reversed()) {
                    val s = style[j]
                    if (s is BackgroundColorSpan) out.append("</span>"); if (s is ForegroundColorSpan) out.append("</span>")
                    if (s is RelativeSizeSpan) out.append("</span>"); if (s is AbsoluteSizeSpan) out.append("</span>")
                    if (s is URLSpan) out.append("</a>"); if (s is StrikethroughSpan) out.append("</span>")
                    if (s is UnderlineSpan) out.append("</u>"); if (s is SubscriptSpan) out.append("</sub>")
                    if (s is SuperscriptSpan) out.append("</sup>")
                    if (s is TypefaceSpan && s.family == "monospace") out.append("</tt>")
                    if (s is StyleSpan) { val st = s.style; if ((st and Typeface.BOLD) != 0) out.append("</b>"); if ((st and Typeface.ITALIC) != 0) out.append("</i>") }
                }
                i = next
            }
        }

        private fun withinStyle(out: StringBuilder, text: CharSequence, start: Int, end: Int) {
            for (i in start until end) {
                val c = text[i]
                when { c == '<' -> out.append("&lt;"); c == '>' -> out.append("&gt;"); c == '&' -> out.append("&amp;"); c >= '\uD800' && c <= '\uDFFF' -> { if (c < '\uDC00' && i + 1 < end) { val d = text[i + 1]; if (d >= '\uDC00' && d <= '\uDFFF') { val codepoint = 0x010000 or ((c.code - 0xD800) shl 10) or (d.code - 0xDC00); out.append("&#").append(codepoint).append(";") } } }; c > '\u007E' || c < ' ' -> out.append("&#").append(c.code).append(";"); c == ' ' -> { var j = i; while (j + 1 < end && text[j + 1] == ' ') { out.append("&nbsp;"); j++ }; out.append(' ') }; else -> out.append(c) }
            }
        }
    }
}
