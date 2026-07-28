package forpdateam.ru.forpda.ui.fragments.theme.nativerender

/**
 * Куда должна вести запись in-tab back-истории темы (`NativeTopicFragment.ThemeBackEntry`).
 *
 * Раньше туда клался `loadedUrl` — url ЗАПРОСА, которым тема открывалась. Для открытий из избранного /
 * уведомления / «Первое непрочитанное» это серверный редирект `view=getnewpost` или `view=getlastpost`
 * («сервер, посади меня сам»), да ещё и приколоченный ко ВХОДНОЙ странице: infinite-scroll его не двигает.
 * На «назад» этот редирект резолвился ЗАНОВО — но граница прочитанного к тому моменту уже уехала (юзер
 * дочитал страницу, да и сам GET метит страницу прочитанной), и сервер отдавал уже ДРУГУЮ страницу.
 * Пост-источник ссылки в неё не попадал, restore-по-посту проваливался, и юзер приземлялся на низ
 * последней страницы, которая тут же засчитывалась прочитанной. Отсюда жалоба «жмёшь назад — не
 * возвращаешься к посту, с которого ушёл, а попадаешь на последнюю страницу темы» и её «через раз»:
 * всё зависело от типа входного url и от того, успела ли граница уехать за входную страницу.
 *
 * Поэтому back-запись целится в ДЕТЕРМИНИРОВАННОЕ место: страницу поста-якоря (чистый `st=`, без
 * серверных якорей и unread-побочек), либо findpost на сам пост, если номер страницы неизвестен.
 */
object ThemeBackEntryUrlPolicy {

    sealed interface Target {
        /** Загрузить 1-based страницу темы (`TopicPaginationController.pageUrl`). */
        data class Page(val pageNumber: Int) : Target

        /** Номер страницы неизвестен — пусть сервер найдёт пост сам. */
        data class FindPost(val topicId: Int, val postId: Int) : Target

        /** Якоря нет и подменять нечем — оставить url как есть. */
        object KeepLoadedUrl : Target
    }

    /**
     * @param loadedUrl url, которым сейчас загружена тема (то, что клалось в back-запись раньше).
     * @param anchorPostId пост, на который «назад» обязан вернуть (источник тапнутой ссылки либо верхний
     *   видимый), 0 — неизвестен.
     * @param anchorPostPage 1-based страница поста-якоря (`NativePostItem.pageNumber`), 0 — неизвестна.
     * @param topicId тема, к которой относится [anchorPostId].
     * @param paginationReady пагинация проинициализирована И относится к [topicId] (иначе `pageUrl`
     *   построит адрес чужой темы).
     * @param loadedPage самая глубокая загруженная страница — запасная цель, когда якоря нет вовсе.
     */
    fun resolve(
            loadedUrl: String,
            anchorPostId: Int,
            anchorPostPage: Int,
            topicId: Int,
            paginationReady: Boolean,
            loadedPage: Int,
    ): Target {
        if (anchorPostId > 0 && topicId > 0) {
            if (anchorPostPage > 0 && paginationReady) return Target.Page(anchorPostPage)
            return Target.FindPost(topicId, anchorPostId)
        }
        // Якоря нет (вью ещё не создана / пустое окно) — хотя бы не оставляем серверный редирект,
        // иначе «назад» снова уедет туда, куда сервер решит посадить в СЛЕДУЮЩИЙ раз.
        if (!isServerAnchoredOpenUrl(loadedUrl)) return Target.KeepLoadedUrl
        if (paginationReady && loadedPage > 0) return Target.Page(loadedPage)
        return Target.KeepLoadedUrl
    }

    /** `view=getnewpost` / `view=getlastpost` — «сервер решает, куда сажать», а не фиксированная страница. */
    fun isServerAnchoredOpenUrl(url: String): Boolean =
            url.contains("getnewpost", ignoreCase = true) || url.contains("getlastpost", ignoreCase = true)
}
