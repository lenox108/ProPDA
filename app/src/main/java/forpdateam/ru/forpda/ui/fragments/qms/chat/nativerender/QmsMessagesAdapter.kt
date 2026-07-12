package forpdateam.ru.forpda.ui.fragments.qms.chat.nativerender

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.ui.fragments.theme.nativerender.BodyBlockViewFactory

/**
 * Native QMS chat list (replaces the `template_qms_chat.html` + `qms.js` WebView).
 *
 * Message bodies are rendered by the shared [BodyBlockViewFactory] — the same code path the native
 * topic renderer uses for post bodies — so quotes, spoilers, code, smiles, images and file
 * attachments behave identically in a dialog and in a topic.
 */
class QmsMessagesAdapter(
        linkHandler: ILinkHandler,
        private val listener: Listener,
) : ListAdapter<QmsChatItem, RecyclerView.ViewHolder>(DIFF) {

    interface Listener {
        /** Tap on an attachment image → swipeable viewer over that message's images. */
        fun onImageClick(galleryUrls: List<String>, index: Int)

        /** Long-press on an attachment image → actions menu (save / open in browser / copy link). */
        fun onImageLongClick(imageUrl: String)

        /** Long-press on a bubble → the message actions menu (copy). */
        fun onMessageLongClick(anchor: View, item: QmsChatItem.Message)

        /** Tap on a file-attachment link → download it (host passes an Activity context so the
         *  «Способ загрузки → Спрашивать каждый раз» chooser can appear). */
        fun onDownloadLinkTap(url: String, fileName: String?)

        /** Long-press on an in-text hyperlink → chooser (open in browser / share / copy link). */
        fun onLinkLongClick(url: String)
    }

    /** Spoiler expand state, keyed "messageId:spoilerIndex", surviving view recycling. */
    private val spoilerStates = HashMap<String, Boolean>()

    private val blockFactory = BodyBlockViewFactory(
            linkHandler,
            spoilerStates,
            object : BodyBlockViewFactory.Callbacks {
                override fun onImageClick(galleryUrls: List<String>, index: Int) =
                        listener.onImageClick(galleryUrls, index)

                override fun onImageLongClick(imageUrl: String) =
                        listener.onImageLongClick(imageUrl)

                override fun onDownloadLinkTap(url: String, fileName: String?) =
                        listener.onDownloadLinkTap(url, fileName)

                override fun onLinkLongClick(url: String) =
                        listener.onLinkLongClick(url)
            },
    )

    /** Body text scale, mirroring the app font-size preference (1.0 = the reference 16-px body). */
    var textScale: Float = 1f
        set(value) {
            if (value == field) return
            field = value
            notifyDataSetChanged()
        }

    /** «Анимированные смайлы» pref: play smile GIFs in message bodies instead of a static frame. */
    var animatedSmiles: Boolean = false
        set(value) {
            if (value == field) return
            field = value
            notifyDataSetChanged()
        }

    /** «Плоские посты» pref: drop the hairline stroke on quote/spoiler blocks inside messages. */
    var flatBlocks: Boolean = false
        set(value) {
            if (value == field) return
            field = value
            notifyDataSetChanged()
        }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is QmsChatItem.DateDivider -> TYPE_DATE
        is QmsChatItem.Message -> TYPE_MESSAGE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_DATE) {
            DateViewHolder(inflater.inflate(R.layout.item_qms_date, parent, false))
        } else {
            MessageViewHolder(inflater.inflate(R.layout.item_qms_message, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        blockFactory.textScale = textScale
        blockFactory.animatedSmiles = animatedSmiles
        blockFactory.flatBlocks = flatBlocks
        when (val item = getItem(position)) {
            is QmsChatItem.DateDivider -> (holder as DateViewHolder).bind(item, textScale)
            is QmsChatItem.Message -> (holder as MessageViewHolder).bind(item, textScale)
        }
    }

    private class DateViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val label: TextView = itemView as TextView

        fun bind(item: QmsChatItem.DateDivider, textScale: Float) {
            label.text = item.date
            label.textSize = 14f * textScale
        }
    }

    private inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val bubble: LinearLayout = itemView.findViewById(R.id.qms_message_bubble)
        private val body: LinearLayout = itemView.findViewById(R.id.qms_message_body)
        private val meta: LinearLayout = itemView.findViewById(R.id.qms_message_meta)
        private val time: TextView = itemView.findViewById(R.id.qms_message_time)
        private val status: View = itemView.findViewById(R.id.qms_message_status)

        /**
         * Rounded bubble background; the fill is re-tinted per bind (own vs. incoming message).
         * Radius and elevation come from the app-wide card metrics (`CardStyle.Item`), so a bubble is
         * cut from the same cloth as a post card, a favourites plate or a note.
         */
        private val bubbleBg = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = itemView.resources.getDimension(R.dimen.card_corner_radius)
        }

        init {
            bubble.background = bubbleBg
            bubble.clipToOutline = true
            androidx.core.view.ViewCompat.setElevation(
                    bubble,
                    itemView.resources.getDimension(R.dimen.card_elevation),
            )
            // The app's one unread marker (bottom-nav badge → `?attr/notify_dot_tab`), not a private red.
            status.setBackgroundResource(R.drawable.notify_dot)
        }

        fun bind(item: QmsChatItem.Message, textScale: Float) {
            val ctx = itemView.context
            val dm = ctx.resources.displayMetrics
            // The CSS gives the opposite side a 4em gutter so a bubble never spans the full width.
            val gutter = (BUBBLE_GUTTER_DP * dm.density).toInt()
            val edge = (LIST_EDGE_DP * dm.density).toInt()
            val vPad = (4 * dm.density).toInt()
            itemView.setPadding(
                    if (item.isMine) gutter else edge,
                    vPad,
                    if (item.isMine) edge else gutter,
                    vPad,
            )
            // Re-ASSIGN the params, don't just mutate them: a bare `lp.gravity = …` never requests a
            // layout pass, so a recycled row kept its previous alignment and the unread dot stayed at
            // the x it had in the item this holder rendered before (observed: dot drawn to the LEFT of
            // the timestamp on a freshly sent message).
            val side = if (item.isMine) android.view.Gravity.END else android.view.Gravity.START
            bubble.layoutParams = (bubble.layoutParams as LinearLayout.LayoutParams).apply { gravity = side }
            meta.layoutParams = (meta.layoutParams as LinearLayout.LayoutParams).apply { gravity = side }

            val fill = bubbleFill(item.isMine)
            // Contrast rescue for inline colours and links must be measured against the BUBBLE, not the
            // post-card surface the factory assumes by default — an own bubble is accent-tinted.
            blockFactory.readingSurfaceColor = { fill }
            bubbleBg.setColor(fill)
            bubbleBg.setStroke((1f * dm.density).toInt().coerceAtLeast(1), bubbleBorderColor(fill))

            body.removeAllViews()
            blockFactory.render(body, item.blocks, BodyBlockViewFactory.RenderScope(item.id))

            time.text = item.time
            time.textSize = 12f * textScale
            status.visibility = if (item.isUnread) View.VISIBLE else View.GONE

            bubble.setOnLongClickListener {
                listener.onMessageLongClick(bubble, item)
                true
            }
        }

        /**
         * An incoming bubble IS a content card, so it takes the app-wide card fill
         * (`?attr/content_card_surface`) — the very role a post card, a favourites plate, a note and a
         * search result use. That attr is a per-theme redirect (`colorSurface` on light/cream ramps,
         * `colorSurfaceContainerHigh` on dark/AMOLED, where the lower containers collapse to near-black),
         * which is exactly why hand-picking an M3 container role here produced invisible bubbles on some
         * palettes. An own bubble keeps the WebView's `mix(accent, card, 10%)` tint over that same fill.
         */
        private fun bubbleFill(isMine: Boolean): Int {
            val ctx = itemView.context
            val card = ctx.getColorFromAttr(R.attr.content_card_surface)
            if (!isMine) return card
            val accent = ctx.getColorFromAttr(androidx.appcompat.R.attr.colorAccent)
            return androidx.core.graphics.ColorUtils.blendARGB(card, accent, OWN_BUBBLE_ACCENT_RATIO)
        }

        /**
         * The same hairline a post card draws: bare `colorOutline` on a light fill, and on a dark one
         * lifted toward `colorOnSurface` until the edge separates from a near-black card (elevation
         * shadows are invisible there). Keeps bubbles delineated on AMOLED and dynamic dark schemes.
         */
        private fun bubbleBorderColor(fill: Int): Int {
            val ctx = itemView.context
            val outline = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOutline)
            if (androidx.core.graphics.ColorUtils.calculateLuminance(fill) >= 0.5) return outline
            val onSurface = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
            return androidx.core.graphics.ColorUtils.blendARGB(outline, onSurface, DARK_CARD_BORDER_LIFT)
        }
    }

    private companion object {
        const val TYPE_DATE = 0
        const val TYPE_MESSAGE = 1

        /** Free space left on the opposite side of a bubble (CSS: `margin-left/right: 4em`). */
        const val BUBBLE_GUTTER_DP = 56f
        const val LIST_EDGE_DP = 12f
        const val OWN_BUBBLE_ACCENT_RATIO = 0.12f

        /** How far a dark card's outline is lifted toward `colorOnSurface` (post-card parity). */
        const val DARK_CARD_BORDER_LIFT = 0.30f

        val DIFF = object : DiffUtil.ItemCallback<QmsChatItem>() {
            override fun areItemsTheSame(a: QmsChatItem, b: QmsChatItem): Boolean = when {
                a is QmsChatItem.Message && b is QmsChatItem.Message -> a.id == b.id
                a is QmsChatItem.DateDivider && b is QmsChatItem.DateDivider -> a.date == b.date
                else -> false
            }

            override fun areContentsTheSame(a: QmsChatItem, b: QmsChatItem): Boolean = a == b
        }
    }
}
