package forpdateam.ru.forpda.model.interactors.news

import forpdateam.ru.forpda.entity.remote.news.Comment
import org.junit.Assert.assertEquals
import org.junit.Test

class NewCommentDetectorTest {

    @Test
    fun `extractCommentIdFromUrl reads comment and entry anchors`() {
        assertEquals(123, NewCommentDetector.extractCommentIdFromUrl("https://4pda.to/index.php?p=1#comment-123"))
        assertEquals(456, NewCommentDetector.extractCommentIdFromUrl("https://4pda.to/index.php?p=1#comment_456"))
        assertEquals(789, NewCommentDetector.extractCommentIdFromUrl("https://4pda.to/index.php?p=1#entry789"))
    }

    @Test
    fun `findNewCommentId prefers submitted text when ids are localized`() {
        val tree = Comment().apply {
            children.add(Comment().apply { id = 3; content = "older reply" })
            children.add(Comment().apply { id = 2; content = "My fresh reply text" })
        }
        val id = NewCommentDetector.findNewCommentId(
                tree = tree,
                knownIds = setOf(1, 3),
                submittedText = "My fresh reply text"
        )
        assertEquals(2, id)
    }

    @Test
    fun `resolvePendingScrollCommentId prefers redirect over stale sole tree id`() {
        val tree = Comment().apply {
            children.add(Comment().apply { id = 7 })
        }
        val id = NewCommentDetector.resolvePendingScrollCommentId(
                tree = tree,
                knownIds = emptySet(),
                redirectUrl = "https://4pda.to/index.php?p=42#comment-99"
        )
        assertEquals(99, id)
    }

    @Test
    fun `resolvePendingScrollCommentId uses redirect when tree diff is empty`() {
        val tree = Comment().apply {
            children.add(Comment().apply { id = 7 })
        }
        val id = NewCommentDetector.resolvePendingScrollCommentId(
                tree = tree,
                knownIds = setOf(7),
                redirectUrl = "https://4pda.to/index.php?p=42#comment-88"
        )
        assertEquals(88, id)
    }

    @Test
    fun `findNewCommentId returns sole unknown comment without max id heuristic`() {
        val tree = Comment().apply {
            children.add(Comment().apply { id = 5; content = "only new" })
        }
        assertEquals(5, NewCommentDetector.findNewCommentId(tree, setOf(10, 20)))
    }
}
