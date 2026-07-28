package forpdateam.ru.forpda.notifications

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.appicon.applySelectedNotificationIcon
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import forpdateam.ru.forpda.model.data.remote.api.ApiUtils
import forpdateam.ru.forpda.ui.activities.MainActivity
import timber.log.Timber

/**
 * Единая сборка системных уведомлений для foreground-сервиса и фонового воркера.
 * До этого у каждого была своя копия билдера, и они успели разойтись: воркер не умел
 * ни аватарки, ни stacked-уведомления, ни события сайта.
 *
 * Группировка ([NotificationGroups]) — вторая обязанность объекта: каждое событие уходит в
 * группу своей категории, а рядом живёт сводка, которую пересобирает [refreshGroupSummaries]
 * после любой публикации и любой отмены.
 */
object NotificationPublisher {

    private const val NOTIFICATIONS_LOG_TAG = "Notifications"
    /** Помечает интент открытия темы из шторки — MainActivity даёт ему «доверие непрочитанного». */
    const val EXTRA_FROM_NOTIFICATION_TOPIC = "forpda_from_notification_topic"
    private const val INBOX_STYLE_MAX_LINES = 6

    /**
     * Сколько уведомлений пачки показать отдельными карточками. Остальные видны только строкой
     * «…и ещё N» в сводке: Android держит жёсткий лимит активных уведомлений на приложение
     * (25), и высыпать туда всю пачку — верный способ потерять часть молча.
     */
    private const val BATCH_CHILDREN_MAX = 8

    /** Сводка нужна только когда сворачивать реально есть что. */
    private const val SUMMARY_MIN_CHILDREN = 2

    /** @return ID опубликованного уведомления либо null, если публикация не состоялась. */
    @SuppressLint("MissingPermission")
    fun publish(
            context: Context,
            prefs: NotificationPreferencesHolder,
            event: NotificationEvent,
            intentUrlOverride: String? = null,
            avatar: android.graphics.Bitmap? = null,
            silent: Boolean = false,
            refreshSummary: Boolean = true,
    ): Int? {
        if (!prefs.getMainEnabled()) return null

        val channelId = channelIdFor(event)
        NotificationsService.createEventChannels(context)
        ensureChannel(context, channelId, channelNameFor(context, event))
        // Гейт до сборки: иначе заблокированная публикация всё равно успевала опубликовать
        // ярлык собеседника и прогреть аватар.
        if (!canNotify(context, channelId, event.notificationLogCategory())) return null

        val title = titleFor(context, event)
        val text = textFor(context, event)
        val summary = summaryFor(context, event)
        val intentUrl = intentUrlOverride ?: intentUrlFor(event)
        val qmsAvatar = avatarFor(event, avatar)
        val conversationShortcutId = QmsConversationShortcuts.push(context, event, qmsAvatar)

        val notifyIntent = Intent(Intent.ACTION_VIEW, Uri.parse(intentUrl))
                .setClass(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Открытие темы из шторки — авторитетный сигнал «здесь есть непрочитанное». Помечаем его,
        // чтобы тема открылась с тем же доверием к getnewpost, что и открытие из Избранного, и
        // пометилась прочитанной по дочтению (иначе suppress-гейт не снимался без физ. скролла, и
        // короткий «новый ответ» на экран не помечался прочитанным — полевой репорт).
        if (event.fromTheme() && !event.isMention) {
            notifyIntent.putExtra(EXTRA_FROM_NOTIFICATION_TOPIC, true)
        }
        val pi = PendingIntent.getActivity(
                context,
                event.notifyId(),
                notifyIntent,
                NotificationsService.activityPendingIntentFlags(0)
        )

        val builder = NotificationCompat.Builder(context, channelId)
                .applySelectedNotificationIcon(context, smallIconFor(event))
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(styleFor(context, event, title, text, summary, qmsAvatar, conversationShortcutId))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)

        qmsAvatar?.let { builder.setLargeIcon(it) }
        conversationShortcutId?.let {
            // Пропуск в раздел «Диалоги» (Android 11+): shortcutId + MessagingStyle + Person.
            builder.setShortcutId(it)
            builder.setCategory(NotificationCompat.CATEGORY_MESSAGE)
        }
        NotificationActions.apply(context, builder, event)
        if (silent) {
            // Пачка: звонит один раз сводка, дети молчат. Иначе десяток событий = десяток сигналов.
            builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
        }

        val manager = NotificationManagerCompat.from(context)
        val notifyId = event.notifyId()
        manager.notify(notifyId, builder.build())
        Log.i(NOTIFICATIONS_LOG_TAG, "Published ${event.notificationLogCategory()} notification")
        // Запоминаем ДО refresh: шторка своего же уведомления сейчас ещё не отдаёт.
        rememberChild(summaryChild(context, event))
        if (refreshSummary) refreshGroupSummaries(context)
        return notifyId
    }

