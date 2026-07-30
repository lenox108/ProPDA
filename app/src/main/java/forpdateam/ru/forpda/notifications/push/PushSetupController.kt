package forpdateam.ru.forpda.notifications.push

import android.content.Context
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.request.ImageRequest
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.resume

/**
 * Одноразовая настройка push-доставки: получает app-протокольную сессию (login_key) через `ml`
 * (пароль + капча — как у офиц. клиента, который логинится тем же способом) и регистрирует
 * FCM-токен. login_key сохраняется, поэтому дальше регистрация идёт молча по `ma`.
 *
 * UI-часть (диалоги логина/капчи) намеренно простая: это редкий одноразовый шаг.
 */
class PushSetupController(private val context: Context) {

    private val notifPrefs = NotificationPreferencesHolder(context)
    private val session = PushSessionStore(context)
    private val registrar = PushRegistrar(context, notifPrefs, session)

    /** Причина последнего сетевого сбоя — показываем её вместо бесполезного «status -1». */
    private var lastError: String? = null

    sealed class Outcome {
        object Registered : Outcome()
        object Cancelled : Outcome()
        data class Failed(val message: String) : Outcome()
    }

    /** Включение push: если сессия есть — просто регистрируем токен, иначе логин → регистрация. */
    suspend fun enablePush(defaultLogin: String? = null): Outcome {
        if (session.hasSession()) {
            return when (val r = registrar.register(force = true)) {
                is PushRegistrar.Result.Success -> Outcome.Registered
                is PushRegistrar.Result.NoSession -> loginThenRegister(defaultLogin)
                is PushRegistrar.Result.NoGms -> Outcome.Failed(NO_GMS)
                is PushRegistrar.Result.NotPro -> Outcome.Failed(NOT_PRO)
                is PushRegistrar.Result.Error -> Outcome.Failed(r.reason)
            }
        }
        return loginThenRegister(defaultLogin)
    }

    /**
     * Выключение push: снимаем регистрацию токена на сервере, но СЕССИЮ СОХРАНЯЕМ.
     *
     * Раньше здесь стоял `session.clear()`, и это ломало соседнюю функцию: на той же сессии
     * (`ma` по `login_key`) держится живой канал событий
     * ([forpdateam.ru.forpda.model.repository.events.RealtimeEventClient]) — а он-то как раз и
     * нужен тем, кто отказался от push или у кого нет сервисов Google. Стирать `login_key`
     * уместно там, где сессия действительно перестаёт быть нашей: выход из аккаунта
     * ([PushLogout]) и удаление ключа активации.
     */
    suspend fun disablePush() {
        runCatching { registrar.unregister() }
    }

    private suspend fun loginThenRegister(defaultLogin: String?): Outcome {
        val creds = askCredentials(defaultLogin) ?: return Outcome.Cancelled
        val loginOk = withContext(Dispatchers.IO) {
            runCatching {
                AppProtocolClient.connectAny().use { client ->
                    var result = client.login(creds.first, creds.second)
                    var attempts = 0
                    while (result is AppProtocolClient.LoginResult.Captcha && attempts < 3) {
                        attempts++
                        val url = result.imageUrl
                        val captcha = withContext(Dispatchers.Main) { askCaptcha(url) }
                                ?: return@use AppProtocolClient.LoginResult.Failed(-99)
                        result = client.login(creds.first, creds.second, captcha = captcha)
                    }
                    result
                }
            }.getOrElse {
                Timber.e(it, "push login failed")
                // Текст причины нужен пользователю: «status -1» ничего не говорит о том,
                // что именно не сложилось — сеть, TLS или блокировка провайдером.
                lastError = it.message ?: it.javaClass.simpleName
                AppProtocolClient.LoginResult.Failed(-1)
            }
        }

        return when (loginOk) {
            is AppProtocolClient.LoginResult.Success -> {
                // Аккаунт push обязан совпадать с тем, под которым работает приложение: иначе
                // сервер слал бы события ЧУЖОГО пользователя, а локальная проверка их не нашла
                // бы — пустые пробуждения и путаница.
                val appUserId = runCatching {
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                            .getString("member_id", null)?.toIntOrNull()
                }.getOrNull()
                if (appUserId != null && appUserId != 0 && appUserId != loginOk.memberId) {
                    Timber.w("push login account mismatch: app=%d push=%d", appUserId, loginOk.memberId)
                    return Outcome.Failed(ACCOUNT_MISMATCH)
                }
                session.saveSession(loginOk.memberId, loginOk.loginKey)
                when (val r = registrar.register(force = true)) {
                    is PushRegistrar.Result.Success -> Outcome.Registered
                    is PushRegistrar.Result.NoGms -> Outcome.Failed(NO_GMS)
                    is PushRegistrar.Result.NoSession -> Outcome.Failed("session lost")
                    is PushRegistrar.Result.NotPro -> Outcome.Failed(NOT_PRO)
                    is PushRegistrar.Result.Error -> Outcome.Failed(r.reason)
                }
            }
            is AppProtocolClient.LoginResult.Captcha -> Outcome.Failed("captcha")
            is AppProtocolClient.LoginResult.Failed -> when {
                loginOk.status == -99 -> Outcome.Cancelled
                loginOk.status == -1 -> Outcome.Failed(lastError ?: "нет связи с сервером")
                else -> Outcome.Failed("login status ${loginOk.status}")
            }
        }
    }

