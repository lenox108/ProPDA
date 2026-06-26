package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.entity.remote.theme.ThemePost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicPrependedHatPolicyTest {

    @Test
    fun `detectPrependedHat finds hat when first post number is below page window`() {
        val page = deepPage(
                posts = listOf(
                        hatPost(id = 999, number = 0),
                        regularPost(id = 143179849, number = 1141),
                )
        )

        val detected = TopicPrependedHatPolicy.detectPrependedHat(page)

        assertNotNull(detected)
        assertEquals(999, detected?.id)
    }

    @Test
    fun `stripFromNonFirstPage removes prepended hat by number window`() {
        val page = deepPage(
                posts = listOf(
                        hatPost(id = 999, number = 0),
                        regularPost(id = 143179849, number = 1141),
                        regularPost(id = 143179850, number = 1142),
                )
        )

        val kept = TopicPrependedHatPolicy.stripFromNonFirstPage(
                page = page,
                requestedPage = 58,
                knownHatId = null,
        )

        assertTrue(kept)
        assertEquals(2, page.posts.size)
        assertEquals(1141, page.posts.first().number)
    }

    @Test
    fun `stripFromNonFirstPage removes known hat id even when number parsing is stale`() {
        val page = deepPage(
                posts = listOf(
                        hatPost(id = 135617646, number = 1141),
                        regularPost(id = 143179849, number = 1142),
                )
        )

        val kept = TopicPrependedHatPolicy.stripFromNonFirstPage(
                page = page,
                requestedPage = 58,
                knownHatId = 135617646,
        )

        assertTrue(kept)
        assertEquals(1, page.posts.size)
        assertEquals(143179849, page.posts.first().id)
    }

    @Test
    fun `detectPrependedHat keeps classic number one heuristic`() {
        val page = deepPage(
                posts = listOf(
                        hatPost(id = 100, number = 1),
                        regularPost(id = 200, number = 1141),
                )
        )

        assertNotNull(TopicPrependedHatPolicy.detectPrependedHat(page))
    }

    @Test
    fun `detectPrependedHat ignores in-window first post on deep page`() {
        val page = deepPage(
                posts = listOf(
                        regularPost(id = 143179849, number = 1141),
                        regularPost(id = 143179850, number = 1142),
                )
        )

        assertNull(TopicPrependedHatPolicy.detectPrependedHat(page))
    }

    @Test
    fun `resolvePrependedHatId finds hat by scroll anchor on deep page`() {
        val page = page1212WithPrependedHat()

        val resolved = TopicPrependedHatPolicy.resolvePrependedHatId(
                page = page,
                requestedPage = 1212,
                knownHatId = null,
        )

        assertEquals(135617646, resolved)
    }

    @Test
    fun `stripFromNonFirstPage removes hat matched by anchor on page 1212`() {
        val page = page1212WithPrependedHat()

        val kept = TopicPrependedHatPolicy.stripFromNonFirstPage(
                page = page,
                requestedPage = 1212,
                knownHatId = null,
        )

        assertTrue(kept)
        assertEquals(9, page.posts.size)
        assertEquals(143793504, page.posts.first().id)
        assertFalse(page.posts.any { it.id == 135617646 })
    }

    @Test
    fun `resolvePrependedHatId finds ProPDA hat when number is inside page window`() {
        val page = propdaPage58WithInWindowHat()

        val resolved = TopicPrependedHatPolicy.resolvePrependedHatId(
                page = page,
                requestedPage = 58,
                knownHatId = null,
        )

        assertEquals(143179849, resolved)
    }

    @Test
    fun `stripFromNonFirstPage removes ProPDA hat when number is inside page window`() {
        val page = propdaPage58WithInWindowHat()

        val kept = TopicPrependedHatPolicy.stripFromNonFirstPage(
                page = page,
                requestedPage = 58,
                knownHatId = null,
        )

        assertTrue(kept)
        assertEquals(10, page.posts.size)
        assertEquals(143784670, page.posts.first().id)
        assertFalse(page.posts.any { it.id == 143179849 })
    }

    @Test
    fun `stripFromNonFirstPage removes appended ProPDA hat at end of list`() {
        val page = deepPage(
                posts = listOf(
                        regularPost(id = 143784670, number = 1150),
                        regularPost(id = 143784679, number = 1151),
                        propdaHatPost(id = 143179849, number = 1),
                )
        )

        val kept = TopicPrependedHatPolicy.stripFromNonFirstPage(
                page = page,
                requestedPage = 58,
                knownHatId = null,
        )

        assertTrue(kept)
        assertEquals(2, page.posts.size)
        assertFalse(page.posts.any { it.id == 143179849 })
    }

    @Test
    fun `stripFromNonFirstPage removes known hat id anywhere in list`() {
        val page = deepPage(
                posts = listOf(
                        regularPost(id = 143179849, number = 1141),
                        hatPost(id = 135617646, number = 1141),
                        regularPost(id = 143179850, number = 1142),
                )
        )

        val kept = TopicPrependedHatPolicy.stripFromNonFirstPage(
                page = page,
                requestedPage = 58,
                knownHatId = 135617646,
        )

        assertTrue(kept)
        assertEquals(2, page.posts.size)
        assertFalse(page.posts.any { it.id == 135617646 })
    }

    @Test
    fun `resolvePrependedHatId finds in-window hat by topic title on page 1215`() {
        val page = page1215WithPrependedHat()

        val resolved = TopicPrependedHatPolicy.resolvePrependedHatId(
                page = page,
                requestedPage = 1215,
                knownHatId = null,
        )

        assertEquals(135617646, resolved)
    }

    @Test
    fun `stripFromNonFirstPage removes in-window hat by topic title on page 1215`() {
        val page = page1215WithPrependedHat()

        val kept = TopicPrependedHatPolicy.stripFromNonFirstPage(
                page = page,
                requestedPage = 1215,
                knownHatId = null,
        )

        assertTrue(kept)
        assertEquals(9, page.posts.size)
        assertEquals(143793504, page.posts.first().id)
        assertEquals(24282, page.posts.first().number)
        assertFalse(page.posts.any { it.id == 135617646 })
    }

    @Test
    fun `stripFromNonFirstPage removes hat beside anchor on sparse page 1216`() {
        val page = page1216WithHatBesideAnchor()

        val kept = TopicPrependedHatPolicy.stripFromNonFirstPage(
                page = page,
                requestedPage = 1216,
                knownHatId = null,
        )

        assertTrue(kept)
        assertEquals(1, page.posts.size)
        assertEquals(143797927, page.posts.first().id)
        assertFalse(page.posts.any { it.id == 135617646 })
    }

    @Test
    fun `filterPostsForPageList excludes topic header on page 1 when inline block renders`() {
        val page = ThemePage().apply {
            id = 1121483
            url = "https://4pda.to/forum/index.php?showtopic=1121483"
            pagination.current = 1
            topicHatPost = ThemePost().apply {
                id = 143179849
                number = 1
                body = "ProPDA hat"
            }
            posts.add(topicHatPost!!)
            posts.add(ThemePost().apply {
                id = 142886283
                number = 2
                body = "Reply"
            })
        }

        val filtered = TopicPrependedHatPolicy.filterPostsForPageList(
                page = page,
                requestedPage = 1,
                knownHatId = 143179849,
        )

        assertEquals(1, filtered.size)
        assertEquals(142886283, filtered.first().id)
    }

    @Test
    fun `filterPostsForPageList excludes topic header on page 1 when topicHatPost is set`() {
        val page = ThemePage().apply {
            id = 1121483
            url = "https://4pda.to/forum/index.php?showtopic=1121483"
            pagination.current = 1
            topicHatPost = ThemePost().apply {
                id = 143179849
                number = 1
            }
            posts.add(topicHatPost!!)
            posts.add(ThemePost().apply { id = 142886283; number = 2 })
        }

        val filtered = TopicPrependedHatPolicy.filterPostsForPageList(
                page = page,
                requestedPage = 1,
                knownHatId = 143179849,
        )

        assertEquals(1, filtered.size)
        assertEquals(142886283, filtered.first().id)
    }

    @Test
    fun `filterPostsForPageList excludes ProPDA hat heuristic on page 1`() {
        val page = ThemePage().apply {
            id = 1121483
            title = "ProPDA"
            url = "https://4pda.to/forum/index.php?showtopic=1121483"
            pagination.current = 1
            posts.add(ThemePost().apply {
                id = 135617646
                number = 1
                nick = "Lenox30"
                body = "<b>ProPDA</b><br/>Версия: 2.9.1"
            })
            posts.add(ThemePost().apply { id = 142886283; number = 2; body = "Reply" })
        }

        val filtered = TopicPrependedHatPolicy.filterPostsForPageList(page = page, requestedPage = 1)

        assertEquals(1, filtered.size)
        assertEquals(142886283, filtered.first().id)
    }

    @Test
    fun `filterPostsForPageList excludes hat beside anchor on page 1216`() {
        val page = page1216WithHatBesideAnchor()

        val filtered = TopicPrependedHatPolicy.filterPostsForPageList(
                page = page,
                requestedPage = 1216,
                knownHatId = null,
        )

        assertEquals(1, filtered.size)
        assertEquals(143797927, filtered.first().id)
    }

    @Test
    fun `resolvePrependedHatId finds hat beside anchor on page 1216`() {
        val page = page1216WithHatBesideAnchor()

        val resolved = TopicPrependedHatPolicy.resolvePrependedHatId(
                page = page,
                requestedPage = 1216,
                knownHatId = null,
        )

        assertEquals(135617646, resolved)
    }

    private fun page1216WithHatBesideAnchor(): ThemePage =
            ThemePage().apply {
                id = 1103268
                title = "Обсуждение OnePlus 15"
                url = "https://4pda.to/forum/index.php?showtopic=1103268&st=24300#entry143797927"
                pagination.current = 1216
                pagination.all = 1216
                pagination.perPage = 20
                addAnchor("entry143797927")
                posts.addAll(
                        listOf(
                                deviceHatPost(id = 135617646, number = 24301),
                                regularPost(id = 143797927, number = 24302),
                        )
                )
            }

    private fun page1215WithPrependedHat(): ThemePage =
            ThemePage().apply {
                id = 1103268
                title = "Обсуждение OnePlus 15"
                url = "https://4pda.to/forum/index.php?showtopic=1103268&st=24280"
                pagination.current = 1215
                pagination.all = 1215
                pagination.perPage = 20
                posts.addAll(
                        listOf(
                                deviceHatPost(id = 135617646, number = 24281),
                                regularPost(id = 143793504, number = 24282),
                                regularPost(id = 143793505, number = 24283),
                                regularPost(id = 143793506, number = 24284),
                                regularPost(id = 143793507, number = 24285),
                                regularPost(id = 143793508, number = 24286),
                                regularPost(id = 143793509, number = 24287),
                                regularPost(id = 143793510, number = 24288),
                                regularPost(id = 143793511, number = 24289),
                                regularPost(id = 143793512, number = 24290),
                        )
                )
            }

    private fun deviceHatPost(id: Int, number: Int): ThemePost =
            ThemePost().apply {
                this.id = id
                this.number = number
                nick = "Санёкк"
                body = "Обсуждение OnePlus 15<br>FAQ &raquo;"
            }

    private fun page1212WithPrependedHat(): ThemePage =
            ThemePage().apply {
                id = 1103268
                url = "https://4pda.to/forum/index.php?showtopic=1103268&st=24220#entry143793504"
                pagination.current = 1212
                pagination.all = 1212
                pagination.perPage = 20
                addAnchor("entry135617646")
                posts.addAll(
                        listOf(
                                hatPost(id = 135617646, number = 24221),
                                regularPost(id = 143793504, number = 24222),
                                regularPost(id = 143793505, number = 24223),
                                regularPost(id = 143793506, number = 24224),
                                regularPost(id = 143793507, number = 24225),
                                regularPost(id = 143793508, number = 24226),
                                regularPost(id = 143793509, number = 24227),
                                regularPost(id = 143793510, number = 24228),
                                regularPost(id = 143793511, number = 24229),
                                regularPost(id = 143793512, number = 24230),
                        )
                )
            }

    @Test
    fun `stripFromNonFirstPage keeps the unread anchor post even when its number is below the window`() {
        // Device log 26_06-11-11, topic 1122662 last page (19/19): the server prepends the hat
        // (143681911) and the first-unread anchor (143996702) is the FIRST content post after it, but
        // its sequential `number` is 0 (unreliable on the getnewpost->findpost reload). Without the
        // anchor guard the number-window strip removed 143996702 along with the hat, so the highlight
        // resolver saw it off-page and the open visibly scrolled to the wrong (last) post.
        val page = deepPage(
                posts = listOf(
                        hatPost(id = 143681911, number = 0),
                        regularPost(id = 143996702, number = 0),
                        regularPost(id = 143997335, number = 362),
                        regularPost(id = 143999790, number = 363),
                )
        ).apply {
            pagination.current = 19
            pagination.all = 19
            url = "https://4pda.to/forum/index.php?showtopic=1122662&st=360#entry143996702"
            anchorPostId = "143996702"
            anchors.add("entry143996702")
        }

        val kept = TopicPrependedHatPolicy.stripFromNonFirstPage(
                page = page,
                requestedPage = 19,
                knownHatId = null,
        )

        assertTrue(kept)
        val ids = page.posts.map { it.id }
        assertFalse("the prepended hat must be removed", ids.contains(143681911))
        assertTrue("the unread anchor post must be preserved", ids.contains(143996702))
        assertEquals(143996702, page.posts.first().id)
    }

    @Test
    fun `stripFromNonFirstPage keeps the anchor from page url even when anchors list is cleared`() {
        // Device log 26_06-12-04, topic 461675: a SECOND strip pass (hat-overlay remap / template
        // fragment mapping) runs after page.anchors / anchorPostId have been cleared, so the list-based
        // guard no longer protects the first-unread post and it is re-stripped by the number==0 rule.
        // The redirect hash in page.url (…#entry143885374) is stable across both passes and must keep
        // the anchor on the page.
        val page = deepPage(
                posts = listOf(
                        hatPost(id = 21945584, number = 0),
                        regularPost(id = 143885374, number = 0),
                        regularPost(id = 143885377, number = 372),
                        regularPost(id = 143885461, number = 373),
                )
        ).apply {
            pagination.current = 18584
            pagination.all = 18624
            url = "https://4pda.to/forum/index.php?showtopic=461675&st=371660#entry143885374"
            // anchors intentionally left EMPTY and anchorPostId null — the cleared-state regression.
        }

        val kept = TopicPrependedHatPolicy.stripFromNonFirstPage(
                page = page,
                requestedPage = 18584,
                knownHatId = 21945584,
        )

        assertTrue(kept)
        val ids = page.posts.map { it.id }
        assertFalse("the prepended hat must be removed", ids.contains(21945584))
        assertTrue("the url-anchor post must be preserved even with empty anchors", ids.contains(143885374))
        assertEquals(143885374, page.posts.first().id)
    }

    @Test
    fun `stripFromNonFirstPage with all-zero post numbers keeps anchor and strips only the known hat`() {
        // Device log 26_06-12-14, topic 928862: on this deep page the parser left EVERY post at
        // number==0. The number-based hat heuristic then ate the leading post on each re-strip pass —
        // once the unread anchor became first it was mis-detected as the hat and removed (via
        // `post.id == hatId`, before the anchor guard). With numbers unreliable, only the explicitly
        // known hat must be stripped; all real content (incl. the anchor) stays. No #entry in the url
        // here so the protection comes purely from the number-reliability gate, not the url fallback.
        val page = deepPage(
                posts = listOf(
                        hatPost(id = 78923713, number = 0),
                        regularPost(id = 143862484, number = 0),
                        regularPost(id = 143863252, number = 0),
                        regularPost(id = 143863329, number = 0),
                )
        ).apply {
            pagination.current = 5226
            pagination.all = 5240
            url = "https://4pda.to/forum/index.php?showtopic=928862&st=104500"
        }

        val kept = TopicPrependedHatPolicy.stripFromNonFirstPage(
                page = page,
                requestedPage = 5226,
                knownHatId = 78923713,
        )

        assertTrue(kept)
        val ids = page.posts.map { it.id }
        assertFalse("the known hat must be removed", ids.contains(78923713))
        assertTrue("the unread anchor must survive despite number==0", ids.contains(143862484))
        assertTrue("other content must survive despite number==0", ids.contains(143863252))
        assertEquals(143862484, page.posts.first().id)
    }

    @Test
    fun `stripFromNonFirstPage with all-zero numbers and no known hat does not over-strip content`() {
        // Same all-zero deep page but the hat id is unknown: the number heuristic must NOT guess and
        // eat the leading content posts. We keep everything rather than risk stripping the anchor.
        val page = deepPage(
                posts = listOf(
                        regularPost(id = 143862484, number = 0),
                        regularPost(id = 143863252, number = 0),
                        regularPost(id = 143863329, number = 0),
                )
        ).apply {
            pagination.current = 5226
            pagination.all = 5240
            url = "https://4pda.to/forum/index.php?showtopic=928862&st=104500"
        }

        TopicPrependedHatPolicy.stripFromNonFirstPage(page, requestedPage = 5226, knownHatId = null)

        assertTrue("the first-unread anchor must not be stripped by the number heuristic", page.posts.any { it.id == 143862484 })
        assertEquals(143862484, page.posts.first().id)
    }

    @Test
    fun `resolvePrependedHatId never returns the open anchor and strip keeps it on a re-strip pass`() {
        // Device log 26_06-15-03, topic 1111449: the first strip removes the real hat and the unread
        // anchor (143983265) becomes the first post. A SECOND strip pass (hatMetadataPreload /
        // ThemeTemplate) runs with knownHatId=null and re-resolved the anchor AS the hat, so removeAll
        // stripped it via `post.id == hatId` before the anchor guard. The anchor must never be the hat.
        val page = deepPage(
                posts = listOf(
                        regularPost(id = 143983265, number = 0),
                        regularPost(id = 143984639, number = 0),
                        regularPost(id = 143990913, number = 0),
                )
        ).apply {
            pagination.current = 74
            pagination.all = 74
            url = "https://4pda.to/forum/index.php?showtopic=1111449&st=1460#entry143983265"
        }

        val hatId = TopicPrependedHatPolicy.resolvePrependedHatId(page, requestedPage = 74, knownHatId = null)
        assertTrue("the open anchor must never be resolved as the hat", hatId != 143983265)

        TopicPrependedHatPolicy.stripFromNonFirstPage(page, requestedPage = 74, knownHatId = null)
        assertTrue("the anchor must survive a knownHatId=null re-strip pass", page.posts.any { it.id == 143983265 })
        assertEquals(143983265, page.posts.first().id)
    }

    private fun deepPage(posts: List<ThemePost>): ThemePage =
            ThemePage().apply {
                id = 1121483
                url = "https://4pda.to/forum/index.php?showtopic=1121483&st=1140"
                pagination.current = 58
                pagination.all = 58
                pagination.perPage = 20
                this.posts.addAll(posts)
            }

    private fun propdaPage58WithInWindowHat(): ThemePage =
            deepPage(
                    posts = listOf(
                            propdaHatPost(id = 143179849, number = 1141),
                            regularPost(id = 143784670, number = 1150),
                            regularPost(id = 143784679, number = 1151),
                            regularPost(id = 143784680, number = 1152),
                            regularPost(id = 143784681, number = 1153),
                            regularPost(id = 143784682, number = 1154),
                            regularPost(id = 143784683, number = 1155),
                            regularPost(id = 143784684, number = 1156),
                            regularPost(id = 143784685, number = 1157),
                            regularPost(id = 143784686, number = 1158),
                            regularPost(id = 143784687, number = 1159),
                    )
            )

    private fun propdaHatPost(id: Int, number: Int): ThemePost =
            ThemePost().apply {
                this.id = id
                this.number = number
                nick = "Lenox30"
                body = "ProPDA<br>Версия: 2.9.1"
            }

    private fun hatPost(id: Int, number: Int): ThemePost =
            ThemePost().apply {
                this.id = id
                this.number = number
                nick = "Lenox30"
                body = "ProPDA"
            }

    private fun regularPost(id: Int, number: Int): ThemePost =
            ThemePost().apply {
                this.id = id
                this.number = number
                nick = "User"
                body = "Reply"
            }
}
