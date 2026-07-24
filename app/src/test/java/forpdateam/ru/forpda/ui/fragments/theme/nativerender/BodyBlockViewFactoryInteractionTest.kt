package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class BodyBlockViewFactoryInteractionTest {

    private val context: Context by lazy {
        ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(),
                R.style.DayNightAppTheme,
        )
    }

    private val linkHandler = object : ILinkHandler {
        override fun handle(inputUrl: String?, router: TabRouter?, args: Map<String, String>) = true
        override fun handle(inputUrl: String?, router: TabRouter?) = true
        override fun findScreen(url: String): String? = null
    }

    @Test
    fun `quote header long press opens link actions and quoted text stays selectable`() {
        var longPressedUrl: String? = null
        val factory = BodyBlockViewFactory(
                linkHandler,
                mutableMapOf(),
                callbacks(longPress = { longPressedUrl = it }),
        )
        val root = LinearLayout(context)
        val sourceUrl = "https://4pda.to/forum/index.php?showtopic=123&view=findpost&p=456"

        factory.render(
                root,
                listOf(
                        BodyBlock.Quote(
                                author = "Автор",
                                date = "24.07.2026",
                                sourceUrl = sourceUrl,
                                inner = listOf(BodyBlock.Text("Текст цитаты")),
                        ),
                ),
                BodyBlockViewFactory.RenderScope(scopeId = 789, allowQuoteSelection = true),
        )

        val texts = root.descendantTextViews()
        val header = texts.first { it.text.contains("Автор") }
        val quoteText = texts.first { it.text.contains("Текст цитаты") }
        assertTrue(header.performLongClick())
        assertEquals(sourceUrl, longPressedUrl)
        assertTrue(quoteText.isTextSelectable)
    }

    @Test
    fun `fallback text inside a quote is selectable too`() {
        val factory = BodyBlockViewFactory(linkHandler, mutableMapOf(), callbacks())
        val root = LinearLayout(context)

        factory.render(
                root,
                listOf(
                        BodyBlock.Quote(
                                author = null,
                                date = null,
                                sourceUrl = null,
                                inner = listOf(
                                        BodyBlock.WebFallback(
                                                "<div>Сложный текст цитаты</div>",
                                                BodyBlock.WebFallback.Kind.UNKNOWN,
                                        ),
                                ),
                        ),
                ),
                BodyBlockViewFactory.RenderScope(scopeId = 1, allowQuoteSelection = true),
        )

        val fallback = root.descendantTextViews().first { it.text.contains("Сложный текст цитаты") }
        assertTrue(fallback.isTextSelectable)
    }

    private fun callbacks(longPress: (String) -> Unit = {}) =
            object : BodyBlockViewFactory.Callbacks {
                override fun onImageClick(galleryUrls: List<String>, index: Int) = Unit
                override fun onLinkLongClick(url: String) = longPress(url)
            }

    private fun ViewGroup.descendantTextViews(): List<TextView> {
        val result = ArrayList<TextView>()
        fun collect(group: ViewGroup) {
            for (index in 0 until group.childCount) {
                when (val child = group.getChildAt(index)) {
                    is TextView -> result += child
                    is ViewGroup -> collect(child)
                }
            }
        }
        collect(this)
        return result
    }
}
