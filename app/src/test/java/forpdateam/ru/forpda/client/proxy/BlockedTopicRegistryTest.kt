package forpdateam.ru.forpda.client.proxy

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Список «темы через прокси» показывается пользователю в настройках, поэтому рядом с id хранится
 * имя темы. Заголовки лежат в том же файле настроек — важно, чтобы они не считались отдельными
 * темами и не оставались после удаления.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BlockedTopicRegistryTest {

    private lateinit var registry: BlockedTopicRegistry

    @Before
    fun setUp() {
        registry = BlockedTopicRegistry(ApplicationProvider.getApplicationContext())
        registry.clear()
    }

    @Test
    fun `keeps the title next to the topic and counts topics, not keys`() {
        registry.remember(777, "Zona", nowMs = 1_000L)

        assertEquals(1, registry.size())
        assertEquals(BlockedTopicRegistry.BlockedTopic(777, "Zona", 1_000L), registry.topics().single())
    }

    @Test
    fun `fresh topics come first`() {
        registry.remember(1, "Старая", nowMs = 1_000L)
        registry.remember(2, "Свежая", nowMs = 2_000L)

        assertEquals(listOf("Свежая", "Старая"), registry.topics().map { it.title })
    }

    /** Тема могла попасть в список до того, как мы стали сохранять имя — экран покажет номер. */
    @Test
    fun `topic without a title stays in the list`() {
        registry.remember(555, nowMs = 1_000L)

        assertNull(registry.topics().single().title)
    }

    /** Пустое имя не должно затирать нормальное: страница-заглушка приходит без заголовка. */
    @Test
    fun `blank title does not wipe the stored one`() {
        registry.remember(777, "Zona", nowMs = 1_000L)
        registry.remember(777, "  ", nowMs = 2_000L)

        assertEquals("Zona", registry.topics().single().title)
    }

    @Test
    fun `forget removes the title too`() {
        registry.remember(777, "Zona", nowMs = 1_000L)
        registry.forget(777)

        assertEquals(0, registry.size())
        registry.remember(777, nowMs = 3_000L)
        assertNull(registry.topics().single().title)
    }
}
