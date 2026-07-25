package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.text.Selection
import android.text.Spannable
import android.text.method.ArrowKeyMovementMethod
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
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
    fun `modern post date setting also formats the quote header`() {
        val factory = BodyBlockViewFactory(linkHandler, mutableMapOf(), callbacks()).apply {
            modernPostDates = true
        }
        val root = LinearLayout(context)
        val rawDate = LocalDateTime.now()
                .minusHours(2)
                .format(DateTimeFormatter.ofPattern("dd.MM.yy, HH:mm"))

        factory.render(
                root,
                listOf(
                        BodyBlock.Quote(
                                author = "Автор",
                                date = rawDate,
                                sourceUrl = null,
                                inner = listOf(BodyBlock.Text("Текст цитаты")),
                        ),
                ),
                BodyBlockViewFactory.RenderScope(scopeId = 790, allowQuoteSelection = true),
        )

        val header = root.descendantTextViews().first { it.text.contains("Автор") }
        assertEquals("Автор · 2 ч.", header.text.toString())
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
    fun `digest list icon and following link render as one compact row`() {
        val factory = BodyBlockViewFactory(linkHandler, mutableMapOf(), callbacks())
        val root = LinearLayout(context)

        factory.render(
                root,
                listOf(
                        BodyBlock.Image(
                                imageUrl = "",
                                linkUrl = null,
                                displayWidthPx = 0,
                                displayHeightPx = 0,
                                inline = true,
                                inlineListIcon = true,
                        ),
                        BodyBlock.Text("<a href=\"https://4pda.to/forum/post\">Новость</a>"),
                ),
                BodyBlockViewFactory.RenderScope(scopeId = 144356735),
        )

        assertEquals("icon + link pair must consume one vertical block", 1, root.childCount)
        val row = root.getChildAt(0) as LinearLayout
        assertEquals(LinearLayout.HORIZONTAL, row.orientation)
        assertEquals(2, row.childCount)
        val icon = row.getChildAt(0) as ImageView
        val expectedSize = (20f * context.resources.displayMetrics.density).toInt()
        assertEquals(expectedSize, icon.layoutParams.width)
        assertEquals(expectedSize, icon.layoutParams.height)
        assertEquals("Новость", (row.getChildAt(1) as TextView).text.toString())
    }

    @Test
    fun `attachment rearms selection controller after detached native quote premeasure`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val movement = SelectionCheckMovementMethod()
        val textView = TopicSelectableTextView(
                ContextThemeWrapper(activity, R.style.DayNightAppTheme),
        ).apply {
            text = "страница дергается при прокрутке"
            setTextIsSelectable(true)
            movementMethod = movement
        }

        // quoteView() performs this detached measurement to decide whether a quote should collapse.
        // Android creates the text Layout here while selection handles are not window-supported.
        textView.measure(
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        textView.layout(0, 0, textView.measuredWidth, textView.measuredHeight)
        val checksBeforeAttachment = movement.selectionChecks

        activity.setContentView(textView)

        assertTrue(movement.selectionChecks > checksBeforeAttachment)
        assertTrue(textView.movementMethod === movement)
    }

    @Test
    fun `platform copy action writes selected quote text to clipboard`() {
        val factory = BodyBlockViewFactory(linkHandler, mutableMapOf(), callbacks())
        val root = LinearLayout(context)
        factory.render(
                root,
                listOf(
                        BodyBlock.Quote(
                                author = "eXense",
                                date = "Сегодня, 18:37",
                                sourceUrl = null,
                                inner = listOf(BodyBlock.Text("страница дергается при прокрутке")),
                        ),
                ),
                BodyBlockViewFactory.RenderScope(scopeId = 4, allowQuoteSelection = true),
        )
        val quoteText = root.descendantTextViews()
                .first { it.text.contains("страница дергается") }
        val start = quoteText.text.indexOf("при")
        Selection.setSelection(quoteText.text as Spannable, start, start + "при".length)

        assertTrue(quoteText.onTextContextMenuItem(android.R.id.copy))

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("при", clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString())
    }

    private fun callbacks(longPress: (String) -> Unit = {}) =
            object : BodyBlockViewFactory.Callbacks {
                override fun onImageClick(galleryUrls: List<String>, index: Int) = Unit
                override fun onLinkLongClick(url: String) = longPress(url)
            }

    private class SelectionCheckMovementMethod : ArrowKeyMovementMethod() {
        var selectionChecks = 0

        override fun canSelectArbitrarily(): Boolean {
            selectionChecks++
            return true
        }
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
