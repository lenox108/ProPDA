package forpdateam.ru.forpda.notifications.push

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Минимальный клиент Huawei Mobile Services для получения push-токена — без HMS SDK.
 *
 * Зачем свой: официальный клиент 4PDA (`ru.fourpda.client`) делает ровно так же — в его apk нет
 * ни классов `com.huawei`, ни `agconnect-services.json`, только ручная AIDL-привязка (их класс так и
 * логируется — «PicoHCM»). Тянуть ради одного токена весь HMS SDK не нужно, а на устройствах без
 * HMS он ещё и мёртвый груз.
 *
 * Протокол (реверс их `c1.java`):
 *  - bind `com.huawei.hms.core.aidlservice` в пакете `com.huawei.hwid`;
 *  - запрос: `transact(2, …, FLAG_ONEWAY)` с интерфейсом `com.huawei.hms.core.aidl.IAIDLInvoke`,
 *    затем `writeInt(1)`, seq, имя метода, header-Bundle, body-Bundle и биндер обратного вызова;
 *  - ответ приходит в наш биндер (`com.huawei.hms.core.aidl.IAIDLCallback`, транзакция 1):
 *    флаг, версия, uri, header (`statusCode`) и body;
 *  - методы: `core.connect`, затем `push.gettoken`;
 *  - САМ ТОКЕН приходит не в ответе, а широковещательным `com.huawei.android.push.intent
 *    .REGISTRATION` с байтовым extra `device_token` — его ловит [HmsPushReceiver].
 *
 * @param appId идентификатор приложения 4PDA в Huawei (в их apk лежит открытым текстом).
 */
