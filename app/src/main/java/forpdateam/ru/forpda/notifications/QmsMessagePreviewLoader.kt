package forpdateam.ru.forpda.notifications

import android.content.Context
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.entity.remote.qms.QmsMessage
import forpdateam.ru.forpda.model.data.remote.api.qms.QmsApi
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Дозагрузка текста сообщений QMS для шторки.
 *
 * Ни один источник событий текста не несёт: WS-пакет — это `[.., "q<id>", eventCode, msgId]`,
 * FCM-payload 4PDA — `t/i/e1/an` (тип/диалог/код/ник), инспектор `CODE=qms` — ник, заголовок
 * диалога и счётчик. Поэтому уведомление показывало «N непрочитанных сообщений»: показывать
 * было нечего. Текст существует только в самом диалоге, и взять его можно лишь отдельным
 * запросом.
 *
 * Берём его XHR-эндпоинтом `action=get-thread-messages` (тот же, которым открытый чат
 * добирает сообщения в реальном времени), а НЕ страницей диалога `act=qms&mid&t`.
 *
 * ⚠️ ЦЕНА, ИЗМЕРЕННАЯ ЖИВЬЁМ (28.07.26): этот запрос сервер всё равно засчитывает как ПРОЧТЕНИЕ
 * диалога. Замер в воркере — счётчик инспектора до и сразу после дозагрузки:
 * `before=[9432633:3] after=[]`, дважды подряд; контрольный прогон с выключенной дозагрузкой
 * оставлял непрочитанное на месте. То есть текст в шторке стоит того, что сообщения гаснут
 * ещё до того, как их открыли: и бейдж QMS, и кнопка «Прочитано», и непрочитанное на других
 * устройствах. Безопасного источника текста пока не найдено (в WS/пуше/инспекторе его нет,
 * список диалогов и список контактов отдают только заголовки и счётчики), поэтому настройка
 * по умолчанию ВЫКЛЮЧЕНА и включается осознанно.
 *
 * Запрос инкрементальный: [NotificationPreferencesHolder.getQmsPreviewAnchor] помнит
 * последний виденный id сообщения в диалоге, поэтому после первого раза в ответ попадают
 * только новые сообщения, а не вся переписка.
 */
