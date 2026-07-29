package forpdateam.ru.forpda.ui.fragments.settings

import android.content.Context
import androidx.preference.PreferenceManager
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.client.proxy.ProxySettings
import forpdateam.ru.forpda.common.AppToast
import forpdateam.ru.forpda.notifications.push.PushSetupController
import forpdateam.ru.forpda.pro.ProLicense
import kotlinx.coroutines.launch

/**
 * Диалог активации ProPDA Pro — общий для всех платных функций.
 *
 * Ключ один на все: активировал push — прокси открылся тем же ключом, отдельной покупки нет.
 * Поэтому диалог живёт отдельно от экрана уведомлений: его открывают оба экрана, и оба показывают
 * пользователю одно и то же окно с его ID и полем ввода.
 */
object ProDialog {

    /**
     * @param onChanged вызывается после успешной активации — экрану нужно обновить свои подписи и
     *   доступность пунктов.
     */
    fun show(context: Context, onChanged: () -> Unit = {}) {
        val memberId = ProLicense.currentMemberId(context)
        if (memberId == null) {
            toast(context, R.string.pro_status_not_logged)
            return
        }
        val density = context.resources.displayMetrics.density
        val pad = (24 * density).toInt()
        val input = android.widget.EditText(context).apply {
            hint = context.getString(R.string.pro_dialog_hint)
            setSingleLine(false)
            maxLines = 3
            setText(PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(ProLicense.KEY_LICENSE, "").orEmpty())
        }
        // Пояснение и ID — своими View, а не setMessage(): текст диалога не кликабелен, а ID
        // пользователю нужно передать автору, поэтому он должен копироваться одним касанием.
        val explanation = android.widget.TextView(context).apply {
            text = context.getString(R.string.pro_dialog_message)
            setPadding(0, 0, 0, (12 * density).toInt())
        }
        val idView = android.widget.TextView(context).apply {
            text = context.getString(R.string.pro_dialog_id, memberId)
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            isClickable = true
            isFocusable = true
            // Штатный фон «нажимаемого» элемента, чтобы касание давало отклик.
            val outValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            val vp = (8 * density).toInt()
            setPadding(vp, vp, vp, vp)
            setOnClickListener { copyMemberId(context, memberId) }
        }
        val hintView = android.widget.TextView(context).apply {
            text = context.getString(R.string.pro_dialog_id_hint)
            textSize = 12f
            alpha = 0.7f
            setPadding(0, 0, 0, (12 * density).toInt())
        }
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(explanation)
            addView(idView)
            addView(hintView)
            addView(input)
        }
        val builder = androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle(R.string.pro_dialog_title)
                .setView(container)
                .setPositiveButton(R.string.pro_activate) { _, _ ->
                    when (ProLicense.activate(context, input.text.toString())) {
                        ProLicense.Result.Activated -> {
                            // Сетевой слой держит результат проверки в коротком кэше — сбрасываем,
                            // чтобы функции открылись сразу, а не через минуту.
                            forpdateam.ru.forpda.pro.LicenseGuard.invalidate()
                            toast(context, R.string.pro_activated)
                            onChanged()
                        }
                        ProLicense.Result.Invalid -> toast(context, R.string.pro_invalid)
                        ProLicense.Result.NotLoggedIn -> toast(context, R.string.pro_status_not_logged)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
        builder.show()
    }

    /**
     * Приводит платные функции в соответствие с ключом: если активации нет, выключает их.
     *
     * Кнопки «удалить ключ» намеренно нет — покупка разовая и привязана к аккаунту, удалять его
     * пользователю незачем. Но активация может пропасть сама: ключ выпущен под конкретный
     * member_id, и после смены аккаунта 4PDA он перестаёт подходить. Тогда остался бы push,
     * который больше не доставляется (сообщения отбрасывает
     * [forpdateam.ru.forpda.notifications.push.FcmMessagingReceiver]) — и пользователь молча сидел
     * бы без уведомлений; и включённый тумблер прокси, который ничего не делает.
     */
    fun revokeIfLocked(context: Context) {
        if (ProLicense.isUnlocked(context)) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val hasSomethingToTurnOff = prefs.getBoolean(ProxySettings.KEY_ENABLED, false) ||
                prefs.getString(KEY_DELIVERY_METHOD, DELIVERY_POLL) == DELIVERY_PUSH
        if (!hasSomethingToTurnOff) return
        prefs.edit()
                .putBoolean(ProxySettings.KEY_ENABLED, false)
                // Возвращаем бесплатный канал доставки — те же значения, что ставит
                // NotificationsSettingsFragment.applyDeliveryMethod("poll").
                .putString(KEY_DELIVERY_METHOD, DELIVERY_POLL)
                .putBoolean(KEY_BG_ENABLED, true)
                .putBoolean(KEY_PERSISTENT_WS, false)
                .apply()
        // Токен снимаем в фоне: сеть может быть недоступна, но локально push уже выключен.
        pushCleanupScope.launch {
            runCatching { PushSetupController(context).disablePush() }
        }
    }

    /** Отписка от push переживает закрытие экрана — иначе токен остался бы зарегистрированным. */
    private val pushCleanupScope =
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    /** Копирует ID аккаунта — его нужно передать автору для выпуска ключа. */
    fun copyMemberId(context: Context, memberId: Int) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                as? android.content.ClipboardManager ?: return
        cm.setPrimaryClip(android.content.ClipData.newPlainText("4PDA ID", memberId.toString()))
        // Android 13+ сам показывает всплывающее подтверждение копирования — свой тост был бы дублем.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            toast(context, R.string.pro_id_copied)
        }
    }

    /** Подпись статуса для пункта «Активация» на любом экране. */
    fun statusSummary(context: Context): String = when {
        ProLicense.isUnlocked(context) -> context.getString(R.string.pro_status_active)
        else -> ProLicense.currentMemberId(context)
                ?.let { context.getString(R.string.pro_status_locked, it) }
                ?: context.getString(R.string.pro_status_not_logged)
    }

    private fun toast(context: Context, res: Int) =
            AppToast.makeText(context, res, AppToast.LENGTH_SHORT).show()

    private const val KEY_DELIVERY_METHOD = "notifications.delivery_method"
    private const val DELIVERY_POLL = "poll"
    private const val DELIVERY_PUSH = "push"
    private const val KEY_BG_ENABLED = "notifications.bg.enabled"
    private const val KEY_PERSISTENT_WS = "notifications.bg.persistent_ws"
}
