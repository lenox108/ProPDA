package forpdateam.ru.forpda.ui.fragments.favorites

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.presentation.favorites.FavoritesViewModel
import forpdateam.ru.forpda.ui.compose.screens.FavoritesScreen
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment

/**
 * Compose preview of the favorites list (§3.2 of REFACTOR_PLAN.md).
 *
 * Sits next to the legacy [FavoritesFragment] for A/B comparison.
 * Wires up the same [FavoritesViewModel] used by the legacy
 * RecyclerView path; the navigation is handled by the VM (e.g.
 * [FavoritesViewModel.onItemClick] navigates to the topic or
 * forum).
 *
 * Routing in [forpdateam.ru.forpda.ui.navigation.TabHelper] is
 * intentionally NOT wired in this commit — the flag
 * `useComposeFavorites` was added in a previous commit so a
 * follow-up can flip the switch without touching the Compose
 * host.
 */
@AndroidEntryPoint
class FavoritesComposeFragment : RecyclerFragment() {

    private val viewModel: FavoritesViewModel by viewModels()

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        viewModel.start()
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                FavoritesScreen(
                        viewModel = viewModel,
                        onItemClick = { item -> viewModel.onItemClick(item) }
                )
            }
        }
    }
}