    /** Цветная large icon допустима только как аватар собеседника в QMS. */
    internal fun avatarFor(
            event: NotificationEvent,
            avatar: android.graphics.Bitmap?,
    ): android.graphics.Bitmap? = avatar.takeIf { event.fromQms() }

    /**
     * Публикация пачки событий: сами события идут отдельными (но молчащими) уведомлениями,
     * а звучит и сворачивает их одна сводка группы. Раньше здесь рисовалась рукодельная
     * «стопка» вне группы — она ложилась ПОВЕРХ уже висящих одиночных уведомлений.
     *
     * @return ID сводки либо null, если хотя бы одно уведомление пачки система не пропустила
     * (воркер по null не двигает снапшот, и событие переиграет).
     */
    fun publishBatch(
            context: Context,
            prefs: NotificationPreferencesHolder,
            events: List<NotificationEvent>,
    ): Int? {
        if (events.isEmpty() || !prefs.getMainEnabled()) return null

        val children = events.take(BATCH_CHILDREN_MAX)
        // Один ребёнок сводки не получит (сводку под него не публикуем) — значит, замолчать
        // он не имеет права: иначе событие пришло бы совсем беззвучно.
        val silent = children.size >= SUMMARY_MIN_CHILDREN
        var blocked = false
        val groups = mutableSetOf<String>()
        for (event in children) {
            if (publish(context, prefs, event, silent = silent, refreshSummary = false) == null) {
                blocked = true
            } else {
                groups += NotificationGroups.keyFor(event)
            }
        }
        val dropped = events.size - children.size
        if (dropped > 0) {
            Log.i(NOTIFICATIONS_LOG_TAG, "Batch trimmed: $dropped events shown only in group summary")
            NotifDiagLog.log(context, "batch: ${events.size} events, ${children.size} shown individually")
        }
        refreshGroupSummaries(context, alertGroups = groups)
        Log.i(NOTIFICATIONS_LOG_TAG, "Published batch of ${events.size} events into groups $groups")
        if (blocked) return null
        return NotificationGroups.summaryIdFor(NotificationGroups.keyFor(children.first()))
    }

    /** Ребёнок группы в том виде, в каком его показывает сводка. */
    data class SummaryChild(
            val id: Int,
            val groupKey: String,
            val line: CharSequence,
            val postTime: Long,
    )

    /**
     * Только что опубликованные дети. `notify()` — асинхронный binder-вызов, и следующий за ним
     * `getActiveNotifications()` своего же уведомления ещё не видит. На пачке (WS сыплет события
     * по одному в тесном цикле) это значило, что КАЖДАЯ публикация видела «детей меньше двух» и
     * сводку не ставила вовсе — полевой симптом «группировка не работает». Реестр живёт
     * [RECENT_TRUST_WINDOW_MS] и только дополняет шторку, никогда её не переопределяя: дальше
     * этого окна авторитет снова у системы, и свайпы пользователя ничего не воскрешает.
     */
    private val recentChildren = java.util.concurrent.ConcurrentHashMap<Int, SummaryChild>()

    private const val RECENT_TRUST_WINDOW_MS = 8_000L

