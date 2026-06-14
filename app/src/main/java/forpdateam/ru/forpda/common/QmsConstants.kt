package forpdateam.ru.forpda.common

/**
 * Константы для QMS (Quick Message System)
 */
object QmsConstants {
    
    /**
     * Режимы чата QMS
     */
    object ChatMode {
        const val CHAT = "chat"
        const val CREATING = "creating"
    }
    
    /**
     * Ключи аргументов для QMS навигации
     */
    object Args {
        const val USER_ID = "themesUserId"
        const val AVATAR_URL = "avatarUrl"
    }
}
