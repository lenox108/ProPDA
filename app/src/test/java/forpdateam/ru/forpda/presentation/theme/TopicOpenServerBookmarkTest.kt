package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.Preferences as AppPreferences
import forpdateam.ru.forpda.common.TopicOpenListHints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «Серверная закладка» ([AppPreferences.Main.TopicOpenTarget.SERVER_BOOKMARK]): единственный источник
 * позиции — отметка прочитанного на 4PDA. Всегда `view=getnewpost`, списочные хинты игнорируются,
 * клиентская граница прочитанного якорь не переопределяет.
 */
class TopicOpenServerBookmarkTest {

    private val setting = AppPreferences.Main.TopicOpenTarget.SERVER_BOOKMARK

    private fun resolve(
            url: String,
            sourceScreen: String = "favorites",
            listTopicMarkedUnread: Boolean = false,
            unreadUrlFromList: String? = null,
            lastReadUrlFromList: String? = null,
    ) = TopicOpenTargetResolver.resolve(
            TopicOpenContext(
                    rawUrl = url,
                    setting = setting,
                    sourceScreen = sourceScreen,
                    listTopicMarkedUnread = listTopicMarkedUnread,
                    unreadUrlFromList = unreadUrlFromList,
                    lastReadUrlFromList = lastReadUrlFromList,
            )
    )

