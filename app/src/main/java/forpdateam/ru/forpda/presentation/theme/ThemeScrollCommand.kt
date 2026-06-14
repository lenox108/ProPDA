package forpdateam.ru.forpda.presentation.theme

import org.json.JSONObject
import java.util.UUID

/**
 * Explicit scroll command issued to theme WebView JS with completion callback.
 */
data class ThemeScrollCommand(
        val commandId: String,
        val kind: Kind,
        val anchorPostId: String? = null,
        val scrollY: Int? = null,
        val restoreId: String? = null,
        val restoreMode: String? = null
) {
    enum class Kind {
        ANCHOR,
        /** End-of-topic navigation: prefer server last-post anchor, then fall back to bottom. */
        END_ANCHOR_OR_BOTTOM,
        /** Resolves PageInfo.elemToScroll, then loadAnchorPostId, on first page load. */
        INITIAL_ANCHOR,
        SCROLL_Y,
        UNREAD,
        BOTTOM,
        REFRESH_RESTORE
    }

    fun toPayloadJson(): String = JSONObject().apply {
        put("commandId", commandId)
        put("kind", kind.name)
        anchorPostId?.let { put("anchorPostId", it) }
        scrollY?.let { put("scrollY", it) }
        restoreId?.let { put("restoreId", it) }
        restoreMode?.let { put("restoreMode", it) }
    }.toString()

    companion object {
        fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(8)

        fun refreshRestore(restoreId: String, mode: String): ThemeScrollCommand =
                ThemeScrollCommand(
                        commandId = newId(),
                        kind = Kind.REFRESH_RESTORE,
                        restoreId = restoreId,
                        restoreMode = mode
                )

        fun bottom(): ThemeScrollCommand =
                ThemeScrollCommand(commandId = newId(), kind = Kind.BOTTOM)

        fun anchor(postId: String): ThemeScrollCommand =
                ThemeScrollCommand(commandId = newId(), kind = Kind.ANCHOR, anchorPostId = postId)

        fun endAnchorOrBottom(postId: String): ThemeScrollCommand =
                ThemeScrollCommand(commandId = newId(), kind = Kind.END_ANCHOR_OR_BOTTOM, anchorPostId = postId)

        fun initialAnchor(): ThemeScrollCommand =
                ThemeScrollCommand(commandId = newId(), kind = Kind.INITIAL_ANCHOR)
    }
}
