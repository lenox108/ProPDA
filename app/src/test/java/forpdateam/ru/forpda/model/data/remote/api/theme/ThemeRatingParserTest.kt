package forpdateam.ru.forpda.model.data.remote.api.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeRatingParserTest {

    @Test
    fun parsesRussianRatingsByEntryId() {
        val html = """
            <div class="post" id="entry111">
                <div class="post_body">body</div>
                <div class="post_footer">Рейтинг: +12</div>
            </div>
            <div class="post" id="entry222">
                <div class="post_body">body</div>
                <div class="post_footer">Рейтинг: −3</div>
            </div>
        """.trimIndent()

        assertEquals(mapOf(111 to "+12", 222 to "-3"), ThemeRatingParser.parsePostRatings(html))
    }

    @Test
    fun parsesEnglishRatingsByDataPost() {
        val html = """
            <article data-post="333">
                <section>message</section>
                <footer>Rating: 0</footer>
            </article>
        """.trimIndent()

        assertEquals(mapOf(333 to "0"), ThemeRatingParser.parsePostRatings(html))
    }

    @Test
    fun parsesRatingFromElementAttributesBeforeTagsAreStripped() {
        val html = """
            <div class="post" id="entry444">
                <a class="post_rating" title="Рейтинг поста: +7" href="#">rate</a>
            </div>
        """.trimIndent()

        assertEquals(mapOf(444 to "+7"), ThemeRatingParser.parsePostRatings(html))
    }

    @Test
    fun parsesRatingFromKnownRatingElement() {
        val html = """
            <div class="post" id="entry555">
                <span id="ka_555">−2</span>
            </div>
        """.trimIndent()

        assertEquals(mapOf(555 to "-2"), ThemeRatingParser.parsePostRatings(html))
    }

    @Test
    fun parsesRatingNearVoteControlsWithoutUsingUserReputation() {
        val html = """
            <div class="post" id="entry666">
                Реп: (<span data-member-rep="42">157</span>)
                <a href="https://4pda.to/forum/zka.php?i=666&amp;v=1">plus</a>
                <span class="post-rating">+3</span>
                <a href="https://4pda.to/forum/zka.php?i=666&amp;v=-1">minus</a>
            </div>
        """.trimIndent()

        assertEquals(mapOf(666 to "+3"), ThemeRatingParser.parsePostRatings(html))
    }

    @Test
    fun parsesRatingFromPostActionWithoutRatingLabel() {
        val html = """
            <div data-post="777">
                <a name="entry777"></a>
                <span class="post_action">
                    <a href="/forum/index.php?act=rep&type=post&i=777&v=1">+</a>
                    <span class="vote_total">+12</span>
                    <a href="/forum/index.php?act=rep&type=post&i=777&v=-1">-</a>
                </span>
            </div>
        """.trimIndent()

        assertEquals(mapOf(777 to "+12"), ThemeRatingParser.parsePostRatings(html))
    }

    @Test
    fun parsesPostVoteControlsFromPostAction() {
        val html = """
            <div data-post="778">
                <a name="entry778"></a>
                <span class="post_action">
                    <a href="/forum/index.php?act=rep&type=post&i=778&v=1">+</a>
                    <span class="vote_total">0</span>
                    <a href="/forum/index.php?act=rep&type=post&i=778&v=-1">-</a>
                </span>
            </div>
        """.trimIndent()

        assertEquals(
                mapOf(778 to ThemeRatingParser.PostVoteControls(canPlus = true, canMinus = true)),
                ThemeRatingParser.parsePostVoteControls(html)
        )
    }

    @Test
    fun ignoresProfileReputationControlsWhenParsingPostVoteControls() {
        val html = """
            <div class="post" id="entry779">
                <span class="post_user_info">
                    <a href="/forum/index.php?act=rep&amp;mid=42&amp;p=779&amp;type=win_minus">-</a>
                    (<a href="/forum/index.php?showuser=42"><span data-reputation="544">544</span></a>)
                    <a href="/forum/index.php?act=rep&amp;mid=42&amp;p=779&amp;type=win_add">+</a>
                </span>
                <span class="post_action">
                    <a href="/forum/index.php?act=report&amp;p=779">report</a>
                </span>
            </div>
        """.trimIndent()

        assertEquals(emptyMap<Int, ThemeRatingParser.PostVoteControls>(), ThemeRatingParser.parsePostVoteControls(html))
    }

    @Test
    fun parsesRatingFromPlainDataRatingAttribute() {
        val html = """
            <div data-post="888">
                <a name="entry888"></a>
                <span class="post_action" data-rating="−9"></span>
            </div>
        """.trimIndent()

        assertEquals(mapOf(888 to "-9"), ThemeRatingParser.parsePostRatings(html))
    }

    @Test
    fun ignoresUserReputationDataAttributesWhenResolvingVoteArea() {
        val html = """
            <div data-post="999">
                <a name="entry999"></a>
                <div class="post_user_info">
                    <span data-reputation="544">x</span>
                </div>
                <a href="https://4pda.to/forum/zka.php?i=999&amp;v=1">+</a>
                <span class="vote_total"><b>+7</b></span>
                <a href="https://4pda.to/forum/zka.php?i=999&amp;v=-1">-</a>
            </div>
        """.trimIndent()

        assertEquals(mapOf(999 to "+7"), ThemeRatingParser.parsePostRatings(html))
    }

    @Test
    fun prefersPostVoteTotalOverDesktopProfileReputation() {
        val html = """
            <div class="post" id="entry143230576">
                <span class="post_user_info">
                    <a href="#" onclick="return win_minus({i:143230576})">-</a>
                    (<a href="/forum/index.php?showuser=42"><span data-reputation="544">544</span></a>)
                    <a href="#" onclick="return win_add({i:143230576})">+</a>
                </span>
                <span class="post_action">
                    <a href="https://4pda.to/forum/zka.php?i=143230576&amp;v=1">+</a>
                    <span class="vote_total">0</span>
                    <a href="https://4pda.to/forum/zka.php?i=143230576&amp;v=-1">-</a>
                </span>
            </div>
        """.trimIndent()

        assertEquals(mapOf(143230576 to "0"), ThemeRatingParser.parsePostRatings(html))
    }

    @Test
    fun parsesDesktopWinReputationPostRatingLabel() {
        val html = """
            <div class="post" id="entry143230576">
                <span class="post_user_info">
                    Реп: (<a href="/forum/index.php?act=rep&amp;view=history&amp;mid=1812505"><span data-member-rep="1812505">544</span></a>)
                </span>
                <span class="post_action">
                    <a href="/forum/index.php?act=rep&amp;mid=1812505&amp;p=143230576&amp;type=win_minus">-</a>
                    <span>Рейтинг: <b>+1</b></span>
                    <a href="/forum/index.php?act=rep&amp;mid=1812505&amp;p=143230576&amp;type=win_add">+</a>
                </span>
            </div>
        """.trimIndent()

        assertEquals(mapOf(143230576 to "+1"), ThemeRatingParser.parsePostRatings(html))
        assertEquals(2, ThemeRatingParser.countVoteControlMarkers(html))
    }

    @Test
    fun ignoresDesktopProfileReputationControlsWithoutPostVoteTotal() {
        val html = """
            <div class="post" id="entry143230577">
                <span class="post_user_info">
                    <a href="/forum/index.php?act=rep&amp;mid=42&amp;p=143230577&amp;type=win_minus">-</a>
                    (<a href="/forum/index.php?showuser=42"><span data-reputation="544">544</span></a>)
                    <a href="/forum/index.php?act=rep&amp;mid=42&amp;p=143230577&amp;type=win_add">+</a>
                </span>
                <span class="post_action">
                    <a href="/forum/index.php?act=report&amp;p=143230577">report</a>
                </span>
            </div>
        """.trimIndent()

        assertEquals(emptyMap<Int, String>(), ThemeRatingParser.parsePostRatings(html))
    }

    @Test
    fun parsesRatingsFromKaPJsMapWhenKaDivsAreEmpty() {
        val html = """
            <script>
                ka_p={143237068:[0,-1,true],143246852:[0,7,true]};
                ka_u=0; ka_h=-5; ka_init();
            </script>
            <div class="post" id="entry143237068">
                <div id="ka_143237068" class="ka"></div>
                Реп: (<span data-member-rep="1812505">544</span>)
            </div>
            <div class="post" id="entry143246852">
                <div id="ka_143246852" class="ka"></div>
            </div>
        """.trimIndent()

        assertEquals(
                mapOf(143237068 to "-1", 143246852 to "+7"),
                ThemeRatingParser.parsePostRatings(html)
        )
    }

    @Test
    fun parsesPostVoteControlsFromKaPJsMapWithoutVisibleLinks() {
        val html = """
            <script>
                ka_p={143237068:[0,0,true],143246852:[0,7,false],143246853:[0,-1,1]};
                ka_u=0; ka_h=-5; ka_init();
            </script>
            <div class="post" id="entry143237068"><div id="ka_143237068" class="ka"></div></div>
            <div class="post" id="entry143246852"><div id="ka_143246852" class="ka"></div></div>
            <div class="post" id="entry143246853"><div id="ka_143246853" class="ka"></div></div>
        """.trimIndent()

        assertEquals(
                mapOf(
                        143237068 to ThemeRatingParser.PostVoteControls(canPlus = true, canMinus = true),
                        143246853 to ThemeRatingParser.PostVoteControls(canPlus = true, canMinus = true)
                ),
                ThemeRatingParser.parsePostVoteControls(html)
        )
    }

    @Test
    fun parsesRatingsFromTwoFieldKaPJsMap() {
        val html = """
            <script>
                ka_p={143237068:[0,-1],143246852:[0,7]};
            </script>
        """.trimIndent()

        assertEquals(
                mapOf(143237068 to "-1", 143246852 to "+7"),
                ThemeRatingParser.parsePostRatings(html)
        )
    }

    @Test
    fun parsesPostVoteControlsFromTwoFieldKaPJsMap() {
        val html = """
            <script>
                ka_p={143237068:[0,-1],143246852:[0,7]};
                ka_u=0; ka_h=-5; ka_init();
            </script>
            <div class="post" id="entry143237068"><div id="ka_143237068" class="ka"></div></div>
            <div class="post" id="entry143246852"><div id="ka_143246852" class="ka"></div></div>
        """.trimIndent()

        assertEquals(
                mapOf(
                        143237068 to ThemeRatingParser.PostVoteControls(canPlus = true, canMinus = true),
                        143246852 to ThemeRatingParser.PostVoteControls(canPlus = true, canMinus = true),
                ),
                ThemeRatingParser.parsePostVoteControls(html)
        )
    }
}
