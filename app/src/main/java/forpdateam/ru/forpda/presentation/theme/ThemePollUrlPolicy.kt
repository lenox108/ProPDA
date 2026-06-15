package forpdateam.ru.forpda.presentation.theme

import android.net.Uri
import java.util.regex.Pattern

/**
 * Pure URL helpers for the topic poll UX. Extracted from
 * `ThemeViewModel` (god-class §1.1) — no I/O, no state, easy to unit
 * test. The view-model still owns the call into `loadUrl` and the
 * state mutation around poll openness.
 */
object ThemePollUrlPolicy {

    private val ADD_POLL_PATTERN: Pattern =
            Pattern.compile("4pda\\.to.*?addpoll=1", Pattern.CASE_INSENSITIVE)

    /**
     * Returns a new URL with `&poll_open=true` appended (de-duplicated
     * against an existing `poll_open=true`).
     */
    fun appendPollOpen(url: String): String {
        val clean = url
                .replace("&poll_open=true", "")
                .replace("?poll_open=true&", "?")
                .replace("?poll_open=true", "")
        return clean + if (clean.contains("?")) "&poll_open=true" else "?poll_open=true"
    }

    /**
     * Strips the `mode=show` and `poll_open=true` flags from the
     * URL, then re-appends `poll_open=true` via [appendPollOpen].
     * Used by the "open poll" click in the topic header.
     */
    fun buildPollOpenUrl(themeUrl: String): String {
        val stripped = themeUrl
                .replaceFirst("#[^&]*".toRegex(), "")
                .replace("&mode=show", "")
                .replace("&poll_open=true", "")
        return appendPollOpen(stripped)
    }

    /**
     * Detects a 4pda "add poll" deep link and, if the topic id and
     * st-offset are known, rewrites the URL so the user lands back on
     * the current page. Returns `null` if [url] is not a poll add
     * request; otherwise the rewritten URL ready to be loaded.
     */
    fun rewriteAddPoll(url: String, topicId: Int, stOffset: Int): String? {
        if (!ADD_POLL_PATTERN.matcher(url).find()) return null
        var uri = Uri.parse(url)
        uri = uri.buildUpon()
                .appendQueryParameter("showtopic", Integer.toString(topicId))
                .appendQueryParameter("st", stOffset.toString())
                .build()
        return uri.toString()
    }
}
