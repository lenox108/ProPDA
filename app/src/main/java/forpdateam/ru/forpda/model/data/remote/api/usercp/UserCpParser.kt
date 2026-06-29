package forpdateam.ru.forpda.model.data.remote.api.usercp

import forpdateam.ru.forpda.entity.remote.usercp.ForumSettings
import java.util.regex.Pattern

/**
 * Снимает текущее состояние формы «Настройки форума» из HTML страницы act=UserCP.
 *
 * Парсер самодостаточный (без удалённых паттернов): имена полей формы — стабильные
 * строковые ключи IPB, поэтому регэкспы привязаны прямо к ним. Любое поле, которое
 * не удалось распознать, остаётся со значением по умолчанию из [ForumSettings].
 */
class UserCpParser {

    fun parse(html: String): ForumSettings {
        val defaults = ForumSettings()
        return ForumSettings(
                tzAutoset = isChecked(html, "tz-autoset", defaults.tzAutoset),
                timeOffset = selectedValue(html, "time-offset", defaults.timeOffset),
                dstInUse = isChecked(html, "dst-in-use", defaults.dstInUse),
                viewSigs = isChecked(html, "view-sigs", defaults.viewSigs),
                viewImg = isChecked(html, "view-img", defaults.viewImg),
                viewAvs = isChecked(html, "view-avs", defaults.viewAvs),
                ucpShowQrCode = isChecked(html, "ucp-show-qr-code", defaults.ucpShowQrCode),
                topicPage = selectedValue(html, "topicpage", defaults.topicPage),
                postPage = selectedValue(html, "postpage", defaults.postPage),
                mentionNotify = isChecked(html, "mention-notify", defaults.mentionNotify),
                sendFullMsg = isChecked(html, "send-full-msg", defaults.sendFullMsg),
                autoTrack = isChecked(html, "auto-track", defaults.autoTrack),
                trackChoice = selectedValue(html, "trackchoice", defaults.trackChoice),
                qrOpen = isChecked(html, "qr-open", defaults.qrOpen),
                repNotify = isChecked(html, "rep-notify", defaults.repNotify)
        )
    }

    /**
     * @return true, если хотя бы один `<input name="...">` имеет атрибут `checked`.
     * Перебираем все теги с этим именем (а не только первый) на случай скрытого
     * input-компаньона IPB с тем же именем. Если поля на странице нет вообще
     * (не залогинен / редизайн) — возвращается [fallback].
     */
    private fun isChecked(html: String, name: String, fallback: Boolean): Boolean {
        val matcher = Pattern
                .compile("(?i)<input\\b[^>]*\\bname\\s*=\\s*[\"']" + Pattern.quote(name) + "[\"'][^>]*>")
                .matcher(html)
        var found = false
        val checkedPattern = Pattern.compile("(?i)\\bchecked\\b")
        while (matcher.find()) {
            found = true
            if (checkedPattern.matcher(matcher.group()).find()) return true
        }
        return if (found) false else fallback
    }

    /**
     * @return value выбранного `<option selected>` внутри `<select name="...">`;
     * при отсутствии select/выбранной опции возвращается [fallback].
     */
    private fun selectedValue(html: String, name: String, fallback: String): String {
        val block = Pattern
                .compile("(?i)<select\\b[^>]*\\bname\\s*=\\s*[\"']" + Pattern.quote(name) + "[\"'][^>]*>([\\s\\S]*?)</select>")
                .matcher(html)
        if (!block.find()) return fallback
        val options = block.group(1) ?: return fallback

        val optionMatcher = Pattern.compile("(?i)<option\\b([^>]*)>").matcher(options)
        while (optionMatcher.find()) {
            val attrs = optionMatcher.group(1) ?: continue
            if (!Pattern.compile("(?i)\\bselected\\b").matcher(attrs).find()) continue
            val value = extractAttr(attrs, "value")
            if (value != null) return value
        }
        return fallback
    }

    private fun extractAttr(attrs: String, attr: String): String? {
        val matcher = Pattern
                .compile("(?i)\\b" + Pattern.quote(attr) + "\\s*=\\s*[\"']([^\"']*)[\"']")
                .matcher(attrs)
        return if (matcher.find()) matcher.group(1) else null
    }
}
