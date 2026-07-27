package forpdateam.ru.forpda.pro

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import forpdateam.ru.forpda.BuildConfig
import timber.log.Timber
import java.security.MessageDigest
import java.util.Locale

/**
 * Проверка того, что APK подписан ключом автора.
 *
 * Смысл: пропатчить проверку лицензии можно, но пересобранный APK придётся подписать ЧУЖИМ
 * ключом — исходный приватный ключ подписи есть только у автора. Значит взломанная сборка
 * отличима от настоящей, и одного «поправить if» уже недостаточно.
 *
 * Это НЕ непробиваемая защита: сам этот класс тоже можно вырезать. Задача скромнее — поднять
 * цену взлома с «пары минут» до «надо разобраться», см. комментарий в [ProLicense].
 *
 * Поведение специально сделано отказоустойчивым: если ожидаемый отпечаток не настроен,
 * проверка ПРОПУСКАЕТСЯ, а не запрещает Pro. Иначе забытая настройка молча лишила бы
 * оплативших пользователей функции — это хуже, чем неактивная защита от пиратства.
 */
object AppIntegrity {

    /**
     * SHA-256 сертификата подписи РЕЛИЗНЫХ сборок (нижний регистр, без разделителей).
     *
     * ⚠️ ЗАПОЛНИТЬ ПЕРЕД ПУБЛИКАЦИЕЙ. Получить командой:
     *   apksigner verify --print-certs ProPDA-release.apk | grep "SHA-256 digest"
     *
     * Пока здесь пусто, проверка подписи не работает (Pro при этом функционирует нормально).
     */
    private const val RELEASE_CERT_SHA256 = ""

    /** Отладочный ключ этой машины: чтобы локальные сборки не спотыкались о проверку. */
    private const val DEBUG_CERT_SHA256 =
            "0d73c87a7ab09f4dfbe9ebfc03b0967e92fab2f56b752ceda30ff873b748c109"

    /** true, если подпись своя ЛИБО проверка ещё не настроена. */
    fun isTrusted(context: Context): Boolean {
        val expected = buildList {
            if (RELEASE_CERT_SHA256.isNotBlank()) add(RELEASE_CERT_SHA256.lowercase(Locale.ROOT))
            if (BuildConfig.DEBUG) add(DEBUG_CERT_SHA256)
        }
        if (expected.isEmpty()) return true // отпечаток не задан — не мешаем работе
        val actual = currentCertSha256(context) ?: return true
        val ok = actual in expected
        if (!ok) Timber.w("app signature mismatch: %s", actual.take(12))
        return ok
    }

    private fun currentCertSha256(context: Context): String? = runCatching {
        val pm = context.packageManager
        val bytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull()?.toByteArray()
        } ?: return@runCatching null
        MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
