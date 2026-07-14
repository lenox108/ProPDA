package forpdateam.ru.forpda.ui.fragments.other

import android.content.Context
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.app.history.HistoryItem
import forpdateam.ru.forpda.entity.app.other.MenuShortcut
import forpdateam.ru.forpda.entity.remote.search.SearchSettings

/**
 * Диалоги закрепления плиток. Все источники сводятся к паре «название + ссылка 4PDA»:
 * открывает такую плитку LinkHandler, поэтому тема, раздел, диалог QMS, поиск и профиль
 * не требуют отдельных веток роутинга.
 */
object MenuShortcutDialogs {

    fun showSourceChooser(
            context: Context,
            onHistory: () -> Unit,
            onSearch: () -> Unit,
            onLink: () -> Unit
    ) {
        val titles = arrayOf(
                context.getString(R.string.other_menu_add_source_history),
                context.getString(R.string.other_menu_add_source_search),
                context.getString(R.string.other_menu_add_source_link)
        )
        MaterialAlertDialogBuilder(context)
                .setTitle(R.string.other_menu_add_title)
                .setItems(titles) { _, which ->
                    when (which) {
                        0 -> onHistory()
                        1 -> onSearch()
                        else -> onLink()
                    }
                }
                .show()
    }

    fun showHistoryPicker(
            context: Context,
            items: List<HistoryItem>,
            onPicked: (title: String, url: String) -> Unit
    ) {
        val usable = items.filter { !it.url.isNullOrBlank() && !it.title.isNullOrBlank() }
        if (usable.isEmpty()) {
            MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.other_menu_add_history_title)
                    .setMessage(R.string.other_menu_add_history_empty)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            return
        }
        val titles = usable.map { it.title.orEmpty() }.toTypedArray()
        MaterialAlertDialogBuilder(context)
                .setTitle(R.string.other_menu_add_history_title)
                .setItems(titles) { _, which ->
                    val item = usable[which]
                    onPicked(item.title.orEmpty(), item.url.orEmpty())
                }
                .show()
    }

    fun showSearchQueryInput(
            context: Context,
            onEntered: (title: String, url: String) -> Unit
    ) {
        val input = editText(context, R.string.other_menu_add_search_hint)
        MaterialAlertDialogBuilder(context)
                .setTitle(R.string.other_menu_add_search_title)
                .setView(wrap(context, input))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val query = input.text.toString().trim()
                    if (query.isEmpty()) return@setPositiveButton
                    val url = SearchSettings().apply {
                        this.query = query
                        result = SearchSettings.RESULT_TOPICS.first
                    }.toUrl()
                    onEntered(query, url)
                }
                .show()
    }

    fun showLinkInput(
            context: Context,
            onEntered: (title: String, url: String) -> Unit,
            onInvalid: () -> Unit
    ) {
        val nameInput = editText(context, R.string.other_menu_add_link_name_hint)
        val urlInput = editText(context, R.string.other_menu_add_link_url_hint).apply {
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        val container = FrameLayout(context).apply {
            val pad = resources.getDimensionPixelSize(R.dimen.content_padding_horizontal)
            setPadding(pad, pad / 2, pad, 0)
            addView(android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                addView(nameInput, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                addView(urlInput, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            })
        }
        MaterialAlertDialogBuilder(context)
                .setTitle(R.string.other_menu_add_link_title)
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val url = urlInput.text.toString().trim()
                    if (!isFourPdaUrl(url)) {
                        onInvalid()
                        return@setPositiveButton
                    }
                    val title = nameInput.text.toString().trim().ifEmpty { url }
                    onEntered(title, url)
                }
                .show()
    }

    /** Тип ярлыка по ссылке — нужен только для иконки плитки. */
    fun typeOf(url: String): MenuShortcut.Type {
        val lower = url.lowercase()
        return when {
            lower.contains("act=qms") -> MenuShortcut.Type.DIALOG
            lower.contains("act=search") -> MenuShortcut.Type.SEARCH
            lower.contains("showuser=") -> MenuShortcut.Type.PROFILE
            lower.contains("showtopic=") || lower.contains("showpost=") -> MenuShortcut.Type.TOPIC
            lower.contains("showforum=") -> MenuShortcut.Type.FORUM
            else -> MenuShortcut.Type.LINK
        }
    }

    private fun isFourPdaUrl(url: String): Boolean =
            url.startsWith("http", ignoreCase = true) &&
                    (url.contains("4pda.to", ignoreCase = true) || url.contains("4pda.ru", ignoreCase = true))

    private fun editText(context: Context, hintRes: Int): EditText = EditText(context).apply {
        setHint(hintRes)
        maxLines = 2
    }

    private fun wrap(context: Context, editText: EditText): FrameLayout = FrameLayout(context).apply {
        val pad = resources.getDimensionPixelSize(R.dimen.content_padding_horizontal)
        setPadding(pad, pad / 2, pad, 0)
        addView(editText, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
