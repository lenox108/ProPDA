package forpdateam.ru.forpda.ui.fragments.settings

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.ui.chromeCanvasColor
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.ui.dp2
import forpdateam.ru.forpda.ui.activities.SettingsActivity
import forpdateam.ru.forpda.ui.views.dialog.applyCompactWidthAnimated
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons

/**
 * Created by radiationx on 24.09.17.
 */

open class BaseSettingFragment : PreferenceFragmentCompat() {

    companion object {
        // Тег, под которым androidx.preference показывает диалог настройки (константа фреймворка приватна).
        private const val PREF_DIALOG_TAG = "androidx.preference.PreferenceFragment.DIALOG"

        /** Ключ пункта, к которому надо прокрутить и подсветить — приходит из поиска и «Недавно изменённых». */
        const val ARG_HIGHLIGHT_KEY = "highlight_key"

        private const val HIGHLIGHT_DURATION_MS = 2600L

        /** Кнопка «Поддержать автора» — рисуется без плашки, поэтому рвёт группу как заголовок. */
        private const val KEY_SUPPORT_AUTHOR = "about.support_author"

        /**
         * Граница плашки: заголовок категории, край списка или пункт без собственной плашки.
         * Соседство с таким элементом = у плашки здесь скруглённый край.
         */
        private fun isPlateBreak(pref: Preference?): Boolean =
                pref == null || pref is PreferenceCategory || pref.key == KEY_SUPPORT_AUTHOR
    }

    private var listScrollY = 0
    private var lastIsVisible = false
    private var highlightedKey: String? = null

    /** Раздел, который показывает экран (для хрома активити). null — экран вне схемы разделов. */
    open fun searchSection(): SettingsSection? = null

