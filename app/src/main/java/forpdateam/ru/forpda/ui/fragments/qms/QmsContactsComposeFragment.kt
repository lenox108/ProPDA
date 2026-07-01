package forpdateam.ru.forpda.ui.fragments.qms

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.presentation.qms.contacts.QmsContactsViewModel
import forpdateam.ru.forpda.ui.compose.screens.QmsContactsScreen
import forpdateam.ru.forpda.ui.compose.theme.ForpdaTheme
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment

/**
 * Compose-версия QmsContactsFragment.
 *
 * Использует уже существующую [QmsContactsViewModel] (которая отдаёт
 * `StateFlow<UiState>`) и монтирует [QmsContactsScreen] в
 * `ComposeView`. Полный набор действий, которые выполняет legacy
 * `QmsContactsFragment` (FAB, диалог-меню, snackbar, добавить
 * заметку, открыть профиль/чёрный список, pull-to-refresh),
 * остаётся в legacy-фрагменте; здесь мы закрываем только
 * визуальный список, что совпадает с §3.2 шагом 2.
 *
 * Рядом со старым [QmsContactsFragment] (для A/B). Переключение
 * в `TabHelper.kt` и `CiceroneRouter.kt` — отдельный commit.
 */
@AndroidEntryPoint
class QmsContactsComposeFragment : RecyclerFragment() {

    private val viewModel: QmsContactsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ForpdaTheme {
                    QmsContactsScreen(
                        viewModel = viewModel,
                        onItemClick = { contact -> viewModel.onItemClick(contact) }
                    )
                }
            }
        }
    }
}
