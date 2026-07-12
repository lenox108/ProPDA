package forpdateam.ru.forpda.ui.fragments.settings

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.ui.dp2
import forpdateam.ru.forpda.ui.activities.SettingsActivity
import forpdateam.ru.forpda.ui.views.dialog.shrinkWidthToContent

/**
 * Created by radiationx on 24.09.17.
 */

open class BaseSettingFragment : PreferenceFragmentCompat() {

    private companion object {
        // Тег, под которым androidx.preference показывает диалог настройки (константа фреймворка приватна).
        const val PREF_DIALOG_TAG = "androidx.preference.PreferenceFragment.DIALOG"
    }

    private var listScrollY = 0
    private var lastIsVisible = false

    /**
     * Дополнительный отступ снизу под списком (помимо системной навбар-вставки).
     * По умолчанию 0; экраны переопределяют, если нужен запас под последней плашкой.
     */
    protected open val extraBottomPaddingPx: Int
        get() = 0

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

    }

    /**
     * Диалоги настроек (ListPreference и пр.) создаёт фреймворк androidx.preference — они идут мимо
     * нашего showWithStyledButtons, поэтому раздувались на ~весь экран. Ужимаем их ширину тем же
     * механизмом (замер контента → фиксированная ширина окна), что и ручные диалоги.
     */
    override fun onDisplayPreferenceDialog(preference: Preference) {
        super.onDisplayPreferenceDialog(preference)
        val fm = parentFragmentManager
        fm.executePendingTransactions()
        val dialogFragment = fm.findFragmentByTag(PREF_DIALOG_TAG)
                as? androidx.fragment.app.DialogFragment
        (dialogFragment?.dialog as? androidx.appcompat.app.AlertDialog)?.let { alert ->
            if (alert.isShowing) alert.shrinkWidthToContent()
            else alert.setOnShowListener { alert.shrinkWidthToContent() }
        }
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
                    pref?.key == "about.support_author" -> bindSupportAuthorPlate(holder.itemView, prevPref, nextPref)
                    else -> bindPreferencePlate(holder.itemView, prevPref, nextPref)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Keep Settings background consistent with "Menu"/grouped lists:
        // page = background_base, plates = cards_background (see pref_plate_*.xml).
        view.setBackgroundColor(view.context.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerLowest))
        view.findViewById<androidx.recyclerview.widget.RecyclerView>(androidx.preference.R.id.recycler_view)?.also { list ->
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
    }

    private fun bindCategoryPlate(itemView: View) {
        setListItemMargins(itemView, isCategory = true)
        // Category view is just a header. Keep it transparent (no grey bars) and rely on padding/margins.
        itemView.background = null
    }

    private fun bindPreferencePlate(itemView: View, prevPref: Preference?, nextPref: Preference?) {
        val prevIsCategory = prevPref == null || prevPref is PreferenceCategory
        val nextIsCategory = nextPref == null || nextPref is PreferenceCategory

        // Rounded "plates" grouping (like in the design screenshot).
        itemView.setBackgroundResource(drawableForPrefPlate(prevIsCategory, nextIsCategory))
        setListItemMargins(itemView, isCategory = false, prevIsCategory = prevIsCategory, nextIsCategory = nextIsCategory)

        // No inner dividers inside plates.
        itemView.findViewById<View?>(R.id.prefRowDivider)?.visibility = View.GONE
    }

    private fun bindSupportAuthorPlate(itemView: View, prevPref: Preference?, nextPref: Preference?) {
        val prevIsCategory = prevPref == null || prevPref is PreferenceCategory
        val nextIsCategory = nextPref == null || nextPref is PreferenceCategory

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

    private fun buildKeywordHints(key: String): List<String> {
        if (key.isBlank()) return emptyList()
        val out = ArrayList<String>(8)
        fun addAll(vararg v: String) = v.forEach { out.add(it) }

        // Редактор/клавиатура/вложения/BBCode/смайлы
        if (key.contains("message") || key.contains("editor") || key.contains("panel")) {
            addAll("редактор", "клава", "клавиатура", "bbcode", "смайлы", "emoji", "вложения", "attachments")
        }
        // Уведомления/аватары
        if (key.contains("notif") || key.contains("notification")) {
            addAll("уведомления", "notify", "push")
        }
        if (key.contains("avatar") || key.contains("image") || key.contains("coil")) {
            addAll("аватар", "аватарки", "картинки", "изображения")
        }
        // Тема/шрифт/размер
        if (key.contains("theme") || key.contains("font") || key.contains("text") || key.contains("size")) {
            addAll("тема", "оформление", "шрифт", "размер текста")
        }
        if (key.contains("palette") || key.contains("ui.")) {
            addAll("палитра", "цвета", "4pda", "классика", "ios", "системный", "accent")
        }
        // Сеть
        if (key.contains("network") || key.contains("http") || key.contains("timeout")) {
            addAll("сеть", "интернет", "таймаут", "повторы", "retry")
        }
        if (key.contains("bottom_nav") || key.contains("menu_sequence")) {
            addAll("нижнее меню", "панель", "вкладки", "новости", "избранное", "порядок", "таб", "bottom", "nav", "tab bar")
        }
        return out
    }

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
