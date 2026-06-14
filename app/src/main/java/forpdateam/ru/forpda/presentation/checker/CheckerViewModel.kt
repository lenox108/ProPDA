package forpdateam.ru.forpda.presentation.checker

import forpdateam.ru.forpda.presentation.BaseViewModel
import timber.log.Timber

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import forpdateam.ru.forpda.entity.remote.checker.UpdateData
import forpdateam.ru.forpda.model.repository.checker.CheckerRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val CHECKER_TAG = "ForPDA.Checker"

/**
 * Экран проверки обновлений без Moxy — задел под единый стиль ViewModel + StateFlow.
 */
@HiltViewModel
class CheckerViewModel @Inject constructor(
        private val checkerRepository: CheckerRepository,
        private val errorHandler: IErrorHandler
) : BaseViewModel() {

    data class UiState(
            val loading: Boolean = true,
            val update: UpdateData? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    private var checkJob: Job? = null

    fun checkUpdate(forceRefresh: Boolean) {
        checkJob?.cancel()
        checkJob = scope.launch {
            _uiState.update { it.copy(loading = true) }
            runCatching {
                checkerRepository.checkUpdate(forceRefresh)
            }.onSuccess { data ->
                Timber.d("checkUpdate ok code=${data.code}")
                _uiState.update { it.copy(loading = false, update = data) }
            }.onFailure { e ->
                Timber.e(e, "checkUpdate failed")
                _uiState.update { it.copy(loading = false, update = null) }
                errorHandler.handle(e)
            }
        }
    }

}
