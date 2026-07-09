package forpdateam.ru.forpda.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder
import forpdateam.ru.forpda.presentation.announce.AnnounceTemplate
import forpdateam.ru.forpda.presentation.articles.detail.ArticleTemplate
import forpdateam.ru.forpda.presentation.forumrules.ForumRulesTemplate
import forpdateam.ru.forpda.presentation.theme.ThemeTemplate
import forpdateam.ru.forpda.ui.TemplateManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PresentationModule {

    @Provides @Singleton
    fun provideThemeTemplate(@ApplicationContext context: Context, tm: TemplateManager, ah: AuthHolder, mp: MainPreferencesHolder, tp: TopicPreferencesHolder) =
            ThemeTemplate(context, tm, ah, mp, tp)

    @Provides @Singleton
    fun provideArticleTemplate(tm: TemplateManager) = ArticleTemplate(tm)

    @Provides @Singleton
    fun provideForumRulesTemplate(tm: TemplateManager) = ForumRulesTemplate(tm)

    @Provides @Singleton
    fun provideAnnounceTemplate(tm: TemplateManager) = AnnounceTemplate(tm)
}
