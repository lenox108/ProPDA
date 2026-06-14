package forpdateam.ru.forpda.presentation.theme

/**
 * Guards [ThemeViewModel.onTopicRenderSettled] and hat-metadata reload until programmatic
 * anchor/restore scroll commands finish — otherwise a second [loadDataWithBaseURL] fires mid-scroll.
 */
internal object ThemeRenderSettledPolicy {

    fun isBlockingScrollKind(kind: ThemeScrollCommand.Kind): Boolean =
            when (kind) {
                ThemeScrollCommand.Kind.INITIAL_ANCHOR,
                ThemeScrollCommand.Kind.REFRESH_RESTORE,
                ThemeScrollCommand.Kind.END_ANCHOR_OR_BOTTOM,
                ThemeScrollCommand.Kind.ANCHOR -> true
                else -> false
            }
}
