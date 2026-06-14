package forpdateam.ru.forpda.model.data.remote.api.qms

import forpdateam.ru.forpda.model.data.remote.api.qms.QmsHtmlValidator.PageKind
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

class QmsParserChatTest {

    @Test
    fun `mess_container with data-mess-id before class parses messages`() {
        val parser = QmsParser(loadProductionPatterns())
        val html = loadQmsFixture("chat_mess_container_id_first.html")
        val messages = parser.parseChat(html).messages
        assertEquals(2, messages.size)
        assertEquals(9001, messages.first().id)
        assertEquals("Server order attrs", messages.first().content)
        assertTrue(messages[1].isMyMessage)
    }

    @Test
    fun `mess_container class before data-mess-id parses via pattern`() {
        val parser = QmsParser(loadProductionPatterns())
        val html = """
            <div class="mess_list">
            <div class="mess_container our" data-mess-id="301">
            <div class="mess"><div class="content">Template order</div></div>
            <div class="time"><span>08:00</span></div></div>
            </div>
        """.trimIndent()
        val messages = parser.parseChat(html).messages
        assertEquals(1, messages.size)
        assertEquals(301, messages.first().id)
        assertEquals("Template order", messages.first().content)
    }

    @Test
    fun `mess_container HTML parses messages`() {
        val parser = QmsParser(loadProductionPatterns())
        val html = """
            <div class="mess_list">
            <div class="mess_container our" data-mess-id="201">
            <div class="mess"><div class="content">Container msg</div></div>
            <div class="time"><span>09:15</span></div></div>
            <div class="mess_container his unread" data-mess-id="202">
            <div class="mess"><div class="content">Reply</div></div>
            <div class="time"><span>09:16</span></div></div>
            </div>
        """.trimIndent()
        val messages = parser.parseChat(html).messages
        assertEquals(2, messages.size)
        assertEquals(201, messages.first().id)
        assertTrue(messages.first().isMyMessage)
        assertEquals("Container msg", messages.first().content)
    }

    @Test
    fun `empty mess_list parses as empty thread without crash`() {
        val parser = QmsParser(loadProductionPatterns())
        val html = """
            <div class="mess_list"></div>
            <div id="thread-inside-bottom"></div>
        """.trimIndent()
        val chat = parser.parseChat(html)
        assertTrue(chat.messages.isEmpty())
        assertEquals(0, chat.messages.size)
    }

    @Test
    fun selectorMismatchContainerFixture_hasMarkersButParsesZeroMessages() {
        val html = loadQmsFixture("chat_selector_mismatch_container.html")
        val signals = QmsHtmlValidator.measureThread(html)
        assertTrue(signals.containerMessageMarkers > 0)
        assertEquals(PageKind.QMS_THREAD, QmsHtmlValidator.classify(200, html))
        val parser = QmsParser(loadProductionPatterns())
        assertTrue(parser.parseChat(html).messages.isEmpty())
    }

    @Test
    fun emptyThreadFixture_documentsEmptyQmsThreadCorpus() {
        val html = loadQmsFixture("chat_empty_thread.html")
        assertTrue(html.contains("mess_list"))
        assertTrue(html.contains("thread-inside-bottom"))
        assertEquals(PageKind.QMS_EMPTY_THREAD, QmsHtmlValidator.classify(200, html))
    }

    @Test
    fun `real system notification rows parse as messages`() {
        val parser = QmsParser(loadProductionPatterns())
        val html = loadQmsFixture("chat_system_notifications.html")
        // parseMoreMessages exercises the same message scan as parseChat without invoking
        // chat_info's Android Html.fromHtml (unavailable in plain JVM unit tests).
        val messages = parser.parseMoreMessages(html)
        val realMessages = messages.filter { !it.isDate }
        assertEquals(4, realMessages.size)
        assertEquals(2599357, realMessages.first().id)
        assertFalse("system notices are not our messages", realMessages.first().isMyMessage)
        assertTrue(
                "expected reputation notice content",
                realMessages.first().content?.contains("репутаци") == true
        )
    }

    @Test
    fun `real system notification thread is classified QMS_THREAD not empty`() {
        val html = loadQmsFixture("chat_system_notifications.html")
        assertEquals(PageKind.QMS_THREAD, QmsHtmlValidator.classify(200, html))
        val signals = QmsHtmlValidator.measureThread(html)
        assertEquals(4, signals.messageMarkers)
        assertEquals(4, signals.contentMarkers)
    }

