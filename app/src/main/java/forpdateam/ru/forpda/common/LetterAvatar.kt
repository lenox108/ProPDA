package forpdateam.ru.forpda.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable

/**
 * Coloured circle + first letter of the nick — WebView-style fallback avatar, so a user with an empty or
 * broken avatar never renders blank. Extracted from the topic adapter so search-result post cards reuse the
 * exact same fallback (and can call [ForPdaCoil.loadAvatar], which cancels the previous request synchronously
 * to avoid the recycled-view «чужой аватар» flash — see [[native-avatar-recycling-wrong-face]]).
 */
fun letterAvatarDrawable(ctx: Context, nick: String?): Drawable {
    val letter = nick?.trim()?.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    val palette = intArrayOf(
            0xFF5C6BC0.toInt(), 0xFF26A69A.toInt(), 0xFFEF5350.toInt(), 0xFF66BB6A.toInt(),
            0xFFAB47BC.toInt(), 0xFFFFA726.toInt(), 0xFF42A5F5.toInt(), 0xFF8D6E63.toInt())
    val bg = palette[((nick?.hashCode() ?: 0) and 0x7FFFFFFF) % palette.size]
    return object : Drawable() {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX(); val cy = b.exactCenterY()
            val r = minOf(b.width(), b.height()) / 2f
            canvas.drawCircle(cx, cy, r, bgPaint)
            textPaint.textSize = r
            val fm = textPaint.fontMetrics
            canvas.drawText(letter, cx, cy - (fm.ascent + fm.descent) / 2f, textPaint)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(cf: ColorFilter?) {}
        @Deprecated("deprecated in Drawable")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
