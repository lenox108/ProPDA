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
        val readPosition = readPositionRepository.get(topicId)
        val pagePostIds = page.posts.map { it.id.toLong() }

        val resolution = HighlightResolver.resolve(
                topicId = topicId,
                unread = unread,
                readPosition = readPosition,
                explicitPostId = explicitPostId,
                pagePostIds = pagePostIds,
        )

        // TODO restore on next pass: ThemePage.highlightTarget and
        //  ThemePage.renderGenerationId are not in the tracked entity yet. The
        //  resolution is still returned to callers so the diagnostic pipeline
        //  (TopicHighlightDiagnostics) remains observable.
        @Suppress("UNUSED_VARIABLE")
        val _resolvedTarget = resolution.target
        @Suppress("UNUSED_VARIABLE")
        val _bumpGeneration = if (false) nextGeneration() else 0
        return resolution
    }
}
