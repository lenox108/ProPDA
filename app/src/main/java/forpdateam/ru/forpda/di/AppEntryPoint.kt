package forpdateam.ru.forpda.di

import android.content.SharedPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.model.NetworkStateProvider
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.interactors.other.MenuRepository
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import forpdateam.ru.forpda.model.preferences.OtherPreferencesHolder
import forpdateam.ru.forpda.model.repository.avatar.AvatarRepository
import forpdateam.ru.forpda.model.repository.note.NotesRepository
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.ISystemLinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.ui.DimensionsProvider
import forpdateam.ru.forpda.ui.TemplateManager
import javax.inject.Named

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun clipboardHelper(): ClipboardHelper
    fun avatarRepository(): AvatarRepository
    fun dimensionsProvider(): DimensionsProvider
    fun linkHandler(): ILinkHandler
    fun systemLinkHandler(): ISystemLinkHandler
    fun mainPreferencesHolder(): MainPreferencesHolder
    fun notesRepository(): NotesRepository
    fun notificationPreferencesHolder(): NotificationPreferencesHolder
    fun otherPreferencesHolder(): OtherPreferencesHolder
    fun router(): TabRouter
    fun webClient(): IWebClient
    fun networkState(): NetworkStateProvider
    fun templateManager(): TemplateManager
    fun menuRepository(): MenuRepository
    @Named("data_storage") fun dataStoragePreferences(): SharedPreferences
}
