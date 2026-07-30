package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoader
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Рендер личной подписи автора под телом поста («Показывать подписи пользователей»).
 *
 * Живьём сценарий требует темы, где у авторов есть подписи, и дождавшегося отложенного десктопного
 * обогащения; здесь фиксируем сам биндинг: настройка выключена — блока нет вовсе, включена и подпись
 * приехала — под телом появляется отбивка и текст подписи с сохранённой разметкой.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class TopicPostsAdapterSignatureTest {

    private val context: Context by lazy {
        ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.DayNightAppTheme)
    }

    private lateinit var imageLoader: ImageLoader
    private lateinit var adapter: TopicPostsAdapter

    @Before
    fun setUp() {
        imageLoader = ImageLoader.Builder(context.applicationContext).build()
        ForPdaCoil.bindImageLoaderForTest(imageLoader)
        adapter = TopicPostsAdapter(noopLinkHandler, noopListener)
        adapter.setAuthContext(authorized = true, memberId = 777)
        adapter.setDisplaySettings(TopicPostsAdapter.PostDisplaySettings(showSignatures = true))
    }

    @After
    fun tearDown() {
        imageLoader.shutdown()
    }

    @Test
    fun `signature renders under the post body when the setting is on`() {
        val holder = bind(item(postId = 1, signature = "Nokia 3310 &gt; Galaxy S26 Ultra"))

        assertEquals(View.VISIBLE, holder.signature().visibility)
        assertEquals("Nokia 3310 > Galaxy S26 Ultra", holder.signatureText().text.toString())
    }

    /** Разметка подписи (цвета, ссылки, жирный) не теряется — рендерим тем же движком, что и тело. */
    @Test
    fun `signature markup is rendered, not shown as raw html`() {
        val holder = bind(item(
                postId = 2,
                signature = """<b>Юзерскрипты:</b> <a href="https://4pda.to/">Dark Mode</a>""",
        ))

        val text = holder.signatureText().text.toString()
        assertEquals("Юзерскрипты: Dark Mode", text)
        assertTrue(holder.signatureText().urls.isNotEmpty())
    }

    @Test
    fun `setting off hides the signature block entirely`() {
        adapter.setDisplaySettings(TopicPostsAdapter.PostDisplaySettings(showSignatures = false))
        val holder = bind(item(postId = 3, signature = "Подпись"))

        assertEquals(View.GONE, holder.signature().visibility)
    }

    /** До десктопного обогащения подпись ещё не приехала — блока быть не должно. */
    @Test
    fun `post without a signature keeps the block gone`() {
        val holder = bind(item(postId = 4, signature = null))

        assertEquals(View.GONE, holder.signature().visibility)
    }

    /** Переиспользованный холдер не должен донашивать подпись предыдущего поста. */
    @Test
    fun `recycled holder drops the previous signature`() {
        adapter.submitList(listOf(
                item(postId = 5, signature = "Первая подпись"),
                item(postId = 6, signature = null),
        ))
        val parent = FrameLayout(context)
        val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0))

        adapter.onBindViewHolder(holder, 0)
        assertEquals(View.VISIBLE, holder.signature().visibility)

        adapter.onBindViewHolder(holder, 1)
        assertEquals(View.GONE, holder.signature().visibility)
        // Остался только разделитель из разметки — текста прошлой подписи в контейнере нет.
        assertEquals(1, holder.signature().childCount)
    }

    /** Свёрнутая шапка темы прячет всё содержимое карточки, включая подпись. */
    @Test
    fun `collapsed topic hat hides the signature too`() {
        adapter.submitList(listOf(item(postId = 7, signature = "Подпись")))
        adapter.setTopicHat(postId = 7, collapsed = true)
        val parent = FrameLayout(context)
        val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)

        assertEquals(View.GONE, holder.signature().visibility)
    }

    // region helpers

    private fun bind(item: NativePostItem): TopicPostsAdapter.PostViewHolder {
        adapter.submitList(listOf(item))
        val parent = FrameLayout(context)
        val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        return holder
    }

    private fun TopicPostsAdapter.PostViewHolder.signature(): LinearLayout =
            itemView.findViewById(R.id.native_post_signature_container)

    private fun TopicPostsAdapter.PostViewHolder.signatureText(): TextView =
            signature().getChildAt(1) as TextView

    private fun item(postId: Int, signature: String?) = NativePostItem(
            postId = postId,
            topicId = 934059,
            number = postId,
            userId = 100,
            nick = "SomeUser",
            avatarUrl = null,
            group = "Постоянный",
            groupColor = null,
            date = "28.07.26, 10:05",
            reputation = "11",
            userPostCount = 209,
            signatureHtml = signature,
            postRating = null,
            isCurator = false,
            isOnline = false,
            blocks = PostBodyRenderer().render("<p>Текст поста</p>"),
            rawBodyHtml = "<p>Текст поста</p>",
            canEdit = false,
            canDelete = false,
            canQuote = true,
            canReport = false,
            canPlusRep = false,
            canMinusRep = false,
            canPlusPostRating = false,
            canMinusPostRating = false,
    )

    private val noopLinkHandler = object : ILinkHandler {
        override fun handle(inputUrl: String?, router: TabRouter?, args: Map<String, String>) = true
        override fun handle(inputUrl: String?, router: TabRouter?) = true
        override fun findScreen(url: String): String? = null
    }

    private val noopListener = object : TopicPostsAdapter.PostActionListener {
        override fun onVote(item: NativePostItem, up: Boolean) = Unit
        override fun onReply(item: NativePostItem) = Unit
        override fun onQuote(item: NativePostItem) = Unit
        override fun onQuoteSelection(item: NativePostItem, selectedText: String) = Unit
        override fun onEdit(item: NativePostItem) = Unit
        override fun onDelete(item: NativePostItem) = Unit
        override fun onReputation(item: NativePostItem) = Unit
        override fun onReputationLongPress(item: NativePostItem, up: Boolean) = Unit
        override fun onAvatarClick(item: NativePostItem) = Unit
        override fun onPostMenu(item: NativePostItem) = Unit
        override fun onToggleHat() = Unit
        override fun onToggleLowRatedPost(item: NativePostItem, expand: Boolean) = Unit
        override fun onSpoilerCopyLink(item: NativePostItem, spoilNumber: Int) = Unit
        override fun onImageClick(galleryUrls: List<String>, index: Int) = Unit
        override fun onImageLongClick(imageUrl: String) = Unit
        override fun onDownloadLinkLongPress(url: String, fileName: String?) = Unit
        override fun onDownloadLinkTap(url: String, fileName: String?) = Unit
        override fun onLinkLongClick(url: String) = Unit
        override fun onContentLinkTap(sourcePostId: Int, url: String) = Unit
    }

    // endregion
}
