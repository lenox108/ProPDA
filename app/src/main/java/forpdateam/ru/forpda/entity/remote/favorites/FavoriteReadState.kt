package forpdateam.ru.forpda.entity.remote.favorites

/**
 * Parsed / merged read state for a favorite topic row.
 *
 * [UNKNOWN] must not be treated as read in cache merge — only [READ] is a confident read.
 */
enum class FavoriteReadState {
    READ,
    UNREAD,
    UNKNOWN;

    fun isUnread(): Boolean = this == UNREAD

    companion object {
        const val STORAGE_UNKNOWN = 0
        const val STORAGE_READ = 1
        const val STORAGE_UNREAD = 2

        fun fromStorage(value: Int): FavoriteReadState = when (value) {
            STORAGE_READ -> READ
            STORAGE_UNREAD -> UNREAD
            else -> UNKNOWN
        }

        fun FavoriteReadState.toStorage(): Int = when (this) {
            READ -> STORAGE_READ
            UNREAD -> STORAGE_UNREAD
            UNKNOWN -> STORAGE_UNKNOWN
        }
    }
}
