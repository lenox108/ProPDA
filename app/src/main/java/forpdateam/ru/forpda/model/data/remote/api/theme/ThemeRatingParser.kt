package forpdateam.ru.forpda.model.data.remote.api.theme

object ThemeRatingParser {

    data class PostVoteControls(
            val canPlus: Boolean,
            val canMinus: Boolean
    )

    fun parsePostRatings(response: String): Map<Int, String> {
        parsePostRatingsFromKaP(response).takeIf { it.isNotEmpty() }?.let { return it }

        val ratings = mutableMapOf<Int, String>()
        forEachPostHtml(response) { postId, postHtml ->
            extractPostRating(postHtml, postId)?.let {
                ratings[postId] = it
            }
        }
        return ratings
    }

    fun parsePostVoteControls(response: String): Map<Int, PostVoteControls> {
        val controls = parsePostVoteControlsFromKaP(response).toMutableMap()
        forEachPostHtml(response) { postId, postHtml ->
            val postActionStart = postActionStartRegex.find(postHtml)?.range?.first
            val controlsHtml = postActionStart?.let { postHtml.substring(it) } ?: postHtml
            val signs = voteControlLinkForPostRegex(postId)
                    .findAll(controlsHtml)
                    .mapNotNull {
                        val controlHtml = it.value
                        if (postActionStart == null && winReputationTypeRegex.containsMatchIn(controlHtml)) {
                            null
                        } else {
                            voteControlSign(controlHtml)
                        }
                    }
                    .toSet()
            val canPlus = signs.contains(1)
            val canMinus = signs.contains(-1)
            if (canPlus || canMinus) {
                controls[postId] = PostVoteControls(canPlus = canPlus, canMinus = canMinus)
            }
        }
        return controls
    }

