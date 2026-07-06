package forpdateam.ru.forpda.presentation.theme

/**
 * Решает, надо ли при открытии темы «удержать» якорь на клиентской границе прочитанного вместо
 * серверного getnewpost-таргета.
 *
 * Контекст: 4PDA/IPB (как и XenForo) метит ВСЮ загруженную страницу прочитанной по факту загрузки,
 * поэтому серверный getnewpost неизбежно уезжает вниз (walk-down) и при переоткрытии сажает якорь
 * НИЖЕ реально непрочитанных постов. [TopicReadBoundaryStore] ведёт точную клиентскую границу —
 * самый дальний пост, который пользователь реально видел. Здесь мы сравниваем, куда сядет сервер,
 * с этой границей, и если сервер увёл бы НИЖЕ границы (т.е. пропустил бы не-виденные посты) —
 * возвращаем id границы, чтобы перезагрузиться туда через findpost.
 *
 * id постов на 4PDA глобально возрастают (больше id = позже опубликован), поэтому «ниже/новее» =
 * «больше id».
 */
object TopicReadBoundaryPolicy {

    /**
     * @param boundaryPostId самый дальний реально-виденный пост (0 = границы нет).
     * @param serverAnchorPostId пост, на который сервер разрешил якорь (null при ambiguous all-read).
     * @param lastLoadedPostId последний пост загруженного окна (fallback для ambiguous all-read,
     *        когда сервер «резюмит на низ»).
     * @return id поста для findpost-резюма (== boundary), либо null — если переопределять не нужно
     *         (границы нет, или сервер сел бы НЕ ниже границы).
     */
    fun resumeAnchorPostId(
        boundaryPostId: Int,
        serverAnchorPostId: Int?,
        lastLoadedPostId: Int?,
    ): Int? {
        if (boundaryPostId <= 0) return null
        val serverLandingId = when {
            serverAnchorPostId != null && serverAnchorPostId > 0 -> serverAnchorPostId
            lastLoadedPostId != null && lastLoadedPostId > 0 -> lastLoadedPostId
            else -> return null
        }
        // Переопределяем только если сервер сел бы СТРОГО НИЖЕ (новее) границы — тогда между границей
        // и серверным таргетом есть не-виденные посты, которые иначе будут пропущены.
        return if (serverLandingId > boundaryPostId) boundaryPostId else null
    }
}
