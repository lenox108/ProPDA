package forpdateam.ru.forpda.entity.remote.sitecontent

/**
 * Один комментарий пользователя со страницы `https://4pda.to/<ник>/comments/`.
 * [articleId] нужен для нативного открытия статьи (Screen.ArticleDetail).
 */
data class SiteComment(
        val articleId: Int,
        val articleTitle: String,
        val articleUrl: String,
        val snippet: String,
        val date: String?,
        val nick: String?,
)
