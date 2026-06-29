package forpdateam.ru.forpda.di

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.github.terrakok.cicerone.Cicerone
import com.github.terrakok.cicerone.NavigatorHolder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import forpdateam.ru.forpda.common.DayNightHelper
import forpdateam.ru.forpda.common.di.AppScope
import forpdateam.ru.forpda.entity.app.profile.IUserHolder
import forpdateam.ru.forpda.entity.app.profile.UserHolder
import forpdateam.ru.forpda.model.*
import forpdateam.ru.forpda.model.data.storage.ExternalStorageProvider
import forpdateam.ru.forpda.model.system.ExternalStorage
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.system.PatternProvider
import forpdateam.ru.forpda.model.interactors.CrossScreenInteractor
import forpdateam.ru.forpda.model.preferences.*
import forpdateam.ru.forpda.model.system.AppNetworkState
import forpdateam.ru.forpda.model.NetworkStateProvider
import forpdateam.ru.forpda.presentation.*
import forpdateam.ru.forpda.ui.DimensionsProvider
import forpdateam.ru.forpda.ui.TemplateManager
import forpdateam.ru.forpda.downloads.InternalDownloader
import forpdateam.ru.forpda.appupdates.AppUpdateParser
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDefaultPreferences(@ApplicationContext context: Context): SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(context)

    @Provides
    @Singleton
    @Named("data_storage")
    fun provideDataStoragePreferences(@ApplicationContext context: Context): SharedPreferences =
            context.getSharedPreferences("${context.packageName}_data_storage", Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun provideDimensionsProvider(): DimensionsProvider = DimensionsProvider()

    @Provides
    @Singleton
    fun provideDayNightHelper(@ApplicationContext context: Context): DayNightHelper {
        val defaultIsNight = DayNightHelper.isUiModeNight(context.resources.configuration)
        return DayNightHelper(defaultIsNight)
    }

    @Provides
    @Singleton
    fun provideCicerone(
        @ApplicationContext context: Context,
        @AppScope appScope: kotlinx.coroutines.CoroutineScope,
    ): Cicerone<TabRouter> = Cicerone.create(TabRouter(context, appScope))

    @Provides
    @Singleton
    fun provideRouter(cicerone: Cicerone<TabRouter>): TabRouter = cicerone.router

    @Provides
    @Singleton
    fun provideNavigatorHolder(cicerone: Cicerone<TabRouter>): NavigatorHolder = cicerone.getNavigatorHolder()

    @Provides
    @Singleton
    fun provideNetworkState(@ApplicationContext context: Context): NetworkStateProvider = AppNetworkState(context)

    @Provides
    @Singleton
    fun provideExternalStorage(): ExternalStorageProvider = ExternalStorage()

    @Provides
    @Singleton
    fun provideAuthHolder(preferences: SharedPreferences): AuthHolder = AuthHolder(preferences)

    @Provides
    @Singleton
    fun provideCountersHolder(preferences: SharedPreferences): CountersHolder = CountersHolder(preferences)

    @Provides
    @Singleton
    fun provideCloseableInfoHolder(preferences: SharedPreferences): CloseableInfoHolder = CloseableInfoHolder(preferences)

    @Provides
    @Singleton
    fun provideForumPageSizeHolder(preferences: SharedPreferences): ForumPageSizeHolder = ForumPageSizeHolder(preferences)

    @Provides
    @Singleton
    fun providePatternProvider(
            @ApplicationContext context: Context,
            @Named("data_storage") preferences: SharedPreferences
    ): IPatternProvider = PatternProvider(context, preferences)

    @Provides
    @Singleton
    fun provideTemplateManager(
            @ApplicationContext context: Context,
            dayNightHelper: DayNightHelper,
            mainPreferencesHolder: MainPreferencesHolder
    ): TemplateManager = TemplateManager(context, dayNightHelper, mainPreferencesHolder)

    @Provides
    @Singleton
    fun provideInternalDownloader(
            permissionHelper: forpdateam.ru.forpda.common.PermissionHelper,
            mainPreferencesHolder: MainPreferencesHolder,
            @Named("data_storage") dataStoragePreferences: SharedPreferences
    ): InternalDownloader = InternalDownloader(permissionHelper, mainPreferencesHolder, dataStoragePreferences)

    @Provides
    @Singleton
    fun provideAppUpdateParser(): AppUpdateParser = AppUpdateParser()

    @Provides
    @Singleton
    fun provideSystemLinkHandler(
            @ApplicationContext context: Context,
            mainPreferencesHolder: MainPreferencesHolder,
            notificationPreferencesHolder: NotificationPreferencesHolder,
            router: TabRouter,
            authHolder: AuthHolder,
            webClient: IWebClient,
            internalDownloader: InternalDownloader
    ): ISystemLinkHandler = SystemLinkHandler(context, mainPreferencesHolder, notificationPreferencesHolder, router, authHolder, webClient, internalDownloader)

    @Provides
    @Singleton
    fun provideLinkHandler(systemLinkHandler: ISystemLinkHandler, router: TabRouter): ILinkHandler = LinkHandler(systemLinkHandler, router)

    @Provides
    @Singleton
    fun provideCrossScreenInteractor(): CrossScreenInteractor = CrossScreenInteractor()

    // Preferences holders
    @Provides
    @Singleton
    fun provideOtherPreferencesHolder(@ApplicationContext context: Context): OtherPreferencesHolder =
            OtherPreferencesHolder(context)

    @Provides
    @Singleton
    fun provideMainPreferencesHolder(@ApplicationContext context: Context): MainPreferencesHolder =
            MainPreferencesHolder(context)

    @Provides
    @Singleton
    fun provideTopicPreferencesHolder(@ApplicationContext context: Context): TopicPreferencesHolder =
            TopicPreferencesHolder(context)

    @Provides
    @Singleton
    fun provideListsPreferencesHolder(@ApplicationContext context: Context): ListsPreferencesHolder =
            ListsPreferencesHolder(context)

    @Provides
    @Singleton
    fun provideNotificationPreferencesHolder(@ApplicationContext context: Context): NotificationPreferencesHolder =
            NotificationPreferencesHolder(context)
}