@Singleton
class QmsMessagePreviewLoader @Inject constructor(
        private val qmsApi: QmsApi,
        private val prefs: NotificationPreferencesHolder,
) {

    /**
     * Пишет в [NotificationEvent.previewMessages] текст непрочитанных сообщений диалога.
     * Любой сбой (сеть, разбор, таймаут) — не ошибка: уведомление просто останется со
     * счётчиком, как раньше.
     */
    suspend fun enrich(context: Context, event: NotificationEvent) {
        if (!event.fromQms() || event.isRead) return
        if (!prefs.getQmsPreviewEnabled()) return
        // Системный диалог («Сообщения 4PDA», mid=0) не трогаем: get-thread-messages с mid=0
        // не проверен, а молча пометить прочитанными СИСТЕМНЫЕ оповещения — худшая из побочек.
        if (event.userId <= 0) return
        val themeId = event.sourceId
        if (themeId <= 0) return

        val startedAt = System.currentTimeMillis()
        val texts = runCatching { load(context, event.userId, themeId, event.msgCount) }
                .onFailure {
                    Timber.w(it, "QMS preview load failed for theme %d", themeId)
                    NotifDiagLog.log(context, "qms preview: t=$themeId failed ${it.javaClass.simpleName}")
                }
                .getOrNull()
                .orEmpty()
        if (texts.isEmpty()) {
            NotifDiagLog.log(context, "qms preview: t=$themeId empty (${System.currentTimeMillis() - startedAt}ms)")
            return
        }
        event.previewMessages = texts
        NotifDiagLog.log(
                context,
                "qms preview: t=$themeId shown=${texts.size} chars=${texts.sumOf { it.length }} " +
                        "(${System.currentTimeMillis() - startedAt}ms)"
        )
    }

    /**
     * Сериализуем проходы: WS сыплет события пачкой, и два параллельных прохода по одному
     * диалогу успели бы разъехаться на общем якоре (оба увидели бы старое значение).
     */
    private val mutex = Mutex()

    private suspend fun load(
            context: Context,
            userId: Int,
            themeId: Int,
            unreadCount: Int,
    ): List<String> = mutex.withLock {
        val anchor = prefs.getQmsPreviewAnchor(themeId)
        var fetched = fetch(userId, themeId, anchor)
        // Пусто при живом якоре — это чаще всего «нового нет» (дубль события: WS и пуш об одном
        // и том же). Тогда показываем то, что уже в буфере, и НЕ ходим за диалогом целиком.
        // Полный перезабор только когда показывать реально нечего: буфер пуст после перезапуска
        // процесса, либо якорь протух (сообщения удалены — удаление в QMS одностороннее).
        if (fetched.isEmpty() && anchor > 0 && QmsPreviewStore.isEmpty(themeId)) {
            NotifDiagLog.log(context, "qms preview: t=$themeId anchor=$anchor stale, refetch from 0")
            fetched = fetch(userId, themeId, 0)
        }
        // Якорь двигаем по ВСЕМ сообщениям, включая свои: иначе собственный ответ
        // пользователя каждый раз возвращался бы в выборке.
        fetched.maxOfOrNull { it.id }?.let { maxId ->
            if (maxId > anchor) prefs.setQmsPreviewAnchor(themeId, maxId)
        }

        val placeholder = context.getString(R.string.notification_qms_image)
        val incoming = fetched
                .filter { !it.isDate && !it.isMyMessage && it.id > 0 }
                .mapNotNull { message ->
                    QmsPreviewText.fromHtml(message.content, placeholder)
                            .takeIf { it.isNotEmpty() }
                            ?.let { message.id to it }
                }
        QmsPreviewStore.append(themeId, incoming)
        // Показываем ровно столько, сколько сервер считает непрочитанным (но не больше буфера):
        // иначе уведомление про одно сообщение притащило бы в шторку и уже прочитанные.
        QmsPreviewStore.take(themeId, unreadCount.coerceAtLeast(1))
    }

    private suspend fun fetch(userId: Int, themeId: Int, afterMessageId: Int): List<QmsMessage> =
            withContext(Dispatchers.IO) {
                withTimeout(FETCH_TIMEOUT_MS) {
                    qmsApi.getMessagesAfter(userId, themeId, afterMessageId)
                }
            }

    private companion object {
        private const val FETCH_TIMEOUT_MS = 20_000L
    }
}

/**
 * Уже показанные тексты по диалогам.
 *
 * Нужен, потому что второе сообщение перерисовывает то же уведомление (notifyId один на
 * диалог), а инкрементальный запрос вернёт только его: без буфера первое сообщение молча
 * исчезало бы из «переписки» в шторке. Живёт только в памяти — после смерти процесса
 * уведомление честно покажет то, что удалось добрать сейчас.
 */
object QmsPreviewStore {

    /** Столько строк максимум держим на диалог: дальше шторка всё равно не покажет. */
    const val MAX_PER_THEME = 5

    private val buffers = java.util.concurrent.ConcurrentHashMap<Int, MutableList<Pair<Int, String>>>()

    fun append(themeId: Int, messages: List<Pair<Int, String>>) {
        if (messages.isEmpty()) return
        val buffer = buffers.getOrPut(themeId) { java.util.Collections.synchronizedList(mutableListOf()) }
        synchronized(buffer) {
            for (message in messages) {
                if (buffer.none { it.first == message.first }) buffer.add(message)
            }
            while (buffer.size > MAX_PER_THEME) buffer.removeAt(0)
        }
    }

    fun take(themeId: Int, limit: Int): List<String> {
        val buffer = buffers[themeId] ?: return emptyList()
        return synchronized(buffer) {
            buffer.takeLast(limit.coerceIn(1, MAX_PER_THEME)).map { it.second }
        }
    }

    fun isEmpty(themeId: Int): Boolean = buffers[themeId].isNullOrEmpty()

    /** Диалог прочитан/уведомление снято — накопленное больше не наше дело. */
    fun forget(themeId: Int) {
        buffers.remove(themeId)
    }

    fun clear() {
        buffers.clear()
    }
}
