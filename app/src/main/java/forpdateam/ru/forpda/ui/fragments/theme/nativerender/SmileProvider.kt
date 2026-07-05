package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.content.res.AssetManager
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import java.util.regex.Pattern

/**
 * Renders 4pda smile shortcodes (`:thank_you:`, `:happy:`, …) as native inline images from the
 * bundled `assets/smiles/` gifs. The WebView engine does this via `z_emoticons.js`; the native
 * renderer shows the raw shortcode text without this pass (roadmap `native-topic-renderer.md`,
 * Фаза 2 "смайлы").
 *
 * The shortcode → filename map is parsed once from the same `assets/forpda/scripts/z_emoticons.js`
 * the WebView uses, so the two stay in sync. v1 handles only unambiguous colon-delimited word
 * shortcodes (`:[a-z0-9_]+:`) — the ones that currently leak as literal text; 2-char emoticons like
 * `:)` are a later, riskier (false-positive-prone) step. Smile gifs are local (no network, no async
 * layout jump); they render as a static first frame at a fixed inline size.
 */
object SmileProvider {

    private const val SMILE_ASSET_DIR = "smiles"
    private const val EMOTICON_SCRIPT = "forpda/scripts/z_emoticons.js"

    /** shortcode (":thank_you:") → gif filename ("thank_you.gif"). */
    @Volatile
    private var codeToFile: Map<String, String>? = null

    /** Combined "|"-alternation of all known shortcodes, longest first. */
    @Volatile
    private var pattern: Pattern? = null

    private val drawableCache = HashMap<String, Drawable?>()

    private fun ensureLoaded(assets: AssetManager) {
        if (codeToFile != null) return
        synchronized(this) {
            if (codeToFile != null) return
            val map = LinkedHashMap<String, String>()
            runCatching {
                val js = assets.open(EMOTICON_SCRIPT).bufferedReader().use { it.readText() }
                // Match:  ":word:": ["file.gif"  — colon-delimited word shortcodes only.
                val entry = Pattern.compile("\"(:[a-z0-9_]+:)\"\\s*:\\s*\\[\"([a-z0-9_]+\\.gif)\"")
                val m = entry.matcher(js)
                while (m.find()) {
                    map[m.group(1)!!] = m.group(2)!!
                }
            }
            codeToFile = map
            pattern = if (map.isEmpty()) {
                null
            } else {
                // Longest first so e.g. ":happy:" isn't shadowed by a shorter prefix.
                val alternation = map.keys.sortedByDescending { it.length }
                        .joinToString("|") { Pattern.quote(it) }
                Pattern.compile(alternation)
            }
        }
    }

    /**
     * Returns [text] with every known smile shortcode replaced by an inline [ImageSpan] sized
     * [sizePx] square. If nothing matches (or the map failed to load) the original text is returned.
     */
    fun applySmiles(text: CharSequence, assets: AssetManager, sizePx: Int): CharSequence {
        ensureLoaded(assets)
        val p = pattern ?: return text
        val map = codeToFile ?: return text
        val m = p.matcher(text)
        if (!m.find()) return text
        val out = SpannableStringBuilder(text)
        // Re-match on the builder; spans are applied by original index (indices are stable since we
        // only overlay spans, not edit text).
        m.reset(out)
        while (m.find()) {
            val file = map[m.group()] ?: continue
            val drawable = drawableFor(file, assets, sizePx) ?: continue
            out.setSpan(
                    ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM),
                    m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        return out
    }

    private fun drawableFor(file: String, assets: AssetManager, sizePx: Int): Drawable? {
        val cached = drawableCache[file]
        if (cached != null) {
            return cached.constantState?.newDrawable()?.mutate()?.apply { setBounds(0, 0, sizePx, sizePx) }
        }
        if (drawableCache.containsKey(file)) return null // known-missing
        val d = runCatching {
            assets.open("$SMILE_ASSET_DIR/$file").use { Drawable.createFromStream(it, file) }
        }.getOrNull()
        drawableCache[file] = d
        return d?.constantState?.newDrawable()?.mutate()?.apply { setBounds(0, 0, sizePx, sizePx) }
                ?: d?.apply { setBounds(0, 0, sizePx, sizePx) }
    }
}
