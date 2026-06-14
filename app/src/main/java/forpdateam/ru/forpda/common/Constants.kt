package forpdateam.ru.forpda.common

/**
 * Общие константы приложения
 */
object Constants {
    
    /**
     * Ключи аргументов для навигации
     */
    object Args {
        const val TITLE = "arg_title"
        const val SUBTITLE = "arg_subtitle"
        const val ANNOUNCE_ID = "announceId"
        const val FORUM_ID = "forumId"
    }
    
    /**
     * Теги для логирования
     */
    object LogTags {
        const val LINK_HANDLER = "LinkHandler"
        const val CHECKER = "ForPDA.Checker"
        const val EDIT_POST = "ForPDA.EditPost"
    }
    
    /**
     * Таймауты (в миллисекундах)
     */
    object Timeouts {
        const val EDIT_LOAD_SAFETY = 72_000L
    }
    
    /**
     * Коды результатов для Cicerone
     */
    object ResultCodes {
        const val EDIT_POST_SYNC = "forpda.theme.EDIT_POST_SYNC"
        const val EDIT_POST_PAGE = "forpda.theme.EDIT_POST_PAGE"
    }
}
