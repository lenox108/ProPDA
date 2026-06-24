package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.diagnostic.TopicHighlightDiagnostics
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.repository.theme.ThemeReadPositionRepository
import java.util.concurrent.atomic.AtomicInteger

/**
 * Glue between the resolver and the [ThemePage] data class.
 *
 * The single source of truth for the highlight is here. The template reads
 * [ThemePage.highlightTarget] / [ThemePage.renderGenerationId] via the
 * `ppda_highlight_post_id_int` / `ppda_render_generation_id_int` variables.
 *
 * The [nextRenderGenerationId] counter is a process-wide atomic so that two
 * parallel `mapEntity` calls (e.g. for a hybrid refresh) do not collide.
 */
object TopicHighlightApply {

    private val nextRenderGenerationId = AtomicInteger(0)

    fun nextGeneration(): Int = nextRenderGenerationId.incrementAndGet()

    /**
     * Resolve the highlight target for [page] and stamp it onto the page.
     * Returns the resolved [HighlightResolution] for diagnostics and so callers
     * can log render success.
     */
    fun applyToPage(
            page: ThemePage,
            readPositionRepository: ThemeReadPositionRepository,
            explicitPostId: Long? = null,
            unreadUrl: String? = null,
            firstUnreadPostId: Long? = null,
            unreadPage: Int? = null,
            readPositionOverride: ReadPosition? = null,
            lastReadSource: String? = null,
    ): HighlightResolution {
        val topicId = page.id.toLong()
        val unread = if (firstUnreadPostId != null && firstUnreadPostId > 0L) {
            UnreadTarget(
                    topicId = topicId,
                    firstUnreadPostId = firstUnreadPostId,
                    unreadPage = unreadPage,
                    unreadUrl = unreadUrl
            ).also {
                TopicHighlightDiagnostics.unreadTargetLoaded(
                        topicId = topicId,
                        firstUnreadPostId = it.firstUnreadPostId,
                        unreadPage = it.unreadPage,
                        unreadUrl = it.unreadUrl
                )
            }
        } else {
            null
        }
        val readPosition = readPositionOverride ?: readPositionRepository.get(topicId)
        val pagePostIds = page.posts.map { it.id.toLong() }

        val resolution = HighlightResolver.resolve(
                topicId = topicId,
                unread = unread,
                readPosition = readPosition,
                explicitPostId = explicitPostId,
                pagePostIds = pagePostIds,
                lastReadSource = lastReadSource,
        )

        val resolvedTarget = resolution.target
        val previousTarget = page.highlightTarget
        page.highlightTarget = resolvedTarget

        // Only bump the generation when the highlight actually changed (or when the
        // page had no generation yet). A refresh of the *same* page with the *same*
        // resolved target keeps the generation stable, so the JS guard still accepts
        // callbacks for the current render instead of treating them as stale.
        val sameAsBefore = previousTarget == resolvedTarget && page.renderGenerationId > 0
        if (!sameAsBefore) {
            page.renderGenerationId = nextGeneration()
        }
        return resolution
    }
}
