package forpdateam.ru.forpda.model.data.remote.api.profile

import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.regex.Pattern

/**
 * Блок «Устройства» в профиле: строка не должна оставаться пустой.
 *
 * Пустой текст ссылки встречается в живой разметке 4pda (девайс без названия
 * в devdb, служебная ссылка правки перед настоящей). Раньше такая запись
 * превращалась в `"${name} ${accessory}"` из пробелов — в карточке был пустой
 * ряд без названия устройства.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProfileParserDevicesTest {

    @Test
    fun parse_deviceWithText_usesLinkText() {
        val device = parseDevices().first { it.url?.endsWith("oneplus_open") == true }
        assertEquals("OnePlus Open", device.name)
        assertNull(device.accessory)
    }

    @Test
    fun parse_deviceWithEmptyLinkText_fallsBackToDevDbSlug() {
        val device = parseDevices().first { it.url?.endsWith("xiaomi_14_ultra") == true }
        assertEquals("Xiaomi 14 Ultra", device.name)
    }

    @Test
    fun parse_serviceLinkBeforeDevice_takesNameFromTail() {
        // Первой в <li> идёт служебная ссылка без текста — имя устройства
        // остаётся в «хвосте» до </li> и должно попасть в name.
        val device = parseDevices().first { it.url == "#" }
        assertEquals("Pixel 9 Pro", device.name)
        assertNull(device.accessory)
    }

    @Test
    fun parse_noDeviceHasBlankName() {
        val devices = parseDevices()
        assertEquals(3, devices.size)
        assertEquals(emptyList<String>(), devices.filter { it.name.isNullOrBlank() }.map { it.url })
    }

    private fun parseDevices() =
            ProfileParser(loadProductionPatterns())
                    .parse(profileHtml(), "https://4pda.to/forum/index.php?showuser=123")
                    .devices

    private fun profileHtml(): String = """
        <div class="user-box">
        <img src="https://4pda.to/avatar.gif">
        <h1>TestUser</h1>
        <span class="title">Статус</span>
        <h2>Начинающий</h2>
        <ul class="info-list black-link">
          <li><span class="title">Регистрация:</span><div class="area">29.09.10</div></li>
          <li><span class="title">Последнее посещение:</span><div class="area">Сегодня, 08:09</div></li>
        </ul>
        <div class="u-note">Нет подписи</div>
        <div class="tail">
        <ul class="info-list width1 black-link">
          <li><span class="title">Мужчина</span></li>
          <li><span class="title">Время у юзера:</span><div class="area">28.07.2026, 08:09</div></li>
        </ul>
        <ul class="social-link"><li><a href="https://t.me/test/" title="@test">Telegram</a></li></ul>
        <ul class="info-list width1 black-link" id="user-profile-0-device-list">
          <li>
            <a href="https://4pda.to/devdb/oneplus_open" target="_blank">OnePlus Open</a>
          </li>
          <li>
            <a href="https://4pda.to/devdb/xiaomi_14_ultra" target="_blank"></a>
          </li>
          <li>
            <a href="#" class="edit-device"></a><a href="https://4pda.to/devdb/pixel_9_pro" target="_blank">Pixel 9 Pro</a>
          </li>
        </ul>
        <ul class="info-list width2"><li><span class="title">Карма:</span><div class="area"><a href="#" title="t">0</a></div></li></ul>
        <ul class="info-list width2"><li><span class="title">Репутация:</span><div class="area">479</div></li></ul>
        </div>
        </div>
    """.trimIndent()

    private fun loadProductionPatterns(): IPatternProvider {
        val patternsFile = listOf(
                File("src/main/assets/patterns.json"),
                File("app/src/main/assets/patterns.json"),
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
                    patternsByScope[scope]?.get(key) ?: Pattern.compile("a^")

            override fun update(jsonString: String) = Unit
        }
    }
}
