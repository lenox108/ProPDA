package forpdateam.ru.forpda.model.data.remote.api.theme

import forpdateam.ru.forpda.entity.remote.theme.TopicActiveReaders

/**
 * Достаёт из HTML страницы темы блок «сейчас эту тему читают» (IPB `topic active users`), который
 * сервер печатает под последним постом. Отдельного запроса не делаем — блок приезжает с той же
 * страницей, что и посты.
 *
 * ВАЖНО: у неавторизованного пользователя блока в разметке НЕТ (проверено на живых страницах
 * 263718 / 1103268 — ни фразы, ни контейнера под AJAX), поэтому для гостя парсер честно отдаёт null,
 * и UI счётчик просто не показывает.
 *
 * Разметка форума за годы менялась (и отличается между скинами), поэтому фразу ищем несколькими
 * шаблонами, а числа считаем по тексту БЕЗ тегов: `<b>3</b> чел. читают эту тему` и
 * `Сейчас эту тему читают: 3` должны разбираться одинаково.
 */
class TopicActiveUsersParser {

    fun parse(response: String): TopicActiveReaders? {
        if (response.isBlank()) return null
        // Дешёвый отсев: на подавляющем большинстве страниц (гость, старые скины) блока нет, и гонять
        // по 300-килобайтному HTML три регулярки со свободными разделителями незачем.
        if (!response.contains("эту", ignoreCase = true)) return null
        if (!response.contains("читают", ignoreCase = true) &&
                !response.contains("просматрива", ignoreCase = true)) return null

        val match = PHRASES.firstNotNullOfOrNull { it.find(response) } ?: return null
        val block = blockAround(response, match.range.first, match.range.last)
        val text = stripTags(block)

        val phraseTotal = match.groupValues.drop(1).firstNotNullOfOrNull { it.toIntOrNull() } ?: 0
        val guests = reGuests.find(text)?.firstNumber() ?: 0
        val hidden = reHidden.find(text)?.firstNumber() ?: 0
        val members = parseMembers(block)

        val total = if (phraseTotal > 0) phraseTotal else members.size + guests + hidden
        if (total <= 0) return null
        return TopicActiveReaders(total = total, members = members, guests = guests, hidden = hidden)
    }

    /**
     * Окно вокруг найденной фразы: список ников идёт СРАЗУ за ней, но за пределами блока начинаются
     * посторонние ссылки на профили (подписи, футер), поэтому режем по ближайшему закрытию контейнера
     * после фразы и не даём окну разрастись.
     */
    private fun blockAround(response: String, phraseStart: Int, phraseEnd: Int): String {
        val from = (phraseStart - LOOKBEHIND).coerceAtLeast(0)
        val hardTo = (phraseEnd + LOOKAHEAD).coerceAtMost(response.length)
        val tailStart = (phraseEnd + 1).coerceAtMost(hardTo)
        val closing = CLOSERS
                .map { response.indexOf(it, tailStart, ignoreCase = true) }
                .filter { it in tailStart until hardTo }
                .minOrNull()
        return response.substring(from, closing ?: hardTo)
    }

    private fun parseMembers(block: String): List<TopicActiveReaders.Member> {
        val seen = LinkedHashMap<Int, TopicActiveReaders.Member>()
        for (m in reMemberLink.findAll(block)) {
            val id = m.groupValues[1].toIntOrNull() ?: continue
            val nick = decodeEntities(stripTags(m.groupValues[2])).trim()
            if (nick.isEmpty()) continue
            seen.getOrPut(id) { TopicActiveReaders.Member(id, nick) }
        }
        return seen.values.toList()
    }

    private fun MatchResult.firstNumber(): Int? =
            groupValues.drop(1).firstNotNullOfOrNull { it.toIntOrNull() }

    private fun stripTags(value: String): String = value
            .replace(reTag, " ")
            .replace("&nbsp;", " ")
            .replace("&#160;", " ")
            .replace(reSpaces, " ")
            .trim()

