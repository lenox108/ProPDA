package forpdateam.ru.forpda.presentation.theme

/**
 * Maps native topic chrome (toolbar + optional pagination) to WebView top inset in px.
 */
internal object TopicTopChromePaddingPolicy {

  fun paddingPxFromWindowGeometry(
      webViewWindowY: Int,
      appBarWindowY: Int,
      appBarHeight: Int,
      paginationWindowY: Int?,
      paginationHeight: Int,
      paginationVisible: Boolean,
  ): Int {
    val chromeBottom = listOfNotNull(
        if (appBarHeight > 0) appBarWindowY + appBarHeight else null,
        if (paginationVisible && paginationHeight > 0 && paginationWindowY != null) {
          paginationWindowY + paginationHeight
        } else {
          null
        },
    ).maxOrNull() ?: 0
    return paddingPxFromChromeBottom(webViewWindowY, chromeBottom)
  }

  fun paddingPxFromChromeBottom(webViewWindowY: Int, chromeBottomWindowY: Int): Int =
      (chromeBottomWindowY - webViewWindowY).coerceAtLeast(0)

  /**
   * Auto-hide moves the app bar via [translationY]; window geometry shrinks and must not shrink
   * WebView top padding (log 578: toolbar flicker while hybrid prepend compensates scroll).
   */
  fun expandedChromeBottomWindowY(
      chromeWindowY: Int,
      chromeHeight: Int,
      translationY: Float,
  ): Int = chromeWindowY + chromeHeight + (-translationY).coerceAtLeast(0f).toInt()
}
