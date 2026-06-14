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

    private val mTypeface: Typeface = sTypefaceCache.get(typefaceName) ?: run {
        Typeface.createFromAsset(context?.assets, "fonts/$typefaceName").also {
            sTypefaceCache.put(typefaceName, it)
        }
    }

    override fun updateMeasureState(p: TextPaint) {
        p.typeface = mTypeface
        p.flags = p.flags or Paint.SUBPIXEL_TEXT_FLAG
    }

    override fun updateDrawState(tp: TextPaint) {
        tp.typeface = mTypeface
        tp.flags = tp.flags or Paint.SUBPIXEL_TEXT_FLAG
    }
}
