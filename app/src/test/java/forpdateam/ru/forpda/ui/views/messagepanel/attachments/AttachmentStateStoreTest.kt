package forpdateam.ru.forpda.ui.views.messagepanel.attachments

import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentStateStoreTest {

    @Test
    fun `selection is presentation state and does not report content change`() {
        val store = AttachmentStateStore()
        val item = AttachmentItem("one")
        val changes = mutableListOf<AttachmentStateStore.Change>()
        store.addListener(changes::add)
        store.add(item)
        changes.clear()

        store.toggleSelection(item)

        assertEquals(listOf(item), store.selectedItems())
        assertEquals(1, changes.size)
        assertFalse(changes.single().contentChanged)
    }

    @Test
    fun `local stable ids are unique and server id is retained`() {
        val store = AttachmentStateStore()
        val first = AttachmentItem("first")
        val second = AttachmentItem("second")
        val uploaded = AttachmentItem("uploaded").apply { id = 81 }
        store.addAll(listOf(first, second, uploaded))

        assertTrue(store.rowId(first) < 0)
        assertNotEquals(store.rowId(first), store.rowId(second))
        assertEquals(81L, store.rowId(uploaded))
        assertEquals(store.rowId(first), store.rowId(first))
    }
}
