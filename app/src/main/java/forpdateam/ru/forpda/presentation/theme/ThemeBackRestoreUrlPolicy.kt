package forpdateam.ru.forpda.presentation.theme

/**
 * Builds a clean back-restore URL: topic st offset plus optional #entry post anchor.
 * Avoids findpost/getnewpost/p= params that make the server redirect away from saved position.
 */
object ThemeBackRestoreUrlPolicy {

    fun buildRestoreUrl(topicId: Int, st: Int, anchorPostId: String?, pageUrl: String?): String {
        val cleanUrl = buildCleanThemeUrl(topicId, st)
        val postId = anchorPostId
                ?.removePrefix("entry")
                ?.takeIf { it.all(Char::isDigit) }
                ?: extractEntryPostIdFromUrl(pageUrl)
                ?: return cleanUrl
        return "$cleanUrl#entry$postId"
    }

    fun buildCleanThemeUrl(topicId: Int, st: Int): String {
        val safeSt = if (st < 0) 0 else st
        return if (safeSt > 0) {
            "https://4pda.to/forum/index.php?showtopic=$topicId&st=$safeSt"
        } else {
            "https://4pda.to/forum/index.php?showtopic=$topicId"
        }
    }

    fun extractEntryPostIdFromUrl(url: String?): String? {
        val hash = url?.substringAfter('#', "")?.takeIf { it.isNotBlank() } ?: return null
        return hash.removePrefix("entry").takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
    }
}
