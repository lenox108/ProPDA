package forpdateam.ru.forpda.model.repository.mentions

import forpdateam.ru.forpda.entity.remote.mentions.MentionItem

internal data class MentionIdentity(
        val key: String,
        val topicId: Int,
        val postId: Int,
)

internal fun MentionItem.mentionIdentity(): MentionIdentity? {
    val normalizedLink = link
            ?.replace("&amp;", "&")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
    val topicId = Regex("""(?i)[?&]showtopic=(\d+)""")
            .find(normalizedLink)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0
    val postId = Regex("""(?i)(?:[?&](?:p|pid)=|[/#]entry)(\d+)""")
            .find(normalizedLink)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0
    val key = if (topicId > 0 && postId > 0) {
        "topic:$topicId:post:$postId"
    } else {
        "$type:$normalizedLink"
    }
    return MentionIdentity(key, topicId, postId)
}
