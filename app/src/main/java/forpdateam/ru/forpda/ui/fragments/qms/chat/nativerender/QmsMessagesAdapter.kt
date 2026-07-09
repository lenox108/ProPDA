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

        /** Long-press on a bubble → the message actions menu (copy). */
        fun onMessageLongClick(anchor: View, item: QmsChatItem.Message)
    }

    /** Spoiler expand state, keyed "messageId:spoilerIndex", surviving view recycling. */
    private val spoilerStates = HashMap<String, Boolean>()

    private val blockFactory = BodyBlockViewFactory(
            linkHandler,
            spoilerStates,
            object : BodyBlockViewFactory.Callbacks {
                override fun onImageClick(galleryUrls: List<String>, index: Int) =
                        listener.onImageClick(galleryUrls, index)
            },
    )

    /** Body text scale, mirroring the app font-size preference (1.0 = the reference 16-px body). */
    var textScale: Float = 1f
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

        /** Rounded bubble background; the fill is re-tinted per bind (own vs. incoming message). */
        private val bubbleBg = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = BUBBLE_CORNER_DP * itemView.resources.displayMetrics.density
        }

        /** Red unread dot, WebView parity with `.mess_container.unread .status`. */
        private val statusBg = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(UNREAD_DOT_COLOR)
        }

        init {
            bubble.background = bubbleBg
            bubble.clipToOutline = true
            status.background = statusBg
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
            val side = if (item.isMine) android.view.Gravity.END else android.view.Gravity.START
            (bubble.layoutParams as LinearLayout.LayoutParams).gravity = side
            (meta.layoutParams as LinearLayout.LayoutParams).gravity = side

            bubbleBg.setColor(bubbleFill(item.isMine))
            bubbleBg.setStroke(
                    (1f * dm.density).toInt().coerceAtLeast(1),
                    ctx.getColorFromAttr(com.google.android.material.R.attr.colorOutlineVariant),
            )

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
         * Incoming bubbles must lift off the list background, and own bubbles must read apart from
         * incoming ones — on all ten palettes.
         *
         * The M3 container roles can't be trusted for the first job: several of the app's palettes
         * resolve `colorSurfaceContainerHigh` to the very same value as the page tint
         * (`colorSurfaceContainerLowest`, see [TabFragment.setListsBackground]), which left every
         * incoming bubble invisible but for its hairline. So we take the role colour when it is
         * genuinely distinct and otherwise synthesise one tonal step off the page. Own bubbles then
         * add the WebView's `mix(accent, card, 10%)` tint on top of that fill.
         */
        private fun bubbleFill(isMine: Boolean): Int {
            val ctx = itemView.context
            val page = ctx.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerLowest)
            val role = ctx.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerHigh)
            val base = if (isDistinctFrom(role, page)) {
                role
            } else {
                val onSurface = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
                androidx.core.graphics.ColorUtils.blendARGB(page, onSurface, SYNTHETIC_BUBBLE_STEP)
            }
            if (!isMine) return base
            val accent = ctx.getColorFromAttr(androidx.appcompat.R.attr.colorAccent)
            return androidx.core.graphics.ColorUtils.blendARGB(base, accent, OWN_BUBBLE_ACCENT_RATIO)
        }

        /** Perceptible fill difference — below this the two surfaces look like one flat sheet. */
        private fun isDistinctFrom(color: Int, other: Int): Boolean = kotlin.math.abs(
                androidx.core.graphics.ColorUtils.calculateLuminance(color) -
                        androidx.core.graphics.ColorUtils.calculateLuminance(other),
        ) >= MIN_BUBBLE_LUMINANCE_DELTA
    }

    private companion object {
        const val TYPE_DATE = 0
        const val TYPE_MESSAGE = 1
        const val BUBBLE_CORNER_DP = 16f

        /** Free space left on the opposite side of a bubble (CSS: `margin-left/right: 4em`). */
        const val BUBBLE_GUTTER_DP = 56f
        const val LIST_EDGE_DP = 12f
        const val OWN_BUBBLE_ACCENT_RATIO = 0.12f

        /** Tonal step used when the palette's container role collapses onto the page tint. */
        const val SYNTHETIC_BUBBLE_STEP = 0.07f
        const val MIN_BUBBLE_LUMINANCE_DELTA = 0.015

        val UNREAD_DOT_COLOR = android.graphics.Color.parseColor("#EF5350")

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
