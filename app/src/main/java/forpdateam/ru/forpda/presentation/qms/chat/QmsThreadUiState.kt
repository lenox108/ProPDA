package forpdateam.ru.forpda.presentation.qms.chat

import forpdateam.ru.forpda.entity.remote.qms.QmsChatModel

sealed class QmsThreadUiState {
    data object Idle : QmsThreadUiState()

    data class Loading(val requestId: Int) : QmsThreadUiState()

    data class Content(
            val requestId: Int,
            val chat: QmsChatModel,
            val canLoadMore: Boolean = true
    ) : QmsThreadUiState()

    data class Empty(
            val requestId: Int,
            val reason: String
    ) : QmsThreadUiState()

    data class Error(
            val requestId: Int,
            val kind: QmsLoadErrorKind,
            val message: String,
            val canRetry: Boolean = true
    ) : QmsThreadUiState()
}

enum class QmsLoadErrorKind {
    NETWORK,
    SESSION,
    CAPTCHA,
    SERVER,
    PARSER,
    UNKNOWN
}

sealed class QmsDialogState {
    data object Idle : QmsDialogState()

    data class Loading(val requestId: Int) : QmsDialogState()

    data class Loaded(
            val messages: List<forpdateam.ru.forpda.entity.remote.qms.QmsMessage>,
            val canLoadMore: Boolean = true,
            val editor: Any? = null
    ) : QmsDialogState()

    data class Empty(val reason: String) : QmsDialogState()

    data class Error(
            val type: QmsLoadErrorKind,
            val message: String,
            val retryAvailable: Boolean = true
    ) : QmsDialogState()
}
