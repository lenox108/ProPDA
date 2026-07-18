package forpdateam.ru.forpda.model.data.remote.api.editpost

import timber.log.Timber
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.editpost.EditPostForm
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.api.NetworkRequest
import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.model.data.remote.api.attachments.AttachmentsParser
import forpdateam.ru.forpda.common.encodeEditPostBodyForSubmit
import forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi
import forpdateam.ru.forpda.model.data.remote.api.theme.ThemeParser
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.*

private const val EDIT_POST_DIAG = "ForPDA.EditPost"

/**
 * Created by radiationx on 10.01.17.
 */

class EditPostApi(
        private val webClient: IWebClient,
        private val themeApi: ThemeApi,
        private val editPostParser: EditPostParser,
        private val attachmentsParser: AttachmentsParser,
        private val themeParser: ThemeParser
) {

    fun loadForm(postId: Int): EditPostForm {
        val url = "https://4pda.to/forum/index.php?act=post&do=edit_post&p=" + Integer.toString(postId)
        var response = webClient.get(url)
        if (response.body == "nopermission") {
            Timber.d("loadForm postId=%s result=nopermission", postId)
            return EditPostForm().apply {
                errorCode = EditPostForm.ERROR_NO_PERMISSION
            }
        }

        val body = response.body
        val bodyNorm = body.replace("&#45;", "-").replace("&#x2d;", "-").replace("&#X2D;", "-")
        Timber.d(
                "loadForm postId=%s bodyLen=%d hasEdTextarea=%s hasNamePost=%s",
                postId,
                body.length,
                Regex("(?i)ed-\\d+_textarea").containsMatchIn(bodyNorm),
                Regex("(?i)name\\s*=\\s*[\"']post[\"']").containsMatchIn(body)
        )
        val form = editPostParser.parseForm(body)
        form.poll = editPostParser.parsePoll(response.body)
        // Вложения на странице редактирования часто есть только в HTML формы, а не в ответе attach init —
        // иначе список в панели пустой и удалить файлы нельзя.
        val fromEditPage = attachmentsParser.parseAttachments(body)
        if (fromEditPage.isNotEmpty()) {
            form.attachments.addAll(fromEditPage)
            Timber.d("loadForm postId=%s attachmentsFromEditPage=%d", postId, fromEditPage.size)
        }
        return form
    }

    /** Отдельный запрос — при сбое/зависании не должен блокировать показ текста поста в редакторе. */
    fun loadEditAttachments(postId: Int): List<AttachmentItem> {
        val response = webClient.get("https://4pda.to/forum/index.php?act=attach&index=1&relId=$postId&maxSize=134217728&allowExt=&code=init&unlinked=")
        return attachmentsParser.parseAttachments(response.body)
    }

    suspend fun sendPost(form: EditPostForm, scrollTraceId: String? = null): ThemePage {
        val url = "https://4pda.to/forum/index.php"
        val headers = HashMap<String, String>()

        val builder = NetworkRequest.Builder()
                .url(url)
                .formHeaders(headers)
                .multipart()
                .formHeader("act", "Post")
                .formHeader("CODE", if (form.type == EditPostForm.TYPE_NEW_POST) "03" else "9")
                .formHeader("f", form.forumId.toString())
                .formHeader("t", form.topicId.toString())
                .formHeader("auth_key", webClient.getAuthKey())
                .formHeader("Post", encodeEditPostBodyForSubmit(form.message))
                .formHeader("enablesig", "yes")
                .formHeader("enableemo", "yes")
                .formHeader("st", form.st.toString())
                .formHeader("removeattachid", "0")
                .formHeader("MAX_FILE_SIZE", "0")
                .formHeader("parent_id", "0")
                .formHeader("ed-0_wysiwyg_used", "0")
                .formHeader("editor_ids[]", "ed-0")
                .formHeader("iconid", "0")
                .formHeader("_upload_single_file", "1")

        val poll = form.poll
        if (poll != null) {
            builder.formHeader("poll_question", poll.title.replace("\n".toRegex(), " "))
            for (i in 0 until poll.questions.size) {
                val question = poll.getQuestion(i) ?: continue
                val q_index = i + 1
                builder.formHeader("question[$q_index]", question.title.replace("\n".toRegex(), " "))
                builder.formHeader("multi[$q_index]", if (question.isMulti) "1" else "0")
                for (j in 0 until question.choices.size) {
                    val choice = question.getChoice(j)
                    val c_index = j + 1
                    choice?.let {
                        builder.formHeader("choice[$q_index${'_'}$c_index]", it.title.replace("\n".toRegex(), " "))
                    }
                }
            }
        }

        //.formHeader("file-list", addedFileList);
        if (form.type == EditPostForm.TYPE_EDIT_POST) {
            builder.formHeader("post_edit_reason", form.editReason)
        }
        val ids = StringBuilder()
        if (form.attachments.isNotEmpty()) {
            for (i in 0 until form.attachments.size) {
                val id = form.attachments[i].id
                ids.append(id)
                if (i < form.attachments.size - 1) {
                    ids.append(",")
                }
            }
        }
        builder.formHeader("file-list", ids.toString())
        if (form.postId != 0)
            builder.formHeader("p", form.postId.toString())

        android.util.Log.i(
                "ForPDA.AttachDel",
                "sendPost type=${form.type} postId=${form.postId} file-list=[${ids}] " +
                        "attachCount=${form.attachments.size} removeattachid=0 " +
                        "bodyHasAttachTag=${Regex("(?i)\\[attachment").containsMatchIn(form.message)} " +
                        "bodyHasDlUrl=${form.message.contains("/forum/dl/post/", ignoreCase = true)}"
        )
        val response = webClient.request(builder.build())
        val redirectUrl = response.redirectWithFragment.ifBlank { response.redirect }
        if (form.type == EditPostForm.TYPE_EDIT_POST) {
            return parseEditedPostPage(form, response.body, redirectUrl, scrollTraceId)
        }

        return parseNewPostPage(form, response.body, redirectUrl, scrollTraceId)
    }

    private suspend fun parseNewPostPage(
            form: EditPostForm,
            body: String,
            redirectUrl: String,
            scrollTraceId: String?
    ): ThemePage {
        val effectiveUrl = EditPostSubmitUrl.applySubmittedPageSt(
                if (redirectUrl.contains("showtopic=", ignoreCase = true)) {
                    redirectUrl
                } else {
                    "https://4pda.to/forum/index.php?showtopic=${form.topicId}&st=${form.st}"
                },
                form.st
        )
        val targetPostId = ThemeApi.extractScrollPostIdFromFinalTopicUrl(effectiveUrl)?.toIntOrNull()
        // POST response often contains only the new message while pagination footer is complete.
        // Parse it only to discover the posted entry id, then always load the full topic page at st.
        val previewPage = themeParser.parsePage(body, effectiveUrl, false, false, initialRequestUrl = effectiveUrl)
        ThemeApi.ensureScrollAnchorForPostedPage(previewPage, body, scrollTraceId)
        val anchorPostId = previewPage.anchor
                ?.removePrefix("entry")
                ?.takeIf { it.all(Char::isDigit) }
                ?.toIntOrNull()
        val scrollToPostId = targetPostId ?: anchorPostId

        val fullPageUrl = if (scrollToPostId == null) {
            "https://4pda.to/forum/index.php?showtopic=${form.topicId}&view=getlastpost"
        } else {
            EditPostSubmitUrl.buildPostedFullPageUrl(form.topicId, form.st, scrollToPostId)
        }
        var page = loadPostedThemePage(fullPageUrl, fullPageUrl)
        if (scrollToPostId != null && page.posts.none { it.id == scrollToPostId }) {
            val findPostUrl =
                    "https://4pda.to/forum/index.php?showtopic=${form.topicId}&view=findpost&p=$scrollToPostId"
            page = loadPostedThemePage(findPostUrl, findPostUrl)
        }
        applyPostedScrollAnchor(page, scrollToPostId, scrollTraceId)

        val canonicalUrl = EditPostSubmitUrl.applySubmittedPageSt(
                page.url?.takeIf { it.contains("showtopic=", ignoreCase = true) } ?: effectiveUrl,
                form.st
        )
        page.url = canonicalUrl
        themeApi.mergeDesktopRatingsIntoPage(page, canonicalUrl)
        return page
    }

    private fun loadPostedThemePage(requestUrl: String, initialRequestUrl: String): ThemePage {
        val response = webClient.get(requestUrl)
        val loadedUrl = response.redirectWithFragment
                .ifBlank { response.redirect }
                .takeIf { it.contains("showtopic=", ignoreCase = true) }
                ?: requestUrl
        return themeParser.parsePage(response.body, loadedUrl, false, false, initialRequestUrl = initialRequestUrl)
    }

    private fun applyPostedScrollAnchor(page: ThemePage, scrollToPostId: Int?, scrollTraceId: String?) {
        if (scrollToPostId != null) {
            page.anchors.clear()
            page.addAnchor("entry$scrollToPostId")
            page.anchorPostId = scrollToPostId.toString()
            return
        }
        ThemeApi.ensureScrollAnchorForPostedPage(page, null, scrollTraceId)
    }

    private suspend fun parseEditedPostPage(
            form: EditPostForm,
            body: String,
            redirectUrl: String,
            scrollTraceId: String?
    ): ThemePage {
        val effectiveUrl = normalizeEditRedirectUrl(form, redirectUrl)
        var page = themeParser.parsePage(body, effectiveUrl, false, false, initialRequestUrl = effectiveUrl)
        val targetPostId = editedTargetPostId(form, page.url.orEmpty())

        if (targetPostId > 0 && page.posts.none { it.id == targetPostId }) {
            val response = webClient.get(effectiveUrl)
            val loadedUrl = response.redirectWithFragment
                    .ifBlank { response.redirect }
                    .takeIf { it.contains("showtopic=", ignoreCase = true) }
                    ?: effectiveUrl
            page = themeParser.parsePage(response.body, loadedUrl, false, false, initialRequestUrl = effectiveUrl)
        }

        forceEditedPostAnchor(page, targetPostId, scrollTraceId)
        themeApi.mergeDesktopRatingsIntoPage(page, page.url ?: effectiveUrl)
        return page
    }

    /**
     * После сохранения редактирования Location часто даёт `showtopic` с неверным `st` (например 0),
     * без фрагмента `#entry…`. Тогда парсер получает лист «не той» страницы — якорь поста нет в DOM,
     * скролл срывается на первое сообщение. Явный `view=findpost&p=` совпадает с поиском/уведомлениями
     * и стабильно открывает страницу с нужным постом.
     */
    private fun normalizeEditRedirectUrl(form: EditPostForm, redirectUrl: String): String {
        if (form.topicId > 0 && form.postId > 0) {
            return buildEditTargetUrl(form)
        }
        val fallback = buildEditTargetUrl(form)
        if (!redirectUrl.contains("showtopic=", ignoreCase = true)) return fallback

        val postIdFromRedirect = ThemeApi.extractScrollPostIdFromFinalTopicUrl(redirectUrl)
        var result = if (hasQueryParam(redirectUrl, "st")) {
            redirectUrl
        } else {
            appendQueryBeforeFragment(redirectUrl, "st=${form.st.coerceAtLeast(0)}")
        }
        if (postIdFromRedirect == null && form.postId > 0 && !result.contains('#')) {
            result += "#entry${form.postId}"
        }
        return result
    }

    private fun buildEditTargetUrl(form: EditPostForm): String {
        val tid = form.topicId
        val pid = form.postId
        if (tid > 0 && pid > 0) {
            return "https://4pda.to/forum/index.php?showtopic=$tid&view=findpost&p=$pid"
        }
        val safeSt = form.st.coerceAtLeast(0)
        val anchor = if (pid > 0) "#entry$pid" else ""
        return "https://4pda.to/forum/index.php?showtopic=$tid&st=$safeSt$anchor"
    }

    private fun editedTargetPostId(form: EditPostForm, url: String): Int {
        return ThemeApi.extractScrollPostIdFromFinalTopicUrl(url)?.toIntOrNull()
                ?: form.postId
    }

    private fun forceEditedPostAnchor(page: ThemePage, postId: Int, scrollTraceId: String?) {
        if (postId <= 0) {
            ThemeApi.ensureScrollAnchorForPostedPage(page, null, scrollTraceId)
            return
        }
        page.anchors.clear()
        page.addAnchor("entry$postId")
        page.anchorPostId = postId.toString()
    }

    private fun hasQueryParam(url: String, name: String): Boolean {
        val query = url.substringAfter('?', missingDelimiterValue = "")
                .substringBefore('#')
        if (query.isEmpty()) return false
        return query.split('&').any { it.substringBefore('=') == name }
    }

    private fun appendQueryBeforeFragment(url: String, queryPart: String): String {
        val base = url.substringBefore('#')
        val fragment = url.substringAfter('#', missingDelimiterValue = "")
        val separator = if (base.contains('?')) "&" else "?"
        val result = "$base$separator$queryPart"
        return if (fragment.isEmpty()) result else "$result#$fragment"
    }

}

/**
 * После POST сервер часто редиректит на `showtopic` без `st` (страница 1), хотя ответ
 * отправляли с текущего `st` — восстанавливаем смещение страницы в URL темы.
 */
internal object EditPostSubmitUrl {
    fun buildPostedFullPageUrl(topicId: Int, submittedSt: Int, scrollToPostId: Int): String {
        val safeSt = submittedSt.coerceAtLeast(0)
        val base = if (safeSt > 0) {
            "https://4pda.to/forum/index.php?showtopic=$topicId&st=$safeSt"
        } else {
            "https://4pda.to/forum/index.php?showtopic=$topicId"
        }
        return "$base#entry$scrollToPostId"
    }

    fun applySubmittedPageSt(url: String, submittedSt: Int): String {
        if (submittedSt <= 0 || !url.contains("showtopic=", ignoreCase = true)) return url
        val hashIndex = url.indexOf('#')
        val withoutHash = if (hashIndex >= 0) url.substring(0, hashIndex) else url
        val hash = if (hashIndex >= 0) url.substring(hashIndex) else ""
        val query = withoutHash.substringAfter('?', "")
        if (query.isEmpty()) {
            return "$withoutHash?st=$submittedSt$hash"
        }
        val params = linkedMapOf<String, String>()
        query.split('&').forEach { part ->
            if (part.isBlank()) return@forEach
            val name = part.substringBefore('=')
            val value = part.substringAfter('=', "")
            params[name] = value
        }
        val existingSt = params["st"]?.toIntOrNull() ?: 0
        if (existingSt > 0) return url
        params["st"] = submittedSt.toString()
        val base = withoutHash.substringBefore('?')
        val newQuery = params.entries.joinToString("&") { "${it.key}=${it.value}" }
        return "$base?$newQuery$hash"
    }
}
