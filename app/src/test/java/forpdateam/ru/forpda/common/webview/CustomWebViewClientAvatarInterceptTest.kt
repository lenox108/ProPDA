package forpdateam.ru.forpda.common.webview

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.webkit.WebView
import coil.ImageLoader
import coil.disk.DiskCache
import forpdateam.ru.forpda.common.ForPdaCoil
import io.mockk.every
import io.mockk.mockk
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CustomWebViewClientAvatarInterceptTest {

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var webView: WebView
    private lateinit var client: CustomWebViewClient

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication() as Application
        val diskCache = DiskCache.Builder()
            .directory(context.cacheDir.resolve("forpda_webview_avatar_cache"))
            .maxSizeBytes(4L * 1024L * 1024L)
            .build()
        ForPdaCoil.bindImageLoaderForTest(
            ImageLoader.Builder(context)
                .diskCache(diskCache)
                .build()
        )
        webView = WebView(context)
        client = CustomWebViewClient(avatarRepository = null)
    }

    @After
    fun tearDown() {
        webView.destroy()
        executor.shutdownNow()
    }

    @Test
    fun convertToPngBytes_returnsPngPayload() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        val bytes = client.convertToPngBytes(bitmap)
        assertNotNull(bytes)
        assertTrue(bytes!!.size > 8)
        assertEquals(0x89.toByte(), bytes[0])
        assertEquals('P'.code.toByte(), bytes[1])
        assertEquals('N'.code.toByte(), bytes[2])
        assertEquals('G'.code.toByte(), bytes[3])
    }

    @Test
    fun shouldInterceptRequest_servesCachedDiskBytesWithoutReencode() {
        val url = "https://4pda.to/forum/avatar-intercept.jpg"
        val payload = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x11, 0x22, 0x33)
        val diskCache = ForPdaCoil.imageLoader.diskCache!!
        val editor = diskCache.openEditor(url)!!
        diskCache.fileSystem.write(editor.data) {
            write(payload)
        }
        editor.commit()

        val interceptUrl = "app_cache:avatars?url=${java.net.URLEncoder.encode(url, "UTF-8")}"
        val latch = CountDownLatch(1)
        var responseBytes: ByteArray? = null
        var mimeType: String? = null
        executor.execute {
            val response = client.shouldInterceptRequest(webView, interceptUrl)
            mimeType = response?.mimeType
            responseBytes = response?.data?.readBytes()
            latch.countDown()
        }
        latch.await()

        assertEquals("image/jpeg", mimeType)
        assertNotNull(responseBytes)
        assertTrue(responseBytes!!.contentEquals(payload))
    }

    @Test
    fun shouldInterceptRequest_resolvesNickFromRepository() {
        val avatarRepository = mockk<forpdateam.ru.forpda.model.repository.avatar.AvatarRepository>()
        val avatarUrl = "https://4pda.to/forum/nick-avatar.png"
        val payload = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A)
        every { avatarRepository.getAvatarForWebViewInterceptSync("tester") } returns avatarUrl

        val diskCache = ForPdaCoil.imageLoader.diskCache!!
        val editor = diskCache.openEditor(avatarUrl)!!
        diskCache.fileSystem.write(editor.data) {
            write(payload)
        }
        editor.commit()

        val nickClient = CustomWebViewClient(avatarRepository = avatarRepository)
        val interceptUrl = "app_cache:avatars?nick=tester"
        val latch = CountDownLatch(1)
        var responseBytes: ByteArray? = null
        executor.execute {
            val response = nickClient.shouldInterceptRequest(webView, interceptUrl)
            responseBytes = response?.data?.readBytes()
            latch.countDown()
        }
        latch.await()

        assertNotNull(responseBytes)
        assertTrue(responseBytes!!.contentEquals(payload))
    }
}
