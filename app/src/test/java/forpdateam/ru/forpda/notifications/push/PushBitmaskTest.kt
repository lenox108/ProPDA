package forpdateam.ru.forpda.notifications.push

import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Битмаск семейств событий, который уходит на сервер 4PDA опкодом `ai`.
 * Биты (из офиц. клиента): б0 QMS, б1 системные QMS, б2 избранное, б3 важные темы, б4 упоминания.
 *
 * Системные события QMS — ОТДЕЛЬНЫЙ бит: раньше он всегда шёл вместе с б0, и отключить только
 * сообщения от 4PDA (репутация, состояние аккаунта) было нельзя, хотя у офиц. клиента можно.
 */
class PushBitmaskTest {

    private fun prefs(
            main: Boolean = true,
            qms: Boolean = false,
            qmsSystem: Boolean = false,
            fav: Boolean = false,
            favOnlyImportant: Boolean = false,
            mentions: Boolean = false,
    ): NotificationPreferencesHolder = mockk(relaxed = true) {
        every { getMainEnabled() } returns main
        every { getQmsEnabled() } returns qms
        every { getQmsSystemEnabled() } returns qmsSystem
        every { getFavEnabled() } returns fav
        every { getFavOnlyImportant() } returns favOnlyImportant
        every { getMentionsEnabled() } returns mentions
    }

    private fun mask(p: NotificationPreferencesHolder): Int =
            PushRegistrar(mockk(relaxed = true), p, mockk(relaxed = true)).computeBitmask()

    @Test
    fun `переписка и системные события — независимые биты`() {
        assertEquals(0b00001, mask(prefs(qms = true, qmsSystem = false)))
        assertEquals(0b00010, mask(prefs(qms = false, qmsSystem = true)))
        assertEquals(0b00011, mask(prefs(qms = true, qmsSystem = true)))
    }

    @Test
    fun `главный тумблер выключает всё`() {
        assertEquals(0, mask(prefs(main = false, qms = true, qmsSystem = true, fav = true, mentions = true)))
    }

    @Test
    fun `избранное и упоминания на своих местах`() {
        // Избранное «все темы» = б2+б3, «только важные» = только б3.
        assertEquals(0b01100, mask(prefs(fav = true)))
        assertEquals(0b01000, mask(prefs(fav = true, favOnlyImportant = true)))
        assertEquals(0b10000, mask(prefs(mentions = true)))
    }
}
