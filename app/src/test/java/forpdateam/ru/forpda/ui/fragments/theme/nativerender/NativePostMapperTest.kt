package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import forpdateam.ru.forpda.entity.remote.theme.IThemePost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [NativePostMapper] tests — pure JVM via a fake [IThemePost] (the Android-free interface),
 * no Robolectric. Verifies the stable-id contract, field carry-over, and that body HTML is
 * segmented through [PostBodyRenderer].
 */
class NativePostMapperTest {

    private val mapper = NativePostMapper()

    @Test
    fun stableId_equalsPostId() {
        val item = mapper.map(fakePost(id = 140711020))
        assertEquals(140711020, item.postId)
        assertEquals(140711020L, item.stableId)
    }

    @Test
    fun headerAndPermissionFields_carryOverVerbatim() {
        val post = fakePost(
            id = 5,
            nick = "SnapshotUser",
            avatar = "https://4pda.to/av.png",
            group = "Постоянный",
            groupColor = "#FF9900",
            date = "20.05.2026",
            number = 21,
            canEdit = true,
            canQuote = true,
            canDelete = false,
        )
        val item = mapper.map(post)
        assertEquals("SnapshotUser", item.nick)
        assertEquals("https://4pda.to/av.png", item.avatarUrl)
        assertEquals("Постоянный", item.group)
        assertEquals("#FF9900", item.groupColor)
        assertEquals("20.05.2026", item.date)
        assertEquals(21, item.number)
        assertTrue(item.canEdit)
        assertTrue(item.canQuote)
        assertTrue(!item.canDelete)
    }

    @Test
    fun body_isSegmentedIntoBlocks() {
        val post = fakePost(
            id = 1,
            body = "Текст <b>жирный</b><div class=\"post-block spoil close\">" +
                    "<div class=\"block-title\">t</div><div class=\"block-body\">s</div></div>",
        )
        val item = mapper.map(post)
        // one leading Text block + one native spoiler (title "t", inner text "s")
        assertEquals(2, item.blocks.size)
        assertTrue(item.blocks[0] is BodyBlock.Text)
        val spoiler = item.blocks[1] as BodyBlock.Spoiler
        assertEquals("t", spoiler.title)
        assertTrue(spoiler.inner.any { it is BodyBlock.Text && it.html.contains("s") })
    }

    @Test
    fun nullBody_yieldsNoBlocks() {
        assertEquals(emptyList<BodyBlock>(), mapper.map(fakePost(id = 1, body = null)).blocks)
    }

    @Test
    fun listMap_preservesOrderAndCount() {
        val items = mapper.map(listOf(fakePost(id = 10), fakePost(id = 11), fakePost(id = 12)))
        assertEquals(listOf(10, 11, 12), items.map { it.postId })
    }

    @Suppress("LongParameterList")
    private fun fakePost(
        id: Int,
        number: Int = 0,
        userId: Int = 0,
        nick: String? = null,
        avatar: String? = null,
        group: String? = null,
        groupColor: String? = "black",
        date: String? = null,
        reputation: String? = null,
        postRating: String? = null,
        isCurator: Boolean = false,
        isOnline: Boolean = false,
        body: String? = "",
        canEdit: Boolean = false,
        canDelete: Boolean = false,
        canQuote: Boolean = false,
        canReport: Boolean = false,
        canPlusRep: Boolean = false,
        canMinusRep: Boolean = false,
        canPlusPostRating: Boolean = false,
        canMinusPostRating: Boolean = false,
    ): IThemePost = object : IThemePost {
        override val topicId = 0
        override val forumId = 0
        override val id = id
        override val date = date
        override val number = number
        override val avatar = avatar
        override val nick = nick
        override val groupColor = groupColor
        override val group = group
        override val userId = userId
        override val reputation = reputation
        override val postRating = postRating
        override val canMinusPostRating = canMinusPostRating
        override val canPlusPostRating = canPlusPostRating
        override val body = body
        override val isCurator = isCurator
        override val isOnline = isOnline
        override val canMinusRep = canMinusRep
        override val canPlusRep = canPlusRep
        override val canReport = canReport
        override val canEdit = canEdit
        override val canDelete = canDelete
        override val canQuote = canQuote
    }
}
