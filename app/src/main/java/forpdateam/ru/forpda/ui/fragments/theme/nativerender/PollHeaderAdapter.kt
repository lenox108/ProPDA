package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.entity.remote.theme.Poll
import forpdateam.ru.forpda.entity.remote.theme.PollQuestionItem

/**
 * A single-item (or empty) RecyclerView adapter rendering the topic-level poll ([Poll]) as a header
 * above the posts, combined with [TopicPostsAdapter] via ConcatAdapter (roadmap
 * `native-topic-renderer.md`, Фаза 4).
 *
 * A RESULT poll (already voted / closed) shows options + result bars. An open poll the user can
 * vote in shows radio/checkbox inputs and a «Голосовать» button — voting is submitted through
 * [PollVoteListener] (parity with the WebView `submitThemePoll`, which posts the checked inputs +
 * hidden fields to the poll form action). Selection is held here so it survives a re-bind.
 */
class PollHeaderAdapter(
        private val voteListener: PollVoteListener? = null,
        /** Inline poll (page 1) starts collapsed and toggles via its title bar; the popup is always open. */
        private val collapsible: Boolean = true,
) : RecyclerView.Adapter<PollHeaderAdapter.PollViewHolder>() {

    /** Fired when the user taps «Голосовать»; mirrors the WebView JS `IThemePresenter.submitPoll`. */
    fun interface PollVoteListener {
        fun onSubmitPoll(action: String, method: String, encodedForm: String)
    }

    private var poll: Poll? = null
    /** Inline collapse state (collapsed by default when [collapsible]); the popup passes false. */
    private var collapsed: Boolean = collapsible
    /** Selected option keys ("name\u0000value"); a radio group (same [name]) keeps at most one. */
    private val selected = LinkedHashSet<String>()

    fun setPoll(poll: Poll?) {
        val had = this.poll != null
        this.poll = poll
        selected.clear()
        val has = poll != null
        when {
            had && !has -> notifyItemRemoved(0)
            !had && has -> notifyItemInserted(0)
            has -> notifyItemChanged(0)
        }
    }

    override fun getItemCount(): Int = if (poll != null) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PollViewHolder {
        val ctx = parent.context
        val dm = ctx.resources.displayMetrics
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT,
            )
            // Same geometry as a post card (item_native_post.xml): 8dp horizontal / 4dp vertical margins,
            // 12dp / 10dp inner padding — so the poll header lines up flush with the posts below it.
            val hMargin = (8 * dm.density).toInt()
            val vMargin = (4 * dm.density).toInt()
            setPadding((12 * dm.density).toInt(), (10 * dm.density).toInt(),
                    (12 * dm.density).toInt(), (10 * dm.density).toInt())
            (layoutParams as RecyclerView.LayoutParams).setMargins(hMargin, vMargin, hMargin, vMargin)
            // Match the post card's Material 3 look (rounded 16dp corners + hairline outline + a slight
            // elevation) instead of a flat rectangle, so the poll header reads as the same M3 surface as
            // the «ШАПКА ТЕМЫ» card and every post below it. «Плоские посты» drops the outline+shadow to
            // stay in sync with the post cards below.
            val flat = forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder(ctx).getFlatPosts()
            background = postCardBackground(ctx, flat)
            clipToOutline = true
            androidx.core.view.ViewCompat.setElevation(this, (if (flat) 0f else 2f) * dm.density)
        }
        return PollViewHolder(root)
    }

    override fun onBindViewHolder(holder: PollViewHolder, position: Int) {
        poll?.let { holder.bind(it) }
    }

    /**
     * The same rounded Material 3 card background the post cards draw (TopicPostsAdapter): a
     * `content_card_surface` fill, 16dp corners and a resting hairline outline — kept in sync with the
     * post card so the poll header sits on the identical surface as the «ШАПКА ТЕМЫ» card below it.
     */
    private fun postCardBackground(ctx: android.content.Context, flat: Boolean = false): android.graphics.drawable.GradientDrawable {
        val dm = ctx.resources.displayMetrics
        val fill = ctx.getColorFromAttr(forpdateam.ru.forpda.R.attr.content_card_surface)
        return android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 16f * dm.density
            setColor(fill)
            setStroke(
                    if (flat) 0 else (1f * dm.density).toInt().coerceAtLeast(1),
                    restingCardBorderColor(ctx, fill),
            )
        }
    }

    /** Resting card border colour, mirroring [TopicPostsAdapter]'s hairline: on light fills soften
     *  `colorOutlineVariant` toward the fill; on dark/AMOLED fills lift `colorOutline` toward
     *  `colorOnSurface` so the edge still separates a near-black card from the near-black page. */
    private fun restingCardBorderColor(ctx: android.content.Context, fill: Int): Int {
        if (androidx.core.graphics.ColorUtils.calculateLuminance(fill) >= 0.5) {
            val outlineVariant = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOutlineVariant)
            return androidx.core.graphics.ColorUtils.blendARGB(outlineVariant, fill, 0.35f)
        }
        val outline = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOutline)
        val onSurface = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
        return androidx.core.graphics.ColorUtils.blendARGB(outline, onSurface, 0.30f)
    }

    private fun key(item: PollQuestionItem): String = "${item.name.orEmpty()}\u0000${item.value}"

    private fun isChecked(item: PollQuestionItem): Boolean = selected.contains(key(item))

    /** Toggle an option, enforcing single-choice for radio groups (same input [name]). */
    private fun toggle(item: PollQuestionItem, checked: Boolean) {
        val k = key(item)
        val isRadio = item.type.equals("radio", ignoreCase = true)
        if (checked) {
            if (isRadio) {
                // Radio: drop any other selection sharing this input name before adding this one.
                selected.removeAll { it.substringBefore('\u0000') == item.name.orEmpty() }
            }
            selected.add(k)
        } else {
            selected.remove(k)
        }
    }

    private fun buildEncodedForm(poll: Poll): String? {
        val checked = poll.questions.flatMap { it.questionItems }.filter { isChecked(it) }
        return PollVoteFormEncoder.encode(checked, poll.hiddenInputs)
    }

    inner class PollViewHolder(private val root: LinearLayout) : RecyclerView.ViewHolder(root) {

        fun bind(poll: Poll) {
            root.removeAllViews()
            val ctx = root.context
            val dm = ctx.resources.displayMetrics
            val onSurface = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
            val onSurfaceVar = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
            // colorAccent, НЕ colorPrimary: заголовок должен быть идентичен полосе «ШАПКА ТЕМЫ»
            // (TopicPostsAdapter.fillHatToggle). colorPrimary в ряде палитр (светлая #FFFFFF,
            // Sepia — кремовый) почти совпадает с фоном карточки — текст был нечитаемым.
            // Под Material You colorAccent тоже корректен: MaterialYouAccent-оверлей мапит его
            // в динамический colorPrimary обоев.
            val accent = ctx.getColorFromAttr(androidx.appcompat.R.attr.colorAccent)

            fun tv(text: CharSequence, sizeSp: Float, color: Int, bold: Boolean = false, topDp: Int = 0) =
                    TextView(ctx).apply {
                        setText(text)
                        textSize = sizeSp
                        setTextColor(color)
                        if (bold) setTypeface(typeface, Typeface.BOLD)
                        if (topDp > 0) {
                            layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                            ).apply { topMargin = (topDp * dm.density).toInt() }
                        }
                    }

            // Title bar. Inline (collapsible) → WebView-style bar: uppercase title + right-aligned chevron
            // (identical to the collapsed hat block). Popup → plain title, no chevron.
            val pollTitle = poll.title?.takeIf { it.isNotBlank() } ?: "Опрос"
            if (collapsible) {
                val titleText = tv(pollTitle.uppercase(), 15f, accent, bold = true).apply {
                    layoutParams = LinearLayout.LayoutParams(0,
                            LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val chevron = tv(if (collapsed) "▾" else "▴", 15f, accent, bold = true)
                root.addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    isClickable = true
                    setOnClickListener { collapsed = !collapsed; notifyItemChanged(0) }
                    addView(titleText)
                    addView(chevron)
                })
            } else {
                root.addView(tv(pollTitle, 15f, accent, bold = true))
            }
            // WebView keeps «Проголосовало: N» inside the collapsible body, so a collapsed inline poll
            // shows ONLY the title bar (parity: 2 blocks that fully hide/expand).
            if (collapsible && collapsed) return
            if (poll.votesCount > 0) {
                root.addView(tv("Проголосовало: ${poll.votesCount}", 12f, onSurfaceVar, topDp = 2))
            }

            val votable = !poll.isResult && poll.canVote

            for (question in poll.questions) {
                question.title?.takeIf { it.isNotBlank() }?.let {
                    root.addView(tv(it, 14f, onSurface, bold = true, topDp = 8))
                }
                for (item in question.questionItems) {
                    val optionTitle = item.title?.takeIf { it.isNotBlank() } ?: continue
                    if (poll.isResult) {
                        // Result row: "option — N (X%)" + a progress bar.
                        val label = "$optionTitle — ${item.votes} (${item.percent.toInt()}%)"
                        root.addView(tv(label, 13f, onSurface, topDp = 6))
                        root.addView(ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                            max = 100
                            progress = item.percent.toInt().coerceIn(0, 100)
                            layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    (6 * dm.density).toInt(),
                            ).apply { topMargin = (2 * dm.density).toInt() }
                        })
                    } else if (votable) {
                        // Open poll: a real radio/checkbox the user can select before voting.
                        root.addView(optionButton(ctx, item, optionTitle, onSurface, dm))
                    } else {
                        // Read-only choices (e.g. voting closed for this user): list options only.
                        root.addView(tv("• $optionTitle", 13f, onSurface, topDp = 6))
                    }
                }
            }

            if (votable && voteListener != null) {
                root.addView(com.google.android.material.button.MaterialButton(ctx).apply {
                    text = "Голосовать"
                    layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = (10 * dm.density).toInt() }
                    setOnClickListener {
                        val encoded = buildEncodedForm(poll)
                        if (encoded == null) {
                            android.widget.Toast.makeText(ctx, "Выберите вариант ответа", android.widget.Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        val action = poll.formAction?.takeIf { it.isNotBlank() } ?: "https://4pda.to/forum/index.php"
                        val method = if (poll.formMethod.equals("post", ignoreCase = true)) "post" else "get"
                        voteListener.onSubmitPoll(action, method, encoded)
                    }
                })
            } else if (!poll.isResult && poll.canVote) {
                root.addView(tv("Голосование недоступно", 11f, onSurfaceVar, topDp = 8)
                        .apply { gravity = Gravity.START })
            }
        }

        private fun optionButton(
                ctx: android.content.Context,
                item: PollQuestionItem,
                optionTitle: String,
                onSurface: Int,
                dm: android.util.DisplayMetrics,
        ): CompoundButton {
            val isRadio = item.type.equals("radio", ignoreCase = true)
            val button = if (isRadio) RadioButton(ctx) else CheckBox(ctx)
            return button.apply {
                text = optionTitle
                textSize = 13f
                setTextColor(onSurface)
                isChecked = isChecked(item)
                layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = (4 * dm.density).toInt() }
                setOnClickListener {
                    toggle(item, isChecked)
                    // A radio pick clears siblings — rebind so their buttons visually deselect.
                    if (isRadio) notifyItemChanged(0)
                }
            }
        }
    }
}
