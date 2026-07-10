package forpdateam.ru.forpda.notifications

import forpdateam.ru.forpda.entity.remote.mentions.MentionItem

/**
 * Решает, о каких упоминаниях уведомлять при фоновом опросе `act=mentions`.
 *
 * Дедуп держится на множестве ключей НЕПРОЧИТАННЫХ упоминаний: каждый цикл мы сохраняем
 * текущее множество целиком, поэтому прочитанное упоминание выпадает из него само и
 * хранилище не растёт без границы.
 *
 * Первый проход ничего не показывает, только запоминает ([Decision.markSeeded]): иначе
 * включение фичи высыпало бы в шторку всю накопленную историю упоминаний.
 */
object BackgroundMentionPolicy {

    data class Decision(
            /** Упоминания, для которых нужно опубликовать уведомление. */
            val toNotify: List<MentionItem>,
            /** Множество ключей, которое следует сохранить как «уже виденные». */
            val keysToPersist: Set<String>,
            /** Требуется выставить флаг «первый проход состоялся». */
            val markSeeded: Boolean
    )

    fun decide(
            items: List<MentionItem>,
            alreadyNotifiedKeys: Set<String>,
            seeded: Boolean,
            mutedTopicIds: Set<Int> = emptySet()
    ): Decision {
        val keyed = items
                .filterNot { it.isRead }
                .mapNotNull { item -> MentionNotificationMapper.mentionKey(item)?.let { it to item } }
        val currentKeys = keyed.map { it.first }.toSet()

        if (!seeded) {
            return Decision(toNotify = emptyList(), keysToPersist = currentKeys, markSeeded = true)
        }

        val fresh = keyed
                .filterNot { (key, _) -> key in alreadyNotifiedKeys }
                .filterNot { (_, item) -> isMuted(item, mutedTopicIds) }
                .map { (_, item) -> item }

        return Decision(toNotify = fresh, keysToPersist = currentKeys, markSeeded = false)
    }

    private fun isMuted(item: MentionItem, mutedTopicIds: Set<Int>): Boolean {
        if (mutedTopicIds.isEmpty()) return false
        val event = MentionNotificationMapper.toNotificationEvent(item) ?: return false
        return event.fromTheme() && event.sourceId in mutedTopicIds
    }
}
