package forpdateam.ru.forpda.ui.fragments.search

import android.app.SearchManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.LinearLayout
import androidx.appcompat.R as AppCompatR
import androidx.appcompat.widget.SearchView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.entity.remote.search.SearchSettings
import forpdateam.ru.forpda.presentation.search.SearchViewModel
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons

/**
 * Убирает непрозрачную подложку у AppCompat SearchView и выравнивает цвета с верхней плашкой
 * и `background_base`, чтобы скругления плашки не перекрывались прямоугольником поля поиска.
 */
fun SearchView.applyToolbarSearchPlateChrome() {
    val ctx = context
    val transparent = Color.TRANSPARENT
    fun clearBackground(id: Int) {
        findViewById<View>(id)?.apply {
            background = null
            setBackgroundColor(transparent)
        }
    }
    clearBackground(AppCompatR.id.search_bar)
    clearBackground(AppCompatR.id.search_edit_frame)
    clearBackground(AppCompatR.id.search_plate)

    val iconTint = ColorStateList.valueOf(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
    findViewById<ImageView>(AppCompatR.id.search_mag_icon)?.imageTintList = iconTint
    findViewById<ImageView>(AppCompatR.id.search_close_btn)?.imageTintList = iconTint

    (findViewById<View>(AppCompatR.id.search_src_text) as? TextView)?.apply {
        setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
        setHintTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
    }
}

/**
 * Контроллер для управления логикой поиска в SearchFragment.
 * Отвечает за настройку SearchView, обработку запросов и управление настройками поиска.
 */
class SearchQueryHelper(
        private val context: Context,
        private val onSearchSubmit: (String, String) -> Unit,
        private val onSettingsUpdate: (String, Int) -> Unit,
        private val onSettingsSave: () -> Unit
) {

    /**
     * Флаг инициализации: пока true, события спиннеров игнорируются,
     * чтобы не перезаписывать настройки при programmatic setSelection.
     */
    private var isInitializing = false

    /**
     * Настраивает SearchView для обработки поисковых запросов.
     */
    fun setupSearchView(searchView: SearchView, searchManager: SearchManager, componentName: android.content.ComponentName?) {
        searchView.setSearchableInfo(searchManager.getSearchableInfo(componentName))
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                // Callback handled by caller
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                return false
            }
        })
        searchView.queryHint = context.getString(R.string.search_keywords)
        searchView.maxWidth = Int.MAX_VALUE
        searchView.applyToolbarSearchPlateChrome()

        val searchEditFrame = searchView.findViewById<LinearLayout>(R.id.search_edit_frame) ?: return
        val params = searchEditFrame.layoutParams as? LinearLayout.LayoutParams ?: return
        params.leftMargin = 0

        val searchSrcText = searchView.findViewById<View>(R.id.search_src_text) ?: return
        searchSrcText.setPadding(0, searchSrcText.paddingTop, 0, searchSrcText.paddingBottom)
    }

    /**
     * Настраивает слушатель для кнопки отправки поиска.
     */
    fun setupSubmitButton(submitButton: View, searchView: SearchView, nickField: android.widget.TextView) {
        submitButton.setOnClickListener {
            val query = searchView.query.toString()
            val nick = nickField.text.toString()
            onSearchSubmit(query, nick)
        }
    }

    /**
     * Настраивает слушатель для кнопки сохранения настроек.
     */
    fun setupSaveButton(saveButton: View) {
        saveButton.setOnClickListener {
            onSettingsSave()
        }
    }

    /**
     * Проверяет, соответствует ли аргумент паре ключ-значение.
     */
    fun checkArg(arg: String, pair: Pair<String, String>): Boolean {
        return arg == pair.first
    }

    private data class DropdownField(
            val textInputLayout: TextInputLayout,
            val fieldKey: String,
            var items: List<String> = emptyList()
    )

    /**
     * Настраивает поля выбора через AlertDialog (Spinner/AutoComplete не работают в BottomSheet).
     */
    fun setupSpinners(
            resourceLayout: TextInputLayout,
            resultLayout: TextInputLayout,
            sortLayout: TextInputLayout,
            sourceLayout: TextInputLayout,
            fieldsMap: Map<String, List<String>>
    ) {
        val fields = listOf(
                DropdownField(resourceLayout, SearchViewModel.FIELD_RESOURCE, fieldsMap[SearchViewModel.FIELD_RESOURCE] ?: emptyList()),
                DropdownField(resultLayout, SearchViewModel.FIELD_RESULT, fieldsMap[SearchViewModel.FIELD_RESULT] ?: emptyList()),
                DropdownField(sortLayout, SearchViewModel.FIELD_SORT, fieldsMap[SearchViewModel.FIELD_SORT] ?: emptyList()),
                DropdownField(sourceLayout, SearchViewModel.FIELD_SOURCE, fieldsMap[SearchViewModel.FIELD_SOURCE] ?: emptyList())
        )

        fields.forEach { dropdown ->
            val showDialog = View.OnClickListener {
                if (isInitializing) return@OnClickListener
                if (dropdown.items.isEmpty()) return@OnClickListener

                val editText = dropdown.textInputLayout.editText as? TextInputEditText
                val currentText = editText?.text?.toString() ?: ""
                val selectedIndex = dropdown.items.indexOf(currentText).coerceAtLeast(0)

                MaterialAlertDialogBuilder(context)
                        .setSingleChoiceItems(
                                dropdown.items.toTypedArray(),
                                selectedIndex
                        ) { dialog, which ->
                            editText?.setText(dropdown.items[which])
                            onSettingsUpdate(dropdown.fieldKey, which)
                            dialog.dismiss()
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .showWithStyledButtons()
            }
            dropdown.textInputLayout.setOnClickListener(showDialog)
            dropdown.textInputLayout.editText?.setOnClickListener(showDialog)
            dropdown.textInputLayout.setEndIconOnClickListener(showDialog)
        }
    }

    /**
     * Устанавливает текст в поле выбора по значению пары ключ-значение.
     */
    fun setSelection(layout: TextInputLayout, items: List<String>, pair: Pair<String, String>) {
        val index = items.indexOf(pair.second)
        if (index >= 0) {
            (layout.editText as? TextInputEditText)?.setText(items[index])
        }
    }

    /**
     * Заполняет данные настроек поиска.
     */
    fun fillSettingsData(
            searchView: SearchView,
            nickField: android.widget.TextView,
            resourceLayout: TextInputLayout,
            resultLayout: TextInputLayout,
            sortLayout: TextInputLayout,
            sourceLayout: TextInputLayout,
            settings: SearchSettings,
            fields: Map<String, List<String>>
    ) {
        isInitializing = true
        searchView.post { searchView.setQuery(settings.query, false) }
        nickField.text = settings.nick

        val resourceItems = fields[SearchViewModel.FIELD_RESOURCE] ?: emptyList()
        val resultItems = fields[SearchViewModel.FIELD_RESULT] ?: emptyList()
        val sortItems = fields[SearchViewModel.FIELD_SORT] ?: emptyList()
        val sourceItems = fields[SearchViewModel.FIELD_SOURCE] ?: emptyList()

        (resourceLayout.editText as? TextInputEditText)?.setText(resourceItems.firstOrNull() ?: "")
        (resultLayout.editText as? TextInputEditText)?.setText(resultItems.firstOrNull() ?: "")
        (sortLayout.editText as? TextInputEditText)?.setText(sortItems.firstOrNull() ?: "")
        (sourceLayout.editText as? TextInputEditText)?.setText(sourceItems.firstOrNull() ?: "")

        when {
            checkArg(settings.resourceType, SearchSettings.RESOURCE_NEWS) -> {
                setSelection(resourceLayout, resourceItems, SearchSettings.RESOURCE_NEWS)
            }
            checkArg(settings.resourceType, SearchSettings.RESOURCE_FORUM) -> {
                setSelection(resourceLayout, resourceItems, SearchSettings.RESOURCE_FORUM)
            }
        }

        when {
            checkArg(settings.result, SearchSettings.RESULT_TOPICS) -> {
                setSelection(resultLayout, resultItems, SearchSettings.RESULT_TOPICS)
            }
            checkArg(settings.result, SearchSettings.RESULT_POSTS) -> {
                setSelection(resultLayout, resultItems, SearchSettings.RESULT_POSTS)
            }
        }

        when {
            checkArg(settings.sort, SearchSettings.SORT_DA) -> {
                setSelection(sortLayout, sortItems, SearchSettings.SORT_DA)
            }
            checkArg(settings.sort, SearchSettings.SORT_DD) -> {
                setSelection(sortLayout, sortItems, SearchSettings.SORT_DD)
            }
            checkArg(settings.sort, SearchSettings.SORT_REL) -> {
                setSelection(sortLayout, sortItems, SearchSettings.SORT_REL)
            }
        }

        when {
            checkArg(settings.source, SearchSettings.SOURCE_ALL) -> {
                setSelection(sourceLayout, sourceItems, SearchSettings.SOURCE_ALL)
            }
            checkArg(settings.source, SearchSettings.SOURCE_TITLES) -> {
                setSelection(sourceLayout, sourceItems, SearchSettings.SOURCE_TITLES)
            }
            checkArg(settings.source, SearchSettings.SOURCE_CONTENT) -> {
                setSelection(sourceLayout, sourceItems, SearchSettings.SOURCE_CONTENT)
            }
        }
        isInitializing = false
    }

    /**
     * Устанавливает режим новостей (скрывает ненужные поля).
     */
    fun setNewsMode(nickBlock: View, resultBlock: View, sortBlock: View, sourceBlock: View) {
        nickBlock.visibility = View.GONE
        resultBlock.visibility = View.GONE
        sortBlock.visibility = View.GONE
        sourceBlock.visibility = View.GONE
    }

    /**
     * Устанавливает режим форума (показывает все поля).
     */
    fun setForumMode(nickBlock: View, resultBlock: View, sortBlock: View, sourceBlock: View) {
        nickBlock.visibility = View.VISIBLE
        resultBlock.visibility = View.VISIBLE
        sortBlock.visibility = View.VISIBLE
        sourceBlock.visibility = View.VISIBLE
    }

    /**
     * Формирует заголовок поиска на основе настроек.
     */
    fun buildSearchTitle(settings: SearchSettings, scopedForumTitle: String? = null): String {
        val titleBuilder = StringBuilder()
        titleBuilder.append("Поиск")
        if (settings.resourceType == SearchSettings.RESOURCE_NEWS.first) {
            titleBuilder.append(" новостей")
        } else {
            if (settings.result == SearchSettings.RESULT_POSTS.first) {
                titleBuilder.append(" сообщений")
            } else {
                titleBuilder.append(" тем")
            }
            if (!settings.nick.isNullOrEmpty()) {
                titleBuilder.append(" пользователя \"").append(settings.nick).append("\"")
            }
            if (settings.topics.isEmpty() && settings.forums.isNotEmpty()) {
                val forumLabel = scopedForumTitle?.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.search_scoped_forum_fallback)
                titleBuilder.append(" в разделе «").append(forumLabel).append('»')
            }
        }
        if (!settings.query.isEmpty()) {
            titleBuilder.append(" по запросу \"").append(settings.query).append("\"")
        }
        return titleBuilder.toString()
    }
}