    // region dialogs
    private suspend fun askCredentials(defaultLogin: String?): Pair<String, String>? =
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val pad = (context.resources.displayMetrics.density * 16).toInt()
                    val loginField = EditText(context).apply {
                        hint = "Логин 4PDA"
                        setText(defaultLogin.orEmpty())
                        inputType = InputType.TYPE_CLASS_TEXT
                    }
                    val passField = EditText(context).apply {
                        hint = "Пароль"
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    }
                    val hint = TextView(context).apply {
                        text = "Вход нужен один раз, чтобы включить push (как в официальном клиенте). " +
                                "Пароль не сохраняется — хранится только ключ сессии."
                        setPadding(0, 0, 0, pad / 2)
                    }
                    val layout = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(pad, pad / 2, pad, 0)
                        addView(hint)
                        addView(loginField)
                        addView(passField)
                    }
                    MaterialAlertDialogBuilder(context)
                            .setTitle("Включить push (Google)")
                            .setView(layout)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                val l = loginField.text.toString().trim()
                                val p = passField.text.toString()
                                if (cont.isActive) cont.resume(if (l.isEmpty() || p.isEmpty()) null else l to p)
                            }
                            .setNegativeButton(android.R.string.cancel) { _, _ ->
                                if (cont.isActive) cont.resume(null)
                            }
                            .setOnCancelListener { if (cont.isActive) cont.resume(null) }
                            .show()
                }
            }

    private suspend fun askCaptcha(imageUrl: String): Int? =
            suspendCancellableCoroutine { cont ->
                val pad = (context.resources.displayMetrics.density * 16).toInt()
                val image = ImageView(context).apply {
                    adjustViewBounds = true
                    layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                val field = EditText(context).apply {
                    hint = "Число с картинки"
                    inputType = InputType.TYPE_CLASS_NUMBER
                }
                val layout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(pad, pad / 2, pad, 0)
                    addView(image)
                    addView(field)
                }
                // Через общий ImageLoader приложения, а не своим URL.openStream(): тот шёл мимо
                // регулятора запросов и кук, добавлял неучтённый запрос к 4pda.to и на 429
                // просто молча не показывал картинку.
                ForPdaCoil.imageLoader.enqueue(
                        ImageRequest.Builder(context)
                                .data(ForPdaCoil.normalizeData(imageUrl))
                                .target(image)
                                .build())
                MaterialAlertDialogBuilder(context)
                        .setTitle("Введите число с картинки")
                        .setView(layout)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            val n = field.text.toString().trim().toIntOrNull()
                            if (cont.isActive) cont.resume(n)
                        }
                        .setNegativeButton(android.R.string.cancel) { _, _ ->
                            if (cont.isActive) cont.resume(null)
                        }
                        .setOnCancelListener { if (cont.isActive) cont.resume(null) }
                        .show()
            }
    // endregion

    companion object {
        const val NO_GMS = "no_gms"
        const val ACCOUNT_MISMATCH = "account_mismatch"
        const val NOT_PRO = "not_pro"
    }
}
