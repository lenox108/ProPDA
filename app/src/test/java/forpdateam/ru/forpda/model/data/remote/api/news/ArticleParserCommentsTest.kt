package forpdateam.ru.forpda.model.data.remote.api.news

import android.util.SparseArray
import forpdateam.ru.forpda.entity.remote.news.Comment
import forpdateam.ru.forpda.model.data.remote.ParserPatterns
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.regex.Pattern
import forpdateam.ru.forpda.ui.fragments.news.details.ArticleCommentActionVisibility

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ArticleParserCommentsTest {

    private class ArticlesPatternProviderStub : IPatternProvider {
        override fun getCurrentVersion(): Int = 29
        override fun update(jsonString: String) {}
        override fun getPattern(scope: String, key: String): Pattern {
            require(scope == ParserPatterns.Articles.scope) { scope }
            return when (key) {
                ParserPatterns.Articles.exclude_form_comment ->
                    Pattern.compile("<form[\\s\\S]*", Pattern.CASE_INSENSITIVE)
                ParserPatterns.Articles.comment_id ->
                    Pattern.compile("comment-(\\d+)")
                ParserPatterns.Articles.comment_user_id ->
                    Pattern.compile("showuser=(\\d+)")
                ParserPatterns.Articles.karmaSource ->
                    Pattern.compile("ModKarma\\((\\{[\\s\\S]*?\\})")
                ParserPatterns.Articles.karma ->
                    Pattern.compile("a^")
                else -> throw IllegalArgumentException(key)
            }
        }
    }

    @Test
    fun applyFallbackOwnCommentActions_preservesInlineEditPayload() {
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val root = Comment().apply {
            children.add(
                    Comment().apply {
                        id = 10
                        userId = 5
                        actions.edit = Comment.Action(
                                url = "#",
                                type = Comment.Action.Type.EDIT,
                                editableElementId = "comment-form-edit-10",
                                editableHtml = "Inline body"
                        )
                    }
            )
        }
        parser.applyFallbackOwnCommentActions(root, authUserId = 5)
        val comment = root.children.single()

        assertEquals("Inline body", comment.actions.edit?.editableHtml)
        assertEquals("comment-form-edit-10", comment.actions.edit?.editableElementId)
        assertEquals("#", comment.actions.edit?.url)
    }

    @Test
    fun applyFallbackOwnCommentActions_fillsMissingEditAndDelete() {
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val root = Comment().apply {
            children.add(
                    Comment().apply {
                        id = 10
                        userId = 5
                    }
            )
        }
        parser.applyFallbackOwnCommentActions(root, authUserId = 5, articleId = 100)
        val comment = root.children.single()
        // Правка идёт обычным POST на wp-comments-post.php с comment_ID (механизм 4pda), а НЕ через
        // несуществующий admin-ajax editcomment (404), из-за которого редактирование не работало.
        assertEquals("https://4pda.to/wp-comments-post.php", comment.actions.edit?.url)
        assertEquals("10", comment.actions.edit?.fields?.get("comment_ID"))
        assertEquals("100", comment.actions.edit?.fields?.get("comment_post_ID"))
        // Удаление не менялось — оно работало через admin-ajax deletecomment.
        assertEquals("https://4pda.to/wp-admin/admin-ajax.php?action=deletecomment&c=10", comment.actions.delete?.url)
        assertTrue(ArticleCommentActionVisibility.canShowEdit(auth = true, authUserId = 5, comment = comment))
    }

    @Test
    fun authorCommentsMissingOwnModeration_requiresActionableEditOrDelete() {
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val html = """<ul class="comment-list"><li><div id="comment-10" class="comment">
            <a class="comment-avatar" href="https://4pda.to/forum/index.php?showuser=5">x</a>
            <a class="nickname">Me</a>
            <p class="content">Mine</p>
            <a class="comment-edit" href="/wp-admin/admin-ajax.php?action=editcomment&amp;c=10">Редактировать</a>
        </div></li></ul>"""
        assertTrue(parser.authorCommentsMissingOwnModeration(html, authUserId = 5))
    }

    @Test
    fun authorCommentsMissingOwnModeration_falseWhenNoncePresentInUrl() {
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val html = """<ul class="comment-list"><li><div id="comment-10" class="comment">
            <a class="comment-avatar" href="https://4pda.to/forum/index.php?showuser=5">x</a>
            <a class="nickname">Me</a>
            <p class="content">Mine</p>
            <a class="comment-edit" href="/wp-admin/comment.php?action=editcomment&amp;c=10&amp;_wpnonce=abc">Редактировать</a>
        </div></li></ul>"""
        assertEquals(false, parser.authorCommentsMissingOwnModeration(html, authUserId = 5))
    }

    @Test
    fun `comment action probes share parsed comments for same source`() {
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val html = """<ul class="comment-list"><li><div id="comment-10" class="comment">
            <a class="comment-avatar" href="https://4pda.to/forum/index.php?showuser=5">x</a>
            <a class="nickname">Me</a>
            <p class="content">Mine</p>
            <a class="comment-edit" href="/wp-admin/comment.php?action=editcomment&amp;c=10&amp;_wpnonce=abc">Редактировать</a>
            <a class="comment-reply-link" href="/index.php?act=rep&amp;c=10">Ответить</a>
        </div></li></ul>"""

        assertTrue(parser.hasCommentEditActions(html))
        assertTrue(parser.hasAnyComments(html))
        assertEquals(false, parser.authorCommentsMissingOwnModeration(html, authUserId = 5))
    }

    @Test
    fun parseComments_articleWithCommentsFixture_parsesComment() {
        val html = javaClass.classLoader
                ?.getResourceAsStream("parser/news/article_with_comments.html")
                ?.bufferedReader()
                ?.readText()
                .orEmpty()
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val root = parser.parseComments(SparseArray<Comment.Karma>(), html)
        assertEquals(1, root.children.size)
        assertEquals(1, root.children[0].id)
        assertEquals("Commenter", root.children[0].userNick)
        assertTrue(root.children[0].content.orEmpty().contains("First comment"))
    }

    @Test
    fun parseComments_divAnchor_parsesComment() {
        val html = """<ul class="comment-list"><li><div id="comment-42" class="comment">
        <a class="comment-avatar" href="https://4pda.to/forum/index.php?showuser=99">x</a>
        <a class="nickname">Kapustorei</a>
        <a class="date">01.01.2025</a>
        <p class="content">Hello</p>
    </div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val root = parser.parseComments(SparseArray<Comment.Karma>(), html)
        assertEquals(1, root.children.size)
        assertEquals(42, root.children[0].id)
        assertEquals("Kapustorei", root.children[0].userNick)
        assertEquals("Hello", root.children[0].content.orEmpty().trim())
    }

    @Test
    fun parseComments_articleAnchor_parsesComment() {
        val html = """<ul class="comment-list"><li>
        <article id="comment-7" class="comment-item">
        <a class="comment-avatar" href="showuser=3">x</a>
        <span class="nickname">Nick</span>
        <a class="date">today</a>
        <div class="content">Body</div>
        </article></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val root = parser.parseComments(SparseArray<Comment.Karma>(), html)
        assertEquals(1, root.children.size)
        assertEquals(7, root.children[0].id)
        assertTrue(root.children[0].content.orEmpty().contains("Body"))
    }

    @Test
    fun parseComments_tagFallback_parsesDataCommentArticleVariant() {
        val html = """<section>
        <article class="comment-card" data-comment-id="77" data-comment="77">
            <a class="comment-avatar" href="https://4pda.to/forum/index.php?showuser=12">x</a>
            <span class="nickname">FallbackNick</span>
            <time class="comment-date">02.01.2025</time>
            <div class="comment-content">Fallback body</div>
        </article>
    </section>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val root = parser.parseComments(SparseArray<Comment.Karma>(), html)
        assertEquals(1, root.children.size)
        assertEquals(77, root.children[0].id)
        assertEquals(12, root.children[0].userId)
        assertEquals("FallbackNick", root.children[0].userNick)
        assertEquals("02.01.2025", root.children[0].date)
        assertEquals("Fallback body", root.children[0].content.orEmpty().trim())
    }

    @Test
    fun parseComments_commentsListClass_findsUl() {
        val html = """<ul class="comments-list theme_4pda"><li><div id="comment-1">
        <p class="content">X</p></div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val root = parser.parseComments(SparseArray<Comment.Karma>(), html)
        assertEquals(1, root.children.size)
        assertEquals(1, root.children[0].id)
    }

    @Test
    fun parseComments_multipleComments_parsesAll() {
        val html = """<ul class="comment-list"><li><div id="comment-1" class="comment">
        <a class="nickname">User1</a>
        <p class="content">Comment 1</p>
    </div></li><li><div id="comment-2" class="comment">
        <a class="nickname">User2</a>
        <p class="content">Comment 2</p>
    </div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val root = parser.parseComments(SparseArray<Comment.Karma>(), html)
        assertEquals(2, root.children.size)
        assertEquals(1, root.children[0].id)
        assertEquals(2, root.children[1].id)
    }

    @Test
    fun parseComments_emptyHtml_returnsEmptyRoot() {
        val html = ""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val root = parser.parseComments(SparseArray<Comment.Karma>(), html)
        assertEquals(0, root.children.size)
    }

    @Test
    fun parseComments_noComments_returnsEmptyRoot() {
        val html = """<div class="no-comments">No comments yet</div>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val root = parser.parseComments(SparseArray<Comment.Karma>(), html)
        assertEquals(0, root.children.size)
    }

    @Test
    fun parseKarmaMap_withFiveElementArray_parsesStatusAndCount() {
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val karma = parser.parseKarmaMap("""ModKarma({"10614341":[1,0,0,0,5],"10614363":[0,0,0,0,2]})""")

        assertEquals(2, karma.size())
        assertEquals(Comment.Karma.LIKED, karma.get(10614341)?.status)
        assertEquals(5, karma.get(10614341)?.count)
        assertEquals(Comment.Karma.NOT_LIKED, karma.get(10614363)?.status)
        assertEquals(2, karma.get(10614363)?.count)
    }

    @Test
    fun parseComments_withoutKarmaEntry_isNotLikedByDefault() {
        val html = """<ul class="comment-list"><li><div id="comment-10" class="comment">
        <a class="nickname">User</a>
        <span class="karma" data-karma-ver="2" data-karma="456-10"></span>
        <p class="content">Comment</p>
    </div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val comment = parser.parseComments(SparseArray<Comment.Karma>(), html).children.single()

        assertFalse(comment.likedByMe)
        assertEquals("https://4pda.to/pages/karma?p=456&c=10&v=1", comment.likeAction?.url)
    }

    @Test
    fun parseComments_withKarma_parsesKarma() {
        val html = """<ul class="comment-list"><li><div id="comment-1" class="comment">
        <a class="nickname">User</a>
        <p class="content">Comment</p>
    </div></li></ul>"""
        val karma = SparseArray<Comment.Karma>()
        val karmaObj = Comment.Karma()
        karmaObj.status = 5
        karmaObj.count = 2
        karma.put(1, karmaObj)
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val root = parser.parseComments(karma, html)
        assertEquals(1, root.children.size)
        assertEquals(1, root.children[0].id)
        assertEquals(5, root.children[0].karma?.status)
        assertEquals(2, root.children[0].karma?.count)
    }

    @Test
    fun parseComments_withSiteKarmaData_parsesLikeStateAndAction() {
        val html = """<ul class="comment-list"><li><div id="comment-10" class="comment">
        <a class="nickname">User</a>
        <span class="karma" data-karma-ver="2" data-karma="456-10"><span class="num">7</span></span>
        <p class="content">Comment</p>
    </div></li></ul>"""
        val karma = SparseArray<Comment.Karma>()
        karma.put(10, Comment.Karma().apply {
            status = Comment.Karma.LIKED
            count = 7
        })
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val comment = parser.parseComments(karma, html).children.single()

        assertTrue(comment.likedByMe)
        assertEquals(7, comment.likeCount)
        assertEquals("https://4pda.to/pages/karma?p=456&c=10&v=1", comment.likeAction?.url)
        assertEquals("https://4pda.to/pages/karma?p=456&c=10&v=0", comment.unlikeAction?.url)
        assertEquals(Comment.Action.Type.COMMENT_LIKE, comment.likeAction?.type)
        assertEquals(Comment.Action.Type.COMMENT_UNLIKE, comment.unlikeAction?.type)
    }

    @Test
    fun parseComments_withExplicitRemoveLikeAction_parsesUnlikeAction() {
        val html = """<ul class="comment-list"><li><div id="comment-10" class="comment">
        <a class="nickname">User</a>
        <a class="comment-karma" href="https://4pda.to/pages/karma?p=456&amp;c=10&amp;v=1">+</a>
        <a class="comment-karma" href="https://4pda.to/pages/karma?p=456&amp;c=10&amp;v=0">remove</a>
        <p class="content">Comment</p>
    </div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val comment = parser.parseComments(SparseArray<Comment.Karma>(), html).children.single()

        assertEquals("https://4pda.to/pages/karma?p=456&c=10&v=1", comment.likeAction?.url)
        assertEquals("https://4pda.to/pages/karma?p=456&c=10&v=0", comment.unlikeAction?.url)
    }

    @Test
    fun parseComments_withMismatchedCommentKarmaAction_doesNotAttachLikeAction() {
        val html = """<ul class="comment-list"><li><div id="comment-10" class="comment">
        <a class="nickname">User</a>
        <a class="comment-karma" href="https://4pda.to/pages/karma?p=456&amp;c=11&amp;v=1">+</a>
        <p class="content">Comment</p>
    </div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val comment = parser.parseComments(SparseArray<Comment.Karma>(), html).children.single()

        assertNull(comment.likeAction)
        assertNull(comment.actions.like)
    }

    @Test
    fun parseComments_commentformMoveWithEditFlag_parsesEditAndExtractsEditableBlock() {
        val html = """<ul class="comment-list"><li><div id="comment-10" class="comment">
            <a class="comment-avatar" href="https://4pda.to/forum/index.php?showuser=5">x</a>
            <a class="nickname" data-callfn="commentform_move" data-comment="10" href="#">Me</a>
            <a class="date">01.01.2026</a>
            <div class="dropdown-menu">
              <a href="#" data-callfn="commentform_move" data-comment="10">Ответить</a>
              <a href="#" data-callfn="commentform_move" data-comment="10" data-editcomment="1" data-submit-text="Изменить">Редактировать</a>
              <a href="https://4pda.to/forum/index.php?act=rep&amp;view=win_add&amp;mid=5&amp;c=10">Репутация +</a>
              <a href="https://4pda.to/forum/index.php?act=report&amp;comment=10" data-report-comment="10">Жалоба</a>
              <a href="#" data-karma-act="1-456-10">Нравится</a>
            </div>
            <div class="content">Old</div>
            <div class="content" id="comment-form-edit-10">бомба</div>
        </div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val comment = parser.parseComments(SparseArray<Comment.Karma>(), html).children.single()

        assertEquals(10, comment.id)
        assertEquals(Comment.Action.Type.EDIT, comment.actions.edit?.type)
        assertEquals("comment-form-edit-10", comment.actions.edit?.editableElementId)
        assertEquals("Изменить", comment.actions.edit?.submitText)
        assertEquals("бомба", comment.actions.edit?.editableHtml)
        assertEquals(INLINE_COMMENT_EDIT_SUBMIT_URL, comment.actions.edit?.url)
        assertEquals("10", comment.actions.edit?.fields?.get("comment_ID"))
        assertEquals("456", comment.actions.edit?.fields?.get("comment_post_ID"))
        assertEquals(Comment.Action.Type.REPLY, comment.actions.reply?.type)
        assertNull(comment.actions.delete)
        assertEquals(Comment.Action.Type.REPUTATION_PLUS, comment.actions.reputationPlus?.type)
        assertEquals(Comment.Action.Type.REPORT, comment.actions.report?.type)
        assertEquals(Comment.Action.Type.COMMENT_LIKE, comment.actions.like?.type)
    }

    @Test
    fun parseComments_withNegativeKarmaAction_doesNotTreatAsUnlike() {
        val html = """<ul class="comment-list"><li><div id="comment-10" class="comment">
        <a class="nickname">User</a>
        <a class="comment-karma" href="https://4pda.to/pages/karma?p=456&amp;c=10&amp;v=-1">-</a>
        <p class="content">Comment</p>
    </div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val comment = parser.parseComments(SparseArray<Comment.Karma>(), html).children.single()

        assertNull(comment.unlikeAction)
    }

    @Test
    fun parseComments_withActionLinks_parsesAvailableActionsOnly() {
        val html = """<ul class="comment-list"><li><div id="comment-10" class="comment">
        <a class="comment-avatar" href="https://4pda.to/forum/index.php?showuser=99">x</a>
        <a class="nickname">User</a>
        <a class="date">today</a>
        <p class="content">Comment</p>
        <a class="comment-reply-link" href="#respond" data-commentid="10">Ответить</a>
        <a class="comment-karma" href="https://4pda.to/pages/karma?p=456&amp;c=10&amp;v=1">+</a>
        <a class="report" href="https://4pda.to/forum/index.php?act=report&amp;send=1&amp;t=0&amp;p=10">Жалоба</a>
    </div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val comment = parser.parseComments(SparseArray<Comment.Karma>(), html).children.single()

        assertEquals("https://4pda.to/forum/index.php?showuser=99", comment.actions.profile?.url)
        assertEquals(Comment.Action.Type.PROFILE, comment.actions.profile?.type)
        assertEquals(Comment.Action.Type.REPLY, comment.actions.reply?.type)
        assertEquals("10", comment.actions.reply?.fields?.get("comment_reply_ID"))
        assertNull(comment.actions.karmaPlus)
        assertTrue(comment.likeAction?.url.orEmpty().contains("pages/karma"))
        assertEquals(Comment.Action.Type.COMMENT_LIKE, comment.likeAction?.type)
        assertTrue(comment.actions.report?.url.orEmpty().contains("act=report"))
        assertEquals(Comment.Action.Type.REPORT, comment.actions.report?.type)
        assertEquals(null, comment.actions.hide)
        assertEquals(null, comment.actions.reputationMinus)
    }

    @Test
    fun parseComments_withOwnCommentLinks_parsesEditAndDeleteActions() {
        val html = """<ul class="comment-list"><li><div id="comment-10" class="comment">
        <a class="nickname">User</a>
        <p class="content">Comment</p>
        <a class="comment-edit" href="/wp-admin/admin-ajax.php?action=editcomment&amp;c=10&amp;_wpnonce=abc">Редактировать</a>
        <a class="comment-delete" href="/wp-admin/admin-ajax.php?action=deletecomment&amp;c=10&amp;_wpnonce=def">Удалить</a>
    </div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val comment = parser.parseComments(SparseArray<Comment.Karma>(), html).children.single()

        assertEquals("https://4pda.to/wp-admin/admin-ajax.php?action=editcomment&c=10&_wpnonce=abc", comment.actions.edit?.url)
        assertEquals("https://4pda.to/wp-admin/admin-ajax.php?action=deletecomment&c=10&_wpnonce=def", comment.actions.delete?.url)
        assertEquals(Comment.Action.Type.EDIT, comment.actions.edit?.type)
        assertEquals(Comment.Action.Type.DELETE, comment.actions.delete?.type)
        assertTrue(comment.actions.delete?.requiresConfirmation == true)
    }

    @Test
    fun parseComments_withOwnCommentForms_parsesEditAndDeleteActionsWithFields() {
        val html = """<ul class="comment-list"><li><div id="comment-10" class="comment">
        <a class="nickname">User</a>
        <p class="content">Comment</p>
        <form class="comment-edit" action="/wp-admin/admin-ajax.php?action=editcomment" method="post">
            <input type="hidden" name="_wpnonce" value="abc">
            <textarea name="comment">Old text</textarea>
        </form>
        <form class="comment-delete" action="/wp-admin/admin-ajax.php?action=deletecomment" method="post">
            <input type="hidden" name="_wpnonce" value="def">
            <input type="hidden" name="comment_ID" value="10">
        </form>
    </div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val comment = parser.parseComments(SparseArray<Comment.Karma>(), html).children.single()

        assertEquals("https://4pda.to/wp-admin/admin-ajax.php?action=editcomment", comment.actions.edit?.url)
        assertEquals("abc", comment.actions.edit?.fields?.get("_wpnonce"))
        assertEquals("Old text", comment.actions.edit?.fields?.get("comment"))
        assertEquals("https://4pda.to/wp-admin/admin-ajax.php?action=deletecomment", comment.actions.delete?.url)
        assertEquals("10", comment.actions.delete?.fields?.get("comment_ID"))
        assertEquals("def", comment.actions.delete?.token)
    }

    @Test
    fun parseCommentEditAction_fromFetchedForm_parsesSubmitEndpointAndCommentField() {
        val html = """<div>
        <form id="comment-edit" action="/wp-admin/admin-ajax.php?action=editcomment" method="post">
            <input type="hidden" name="_wpnonce" value="abc">
            <textarea name="comment">Old text</textarea>
            <button type="submit">Сохранить</button>
        </form>
    </div>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val action = parser.parseCommentEditAction(html)

        assertEquals("https://4pda.to/wp-admin/admin-ajax.php?action=editcomment", action?.url)
        assertEquals("abc", action?.fields?.get("_wpnonce"))
        assertEquals("Old text", action?.fields?.get("comment"))
    }

    @Test
    fun extractCommentEditActionFromHtml_buildsSubmitActionFromHiddenFields() {
        val html = """<div class="wrap">
        <form name="post" action="comment.php" method="post">
            <input type="hidden" name="_wpnonce" value="abc">
            <input type="hidden" name="action" value="editedcomment">
            <input type="hidden" name="comment_ID" value="10">
            <input type="hidden" name="comment_post_ID" value="456">
            <textarea name="content">Old text</textarea>
        </form>
    </div>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val action = parser.extractCommentEditActionFromHtml(html, commentId = 10)

        assertEquals("https://4pda.to/wp-admin/comment.php", action?.url)
        assertEquals("abc", action?.fields?.get("_wpnonce"))
        assertEquals("456", action?.fields?.get("comment_post_ID"))
        assertEquals("Old text", action?.fields?.get("content"))
    }

    @Test
    fun parseCommentEditAction_fromWordPressAdminForm_parsesEditedCommentForm() {
        val html = """<div class="wrap">
        <form name="post" action="comment.php" method="post" id="post">
            <input type="hidden" name="_wpnonce" value="abc">
            <input type="hidden" name="action" value="editedcomment">
            <input type="hidden" name="comment_ID" value="10">
            <input type="hidden" name="comment_post_ID" value="456">
            <textarea name="content">Old text</textarea>
            <button type="submit" name="save">Обновить</button>
        </form>
    </div>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val action = parser.parseCommentEditAction(html)

        assertEquals("https://4pda.to/wp-admin/comment.php", action?.url)
        assertEquals("abc", action?.fields?.get("_wpnonce"))
        assertEquals("editedcomment", action?.fields?.get("action"))
        assertEquals("Old text", action?.fields?.get("content"))
    }

    @Test
    fun parseCommentDeleteAction_fromFetchedForm_parsesSubmitEndpointAndToken() {
        val html = """<div>
        <form id="comment-delete" action="/wp-admin/admin-ajax.php?action=deletecomment" method="post">
            <input type="hidden" name="_wpnonce" value="def">
            <input type="hidden" name="comment_ID" value="10">
            <button type="submit">Удалить</button>
        </form>
    </div>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val action = parser.parseCommentDeleteAction(html)

        assertEquals("https://4pda.to/wp-admin/admin-ajax.php?action=deletecomment", action?.url)
        assertEquals("def", action?.fields?.get("_wpnonce"))
        assertEquals("10", action?.fields?.get("comment_ID"))
        assertEquals(Comment.Action.Type.DELETE, action?.type)
        assertTrue(action?.requiresConfirmation == true)
    }

    @Test
    fun parseCommentDeleteAction_fromWordPressAdminForm_parsesTrashCommentForm() {
        val html = """<div class="wrap">
        <form id="delete-comment" action="comment.php" method="post">
            <input type="hidden" name="_wpnonce" value="def">
            <input type="hidden" name="action" value="trashcomment">
            <input type="hidden" name="c" value="10">
            <button type="submit">Удалить</button>
        </form>
    </div>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val action = parser.parseCommentDeleteAction(html)

        assertEquals("https://4pda.to/wp-admin/comment.php", action?.url)
        assertEquals("def", action?.fields?.get("_wpnonce"))
        assertEquals("trashcomment", action?.fields?.get("action"))
        assertEquals("10", action?.fields?.get("c"))
        assertEquals(Comment.Action.Type.DELETE, action?.type)
    }

    @Test
    fun parseComments_withReputationActions_parsesPlusMinus() {
        val html = """<ul class="comment-list"><li><div id="comment-11" class="comment">
        <a class="comment-avatar" href="https://4pda.to/forum/index.php?showuser=77">x</a>
        <a class="nickname">User</a>
        <p class="content">Comment</p>
        <a class="win_add" href="https://4pda.to/forum/index.php?act=rep&amp;mid=77&amp;type=add">+</a>
        <a class="win_minus" href="https://4pda.to/forum/index.php?act=rep&amp;mid=77&amp;type=minus">-</a>
    </div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val comment = parser.parseComments(SparseArray<Comment.Karma>(), html).children.single()

        assertEquals("https://4pda.to/forum/index.php?act=rep&mid=77&type=add", comment.actions.reputationPlus?.url)
        assertEquals("add", comment.actions.reputationPlus?.fields?.get("type"))
        assertEquals("77", comment.actions.reputationPlus?.fields?.get("mid"))
        assertEquals(Comment.Action.METHOD_GET, comment.actions.reputationPlus?.method)
        assertEquals(Comment.Action.Type.REPUTATION_PLUS, comment.actions.reputationPlus?.type)
        assertTrue(comment.actions.reputationPlus?.requiresReason == true)
        assertEquals("https://4pda.to/forum/index.php?act=rep&mid=77&type=minus", comment.actions.reputationMinus?.url)
        assertEquals("minus", comment.actions.reputationMinus?.fields?.get("type"))
        assertEquals("77", comment.actions.reputationMinus?.fields?.get("mid"))
        assertEquals(Comment.Action.Type.REPUTATION_MINUS, comment.actions.reputationMinus?.type)
    }

    @Test
    fun parseComments_withKarmaAndReputationActions_keepsActionsDistinct() {
        val html = """<ul class="comment-list"><li><div id="comment-11" class="comment">
        <a class="comment-avatar" href="https://4pda.to/forum/index.php?showuser=77">x</a>
        <a class="nickname">User</a>
        <p class="content">Comment</p>
        <a class="win_add comment-karma" href="https://4pda.to/pages/karma?p=456&amp;c=11&amp;v=1">Плюс к карме</a>
        <a class="win_add" href="https://4pda.to/forum/index.php?act=rep&amp;mid=77&amp;type=add">Репутация +</a>
        <a class="win_minus" href="https://4pda.to/forum/index.php?act=rep&amp;mid=77&amp;type=minus">Репутация -</a>
    </div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val comment = parser.parseComments(SparseArray<Comment.Karma>(), html).children.single()

        assertNull(comment.actions.karmaPlus)
        assertEquals("https://4pda.to/pages/karma?p=456&c=11&v=1", comment.likeAction?.url)
        assertEquals("https://4pda.to/forum/index.php?act=rep&mid=77&type=add", comment.actions.reputationPlus?.url)
        assertEquals("rep", comment.actions.reputationPlus?.fields?.get("act"))
        assertEquals("add", comment.actions.reputationPlus?.fields?.get("type"))
        assertEquals("https://4pda.to/forum/index.php?act=rep&mid=77&type=minus", comment.actions.reputationMinus?.url)
        assertEquals("minus", comment.actions.reputationMinus?.fields?.get("type"))
    }

    @Test
    fun parseComments_withRealKarmaPlusAction_doesNotUseCommentLikeEndpoint() {
        val html = """<ul class="comment-list"><li><div id="comment-11" class="comment">
        <a class="comment-avatar" href="https://4pda.to/forum/index.php?showuser=77">x</a>
        <a class="nickname">User</a>
        <p class="content">Comment</p>
        <a class="karma-plus" href="https://4pda.to/forum/index.php?act=karma&amp;mid=77&amp;type=add">Плюс к карме</a>
        <a class="comment-karma" href="https://4pda.to/pages/karma?p=456&amp;c=11&amp;v=1">Нравится</a>
    </div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val comment = parser.parseComments(SparseArray<Comment.Karma>(), html).children.single()

        assertEquals("https://4pda.to/forum/index.php?act=karma&mid=77&type=add", comment.actions.karmaPlus?.url)
        assertEquals(Comment.Action.Type.KARMA_PLUS, comment.actions.karmaPlus?.type)
        assertEquals("https://4pda.to/pages/karma?p=456&c=11&v=1", comment.likeAction?.url)
    }

    @Test
    fun parseComments_withOnlyKarmaPlusAction_doesNotUseItAsHeartLike() {
        val html = """<ul class="comment-list"><li><div id="comment-11" class="comment">
        <a class="comment-avatar" href="https://4pda.to/forum/index.php?showuser=77">x</a>
        <a class="nickname">User</a>
        <p class="content">Comment</p>
        <a class="karma-plus" href="https://4pda.to/forum/index.php?act=karma&amp;mid=77&amp;type=add">Плюс к карме</a>
    </div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val comment = parser.parseComments(SparseArray<Comment.Karma>(), html).children.single()

        assertEquals(Comment.Action.Type.KARMA_PLUS, comment.actions.karmaPlus?.type)
        assertNull(comment.likeAction)
    }

    @Test
    fun mergeCommentReputationActions_fromDesktopSource_mergesByCommentId() {
        val mobile = """<ul class="comment-list"><li><div id="comment-11" class="comment">
        <a class="comment-avatar" href="https://4pda.to/forum/index.php?showuser=77">x</a>
        <a class="nickname">User</a>
        <a class="date">01.01.2025 12:00</a>
        <p class="content">Mobile comment</p>
    </div></li></ul>"""
        val desktop = """<ul class="comment-list"><li><div id="comment-11" class="comment">
        <a class="comment-avatar" href="https://4pda.to/forum/index.php?showuser=77">x</a>
        <a class="nickname">User</a>
        <a class="date">01.01.2025 12:00</a>
        <p class="content">Desktop comment</p>
        <a class="win_add" href="https://4pda.to/forum/index.php?act=rep&amp;mid=77&amp;type=add">Репутация +</a>
        <a class="win_minus" href="https://4pda.to/forum/index.php?act=rep&amp;mid=77&amp;type=minus">Репутация -</a>
    </div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val root = parser.parseComments(SparseArray<Comment.Karma>(), mobile)

        parser.mergeCommentReputationActions(root, desktop)

        val comment = root.children.single()
        assertEquals("Mobile comment", comment.content.orEmpty().trim())
        assertEquals("add", comment.actions.reputationPlus?.fields?.get("type"))
        assertEquals("minus", comment.actions.reputationMinus?.fields?.get("type"))
        assertEquals("77", comment.actions.reputationPlus?.fields?.get("mid"))
    }

    @Test
    fun mergeCommentDesktopActions_fromDesktopSource_mergesOwnEditAndDeleteByCommentId() {
        val mobile = """<ul class="comment-list"><li><div id="comment-10" class="comment">
        <a class="nickname">User</a>
        <a class="date">01.01.2025 12:00</a>
        <p class="content">Mobile comment</p>
    </div></li></ul>"""
        val desktop = """<ul class="comment-list"><li><div id="comment-10" class="comment">
        <a class="nickname">User</a>
        <a class="date">01.01.2025 12:00</a>
        <p class="content">Desktop comment</p>
        <a class="comment-edit" href="/wp-admin/comment.php?action=editcomment&amp;c=10&amp;_wpnonce=abc">Редактировать</a>
        <a class="comment-delete" href="/wp-admin/comment.php?action=deletecomment&amp;c=10&amp;_wpnonce=def">Удалить</a>
    </div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val root = parser.parseComments(SparseArray<Comment.Karma>(), mobile)

        parser.mergeCommentDesktopActions(root, desktop)

        val comment = root.children.single()
        assertEquals("Mobile comment", comment.content.orEmpty().trim())
        assertEquals("https://4pda.to/wp-admin/comment.php?action=editcomment&c=10&_wpnonce=abc", comment.actions.edit?.url)
        assertEquals(Comment.Action.Type.EDIT, comment.actions.edit?.type)
        assertEquals("https://4pda.to/wp-admin/comment.php?action=deletecomment&c=10&_wpnonce=def", comment.actions.delete?.url)
        assertEquals(Comment.Action.Type.DELETE, comment.actions.delete?.type)
    }

    @Test
    fun mergeCommentDesktopActions_mobileWithoutAuthorId_stillExposesServerOwnEditInUiDecision() {
        val mobile = """<ul class="comment-list"><li><div id="comment-10" class="comment">
        <a class="nickname">Me</a>
        <a class="date">01.01.2025 12:00</a>
        <p class="content">Mobile comment</p>
    </div></li></ul>"""
        val desktop = """<ul class="comment-list"><li><div id="comment-10" class="comment">
        <a class="nickname">Me</a>
        <a class="date">01.01.2025 12:00</a>
        <p class="content">Desktop comment</p>
        <a class="comment-edit" href="/wp-admin/comment.php?action=editcomment&amp;c=10&amp;_wpnonce=abc">Редактировать</a>
    </div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val root = parser.parseComments(SparseArray<Comment.Karma>(), mobile)

        parser.mergeCommentDesktopActions(root, desktop)

        val comment = root.children.single()
        assertEquals(0, comment.userId)
        assertEquals(Comment.Action.Type.EDIT, comment.actions.edit?.type)
        assertTrue(ArticleCommentActionVisibility.canShowEdit(auth = true, authUserId = 5, comment = comment))
    }

    @Test
    fun mergeCommentReputationActions_withAmbiguousAuthorDate_doesNotMergeWrongComment() {
        val mobile = """<ul class="comment-list"><li><div id="comment-0" class="comment">
        <a class="comment-avatar" href="https://4pda.to/forum/index.php?showuser=77">x</a>
        <a class="nickname">User</a>
        <a class="date">01.01.2025 12:00</a>
        <p class="content">Mobile comment</p>
    </div></li></ul>"""
        val desktop = """<ul class="comment-list">
        <li><div id="comment-0" class="comment">
            <a class="comment-avatar" href="https://4pda.to/forum/index.php?showuser=77">x</a>
            <a class="nickname">User</a>
            <a class="date">01.01.2025 12:00</a>
            <p class="content">Desktop first</p>
            <a class="win_add" href="https://4pda.to/forum/index.php?act=rep&amp;mid=77&amp;type=add">Репутация +</a>
        </div></li>
        <li><div id="comment-0" class="comment">
            <a class="comment-avatar" href="https://4pda.to/forum/index.php?showuser=77">x</a>
            <a class="nickname">User</a>
            <a class="date">01.01.2025 12:00</a>
            <p class="content">Desktop second</p>
            <a class="win_minus" href="https://4pda.to/forum/index.php?act=rep&amp;mid=77&amp;type=minus">Репутация -</a>
        </div></li>
    </ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val root = parser.parseComments(SparseArray<Comment.Karma>(), mobile)

        parser.mergeCommentReputationActions(root, desktop)

        val comment = root.children.single()
        assertNull(comment.actions.reputationPlus)
        assertNull(comment.actions.reputationMinus)
    }

    @Test
    fun parseComments_withoutReputationMid_doesNotExposeReputationAction() {
        val html = """<ul class="comment-list"><li><div id="comment-11" class="comment">
        <a class="nickname">User</a>
        <p class="content">Comment</p>
        <a class="win_add" href="https://4pda.to/forum/index.php?act=rep&amp;type=add">Репутация +</a>
    </div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val comment = parser.parseComments(SparseArray<Comment.Karma>(), html).children.single()

        assertNull(comment.actions.reputationPlus)
    }

    @Test
    fun parseComments_editedText_becomesCompactMarker() {
        val html = """<ul class="comment-list"><li><div id="comment-12" class="comment">
        <a class="nickname">User</a>
        <p class="content">Body <span class="edited">отредактирован</span></p>
    </div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val comment = parser.parseComments(SparseArray<Comment.Karma>(), html).children.single()

        assertTrue(comment.isEdited)
        assertTrue(comment.content.orEmpty().contains("Body"))
        assertTrue(!comment.content.orEmpty().contains("отредактирован"))
    }

    @Test
    fun parseComments_editedTextInParentheses_removesWholeWrapper() {
        val html = """<ul class="comment-list"><li><div id="comment-12" class="comment">
        <a class="nickname">User</a>
        <p class="content">Body<br>
        (<span class="edited">отредактирован User - Сегодня, 12:00</span>)</p>
    </div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val comment = parser.parseComments(SparseArray<Comment.Karma>(), html).children.single()

        assertTrue(comment.isEdited)
        assertEquals("Body", comment.content.orEmpty())
        assertTrue(!comment.content.orEmpty().contains("()"))
        assertTrue(!comment.content.orEmpty().contains("отредактирован"))
    }

    @Test
    fun parseComments_editedPlainTextWithoutClass_isDetectedAndStripped() {
        // Живой кейс 4pda: маркер правки — просто текст «(отредактирован)» в контенте,
        // без class="edited" на узле. Детект держится только на текстовом regex, а он
        // из-за ASCII-\b (java.util.regex) не видел кириллицу → маркер не срабатывал.
        val html = """<ul class="comment-list"><li><div id="comment-12" class="comment">
        <a class="nickname">User</a>
        <p class="content">В какой момент должен наступить эмейзинг? (отредактирован)</p>
    </div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val comment = parser.parseComments(SparseArray<Comment.Karma>(), html).children.single()

        assertTrue(comment.isEdited)
        assertTrue(comment.content.orEmpty().contains("эмейзинг"))
        assertTrue(!comment.content.orEmpty().contains("отредактирован"))
    }

    @Test
    fun parseComments_unauthorizedComment_hasNoNetworkActions() {
        val html = """<ul class="comment-list"><li><div id="comment-13" class="comment">
        <a class="nickname">User</a>
        <p class="content">Comment</p>
    </div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val comment = parser.parseComments(SparseArray<Comment.Karma>(), html).children.single()

        assertEquals(null, comment.actions.karmaPlus)
        assertEquals(null, comment.actions.report)
        assertEquals(null, comment.actions.reputationPlus)
    }

    @Test
    fun parseCommentEditAction_fromFixtureFile_parsesFullWordPressForm() {
        val html = javaClass.classLoader
                ?.getResourceAsStream("parser/news/comment_edit_form_wp_admin.html")
                ?.bufferedReader()
                ?.readText()
                .orEmpty()
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val action = parser.parseCommentEditAction(html)

        assertEquals("https://4pda.to/wp-admin/comment.php", action?.url)
        assertEquals("f1e2d3c4b5", action?.fields?.get("_wpnonce"))
        assertEquals("10597307", action?.fields?.get("comment_ID"))
        assertEquals("Текст моего комментария", action?.fields?.get("content"))
    }

    @Test
    fun extractCommentEditNonceFromPage_readsNonceFromInlineScript() {
        val html = """<html><body><script>
            var cfg = {"_ajax_nonce-replyto-comment":"script-nonce-42"};
        </script></body></html>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())

        val nonce = parser.extractCommentEditNonceFromPage(html)

        assertEquals("_ajax_nonce-replyto-comment", nonce?.first)
        assertEquals("script-nonce-42", nonce?.second)
    }

    @Test
    fun extractCommentEditActionFromHtml_formlessAjaxFragment_parsesNonceAndTextarea() {
        val html = """<div id="comment-10" class="ajax-response">
            <input type="hidden" name="_ajax_nonce-replyto-comment" value="ajax-nonce-1">
            <input type="hidden" name="comment_ID" value="10">
            <textarea id="replycontent" rows="8" cols="40">Inline body</textarea>
        </div>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val action = parser.extractCommentEditActionFromHtml(html, commentId = 10)

        assertEquals("ajax-nonce-1", action?.fields?.get("_ajax_nonce-replyto-comment"))
        assertEquals("Inline body", action?.fields?.get("content"))
        assertEquals("10", action?.fields?.get("comment_ID"))
    }

    @Test
    fun parseComments_jsonEnvelope_parsesComments() {
        val html = """{"html":"<ul class=\"comment-list\"><li><div id=\"comment-9\"><a class=\"nickname\">User</a><div class=\"content\">Hi</div></div></li></ul>"}"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val root = parser.parseComments(SparseArray<Comment.Karma>(), html)
        assertEquals(1, root.children.size)
        assertEquals(9, root.children.single().id)
        assertEquals("Hi", root.children.single().content.orEmpty().trim())
    }

    @Test
    fun parseComments_nestedListInBody_parsesAllTopLevelComments() {
        val html = """<ul class="comment-list level-0">
            <li><div id="comment-1"><a class="nickname">A</a><div class="content">One<ul><li>nested</li></ul></div></div></li>
            <li><div id="comment-2"><a class="nickname">B</a><div class="content">Two</div></div></li>
        </ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val balanced = parser.ensureBalancedCommentsHtml(html).orEmpty()
        val root = parser.parseComments(SparseArray<Comment.Karma>(), balanced)
        assertEquals(2, root.children.size)
        assertEquals(1, root.children[0].id)
        assertEquals(2, root.children[1].id)
    }

    @Test
    fun parseComments_haierFixtureFromBalancedExtract_parsesFiveComments() {
        val page = javaClass.classLoader
                ?.getResourceAsStream("parser/news/haier_w3_article.html")
                ?.bufferedReader()
                ?.readText()
                .orEmpty()
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val balanced = parser.ensureBalancedCommentsHtml(page).orEmpty()
        val root = parser.parseComments(SparseArray<Comment.Karma>(), balanced)
        assertEquals(5, root.children.size)
        assertEquals(10597307, root.children.first().id)
    }

    @Test
    fun hasCommentNodeMarkup_emptyCommentListShell_isFalse() {
        // 4PDA ships a collapsed/lazy comment-list shell on some article renders: the container and
        // the reply-form placeholder are present, but there is no actual comment node yet.
        val shell = """<div class="comment-box" id="comments">
            <div class="spoiler-open"><span title="5 комментариев">5</span></div>
            <ul class="comment-list level-0"></ul>
            <div id="comment-form-reply-0"></div>
        </div>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        assertEquals(false, parser.hasCommentNodeMarkup(shell))
        // And it parses to zero comments (it must be treated as "no comments in source", not a node).
        assertEquals(0, parser.parseComments(SparseArray<Comment.Karma>(), shell).children.size)
    }

    @Test
    fun hasCommentNodeMarkup_realCommentNode_isTrue() {
        val withNode = """<ul class="comment-list level-0"><li data-author-id="2221326">
            <a id="comment10597307" class="link-anchor"></a>
            <div id="comment-10597307"><div class="content">hi</div></div>
        </li></ul>"""
        val dataCommentNode = """<ul class="comment-list"><li data-comment-id="77"><div class="content">x</div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        assertTrue(parser.hasCommentNodeMarkup(withNode))
        assertTrue(parser.hasCommentNodeMarkup(dataCommentNode))
    }

    @Test
    fun parseComments_liWithDataCommentId_parsesComment() {
        val html = """<ul class="comment-list"><li data-comment-id="77">
            <a class="nickname">Nick</a><div class="content">Body</div>
        </li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val root = parser.parseComments(SparseArray<Comment.Karma>(), html)
        assertEquals(1, root.children.size)
        assertEquals(77, root.children.single().id)
    }

    @Test
    fun parseComments_largeNestedListLikeRealPage_parsesAllTopLevelComments() {
        val total = 300
        val sb = StringBuilder()
        sb.append("<div id=\"comments\">")
        sb.append("<ul class=\"comment-list level-0\">\n")
        for (i in 1..total) {
            val id = 10000000 + i
            val userId = 2000000 + i
            // Realistic 4PDA mobile comment node: link-anchor + div#comment-<id> + dropdown menu + content,
            // followed by a nested (usually empty) reply list inside the same <li>.
            sb.append("<li data-author-id=\"$userId\">")
            sb.append("<a id=\"comment$id\" class=\"link-anchor\"></a>")
            sb.append("<div id=\"comment-$id\"><div class=\"heading\"><div class=\"text-left\">")
            sb.append("<a class=\"comment-avatar\" href=\"https://4pda.to/forum/index.php?showuser=$userId\" target=\"_blank\"><img src=\"https://4pda.to/s/avatar$i.jpg\"></a>")
            sb.append("<a class=\"nickname\" data-no-hide=\"1\" data-callfn=\"commentform_move\" data-comment=\"$id\" href=\"https://4pda.to/2026/05/26/1/#comment$id\" title=\"Ответить: User &quot;$i&quot;\">User_$i</a>")
            sb.append("</div><div class=\"text-right\">")
            sb.append("<span class=\"karma\" data-karma-ver=\"2\" data-karma=\"1-$id\"></span>")
            sb.append("<a class=\"date\" href=\"https://4pda.to/2026/05/26/1/#comment$id\">0$i.06.26</a>")
            sb.append("<div class=\"dropdown pull-right wrap-menu\"><a data-toggle=\"dropdown\" data-target=\"#\" href=\"#\" class=\"icon-burger-dots\"></a>")
            sb.append("<ul class=\"dropdown-menu\" role=\"menu\">")
            sb.append("<li><a href=\"#\" data-no-hide=\"1\" data-callfn=\"commentform_move\" data-comment=\"$id\"><i class=\"icon-pencil\"></i> Ответить</a></li>")
            sb.append("<li><a href=\"https://4pda.to/forum/index.php?act=rep&amp;view=win_add&amp;mid=$userId&amp;c=$id\" target=\"_blank\">Репутация +</a></li>")
            sb.append("<li><a href=\"https://4pda.to/forum/index.php?act=report&amp;comment=$id\" target=\"_blank\" data-report-comment=\"$id\">Жалоба</a></li>")
            sb.append("</ul></div></div></div>")
            // Content with quotes, ampersands, angle brackets, and an occasional user-authored <ul>.
            val body = if (i % 7 == 0) {
                "Список причин: <ul><li>раз</li><li>два &amp; три</li></ul> и кавычки \"ёлочки\" 'апострофы' <b>жирный</b>"
            } else {
                "Комментарий №$i с символами &amp; &lt;тег&gt; и кавычками \"$i\""
            }
            sb.append("<div class=\"content\">$body</div><div id=\"comment-form-reply-$id\"></div></div>")
            // Every 5th top-level comment has a real nested reply (level-1 with a child <li>).
            if (i % 5 == 0) {
                val replyId = id + 500000
                sb.append("<ul class=\"comment-list level-1\">")
                sb.append("<li data-author-id=\"9$userId\">")
                sb.append("<a id=\"comment$replyId\" class=\"link-anchor\"></a>")
                sb.append("<div id=\"comment-$replyId\"><div class=\"heading\"><div class=\"text-left\">")
                sb.append("<a class=\"comment-avatar\" href=\"https://4pda.to/forum/index.php?showuser=9$userId\"><img src=\"x\"></a>")
                sb.append("<a class=\"nickname\">Reply_$i</a></div></div>")
                sb.append("<div class=\"content\">Ответ на №$i</div></div>")
                sb.append("<ul class=\"comment-list level-2\"></ul></li></ul>")
            } else {
                sb.append("<ul class=\"comment-list level-1\"></ul>")
            }
            sb.append("</li>\n")
        }
        sb.append("</ul>")
        sb.append("<form action=\"https://4pda.to/wp-comments-post.php\" method=\"post\" class=\"comment-form\" id=\"commentform\"><textarea name=\"comment\"></textarea></form>")
        sb.append("</div>")

        val parser = ArticleParser(ArticlesPatternProviderStub())
        val balanced = parser.ensureBalancedCommentsHtml(sb.toString()).orEmpty()
        assertTrue("balanced extraction should not be blank", balanced.isNotBlank())
        val root = parser.parseComments(SparseArray<Comment.Karma>(), balanced)
        assertEquals(total, root.children.size)
        assertEquals(10000001, root.children.first().id)
        assertEquals("User_1", root.children.first().userNick)
        // Replies should be attached as nested children, not flattened or dropped.
        val withReplies = root.children.count { it.children.isNotEmpty() }
        assertEquals(total / 5, withReplies)
    }

    @Test
    fun parseComments_tagFallback_keepsCommentWhenOnlyAuthorOrDateRecognized() {
        // No <ul class="comment-list"> wrapper -> DOM recurse misses -> tag fallback runs.
        // Content/nick use class names the fallback selectors do not recognize, but the node is a
        // real comment (valid id + avatar/showuser + date). It must NOT be dropped, otherwise the
        // whole page parses to zero comments and shows the "parse empty" error.
        val html = """<section>
            <div id="comment-555" class="comment">
                <a class="avatar-link" href="https://4pda.to/forum/index.php?showuser=4242">x</a>
                <span class="user-name">SomeUser</span>
                <a class="comment-date">04.06.2026</a>
                <div class="comment-text">Текст с кавычками "abc"</div>
            </div>
        </section>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val root = parser.parseComments(SparseArray<Comment.Karma>(), html)
        assertEquals(1, root.children.size)
        assertEquals(555, root.children.single().id)
        assertEquals(4242, root.children.single().userId)
    }

    @Test
    fun parseComments_tagFallback_marksDeletedCommentWithoutContent() {
        val html = """<section>
            <div id="comment-9001" class="comment deleted">
                <div class="comment-content"></div>
            </div>
        </section>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val root = parser.parseComments(SparseArray<Comment.Karma>(), html)
        assertEquals(1, root.children.size)
        val comment = root.children.single()
        assertEquals(9001, comment.id)
        assertTrue(comment.isDeleted)
        assertTrue(comment.content.isNullOrBlank())
    }

    @Test
    fun resolveArticleCommentsCount_zeroOwnBadge_ignoresRelatedWidgetInflation() {
        // Real-markup regression: a 0-comment article whose page-wide #comments / v-count scan would
        // otherwise pick the max comment count from the related/popular-news widgets (345). The
        // parser must trust the article's own counter badge (0).
        val html = javaClass.classLoader
                ?.getResourceAsStream("parser/news/article_zero_comments_inflated_count.html")
                ?.bufferedReader()
                ?.readText()
                .orEmpty()
        assertTrue("fixture should load", html.isNotBlank())
        val parser = ArticleParser(ArticlesPatternProviderStub())

        assertEquals(0, parser.resolveArticleCommentsCountForPage(html))
        // The empty inline comment-list confirms there are genuinely zero comments on this page.
        assertEquals(0, parser.parseComments(SparseArray<Comment.Karma>(), html).children.size)
    }

    @Test
    fun resolveArticleCommentsCount_ownBadgeWins_overInflatedRelatedAnchors() {
        // head-comments-count badge says 0 even though related widgets carry large #comments anchors.
        val html = """<article class="post" itemscope itemid="1">
            <div class="article"><div class="article-meta-comment"><a href="/x/#comments">0</a></div></div>
        </article>
        <div class="head-comments-count"><span title="Комментарии"><i class="icon-comment"></i>0</span></div>
        <ul class="comment-list level-0"></ul>
        <article class="post" itemscope itemid="2"><a class="v-count" href="/y/#comments">345</a></article>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        assertEquals(0, parser.resolveArticleCommentsCountForPage(html))
    }

    @Test
    fun commentsSourceUnderfetchesExpected_detectsPartialLazyBatch() {
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val partial = buildString {
            append("""<ul class="comment-list level-0">""")
            repeat(10) { index ->
                val id = 1000 + index
                append("""<li><div id="comment-$id"><div class="content">c$id</div></div></li>""")
            }
            append("</ul>")
        }
        assertTrue(parser.commentsSourceUnderfetchesExpected(partial, expectedCount = 180))
        assertEquals(10, parser.countCommentNodesInSource(partial))
        assertFalse(parser.commentsSourceUnderfetchesExpected(partial, expectedCount = 8))
    }

    @Test
    fun extractCommentEditActionFromHtml_wpAjaxResponse_parsesEmbeddedForm() {
        val html = """<?xml version="1.0" encoding="UTF-8"?>
        <wp_ajax_response>
            <response action="editcomment-10">
                <![CDATA[
                    <input type="hidden" name="_wpnonce" value="nonce-from-cdata">
                    <input type="hidden" name="comment_ID" value="10">
                    <textarea name="content">From cdata</textarea>
                ]]>
            </response>
        </wp_ajax_response>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val action = parser.extractCommentEditActionFromHtml(html, commentId = 10)

        assertEquals("nonce-from-cdata", action?.fields?.get("_wpnonce"))
        assertEquals("From cdata", action?.fields?.get("content"))
    }

    @Test
    fun parseComments_withDataKarmaActionsAttribute_parsesLikeAction() {
        val html = """<ul class="comment-list"><li><div id="comment-10" class="comment">
        <a class="nickname">User</a>
        <span data-karma-actions="456-10"></span>
        <p class="content">Comment</p>
    </div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val comment = parser.parseComments(SparseArray<Comment.Karma>(), html).children.single()

        assertEquals("https://4pda.to/pages/karma?p=456&c=10&v=1", comment.likeAction?.url)
        assertEquals("https://4pda.to/pages/karma?p=456&c=10&v=0", comment.unlikeAction?.url)
    }

    @Test
    fun parseComments_withDataKarmaActWithoutHref_parsesLikeAction() {
        val html = """<ul class="comment-list"><li><div id="comment-10" class="comment">
        <a class="nickname">User</a>
        <a data-karma-act="1-456-10" title="Мне нравится">Нравится</a>
        <p class="content">Comment</p>
    </div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val comment = parser.parseComments(SparseArray<Comment.Karma>(), html).children.single()

        assertEquals("https://4pda.to/pages/karma?p=456&c=10&v=1", comment.likeAction?.url)
        assertEquals(Comment.Action.Type.COMMENT_LIKE, comment.likeAction?.type)
    }

    @Test
    fun ensureCommentLikeActions_withoutKarmaMarkup_usesArticleIdFallback() {
        val html = """<ul class="comment-list"><li><div id="comment-10" class="comment">
        <a class="nickname">ShantiSnake</a>
        <p class="content">Comment without karma markup</p>
    </div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val root = parser.parseComments(SparseArray<Comment.Karma>(), html)

        parser.ensureCommentLikeActions(root, articleId = 456, commentsSource = html)

        val comment = root.children.single()
        assertEquals("https://4pda.to/pages/karma?p=456&c=10&v=1", comment.likeAction?.url)
        assertEquals("https://4pda.to/pages/karma?p=456&c=10&v=0", comment.unlikeAction?.url)
    }

    @Test
    fun ensureCommentLikeActions_withConflictingKarmaMarkup_doesNotUseArticleFallback() {
        val html = """<ul class="comment-list"><li><div id="comment-10" class="comment">
        <a class="nickname">User</a>
        <a class="comment-karma" href="https://4pda.to/pages/karma?p=456&amp;c=11&amp;v=1">+</a>
        <p class="content">Comment</p>
    </div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val root = parser.parseComments(SparseArray<Comment.Karma>(), html)

        parser.ensureCommentLikeActions(root, articleId = 456, commentsSource = html)

        val comment = root.children.single()
        assertNull(comment.likeAction)
    }

    @Test
    fun mergeCommentDesktopActions_fromDesktopSource_mergesLikeActionsByCommentId() {
        val mobile = """<ul class="comment-list"><li><div id="comment-10" class="comment">
        <a class="nickname">User</a>
        <p class="content">Mobile comment</p>
    </div></li></ul>"""
        val desktop = """<ul class="comment-list"><li><div id="comment-10" class="comment">
        <a class="nickname">User</a>
        <span class="karma" data-karma="456-10"></span>
        <p class="content">Desktop comment</p>
    </div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val root = parser.parseComments(SparseArray<Comment.Karma>(), mobile)

        parser.mergeCommentDesktopActions(root, desktop)

        val comment = root.children.single()
        assertEquals("https://4pda.to/pages/karma?p=456&c=10&v=1", comment.likeAction?.url)
        assertEquals("https://4pda.to/pages/karma?p=456&c=10&v=0", comment.unlikeAction?.url)
    }

    @Test
    fun parseComments_balancedHaierFixture_parsesCommentMenuActions() {
        val page = javaClass.classLoader
                ?.getResourceAsStream("parser/news/haier_w3_article.html")
                ?.bufferedReader()
                ?.readText()
                .orEmpty()
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val balanced = parser.ensureBalancedCommentsHtml(page).orEmpty()
        val first = parser.parseComments(SparseArray<Comment.Karma>(), balanced).children.first()

        assertEquals(Comment.Action.Type.REPLY, first.actions.reply?.type)
        assertEquals(Comment.Action.Type.REPORT, first.actions.report?.type)
        assertEquals("https://4pda.to/pages/karma?p=456862&c=10597307&v=1", first.likeAction?.url)
    }

    @Test
    fun parseCommentsBatch_haierFixture_parsesCommentMenuActions() {
        val page = javaClass.classLoader
                ?.getResourceAsStream("parser/news/haier_w3_article.html")
                ?.bufferedReader()
                ?.readText()
                .orEmpty()
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val balanced = parser.ensureBalancedCommentsHtml(page).orEmpty()
        val batch = parser.parseCommentsBatch(SparseArray<Comment.Karma>(), balanced, skip = 0, limit = 5)
        val first = batch.children.first()

        assertEquals(5, batch.children.size)
        assertEquals(10597307, first.id)
        assertEquals("https://4pda.to/pages/karma?p=456862&c=10597307&v=1", first.likeAction?.url)
        assertEquals(Comment.Action.Type.REPLY, first.actions.reply?.type)
        assertEquals(Comment.Action.Type.REPORT, first.actions.report?.type)
        assertEquals(Comment.Action.Type.HIDE, first.actions.hide?.type)
        assertEquals(Comment.Action.Type.REPUTATION_PLUS, first.actions.reputationPlus?.type)
        assertEquals(Comment.Action.Type.REPUTATION_MINUS, first.actions.reputationMinus?.type)
        assertEquals("https://4pda.to/pages/karma?p=456862&c=10597307&v=1", first.likeAction?.url)
    }

    @Test
    fun parseCommentsViaTagsOnly_haierFixture_parsesCommentMenuActions() {
        val page = javaClass.classLoader
                ?.getResourceAsStream("parser/news/haier_w3_article.html")
                ?.bufferedReader()
                ?.readText()
                .orEmpty()
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val balanced = parser.ensureBalancedCommentsHtml(page).orEmpty()
        val root = parser.parseCommentsViaTagsOnly(SparseArray<Comment.Karma>(), balanced)
        val first = root.children.first()

        assertEquals(5, root.children.size)
        assertEquals(Comment.Action.Type.REPORT, first.actions.report?.type)
        assertEquals(Comment.Action.Type.REPUTATION_PLUS, first.actions.reputationPlus?.type)
        assertEquals("https://4pda.to/pages/karma?p=456862&c=10597307&v=1", first.likeAction?.url)
    }

    @Test
    fun parseCommentsBatch_withoutDomKarmaMarkup_fillsLikeFromHtmlRegex() {
        val html = buildString {
            append("""<ul class="comment-list level-0">""")
            repeat(3) { index ->
                val id = 100 + index
                append("""<li><div id="comment-$id" class="comment">""")
                append("""<a class="nickname">User$id</a>""")
                if (index == 1) {
                    append("""<span data-karma-actions="456-$id"></span>""")
                }
                append("""<p class="content">Body$id</p></div></li>""")
            }
            append("</ul>")
        }
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val batch = parser.parseCommentsBatch(SparseArray<Comment.Karma>(), html, skip = 0, limit = 3)

        assertEquals(3, batch.children.size)
        assertNull(batch.children[0].likeAction)
        assertEquals("https://4pda.to/pages/karma?p=456&c=101&v=1", batch.children[1].likeAction?.url)
        assertNull(batch.children[2].likeAction)
    }

    @Test
    fun parseCommentsBatch_editedPlainText_isDetectedAndStripped() {
        // Живой inline-путь (пагинированные комментарии) идёт через parseCommentsFromTags,
        // а не через основной parseComments. Раньше он вообще не помечал правку: isEdited
        // оставался false и «(отредактирован)» висел прямо в тексте.
        val html = """<ul class="comment-list level-0"><li><div id="comment-100" class="comment">
        <a class="nickname">Huts</a>
        <p class="content">В какой момент должен наступить эмейзинг? (отредактирован)</p>
    </div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val batch = parser.parseCommentsBatch(SparseArray<Comment.Karma>(), html, skip = 0, limit = 3)

        val comment = batch.children.single()
        assertTrue(comment.isEdited)
        assertTrue(comment.content.orEmpty().contains("эмейзинг"))
        assertTrue(!comment.content.orEmpty().contains("отредактирован"))
    }
}
