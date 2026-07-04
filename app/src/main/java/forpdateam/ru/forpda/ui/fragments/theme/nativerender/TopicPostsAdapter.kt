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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_native_post, parent, false)
        return PostViewHolder(view, linkHandler)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PostViewHolder(
            itemView: View,
            private val linkHandler: ILinkHandler,
    ) : RecyclerView.ViewHolder(itemView) {
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
                    is BodyBlock.Image -> imageView(block)
                    is BodyBlock.WebFallback -> bindFallback(block)
                }
                body.addView(child)
            }
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
                // Only attach the link movement method when the block actually has links — this
                // both avoids the ScrollingMovementMethod interfering with RecyclerView drags on
                // plain-text posts and routes taps through the app's in-app navigation.
                if (text is Spanned &&
                        text.getSpans(0, text.length, URLSpan::class.java).isNotEmpty()) {
                    movementMethod = LinkMovementMethod(object : LinkMovementMethod.ClickListener {
                        override fun onClick(url: String): Boolean = linkHandler.handle(url, null)
                    })
                }
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
        const val DEFAULT_IMAGE_RATIO = 0.66f

        val DIFF = object : DiffUtil.ItemCallback<NativePostItem>() {
            override fun areItemsTheSame(a: NativePostItem, b: NativePostItem) = a.postId == b.postId
            override fun areContentsTheSame(a: NativePostItem, b: NativePostItem) = a == b
        }
    }
}
