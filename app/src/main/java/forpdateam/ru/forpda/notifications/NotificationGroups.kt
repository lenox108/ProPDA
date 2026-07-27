package forpdateam.ru.forpda.notifications

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent

/**
 * Группировка уведомлений о событиях форума: ключ группы, ID сводки, канал и «лицо» категории.
 *
 * Одного [android.app.Notification.getGroup] мало: уведомление с группой, но без опубликованной
 * сводки ([androidx.core.app.NotificationCompat.Builder.setGroupSummary]), Android НЕ сворачивает
 * и одновременно исключает из авто-бандлинга (`StatusBarNotification.isAppGroup()`). Ровно это
 * и давало кучу отдельных карточек в шторке. Сводка на категорию — обязательная вторая половина.
 */
object NotificationGroups {

    const val QMS = "forpda.group.qms"
    const val FAV = "forpda.group.fav"
    const val MENTION = "forpda.group.mention"
    const val SITE = "forpda.group.site"

    /**
     * ID сводок отрицательные: [NotificationEvent.notifyId] всегда неотрицателен, а
     * отрицательный поддиапазон уже поделён между служебными уведомлениями (FGS = -345,
     * неудача быстрого ответа ≤ -1000). QMS/FAV сохраняют исторические значения, чтобы
     * после обновления не осталось висеть старое stacked-уведомление под тем же ID.
     */
    const val SUMMARY_QMS_ID = -123
    const val SUMMARY_FAV_ID = -234
    const val SUMMARY_MENTION_ID = -235
    const val SUMMARY_SITE_ID = -236

    val ALL: List<String> = listOf(QMS, FAV, MENTION, SITE)

    val SUMMARY_IDS: List<Int> = ALL.map { summaryIdFor(it) }

    fun keyFor(event: NotificationEvent): String = when {
        event.isMention -> MENTION
        event.fromQms() -> QMS
        event.fromTheme() -> FAV
        else -> SITE
    }

    fun summaryIdFor(groupKey: String): Int = when (groupKey) {
        QMS -> SUMMARY_QMS_ID
        FAV -> SUMMARY_FAV_ID
        MENTION -> SUMMARY_MENTION_ID
        else -> SUMMARY_SITE_ID
    }

    fun channelIdFor(groupKey: String): String = when (groupKey) {
        QMS -> NotificationsService.CHANNEL_QMS_ID
        FAV -> NotificationsService.CHANNEL_FAV_ID
        MENTION -> NotificationsService.CHANNEL_MENTION_ID
        else -> NotificationsService.CHANNEL_SITE_ID
    }

    @DrawableRes
    fun smallIconFor(groupKey: String): Int = when (groupKey) {
        QMS -> R.drawable.ic_notify_qms
        FAV -> R.drawable.ic_notify_favorites
        MENTION -> R.drawable.ic_notify_mention
        else -> R.drawable.ic_notify_site
    }

    @StringRes
    fun titleResFor(groupKey: String): Int = when (groupKey) {
        QMS -> R.string.notification_group_title_qms
        FAV -> R.string.notification_group_title_fav
        MENTION -> R.string.notification_group_title_mention
        else -> R.string.notification_group_title_site
    }

    /** Куда ведёт тап по сводке: список раздела, а не конкретная тема/диалог. */
    fun listUrlFor(groupKey: String): String = when (groupKey) {
        QMS -> "https://4pda.to/forum/index.php?act=qms"
        FAV -> "https://4pda.to/forum/index.php?act=fav"
        else -> "https://4pda.to/forum/index.php?act=mentions"
    }
}
