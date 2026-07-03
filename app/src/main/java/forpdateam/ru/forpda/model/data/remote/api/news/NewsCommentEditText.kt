package forpdateam.ru.forpda.model.data.remote.api.news

// Тот же паттерн, что commentEditedWrapperRegex в ArticleParser (маркер «(отредактирован …)» /
// «(message edited by …)»). Вынесен для переиспользования в форме ПРАВКИ коммента: текст для
// редактора берётся из editableHtml / поля серверной формы, а там маркер остаётся — и при повторной
// правке «(отредактирован)» подставлялся в редактор и повторно уходил на сервер (запекался в контент).
private val newsCommentEditedMarkerRegex = Regex(
        """(?is)(?:&nbsp;|\s|<br\s*/?>)*\((?:&nbsp;|\s|<br\s*/?>|<[^>]+>)*(?:сообщение\s+)?""" +
                """(?:отредактирован[а-я]*|(?:message\s+)?edited\s+by\b)[\s\S]{0,300}?\)(?:&nbsp;|\s|<br\s*/?>)*"""
)

/**
 * Убирает хвост-маркер «(отредактирован …)» из текста, который подставляется в форму правки коммента
 * к новости, чтобы пользователь не видел и повторно не отправлял его. Отображение коммента маркер
 * уже срезает отдельно (иконка-карандаш ✎).
 */
internal fun stripNewsCommentEditedMarker(text: String): String =
        newsCommentEditedMarkerRegex.replace(text, "").trim()
