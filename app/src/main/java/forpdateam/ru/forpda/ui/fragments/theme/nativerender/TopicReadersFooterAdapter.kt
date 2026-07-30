package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.entity.remote.theme.TopicActiveReaders

/**
 * Подвал «Сейчас читают тему: N» под последним постом страницы — как на полной версии сайта.
 *
 * Показывается ТОЛЬКО в классическом режиме чтения (страница за страницей). В гибриде страницы
 * склеены в одну ленту, и такой блок либо оказался бы посреди неё, либо ломал бы пин последнего
 * поста и проверки «конец ленты» — поэтому там счётчик живёт только в меню темы.
 *
 * Одна строка + строка кликабельных ников (тап → профиль). Скрывается, когда читателей ≤ 1: на
 * мёртвой теме «читают: 1» — это ты сам, и строка только шумит.
 */
class TopicReadersFooterAdapter(
        private val onMemberClick: (userId: Int) -> Unit,
) : RecyclerView.Adapter<TopicReadersFooterAdapter.ReadersViewHolder>() {

    private var readers: TopicActiveReaders? = null

    /** @param enabled режим чтения допускает подвал (классический); в гибриде — false. */
    fun setReaders(readers: TopicActiveReaders?, enabled: Boolean) {
        val next = readers?.takeIf { enabled && it.total > 1 }
        val had = this.readers != null
        this.readers = next
        val has = next != null
        when {
            had && !has -> notifyItemRemoved(0)
            !had && has -> notifyItemInserted(0)
            has -> notifyItemChanged(0)
        }
    }

    override fun getItemCount(): Int = if (readers != null) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReadersViewHolder {
        val ctx = parent.context
        val dm = ctx.resources.displayMetrics
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT,
            )
            // Геометрия карточки поста (item_native_post.xml): 8dp по горизонтали, 4dp по вертикали,
            // 12/10dp внутренние отступы — подвал встаёт вровень с постами над ним.
            val hMargin = (8 * dm.density).toInt()
            val vMargin = (4 * dm.density).toInt()
            setPadding((12 * dm.density).toInt(), (10 * dm.density).toInt(),
                    (12 * dm.density).toInt(), (10 * dm.density).toInt())
            (layoutParams as RecyclerView.LayoutParams).setMargins(hMargin, vMargin, hMargin, vMargin)
            // Тот же M3-фон, что у карточки поста и у шапки опроса, включая «Плоские посты».
            val flat = forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder(ctx).getFlatPosts()
            background = postCardBackground(ctx, flat)
            clipToOutline = true
            androidx.core.view.ViewCompat.setElevation(this, (if (flat) 0f else 2f) * dm.density)
        }
        val title = TextView(ctx).apply {
            textSize = 13f
            setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
        }
        val members = TextView(ctx).apply {
            textSize = 13f
            setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
            movementMethod = LinkMovementMethod.getInstance()
            (layoutParams as? LinearLayout.LayoutParams ?: LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
            )).also {
                it.topMargin = (4 * dm.density).toInt()
                layoutParams = it
            }
        }
        root.addView(title)
        root.addView(members)
        return ReadersViewHolder(root, title, members)
    }

    override fun onBindViewHolder(holder: ReadersViewHolder, position: Int) {
        readers?.let { holder.bind(it, onMemberClick) }
    }

    /** Фон карточки поста (см. [PollHeaderAdapter.postCardBackground]) — подвал не должен выбиваться. */
    private fun postCardBackground(
            ctx: android.content.Context,
            flat: Boolean,
    ): android.graphics.drawable.GradientDrawable {
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

    private fun restingCardBorderColor(ctx: android.content.Context, fill: Int): Int {
        if (androidx.core.graphics.ColorUtils.calculateLuminance(fill) >= 0.5) {
            val outlineVariant = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOutlineVariant)
            return androidx.core.graphics.ColorUtils.blendARGB(outlineVariant, fill, 0.35f)
        }
        val outline = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOutline)
        val onSurface = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
        return androidx.core.graphics.ColorUtils.blendARGB(outline, onSurface, 0.30f)
    }

    class ReadersViewHolder(
            root: View,
            private val title: TextView,
            private val members: TextView,
    ) : RecyclerView.ViewHolder(root) {

        fun bind(readers: TopicActiveReaders, onMemberClick: (userId: Int) -> Unit) {
            title.text = buildString {
                append("Сейчас читают тему: ")
                append(readers.total)
                val tail = buildList {
                    if (readers.guests > 0) add("гостей: ${readers.guests}")
                    if (readers.hidden > 0) add("скрытых: ${readers.hidden}")
                }
                if (tail.isNotEmpty()) tail.joinTo(this, prefix = " (", postfix = ")")
            }
            if (readers.members.isEmpty()) {
                members.visibility = View.GONE
                members.text = ""
                return
            }
            members.visibility = View.VISIBLE
            val accent = members.context.getColorFromAttr(androidx.appcompat.R.attr.colorAccent)
            val text = SpannableStringBuilder()
            readers.members.forEachIndexed { index, member ->
                if (index > 0) text.append(", ")
                val start = text.length
                text.append(member.nick)
                text.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) = onMemberClick(member.userId)
                    override fun updateDrawState(ds: android.text.TextPaint) {
                        ds.color = accent
                        ds.isUnderlineText = false
                    }
                }, start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            members.text = text
        }
    }
}
