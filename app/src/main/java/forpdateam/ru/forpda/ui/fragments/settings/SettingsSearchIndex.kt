package forpdateam.ru.forpda.ui.fragments.settings

import android.content.Context
import android.content.res.XmlResourceParser
import org.xmlpull.v1.XmlPullParser

/**
 * Сквозной индекс настроек: собирает пункты сразу из всех preferences-xml, чтобы поиск на
 * корневом экране находил настройку в любом разделе, а не только на открытом.
 *
 * Индекс строится разбором xml (без инфляции Preference-объектов): это дёшево, не требует
 * PreferenceManager и не зависит от того, какой экран сейчас показан.
 */
object SettingsSearchIndex {

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val TAG_CATEGORY = "PreferenceCategory"
    private const val TAG_SCREEN = "PreferenceScreen"

    /** Строки-навигаторы («перейти в раздел») не настройки — в выдаче им не место. */
    private val SKIPPED_KEY_PREFIXES = listOf("settings.section.", "settings.category.")
    private val SKIPPED_KEYS = setOf(
            "about.support_author",
            "open_notifications",
            "open_proxy_settings",
    )

    data class Entry(
            val key: String,
            val title: String,
            val summary: String?,
            val section: SettingsSection,
            val group: String?,
            val isSwitch: Boolean,
            val defaultBoolean: Boolean,
    ) {
        /** «Внешний вид › Текст» — путь до пункта, показываем вместо summary в выдаче. */
        fun breadcrumb(context: Context): String {
            val sectionTitle = context.getString(section.titleRes)
            return if (group.isNullOrBlank() || group == sectionTitle) sectionTitle
            else "$sectionTitle › $group"
        }
    }

    @Volatile
    private var cache: List<Entry>? = null

    fun entries(context: Context): List<Entry> = cache ?: synchronized(this) {
        cache ?: buildIndex(context).also { cache = it }
    }

    /** Сбрасывается при смене языка/темы: заголовки в индексе — уже готовые строки. */
    fun invalidate() {
        cache = null
    }

    fun find(context: Context, key: String): Entry? = entries(context).firstOrNull { it.key == key }

    fun search(context: Context, rawQuery: String?): List<Entry> {
        val tokens = rawQuery?.trim()?.lowercase()?.split(Regex("\\s+"))?.filter { it.isNotBlank() }.orEmpty()
        if (tokens.isEmpty()) return emptyList()
        return entries(context).filter { entry -> tokens.all { token -> haystack(entry).contains(token) } }
    }

    private fun haystack(entry: Entry): String = buildString {
        append(entry.title.lowercase()).append('\n')
        append(entry.summary?.lowercase().orEmpty()).append('\n')
        append(entry.group?.lowercase().orEmpty()).append('\n')
        append(entry.key.lowercase()).append('\n')
        for (hint in keywordHints(entry.key)) append(hint).append('\n')
    }

    /**
     * Синонимы: пользователь ищет «клава», «пуш», «аватарки» — слов, которых нет ни в заголовке,
     * ни в описании. Раньше этот список жил в BaseSettingFragment и работал только для открытого
     * экрана; теперь один на весь индекс.
     */
    fun keywordHints(key: String): List<String> {
        if (key.isBlank()) return emptyList()
        val out = ArrayList<String>(8)
        fun addAll(vararg v: String) = v.forEach { out.add(it) }

        if (key.contains("message") || key.contains("editor") || key.contains("panel")) {
            addAll("редактор", "клава", "клавиатура", "bbcode", "смайлы", "emoji", "вложения", "attachments")
        }
        if (key.contains("notif") || key.contains("notification")) {
            addAll("уведомления", "notify", "push", "пуш")
        }
        if (key.contains("avatar") || key.contains("image") || key.contains("coil")) {
            addAll("аватар", "аватарки", "картинки", "изображения")
        }
        if (key.contains("theme") || key.contains("font") || key.contains("text") || key.contains("size")) {
            addAll("тема", "оформление", "шрифт", "размер текста")
        }
        if (key.contains("palette") || key.contains("ui.") || key.contains("accent")) {
            addAll("палитра", "цвета", "4pda", "классика", "ios", "системный", "accent", "акцент")
        }
        if (key.contains("network") || key.contains("http") || key.contains("timeout") || key.contains("proxy")) {
            addAll("сеть", "интернет", "таймаут", "повторы", "retry", "прокси")
        }
        if (key.contains("bottom_nav") || key.contains("menu_sequence") || key.contains("startup")) {
            addAll("нижнее меню", "панель", "вкладки", "новости", "избранное", "порядок", "таб", "bottom", "nav", "tab bar")
        }
        if (key.contains("download") || key.contains("backup")) {
            addAll("загрузки", "скачивание", "папка", "бэкап", "резервная копия")
        }
        if (key.contains("topic") || key.contains("theme.")) {
            addAll("тема форума", "посты", "чтение")
        }
        return out
    }

    private fun buildIndex(context: Context): List<Entry> {
        val result = ArrayList<Entry>(96)
        for (section in SettingsSection.indexed) {
            runCatching { parseSection(context, section, result) }
        }
        return result
    }

    private fun parseSection(context: Context, section: SettingsSection, out: MutableList<Entry>) {
        val parser: XmlResourceParser = context.resources.getXml(section.xmlRes)
        try {
            var group: String? = null
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name
                if (event == XmlPullParser.START_TAG && tag != null) {
                    when (tag) {
                        TAG_SCREEN -> Unit
                        TAG_CATEGORY -> group = attrString(context, parser, "title")
                        else -> parseEntry(context, parser, section, group)?.let(out::add)
                    }
                } else if (event == XmlPullParser.END_TAG && tag == TAG_CATEGORY) {
                    group = null
                }
                event = parser.next()
            }
        } finally {
            parser.close()
        }
    }

    private fun parseEntry(
            context: Context,
            parser: XmlResourceParser,
            section: SettingsSection,
            group: String?,
    ): Entry? {
        val key = parser.getAttributeValue(ANDROID_NS, "key")?.takeIf { it.isNotBlank() } ?: return null
        if (key in SKIPPED_KEYS) return null
        if (SKIPPED_KEY_PREFIXES.any { key.startsWith(it) }) return null
        val title = attrString(context, parser, "title")?.takeIf { it.isNotBlank() } ?: return null
        // «%s» — плейсхолдер ListPreference под текущее значение, в поиске он бесполезен.
        val summary = attrString(context, parser, "summary")?.takeIf { it.isNotBlank() && it != "%s" }
        val isSwitch = parser.name.orEmpty().endsWith("SwitchPreference") ||
                parser.name.orEmpty().endsWith("SwitchPreferenceCompat") ||
                parser.name.orEmpty().endsWith("CheckBoxPreference")
        return Entry(
                key = key,
                title = title,
                summary = summary,
                section = section,
                group = group,
                isSwitch = isSwitch,
                defaultBoolean = parser.getAttributeBooleanValue(ANDROID_NS, "defaultValue", false),
        )
    }

    /** Атрибут может быть и ссылкой (@string/...), и литералом («ProPDA»). */
    private fun attrString(context: Context, parser: XmlResourceParser, name: String): String? {
        val resId = parser.getAttributeResourceValue(ANDROID_NS, name, 0)
        if (resId != 0) return runCatching { context.getString(resId) }.getOrNull()
        return parser.getAttributeValue(ANDROID_NS, name)
    }
}
