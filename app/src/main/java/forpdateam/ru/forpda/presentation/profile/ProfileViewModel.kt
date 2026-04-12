package forpdateam.ru.forpda.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel
import forpdateam.ru.forpda.model.SchedulersProvider
import forpdateam.ru.forpda.model.repository.profile.ProfileRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asCoroutineDispatcher
import kotlinx.coroutines.withContext

class ProfileViewModel(
        private val profileRepository: ProfileRepository,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler,
        private val schedulers: SchedulersProvider
) : ViewModel() {

    @Volatile
    private var profileView: ProfileView? = null

    fun attachView(view: ProfileView) {
        profileView = view
    }

    fun detachView() {
        profileView = null
    }

    private var subscriptionsStarted = false

    var profileUrl: String? = null
    private var currentData: ProfileModel? = null

    fun start() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true
        loadProfile()
    }

    private fun loadProfile() {
        val url = profileUrl ?: return
        viewModelScope.launch {
            try {
                profileView?.setRefreshing(true)
                val profileModel = profileRepository.loadProfile(url)
                currentData = profileModel
                loadAvatar(profileModel)
                profileView?.showProfile(profileModel)
            } catch (e: Throwable) {
                errorHandler.handle(e)
            } finally {
                profileView?.setRefreshing(false)
            }
        }
    }

    fun saveNote(note: String) {
        viewModelScope.launch {
            runCatching { profileRepository.saveNote(note) }
                    .onSuccess { profileView?.onSaveNote(it) }
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
        Utils.copyToClipBoard(profileUrl)
    }

    fun navigateToQms() {
        currentData?.let {
            linkHandler.handle(it.contacts[0].url, router)
        }
    }

    private fun loadAvatar(profile: ProfileModel) {
        val io = schedulers.io().asCoroutineDispatcher()
        viewModelScope.launch {
            runCatching {
                withContext(io) {
                    ForPdaCoil.loadBitmapSync(App.getContext(), profile.avatar ?: "")
                }
            }.onSuccess { bitmap ->
                bitmap?.let { profileView?.showAvatar(it) }
            }.onFailure {
                errorHandler.handle(it)
            }
        }
    }

    class Factory(
            private val profileRepository: ProfileRepository,
            private val router: TabRouter,
            private val linkHandler: ILinkHandler,
            private val errorHandler: IErrorHandler,
            private val schedulers: SchedulersProvider
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass != ProfileViewModel::class.java) throw IllegalArgumentException("Unknown ViewModel class")
            return ProfileViewModel(profileRepository, router, linkHandler, errorHandler, schedulers) as T
        }
    }
}
