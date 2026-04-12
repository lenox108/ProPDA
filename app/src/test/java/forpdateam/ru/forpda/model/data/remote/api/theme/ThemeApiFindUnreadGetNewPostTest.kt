package forpdateam.ru.forpda.model.data.remote.api.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeApiFindUnreadGetNewPostTest {

    @Test
    fun findsDataPostIdWithUnreadClass() {
        val html = """<div data-post-id="555" class="post_wrap something unread foo">""" +
                """<a name="entry555"></a></div>"""
        assertEquals(555, ThemeApi.findUnreadPostEntryIdForGetNewPost(html))
    }

    @Test
    fun findsUnreadClassThenDataPost() {
        val html = """<div class="unread" data-post="777">x</div>"""
        assertEquals(777, ThemeApi.findUnreadPostEntryIdForGetNewPost(html))
    }

    @Test
    fun findsIpsCommentUnreadBeforeEntry() {
        val html = """<div class="ipsComment_unread ipsType_blendBackground">""" +
                """<div class="inner"><a name="entry888"></a></div></div>"""
        assertEquals(888, ThemeApi.findUnreadPostEntryIdForGetNewPost(html))
    }

    @Test
    fun findsIsUnreadTrueBeforeEntry() {
        val html = """<div isUnread="true"><a name="entry999"></a></div>"""
        assertEquals(999, ThemeApi.findUnreadPostEntryIdForGetNewPost(html))
    }

    @Test
    fun returnsNullWhenNoUnreadMarker() {
        val html = """<div class="post"><a name="entry111"></a></div>"""
        assertNull(ThemeApi.findUnreadPostEntryIdForGetNewPost(html))
    }

    @Test
    fun scanFindsSecondPostWhenUnreadOnlyInClassLookback() {
        val html = """<div class="read"><a name="entry1"></a></div><div class="post unread"><a name="entry2"></a></div>"""
        assertEquals(2, ThemeApi.findUnreadPostEntryIdForGetNewPost(html))
    }

    @Test
    fun scanUsesLooseAttrUnreadBeforeAnchor() {
        val html = """<div class="post_wrap subtle unread"><a name="entry7"></a></div>"""
        assertEquals(7, ThemeApi.findUnreadPostEntryIdForGetNewPost(html))
    }

    @Test
    fun markerListPicksEarliestUnreadInHtmlNotFirstRegexOrder() {
        val html = """<div class="unread"><a name="entry111"></a></div>""" +
                "x".repeat(400) +
                """<div class="post_wrap unread"><a name="entry222"></a></div>"""
        assertEquals(111, ThemeApi.findUnreadPostEntryIdForGetNewPost(html))
    }
}
