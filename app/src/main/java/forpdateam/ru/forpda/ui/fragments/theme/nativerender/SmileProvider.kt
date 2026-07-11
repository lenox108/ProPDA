package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.content.res.AssetManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.view.View
import android.widget.TextView
import java.nio.ByteBuffer
import java.util.regex.Pattern

/**
 * Renders 4pda smile shortcodes (`:thank_you:`, `:happy:`, …) as native inline images from the
 * bundled `assets/smiles/` gifs. The WebView engine does this via `z_emoticons.js`; the native
 * renderer shows the raw shortcode text without this pass (roadmap `native-topic-renderer.md`,
 * Фаза 2 "смайлы").
 *
 * The shortcode → filename map is parsed once from the same `assets/forpda/scripts/z_emoticons.js`
 * the WebView uses, so the two stay in sync. v1 handles only unambiguous colon-delimited word
 * shortcodes (`:[a-z0-9_-]+:`) — the ones that currently leak as literal text; 2-char emoticons like
 * `:)` are a later, riskier (false-positive-prone) step. Smile gifs are local (no network, no async
 * layout jump); by default they render as a static first frame at a fixed inline size. With the
 * «Анимированные смайлы» pref on (and API 28+) the span carries an [AnimatedImageDrawable] instead —
 * playback is wired to the host TextView via [startAnimations].
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

    /** gif filename → decoded first frame (null = known-undecodable, don't retry). */
    private val bitmapCache = HashMap<String, Bitmap?>()

    /** gif filename → raw bytes for the animated decode path (null = unreadable, don't retry).
     *  Each animated span needs its OWN [AnimatedImageDrawable] (per-span bounds + playback state),
     *  so what's shared is the source bytes, not the drawable. All 171 gifs total ~1.2 MB. */
    private val bytesCache = HashMap<String, ByteArray?>()

    private fun ensureLoaded(assets: AssetManager) {
        if (codeToFile != null) return
        synchronized(this) {
            if (codeToFile != null) return
            val map = LinkedHashMap<String, String>()
            runCatching {
                val js = assets.open(EMOTICON_SCRIPT).bufferedReader().use { it.readText() }
                // Match:  ":word:": ["file.gif"  — colon-delimited word shortcodes only.
                // Hyphens are part of the word (":scratch_one-s_head:", ":i-m_so_happy:").
                val entry = Pattern.compile("\"(:[a-z0-9_-]+:)\"\\s*:\\s*\\[\"([a-z0-9_-]+\\.gif)\"")
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
     * With [animated] the span gets a live [AnimatedImageDrawable] (API 28+; the host must call
     * [startAnimations] on the TextView after setText); otherwise — a static first frame.
     */
    fun applySmiles(text: CharSequence, res: Resources, sizePx: Int, animated: Boolean = false): CharSequence {
        ensureLoaded(res.assets)
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
            val drawable = (if (animated) animatedDrawableFor(file, res.assets, sizePx) else null)
                    ?: drawableFor(file, res, sizePx) ?: continue
            out.setSpan(
                    ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM),
                    m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        return out
    }

    /**
     * A fresh [BitmapDrawable] per span over a shared cached frame — an [ImageSpan]'s drawable carries
     * its own bounds and may be drawn by several TextViews at once, so instances are never shared.
     *
     * Decoding goes through [BitmapFactory] rather than `Drawable.createFromStream`: 149 of the 171
     * bundled smiles are animated, and for those the latter hands back an `AnimatedImageDrawable`
     * whose `constantState` is null — uncopyable, so every cache hit produced a null drawable and the
     * shortcode leaked through as literal text.
     */
    private fun drawableFor(file: String, res: Resources, sizePx: Int): Drawable? {
        val frame = frameFor(file, res.assets) ?: return null
        return BitmapDrawable(res, frame).apply { setBounds(0, 0, sizePx, sizePx) }
    }

    private fun frameFor(file: String, assets: AssetManager): Bitmap? = synchronized(bitmapCache) {
        if (bitmapCache.containsKey(file)) return bitmapCache[file] // includes known-undecodable (null)
        val frame = runCatching {
            assets.open("$SMILE_ASSET_DIR/$file").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
        bitmapCache[file] = frame
        return frame
    }

    /**
     * A fresh [AnimatedImageDrawable] over the cached gif bytes, or null when the platform can't
     * (API < 28), decoding fails, or the gif turns out to be single-frame (ImageDecoder then returns
     * a plain BitmapDrawable) — every null falls back to the static [drawableFor] path in the caller.
     */
    private fun animatedDrawableFor(file: String, assets: AssetManager, sizePx: Int): Drawable? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val bytes = bytesFor(file, assets) ?: return null
        return runCatching {
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes)))
        }.getOrNull()
                ?.takeIf { it is AnimatedImageDrawable }
                ?.apply { setBounds(0, 0, sizePx, sizePx) }
    }

    private fun bytesFor(file: String, assets: AssetManager): ByteArray? = synchronized(bytesCache) {
        if (bytesCache.containsKey(file)) return bytesCache[file] // includes known-unreadable (null)
        val bytes = runCatching {
            assets.open("$SMILE_ASSET_DIR/$file").use { it.readBytes() }
        }.getOrNull()
        bytesCache[file] = bytes
        return bytes
    }

    /**
     * Starts playback for every animated smile span in [tv]'s current text and ties it to the view's
     * window attachment (start on attach / stop on detach — the renderers rebuild bodies from scratch
     * each bind, so a detached TextView is a discarded one and its playback must not keep ticking).
     *
     * A span's drawable lives outside the view hierarchy, so frame ticks have nothing to invalidate
     * by themselves: a per-view [Drawable.Callback] bridges them to [TextView.invalidate]. The driver
     * object is strongly held through the attach-listener list ([Drawable.setCallback] keeps only a
     * weak reference). No-op below API 28 or when the text carries no animated spans.
     */
    fun startAnimations(tv: TextView) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val text = tv.text as? Spanned ?: return
        val drawables = text.getSpans(0, text.length, ImageSpan::class.java)
                .mapNotNull { it.drawable as? AnimatedImageDrawable }
        if (drawables.isEmpty()) return
        val driver = object : Drawable.Callback, View.OnAttachStateChangeListener {
            override fun invalidateDrawable(who: Drawable) = tv.invalidate()
            override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
                tv.postDelayed(what, `when` - SystemClock.uptimeMillis())
            }
            override fun unscheduleDrawable(who: Drawable, what: Runnable) {
                tv.removeCallbacks(what)
            }
            override fun onViewAttachedToWindow(v: View) = drawables.forEach { it.start() }
            override fun onViewDetachedFromWindow(v: View) = drawables.forEach { it.stop() }
        }
        drawables.forEach { it.callback = driver }
        tv.addOnAttachStateChangeListener(driver)
        if (tv.isAttachedToWindow) drawables.forEach { it.start() }
    }
}
