package forpdateam.ru.forpda.ui.fragments.news.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.presentation.articles.list.ArticlesListViewModel
import forpdateam.ru.forpda.ui.compose.screens.ArticlesListScreen
import forpdateam.ru.forpda.ui.compose.theme.ForpdaTheme
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment

/**
 * Compose preview of the article list (§3.2 of REFACTOR_PLAN.md).
 *
 * Sits next to the legacy [NewsMainFragment] for A/B comparison.
 * Wires up the same [ArticlesListViewModel] used by the legacy
 * RecyclerView path so the data flow stays identical. The host
 * fragment owns the navigation/router integration
 * (e.g. [ArticlesListViewModel.onItemClick] navigates to
 * [forpdateam.ru.forpda.presentation.Screen.ArticleDetail]).
 *
 * Routing in [forpdateam.ru.forpda.ui.navigation.TabHelper] is
 * intentionally NOT wired in this commit — the flag
 * `useComposeArticleList` is added so a follow-up commit can flip
 * the switch without touching the Compose host.
 */
@AndroidEntryPoint
class NewsMainComposeFragment : RecyclerFragment() {

    private val viewModel: ArticlesListViewModel by viewModels()

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
                ForpdaTheme {
                    ArticlesListScreen(
                            viewModel = viewModel,
                            onItemClick = { item -> viewModel.onItemClick(item) }
                    )
                }
            }
        }
    }
}
