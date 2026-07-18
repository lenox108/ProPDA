package forpdateam.ru.forpda.entity.app.history

/**
 * Created by radiationx on 01.01.18.
 */
data class HistoryItem(
    override var id: Int = 0,
    override var url: String? = null,
    override var date: String? = null,
    override var title: String? = null,
    override var unixTime: Long = 0,
    // --- Транзиентное состояние индикатора «новых сообщений» (НЕ персистентно, не в Room). ---
    // Заполняется в HistoryViewModel сшивкой с кэшем Избранного по topicId (= HistoryItem.id):
    // для тем, которые есть в избранном и там непрочитаны. Безопасный read-only источник статуса —
    // никаких сетевых проб самой темы (любой GET темы метит её прочитанной на сервере).
    var isUnread: Boolean = false,
    var unreadCount: Int = 0
) : IHistoryItem {
    constructor(item: IHistoryItem) : this(
        id = item.id,
        url = item.url,
        date = item.date,
        title = item.title,
        unixTime = item.unixTime
    )
}
