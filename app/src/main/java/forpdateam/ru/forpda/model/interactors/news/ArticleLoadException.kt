package forpdateam.ru.forpda.model.interactors.news

/**
 * User-visible article load failure (message is already localized for UI/toast).
 */
class ArticleLoadException(
        message: String = "Не удалось загрузить новость"
) : Exception(message)
