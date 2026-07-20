package forpdateam.ru.forpda.appupdates

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.showSnackbarAboveSystemBars
import forpdateam.ru.forpda.ui.activities.MainActivity
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Единый сценарий ручной проверки обновлений — вызывается и из настроек, и из «Быстрых настроек»
 * меню, чтобы поведение (снекбар «Проверяем…», диалог с «Скачать»/«Открыть тему», сообщения об
 * ошибках) было идентичным и не расходилось между двумя точками входа.
 *
 * Диалог используется вместо Snackbar с кнопкой-действием намеренно: на Android 14+/16 инфляция
 * Snackbar$SnackbarLayout падает (colorOnSurface не разрешается в теме, см. SnackbarHelper) и показ
 * деградирует до Toast — а у Toast нет кнопки-действия, поэтому «Скачать» пропадала бы. У диалога
 * (materialAlertDialogTheme → ThemeOverlay) этой проблемы нет.
 */
fun Fragment.runManualAppUpdateCheck(repository: AppUpdateRepository) {
    showSnackbarAboveSystemBars(R.string.app_update_checking)
    Timber.tag(AppUpdateRepository.LOG_TAG).i("manual UI start")
    viewLifecycleOwner.lifecycleScope.launch {
        runCatching { repository.check(manual = true) }
            .onSuccess { result ->
                if (!isAdded) return@onSuccess
                when (result) {
                    is AppUpdateRepository.CheckResult.UpdateAvailable ->
                        showAppUpdateAvailableDialog(repository, result)
                    is AppUpdateRepository.CheckResult.UpToDate ->
                        showSnackbarAboveSystemBars(
                            getString(R.string.app_update_up_to_date, BuildConfig.VERSION_NAME)
                        )
                }
            }
            .onFailure { error ->
                Timber.tag(AppUpdateRepository.LOG_TAG).w(error, "manual UI failed")
                if (isAdded) showSnackbarAboveSystemBars(appUpdateCheckErrorMessage(error))
            }
    }
}

private fun Fragment.showAppUpdateAvailableDialog(
    repository: AppUpdateRepository,
    result: AppUpdateRepository.CheckResult.UpdateAvailable
) {
    if (!isAdded) return
    val preferred = repository.pickPreferredDownload(result.downloads)
    val title = getString(R.string.app_update_available, result.version.toString())
    val notes = result.description?.takeIf { it.isNotBlank() }

    Timber.tag(AppUpdateRepository.LOG_TAG).i(
        "manual UI dialog shown version=%s downloads=%d preferredUrl=%s",
        result.version, result.downloads.size, preferred?.url
    )

    runCatching {
        val builder = MaterialAlertDialogBuilder(requireActivity()).setTitle(title)
        if (notes != null) builder.setMessage(notes)

        if (preferred != null) {
            // Есть прямая ссылка на APK — основное действие «Скачать», запасное «Открыть тему».
            builder.setPositiveButton(R.string.app_update_action_download) { _, _ ->
                startApkDownload(preferred)
            }
            builder.setNeutralButton(R.string.app_update_action_open_topic) { _, _ ->
                openAppUpdateTopicUrl(result.topicUrl)
            }
        } else {
            // Прямой ссылки нет (версия отдана из кэша) — единственное действие «Открыть тему».
            builder.setPositiveButton(R.string.app_update_action_open_topic) { _, _ ->
                openAppUpdateTopicUrl(result.topicUrl)
            }
        }
        builder.setNegativeButton(R.string.close, null)
        builder.showWithStyledButtons()
    }.onFailure { e ->
        // Даже диалог не построился (крайне маловероятно) — не роняем экран, показываем текст.
        Timber.tag(AppUpdateRepository.LOG_TAG).w(e, "update-available dialog failed; toast fallback")
        runCatching { Toast.makeText(requireContext(), title, Toast.LENGTH_LONG).show() }
    }
}

private fun Fragment.startApkDownload(link: DownloadLink) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { startActivity(intent) }
        .onFailure { showSnackbarAboveSystemBars(getString(R.string.app_update_check_failed_unknown)) }
}

private fun Fragment.openAppUpdateTopicUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        setClass(requireContext(), MainActivity::class.java)
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    startActivity(intent)
}

private fun Fragment.appUpdateCheckErrorMessage(error: Throwable): String {
    val reason = (error as? AppUpdateRepository.CheckException)?.reason
    val reasonText = when (reason) {
        AppUpdateRepository.FailureReason.Network -> getString(R.string.app_update_check_failed_network)
        AppUpdateRepository.FailureReason.RateLimited -> getString(R.string.app_update_check_failed_rate_limited)
        AppUpdateRepository.FailureReason.Forbidden,
        AppUpdateRepository.FailureReason.Captcha -> getString(R.string.app_update_check_failed_forbidden)
        AppUpdateRepository.FailureReason.NotFound -> getString(R.string.app_update_check_failed_not_found)
        AppUpdateRepository.FailureReason.Parse -> getString(R.string.app_update_check_failed_parse)
        AppUpdateRepository.FailureReason.Server -> getString(R.string.app_update_check_failed_server)
        else -> error.message?.takeIf { it.isNotBlank() }?.take(80)
            ?: getString(R.string.app_update_check_failed_unknown)
    }
    return getString(R.string.app_update_check_failed_with_reason, reasonText)
}
