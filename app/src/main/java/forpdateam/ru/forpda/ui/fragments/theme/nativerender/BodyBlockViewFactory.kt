package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.SpannedString
import android.text.style.URLSpan
import android.view.Gravity
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

        /**
         * Running count of find-on-page occurrences met while rendering this body, in document order.
         * Identifies the active occurrence without threading char offsets around: the block being
         * painted knows that its k-th match is the `searchSeq + k`-th of the post (see
         * [TopicSearchScan], which counts the same units in the same order).
         */
        var searchSeq: Int = 0

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

    /**
     * The ONE occurrence the user is currently standing on, painted solid instead of the pale
     * background every other match gets — «видно, куда именно привело», like a browser's find bar.
     * [ActiveMatch.ordinal] counts occurrences within the post in the [TopicSearchScan] traversal order,
     * which is also the order the renderer meets them, so the two sides agree without passing offsets.
     * null = a plain query with no active occurrence (nothing found, or the bar was just opened).
     */
    var activeMatch: ActiveMatch? = null

    /** Which occurrence of [searchQuery] is the active one: [ordinal]-th match inside post [scopeId]. */
    data class ActiveMatch(val scopeId: Int, val ordinal: Int)

    /**
     * Marks the active occurrence's background span so the host can find the exact TextView (and line)
     * it landed on and scroll it into view — a bare [android.text.style.BackgroundColorSpan] would be
     * indistinguishable from the pale ones.
     */
    class ActiveSearchMatchSpan(color: Int) : android.text.style.BackgroundColorSpan(color)

    /** «Анимированные смайлы»: render smile spans as live GIFs instead of a static first frame.
     *  Set by the host before each bind pass (topic mirrors the pref; hosts that never set it get
     *  the static behaviour). Playback needs API 28+ — below that the flag silently degrades. */
    var animatedSmiles: Boolean = false

    /** «Плоские посты»: drop the hairline stroke on spoiler blocks (fill+radius stay).
     *  Quotes carry no stroke at all since the Telegram-style redesign, so the toggle only
     *  affects spoilers — code/attachment/fallback blocks keep their outline. */
    var flatBlocks: Boolean = false

    /**
     * «Современная дата поста»: use the same relative form in quote headers and merged-post
     * «Добавлено …» markers as in the owning post header. Left false by shared non-topic hosts (QMS).
     */
    var modernPostDates: Boolean = false

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
        var index = 0
        while (index < blocks.size) {
            val block = blocks[index]
            // A RUN of viewable pictures (a wallpaper/screenshot spoiler posts dozens back to back) becomes
            // ONE grid instead of one full-width row each — stacked singly they left the rest of the line
            // empty and made browsing a long series a scroll marathon (user report).
            val gridRun = imageGridRunLength(blocks, index)
            val inlineIconText = if (gridRun == 0 && block is BodyBlock.Image && block.inlineListIcon) {
                blocks.getOrNull(index + 1) as? BodyBlock.Text
            } else {
                null
            }
            val child = if (gridRun > 0) {
                val run = (index until index + gridRun).map { blocks[it] as BodyBlock.Image }
                imageGridView(ctx, run, scope)
            } else if (block is BodyBlock.Image && inlineIconText != null) {
                inlineListIconView(ctx, block, inlineIconText, scope)
            } else when (block) {
                is BodyBlock.Text -> textView(ctx, spanned(ctx, block.html), scope)
                is BodyBlock.EditNote -> editNoteView(ctx, block)
                is BodyBlock.Image -> imageView(ctx, block, scope)
                is BodyBlock.Quote -> quoteView(ctx, block, scope)
                is BodyBlock.Spoiler -> spoilerView(ctx, block, scope)
                is BodyBlock.Hidden -> hiddenView(ctx, block, scope)
                is BodyBlock.Code -> codeView(ctx, block, scope)
                is BodyBlock.FileAttachment -> fileAttachmentView(ctx, block, scope)
                is BodyBlock.Table -> tableView(ctx, block, scope)
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
            val tightInlineListToPrev = index >= 2 &&
                    block is BodyBlock.Image && block.inlineListIcon &&
                    (blocks[index - 2] as? BodyBlock.Image)?.inlineListIcon == true
            lp.topMargin = when {
                index == 0 -> 0
                tightToPrev || tightInlineListToPrev ->
                    (TIGHT_BLOCK_GAP_DP * ctx.resources.displayMetrics.density).toInt()
                else -> spacingPx
            }
            child.layoutParams = lp
            container.addView(child)
            index += when {
                gridRun > 0 -> gridRun
                inlineIconText != null -> 2
                else -> 1
            }
        }
    }

    /**
     * [Companion.imageGridRunLength], skipped entirely in a content-measured host (a QMS chat bubble,
     * [textBlockMaxWidthPx] > 0): its width comes FROM its children, so a MATCH_PARENT grid would have
     * no column to divide.
     */
    private fun imageGridRunLength(blocks: List<BodyBlock>, start: Int): Int =
            if (textBlockMaxWidthPx > 0) 0 else Companion.imageGridRunLength(blocks, start)

    /**
     * A run of gallery pictures laid out as an even grid of uniform cells. The column count adapts to the
     * width — about [IMAGE_GRID_TARGET_CELL_DP] per cell, so a phone gets 3, a narrow screen 2 and a
     * landscape/tablet column 4–6 — which keeps every row symmetric on any device without a user setting.
     *
     * Cells are a fixed [IMAGE_GRID_CELL_RATIO] portrait box filled CENTER_CROP: the row lines up
     * regardless of what the sources measure, and the full frame is one tap away in the viewer. A partial
     * last row is padded with empty weighted spacers so its pictures keep the column width instead of
     * stretching across the line. Cell height is derived from the measured width, so the grid is correct in
     * a spoiler/quote card too — narrower than the post column — and it is known BEFORE the bitmaps load,
     * which keeps the scroll anchor from sliding as the images arrive.
     */
    private fun imageGridView(ctx: Context, images: List<BodyBlock.Image>, scope: RenderScope): View {
        val dm = ctx.resources.displayMetrics
        val horizontalChromePx = (40 * dm.density).toInt() // card margins + paddings
        val columnWidthPx = (dm.widthPixels - horizontalChromePx).coerceAtLeast(1)
        val columns = resolveImageGridColumns(columnWidthPx, dm.density)
        val gapPx = (IMAGE_GRID_GAP_DP * dm.density).toInt()
        val cornerPx = IMAGE_GRID_CORNER_DP * dm.density
        val grid = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        images.chunked(columns).forEachIndexed { rowIndex, row ->
            val rowView = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            for (column in 0 until columns) {
                val cell = row.getOrNull(column)
                        ?.let { imageGridCell(ctx, it, cornerPx, scope) }
                        ?: View(ctx)
                rowView.addView(
                        cell,
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            if (column > 0) marginStart = gapPx
                        },
                )
            }
            grid.addView(
                    rowView,
                    LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { if (rowIndex > 0) topMargin = gapPx },
            )
        }
        return grid
    }

    /** One cell of [imageGridView]: a rounded portrait thumbnail wired to the body-wide gallery. */
    private fun imageGridCell(
            ctx: Context,
            block: BodyBlock.Image,
            cornerPx: Float,
            scope: RenderScope,
    ): View {
        val tapUrl = block.linkUrl?.takeIf { it.isNotBlank() } ?: block.imageUrl
        val viewerUrl = FourPdaImageUrls.resolveViewerUrl(tapUrl)
        // Same running gallery as the single-image path: a tap opens the whole body, starting here.
        val index = scope.galleryUrls.size
        scope.galleryUrls.add(viewerUrl)
        val cell = object : ImageView(ctx) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val width = MeasureSpec.getSize(widthMeasureSpec)
                if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED || width <= 0) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                    return
                }
                setMeasuredDimension(width, (width * IMAGE_GRID_CELL_RATIO).toInt())
            }
        }
        return cell.apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = cornerPx
                setColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceVariant))
            }
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            ForPdaCoil.loadInto(this, block.imageUrl)
            setOnClickListener { callbacks.onImageClick(scope.galleryUrls, index) }
            setOnLongClickListener { callbacks.onImageLongClick(viewerUrl); true }
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
        // A collapsed spoiler used to swallow its matches silently: find-on-page counted them, jumped
        // here and showed nothing. Say how many are inside, right in the header.
        val hiddenMatches = if (searchQuery.isBlank()) {
            0
        } else {
            TopicSearchScan.countInBlocks(block.inner, searchQuery) { plainForSearch(it) }
        }
        if (hiddenMatches > 0) {
            header.addView(TextView(ctx).apply {
                text = hiddenMatches.toString()
                textSize = scaledSp(11f)
                // The count sits on a translucent-accent pill: accent-on-accent survives a dark palette but
                // washes out on a light one (pale blue on pale blue), so the digit takes the body colour,
                // which is contrast-guaranteed against every surface the pill can sit on.
                setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
                val ph = (7 * dm.density).toInt()
                setPadding(ph, 0, ph, 0)
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 999f
                    setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(accent, PASSIVE_MATCH_ALPHA))
                }
                layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = (6 * dm.density).toInt() }
            })
        }
        val bodyContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8 * dm.density).toInt(), 0, 0)
        }
        fun applyState() {
            chevron.rotation = if (open) 90f else 0f
            bodyContainer.visibility = if (open) View.VISIBLE else View.GONE
        }
        // Render first, then decide: the inner pass consumes the occurrence ordinals, so the seq range
        // it spans tells us whether the ACTIVE occurrence lives in here — if it does, the spoiler opens
        // itself, otherwise stepping onto that match would scroll to a closed strip.
        val seqBefore = scope.searchSeq
        renderBlocksOnSurface(ctx, bodyContainer, block.inner, scope, blockFillColor(ctx))
        activeMatch?.let { active ->
            if (active.scopeId == scope.scopeId && active.ordinal in seqBefore until scope.searchSeq) {
                open = true
                spoilerStates[key] = true
            }
        }
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
        val rawDate = block.date?.takeIf { it.isNotBlank() }
        val date = rawDate?.let { if (modernPostDates) PostDateFormatter.relative(it) else it }
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
            if (src != null) {
                setOnClickListener {
                    callbacks.onContentLinkTap(scope.scopeId, src)
                    linkHandler.handle(src, null)
                }
                // The quote header is a semantic link even though it is rendered as a styled
                // TextView rather than a URLSpan. Without an explicit long-click it was the only
                // in-topic link that could not open the standard link-actions menu.
                setOnLongClickListener {
                    callbacks.onLinkLongClick(src)
                    true
                }
            }
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
            // Keep expansion on an explicit control. Making the whole quote card clickable gives
            // its parent FrameLayout ownership of long-press gestures on some Android/ROM versions,
            // preventing the descendant TextViews from starting text selection.
            chevron.setOnClickListener(toggle)
        }

        card.addView(column)
        card.addView(bar)
        card.addView(mark)
        return card
    }

    /**
     * Native inline attachment image. Images with server-provided dimensions reserve their final box
     * before loading. Old markup without dimensions starts at WRAP_CONTENT and is sized from the source
     * bitmap after loading, which prevents tiny service icons from being decoded/upscaled to full width.
     * Tapping routes the attachment link through the app (image viewer / download), same as the WebView.
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
        val hasDeclaredSize = block.displayWidthPx > 0 && block.displayHeightPx > 0
        return ImageView(ctx).apply {
            if (block.inline) {
                if (isButtonGif || block.attachmentButton || !hasDeclaredSize) {
                    // Download/update graphics still need their decoded dimensions to distinguish a wide
                    // banner from a tiny file glyph. Ordinary undimensioned images also must not reserve a
                    // guessed full-width box: the real source dimensions are applied after Coil loads them.
                    layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = topInset }
                } else {
                    // Reserve the final image box BEFORE Coil completes. Server dimensions retain the
                    // authored aspect ratio and avoid moving the post when a large content image arrives.
                    val box = resolveStableInlineImageBox(
                            displayWidthPx = block.displayWidthPx,
                            displayHeightPx = block.displayHeightPx,
                            density = dm.density,
                            columnWidthPx = columnWidthPx,
                            maxHeightPx = dm.heightPixels,
                    )
                    layoutParams = LinearLayout.LayoutParams(box.widthPx, box.heightPx)
                            .apply { topMargin = topInset }
                }
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
            // The box is already the browser-like authored/intrinsic size, capped to the content column.
            // FIT_CENTER fills that exact box without cropping. Crucially, an undimensioned image does not
            // receive its box until the decoded source size is known, so this can never inflate it to a
            // guessed full-column rectangle.
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
            } else if (block.inline && !hasDeclaredSize) {
                // Do not let this WRAP_CONTENT ImageView make Coil decode an unbounded original. INEXACT
                // decoding preserves a tiny source's true 32×24 (etc.) dimensions but samples large photos
                // to the post bounds. The box converts the reported pixels to browser-like device pixels.
                ForPdaCoil.loadIntoAtMost(
                        imageView = this,
                        url = block.imageUrl,
                        maxWidthPx = columnWidthPx,
                        maxHeightPx = dm.heightPixels,
                ) { w, h ->
                    if (w > 0 && h > 0) {
                        val box = resolveStableInlineImageBox(
                                displayWidthPx = w,
                                displayHeightPx = h,
                                density = dm.density,
                                columnWidthPx = columnWidthPx,
                                maxHeightPx = dm.heightPixels,
                        )
                        (layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
                            lp.width = box.widthPx
                            lp.height = box.heightPx
                            layoutParams = lp
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

    /**
     * Browser-like row for 4pda digest/list markers: the tiny glyph and its following linked text remain
     * one visual line. These glyphs have no width/height attributes, so routing them through [imageView]
     * would hit the unknown-size full-width fallback and upscale a 20×20 source into a large blurry block.
     */
    private fun inlineListIconView(
            ctx: Context,
            image: BodyBlock.Image,
            text: BodyBlock.Text,
            scope: RenderScope,
    ): View {
        val density = ctx.resources.displayMetrics.density
        val iconSizePx = (INLINE_LIST_ICON_SIZE_DP * density).toInt().coerceAtLeast(1)
        val gapPx = (INLINE_LIST_ICON_GAP_DP * density).toInt()
        val icon = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(iconSizePx, iconSizePx).apply {
                marginEnd = gapPx
                topMargin = (INLINE_LIST_ICON_TOP_DP * density).toInt()
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = false
            contentDescription = null
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            ForPdaCoil.loadInto(this, image.imageUrl)
        }
        val label = textView(ctx, spanned(ctx, text.html), scope).apply {
            layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
            )
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            addView(icon)
            addView(label)
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
    private fun fileAttachmentView(ctx: Context, block: BodyBlock.FileAttachment, scope: RenderScope): View {
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
            setText(highlightSearchMatches(ctx, block.name, scope))
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
        val subtitleView = TextView(ctx).apply {
            textSize = scaledSp(12f)
            setTextColor(muted)
            // Same reason as the name above: «80,2 МБ · Кол-во скачиваний: …» clipped away the count
            // itself — the only number the line carries. The count now rides behind a download glyph
            // (see [attachmentSubtitle]) so it fits, and wrapping stays on as the fallback for a stray
            // long `.desc` (a date, a note) that the page may put there instead.
            setSingleLine(false)
            maxLines = Integer.MAX_VALUE
            ellipsize = null
            breakStrategy = android.text.Layout.BREAK_STRATEGY_SIMPLE
            hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
            setPadding(0, dp(1f), 0, 0)
        }
        val subtitle = attachmentSubtitle(ctx, block, muted, subtitleView.textSize)
        if (subtitle.isNotEmpty()) {
            subtitleView.text = subtitle
            texts.addView(subtitleView)
        }

        row.addView(badge)
        row.addView(texts)
        return row
    }

    /**
     * The muted line under a file's name: «80,2 МБ  ·  ⤓ 421».
     *
     * 4pda writes the count as the prose «Кол-во скачиваний: 421», which ate the whole line and pushed
     * the number itself out of view. The label carries no information the glyph doesn't — so when the
     * `.desc` IS a download count, only the number survives, behind [R.drawable.ic_download] (the same
     * arrow-into-tray as the Загрузки tab, so the meaning is already learned elsewhere in the app). A
     * `.desc` that is anything else (a date, an author note) is left verbatim — we only drop text we
     * can positively identify.
     *
     * The glyph is an [ImageSpan] rather than a Unicode arrow on purpose: the body font is
     * user-switchable, and «⤓»/«⇩» are missing from Roboto Mono and many of the bundled faces — a
     * drawable renders everywhere, tints with the line and scales with [textSizePx].
     */
    private fun attachmentSubtitle(
            ctx: Context,
            block: BodyBlock.FileAttachment,
            muted: Int,
            textSizePx: Float,
    ): CharSequence {
        val out = SpannableStringBuilder()
        block.size?.trim()?.takeIf { it.isNotBlank() }?.let(out::append)
        val desc = block.desc?.trim()?.takeIf { it.isNotBlank() }
        val downloads = desc?.let { DOWNLOAD_COUNT.find(it)?.groupValues?.get(1)?.trim() }
        val glyph = downloads?.let {
            androidx.core.content.ContextCompat.getDrawable(ctx, forpdateam.ru.forpda.R.drawable.ic_download)?.mutate()
        }
        when {
            glyph != null -> {
                if (out.isNotEmpty()) out.append(SUBTITLE_SEPARATOR)
                val side = (textSizePx * DOWNLOAD_GLYPH_SCALE).toInt()
                glyph.setBounds(0, 0, side, side)
                glyph.setTint(muted)
                val start = out.length
                out.append('￼') // OBJECT REPLACEMENT CHARACTER — the span draws over it
                out.setSpan(CenteredImageSpan(glyph), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.append(' ').append(downloads)
            }
            desc != null -> {
                if (out.isNotEmpty()) out.append(SUBTITLE_SEPARATOR)
                out.append(desc)
            }
        }
        return out
    }

    /**
     * [ImageSpan] centred on the line's x-height instead of sitting on the baseline (`ALIGN_CENTER` is
     * API 29+, and the app runs from 26). Without it a 12sp glyph next to 12sp digits reads as if it
     * had slipped a pixel down.
     */
    private class CenteredImageSpan(drawable: android.graphics.drawable.Drawable) :
            android.text.style.ImageSpan(drawable, ALIGN_BOTTOM) {
        override fun draw(
                canvas: android.graphics.Canvas,
                text: CharSequence?,
                start: Int,
                end: Int,
                x: Float,
                top: Int,
                y: Int,
                bottom: Int,
                paint: android.graphics.Paint,
        ) {
            val d = drawable
            val fm = paint.fontMetricsInt
            val dy = y + (fm.descent + fm.ascent) / 2 - d.bounds.height() / 2
            canvas.save()
            canvas.translate(x, dy.toFloat())
            d.draw(canvas)
            canvas.restore()
        }
    }

    /**
     * Native code block: monospace text in a horizontal scroller (long lines don't wrap) on a
     * distinct panel, with a "Копировать" action that puts the raw code on the clipboard.
     */
    private fun codeView(ctx: Context, block: BodyBlock.Code, scope: RenderScope): View {
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
            setText(highlightSearchMatches(ctx, block.text, scope))
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
    private fun tableView(ctx: Context, block: BodyBlock.Table, scope: RenderScope): View {
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
                    setText(highlightSearchMatches(ctx, spanned(ctx, cellHtml), scope))
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
        val content = selectableTextView(ctx, scope).apply {
            val text = neutralizeLowContrastColors(surface, stripLinkColors(spanned(ctx, block.html)))
            setText(highlightSearchMatches(ctx, text, scope))
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
            val linkClicks = object : LinkMovementMethod.ClickListener {
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
            }
            if (scope.selectableText) {
                // Fallback markup is still user-visible post text (and is common inside quotes).
                // It previously installed a selection-aware movement method for links but never
                // actually enabled TextView selection, so long-press produced no handles or menu.
                setTextIsSelectable(true)
                installQuoteSelectionAction(this, scope)
                if (hasLinks) movementMethod = SelectableLinkMovementMethod(linkClicks)
            } else if (hasLinks) {
                movementMethod = LinkMovementMethod(linkClicks)
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
     * The compact server edit marker (`✎ HH:mm`, plus an optional edit reason) — a SYSTEM meta line.
     * Rendered smaller and muted (mirrors the WebView `.edit`: 0.875em, #757575) so it visually separates
     * from the user's own post text.
     */
    private fun editNoteView(ctx: Context, block: BodyBlock.EditNote): View {
        val muted = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
        // The compact marker is metadata rather than an action. Edit reasons may still contain server
        // markup, so remove any links and keep the whole block passive.
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

    /**
     * Личная подпись автора под телом поста («Показывать подписи пользователей»).
     *
     * Разметка подписи произвольная, но всегда инлайновая (`span` с цветом/шрифтом, `b`/`i`/`u`, `a`,
     * `br`, изредка смайл), поэтому это один [textView] — со всей его обработкой серверных цветов
     * (перекраска нечитаемых на тёмной карточке) и внутриприложенных переходов по ссылкам. Отличия от
     * тела: кегль 13sp против 16sp и приглушённый цвет — подпись не должна спорить с текстом поста.
     * Выделение текста выключено: подпись не часть сообщения, её незачем цитировать.
     */
    fun signatureView(ctx: Context, html: String, postId: Int): View {
        val scope = RenderScope(postId, allowQuoteSelection = false, selectableText = false)
        return textView(ctx, spanned(ctx, html), scope).apply {
            textSize = scaledSp(13f)
            setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
            setLineSpacing(0f, 1.15f)
            layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
            )
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

    private fun selectableTextView(ctx: Context, scope: RenderScope): TextView =
            if (scope.selectableText && scope.quoteDepth > 0) {
                TopicSelectableTextView(ctx)
            } else {
                TextView(ctx)
            }

    private fun textView(ctx: Context, text: CharSequence, scope: RenderScope): TextView {
        return selectableTextView(ctx, scope).apply {
            if (textBlockMaxWidthPx > 0) {
                layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                maxWidth = textBlockMaxWidthPx
            }
            val surface = currentSurface(ctx, scope)
            val displayedText = modernizeMergedPostDates(text)
            setText(highlightSearchMatches(
                    ctx,
                    neutralizeLowContrastColors(surface, stripLinkColors(displayedText)),
                    scope,
            ))
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
     * Replace only the timestamp part of system «Добавлено DATE:» markers. Applying replacements to a
     * [SpannableStringBuilder] from right to left keeps the server's size/style spans around the marker.
     */
    private fun modernizeMergedPostDates(text: CharSequence): CharSequence {
        if (!modernPostDates) return text
        val replacements = PostDateFormatter.mergedPostDateReplacements(text.toString())
        if (replacements.isEmpty()) return text
        return SpannableStringBuilder(text).apply {
            for (replacement in replacements.asReversed()) {
                replace(replacement.start, replacement.endExclusive, replacement.value)
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
            // Ranges of this colour span that sit ON a link, clipped to the span and sorted.
            val linkRanges = urls
                .map { maxOf(fs, text.getSpanStart(it)) to minOf(fe, text.getSpanEnd(it)) }
                .filter { it.first < it.second }
                .sortedBy { it.first }
            if (linkRanges.isEmpty()) continue
            // A server colour must not tint a link (links use the app accent). But a colour span often
            // ALSO covers non-link text — e.g. an [offtop] whose grey wraps BOTH the reply-to nick link
            // AND the note text. Removing the WHOLE span dropped the note's grey too, so offtop that
            // followed a reply-nick rendered as ordinary body text while the same offtop in a quote looked
            // right (user report). Clip the colour to the gaps AROUND the links instead of deleting it, so
            // the non-link text keeps its colour and only the link itself falls back to the accent.
            val color = fg.foregroundColor
            val flags = out.getSpanFlags(fg)
            out.removeSpan(fg)
            var cursor = fs
            for ((ls, le) in linkRanges) {
                if (cursor < ls) out.setSpan(android.text.style.ForegroundColorSpan(color), cursor, ls, flags)
                cursor = maxOf(cursor, le)
            }
            if (cursor < fe) out.setSpan(android.text.style.ForegroundColorSpan(color), cursor, fe, flags)
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
     * Keeps the parent list from intercepting an active selection and, when allowed, adds a
     * «Цитировать» item that sends the selected text to the editor.
     */
    private fun installQuoteSelectionAction(tv: TextView, scope: RenderScope) {
        tv.customSelectionActionModeCallback = object : android.view.ActionMode.Callback {
            /** Add the «Цитировать» item (front of the menu, always visible). */
            fun addQuoteItem(menu: android.view.Menu) {
                if (!scope.allowQuoteSelection) return
                // ALWAYS (not IF_ROOM): MIUI/HyperOS floating toolbar drops app items that land in the
                // hidden overflow — forcing the primary row keeps «Цитировать» visible on Xiaomi.
                menu.add(0, QUOTE_MENU_ID, 0, "Цитировать")
                        .setShowAsActionFlags(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
            /** Idempotently ensure the «Цитировать» item is present; returns true if it was (re)added. */
            fun ensureQuoteItem(menu: android.view.Menu): Boolean {
                if (!scope.allowQuoteSelection) return false
                if (menu.findItem(QUOTE_MENU_ID) != null) return false
                addQuoteItem(menu)
                return true
            }
            override fun onCreateActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean {
                // Once Android has recognised the long-press, keep RecyclerView/SwipeRefreshLayout from
                // stealing MOVE events used to place the selection handles. The request propagates
                // through every parent and is released in onDestroyActionMode.
                tv.parent?.requestDisallowInterceptTouchEvent(true)
                ensureQuoteItem(menu)
                return true
            }
            // Some ROMs rebuild the floating toolbar in onPrepare and drop app-provided items, so make
            // sure ours is still present. Never clear/recreate framework menu items here: Android keeps
            // private click handlers on those instances, and replacing them can abort the whole
            // selection ActionMode on OEM implementations (the quote long-press then appears to do
            // nothing).
            override fun onPrepareActionMode(mode: android.view.ActionMode, menu: android.view.Menu) =
                    ensureQuoteItem(menu)
            override fun onActionItemClicked(mode: android.view.ActionMode, menuItem: android.view.MenuItem): Boolean {
                if (scope.allowQuoteSelection && menuItem.itemId == QUOTE_MENU_ID) {
                    val s = tv.selectionStart.coerceAtLeast(0)
                    val e = tv.selectionEnd.coerceAtLeast(0)
                    if (e > s) callbacks.onQuoteSelection(scope.scopeId, tv.text.subSequence(s, e).toString())
                    mode.finish()
                    return true
                }
                return false
            }
            override fun onDestroyActionMode(mode: android.view.ActionMode) {
                tv.parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
    }

    /**
     * Wrap each case-insensitive [searchQuery] match in [text] with a highlight background span, and
     * consume one [RenderScope.searchSeq] ordinal per match so the occurrence the host is standing on
     * ([activeMatch]) can be told apart from the rest: active = solid accent, the others = a pale wash
     * that stays legible without stealing the eye.
     *
     * EVERY call site that renders searchable text must go through here, even when the query cannot
     * match — skipping one would desynchronise the ordinals from [TopicSearchScan]'s count.
     */
    private fun highlightSearchMatches(ctx: Context, text: CharSequence, scope: RenderScope?): CharSequence {
        val q = searchQuery
        if (q.isBlank()) return text
        val out = android.text.SpannableStringBuilder(text)
        val hay = out.toString()
        val accent = ctx.getColorFromAttr(androidx.appcompat.R.attr.colorAccent)
        // Contrast against the ACCENT fill, picked by its luminance — not colorOnPrimary, which is paired
        // with colorPrimary and diverges from colorAccent on the Material You palettes (Android 14+ slot
        // divergence), leaving the active match's text unreadable on its own highlight.
        val onAccent = if (androidx.core.graphics.ColorUtils.calculateLuminance(accent) > 0.5) {
            0xFF000000.toInt()
        } else {
            0xFFFFFFFF.toInt()
        }
        val pale = androidx.core.graphics.ColorUtils.setAlphaComponent(accent, PASSIVE_MATCH_ALPHA)
        val active = activeMatch?.takeIf { scope != null && it.scopeId == scope.scopeId }
        var i = hay.indexOf(q, ignoreCase = true)
        while (i >= 0) {
            val ordinal = scope?.searchSeq ?: -1
            if (scope != null) scope.searchSeq++
            if (active != null && active.ordinal == ordinal) {
                out.setSpan(ActiveSearchMatchSpan(accent), i, i + q.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(android.text.style.ForegroundColorSpan(onAccent),
                        i, i + q.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                out.setSpan(android.text.style.BackgroundColorSpan(pale), i, i + q.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
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
        internal data class ImageBox(val widthPx: Int, val heightPx: Int)

        /** Alpha of the background behind a NON-active find-on-page match (the active one is solid). */
        private const val PASSIVE_MATCH_ALPHA = 0x59 // ~35%

        /**
         * The characters a markup run renders to, for match counting — the same parse the views use
         * ([spanned]) minus the smile pass, which only overlays image spans and never edits text. Goes
         * through the shared [HTML_CACHE], so counting a page costs nothing once its posts are warmed.
         */
        fun plainForSearch(html: String): CharSequence = try {
            HTML_CACHE.get(html) ?: Html
                    .fromHtml(html, Html.FROM_HTML_MODE_COMPACT or Html.FROM_HTML_OPTION_USE_CSS_COLORS, null, null)
                    .trimTrailingNewlines()
                    .let { SpannedString(it) }
                    .also { HTML_CACHE.put(html, it) }
        } catch (t: Throwable) {
            html
        }

        /**
         * Stable layout box for an in-post content image. The returned dimensions never depend on the
         * asynchronous drawable, so binding the bitmap cannot change a RecyclerView item's height.
         */
        internal fun resolveStableInlineImageBox(
                displayWidthPx: Int,
                displayHeightPx: Int,
                density: Float,
                columnWidthPx: Int,
                maxHeightPx: Int,
        ): ImageBox {
            val safeColumnWidth = columnWidthPx.coerceAtLeast(1)
            val safeMaxHeight = maxHeightPx.coerceAtLeast(1)
            if (displayWidthPx <= 0 || displayHeightPx <= 0) {
                return ImageBox(
                        widthPx = safeColumnWidth,
                        heightPx = (safeColumnWidth * DEFAULT_IMAGE_RATIO).toInt()
                                .coerceIn(1, safeMaxHeight),
                )
            }

            val naturalWidth = (displayWidthPx * density).coerceAtLeast(1f)
            val naturalHeight = (displayHeightPx * density).coerceAtLeast(1f)
            val scale = minOf(
                    1f,
                    safeColumnWidth / naturalWidth,
                    safeMaxHeight / naturalHeight,
            )
            return ImageBox(
                    widthPx = (naturalWidth * scale).toInt().coerceAtLeast(1),
                    heightPx = (naturalHeight * scale).toInt().coerceAtLeast(1),
            )
        }

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

        /**
         * The download count inside a post attachment's `.desc`, written by 4pda as «Кол-во скачиваний:
         * 421» (older posts: «скачиваний: 421»). Only the digits are kept — see [attachmentSubtitle].
         * Anchored on the word so a `.desc` carrying something else entirely is never mistaken for a count.
         */
        val DOWNLOAD_COUNT = Regex("""скачивани\w*\s*:?\s*(\d[\d\s ]*)""", RegexOption.IGNORE_CASE)

        /** Separator between the size and the download count on the attachment subtitle line. */
        const val SUBTITLE_SEPARATOR = "  ·  "

        /** Download glyph side, as a multiple of the subtitle text size — optically level with the digits. */
        const val DOWNLOAD_GLYPH_SCALE = 1.15f

        /** A 4pda post-attachment download link: `…/forum/dl/post/<id>/<name>.<ext>`. */
        val ATTACHMENT_URL: Pattern =
                Pattern.compile("""https?://4pda\.(?:to|ru)/forum/dl/post/\d+/(.+\.([^./?#]+))(?:[?#]|$)""")

        const val DEFAULT_IMAGE_RATIO = 0.66f

        /** How many pictures must stand back to back before they are laid out as a grid. */
        const val IMAGE_GRID_MIN_COUNT = 3

        /** Preferred grid cell width (dp). The column count is the width divided by this and rounded,
         *  so the same rule yields 3 columns on a phone, 2 on a narrow screen and 4–6 in landscape. */
        const val IMAGE_GRID_TARGET_CELL_DP = 112f
        const val IMAGE_GRID_MIN_COLUMNS = 2
        const val IMAGE_GRID_MAX_COLUMNS = 6

        /** Cell height as a multiple of its width — a portrait 3:4 preview, the shape wallpapers and
         *  phone screenshots (the usual grid content) lose the least of when cropped to fit. */
        const val IMAGE_GRID_CELL_RATIO = 4f / 3f
        const val IMAGE_GRID_GAP_DP = 4f
        const val IMAGE_GRID_CORNER_DP = 8f

        /**
         * Length of the run of consecutive gallery pictures starting at [start], or 0 when this position
         * does not open a grid. Only real, viewer-openable pictures qualify: decorative list glyphs,
         * «СКАЧАТЬ»/«UPDATE» button graphics and off-site images keep their own row, since they are read as
         * part of the text flow rather than browsed as a series. A run shorter than [IMAGE_GRID_MIN_COUNT]
         * also stays as-is — one or two pictures read better at their authored size than shrunk into cells.
         */
        fun imageGridRunLength(blocks: List<BodyBlock>, start: Int): Int {
            var end = start
            while (end < blocks.size && isGalleryPicture(blocks[end])) end++
            val length = end - start
            return if (length >= IMAGE_GRID_MIN_COUNT) length else 0
        }

        /** True for an image that belongs in the browsable gallery grid (see [imageGridRunLength]). */
        fun isGalleryPicture(block: BodyBlock): Boolean {
            if (block !is BodyBlock.Image) return false
            if (block.inlineListIcon || block.attachmentButton) return false
            val tapUrl = block.linkUrl?.takeIf { it.isNotBlank() } ?: block.imageUrl
            return FourPdaImageUrls.isViewableInViewer(FourPdaImageUrls.resolveViewerUrl(tapUrl))
        }

        /**
         * Column count for a picture grid filling [columnWidthPx]: as many cells of about
         * [IMAGE_GRID_TARGET_CELL_DP] as fit, clamped to [IMAGE_GRID_MIN_COLUMNS]…[IMAGE_GRID_MAX_COLUMNS].
         * One rule covers every screen — 3 on a typical phone, 2 on a narrow one, 4–6 in landscape and on
         * tablets — so no user setting is needed to keep rows symmetric.
         */
        fun resolveImageGridColumns(columnWidthPx: Int, density: Float): Int =
                Math.round(columnWidthPx / (IMAGE_GRID_TARGET_CELL_DP * density))
                        .coerceIn(IMAGE_GRID_MIN_COLUMNS, IMAGE_GRID_MAX_COLUMNS)

        private const val INLINE_LIST_ICON_SIZE_DP = 20f
        private const val INLINE_LIST_ICON_GAP_DP = 6f
        private const val INLINE_LIST_ICON_TOP_DP = 1f
        private const val TIGHT_BLOCK_GAP_DP = 2f

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
