package forpdateam.ru.forpda.presentation.checker

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import forpdateam.ru.forpda.entity.remote.checker.UpdateData
import forpdateam.ru.forpda.model.repository.checker.CheckerRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val CHECKER_TAG = "ForPDA.Checker"

/**
 * Экран проверки обновлений без Moxy — задел под единый стиль ViewModel + StateFlow.
 */
class CheckerViewModel(
        private val checkerRepository: CheckerRepository,
        private val errorHandler: IErrorHandler
) : ViewModel() {

    data class UiState(
            val loading: Boolean = true,
            val update: UpdateData? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun checkUpdate(forceRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            runCatching {
                checkerRepository.checkUpdate(forceRefresh)
            }.onSuccess { data ->
                Log.d(CHECKER_TAG, "checkUpdate ok code=${data.code}")
                _uiState.update { it.copy(loading = false, update = data) }
            }.onFailure { e ->
                Log.e(CHECKER_TAG, "checkUpdate failed", e)
                _uiState.update { it.copy(loading = false, update = null) }
                errorHandler.handle(e)
            }
        }
    }

    class Factory(
            private val checkerRepository: CheckerRepository,
            private val errorHandler: IErrorHandler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass != CheckerViewModel::class.java) {
                throw IllegalArgumentException("Unknown ViewModel class")
            }
            return CheckerViewModel(checkerRepository, errorHandler) as T
        }
    }
}
