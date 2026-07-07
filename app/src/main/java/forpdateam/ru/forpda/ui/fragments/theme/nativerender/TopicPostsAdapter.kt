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
import forpdateam.ru.forpda.common.FourPdaImageUrls
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
        /** The user tapped the reputation avatar badge → open the reputation menu. */
        fun onReputation(item: NativePostItem)
        /** The user tapped the author avatar → open the user menu (profile/rep/QMS/messages…). */
        fun onAvatarClick(item: NativePostItem)
        /** The user tapped the «⋮» post menu → reply/quote/copy-link/share/report/edit/delete/note. */
        fun onPostMenu(item: NativePostItem)
        /** The user tapped the «Шапка темы» collapse header → toggle the hat body. */
        fun onToggleHat()
        /** Long-press on a spoiler header → copy a deep link to that spoiler ([spoilNumber] is 1-based). */
        fun onSpoilerCopyLink(item: NativePostItem, spoilNumber: Int)
        /** Tap on an attachment image → open the swipeable image viewer over [galleryUrls] at [index]
         *  (parity with the WebView, which groups all of a post's images into one gallery). */
        fun onImageClick(galleryUrls: List<String>, index: Int)
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
     * The post to flash on open (target of a link/find/unread open), plus the wall-clock DEADLINE until
     * which the flash stays active. Tracking a deadline (not a one-shot boolean consumed on first bind)
     * makes the highlight survive the re-binds that happen right after open — the deferred
     * enrichment merge, the settle-at-top re-scroll, DiffUtil updates — which previously cancelled the
     * fade within milliseconds (a bind during the animator's start-delay read isRunning=false). Any bind
     * of this post before the deadline continues/re-attaches the flash for the REMAINING time; after the
     * deadline it never re-flashes (one flash per open — cf. the WebView "double-flash"/"stuck-lit" fixes).
     */
    private var highlightTargetPostId: Int = 0
    private var highlightDeadlineUptime: Long = 0L

    /** Arm the open-highlight for [postId]; fires when that post binds (now if already visible). */
    fun requestHighlight(postId: Int) {
        highlightTargetPostId = postId
        highlightDeadlineUptime = android.os.SystemClock.uptimeMillis() + HIGHLIGHT_TOTAL_MS
        val pos = currentList.indexOfFirst { it.postId == postId }
        if (pos >= 0) notifyItemChanged(pos)
    }

    /** Remaining flash time (ms) for [postId], or 0 when it is not the (still-active) highlight target. */
    private fun highlightRemainingMsFor(postId: Int): Long {
        if (postId <= 0 || postId != highlightTargetPostId) return 0L
        return (highlightDeadlineUptime - android.os.SystemClock.uptimeMillis()).coerceAtLeast(0L)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_native_post, parent, false)
        return PostViewHolder(view, linkHandler, spoilerStates, actionListener)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val item = getItem(position)
        val highlightRemainingMs = highlightRemainingMsFor(item.postId)
        val isHat = topicHatPostId != null && item.postId == topicHatPostId
        // The «Страница N» divider label is baked into the item at list assembly (see the fragment), so
        // DiffUtil rebinds the boundary post when a prepended page shifts it. Never on the hat.
        val pageDivider = item.pageDividerLabel?.takeIf { !isHat }
        holder.bind(item, highlightRemainingMs, displaySettings, searchQuery, isHat, hatCollapsed, authorized, memberId, pageDivider)
    }

    /** Per-post render pass state threaded through the recursive block rendering. */
    private class RenderScope(val postId: Int) {
        var spoilerSeq: Int = 0
        /** Viewer-resolved URLs of this post's attachment images, in document order (incl. nested in
         *  quotes/spoilers). Built as images are rendered; each image view captures its own index so a
         *  tap opens the whole post as one swipeable gallery (WebView parity). */
        val galleryUrls = ArrayList<String>()
    }

    class PostViewHolder(
            itemView: View,
            private val linkHandler: ILinkHandler,
            private val spoilerStates: MutableMap<String, Boolean>,
            private val actionListener: PostActionListener,
    ) : RecyclerView.ViewHolder(itemView) {
        private val header: View = itemView.findViewById(R.id.native_post_header)
        private val avatar: ImageView = itemView.findViewById(R.id.native_post_avatar)
        private val repBadge: TextView = itemView.findViewById(R.id.native_post_rep_badge)
        private val nick: TextView = itemView.findViewById(R.id.native_post_nick)
        private val meta: TextView = itemView.findViewById(R.id.native_post_meta)
        private val date: TextView = itemView.findViewById(R.id.native_post_date)
        private val postCount: TextView = itemView.findViewById(R.id.native_post_count)
        private val number: TextView = itemView.findViewById(R.id.native_post_number)
        private val menu: ImageView = itemView.findViewById(R.id.native_post_menu)
        private val body: LinearLayout = itemView.findViewById(R.id.native_post_body)
        private val footer: TextView = itemView.findViewById(R.id.native_post_footer)
        private val actions: LinearLayout = itemView.findViewById(R.id.native_post_actions)
        private val hatToggle: LinearLayout = itemView.findViewById(R.id.native_post_hat_toggle)
        private val pageDivider: TextView = itemView.findViewById(R.id.native_post_page_divider)
        /** The surface card inside the transparent wrapper — bg/highlight/density apply HERE, so the
         *  «Страница N» divider (a sibling above it) stays on the neutral list background. */
        private val card: View = itemView.findViewById(R.id.native_post_card)

        /** Rounded Material 3 card background — colour is set per-bind (and animated on highlight) via
         *  [android.graphics.drawable.GradientDrawable.setColor], which keeps the rounded corners. */
        private val cardBg = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 16f * itemView.resources.displayMetrics.density
        }

        init {
            card.background = cardBg
            card.clipToOutline = true
            androidx.core.view.ViewCompat.setElevation(card, 2f * itemView.resources.displayMetrics.density)
        }

        /** Running fade for a target-post highlight, cancelled on any rebind so recycling is clean. */
        private var highlightAnimator: android.animation.ValueAnimator? = null
        /** Post id the running highlight belongs to, so a re-bind of the SAME post (e.g. the async
         *  userPostCount/rating enrichment) doesn't cancel a mid-fade highlight. */
        private var highlightingPostId: Int = 0

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
                highlightRemainingMs: Long = 0L,
                settings: PostDisplaySettings = PostDisplaySettings(),
                searchQuery: String = "",
                isHat: Boolean = false,
                hatCollapsed: Boolean = false,
                authorized: Boolean = false,
                memberId: Int = 0,
                pageDividerLabel: String? = null,
        ) {
            pageDivider.text = pageDividerLabel.orEmpty()
            pageDivider.visibility = if (pageDividerLabel != null) View.VISIBLE else View.GONE
            this.settings = settings
            this.searchQuery = searchQuery
            this.authorized = authorized
            this.memberId = memberId
            // Keep a running highlight alive across a re-bind of the SAME post that is still within its
            // flash window (enrichment/diff updates rebind the post right after open and previously
            // cancelled the fade). Check the animator by presence — NOT isRunning — because during the
            // brief start-delay isRunning is false and a re-bind would wrongly cancel it. Reset the border
            // only when this holder is reused for a DIFFERENT post, or the flash window has elapsed.
            val wantHighlight = highlightRemainingMs > 0L
            val keepHighlight = highlightAnimator != null && highlightingPostId == item.postId && wantHighlight
            if (!keepHighlight) {
                highlightAnimator?.cancel()
                highlightAnimator = null
                applyRestingCardBorder() // hairline M3 outline so cards stay separated (esp. dark/AMOLED)
            }
            cardBg.setColor(cardBaseColor())
            applyDensity()
            bindNick(item)
            bindMeta(item)
            // Post index («#N») intentionally hidden — the user asked to drop it from the header.
            number.visibility = View.GONE
            bindAvatar(item)
            bindRepBadge(item)
            bindPostCount(item)
            bindPostMenu(item)
            bindFooter(item)
            bindAuthorActions(item)
            renderBody(item, isHat, hatCollapsed)
            bindHatToggle(isHat, hatCollapsed)
            // Topic hat (WebView parity): the «ШАПКА ТЕМЫ» toggle bar is rendered ABOVE the author header
            // as a standalone block, and the whole post below it (author header + body + footer + actions)
            // is the collapsible hat_content. Collapsed → only the toggle bar shows; expanded → toggle bar
            // followed by the full post.
            val hatFolded = isHat && hatCollapsed
            header.visibility = if (hatFolded) View.GONE else View.VISIBLE
            footer.visibility = if (hatFolded) View.GONE else footer.visibility
            actions.visibility = if (hatFolded) View.GONE else actions.visibility
            body.visibility = if (hatFolded) View.GONE else View.VISIBLE
            (body.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                val top = if (hatFolded) 0 else (8 * itemView.resources.displayMetrics.density).toInt()
                if (lp.topMargin != top) { lp.topMargin = top; body.layoutParams = lp }
            }
            if (wantHighlight && !keepHighlight) {
                highlightingPostId = item.postId
                playHighlight(highlightRemainingMs)
            }
        }

        /** Populate/toggle the standalone «ШАПКА ТЕМЫ ▾/▴» bar shown above the hat post (GONE otherwise). */
        private fun bindHatToggle(isHat: Boolean, collapsed: Boolean) {
            hatToggle.removeAllViews()
            if (!isHat) {
                hatToggle.visibility = View.GONE
                return
            }
            hatToggle.visibility = View.VISIBLE
            fillHatToggle(hatToggle, collapsed)
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
            // A letter-avatar fallback so users with an empty or broken avatar never render blank.
            ForPdaCoil.loadAvatar(avatar, item.avatarUrl, letterAvatar(avatar.context, item.nick))
            avatar.isClickable = true
            avatar.setOnClickListener { if (item.userId > 0) actionListener.onAvatarClick(item) }
        }

        /** Colored circle + first letter of the nick — WebView-style fallback avatar. */
        private fun letterAvatar(ctx: android.content.Context, nick: String?): android.graphics.drawable.Drawable {
            val letter = nick?.trim()?.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
            val palette = intArrayOf(
                    0xFF5C6BC0.toInt(), 0xFF26A69A.toInt(), 0xFFEF5350.toInt(), 0xFF66BB6A.toInt(),
                    0xFFAB47BC.toInt(), 0xFFFFA726.toInt(), 0xFF42A5F5.toInt(), 0xFF8D6E63.toInt())
            val bg = palette[((nick?.hashCode() ?: 0) and 0x7FFFFFFF) % palette.size]
            return object : android.graphics.drawable.Drawable() {
                private val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                        .apply { color = bg }
                private val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                override fun draw(canvas: android.graphics.Canvas) {
                    val b = bounds
                    val cx = b.exactCenterX(); val cy = b.exactCenterY()
                    val r = minOf(b.width(), b.height()) / 2f
                    canvas.drawCircle(cx, cy, r, bgPaint)
                    textPaint.textSize = r
                    val fm = textPaint.fontMetrics
                    canvas.drawText(letter, cx, cy - (fm.ascent + fm.descent) / 2f, textPaint)
                }
                override fun setAlpha(alpha: Int) {}
                override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
                @Deprecated("deprecated in Drawable")
                override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
            }
        }

        private fun cardBaseColor(): Int = com.google.android.material.color.MaterialColors.getColor(
                itemView, com.google.android.material.R.attr.colorSurfaceContainer)

        /**
         * Resting hairline border for the post card. Elevation shadows are invisible on dark/AMOLED
         * surfaces, so without an outline every near-black card melts into the near-black page (user
         * report). A 1dp [colorOutline] edge (M3 outlined-card tone) keeps cards delineated in every
         * theme — subtle on light palettes, essential on dark.
         */
        private fun restingCardBorderColor(): Int = com.google.android.material.color.MaterialColors.getColor(
                itemView, com.google.android.material.R.attr.colorOutline)

        private fun cardBorderWidthPx(): Int =
                (1f * itemView.resources.displayMetrics.density).toInt().coerceAtLeast(1)

        private fun applyRestingCardBorder() =
                cardBg.setStroke(cardBorderWidthPx(), restingCardBorderColor())

        /**
         * Rounded Material 3 container for an inline block (quote / spoiler / …): a tonal fill plus a
         * 1dp [colorOutlineVariant] hairline and rounded corners, so nested blocks read as distinct M3
         * surfaces on every palette instead of flat rectangles.
         */
        private fun m3BlockBackground(
                fillAttr: Int,
                cornerDp: Float = 12f,
        ): android.graphics.drawable.GradientDrawable {
            val dm = itemView.resources.displayMetrics
            return android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = cornerDp * dm.density
                setColor(itemView.context.getColorFromAttr(fillAttr))
                setStroke(
                        (1f * dm.density).toInt().coerceAtLeast(1),
                        itemView.context.getColorFromAttr(com.google.android.material.R.attr.colorOutlineVariant),
                )
            }
        }

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
            card.setPadding(hPad, vPad, hPad, vPad)
            (card.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                val gap = (gapDp * dm.density).toInt()
                lp.topMargin = gap
                lp.bottomMargin = gap
                card.layoutParams = lp
            }
        }

        /**
         * Flash the card BORDER: a bold accent stroke that holds, then fades over [durationMs] into the
         * resting hairline outline (not transparent — the card keeps its permanent M3 border, so it never
         * loses separation on dark themes). No start-delay (a delay left isRunning=false during which a
         * re-bind cancelled the flash). An AccelerateInterpolator keeps the accent near full for the first
         * part of the window so the flash is clearly visible, then eases out.
         */
        private fun playHighlight(durationMs: Long) {
            val accent = com.google.android.material.color.MaterialColors.getColor(
                    itemView, androidx.appcompat.R.attr.colorAccent)
            val restingColor = restingCardBorderColor()
            val strokeWidth = (3f * itemView.resources.displayMetrics.density).toInt().coerceAtLeast(2)
            highlightAnimator = android.animation.ValueAnimator
                    .ofObject(android.animation.ArgbEvaluator(), accent, restingColor).apply {
                        duration = durationMs.coerceIn(300L, HIGHLIGHT_TOTAL_MS)
                        interpolator = android.view.animation.AccelerateInterpolator(1.6f)
                        addUpdateListener { cardBg.setStroke(strokeWidth, it.animatedValue as Int) }
                        addListener(object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: android.animation.Animator) {
                                highlightAnimator = null
                                applyRestingCardBorder()
                            }
                        })
                        start()
                    }
        }

        /**
         * Tap nick → user profile; long-press the header → an actions menu. The AVATAR tap is wired
         * separately in bindAvatar() to open the M3 user menu (Профиль/Репутация/QMS/…), so it must
         * NOT be overridden here. Navigation-only (no writes).
         */
        private fun bindAuthorActions(item: NativePostItem) {
            val openProfile = View.OnClickListener {
                if (item.userId > 0) linkHandler.handle(profileUrl(item.userId), null)
            }
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
                                ctx.getColorFromAttr(androidx.appcompat.R.attr.colorAccent)),
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

        /** Group line (tinted by server groupColor) + the date in the right column — WebView layout.
         * Reputation is the avatar badge ([bindRepBadge]); post count is [bindPostCount]. */
        private fun bindMeta(item: NativePostItem) {
            val group = item.group?.takeIf { it.isNotBlank() }
            if (group == null) {
                meta.visibility = View.GONE
            } else {
                meta.visibility = View.VISIBLE
                meta.text = group
                meta.setTextColor(parseColor(item.groupColor)
                        ?: itemView.context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
            }
            date.text = item.date?.takeIf { it.isNotBlank() }.orEmpty()
        }

        /** Reputation number overlaid on the avatar (parity with WebView .reputation). Tap → rep menu. */
        private fun bindRepBadge(item: NativePostItem) {
            val rep = item.reputation?.takeIf { it.isNotBlank() }
            if (rep == null || !settings.showAvatars) {
                repBadge.visibility = View.GONE
                return
            }
            repBadge.visibility = View.VISIBLE
            repBadge.text = rep
            repBadge.setOnClickListener { actionListener.onReputation(item) }
        }

        /** Author's forum post count as a 💬 N badge (parity with WebView user_post_count). */
        private fun bindPostCount(item: NativePostItem) {
            val count = item.userPostCount?.takeIf { it > 0 }
            // The count comes from the DEFERRED enrichment (~1s after open). Reserve its row height up
            // front so the header doesn't grow a line — and shove/rescale the whole post — when the count
            // arrives. INVISIBLE keeps the layout slot; the icon + placeholder give it the final height,
            // so the only change on enrichment is the number fading in (no jump).
            postCount.visibility = if (count == null) View.INVISIBLE else View.VISIBLE
            postCount.text = count?.toString() ?: "0"
            val icon = androidx.core.content.ContextCompat.getDrawable(
                    itemView.context, R.drawable.ic_post_count)?.mutate()?.apply {
                setTint(itemView.context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
                val s = (13 * itemView.resources.displayMetrics.density).toInt()
                setBounds(0, 0, s, s)
            }
            postCount.setCompoundDrawables(icon, null, null, null)
        }

        /** Three-dots post menu (parity with WebView showPostMenu). */
        private fun bindPostMenu(item: NativePostItem) {
            menu.setOnClickListener { actionListener.onPostMenu(item) }
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
            // Show the rating NUMBER only when it's non-zero — the WebView hides a «0» rating
            // (post_rating_hidden), otherwise every post would carry a meaningless «0» between the thumbs.
            item.postRating
                    ?.takeIf { it.isNotBlank() && it.replace("+", "").trim().toIntOrNull() != 0 }
                    ?.let { actions.addView(ratingLabel(it)) }
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
            val accent = ctx.getColorFromAttr(androidx.appcompat.R.attr.colorAccent)
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
            // The «ШАПКА ТЕМЫ» toggle is no longer rendered inside the body — it sits above the whole post
            // (see bindHatToggle). A collapsed hat hides the body entirely, so skip rendering its (often
            // huge) content until it's expanded.
            if (isHat && hatCollapsed) return
            renderBlocksInto(body, item.blocks, RenderScope(item.postId), item)
        }

        /** Fill [container] with a tappable «ШАПКА ТЕМЫ  ▾/▴» row that toggles the hat (WebView parity:
         *  uppercase title + right-aligned chevron, identical to the collapsed poll bar). */
        private fun fillHatToggle(container: LinearLayout, collapsed: Boolean) {
            val ctx = itemView.context
            val dm = ctx.resources.displayMetrics
            val accent = ctx.getColorFromAttr(androidx.appcompat.R.attr.colorAccent)
            container.isClickable = true
            // Add a little bottom padding only when expanded so the toggle isn't glued to the post below it.
            val vPad = if (collapsed) 0 else (6 * dm.density).toInt()
            container.setPadding(0, 0, 0, vPad)
            container.setOnClickListener { actionListener.onToggleHat() }
            container.addView(TextView(ctx).apply {
                text = "ШАПКА ТЕМЫ"
                textSize = scaledSp(15f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(accent)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            container.addView(TextView(ctx).apply {
                text = if (collapsed) "▾" else "▴"
                textSize = scaledSp(15f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(accent)
            })
        }

        /** Renders [blocks] as children of [container]. Reused recursively by quotes/spoilers. */
        private fun renderBlocksInto(container: LinearLayout, blocks: List<BodyBlock>, scope: RenderScope, item: NativePostItem) {
            for (block in blocks) {
                val child = when (block) {
                    is BodyBlock.Text -> textView(spanned(block.html), item)
                    is BodyBlock.EditNote -> editNoteView(block)
                    is BodyBlock.Image -> imageView(block, scope)
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
            val spoilNumber = scope.spoilerSeq + 1 // 1-based index of this spoiler within the post
            val key = "${scope.postId}:${scope.spoilerSeq++}"
            var open = spoilerStates[key] ?: block.initiallyOpen

            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((10 * dm.density).toInt())
                background = m3BlockBackground(com.google.android.material.R.attr.colorSurfaceContainerHigh)
                clipToOutline = true
                layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = (6 * dm.density).toInt() }
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
            renderBlocksInto(bodyContainer, block.inner, scope, item)
            applyState()
            header.setOnClickListener {
                open = !open
                spoilerStates[key] = open
                applyState()
            }
            // Long-press the spoiler title → copy a deep link to it (parity with the WebView copySpoilerLink).
            header.setOnLongClickListener {
                actionListener.onSpoilerCopyLink(item, spoilNumber)
                true
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
            val accent = ctx.getColorFromAttr(androidx.appcompat.R.attr.colorAccent)
            // M3 quote: a rounded tonal card (surfaceContainerHigh + hairline outline). The tonal fill and
            // the accent-coloured author header are the quote signifiers — no extra leading colour bar.
            val content = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((10 * dm.density).toInt())
                layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            }
            val headerText = listOfNotNull(
                    block.author?.takeIf { it.isNotBlank() },
                    block.date?.takeIf { it.isNotBlank() },
            ).joinToString(" · ").ifBlank { "Цитата" }
            val header = TextView(ctx).apply {
                text = headerText
                textSize = scaledSp(13f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(accent)
                val src = block.sourceUrl?.takeIf { it.isNotBlank() }
                if (src != null) setOnClickListener { linkHandler.handle(src, null) }
            }
            content.addView(header)
            renderBlocksInto(content, block.inner, scope, item)
            return LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                background = m3BlockBackground(com.google.android.material.R.attr.colorSurfaceContainerHigh)
                clipToOutline = true
                layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = (6 * dm.density).toInt() }
                addView(content)
            }
        }

        /**
         * Native inline attachment image. Reserves height from the server-provided display
         * dimensions BEFORE the bitmap loads, so a late-arriving image never slides the scroll
         * anchor (§2/§6). Tapping routes the attachment link through the app (image viewer /
         * download), same as the WebView path.
         */
        private fun imageView(block: BodyBlock.Image, scope: RenderScope): View {
            val ctx = itemView.context
            val dm = ctx.resources.displayMetrics
            val horizontalChromePx = (40 * dm.density).toInt() // card margins + paddings
            val columnWidthPx = (dm.widthPixels - horizontalChromePx).coerceAtLeast(1)
            val ratio = if (block.displayWidthPx > 0 && block.displayHeightPx > 0) {
                block.displayHeightPx.toFloat() / block.displayWidthPx.toFloat()
            } else {
                DEFAULT_IMAGE_RATIO
            }
            val topInset = (6 * dm.density).toInt()
            return ImageView(ctx).apply {
                if (block.inline) {
                    // INLINE content image (banner / preview / animated gif peeled from post text): render at
                    // its INTRINSIC size, downscaled to fit the column but NEVER upscaled — otherwise a small /
                    // low-res icon balloons into a blurry full-width block (user report). Crisp, like the browser.
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
                ForPdaCoil.loadInto(this, block.imageUrl)
                val tapUrl = block.linkUrl?.takeIf { it.isNotBlank() } ?: block.imageUrl
                val viewerUrl = FourPdaImageUrls.resolveViewerUrl(tapUrl)
                if (FourPdaImageUrls.isViewableInViewer(viewerUrl)) {
                    // Add to the post's running gallery and remember our slot; a tap opens the whole post
                    // as one swipeable gallery starting on this image (WebView parity).
                    val index = scope.galleryUrls.size
                    scope.galleryUrls.add(viewerUrl)
                    setOnClickListener { actionListener.onImageClick(scope.galleryUrls, index) }
                } else {
                    // Non-viewable (e.g. an off-site link) → hand off to the link handler as before.
                    setOnClickListener { linkHandler.handle(tapUrl, null) }
                }
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
                setTextColor(ctx.getColorFromAttr(androidx.appcompat.R.attr.colorAccent))
                setPadding(pad, pad, pad, pad)
                background = m3BlockBackground(com.google.android.material.R.attr.colorSurfaceContainerHigh)
                clipToOutline = true
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
                background = m3BlockBackground(com.google.android.material.R.attr.colorSurfaceContainerHigh)
                clipToOutline = true
                layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = (6 * dm.density).toInt() }
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
                setPadding((10 * resources.displayMetrics.density).toInt())
                background = m3BlockBackground(com.google.android.material.R.attr.colorSurfaceContainerHigh)
                clipToOutline = true
            }
            // NOTE: no «[KIND]» debug label — it is a dev artifact and must never reach users
            // (was surfacing e.g. «[UNKNOWN]» above a curator banner). Render only the content.
            val content = TextView(ctx).apply {
                setText(spanned(block.html))
                textSize = scaledSp(15f)
                setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
                setLineSpacing(0f, 1.1f)
            }
            panel.addView(content)
            val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = (6 * ctx.resources.displayMetrics.density).toInt() }
            panel.layoutParams = lp
            return panel
        }

        /**
         * The server edit note («Сообщение отредактировал … — …», + «Причина редактирования: …») — a
         * SYSTEM meta line. Rendered smaller and muted (mirrors the WebView `.edit`: 0.875em, #757575) so
         * it visually separates from the user's own post text. The editor-nick link inside stays tappable.
         */
        private fun editNoteView(block: BodyBlock.EditNote): View {
            val ctx = itemView.context
            val muted = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
            // Системная строка «Сообщение отредактировал N» — это метка, а не действие: ник должен быть
            // обычным muted-текстом, без гиперссылки и перехода в профиль. Убираем URLSpan целиком.
            val text = stripLinks(spanned(block.html))
            return TextView(ctx).apply {
                setText(text)
                textSize = scaledSp(13f) // ~0.875 of the 15sp body
                setTextColor(muted)
                setLineSpacing(0f, 1.15f)
                layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = (6 * ctx.resources.displayMetrics.density).toInt() }
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

        private fun textView(text: CharSequence, item: NativePostItem): TextView {
            val ctx = itemView.context
            return TextView(ctx).apply {
                setText(highlightSearchMatches(neutralizeLowContrastColors(stripLinkColors(text))))
                textSize = scaledSp(15f)
                setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
                // Force in-text links (profile nicks in the hat / «отредактировал N» footer) to the readable
                // accent — their server-side inline colour is picked for a white bg and vanishes on Sepia.
                // Use a contrast-safe variant: the per-palette accent is tuned for that palette's LIGHT card,
                // so on an AMOLED/dark surface it must be brightened or links «сливаются с фоном».
                setLinkTextColor(contrastSafeLinkColor())
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

        /**
         * Drop inline server text colours that are near-invisible on the current reading surface. The 4pda
         * topic hat is full of colours picked for a WHITE background (white/pale nicks, headers), which the
         * WebView neutralises via CSS but [Html.fromHtml] with FROM_HTML_OPTION_USE_CSS_COLORS applies
         * verbatim → on Sepia/Nord/… half the hat text (and the «отредактировал»/«Куратор темы» nicks) turns
         * invisible, leaving big empty gaps. We remove only the low-contrast spans so that text falls back to
         * the high-contrast colorOnSurface, while readable colours (green curator note, links) stay.
         */
        private fun neutralizeLowContrastColors(text: CharSequence): CharSequence {
            if (text !is Spanned) return text
            if (text.getSpans(0, text.length, android.text.style.ForegroundColorSpan::class.java).isEmpty()) return text
            val surface = itemView.context.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainer)
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
         * invisible — and an inline [ForegroundColorSpan] overrides the TextView's linkTextColor. Removing
         * any colour span overlapping a [URLSpan] lets [setLinkTextColor] win, so every link is readable
         * (parity with the WebView, which colours all links with the link colour).
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
        private fun contrastSafeLinkColor(): Int {
            val ctx = itemView.context
            val accent = ctx.getColorFromAttr(androidx.appcompat.R.attr.colorAccent)
            val surface = ctx.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainer)
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
                    itemView, androidx.appcompat.R.attr.colorAccent)
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
        /** Total lifetime of the open-highlight flash (ms). Kept generously long so the accent border is
         *  clearly noticeable even after the post-open enrichment re-binds the target post. */
        const val HIGHLIGHT_TOTAL_MS = 2600L
        const val DEFAULT_IMAGE_RATIO = 0.66f
        const val SMILE_SIZE_SP = 18f
        const val QUOTE_MENU_ID = 0x71_0716
        // Below this WCAG contrast ratio against the reading surface, an inline server text colour is
        // treated as invisible and dropped so the text falls back to colorOnSurface. ~2.5 keeps readable
        // colours (green curator note ≈4.5, medium greys ≈3.5) but strips white/pale-on-Sepia (≈1.2–2.0).
        const val LOW_CONTRAST_THRESHOLD = 2.5

        /** Comfortable link contrast on a DARK/AMOLED post surface, where saturated mid-blue accents
         *  read dim even above the bare-legibility floor. Above this we brighten the link. */
        const val DARK_SURFACE_LINK_CONTRAST = 5.5

        val ONLINE_DOT_COLOR = android.graphics.Color.parseColor("#4CAF50")

        val DIFF = object : DiffUtil.ItemCallback<NativePostItem>() {
            override fun areItemsTheSame(a: NativePostItem, b: NativePostItem) = a.postId == b.postId
            override fun areContentsTheSame(a: NativePostItem, b: NativePostItem) = a == b
        }
    }
}
