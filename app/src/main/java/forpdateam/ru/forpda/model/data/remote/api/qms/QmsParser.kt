package forpdateam.ru.forpda.model.data.remote.api.qms

import forpdateam.ru.forpda.entity.remote.others.user.ForumUser
import forpdateam.ru.forpda.entity.remote.qms.*
import forpdateam.ru.forpda.model.data.remote.ParserPatterns
import forpdateam.ru.forpda.model.data.remote.api.ApiUtils
import forpdateam.ru.forpda.model.data.remote.parser.BaseParser
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Безопасные extension-функции для извлечения групп из Matcher.
 * Возвращают null вместо краша при отсутствии группы или ошибке парсинга.
 */
private fun Matcher.groupInt(group: Int): Int? {
    val value = this.group(group) ?: return null
    return value.toIntOrNull()
}

private fun Matcher.groupLong(group: Int): Long? {
    val value = this.group(group) ?: return null
    return value.toLongOrNull()
}

class QmsParser(
        private val patternProvider: IPatternProvider
) : BaseParser() {

    private val scope = ParserPatterns.Qms

    fun parseSearch(response: String): List<ForumUser> = patternProvider
            .getPattern(scope.scope, scope.finduser)
            .matcher(response)
            .map { matcher ->
                ForumUser().apply {
                    id = matcher.groupInt(1) ?: return@map null
                    nick = matcher.group(2).fromHtml().orEmpty()
                    avatar = matcher.group(3)?.let {
                        when {
                            it.substring(0, 2) == "//" -> "https:$it"
                            it.substring(0, 1) == "/" -> "https://4pda.to$it"
                            else -> it
                        }
                    }
                }
            }
            .filterNotNull()

    fun parseBlackList(response: String): List<QmsContact> = response
            .also { checkOperation(it) }
            .let {
                patternProvider
                        .getPattern(scope.scope, scope.blacklist_main)
                        .matcher(it)
                        .map { matcher ->
                            QmsContact().apply {
                                id = matcher.groupInt(1) ?: return@map null
                                avatar = matcher.group(2)
                                nick = matcher.group(3).fromHtml()
                            }
                        }
                        .filterNotNull()
            }

    private fun checkOperation(response: String) = patternProvider
            .getPattern(scope.scope, scope.blacklist_msg)
            .matcher(response)
            .findAll { matcher ->
                if (!matcher.group(1).contains("success")) {
                    throw Exception(matcher.group(2).trim().fromHtml())
                }
            }

    fun parseContacts(response: String): List<QmsContact> = patternProvider
            .getPattern(scope.scope, scope.contacts_main)
            .matcher(response)
            .map { matcher ->
                QmsContact().apply {
                    id = matcher.groupInt(1) ?: return@map null
                    matcher.group(2).let {
                        count = if (it.isNullOrEmpty()) 0 else it.toIntOrNull() ?: 0
                    }
                    avatar = matcher.group(3)
                    nick = ApiUtils.fromHtml(matcher.group(4).trim())
                }
            }
            .filterNotNull()

    fun parseThemes(response: String, argId: Int): QmsThemes = QmsThemes().also { data ->
        data.userId = argId
        patternProvider
                .getPattern(scope.scope, scope.thread_nick)
                .matcher(response)
                .findOnce { matcher ->
                    data.nick = matcher.group(1).fromHtml()
                }
        val list = patternProvider
                .getPattern(scope.scope, scope.thread_main)
                .matcher(response)
                .map { matcher ->
                    QmsTheme().apply {
                        id = matcher.groupInt(1) ?: return@map null
                        date = matcher.group(2)
                        name = matcher.group(3).trim().fromHtml()
                        countMessages = matcher.groupInt(4) ?: return@map null
                        matcher.group(5).also {
                            countNew = if (it.isNullOrEmpty()) 0 else it.toIntOrNull() ?: 0
                        }
                    }
                }
                .filterNotNull()
        data.themes.addAll(list)

        // Intentionally NOT synthesising a placeholder theme for the empty system mailbox (argId==0):
        // a virtual theme with id=0 navigated into the chat screen, which rejects themeId<=0 as an
        // invalid theme and dead-ends on a misleading empty "Оповещения". When the mailbox genuinely
        // yields no rows we leave the list empty so the themes screen can show an honest empty state
        // and the user can pull-to-refresh, instead of opening an unusable id=0 thread.
    }

    fun parseChat(response: String): QmsChatModel = QmsChatModel().also { data ->
        data.messages.addAll(localParseMessages(response))
        parseChatInfo(response, data)
    }

    fun parseChatInfo(response: String, data: QmsChatModel) {
        patternProvider
                .getPattern(scope.scope, scope.chat_info)
                .matcher(response)
                .findOnce { matcher ->
                    data.nick = matcher.group(1)?.trim()?.fromHtml()
                    data.title = matcher.group(2)?.trim()?.fromHtml()
                    data.userId = matcher.groupInt(3) ?: 0
                    data.themeId = matcher.groupInt(4) ?: 0
                    data.avatarUrl = matcher.group(5)
                }
    }

    fun sendMessage(response: String): List<QmsMessage> = response
            .also {
                patternProvider
                        .getPattern(scope.scope, scope.send_message_error)
                        .matcher(it)
                        .findOnce {
                            throw Exception(it.group(1).trim())
                        }
            }
            .let {
                localParseMessages(it)
            }

    fun parseMoreMessages(response: String): List<QmsMessage> = localParseMessages(response)

    fun parseUserFromWebSocket(response: String): Int = patternProvider
            .getPattern(scope.scope, scope.message_info)
            .matcher(response)
            .mapOnce {
                it.groupInt(1) ?: 0
            } ?: 0

    private fun localParseMessages(response: String): List<QmsMessage> {
        val container = parseContainerChatMessages(response)
        if (container.isNotEmpty()) return container
        val legacy = parseLegacyChatMessages(response)
        if (legacy.isNotEmpty()) return legacy
        // System-notification dialogs ("Сообщения 4PDA") render `list-group-item` rows that may
        // omit `data-message-id`/`data-unread-status`/avatar, so the strict legacy/container
        // patterns above return zero. Fall back to a tolerant `msg-content` scan so these threads
        // are not misread as empty.
        val systemRows = parseSystemListGroupMessages(response)
        if (systemRows.isNotEmpty()) return systemRows
        return emptyList()
    }

    private fun parseLegacyChatMessages(response: String): List<QmsMessage> =
            parseMessagesWithPattern(response, scope.chat_pattern) { matcher ->
                val authorToken = matcher.group(1)
                val dateLabel = matcher.group(7)
                when {
                    authorToken == null && !dateLabel.isNullOrBlank() ->
                            QmsMessage().apply {
                                isDate = true
                                date = dateLabel.trim()
                            }
                    authorToken != null -> {
                        val messageContent = matcher.group(6)?.trim().orEmpty()
                        if (messageContent.isEmpty() && dateLabel.isNullOrBlank()) {
                            null
                        } else {
                            val messageId = matcher.groupInt(2)
                            if (messageId == null) {
                                null
                            } else {
                                QmsMessage().apply {
                                    isMyMessage = authorToken.isNotEmpty()
                                    id = messageId
                                    readStatus = if (isMyMessage) matcher.group(3) != "1" else true
                                    time = matcher.group(4)
                                    avatar = matcher.group(5)
                                    content = messageContent
                                }
                            }
                        }
                    }
                    else -> null
                }
            }

    private fun parseContainerChatMessages(response: String): List<QmsMessage> {
        val regexParsed = parseContainerChatMessagesWithPattern(response)
        if (regexParsed.isNotEmpty()) return regexParsed
        return parseContainerChatMessagesFromTags(response)
    }

    private fun parseContainerChatMessagesWithPattern(response: String): List<QmsMessage> =
            parseMessagesWithPattern(response, scope.chat_pattern_container) { matcher ->
                val classToken = matcher.group(1)?.takeIf { it.isNotEmpty() }
                        ?: matcher.group(4).orEmpty()
                val content = matcher.group(5)?.trim().orEmpty()
                val messageId = matcher.groupInt(2) ?: matcher.groupInt(3)
                if (content.isEmpty() || messageId == null) {
                    null
                } else {
                    QmsMessage().apply {
                        id = messageId
                        isMyMessage = classToken.contains("our", ignoreCase = true)
                        readStatus = !classToken.contains("unread", ignoreCase = true)
                        time = matcher.group(6)?.trim()
                        this.content = content
                    }
                }
            }

    /**
     * XHR may emit `data-mess-id` before `class="mess_container …"`; [chat_pattern_container] requires
     * the legacy attribute order and would return zero messages while markers are present.
     */
    private fun parseContainerChatMessagesFromTags(response: String): List<QmsMessage> {
        val openings = CONTAINER_OPEN_TAG_REGEX.findAll(response)
                .map { it.range.first to it.groupValues[1] }
                .toList()
        if (openings.isEmpty()) return emptyList()
        val result = ArrayList<QmsMessage>()
        openings.forEachIndexed { index, (start, attrs) ->
            val messageId = CONTAINER_MESS_ID_REGEX.find(attrs)?.groupValues?.get(1)?.toIntOrNull()
                    ?: return@forEachIndexed
            val classToken = CONTAINER_CLASS_REGEX.find(attrs)?.groupValues?.get(1).orEmpty()
            val blockEnd = openings.getOrNull(index + 1)?.first ?: response.length
            val block = response.substring(start, blockEnd)
            val content = CONTAINER_CONTENT_REGEX.find(block)?.groupValues?.get(1)?.trim().orEmpty()
            if (content.isEmpty()) return@forEachIndexed
            val time = CONTAINER_TIME_REGEX.find(block)?.groupValues?.get(1)?.trim()
            result.add(
                    QmsMessage().apply {
                        id = messageId
                        isMyMessage = classToken.contains("our", ignoreCase = true)
                        readStatus = !classToken.contains("unread", ignoreCase = true)
                        this.time = time
                        this.content = content
                    }
            )
        }
        return result
    }

    /**
     * Tolerant fallback for `list-group-item` message rows whose markup the strict legacy pattern
     * rejects (e.g. 4PDA system notifications without `data-message-id`/avatar). Only rows that
     * actually contain a `msg-content` block become messages, so genuinely empty threads stay empty.
     * Synthetic sequential ids are assigned when `data-message-id` is absent.
     */
    private fun parseSystemListGroupMessages(response: String): List<QmsMessage> {
        val openings = LIST_GROUP_OPEN_TAG_REGEX.findAll(response)
                .map { it.range.first to it.groupValues[1] }
                .toList()
        if (openings.isEmpty()) return emptyList()
        val result = ArrayList<QmsMessage>()
        var syntheticId = 0
        openings.forEachIndexed { index, (start, attrs) ->
            val classToken = LIST_GROUP_CLASS_REGEX.find(attrs)?.groupValues?.get(1).orEmpty()
            // Skip non-message rows (contact/blacklist/error boxes never carry msg-content).
            val blockEnd = openings.getOrNull(index + 1)?.first ?: response.length
            val block = response.substring(start, blockEnd)
            val content = LIST_GROUP_CONTENT_REGEX.find(block)?.groupValues?.get(1)?.trim().orEmpty()
            if (content.isEmpty()) return@forEachIndexed
            val explicitId = LIST_GROUP_MESSAGE_ID_REGEX.find(attrs)?.groupValues?.get(1)?.toIntOrNull()
            val unreadStatus = LIST_GROUP_UNREAD_REGEX.find(attrs)?.groupValues?.get(1)
            val time = LIST_GROUP_TIME_REGEX.find(block)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            result.add(
                    QmsMessage().apply {
                        id = explicitId ?: (++syntheticId)
                        isMyMessage = classToken.contains("our", ignoreCase = true)
                        readStatus = when {
                            unreadStatus != null -> unreadStatus != "1"
                            else -> !classToken.contains("unread", ignoreCase = true)
                        }
                        this.time = time
                        this.content = content
                    }
            )
        }
        return result
    }

    companion object {
        private val CONTAINER_OPEN_TAG_REGEX =
                Regex("""<div\s+([^>]*\bmess_container\b[^>]*)>""", RegexOption.IGNORE_CASE)
        private val LIST_GROUP_OPEN_TAG_REGEX =
                Regex("""<div\s+([^>]*\blist-group-item\b[^>]*)>""", RegexOption.IGNORE_CASE)
        private val LIST_GROUP_CLASS_REGEX =
                Regex("""\bclass\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        private val LIST_GROUP_MESSAGE_ID_REGEX =
                Regex("""\bdata-message-id\s*=\s*["']?(\d+)""", RegexOption.IGNORE_CASE)
        private val LIST_GROUP_UNREAD_REGEX =
                Regex("""\bdata-unread-status\s*=\s*["']?(\d+)""", RegexOption.IGNORE_CASE)
        private val LIST_GROUP_CONTENT_REGEX =
                Regex("""<div[^>]*\bmsg-content\b[^>]*>([\s\S]*?)</div>""", RegexOption.IGNORE_CASE)
        private val LIST_GROUP_TIME_REGEX =
                Regex("""</b>\s*([^<]*?)\s*<""", RegexOption.IGNORE_CASE)
        private val CONTAINER_MESS_ID_REGEX =
                Regex("""\bdata-mess-id\s*=\s*["']?(\d+)""", RegexOption.IGNORE_CASE)
        private val CONTAINER_CLASS_REGEX =
                Regex("""\bclass\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        private val CONTAINER_CONTENT_REGEX =
                Regex("""<div[^>]*\bcontent\b[^>]*>([\s\S]*?)</div>""", RegexOption.IGNORE_CASE)
        private val CONTAINER_TIME_REGEX =
                Regex("""<div[^>]*\btime\b[^>]*>[\s\S]*?<span>([^<]*)</span>""", RegexOption.IGNORE_CASE)
    }

    private fun parseMessagesWithPattern(
            response: String,
            patternKey: String,
            block: (Matcher) -> QmsMessage?
    ): List<QmsMessage> {
        val matcher = patternProvider.getPattern(scope.scope, patternKey).matcher(response)
        val result = ArrayList<QmsMessage>()
        while (matcher.find()) {
            block(matcher)?.let { result.add(it) }
        }
        return result
    }

    /**
     * Parse system alerts from inspector API format
     * Format: sourceId "title" msgCount userId "userNick" timestamp lastTimestamp
     * For system messages, userId=0 and userNick="" (or "Сообщения 4PDA")
     */
    fun parseInspectorAlerts(response: String): List<QmsMessage> {
        val messages = mutableListOf<QmsMessage>()
        
        // Inspector pattern: id "title" count userId "nick" ts lastTs
        val inspectorPattern = Pattern.compile(
            "(\\d+) \"([\\s\\S]*?)\" (\\d+) (\\d+) \"([\\s\\S]*?)\" (\\d+) (\\d+)"
        )
        
        val matcher = inspectorPattern.matcher(response)
        var id = 1
        while (matcher.find()) {
            val sourceId = matcher.groupInt(1) ?: continue
            val title = matcher.group(2)?.fromHtml() ?: ""
            val msgCount = matcher.groupInt(3) ?: continue
            val userId = matcher.groupInt(4) ?: continue
            val userNick = matcher.group(5)?.fromHtml() ?: ""
            val timeStamp = matcher.groupLong(6) ?: continue
            
            // Only include system messages (userId=0) or messages with empty nick (system)
            if (userId == 0 || userNick.isEmpty()) {
                val date = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(timeStamp * 1000))
                
                messages.add(QmsMessage().apply {
                    this.id = id++
                    this.content = "$title ($msgCount сообщений)"
                    this.time = date
                    this.isMyMessage = false
                    this.readStatus = true
                    this.avatar = ""
                })
            }
        }
        
        return messages
    }

}
