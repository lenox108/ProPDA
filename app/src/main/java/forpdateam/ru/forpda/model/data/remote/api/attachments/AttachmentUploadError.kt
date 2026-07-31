package forpdateam.ru.forpda.model.data.remote.api.attachments

import java.util.Locale

/**
 * Человеческий текст ошибки аплоада вложения.
 *
 * Форум на отказ отвечает не текстом, а односимвольным телом («1»), и раньше именно эта
 * единица и показывалась пользователю под именем файла — понять из неё, что формат просто
 * не принимается, было невозможно (жалоба «нельзя прикрепить анимированные файлы» — это был
 * отклонённый animated WebP).
 *
 * Чистая функция без Android-зависимостей — тестируется на JVM.
 */
object AttachmentUploadError {

    /** Расширения, которые форум заведомо не принимает (или принимает и портит). */
    private val UNSUPPORTED = mapOf(
        "webp" to "WebP",
        "avif" to "AVIF",
        "heic" to "HEIC",
        "heif" to "HEIF",
        "jxl" to "JPEG XL",
        "webm" to "WebM",
        "mp4" to "MP4",
        "mkv" to "MKV",
        "mov" to "MOV",
    )

    private const val MAX_SERVER_TEXT = 200
    private const val MIN_SERVER_TEXT = 4

    fun describe(rawResponse: String?, fileName: String?): String {
        serverText(rawResponse)?.let { return it }

        val extension = extensionOf(fileName)
        val label = UNSUPPORTED[extension]
        return when {
            label != null ->
                "Форум не принимает файлы $label. Для анимации подойдёт GIF, для картинки — PNG или JPG."

            extension.isNotEmpty() ->
                "Форум отклонил файл .$extension — формат не поддерживается или файл слишком большой."

            else ->
                "Форум отклонил файл — формат не поддерживается или файл слишком большой."
        }
    }

    /** Осмысленный текст ошибки от сервера, если он вообще что-то написал словами. */
    private fun serverText(rawResponse: String?): String? {
        val text = rawResponse
            .orEmpty()
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (text.length < MIN_SERVER_TEXT) return null
        if (text.none { it.isLetter() }) return null
        return text.take(MAX_SERVER_TEXT)
    }

    private fun extensionOf(fileName: String?): String {
        val name = fileName.orEmpty().substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return ""
        return name.substring(dot + 1).lowercase(Locale.ROOT).takeIf { it.length <= 8 }.orEmpty()
    }
}
