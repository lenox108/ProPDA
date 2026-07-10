package forpdateam.ru.forpda.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Уведомления о загрузках не подчиняются главному тумблеру уведомлений — и так считает код:
 * `wantsPushNotificationsSync()` учитывает только темы/QMS/упоминания, а `DownloadWorker`
 * смотрит исключительно на свой флаг.
 *
 * Раньше разметка утверждала обратное: у переключателя стоял
 * `android:dependency="notifications.main.enabled"`, поэтому при выключенном главном тумблере
 * он становился серым, а сохранённое значение оставалось `true` — и уведомления о скачивании
 * продолжали приходить. Тест пиннит, что разметка снова не расходится с поведением.
 */
class DownloadsPreferenceIndependenceTest {

    private val downloadsKey = "notifications.downloads.enabled"

    @Test
    fun downloadsSwitch_hasNoDependencyOnMainToggle() {
        val block = downloadsPreferenceBlock()
        assertFalse(
            "переключатель загрузок не должен зависеть от notifications.main.enabled:\n$block",
            block.contains("android:dependency")
        )
    }

    @Test
    fun pushFamilySwitches_stillDependOnMainToggle() {
        // Обратная страховка: у настоящих push-семейств зависимость обязана остаться.
        val xml = readPreferencesXml()
        for (key in listOf("notifications.fav.enabled", "notifications.qms.enabled", "notifications.mentions.enabled")) {
            val block = preferenceBlockFor(xml, key)
            assertTrue(
                "$key обязан зависеть от главного тумблера:\n$block",
                block.contains("""android:dependency="notifications.main.enabled"""")
            )
        }
    }

    private fun downloadsPreferenceBlock(): String = preferenceBlockFor(readPreferencesXml(), downloadsKey)

    /** Вырезает объявление <...Preference ... key="<key>" ... /> целиком. */
    private fun preferenceBlockFor(xml: String, key: String): String {
        val keyIndex = xml.indexOf("""android:key="$key"""")
        check(keyIndex >= 0) { "не найден ключ $key в preferences_notifications.xml" }
        val start = xml.lastIndexOf('<', keyIndex)
        val end = xml.indexOf("/>", keyIndex)
        check(start >= 0 && end > start) { "не удалось выделить объявление для $key" }
        return xml.substring(start, end + 2)
    }

    private fun readPreferencesXml(): String {
        val file = java.io.File("src/main/res/xml/preferences_notifications.xml")
        check(file.exists()) { "preferences_notifications.xml не найден: ${file.absolutePath}" }
        return file.readText()
    }
}
