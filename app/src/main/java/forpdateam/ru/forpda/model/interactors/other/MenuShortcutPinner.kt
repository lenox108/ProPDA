package forpdateam.ru.forpda.model.interactors.other

import forpdateam.ru.forpda.entity.app.other.MenuShortcut
import forpdateam.ru.forpda.ui.views.drawers.adapters.OtherMenuSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Одна точка «Закрепить в меню» для экранов темы, избранного, QMS, поиска и профиля.
 * Чтобы не тащить [MenuShortcutsRepository] и корутинный scope в каждый presenter/VM,
 * фрагменты внедряют этот класс напрямую и дёргают [pin].
 *
 * Новые плитки всегда падают в «Быстрый доступ» — оттуда пользователь перетащит их куда нужно.
 */
class MenuShortcutPinner(
        private val repository: MenuShortcutsRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun pin(type: MenuShortcut.Type, title: String, url: String) {
        if (url.isBlank()) return
        scope.launch {
            repository.add(type, title.ifBlank { url }, url, OtherMenuSection.QUICK)
        }
    }

    fun pinTopic(topicId: Int, title: String) {
        if (topicId <= 0) return
        pin(MenuShortcut.Type.TOPIC, title, "https://4pda.to/forum/index.php?showtopic=$topicId")
    }

    fun pinDialog(userId: Int, nick: String) {
        if (userId <= 0) return
        pin(MenuShortcut.Type.DIALOG, nick, "https://4pda.to/forum/index.php?act=qms&mid=$userId")
    }
}
