package forpdateam.ru.forpda.model.data.remote.api.forum

import forpdateam.ru.forpda.entity.remote.forum.ForumItemFlat
import forpdateam.ru.forpda.entity.remote.forum.ForumItemTree
import forpdateam.ru.forpda.entity.remote.forum.IForumItemFlat

/**
 * Собирает дерево разделов из плоского списка по [IForumItemFlat.parentId].
 *
 * Раньше использовался стек по полю level и **порядку строк** в списке (как в старом select).
 * При загрузке из Realm [io.realm.RealmQuery.findAll] порядок не гарантирован — соседние
 * подфорумы оказывались «ребёнком предыдущего» и визуально «лесенкой» уходили вправо.
 *
 * Если один и тот же [id] встречается дважды с разными parentId (битый кэш / старый парсер),
 * приоритет у привязки к корню (parentId ≤ 0), иначе один узел оказывается у двух родителей.
 */
fun buildForumTreeFromFlatList(list: Collection<IForumItemFlat>, rootForum: ForumItemTree) {
    if (list.isEmpty()) return
    val merged = mergeDuplicateForumIds(list)
    val nodes = LinkedHashMap<Int, ForumItemTree>(merged.size)
    for (item in merged) {
        nodes[item.id] = ForumItemTree(item)
    }
    for (item in merged) {
        val node = nodes[item.id] ?: continue
        val p = item.parentId
        when {
            p <= 0 || p == -1 -> rootForum.addForum(node)
            else -> {
                val parentNode = nodes[p]
                if (parentNode != null) {
                    parentNode.addForum(node)
                } else {
                    rootForum.addForum(node)
                }
            }
        }
    }
}

private fun mergeDuplicateForumIds(list: Collection<IForumItemFlat>): List<IForumItemFlat> {
    val preferRoot = { pid: Int -> pid <= 0 || pid == -1 }
    val bestParent = mutableMapOf<Int, Int>()
    for (item in list) {
        val id = item.id
        val p = item.parentId
        val old = bestParent[id]
        bestParent[id] = when {
            old == null -> p
            preferRoot(p) && !preferRoot(old) -> p
            preferRoot(old) && !preferRoot(p) -> old
            else -> old
        }
    }
    val seen = mutableSetOf<Int>()
    val out = mutableListOf<IForumItemFlat>()
    for (item in list) {
        val id = item.id
        if (id in seen) continue
        seen.add(id)
        out.add(ForumItemFlat().apply {
            this.id = id
            parentId = bestParent[id] ?: 0
            level = item.level
            title = item.title
        })
    }
    return out
}
