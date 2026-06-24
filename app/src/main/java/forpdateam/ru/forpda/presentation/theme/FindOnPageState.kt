package forpdateam.ru.forpda.presentation.theme

/**
 * Чистая логика состояния «поиска на странице» (find on page) для WebView.
 *
 * Главная причина, по которой поиск раньше «не находил/не подсвечивал»: вызывался
 * [android.webkit.WebView.findAllAsync], но без [android.webkit.WebView.FindListener]
 * результат подсветки на многих устройствах не применяется, а [android.webkit.WebView.findNext]
 * вызывался даже когда активного запроса ещё не было. Этот класс хранит активный запрос и
 * число совпадений, чтобы:
 *  - запускать [android.webkit.WebView.findAllAsync] только на непустой строке,
 *  - разрешать переход prev/next ([android.webkit.WebView.findNext]) лишь после успешного поиска
 *    с найденными совпадениями,
 *  - корректно очищать подсветку при пустой строке/закрытии панели.
 *
 * Класс не зависит от Android и потому покрывается обычными юнит-тестами.
 */
class FindOnPageState {

    /** Текущий активный (непустой) поисковый запрос, либо null если поиск не идёт. */
    var activeQuery: String? = null
        private set

    /** Число найденных совпадений по последнему завершённому поиску. */
    var matchCount: Int = 0
        private set

    /** Индекс текущего активного совпадения (0-based), либо -1 если совпадений нет. */
    var activeMatchIndex: Int = -1
        private set

    /** true, если есть активный запрос с хотя бы одним совпадением и навигация prev/next имеет смысл. */
    val hasMatches: Boolean
        get() = activeQuery != null && matchCount > 0

    /**
     * Решение по новому тексту в строке поиска.
     * @return [Decision.Clear] если строка пустая (нужно очистить подсветку),
     *         [Decision.Find] с обрезанным запросом если нужно запустить поиск.
     */
    fun onTextChanged(rawText: String): Decision {
        val query = rawText.trim()
        if (query.isEmpty()) {
            reset()
            return Decision.Clear
        }
        activeQuery = query
        // Счётчики обновятся в onFindResult; до этого считаем результат неизвестным.
        matchCount = 0
        activeMatchIndex = -1
        return Decision.Find(query)
    }

    /**
     * Можно ли выполнять переход к следующему/предыдущему совпадению.
     * findNext имеет смысл только после успешного findAllAsync с совпадениями.
     */
    fun canFindNext(): Boolean = hasMatches

    /** Обновляет состояние по результату нативного FindListener.onFindResultReceived. */
    fun onFindResult(activeMatchOrdinal: Int, numberOfMatches: Int) {
        matchCount = numberOfMatches.coerceAtLeast(0)
        activeMatchIndex = if (numberOfMatches > 0) activeMatchOrdinal else -1
    }

    /** Сбрасывает состояние (пустая строка/закрытие панели/очистка). */
    fun reset() {
        activeQuery = null
        matchCount = 0
        activeMatchIndex = -1
    }

    sealed class Decision {
        /** Очистить подсветку (clearMatches). */
        object Clear : Decision()

        /** Запустить поиск findAllAsync с этим (непустым, обрезанным) запросом. */
        data class Find(val query: String) : Decision()
    }
}
