package forpdateam.ru.forpda.common

import android.os.Handler
import android.os.Looper
import forpdateam.ru.forpda.ui.views.ExtendedWebView
import org.json.JSONTokener

/**
 * Берёт HTML (или текст) тела поста из уже отрисованной страницы темы/поиска.
 * Возвращается JSON.stringify(innerHTML) — без base64, корректно для Unicode и кавычек.
 */
fun ExtendedWebView.extractPostBodyHtml(postId: Int, onResult: (String?) -> Unit) {
    val pid = postId.toString()
    val script = """
        (function(){
          var pid = '$pid';
          var el = document.querySelector('.post_container[data-post-id="' + pid + '"] .post_body')
            || document.querySelector('[data-post-id="' + pid + '"] .post_body')
            || document.querySelector('[name="entry' + pid + '"] .post_body');
          if(!el) return null;
          var html = el.innerHTML;
          if(!html || !String(html).trim()) html = el.innerText || '';
          return JSON.stringify(html);
        })()
    """.trimIndent()
    evalJs(script) { jsonResult ->
        val html = decodeJsJsonString(jsonResult)
        Handler(Looper.getMainLooper()).post {
            onResult(html)
        }
    }
}

private fun decodeJsJsonString(jsonResult: String?): String? {
    if (jsonResult.isNullOrBlank() || jsonResult == "null") return null
    return try {
        JSONTokener(jsonResult).nextValue() as? String
    } catch (e: Exception) {
        null
    }
}
