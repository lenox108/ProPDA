package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import forpdateam.ru.forpda.entity.remote.theme.PollQuestionItem
import java.net.URLEncoder

/**
 * Builds the poll-vote request body, byte-for-byte the way the WebView JS `submitThemePoll` does:
 * every checked radio/checkbox as `name=value`, followed by every non-blank hidden input (defaulting
 * to `addpoll=1` when the parser found none), UTF-8 percent-encoded like `encodeURIComponent` and
 * joined with `&`. Kept pure/synchronous so the exact wire format is unit-testable without a device.
 */
object PollVoteFormEncoder {

    /** @return the encoded form, or null when nothing is selected (the caller must prompt to pick). */
    fun encode(checkedItems: List<PollQuestionItem>, hiddenInputs: List<Pair<String, String>>): String? {
        if (checkedItems.isEmpty()) return null
        val parts = ArrayList<String>(checkedItems.size + hiddenInputs.size)
        for (item in checkedItems) {
            parts.add("${enc(item.name.orEmpty())}=${enc(item.value)}")
        }
        val hidden = hiddenInputs.filter { it.first.isNotBlank() }.ifEmpty { listOf("addpoll" to "1") }
        for ((name, value) in hidden) {
            parts.add("${enc(name)}=${enc(value)}")
        }
        return parts.joinToString("&")
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