    @Test
    fun plainTopicGetsGetNewPost() {
        val resolution = resolve("https://4pda.to/forum/index.php?showtopic=123")
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=123&view=getnewpost",
                resolution.url
        )
        assertEquals(TopicOpenTargetType.SETTING_SERVER_BOOKMARK, resolution.targetType)
        assertTrue(resolution.suppressScrollRestore)
    }

    /** Прочитанная строка списка НЕ уходит в getlastpost (это ветка LAST_UNREAD) — сервер решает сам. */
    @Test
    fun readListRowStillUsesGetNewPost() {
        val resolution = resolve(
                url = "https://4pda.to/forum/index.php?showtopic=123",
                lastReadUrlFromList = "https://4pda.to/forum/index.php?showtopic=123&view=getlastpost",
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=123&view=getnewpost",
                resolution.url
        )
        assertEquals(TopicOpenTargetType.SETTING_SERVER_BOOKMARK, resolution.targetType)
    }

    /** Непрочитанная строка тоже идёт через голый getnewpost, без подстановки списочного URL. */
    @Test
    fun unreadListRowIgnoresListUrl() {
        val resolution = resolve(
                url = "https://4pda.to/forum/index.php?showtopic=123",
                listTopicMarkedUnread = true,
                unreadUrlFromList = "https://4pda.to/forum/index.php?showtopic=123&st=200&view=getnewpost",
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=123&view=getnewpost",
                resolution.url
        )
        assertEquals(TopicOpenTargetType.SETTING_SERVER_BOOKMARK, resolution.targetType)
    }

    /** Резюмный `st=` из href списка — не явная пагинация: его надо срезать, как и под LAST_UNREAD. */
    @Test
    fun listResumeStIsStripped() {
        val resolution = resolve("https://4pda.to/forum/index.php?showtopic=123&st=340")
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=123&view=getnewpost",
                resolution.url
        )
    }

    /** Явная страница из ссылки/пагинации остаётся явной. */
    @Test
    fun explicitPaginationPageIsHonored() {
        val url = "https://4pda.to/forum/index.php?showtopic=123&st=340"
        val resolution = resolve(url, sourceScreen = "pagination")
        assertEquals(url, resolution.url)
        assertEquals(TopicOpenTargetType.EXPLICIT_PAGE, resolution.targetType)
    }

    /** Закладка/упоминание на конкретный пост открывается на нём, а не по серверной отметке. */
    @Test
    fun explicitPostOpenWins() {
        val url = "https://4pda.to/forum/index.php?showtopic=123&view=findpost&p=555"
        val resolution = resolve(url, sourceScreen = "bookmark")
        assertEquals(TopicOpenTargetType.EXPLICIT_POST, resolution.targetType)
        assertEquals(555, resolution.resolvedPostId)
    }

    /**
     * Парсерный хинт «доверять списочному unread» гасится: он существует, чтобы отвергать нижний
     * редирект сервера как возможную закладку «всё прочитано», а этот режим на неё как раз и садится.
     */
    @Test
    fun parserUnreadHintIsOff() {
        val hints = TopicOpenListHints(
                unreadUrlFromList = "https://4pda.to/forum/index.php?showtopic=123&view=getnewpost",
                topicMarkedUnread = true,
        )
        val url = "https://4pda.to/forum/index.php?showtopic=123&view=getnewpost"
        assertFalse(TopicUnreadOpenPolicy.parserTrustsGetNewPostUnread(hints, url, setting))
        assertFalse(TopicUnreadOpenPolicy.prefetchParserHint(hints, url, setting))
        // LAST_UNREAD не задет
        assertTrue(
                TopicUnreadOpenPolicy.parserTrustsGetNewPostUnread(
                        hints, url, AppPreferences.Main.TopicOpenTarget.LAST_UNREAD
                )
        )
    }

    /** Ветка списочного открытия принадлежит LAST_UNREAD — здесь она не участвует. */
    @Test
    fun listOpenPolicyDoesNotApply() {
        val info = ThemeUrlPolicy.parse("https://4pda.to/forum/index.php?showtopic=123")!!
        val context = TopicOpenContext(
                rawUrl = "https://4pda.to/forum/index.php?showtopic=123",
                setting = setting,
                sourceScreen = "favorites",
                listTopicMarkedUnread = true,
        )
        assertEquals(null, TopicUnreadOpenPolicy.resolveListOpen(context, info))
    }

    /** Сохранённый скролл не должен перебивать серверный якорь на свежем открытии. */
    @Test
    fun savedScrollIsSuppressedOnFreshOpen() {
        assertFalse(
                TopicOpenScrollRestorePolicy.savedScrollRestoreAllowed(
                        openIntentRaw = TopicOpenIntentClassifier.FRESH_FAVORITES,
                        setting = setting,
                        loadAction = ThemeLoadAction.Normal,
                )
        )
        assertTrue(
                TopicOpenScrollRestorePolicy.shouldSuppressScrollRestoreOnRender(
                        suppressScrollRestoreForOpen = false,
                        pendingUnreadOpenSuppressScroll = false,
                        loadAction = ThemeLoadAction.Normal,
                        hasActiveRefreshRestore = false,
                        themeUrl = "https://4pda.to/forum/index.php?showtopic=123&view=getnewpost",
                        topicOpenTarget = setting,
                )
        )
    }

    /** Открытие вглубь темы → инлайн-шапка при возврате на стр. 1 остаётся свёрнутой, как под LAST_UNREAD. */
    @Test
    fun inlineHatStaysCollapsedOnInSessionPageOne() {
        val page = forpdateam.ru.forpda.entity.remote.theme.ThemePage().apply { id = 123 }
        assertTrue(
                TopicInlineHatOpenPolicy.shouldForceCollapsedForLoad(
                        url = "https://4pda.to/forum/index.php?showtopic=123",
                        requestedTopicId = 123,
                        topicOpenTarget = setting,
                        sourceScreen = "pagination",
                        currentPage = page,
                )
        )
    }

    @Test
    fun settingFlagsSplitFirstPageFromServerNavigation() {
        assertTrue(AppPreferences.Main.TopicOpenTarget.SERVER_BOOKMARK.usesServerNavigation)
        assertTrue(AppPreferences.Main.TopicOpenTarget.LAST_UNREAD.usesServerNavigation)
        assertFalse(AppPreferences.Main.TopicOpenTarget.FIRST_PAGE.usesServerNavigation)
    }
}
