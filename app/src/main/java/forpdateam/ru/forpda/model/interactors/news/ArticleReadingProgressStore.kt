package forpdateam.ru.forpda.model.interactors.news

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.roundToInt

/**
 * Persists news article WebView scroll percent per article id.
 */
class ArticleReadingProgressStore(context: Context) {

    private val preferences: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveScrollPercent(articleId: Int, percent: Int) {
        if (articleId <= 0) return
        val clamped = percent.coerceIn(0, 100)
        if (clamped <= 0) {
            preferences.edit().remove(key(articleId)).apply()
            return
        }
        preferences.edit().putInt(key(articleId), clamped).apply()
    }

    fun readScrollPercent(articleId: Int): Int {
        if (articleId <= 0) return 0
        return preferences.getInt(key(articleId), 0).coerceIn(0, 100)
    }

    fun clear(articleId: Int) {
        if (articleId <= 0) return
        preferences.edit().remove(key(articleId)).apply()
    }

    companion object {
        private const val PREFS_NAME = "article_reading_progress"
        private const val KEY_PREFIX = "article.scroll."

        private fun key(articleId: Int): String = KEY_PREFIX + articleId

        fun scrollPercentFrom(scrollY: Int, maxScroll: Int): Int {
            if (maxScroll <= 0 || scrollY <= 0) return 0
            return ((scrollY.toFloat() / maxScroll.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
        }
    }
}
