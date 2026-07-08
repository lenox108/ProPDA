package forpdateam.ru.forpda.common

import android.text.TextPaint
import android.text.style.URLSpan

/**
 * A [URLSpan] that paints its own text in [color] — overriding the TextView's theme `linkColor`, which
 * 4pda's server markup often leaves at a dim, near-invisible value. It stays a real [URLSpan] (keeps `.url`)
 * so [LinkMovementMethod] handles the click exactly as before; only the drawn colour changes.
 *
 * Used for the profile «О себе» link and the header signature links, where the theme link colour is too dim
 * to read on the reading surface.
 */
class ColoredUrlSpan(url: String, private val color: Int) : URLSpan(url) {
    override fun updateDrawState(ds: TextPaint) {
        ds.color = color
        ds.isUnderlineText = true
    }
}
