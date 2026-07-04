package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.common.Html
import forpdateam.ru.forpda.common.getColorFromAttr

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
class TopicPostsAdapter : ListAdapter<NativePostItem, TopicPostsAdapter.PostViewHolder>(DIFF) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).stableId

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_native_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatar: ImageView = itemView.findViewById(R.id.native_post_avatar)
        private val nick: TextView = itemView.findViewById(R.id.native_post_nick)
        private val meta: TextView = itemView.findViewById(R.id.native_post_meta)
        private val number: TextView = itemView.findViewById(R.id.native_post_number)
        private val body: LinearLayout = itemView.findViewById(R.id.native_post_body)
        private val footer: TextView = itemView.findViewById(R.id.native_post_footer)

        fun bind(item: NativePostItem) {
            nick.text = item.nick.orEmpty()
            meta.text = listOfNotNull(item.group?.takeIf { it.isNotBlank() }, item.date?.takeIf { it.isNotBlank() })
                    .joinToString(" · ")
            number.text = if (item.number > 0) "#${item.number}" else ""
            ForPdaCoil.loadInto(avatar, item.avatarUrl)

            val rating = item.postRating?.takeIf { it.isNotBlank() }
            footer.text = if (rating != null) "Рейтинг: $rating" else ""
            footer.visibility = if (rating != null) View.VISIBLE else View.GONE

            renderBody(item.blocks)
        }

        private fun renderBody(blocks: List<BodyBlock>) {
            body.removeAllViews()
            for (block in blocks) {
                val child = when (block) {
                    is BodyBlock.Text -> textView(spanned(block.html))
                    is BodyBlock.WebFallback -> bindFallback(block)
                }
                body.addView(child)
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
            }
        }

        private fun spanned(html: String): CharSequence = try {
            Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT or Html.FROM_HTML_OPTION_USE_CSS_COLORS, null, null)
                    .trimTrailingNewlines()
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
        val DIFF = object : DiffUtil.ItemCallback<NativePostItem>() {
            override fun areItemsTheSame(a: NativePostItem, b: NativePostItem) = a.postId == b.postId
            override fun areContentsTheSame(a: NativePostItem, b: NativePostItem) = a == b
        }
    }
}
