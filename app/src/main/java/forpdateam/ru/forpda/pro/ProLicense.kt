package forpdateam.ru.forpda.pro

import android.content.Context
import android.util.Base64
import androidx.preference.PreferenceManager
import timber.log.Timber
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Проверка ключа активации ProPDA Pro (разовая покупка).
 *
 * Схема: ECDSA P-256. В приложении лежит ТОЛЬКО публичный ключ, поэтому выпустить себе
 * активацию, разобрав APK, невозможно — для этого нужен приватный ключ автора.
 *
 * Ключ привязан к `member_id` аккаунта 4PDA: передать его другому человеку бессмысленно,
 * у него другой id. Проверка полностью офлайн — ни сервера, ни сети не требуется.
 *
 * Границы честности: любая клиентская проверка в принципе обходится патчем APK. Эта схема
 * закрывает обычное «скинул другу ключ / скачал чужой APK», но не защищает от взломанной сборки.
 *
 * Ключи выпускаются утилитой `tools/prokey/ProKeyGen.java`.
 */
object ProLicense {

    /** Публичный ключ (X.509). Приватная половина хранится только у автора, вне репозитория. */
    private const val PUBLIC_KEY_B64 =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAElgz1YCTYb6UU4eG1znQmxRNMD0S7hdM2AkzUWX8Sj" +
                    "DkdBSXBaZzME2bWP6LFpcJs/oKW5SgWCkbO5ffQAeT9dQ=="

    /**
     * Подписываемая строка ("propda-pro:v1:", должна совпадать с ProKeyGen.MESSAGE_PREFIX).
     *
     * Хранится в XOR-виде намеренно: в открытом виде она была бы заметна обычным поиском по
     * строкам в APK и приводила бы взломщика прямо к проверке лицензии. Это не шифрование —
     * лишь снятие очевидной подсказки.
     */
    private val MESSAGE_PREFIX: String
        get() = byteArrayOf(0x2a, 0x28, 0x35, 0x2a, 0x3e, 0x3b, 0x77,
                0x2a, 0x28, 0x35, 0x60, 0x2c, 0x6b, 0x60)
                .map { (it.toInt() xor 0x5A).toChar() }
                .joinToString("")

    const val KEY_LICENSE = "pro.license_key"
    private const val KEY_MEMBER_ID = "member_id"

    /**
     * Активирован ли Pro для текущего аккаунта.
     *
     * Проверка намеренно повторяется в нескольких независимых местах (здесь, в PushRegistrar
     * и в FcmMessagingReceiver): патч одной точки не открывает функцию целиком.
     */
    fun isUnlocked(context: Context): Boolean {
        if (!AppIntegrity.isTrusted(context)) return false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val license = prefs.getString(KEY_LICENSE, null)?.trim().orEmpty()
        if (license.isEmpty()) return false
        val memberId = prefs.getString(KEY_MEMBER_ID, null)?.toIntOrNull() ?: return false
        return verify(memberId, license)
    }

    /** Текущий id аккаунта 4PDA — его покупатель сообщает автору для выпуска ключа. */
    fun currentMemberId(context: Context): Int? =
            PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(KEY_MEMBER_ID, null)?.toIntOrNull()?.takeIf { it != 0 }

    /**
     * Проверяет подпись ключа для конкретного аккаунта.
     * Любая ошибка разбора трактуется как «ключ неверен» — подделать её нельзя.
     */
    fun verify(memberId: Int, license: String): Boolean = runCatching {
        val keyBytes = Base64.decode(PUBLIC_KEY_B64, Base64.DEFAULT)
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(keyBytes))
        val signature = Base64.decode(license.trim(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update((MESSAGE_PREFIX + memberId).toByteArray(Charsets.UTF_8))
            verify(signature)
        }
    }.getOrElse {
        Timber.d("pro license rejected: %s", it.message)
        false
    }

    /** Сохраняет ключ, если он подходит текущему аккаунту. Возвращает результат проверки. */
    fun activate(context: Context, license: String): Result {
        val memberId = currentMemberId(context) ?: return Result.NotLoggedIn
        if (!verify(memberId, license)) return Result.Invalid
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putString(KEY_LICENSE, license.trim()).apply()
        return Result.Activated
    }

    fun deactivate(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit().remove(KEY_LICENSE).apply()
    }

    enum class Result { Activated, Invalid, NotLoggedIn }
}
