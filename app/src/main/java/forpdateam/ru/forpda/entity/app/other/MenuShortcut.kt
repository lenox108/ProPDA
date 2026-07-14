package forpdateam.ru.forpda.entity.app.other

import forpdateam.ru.forpda.ui.views.drawers.adapters.OtherMenuSection

/**
 * Пользовательская плитка меню: закреплённая тема, раздел форума, диалог QMS, сохранённый
 * поиск, профиль или произвольная ссылка 4PDA.
 *
 * Открывается всегда через [forpdateam.ru.forpda.presentation.ILinkHandler] по [url] — он уже
 * умеет разбирать showtopic/showforum/act=qms/act=search/профили, поэтому отдельного роутинга
 * на каждый тип не нужно; [type] влияет только на иконку.
 *
 * [id] отрицательный, чтобы не пересекаться с константами [forpdateam.ru.forpda.model.interactors.other.MenuRepository]
 * и спокойно жить в той же строке порядка плиток.
 */
data class MenuShortcut(
        val id: Int,
        val type: Type,
        val title: String,
        val url: String,
        val section: OtherMenuSection = OtherMenuSection.QUICK
) {
    enum class Type { TOPIC, FORUM, DIALOG, SEARCH, PROFILE, LINK }
}
