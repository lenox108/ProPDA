package forpdateam.ru.forpda.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * Created by radiationx on 23.08.16.
 */
object BitmapUtils {

    @JvmStatic
    fun createAvatar(bitmap: Bitmap, width: Int, height: Int, isCircle: Boolean): Bitmap {
        val output: Bitmap
        if (isCircle) {
            val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
            output = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint()
            val rect = Rect(0, 0, scaled.width, scaled.height)
            val rectF = RectF(rect)
            paint.isAntiAlias = true
            canvas.drawARGB(0, 0, 0, 0)
            paint.color = Color.RED
            canvas.drawOval(rectF, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(scaled, rect, rect, paint)
        } else {
            output = Bitmap.createScaledBitmap(bitmap, width, height, true)
        }
        return output
    }

    @JvmStatic
    fun centerCrop(src: Bitmap, w: Int, h: Int, scaleFactor: Float): Bitmap {
        var w = w
        var h = h
        val srcWidth = (src.width / scaleFactor).toInt()
        val srcHeight = (src.height / scaleFactor).toInt()
        w = (w / scaleFactor).toInt()
        h = (h / scaleFactor).toInt()

        if (w == srcWidth && h == srcHeight) return src

        val m = Matrix()
        val scale = max(w.toFloat() / srcWidth, h.toFloat() / srcHeight)
        m.setScale(scale, scale)
        val srcCroppedW = round(w / scale).toInt()
        val srcCroppedH = round(h / scale).toInt()
        var srcX = (srcWidth * 0.5f - srcCroppedW / 2).toInt()
        var srcY = (srcHeight * 0.5f - srcCroppedH / 2).toInt()
        srcX = max(min(srcX, srcWidth - srcCroppedW), 0)
        srcY = max(min(srcY, srcHeight - srcCroppedH), 0)

        val overlay = Bitmap.createBitmap(srcCroppedW, srcCroppedH, Bitmap.Config.ARGB_8888)
        overlay.eraseColor(Color.WHITE)
        val canvas = Canvas(overlay)
        canvas.translate(-srcX / scaleFactor, -srcY / scaleFactor)
        canvas.scale(1 / scaleFactor, 1 / scaleFactor)
        canvas.drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        return overlay
    }

    @JvmStatic
    fun rsBlur(context: Context, sentBitmap: Bitmap, radius: Int): Bitmap {
        val overlay = Bitmap.createBitmap(sentBitmap.width, sentBitmap.height, Bitmap.Config.ARGB_8888)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            val rs = android.renderscript.RenderScript.create(context)
            val overlayAlloc = android.renderscript.Allocation.createFromBitmap(rs, sentBitmap)
            val blur = android.renderscript.ScriptIntrinsicBlur.create(rs, overlayAlloc.element)
            blur.setInput(overlayAlloc)
            blur.setRadius(radius.toFloat())
            blur.forEach(overlayAlloc)
            overlayAlloc.copyTo(overlay)
            rs.destroy()
        }
        return overlay
    }

    @JvmStatic
    fun fastBlur(sentBitmap: Bitmap, radius: Int, canReuseInBitmap: Boolean): Bitmap? {
        val bitmap = if (canReuseInBitmap) sentBitmap else sentBitmap.copy(sentBitmap.config ?: Bitmap.Config.ARGB_8888, true)
        if (radius < 1) return null

        val w = bitmap.width
        val h = bitmap.height

        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        val vmin = IntArray(max(w, h))

        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        for (i in dv.indices) { dv[i] = i / divsum }

        var yw = 0; var yi = 0

        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int; var goutsum: Int; var boutsum: Int
        var rinsum: Int; var ginsum: Int; var binsum: Int
        var rsum: Int; var gsum: Int; var bsum: Int
        var p: Int; var yp: Int

        for (y in 0 until h) {
            rinsum = 0; ginsum = 0; binsum = 0; routsum = 0; goutsum = 0; boutsum = 0; rsum = 0; gsum = 0; bsum = 0
            for (i in -radius..radius) {
                p = pix[yi + min(wm, max(i, 0))]
                sir = stack[i + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff
                rbs = r1 - abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2] }
                else { routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2] }
            }
            stackpointer = radius

            for (x in 0 until w) {
                r[yi] = dv[rsum]; g[yi] = dv[gsum]; b[yi] = dv[bsum]
                rsum -= routsum; gsum -= goutsum; bsum -= boutsum
                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]
                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]
                if (y == 0) { vmin[x] = min(x + radius + 1, wm) }
                p = pix[yw + vmin[x]]
                sir[0] = (p and 0xff0000) shr 16; sir[1] = (p and 0x00ff00) shr 8; sir[2] = p and 0x0000ff
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
                rsum += rinsum; gsum += ginsum; bsum += binsum
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]
                yi++
            }
            yw += w
        }

        for (x in 0 until w) {
            rinsum = 0; ginsum = 0; binsum = 0; routsum = 0; goutsum = 0; boutsum = 0; rsum = 0; gsum = 0; bsum = 0
            yp = -radius * w
            for (i in -radius..radius) {
                yi = max(0, yp) + x
                sir = stack[i + radius]
                sir[0] = r[yi]; sir[1] = g[yi]; sir[2] = b[yi]
                rbs = r1 - abs(i)
                rsum += r[yi] * rbs; gsum += g[yi] * rbs; bsum += b[yi] * rbs
                if (i > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2] }
                else { routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2] }
                if (i < hm) { yp += w }
            }
            yi = x
            stackpointer = radius
            for (y in 0 until h) {
                pix[yi] = (0xff000000.toInt() and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                rsum -= routsum; gsum -= goutsum; bsum -= boutsum
                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]
                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]
                if (x == 0) { vmin[y] = min(y + r1, hm) * w }
                p = x + vmin[y]
                sir[0] = r[p]; sir[1] = g[p]; sir[2] = b[p]
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
                rsum += rinsum; gsum += ginsum; bsum += binsum
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]
                yi += w
            }
        }
        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return bitmap
    }

    private fun abs(x: Int): Int = kotlin.math.abs(x)
}
