package forpdateam.ru.forpda.notifications

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.ui.activities.MainActivity
import timber.log.Timber

/**
 * Long-lived динамические ярлыки на собеседников QMS. Нужны не ради лаунчера, а как пропуск
 * в раздел «Диалоги» шторки (Android 11+): уведомление попадает туда, только если у него есть
 * `shortcutId` живого long-lived ярлыка И MessagingStyle с [Person]. Побочно даёт аватар
 * вместо значка приложения и поддержку bubbles.
 *
 * Ярлыки живут рядом с [forpdateam.ru.forpda.ui.FavoriteShortcuts] в общем пуле динамических
 * ярлыков — тот при обновлении обязан их сохранять (см. `isConversationShortcut`).
 */
object QmsConversationShortcuts {

    private const val ID_PREFIX = "qms_user_"

    /** Избранные темы занимают ранги 0..2 — при нехватке слотов лаунчер вытеснит диалоги первыми. */
    private const val RANK = 10

    private const val LABEL_MAX = 24

    fun shortcutId(userId: Int): String = "$ID_PREFIX$userId"

    fun isConversationShortcut(id: String): Boolean = id.startsWith(ID_PREFIX)

    /**
     * Публикует (или обновляет) ярлык собеседника.
     *
     * @return id ярлыка, который можно ставить уведомлению, либо null — тогда уведомление
     * останется обычным, без раздела «Диалоги». Осечка ярлыка не должна ронять публикацию.
     */
    fun push(context: Context, event: NotificationEvent, avatar: Bitmap?): String? {
        if (!event.fromQms() || event.userId <= 0 || event.userNick.isBlank()) return null
        val id = shortcutId(event.userId)
        return runCatching {
            val label = event.userNick.take(LABEL_MAX)
            val icon = avatar?.let { IconCompat.createWithAdaptiveBitmap(it) }
                    ?: IconCompat.createWithResource(context, R.drawable.ic_shortcut_contacts)
            val person = Person.Builder()
                    .setName(event.userNick)
                    .setKey(id)
                    .setIcon(icon)
                    .build()
            val shortcut = ShortcutInfoCompat.Builder(context, id)
                    .setShortLabel(label)
                    .setLongLabel(label)
                    .setIcon(icon)
                    .setPerson(person)
                    // Обязательное условие раздела «Диалоги»: система кэширует такой ярлык,
                    // пока на него ссылается уведомление, даже если он вылетел из динамических.
                    .setLongLived(true)
                    .setRank(RANK)
                    .setIntent(dialogIntent(context, event))
                    .build()
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
            id
        }.onFailure { Timber.w(it, "QMS conversation shortcut push failed") }.getOrNull()
    }

    private fun dialogIntent(context: Context, event: NotificationEvent): Intent {
        val url = if (event.sourceId > 0) {
            "https://4pda.to/forum/index.php?act=qms&mid=${event.userId}&t=${event.sourceId}"
        } else {
            "https://4pda.to/forum/index.php?act=qms&mid=${event.userId}"
        }
        return Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .setClass(context, MainActivity::class.java)
    }
}
