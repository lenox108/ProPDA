package forpdateam.ru.forpda.model.data.remote.api.qms
import forpdateam.ru.forpda.BuildConfig

import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.editpost.ImgbbResponseJson
import forpdateam.ru.forpda.entity.remote.others.user.ForumUser
import forpdateam.ru.forpda.entity.remote.qms.QmsChatModel
import forpdateam.ru.forpda.entity.remote.qms.QmsContact
import forpdateam.ru.forpda.entity.remote.qms.QmsMessage
import forpdateam.ru.forpda.entity.remote.qms.QmsThemes
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.api.NetworkRequest
import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URLEncoder
import java.util.*
import java.util.regex.Pattern


/**
 * Created by radiationx on 29.07.16.
 */
class QmsApi(
        private val webClient: IWebClient,
        private val qmsParser: QmsParser
) {

    private val imgBbPattern = Pattern.compile("PF\\.obj\\.config\\.json_api=\"([^\"]*?)\"[\\s\\S]*?PF\\.obj\\.config\\.auth_token=\"([^\"]*?)\"")
    private val json = Json { ignoreUnknownKeys = true }

    fun getBlackList(): List<QmsContact> {
        val builder = NetworkRequest.Builder()
                .url("https://4pda.to/forum/index.php?act=qms&settings=blacklist")
                .formHeader("xhr", "body")
        val response = webClient.request(builder.build())
        return qmsParser.parseBlackList(response.body)
    }

    fun getContactList(): List<QmsContact> {
        val response = webClient.request(NetworkRequest.Builder().url("https://4pda.to/forum/index.php?&act=qms-xhr&action=userlist").build())
        return qmsParser.parseContacts(response.body)
    }

    fun unBlockUsers(id: Int): List<QmsContact> {
        val builder = NetworkRequest.Builder()
                .url("https://4pda.to/forum/index.php?act=qms&settings=blacklist&xhr=blacklist-form&do=1")
                .formHeader("action", "delete-users")
        val strId = Integer.toString(id)
        builder.formHeader("user-id[$strId]", strId)
        val response = webClient.request(builder.build())
        return qmsParser.parseBlackList(response.body)
    }

    fun blockUser(nick: String): List<QmsContact> {
        val builder = NetworkRequest.Builder()
                .url("https://4pda.to/forum/index.php?act=qms&settings=blacklist&xhr=blacklist-form&do=1")
                .formHeader("action", "add-user")
                .formHeader("username", nick)
        val response = webClient.request(builder.build())
        return qmsParser.parseBlackList(response.body)
    }

    fun getThemesList(id: Int): QmsThemes {
        val builder = NetworkRequest.Builder()
        // The system contact "Сообщения 4PDA" lives in mailbox mid=0 (its threads, e.g.
        // "Оповещения" t=282644, post to act=qms&mid=0&t=…). Requesting the QMS root (act=qms,
        // the contacts page) returned no thread rows, so parseThemes synthesised a virtual id=0
        // theme that the chat screen later rejected as invalid → empty alerts. Always address the
        // mailbox by mid so the real thread ids are parsed.
        val url = "https://4pda.to/forum/index.php?act=qms&mid=$id"
        builder.url(url)
        builder.xhrHeader()
        val response = webClient.request(builder.build())
        if (forpdateam.ru.forpda.BuildConfig.DEBUG) timber.log.Timber.v("QMS_API getThemesList: id=$id, length=${response.body.length}")
        val themes = qmsParser.parseThemes(response.body, id)
        logThemesList(id, url, response, themes)
        return themes
    }

    /**
     * DEBUG-only: captures which themes-list URL was requested for a contact and what ids were
     * parsed. For the system contact (id=0) this proves whether the "Оповещения" thread is parsed
     * with a real thread id (>0) or whether parseThemes synthesised the virtual id=0 placeholder
     * (which downstream is rejected as an invalid theme and surfaces as an empty alerts screen).
     */
    private fun logThemesList(
            id: Int,
            url: String,
            response: NetworkResponse,
            themes: QmsThemes
    ) {
        if (!BuildConfig.DEBUG) return
        val parsedIds = themes.themes.joinToString(separator = ",") { theme ->
            "${theme.id}:${theme.name?.take(24).orEmpty()}"
        }
        val syntheticVirtual = id == 0 &&
                themes.themes.size == 1 &&
                themes.themes.first().id == 0
        forpdateam.ru.forpda.diagnostic.FpdaDebugLog.log(
                forpdateam.ru.forpda.diagnostic.FpdaDebugLog.TAG_QMS_OPEN,
                "themes_list_parsed",
                mapOf(
                        "contactId" to id,
                        "mid" to id,
                        "requestedUrlSanitized" to
                                forpdateam.ru.forpda.diagnostic.FpdaDebugLog.sanitizeUrl(url),
                        "httpStatus" to response.code,
                        "redirectUrlSanitized" to
                                forpdateam.ru.forpda.diagnostic.FpdaDebugLog.sanitizeUrl(response.redirect),
                        "responseSizeBytes" to response.body.length,
                        "parsedThemeCount" to themes.themes.size,
                        "syntheticVirtualTheme" to syntheticVirtual,
                        "parsedThemeIds" to parsedIds.take(200)
                )
        )
    }

    fun deleteTheme(id: Int, themeId: Int): QmsThemes {
        val builder = NetworkRequest.Builder()
                .url("https://4pda.to/forum/index.php?act=qms&mid=$id&xhr=body&do=1")
                .formHeader("xhr", "body")
                .formHeader("action", "delete-threads")
                .formHeader("thread-id[$themeId]", themeId.toString())
        val response = webClient.request(builder.build())
        return qmsParser.parseThemes(response.body, id)
    }

    /**
     * Deletes one or more messages from a thread. Mirrors the site's «Действия → Удалить сообщения»
     * (same endpoint shape as [deleteTheme]): a POST to the thread's xhr=body page with
     * `action=delete-messages` and one `message-id[$id]=$id` pair per message.
     *
     * NB: on 4pda this deletion is ONE-SIDED — it removes the message only from the current
     * account's copy of the thread; the interlocutor still sees it. Verified live 2026-07-21.
     */
    fun deleteMessages(userId: Int, themeId: Int, messageIds: List<Int>): String {
        val builder = NetworkRequest.Builder()
                .url("https://4pda.to/forum/index.php?act=qms&mid=$userId&t=$themeId&xhr=body&do=1")
                .formHeader("xhr", "body")
                .formHeader("action", "delete-messages")
        messageIds.forEach { id ->
            builder.formHeader("message-id[$id]", id.toString())
        }
        return webClient.request(builder.build()).body
    }

    suspend fun fetchChat(userId: Int, themeId: Int): NetworkResponse {
        if (userId == 0) {
            return fetchSystemAlertsChat(themeId)
        }
        val builder = NetworkRequest.Builder()
                .url("https://4pda.to/forum/index.php?act=qms&mid=$userId&t=$themeId")
                .xhrHeader()
        return webClient.request(builder.build())
    }

    fun parseFetchedChat(body: String): QmsChatModel {
        val parsed = qmsParser.parseChat(body)
        logParsedChat(parsed, body)
        return parsed
    }

    fun parseFetchedSystemChat(body: String, themeId: Int): QmsChatModel {
        val parsed = qmsParser.parseChat(body)
        parsed.userId = 0
        parsed.themeId = themeId
        if (parsed.nick.isNullOrBlank()) parsed.nick = "Сообщения 4PDA"
        logParsedChat(parsed, body)
        return parsed
    }

    suspend fun getChat(userId: Int, themeId: Int): QmsChatModel {
        val response = fetchChat(userId, themeId)
        timber.log.Timber.d(
                "QmsApi.getChat: userId=%d themeId=%d code=%d redirect=%s bodyLen=%d",
                userId, themeId, response.code, response.redirect, response.body.length
        )
        return if (userId == 0) {
            parseFetchedSystemChat(response.body, themeId)
        } else {
            parseFetchedChat(response.body)
        }
    }

    /**
     * Suspend variant of the system-alerts chat fetch. When no valid thread id is provided, we
     * resolve it from the themes-list and then issue the chat request with the resolved id.
     * The themes-list and chat requests are NOT issued in parallel because the chat request
     * needs the resolved themeId from the themes-list; running them concurrently would either
     * double the network traffic (chat fired speculatively) or require cancellation logic
     * that adds risk for a low-frequency fallback path.
     */
    private suspend fun fetchSystemAlertsChat(themeId: Int): NetworkResponse {
        if (themeId > 0) {
            return withContext(Dispatchers.IO) {
                webClient.request(
                        NetworkRequest.Builder()
                                .url("https://4pda.to/forum/index.php?act=qms&mid=0&t=$themeId")
                                .xhrHeader()
                                .build()
                )
            }
        }
        val themes = withContext(Dispatchers.IO) { fetchSystemAlertsThemesList() }
        val resolvedThemeId = themes.themes
                .find { it.name?.contains("Оповещения") == true }
                ?.id
                ?.takeIf { it > 0 }
                ?: themeId
        return withContext(Dispatchers.IO) {
            webClient.request(
                    NetworkRequest.Builder()
                            .url("https://4pda.to/forum/index.php?act=qms&mid=0&t=$resolvedThemeId")
                            .xhrHeader()
                            .build()
            )
        }
    }

    private fun fetchSystemAlertsThemesList(): forpdateam.ru.forpda.entity.remote.qms.QmsThemes {
        val themesBuilder = NetworkRequest.Builder()
                .url("https://4pda.to/forum/index.php?act=qms&mid=0")
                .xhrHeader()
        val themesResponse = webClient.request(themesBuilder.build())
        return qmsParser.parseThemes(themesResponse.body, 0)
    }

    private fun logParsedChat(parsed: QmsChatModel, body: String) {
        timber.log.Timber.d(
                "QmsApi.getChat parsed: messages=%d nick=%s title=%s parsedThemeId=%d parsedUserId=%d",
                parsed.messages.size, parsed.nick, parsed.title, parsed.themeId, parsed.userId
        )
        if (BuildConfig.DEBUG && parsed.messages.isEmpty() && body.isNotEmpty()) {
            val fields = forpdateam.ru.forpda.diagnostic.FpdaDebugLog.classifyHtml(body) +
                    mapOf("parsedMessages" to parsed.messages.size)
            forpdateam.ru.forpda.diagnostic.FpdaDebugLog.warn(
                    forpdateam.ru.forpda.diagnostic.FpdaDebugLog.TAG_QMS_PARSE,
                    "chat_empty_parse",
                    fields
            )
        }
    }
    fun findUser(nick: String): List<ForumUser> {
        val encodedNick = URLEncoder.encode(nick, "UTF-8")
        val builder = NetworkRequest.Builder()
                .url("https://4pda.to/forum/index.php?act=qms-xhr&action=autocomplete-username&q=$encodedNick")
                .xhrHeader()
        val response = webClient.request(builder.build())
        return qmsParser.parseSearch(response.body)
    }

    fun sendNewTheme(nick: String, title: String, mess: String, files: List<AttachmentItem>): QmsChatModel {
        val builder = NetworkRequest.Builder()
                .url("https://4pda.to/forum/index.php?act=qms&action=create-thread&xhr=body&do=1")
                .formHeader("username", nick)
                .formHeader("title", title)
                .formHeader("message", mess)
                .formHeader("attaches", files.joinToString { it.id.toString() })
        val response = webClient.request(builder.build())
        return qmsParser.parseChat(response.body)
    }

    fun sendMessage(userId: Int, themeId: Int, text: String, files: List<AttachmentItem>): List<QmsMessage> {
        val builder = NetworkRequest.Builder()
                .url("https://4pda.to/forum/index.php")
                .formHeader("act", "qms-xhr")
                .formHeader("action", "send-message")
                .formHeader("message", text)
                .formHeader("mid", Integer.toString(userId))
                .formHeader("t", Integer.toString(themeId))
                .formHeader("attaches", files.joinToString { it.id.toString() })
        val response = webClient.request(builder.build())
        return qmsParser.sendMessage(response.body)
    }

    fun getMessagesFromWs(themeId: Int, messageId: Int, afterMessageId: Int): List<QmsMessage> {
        val messInfoBuilder = NetworkRequest.Builder()
                .url("https://4pda.to/forum/index.php?act=qms-xhr&")
                .formHeader("action", "message-info")
                .formHeader("t", Integer.toString(themeId))
                .formHeader("msg-id", Integer.toString(messageId))
        val messInfoResponse = webClient.request(messInfoBuilder.build())
        val userId = qmsParser.parseUserFromWebSocket(messInfoResponse.body)
        return getMessagesAfter(userId, themeId, afterMessageId)
    }

    fun getMessagesAfter(userId: Int, themeId: Int, afterMessageId: Int): List<QmsMessage> {
        val threadMessagesBuilder = NetworkRequest.Builder()
                .url("https://4pda.to/forum/index.php?act=qms-xhr&")
                .xhrHeader()
                .formHeader("action", "get-thread-messages")
                .formHeader("mid", Integer.toString(userId))
                .formHeader("t", Integer.toString(themeId))
                .formHeader("after-message", Integer.toString(afterMessageId))
        val response = webClient.request(threadMessagesBuilder.build())
        return qmsParser.parseMoreMessages(response.body)
    }

    fun deleteDialog(mid: Int): String {
        val builder = NetworkRequest.Builder()
                .url("https://4pda.to/forum/index.php")
                .formHeader("act", "qms-xhr")
                .formHeader("action", "del-member")
                .formHeader("del-mid", Integer.toString(mid))
        return webClient.request(builder.build()).body
    }

    fun uploadFiles(files: List<RequestFile>, pending: List<AttachmentItem>): List<AttachmentItem> {
        val baseUrl = "https://ru.imgbb.com/"
        var uploadUrl = "https://ru.imgbb.com/json"
        var authToken = "null"

        val baseResponse = webClient.get(baseUrl)
        val baseMatcher = imgBbPattern.matcher(baseResponse.body)
        if (baseMatcher.find()) {
            uploadUrl = baseMatcher.group(1) ?: ""
            authToken = baseMatcher.group(2) ?: ""
        }


        val headers = HashMap<String, String>()
        headers["type"] = "file"
        headers["action"] = "upload"
        headers["privacy"] = "undefined"
        headers["timestamp"] = java.lang.Long.toString(System.currentTimeMillis())
        headers["auth_token"] = authToken
        headers["nsfw"] = "0"
        //Matcher matcher = null;
        for (i in files.indices) {
            val file = files[i]
            val item = pending[i]

            file.requestName = "source"
            val builder = NetworkRequest.Builder()
                    .url(uploadUrl)
                    .formHeaders(headers)
                    .file(file)
            val response = webClient.request(builder.build(), item.itemProgressListener)

            val responseJson = json.decodeFromString(ImgbbResponseJson.serializer(), response.body)
            forpdateam.ru.forpda.common.Utils.longLog(responseJson.toString())
            if (responseJson.statusCode == 200) {
                val imageJson = responseJson.image
                item.name = imageJson?.filename ?: ""
                item.id = 0
                item.extension = imageJson?.extension ?: ""
                item.weight = imageJson?.sizeFormatted ?: ""
                item.typeFile = AttachmentItem.TYPE_IMAGE
                item.loadState = AttachmentItem.STATE_LOADED
                item.imageUrl = imageJson?.medium?.url ?: ""
                item.url = imageJson?.image?.url ?: ""
            }
        }

        return pending
    }

}
