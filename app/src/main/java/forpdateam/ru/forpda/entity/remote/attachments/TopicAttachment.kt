package forpdateam.ru.forpda.entity.remote.attachments

/**
 * Одна строка списка вложений темы (страница `act=attach&code=showtopic&tid=…`).
 *
 * [url] — абсолютная ссылка на скачивание вида `https://4pda.to/forum/dl/post/<id>/<name>`,
 * которую [forpdateam.ru.forpda.presentation.LinkHandler.handleMedia] уже умеет роутить:
 * картинки → встроенный ImageViewer, прочие файлы → встроенный загрузчик. Поэтому тап по
 * элементу не требует отдельного кода скачивания.
 */
data class TopicAttachment(
        val name: String,
        val url: String,
        val sizeText: String? = null,
        val isImage: Boolean = false,
        /** Доп. подпись, если есть в разметке (кол-во скачиваний / дата). */
        val meta: String? = null,
)
