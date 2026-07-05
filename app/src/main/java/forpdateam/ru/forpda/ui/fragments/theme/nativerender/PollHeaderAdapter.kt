package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.entity.remote.theme.Poll

/**
 * A single-item (or empty) RecyclerView adapter rendering the topic-level poll ([Poll]) as a header
 * above the posts, combined with [TopicPostsAdapter] via ConcatAdapter (roadmap
 * `native-topic-renderer.md`, Фаза 4). The poll is parsed by `ThemeParser` into [Poll] already, so
 * this only presents it — READ-ONLY (question + options + result bars / vote counts). Voting is a
 * server write and is intentionally not wired here.
 */
class PollHeaderAdapter : RecyclerView.Adapter<PollHeaderAdapter.PollViewHolder>() {

    private var poll: Poll? = null

    fun setPoll(poll: Poll?) {
        val had = this.poll != null
        this.poll = poll
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
            val margin = (8 * dm.density).toInt()
            setPadding((12 * dm.density).toInt(), (10 * dm.density).toInt(),
                    (12 * dm.density).toInt(), (10 * dm.density).toInt())
            (layoutParams as RecyclerView.LayoutParams).setMargins(margin, margin, margin, margin)
            setBackgroundColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainer))
        }
        return PollViewHolder(root)
    }

    override fun onBindViewHolder(holder: PollViewHolder, position: Int) {
        poll?.let { holder.bind(it) }
    }

    class PollViewHolder(private val root: LinearLayout) : RecyclerView.ViewHolder(root) {

        fun bind(poll: Poll) {
            root.removeAllViews()
            val ctx = root.context
            val dm = ctx.resources.displayMetrics
            val onSurface = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
            val onSurfaceVar = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
            val accent = ctx.getColorFromAttr(androidx.appcompat.R.attr.colorPrimary)

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

            root.addView(tv("📊 " + (poll.title?.takeIf { it.isNotBlank() } ?: "Опрос"), 15f, accent, bold = true))
            if (poll.votesCount > 0) {
                root.addView(tv("Проголосовало: ${poll.votesCount}", 12f, onSurfaceVar, topDp = 2))
            }

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
                    } else {
                        // Not voted yet: just list the options (voting is a server write, not wired).
                        root.addView(tv("• $optionTitle", 13f, onSurface, topDp = 6))
                    }
                }
            }

            if (!poll.isResult && poll.canVote) {
                root.addView(tv("Голосование — в WebView-режиме темы", 11f, onSurfaceVar, topDp = 8)
                        .apply { gravity = Gravity.START })
            }
        }
    }
}