    private fun parsePostVoteControlsFromKaP(response: String): Map<Int, PostVoteControls> {
        val body = kaPBodyRegex.find(response)?.groupValues?.getOrNull(1) ?: return emptyMap()
        val result = HashMap<Int, PostVoteControls>()
        kaPEntryRegex.findAll(body).forEach { m ->
            val postId = m.groupValues.getOrNull(1)?.toIntOrNull() ?: return@forEach
            val voteFlag = m.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }
            // Legacy ka_p used [userVote, total, canVote]. When the third flag is omitted, assume voting is allowed.
            val canVote = voteFlag?.let { parseKaPVoteAllowed(it) } ?: true
            if (canVote) {
                result[postId] = PostVoteControls(canPlus = true, canMinus = true)
            }
        }
        return result
    }

    private fun forEachPostHtml(response: String, block: (postId: Int, postHtml: String) -> Unit) {
        val startsByPostId = linkedMapOf<Int, Int>()
        postStartRegex.findAll(response).forEach { match ->
            val id = (1..3)
                    .asSequence()
                    .mapNotNull { index -> match.groupValues.getOrNull(index)?.toIntOrNull() }
                    .firstOrNull()
                    ?: return@forEach
            if (!startsByPostId.containsKey(id)) {
                startsByPostId[id] = match.range.first
            }
        }

        val starts = startsByPostId.entries.sortedBy { it.value }
        if (starts.isEmpty()) return

        starts.forEachIndexed { index, entry ->
            val end = starts.getOrNull(index + 1)?.value ?: response.length
            if (entry.value >= end) return@forEachIndexed
            block(entry.key, response.substring(entry.value, end))
        }
    }

    fun parsePostRatingsFromKaP(response: String): Map<Int, String> {
        val body = kaPBodyRegex.find(response)?.groupValues?.getOrNull(1) ?: return emptyMap()
        val result = HashMap<Int, String>()
        kaPEntryRegex.findAll(body).forEach { m ->
            val postId = m.groupValues.getOrNull(1)?.toIntOrNull() ?: return@forEach
            val ratingTotal = m.groupValues.getOrNull(2)?.toIntOrNull() ?: return@forEach
            result[postId] = ratingTotal.toSignedRatingString()
        }
        return result
    }

    fun countKaPEntries(response: String): Int {
        val body = kaPBodyRegex.find(response)?.groupValues?.getOrNull(1) ?: return 0
        return kaPEntryRegex.findAll(body).count()
    }

    fun countRatingMarkers(response: String): Int {
        return countRatingTextMarkers(response) + countVoteControlMarkers(response)
    }

    fun countRatingTextMarkers(response: String): Int {
        return ratingMarkerRegex.findAll(response).count()
    }

    fun countVoteControlMarkers(response: String): Int {
        return zkaLinkRegex.findAll(response).count()
    }

    /**
     * Diagnostics for "desktop rating controls exist in HTML?".
     * Keep counts only (no snippets) to avoid leaking user/content data into logs.
     */
    fun countDiagnosticsMarkers(response: String): RatingDiagnosticsMarkers {
        return RatingDiagnosticsMarkers(
                zkaPhp = zkaPhpRegex.findAll(response).count(),
                voteTotal = voteTotalRegex.findAll(response).count(),
                postAction = postActionBlockRegex.findAll(response).count(),
                kaId = kaIdRegex.findAll(response).count(),
                ratingLabel = ratingMarkerRegex.findAll(response).count(),
                ratePost = ratePostRegex.findAll(response).count(),
                repPostControls = repPostControlsRegex.findAll(response).count(),
        )
    }

    data class RatingDiagnosticsMarkers(
            val zkaPhp: Int,
            val voteTotal: Int,
            val postAction: Int,
            val kaId: Int,
            val ratingLabel: Int,
            val repPostControls: Int,
            val ratePost: Int,
    )

    private fun extractPostRating(postHtml: String, postId: Int): String? {
        // Only explicit "Рейтинг..." in title/aria on the whole post — never generic data-* here:
        // profile/user rows often carry data-reputation / scores unrelated to post karma.
        extractRatingFromLabeledAttributes(postHtml)?.let { return it }
        extractRatingFromKnownElements(postHtml, postId)?.let { return it }
        extractRatingFromVoteControls(postHtml, postId)?.let { return it }
        extractRatingFromPostAction(postHtml, postId)?.let { return it }

        val text = postHtml
                .replace(scriptRegex, " ")
                .replace(styleRegex, " ")
                .replace(brRegex, " ")
                .replace(tagRegex, " ")
                .decodeHtmlEntities()
                .replace(Regex("\\s+"), " ")
                .trim()

        return postRatingTextRegex.find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.replace(" ", "")
                ?.replace('−', '-')
                ?.replace('–', '-')
                ?.takeIf { it.isNotBlank() }
    }

    private fun extractRatingFromLabeledAttributes(postHtml: String): String? {
        return ratingAttributeRegex.find(postHtml)
                ?.groupValues
                ?.getOrNull(2)
                ?.normalizeRating()
    }

    /** Plain data-* hints allowed only inside action UI (e.g. post_action), not profile/header. */
    private fun extractRatingFromPostActionDataAttributes(postActionHtml: String): String? {
        return postActionPlainRatingAttributeRegex.find(postActionHtml)
                ?.groupValues
                ?.getOrNull(2)
                ?.normalizeRating()
    }

    private fun extractRatingFromKnownElements(postHtml: String, postId: Int): String? {
        val idRegex = Regex(
                """(?is)<[^>]+(?:id|class|data-[\w-]+)\s*=\s*["'][^"']*(?:ka[_-]?$postId|vote[_-]?total|post[-_]?rating|rating[-_]?post|postrating)[^"']*["'][^>]*>([\s\S]{0,180}?)</[^>]+>"""
        )
        return idRegex.find(postHtml)
                ?.groupValues
                ?.getOrNull(1)
                ?.stripHtml()
                ?.let { signedNumberRegex.find(it)?.value }
                ?.normalizeRating()
    }

    private fun extractRatingFromVoteControls(postHtml: String, postId: Int): String? {
        val voteLinks = voteControlLinkForPostRegex(postId).findAll(postHtml).toList()
        if (voteLinks.isEmpty()) return null

        val voteSigns = voteLinks.mapNotNull { voteControlSign(it.value) }.toSet()
        if (!voteSigns.contains(1) || !voteSigns.contains(-1)) return null

        val firstVoteLink = voteLinks.first()
        val from = (firstVoteLink.range.last + 1).coerceAtMost(postHtml.length)
        val to = voteLinks.getOrNull(1)?.range?.first
                ?: (from + VOTE_CONTROL_LOOKAROUND).coerceAtMost(postHtml.length)
        if (from >= to) return null
        val window = postHtml.substring(from, to)

        extractRatingFromKnownElements(window, postId)?.let { return it }

        val text = window.stripHtml()
        postRatingTextRegex.find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.normalizeRating()
                ?.let { return it }
        if (voteLinks.any { winReputationTypeRegex.containsMatchIn(it.value) }) return null

        return signedNumberRegex.findAll(text)
                .map { it.value.normalizeRating() }
                .firstOrNull { it != null && it != "1" && it != "-1" && it != postId.toString() }
    }

    private fun extractRatingFromPostAction(postHtml: String, postId: Int): String? {
        val postActionBlock = postActionBlockRegex.find(postHtml)?.value ?: return null
        extractRatingFromLabeledAttributes(postActionBlock)?.let { return it }
        extractRatingFromPostActionDataAttributes(postActionBlock)?.let { return it }
        extractRatingFromKnownElements(postActionBlock, postId)?.let { return it }
        return extractRatingFromVoteControls(postActionBlock, postId)
    }

    private fun String.stripHtml(): String {
        return replace(scriptRegex, " ")
                .replace(styleRegex, " ")
                .replace(brRegex, " ")
                .replace(tagRegex, " ")
                .decodeHtmlEntities()
                .replace(Regex("\\s+"), " ")
                .trim()
    }

    private fun String.normalizeRating(): String? {
        return replace(" ", "")
                .replace('−', '-')
                .replace('–', '-')
                .takeIf { it.isNotBlank() }
    }

    private fun Int.toSignedRatingString(): String = when {
        this > 0 -> "+$this"
        else -> toString()
    }

    private fun String.decodeHtmlEntities(): String {
        return replace("&nbsp;", " ")
                .replace("&#160;", " ")
                .replace("&amp;", "&")
                .replace("&minus;", "-")
                .replace("&#8722;", "-")
                .replace("&ndash;", "-")
                .replace("&#8211;", "-")
                .replace("&plus;", "+")
    }

    private fun parseKaPVoteAllowed(value: String): Boolean {
        return value.equals("true", ignoreCase = true) || value == "1"
    }

    private fun voteControlSign(voteControlHtml: String): Int? {
        val normalized = voteControlHtml.decodeHtmlEntities()
        return when {
            negativeVoteValueRegex.containsMatchIn(normalized) -> -1
            negativeWinReputationTypeRegex.containsMatchIn(normalized) -> -1
            positiveVoteValueRegex.containsMatchIn(normalized) -> 1
            positiveWinReputationTypeRegex.containsMatchIn(normalized) -> 1
            else -> null
        }
    }

    private val postStartRegex = Regex(
            """(?is)(?:\b(?:id|name)\s*=\s*["']entry(\d+)["']|\bdata-(?:post-id|post)\s*=\s*["'](\d+)["']|\bdata-entry-pid\s*=\s*["'](\d+)["'])"""
    )
    private val scriptRegex = Regex("""(?is)<script\b[^>]*>.*?</script>""")
    private val styleRegex = Regex("""(?is)<style\b[^>]*>.*?</style>""")
    private val brRegex = Regex("""(?i)<br\s*/?>""")
    private val tagRegex = Regex("""<[^>]+>""")
    private fun voteControlLinkForPostRegex(postId: Int) = Regex(
            """(?is)(?:zka\.php\?[^"']*\bi=$postId\b[^"']*|\b(?:act|do)\s*=\s*(?:["'])?(?:rep|votepost)(?:["'])?[^"']*\b(?:type|mode)\s*=\s*(?:["'])?post(?:["'])?[^"']*\b(?:i|pid|post|p)\s*=\s*$postId\b[^"']*|\bact\s*=\s*(?:["'])?rep(?:["'])?[^"']*(?=[^"']*\bp\s*=\s*$postId\b)(?=[^"']*\btype\s*=\s*(?:["'])?win_(?:add|minus)\b)[^"']*)"""
    )

    private const val VOTE_CONTROL_LOOKAROUND = 900

    private val postRatingTextRegex = Regex("""(?i)(?:Рейтинг(?:\s+(?:поста|сообщения))?|Rating(?:\s+post)?)\s*:\s*([+\-−–]?\s*\d+)""")
    private val ratingMarkerRegex = Regex("""(?i)(?:Рейтинг(?:\s+(?:поста|сообщения))?|Rating(?:\s+post)?)\s*:""")
    private val ratingAttributeRegex = Regex(
            """(?is)\b(?:title|aria-label|data-title|data-rating-title)\s*=\s*(["'])[^"']*(?:Рейтинг(?:\s+(?:поста|сообщения))?|Rating(?:\s+post)?)\s*:\s*([+\-−–]?\s*\d+)[^"']*\1"""
    )
    /**
     * Avoid data-reputation / data-score / member stats — those track user reputation, not post karma.
     * Keep only attributes that forums attach to the vote/rating control itself.
     */
    private val postActionPlainRatingAttributeRegex = Regex(
            """(?is)\b(?:data-rating|data-post-rating)\s*=\s*(["'])([+\-−–]?\s*\d+)\1"""
    )
    private val signedNumberRegex = Regex("""[+\-−–]?\s*\d+""")
    private val positiveVoteValueRegex = Regex("""(?i)(?:[?&;]|\b)v\s*=\s*(?:["'])?1\b""")
    private val negativeVoteValueRegex = Regex("""(?i)(?:[?&;]|\b)v\s*=\s*(?:["'])?-1\b""")
    private val positiveWinReputationTypeRegex = Regex("""(?i)\btype\s*=\s*(?:["'])?win_add\b""")
    private val negativeWinReputationTypeRegex = Regex("""(?i)\btype\s*=\s*(?:["'])?win_minus\b""")
    private val winReputationTypeRegex = Regex("""(?i)\btype\s*=\s*(?:["'])?win_(?:add|minus)\b""")
    private val zkaLinkRegex = Regex("""(?is)(?:zka\.php\?[^"']*\bi=\d+\b[^"']*|\b(?:act|do)\s*=\s*(?:["'])?(?:rep|votepost)(?:["'])?[^"']*\b(?:type|mode)\s*=\s*(?:["'])?post(?:["'])?[^"']*\b(?:i|pid|post|p)\s*=\s*\d+\b[^"']*|\bact\s*=\s*(?:["'])?rep(?:["'])?[^"']*(?=[^"']*\bp\s*=\s*\d+\b)(?=[^"']*\btype\s*=\s*(?:["'])?win_(?:add|minus)\b)[^"']*)""")
    private val postActionBlockRegex = Regex("""(?is)<span[^>]*\bclass\s*=\s*["'][^"']*\bpost_action\b[^"']*["'][^>]*>[\s\S]{0,1200}?</span>""")
    private val postActionStartRegex = Regex("""(?is)<span[^>]*\bclass\s*=\s*["'][^"']*\bpost_action\b[^"']*["'][^>]*>""")

    // Additional marker counters for troubleshooting (HTML contains controls but parser doesn't match).
    private val zkaPhpRegex = Regex("""(?i)\bzka\.php\b""")
    private val voteTotalRegex = Regex("""(?i)\bvote_total\b""")
    private val kaIdRegex = Regex("""(?i)\bka_\d+\b""")
    private val ratePostRegex = Regex("""(?i)\brate[-_]?post\b""")
    private val kaPBodyRegex = Regex("""(?is)\bka_p\s*=\s*\{([\s\S]*?)\}\s*;""")
    /**
     * Extract only <postId>:[x,y,...] where y is rating total.
     * We intentionally ignore anything outside ka_p to avoid confusing with profile reputation.
     */
    private val kaPEntryRegex = Regex("""(?s)\b(\d+)\s*:\s*\[\s*[-+]?\d+\s*,\s*([-+]?\d+)(?:\s*,\s*(true|false|1|0))?""")
    /**
     * Post-vote controls only (exclude act=rep view=history etc).
     * Count both "type=post&i=" and legacy "type=win_(add|minus)&p=" variants.
     */
    private val repPostControlsRegex = Regex(
            """(?is)\bact\s*=\s*(?:["'])?rep(?:["'])?[\s\S]{0,240}?\b(?:type|mode)\s*=\s*(?:["'])?(?:post|win_(?:add|minus))(?:["'])?[\s\S]{0,240}?\b(?:i|pid|post|p)\s*=\s*\d+\b"""
    )
}
