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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Проверяет РЕНДЕР свёрнутого заминусованного поста: вместо шапки/тела/действий показывается
 * однострочная плашка, а сам элемент остаётся в списке.
 *
 * Живьём это на тестовом аккаунте не проверить — карма гейтится аккаунтом, и в HTML, который качает
 * клиент, `postRating` там пуст у всех постов (`post-rating-ka-data-absent`). Поэтому фиксируем
 * поведение здесь, на реальном биндинге адаптера.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class TopicPostsAdapterLowRatingTest {

    private val context: Context by lazy {
        ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.DayNightAppTheme)
    }

    private lateinit var imageLoader: ImageLoader
    private lateinit var adapter: TopicPostsAdapter
    private val toggled = mutableListOf<Pair<Int, Boolean>>()

    @Before
    fun setUp() {
        imageLoader = ImageLoader.Builder(context.applicationContext).build()
        ForPdaCoil.bindImageLoaderForTest(imageLoader)
        adapter = TopicPostsAdapter(noopLinkHandler, recordingListener)
        adapter.setAuthContext(authorized = true, memberId = 777)
        adapter.setDisplaySettings(TopicPostsAdapter.PostDisplaySettings(
                hideLowRatedPosts = true,
                lowRatingThreshold = -3,
        ))
    }

    @After
    fun tearDown() {
        imageLoader.shutdown()
    }

    @Test
    fun `post at threshold collapses into a stub instead of its content`() {
        val holder = bind(item(postId = 1, number = 413, nick = "Гость_77", postRating = "-4"))

        assertEquals(View.VISIBLE, holder.stub().visibility)
        assertEquals(View.GONE, holder.header().visibility)
        assertEquals(View.GONE, holder.body().visibility)
        assertEquals(View.GONE, holder.actions().visibility)
        // Тело не просто спрятано — оно вообще не построено (иначе сворачивание не экономило бы разметку).
        assertEquals(0, holder.body().childCount)
        // Только ник — номер поста в плашке не показываем (в шапке поста он тоже скрыт).
        assertEquals("Гость_77", holder.stubLabel().text.toString())
        assertEquals("-4", holder.stubValue().text.toString())
    }

    @Test
    fun `post above threshold renders normally`() {
        val holder = bind(item(postId = 2, postRating = "-2"))

        assertEquals(View.GONE, holder.stub().visibility)
        assertEquals(View.VISIBLE, holder.header().visibility)
        assertEquals(View.VISIBLE, holder.body().visibility)
    }

    @Test
    fun `post without a rating renders normally even at threshold minus one`() {
        adapter.setDisplaySettings(TopicPostsAdapter.PostDisplaySettings(
                hideLowRatedPosts = true,
                lowRatingThreshold = -1,
        ))
        val holder = bind(item(postId = 3, postRating = null))

        assertEquals(View.GONE, holder.stub().visibility)
        assertEquals(View.VISIBLE, holder.header().visibility)
    }

    @Test
    fun `own downvoted post is never collapsed`() {
        val holder = bind(item(postId = 4, userId = 777, postRating = "-9"))

        assertEquals(View.GONE, holder.stub().visibility)
        assertEquals(View.VISIBLE, holder.header().visibility)
    }

    @Test
    fun `topic hat is never collapsed`() {
        adapter.submitList(listOf(item(postId = 5, postRating = "-9")))
        adapter.setTopicHat(postId = 5, collapsed = false)
        val holder = bindAt(0)

        assertEquals(View.GONE, holder.stub().visibility)
        assertEquals(View.VISIBLE, holder.header().visibility)
    }

    @Test
    fun `tapping the stub reports an expand request and expanding reveals the post`() {
        val post = item(postId = 6, postRating = "-5")
        bind(post).stub().performClick()

        assertEquals(listOf(6 to true), toggled)

        // Хост отвечает на колбэк раскрытием — пост показывается целиком и получает «Свернуть».
        adapter.expandLowRatedPost(6)
        val holder = bindAt(0)
        assertEquals(View.GONE, holder.stub().visibility)
        assertEquals(View.VISIBLE, holder.header().visibility)
        assertNotNull(holder.findAction(context.getString(R.string.low_rated_post_hide)))
    }

    @Test
    fun `highlighted post is expanded so a link landing never shows a stub`() {
        adapter.submitList(listOf(item(postId = 7, postRating = "-8")))
        adapter.requestHighlight(7)
        val holder = bindAt(0)

        assertEquals(View.GONE, holder.stub().visibility)
        assertEquals(View.VISIBLE, holder.header().visibility)
        // Путь назад есть и у авто-раскрытого поста: иначе свернуть его снова было бы нечем.
        assertNotNull(holder.findAction(context.getString(R.string.low_rated_post_hide)))
    }

    @Test
    fun `collapsing back returns the stub`() {
        adapter.submitList(listOf(item(postId = 8, postRating = "-5")))
        adapter.expandLowRatedPost(8)
        adapter.collapseLowRatedPost(8)
        val holder = bindAt(0)

        assertEquals(View.VISIBLE, holder.stub().visibility)
        assertEquals(View.GONE, holder.header().visibility)
    }

    @Test
    fun `feature off keeps every post expanded`() {
        adapter.setDisplaySettings(TopicPostsAdapter.PostDisplaySettings(hideLowRatedPosts = false))
        val holder = bind(item(postId = 9, postRating = "-42"))

        assertEquals(View.GONE, holder.stub().visibility)
        assertEquals(View.VISIBLE, holder.header().visibility)
    }

    // region helpers

    private fun bind(item: NativePostItem): TopicPostsAdapter.PostViewHolder {
        adapter.submitList(listOf(item))
        return bindAt(0)
    }

    private fun bindAt(position: Int): TopicPostsAdapter.PostViewHolder {
        val parent = FrameLayout(context)
        val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(position))
        adapter.onBindViewHolder(holder, position)
        return holder
    }

    private fun TopicPostsAdapter.PostViewHolder.stub(): View =
            itemView.findViewById(R.id.native_post_low_rating_stub)

    private fun TopicPostsAdapter.PostViewHolder.stubLabel(): TextView =
            itemView.findViewById(R.id.native_post_low_rating_label)

    private fun TopicPostsAdapter.PostViewHolder.stubValue(): TextView =
            itemView.findViewById(R.id.native_post_low_rating_value)

    private fun TopicPostsAdapter.PostViewHolder.header(): View =
            itemView.findViewById(R.id.native_post_header)

    private fun TopicPostsAdapter.PostViewHolder.body(): LinearLayout =
            itemView.findViewById(R.id.native_post_body)

    private fun TopicPostsAdapter.PostViewHolder.actions(): LinearLayout =
            itemView.findViewById(R.id.native_post_actions)

    /** Ищет действие в подвале поста по подписи (подвал строится кодом, а не разметкой). */
    private fun TopicPostsAdapter.PostViewHolder.findAction(label: String): View? {
        val row = actions()
        return (0 until row.childCount)
                .map { row.getChildAt(it) }
                .firstOrNull { (it as? TextView)?.text?.toString() == label }
    }

    private fun item(
            postId: Int,
            number: Int = postId,
            userId: Int = 100,
            nick: String = "SomeUser",
            postRating: String? = null,
    ) = NativePostItem(
            postId = postId,
            topicId = 934059,
            number = number,
            userId = userId,
            nick = nick,
            avatarUrl = null,
            group = "Постоянный",
            groupColor = null,
            date = "28.07.26, 10:05",
            reputation = "11",
            userPostCount = 209,
            postRating = postRating,
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

    private val recordingListener = object : TopicPostsAdapter.PostActionListener {
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
        override fun onToggleLowRatedPost(item: NativePostItem, expand: Boolean) {
            toggled.add(item.postId to expand)
        }
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
