package forpdateam.ru.forpda.common.webview

import android.webkit.WebView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Coroutine-based wrapper for WebView.evaluateJavascript
 * Returns the result as a suspend String
 */
suspend fun WebView.evaluateJs(script: String): String = suspendCancellableCoroutine { continuation ->
    evaluateJavascript(script) { result ->
        if (continuation.isActive) {
            continuation.resume(result)
        }
    }
}
