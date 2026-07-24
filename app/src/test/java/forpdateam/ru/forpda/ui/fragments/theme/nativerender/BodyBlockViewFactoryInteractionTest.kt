package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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
        assertTrue(quoteText.isLongClickable)
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

    @Test
    fun `real long press selects quote text in attached window`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val themedContext = ContextThemeWrapper(activity, R.style.DayNightAppTheme)
        val root = LinearLayout(themedContext)
        val factory = BodyBlockViewFactory(linkHandler, mutableMapOf(), callbacks())
        factory.render(
                root,
                listOf(
                        BodyBlock.Quote(
                                author = "Автор",
                                date = null,
                                sourceUrl = null,
                                inner = listOf(BodyBlock.Text("Выделяемый текст цитаты")),
                        ),
                ),
                BodyBlockViewFactory.RenderScope(scopeId = 3, allowQuoteSelection = true),
        )
        activity.setContentView(root)
        root.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.AT_MOST),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        val quoteText = root.descendantTextViews()
                .first { it.text.contains("Выделяемый текст цитаты") }
        val downTime = SystemClock.uptimeMillis()
        quoteText.dispatchTouchEvent(
                MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 20f, 20f, 0),
        )
        shadowOf(android.os.Looper.getMainLooper()).idleFor(
                ViewConfiguration.getLongPressTimeout().toLong() + 100L,
                java.util.concurrent.TimeUnit.MILLISECONDS,
        )

        assertNotEquals(quoteText.selectionStart, quoteText.selectionEnd)
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
