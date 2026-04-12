package forpdateam.ru.forpda.model.data.remote.api.favorites

import forpdateam.ru.forpda.entity.remote.favorites.FavData
import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.model.data.remote.ParserPatterns
import forpdateam.ru.forpda.model.data.remote.parser.BaseParser
import forpdateam.ru.forpda.model.data.storage.IPatternProvider

class FavoritesParser(
        private val patternProvider: IPatternProvider
) : BaseParser() {

    private val scope = ParserPatterns.Favorites

    fun parseFavorites(response: String): FavData = FavData().also { data ->
        val list = patternProvider
                .getPattern(scope.scope, scope.main)
                .matcher(response)
                .map { matcher ->
                    FavItem().apply {
                        isForum = matcher.group(19) != null

                        favId = matcher.group(1).toInt()
                        trackType = matcher.group(2)
                        isPin = matcher.group(3) == "1"

                        matcher.group(4)?.also {
                            infoColor = it
                        }

                        var plusDigitsUnread: Int? = null
                        matcher.group(5)?.also {
                            Regex("""\+(\d+)""").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { n ->
                                plusDigitsUnread = n
                            }
                            isPoll = it.contains("^")
                            isClosed = it.contains("Х")
                        }

                        matcher.group(6).toInt().also {
                            if (isForum) {
                                forumId = it
                            } else {
                                topicId = it
                            }
                        }

                        isNew = matcher.group(7) != null
                        topicTitle = matcher.group(8).fromHtml()

                        if (isForum) {
                            date = matcher.group(19)
                            lastUserId = matcher.group(20).toInt()
                            lastUserNick = matcher.group(21).fromHtml()
                        } else {
                            matcher.group(9)?.also {
                                stParam = it.toInt()
                                pages = stParam / 20 + 1
                            }
                            matcher.group(10)?.also {
                                desc = it.fromHtml()
                            }

                            forumId = matcher.group(12).toInt()
                            forumTitle = matcher.group(13).fromHtml()
                            authorId = matcher.group(14).toInt()
                            authorUserNick = matcher.group(15).fromHtml()
                            lastUserId = matcher.group(16).toInt()
                            lastUserNick = matcher.group(17).fromHtml()
                            date = matcher.group(18)

                            matcher.group(22)?.also {
                                curatorId = it.toInt()
                                curatorNick = matcher.group(23).fromHtml()
                            }

                            subType = matcher.group(24).trim().toLowerCase()

                            unreadPostCount = when {
                                isNew && plusDigitsUnread != null -> plusDigitsUnread!!
                                isNew -> 1
                                else -> 0
                            }
                        }
                    }
                }
        data.items.addAll(list)
        data.pagination = Pagination.parseForum(response)
        data.sorting = Sorting.parse(response)
        return data
    }

    fun checkIsComplete(result: String): Boolean {
        return patternProvider
                .getPattern(scope.scope, scope.check_action)
                .matcher(result)
                .find()
    }

    /**
     * Успех «пометить прочитанным» (tact=read). Полная страница — [checkIsComplete] (зелёный блок + #navstrip).
     * XHR часто отдаёт фрагмент без navstrip — расширяем эвристиками, иначе массовая пометка ломается и даёт «failed» через десятки секунд.
     */
    fun checkFavoritesReadComplete(result: String): Boolean {
        if (checkIsComplete(result)) {
            return true
        }
        if (looksLikeFavoritesReadFailure(result)) {
            return false
        }
        val r = result.lowercase()
        if (r.contains("dff0d8") || r.contains("d4edda") || r.contains("c3e6cb")) {
            return true
        }
        if (result.contains("alert-success", ignoreCase = true)) {
            return true
        }
        if (r.contains("message-success") || r.contains("ipsmessage_success")) {
            return true
        }
        val compact = result.replace(Regex("\\s+"), " ").lowercase()
        if (compact.contains("помечен") && compact.contains("прочитан")) {
            return true
        }
        if (compact.contains("marked") && compact.contains("read")) {
            return true
        }
        return false
    }

    private fun looksLikeFavoritesReadFailure(result: String): Boolean {
        val r = result.lowercase()
        if (r.contains("f2dede") || r.contains("f8d7da") || r.contains("ebccd1")) {
            return true
        }
        if (result.contains("alert-danger", ignoreCase = true) || result.contains("alert-error", ignoreCase = true)) {
            return true
        }
        if (result.contains("ipsmessage_error", ignoreCase = true)) {
            return true
        }
        return false
    }

}
