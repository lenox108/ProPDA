package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import forpdateam.ru.forpda.entity.remote.theme.IThemePost

/**
 * Maps a parsed post ([IThemePost]) to the native adapter's [NativePostItem], running its
 * body HTML through [PostBodyRenderer] (roadmap `native-topic-renderer.md`, Фаза 1).
 *
 * Deliberately depends on the Android-free [IThemePost] interface (not the concrete
 * `ThemePost`, which pulls in `android.util.Pair`), so the whole post→item pipeline —
 * including block segmentation — stays JVM-unit-testable without Robolectric.
 */
class NativePostMapper(
    private val bodyRenderer: PostBodyRenderer = PostBodyRenderer(),
) {

    fun map(post: IThemePost): NativePostItem = NativePostItem(
        postId = post.id,
        topicId = post.topicId,
        number = post.number,
        userId = post.userId,
        nick = post.nick,
        avatarUrl = post.avatar,
        group = post.group,
        groupColor = post.groupColor,
        date = post.date,
        reputation = post.reputation,
        // userPostCount lives only on the concrete ThemePost (populated by getTheme's profile merge);
        // read it via a safe cast so the mapper still compiles against the Android-free interface.
        userPostCount = (post as? forpdateam.ru.forpda.entity.remote.theme.ThemePost)?.userPostCount,
        postRating = post.postRating,
        isCurator = post.isCurator,
        isOnline = post.isOnline,
        blocks = bodyRenderer.render(post.body),
        rawBodyHtml = post.body,
        canEdit = post.canEdit,
        canDelete = post.canDelete,
        canQuote = post.canQuote,
        canReport = post.canReport,
        canPlusRep = post.canPlusRep,
        canMinusRep = post.canMinusRep,
        canPlusPostRating = post.canPlusPostRating,
        canMinusPostRating = post.canMinusPostRating,
    )

    fun map(posts: List<IThemePost>): List<NativePostItem> = posts.map(::map)
}
