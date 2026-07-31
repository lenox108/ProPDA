package forpdateam.ru.forpda.model.data.remote.api.attachments

import forpdateam.ru.forpda.model.data.remote.api.RequestFile

/**
 * Подмена файла перед отправкой на форум, если исходный формат форум не принимает
 * (animated WebP) или принимает, но портит (APNG пережимается в один кадр).
 *
 * Реализация — [forpdateam.ru.forpda.common.animation.AnimatedAttachmentConverter].
 */
interface AttachmentFileConverter {

    /** @return файл-замена или null, если конвертация не нужна (или не удалась). */
    fun convert(file: RequestFile): RequestFile?
}
