package forpdateam.ru.forpda.entity.remote.news

data class CommentKarmaVoteResult(
        val commentId: Int,
        val karma: Comment.Karma,
) {
    val likedByMe: Boolean
        get() = karma.status == Comment.Karma.LIKED
}
