package forpdateam.ru.forpda.ui.fragments.search

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Html
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.URLSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.common.LinkMovementMethod
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.ui.fragments.theme.nativerender.BodyBlock
import forpdateam.ru.forpda.ui.fragments.theme.nativerender.PostBodyRenderer
import forpdateam.ru.forpda.ui.fragments.theme.nativerender.SmileProvider
import kotlin.math.roundToInt

/**
 * Renders a forum-post body ([forpdateam.ru.forpda.entity.remote.search.SearchItem.body]) NATIVELY into a
 * vertical [LinearLayout], for the «поиск по сообщениям форума» results — replacing the legacy WebView that
 * the search screen used to render post-body hits (Фаза 7: WebView-движок тем удалён, поиск переведён на
 * нативный рендер).
 *
 * Reuses the topic engine's standalone [PostBodyRenderer] (HTML → [BodyBlock]s) and [SmileProvider], but is
 * deliberately SELF-CONTAINED (does not touch the 1300-line topic adapter): search shows read-only result
 * cards, so there is no reply/quote/vote/rep chrome — just faithful body content that opens the post on tap.
 * Complex blocks are rendered with a compact native view; anything unclassified degrades to spanned text
 * (never a WebView, never a crash — §6 graceful fallback).
 */
class SearchPostBodyRenderer(
        private val linkHandler: ILinkHandler,
        private val onImageClick: (urls: List<String>, index: Int) -> Unit,
) {
    private val bodyParser = PostBodyRenderer()

    // HTML → [BodyBlock] parsing is the heavy part of a bind and [renderInto] runs on the main thread for
    // EVERY (re)bind while flinging the «поиск по сообщениям» list. RecyclerView rebinds the same item many
    // times during a scroll, so cache the parsed block tree by body HTML — the per-bind cost then drops to
    // just building views. Small LRU (bodies are large): enough to cover a viewport's worth of cards.
    private val blockCache = android.util.LruCache<String, List<BodyBlock>>(BLOCK_CACHE_SIZE)

    fun renderInto(container: LinearLayout, bodyHtml: String?) {
        container.removeAllViews()
        val key = bodyHtml.orEmpty()
        val blocks = blockCache.get(key) ?: try {
            bodyParser.render(bodyHtml).also { blockCache.put(key, it) }
        } catch (t: Throwable) {
            listOf(BodyBlock.Text(key))
        }
        val gallery = ArrayList<String>()
        renderBlocks(container, blocks, gallery, depth = 0)
    }

    private fun renderBlocks(container: LinearLayout, blocks: List<BodyBlock>, gallery: ArrayList<String>, depth: Int) {
        val ctx = container.context
        blocks.forEach { block ->
            val view: View = when (block) {
                is BodyBlock.Text -> textView(ctx, spanned(ctx, block.html))
                is BodyBlock.EditNote -> editNoteView(ctx, block.html)
                is BodyBlock.Image -> imageView(ctx, block, gallery)
                is BodyBlock.Quote -> quoteView(ctx, block, gallery, depth)
                is BodyBlock.Spoiler -> spoilerView(ctx, block, gallery, depth)
                is BodyBlock.Code -> codeView(ctx, block)
                is BodyBlock.FileAttachment -> fileAttachmentView(ctx, block)
                is BodyBlock.Table -> textView(ctx, spanned(ctx, tableToHtml(block)))
                is BodyBlock.WebFallback -> textView(ctx, spanned(ctx, block.html))
            }
            container.addView(view, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(ctx, 2f)
            })
        }
    }

    private fun textView(ctx: Context, text: CharSequence): TextView = TextView(ctx).apply {
        setText(text)
        textSize = 15f
        setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
        // Contrast-safe accent (parity with the topic renderer) instead of raw colorPrimary: on the light
        // «reading» palettes (e.g. Minimal Reader UI) colorPrimary is a muted near-invisible link colour.
        setLinkTextColor(contrastSafeLinkColor(ctx))
        setLineSpacing(0f, 1.1f)
        val hasLinks = text is Spanned && text.getSpans(0, text.length, URLSpan::class.java).isNotEmpty()
        if (hasLinks) {
            movementMethod = LinkMovementMethod(object : LinkMovementMethod.ClickListener {
                override fun onClick(url: String): Boolean = linkHandler.handle(url, null)
            })
        }
    }

    private fun editNoteView(ctx: Context, html: String): TextView = TextView(ctx).apply {
        // «Сообщение отредактировал <ник> — дата»: the nick is an <a> whose URLSpan paints the theme's
        // (pale, near-invisible) link colour and stays clickable. The edit note is muted metadata, so strip
        // the link → the whole line renders in the readable muted colorOnSurfaceVariant, non-clickable.
        val base = spanned(ctx, html)
        val text = if (base is Spanned) {
            SpannableStringBuilder(base).also { sb ->
                sb.getSpans(0, sb.length, URLSpan::class.java).forEach { sb.removeSpan(it) }
            }
        } else base
        setText(text)
        textSize = 13f
        setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
    }

    private fun imageView(ctx: Context, block: BodyBlock.Image, gallery: ArrayList<String>): View = ImageView(ctx).apply {
        val dm = ctx.resources.displayMetrics
        val columnWidthPx = (dm.widthPixels - dp(ctx, 32f)).coerceAtLeast(1)
        scaleType = ImageView.ScaleType.FIT_START
        adjustViewBounds = true
        // Search results are a DENSE list of preview cards, so EVERY image (content banner, attachment
        // screenshot, quoted picture) is a COMPACT thumbnail — fit within the column and a modest max height,
        // downscaled, NEVER upscaled. The topic renderer can afford full-column inline images; here a
        // full-width screenshot balloons every result (user: «раскрываю спойлер — большое изображение»).
        layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        maxWidth = columnWidthPx
        maxHeight = dp(ctx, THUMB_MAX_HEIGHT_DP)
        val viewerUrl = block.linkUrl ?: block.imageUrl
        val index = gallery.size
        gallery.add(viewerUrl)
        ForPdaCoil.loadInto(this, block.imageUrl)
        setOnClickListener { onImageClick(ArrayList(gallery), index) }
    }

    private fun quoteView(ctx: Context, block: BodyBlock.Quote, gallery: ArrayList<String>, depth: Int): View {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 10f), dp(ctx, 6f), dp(ctx, 10f), dp(ctx, 6f))
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 8f).toFloat()
                setColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerHigh))
            }
        }
        val head = listOfNotNull(block.author?.takeIf { it.isNotBlank() }, block.date?.takeIf { it.isNotBlank() })
                .joinToString(" · ")
        if (head.isNotEmpty()) {
            card.addView(TextView(ctx).apply {
                text = head
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                // colorAccent (parity with the topic renderer's quote header): colorPrimary is near-invisible
                // on the light «reading» / Material You palettes — the same pale-header bug as the topic title.
                setTextColor(ctx.getColorFromAttr(androidx.appcompat.R.attr.colorAccent))
            })
        }
        val inner = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        if (depth < MAX_NEST) renderBlocks(inner, block.inner, gallery, depth + 1)
        else inner.addView(textView(ctx, "…"))
        card.addView(inner)
        return card
    }

    private fun spoilerView(ctx: Context, block: BodyBlock.Spoiler, gallery: ArrayList<String>, depth: Int): View {
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (block.initiallyOpen) View.VISIBLE else View.GONE
            setPadding(dp(ctx, 10f), dp(ctx, 4f), 0, 0)
        }
        val header = TextView(ctx).apply {
            text = "▸ " + (block.title?.takeIf { it.isNotBlank() } ?: "Спойлер")
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            // colorAccent (parity with the topic renderer's spoiler header) — colorPrimary reads as pale grey
            // on the light «reading» / Material You palettes, so «Прикреплённые изображения» was unreadable.
            setTextColor(ctx.getColorFromAttr(androidx.appcompat.R.attr.colorAccent))
            setPadding(0, dp(ctx, 4f), 0, dp(ctx, 4f))
            setOnClickListener {
                val open = inner.visibility == View.VISIBLE
                inner.visibility = if (open) View.GONE else View.VISIBLE
                text = (if (open) "▸ " else "▾ ") + (block.title?.takeIf { it.isNotBlank() } ?: "Спойлер")
            }
        }
        if (depth < MAX_NEST) renderBlocks(inner, block.inner, gallery, depth + 1)
        root.addView(header)
        root.addView(inner)
        return root
    }

    private fun codeView(ctx: Context, block: BodyBlock.Code): View {
        val scroller = HorizontalScrollView(ctx).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 8f).toFloat()
                setColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerHighest))
            }
            setPadding(dp(ctx, 10f), dp(ctx, 8f), dp(ctx, 10f), dp(ctx, 8f))
        }
        scroller.addView(TextView(ctx).apply {
            text = block.text
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
        })
        return scroller
    }

    private fun fileAttachmentView(ctx: Context, block: BodyBlock.FileAttachment): View = TextView(ctx).apply {
        text = "📎 " + block.name
        textSize = 14f
        setTextColor(contrastSafeLinkColor(ctx))
        setPadding(0, dp(ctx, 4f), 0, dp(ctx, 4f))
        setOnClickListener { linkHandler.handle(block.url, null) }
    }

    /**
     * A link colour that stays readable on the current post surface — a self-contained copy of the topic
     * renderer's helper. Starts from the palette accent; on a DARK surface it demands a comfortable link
     * contrast and brightens the accent toward [colorOnSurface] to reach it (mirrors the WebView's near-white
     * link on dark); on a LIGHT surface it keeps the accent and only rescues a genuinely invisible one. This
     * is why the muted colorPrimary of the light «reading» palettes no longer makes links vanish.
     */
    private fun contrastSafeLinkColor(ctx: Context): Int {
        val accent = ctx.getColorFromAttr(androidx.appcompat.R.attr.colorAccent)
        val surface = ctx.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainer)
        val onSurface = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
        val surfaceIsDark = androidx.core.graphics.ColorUtils.calculateLuminance(surface) < 0.5
        val target = if (surfaceIsDark) DARK_SURFACE_LINK_CONTRAST else LOW_CONTRAST_THRESHOLD
        if (androidx.core.graphics.ColorUtils.calculateContrast(accent, surface) >= target) return accent
        var c = accent
        repeat(10) {
            c = androidx.core.graphics.ColorUtils.blendARGB(c, onSurface, 0.18f)
            if (androidx.core.graphics.ColorUtils.calculateContrast(c, surface) >= target) return c
        }
        return c
    }

    private fun tableToHtml(block: BodyBlock.Table): String = buildString {
        block.rows.forEach { row -> append(row.joinToString("  |  ")); append("<br>") }
    }

    private fun spanned(ctx: Context, html: String): CharSequence = try {
        val base = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT or Html.FROM_HTML_OPTION_USE_CSS_COLORS, null, null)
                .let { var e = it.length; while (e > 0 && (it[e - 1] == '\n' || it[e - 1] == ' ')) e--; it.subSequence(0, e) }
        val size = (ctx.resources.displayMetrics.scaledDensity * 18f).toInt().coerceAtLeast(1)
        val readable = stripLinkColors(neutralizeLowContrastColors(ctx, base))
        SmileProvider.applySmiles(readable, ctx.assets, size)
    } catch (t: Throwable) {
        SpannableStringBuilder(html) as Spannable
    }

    /**
     * Drop inline server text colours that are near-invisible on the reading surface — the same fix the topic
     * adapter applies. 4pda's post/quote markup carries CSS greys picked for a WHITE background, which the old
     * WebView neutralised via CSS but [Html.fromHtml] with FROM_HTML_OPTION_USE_CSS_COLORS applies verbatim →
     * on the dark card half the text «сливается с фоном». We remove only the low-contrast [ForegroundColorSpan]s
     * so that text falls back to the high-contrast colorOnSurface, while readable colours stay. Referenced
     * against colorSurface (the darkest relevant surface): a colour removed here always falls back to a colour
     * that is readable on both the card and the lighter quote bg.
     */
    private fun neutralizeLowContrastColors(ctx: Context, text: CharSequence): CharSequence {
        if (text !is Spanned) return text
        val spans = text.getSpans(0, text.length, android.text.style.ForegroundColorSpan::class.java)
        if (spans.isEmpty()) return text
        val surface = ctx.getColorFromAttr(com.google.android.material.R.attr.colorSurface)
        val bg = android.graphics.Color.rgb(
                android.graphics.Color.red(surface), android.graphics.Color.green(surface), android.graphics.Color.blue(surface))
        val out = SpannableStringBuilder(text)
        for (span in out.getSpans(0, out.length, android.text.style.ForegroundColorSpan::class.java)) {
            val fg = span.foregroundColor
            val opaqueFg = android.graphics.Color.rgb(
                    android.graphics.Color.red(fg), android.graphics.Color.green(fg), android.graphics.Color.blue(fg))
            if (androidx.core.graphics.ColorUtils.calculateContrast(opaqueFg, bg) < LOW_CONTRAST_THRESHOLD) {
                out.removeSpan(span)
            }
        }
        return out
    }

    /** Remove colour spans overlapping a link so the TextView's [setLinkTextColor] (accent) wins — matches the
     *  topic adapter; without it 4pda's inline-grey `<a style="color:#…">` links stay near-invisible on dark. */
    private fun stripLinkColors(text: CharSequence): CharSequence {
        if (text !is Spanned) return text
        val urls = text.getSpans(0, text.length, URLSpan::class.java)
        if (urls.isEmpty()) return text
        if (text.getSpans(0, text.length, android.text.style.ForegroundColorSpan::class.java).isEmpty()) return text
        val out = SpannableStringBuilder(text)
        for (fg in out.getSpans(0, out.length, android.text.style.ForegroundColorSpan::class.java)) {
            val fs = out.getSpanStart(fg); val fe = out.getSpanEnd(fg)
            val overlapsLink = urls.any { u ->
                val us = text.getSpanStart(u); val ue = text.getSpanEnd(u)
                fs < ue && us < fe
            }
            if (overlapsLink) out.removeSpan(fg)
        }
        return out
    }

    private fun dp(ctx: Context, v: Float): Int = (v * ctx.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val MAX_NEST = 4

        /** Below this fg↔bg contrast ratio an inline server colour is treated as invisible and dropped. */
        const val LOW_CONTRAST_THRESHOLD = 2.5

        /** Comfortable link contrast demanded on a dark surface (parity with the topic renderer). */
        const val DARK_SURFACE_LINK_CONTRAST = 5.5

        /** Max height of a preview image in a search-result card (compact thumbnail; downscale, never upscale). */
        const val THUMB_MAX_HEIGHT_DP = 200f

        /** Parsed-body LRU capacity: covers a viewport of post cards so a fling never re-parses HTML. */
        const val BLOCK_CACHE_SIZE = 48
    }
}
