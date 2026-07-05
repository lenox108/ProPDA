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
        private val actionListener: PostActionListener,
) : ListAdapter<NativePostItem, TopicPostsAdapter.PostViewHolder>(DIFF) {

    /** Post write-actions, handled by the fragment (which holds the API + coroutine scope + editor). */
    interface PostActionListener {
        fun onVote(item: NativePostItem, up: Boolean)
        fun onReply(item: NativePostItem)
        fun onQuote(item: NativePostItem)
        /** The user selected [selectedText] inside [item]'s body and chose «Цитировать». */
        fun onQuoteSelection(item: NativePostItem, selectedText: String)
        fun onEdit(item: NativePostItem)
        fun onDelete(item: NativePostItem)
        /** The user tapped the «Реп: N» number → open the reputation menu (increase/look/decrease). */
        fun onReputation(item: NativePostItem)
        /** The user tapped the «Шапка темы» collapse header → toggle the hat body. */
        fun onToggleHat()
    }

    /**
     * User display preferences honoured by the renderer (parity with the WebView path's
     * font-size / avatar settings). [textScale] scales all body-content text (default 1.0 =
     * the reference 16-px body); [showAvatars]/[circleAvatars] mirror the topic prefs.
     */
    data class PostDisplaySettings(
            val textScale: Float = 1f,
            val showAvatars: Boolean = true,
            val circleAvatars: Boolean = false,
            val density: forpdateam.ru.forpda.common.Preferences.Main.TopicPostDensity =
                    forpdateam.ru.forpda.common.Preferences.Main.TopicPostDensity.COMFORTABLE,
    )

    private var displaySettings = PostDisplaySettings()

    /** Current find-on-page query; matched substrings get a highlight background when non-blank. */
    private var searchQuery: String = ""

    /** Auth context for resolving post-rating (👍/👎) visibility — parity with the WebView. */
    private var authorized: Boolean = false
    private var memberId: Int = 0

    /** Set the logged-in context so the footer can decide 👍/👎 visibility like the WebView. */
    fun setAuthContext(authorized: Boolean, memberId: Int) {
        if (this.authorized == authorized && this.memberId == memberId) return
        this.authorized = authorized
        this.memberId = memberId
        notifyDataSetChanged()
    }

    /** The post that is the collapsible topic hat (server-marked, `prependedHatPostId`), if any. */
    private var topicHatPostId: Int? = null
    /** Whether the topic-hat post's body is currently collapsed (session state, driven by the host). */
    private var hatCollapsed: Boolean = false

    /** Point the adapter at the hat post and its collapsed state; rebinds it when either changes. */
    fun setTopicHat(postId: Int?, collapsed: Boolean) {
        if (topicHatPostId == postId && hatCollapsed == collapsed) return
        topicHatPostId = postId
        hatCollapsed = collapsed
        val idx = currentList.indexOfFirst { it.postId == postId }
        if (idx >= 0) notifyItemChanged(idx)
    }

    /** Apply new display prefs; rebinds visible posts so text sizes / avatars update in place. */
    fun setDisplaySettings(settings: PostDisplaySettings) {
        if (settings == displaySettings) return
        displaySettings = settings
        notifyDataSetChanged()
    }

    /** Set the find-on-page query (case-insensitive); rebinds so match highlights update. */
    fun setSearchQuery(query: String) {
        if (query == searchQuery) return
        searchQuery = query
        notifyDataSetChanged()
    }

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

    /**
     * The post to flash once when it next binds (target of a link/find/unread open). Consumed on the
     * first matching bind so scrolling away and back — or an infinite-scroll rebind — does NOT re-flash
     * it (cf. the WebView "double-flash"/"stuck-lit" fixes: exactly one highlight per open).
     */
    private var pendingHighlightPostId: Int? = null

    /** Arm a one-shot highlight for [postId]; fires when that post binds (now if already visible). */
    fun requestHighlight(postId: Int) {
        pendingHighlightPostId = postId
        val pos = currentList.indexOfFirst { it.postId == postId }
        if (pos >= 0) notifyItemChanged(pos)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_native_post, parent, false)
        return PostViewHolder(view, linkHandler, spoilerStates, actionListener)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val item = getItem(position)
        val highlight = pendingHighlightPostId == item.postId
        if (highlight) pendingHighlightPostId = null
        val isHat = topicHatPostId != null && item.postId == topicHatPostId
        holder.bind(item, highlight, displaySettings, searchQuery, isHat, hatCollapsed, authorized, memberId)
    }

    /** Per-post render pass state threaded through the recursive block rendering. */
    private class RenderScope(val postId: Int) {
        var spoilerSeq: Int = 0
    }

    class PostViewHolder(
            itemView: View,
            private val linkHandler: ILinkHandler,
            private val spoilerStates: MutableMap<String, Boolean>,
            private val actionListener: PostActionListener,
    ) : RecyclerView.ViewHolder(itemView) {
        private val avatar: ImageView = itemView.findViewById(R.id.native_post_avatar)
        private val nick: TextView = itemView.findViewById(R.id.native_post_nick)
        private val meta: TextView = itemView.findViewById(R.id.native_post_meta)
        private val number: TextView = itemView.findViewById(R.id.native_post_number)
        private val body: LinearLayout = itemView.findViewById(R.id.native_post_body)
        private val footer: TextView = itemView.findViewById(R.id.native_post_footer)
        private val actions: LinearLayout = itemView.findViewById(R.id.native_post_actions)

        /** Running fade for a target-post highlight, cancelled on any rebind so recycling is clean. */
        private var highlightAnimator: android.animation.ValueAnimator? = null

        /** Display prefs for the current bind pass, read by the body-render helpers below. */
        private var settings = PostDisplaySettings()

        /** Find-on-page query for the current bind pass; matched substrings get a highlight span. */
        private var searchQuery: String = ""

        /** Auth context for the current bind pass, used to resolve 👍/👎 visibility (see bindActions). */
        private var authorized: Boolean = false
        private var memberId: Int = 0

        /** Scale a base sp size by the user's font-size preference. */
        private fun scaledSp(base: Float): Float = base * settings.textScale

        fun bind(
                item: NativePostItem,
                highlight: Boolean = false,
                settings: PostDisplaySettings = PostDisplaySettings(),
                searchQuery: String = "",
                isHat: Boolean = false,
                hatCollapsed: Boolean = false,
                authorized: Boolean = false,
                memberId: Int = 0,
        ) {
            this.settings = settings
            this.searchQuery = searchQuery
            this.authorized = authorized
            this.memberId = memberId
            // Reset the card background on every (re)bind so a recycled holder never keeps a prior
            // post's mid-fade tint.
            highlightAnimator?.cancel()
            highlightAnimator = null
            itemView.setBackgroundColor(cardBaseColor())
            applyDensity()
            bindNick(item)
            bindMeta(item)
            number.text = if (item.number > 0) "#${item.number}" else ""
            bindAvatar(item)
            bindFooter(item)
            bindAuthorActions(item)
            renderBody(item, isHat, hatCollapsed)
            if (highlight) playHighlight()
        }

        /** Honour the show-avatars / circle-avatars prefs (parity with the WebView topic settings). */
        private fun bindAvatar(item: NativePostItem) {
            if (!settings.showAvatars) {
                avatar.visibility = View.GONE
                return
            }
            avatar.visibility = View.VISIBLE
            if (settings.circleAvatars) {
                avatar.clipToOutline = true
                avatar.outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setOval(0, 0, view.width, view.height)
                    }
                }
            } else {
                avatar.clipToOutline = false
                avatar.outlineProvider = null
            }
            ForPdaCoil.loadInto(avatar, item.avatarUrl)
        }

        private fun cardBaseColor(): Int = com.google.android.material.color.MaterialColors.getColor(
                itemView, com.google.android.material.R.attr.colorSurfaceContainer)

        /**
         * Post density (Комфортная/Компактная/Сверхкомпактная) — tightens the card's inner vertical
         * padding and the gap between cards, mirroring the WebView density setting. Horizontal
         * padding stays constant so text width doesn't jump.
         */
        private fun applyDensity() {
            val dm = itemView.resources.displayMetrics
            val (vPadDp, gapDp) = when (settings.density) {
                forpdateam.ru.forpda.common.Preferences.Main.TopicPostDensity.SUPER_COMPACT -> 3f to 1f
                forpdateam.ru.forpda.common.Preferences.Main.TopicPostDensity.COMPACT -> 6f to 2f
                forpdateam.ru.forpda.common.Preferences.Main.TopicPostDensity.COMFORTABLE -> 10f to 4f
            }
            val hPad = (12 * dm.density).toInt()
            val vPad = (vPadDp * dm.density).toInt()
            itemView.setPadding(hPad, vPad, hPad, vPad)
            (itemView.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                val gap = (gapDp * dm.density).toInt()
                lp.topMargin = gap
                lp.bottomMargin = gap
                itemView.layoutParams = lp
            }
        }

        /** Flash the card with an accent-tinted background that fades back to the surface colour. */
        private fun playHighlight() {
            val base = cardBaseColor()
            val accent = com.google.android.material.color.MaterialColors.getColor(
                    itemView, androidx.appcompat.R.attr.colorPrimary)
            val start = com.google.android.material.color.MaterialColors.layer(base, accent, 0.22f)
            highlightAnimator = android.animation.ValueAnimator
                    .ofObject(android.animation.ArgbEvaluator(), start, base).apply {
                        duration = 1600L
                        startDelay = 250L
                        addUpdateListener { itemView.setBackgroundColor(it.animatedValue as Int) }
                        start()
                    }
        }

        /** Tap avatar/nick → user profile; long-press the post → an actions menu. Navigation-only (no writes). */
        private fun bindAuthorActions(item: NativePostItem) {
            val openProfile = View.OnClickListener {
                if (item.userId > 0) linkHandler.handle(profileUrl(item.userId), null)
            }
            avatar.setOnClickListener(openProfile)
            nick.setOnClickListener(openProfile)
            // Post menu on long-press of the HEADER (avatar/nick) — the body is reserved for text
            // selection (long-press there starts selection + «Цитировать»), so no gesture conflict.
            val openMenu = View.OnLongClickListener {
                showPostMenu(item)
                true
            }
            avatar.setOnLongClickListener(openMenu)
            nick.setOnLongClickListener(openMenu)
        }

        private fun showPostMenu(item: NativePostItem) {
            val ctx = itemView.context
            val popup = android.widget.PopupMenu(ctx, nick)
            val idProfile = 1; val idCopyLink = 2; val idEdit = 3; val idDelete = 4
            var order = 0
            if (item.userId > 0) popup.menu.add(0, idProfile, order++, "Профиль")
            popup.menu.add(0, idCopyLink, order++, "Копировать ссылку на пост")
            if (item.canEdit) popup.menu.add(0, idEdit, order++, "Редактировать")
            if (item.canDelete) popup.menu.add(0, idDelete, order++, "Удалить")
            popup.setOnMenuItemClickListener { mi ->
                when (mi.itemId) {
                    idProfile -> linkHandler.handle(profileUrl(item.userId), null)
                    idCopyLink -> {
                        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as? android.content.ClipboardManager
                        cm?.setPrimaryClip(android.content.ClipData.newPlainText("post", postUrl(item)))
                    }
                    idEdit -> actionListener.onEdit(item)
                    idDelete -> actionListener.onDelete(item)
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
                val start = sb.length
                sb.append("Реп: $it")
                // Tap the reputation number → reputation menu (increase / look / decrease), as WebView.
                sb.setSpan(object : android.text.style.ClickableSpan() {
                    override fun onClick(widget: View) = actionListener.onReputation(item)
                    override fun updateDrawState(ds: android.text.TextPaint) {
                        ds.color = meta.context.getColorFromAttr(androidx.appcompat.R.attr.colorPrimary)
                        ds.isUnderlineText = false
                    }
                }, start, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                meta.movementMethod = android.text.method.LinkMovementMethod.getInstance()
            }
            meta.text = sb
        }

        private fun bindFooter(item: NativePostItem) {
            // The post rating now lives inline in the action row (between 👍/👎), matching WebView,
            // so the separate footer text is unused.
            footer.visibility = View.GONE
            bindActions(item)
        }

        /**
         * Post action row matching the WebView footer: 👍-icon · rating · 👎-icon · ↩-icon «Ответить» ·
         * ❝-icon «Цитировать». Icons are tinted with the accent colour; taps route to the fragment.
         */
        private fun bindActions(item: NativePostItem) {
            actions.removeAllViews()
            // Resolve 👍/👎 visibility with the same fallback logic as the WebView (mobile HTML omits
            // the rating metadata, so a quotable non-own post still gets the controls).
            val (canPlus, canMinus) = NativePostRatingActions.resolve(
                    canQuote = item.canQuote,
                    postRating = item.postRating,
                    parsedCanPlus = item.canPlusPostRating,
                    parsedCanMinus = item.canMinusPostRating,
                    postUserId = item.userId,
                    authorized = authorized,
                    memberId = memberId,
            )
            // Left group: post-rating vote 👍 · rating · 👎 (the WebView's rep-thumb-final outline
            // thumbs, background stripped, tinted to the muted action-icon colour).
            if (canPlus) {
                actions.addView(iconAction(R.drawable.ic_post_thumb_up, null) { actionListener.onVote(item, up = true) })
            }
            item.postRating?.takeIf { it.isNotBlank() }?.let { actions.addView(ratingLabel(it)) }
            if (canMinus) {
                actions.addView(iconAction(R.drawable.ic_post_thumb_down, null) { actionListener.onVote(item, up = false) })
            }
            // Spacer pushes reply/quote to the right edge, matching the WebView footer layout.
            if (item.canQuote) {
                actions.addView(View(itemView.context), LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.MATCH_PARENT, 1f))
                // Right group: reply (speech bubble) · quote (⇄) — ICONS ONLY, exact WebView icons.
                actions.addView(iconAction(R.drawable.ic_post_reply, null) { actionListener.onReply(item) })
                actions.addView(iconAction(R.drawable.ic_post_quote, null) { actionListener.onQuote(item) })
            }
            actions.visibility = if (actions.childCount > 0) View.VISIBLE else View.GONE
        }

        /**
         * A footer action icon tinted muted grey (`colorOnSurfaceVariant`) to match the WebView's
         * `post-action-icon-color`; a text label (if any) uses the accent colour.
         */
        private fun iconAction(iconRes: Int, label: String?, onClick: () -> Unit): TextView {
            val ctx = itemView.context
            val dm = ctx.resources.displayMetrics
            val accent = ctx.getColorFromAttr(androidx.appcompat.R.attr.colorPrimary)
            val iconTint = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
            return TextView(ctx).apply {
                text = label.orEmpty()
                if (!label.isNullOrEmpty()) {
                    textSize = scaledSp(13f)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(accent)
                }
                val icon = androidx.core.content.ContextCompat.getDrawable(ctx, iconRes)?.mutate()?.apply {
                    setTint(iconTint)
                    val s = (18 * dm.density).toInt()
                    setBounds(0, 0, s, s)
                }
                setCompoundDrawables(icon, null, null, null)
                compoundDrawablePadding = (4 * dm.density).toInt()
                val padH = (8 * dm.density).toInt()
                val padV = (5 * dm.density).toInt()
                setPadding(padH, padV, padH, padV)
                gravity = android.view.Gravity.CENTER_VERTICAL
                setOnClickListener { onClick() }
            }
        }

        /** The post-rating number shown between the 👍/👎 vote icons. */
        private fun ratingLabel(rating: String): TextView {
            val ctx = itemView.context
            return TextView(ctx).apply {
                text = rating
                textSize = scaledSp(13f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
                val padH = (4 * ctx.resources.displayMetrics.density).toInt()
                setPadding(padH, 0, padH, 0)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
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

        private fun renderBody(item: NativePostItem, isHat: Boolean = false, hatCollapsed: Boolean = false) {
            body.removeAllViews()
            if (isHat) {
                // The topic hat gets a collapse header (parity with the WebView «Показать/Скрыть шапку»).
                body.addView(hatHeader(hatCollapsed))
                if (hatCollapsed) return // collapsed: header only, hide the (often huge) hat content
            }
            renderBlocksInto(body, item.blocks, RenderScope(item.postId), item)
        }

        /** A tappable «▾/▸ Шапка темы» row that toggles the hat body via [actionListener]. */
        private fun hatHeader(collapsed: Boolean): View {
            val ctx = itemView.context
            val dm = ctx.resources.displayMetrics
            return TextView(ctx).apply {
                text = if (collapsed) "▸ Шапка темы" else "▾ Шапка темы"
                textSize = scaledSp(13f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ctx.getColorFromAttr(androidx.appcompat.R.attr.colorPrimary))
                val vPad = (6 * dm.density).toInt()
                setPadding(0, vPad, 0, vPad)
                setOnClickListener { actionListener.onToggleHat() }
            }
        }

        /** Renders [blocks] as children of [container]. Reused recursively by quotes/spoilers. */
        private fun renderBlocksInto(container: LinearLayout, blocks: List<BodyBlock>, scope: RenderScope, item: NativePostItem) {
            for (block in blocks) {
                val child = when (block) {
                    is BodyBlock.Text -> textView(spanned(block.html), item)
                    is BodyBlock.Image -> imageView(block)
                    is BodyBlock.Quote -> quoteView(block, scope, item)
                    is BodyBlock.Spoiler -> spoilerView(block, scope, item)
                    is BodyBlock.Code -> codeView(block)
                    is BodyBlock.FileAttachment -> fileAttachmentView(block)
                    is BodyBlock.Table -> tableView(block)
                    is BodyBlock.WebFallback -> bindFallback(block)
                }
                container.addView(child)
            }
        }

        /**
         * Native spoiler: a tappable "▸/▾ title" header toggling a collapsible body of the recursively
         * rendered inner blocks. Open/collapsed state persists across recycling via [spoilerStates].
         */
        private fun spoilerView(block: BodyBlock.Spoiler, scope: RenderScope, item: NativePostItem): View {
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
                textSize = scaledSp(14f)
                setTextColor(ctx.getColorFromAttr(androidx.appcompat.R.attr.colorPrimary))
            }
            val bodyContainer = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
            }
            fun applyState() {
                header.text = (if (open) "▾ " else "▸ ") + label
                bodyContainer.visibility = if (open) View.VISIBLE else View.GONE
            }
            renderBlocksInto(bodyContainer, block.inner, scope, item)
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
        private fun quoteView(block: BodyBlock.Quote, scope: RenderScope, item: NativePostItem): View {
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
                textSize = scaledSp(13f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ctx.getColorFromAttr(androidx.appcompat.R.attr.colorPrimary))
                val src = block.sourceUrl?.takeIf { it.isNotBlank() }
                if (src != null) setOnClickListener { linkHandler.handle(src, null) }
            }
            card.addView(header)
            renderBlocksInto(card, block.inner, scope, item)
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
                textSize = scaledSp(14f)
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
                textSize = scaledSp(12f)
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
        private fun tableView(block: BodyBlock.Table): View {
            val ctx = itemView.context
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
                        setText(highlightSearchMatches(spanned(cellHtml)))
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
                ).apply { topMargin = (6 * dm.density).toInt() }
            }
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
                textSize = scaledSp(11f)
                setTextColor(ctx.getColorFromAttr(androidx.appcompat.R.attr.colorPrimary))
            }
            val content = TextView(ctx).apply {
                setText(spanned(block.html))
                textSize = scaledSp(15f)
                setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
                setLineSpacing(0f, 1.1f)
            }
            panel.addView(label)
            panel.addView(content)
            val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = (6 * ctx.resources.displayMetrics.density).toInt() }
            panel.layoutParams = lp
            return panel
        }

        private fun textView(text: CharSequence, item: NativePostItem): TextView {
            val ctx = itemView.context
            return TextView(ctx).apply {
                setText(highlightSearchMatches(text))
                textSize = scaledSp(15f)
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
                    // No links → selectable text: native Copy/Share plus a custom «Цитировать» that
                    // wraps the selection in a [quote …] for the reply editor (§4 selection→quote).
                    setTextIsSelectable(true)
                    installQuoteSelectionAction(this, item)
                }
            }
        }

        /** Adds a «Цитировать» item to the text-selection action bar → quotes the selection into the editor. */
        private fun installQuoteSelectionAction(tv: TextView, item: NativePostItem) {
            if (!item.canQuote) return
            tv.customSelectionActionModeCallback = object : android.view.ActionMode.Callback {
                override fun onCreateActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean {
                    menu.add(0, QUOTE_MENU_ID, 0, "Цитировать")
                    return true
                }
                override fun onPrepareActionMode(mode: android.view.ActionMode, menu: android.view.Menu) = false
                override fun onActionItemClicked(mode: android.view.ActionMode, menuItem: android.view.MenuItem): Boolean {
                    if (menuItem.itemId == QUOTE_MENU_ID) {
                        val s = tv.selectionStart.coerceAtLeast(0)
                        val e = tv.selectionEnd.coerceAtLeast(0)
                        if (e > s) actionListener.onQuoteSelection(item, tv.text.subSequence(s, e).toString())
                        mode.finish()
                        return true
                    }
                    return false
                }
                override fun onDestroyActionMode(mode: android.view.ActionMode) {}
            }
        }

        /** Wrap each case-insensitive [searchQuery] match in [text] with a highlight background span. */
        private fun highlightSearchMatches(text: CharSequence): CharSequence {
            val q = searchQuery
            if (q.isBlank()) return text
            val out = android.text.SpannableStringBuilder(text)
            val hay = out.toString()
            val color = com.google.android.material.color.MaterialColors.getColor(
                    itemView, androidx.appcompat.R.attr.colorPrimary)
            var i = hay.indexOf(q, ignoreCase = true)
            while (i >= 0) {
                out.setSpan(android.text.style.BackgroundColorSpan(color), i, i + q.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(android.text.style.ForegroundColorSpan(
                        com.google.android.material.color.MaterialColors.getColor(
                                itemView, com.google.android.material.R.attr.colorOnPrimary)),
                        i, i + q.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                i = hay.indexOf(q, i + q.length, ignoreCase = true)
            }
            return out
        }

        private fun spanned(html: String): CharSequence = try {
            val base = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT or Html.FROM_HTML_OPTION_USE_CSS_COLORS, null, null)
                    .trimTrailingNewlines()
            // Replace 4pda smile shortcodes (:thank_you: …) with inline images from bundled assets.
            val ctx = itemView.context
            val smileSize = (ctx.resources.displayMetrics.scaledDensity * scaledSp(SMILE_SIZE_SP)).toInt().coerceAtLeast(1)
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
        const val QUOTE_MENU_ID = 0x71_0716
        val ONLINE_DOT_COLOR = android.graphics.Color.parseColor("#4CAF50")

        val DIFF = object : DiffUtil.ItemCallback<NativePostItem>() {
            override fun areItemsTheSame(a: NativePostItem, b: NativePostItem) = a.postId == b.postId
            override fun areContentsTheSame(a: NativePostItem, b: NativePostItem) = a == b
        }
    }
}
