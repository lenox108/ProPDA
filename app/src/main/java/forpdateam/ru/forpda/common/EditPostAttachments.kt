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