    /**
     * Дополнительный отступ снизу под списком (помимо системной навбар-вставки),
     * чтобы последняя плашка не липла к краю экрана и не срезалась. Экраны могут увеличить.
     */
    protected open val extraBottomPaddingPx: Int
        get() = resources.getDimensionPixelSize(R.dimen.settings_list_bottom_padding)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

    }

    /**
     * Диалоги настроек создаёт фреймворк androidx.preference — через AppCompat AlertDialog.Builder,
     * мимо MaterialAlertDialogBuilder. Такой диалог не получает ни M3-формы (прямые углы), ни наших
     * цветов кнопок/шрифта — он выпадает из общего стандарта остальных диалогов приложения.
     *
     * Поэтому ListPreference (единственный диалоговый тип в наших preferences) показываем сами —
     * тем же путём, что и ручные пикеры: MaterialAlertDialogBuilder + showWithStyledButtons.
     * Значение пишем через callChangeListener → setValue, чтобы OnPreferenceChangeListener'ы
     * экранов (сводки, рестарт темы и пр.) отрабатывали ровно как раньше.
     */
    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is ListPreference && showMaterialListPreferenceDialog(preference)) return

        super.onDisplayPreferenceDialog(preference)
        val fm = parentFragmentManager
        fm.executePendingTransactions()
        val dialogFragment = fm.findFragmentByTag(PREF_DIALOG_TAG)
                as? androidx.fragment.app.DialogFragment
        (dialogFragment?.dialog as? androidx.appcompat.app.AlertDialog)?.let { alert ->
            if (alert.isShowing) alert.applyCompactWidthAnimated()
            else alert.setOnShowListener { alert.applyCompactWidthAnimated() }
        }
    }

    /** @return false, если данных не хватает (пустые entries) — тогда отдаём диалог фреймворку. */
    private fun showMaterialListPreferenceDialog(preference: ListPreference): Boolean {
        val entries = preference.entries ?: return false
        val values = preference.entryValues ?: return false
        if (entries.isEmpty() || entries.size != values.size) return false

        val checked = preference.value?.let { current -> values.indexOfFirst { it == current } } ?: -1

        MaterialAlertDialogBuilder(requireContext())
                .setTitle(preference.dialogTitle ?: preference.title)
                .setSingleChoiceItems(entries, checked) { dialog, which ->
                    val picked = values[which].toString()
                    if (picked != preference.value && preference.callChangeListener(picked)) {
                        preference.value = picked
                    }
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
        return true
    }

    override fun onCreateAdapter(preferenceScreen: PreferenceScreen): RecyclerView.Adapter<*> {
        return object : PreferenceGroupAdapter(preferenceScreen) {
            override fun onBindViewHolder(holder: PreferenceViewHolder, position: Int) {
                super.onBindViewHolder(holder, position)
                val pref = getItem(position)
                val prevPref = if (position > 0) getItem(position - 1) else null
                val nextPref = if (position + 1 < itemCount) getItem(position + 1) else null

                when {
                    pref is PreferenceCategory -> bindCategoryPlate(holder.itemView)
                    pref?.key == KEY_SUPPORT_AUTHOR -> bindSupportAuthorPlate(holder.itemView, prevPref, nextPref)
                    else -> bindPreferencePlate(holder.itemView, prevPref, nextPref)
                }
                if (pref != null && pref.key != null && pref.key == highlightedKey) {
                    holder.itemView.setBackgroundResource(R.drawable.bg_settings_highlight)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Keep Settings background consistent with "Menu"/grouped lists:
        // page = background_base, plates = cards_background (see pref_plate_*.xml).
        view.setBackgroundColor(view.context.chromeCanvasColor(com.google.android.material.R.attr.colorSurfaceContainerLowest))
        view.findViewById<androidx.recyclerview.widget.RecyclerView>(androidx.preference.R.id.recycler_view)?.also { list ->
            // Запас снизу ставим сразу: если окно не отдаёт insets списку (навбар уже съеден
            // декором активити), листенер ниже может не сработать — а воздух под последней
            // плашкой нужен в любом случае, иначе она упирается в край экрана.
            list.setPadding(list.paddingLeft, list.paddingTop, list.paddingRight, extraBottomPaddingPx)
            // Fix: Add padding for navigation bar to prevent bottom items from being covered
            ViewCompat.setOnApplyWindowInsetsListener(list) { v, insets ->
                val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, navBarInsets.bottom + extraBottomPaddingPx)
                insets
            }
            list.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    listScrollY = recyclerView.computeVerticalScrollOffset()
                    updateToolbarShadow()
                }
            })
        }
        updateToolbarShadow()
        setDividerHeight(0)
        consumeHighlightArgument()
    }

    override fun onResume() {
        super.onResume()
        (activity as? SettingsActivity)?.onSettingsScreenChanged()
    }

    /**
     * Пришли из поиска или «Недавно изменённых»: прокручиваем к пункту и ненадолго подсвечиваем,
     * иначе на длинном разделе непонятно, ради чего экран открылся. Аргумент одноразовый —
     * после поворота или возврата назад подсветка не повторяется.
     */
    private fun consumeHighlightArgument() {
        val key = arguments?.getString(ARG_HIGHLIGHT_KEY)?.takeIf { it.isNotBlank() } ?: return
        arguments?.remove(ARG_HIGHLIGHT_KEY)
        if (findPreference<Preference>(key) == null) return
        highlightedKey = key
        val list = view?.findViewById<RecyclerView>(androidx.preference.R.id.recycler_view)
        list?.post {
            if (!isAdded) return@post
            scrollToPreference(key)
            listView.adapter?.notifyDataSetChanged()
            list.postDelayed({
                if (!isAdded) return@postDelayed
                highlightedKey = null
                listView.adapter?.notifyDataSetChanged()
            }, HIGHLIGHT_DURATION_MS)
        }
    }

    private fun bindCategoryPlate(itemView: View) {
        setListItemMargins(itemView, isCategory = true)
        // Category view is just a header. Keep it transparent (no grey bars) and rely on padding/margins.
        itemView.background = null
    }

    private fun bindPreferencePlate(itemView: View, prevPref: Preference?, nextPref: Preference?) {
        val prevIsCategory = isPlateBreak(prevPref)
        val nextIsCategory = isPlateBreak(nextPref)

        // Rounded "plates" grouping (like in the design screenshot).
        itemView.setBackgroundResource(drawableForPrefPlate(prevIsCategory, nextIsCategory))
        setListItemMargins(itemView, isCategory = false, prevIsCategory = prevIsCategory, nextIsCategory = nextIsCategory)

        // No inner dividers inside plates.
        itemView.findViewById<View?>(R.id.prefRowDivider)?.visibility = View.GONE
    }

    private fun bindSupportAuthorPlate(itemView: View, prevPref: Preference?, nextPref: Preference?) {
        val prevIsCategory = isPlateBreak(prevPref)
        val nextIsCategory = isPlateBreak(nextPref)

        itemView.background = null
        setListItemMargins(itemView, isCategory = false, prevIsCategory = prevIsCategory, nextIsCategory = nextIsCategory)
        itemView.findViewById<View?>(R.id.prefRowDivider)?.visibility = View.GONE
    }

    private fun setListItemMargins(itemView: View, isCategory: Boolean, prevIsCategory: Boolean = true, nextIsCategory: Boolean = true) {
        val lp = itemView.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val h = itemView.resources.getDimensionPixelSize(R.dimen.content_padding_horizontal)
        val vBetween = itemView.resources.getDimensionPixelSize(R.dimen.content_spacing_half)
        val vCategoryTop = itemView.resources.getDimensionPixelSize(R.dimen.content_padding_vertical)

        lp.marginStart = h
        lp.marginEnd = h
        if (isCategory) {
            lp.topMargin = vCategoryTop
            lp.bottomMargin = 0
        } else {
            // When preferences are inside the same category, they form a single plate group.
            // Only the first item gets a top gap; only the last item gets a bottom gap.
            lp.topMargin = if (prevIsCategory) vBetween else 0
            lp.bottomMargin = if (nextIsCategory) vBetween else 0
        }
        itemView.layoutParams = lp
    }

    private fun drawableForPrefPlate(prevIsCategory: Boolean, nextIsCategory: Boolean): Int = when {
        prevIsCategory && nextIsCategory -> R.drawable.pref_plate_single
        prevIsCategory && !nextIsCategory -> R.drawable.pref_plate_top
        !prevIsCategory && nextIsCategory -> R.drawable.pref_plate_bottom
        else -> R.drawable.pref_plate_middle
    }

    /**
     * Фильтрация настроек по title/summary.
     * Возвращает true, если в группе есть видимые элементы (используется для категорий).
     */
    fun applySearchQuery(rawQuery: String?) {
        val q = rawQuery?.trim().orEmpty()
        val root = preferenceScreen ?: return
        if (q.isEmpty()) {
            setAllVisible(root, true)
            return
        }
        filterGroup(root, q.lowercase())
    }

    private fun setAllVisible(group: PreferenceGroup, visible: Boolean) {
        for (i in 0 until group.preferenceCount) {
            val p = group.getPreference(i)
            p.isVisible = visible
            if (p is PreferenceGroup) {
                setAllVisible(p, visible)
            }
        }
    }

    private fun matches(pref: Preference, q: String): Boolean {
        val t = pref.title?.toString()?.lowercase().orEmpty()
        val s = pref.summary?.toString()?.lowercase().orEmpty()
        val k = pref.key?.lowercase().orEmpty()

        // "Умный" поиск: поддержка нескольких слов и ключевых слов/синонимов.
        val tokens = q.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return true

        val extra = buildKeywordHints(k)
        val haystack = buildString {
            append(t).append('\n')
            append(s).append('\n')
            append(k).append('\n')
            for (e in extra) append(e).append('\n')
        }
        return tokens.all { token -> haystack.contains(token) }
    }

    // Синонимы («клава», «пуш», «аватарки») общие с индексом сквозного поиска — см. SettingsSearchIndex.
    private fun buildKeywordHints(key: String): List<String> = SettingsSearchIndex.keywordHints(key)

    private fun filterGroup(group: PreferenceGroup, q: String): Boolean {
        var anyVisible = false
        for (i in 0 until group.preferenceCount) {
            val p = group.getPreference(i)
            val visible = if (p is PreferenceGroup) {
                val childVisible = filterGroup(p, q)
                // Группа видима, если совпала сама или кто-то из детей
                matches(p, q) || childVisible
            } else {
                matches(p, q)
            }
            p.isVisible = visible
            anyVisible = anyVisible || visible
        }
        return anyVisible
    }

    private fun updateToolbarShadow() {
        val isVisible = listScrollY > 0
        if (lastIsVisible != isVisible) {
            (activity as? SettingsActivity)?.supportActionBar?.elevation = if (isVisible) dp2.toFloat() else 0f
            lastIsVisible = isVisible
        }
    }
}
