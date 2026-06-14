package forpdateam.ru.forpda.ui.fragments.topics

object TopicListMetadataFormatter {

    fun format(author: String?, pages: Int): String {
        val cleanAuthor = author.orEmpty().trim()
        val pagesText = formatPages(pages)
        return when {
            cleanAuthor.isNotEmpty() && pagesText != null -> "$cleanAuthor • $pagesText"
            cleanAuthor.isNotEmpty() -> cleanAuthor
            pagesText != null -> pagesText
            else -> ""
        }
    }

    fun format(lastPostAuthor: String?, fallbackAuthor: String?, pages: Int): String {
        return format(lastPostAuthor?.takeIf { it.isNotBlank() } ?: fallbackAuthor, pages)
    }

    private fun formatPages(pages: Int): String? {
        return pages.takeIf { it > 1 }?.let { "$it стр." }
    }
}
