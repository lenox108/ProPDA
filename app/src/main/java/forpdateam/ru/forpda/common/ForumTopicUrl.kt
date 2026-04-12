package forpdateam.ru.forpda.common

import android.net.Uri

/**
 * Нормализация URL темы при открытии из приложения:
 * — «голая» ссылка на тему → [view=getnewpost] (как «перейти к непрочитанному» на сайте);
 * — [showtopic…&p=] без [view=] → [view=findpost] (позиционирование на пост);
 * — [st] только при ненулевом смещении страницы: [st=0] не отменяет getnewpost (часто встречается в ссылках).
 */
fun topicUrlWithUnreadIfPlainOpen(uri: Uri): String {
    if (uri.getQueryParameter("showtopic").isNullOrEmpty()) return uri.toString()
    if (uri.getQueryParameter("act") == "findpost") return uri.toString()
    val hasPostId = !uri.getQueryParameter("p").isNullOrEmpty() || !uri.getQueryParameter("pid").isNullOrEmpty()
    if (hasPostId && uri.getQueryParameter("view").isNullOrEmpty()) {
        return uri.buildUpon().appendQueryParameter("view", "findpost").build().toString()
    }
    if (!uri.getQueryParameter("view").isNullOrEmpty()) return uri.toString()
    val st = uri.getQueryParameter("st")
    if (isNonZeroTopicPageOffset(st)) return uri.toString()
    if (!uri.getQueryParameter("anchor").isNullOrEmpty()) return uri.toString()
    if (!uri.fragment.isNullOrEmpty()) return uri.toString()
    return uri.buildUpon()
            .appendQueryParameter("view", "getnewpost")
            .build()
            .toString()
}

private fun isNonZeroTopicPageOffset(st: String?): Boolean {
    val s = st?.trim().orEmpty()
    if (s.isEmpty()) return false
    return s.toIntOrNull()?.let { it != 0 } ?: true
}

/**
 * Для строкового URL темы: [st] считается «настоящей» пагинацией только если задано ненулевое число
 * (как в [topicUrlWithUnreadIfPlainOpen]).
 */
fun topicUrlHasNonZeroStParameter(url: String): Boolean {
    val st = try {
        Uri.parse(url).getQueryParameter("st")
    } catch (_: Exception) {
        null
    }
    return isNonZeroTopicPageOffset(st)
}
