package forpdateam.ru.forpda.entity.remote.events

/**
 * Created by radiationx on 29.07.17.
 */

data class NotificationEvent @JvmOverloads constructor(
        var type: Type,
        var source: Source,

        var messageId: Int = 0,

        var sourceId: Int = 0,
        var userId: Int = 0,

        var timeStamp: Long = 0,
        var lastTimeStamp: Long = 0,

        var msgCount: Int = 0,
        var isImportant: Boolean = false,

        var sourceTitle: String = "",
        var userNick: String = "",

        var sourceEventText: String? = null
) {


    /*
    * short
    * */

    val isNew: Boolean
        get() = NotificationEvent.isNew(type)

    val isRead: Boolean
        get() = NotificationEvent.isRead(type)

    val isMention: Boolean
        get() = NotificationEvent.isMention(type)


    enum class Type(val value: Int) {
        NEW(2),
        READ(4),
        MENTION(8),
        HAT_EDITED(16)
    }

    enum class Source(val value: Int) {
        THEME(32),
        SITE(64),
        QMS(128)
    }

    fun fromTheme(): Boolean {
        return NotificationEvent.fromTheme(source)
    }

    fun fromSite(): Boolean {
        return NotificationEvent.fromSite(source)
    }

    fun fromQms(): Boolean {
        return NotificationEvent.fromQms(source)
    }

    /**
     * ID уведомления в шторке и ключ истории событий.
     *
     * Упаковка бит-в-бит, а не хеш: [sourceId] в старших 27 битах, [source] и [type] —
     * в младших. Значит два события совпадают по ID тогда и только тогда, когда совпадают
     * все три поля (при sourceId < 2^27, чего с запасом хватает на ID тем 4PDA).
     * Результат всегда неотрицателен — отрицательные ID заняты служебными уведомлениями
     * (stacked QMS/избранное, foreground-канал).
     */
    @JvmOverloads
    fun notifyId(type: Type? = this.type): Int {
        val actualType = type ?: this.type
        val id = sourceId and SOURCE_ID_MASK
        return (id shl 4) or (source.ordinal shl 2) or actualType.ordinal
    }

    companion object {
        /** 27 бит под sourceId: 4 младших бита уходят под source.ordinal и type.ordinal. */
        private const val SOURCE_ID_MASK = 0x07FFFFFF

        const val SRC_EVENT_NEW = 1
        const val SRC_EVENT_READ = 2
        const val SRC_EVENT_MENTION = 3
        const val SRC_EVENT_HAT_EDITED = 4
        const val SRC_TYPE_SITE = "s"
        const val SRC_TYPE_THEME = "t"
        const val SRC_TYPE_QMS = "q"


        fun isNew(type: Type?): Boolean {
            return type != null && type == Type.NEW
        }

        fun isRead(type: Type?): Boolean {
            return type != null && type == Type.READ
        }

        fun isMention(type: Type?): Boolean {
            return type != null && type == Type.MENTION
        }

        fun fromTheme(source: Source?): Boolean {
            return source != null && source == Source.THEME
        }

        fun fromSite(source: Source?): Boolean {
            return source != null && source == Source.SITE
        }

        fun fromQms(source: Source?): Boolean {
            return source != null && source == Source.QMS
        }
    }
}
