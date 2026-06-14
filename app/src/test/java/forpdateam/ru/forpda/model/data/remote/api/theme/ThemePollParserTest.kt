package forpdateam.ru.forpda.model.data.remote.api.theme

import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.regex.Pattern

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemePollParserTest {

    @Test
    fun parsesVoteablePollForm() {
        val page = ThemeParser(loadProductionPatterns()).parsePage(
                response = """
                    <html><body>
                    <form action="/forum/index.php?showtopic=42" method="post">
                        <input type="hidden" name="addpoll" value="1">
                        <input type="hidden" name="auth_key" value="token">
                        <tr><th><span>Questionnaire</span></th></tr>
                        <tr><td><div class="poll_question"><strong>Choose one</strong>
                            <table>
                                <tr><td colspan="2"><input type="radio" name="poll_vote[1]" value="10"> <b>First choice</b></td></tr>
                                <tr><td colspan="2"><input type="radio" name="poll_vote[1]" value="11"> <b>Second choice</b></td></tr>
                            </table>
                        </div></td></tr>
                        <tr><td><b>Всего голосов: 0</b></td></tr>
                        <td class="formbuttonrow"><input type="submit" value="Голосовать"></td>
                    </form>
                    <a name="entry1"></a><div class="post_body">body</div>
                    </body></html>
                """.trimIndent(),
                argUrl = "https://4pda.to/forum/index.php?showtopic=42"
        )

        val poll = page.poll ?: error("Poll was not parsed")
        assertFalse(poll.isResult)
        assertTrue(poll.canVote)
        assertEquals("https://4pda.to/forum/index.php?showtopic=42", poll.formAction)
        assertEquals("post", poll.formMethod)
        assertEquals(listOf("addpoll" to "1", "auth_key" to "token"), poll.hiddenInputs)
        assertEquals("Choose one", poll.questions.single().title)
        assertEquals("radio", poll.questions.single().questionItems.first().type)
        assertEquals("poll_vote[1]", poll.questions.single().questionItems.first().name)
        assertEquals("10", poll.questions.single().questionItems.first().value)
        assertEquals("First choice", poll.questions.single().questionItems.first().title)
    }

    @Test
    fun parsesVoteInputsWhenInputAttributesAreReordered() {
        val page = ThemeParser(loadProductionPatterns()).parsePage(
                response = """
                    <html><body>
                    <form action="/forum/index.php?showtopic=42" method="post">
                        <input name="addpoll" value="1" type="hidden">
                        <tr><th><span>Poll</span></th></tr>
                        <tr><td><div class="poll_question"><strong>Pick</strong>
                            <table>
                                <tr><td colspan="2"><label><input value="7" name="poll_vote[2][]" type="checkbox"> Alpha</label></td></tr>
                            </table>
                        </div></td></tr>
                        <tr><td><b>Всего голосов: 0</b></td></tr>
                        <td class="formbuttonrow"><input type="submit" value="Голосовать"></td>
                    </form>
                    <a name="entry1"></a><div class="post_body">body</div>
                    </body></html>
                """.trimIndent(),
                argUrl = "https://4pda.to/forum/index.php?showtopic=42"
        )

        val item = page.poll?.questions?.single()?.questionItems?.single() ?: error("Poll item was not parsed")
        assertTrue(page.poll?.canVote == true)
        assertEquals("checkbox", item.type)
        assertEquals("poll_vote[2][]", item.name)
        assertEquals("7", item.value)
        assertEquals("Alpha", item.title)
    }

    @Test
    fun parsesRealisticPollFormWithResultsLinkFallback() {
        val page = ThemeParser(loadProductionPatterns()).parsePage(
                response = """
                    <html><body>
                    <form class="topic_poll" action="index.php?showtopic=42" method="post">
                        <input name="addpoll" value="1" type="hidden">
                        <input name="auth_key" value="token" type="hidden">
                        <h3>Опрос</h3>
                        <div class="poll_question">
                            <strong>Что выбрать?</strong>
                            <label class="poll_option">
                                <input value="yes_value" name="poll_vote[1]" type="radio">
                                Первый вариант
                            </label>
                            <label class="poll_option">
                                <input value="no_value" name="poll_vote[1]" type="radio">
                                Второй вариант
                            </label>
                        </div>
                        <button type="submit">Голосовать</button>
                        <a href="index.php?showtopic=42&amp;mode=show">Посмотреть результаты</a>
                    </form>
                    <a name="entry1"></a><div class="post_body">body</div>
                    </body></html>
                """.trimIndent(),
                argUrl = "https://4pda.to/forum/index.php?showtopic=42"
        )

        val poll = page.poll ?: error("Poll was not parsed")
        val item = poll.questions.single().questionItems.first()
        assertTrue(poll.canVote)
        assertTrue(poll.showResultsButton)
        assertEquals("https://4pda.to/forum/index.php?showtopic=42", poll.formAction)
        assertEquals("https://4pda.to/forum/index.php?showtopic=42&mode=show", poll.resultsUrl)
        assertEquals("radio", item.type)
        assertEquals("poll_vote[1]", item.name)
        assertEquals("yes_value", item.value)
        assertEquals("Первый вариант", item.title)
    }

    @Test
    fun `poll form with decorative images remains voteable`() {
        val page = ThemeParser(loadProductionPatterns()).parsePage(
                response = """
                    <html><body>
                    <form action="/forum/index.php?showtopic=42" method="post">
                        <input type="hidden" name="addpoll" value="1">
                        <tr><th><span>Опрос</span></th></tr>
                        <tr><td><div class="poll_question"><strong>Выберите вариант</strong>
                            <table>
                                <tr><td colspan="2"><input type="radio" name="poll_vote[1]" value="1"> <img src="icon.png" alt=""> <b>Первый</b></td></tr>
                                <tr><td colspan="2"><input type="radio" name="poll_vote[1]" value="2"> <b>Второй</b></td></tr>
                            </table>
                        </div></td></tr>
                        <tr><td><b>Всего голосов: 0</b></td></tr>
                        <td class="formbuttonrow"><input type="submit" value="Голосовать"></td>
                    </form>
                    <a name="entry1"></a><div class="post_body">body</div>
                    </body></html>
                """.trimIndent(),
                argUrl = "https://4pda.to/forum/index.php?showtopic=42"
        )

        val poll = page.poll ?: error("Poll was not parsed")
        val firstItem = poll.questions.single().questionItems.first()
        assertFalse(poll.isResult)
        assertTrue(poll.canVote)
        assertEquals("radio", firstItem.type)
        assertEquals("poll_vote[1]", firstItem.name)
        assertEquals("1", firstItem.value)
        assertEquals("Первый", firstItem.title)
    }

    @Test
    fun `guest results poll renders as results not readonly choices`() {
        val page = ThemeParser(loadProductionPatterns()).parsePage(
                response = """
                    <html><body>
                    <form action="/forum/index.php?showtopic=1050118" method="post">
                        <tr><th><span>Работает ли у вас YouTube Revanced?</span></th></tr>
                        <tr><td><div class="poll_question"><strong>Работа ReVanced</strong>
                            <table>
                                <tr><td width="45%">У меня работает ReVanced NonRoot</td><td><b>12883</b> [56.15%]</td></tr>
                                <tr><td width="45%">Купил премиум</td><td><b>571</b> [2.49%]</td></tr>
                            </table>
                        </div></td></tr>
                        <tr><td><b>Всего голосов: 22944</b></td></tr>
                        <td class="formbuttonrow"></td>
                    </form>
                    <a name="entry1"></a><div class="post_body">body</div>
                    </body></html>
                """.trimIndent(),
                argUrl = "https://4pda.to/forum/index.php?showtopic=1050118"
        )

        val poll = page.poll ?: error("Poll was not parsed")
        assertTrue(poll.isResult)
        assertFalse(poll.canVote)
        assertEquals(22944, poll.votesCount)
        assertEquals(56.15f, poll.questions.single().questionItems.first().percent, 0.001f)
    }

    @Test
    fun `revanced guest result table parses all result rows`() {
        val page = ThemeParser(loadProductionPatterns()).parsePage(
                response = """
                    <html><body>
                    <form action="/forum/index.php?showtopic=1050118&amp;st=0" method="post">
                        <tr><th><span>Работа ReVanced</span></th></tr>
                        <tr><td><div class="poll_question"><strong>Работает ли у вас YouTube Revanced?</strong>
                            <table>
                                <tr><td width="45%">У меня работает ReVanced NonRoot</td><td>[ 12883 ]</td><td>[56.15%]</td></tr>
                                <tr><td width="45%">У меня работает ReVanced Root</td><td>[ 1660 ]</td><td>[7.24%]</td></tr>
                                <tr><td width="45%">У меня не работает ReVanced NonRoot и я терпеливо жду исправления (используя временное решение из шапки)</td><td>[ 4128 ]</td><td>[17.99%]</td></tr>
                                <tr><td width="45%">У меня не работает ReVanced Root</td><td>[ 1060 ]</td><td>[4.62%]</td></tr>
                                <tr><td width="45%">У меня не работает ReVanced NonRoot и я перешёл временно на другое приложение (о нем я писать в теме не буду, так как все есть в шапке)</td><td>[ 1797 ]</td><td>[7.83%]</td></tr>
                                <tr><td width="45%">Перешёл навсегда на другое приложение</td><td>[ 845 ]</td><td>[3.68%]</td></tr>
                                <tr><td width="45%">Купил премиум</td><td>[ 571 ]</td><td>[2.49%]</td></tr>
                            </table>
                        </div></td></tr>
                        <tr><td><b>Всего голосов: 22944</b></td></tr>
                        <td class="formbuttonrow"></td>
                    </form>
                    <a name="entry1"></a><div class="post_body">body</div>
                    </body></html>
                """.trimIndent(),
                argUrl = "https://4pda.to/forum/index.php?showtopic=1050118&st=0"
        )

        val poll = page.poll ?: error("Poll was not parsed")
        val items = poll.questions.single().questionItems
        assertTrue(poll.isResult)
        assertFalse(poll.canVote)
        assertEquals(22944, poll.votesCount)
        assertEquals(7, items.size)
        assertEquals("Работает ли у вас YouTube Revanced?", poll.questions.single().title)
        assertEquals("У меня работает ReVanced NonRoot", items.first().title)
        assertEquals(12883, items.first().votes)
        assertEquals(56.15f, items.first().percent, 0.001f)
        assertEquals("Купил премиум", items.last().title)
        assertEquals(571, items.last().votes)
        assertEquals(2.49f, items.last().percent, 0.001f)
    }

    @Test
    fun `authenticated unvoted poll with loose mobile markup renders vote controls`() {
        val page = ThemeParser(loadProductionPatterns()).parsePage(
                response = """
                    <html><body>
                    <form class="topic_poll" action="/forum/index.php?showtopic=1050118&amp;st=0" method="post">
                        <input name="addpoll" value="1" type="hidden">
                        <input name="auth_key" value="secret" type="hidden">
                        <div class="poll_question">
                            <strong>Работает ли у вас YouTube Revanced?</strong>
                            <table>
                                <tr><td><label><input value="1" name="poll_vote[1]" type=radio>У меня работает ReVanced NonRoot</label></td></tr>
                                <tr><td><label><input value="2" name="poll_vote[1]" type=radio>У меня работает ReVanced Root</label></td></tr>
                            </table>
                        </div>
                        <button class="button" type="submit">Голосовать</button>
                        <a href="index.php?showtopic=1050118&amp;mode=show">Результаты</a>
                    </form>
                    <a name="entry1"></a><div class="post_body">body</div>
                    </body></html>
                """.trimIndent(),
                argUrl = "https://4pda.to/forum/index.php?showtopic=1050118&st=0"
        )

        val poll = page.poll ?: error("Poll was not parsed")
        val first = poll.questions.single().questionItems.first()
        assertFalse(poll.isResult)
        assertTrue(poll.canVote)
        assertTrue(poll.voteButton)
        assertEquals("radio", first.type)
        assertEquals("poll_vote[1]", first.name)
        assertEquals("1", first.value)
        assertEquals("У меня работает ReVanced NonRoot", first.title)
    }

    private fun loadProductionPatterns(): IPatternProvider {
        val patternsFile = listOf(
                File("src/main/assets/patterns.json"),
                File("app/src/main/assets/patterns.json")
        ).first { it.exists() }
        val patternsJson = patternsFile.readText()
        val root = Json.parseToJsonElement(patternsJson).jsonObject
        val scopes = root.getValue("scopes").jsonArray
        val patternsByScope = mutableMapOf<String, MutableMap<String, Pattern>>()
        scopes.forEach { scopeElement ->
            val scope = scopeElement.jsonObject
            val name = scope.getValue("scope").jsonPrimitive.content
            val map = mutableMapOf<String, Pattern>()
            val patterns = scope.getValue("patterns").jsonArray
            patterns.forEach { patternElement ->
                val p = patternElement.jsonObject
                map[p.getValue("key").jsonPrimitive.content] = Pattern.compile(p.getValue("value").jsonPrimitive.content)
            }
            patternsByScope[name] = map
        }
        return object : IPatternProvider {
            override fun getCurrentVersion(): Int = -1
            override fun getPattern(scope: String, key: String): Pattern {
                return patternsByScope[scope]?.get(key)
                        ?: error("No pattern $scope/$key in production patterns.json")
            }

            override fun update(jsonString: String) = Unit
        }
    }
}
