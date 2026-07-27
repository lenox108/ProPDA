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

    /** Подписываемая строка. Должна совпадать с ProKeyGen.MESSAGE_PREFIX. */
    private const val MESSAGE_PREFIX = "propda-pro:v1:"

    const val KEY_LICENSE = "pro.license_key"
    private const val KEY_MEMBER_ID = "member_id"

    /** Активирован ли Pro для текущего аккаунта. */
    fun isUnlocked(context: Context): Boolean {
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
