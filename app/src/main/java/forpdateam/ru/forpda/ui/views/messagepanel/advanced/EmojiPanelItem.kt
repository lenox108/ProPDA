package forpdateam.ru.forpda.ui.views.messagepanel.advanced

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.databinding.MessagePanelEmojiHeaderBinding
import forpdateam.ru.forpda.databinding.MessagePanelEmojiItemBinding
import forpdateam.ru.forpda.ui.views.messagepanel.MessagePanel

/**
 * Вкладка обычных Unicode-эмодзи рядом со «Смайлами».
 *
 * Смайлы 4PDA — это шорткоды (`:wallbash:`), их набор задаёт форум. Эмодзи же уходят в пост как
 * символы: непредставимые в windows-1251 кодируются в `&#NNNN;` (см. `encodeEditPostBodyForSubmit`),
 * ровно как это делает браузер, поэтому их видят и остальные пользователи форума.
 *
 * Рисуются системным шрифтом; на старых Android недостающие глифы подтягивает emoji2 (транзитивно
 * из appcompat), поэтому набор ограничен эмодзи, которые он покрывает.
 */
@SuppressLint("ViewConstructor")
class EmojiPanelItem(context: Context, panel: MessagePanel) :
    BasePanelItem(context, panel, context.getString(R.string.emoji_title)) {

    init {
        val rows = buildRows(context)
        val manager = recyclerView.getManager()
        manager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int =
                if (rows[position] is Row.Header) manager.spanCount else 1
        }
        recyclerView.adapter = EmojiAdapter(rows) { emoji -> messagePanel.insertText(emoji) }
    }

    private sealed class Row {
        class Header(val title: String) : Row()
        class Emoji(val value: String) : Row()
    }

    private class EmojiAdapter(
        private val rows: List<Row>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemCount(): Int = rows.size

        override fun getItemViewType(position: Int): Int =
            if (rows[position] is Row.Header) TYPE_HEADER else TYPE_EMOJI

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_HEADER) {
                HeaderHolder(MessagePanelEmojiHeaderBinding.inflate(inflater, parent, false))
            } else {
                EmojiHolder(MessagePanelEmojiItemBinding.inflate(inflater, parent, false), onClick)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is Row.Header -> (holder as HeaderHolder).binding.headerTitle.text = row.title
                is Row.Emoji -> (holder as EmojiHolder).bind(row.value)
            }
        }

        private class HeaderHolder(val binding: MessagePanelEmojiHeaderBinding) :
            RecyclerView.ViewHolder(binding.root)

        private class EmojiHolder(
            private val binding: MessagePanelEmojiItemBinding,
            onClick: (String) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {
            private var emoji: String = ""

            init {
                binding.root.setOnClickListener { onClick(emoji) }
            }

            fun bind(value: String) {
                emoji = value
                binding.itemEmoji.text = value
                binding.root.contentDescription = value
            }
        }

        companion object {
            const val TYPE_HEADER = 0
            const val TYPE_EMOJI = 1
        }
    }

    private companion object {

        fun buildRows(context: Context): List<Row> {
            val rows = ArrayList<Row>()
            for ((titleRes, emojis) in categories) {
                rows.add(Row.Header(context.getString(titleRes)))
                emojis.forEach { rows.add(Row.Emoji(it)) }
            }
            return rows
        }

        /** Пары «заголовок → эмодзи». Каждая строка разбивается по пробелу. */
        val categories: List<Pair<Int, List<String>>> = listOf(
            R.string.emoji_cat_smileys to split(
                "😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😙 " +
                    "😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔 🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 🤥 " +
                    "😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🤧 🥵 🥶 🥴 😵 🤯 🤠 🥳 😎 🤓 " +
                    "🧐 😕 😟 🙁 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 " +
                    "😓 😩 😫 😤 😡 😠 🤬 😈 👿 💀 💩 🤡 👹 👺 👻 👽 🤖"
            ),
            R.string.emoji_cat_gestures to split(
                "👍 👎 👌 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ ✋ 🤚 🖐️ 🖖 👋 🤝 🙏 💪 ✍️ 🤳"
            ),
            R.string.emoji_cat_hearts to split(
                "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝"
            ),
            R.string.emoji_cat_animals to split(
                "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🦆 🦉 " +
                    "🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🐢 🐍 🐙 🦀 🐠 🐟 🐬 🐳 🦈"
            ),
            R.string.emoji_cat_nature to split(
                "🔥 ⭐ 🌟 ✨ ⚡ ☀️ 🌤️ ☁️ 🌧️ ⛈️ ❄️ ☃️ 🌊 💧 🌈 🌙 🌍 🍀 🌸 🌹 🌻 🌵 🌲 🍁"
            ),
            R.string.emoji_cat_food to split(
                "🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍒 🍑 🥭 🍍 🥝 🍅 🥑 🍆 🥕 🌽 🌶️ " +
                    "🥒 🥦 🍞 🧀 🍗 🍖 🍟 🍕 🌭 🍔 🍿 🥓 🥚 🍳 🥞 🍝 🍜 🍣 🍱 🍛 " +
                    "🍚 🍦 🍰 🎂 🍫 🍬 🍭 🍩 🍪 ☕ 🍵 🍺 🍻 🥂 🍷 🥃 🍸"
            ),
            R.string.emoji_cat_activity to split(
                "⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🎱 🏓 🏸 🥊 🎯 🎮 🕹️ 🎲 🎸 🎧 🎤 🎬 🏆 🥇 🥈 🥉"
            ),
            R.string.emoji_cat_objects to split(
                "📱 💻 ⌨️ 🖥️ 🖨️ 🖱️ 💾 💿 📷 📹 🎥 📺 📻 ⏰ ⌛ ⏳ 🔋 🔌 💡 🔦 " +
                    "🕯️ 🧯 🛒 💰 💳 💎 🔧 🔨 🛠️ ⚙️ 🔒 🔓 🔑 🚪 🛏️ 🚿 📦 📎 📌 ✂️"
            ),
            R.string.emoji_cat_symbols to split(
                "✅ ❌ ❓ ❗ ⚠️ 🚫 💯 🔔 🔕 ➕ ➖ ✖️ ➗ 🔁 🔂 ▶️ ⏸️ ⏹️ ⏺️ ⏭️ " +
                    "⏮️ 🔀 🔄 🆗 🆕 🆓 🔝 🔜 ©️ ®️ ™️"
            ),
        )

        fun split(line: String): List<String> = line.split(' ').filter { it.isNotEmpty() }
    }
}
