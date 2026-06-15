package forpdateam.ru.forpda.ui.navigation
import timber.log.Timber

import android.os.Bundle
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.fragments.auth.AuthFragment
import forpdateam.ru.forpda.ui.fragments.devdb.brand.DevicesFragment
import forpdateam.ru.forpda.ui.fragments.devdb.brands.BrandsFragment
import forpdateam.ru.forpda.ui.fragments.devdb.device.DeviceFragment
import forpdateam.ru.forpda.ui.fragments.devdb.search.DevDbSearchFragment
import forpdateam.ru.forpda.ui.fragments.editpost.EditPostFragment
import forpdateam.ru.forpda.ui.fragments.favorites.FavoritesFragment
import forpdateam.ru.forpda.ui.fragments.forum.ForumFragment
import forpdateam.ru.forpda.ui.fragments.history.HistoryFragment
import forpdateam.ru.forpda.ui.fragments.mentions.MentionsFragment
import forpdateam.ru.forpda.ui.fragments.news.details.NewsDetailsFragment
import forpdateam.ru.forpda.ui.fragments.news.main.NewsMainFragment
import forpdateam.ru.forpda.ui.fragments.notes.NotesFragment
import forpdateam.ru.forpda.ui.fragments.other.AnnounceFragment
import forpdateam.ru.forpda.ui.fragments.other.ForumRulesFragment
import forpdateam.ru.forpda.ui.fragments.other.GoogleCaptchaFragment
import forpdateam.ru.forpda.ui.fragments.other.OtherFragment
import forpdateam.ru.forpda.ui.fragments.profile.ProfileFragment
import forpdateam.ru.forpda.ui.fragments.qms.QmsBlackListFragment
import forpdateam.ru.forpda.ui.fragments.qms.QmsContactsFragment
import forpdateam.ru.forpda.ui.fragments.qms.QmsThemesFragment
import forpdateam.ru.forpda.ui.fragments.qms.chat.QmsChatFragment
import forpdateam.ru.forpda.ui.fragments.reputation.ReputationFragment
import forpdateam.ru.forpda.ui.fragments.search.SearchFragment
import forpdateam.ru.forpda.ui.fragments.downloads.DownloadsFragment
import forpdateam.ru.forpda.ui.fragments.theme.ThemeFragmentWeb
import forpdateam.ru.forpda.ui.fragments.theme.blacklist.ForumBlackListFragment
import forpdateam.ru.forpda.ui.fragments.topics.TopicsFragment

object TabHelper {

    private const val EDIT_POST_DRAFT_SYNC_TAG = "EditPostDraftSync"

    /**
     * §3.2 flag: when true, [Screen.QmsContacts] routes through the
     * Compose-based [forpdateam.ru.forpda.ui.fragments.qms.QmsContactsComposeFragment];
     * when false (default) it falls back to the legacy
     * [forpdateam.ru.forpda.ui.fragments.qms.QmsContactsFragment].
     * Flip this single line to roll back to legacy in case of regressions.
     */
    @Volatile
    var useComposeQmsContacts: Boolean = false

    /**
     * A/B flag for the offline-reading list (§5.1 of REFACTOR_PLAN.md). When
     * `true`, [forpdateam.ru.forpda.ui.fragments.offline.OfflineListComposeFragment]
     * is used; otherwise a future legacy fragment path takes over. Flip the
     * flag to roll back to legacy in case of regressions.
     */
    @Volatile
    var useComposeOfflineList: Boolean = false

    /**
     * §3.2 flag: when true, [Screen.ArticleList] routes through the
     * Compose-based [forpdateam.ru.forpda.ui.fragments.news.main.NewsMainComposeFragment];
     * when false (default) it falls back to the legacy
     * [forpdateam.ru.forpda.ui.fragments.news.main.NewsMainFragment].
     * Flip this single line to roll back to legacy in case of regressions.
     */
    @Volatile
    var useComposeArticleList: Boolean = false

    /**
     * §3.2 flag: when true, [Screen.Favorites] routes through the
     * Compose-based favorites fragment (FavoritesComposeFragment);
     * when false (default) it falls back to the legacy
     * [forpdateam.ru.forpda.ui.fragments.favorites.FavoritesFragment].
     * Flip this single line to roll back to legacy in case of regressions.
     */
    @Volatile
    var useComposeFavorites: Boolean = false

