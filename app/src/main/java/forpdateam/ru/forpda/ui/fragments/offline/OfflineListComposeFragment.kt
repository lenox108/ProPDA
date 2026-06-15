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
import forpdateam.ru.forpda.entity.db.offline.OfflineItemType
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.presentation.offline.OfflineListViewModel
import forpdateam.ru.forpda.ui.compose.screens.OfflineListScreen
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Compose host for the offline-reading list (§5.1 of REFACTOR_PLAN.md).
 *
 * Mirrors the [forpdateam.ru.forpda.ui.fragments.qms.QmsContactsComposeFragment]
 * pattern: it extends [RecyclerFragment] (so it can be hosted in the existing
 * tab navigation), injects [OfflineListViewModel], and mounts
 * [OfflineListScreen] in a `ComposeView`. The legacy fragment path is
 * preserved by routing through [forpdateam.ru.forpda.ui.navigation.TabHelper.useComposeOfflineList].
 *
 * Row click navigation is wired through [TabRouter.navigateTo]. The
 * destination fragment ([ArticleContentFragment] /
 * [forpdateam.ru.forpda.ui.fragments.theme.ThemeFragmentWeb]) consults
 * [forpdateam.ru.forpda.model.data.offline.OfflineArticleSource] in the
 * same way it would for a cold-open: if the row is in the offline
 * cache, the asset loader short-circuits the network call and renders
 * the saved HTML.
 */
@AndroidEntryPoint
class OfflineListComposeFragment : RecyclerFragment() {

    private val viewModel: OfflineListViewModel by viewModels()

    @Inject lateinit var router: TabRouter

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
        val row = viewModel.uiState.value.items.firstOrNull { it.id == id } ?: return
        val screen = buildScreenForRow(row) ?: return
        router.navigateTo(screen)
    }

    private fun buildScreenForRow(
            row: forpdateam.ru.forpda.entity.db.offline.OfflineItemRoom
    ): Screen? = buildOfflineItemScreen(row)
}

/**
 * Top-level helper used by [OfflineListComposeFragment.openOfflineItem]
 * and tested in [forpdateam.ru.forpda.ui.fragments.offline.OfflineListOpenItemRoutingTest].
 *
 * Parses the row id (e.g. `article:42` / `theme:777` / `theme:777:3`)
 * and returns the matching [Screen] for the navigation. Returns
 * `null` when the id cannot be parsed or the type is unknown.
 */
internal fun buildOfflineItemScreen(
        row: forpdateam.ru.forpda.entity.db.offline.OfflineItemRoom
): Screen? = when (row.type) {
    OfflineItemType.ARTICLE -> {
        val articleId = row.id.removePrefix("article:").toIntOrNull() ?: 0
        if (articleId <= 0) null
        else Screen.ArticleDetail().apply {
            this.articleId = articleId
            articleUrl = row.sourceUrl
            articleTitle = row.title
        }
    }
    OfflineItemType.THEME -> {
        // Format: "theme:<topicId>:<page>" or "theme:<topicId>"
        val tail = row.id.removePrefix("theme:")
        val topicId = tail.substringBefore(':').toIntOrNull() ?: 0
        if (topicId <= 0) null
        else Screen.Theme().apply {
            themeUrl = row.sourceUrl
            screenTitle = row.title
        }
    }
    else -> null
}
