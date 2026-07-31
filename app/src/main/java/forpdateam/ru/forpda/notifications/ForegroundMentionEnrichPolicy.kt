package forpdateam.ru.forpda.notifications

import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.entity.remote.mentions.MentionItem

/**
 * Дообогащение realtime-события об упоминании строкой из `act=mentions` перед публикацией в шторку.
 *
 * WS/`ev`-событие несёт ТОЛЬКО идентификаторы (`sourceId` = тема, `messageId` = пост), но именно они
 * авторитетны: сервер прислал их в момент появления поста. Ник и заголовок темы приходится добирать
 * из списка «Ответы».
 *
 * Ключевое правило: **`messageId` события заменяется строкой списка только при ТОЧНОМ совпадении
 * поста.** Раньше бралась просто первая строка этой темы («самая свежая»), и когда `act=mentions`
 * отставал — своя память репозитория при сетевом сбое, кэш CDN на `act=mentions` (тот же, из-за
 * которого страница периодически отдаёт 404), либо просто ещё не обновившийся список — на месте
 * свежего упоминания оказывалось ПРЕДЫДУЩЕЕ упоминание в той же теме. Его id уходил в
 * `view=findpost&p=`, и тап по уведомлению открывал старый пост вместо того, где упомянули
 * (полевой репорт: «открываются другие посты, которые были раньше»).
 *
 * Заголовок темы одинаков для всех строк одной темы, поэтому его можно взять и без точного
 * совпадения. Ник принадлежит КОНКРЕТНОМУ упоминанию — чужой ник в заголовке уведомления был бы
 * такой же ложью, как чужой пост, поэтому без совпадения он остаётся пустым (шторка покажет
 * обобщённый заголовок).
 */
object ForegroundMentionEnrichPolicy {

    /**
     * @param event событие из realtime-канала (тема + пост).
     * @param items строки `act=mentions`, как они пришли (свежие — первыми).
     * @return событие для публикации: с якорем на РЕАЛЬНЫЙ пост упоминания.
     */
    fun enrich(event: NotificationEvent, items: List<MentionItem>): NotificationEvent {
        if (!event.fromTheme() || !event.isMention) return event

        val candidates = items
                .mapNotNull { MentionNotificationMapper.toNotificationEvent(it) }
                .filter { it.fromTheme() && it.isMention && it.sourceId == event.sourceId }

        candidates.firstOrNull { it.messageId == event.messageId && it.messageId > 0 }
                ?.let {
                    log(event, candidates, reason = "exact_row", result = it)
                    return it
                }

        // Точного совпадения нет. Пока известен пост из события — он и есть якорь.
        if (event.messageId > 0) {
            val topicTitle = candidates.firstOrNull { it.sourceTitle.isNotBlank() }?.sourceTitle
            if (event.sourceTitle.isBlank() && topicTitle != null) {
                event.sourceTitle = topicTitle
            }
            log(event, candidates, reason = "event_post_kept", result = event)
            return event
        }

        // Событие без поста (испорченный/усечённый пакет) — лучше свежая строка списка, чем
        // `view=findpost&p=0`.
        val fallback = candidates.firstOrNull() ?: event
        log(event, candidates, reason = "no_event_post_freshest_row", result = fallback)
        return fallback
    }

    /**
     * Разбор именно этого решения — единственный способ отличить «уведомление увело не туда» от
     * «сервер прислал не тот пост»: в логе видно, что было в событии и что предлагал список.
     */
    private fun log(
            event: NotificationEvent,
            candidates: List<NotificationEvent>,
            reason: String,
            result: NotificationEvent,
    ) {
        if (!forpdateam.ru.forpda.BuildConfig.DEBUG) return
        android.util.Log.i("FPDA_MENTION_ENRICH",
                "topic=${event.sourceId} wsPost=${event.messageId} reason=$reason" +
                        " anchorPost=${result.messageId} nick=${result.userNick}" +
                        " rows=${candidates.map { it.messageId }}")
    }
}