    @Test
    fun `system notification rows without data-message-id still parse via fallback`() {
        val parser = QmsParser(loadProductionPatterns())
        // Defensive case: a system row variant that omits data-message-id/data-unread-status but
        // keeps the msg-content block. The legacy/container patterns return empty here.
        val html = """
            <div class="body-tbl list-group thread-list">
            <div class="list-group-item">
            <div class="time"><b class="read-status read big-dot"></b> 09:00 </div>
            <strong>Сообщения 4PDA</strong><br>
            <div class="msg-content emoticons">Тема перемещена в раздел Android</div></div>
            <div id="thread-inside-bottom"></div>
            </div>
        """.trimIndent()
        val messages = parser.parseMoreMessages(html).filter { !it.isDate }
        assertEquals(1, messages.size)
        assertTrue(messages.first().content?.contains("перемещена") == true)
    }

    @Test
    fun systemMessagesFixture_documentsLegacySystemCorpus() {
        val html = loadQmsFixture("chat_system_messages.html")
        assertTrue(html.contains("data-message-id=\"501\""))
        assertTrue(html.contains("msg-content"))
        assertTrue(html.contains("Сообщения 4PDA"))
        assertTrue(html.contains("our-message"))
    }

    @Test
    fun `thread_main pattern matches a system mailbox row shape with a real thread id`() {
        // parseThemes() itself routes the theme name through Android's Html.fromHtml (unavailable in
        // plain JVM, like chat_info), so we assert the production thread_main regex directly against
        // the documented mid=0 row shape: a list-group-item anchor with data-thread-id (282644 from
        // the saved "Оповещения" thread fixture), a bage date and a "(count / new)" suffix.
        // NOTE: the real act=qms&mid=0 themes-list HTML is still the artifact that would fully
        // confirm the live markup matches this shape; this guards the documented shape.
        val provider = loadProductionPatterns()
        val pattern = provider.getPattern("qms", "thread_main")
        val row = """
            <a class="list-group-item" data-thread-id="282644" href="?act=qms&mid=0&t=282644">
            <div class="bage green">25 июн. 2013</div>Оповещения (4 / 2)</a>
        """.trimIndent()

        val matcher = pattern.matcher(row)
        assertTrue("thread_main should match the documented system row shape", matcher.find())
        assertEquals("282644", matcher.group(1))
        assertEquals("Оповещения", matcher.group(3).trim())
        assertEquals("4", matcher.group(4))
    }

    @Test
    fun `empty system mailbox parses to empty themes without virtual placeholder`() {
        val parser = QmsParser(loadProductionPatterns())

        val themes = parser.parseThemes("<div class=\"nav\"></div>", 0)

        assertTrue(themes.themes.isEmpty())
    }

    @Test
    fun `valid QMS HTML parses messages`() {
        val parser = QmsParser(loadProductionPatterns())
        val html = """
            <div class="list-group-item our-message" data-message-id="101" data-unread-status="0">
            <b></b> 12:34 <img src="/avatar.png"/>
            <div class="msg-content">First message</div></div>
            <div class="list-group-item" data-message-id="102" data-unread-status="1">
            <b>other</b> 12:35 <img src="/avatar2.png"/>
            <div class="msg-content">Second message</div></div>
            <div id="thread-inside-bottom"></div>
        """.trimIndent()
        val messages = parser.parseMoreMessages(html)
        assertTrue("expected at least one message, got ${messages.size}", messages.isNotEmpty())
        assertEquals(101, messages.first().id)
        assertTrue(messages.any { it.content?.contains("First message") == true })
    }

    private fun loadQmsFixture(name: String): String {
        val file = listOf(
                File("src/test/resources/parser/qms/$name"),
                File("app/src/test/resources/parser/qms/$name")
        ).first { it.exists() }
        return file.readText()
    }

    private fun loadProductionPatterns(): IPatternProvider {
        val patternsFile = listOf(
                File("src/main/assets/patterns.json"),
                File("app/src/main/assets/patterns.json")
        ).first { it.exists() }
        val root = Json.parseToJsonElement(patternsFile.readText()).jsonObject
        val patternsByScope = mutableMapOf<String, MutableMap<String, Pattern>>()
        root.getValue("scopes").jsonArray.forEach { scopeElement ->
            val scope = scopeElement.jsonObject
            val name = scope.getValue("scope").jsonPrimitive.content
            val map = mutableMapOf<String, Pattern>()
            scope.getValue("patterns").jsonArray.forEach { patternElement ->
                val p = patternElement.jsonObject
                map[p.getValue("key").jsonPrimitive.content] =
                        Pattern.compile(p.getValue("value").jsonPrimitive.content)
            }
            patternsByScope[name] = map
        }
        return object : IPatternProvider {
            override fun getCurrentVersion(): Int = -1
            override fun getPattern(scope: String, key: String): Pattern =
                    patternsByScope[scope]?.get(key)
                            ?: error("No pattern $scope/$key")
            override fun update(jsonString: String) = Unit
        }
    }
}
