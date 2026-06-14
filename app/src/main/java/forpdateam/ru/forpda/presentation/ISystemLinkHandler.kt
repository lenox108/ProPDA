package forpdateam.ru.forpda.presentation

import android.content.Context

interface ISystemLinkHandler {
    fun handle(url: String)
    fun handleDownload(
            url: String,
            inputFileName: String? = null,
            uiContext: Context? = null,
            contentDisposition: String? = null
    )
}