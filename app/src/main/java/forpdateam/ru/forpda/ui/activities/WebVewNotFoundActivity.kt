package forpdateam.ru.forpda.ui.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView

import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.LocaleHelper
import forpdateam.ru.forpda.databinding.ActivityWvNotFoundBinding
import forpdateam.ru.forpda.ui.EdgeToEdge
import forpdateam.ru.forpda.common.PermissionHelper
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Created by radiationx on 23.07.17.
 */

@AndroidEntryPoint
class WebVewNotFoundActivity : AppCompatActivity() {
    @Inject lateinit var permissionHelper: PermissionHelper

    private var _binding: ActivityWvNotFoundBinding? = null
    private val binding: ActivityWvNotFoundBinding get() = requireNotNull(_binding)

    private val nougatMsg = """Убедитесь, что сервис WebView установлен и активирован:
1. Включите режим разработчика на вашем Android-устройстве.

2.Зайдите в раздел «Для разработчиков» и нажмите по пункту «Сервис WebView».

3.Возможно, вы увидите там возможность выбрать между Chrome Stable и Android System WebView (или Google WebView, что одно и то же)."""

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityWvNotFoundBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.apply(this, findViewById(android.R.id.content), padTop = true, padBottom = false)
        val getInGp = binding.getInGp
        val getIn4pda = binding.getIn4pda
        val tryStart = binding.wvTryStart
        val nougatPlus = binding.nougatplus

        nougatPlus.visibility = View.VISIBLE
        nougatPlus.text = nougatMsg


        getInGp.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.webview")).addFlags(FLAG_ACTIVITY_NEW_TASK)
            startActivity(Intent.createChooser(intent, "Открыть в").addFlags(FLAG_ACTIVITY_NEW_TASK))
        }

        getIn4pda.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://4pda.to/forum/index.php?showtopic=705513")).addFlags(FLAG_ACTIVITY_NEW_TASK)
            startActivity(Intent.createChooser(intent, "Открыть в").addFlags(FLAG_ACTIVITY_NEW_TASK))
        }

        tryStart.setOnClickListener {
            val intent = Intent(applicationContext, MainActivity::class.java)
                    .putExtra(MainActivity.ARG_CHECK_WEBVIEW, false)
            startActivity(intent)
            finish()
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
