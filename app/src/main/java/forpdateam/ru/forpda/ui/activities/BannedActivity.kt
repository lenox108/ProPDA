package forpdateam.ru.forpda.ui.activities

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.LocaleHelper

/**
 * Блокирующая заглушка для забаненного аккаунта. Показывается вместо
 * [MainActivity] при старте (см. проверку в MainActivity.onCreate) и не даёт вернуться
 * в приложение по «Назад». Сетевой рубеж — [forpdateam.ru.forpda.client.interceptors.BlocklistInterceptor].
 *
 * Свёрстана в коде (без биндинга/тем-атрибутов), чтобы не зависеть от палитры и
 * гарантированно отрисоваться в любом состоянии темы.
 */
class BannedActivity : AppCompatActivity() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        val pad = (32 * density).toInt()

        val message = TextView(this).apply {
            text = getString(R.string.blocklist_banned_message)
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(0xFFECECEC.toInt())
            setPadding(pad, pad, pad, pad)
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFF121212.toInt())
            addView(
                message,
                FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER)
            )
        }

        setContentView(root)
    }

    @Suppress("MissingSuperCall", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // Нельзя вернуться в приложение — полностью закрываем задачу.
        finishAffinity()
    }
}
