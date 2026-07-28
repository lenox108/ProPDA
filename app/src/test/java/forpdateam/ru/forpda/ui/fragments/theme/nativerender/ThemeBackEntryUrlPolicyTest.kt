package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import forpdateam.ru.forpda.ui.fragments.theme.nativerender.ThemeBackEntryUrlPolicy.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Жалоба: «жмёшь назад — не возвращаешься к посту, с которого ушёл, а попадаешь на последнюю страницу
 * темы, и она засчитывается прочитанной; так происходит не всегда». Причина — back-запись хранила
 * серверно-редиректный url открытия (`getnewpost`/`getlastpost`), который на «назад» резолвился заново,
 * уже по уехавшей границе прочитанного. Тесты фиксируют, что back целится в страницу поста-якоря.
 */
class ThemeBackEntryUrlPolicyTest {

    private val getNewPost = "https://4pda.to/forum/index.php?showtopic=42&view=getnewpost"
    private val plainPage = "https://4pda.to/forum/index.php?showtopic=42&st=200"

    @Test
    fun anchorWithKnownPage_targetsThatPage_notTheServerRedirect() {
        val target = ThemeBackEntryUrlPolicy.resolve(
                loadedUrl = getNewPost,
                anchorPostId = 144342027,
                anchorPostPage = 11,
                topicId = 42,
                paginationReady = true,
                loadedPage = 13,
        )
        // Страница ПОСТА (11), а не входная/самая глубокая загруженная (13) и не редирект.
        assertEquals(Target.Page(11), target)
    }

    @Test
    fun anchorOnDeeperInfiniteScrollPage_targetsThatDeeperPage() {
        // Ровно сценарий жалобы: тема открыта getnewpost'ом на стр. 10, юзер долистал до 13 и тапнул ник.
        val target = ThemeBackEntryUrlPolicy.resolve(
                loadedUrl = getNewPost,
                anchorPostId = 999,
                anchorPostPage = 13,
                topicId = 42,
                paginationReady = true,
                loadedPage = 13,
        )
        assertEquals(Target.Page(13), target)
    }

    @Test
    fun anchorWithoutPageTag_fallsBackToFindPost() {
        val target = ThemeBackEntryUrlPolicy.resolve(
                loadedUrl = getNewPost,
                anchorPostId = 555,
                anchorPostPage = 0,
                topicId = 42,
                paginationReady = true,
                loadedPage = 3,
        )
        assertEquals(Target.FindPost(topicId = 42, postId = 555), target)
    }

    @Test
    fun anchorWithUninitialisedPagination_fallsBackToFindPost() {
        val target = ThemeBackEntryUrlPolicy.resolve(
                loadedUrl = getNewPost,
                anchorPostId = 555,
                anchorPostPage = 7,
                topicId = 42,
                paginationReady = false, // пагинация чужой темы / ещё не сброшена → pageUrl доверять нельзя
                loadedPage = 1,
        )
        assertEquals(Target.FindPost(topicId = 42, postId = 555), target)
    }

    @Test
    fun noAnchor_onServerRedirectUrl_fallsBackToLoadedPage() {
        val target = ThemeBackEntryUrlPolicy.resolve(
                loadedUrl = getNewPost,
                anchorPostId = 0,
                anchorPostPage = 0,
                topicId = 42,
                paginationReady = true,
                loadedPage = 9,
        )
        assertEquals(Target.Page(9), target)
    }

    @Test
    fun noAnchor_onPlainPageUrl_keepsIt() {
        val target = ThemeBackEntryUrlPolicy.resolve(
                loadedUrl = plainPage,
                anchorPostId = 0,
                anchorPostPage = 0,
                topicId = 42,
                paginationReady = true,
                loadedPage = 9,
        )
        assertEquals(Target.KeepLoadedUrl, target)
    }

    @Test
    fun noAnchor_noPagination_keepsLoadedUrlEvenIfRedirecting() {
        val target = ThemeBackEntryUrlPolicy.resolve(
                loadedUrl = getNewPost,
                anchorPostId = 0,
                anchorPostPage = 0,
                topicId = 42,
                paginationReady = false,
                loadedPage = 0,
        )
        assertEquals(Target.KeepLoadedUrl, target)
    }

    @Test
    fun unknownTopicId_keepsLoadedUrl_ratherThanBuildingAFindPostForTopicZero() {
        val target = ThemeBackEntryUrlPolicy.resolve(
                loadedUrl = plainPage,
                anchorPostId = 555,
                anchorPostPage = 0,
                topicId = 0,
                paginationReady = false,
                loadedPage = 0,
        )
        assertEquals(Target.KeepLoadedUrl, target)
    }

    @Test
    fun serverAnchoredUrlDetection() {
        assertTrue(ThemeBackEntryUrlPolicy.isServerAnchoredOpenUrl(getNewPost))
        assertTrue(ThemeBackEntryUrlPolicy.isServerAnchoredOpenUrl(
                "https://4pda.to/forum/index.php?showtopic=42&view=GetLastPost"))
        assertFalse(ThemeBackEntryUrlPolicy.isServerAnchoredOpenUrl(plainPage))
        assertFalse(ThemeBackEntryUrlPolicy.isServerAnchoredOpenUrl(
                "https://4pda.to/forum/index.php?showtopic=42&view=findpost&p=555"))
    }
}