    /**
     * Ники приходят как есть из HTML, а форум экранирует апострофы и кавычки — без декода в списке
     * читателей висело «I&#39;m legends» (замечено живьём). Декодируем сами, без android.text.Html:
     * парсер обязан оставаться чистым Kotlin, иначе его не прогнать юнит-тестом.
     */
    private fun decodeEntities(value: String): String {
        if (!value.contains('&')) return value
        return reNumericEntity.replace(value) { m ->
            val raw = m.groupValues[1]
            val code = if (raw.startsWith("x", ignoreCase = true)) {
                raw.drop(1).toIntOrNull(16)
            } else {
                raw.toIntOrNull()
            }
            code?.takeIf { it in 1..0x10FFFF }?.let { String(Character.toChars(it)) } ?: m.value
        }
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
    }

    companion object {
        private const val LOOKBEHIND = 200

        /**
         * Окно поиска ников после фразы. Щедрое намеренно: одна ссылка на профиль в разметке — под
         * сотню символов, так что у популярной темы со 100+ читателями коротким окном список молча
         * обрезался бы (число в шапке при этом осталось бы верным — и расхождение было бы незаметным).
         * Реально окно почти всегда закрывается раньше по [CLOSERS] — блок стоит перед подвалом.
         */
        private const val LOOKAHEAD = 24000

        /**
         * Чем ограничиваем окно поиска ников. НЕ по первому `</div>`: живая разметка 4pda держит фразу
         * и список читателей в РАЗНЫХ дивах одного `borderwrap`
         * (`<div class="formsubtitle"><b>11</b> чел. читают эту тему (…)</div>` +
         * `<div class="row1">Пользователей: <b>7</b> <a …showuser=ID><span>ник</span></a>…</div>`),
         * так что обрез по `</div>` съедал бы весь список.
         */
        private val CLOSERS = listOf("</table>", "</tbody>", "id=\"gfooter\"", "class=\"copyright\"")

        /**
         * ВНИМАНИЕ: `\w` в java.util.regex по умолчанию ASCII-only и кириллицу НЕ ловит («гост\w*» не
         * совпадает с «гостей»), а `(?i)` без `u` не приводит регистр кириллицы. Поэтому везде явные
         * классы `[а-яё]` и флаг `u`.
         */
        private const val CYR = """[а-яё]"""
        private const val GAP = """(?:\s|&nbsp;|&#160;|</?[^>]+>)*"""

        /**
         * Порядок важен: сначала «N чел. читают эту тему» (классический IPB-скин 4pda), затем
         * «Сейчас эту тему читают: N» и вариант с «просматривают». Число может быть завёрнуто в теги,
         * поэтому между ним и словами допускаем разметку и `&nbsp;`.
         */
        private val PHRASES = listOf(
                Regex("""(?isu)(\d+)$GAP(?:чел\.?|человека?|пользовател$CYR+)$GAP(?:сейчас$GAP)?(?:читают?|просматрива$CYR+)${GAP}эту${GAP}тему"""),
                Regex("""(?isu)сейчас${GAP}эту${GAP}тему$GAP(?:читают?|просматрива$CYR+)(?:\s|&nbsp;|&#160;|</?[^>]+>|:)*(\d+)"""),
                Regex("""(?isu)эту${GAP}тему$GAP(?:читают?|просматрива$CYR+)(?:\s|&nbsp;|&#160;|</?[^>]+>|:)*(\d+)"""),
                // Фраза вообще без числа («Эту тему просматривают: ник, ник»): считаем сами по списку.
                Regex("""(?isu)эту${GAP}тему$GAP(?:читают?|просматрива$CYR+)"""),
        )
        private val reGuests = Regex("""(?iu)гост$CYR*\s*:?\s*(\d+)|(\d+)\s*гост$CYR*""")
        private val reHidden = Regex("""(?iu)скрыт$CYR*\s*(?:пользовател$CYR*)?\s*:?\s*(\d+)|(\d+)\s*скрыт$CYR*""")
        private val reMemberLink = Regex("""(?is)<a[^>]*showuser=(\d+)[^>]*>(.*?)</a>""")
        private val reTag = Regex("""<[^>]*>""")
        private val reNumericEntity = Regex("""&#(x?[0-9a-fA-F]+);""")
        private val reSpaces = Regex("""\s+""")
    }
}
