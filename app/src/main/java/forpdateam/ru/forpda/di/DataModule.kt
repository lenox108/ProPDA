package forpdateam.ru.forpda.di

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.CountersHolder
import forpdateam.ru.forpda.model.data.cache.favorites.FavoritesCacheRoom
import forpdateam.ru.forpda.model.data.cache.forum.ForumCacheRoom
import forpdateam.ru.forpda.model.data.cache.forumuser.ForumUsersCacheRoom
import forpdateam.ru.forpda.model.data.cache.history.HistoryCacheRoom
import forpdateam.ru.forpda.model.data.cache.notes.NotesCacheRoom
import forpdateam.ru.forpda.model.data.cache.qms.QmsCacheRoom
import forpdateam.ru.forpda.entity.db.notes.NoteFolderDao
import forpdateam.ru.forpda.entity.db.notes.NoteItemDao
import forpdateam.ru.forpda.entity.db.notes.AppDatabase
import forpdateam.ru.forpda.entity.db.notes.NotesMigrations
import forpdateam.ru.forpda.entity.db.history.HistoryItemDao
import forpdateam.ru.forpda.entity.db.favorites.FavItemDao
import forpdateam.ru.forpda.entity.db.forum.ForumItemFlatDao
import forpdateam.ru.forpda.entity.db.ForumUserDao
import forpdateam.ru.forpda.entity.db.qms.QmsContactDao
import forpdateam.ru.forpda.entity.db.qms.QmsThemeDao
import forpdateam.ru.forpda.entity.db.qms.QmsThemesDao
import androidx.room.Room
import forpdateam.ru.forpda.model.data.providers.UserSourceProvider
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.api.attachments.AttachmentsApi
import forpdateam.ru.forpda.model.data.remote.api.auth.AuthApi
import forpdateam.ru.forpda.model.data.remote.api.devdb.DevDbApi
import forpdateam.ru.forpda.model.data.remote.api.editpost.EditPostApi
import forpdateam.ru.forpda.model.data.remote.api.events.NotificationEventsApi
import forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesApi
import forpdateam.ru.forpda.model.data.remote.api.forum.ForumApi
import forpdateam.ru.forpda.model.data.remote.api.mentions.MentionsApi
import forpdateam.ru.forpda.model.data.remote.api.news.NewsApi
import forpdateam.ru.forpda.model.data.remote.api.profile.ProfileApi
import forpdateam.ru.forpda.model.data.remote.api.qms.QmsApi
import forpdateam.ru.forpda.model.data.remote.api.reputation.ReputationApi
import forpdateam.ru.forpda.model.data.remote.api.search.SearchApi
import forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi
import forpdateam.ru.forpda.model.data.remote.api.topcis.TopicsApi
import forpdateam.ru.forpda.model.data.storage.ExternalStorageProvider
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import forpdateam.ru.forpda.model.interactors.qms.QmsInteractor
import forpdateam.ru.forpda.model.interactors.other.MenuRepository
import forpdateam.ru.forpda.model.preferences.*
import forpdateam.ru.forpda.model.repository.auth.AuthRepository
import forpdateam.ru.forpda.model.repository.avatar.AvatarRepository
import forpdateam.ru.forpda.model.repository.devdb.DevDbRepository
import forpdateam.ru.forpda.model.repository.events.EventsRepository
import forpdateam.ru.forpda.model.repository.faviorites.FavoritesRepository
import forpdateam.ru.forpda.model.repository.forum.ForumRepository
import forpdateam.ru.forpda.model.repository.history.HistoryRepository
import forpdateam.ru.forpda.model.repository.mentions.MentionsRepository
import forpdateam.ru.forpda.model.repository.news.NewsRepository
import forpdateam.ru.forpda.model.interactors.news.ArticleDiskCache
import forpdateam.ru.forpda.model.interactors.news.ArticleMemoryCache
import forpdateam.ru.forpda.model.interactors.news.ArticlePrefetchService
import forpdateam.ru.forpda.model.interactors.theme.ThemePrefetchService
import forpdateam.ru.forpda.model.interactors.news.ArticleReadingProgressStore
import forpdateam.ru.forpda.presentation.articles.detail.ArticleTemplate
import forpdateam.ru.forpda.model.repository.note.NotesRepository
import forpdateam.ru.forpda.model.repository.posteditor.PostEditorRepository
import forpdateam.ru.forpda.model.repository.profile.ProfileRepository
import forpdateam.ru.forpda.model.repository.usercp.UserCpRepository
import forpdateam.ru.forpda.model.data.remote.api.usercp.UserCpApi
import forpdateam.ru.forpda.model.preferences.ForumPageSizeHolder
import forpdateam.ru.forpda.model.repository.qms.QmsRepository
import forpdateam.ru.forpda.model.repository.reputation.ReputationRepository
import forpdateam.ru.forpda.model.repository.search.ForumSectionTitleIndex
import forpdateam.ru.forpda.model.repository.search.SearchRepository
import forpdateam.ru.forpda.model.repository.theme.ThemeRepository
import forpdateam.ru.forpda.model.repository.topics.TopicsRepository
import forpdateam.ru.forpda.model.NetworkStateProvider
import forpdateam.ru.forpda.entity.app.profile.IUserHolder
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    // region Caches
    @Provides @Singleton fun provideUserSource(qmsApi: QmsApi) = UserSourceProvider(qmsApi)
    @Provides @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "forpda_database"
        ).addMigrations(
            NotesMigrations.MIGRATION_1_2,
            NotesMigrations.MIGRATION_2_3,
            NotesMigrations.MIGRATION_3_4,
            NotesMigrations.MIGRATION_4_5,
            NotesMigrations.MIGRATION_5_6,
            NotesMigrations.MIGRATION_7_6,
            NotesMigrations.MIGRATION_8_6,
            NotesMigrations.MIGRATION_9_6
        )
            .build()
    }

    @Provides @Singleton
    fun provideNoteItemDao(database: AppDatabase): NoteItemDao = database.noteItemDao()

    @Provides @Singleton
    fun provideNoteFolderDao(database: AppDatabase): NoteFolderDao = database.noteFolderDao()

    @Provides @Singleton
    fun provideHistoryItemDao(database: AppDatabase): HistoryItemDao = database.historyItemDao()

    @Provides @Singleton
    fun provideQmsContactDao(database: AppDatabase): QmsContactDao = database.qmsContactDao()

    @Provides @Singleton
    fun provideQmsThemeDao(database: AppDatabase): QmsThemeDao = database.qmsThemeDao()

    @Provides @Singleton
    fun provideQmsThemesDao(database: AppDatabase): QmsThemesDao = database.qmsThemesDao()

    @Provides @Singleton
    fun provideFavItemDao(database: AppDatabase): FavItemDao = database.favItemDao()

    @Provides @Singleton
    fun provideForumItemFlatDao(database: AppDatabase): ForumItemFlatDao = database.forumItemFlatDao()

    @Provides @Singleton
    fun provideForumUserDao(database: AppDatabase): ForumUserDao = database.forumUserDao()

    @Provides @Singleton fun provideNotesCacheRoom(noteItemDao: NoteItemDao, noteFolderDao: NoteFolderDao) =
            NotesCacheRoom(noteItemDao, noteFolderDao)
    @Provides @Singleton fun provideHistoryCacheRoom(historyItemDao: HistoryItemDao) = HistoryCacheRoom(historyItemDao)
    @Provides @Singleton fun provideFavoritesCacheRoom(favItemDao: FavItemDao) = FavoritesCacheRoom(favItemDao)
    @Provides @Singleton fun provideForumCacheRoom(forumItemFlatDao: ForumItemFlatDao) = ForumCacheRoom(forumItemFlatDao)
    @Provides @Singleton fun provideForumUsersCacheRoom(forumUserDao: ForumUserDao, userSource: UserSourceProvider) = ForumUsersCacheRoom(forumUserDao, userSource)
    @Provides @Singleton fun provideQmsCacheRoom(qmsContactDao: QmsContactDao, qmsThemeDao: QmsThemeDao, qmsThemesDao: QmsThemesDao) = QmsCacheRoom(qmsContactDao, qmsThemeDao, qmsThemesDao)
    // endregion

    // region Repositories
    @Provides @Singleton
    fun provideAvatarRepository(fucRoom: ForumUsersCacheRoom) = AvatarRepository(fucRoom)

    @Provides @Singleton
    fun provideUserCpRepository(api: UserCpApi, pageSizeHolder: ForumPageSizeHolder) = UserCpRepository(api, pageSizeHolder)

    @Provides @Singleton
    fun provideFavoritesRepository(
            api: FavoritesApi,
            cacheRoom: FavoritesCacheRoom,
            authHolder: AuthHolder,
            countersHolder: CountersHolder,
            listsPrefs: ListsPreferencesHolder,
            notifPrefs: NotificationPreferencesHolder,
            eventsApi: NotificationEventsApi
    ) = FavoritesRepository(api, cacheRoom, authHolder, countersHolder, listsPrefs, notifPrefs, eventsApi)

    @Provides @Singleton
    fun provideHistoryRepository(cacheRoom: HistoryCacheRoom) =
            HistoryRepository(cacheRoom)

    @Provides @Singleton
    fun provideMentionsRepository(api: MentionsApi, preferences: SharedPreferences) =
            MentionsRepository(api, preferences)

    @Provides @Singleton
    fun provideAuthRepository(
            api: AuthApi,
            authHolder: AuthHolder,
            countersHolder: CountersHolder,
            userHolder: IUserHolder
    ) = AuthRepository(api, authHolder, countersHolder, userHolder)

    @Provides @Singleton
    fun provideProfileRepository(
            api: ProfileApi,
            userHolder: IUserHolder,
            authHolder: AuthHolder,
            fuc: ForumUsersCacheRoom
    ) = ProfileRepository(api, userHolder, authHolder, fuc)

    @Provides @Singleton
    fun provideReputationRepository(api: ReputationApi) =
            ReputationRepository(api)

    @Provides @Singleton
    fun provideForumRepository(api: ForumApi, cacheRoom: ForumCacheRoom) =
            ForumRepository(api, cacheRoom)

    @Provides @Singleton
    fun provideForumSectionTitleIndex() = ForumSectionTitleIndex()

    @Provides @Singleton
    fun provideTopicsRepository(api: TopicsApi, forumSectionTitleIndex: ForumSectionTitleIndex) =
            TopicsRepository(api, forumSectionTitleIndex)

    @Provides @Singleton
    fun provideThemeRepository(api: ThemeApi, hcRoom: HistoryCacheRoom, fucRoom: ForumUsersCacheRoom) =
            ThemeRepository(api, hcRoom, fucRoom)

    @Provides @Singleton
    fun provideThemePrefetchService(themeRepository: ThemeRepository) =
            ThemePrefetchService(themeRepository)

    @Provides @Singleton
    fun provideQmsRepository(
            api: QmsApi,
            attachmentsApi: AttachmentsApi,
            cacheRoom: QmsCacheRoom,
            fucRoom: ForumUsersCacheRoom,
            countersHolder: CountersHolder
    ) = QmsRepository(api, attachmentsApi, cacheRoom, fucRoom, countersHolder)

    @Provides @Singleton
    fun provideSearchRepository(api: SearchApi, fucRoom: ForumUsersCacheRoom, forumSectionTitleIndex: ForumSectionTitleIndex) =
            SearchRepository(api, fucRoom, forumSectionTitleIndex)

    @Provides @Singleton
    fun provideNewsRepository(api: NewsApi, fucRoom: ForumUsersCacheRoom) =
            NewsRepository(api, fucRoom)

    @Provides @Singleton
    fun provideArticleDiskCache(@ApplicationContext context: Context) =
            ArticleDiskCache(context)

    @Provides @Singleton
    fun provideArticleMemoryCache() = ArticleMemoryCache()

    @Provides @Singleton
    fun provideArticleReadingProgressStore(@ApplicationContext context: Context) =
            ArticleReadingProgressStore(context)

    @Provides @Singleton
    fun provideArticlePrefetchService(
            newsRepository: NewsRepository,
            articleTemplate: ArticleTemplate,
            diskCache: ArticleDiskCache,
            memoryCache: ArticleMemoryCache
    ) = ArticlePrefetchService(newsRepository, articleTemplate, diskCache, memoryCache)

    @Provides @Singleton
    fun provideDevDbRepository(api: DevDbApi) =
            DevDbRepository(api)

    @Provides @Singleton
    fun provideEditPostRepository(@ApplicationContext context: Context, api: EditPostApi, attachmentsApi: AttachmentsApi, fucRoom: ForumUsersCacheRoom) =
            PostEditorRepository(context, api, attachmentsApi, fucRoom)

    @Provides @Singleton
    fun provideNotesRepository(cacheRoom: NotesCacheRoom, es: ExternalStorageProvider) =
            NotesRepository(cacheRoom, es)

    @Provides @Singleton
    fun provideEventsRepository(
            @ApplicationContext context: Context,
            application: Application,
            wc: IWebClient,
            api: NotificationEventsApi,
            networkState: NetworkStateProvider,
            authHolder: AuthHolder,
            countersHolder: CountersHolder,
            notifPrefs: NotificationPreferencesHolder,
            mentionsRepository: MentionsRepository
    ) = EventsRepository(context, application, wc, api, networkState, authHolder, countersHolder, notifPrefs, mentionsRepository)

    @Provides @Singleton
    fun provideMenuRepository(
            preferences: SharedPreferences,
            authHolder: AuthHolder,
            countersHolder: CountersHolder,
            listsPreferencesHolder: ListsPreferencesHolder
    ) = MenuRepository(preferences, authHolder, countersHolder, listsPreferencesHolder)

    // endregion

    // region Interactors
    @Provides @Singleton
    fun provideQmsInteractor(
            qmsRepository: QmsRepository,
            eventsRepository: EventsRepository,
            qmsApi: QmsApi
    ) = QmsInteractor(qmsRepository, eventsRepository, qmsApi)
    // endregion
}
