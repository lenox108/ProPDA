package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.text.Spanned
import android.text.style.URLSpan
import androidx.core.view.setPadding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.common.Html
import forpdateam.ru.forpda.common.LinkMovementMethod
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.presentation.ILinkHandler

/**
 * RecyclerView adapter for the native topic renderer (roadmap `native-topic-renderer.md`,
 * Фаза 1). Stable ids = post id (see [NativePostItem.stableId]) so diffing and the anchor
 * controller line up on the same key.
 *
 * Body rendering (Фаза 1): each [BodyBlock.Text] becomes a native [TextView] fed by
 * [Html.fromHtml] — this is the native-text path the phase is about. Each
 * [BodyBlock.WebFallback] currently renders a DEGRADED NATIVE PREVIEW (the block's text via
 * [Html.fromHtml] on a tinted panel with a kind label) rather than the eventual pooled
 * inline WebView. Rationale: a first on-device check should prove segmentation + fast native
 * text without dozens of WebViews (a topic hat alone has ~8 spoilers) skewing the very perf
 * we are validating (§5 Фаза 1 warns Phase-1 perf is unrepresentative). The real pooled
 * WebView fallback is the next deliberate step; [bindFallback] is the single swap point.
 */
class TopicPostsAdapter(
        private val linkHandler: ILinkHandler,
) : ListAdapter<NativePostItem, TopicPostsAdapter.PostViewHolder>(DIFF) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).stableId

    /**
     * Spoiler expand/collapse state, keyed by "postId:spoilerIndex", surviving view recycling so a
     * spoiler the user opened stays open after scrolling away and back. Absent key → the block's own
     * initial (`open`/`close`) state.
     */
    private val spoilerStates = HashMap<String, Boolean>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_native_post, parent, false)
        return PostViewHolder(view, linkHandler, spoilerStates)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /** Per-post render pass state threaded through the recursive block rendering. */
    private class RenderScope(val postId: Int) {
        var spoilerSeq: Int = 0
    }

    class PostViewHolder(
            itemView: View,
            private val linkHandler: ILinkHandler,
            private val spoilerStates: MutableMap<String, Boolean>,
    ) : RecyclerView.ViewHolder(itemView) {
        private val avatar: ImageView = itemView.findViewById(R.id.native_post_avatar)
        private val nick: TextView = itemView.findViewById(R.id.native_post_nick)
        private val meta: TextView = itemView.findViewById(R.id.native_post_meta)
        private val number: TextView = itemView.findViewById(R.id.native_post_number)
        private val body: LinearLayout = itemView.findViewById(R.id.native_post_body)
        private val footer: TextView = itemView.findViewById(R.id.native_post_footer)

        fun bind(item: NativePostItem) {
            bindNick(item)
            bindMeta(item)
            number.text = if (item.number > 0) "#${item.number}" else ""
            ForPdaCoil.loadInto(avatar, item.avatarUrl)
            bindFooter(item)
            bindAuthorActions(item)
            renderBody(item)
        }

        /** Tap avatar/nick → user profile; long-press the post → an actions menu. Navigation-only (no writes). */
        private fun bindAuthorActions(item: NativePostItem) {
            val openProfile = View.OnClickListener {
                if (item.userId > 0) linkHandler.handle(profileUrl(item.userId), null)
            }
            avatar.setOnClickListener(openProfile)
            nick.setOnClickListener(openProfile)
            itemView.setOnLongClickListener {
                showPostMenu(item)
                true
            }
        }

        private fun showPostMenu(item: NativePostItem) {
            val ctx = itemView.context
            val popup = android.widget.PopupMenu(ctx, nick)
            val idProfile = 1
            val idCopyLink = 2
            if (item.userId > 0) popup.menu.add(0, idProfile, 0, "Профиль")
            popup.menu.add(0, idCopyLink, 1, "Копировать ссылку на пост")
            popup.setOnMenuItemClickListener { mi ->
                when (mi.itemId) {
                    idProfile -> linkHandler.handle(profileUrl(item.userId), null)
                    idCopyLink -> {
                        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as? android.content.ClipboardManager
                        cm?.setPrimaryClip(android.content.ClipData.newPlainText("post", postUrl(item)))
                    }
                }
                true
            }
            popup.show()
        }

        private fun profileUrl(userId: Int) = "https://4pda.to/forum/index.php?showuser=$userId"

        private fun postUrl(item: NativePostItem) =
                "https://4pda.to/forum/index.php?showtopic=${item.topicId}&view=findpost&p=${item.postId}"

        /** Nick + curator star (★) + online dot (●, green) — matching the WebView header. */
        private fun bindNick(item: NativePostItem) {
            val ctx = itemView.context
            val sb = SpannableStringBuilder(item.nick.orEmpty())
            if (item.isCurator) {
                val star = " ★"
                val start = sb.length
                sb.append(star)
                sb.setSpan(
                        android.text.style.ForegroundColorSpan(
                                ctx.getColorFromAttr(androidx.appcompat.R.attr.colorPrimary)),
                        start, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            if (item.isOnline) {
                val dot = " ●"
                val start = sb.length
                sb.append(dot)
                sb.setSpan(
                        android.text.style.ForegroundColorSpan(ONLINE_DOT_COLOR),
                        start, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            nick.text = sb
        }

        /** Meta line "group · date · Рег: N", with the group name tinted by its server groupColor. */
        private fun bindMeta(item: NativePostItem) {
            val sb = SpannableStringBuilder()
            val group = item.group?.takeIf { it.isNotBlank() }
            if (group != null) {
                val start = sb.length
                sb.append(group)
                parseColor(item.groupColor)?.let { c ->
                    sb.setSpan(
                            android.text.style.ForegroundColorSpan(c),
                            start, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            }
            item.date?.takeIf { it.isNotBlank() }?.let {
                if (sb.isNotEmpty()) sb.append(" · ")
                sb.append(it)
            }
            item.reputation?.takeIf { it.isNotBlank() }?.let {
                if (sb.isNotEmpty()) sb.append(" · ")
                sb.append("Реп: $it")
            }
            meta.text = sb
        }

        private fun bindFooter(item: NativePostItem) {
            val rating = item.postRating?.takeIf { it.isNotBlank() }
            footer.text = if (rating != null) "Рейтинг: $rating" else ""
            footer.visibility = if (rating != null) View.VISIBLE else View.GONE
        }

        // groupColor is "#RRGGBB" or a CSS name; "black" is the default → leave untinted.
        private fun parseColor(raw: String?): Int? {
            val v = raw?.trim()?.takeIf { it.isNotBlank() && !it.equals("black", ignoreCase = true) } ?: return null
            return try {
                android.graphics.Color.parseColor(v)
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        private fun renderBody(item: NativePostItem) {
            body.removeAllViews()
            renderBlocksInto(body, item.blocks, RenderScope(item.postId))
        }

        /** Renders [blocks] as children of [container]. Reused recursively by quotes/spoilers. */
        private fun renderBlocksInto(container: LinearLayout, blocks: List<BodyBlock>, scope: RenderScope) {
            for (block in blocks) {
                val child = when (block) {
                    is BodyBlock.Text -> textView(spanned(block.html))
                    is BodyBlock.Image -> imageView(block)
                    is BodyBlock.Quote -> quoteView(block, scope)
                    is BodyBlock.Spoiler -> spoilerView(block, scope)
                    is BodyBlock.Code -> codeView(block)
                    is BodyBlock.FileAttachment -> fileAttachmentView(block)
                    is BodyBlock.WebFallback -> bindFallback(block)
                }
                container.addView(child)
            }
        }

        /**
         * Native spoiler: a tappable "▸/▾ title" header toggling a collapsible body of the recursively
         * rendered inner blocks. Open/collapsed state persists across recycling via [spoilerStates].
         */
        private fun spoilerView(block: BodyBlock.Spoiler, scope: RenderScope): View {
            val ctx = itemView.context
            val dm = ctx.resources.displayMetrics
            val key = "${scope.postId}:${scope.spoilerSeq++}"
            var open = spoilerStates[key] ?: block.initiallyOpen

            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((8 * dm.density).toInt())
                setBackgroundColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceVariant))
                layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = (6 * dm.density).toInt() }
            }
            val label = block.title?.takeIf { it.isNotBlank() } ?: "Спойлер"
            val header = TextView(ctx).apply {
                setTypeface(typeface, Typeface.BOLD)
                textSize = 14f
                setTextColor(ctx.getColorFromAttr(androidx.appcompat.R.attr.colorPrimary))
            }
            val bodyContainer = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
            }
            fun applyState() {
                header.text = (if (open) "▾ " else "▸ ") + label
                bodyContainer.visibility = if (open) View.VISIBLE else View.GONE
            }
            renderBlocksInto(bodyContainer, block.inner, scope)
            applyState()
            header.setOnClickListener {
                open = !open
                spoilerStates[key] = open
                applyState()
            }
            card.addView(header)
            card.addView(bodyContainer)
            return card
        }

        /**
         * Native quote: an accent-bordered card with a tappable "author · date" header (jumps to the
         * source post via the app) and the recursively-rendered quoted content — nested quotes included.
         */
        private fun quoteView(block: BodyBlock.Quote, scope: RenderScope): View {
            val ctx = itemView.context
            val dm = ctx.resources.displayMetrics
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((10 * dm.density).toInt())
                setBackgroundColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceVariant))
                layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = (6 * dm.density).toInt() }
            }
            val headerText = listOfNotNull(
                    block.author?.takeIf { it.isNotBlank() },
                    block.date?.takeIf { it.isNotBlank() },
            ).joinToString(" · ").ifBlank { "Цитата" }
            val header = TextView(ctx).apply {
                text = headerText
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ctx.getColorFromAttr(androidx.appcompat.R.attr.colorPrimary))
                val src = block.sourceUrl?.takeIf { it.isNotBlank() }
                if (src != null) setOnClickListener { linkHandler.handle(src, null) }
            }
            card.addView(header)
            renderBlocksInto(card, block.inner, scope)
            return card
        }

        /**
         * Native inline attachment image. Reserves height from the server-provided display
         * dimensions BEFORE the bitmap loads, so a late-arriving image never slides the scroll
         * anchor (§2/§6). Tapping routes the attachment link through the app (image viewer /
         * download), same as the WebView path.
         */
        private fun imageView(block: BodyBlock.Image): View {
            val ctx = itemView.context
            val dm = ctx.resources.displayMetrics
            val horizontalChromePx = (40 * dm.density).toInt() // card margins + paddings
            val targetWidth = (dm.widthPixels - horizontalChromePx).coerceAtLeast(1)
            val ratio = if (block.displayWidthPx > 0 && block.displayHeightPx > 0) {
                block.displayHeightPx.toFloat() / block.displayWidthPx.toFloat()
            } else {
                DEFAULT_IMAGE_RATIO
            }
            val reservedHeight = (targetWidth * ratio).toInt().coerceIn(1, dm.heightPixels)
            return ImageView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        reservedHeight,
                ).apply { topMargin = (6 * dm.density).toInt() }
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                setBackgroundColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceVariant))
                ForPdaCoil.loadInto(this, block.imageUrl)
                val tapUrl = block.linkUrl?.takeIf { it.isNotBlank() } ?: block.imageUrl
                setOnClickListener { linkHandler.handle(tapUrl, null) }
            }
        }

        /** Native file attachment chip: "📎 filename" on a panel, tap → download via the app. */
        private fun fileAttachmentView(block: BodyBlock.FileAttachment): View {
            val ctx = itemView.context
            val dm = ctx.resources.displayMetrics
            val pad = (10 * dm.density).toInt()
            return TextView(ctx).apply {
                text = "📎 ${block.name}"
                textSize = 14f
                setTextColor(ctx.getColorFromAttr(androidx.appcompat.R.attr.colorPrimary))
                setPadding(pad, pad, pad, pad)
                setBackgroundColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceVariant))
                layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = (6 * dm.density).toInt() }
                setOnClickListener { linkHandler.handle(block.url, null) }
            }
        }

        /**
         * Native code block: monospace text in a horizontal scroller (long lines don't wrap) on a
         * distinct panel, with a "Копировать" action that puts the raw code on the clipboard.
         */
        private fun codeView(block: BodyBlock.Code): View {
            val ctx = itemView.context
            val dm = ctx.resources.displayMetrics
            val pad = (8 * dm.density).toInt()
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceVariant))
                layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = (6 * dm.density).toInt() }
            }
            val copyBtn = TextView(ctx).apply {
                text = block.title?.takeIf { it.isNotBlank() }?.let { "$it · Копировать" } ?: "Копировать"
                setTypeface(typeface, Typeface.BOLD)
                textSize = 12f
                setTextColor(ctx.getColorFromAttr(androidx.appcompat.R.attr.colorPrimary))
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
                textSize = 13f
                setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
                setPadding(pad, 0, pad, pad)
                setHorizontallyScrolling(true)
            }
            scroller.addView(code)
            card.addView(copyBtn)
            card.addView(scroller)
            return card
        }

        /** Фаза-1 degraded native preview for a complex block. Single swap point for the future WebView. */
        private fun bindFallback(block: BodyBlock.WebFallback): View {
            val ctx = itemView.context
            val panel = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((8 * resources.displayMetrics.density).toInt())
                setBackgroundColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceVariant))
            }
            val label = TextView(ctx).apply {
                text = "[${block.kind}]"
                setTypeface(typeface, Typeface.BOLD)
                textSize = 11f
                setTextColor(ctx.getColorFromAttr(androidx.appcompat.R.attr.colorPrimary))
            }
            val content = textView(spanned(block.html))
            panel.addView(label)
            panel.addView(content)
            val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = (6 * ctx.resources.displayMetrics.density).toInt() }
            panel.layoutParams = lp
            return panel
        }

        private fun textView(text: CharSequence): TextView {
            val ctx = itemView.context
            return TextView(ctx).apply {
                setText(text)
                textSize = 15f
                setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
                setLineSpacing(0f, 1.1f)
                val hasLinks = text is Spanned &&
                        text.getSpans(0, text.length, URLSpan::class.java).isNotEmpty()
                if (hasLinks) {
                    // Attach the link movement method only when there ARE links — avoids the
                    // ScrollingMovementMethod fighting RecyclerView drags and routes taps in-app.
                    movementMethod = LinkMovementMethod(object : LinkMovementMethod.ClickListener {
                        override fun onClick(url: String): Boolean = linkHandler.handle(url, null)
                    })
                } else {
                    // No links → make the text selectable so the user can copy/share a fragment
                    // (§4 "выделение текста → копировать/поделиться"). Selection and a custom link
                    // movement method can't coexist on one TextView, so link paragraphs stay
                    // tap-to-navigate and plain paragraphs stay select-to-copy.
                    setTextIsSelectable(true)
                }
            }
        }

        private fun spanned(html: String): CharSequence = try {
            val base = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT or Html.FROM_HTML_OPTION_USE_CSS_COLORS, null, null)
                    .trimTrailingNewlines()
            // Replace 4pda smile shortcodes (:thank_you: …) with inline images from bundled assets.
            val ctx = itemView.context
            val smileSize = (ctx.resources.displayMetrics.scaledDensity * SMILE_SIZE_SP).toInt().coerceAtLeast(1)
            SmileProvider.applySmiles(base, ctx.assets, smileSize)
        } catch (t: Throwable) {
            // Graceful degradation (§6): never crash on a single post's markup.
            SpannableStringBuilder(html)
        }

        private fun CharSequence.trimTrailingNewlines(): CharSequence {
            var end = length
            while (end > 0 && (this[end - 1] == '\n' || this[end - 1] == ' ')) end--
            return subSequence(0, end)
        }
    }

    private companion object {
        const val DEFAULT_IMAGE_RATIO = 0.66f
        const val SMILE_SIZE_SP = 18f
        val ONLINE_DOT_COLOR = android.graphics.Color.parseColor("#4CAF50")

        val DIFF = object : DiffUtil.ItemCallback<NativePostItem>() {
            override fun areItemsTheSame(a: NativePostItem, b: NativePostItem) = a.postId == b.postId
            override fun areContentsTheSame(a: NativePostItem, b: NativePostItem) = a == b
        }
    }
}
