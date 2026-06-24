package forpdateam.ru.forpda.presentation.theme

/**
 * Ensures Kotlin-owned scroll restore is scheduled from a single lifecycle surface.
 * When [domLifecycleClaimed] is true, [ThemeFragmentWeb.onDomContentComplete] already
 * armed the scroll command batch and [onPageComplete] must not compete with it.
 */
object TopicScrollRestoreSchedulingPolicy {

    fun shouldScheduleKotlinRestoreOnPageComplete(domLifecycleClaimed: Boolean): Boolean =
            !domLifecycleClaimed

    fun restorePathLabel(surface: String, kind: String?): String =
            if (kind.isNullOrBlank()) surface else "$surface:$kind"
}