    private fun rememberChild(child: SummaryChild) {
        recentChildren[child.id] = child
    }

    private fun forgetChildren(ids: Set<Int>) {
        ids.forEach { recentChildren.remove(it) }
    }

    /** Сервис снимает всё разом (логаут, выключение push) — реестр обязан обнулиться вместе с ним. */
    fun forgetAllChildren() {
        recentChildren.clear()
    }

    /**
     * Приводит сводки всех групп в соответствие с тем, что реально висит в шторке: где детей
     * больше одного — публикует/обновляет сводку, где не осталось — снимает. Состояние берётся
     * из [NotificationManager.getActiveNotifications], а не из собственного счётчика: свайпы
     * пользователя, авто-отмена по тапу и параллельные публикации сервиса и фонового воркера
     * иначе разъезжаются с любым нашим кэшем.
     *
     * Шторка отвечает с задержкой в обе стороны, поэтому свежие публикации досказывает
     * [recentChildren], а только что снятое — [excludeIds].
     *
     * @param alertGroups группы, чья сводка должна прозвучать (пачка). Для одиночных публикаций
     * пусто: звук уже дал сам ребёнок, а сводка молчит через `GROUP_ALERT_CHILDREN`.
     */
    @SuppressLint("MissingPermission")
    fun refreshGroupSummaries(
            context: Context,
            alertGroups: Set<String> = emptySet(),
            excludeIds: Set<Int> = emptySet(),
    ) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val active = runCatching { manager.activeNotifications }.getOrNull() ?: return
        val compat = NotificationManagerCompat.from(context)
        forgetChildren(excludeIds)
        val now = System.currentTimeMillis()
        recentChildren.entries.removeAll { now - it.value.postTime > RECENT_TRUST_WINDOW_MS }
        val observed = active.mapNotNull { sbn ->
            val notification = sbn.notification ?: return@mapNotNull null
            val groupKey = notification.group ?: return@mapNotNull null
            if (sbn.id in excludeIds) return@mapNotNull null
            if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return@mapNotNull null
            if (groupKey !in NotificationGroups.ALL) return@mapNotNull null
            SummaryChild(sbn.id, groupKey, summaryLineFor(notification), sbn.postTime)
        }
        val observedIds = observed.mapTo(mutableSetOf()) { it.id }
        val all = observed + recentChildren.values.filter { it.id !in observedIds }
        for (groupKey in NotificationGroups.ALL) {
            val summaryId = NotificationGroups.summaryIdFor(groupKey)
            val children = all
                    .filter { it.groupKey == groupKey && it.id != summaryId }
                    .sortedByDescending { it.postTime }
            if (children.size < SUMMARY_MIN_CHILDREN) {
                runCatching { compat.cancel(summaryId) }
                        .onFailure { Timber.w(it, "group summary cancel failed") }
                continue
            }
            val channelId = NotificationGroups.channelIdFor(groupKey)
            if (!canDeliver(context, channelId)) continue
            val builder = summaryBuilder(context, groupKey, children, alert = groupKey in alertGroups)
            runCatching { compat.notify(summaryId, builder.build()) }
                    .onFailure { Timber.w(it, "group summary publish failed") }
        }
    }

    private fun summaryBuilder(
            context: Context,
            groupKey: String,
            children: List<SummaryChild>,
            alert: Boolean,
    ): NotificationCompat.Builder {
        val title = context.getString(NotificationGroups.titleResFor(groupKey))
        val count = children.size
        val text = context.resources.getQuantityString(R.plurals.notification_group_count, count, count)
        val summaryId = NotificationGroups.summaryIdFor(groupKey)

        val notifyIntent = Intent(Intent.ACTION_VIEW, Uri.parse(NotificationGroups.listUrlFor(groupKey)))
                .setClass(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
                context,
                summaryId,
                notifyIntent,
                NotificationsService.activityPendingIntentFlags(0)
        )

        val builder = NotificationCompat.Builder(context, NotificationGroups.channelIdFor(groupKey))
                .applySelectedNotificationIcon(context, NotificationGroups.smallIconFor(groupKey))
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(summaryStyle(context, children, title, text))
                .setNumber(count.coerceAtMost(99))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setGroup(groupKey)
                .setGroupSummary(true)

        if (alert) {
            // Пачка: дети молчат (GROUP_ALERT_SUMMARY), звук даёт сводка — ровно один раз.
            builder.setOnlyAlertOnce(false)
        } else {
            builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                    // Сводка пересобирается на каждое событие и отмену; без этого каждая
                    // пересборка была бы поводом для нового сигнала.
                    .setOnlyAlertOnce(true)
        }
        return builder
    }

    internal fun summaryStyle(
            context: Context,
            children: List<SummaryChild>,
            title: CharSequence,
            summary: CharSequence,
    ): NotificationCompat.Style {
        val inbox = NotificationCompat.InboxStyle()
                .setBigContentTitle(title)
                .setSummaryText(summary)
        val shown = minOf(children.size, INBOX_STYLE_MAX_LINES)
        for (i in 0 until shown) {
            inbox.addLine(children[i].line)
        }
        if (children.size > shown) {
            inbox.addLine(context.getString(R.string.notification_stacked_more, children.size - shown))
        }
        return inbox
    }

    private fun summaryChild(context: Context, event: NotificationEvent): SummaryChild = SummaryChild(
            id = event.notifyId(),
            groupKey = NotificationGroups.keyFor(event),
            line = summaryLine(titleFor(context, event), textFor(context, event)),
            postTime = System.currentTimeMillis(),
    )

    /**
     * Строка сводки для уже висящего уведомления: исходного [NotificationEvent] на руках нет —
     * сводку пересобирает и действие из шторки, и отмена по прочтению.
     */
    private fun summaryLineFor(notification: Notification): CharSequence = summaryLine(
            notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            notification.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
    )

    /** Заголовок и текст пользовательские (ник, название темы) — экранируем перед склейкой в HTML. */
    private fun summaryLine(rawTitle: String, rawText: String): CharSequence {
        val title = rawTitle.trim()
        val text = rawText.trim()
        if (title.isEmpty()) return text
        if (text.isEmpty()) return title
        val html = "<b>${TextUtils.htmlEncode(title)}</b> ${TextUtils.htmlEncode(text)}"
        return ApiUtils.spannedFromHtml(html) ?: "$title $text"
    }

    private fun canNotify(context: Context, channelId: String, category: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(NOTIFICATIONS_LOG_TAG, "Skip $category notification: POST_NOTIFICATIONS denied")
            NotifDiagLog.log(context, "publish blocked: no POST_NOTIFICATIONS permission")
            return false
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.w(NOTIFICATIONS_LOG_TAG, "Skip $category notification: disabled by system")
            NotifDiagLog.log(context, "publish blocked: notifications disabled in Android")
            return false
        }
        // Канал, выключенный пользователем (importance NONE), — коварная потеря: notify() на него
        // МОЛЧА ничего не показывает. Возвращаем false, чтобы publish вернул null → воркер не
        // двинет снапшот/ключи и событие переиграет, когда канал включат (audit BUG-1/BUG-2).
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager?.getNotificationChannel(channelId)
        if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
            Log.w(NOTIFICATIONS_LOG_TAG, "Skip $category notification: channel $channelId disabled")
            NotifDiagLog.log(context, "publish blocked: channel $channelId disabled in Android")
            return false
        }
        return true
    }

    /**
     * Можно ли СЕЙЧАС доставить уведомление на данный канал: разрешение POST_NOTIFICATIONS,
     * глобальный тумблер Android и конкретный канал не выключены. Нужно фоновому воркеру, чтобы
     * НЕ двигать снапшот сравнения, пока доставка заблокирована системой — иначе событие
     * «съедается» безвозвратно (P1 из code review): publish() вернул null, а снапшот уехал, и
     * после выдачи разрешения событие уже не покажется. Каналы создаются заранее
     * ([NotificationsService.createEventChannels]); отсутствующий канал считаем доставляемым
     * (создастся при публикации).
     */
    fun canDeliver(context: Context, channelId: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return false
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return true
        val channel = manager.getNotificationChannel(channelId) ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun cancel(context: Context, event: NotificationEvent) {
        val notifyId = event.notifyId()
        // Диалог прочитан — накопленные тексты больше не показываем: иначе следующее
        // сообщение притащило бы их обратно в шторку.
        if (event.fromQms()) QmsPreviewStore.forget(event.sourceId)
        NotificationManagerCompat.from(context).cancel(notifyId)
        refreshGroupSummaries(context, excludeIds = setOf(notifyId))
    }

    fun channelIdFor(e: NotificationEvent): String = when {
        e.isMention -> NotificationsService.CHANNEL_MENTION_ID
        e.fromQms() -> NotificationsService.CHANNEL_QMS_ID
        e.fromTheme() -> NotificationsService.CHANNEL_FAV_ID
        else -> NotificationsService.CHANNEL_SITE_ID
    }

    fun channelNameFor(context: Context, e: NotificationEvent): String = when {
        e.isMention -> context.getString(R.string.notification_summary_mention)
        e.fromQms() -> context.getString(R.string.notification_summary_qms)
        e.fromTheme() -> context.getString(R.string.notification_summary_fav)
        else -> context.getString(R.string.notification_summary_comment)
    }

    /**
     * QMS с известным собеседником — MessagingStyle: «лицом» уведомления становится
     * аватар отправителя (как в мессенджерах), а не значок приложения. Это
     * единственный легальный способ подменить кружок в строке шторки: кружок
     * рисуется системой из ApplicationInfo и через Notification API не меняется.
     * Вместе с [QmsConversationShortcuts] это же условие пускает уведомление в раздел
     * «Диалоги» (Android 11+), поэтому стиль включается и без аватара — по одному нику.
     * Для всех остальных событий — прежний BigText.
     */
    private fun styleFor(
            context: Context,
            event: NotificationEvent,
            title: String,
            text: String,
            summary: String?,
            avatar: android.graphics.Bitmap?,
            conversationShortcutId: String?,
    ): NotificationCompat.Style {
        if (event.fromQms() && event.userNick.isNotEmpty()) {
            val sender = androidx.core.app.Person.Builder()
                    .setName(event.userNick)
                    .setKey(conversationShortcutId ?: QmsConversationShortcuts.shortcutId(event.userId))
                    .apply {
                        avatar?.let {
                            setIcon(androidx.core.graphics.drawable.IconCompat.createWithBitmap(it))
                        }
                    }
                    .build()
            val me = androidx.core.app.Person.Builder()
                    .setName(context.getString(R.string.notification_qms_me))
                    .build()
            val style = NotificationCompat.MessagingStyle(me)
                    .setConversationTitle(event.sourceTitle.takeIf { it.isNotBlank() })
            val previews = event.previewMessages.orEmpty().filter { it.isNotBlank() }
            if (previews.isEmpty()) {
                // Текст добрать не удалось (выключено настройкой, сеть, таймаут) — прежний
                // счётчик: он хотя бы честно говорит, сколько всего непрочитано.
                style.addMessage(
                        context.resources.getQuantityString(
                                R.plurals.notification_content_qms_count,
                                event.msgCount.coerceAtLeast(1),
                                event.msgCount.coerceAtLeast(1),
                        ),
                        System.currentTimeMillis(),
                        sender,
                )
            } else {
                // Время события — секунды инспектора и одно на всю пачку; разносим строки на
                // секунду, иначе MessagingStyle показывает их как одновременные.
                val baseTime = event.timeStamp.takeIf { it > 0 }?.times(1000L)
                        ?: System.currentTimeMillis()
                previews.forEachIndexed { index, message ->
                    style.addMessage(message, baseTime - (previews.lastIndex - index) * 1000L, sender)
                }
            }
            return style
        }
        return NotificationCompat.BigTextStyle()
                .setBigContentTitle(title)
                .bigText(text)
                .setSummaryText(summary)
    }

    fun smallIconFor(e: NotificationEvent): Int = when {
        e.fromQms() -> R.drawable.ic_notify_qms
        e.fromTheme() && e.isMention -> R.drawable.ic_notify_mention
        e.fromTheme() -> R.drawable.ic_notify_favorites
        e.fromSite() -> R.drawable.ic_notify_site
        else -> R.drawable.ic_notify_qms
    }

    fun titleFor(context: Context, e: NotificationEvent): String = when {
        e.fromQms() -> if (e.userNick.isEmpty()) {
            context.getString(R.string.notification_title_qms_fallback)
        } else {
            context.getString(R.string.notification_title_qms_from_Nick, e.userNick)
        }
        e.fromTheme() && e.isMention -> if (e.userNick.isEmpty()) {
            context.getString(R.string.notification_title_mention_fallback)
        } else {
            context.getString(R.string.notification_title_mention_Nick, e.userNick)
        }
        e.fromSite() -> "ForPDA"
        // Как в офиц. клиенте: в строке шторки — ник ответившего, а темой занят текст.
        // Общая «Новые сообщения в избранной теме» остаётся только когда ника нет
        // (WS-событие без обогащения инспектором).
        e.fromTheme() -> if (e.userNick.isBlank()) {
            context.getString(R.string.notification_title_favorite)
        } else {
            context.getString(R.string.notification_title_favorite_Nick, e.userNick)
        }
        else -> e.userNick
    }

    fun textFor(context: Context, e: NotificationEvent): String = when {
        // Свёрнутая строка и строка сводки группы: сам текст сообщения информативнее
        // заголовка диалога, поэтому он в приоритете, когда его удалось добрать.
        e.fromQms() -> e.previewMessages?.lastOrNull()?.takeIf { it.isNotBlank() }
                ?: e.sourceTitle.ifBlank {
                    context.resources.getQuantityString(
                            R.plurals.notification_content_qms_count,
                            e.msgCount,
                            e.msgCount
                    )
                }
        e.fromTheme() && e.isMention -> e.sourceTitle.ifBlank {
            context.getString(R.string.notification_content_mention_fallback)
        }
        e.fromSite() -> e.sourceTitle.ifBlank {
            context.getString(R.string.notification_content_news)
        }
        e.fromTheme() -> e.sourceTitle.ifBlank {
            context.getString(R.string.notification_content_theme_fallback)
        }
        else -> ""
    }

    fun summaryFor(context: Context, e: NotificationEvent): String = when {
        e.isMention -> context.getString(R.string.notification_summary_mention)
        e.fromQms() -> context.getString(R.string.notification_summary_qms)
        e.fromTheme() -> context.getString(R.string.notification_summary_fav)
        e.fromSite() -> context.getString(R.string.notification_summary_comment)
        else -> ""
    }

    fun intentUrlFor(e: NotificationEvent): String = when {
        e.isMention && e.fromTheme() ->
            "https://4pda.to/forum/index.php?showtopic=${e.sourceId}&view=findpost&p=${e.messageId}"
        e.isMention && e.fromSite() && e.sourceId > 0 && e.messageId > 0 ->
            "https://4pda.to/index.php?p=${e.sourceId}/#comment${e.messageId}"
        e.fromQms() -> "https://4pda.to/forum/index.php?act=qms&mid=${e.userId}&t=${e.sourceId}"
        e.fromTheme() -> "https://4pda.to/forum/index.php?showtopic=${e.sourceId}&view=getnewpost"
        else -> "https://4pda.to/forum/index.php?act=mentions"
    }

    private fun ensureChannel(context: Context, channelId: String, channelName: String) {
        val ch = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
    }
}

private fun NotificationEvent.notificationLogCategory(): String = when {
    isMention && fromSite() -> "site"
    isMention -> "mention"
    fromQms() -> "qms"
    fromTheme() -> "favorite"
    else -> "unknown"
}
