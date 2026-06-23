package forpdateam.ru.forpda.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R3 (perf(notifications)): при серии QMS-сообщений от одного юзера
 * повторные загрузки аватара через Coil можно избежать, держа в
 * `NotificationsService` локальный LruCache<String, Bitmap> на 64 элемента.
 *
 * Этот тест source-level: проверяет, что в `NotificationsService.sendNotification`
 * есть явная проверка кэша ДО вызова `ForPdaCoil.loadBitmapForNotification`,
 * и что `avatarBitmapCache` объявлен через `android.util.LruCache` ровно на 64
 * элемента. Этого достаточно, чтобы удержать регрессию.
 */
class NotificationsServiceAvatarCacheTest {

    @Test
    fun sendNotification_checksAvatarCacheBeforeCoil() {
        val body = readServiceSource()
        val sendOpen = body.indexOf("fun sendNotification(event: NotificationEvent) {")
        assertTrue(
            "sendNotification(event: NotificationEvent) не найден в файле",
            sendOpen >= 0
        )
        // Берём тело до следующего одно-уровневого `fun ` (на отступе 4 пробела)
        // или до companion object.
        val nextFun = Regex("""\n    (private |internal |public |protected )?fun """)
            .find(body, startIndex = sendOpen + 1)
        val sendBody = if (nextFun != null) body.substring(sendOpen, nextFun.range.first) else body.substring(sendOpen)
        assertTrue(
            "sendNotification должен проверять avatarBitmapCache до вызова ForPdaCoil; получили:\n$sendBody",
            sendBody.contains("avatarBitmapCache.get(cacheKey)")
                    && sendBody.contains("avatarBitmapCache.put(cacheKey")
        )
        val cacheCheckIdx = sendBody.indexOf("avatarBitmapCache.get(cacheKey)")
        val coilIdx = sendBody.indexOf("ForPdaCoil.loadBitmapForNotification")
        assertTrue(
            "проверка кэша должна идти раньше обращения к Coil (cache=$cacheCheckIdx, coil=$coilIdx)\n$sendBody",
            cacheCheckIdx in 0 until coilIdx
        )
    }

    @Test
    fun avatarCache_isLruCacheOfSize64() {
        val body = readServiceSource()
        val regex = Regex(
            """LruCache<String,\s*Bitmap>\((\d+)\)"""
        )
        val match = regex.find(body) ?: error("LruCache<String, Bitmap>(N) не найден в NotificationsService.kt")
        assertEquals("размер LruCache аватаров должен быть 64", "64", match.groupValues[1])
    }

    @Test
    fun avatarCache_isEvictedInOnDestroy() {
        val body = readServiceSource()
        assertTrue(
            "onDestroy должен чистить avatarBitmapCache через evictAll()",
            body.contains("avatarBitmapCache.evictAll()")
        )
    }

    private fun readServiceSource(): String {
        val file = java.io.File(
            "src/main/java/forpdateam/ru/forpda/notifications/NotificationsService.kt"
        )
        check(file.exists()) {
            "NotificationsService.kt не найден: ${file.absolutePath}"
        }
        return file.readText()
    }
}
