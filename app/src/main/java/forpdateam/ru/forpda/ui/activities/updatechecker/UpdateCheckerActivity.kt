package forpdateam.ru.forpda.ui.activities.updatechecker

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.databinding.ActivityUpdaterBinding
import forpdateam.ru.forpda.entity.remote.checker.UpdateData
import forpdateam.ru.forpda.model.data.remote.api.ApiUtils
import forpdateam.ru.forpda.presentation.checker.CheckerViewModel
import forpdateam.ru.forpda.ui.EdgeToEdge
import forpdateam.ru.forpda.ui.activities.MainActivity
import kotlinx.coroutines.launch

class UpdateCheckerActivity : AppCompatActivity() {

    companion object {
        const val ARG_FORCE = "force"
    }

    private lateinit var binding: ActivityUpdaterBinding
    private val systemLinkHandler = App.get().Di().systemLinkHandler

    private val viewModel: CheckerViewModel by viewModels {
        CheckerViewModel.Factory(
                App.get().Di().checkerRepository,
                App.get().Di().errorHandler
        )
    }

    private var pendingDownloadUrl: String? = null

    private val storagePermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
    ) { granted ->
        val url = pendingDownloadUrl
        pendingDownloadUrl = null
        if (granted && url != null) {
            systemLinkHandler.handleDownload(url)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUpdaterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.apply(this, binding.root, padTop = true, padBottom = false)
        MainActivity.setLightStatusBar(this, false)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setNavigationIcon(R.drawable.ic_toolbar_arrow_back)

        binding.currentInfo.text = generateCurrentInfo(BuildConfig.VERSION_NAME, BuildConfig.BUILD_DATE)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    applyLoading(state.loading)
                    state.update?.let { showUpdateData(it) }
                }
            }
        }

        val force = intent?.getBooleanExtra(ARG_FORCE, false) ?: false
        viewModel.checkUpdate(force)
    }

    private fun applyLoading(loading: Boolean) {
        if (loading) {
            binding.progressBar.visibility = View.VISIBLE
            binding.updateInfo.visibility = View.GONE
            binding.updateContent.visibility = View.GONE
            binding.updateButton.visibility = View.GONE
            binding.divider.visibility = View.GONE
        } else {
            binding.progressBar.visibility = View.GONE
            binding.updateInfo.visibility = View.VISIBLE
            binding.updateContent.visibility = View.VISIBLE
            binding.updateButton.visibility = View.VISIBLE
            binding.divider.visibility = View.VISIBLE
        }
    }

    private fun showUpdateData(update: UpdateData) {
        binding.updateContent.removeAllViews()

        val currentVersionCode = BuildConfig.VERSION_CODE

        if (update.code > currentVersionCode) {
            binding.updateInfo.text = generateCurrentInfo(update.name, update.date)
            addSection("Важно", update.important)
            addSection("Добавлено", update.added)
            addSection("Исправлено", update.fixed)
            addSection("Изменено", update.changed)

            binding.updateInfo.visibility = View.VISIBLE
            binding.updateButton.visibility = View.VISIBLE
            binding.divider.visibility = View.VISIBLE
        } else {
            binding.updateInfo.text = "Нет обновлений, но вы можете загрузить текущую версию еще раз"
            binding.updateInfo.visibility = View.VISIBLE
            binding.updateContent.visibility = View.GONE
            binding.divider.visibility = View.GONE
        }
        binding.updateButton.visibility = View.VISIBLE
        binding.updateButton.setOnClickListener {
            openDownloadDialog(update)
        }
    }

    private fun openDownloadDialog(update: UpdateData) {
        if (update.links.isEmpty()) {
            return
        }
        if (update.links.size == 1) {
            decideDownload(update.links.last())
            return
        }
        val titles = update.links.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
                .setTitle("Источник")
                .setItems(titles) { _, which ->
                    decideDownload(update.links[which])
                }
                .show()
    }

    private fun decideDownload(link: UpdateData.UpdateLink) {
        when (link.type) {
            "file" -> systemDownloadWithPermissionCheck(link.url)
            "site" -> systemLinkHandler.handle(link.url)
            else -> systemLinkHandler.handle(link.url)
        }
    }

    private fun systemDownloadWithPermissionCheck(url: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            systemLinkHandler.handleDownload(url)
            return
        }
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED -> {
                systemLinkHandler.handleDownload(url)
            }
            else -> {
                pendingDownloadUrl = url
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        App.get().onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun addSection(title: String, array: List<String>) {
        if (array.isEmpty()) {
            return
        }
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(0, 0, 0, (resources.displayMetrics.density * 24).toInt())

        val sectionTitle = TextView(this)
        sectionTitle.text = title
        sectionTitle.setPadding(0, 0, 0, (resources.displayMetrics.density * 8).toInt())
        sectionTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        root.addView(sectionTitle)

        val stringBuilder = StringBuilder()

        array.forEachIndexed { index, s ->
            stringBuilder.append("— ").append(s)
            if (index + 1 < array.size) {
                stringBuilder.append("<br>")
            }
        }

        val sectionText = TextView(this)
        sectionText.text = ApiUtils.spannedFromHtml(stringBuilder.toString())
        sectionText.setPadding((resources.displayMetrics.density * 8).toInt(), 0, 0, 0)
        root.addView(sectionText)

        binding.updateContent.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun generateCurrentInfo(name: String?, date: String?): String {
        return String.format("Версия: %s\nСборка от: %s", name, date)
    }
}
