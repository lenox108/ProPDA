package forpdateam.ru.forpda.common.appicon

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.appupdates.GithubReleaseSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * «Кружок уведомлений в шторке»: цветной значок слева в карточке уведомления.
 *
 * SystemUI (Android 14+, а с редизайном Android 16 — особенно заметно) рисует
 * его из `<application android:icon>` установленного пакета и НИКАК не даёт
 * приложению переопределить его на лету: ни `setLargeIcon` (уходит вправо),
 * ни MessagingStyle-аватар без conversation-shortcut, ни launcher-псевдонимы
 * на него не влияют (проверено на эмуляторе API 36.1 — см. память
 * notification-shade-circle-icon-research).
 *
 * Поэтому смена кружка = переустановка варианта APK, отличающегося только
 * иконкой манифеста. Варианты генерирует `scripts/make_circle_variants.py` из
 * базового APK релиза и выкладываются ассетами рядом с ним:
 * `ProPDA-<версия>-circle-<id>.apk`. Здесь — определение текущего варианта,
 * адрес ассета и скачивание с запуском системной установки.
 *
 * Важное для UX: SystemUI кэширует иконку пакета до перезагрузки — после
 * установки варианта кружок меняется только после ребута устройства
 * (проверено там же). Ярлык на рабочем столе настройка не трогает.
 */
object CircleIcon {

    /**
     * Вариант, вшитый в манифест базовой сборки (`ProPDA-<версия>.apk`).
     * Держать синхронно с `<application android:icon>` в AndroidManifest.xml.
     */
    const val BAKED_ID = "pixel_4"

    /** Debug-переопределение базового URL ассетов (пишет debuglab-ресивер). */
    const val DEBUG_BASE_URL_PREF = "debug.circle_asset_base"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
    }

    /**
     * Текущий вариант кружка — по фактической иконке установленного манифеста,
     * а не по сохранённой настройке: истина всегда в пакете (пользователь мог
     * прервать установку, поставить APK вручную и т.п.).
     */
    fun currentVariant(context: Context): AppIconVariant =
            AppIcons.variants.firstOrNull { it.iconRes == context.applicationInfo.icon }
                    ?: AppIcons.byId(BAKED_ID)

    /** Имя релизного ассета для варианта кружка. */
    fun assetName(variantId: String, versionName: String = BuildConfig.VERSION_NAME): String =
            if (variantId == BAKED_ID) "ProPDA-$versionName.apk"
            else "ProPDA-$versionName-circle-$variantId.apk"

    /**
     * Прямой URL ассета ТЕКУЩЕЙ версии приложения: вариант обязан совпадать по
     * versionCode с установленным, иначе это уже обновление, а не смена кружка.
     */
    fun assetUrl(context: Context, variantId: String): String =
            baseUrl(context) + assetName(variantId)

    private fun baseUrl(context: Context): String {
        if (BuildConfig.DEBUG) {
            val override = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(DEBUG_BASE_URL_PREF, null)
            if (!override.isNullOrBlank()) return override.trimEnd('/') + "/"
        }
        return "https://github.com/${GithubReleaseSource.OWNER}/${GithubReleaseSource.REPO}" +
                "/releases/download/v${BuildConfig.VERSION_NAME}/"
    }

    /** Ошибка «ассета нет в релизе» — показывается отдельным, понятным текстом. */
    class AssetMissingException : Exception()

    /**
     * Скачивает вариант в кэш. Прогресс — доля 0..1 либо -1, пока размер неизвестен.
     * @throws AssetMissingException если релиз не содержит такого ассета (404).
     */
    suspend fun download(
            context: Context,
            variantId: String,
            onProgress: (Float) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val url = assetUrl(context, variantId)
        val dir = File(context.cacheDir, "circle_apk").apply { mkdirs() }
        // Старые скачивания не нужны: держим в кэше максимум один APK.
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, assetName(variantId))
        Timber.i("Скачивание варианта кружка: %s", url)
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.code == 404) throw AssetMissingException()
            if (!response.isSuccessful) throw java.io.IOException("HTTP ${response.code}")
            val body = response.body ?: throw java.io.IOException("пустой ответ")
            val total = body.contentLength()
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        onProgress(if (total > 0) done.toFloat() / total else -1f)
                    }
                }
            }
        }
        target
    }

    /**
     * Открывает системный установщик поверх текущего приложения. Разрешение
     * «Установка из этого источника» установщик запрашивает сам (API 26+ ведёт
     * в настройки и возвращается к установке) — предварительная проверка
     * `canRequestPackageInstalls` не нужна.
     */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
