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
import kotlin.math.roundToInt

/**
 * Renders 4pda smile shortcodes (`:thank_you:`, `:happy:`, …) as native inline images from the
 * bundled `assets/smiles/` gifs. The WebView engine does this via `z_emoticons.js`; the native
 * renderer shows the raw shortcode text without this pass (roadmap `native-topic-renderer.md`,
 * Фаза 2 "смайлы").
 *
 * The shortcode → filename map is parsed once from the same `assets/forpda/scripts/z_emoticons.js`
 * the WebView uses, so the two stay in sync. We handle EVERY code in that map — both the self-delimited
 * `:word:` shortcodes (`:thank_you:`, `:4PDA:`, …) AND the classic ASCII emoticons (`:)`, `:(`, `:D`,
 * `;)`, `:P`, `B)`, `<_<`, `o.O`, …). The ASCII ones are the common case in real posts and previously
 * leaked as literal text ("вместо смайлов — символы"); they are guarded by whitespace/line boundaries
 * exactly as `z_emoticons.js`'s `buildRegexp` guards them, so a `:)` inside a URL/word/code never fires.
 * Smile images are local (no network, no async
 * layout jump); by default they render as a static first frame at a fixed inline size. With the
 * «Анимированные смайлы» pref on (and API 28+) the span carries an [AnimatedImageDrawable] instead —
 * playback is wired to the host TextView via [startAnimations].
 *
 * Two packs ship side by side. The baseline is 4pda's classic ~20px `<name>.gif` set, which covers all
 * 162 files. On top of it, 39 smiles also exist in 4pda's HD set (`s.4pda.to/img/emot_hd/`, sourced as
 * APNG and converted to animated WebP because the platform decodes animated GIF/WebP but not APNG) —
 * those ship as `<name>.webp` and win on API 28+, see [assetFor]. The HD pack matters beyond sharpness:
 * four of its entries animate where the classic gif is a single still — `:D`/`:-D`, `:(`/`:-(`,
 * `:-)`/`:smile:` and `:censored:` — which is what the «смайлы не анимируются» report was about.
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

    /** classic gif filename → HD replacement ("biggrin.gif" → "biggrin.webp"), for the 39 that have one. */
    @Volatile
    private var hdVariants: Map<String, String> = emptyMap()

    /** asset filename → decoded first frame (null = known-undecodable, don't retry). */
    private val bitmapCache = HashMap<String, Bitmap?>()

    /** asset filename → raw bytes for the animated decode path (null = unreadable, don't retry).
     *  Each animated span needs its OWN [AnimatedImageDrawable] (per-span bounds + playback state),
     *  so what's shared is the source bytes, not the drawable. */
    private val bytesCache = HashMap<String, ByteArray?>()

    private fun ensureLoaded(assets: AssetManager) {
        if (codeToFile != null) return
        synchronized(this) {
            if (codeToFile != null) return
            hdVariants = runCatching {
                assets.list(SMILE_ASSET_DIR).orEmpty()
                        .filter { it.endsWith(".webp") }
                        .associateBy { it.removeSuffix(".webp") + ".gif" }
            }.getOrDefault(emptyMap())
            val map = LinkedHashMap<String, String>()
            runCatching {
                val js = assets.open(EMOTICON_SCRIPT).bufferedReader().use { it.readText() }
                // Each map entry is `<key>: ["<file>.gif"…]`. The key is EITHER a quoted string
                // (`":happy:"`, `":)"`, `"<_<"`, `"@}-'-,-"`) OR a bare JS identifier (`o_O:`). The value's
                // first array element is the gif filename. Parse every code (word AND ASCII), not just the
                // lowercase `:word:` subset the old regex caught — that subset silently dropped `:4PDA:`
                // (uppercase) and `o_O` (unquoted) too.
                val entry = Pattern.compile(
                        "(?:\"((?:[^\"\\\\]|\\\\.)*)\"|([A-Za-z_][A-Za-z0-9_]*))" +
                                "\\s*:\\s*\\[\\s*\"([A-Za-z0-9_.\\-]+\\.gif)\"")
                val m = entry.matcher(js)
                while (m.find()) {
                    val code = m.group(1) ?: m.group(2) ?: continue
                    val file = m.group(3) ?: continue
                    map[code] = file
                }
            }
            codeToFile = map
            pattern = buildPattern(map.keys)
        }
    }

    /**
     * Combined alternation over every known code, longest-first (Java alternation is first-match, not
     * longest — so `:-)` must precede `:)`). Mirrors `z_emoticons.js`'s `buildRegexp`: a self-delimited
     * `:word:` code needs no boundary, but an ASCII emoticon (`:)`, `:D`, `B)`, `<_<`, `o.O`, …) is only
     * matched when whitespace/line-bounded so it never fires inside a URL, word, or code snippet. The
     * boundaries are zero-width lookarounds, so a match's [java.util.regex.Matcher.group] is the bare code
     * and its start/end bound exactly the code (no whitespace to trim when placing the span).
     */
    private fun buildPattern(codes: Set<String>): Pattern? {
        if (codes.isEmpty()) return null
        val alternation = codes.sortedByDescending { it.length }.joinToString("|") { code ->
            val quoted = Pattern.quote(code)
            if (code.length > 1 && code.startsWith(":") && code.endsWith(":")) {
                quoted
            } else {
                "(?:^|(?<=\\s))$quoted(?=\\s|\$)"
            }
        }
        return Pattern.compile(alternation)
    }

    /**
     * The asset to actually decode for [file]: the HD WebP when this smile has one and the platform can
     * decode it, otherwise the classic gif. Animated WebP needs [ImageDecoder] (API 28+), and so does the
     * animated path in general, so API 26–27 simply stays on the gif pack it has always used.
     */
    private fun assetFor(file: String): String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) hdVariants[file] ?: file else file

    /**
     * Lays the drawable out on the text line: [sizePx] tall, width following the image's own aspect.
     * The packs are full of non-square smiles (`:clapping:` 40×24, `:feminist:` 105×95), and forcing a
     * square box — as this used to — squashed every one of them.
     */
    private fun Drawable.sizeToLine(sizePx: Int): Drawable = apply {
        val w = if (intrinsicWidth > 0 && intrinsicHeight > 0) {
            (sizePx.toFloat() * intrinsicWidth / intrinsicHeight).roundToInt().coerceAtLeast(1)
        } else {
            sizePx
        }
        setBounds(0, 0, w, sizePx)
    }

    /**
     * Returns [text] with every known smile shortcode replaced by an inline [ImageSpan] [sizePx] tall.
     * If nothing matches (or the map failed to load) the original text is returned.
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
            val asset = assetFor(file)
            // Every step degrades to the next: HD animated → HD still → classic gif still.
            val drawable = (if (animated) animatedDrawableFor(asset, res.assets) else null)
                    ?: drawableFor(asset, res)
                    ?: (if (asset != file) drawableFor(file, res) else null)
                    ?: continue
            drawable.sizeToLine(sizePx)
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
     * Decoding goes through [BitmapFactory] rather than `Drawable.createFromStream`: most of the
     * bundled smiles are animated, and for those the latter hands back an `AnimatedImageDrawable`
     * whose `constantState` is null — uncopyable, so every cache hit produced a null drawable and the
     * shortcode leaked through as literal text.
     */
    private fun drawableFor(file: String, res: Resources): Drawable? {
        val frame = frameFor(file, res.assets) ?: return null
        return BitmapDrawable(res, frame)
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
     * A fresh [AnimatedImageDrawable] over the cached image bytes, or null when the platform can't
     * (API < 28), decoding fails, or the image turns out to be single-frame (ImageDecoder then returns
     * a plain BitmapDrawable) — every null falls back to the static [drawableFor] path in the caller.
     * Single-frame is the normal outcome for 22 of the classic gifs (`:D`, `:(`, `B)`, `:P`, `<_<` …),
     * which is why four of them are served from the HD pack instead — see [assetFor].
     */
    private fun animatedDrawableFor(file: String, assets: AssetManager): Drawable? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val bytes = bytesFor(file, assets) ?: return null
        return runCatching {
            // ВАЖНО: DIRECT-буфер, а НЕ ByteBuffer.wrap(bytes). AnimatedImageDrawable
            // декодирует кадры лениво на hwui-треде «AnimatedImageTh»; для heap-буфера
            // (wrap) нативный ByteArrayStream::read читает Java-byte[] через JNI и на
            // не-Java треде падает «Failed to get JNIEnv» → SIGABRT. Direct-буфер лежит
            // в нативной памяти: читается по адресу без JNIEnv, а native-стрим держит на
            // него global-ref, поэтому он не будет собран GC во время анимации.
            val direct = ByteBuffer.allocateDirect(bytes.size).apply { put(bytes); rewind() }
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(direct))
        }.getOrNull()
                ?.takeIf { it is AnimatedImageDrawable }
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
