package forpdateam.ru.forpda.common

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Spannable
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.util.LruCache

/**
 * Style a [Spannable] with a custom [Typeface].
 */
class AssetsTypefaceSpan(context: Context?, typefaceName: String) : MetricAffectingSpan() {

    companion object {
        private val sTypefaceCache = LruCache<String, Typeface>(12)
    }

    // Nullable + fail-safe: a null context or a missing/broken font asset must NOT throw. Previously
    // Typeface.createFromAsset(null.assets, …) raised an NPE that bubbled out of Html.fromHtml and made the
    // WHOLE post fall back to raw HTML (visible tags). Now the span is simply a no-op if the font can't load.
    private val mTypeface: Typeface? = sTypefaceCache.get(typefaceName) ?: run {
        val assets = context?.assets ?: return@run null
        runCatching { Typeface.createFromAsset(assets, "fonts/$typefaceName") }
                .getOrNull()
                ?.also { sTypefaceCache.put(typefaceName, it) }
    }

    override fun updateMeasureState(p: TextPaint) {
        mTypeface?.let { p.typeface = it; p.flags = p.flags or Paint.SUBPIXEL_TEXT_FLAG }
    }

    override fun updateDrawState(tp: TextPaint) {
        mTypeface?.let { tp.typeface = it; tp.flags = tp.flags or Paint.SUBPIXEL_TEXT_FLAG }
    }
}
