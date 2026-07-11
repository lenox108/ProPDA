package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.presentation.ILinkHandler

/**
 * RecyclerView adapter for the native topic renderer (roadmap `native-topic-renderer.md`,
 * Фаза 1). Stable ids = post id (see [NativePostItem.stableId]) so diffing and the anchor
 * controller line up on the same key.
 *
 * Body rendering lives in [BodyBlockViewFactory], shared with the QMS chat renderer — the post
 * card (header, avatar, rating, action row, highlight) is all this adapter still owns.
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
        /** Long-press on the 👍/👎 post-rating icon → change the author's user reputation directly
         *  ([up] = raise, matching the pressed thumb). A shortcut over the avatar-badge → menu path. */
        fun onReputationLongPress(item: NativePostItem, up: Boolean)
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
        /** Long-press on a downloadable file link → chooser (download / open in browser). */
        fun onDownloadLinkLongPress(url: String, fileName: String?)
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
            /** «Анимированные смайлы»: play smile GIFs in post bodies instead of a static frame. */
            val animatedSmiles: Boolean = true,
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

        /** The post currently bound to this holder — the owner of whatever body views are on screen. */
        private var boundItem: NativePostItem? = null

        /** Shared body renderer; block actions are re-routed to the post that owns the visible views. */
        private val blockFactory = BodyBlockViewFactory(
                linkHandler,
                spoilerStates,
                object : BodyBlockViewFactory.Callbacks {
                    override fun onImageClick(galleryUrls: List<String>, index: Int) =
                            actionListener.onImageClick(galleryUrls, index)

                    override fun onSpoilerCopyLink(scopeId: Int, spoilNumber: Int) {
                        boundItem?.let { actionListener.onSpoilerCopyLink(it, spoilNumber) }
                    }

                    override fun onQuoteSelection(scopeId: Int, selectedText: String) {
                        boundItem?.let { actionListener.onQuoteSelection(it, selectedText) }
                    }

                    override fun onDownloadLinkLongPress(url: String, fileName: String?) =
                            actionListener.onDownloadLinkLongPress(url, fileName)
                },
        )

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
            boundItem = item
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
                val superCompact =
                        settings.density == forpdateam.ru.forpda.common.Preferences.Main.TopicPostDensity.SUPER_COMPACT
                val topDp = if (superCompact) 4f else 8f
                val top = if (hatFolded) 0 else (topDp * itemView.resources.displayMetrics.density).toInt()
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

        /** Colored circle + first letter of the nick — WebView-style fallback avatar (shared helper). */
        private fun letterAvatar(ctx: android.content.Context, nick: String?): android.graphics.drawable.Drawable =
                forpdateam.ru.forpda.common.letterAvatarDrawable(ctx, nick)

        // Post card fill = the app-wide CONTENT CARD surface (`?attr/content_card_surface`), the same
        // role plate lists (favorites/news/menu/settings/DevDB…) and CardStyle.Item (notes, search,
        // device comments) use — so every content card across the app is one colour.
        //
        // The attr is a per-theme redirect because the surface ramps run in OPPOSITE directions and no
        // single M3 role is right for both:
        //  • Dark / AMOLED → `colorSurfaceContainerHigh`. ...Lowest/...Low/...Container all collapse to
        //    near-black (static dark #121212; dynamic «Системный стиль» packs tones 4/10/12), so a card
        //    on those melts into the page («блоки постов прям чёрные»). ...High is the first tone that
        //    lifts to a real grey (static #242424, dynamic tone 17). colorSurface can't be used — under
        //    the dynamic dark scheme it is tone 6, near-black again.
        //  • Light / cream (System light, Sepia, Nord, Gruvbox, …) → `colorSurface`. There the ramp is
        //    INVERTED: colorSurface (card_background) is the LIGHTEST/near-white tone while ...High
        //    (background_for_cards) is a DARKER inset grey, which made cards look muddy («слишком серые»).
        //
        // Redirecting to a framework M3 role (not a literal @color) keeps Material You working:
        // DynamicColors overrides those roles at runtime, so cards track the wallpaper automatically.
        // Nested blocks (quote/spoiler/code) stay on ...Highest — a lighter grey over a dark card, a
        // subtle darker inset over a light one — separated either way.
        private fun cardBaseColor(): Int =
                itemView.context.getColorFromAttr(forpdateam.ru.forpda.R.attr.content_card_surface)

        /**
         * Resting hairline border for the post card. Elevation shadows are invisible on dark/AMOLED
         * surfaces, so without an outline every near-black card melts into the near-black page (user
         * report). A 1dp edge keeps cards delineated in every theme.
         *
         * Delicacy (user: «рамки слишком грубо и жирно», wants VK-style near-invisible hairlines): the
         * two adjacent card edges + the elevation shadow read as a heavy channel between posts. On LIGHT
         * palettes we start from `colorOutlineVariant` (M3's decorative-divider role) and then blend it a
         * third of the way toward the card fill, so the edge softens into a barely-there hairline instead
         * of a hard rule. On DARK/AMOLED palettes (esp. the dynamic «Системный стиль», where …Container/
         * …Lowest and both outline roles collapse to nearly the same near-black) even `colorOutline` is
         * invisible, so we instead lift it toward `colorOnSurface` until the edge just separates from the
         * black card — a faint but visible hairline.
         */
        private fun restingCardBorderColor(): Int {
            val fill = cardBaseColor()
            if (androidx.core.graphics.ColorUtils.calculateLuminance(fill) >= 0.5) {
                val outlineVariant = com.google.android.material.color.MaterialColors.getColor(
                        itemView, com.google.android.material.R.attr.colorOutlineVariant)
                return androidx.core.graphics.ColorUtils.blendARGB(outlineVariant, fill, 0.35f)
            }
            val outline = com.google.android.material.color.MaterialColors.getColor(
                    itemView, com.google.android.material.R.attr.colorOutline)
            val onSurface = com.google.android.material.color.MaterialColors.getColor(
                    itemView, com.google.android.material.R.attr.colorOnSurface)
            return androidx.core.graphics.ColorUtils.blendARGB(outline, onSurface, 0.30f)
        }

        private fun cardBorderWidthPx(): Int =
                (1f * itemView.resources.displayMetrics.density).toInt().coerceAtLeast(1)

        private fun applyRestingCardBorder() =
                cardBg.setStroke(cardBorderWidthPx(), restingCardBorderColor())

        /**
         * Post density (Комфортная/Компактная/Сверхкомпактная) — tightens the card's inner vertical
         * padding and the gap between cards, mirroring the WebView density setting. Horizontal
         * padding stays constant so text width doesn't jump.
         */
        private fun applyDensity() {
            val dm = itemView.resources.displayMetrics
            // Steps spread wide enough to be clearly distinguishable (user: «не вижу разницы между
            // компактной и супер компактной»): SUPER packs cards flush (gap 0, minimal inner pad).
            val (vPadDp, gapDp) = when (settings.density) {
                forpdateam.ru.forpda.common.Preferences.Main.TopicPostDensity.SUPER_COMPACT -> 1f to 0f
                forpdateam.ru.forpda.common.Preferences.Main.TopicPostDensity.COMPACT -> 6f to 3f
                forpdateam.ru.forpda.common.Preferences.Main.TopicPostDensity.COMFORTABLE -> 12f to 5f
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
            applyHeaderDensity(dm)
        }

        /**
         * Super-compact header (user request «супер-компакт»): collapse the author header to a single
         * tight row — a smaller avatar, vertically centred so it no longer «заезжает на верхнюю границу»,
         * with the group line and post-count hidden ([bindMeta]/[bindPostCount]) and the date pulled up
         * next to the ⋮ menu on the same line as the nick. Other densities restore the roomy stacked
         * header. Runs every bind, so recycled holders reset both ways.
         */
        private fun applyHeaderDensity(dm: android.util.DisplayMetrics) {
            val superCompact =
                    settings.density == forpdateam.ru.forpda.common.Preferences.Main.TopicPostDensity.SUPER_COMPACT
            val avatarPx = ((if (superCompact) 32f else 44f) * dm.density).toInt()
            avatar.layoutParams = avatar.layoutParams.apply { width = avatarPx; height = avatarPx }
            (header as? LinearLayout)?.gravity =
                    if (superCompact) android.view.Gravity.CENTER_VERTICAL else android.view.Gravity.TOP
            // The date's parent is the right-hand column (date stacked over the ⋮ menu row). In
            // super-compact lay it out horizontally so date + ⋮ share one line beside the nick.
            (date.parent as? LinearLayout)?.let { right ->
                right.orientation = if (superCompact) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
                right.gravity =
                        if (superCompact) android.view.Gravity.CENTER_VERTICAL else android.view.Gravity.END
            }
            (date.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                val end = if (superCompact) (6 * dm.density).toInt() else 0
                if (lp.marginEnd != end) { lp.marginEnd = end; date.layoutParams = lp }
            }
            // Pull the action row (👍/👎 · ответить · цитата) up against the body in super-compact.
            (actions.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                val top = ((if (superCompact) 0 else 6) * dm.density).toInt()
                if (lp.topMargin != top) { lp.topMargin = top; actions.layoutParams = lp }
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
            val superCompact =
                    settings.density == forpdateam.ru.forpda.common.Preferences.Main.TopicPostDensity.SUPER_COMPACT
            val group = item.group?.takeIf { it.isNotBlank() }
            if (superCompact || group == null) {
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
            // Super-compact hides the post-count row entirely (user request) — no reserved slot either,
            // so the header collapses to a single line.
            if (settings.density == forpdateam.ru.forpda.common.Preferences.Main.TopicPostDensity.SUPER_COMPACT) {
                postCount.visibility = View.GONE
                return
            }
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
                actions.addView(iconAction(R.drawable.ic_post_thumb_up, null,
                        onLongClick = { actionListener.onReputationLongPress(item, up = true) }) {
                    actionListener.onVote(item, up = true)
                })
            }
            // Show the rating NUMBER only when it's non-zero — the WebView hides a «0» rating
            // (post_rating_hidden), otherwise every post would carry a meaningless «0» between the thumbs.
            item.postRating
                    ?.takeIf { it.isNotBlank() && it.replace("+", "").trim().toIntOrNull() != 0 }
                    ?.let { actions.addView(ratingLabel(it)) }
            if (canMinus) {
                actions.addView(iconAction(R.drawable.ic_post_thumb_down, null,
                        onLongClick = { actionListener.onReputationLongPress(item, up = false) }) {
                    actionListener.onVote(item, up = false)
                })
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
        private fun iconAction(
                iconRes: Int,
                label: String?,
                onLongClick: (() -> Unit)? = null,
                onClick: () -> Unit,
        ): TextView {
            val ctx = itemView.context
            val dm = ctx.resources.displayMetrics
            val accent = ctx.getColorFromAttr(androidx.appcompat.R.attr.colorAccent)
            val iconTint = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
            // Super-compact shrinks the action row: smaller icons and much tighter vertical padding so
            // each post loses height (user request «сжать нижнюю панель действий»). Tap targets stay
            // usable via the horizontal padding.
            val superCompact =
                    settings.density == forpdateam.ru.forpda.common.Preferences.Main.TopicPostDensity.SUPER_COMPACT
            return TextView(ctx).apply {
                text = label.orEmpty()
                if (!label.isNullOrEmpty()) {
                    textSize = scaledSp(13f)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(accent)
                }
                val icon = androidx.core.content.ContextCompat.getDrawable(ctx, iconRes)?.mutate()?.apply {
                    setTint(iconTint)
                    val s = ((if (superCompact) 16 else 18) * dm.density).toInt()
                    setBounds(0, 0, s, s)
                }
                setCompoundDrawables(icon, null, null, null)
                compoundDrawablePadding = (4 * dm.density).toInt()
                val padH = (8 * dm.density).toInt()
                val padV = ((if (superCompact) 2 else 5) * dm.density).toInt()
                setPadding(padH, padV, padH, padV)
                gravity = android.view.Gravity.CENTER_VERTICAL
                setOnClickListener { onClick() }
                if (onLongClick != null) {
                    // The framework already emits the long-press haptic when the listener returns true,
                    // so we must NOT fire performHapticFeedback ourselves — that doubled the vibration.
                    setOnLongClickListener {
                        onLongClick()
                        true
                    }
                }
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
            blockFactory.textScale = settings.textScale
            blockFactory.searchQuery = searchQuery
            blockFactory.animatedSmiles = settings.animatedSmiles
            blockFactory.render(
                    body,
                    item.blocks,
                    BodyBlockViewFactory.RenderScope(item.postId, allowQuoteSelection = item.canQuote),
            )
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

    }

    private companion object {
        /** Total lifetime of the open-highlight flash (ms). Kept generously long so the accent border is
         *  clearly noticeable even after the post-open enrichment re-binds the target post. */
        const val HIGHLIGHT_TOTAL_MS = 2600L

        val ONLINE_DOT_COLOR = android.graphics.Color.parseColor("#4CAF50")

        val DIFF = object : DiffUtil.ItemCallback<NativePostItem>() {
            override fun areItemsTheSame(a: NativePostItem, b: NativePostItem) = a.postId == b.postId
            override fun areContentsTheSame(a: NativePostItem, b: NativePostItem) = a == b
        }
    }
}
