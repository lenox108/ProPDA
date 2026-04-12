package forpdateam.ru.forpda.ui.fragments.settings

import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.ui.activities.SettingsActivity

/**
 * Created by radiationx on 24.09.17.
 */

open class BaseSettingFragment : PreferenceFragmentCompat() {

    private var listScrollY = 0
    private var lastIsVisible = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<androidx.recyclerview.widget.RecyclerView>(androidx.preference.R.id.recycler_view)?.also { list ->
            list.setPadding(0, 0, 0, 0)
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
            (activity as? SettingsActivity)?.supportActionBar?.elevation = if (isVisible) App.px2.toFloat() else 0f
            lastIsVisible = isVisible
        }
    }
}
