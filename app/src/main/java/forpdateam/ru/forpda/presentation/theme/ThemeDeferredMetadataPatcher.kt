package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePost

/**
 * Maps deferred desktop/profile metadata merges to lightweight WebView DOM patches
 * instead of a full [ThemeViewModel] reload via [_onLoadData].
 */
internal object ThemeDeferredMetadataPatcher {

    data class PostMetadataUiState(
            val userPostCount: Int?,
            val postRating: String?,
            val canPlusPostRating: Boolean,
            val canMinusPostRating: Boolean,
    )

    fun snapshot(post: ThemePost): PostMetadataUiState =
            PostMetadataUiState(
                    userPostCount = post.userPostCount?.takeIf { it > 0 },
                    postRating = post.postRating?.takeIf { it.isNotBlank() },
                    canPlusPostRating = post.canPlusPostRating,
                    canMinusPostRating = post.canMinusPostRating,
            )

    fun snapshotByPostId(posts: Iterable<ThemePost>): Map<Int, PostMetadataUiState> =
            posts.associate { it.id to snapshot(it) }

    fun uiEvents(
            beforeByPostId: Map<Int, PostMetadataUiState>,
            posts: Iterable<ThemePost>,
    ): List<ThemeUiEvent> {
        val events = ArrayList<ThemeUiEvent>()
        posts.forEach { post ->
            val before = beforeByPostId[post.id] ?: return@forEach
            val after = snapshot(post)
            if (before.userPostCount != after.userPostCount && after.userPostCount != null) {
                events += ThemeUiEvent.PatchUserPostCountUi(
                        postId = post.id,
                        userPostCount = after.userPostCount,
                )
            }
            if (before.postRating != after.postRating ||
                    before.canPlusPostRating != after.canPlusPostRating ||
                    before.canMinusPostRating != after.canMinusPostRating
            ) {
                events += ThemeUiEvent.PatchPostRatingUi(
                        postId = post.id,
                        ratingText = after.postRating.orEmpty().ifBlank { "0" },
                        canPlus = after.canPlusPostRating,
                        canMinus = after.canMinusPostRating,
                )
            }
        }
        return events
    }
}
