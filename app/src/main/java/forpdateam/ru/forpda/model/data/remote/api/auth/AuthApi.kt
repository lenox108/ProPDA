package forpdateam.ru.forpda.model.data.remote.api.auth

import android.content.Context
import androidx.preference.PreferenceManager
import forpdateam.ru.forpda.entity.remote.auth.AuthForm
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.api.ApiUtils
import forpdateam.ru.forpda.model.data.remote.api.NetworkRequest
import forpdateam.ru.forpda.common.Cp1251Codec
import forpdateam.ru.forpda.common.SecureCookiesPreferences
import java.util.regex.Pattern

/**
 * Created by radiationx on 25.03.17.
 */

class AuthApi(
        private val context: Context,
        private val webClient: IWebClient,
        private val authParser: AuthParser
) {

    fun getForm(): AuthForm {
        val response = webClient.get(AUTH_BASE_URL)

        if (response.body.isNullOrEmpty())
            throw Exception("Page empty!")

        if (checkLogin(response.body))
            throw Exception("You already logged")

        return authParser.parseForm(response.body)
    }

    fun login(form: AuthForm): AuthForm {
        val builder = NetworkRequest.Builder()
                .url(AUTH_BASE_URL)
                .formHeader("captcha-time", form.captchaTime ?: "")
                .formHeader("captcha-sig", form.captchaSig ?: "")
                .formHeader("captcha", form.captcha ?: "")
                .formHeader("return", IWebClient.MINIMAL_PAGE)
                .formHeader("login", Cp1251Codec.encode(form.nick), true)
                .formHeader("password", Cp1251Codec.encode(form.password), true)
                .formHeader("remember", "1")
                .formHeader("hidden", if (form.isHidden) "1" else "0")

        val response = webClient.request(builder.build())
        val matcher = errorPattern.matcher(response.body)
        if (matcher.find()) {
            throw Exception(ApiUtils.fromHtml(matcher.group(1))?.replace("\\.".toRegex(), ".\n")?.trim() ?: "Ошибка авторизации")
        }
        if (!checkLogin(response.body)) {
            throw Exception("Ошибка при проверке авторизации")
        }
        return form
    }

    fun logout(): Boolean {
        val response = webClient.get("https://4pda.to/forum/index.php?act=logout&CODE=03&k=" + webClient.getAuthKey())

        val matcher = Pattern.compile("wr va-m text").matcher(response.body)
        if (matcher.find())
            throw Exception("You already logout")

        webClient.clearCookies()
        val securePrefs = SecureCookiesPreferences.getInstance(context)
        securePrefs.remove("cookie_member_id")
        securePrefs.remove("cookie_pass_hash")

        return !checkLogin(webClient.get(IWebClient.MINIMAL_PAGE).body)
    }

    private fun checkLogin(response: String): Boolean {
        val matcher = Pattern.compile("<i class=\"icon-profile\">[\\s\\S]*?<ul class=\"dropdown-menu\">[\\s\\S]*?showuser=(\\d+)\"[\\s\\S]*?action=logout[^\"]*?k=([a-z0-9]{32})").matcher(response)
        if (matcher.find()) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putString("auth_key", matcher.group(2)).apply()
            return true
        }
        return false
    }

    companion object {
        val AUTH_BASE_URL = "https://4pda.to/forum/index.php?act=auth"
        private val errorPattern = Pattern.compile("errors-list\">([\\s\\S]*?)</ul>")
    }

}
