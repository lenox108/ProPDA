package forpdateam.ru.forpda.model.data.remote.api

/**
 * Created by radiationx on 07.07.17.
 * Converted to Kotlin.
 */
data class NetworkResponse(
    var url: String = "",
    var code: Int = 0,
    var message: String = "",
    var redirect: String = url,
    /** Raw `Location` header from the server response (if any). */
    var locationHeader: String? = null,
    var body: String = "",
    /** Фрагмент (#entry…) из заголовка Location при редиректе — OkHttp его обрезает. */
    var redirectFragment: String? = null
) {
    /**
     * Полный URL редиректа включая фрагмент. Если [redirect] уже содержит '#'
     * (например, OkHttp на этой версии сохранил его), не дублируем фрагмент.
     */
    val redirectWithFragment: String
        get() {
            val frag = redirectFragment ?: return redirect
            return if (redirect.contains('#')) redirect else "$redirect#$frag"
        }

    override fun toString(): String {
        return "NetworkResponse{$code, $message, $url, $redirect, frag=$redirectFragment, ${body.length}}"
    }
}
