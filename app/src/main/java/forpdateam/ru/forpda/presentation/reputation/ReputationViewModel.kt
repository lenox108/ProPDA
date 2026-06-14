package forpdateam.ru.forpda.presentation.reputation

import forpdateam.ru.forpda.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import forpdateam.ru.forpda.entity.remote.reputation.RepData
import forpdateam.ru.forpda.entity.remote.reputation.RepItem
import forpdateam.ru.forpda.entity.remote.reputation.ReputationReportForm
import forpdateam.ru.forpda.model.data.remote.api.reputation.ReputationApi
import forpdateam.ru.forpda.model.repository.avatar.AvatarRepository
import forpdateam.ru.forpda.model.repository.reputation.ReputationRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ReputationViewModel @Inject constructor(
        private val reputationRepository: ReputationRepository,
        private val avatarRepository: AvatarRepository,
        private val router: TabRouter,
        private val linkHandler: ILinkHandler,
        private val errorHandler: IErrorHandler
) : BaseViewModel() {

    private var loadJob: Job? = null
    private var changeJob: Job? = null
    private var subscriptionsStarted = false

    private val _currentData = MutableStateFlow(RepData())
    val currentData: StateFlow<RepData> = _currentData.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _uiEvents = MutableSharedFlow<ReputationUiEvent>()
    val uiEvents: SharedFlow<ReputationUiEvent> = _uiEvents.asSharedFlow()

    fun setInitialData(data: RepData) {
        _currentData.value = data
    }

    fun start() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true
        loadReputation()
    }

    fun loadReputation() {
        loadJob?.cancel()
        loadJob = scope.launch {
            _refreshing.value = true
            try {
                val data = reputationRepository.loadReputation(
                        _currentData.value.id, _currentData.value.mode, _currentData.value.sort, _currentData.value.pagination.st
                )
                _currentData.value = data
                _uiEvents.emit(ReputationUiEvent.ShowReputation(data))
                tryShowAvatar(data)
            } catch (e: Exception) {
                errorHandler.handle(e)
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun changeReputation(type: Boolean, message: String) {
        changeJob?.cancel()
        changeJob = scope.launch {
            _refreshing.value = true
            try {
                val result = reputationRepository.changeReputation(0, _currentData.value.id, type, message)
                _uiEvents.emit(ReputationUiEvent.OnChangeReputation(result))
                loadReputation()
            } catch (e: Exception) {
                errorHandler.handle(e)
            } finally {
                _refreshing.value = false
            }
        }
    }

    private fun tryShowAvatar(data: RepData) {
        scope.launch {
            runCatching {
                avatarRepository.getAvatar(data.nick.orEmpty())
            }.onSuccess {
                _uiEvents.emit(ReputationUiEvent.ShowAvatar(it))
            }.onFailure {
                errorHandler.handle(it)
            }
        }
    }

    fun selectPage(page: Int) {
        _currentData.value.pagination.st = page
        loadReputation()
    }

    fun setSort(sort: String) {
        _currentData.value.sort = sort
        loadReputation()
    }

    fun changeReputationMode() {
        _currentData.value.mode = if (_currentData.value.mode == ReputationApi.MODE_FROM) ReputationApi.MODE_TO else ReputationApi.MODE_FROM
        loadReputation()
    }

    fun onItemClick(item: RepItem) {
        scope.launch { _uiEvents.emit(ReputationUiEvent.ShowItemDialogMenu(item)) }
    }

    fun onItemLongClick(item: RepItem) {
        scope.launch { _uiEvents.emit(ReputationUiEvent.ShowItemDialogMenu(item)) }
    }

    fun navigateToProfile(userId: Int) {
        linkHandler.handle("https://4pda.to/forum/index.php?showuser=$userId", router)
    }

    fun navigateToMessage(item: RepItem) {
        linkHandler.handle(item.sourceUrl, router)
    }

    fun onReportClick(item: RepItem) {
        val reportUrl = item.reportActionUrl?.takeIf { it.isNotBlank() } ?: return
        if (item.id <= 0) return
        scope.launch {
            try {
                val form = reputationRepository.loadReportForm(
                        userId = _currentData.value.id,
                        reputationId = item.id,
                        reportUrl = reportUrl,
                )
                _uiEvents.emit(ReputationUiEvent.ShowReportDialog(item, form))
            } catch (e: Exception) {
                errorHandler.handle(e)
            }
        }
    }

    fun submitReport(item: RepItem, form: ReputationReportForm, message: String) {
        scope.launch {
            try {
                reputationRepository.submitReport(_currentData.value.id, form, message)
                _uiEvents.emit(ReputationUiEvent.OnReportSubmitted(true))
            } catch (e: Exception) {
                errorHandler.handle(e)
            }
        }
    }
}

sealed class ReputationUiEvent {
    data class ShowReputation(val data: RepData) : ReputationUiEvent()
    data class ShowAvatar(val avatarUrl: String) : ReputationUiEvent()
    data class ShowItemDialogMenu(val item: RepItem) : ReputationUiEvent()
    data class OnChangeReputation(val result: Boolean) : ReputationUiEvent()
    data class ShowReportDialog(val item: RepItem, val form: ReputationReportForm) : ReputationUiEvent()
    data class OnReportSubmitted(val result: Boolean) : ReputationUiEvent()
}
