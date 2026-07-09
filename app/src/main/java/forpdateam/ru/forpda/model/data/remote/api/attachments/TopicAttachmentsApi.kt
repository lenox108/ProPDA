package forpdateam.ru.forpda.model.data.remote.api.attachments

import forpdateam.ru.forpda.entity.remote.attachments.TopicAttachment
import forpdateam.ru.forpda.model.data.remote.IWebClient

/**
 * Загрузка списка вложений темы. Отдельный от [AttachmentsApi] (тот — про аплоад вложений при
 * редактировании поста), здесь — только листинг страницы `act=attach&code=showtopic&tid=…`.
 *
 * Страница НЕ пагинируется на сервере (`&st=`/`&max=` игнорируются) — отдаёт все вложения разом,
 * поэтому на гигантских темах может весить десятки-сотни МБ. Читаем тело с ограничением [MAX_BODY_BYTES]
 * (см. [IWebClient.getCapped]); при обрезке возвращаем [TopicAttachmentsResult.truncated] = true.
 */
class TopicAttachmentsApi(
        private val webClient: IWebClient,
        private val parser: TopicAttachmentsParser,
) {
    fun getAttachments(topicId: Int): TopicAttachmentsResult {
        val response = webClient.getCapped(
                "https://4pda.to/forum/index.php?act=attach&code=showtopic&tid=$topicId",
                MAX_BODY_BYTES,
        )
        return TopicAttachmentsResult(
                items = parser.parse(response.body),
                truncated = response.truncated,
        )
    }

    companion object {
        /** 4 МБ хватает на тысячи вложений (реальная тема с 3029 файлами ≈ 1.8 МБ). */
        const val MAX_BODY_BYTES: Long = 4L * 1024 * 1024
    }
}

data class TopicAttachmentsResult(
        val items: List<TopicAttachment>,
        /** Список неполон: страница была обрезана по лимиту размера (очень много вложений). */
        val truncated: Boolean,
)
