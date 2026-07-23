package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.SpannedString
import android.text.style.URLSpan
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.common.FourPdaImageUrls
import forpdateam.ru.forpda.common.Html
import forpdateam.ru.forpda.common.LinkMovementMethod
import forpdateam.ru.forpda.common.MimeTypeUtil
import forpdateam.ru.forpda.common.SelectableLinkMovementMethod
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.presentation.ILinkHandler
import java.net.URLDecoder
import java.util.regex.Pattern

/**
 * Turns the [BodyBlock] list produced by [PostBodyRenderer] into native views.
 *
 * Extracted from `TopicPostsAdapter.PostViewHolder` so the SAME renderer serves every screen that
 * displays 4pda body markup: forum posts (native topic renderer) and QMS chat messages, which the
 * server emits with identical `.post-block` structure (quotes, spoilers, code, attachments, tables).
 *
 * The factory is stateless per render pass except for [textScale] / [searchQuery] (display prefs of
 * the current bind) and [spoilerStates] (expand state surviving view recycling), both owned by the
 * calling adapter. Everything block-shaped — colours, M3 containers, image sizing, link handling,
 * smile substitution, contrast rescue — lives here and only here.
 */
class BodyBlockViewFactory(
        private val linkHandler: ILinkHandler,
        private val spoilerStates: MutableMap<String, Boolean>,
        private val callbacks: Callbacks,
) {

    /** Host actions a rendered block can trigger. [scopeId] is the owning post / message id. */
    interface Callbacks {
        /** Tap on an attachment image → swipeable viewer over the whole scope's gallery. */
        fun onImageClick(galleryUrls: List<String>, index: Int)

        /** Long-press on a viewable image → actions menu (save / open in browser / copy link).
         *  [imageUrl] is the viewer-resolved full-size URL. */
        fun onImageLongClick(imageUrl: String) = Unit

        /** Long-press on a spoiler header → copy a deep link to it ([spoilNumber] is 1-based). */
        fun onSpoilerCopyLink(scopeId: Int, spoilNumber: Int) = Unit

        /** The user selected text inside the body and chose «Цитировать». */
        fun onQuoteSelection(scopeId: Int, selectedText: String) = Unit

        /**
         * Long-press on a downloadable file link → the host shows a chooser
         * (Скачать / Открыть в браузере).
         */
        fun onDownloadLinkLongPress(url: String, fileName: String?) = Unit

        /**
         * Tap on a downloadable file link → the host starts the download. Routed through the host
         * (not [linkHandler]) so it can pass an **Activity** context to the download: the app-context
         * `linkHandler.handle` path silently drops the UI context, so «Способ загрузки → Спрашивать
         * каждый раз» could never show its chooser and the download did nothing on non-SYSTEM methods.
         */
        fun onDownloadLinkTap(url: String, fileName: String?) = Unit

        /** Long-press on an in-text hyperlink → the host shows a chooser (открыть в браузере /
         *  поделиться / скопировать ссылку). */
        fun onLinkLongClick(url: String) = Unit

        /**
         * Tap on an in-content hyperlink, fired BEFORE the tap is routed to the link handler.
         * [sourcePostId] is the post/message that owns the tapped link. The host records it as the
         * in-tab Back anchor so «Назад» returns to the SOURCE post rather than the topmost-visible
         * one (a link low in a post makes that an EARLIER post peeking at the top).
         */
        fun onContentLinkTap(sourcePostId: Int, url: String) = Unit
    }

    /**
     * Per-body render pass state, threaded through the recursive block rendering.
     * [scopeId] keys the spoiler state and identifies the owner in [Callbacks].
     * [allowQuoteSelection] enables the «Цитировать» selection action (posts the user may quote).
     */
    class RenderScope(
            val scopeId: Int,
            val allowQuoteSelection: Boolean = false,
            /**
             * Whether body text auto-selects on long-press. `true` for forum posts (long-press → native
             * text selection + «Цитировать»). QMS chat bubbles pass `false` so a long-press falls through
             * to the bubble's own Telegram-style actions menu instead of the awkward native selection;
             * the menu's «Выделить текст» re-enables selection on demand.
             */
            val selectableText: Boolean = true,
    ) {
        var spoilerSeq: Int = 0

        /** 1-based-ish counter of quotes within the body, in document order (incl. nested) —
         *  keys the collapse state of long quotes in [spoilerStates] as `"scopeId:q<seq>"`. */
        var quoteSeq: Int = 0

        /** How many quote cards enclose the block being rendered — feeds the pre-measure width
         *  estimate that decides whether a long quote collapses. */
        var quoteDepth: Int = 0

        /**
         * The colour of the surface the CURRENT block's text is drawn on, used to judge which inline
         * server colours are invisible and how bright a link must be. `null` = the default post-card
         * surface ([readingSurfaceColor]); quote/spoiler blocks override it to their own tonal fill
         * ([blockFillColor]) while rendering their inner content, since that surface is a
         * different shade than the post card — otherwise black/white quoted text and dim links get
         * judged against the wrong background and stay unreadable inside the card.
         */
        var surfaceColorOverride: Int? = null

        /**
         * Viewer-resolved URLs of this body's attachment images, in document order (incl. nested in
         * quotes/spoilers). Built as images are rendered; each image view captures its own index so a
         * tap opens the whole body as one swipeable gallery (WebView parity).
         */
        val galleryUrls = ArrayList<String>()
    }

    /**
     * Width cap, in px, for text blocks rendered into a container that measures itself by its CONTENT
     * (a QMS chat bubble), or 0 for the post-card layout whose width is already fixed.
     *
     * A text block is added with MATCH_PARENT width, and a vertical `wrap_content` LinearLayout does not
     * let a MATCH_PARENT child contribute its width — so a bubble holding an attachment took its width
     * from the 150dp thumbnail, then re-measured the text into that narrow column WITHOUT recomputing the
     * height it had already derived from the first, wider pass. The overflowing lines were clipped: the
     * reported «если к сообщению прикреплена картинка и есть текст, то текст обрезается». With a positive
     * cap the text is laid out WRAP_CONTENT (so it widens the bubble up to this limit) and nothing is cut.
     */
    var textBlockMaxWidthPx: Int = 0

    /** The surface the text in [scope]'s current block is drawn on (block override or post card). */
    private fun currentSurface(ctx: Context, scope: RenderScope): Int =
            scope.surfaceColorOverride ?: readingSurfaceColor(ctx)

    /**
     * Render [blocks] into [container] as if drawn on [surfaceColor] (a quote/spoiler tonal fill), so the
     * contrast helpers judge their text/links against the card they actually sit on. Restores the previous
     * override afterwards, so nested quotes/spoilers unwind correctly.
     */
    private fun renderBlocksOnSurface(
            ctx: Context,
            container: LinearLayout,
            blocks: List<BodyBlock>,
            scope: RenderScope,
            surfaceColor: Int,
    ) {
        val previous = scope.surfaceColorOverride
        scope.surfaceColorOverride = surfaceColor
        try {
            renderBlocksInto(ctx, container, blocks, scope)
        } finally {
            scope.surfaceColorOverride = previous
        }
    }

    /** Scales all body text; 1.0 = the reference 16-px body. Set before each bind pass. */
    var textScale: Float = 1f

    /** Find-on-page query; matched substrings get a highlight background when non-blank. */
    var searchQuery: String = ""

    /** «Анимированные смайлы»: render smile spans as live GIFs instead of a static first frame.
     *  Set by the host before each bind pass (topic mirrors the pref; hosts that never set it get
     *  the static behaviour). Playback needs API 28+ — below that the flag silently degrades. */
    var animatedSmiles: Boolean = false

    /** «Плоские посты»: drop the hairline stroke on spoiler blocks (fill+radius stay).
     *  Quotes carry no stroke at all since the Telegram-style redesign, so the toggle only
     *  affects spoilers — code/attachment/fallback blocks keep their outline. */
    var flatBlocks: Boolean = false

    /**
     * Top margin (dp) between a block-level segment (quote / spoiler / code / table / image / edit-note /
     * fallback) and whatever precedes it. Follows the post-density setting so block spacing tightens in the
     * same step as the card padding/gap (Комфортная 10 · Компактная 6 · Сверхкомпактная 3) — otherwise
     * super-compact left big 10dp gaps between spoilers while the rest of the post was packed tight
     * (user question). The host sets it per bind pass; QMS/other hosts that never set it keep the default
     * comfortable spacing. See [BLOCK_SPACING_DP].
     */
    var blockSpacingDp: Float = BLOCK_SPACING_DP

    /**
     * The colour of the surface the text is read ON, used to decide which inline server colours are
     * invisible and how bright a link has to be. Defaults to the app-wide content-card fill (what a
     * post card uses); the QMS chat overrides it per bubble, since an own bubble is accent-tinted.
     */
    var readingSurfaceColor: ((Context) -> Int) = { ctx ->
        ctx.getColorFromAttr(forpdateam.ru.forpda.R.attr.content_card_surface)
    }

    private fun scaledSp(base: Float): Float = base * textScale

    /** Renders [blocks] as children of [container] (which is NOT cleared — the caller owns that). */
    fun render(container: LinearLayout, blocks: List<BodyBlock>, scope: RenderScope) {
        renderBlocksInto(container.context, container, blocks, scope)
    }

    private fun renderBlocksInto(
            ctx: Context,
            container: LinearLayout,
            blocks: List<BodyBlock>,
            scope: RenderScope,
    ) {
        val spacingPx = (blockSpacingDp * ctx.resources.displayMetrics.density).toInt()
        blocks.forEachIndexed { index, block ->
            val child = when (block) {
                is BodyBlock.Text -> textView(ctx, spanned(ctx, block.html), scope)
                is BodyBlock.EditNote -> editNoteView(ctx, block)
                is BodyBlock.Image -> imageView(ctx, block, scope)
                is BodyBlock.Quote -> quoteView(ctx, block, scope)
                is BodyBlock.Spoiler -> spoilerView(ctx, block, scope)
                is BodyBlock.Hidden -> hiddenView(ctx, block, scope)
                is BodyBlock.Code -> codeView(ctx, block)
                is BodyBlock.FileAttachment -> fileAttachmentView(ctx, block)
                is BodyBlock.Table -> tableView(ctx, block)
                is BodyBlock.WebFallback -> bindFallback(ctx, block, scope)
            }
            // Uniform spacing at EVERY block boundary. The per-block factories only ever set a TOP margin, so
            // a plain paragraph (Text/Offtop carry none) that FOLLOWS a spoiler/quote hugged its bottom edge —
            // "слишком близко к спойлерам и блокам" (user). Drive spacing centrally here instead: every child
            // after the first gets the same top margin (overriding whatever the factory set), the first gets
            // none, so text→block, block→text and block→block all space identically. Preserve any width/height
            // the factory chose (e.g. a Text block's WRAP_CONTENT for QMS bubble sizing) — mutate only topMargin.
            val lp = (child.layoutParams as? LinearLayout.LayoutParams)
                    ?: LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
            // A run of download files (Скачать: … .zip / …apk / …) stacks as one tight Telegram-style
            // group: consecutive FileAttachment rows hug with a hairline gap instead of the full block
            // spacing, so a multi-file post reads as a single compact block rather than sprawling.
            val tightToPrev = index > 0 &&
                    block is BodyBlock.FileAttachment && blocks[index - 1] is BodyBlock.FileAttachment
            lp.topMargin = when {
                index == 0 -> 0
                tightToPrev -> (2 * ctx.resources.displayMetrics.density).toInt()
                else -> spacingPx
            }
            child.layoutParams = lp
            container.addView(child)
        }
    }

    /**
     * Fill for an inline block card (quote / spoiler / code / attachment / …). Derived from the
     * post-card surface this block sits on ([readingSurfaceColor]) nudged one tonal step toward the
     * content colour, so the block always reads as a DISTINCT surface — even on the dark/AMOLED skins
     * that pin `colorSurfaceContainerHigh`/`Highest` to the same value as the post card, where the
     * bare M3 attr would make quotes and spoilers blend into the post (user report). On light skins
     * the step darkens slightly; either way the delta from the card is guaranteed by the blend.
     */
    private fun blockFillColor(ctx: Context): Int {
        val card = readingSurfaceColor(ctx)
        val onSurface = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
        return androidx.core.graphics.ColorUtils.blendARGB(card, onSurface, BLOCK_FILL_TONAL_STEP)
    }

    /**
     * Fill for a quote card: the accent washed over the surface the quote sits on (Telegram-style
     * tint), so quoted speech reads as a DIFFERENT voice, not just a nested block — and the delta
     * from the card is guaranteed on every palette, including the dark/AMOLED skins that pin all
     * surface roles to one value. A nested quote washes one more step over its parent's fill, which
     * doubles as the depth cue.
     *
     * The wash strength is chosen by the SURFACE luminance, not a flat alpha: the same alpha reads
     * far weaker on a light card than on a dark one (accent over near-white barely shifts, so a day
     * skin — Material You included, where the accent is the dynamic wallpaper primary — looked plain
     * grey; user report). On a light surface we tint harder so the colour actually shows; on a dark
     * one the subtle wash already reads, so we keep it low and avoid washing the card out.
     */
    private fun quoteFillColor(ctx: Context, scope: RenderScope): Int {
        val accent = ctx.getColorFromAttr(androidx.appcompat.R.attr.colorAccent)
        val surface = currentSurface(ctx, scope)
        val lightSurface = androidx.core.graphics.ColorUtils.calculateLuminance(surface) > 0.5
        val fraction = if (lightSurface) QUOTE_TINT_FRACTION_LIGHT else QUOTE_TINT_FRACTION_DARK
        return androidx.core.graphics.ColorUtils.blendARGB(surface, accent, fraction)
    }

    /**
     * Rounded Material 3 container for an inline block (quote / spoiler / …): a tonal fill plus a
     * 1dp [colorOutlineVariant] hairline and rounded corners, so nested blocks read as distinct M3
     * surfaces on every palette instead of flat rectangles.
     */
    private fun m3BlockBackground(
            ctx: Context,
            cornerDp: Float = 12f,
            flat: Boolean = false,
    ): android.graphics.drawable.GradientDrawable {
        val dm = ctx.resources.displayMetrics
        return android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = cornerDp * dm.density
            setColor(blockFillColor(ctx))
            setStroke(
                    if (flat) 0 else (1f * dm.density).toInt().coerceAtLeast(1),
                    ctx.getColorFromAttr(com.google.android.material.R.attr.colorOutlineVariant),
            )
        }
    }

    /**
     * Native spoiler: a tappable "▸/▾ title" header toggling a collapsible body of the recursively
     * rendered inner blocks. Open/collapsed state persists across recycling via [spoilerStates].
     */
    private fun spoilerView(ctx: Context, block: BodyBlock.Spoiler, scope: RenderScope): View {
        val dm = ctx.resources.displayMetrics
        val spoilNumber = scope.spoilerSeq + 1 // 1-based index of this spoiler within the body
        val key = "${scope.scopeId}:${scope.spoilerSeq++}"
        var open = spoilerStates[key] ?: block.initiallyOpen

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((10 * dm.density).toInt())
            background = m3BlockBackground(ctx, flat = flatBlocks)
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = (blockSpacingDp * dm.density).toInt() }
        }
        val label = block.title?.takeIf { it.isNotBlank() } ?: "Спойлер"
        val accent = ctx.getColorFromAttr(androidx.appcompat.R.attr.colorAccent)
        // Header row: a leading chevron that rotates open/closed + the bold accent title.
        val chevron = TextView(ctx).apply {
            text = "▸"
            textSize = scaledSp(13f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(accent)
        }
        val title = TextView(ctx).apply {
            text = label
            setTypeface(typeface, Typeface.BOLD)
            textSize = scaledSp(14f)
            setTextColor(accent)
            setPadding((6 * dm.density).toInt(), 0, 0, 0)
        }
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(chevron)
            addView(title)
        }
        val bodyContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8 * dm.density).toInt(), 0, 0)
        }
        fun applyState() {
            chevron.rotation = if (open) 90f else 0f
            bodyContainer.visibility = if (open) View.VISIBLE else View.GONE
        }
        renderBlocksOnSurface(ctx, bodyContainer, block.inner, scope, blockFillColor(ctx))
        applyState()
        // Toggle on the whole card, not just the header row — a collapsed spoiler is a thin strip and
        // the title alone is too small a touch target. When open, inner links/selectable text consume
        // their own touches, so only the header and card padding collapse it back.
        card.setOnClickListener {
            open = !open
            spoilerStates[key] = open
            applyState()
        }
        // Long-press the spoiler card → copy a deep link to it (parity with the WebView copySpoilerLink).
        card.setOnLongClickListener {
            callbacks.onSpoilerCopyLink(scope.scopeId, spoilNumber)
            true
        }
        card.addView(header)
        card.addView(bodyContainer)
        return card
    }

    /**
     * Native «Скрытый текст» block (`.post-block.hidden`): a labeled M3 container whose recursively-rendered
     * inner content is ALWAYS shown — unlike a spoiler it does not collapse on the site, it is just gated to
     * registered users (guests get an empty body). Rendering the body natively (not via the WebView text
     * fallback) is what makes an attachment image / table inside it appear: the fallback's Html.fromHtml has
     * no ImageGetter and silently dropped the picture, leaving an empty box (user report). Deliberately does
     * NOT touch [RenderScope.spoilerSeq], so hidden blocks never offset the spoiler copy-link numbering.
     */
    private fun hiddenView(ctx: Context, block: BodyBlock.Hidden, scope: RenderScope): View {
        val dm = ctx.resources.displayMetrics
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((10 * dm.density).toInt())
            background = m3BlockBackground(ctx, flat = flatBlocks)
            clipToOutline = true
        }
        val accent = ctx.getColorFromAttr(androidx.appcompat.R.attr.colorAccent)
        val header = TextView(ctx).apply {
            text = "Скрытый текст"
            setTypeface(typeface, Typeface.BOLD)
            textSize = scaledSp(14f)
            setTextColor(accent)
        }
        val bodyContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8 * dm.density).toInt(), 0, 0)
        }
        renderBlocksOnSurface(ctx, bodyContainer, block.inner, scope, blockFillColor(ctx))
        card.addView(header)
        card.addView(bodyContainer)
        return card
    }

    /**
     * Native quote, Telegram-style: an accent-tinted rounded card ([quoteFillColor]) with a 3dp
     * leading accent bar and a trailing «❞» mark, a tappable "author · date" header (jumps to the
     * source post via the app) and the recursively-rendered quoted content — nested quotes included.
     *
     * A quote taller than [QUOTE_COLLAPSE_TRIGGER_DP] (pre-measured at its real column width, so the
     * decision is made AT BIND TIME and the card never re-sizes under the scroll anchor) collapses to
     * [QUOTE_COLLAPSED_HEIGHT_DP] with a bottom fade + chevron; tapping the card or the chevron
     * expands it. Expand state survives recycling via [spoilerStates] under the `"scopeId:q<seq>"`
     * key (spoilers use plain numeric keys, so the namespaces never collide).
     */
    private fun quoteView(ctx: Context, block: BodyBlock.Quote, scope: RenderScope): View {
        val dm = ctx.resources.displayMetrics
        fun dp(v: Float): Int = (v * dm.density).toInt()
        val accent = ctx.getColorFromAttr(androidx.appcompat.R.attr.colorAccent)
        val fill = quoteFillColor(ctx, scope)
        val key = "${scope.scopeId}:q${scope.quoteSeq++}"

        val author = block.author?.takeIf { it.isNotBlank() }
        val date = block.date?.takeIf { it.isNotBlank() }
        // Author bold in full accent, the date appended muted (accent at ~60%, regular weight) — the
        // old single-style header read as one long label and buried the name.
        val headerText = SpannableStringBuilder(author ?: "Цитата").apply {
            setSpan(
                    android.text.style.StyleSpan(Typeface.BOLD),
                    0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            if (date != null) {
                val start = length
                append(" · ").append(date)
                setSpan(
                        android.text.style.ForegroundColorSpan(
                                androidx.core.graphics.ColorUtils.setAlphaComponent(accent, QUOTE_DATE_ALPHA)),
                        start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        val header = TextView(ctx).apply {
            text = headerText
            textSize = scaledSp(13f)
            setTextColor(accent)
            // End padding clears the «❞» mark overlaying the card's top-right corner.
            setPadding(0, 0, dp(18f), dp(2f))
            val src = block.sourceUrl?.takeIf { it.isNotBlank() }
            if (src != null) setOnClickListener { linkHandler.handle(src, null) }
        }
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), dp(8f), dp(10f), dp(8f))
        }
        content.addView(header)
        scope.quoteDepth++
        try {
            renderBlocksOnSurface(ctx, content, block.inner, scope, fill)
        } finally {
            scope.quoteDepth--
        }

        // Collapse decision: measure the finished content at the width the card will actually get
        // (post column or QMS bubble cap, minus the chrome of every enclosing quote level).
        val baseWidth = if (textBlockMaxWidthPx > 0) textBlockMaxWidthPx else dm.widthPixels - dp(40f)
        val cardWidth = (baseWidth - scope.quoteDepth * dp(QUOTE_LEVEL_CHROME_DP)).coerceAtLeast(dp(60f))
        content.measure(
                View.MeasureSpec.makeMeasureSpec(cardWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val collapsible = content.measuredHeight > dp(QUOTE_COLLAPSE_TRIGGER_DP)

        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        val card = android.widget.FrameLayout(ctx).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = QUOTE_CORNER_DP * dm.density
                setColor(fill)
            }
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(blockSpacingDp) }
        }
        // The leading accent bar: a plain strip clipped to the card's rounded outline.
        val bar = View(ctx).apply {
            setBackgroundColor(accent)
            layoutParams = android.widget.FrameLayout.LayoutParams(
                    dp(QUOTE_BAR_WIDTH_DP),
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        val mark = TextView(ctx).apply {
            text = "❞"
            textSize = scaledSp(14f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(androidx.core.graphics.ColorUtils.setAlphaComponent(accent, QUOTE_MARK_ALPHA))
            layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.TOP or android.view.Gravity.END,
            ).apply { topMargin = dp(2f); marginEnd = dp(8f) }
        }

        if (!collapsible) {
            column.addView(content)
        } else {
            var open = spoilerStates[key] ?: false
            // Fixed-height frame clipping the overflowing content; the fade dissolves the cut line
            // into the card fill so the clip reads as "continues below", not a rendering bug.
            val clip = android.widget.FrameLayout(ctx)
            clip.addView(content, android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            ))
            val fade = View(ctx).apply {
                background = android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(androidx.core.graphics.ColorUtils.setAlphaComponent(fill, 0), fill),
                )
                layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        dp(QUOTE_FADE_HEIGHT_DP),
                        android.view.Gravity.BOTTOM,
                )
            }
            clip.addView(fade)
            val chevron = TextView(ctx).apply {
                textSize = scaledSp(13f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(accent)
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setPadding(0, 0, 0, dp(4f))
                layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            }
            column.addView(clip, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
            ))
            column.addView(chevron)
            fun applyState() {
                clip.layoutParams = clip.layoutParams.apply {
                    height = if (open) LinearLayout.LayoutParams.WRAP_CONTENT
                    else dp(QUOTE_COLLAPSED_HEIGHT_DP)
                }
                fade.visibility = if (open) View.GONE else View.VISIBLE
                chevron.text = if (open) "▴" else "▾"
            }
            applyState()
            val toggle = View.OnClickListener {
                open = !open
                spoilerStates[key] = open
                applyState()
            }
            // Whole-card toggle (spoiler parity): inner links/selectable text consume their own
            // touches, so only the chevron, header gaps and card padding flip the state.
            chevron.setOnClickListener(toggle)
            card.setOnClickListener(toggle)
        }

        card.addView(column)
        card.addView(bar)
        card.addView(mark)
        return card
    }

    /**
     * Native inline attachment image. Reserves height from the server-provided display
     * dimensions BEFORE the bitmap loads, so a late-arriving image never slides the scroll
     * anchor (§2/§6). Tapping routes the attachment link through the app (image viewer /
     * download), same as the WebView path.
     */
    private fun imageView(ctx: Context, block: BodyBlock.Image, scope: RenderScope): View {
        val dm = ctx.resources.displayMetrics
        val horizontalChromePx = (40 * dm.density).toInt() // card margins + paddings
        val columnWidthPx = (dm.widthPixels - horizontalChromePx).coerceAtLeast(1)
        val ratio = if (block.displayWidthPx > 0 && block.displayHeightPx > 0) {
            block.displayHeightPx.toFloat() / block.displayWidthPx.toFloat()
        } else {
            DEFAULT_IMAGE_RATIO
        }
        val topInset = (6 * dm.density).toInt()
        // The tap target: the enclosing <a> if any, else the image itself.
        val tapUrl = block.linkUrl?.takeIf { it.isNotBlank() } ?: block.imageUrl
        val viewerUrl = FourPdaImageUrls.resolveViewerUrl(tapUrl)
        val viewable = FourPdaImageUrls.isViewableInViewer(viewerUrl)
        // A candidate «UPDATE / СКАЧАТЬ» button: an inline gif wrapped in a link that opens a source
        // post / download (NOT the image viewer). Whether to ENLARGE it is decided AFTER load from the
        // real aspect ratio — a tiny square service icon (snapback arrow, file-type icon) must stay small.
        val isButtonGif = block.inline && !block.linkUrl.isNullOrBlank() && !viewable &&
                block.imageUrl.substringBefore('?').endsWith(".gif", ignoreCase = true)
        return ImageView(ctx).apply {
            if (block.inline) {
                // INLINE content image (banner / preview / animated gif peeled from post text): render at
                // its INTRINSIC size, downscaled to fit the column but NEVER blindly upscaled — otherwise a
                // small icon / low-res arrow balloons into a blurry block (user report). Crisp, like the browser.
                layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = topInset }
                maxWidth = columnWidthPx
                maxHeight = dm.heightPixels
            } else {
                // ATTACHMENT picture: compact reserved-box THUMBNAIL (a tap opens the viewer).
                val thumbMaxPx = (150 * dm.density).toInt().coerceAtMost(columnWidthPx)
                val naturalWidth = (block.displayWidthPx * dm.density).toInt()
                val targetWidth = if (block.displayWidthPx > 0) naturalWidth.coerceIn(1, thumbMaxPx) else thumbMaxPx
                layoutParams = LinearLayout.LayoutParams(
                        targetWidth,
                        (targetWidth * ratio).toInt().coerceIn(1, dm.heightPixels),
                ).apply { topMargin = topInset }
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setBackgroundColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceVariant))
            // The inner <img> of an attach-file link (attachmentButton) and a linked inline gif (isButtonGif)
            // both need a post-load size check: 4pda serves them without width/height attrs, so only the
            // decoded bitmap tells a WIDE «СКАЧАТЬ» banner apart from a tiny square file-type mime glyph.
            if (isButtonGif || block.attachmentButton) {
                ForPdaCoil.loadInto(this, block.imageUrl) { w, h ->
                    val targetH = (BUTTON_GIF_HEIGHT_DP * dm.density).toInt()
                    val wideBanner = w > 0 && h > 0 && w.toFloat() / h >= 2.5f
                    when {
                        // WIDE, SHORT button graphic («UPDATE» ≈ 5:1): bump to a comfortable tap height.
                        wideBanner && h < targetH ->
                            (layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
                                lp.height = targetH
                                lp.width = LinearLayout.LayoutParams.WRAP_CONTENT
                                layoutParams = lp
                            }
                        // Tall/large banner: already big enough — leave at intrinsic size.
                        wideBanner -> Unit
                        // A small square glyph INSIDE an attach-file link is a decorative file-type icon:
                        // hide it (legacy `.ipb-attach.attach-file img{display:none}` parity) — the file chip
                        // below already names the file. Non-attach linked gifs (isButtonGif only) stay as-is.
                        block.attachmentButton -> {
                            visibility = View.GONE
                            (layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
                                lp.height = 0
                                lp.width = 0
                                lp.topMargin = 0
                                layoutParams = lp
                            }
                        }
                    }
                }
            } else {
                ForPdaCoil.loadInto(this, block.imageUrl)
            }
            if (viewable) {
                // Add to the body's running gallery and remember our slot; a tap opens the whole body
                // as one swipeable gallery starting on this image (WebView parity).
                val index = scope.galleryUrls.size
                scope.galleryUrls.add(viewerUrl)
                setOnClickListener { callbacks.onImageClick(scope.galleryUrls, index) }
                // WebView parity: long-press → save / open in browser / copy link menu.
                setOnLongClickListener { callbacks.onImageLongClick(viewerUrl); true }
            } else {
                // Non-viewable (e.g. an off-site link) → hand off to the link handler as before.
                setOnClickListener { linkHandler.handle(tapUrl, null) }
            }
        }
    }

    /** Native file attachment chip: a modern file glyph + filename on a panel, tap → download via the app. */
    /**
     * A downloadable file, drawn as ONE compact Telegram-style row: a circular accent badge with a white
     * file glyph, then the filename over a muted «size · скачиваний» subtitle. Replaces the old full-width
     * name-only chip that left the size/count to spill onto separate body lines below it — «не аккуратно и
     * размашисто» (user). The size/count are folded onto the block by [PostBodyRenderer.foldAttachmentMeta];
     * when both are absent the row is a single name line. Consecutive files are grouped tightly by the
     * spacing rule in [renderBlocksInto].
     */
    private fun fileAttachmentView(ctx: Context, block: BodyBlock.FileAttachment): View {
        val dm = ctx.resources.displayMetrics
        fun dp(v: Float): Int = (v * dm.density).toInt()
        val accent = ctx.getColorFromAttr(androidx.appcompat.R.attr.colorAccent)
        val muted = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(8f), dp(8f), dp(12f), dp(8f))
            background = m3BlockBackground(ctx)
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = (blockSpacingDp * dm.density).toInt() }
            isClickable = true
            setOnClickListener { callbacks.onDownloadLinkTap(block.url, block.name) }
            setOnLongClickListener {
                callbacks.onDownloadLinkLongPress(block.url, block.name)
                true
            }
        }

        // Circular accent badge with a contrast-picked file glyph (white on a dark accent, black on a light
        // one — luminance-based so it stays legible on any palette/AMOLED accent, not tied to colorOnPrimary).
        val badgePx = dp(38f)
        val iconInset = dp(9f)
        val onAccent = if (androidx.core.graphics.ColorUtils.calculateLuminance(accent) > 0.5) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        val badge = ImageView(ctx).apply {
            setImageDrawable(androidx.core.content.ContextCompat.getDrawable(ctx, forpdateam.ru.forpda.R.drawable.ic_attach_file_modern)?.mutate())
            imageTintList = android.content.res.ColorStateList.valueOf(onAccent)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(iconInset, iconInset, iconInset, iconInset)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(accent)
            }
            layoutParams = LinearLayout.LayoutParams(badgePx, badgePx)
        }

        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = dp(12f) }
        }
        texts.addView(TextView(ctx).apply {
            text = block.name
            // Bold, one step above the 14sp spoiler header: the plain-weight 14sp name read «совсем
            // мелко» next to its own badge and the surrounding body text (user).
            setTypeface(typeface, Typeface.BOLD)
            textSize = scaledSp(15f)
            setTextColor(accent)
            // Filenames are the whole point of the row — wrap onto as many lines as needed instead of
            // clipping with «…» at two lines (a mod APK name easily runs past 60 chars and the version /
            // ABI that distinguish two attachments live at the very end).
            setSingleLine(false)
            maxLines = Integer.MAX_VALUE
            ellipsize = null
            breakStrategy = android.text.Layout.BREAK_STRATEGY_SIMPLE
            hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
        })
        val subtitle = listOfNotNull(block.size, block.desc).joinToString("  ·  ")
        if (subtitle.isNotBlank()) {
            texts.addView(TextView(ctx).apply {
                text = subtitle
                textSize = scaledSp(12f)
                setTextColor(muted)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(1f), 0, 0)
            })
        }

        row.addView(badge)
        row.addView(texts)
        return row
    }

    /**
     * Native code block: monospace text in a horizontal scroller (long lines don't wrap) on a
     * distinct panel, with a "Копировать" action that puts the raw code on the clipboard.
     */
    private fun codeView(ctx: Context, block: BodyBlock.Code): View {
        val dm = ctx.resources.displayMetrics
        val pad = (8 * dm.density).toInt()
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = m3BlockBackground(ctx)
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = (blockSpacingDp * dm.density).toInt() }
        }
        val copyBtn = TextView(ctx).apply {
            text = block.title?.takeIf { it.isNotBlank() }?.let { "$it · Копировать" } ?: "Копировать"
            setTypeface(typeface, Typeface.BOLD)
            textSize = scaledSp(12f)
            setTextColor(ctx.getColorFromAttr(androidx.appcompat.R.attr.colorAccent))
            setPadding(pad, pad, pad, pad / 2)
            setOnClickListener {
                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as? android.content.ClipboardManager
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("code", block.text))
            }
        }
        val scroller = android.widget.HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
        }
        val code = TextView(ctx).apply {
            text = block.text
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = scaledSp(13f)
            setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
            setPadding(pad, 0, pad, pad)
            setHorizontallyScrolling(true)
        }
        scroller.addView(code)
        card.addView(copyBtn)
        card.addView(scroller)
        return card
    }

    /**
     * Native table (Фаза 6): a horizontally-scrollable grid of bordered cells, each cell a
     * Spannable TextView. Ragged rows are left-aligned. Merged cells aren't modelled — text
     * still shows in its origin cell.
     */
    private fun tableView(ctx: Context, block: BodyBlock.Table): View {
        val dm = ctx.resources.displayMetrics
        val cellPad = (8 * dm.density).toInt()
        val borderColor = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOutlineVariant)
        val grid = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(borderColor) // shows through 1px gaps as cell borders
        }
        block.rows.forEachIndexed { rowIndex, row ->
            val rowView = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                val topMargin = if (rowIndex == 0) 0 else (1 * dm.density).toInt()
                layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { setMargins(0, topMargin, 0, 0) }
            }
            row.forEachIndexed { colIndex, cellHtml ->
                val cell = TextView(ctx).apply {
                    setText(highlightSearchMatches(ctx, spanned(ctx, cellHtml)))
                    SmileProvider.startAnimations(this)
                    textSize = scaledSp(14f)
                    setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
                    setPadding(cellPad, cellPad, cellPad, cellPad)
                    setBackgroundColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorSurface))
                    minWidth = (64 * dm.density).toInt()
                    val leftMargin = if (colIndex == 0) 0 else (1 * dm.density).toInt()
                    layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.MATCH_PARENT,
                    ).apply { setMargins(leftMargin, 0, 0, 0) }
                }
                rowView.addView(cell)
            }
            grid.addView(rowView)
        }
        return android.widget.HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            addView(grid)
            layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = (blockSpacingDp * dm.density).toInt() }
        }
    }

    /** Фаза-1 degraded native preview for a complex block. Single swap point for the future WebView. */
    private fun bindFallback(ctx: Context, block: BodyBlock.WebFallback, scope: RenderScope): View {
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((10 * resources.displayMetrics.density).toInt())
            background = m3BlockBackground(ctx)
            clipToOutline = true
        }
        // NOTE: no «[KIND]» debug label — it is a dev artifact and must never reach users
        // (was surfacing e.g. «[UNKNOWN]» above a curator banner). Render only the content.
        // The fallback text sits on this panel's own tonal fill (m3BlockBackground → blockFillColor),
        // so neutralise inline server colours against THAT surface. Без этого ник, покрашенный сервером
        // в цвет группы под белый фон (напр. цитата с [member=…]/куратором), становится нечитаемым на
        // светлой карточке Sepia Blue (репорт: ник белым в цитировании) — тот же тракт, что и textView.
        val surface = blockFillColor(ctx)
        val content = TextView(ctx).apply {
            val text = neutralizeLowContrastColors(surface, stripLinkColors(spanned(ctx, block.html)))
            setText(text)
            SmileProvider.startAnimations(this)
            // Fallback body = same 16sp reference as the normal paragraph (see textView()).
            textSize = scaledSp(16f)
            setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
            setLinkTextColor(contrastSafeLinkColor(ctx, surface))
            setLineSpacing(0f, 1.1f)
            // Links inside a fallback block used to be DEAD: the panel rendered the URLSpans but never
            // installed a movement method, so neither tap nor long-press reached a handler (report: the
            // «Скачать: файл.zip» attachment and the «Теги:» links did nothing). 4pda wraps both in markup
            // that lands here, so route them like the native text block — with attachment links going to the
            // download callback, which carries the Activity context the «Способ загрузки» chooser needs.
            val hasLinks = text is Spanned &&
                    text.getSpans(0, text.length, URLSpan::class.java).isNotEmpty()
            if (hasLinks) {
                movementMethod = SelectableLinkMovementMethod(object : LinkMovementMethod.ClickListener {
                    override fun onClick(url: String): Boolean {
                        val fileName = attachmentFileNameOrNull(url)
                        if (fileName != null) {
                            callbacks.onDownloadLinkTap(url, fileName)
                            return true
                        }
                        callbacks.onContentLinkTap(scope.scopeId, url)
                        return linkHandler.handle(url, null)
                    }

                    override fun onLongClick(url: String): Boolean {
                        val fileName = attachmentFileNameOrNull(url)
                        if (fileName != null) {
                            callbacks.onDownloadLinkLongPress(url, fileName)
                        } else {
                            callbacks.onLinkLongClick(url)
                        }
                        return true
                    }
                })
            }
        }
        panel.addView(content)
        val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = (blockSpacingDp * ctx.resources.displayMetrics.density).toInt() }
        panel.layoutParams = lp
        return panel
    }

    /**
     * The file name if [url] is a 4pda post-attachment download (`/forum/dl/post/<id>/<name>`) that is NOT
     * a viewable picture, else `null`. Pictures stay on the link-handler path so a tap still opens them in
     * the image viewer instead of downloading them.
     */
    private fun attachmentFileNameOrNull(url: String): String? {
        val matcher = ATTACHMENT_URL.matcher(url)
        if (!matcher.find()) return null
        val rawName = matcher.group(1) ?: return null
        val extension = matcher.group(2) ?: return null
        if (MimeTypeUtil.isImage(extension)) return null
        // 4pda percent-encodes attachment names in CP1251; decoding keeps Cyrillic file names intact (the
        // same treatment LinkHandler.handleMedia gives them).
        return runCatching { URLDecoder.decode(rawName, "CP1251") }.getOrDefault(rawName)
    }

    /**
     * The server edit note («Сообщение отредактировал … — …», + «Причина редактирования: …») — a
     * SYSTEM meta line. Rendered smaller and muted (mirrors the WebView `.edit`: 0.875em, #757575) so
     * it visually separates from the user's own post text. The editor-nick link inside stays tappable.
     */
    private fun editNoteView(ctx: Context, block: BodyBlock.EditNote): View {
        val muted = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
        // Системная строка «Сообщение отредактировал N» — это метка, а не действие: ник должен быть
        // обычным muted-текстом, без гиперссылки и перехода в профиль. Убираем URLSpan целиком.
        val text = stripLinks(spanned(ctx, block.html))
        return TextView(ctx).apply {
            setText(text)
            SmileProvider.startAnimations(this)
            textSize = scaledSp(14f) // ~0.875 of the 16sp body (mirrors WebView .edit: 0.875em)
            setTextColor(muted)
            setLineSpacing(0f, 1.15f)
            layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = (blockSpacingDp * ctx.resources.displayMetrics.density).toInt() }
        }
    }

    /** Remove URLSpans (and any colour spans overlapping them) so the text renders as plain, non-clickable
     *  content — used for the «отредактировал N» system note where the nick must NOT be a link. */
    private fun stripLinks(text: CharSequence): CharSequence {
        if (text !is Spanned) return text
        val urls = text.getSpans(0, text.length, URLSpan::class.java)
        if (urls.isEmpty()) return text
        val out = SpannableStringBuilder(text)
        for (u in out.getSpans(0, out.length, URLSpan::class.java)) out.removeSpan(u)
        for (fg in out.getSpans(0, out.length, android.text.style.ForegroundColorSpan::class.java)) out.removeSpan(fg)
        return out
    }

    private fun textView(ctx: Context, text: CharSequence, scope: RenderScope): TextView {
        return TextView(ctx).apply {
            if (textBlockMaxWidthPx > 0) {
                layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                maxWidth = textBlockMaxWidthPx
            }
            val surface = currentSurface(ctx, scope)
            setText(highlightSearchMatches(ctx, neutralizeLowContrastColors(surface, stripLinkColors(text))))
            SmileProvider.startAnimations(this)
            // Body base = 16sp so at «Размер шрифта в темах» = N (textScale = N/16) the paragraph
            // renders at N sp — matching the news/WebView path, which sets `defaultFontSize = N` px
            // directly. A 15sp base rendered every theme one step smaller than the same setting in an
            // open article (report: set 20 → тема ~17, новость 20). REFERENCE_FONT_SIZE is 16 too.
            textSize = scaledSp(16f)
            setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
            // Force in-text links (profile nicks in the hat / «отредактировал N» footer) to the readable
            // accent — their server-side inline colour is picked for a white bg and vanishes on Sepia.
            // Use a contrast-safe variant: the per-palette accent is tuned for that palette's LIGHT card,
            // so on an AMOLED/dark surface it must be brightened or links «сливаются с фоном».
            setLinkTextColor(contrastSafeLinkColor(ctx, surface))
            setLineSpacing(0f, 1.1f)
            val hasLinks = text is Spanned &&
                    text.getSpans(0, text.length, URLSpan::class.java).isNotEmpty()
            val linkClicks = object : LinkMovementMethod.ClickListener {
                override fun onClick(url: String): Boolean {
                    callbacks.onContentLinkTap(scope.scopeId, url)
                    return linkHandler.handle(url, null)
                }
                override fun onLongClick(url: String): Boolean {
                    callbacks.onLinkLongClick(url)
                    return true
                }
            }
            if (scope.selectableText) {
                // Selectable: native Copy/Share plus a custom «Цитировать» that wraps the selection in a
                // [quote …] for the reply editor (§4 selection→quote). Previously a block with ANY link
                // (e.g. a clickable @mention nick) fell into a link-only branch and could not be
                // selected/copied at all — long-press did nothing.
                setTextIsSelectable(true)
                installQuoteSelectionAction(this, scope)
                if (hasLinks) {
                    // A selection-aware movement method: keeps the ArrowKeyMovementMethod selection
                    // behaviour that setTextIsSelectable installs AND routes link tap/long-press in-app.
                    movementMethod = SelectableLinkMovementMethod(linkClicks)
                }
            } else {
                // Non-selectable (QMS chat bubbles): a selectable TextView claims the long-press for its
                // text-selection ActionMode (the awkward «текст выделяется + Копировать рядом с Удалить»).
                // Here the long-press falls through to the bubble's own Telegram-style actions menu
                // (Копировать / Выделить текст / Удалить); «Выделить текст» re-enables selection on demand.
                // Link tap/long-press still route in-app via the plain link-only movement.
                if (hasLinks) {
                    movementMethod = LinkMovementMethod(linkClicks)
                }
            }
        }
    }

    /**
     * Drop inline server text colours that are near-invisible on the current reading surface. The 4pda
     * topic hat is full of colours picked for a WHITE background (white/pale nicks, headers), which the
     * WebView neutralises via CSS but [Html.fromHtml] with FROM_HTML_OPTION_USE_CSS_COLORS applies
     * verbatim → on Sepia/Nord/… half the hat text (and the «отредактировал»/«Куратор темы» nicks) turns
     * invisible, leaving big empty gaps. We remove only the low-contrast spans so that text falls back to
     * the high-contrast colorOnSurface, while readable colours (green curator note, links) stay.
     */
    private fun neutralizeLowContrastColors(surface: Int, text: CharSequence): CharSequence {
        if (text !is Spanned) return text
        if (text.getSpans(0, text.length, android.text.style.ForegroundColorSpan::class.java).isEmpty()) return text
        val bg = android.graphics.Color.rgb(
                android.graphics.Color.red(surface),
                android.graphics.Color.green(surface),
                android.graphics.Color.blue(surface))
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

    /**
     * Force links to the theme's readable link colour. 4pda wraps hat nav links / edit-note nicks in
     * inline greys (`<a style="color:#…">` or a coloured parent `<span>`) that in dark mode are almost
     * invisible — and an inline [android.text.style.ForegroundColorSpan] overrides the TextView's
     * linkTextColor. Removing any colour span overlapping a [URLSpan] lets [TextView.setLinkTextColor]
     * win, so every link is readable (parity with the WebView, which colours all links with the link colour).
     */
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

    /**
     * A link colour that stays readable on the current post surface. Some per-palette accents
     * (e.g. Sepia Blue #4F7896) are tuned for that palette's LIGHT cream card and sit at only
     * ~4.4:1 on BOTH the light card AND an AMOLED black surface — technically legible but
     * perceptually dim on black, so links «сливаются с чёрным фоном». A single contrast threshold
     * can't tell the two apart, so we gate on surface darkness: on a DARK surface we demand a
     * comfortable link contrast (and brighten the accent toward [colorOnSurface] to reach it,
     * mirroring the WebView, which uses a near-white link colour on dark); on a LIGHT surface we
     * keep the accent untouched and only rescue a genuinely invisible one.
     */
    private fun contrastSafeLinkColor(ctx: Context, surfaceRaw: Int): Int {
        // ColorUtils.calculateContrast требует НЕпрозрачный фон; на части палитр/тем
        // surface приходит полупрозрачным (alpha<255) → IllegalArgumentException
        // «background can not be translucent». Форсим непрозрачность (как в
        // neutralizeLowContrastColors выше).
        val surface = surfaceRaw or 0xFF000000.toInt()
        val accent = ctx.getColorFromAttr(androidx.appcompat.R.attr.colorAccent)
        val onSurface = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
        val surfaceIsDark = androidx.core.graphics.ColorUtils.calculateLuminance(surface) < 0.5
        val target = if (surfaceIsDark) DARK_SURFACE_LINK_CONTRAST else LOW_CONTRAST_THRESHOLD
        if (androidx.core.graphics.ColorUtils.calculateContrast(accent, surface) >= target) {
            return accent
        }
        var c = accent
        repeat(10) {
            c = androidx.core.graphics.ColorUtils.blendARGB(c, onSurface, 0.18f)
            if (androidx.core.graphics.ColorUtils.calculateContrast(c, surface) >= target) {
                return c
            }
        }
        return c
    }

    /**
     * True if the selection toolbar carries framework «smart text» (assist) actions — e.g. the
     * «Открыть» item Android injects when the selection is a URL/email/phone. Those items dispatch
     * through a click-listener the framework keeps keyed by the ORIGINAL MenuItem instance
     * (`Editor.mAssistClickHandlers`), NOT through a plain `intent`. So a menu.clear()+re-add
     * reorder (used to pull «Цитировать»/«Удалить» to the front on MIUI) silently strips their
     * handler and the item becomes a dead no-op. When such items are present we must NOT rebuild
     * the menu — our own item still shows via SHOW_AS_ACTION_ALWAYS, just not forced to the front.
     */
    private fun menuHasAssistActions(menu: android.view.Menu): Boolean {
        for (i in 0 until menu.size()) {
            val mi = menu.getItem(i)
            if (mi.groupId == android.R.id.textAssist || mi.itemId == android.R.id.textAssist) return true
        }
        return false
    }

    /** Adds a «Цитировать» item to the text-selection action bar → quotes the selection into the editor. */
    private fun installQuoteSelectionAction(tv: TextView, scope: RenderScope) {
        if (!scope.allowQuoteSelection) return
        tv.customSelectionActionModeCallback = object : android.view.ActionMode.Callback {
            /** Add the «Цитировать» item (front of the menu, always visible). */
            fun addQuoteItem(menu: android.view.Menu) {
                // ALWAYS (not IF_ROOM): MIUI/HyperOS floating toolbar drops app items that land in the
                // hidden overflow — forcing the primary row keeps «Цитировать» visible on Xiaomi.
                menu.add(0, QUOTE_MENU_ID, 0, "Цитировать")
                        .setShowAsActionFlags(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
            /** Idempotently ensure the «Цитировать» item is present; returns true if it was (re)added. */
            fun ensureQuoteItem(menu: android.view.Menu): Boolean {
                if (menu.findItem(QUOTE_MENU_ID) != null) return false
                addQuoteItem(menu)
                return true
            }
            /**
             * Force «Цитировать» to the FRONT of the selection toolbar. MIUI/HyperOS ignores our
             * menu `order` (the item lands after «Копировать»), so we rebuild the menu with our item
             * first, then re-add the system items preserving their id/group/title/intent — Copy/Share
             * are dispatched by item id, so their behaviour is unchanged. Adding ours first wins under
             * BOTH orderings a ROM might use (lowest `order` AND first-inserted). Guarded: any failure
             * falls back to merely ensuring the item exists, so the menu is never left broken.
             */
            fun reorderQuoteFirst(menu: android.view.Menu): Boolean {
                if (menu.size() > 0 && menu.getItem(0).itemId == QUOTE_MENU_ID) return false
                // Never rebuild a menu that holds framework smart-text actions (e.g. «Открыть» for a
                // selected URL): the rebuild would strip their click handler and kill the action.
                if (menuHasAssistActions(menu)) return ensureQuoteItem(menu)
                return try {
                    val others = ArrayList<Array<Any?>>(menu.size())
                    for (i in 0 until menu.size()) {
                        val mi = menu.getItem(i)
                        if (mi.itemId == QUOTE_MENU_ID) continue
                        others.add(arrayOf(mi.groupId, mi.itemId, mi.order, mi.title, mi.intent))
                    }
                    menu.clear()
                    addQuoteItem(menu)
                    for (o in others) {
                        val re = menu.add(o[0] as Int, o[1] as Int, o[2] as Int, o[3] as CharSequence?)
                                .setShowAsActionFlags(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM)
                        (o[4] as? android.content.Intent)?.let { re.intent = it }
                    }
                    true
                } catch (t: Throwable) {
                    ensureQuoteItem(menu)
                }
            }
            override fun onCreateActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean {
                ensureQuoteItem(menu)
                return true
            }
            // MIUI/HyperOS rebuilds the floating-selection toolbar in onPrepare and drops / reorders the
            // item added in onCreate. Re-add it AND pull it to the front here (stock Android already has
            // it first, so reorderQuoteFirst early-returns — a no-op there).
            override fun onPrepareActionMode(mode: android.view.ActionMode, menu: android.view.Menu) =
                    reorderQuoteFirst(menu)
            override fun onActionItemClicked(mode: android.view.ActionMode, menuItem: android.view.MenuItem): Boolean {
                if (menuItem.itemId == QUOTE_MENU_ID) {
                    val s = tv.selectionStart.coerceAtLeast(0)
                    val e = tv.selectionEnd.coerceAtLeast(0)
                    if (e > s) callbacks.onQuoteSelection(scope.scopeId, tv.text.subSequence(s, e).toString())
                    mode.finish()
                    return true
                }
                return false
            }
            override fun onDestroyActionMode(mode: android.view.ActionMode) {}
        }
    }

    /** Wrap each case-insensitive [searchQuery] match in [text] with a highlight background span. */
    private fun highlightSearchMatches(ctx: Context, text: CharSequence): CharSequence {
        val q = searchQuery
        if (q.isBlank()) return text
        val out = android.text.SpannableStringBuilder(text)
        val hay = out.toString()
        val color = ctx.getColorFromAttr(androidx.appcompat.R.attr.colorAccent)
        val onPrimary = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnPrimary)
        var i = hay.indexOf(q, ignoreCase = true)
        while (i >= 0) {
            out.setSpan(android.text.style.BackgroundColorSpan(color), i, i + q.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(android.text.style.ForegroundColorSpan(onPrimary),
                    i, i + q.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            i = hay.indexOf(q, i + q.length, ignoreCase = true)
        }
        return out
    }

    /**
     * The markup → [Spanned] step, memoised in [HTML_CACHE].
     *
     * [Html.fromHtml] runs a full XML parse and is by far the most expensive thing a bind does — on a
     * mid-sized post it measured 2–22 ms on the UI thread, and it ran AGAIN every time the post scrolled
     * back into view, so a fast fling dropped frames («микролаги при скроле»). The parse depends on
     * NOTHING but the markup itself — not the palette, not the font scale, not the search query — so its
     * result is cached under the markup string and shared by every bind of that body.
     *
     * What is deliberately NOT cached is everything downstream of the parse: smile spans (each needs its
     * own drawable — a shared [android.graphics.drawable.AnimatedImageDrawable] would be driven by two
     * TextViews at once), the contrast/link-colour rescue (depends on the current surface, so a palette
     * switch must recompute it) and the find-on-page highlight. Those are cheap span passes over an
     * already-parsed text.
     *
     * The cached instance is never handed to a TextView directly: a selectable TextView writes selection
     * spans INTO the Spannable it is given, which would mutate the cache entry (and leak a selection
     * between two views). Every call therefore copies first.
     */
    private fun spanned(ctx: Context, html: String): CharSequence = try {
        val base = HTML_CACHE.get(html) ?: Html
                .fromHtml(html, Html.FROM_HTML_MODE_COMPACT or Html.FROM_HTML_OPTION_USE_CSS_COLORS, null, null)
                .trimTrailingNewlines()
                .let { SpannedString(it) }
                .also { HTML_CACHE.put(html, it) }
        // Replace 4pda smile shortcodes (:thank_you: …) with inline images from bundled assets. Runs on a
        // private copy — see the doc above (and applySmiles itself copies again only when it finds smiles).
        val smileSize = (ctx.resources.displayMetrics.scaledDensity * scaledSp(SMILE_SIZE_SP)).toInt().coerceAtLeast(1)
        SmileProvider.applySmiles(SpannableStringBuilder(base), ctx.resources, smileSize, animatedSmiles)
    } catch (t: Throwable) {
        // Graceful degradation (§6): never crash on a single body's markup.
        SpannableStringBuilder(html)
    }

    companion object {
        /**
         * Parse every markup run in [blocks] into [HTML_CACHE] ahead of time. Call OFF the main thread as
         * soon as a page's posts are known: the cache alone only pays off on a RE-bind, while a fast scroll
         * down meets each post for the FIRST time and would still pay the parse on the UI thread. Warming
         * moves that work to a background thread, so the bind that finally shows the post just reads spans.
         *
         * [Html.fromHtml] touches no UI state (it builds spans, decodes no bitmaps) and [HTML_CACHE] is an
         * [android.util.LruCache], which is synchronised — so a warm-up racing with a bind of the same post
         * is safe: worst case both parse it and the later put wins.
         */
        fun prewarm(blocks: List<BodyBlock>) {
            for (block in blocks) when (block) {
                is BodyBlock.Text -> prewarmHtml(block.html)
                is BodyBlock.EditNote -> prewarmHtml(block.html)
                is BodyBlock.WebFallback -> prewarmHtml(block.html)
                is BodyBlock.Quote -> prewarm(block.inner)
                is BodyBlock.Spoiler -> prewarm(block.inner)
                is BodyBlock.Hidden -> prewarm(block.inner)
                is BodyBlock.Table -> block.rows.forEach { row -> row.forEach(::prewarmHtml) }
                is BodyBlock.Image, is BodyBlock.Code, is BodyBlock.FileAttachment -> Unit
            }
        }

        private fun prewarmHtml(html: String) {
            if (HTML_CACHE.get(html) != null) return
            runCatching {
                HTML_CACHE.put(html, SpannedString(Html
                        .fromHtml(html, Html.FROM_HTML_MODE_COMPACT or Html.FROM_HTML_OPTION_USE_CSS_COLORS, null, null)
                        .trimTrailingNewlines()))
            }
        }

        private fun CharSequence.trimTrailingNewlines(): CharSequence {
            var end = length
            while (end > 0 && (this[end - 1] == '\n' || this[end - 1] == ' ')) end--
            return subSequence(0, end)
        }

        /**
         * Parsed-markup cache behind [spanned], shared by every renderer instance (topic posts and QMS
         * chat alike — a quoted post shows up in both). Sized in CHARACTERS of markup, not entries, so one
         * monstrous topic hat can't evict a whole page of ordinary posts; ~512k chars ≈ a couple of MB of
         * spans, which is small next to the image cache and bounded whatever the user scrolls through.
         */
        val HTML_CACHE = object : android.util.LruCache<String, Spanned>(512 * 1024) {
            override fun sizeOf(key: String, value: Spanned): Int = key.length
        }

        /** A 4pda post-attachment download link: `…/forum/dl/post/<id>/<name>.<ext>`. */
        val ATTACHMENT_URL: Pattern =
                Pattern.compile("""https?://4pda\.(?:to|ru)/forum/dl/post/\d+/(.+\.([^./?#]+))(?:[?#]|$)""")

        const val DEFAULT_IMAGE_RATIO = 0.66f

        /**
         * DEFAULT (Комфортная density) top margin (dp) between block-level segments. Applied uniformly so
         * block-to-block spacing is consistent regardless of whether the author put a stray `<br>` around the
         * block — those edge breaks are stripped in [PostBodyRenderer.flushInline], so this margin is the ONLY
         * thing spacing blocks now (a touch more air than the old 6dp; user report: after the trim blocks sat
         * too tight). The topic host overrides it per density via [blockSpacingDp]; hosts that don't set it
         * (QMS chat) keep this comfortable value.
         */
        const val BLOCK_SPACING_DP = 10f

        /** Comfortable rendered height (dp) for a small linked «UPDATE / СКАЧАТЬ» button gif: at its
         *  intrinsic size it is only a few dp tall — too small to read or reliably tap. */
        const val BUTTON_GIF_HEIGHT_DP = 40f
        const val SMILE_SIZE_SP = 18f
        const val QUOTE_MENU_ID = 0x71_0716

        // Below this WCAG contrast ratio against the reading surface, an inline server text colour is
        // treated as invisible and dropped so the text falls back to colorOnSurface. ~2.5 keeps readable
        // colours (green curator note ≈4.5, medium greys ≈3.5) but strips white/pale-on-Sepia (≈1.2–2.0).
        const val LOW_CONTRAST_THRESHOLD = 2.5

        /** Comfortable link contrast on a DARK/AMOLED post surface, where saturated mid-blue accents
         *  read dim even above the bare-legibility floor. Above this we brighten the link. */
        const val DARK_SURFACE_LINK_CONTRAST = 5.5

        /** How far an inline block card's fill is nudged from the post card toward the content colour
         *  (see [blockFillColor]). Small enough to stay a subtle M3 tonal step, large enough to keep
         *  quotes/spoilers visibly distinct on skins that pin all surface roles to one value. */
        const val BLOCK_FILL_TONAL_STEP = 0.07f

        /** How far a quote card's fill is blended from the surface toward the accent, on a DARK
         *  surface — subtle, since accent-over-dark already reads clearly (see [quoteFillColor]). */
        const val QUOTE_TINT_FRACTION_DARK = 0.12f

        /** Same, on a LIGHT surface — stronger, because the same blend fraction reads far weaker
         *  over near-white, so a subtle wash looked plain grey (user report). */
        const val QUOTE_TINT_FRACTION_LIGHT = 0.20f

        /** Alpha of the decorative «❞» mark in the quote's top-right corner (~50% accent). */
        const val QUOTE_MARK_ALPHA = 128

        /** Alpha of the date part of the quote header (~60% accent) — present but quieter than
         *  the bold author name. */
        const val QUOTE_DATE_ALPHA = 153

        const val QUOTE_BAR_WIDTH_DP = 3f
        const val QUOTE_CORNER_DP = 8f

        /** A quote whose content pre-measures taller than this collapses. Deliberately above
         *  [QUOTE_COLLAPSED_HEIGHT_DP] (hysteresis): expanding must always reveal meaningfully
         *  more, and a barely-over quote is cheaper shown whole than behind a chevron. */
        const val QUOTE_COLLAPSE_TRIGGER_DP = 150f

        /** Visible content height of a collapsed quote (~5 lines of quoted text). */
        const val QUOTE_COLLAPSED_HEIGHT_DP = 100f

        /** Height of the bottom fade dissolving clipped content into the card fill. */
        const val QUOTE_FADE_HEIGHT_DP = 28f

        /** Horizontal chrome one quote level adds around its content (bar + paddings), used by the
         *  pre-measure width estimate for NESTED quotes. */
        const val QUOTE_LEVEL_CHROME_DP = 25f
    }
}