class PicoHms(
        private val context: Context,
        private val appId: String = FOURPDA_HMS_APP_ID,
) {

    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var service: IBinder? = null
    private var binding = false
    private var seq = 0
    private val sessionId = UUID.randomUUID().toString()
    private val pending = mutableListOf<Request>()

    /**
     * Просит HMS выдать push-токен. Результата здесь нет: токен придёт бродкастом в
     * [HmsPushReceiver] — так устроен их протокол.
     */
    fun requestToken() {
        val body = Bundle().apply {
            putString("aaid", aaid())
            putString("appId", appId)
            putBoolean("firstTime", !hasAgreement())
            putString("packageName", context.packageName)
            putString("scope", SCOPE)
        }
        send(METHOD_GET_TOKEN, body)
    }

    private fun send(method: String, body: Bundle) {
        val request = Request(method, body)
        val needBind: Boolean
        synchronized(lock) {
            pending.add(request)
            needBind = service == null && !binding
            if (needBind) binding = true
        }
        if (needBind) {
            val intent = Intent(AIDL_SERVICE).setPackage(HMS_PACKAGE)
            val bound = runCatching {
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }.getOrDefault(false)
            if (!bound) {
                Timber.w("PicoHms: не удалось привязаться к %s", HMS_PACKAGE)
                synchronized(lock) { binding = false; pending.remove(request) }
            }
        } else if (service != null) {
            request.dispatch()
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            synchronized(lock) {
                service = binder
                binding = false
            }
            // Сначала `core.connect` — без него HMS отвечает на push.gettoken отказом.
            Request(METHOD_CONNECT, Bundle()).dispatch()
            val queued = synchronized(lock) { pending.toList() }
            queued.forEach { it.dispatch() }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            synchronized(lock) { service = null }
        }
    }

    /**
     * Запрос и одновременно приёмник ответа: HMS отвечает не в возврате транзакции, а вызовом
     * нашего биндера. Реализуем [android.os.IInterface], потому что `attachInterface` принимает
     * именно его — так же оформлен колбэк в их клиенте.
     */
    private inner class Request(val method: String, val body: Bundle) : Binder(), android.os.IInterface {

        init {
            attachInterface(this, CALLBACK_INTERFACE)
        }

        override fun asBinder(): IBinder = this

        private val timeout = Runnable {
            Timber.w("PicoHms: %s без ответа", method)
            finish()
        }

        fun dispatch() {
            val binder = synchronized(lock) { service } ?: return
            val parcel = Parcel.obtain()
            main.postDelayed(timeout, TimeUnit.SECONDS.toMillis(30))
            try {
                val header = Bundle()
                header.putInt("apiLevel", 0)
                putStringArrayAsLinkedBundle(header, "apiNameList", API_NAMES)
                header.putString("appId", appId)
                header.putString("packageName", context.packageName)
                header.putInt("sdkVersion", SDK_VERSION)
                header.putString("sessionId", sessionId)
                parcel.writeInterfaceToken(INVOKE_INTERFACE)
                parcel.writeInt(1)
                parcel.writeInt(synchronized(lock) { ++seq })
                parcel.writeString(method)
                parcel.writeBundle(header)
                parcel.writeBundle(body)
                parcel.writeStrongBinder(this)
                binder.transact(TRANSACTION_INVOKE, parcel, null, IBinder.FLAG_ONEWAY)
            } catch (t: Throwable) {
                Timber.w(t, "PicoHms: запрос %s не ушёл", method)
                finish()
            } finally {
                parcel.recycle()
            }
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != TRANSACTION_CALLBACK) return super.onTransact(code, data, reply, flags)
            data.enforceInterface(CALLBACK_INTERFACE)
            if (data.readInt() != 0) {
                data.readInt()      // версия ответа
                data.readString()   // uri
                val header = data.readBundle(javaClass.classLoader)
                data.readBundle(javaClass.classLoader) // тело: для gettoken пустое, токен придёт бродкастом
                val status = header?.getInt("statusCode", -1) ?: -1
                Timber.i("PicoHms: %s -> statusCode=%d", method, status)
            } else {
                Timber.w("PicoHms: %s -> пустой ответ", method)
            }
            finish()
            return true
        }

        private fun finish() {
            main.removeCallbacks(timeout)
            synchronized(lock) { pending.remove(this) }
        }
    }

    /** Идентификатор экземпляра приложения: генерируется один раз и переживает перезапуски. */
    private fun aaid(): String {
        val prefs = context.getSharedPreferences(PREFS_AAID, Context.MODE_PRIVATE)
        prefs.getString(KEY_AAID, null)?.let { return it }
        val generated = UUID.randomUUID().toString()
        prefs.edit()
                .putString(KEY_AAID, generated)
                .putLong("creationTime", System.currentTimeMillis())
                .apply()
        return generated
    }

    /** Первый ли это запрос токена — HMS ждёт признак в `firstTime`. */
    private fun hasAgreement(): Boolean =
            context.getSharedPreferences(PREFS_SELF_INFO, Context.MODE_PRIVATE)
                    .contains("hasRequestAgreement")

    companion object {
        /** Идентификатор приложения 4PDA в Huawei — лежит открытым текстом в их apk. */
        const val FOURPDA_HMS_APP_ID = "101200861"

        private const val HMS_PACKAGE = "com.huawei.hwid"
        private const val AIDL_SERVICE = "com.huawei.hms.core.aidlservice"
        private const val INVOKE_INTERFACE = "com.huawei.hms.core.aidl.IAIDLInvoke"
        private const val CALLBACK_INTERFACE = "com.huawei.hms.core.aidl.IAIDLCallback"
        private const val METHOD_CONNECT = "core.connect"
        private const val METHOD_GET_TOKEN = "push.gettoken"
        private const val SCOPE = "HCM"
        private const val SDK_VERSION = 40000300
        private const val TRANSACTION_INVOKE = 2
        private const val TRANSACTION_CALLBACK = 1
        private val API_NAMES = arrayOf("HuaweiPush.API", "Core.API")
        private const val PREFS_AAID = "aaid"
        private const val KEY_AAID = "aaid"
        private const val PREFS_SELF_INFO = "push_client_self_info"

        /** Токен из бродкаста `…push.intent.REGISTRATION`: приходит массивом байт в UTF-8. */
        fun tokenFrom(intent: Intent): String? =
                intent.getByteArrayExtra("device_token")?.let { String(it, Charsets.UTF_8) }?.takeIf { it.isNotBlank() }

        /**
         * Есть ли на устройстве рабочий HMS. Проверка повторяет их: пакет установлен, не выключен
         * и подписан известным сертификатом — иначе это не настоящий HMS Core.
         */
        fun isAvailable(context: Context): Boolean = runCatching {
            val pm = context.packageManager
            val info = pm.getPackageInfo(HMS_PACKAGE, 0) ?: return false
            @Suppress("DEPRECATION")
            if (info.versionCode < MIN_HMS_VERSION) return false
            when (pm.getApplicationEnabledSetting(HMS_PACKAGE)) {
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> return false
            }
            signatureSha256(context) == HMS_SIGNATURE
        }.getOrDefault(false)

        private const val MIN_HMS_VERSION = 30000000
        private const val HMS_SIGNATURE =
                "B92825C2BD5D6D6D1E7F39EECD17843B7D9016F611136B75441BC6F4D3F00F05"

        private fun signatureSha256(context: Context): String? = runCatching {
            @Suppress("DEPRECATION")
            val signatures = context.packageManager
                    .getPackageInfo(HMS_PACKAGE, android.content.pm.PackageManager.GET_SIGNATURES)
                    .signatures ?: return null
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(signatures.first().toByteArray())
            digest.joinToString("") { "%02X".format(it) }
        }.getOrNull()

        /**
         * Массив в их маршалинге: связанный список бандлов `_next_item_`/`_value_`.
         * Обычный `putStringArray` HMS не понимает — проверено по их коду.
         */
        private fun putStringArrayAsLinkedBundle(target: Bundle, key: String, values: Array<String>) {
            var tail = Bundle()
            val head = tail
            for (value in values) {
                val next = Bundle()
                next.putString("_value_", value)
                tail.putBundle("_next_item_", next)
                tail = next
            }
            target.putBundle(key, head)
        }
    }
}
