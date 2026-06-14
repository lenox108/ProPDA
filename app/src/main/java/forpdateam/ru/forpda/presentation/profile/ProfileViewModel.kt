package forpdateam.ru.forpda.presentation.profile

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import coil.request.ImageRequest
import coil.request.SuccessResult
import forpdateam.ru.forpda.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel
import forpdateam.ru.forpda.model.repository.profile.ProfileRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ProfileViewModel @Inject constructor(
        @ApplicationContext private val context: Context,
        private val profileRepository: ProfileRepository,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler,
        private val clipboardHelper: ClipboardHelper
) : BaseViewModel() {

    private var subscriptionsStarted = false

    var profileUrl: String? = null
    private var currentData: ProfileModel? = null

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _uiEvents = MutableSharedFlow<ProfileUiEvent>()
    val uiEvents: SharedFlow<ProfileUiEvent> = _uiEvents.asSharedFlow()

    fun start() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true
        loadProfile()
    }

    private fun loadProfile() {
        val url = profileUrl ?: return
        scope.launch {
            try {
                _refreshing.value = true
                val profileModel = profileRepository.loadProfile(url)
                currentData = profileModel
                loadAvatar(profileModel)
                _uiEvents.emit(ProfileUiEvent.ShowProfile(profileModel))
            } catch (e: Throwable) {
                errorHandler.handle(e)
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun saveNote(note: String) {
        scope.launch {
            runCatching { profileRepository.saveNote(note) }
                    .onSuccess { _uiEvents.emit(ProfileUiEvent.OnSaveNote(it)) }
                    .onFailure { errorHandler.handle(it) }
        }
    }

    fun onContactClick(item: ProfileModel.Contact) {
        linkHandler.handle(item.url, router)
    }

    fun onDeviceClick(item: ProfileModel.Device) {
        linkHandler.handle(item.url, router)
    }

    fun onStatClick(item: ProfileModel.Stat) {
        linkHandler.handle(item.url, router)
    }

    fun copyUrl() {
        clipboardHelper.copyToClipboard(profileUrl)
    }

    fun navigateToQms() {
        currentData?.let {
            linkHandler.handle(it.contacts[0].url, router)
        }
    }

    private fun loadAvatar(profile: ProfileModel) {
        scope.launch {
            runCatching {
                val url = profile.avatar ?: ""
                val data = ForPdaCoil.normalizeData(url)
                val req = ImageRequest.Builder(context.applicationContext)
                        .data(data)
                        .allowHardware(false)
                        .build()
                when (val r = ForPdaCoil.imageLoader.execute(req)) {
                    is SuccessResult -> (r.drawable as? BitmapDrawable)?.bitmap
                    else -> null
                }
            }.onSuccess { bitmap ->
                bitmap?.let { _uiEvents.emit(ProfileUiEvent.ShowAvatar(it)) }
            }.onFailure {
                errorHandler.handle(it)
            }
        }
    }
}

sealed class ProfileUiEvent {
    data class ShowProfile(val profile: ProfileModel) : ProfileUiEvent()
    data class ShowAvatar(val bitmap: android.graphics.Bitmap) : ProfileUiEvent()
    data class OnSaveNote(val result: Boolean) : ProfileUiEvent()
}
