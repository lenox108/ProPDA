package forpdateam.ru.forpda.model.data.remote.api.favorites

import java.util.Locale
import java.util.regex.Pattern
import forpdateam.ru.forpda.diagnostic.FavoritesUnreadTrace
import forpdateam.ru.forpda.entity.remote.favorites.FavData
import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.entity.remote.favorites.FavoriteReadState
import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.model.data.remote.ParserPatterns
import forpdateam.ru.forpda.model.data.remote.parser.BaseParser
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import forpdateam.ru.forpda.model.preferences.ForumPageSizeHolder

class FavoritesParser(
        private val patternProvider: IPatternProvider,
        private val pageSizeHolder: ForumPageSizeHolder,
        private val useJsoup: Boolean = false,
) : BaseParser() {

    private val scope = ParserPatterns.Favorites
    private val jsoupParser = FavoritesJsoupParser()

    fun parseFavorites(response: String): FavData =
            if (useJsoup) jsoupParser.parseFavorites(response)
            else parseFavoritesWithRegex(response)

    private fun parseFavoritesWithRegex(response: String): FavData = FavData().also { data ->
        val list = patternProvider
                .getPattern(scope.scope, scope.main)
                .matcher(response)
                .map { matcher ->
                    val rowHtml = matcher.group(0).orEmpty()
                    FavItem().apply {
                        isForum = matcher.group(19) != null

                        favId = matcher.group(1)?.toIntOrNull() ?: 0
                        trackType = matcher.group(2) ?: ""
                        isPin = matcher.group(3) == "1"

                        matcher.group(4)?.also {
                            infoColor = it
                        }

                        val modifierRegion = resolveModifierRegion(matcher.group(5), rowHtml)
                        val modifierText = stripHtmlForModifierFlags(modifierRegion)
                        var plusDigitsUnread: Int? = null
                        PLUS_COUNT_REGEX.find(modifierRegion)?.groupValues?.getOrNull(1)?.toIntOrNull()?.also {
                            plusDigitsUnread = it
                        }
                        isPoll = modifierText.contains("^")
                        isClosed = modifierText.contains("Х")

                        matcher.group(6)?.toIntOrNull()?.also {
                            if (isForum) {
                                forumId = it
                            } else {
                                topicId = it
                            }
                        }

                        listingHref = extractListingHref(rowHtml)

                        val rowClasses = extractRowClasses(rowHtml)
                        val unread = detectFavoriteRowUnread(
                                rowHtml = rowHtml,
                                modifierRegion = modifierRegion,
                                modifierText = modifierText
                        )
                        readState = unread.readState
                        isNew = unread.readState == FavoriteReadState.UNREAD
                        if (unread.plusCount != null) {
                            plusDigitsUnread = unread.plusCount
                        }
                        topicTitle = matcher.group(8)?.fromHtml()

                        if (isForum) {
                            date = matcher.group(19)
                            lastUserId = matcher.group(20)?.toIntOrNull() ?: 0
                            lastUserNick = matcher.group(21)?.fromHtml()
                        } else {
                            matcher.group(9)?.also {
                                stParam = it.toIntOrNull() ?: 0
                                pages = stParam / pageSizeHolder.getPostsPerPage() + 1
                            }
                            matcher.group(10)?.also {
                                desc = it.fromHtml()
                            }

                            forumId = matcher.group(12)?.toIntOrNull() ?: 0
                            forumTitle = matcher.group(13)?.fromHtml()
                            authorId = matcher.group(14)?.toIntOrNull() ?: 0
                            authorUserNick = matcher.group(15)?.fromHtml()
                            lastUserId = matcher.group(16)?.toIntOrNull() ?: 0
                            lastUserNick = matcher.group(17)?.fromHtml()
                            date = matcher.group(18)

                            matcher.group(22)?.also {
                                curatorId = it.toIntOrNull() ?: 0
                                curatorNick = matcher.group(23)?.fromHtml()
                            }

                            subType = matcher.group(24)?.trim()?.toLowerCase(Locale.ROOT) ?: ""

                            unreadPostCount = when {
                                readState == FavoriteReadState.UNREAD && plusDigitsUnread != null -> plusDigitsUnread ?: 0
                                readState == FavoriteReadState.UNREAD -> 1
                                else -> 0
                            }

                            if (readState == FavoriteReadState.UNKNOWN) {
                                FavoritesUnreadTrace.unreadStateUnknown(topicId, topicTitle)
                            }
                            FavoritesUnreadTrace.topicParsed(
                                    topicId = topicId,
                                    title = topicTitle,
                                    rowClasses = rowClasses,
                                    hasUnreadIcon = unread.hasUnreadIcon,
                                    rawUnreadMarkerFound = unread.markerSummary(),
                                    parsedReadState = readState.name,
                                    parsedIsUnread = isNew,
                                    unreadUrlPresent = hasUnreadListingHref(listingHref),
                                    unreadPostCount = unreadPostCount
                            )
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

    private fun resolveModifierRegion(modifierGroup: String?, rowHtml: String): String {
        val trimmedGroup = modifierGroup?.trim().orEmpty()
        if (trimmedGroup.isNotEmpty()) {
            return trimmedGroup
        }
        return MODIFIER_REGION_REGEX.find(rowHtml)?.groupValues?.getOrNull(1).orEmpty()
    }

    private fun stripHtmlForModifierFlags(modifierRegion: String): String {
        return modifierRegion
                .replace(HTML_TAG_REGEX, "")
                .replace("&nbsp;", " ")
                .trim()
    }

    companion object {
        private val PLUS_COUNT_REGEX = Regex("""\+(\d+)""")
        private val PLUS_BARE_REGEX = Regex("""\+""")
        private val HTML_TAG_REGEX = Regex("""<[^>]+>""")
        private val DATA_ITEM_ROW_REGEX = Regex(
                """<div[^>]*\bdata-item-fid\s*=([^>]*)>""",
                RegexOption.IGNORE_CASE
        )
        private val MODIFIER_REGION_REGEX = Pattern.compile(
                """class="[^"]*\b(?:modifier|forum_img_with_link)\b[^"]*"[^>]*>([\s\S]*?)</(?:span|a|div)>""",
                Pattern.CASE_INSENSITIVE
        ).toRegex()
        private val MODIFIER_UNREAD_CLASS_REGEX = Regex(
                """class="[^"]*\b(?:modifier\b[^"]*\bunread|unread\b[^"]*\bmodifier|forum_img_with_link\b[^"]*\bunread|unread\b[^"]*\bforum_img_with_link)\b""",
                RegexOption.IGNORE_CASE
        )
        private val ROW_UNREAD_CLASS_REGEX = Regex(
                """class="[^"]*\bunread\b[^"]*"""",
                RegexOption.IGNORE_CASE
        )
        private val NEW_POST_IMG_ALT_REGEX = Regex(
                """<img[^>]*\balt\s*=\s*["'](?:>|&gt;|&#62;)N["']""",
                RegexOption.IGNORE_CASE
        )
        private val SHOWTOPIC_TITLE_REGEX = Pattern.compile(
                """showtopic=\d+[^>]*>([\s\S]*?)</a>""",
                Pattern.CASE_INSENSITIVE
        ).toRegex()
        private val SHOWTOPIC_LISTING_HREF_REGEX = Regex(
                """<a[^>]*href=["']([^"']*showtopic=\d+[^"']*)["'][^>]*>""",
                RegexOption.IGNORE_CASE
        )
        private val BOLD_TITLE_REGEX = Regex("""<(?:strong|b)\b""", RegexOption.IGNORE_CASE)
        private val BOLD_STYLE_REGEX = Regex("""font-weight\s*:\s*(?:bold|[6-9]00)""", RegexOption.IGNORE_CASE)

        internal fun extractListingHref(rowHtml: String): String? {
            val links = SHOWTOPIC_LISTING_HREF_REGEX.findAll(rowHtml)
                    .mapNotNull { it.groupValues.getOrNull(1) }
                    .map { it.replace("&amp;", "&", ignoreCase = false) }
                    .toList()
            return links.firstOrNull { it.contains("view=getnewpost", ignoreCase = true) }
                    ?: links.firstOrNull { it.contains("view=getlastpost", ignoreCase = true) }
                    ?: links.firstOrNull()
        }

        internal fun extractRowClasses(rowHtml: String): String? {
            return DATA_ITEM_ROW_REGEX.find(rowHtml)?.groupValues?.getOrNull(1)
                    ?.let { attrs ->
                        Regex("""\bclass=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
                                .find(attrs)
                                ?.groupValues
                                ?.getOrNull(1)
                    }
        }

        internal fun hasUnreadListingHref(listingHref: String?): Boolean {
            val href = listingHref?.trim().orEmpty()
            return href.isNotEmpty() && (
                    href.contains("view=getnewpost", ignoreCase = true) ||
                            href.contains("view=getlastpost", ignoreCase = true)
                    )
        }

        internal fun detectFavoriteRowUnread(
                rowHtml: String,
                modifierRegion: String,
                modifierText: String
        ): FavoriteRowUnreadDetection {
            val markers = linkedSetOf<String>()
            val plusCount = PLUS_COUNT_REGEX.find(modifierRegion)?.groupValues?.getOrNull(1)?.toIntOrNull()
            var hasUnreadIcon = false
            if (plusCount != null) markers.add("plus_count")
            // Bare "+" only in modifier strip (not title/pagination); site puts view=getnewpost on all rows.
            if (plusCount == null &&
                    PLUS_BARE_REGEX.containsMatchIn(modifierText) &&
                    !modifierText.contains("^")
            ) {
                markers.add("plus_bare")
            }
            if (MODIFIER_UNREAD_CLASS_REGEX.containsMatchIn(rowHtml)) markers.add("modifier_unread_class")
            if (NEW_POST_IMG_ALT_REGEX.containsMatchIn(modifierRegion) ||
                    NEW_POST_IMG_ALT_REGEX.containsMatchIn(rowHtml)
            ) {
                markers.add("new_post_img")
                hasUnreadIcon = true
            }
            if (ROW_UNREAD_CLASS_REGEX.containsMatchIn(rowHtml) &&
                    MODIFIER_UNREAD_CLASS_REGEX.containsMatchIn(rowHtml)
            ) {
                markers.add("row_unread_class")
            }
            // 4pda marks unread favorites with <strong>/<b> around the title (not inline style on <a>).
            SHOWTOPIC_TITLE_REGEX.find(rowHtml)?.groupValues?.getOrNull(1)?.let { titleHtml ->
                if (BOLD_TITLE_REGEX.containsMatchIn(titleHtml) && !modifierText.contains("^")) {
                    markers.add("bold_title")
                }
            }

            if (markers.isNotEmpty()) {
                return FavoriteRowUnreadDetection(
                        readState = FavoriteReadState.UNREAD,
                        plusCount = plusCount,
                        markers = markers,
                        hasUnreadIcon = hasUnreadIcon
                )
            }

            return FavoriteRowUnreadDetection(
                    readState = FavoriteReadState.READ,
                    plusCount = plusCount,
                    markers = markers,
                    hasUnreadIcon = hasUnreadIcon
            )
        }
    }
}

internal data class FavoriteRowUnreadDetection(
        val readState: FavoriteReadState,
        val plusCount: Int?,
        val markers: Set<String> = emptySet(),
        val hasUnreadIcon: Boolean = false
) {
    fun markerSummary(): String? = markers.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(separator = ",")
}
