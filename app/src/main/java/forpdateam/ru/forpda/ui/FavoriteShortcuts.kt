package forpdateam.ru.forpda.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.notifications.QmsConversationShortcuts
import forpdateam.ru.forpda.ui.activities.MainActivity

/**
 * Динамические App Shortcuts на топ-избранные ТЕМЫ (long-press по иконке → сразу
 * в свою активную ветку). Дополняет статические ярлыки (Ответы/QMS/Избранное/
 * Новости) из res/xml/shortcuts.xml — это та часть, которую статические не могут:
 * отражают текущее состояние избранного.
 *
 * Deep-link — тем же URL-механизмом, что уведомления/статические ярлыки
 * (ACTION_VIEW `showtopic=<id>` → MainActivity → presenter.openLink).
 */
object FavoriteShortcuts {

    const val MAX = 3
    private const val ID_PREFIX = "fav_topic_"

    /**
     * Чистый отбор тем под ярлыки (тестируется без Android): только темы (не форумы),
     * не скрытые, с валидными id/заголовком; закреплённые вперёд (стабильная
     * сортировка сохраняет порядок кэша = свежесть); не более [MAX].
     */
    fun selectTopics(favorites: List<FavItem>): List<FavItem> = favorites.asSequence()
            .filter { !it.isHidden && !it.isForum && it.topicId > 0 && !it.topicTitle.isNullOrBlank() }
            .sortedByDescending { it.isPin }
            .take(MAX)
            .toList()

    fun update(context: Context, favorites: List<FavItem>) {
        val topics = selectTopics(favorites)

        val shortcuts = topics.mapIndexed { index, item ->
            val title = item.topicTitle!!.trim()
            val url = "https://4pda.to/forum/index.php?showtopic=${item.topicId}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .setClass(context, MainActivity::class.java)
            ShortcutInfoCompat.Builder(context, "$ID_PREFIX${item.topicId}")
                    .setShortLabel(title.take(SHORT_LABEL_MAX))
                    .setLongLabel(title.take(LONG_LABEL_MAX))
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_favorites))
                    .setRank(index)
                    .setIntent(intent)
                    .build()
        }

        runCatching {
            // setDynamicShortcuts заменяет ВЕСЬ динамический пул, а в нём живут ещё и ярлыки
            // собеседников QMS — без них уведомления выпадают из раздела «Диалоги» шторки.
            // Поэтому не затираем их, а переносим (сколько влезет в лимит лаунчера).
            val conversations = ShortcutManagerCompat.getDynamicShortcuts(context)
                    .filter { QmsConversationShortcuts.isConversationShortcut(it.id) }
                    .take(conversationSlots(context, shortcuts.size))
            val merged = shortcuts + conversations
            if (merged.isEmpty()) {
                ShortcutManagerCompat.removeAllDynamicShortcuts(context)
            } else {
                ShortcutManagerCompat.setDynamicShortcuts(context, merged)
            }
        }.onFailure { timber.log.Timber.e(it, "FavoriteShortcuts.update failed") }
    }

    private fun conversationSlots(context: Context, used: Int): Int {
        val max = runCatching { ShortcutManagerCompat.getMaxShortcutCountPerActivity(context) }
                .getOrDefault(DEFAULT_MAX_SHORTCUTS)
                .coerceAtLeast(DEFAULT_MAX_SHORTCUTS)
        return (max - used).coerceAtLeast(0)
    }

    private const val SHORT_LABEL_MAX = 20
    private const val LONG_LABEL_MAX = 40

    /** Гарантия платформы: не меньше 5 динамических ярлыков на активность. */
    private const val DEFAULT_MAX_SHORTCUTS = 5
}
