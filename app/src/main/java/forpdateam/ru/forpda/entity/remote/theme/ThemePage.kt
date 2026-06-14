package forpdateam.ru.forpda.entity.remote.theme

import java.util.ArrayList

import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination

/**
 * Created by radiationx on 04.08.16.
 */
class ThemePage {
    val anchors = mutableListOf<String>()
    var title: String? = null
    var desc: String? = null
    var html: String? = null
    var url: String? = null
    var id = 0
    var forumId = 0
    var favId = 0
    /*public boolean isCurator() {
        return curator;
    }

    public void setCurator(boolean curator) {
        this.curator = curator;
    }*/

    var scrollY = 0
    var anchorPostId: String? = null
    var anchorOffsetTop: Double? = null
    var scrollRatio: Double? = null
    var wasNearBottom: Boolean = false
    var refreshRestoreId: String? = null
    var refreshRestoreMode: String? = null
    var refreshRestoreSource: String? = null
    var renderSignature: String? = null
    var postsFragmentHtml: String? = null
    var isInFavorite = false
    var isCurator = false
    var canQuote = false
    var isHatOpen = false
    var isInlineHatOpen = false
    var isPollOpen = false
    var hasUnreadTarget = false
    var ambiguousLastUnreadBottomRedirect = false
    /**
     * Read-resume / all-read bottom redirect resolved its anchor to the LAST post of the last page.
     * The soft anchor scroll must then land on the BOTTOM of the page (like END navigation) instead
     * of the top of that final post, otherwise the user stays mid-page on a tall final post.
     */
    var resumeToLastPageBottom = false
    var openSessionKind: String? = null
    var topicHatPost: ThemePost? = null
    val posts = ArrayList<ThemePost>()
    var pagination = Pagination()
    var poll: Poll? = null

    val anchor: String?
        get() = if (anchors.isEmpty()) null else anchors[anchors.size - 1]

    val st: Int
        // pagination.current — 1-индексированный номер страницы; на 4PDA st — это 0-based offset
        // (страница 1 → st=0, страница 2 → st=perPage, …). Иначе при возврате назад URL получает st
        // на одну страницу больше реального; для последних страниц сервер клампит до последней.
        get() = (pagination.current - 1).coerceAtLeast(0) * pagination.perPage

    fun addAnchor(anchor: String): Boolean {
        return anchors.add(anchor)
    }

    fun removeAnchor(): String? {
        return if (anchors.isEmpty()) null else anchors.removeAt(anchors.size - 1)
    }
}
