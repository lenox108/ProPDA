package forpdateam.ru.forpda.ui.fragments.notes.adapters

import androidx.annotation.DrawableRes
import forpdateam.ru.forpda.R

/**
 * Тип закладки, выведенный из её ссылки. Нужен ради значка в строке списка: раньше тип
 * читался только по служебному префиксу в названии («пост Клуб пользователей …»), который
 * отъедал ширину у самого названия и был у одних закладок, но не у других.
 */
enum class NoteLinkType(@DrawableRes val iconRes: Int) {
    POST(R.drawable.ic_comment_outline),
    TOPIC(R.drawable.ic_forum),
    // Раздел — список тем: стрелка ic_forum_go_to_topics в кружке читалась как шеврон папки.
    FORUM(R.drawable.ic_view_list),
    NEWS(R.drawable.ic_newspaper),
    // Тот же значок, что у «Мои сообщения» в меню (MenuMapper): ic_qms_theme_title — это
    // буква «T» из панели редактора, в списке она ничего не значит.
    QMS(R.drawable.ic_contacts),
    DEVICE(R.drawable.ic_devices_other),
    PROFILE(R.drawable.ic_account_circle),
    LINK(R.drawable.ic_link);

    companion object {

        /** Заголовки закладок на пост исторически создавались с этим префиксом. */
        private const val POST_TITLE_PREFIX = "пост "

        fun of(link: String?): NoteLinkType {
            val url = link?.trim()?.lowercase().orEmpty()
            if (url.isEmpty()) return LINK
            return when {
                url.contains("act=qms") || url.contains("/qms") -> QMS
                url.contains("/devdb") || url.contains("act=devdb") -> DEVICE
                url.contains("showuser=") || url.contains("act=profile") -> PROFILE
                // Ссылка на конкретный пост: findpost, либо номер поста в параметре.
                url.contains("view=findpost") || POST_PARAM.containsMatchIn(url) -> POST
                url.contains("showtopic=") || url.contains("act=st") -> TOPIC
                url.contains("showforum=") || url.contains("act=sf") -> FORUM
                url.contains("/forum") -> FORUM
                NEWS_ARTICLE.containsMatchIn(url) || url.contains("/news") -> NEWS
                else -> LINK
            }
        }

        /**
         * Название для показа: у закладок на пост срезаем служебный префикс — тип теперь
         * виден по значку. Старые закладки так выравниваются с новыми, не трогая базу.
         */
        fun displayTitle(title: String?, type: NoteLinkType): String {
            val text = title.orEmpty()
            if (type != POST) return text
            if (!text.startsWith(POST_TITLE_PREFIX, ignoreCase = true)) return text
            return text.removeRange(0, POST_TITLE_PREFIX.length).trimStart().ifEmpty { text }
        }

        private val POST_PARAM = Regex("[?&]p=\\d+")
        private val NEWS_ARTICLE = Regex("4pda\\.(to|ru)/\\d{4}/\\d{2}/\\d{2}/")
    }
}