    private fun createFragment(tabClass: Class<out TabFragment>, args: Bundle? = null): TabFragment {
        return tabClass.getDeclaredConstructor().newInstance().apply {
            args?.let { arguments = it }
        }
    }

    fun createTab(screen: Screen): TabFragment {
        val args = Bundle().apply {
            screen.screenTitle?.let {
                putString(TabFragment.ARG_TITLE, it)
            }
            screen.screenSubTitle?.let {
                putString(TabFragment.ARG_SUBTITLE, it)
            }
        }
        return when (screen) {
            is Screen.Auth -> createFragment(AuthFragment::class.java, args)
            is Screen.DevDbDevices -> {
                createFragment(DevicesFragment::class.java, args.apply {
                    putString(DevicesFragment.ARG_CATEGORY_ID, screen.categoryId)
                    putString(DevicesFragment.ARG_BRAND_ID, screen.brandId)
                })
            }
            is Screen.DevDbBrands -> createFragment(BrandsFragment::class.java, args)
            is Screen.DevDbDevice -> {
                createFragment(DeviceFragment::class.java, args.apply {
                    putString(DeviceFragment.ARG_DEVICE_ID, screen.deviceId)
                })
            }
            is Screen.DevDbSearch -> createFragment(DevDbSearchFragment::class.java, args)
            is Screen.EditPost -> {
                val screenMessageLen = screen.editPostForm?.message?.length
                    ?: screen.initialBodyHtml?.length
                    ?: 0
                Timber.d(
                    EDIT_POST_DRAFT_SYNC_TAG,
                    "screenArgs len=$screenMessageLen" +
                        " hasForm=${screen.editPostForm != null}" +
                        " selection=${screen.initialSelectionStart}..${screen.initialSelectionEnd}"
                )
                val arguments = if (screen.editPostForm == null) {
                    EditPostFragment.fillArguments(
                            args,
                            screen.postId,
                            screen.topicId,
                            screen.forumId,
                            screen.st,
                            screen.themeName,
                            screen.initialBodyHtml
                    )
                } else {
                    EditPostFragment.fillArguments(
                            args,
                            screen.editPostForm!!,
                            screen.themeName,
                            screen.initialSelectionStart,
                            screen.initialSelectionEnd
                    )
                }
                createFragment(EditPostFragment::class.java, arguments)
            }
            is Screen.Favorites -> if (useComposeFavorites) {
                createFragment(forpdateam.ru.forpda.ui.fragments.favorites.FavoritesComposeFragment::class.java, args)
            } else {
                createFragment(FavoritesFragment::class.java, args)
            }
            is Screen.Forum -> {
                createFragment(ForumFragment::class.java, args.apply {
                    putInt(ForumFragment.ARG_FORUM_ID, screen.forumId)
                })
            }
            is Screen.History -> createFragment(HistoryFragment::class.java, args)
            is Screen.Mentions -> createFragment(MentionsFragment::class.java, args)
            is Screen.ArticleList -> if (useComposeArticleList) {
                createFragment(forpdateam.ru.forpda.ui.fragments.news.main.NewsMainComposeFragment::class.java, args)
            } else {
                createFragment(NewsMainFragment::class.java, args)
            }
            is Screen.ArticleDetail -> {
                createFragment(NewsDetailsFragment::class.java, args.apply {
                    putInt(NewsDetailsFragment.ARG_NEWS_ID, screen.articleId)
                    putInt(NewsDetailsFragment.ARG_NEWS_COMMENT_ID, screen.commentId)
                    putString(NewsDetailsFragment.ARG_NEWS_URL, screen.articleUrl)
                    putString(NewsDetailsFragment.ARG_NEWS_TITLE, screen.screenTitle)
                    putString(NewsDetailsFragment.ARG_NEWS_AUTHOR_NICK, screen.articleAuthorNick)
                    putString(NewsDetailsFragment.ARG_NEWS_DATE, screen.articleDate)
                    putString(NewsDetailsFragment.ARG_NEWS_IMAGE, screen.articleImageUrl)
                    putInt(NewsDetailsFragment.ARG_NEWS_COMMENTS_COUNT, screen.articleCommentsCount)
                    putString(NewsDetailsFragment.ARG_NEWS_OPEN_SOURCE, screen.articleOpenSource)
                })
            }
            is Screen.Notes -> createFragment(NotesFragment::class.java, args)
            is Screen.Announce -> {
                createFragment(AnnounceFragment::class.java, args.apply {
                    putInt(AnnounceFragment.ARG_ANNOUNCE_ID, screen.announceId)
                    putInt(AnnounceFragment.ARG_FORUM_ID, screen.forumId)
                })
            }
            is Screen.ForumRules -> createFragment(ForumRulesFragment::class.java, args)
            is Screen.ForumBlackList -> createFragment(ForumBlackListFragment::class.java, args)
            is Screen.GoogleCaptcha -> createFragment(GoogleCaptchaFragment::class.java, args)
            is Screen.Profile -> {
                createFragment(ProfileFragment::class.java, args.apply {
                    putString(TabFragment.ARG_TAB, screen.profileUrl)
                })
            }
            is Screen.QmsContacts -> if (useComposeQmsContacts) {
                createFragment(forpdateam.ru.forpda.ui.fragments.qms.QmsContactsComposeFragment::class.java, args)
            } else {
                createFragment(QmsContactsFragment::class.java, args)
            }
            is Screen.QmsBlackList -> createFragment(QmsBlackListFragment::class.java, args)
            is Screen.QmsThemes -> {
                createFragment(QmsThemesFragment::class.java, args.apply {
                    putInt(QmsThemesFragment.USER_ID_ARG, screen.userId)
                    putString(QmsThemesFragment.USER_AVATAR_ARG, screen.avatarUrl)
                })
            }
            is Screen.QmsChat -> {
                createFragment(QmsChatFragment::class.java, args.apply {
                    putInt(QmsChatFragment.THEME_ID_ARG, screen.themeId)
                    putInt(QmsChatFragment.USER_ID_ARG, screen.userId)
                    putString(QmsChatFragment.USER_NICK_ARG, screen.userNick)
                    putString(QmsChatFragment.USER_AVATAR_ARG, screen.avatarUrl)
                    putString(QmsChatFragment.THEME_TITLE_ARG, screen.themeTitle)
                })
            }
            is Screen.Reputation -> {
                createFragment(ReputationFragment::class.java, args.apply {
                    putString(TabFragment.ARG_TAB, screen.reputationUrl)
                })
            }
            is Screen.Search -> {
                createFragment(SearchFragment::class.java, args.apply {
                    putString(TabFragment.ARG_TAB, screen.searchUrl)
                })
            }
            is Screen.Downloads -> createFragment(DownloadsFragment::class.java, args)
            is Screen.Theme -> {
                createFragment(ThemeFragmentWeb::class.java, args.apply {
                    putString(TabFragment.ARG_TAB, screen.themeUrl)
                    putString(Screen.Theme.ARG_TOPIC_OPEN_SOURCE, screen.topicOpenSource)
                    putString(Screen.Theme.ARG_TOPIC_OPEN_INTENT, screen.topicOpenIntent)
                    screen.unreadUrlFromList?.let { putString(Screen.Theme.ARG_UNREAD_URL_FROM_LIST, it) }
                    if (screen.unreadPostIdFromList > 0) {
                        putInt(Screen.Theme.ARG_UNREAD_POST_ID_FROM_LIST, screen.unreadPostIdFromList)
                    }
                })
            }
            is Screen.Topics -> {
                createFragment(TopicsFragment::class.java, args.apply {
                    putInt(TopicsFragment.TOPICS_ID_ARG, screen.forumId)
                })
            }
            is Screen.OtherMenu -> {
                createFragment(OtherFragment::class.java)
            }
            is Screen.OfflineList -> if (useComposeOfflineList) {
                createFragment(
                        forpdateam.ru.forpda.ui.fragments.offline.OfflineListComposeFragment::class.java,
                        args
                )
            } else {
                // No legacy offline-list fragment is shipping yet — fall back to the
                // Compose host. The A/B flag exists so the legacy path can be wired
                // in later without changing the routing.
                createFragment(
                        forpdateam.ru.forpda.ui.fragments.offline.OfflineListComposeFragment::class.java,
                        args
                )
            }
            else -> {
                // Не падаем в проде из-за неизвестного экрана: открываем меню как безопасный fallback.
                createFragment(OtherFragment::class.java)
            }
        }.apply {
            configuration.isMenu = screen.fromMenu
            configuration.isAlone = screen.isAlone
        }
    }

