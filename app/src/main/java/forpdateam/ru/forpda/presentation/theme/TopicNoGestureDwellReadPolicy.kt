package forpdateam.ru.forpda.presentation.theme

/**
 * Разрыв цикла «открыл-глянул-закрыл»: чтение БЕЗ единого жеста скролла — тоже чтение, если весь
 * остаток темы был целиком виден и пользователь задержался на экране (модель Discourse: viewport+dwell).
 *
 * Контекст: посадка на первый непрочитанный штампует границу на якорный пост, а гейт
 * `suppressEndMarkReadUntilUserScroll` (снимается только жестом) давит мгновенный mark-read. Если новые
 * посты целиком влезли в экран и юзер прочитал их, не скролля, то на выходе не происходило НИЧЕГО:
 * граница оставалась на якоре, тема — жирной, а сервер (пометивший страницу прочитанной самим GET)
 * при переоткрытии отдавал all-read-редирект, который резюм перебивал findpost'ом на ту же границу —
 * каждое открытие «перекидывало на старые посты» до первого жеста.
 *
 * Решение: на выходе из темы ([NativeTopicFragment.onPause]) сессия без жеста считается дочиткой, когда
 * ВСЕ условия выполнены — тогда граница пишется по вьюпорту и mark-read-в-конце разрешается. Порог
 * [MIN_DWELL_MS] отсекает случайное «открыл и тут же закрыл» — оно по-прежнему ничего не сжигает.
 */
object TopicNoGestureDwellReadPolicy {

    /** Минимальное время сессии на экране, после которого целиком видимый остаток темы считается прочитанным. */
    const val MIN_DWELL_MS = 4_000L

    /**
     * @param suppressEndMarkReadActive гейт мгновенного mark-read всё ещё взведён — т.е. это была
     *        unread-посадка (или findpost-резюм на границу) и жеста не было. Для остальных сессий
     *        (READ_RESUME / all-read) разруливать нечего.
     * @param hadUserGesture был жест скролла — границу уже пишет обычный IDLE/onPause-путь.
     * @param dwellMs сколько сессия провела на экране с момента рендера.
     * @param hasNextPage ниже есть незагруженные страницы — видимый вьюпорт не «весь остаток темы».
     * @param lastItemFullyVisible последний элемент последней страницы был ЦЕЛИКОМ виден на выходе.
     */
    fun shouldTreatVisibleTailAsRead(
            suppressEndMarkReadActive: Boolean,
            hadUserGesture: Boolean,
            dwellMs: Long,
            hasNextPage: Boolean,
            lastItemFullyVisible: Boolean,
            minDwellMs: Long = MIN_DWELL_MS,
    ): Boolean {
        if (hadUserGesture) return false
        if (!suppressEndMarkReadActive) return false
        if (hasNextPage) return false
        if (!lastItemFullyVisible) return false
        return dwellMs >= minDwellMs
    }
}
