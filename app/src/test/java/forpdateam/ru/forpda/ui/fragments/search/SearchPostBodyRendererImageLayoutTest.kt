package forpdateam.ru.forpda.ui.fragments.search

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoader
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SearchPostBodyRendererImageLayoutTest {

    private val context: Context by lazy {
        ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(),
                R.style.DayNightAppTheme,
        )
    }
    private lateinit var imageLoader: ImageLoader

    private val renderer by lazy {
        SearchPostBodyRenderer(
                linkHandler = object : ILinkHandler {
                    override fun handle(inputUrl: String?, router: TabRouter?, args: Map<String, String>) = true
                    override fun handle(inputUrl: String?, router: TabRouter?) = true
                    override fun findScreen(url: String): String? = null
                },
                onImageClick = { _, _ -> },
        )
    }

    @Before
    fun setUp() {
        imageLoader = ImageLoader.Builder(context.applicationContext).build()
        ForPdaCoil.bindImageLoaderForTest(imageLoader)
    }

    @After
    fun tearDown() {
        imageLoader.shutdown()
    }

    @Test
    fun `undimensioned search image never receives match-parent width`() {
        val root = LinearLayout(context)

        renderer.renderInto(
                root,
                """<img alt="Изображение" src="assets://forpda/res/mask.png" />""",
        )

        val image = root.getChildAt(0) as ImageView
        assertNotEquals(ViewGroup.LayoutParams.MATCH_PARENT, image.layoutParams.width)
        assertEquals(ImageView.ScaleType.FIT_CENTER, image.scaleType)
    }

    @Test
    fun `declared search image keeps authored box instead of filling card`() {
        val root = LinearLayout(context)
        val density = context.resources.displayMetrics.density

        renderer.renderInto(
                root,
                """<img alt="Изображение" src="assets://forpda/res/mask.png" width="64" height="48" />""",
        )

        val image = root.getChildAt(0) as ImageView
        assertEquals((64 * density).toInt(), image.layoutParams.width)
        assertEquals((48 * density).toInt(), image.layoutParams.height)
    }

    @Test
    fun `digest markers in search stay beside their links`() {
        val root = LinearLayout(context)
        val icon = "assets://forpda/res/mask.png"

        renderer.renderInto(
                root,
                """
                <img alt="Изображение" src="$icon" />
                <a href="https://4pda.to/forum/index.php?p=1">Первая новость</a><br />
                <img alt="Изображение" src="$icon" />
                <a href="https://4pda.to/forum/index.php?p=2">Вторая новость</a><br />
                """.trimIndent(),
        )

        assertEquals(2, root.childCount)
        repeat(root.childCount) { index ->
            val row = root.getChildAt(index) as LinearLayout
            assertEquals(LinearLayout.HORIZONTAL, row.orientation)
            assertEquals(2, row.childCount)
            val marker = row.getChildAt(0) as ImageView
            assertEquals((20 * context.resources.displayMetrics.density).toInt(), marker.layoutParams.width)
            assertEquals((20 * context.resources.displayMetrics.density).toInt(), marker.layoutParams.height)
        }
    }
}
