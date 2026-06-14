package forpdateam.ru.forpda.ui.fragments.notes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.entity.app.notes.NoteItem
import forpdateam.ru.forpda.presentation.notes.NotesViewModel
import forpdateam.ru.forpda.ui.compose.screens.NotesScreen
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment

/**
 * Compose-версия NotesFragment для пилотного проекта.
 * Заменяет RecyclerView на Compose UI.
 */
@AndroidEntryPoint
class NotesComposeFragment : RecyclerFragment() {

    private val viewModel: NotesViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        return androidx.compose.ui.platform.ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NotesScreen(
                    viewModel = viewModel,
                    onNavigateToLink = { link ->
                        viewModel.onItemClick(forpdateam.ru.forpda.entity.app.notes.NoteItem().apply {
                            this.link = link
                        })
                    }
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Compose обрабатывает всё внутри, не нужно настраивать RecyclerView
    }
}