    fun findClassByScreen(screen: Screen): Class<out TabFragment> {
        return when (screen) {
            is Screen.Auth -> AuthFragment::class.java
            is Screen.DevDbDevices -> DevicesFragment::class.java
            is Screen.DevDbBrands -> BrandsFragment::class.java
            is Screen.DevDbDevice -> DeviceFragment::class.java
            is Screen.DevDbSearch -> DevDbSearchFragment::class.java
            is Screen.EditPost -> EditPostFragment::class.java
            is Screen.Favorites -> FavoritesFragment::class.java
            is Screen.Forum -> ForumFragment::class.java
            is Screen.History -> HistoryFragment::class.java
            is Screen.Mentions -> MentionsFragment::class.java
            is Screen.ArticleList -> NewsMainFragment::class.java
            is Screen.ArticleDetail -> NewsDetailsFragment::class.java
            is Screen.Notes -> NotesFragment::class.java
            is Screen.Announce -> AnnounceFragment::class.java
            is Screen.ForumRules -> ForumRulesFragment::class.java
            is Screen.ForumBlackList -> ForumBlackListFragment::class.java
            is Screen.GoogleCaptcha -> GoogleCaptchaFragment::class.java
            is Screen.Profile -> ProfileFragment::class.java
            is Screen.QmsContacts -> QmsContactsFragment::class.java
            is Screen.QmsBlackList -> QmsBlackListFragment::class.java
            is Screen.QmsThemes -> QmsThemesFragment::class.java
            is Screen.QmsChat -> QmsChatFragment::class.java
            is Screen.Reputation -> ReputationFragment::class.java
            is Screen.Search -> SearchFragment::class.java
            is Screen.Downloads -> DownloadsFragment::class.java
            is Screen.Theme -> ThemeFragmentWeb::class.java
            is Screen.Topics -> TopicsFragment::class.java
            is Screen.OtherMenu -> OtherFragment::class.java
            else -> {
                // Безопасный fallback
                OtherFragment::class.java
            }
        }
    }

