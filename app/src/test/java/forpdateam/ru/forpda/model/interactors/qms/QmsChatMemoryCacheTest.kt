package forpdateam.ru.forpda.model.interactors.qms

import forpdateam.ru.forpda.entity.remote.qms.QmsChatModel
import forpdateam.ru.forpda.entity.remote.qms.QmsMessage
import forpdateam.ru.forpda.model.data.remote.api.qms.QmsHtmlValidator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QmsChatMemoryCacheTest {

    @After
    fun tearDown() {
        QmsChatMemoryCache.invalidateAll()
    }

    @Test
    fun `cacheAgeMinutes returns zero right after put`() {
        val nowMs = System.currentTimeMillis()
        QmsChatMemoryCache.put(1, 2, chatWithOneMessage(), QmsHtmlValidator.PageKind.QMS_THREAD)

        assertEquals(0, QmsChatMemoryCache.cacheAgeMinutes(1, 2, nowMs + 30_000L))
    }

    @Test
    fun `cacheAgeMinutes returns elapsed whole minutes`() {
        val storedAtMs = System.currentTimeMillis()
        QmsChatMemoryCache.put(1, 2, chatWithOneMessage(), QmsHtmlValidator.PageKind.QMS_THREAD)

        assertEquals(2, QmsChatMemoryCache.cacheAgeMinutes(1, 2, storedAtMs + 125_000L))
    }

    @Test
    fun `cacheAgeMinutes is null when cache expired`() {
        val storedAtMs = System.currentTimeMillis()
        QmsChatMemoryCache.put(1, 2, chatWithOneMessage(), QmsHtmlValidator.PageKind.QMS_THREAD)

        assertNull(QmsChatMemoryCache.cacheAgeMinutes(1, 2, storedAtMs + 16 * 60 * 1000L))
    }

    private fun chatWithOneMessage(): QmsChatModel = QmsChatModel().apply {
        userId = 1
        themeId = 2
        messages.add(QmsMessage().apply {
            id = 1
            content = "cached"
        })
    }
}
