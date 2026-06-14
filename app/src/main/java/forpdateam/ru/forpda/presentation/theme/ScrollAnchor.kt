package forpdateam.ru.forpda.presentation.theme

data class ScrollAnchor(
        val topicId: Int,
        val page: Int,
        val postId: Int?,
        val yOffset: Int?,
        val source: Source
) {
    enum class Source {
        InitialOpen,
        FindPost,
        Unread,
        ManualReload,
        RotationRestore
    }
}
