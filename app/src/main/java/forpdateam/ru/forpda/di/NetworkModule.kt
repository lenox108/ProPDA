package forpdateam.ru.forpda.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import forpdateam.ru.forpda.client.Client
import forpdateam.ru.forpda.common.di.AppScope
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.CountersHolder
import kotlinx.coroutines.CoroutineScope
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.api.attachments.AttachmentsApi
import forpdateam.ru.forpda.model.data.remote.api.attachments.AttachmentsParser
import forpdateam.ru.forpda.model.data.remote.api.attachments.TopicAttachmentsApi
import forpdateam.ru.forpda.model.data.remote.api.attachments.TopicAttachmentsParser
import forpdateam.ru.forpda.model.data.remote.api.auth.AuthApi
import forpdateam.ru.forpda.model.data.remote.api.auth.AuthParser
import forpdateam.ru.forpda.model.data.remote.api.devdb.DevDbApi
import forpdateam.ru.forpda.model.data.remote.api.devdb.DevDbParser
import forpdateam.ru.forpda.model.data.remote.api.editpost.EditPostApi
import forpdateam.ru.forpda.model.data.remote.api.editpost.EditPostParser
import forpdateam.ru.forpda.model.data.remote.api.events.NotificationEventsApi
import forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesApi
import forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesParser
import forpdateam.ru.forpda.model.data.remote.api.forum.ForumApi
import forpdateam.ru.forpda.model.data.remote.api.forum.ForumParser
import forpdateam.ru.forpda.model.data.remote.api.mentions.MentionsApi
import forpdateam.ru.forpda.model.data.remote.api.mentions.MentionsParser
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleParser
import forpdateam.ru.forpda.model.data.remote.api.news.NewsApi
import forpdateam.ru.forpda.model.data.remote.api.profile.ProfileApi
import forpdateam.ru.forpda.model.data.remote.api.profile.ProfileParser
import forpdateam.ru.forpda.model.data.remote.api.usercp.UserCpApi
import forpdateam.ru.forpda.model.data.remote.api.usercp.UserCpParser
import forpdateam.ru.forpda.model.preferences.ForumPageSizeHolder
import forpdateam.ru.forpda.model.data.remote.api.qms.QmsApi
import forpdateam.ru.forpda.model.data.remote.api.qms.QmsParser
import forpdateam.ru.forpda.model.data.remote.api.reputation.ReputationApi
import forpdateam.ru.forpda.model.data.remote.api.reputation.ReputationParser
import forpdateam.ru.forpda.model.data.remote.api.search.SearchApi
import forpdateam.ru.forpda.model.data.remote.api.search.SearchParser
import forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi
import forpdateam.ru.forpda.model.data.remote.api.theme.ThemeParser
import forpdateam.ru.forpda.model.data.remote.api.topcis.TopicsApi
import forpdateam.ru.forpda.model.data.remote.api.topcis.TopicsParser
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideWebClient(
            @ApplicationContext context: Context,
            authHolder: AuthHolder,
            countersHolder: CountersHolder,
            blocklistGuard: forpdateam.ru.forpda.blocklist.BlocklistGuard,
            userHolder: forpdateam.ru.forpda.entity.app.profile.IUserHolder,
            @AppScope appScope: CoroutineScope,
    ): IWebClient = Client(context, authHolder, countersHolder, blocklistGuard, userHolder, appScope)

    // region Parsers (concrete classes; @Binds only works for interface→impl binding)
    @Provides @Singleton fun provideAuthParser(pp: IPatternProvider) = AuthParser(pp)
    @Provides @Singleton fun provideDevDbParser(pp: IPatternProvider) = DevDbParser(pp)
    @Provides @Singleton fun provideThemeParser(pp: IPatternProvider, psh: ForumPageSizeHolder) = ThemeParser(pp, psh)
    @Provides @Singleton fun provideEditPostParser(pp: IPatternProvider) = EditPostParser(pp)
    @Provides @Singleton fun provideFavoritesParser(pp: IPatternProvider, psh: ForumPageSizeHolder) = FavoritesParser(pp, psh)
    @Provides @Singleton fun provideForumParser(pp: IPatternProvider) = ForumParser(pp)
    @Provides @Singleton fun provideMentionsParser(pp: IPatternProvider) = MentionsParser(pp)
    @Provides @Singleton fun provideArticleParser(pp: IPatternProvider) = ArticleParser(pp)
    @Provides @Singleton fun provideProfileParser(pp: IPatternProvider) = ProfileParser(pp)
    @Provides @Singleton fun provideQmsParser(pp: IPatternProvider) = QmsParser(pp)
    @Provides @Singleton fun provideReputationParser(pp: IPatternProvider) = ReputationParser(pp)
    // useJsoup=true: search is parsed via CSS selectors (SearchJsoupParser) instead of the fragile 21-group
    // regexes. Validated on live 4pda HTML (A/B vs regex): topics — jsoup complete & clean vs regex's greedy
    // broken dates + missed rows; posts — exact field parity; news — jsoup fixes the regex which parsed 0 items.
    @Provides @Singleton fun provideSearchParser(pp: IPatternProvider) = SearchParser(pp, useJsoup = true)
    @Provides @Singleton fun provideTopicsParser(pp: IPatternProvider, psh: ForumPageSizeHolder) = TopicsParser(pp, psh)
    @Provides @Singleton fun provideAttachmentsParser(pp: IPatternProvider) = AttachmentsParser(pp)
    @Provides @Singleton fun provideUserCpParser() = UserCpParser()
    // endregion

    // region APIs
    @Provides @Singleton fun provideAuthApi(@ApplicationContext context: Context, wc: IWebClient, p: AuthParser) = AuthApi(context, wc, p)
    @Provides @Singleton fun provideDevDbApi(wc: IWebClient, p: DevDbParser) = DevDbApi(wc, p)
    @Provides @Singleton fun provideThemeApi(wc: IWebClient, p: ThemeParser, authHolder: AuthHolder) = ThemeApi(wc, p, authHolder)
    @Provides @Singleton fun provideEditPostApi(wc: IWebClient, ta: ThemeApi, ep: EditPostParser, ap: AttachmentsParser, tp: ThemeParser) = EditPostApi(wc, ta, ep, ap, tp)
    @Provides @Singleton fun provideEventsApi(wc: IWebClient) = NotificationEventsApi(wc)
    @Provides @Singleton fun provideFavoritesApi(wc: IWebClient, p: FavoritesParser) = FavoritesApi(wc, p)
    @Provides @Singleton fun provideForumApi(wc: IWebClient, p: ForumParser) = ForumApi(wc, p)
    @Provides @Singleton fun provideMentionsApi(wc: IWebClient, p: MentionsParser) = MentionsApi(wc, p)
    @Provides @Singleton fun provideNewsApi(wc: IWebClient, p: ArticleParser) = NewsApi(wc, p)
    @Provides @Singleton fun provideProfileApi(wc: IWebClient, p: ProfileParser) = ProfileApi(wc, p)
    @Provides @Singleton fun provideQmsApi(wc: IWebClient, p: QmsParser) = QmsApi(wc, p)
    @Provides @Singleton fun provideReputationApi(wc: IWebClient, p: ReputationParser) = ReputationApi(wc, p)
    @Provides @Singleton fun provideSearchApi(wc: IWebClient, p: SearchParser) = SearchApi(wc, p)
    @Provides @Singleton fun provideTopicsApi(wc: IWebClient, p: TopicsParser) = TopicsApi(wc, p)
    @Provides @Singleton fun provideAttachmentsApi(wc: IWebClient, p: AttachmentsParser) = AttachmentsApi(wc, p)
    @Provides @Singleton fun provideTopicAttachmentsParser() = TopicAttachmentsParser()
    @Provides @Singleton fun provideTopicAttachmentsApi(wc: IWebClient, p: TopicAttachmentsParser) = TopicAttachmentsApi(wc, p)
    @Provides @Singleton fun provideSiteCommentsParser() =
            forpdateam.ru.forpda.model.data.remote.api.sitecontent.SiteCommentsParser()
    @Provides @Singleton fun provideSiteUserContentApi(
            wc: IWebClient,
            ap: ArticleParser,
            cp: forpdateam.ru.forpda.model.data.remote.api.sitecontent.SiteCommentsParser,
    ) = forpdateam.ru.forpda.model.data.remote.api.sitecontent.SiteUserContentApi(wc, ap, cp)
    @Provides @Singleton fun provideUserCpApi(wc: IWebClient, p: UserCpParser) = UserCpApi(wc, p)
    // endregion
}