    fun findScreenByFragment(fragment: TabFragment): Class<out Screen> {
        return when (fragment) {
            is AuthFragment -> Screen.Auth::class.java
            is DevicesFragment -> Screen.DevDbDevices::class.java
            is BrandsFragment -> Screen.DevDbBrands::class.java
            is DeviceFragment -> Screen.DevDbDevice::class.java
            is DevDbSearchFragment -> Screen.DevDbSearch::class.java
            is EditPostFragment -> Screen.EditPost::class.java
            is FavoritesFragment -> Screen.Favorites::class.java
            is ForumFragment -> Screen.Forum::class.java
            is HistoryFragment -> Screen.History::class.java
            is MentionsFragment -> Screen.Mentions::class.java
            is NewsMainFragment -> Screen.ArticleList::class.java
            is NewsDetailsFragment -> Screen.ArticleDetail::class.java
            is NotesFragment -> Screen.Notes::class.java
            is AnnounceFragment -> Screen.Announce::class.java
            is ForumRulesFragment -> Screen.ForumRules::class.java
            is ForumBlackListFragment -> Screen.ForumBlackList::class.java
            is GoogleCaptchaFragment -> Screen.GoogleCaptcha::class.java
            is ProfileFragment -> Screen.Profile::class.java
            is QmsContactsFragment -> Screen.QmsContacts::class.java
            is QmsBlackListFragment -> Screen.QmsBlackList::class.java
            is QmsThemesFragment -> Screen.QmsThemes::class.java
            is QmsChatFragment -> Screen.QmsChat::class.java
            is ReputationFragment -> Screen.Reputation::class.java
            is SearchFragment -> Screen.Search::class.java
            is DownloadsFragment -> Screen.Downloads::class.java
            is ThemeFragmentWeb -> Screen.Theme::class.java
            is TopicsFragment -> Screen.Topics::class.java
            is OtherFragment -> Screen.OtherMenu::class.java
            else -> {
                // Безопасный fallback
                Screen.OtherMenu::class.java
            }
        }
    }
}
