package forpdateam.ru.forpda.common

import android.app.Application
import coil.ImageLoader
import coil.disk.DiskCache
import okio.buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class ForPdaCoilCachedBytesTest {

    private val executor = Executors.newSingleThreadExecutor()

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication() as Application
        val diskCache = DiskCache.Builder()
            .directory(context.cacheDir.resolve("forpda_coil_test_cache"))
            .maxSizeBytes(4L * 1024L * 1024L)
            .build()
        ForPdaCoil.bindImageLoaderForTest(
            ImageLoader.Builder(context)
                .diskCache(diskCache)
                .build()
        )
    }

    @After
    fun tearDown() {
        executor.shutdownNow()
    }

    @Test
    fun mimeTypeFromUrl_detectsCommonAvatarExtensions() {
        assertEquals("image/jpeg", ForPdaCoil.mimeTypeFromUrl("https://4pda.to/forum/avatar.jpg"))
        assertEquals("image/png", ForPdaCoil.mimeTypeFromUrl("https://4pda.to/forum/avatar.png"))
        assertEquals("image/webp", ForPdaCoil.mimeTypeFromUrl("https://4pda.to/forum/avatar.webp"))
        assertEquals("image/jpeg", ForPdaCoil.mimeTypeFromUrl("https://4pda.to/forum/dl/post/s/123456"))
    }

    @Test
    fun loadCachedImageBytesSync_returnsDiskBytesWithoutDecode() {
        val context = RuntimeEnvironment.getApplication()
        val url = "https://4pda.to/forum/avatar-test.jpg"
        val payload = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00, 0x01, 0x02)

        val diskCache = ForPdaCoil.imageLoader.diskCache!!
        val editor = diskCache.openEditor(url)!!
        diskCache.fileSystem.write(editor.data) {
            write(payload)
        }
        editor.commit()

        val latch = CountDownLatch(1)
        var loaded: ForPdaCoil.CachedImageBytes? = null
        executor.execute {
            loaded = ForPdaCoil.loadCachedImageBytesSync(context, url)
            latch.countDown()
        }
        latch.await()

        assertEquals("image/jpeg", loaded?.mimeType)
        assertArrayEquals(payload, loaded?.bytes)
    }

    @Test
    fun loadCachedImageBytesSync_returnsNullOnMainThread() {
        val context = RuntimeEnvironment.getApplication()
        assertNull(ForPdaCoil.loadCachedImageBytesSync(context, "https://4pda.to/missing.jpg"))
    }
}
