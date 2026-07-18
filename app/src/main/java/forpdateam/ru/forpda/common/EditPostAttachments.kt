package forpdateam.ru.forpda.common

import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.editpost.EditPostForm
import java.util.HashSet

/**
 * Страница `do=edit_post` рисует список вложений скриптом (ответ `act=attach&code=init`), поэтому в
 * HTML формы их часто нет вовсе. Единственный след — BBCode самого поста. Достаём id оттуда, иначе
 * панель открывается пустой, а сабмит уходит с пустым `file-list` и отвязывает файлы от поста.
 */
fun mergeAttachmentIdsFromPostText(form: EditPostForm) {
    val have = form.attachments.map { it.id }.toMutableSet()
    fun add(id: Int, nameHint: String?) {
        if (id <= 0 || id in have) return
        have.add(id)
        val item = AttachmentItem()
        item.id = id
        item.name = nameHint?.trim()?.takeIf { it.isNotBlank() && it.length < 260 }
                ?: "attachment_$id"
        item.loadState = AttachmentItem.STATE_LOADED
        item.status = AttachmentItem.STATUS_READY
        form.attachments.add(item)
    }
    val msg = form.message
    Regex("""(?i)\[attachment\s*=\s*(\d+)\s*:([^\]]+?)]""").findAll(msg).forEach { m ->
        add(m.groupValues[1].toIntOrNull() ?: return@forEach, m.groupValues.getOrNull(2))
    }
    Regex("""(?i)\[attachment\s*=\s*"(\d+)\s*:([^"]+?)"]""").findAll(msg).forEach { m ->
        add(m.groupValues[1].toIntOrNull() ?: return@forEach, m.groupValues.getOrNull(2))
    }
    Regex("""(?i)\[([^\]]{0,400})]\(\s*https?://4pda\.to/forum/dl/post/(\d+)/[^)\s]+""").findAll(msg).forEach { m ->
        add(m.groupValues[2].toIntOrNull() ?: return@forEach, m.groupValues.getOrNull(1))
    }
    Regex("""(?i)\[url=https?://4pda\.to/forum/dl/post/(\d+)/[^\]]+]([^\[]*)\[/url]""").findAll(msg).forEach { m ->
        add(m.groupValues[1].toIntOrNull() ?: return@forEach, m.groupValues.getOrNull(2))
    }
    Regex("""(?i)https?://4pda\.to/forum/dl/post/(\d+)/[^\s\]\)>"']+""").findAll(msg).forEach { m ->
        val id = m.groupValues[1].toIntOrNull() ?: return@forEach
        val tail = m.value.substringAfterLast('/').trim()
        add(id, tail.takeIf { it.contains('.') })
    }
}

fun dedupeAttachmentsById(form: EditPostForm) {
    val seen = HashSet<Int>()
    val it = form.attachments.iterator()
    while (it.hasNext()) {
        val id = it.next().id
        if (id != 0 && !seen.add(id)) it.remove()
    }
}

/**
 * При удалении вложения из панели нужно убрать ВСЕ его следы из тела поста — иначе при сохранении 4PDA
 * заново отрисует картинку/ссылку по оставшейся разметке, и вложение «не удаляется» (тот же id виден в посте
 * после правки). Формы ссылок зеркалят [mergeAttachmentIdsFromPostText]: `[attachment=id:...]` (в т.ч. с
 * пробелами/кавычками), обёртки `[img]`/`[url]`, markdown-ссылка и «голый» URL `dl/post/<id>/...`.
 */
fun removeAttachmentReferencesFromBody(message: String, id: Int): String {
    if (id <= 0 || message.isEmpty()) return message
    val idPart = """0*$id"""
    var out = message
    // [img]https://4pda.to/forum/dl/post/<id>/файл[/img] — снять целиком вместе с обёрткой.
    out = out.replace(
            Regex("""(?is)\[img[^\]]*]\s*https?://4pda\.to/forum/dl/post/$idPart/[^\[\s]+\s*\[/img]"""),
            ""
    )
    // [url=...dl/post/<id>/...]текст[/url]
    out = out.replace(
            Regex("""(?is)\[url\s*=\s*https?://4pda\.to/forum/dl/post/$idPart/[^\]]+].*?\[/url]"""),
            ""
    )
    // markdown [текст](...dl/post/<id>/...)
    out = out.replace(
            Regex("""(?is)\[[^\]]{0,400}]\(\s*https?://4pda\.to/forum/dl/post/$idPart/[^)\s]+\s*\)"""),
            ""
    )
    // [attachment=<id>:...] и [attachment="<id>:...">] с любыми пробелами вокруг = и :
    out = out.replace(
            Regex("""(?is)\[attachment\s*=\s*["']?$idPart\s*:[^\]]*?]"""),
            ""
    )
    // «Голый» URL скачивания без обёрток.
    out = out.replace(
            Regex("""(?i)https?://4pda\.to/forum/dl/post/$idPart/[^\s\]\)>"']+"""),
            ""
    )
    return out
}
