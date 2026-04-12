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
            || document.querySelector('[data-post="' + pid + '"] .post_body')
            || document.querySelector('#post-main-' + pid)
            || document.querySelector('[name="entry' + pid + '"] .post_body');
          if(!el) return null;
          var wrap = el.cloneNode(true);
          function removeByClass(cls) {
            var nodes = wrap.getElementsByClassName(cls);
            while (nodes.length > 0) { try { nodes[0].remove(); } catch (e) {} }
          }
          removeByClass('attachments');
          removeByClass('btns_container');
          var rm = wrap.querySelectorAll('.attach_block, a.attach_block, a.attach.file, a.attach.picture');
          for (var i = 0; i < rm.length; i++) { try { rm[i].remove(); } catch (e) {} }
          var legacy = wrap.querySelectorAll('a.ipb-attach, table[id*="ipb-attach"], table[id*="ipb_attach"]');
          for (var j = 0; j < legacy.length; j++) { try { legacy[j].remove(); } catch (e2) {} }
          var spoils = wrap.querySelectorAll('div.post-block.spoil, div[class*="post-block"][class*="spoil"]');
          for (var k = spoils.length - 1; k >= 0; k--) {
            var bt = spoils[k].querySelector('.block-title');
            var tx = bt ? bt.textContent : '';
            var low = (tx || '').toLowerCase();
            if (tx.indexOf('Прикреплен') >= 0 || low.indexOf('attached file') >= 0) {
              try { spoils[k].remove(); } catch (e3) {}
            }
          }
          var html = wrap.innerHTML;
          if (!html || !String(html).trim()) html = wrap.innerText || '';
          return JSON.stringify(String(html));
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
