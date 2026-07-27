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
     * SHA-256 сертификата подписи автора (нижний регистр, без разделителей).
     *
     * Сверено 27.07.2026 по двум независимым источникам: keystore `signing/forpda-parallel.jks`
     * и опубликованный релиз `ProPDA-3.3.2.apk` с GitHub. Отпечатки совпали — значит debug и
     * release подписываются одним ключом.
     *
     * ⚠️ Сменишь keystore — обнови значение, иначе Pro отключится у ВСЕХ покупателей. Отпечаток
     * новой сборки: `apksigner verify --print-certs <файл>.apk | grep "SHA-256 digest"`.
     */
    private const val TRUSTED_CERT_SHA256 =
            "0d73c87a7ab09f4dfbe9ebfc03b0967e92fab2f56b752ceda30ff873b748c109"

    /**
     * true, если APK подписан ключом автора.
     *
     * В debug-сборках проверка пропускается: проект собирается и без keystore (тогда Gradle
     * подписывает отладочным ключом), и разработчик не должен из-за этого терять Pro.
     * Пиратство касается распространяемых release-сборок — там проверка работает.
     */
    fun isTrusted(context: Context): Boolean {
        if (BuildConfig.DEBUG) return true
        if (TRUSTED_CERT_SHA256.isBlank()) return true // отпечаток не задан — не мешаем работе
        val actual = currentCertSha256(context) ?: return true
        val ok = actual == TRUSTED_CERT_SHA256.lowercase(Locale.ROOT)
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
