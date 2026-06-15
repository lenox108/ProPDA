package forpdateam.ru.forpda.ui.fragments.offline

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.presentation.offline.OfflineListViewModel
import forpdateam.ru.forpda.ui.compose.screens.OfflineListScreen
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Compose host for the offline-reading list (§5.1 of REFACTOR_PLAN.md).
 *
 * Mirrors the [forpdateam.ru.forpda.ui.fragments.qms.QmsContactsComposeFragment]
 * pattern: it extends [RecyclerFragment] (so it can be hosted in the existing
 * tab navigation), injects [OfflineListViewModel], and mounts
 * [OfflineListScreen] in a `ComposeView`. The legacy fragment path is
 * preserved by routing through [forpdateam.ru.forpda.ui.navigation.TabHelper.useComposeOfflineList].
 */
@AndroidEntryPoint
class OfflineListComposeFragment : RecyclerFragment() {

    private val viewModel: OfflineListViewModel by viewModels()

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                OfflineListScreen(
                        viewModel = viewModel,
                        onItemClick = { item -> viewModel.onItemClick(item) }
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is OfflineListViewModel.Event.OpenItem -> openOfflineItem(event.id)
                    }
                }
            }
        }
    }

    private fun openOfflineItem(id: String) {
        // §5.1 cold-open path: navigation to the saved item lives in a separate
        // commit. For now we just no-op so the screen doesn't crash when a row
        // is tapped.
    }
}
