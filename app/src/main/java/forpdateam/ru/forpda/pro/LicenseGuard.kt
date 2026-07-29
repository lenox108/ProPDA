package forpdateam.ru.forpda.pro

import android.content.Context
import android.util.Base64
import androidx.preference.PreferenceManager
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Вторая, НЕЗАВИСИМАЯ проверка активации — намеренный дубль [ProLicense].
 *
 * Зачем дубль, а не вызов `ProLicense.isUnlocked()`: если все платные функции спрашивают
 * разрешение у одного метода, взломщику достаточно заставить этот метод вернуть true — и открыто
 * всё сразу. Здесь подпись проверяется своим кодом, со своими константами и своим порядком
 * действий, поэтому патч одной точки функцию не открывает: сетевой слой спросит другую.
 *
 * Используется в глубине — там, где решается маршрут запроса ([forpdateam.ru.forpda.client.Client],
 * [forpdateam.ru.forpda.client.proxy.ProxySettings]), а не на экране настроек: чем дальше проверка
 * от кнопки, тем меньше шансов, что её заметят рядом с UI платной функции.
 *
 * Результат кэшируется на [CACHE_MS]: проверка подписи стоит около миллисекунды, а спрашивают её
 * на каждый запрос. Кэш короткий, чтобы снятая активация переставала действовать почти сразу.
 */
object LicenseGuard {

    /**
     * Тот же публичный ключ, что и в [ProLicense], но своей копией — общей константы нет намеренно.
     * Как и там, в XOR-виде: строкой в APK не найти.
     */
    private val publicKeyBytes: ByteArray
        get() = Base64.decode(decode(
                    0x17, 0x1c, 0x31, 0x2d, 0x1f, 0x2d, 0x03, 0x12, 0x11, 0x35, 0x00, 0x13,
                    0x20, 0x30, 0x6a, 0x19, 0x1b, 0x0b, 0x03, 0x13, 0x11, 0x35, 0x00, 0x13,
                    0x20, 0x30, 0x6a, 0x1e, 0x1b, 0x0b, 0x39, 0x1e, 0x0b, 0x3d, 0x1b, 0x1f,
                    0x36, 0x3d, 0x20, 0x6b, 0x03, 0x19, 0x0e, 0x03, 0x38, 0x6c, 0x0f, 0x0f,
                    0x6e, 0x3f, 0x1d, 0x6b, 0x20, 0x34, 0x0b, 0x37, 0x22, 0x08, 0x14, 0x17,
                    0x1e, 0x6a, 0x09, 0x6d, 0x32, 0x3e, 0x17, 0x68, 0x1b, 0x31, 0x20, 0x0f,
                    0x0d, 0x02, 0x62, 0x09, 0x30, 0x1e, 0x31, 0x3e, 0x18, 0x09, 0x02, 0x18,
                    0x3b, 0x00, 0x20, 0x17, 0x1f, 0x68, 0x38, 0x0d, 0x0a, 0x6c, 0x16, 0x1c,
                    0x2a, 0x39, 0x10, 0x29, 0x75, 0x35, 0x11, 0x0d, 0x6f, 0x09, 0x3d, 0x0d,
                    0x19, 0x31, 0x38, 0x15, 0x6f, 0x3c, 0x3c, 0x0b, 0x1b, 0x3f, 0x0e, 0x63,
                    0x3e, 0x0b, 0x67, 0x67,
        ), Base64.DEFAULT)

    /** Ключи настроек и префикс подписи — в XOR-виде: строками их в APK не найти. */
    private val licenseKeyName: String get() = decode(0x2a, 0x28, 0x35, 0x74, 0x36, 0x33, 0x39,
            0x3f, 0x34, 0x29, 0x3f, 0x05, 0x31, 0x3f, 0x23)

    private val memberIdKeyName: String get() = decode(0x37, 0x3f, 0x37, 0x38, 0x3f, 0x28, 0x05, 0x33, 0x3e)

    private val messagePrefix: String get() = decode(0x2a, 0x28, 0x35, 0x2a, 0x3e, 0x3b, 0x77,
            0x2a, 0x28, 0x35, 0x60, 0x2c, 0x6b, 0x60)

    private const val CACHE_MS = 60_000L

    @Volatile
    private var cachedAt = 0L

    @Volatile
    private var cached = false

    /** @return true, если для текущего аккаунта есть действующий ключ активации. */
    fun allowed(context: Context): Boolean {
        val now = System.currentTimeMillis()
        // Часы могли уехать назад — тогда просто перепроверяем, а не верим протухшему кэшу вечно.
        val age = now - cachedAt
        if (age in 0 until CACHE_MS) return cached
        val result = check(context.applicationContext)
        cached = result
        cachedAt = now
        return result
    }

    /** Сбрасывает кэш — вызывается после ввода ключа, чтобы функция открылась сразу. */
    fun invalidate() {
        cachedAt = 0L
    }

    private fun check(context: Context): Boolean = runCatching {
        if (!AppIntegrity.isTrusted(context)) return false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val license = prefs.getString(licenseKeyName, null)?.trim().orEmpty()
        if (license.isEmpty()) return false
        val memberId = prefs.getString(memberIdKeyName, null)?.toIntOrNull() ?: return false
        if (memberId == 0) return false
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKeyBytes))
        val signed = (messagePrefix + memberId).toByteArray(Charsets.UTF_8)
        val signature = Base64.decode(license, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(signed)
            verify(signature)
        }
    }.getOrDefault(false)

    private fun decode(vararg bytes: Int): String =
            bytes.map { (it xor 0x5A).toChar() }.joinToString("")
}
