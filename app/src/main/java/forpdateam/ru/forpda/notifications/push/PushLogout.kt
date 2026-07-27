package forpdateam.ru.forpda.notifications.push

import android.content.Context
import androidx.preference.PreferenceManager
import timber.log.Timber

/**
 * Снятие push-регистрации при выходе из аккаунта.
 *
 * Зачем: app-протокольная сессия (`member_id` + `login_key`) живёт отдельно от cookies сайта,
 * поэтому выход из аккаунта её НЕ затрагивал. Последствия были бы неприятные:
 *  - сервер 4PDA продолжал слать push на устройство разлогиненного пользователя (пустые
 *    пробуждения);
 *  - при входе ДРУГИМ аккаунтом остаток старого `login_key` заставил бы [PushRegistrar]
 *    восстановить ЧУЖУЮ сессию и привязать токен к прежнему пользователю — новый владелец
 *    устройства получал бы чужие уведомления.
 *
 * Поэтому локальную сессию стираем СРАЗУ (это защита от подмены аккаунта), а серверную
 * отписку делаем best-effort в фоне на уже снятых из хранилища значениях — они живут только
 * в памяти этого потока и никуда не персистятся.
 */
object PushLogout {

    fun onLogout(context: Context) {
        val appContext = context.applicationContext
        val session = PushSessionStore(appContext)
        if (!session.hasSession()) return

        val memberId = session.memberId
        val loginKey = session.loginKey
        // Сначала — локальная очистка: даже если процесс умрёт следующей строкой, чужая сессия
        // уже не сможет быть переиспользована при входе другим аккаунтом.
        session.clear()
        // Режим доставки возвращаем к опросу, иначе экран настроек показывал бы «Push»,
        // для которого больше нет сессии.
        runCatching {
            PreferenceManager.getDefaultSharedPreferences(appContext)
                    .edit().putString(KEY_DELIVERY_METHOD, "poll").apply()
        }

        if (loginKey.isNullOrEmpty()) return
        Thread {
            runCatching {
                AppProtocolClient.connectAny().use { client ->
                    if (client.resume(memberId, loginKey)) {
                        // Пустой токен = отписка на стороне сервера.
                        client.registerToken("", 0, PushRegistrar.PROVIDER_GOOGLE)
                    }
                }
            }.onFailure { Timber.w(it, "push unregister on logout failed (best effort)") }
        }.apply { isDaemon = true; name = "push-logout" }.start()
    }

    const val KEY_DELIVERY_METHOD = "notifications.delivery_method"
}
