package forpdateam.ru.forpda.common.appicon

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.appupdates.GithubReleaseSource
import forpdateam.ru.forpda.appupdates.SemanticVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * «Иконка уведомлений» — цветной значок слева в карточке уведомления.
 *
 * SystemUI (Android 14+, а с редизайном Android 16 — особенно заметно) рисует
 * его из `<application android:icon>` установленного пакета и НИКАК не даёт
 * приложению переопределить его на лету: ни `setLargeIcon` (уходит вправо),
 * ни MessagingStyle-аватар без conversation-shortcut, ни launcher-псевдонимы
 * на него не влияют (проверено на эмуляторе API 36.1 — см. память
 * notification-shade-circle-icon-research).
 *
 * Поэтому смена значка = переустановка варианта APK, отличающегося только
 * иконкой манифеста. Варианты генерирует `scripts/make_circle_variants.py` из
 * базового APK релиза и выкладывает ассетами рядом с ним:
 * `ProPDA-<версия>-circle-<id>.apk`.
 *
 * Важное для UX: SystemUI кэширует иконку пакета до перезагрузки — после
 * установки варианта значок меняется только после ребута устройства
 * (проверено там же). Ярлык на рабочем столе настройка не трогает.
 */
@Singleton
class CircleIcon @Inject constructor(
        private val githubSource: GithubReleaseSource,
) {

    /**
     * Найденный ассет варианта.
     *
     * @param isUpgrade вариант лежит в релизе НОВЕЕ установленного, то есть его
     *   установка попутно обновит приложение — пользователя об этом надо
     *   спросить, иначе смена значка молча принесёт новую версию.
     */
    data class Resolved(
            val url: String,
            val fileName: String,
            val version: String,
            val isUpgrade: Boolean,
    )

    /**
     * Ищет вариант сначала в релизе установленной версии, затем — в более свежих
     * релизах, от самого нового. Вариант из релиза СТАРШЕ установленного не
     * подходит в принципе: Android отказывает в установке APK с меньшим
     * versionCode, поэтому такие даже не проверяются.
     *
     * Это позволяет не выкладывать варианты в каждый релиз: если в свежем их
     * забыли, работает вариант из предыдущего, а если наоборот (варианты есть
     * только в новом) — смена значка предложит обновиться.
     *
     * @throws AssetMissingException если варианта нет ни в одном подходящем релизе.
     */
    suspend fun resolve(context: Context, variantId: String): Resolved = withContext(Dispatchers.IO) {
        val installed = BuildConfig.VERSION_NAME
        // Локальный сервер E2E-теста отдаёт ассеты одной версии, релизов там нет.
        debugBaseUrl(context)?.let { base ->
            val name = assetName(variantId, installed)
            return@withContext Resolved(base + name, name, installed, isUpgrade = false)
        }

        val own = releaseAsset(variantId, installed, isUpgrade = false)
        if (githubSource.assetExists(own.url)) return@withContext own

        val installedVersion = SemanticVersion.parse(installed)
        val newer = githubSource.fetchReleaseTags()
                .mapNotNull { tag -> SemanticVersion.parse(tag)?.let { tag to it } }
                .filter { (_, version) -> installedVersion == null || version > installedVersion }
                .sortedByDescending { (_, version) -> version }
        for ((tag, version) in newer) {
            val candidate = releaseAsset(variantId, version.toString(), isUpgrade = true, tag = tag)
            if (githubSource.assetExists(candidate.url)) {
                Timber.i("Вариант %s найден в релизе %s (обновление с %s)", variantId, tag, installed)
                return@withContext candidate
            }
        }
        throw AssetMissingException()
    }

    /**
     * Скачивает найденный вариант в кэш. Прогресс — доля 0..1 либо -1, пока
     * размер неизвестен.
     */
    suspend fun download(
            context: Context,
            resolved: Resolved,
            onProgress: (Float) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        // Старые скачивания не нужны: держим в кэше максимум один APK.
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, resolved.fileName)
        Timber.i("Скачивание варианта иконки: %s", resolved.url)
        client.newCall(Request.Builder().url(resolved.url).build()).execute().use { response ->
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

    private fun releaseAsset(
            variantId: String,
            versionName: String,
            isUpgrade: Boolean,
            tag: String = "v$versionName",
    ): Resolved {
        val name = assetName(variantId, versionName)
        return Resolved(
                url = "https://github.com/${GithubReleaseSource.OWNER}/${GithubReleaseSource.REPO}" +
                        "/releases/download/$tag/$name",
                fileName = name,
                version = versionName,
                isUpgrade = isUpgrade,
        )
    }

    private fun debugBaseUrl(context: Context): String? {
        if (!BuildConfig.DEBUG) return null
        val override = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(DEBUG_BASE_URL_PREF, null)
        return override?.takeIf { it.isNotBlank() }?.trimEnd('/')?.plus("/")
    }

    companion object {

        /**
         * Вариант, вшитый в манифест базовой сборки (`ProPDA-<версия>.apk`).
         * Держать синхронно с `<application android:icon>` в AndroidManifest.xml.
         */
        const val BAKED_ID = "pixel_4"

        /** Debug-переопределение базового URL ассетов (пишет debuglab-ресивер). */
        const val DEBUG_BASE_URL_PREF = "debug.circle_asset_base"

        private const val CACHE_DIR = "circle_apk"

        private val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()
        }

        /**
         * Текущий вариант — по фактической иконке установленного манифеста, а не
         * по сохранённой настройке: истина всегда в пакете (пользователь мог
         * прервать установку, поставить APK вручную и т.п.).
         */
        fun currentVariant(context: Context): AppIconVariant =
                AppIcons.variants.firstOrNull { it.iconRes == context.applicationInfo.icon }
                        ?: AppIcons.byId(BAKED_ID)

        /** Имя релизного ассета для варианта: у вшитого — сам базовый APK. */
        fun assetName(variantId: String, versionName: String = BuildConfig.VERSION_NAME): String =
                if (variantId == BAKED_ID) "ProPDA-$versionName.apk"
                else "ProPDA-$versionName-circle-$variantId.apk"

        /**
         * Открывает системный установщик поверх текущего приложения. Разрешение
         * «Установка из этого источника» установщик запрашивает сам (API 26+
         * ведёт в настройки и возвращается к установке), поэтому предварительная
         * проверка `canRequestPackageInstalls` не нужна.
         */
        fun install(context: Context, apk: File) {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", apk)
            context.startActivity(Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Ошибка «варианта нет ни в одном подходящем релизе». */
    class AssetMissingException : Exception()
}
