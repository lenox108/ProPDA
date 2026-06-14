package forpdateam.ru.forpda.model.data.remote.api.regex.parser

import androidx.annotation.Nullable

/**
 * Created by radiationx on 13.08.17.
 */
open class Node {

    companion object {
        const val NODE_DOCUMENT = "#document"
        const val NODE_TEXT = "#text"
        const val NODE_COMMENT = "#comment"
    }

    constructor()
    constructor(name: String?) { this.name = name }

    var name: String? = null
    var text: String? = null
    private val nodes: ArrayList<Node> = ArrayList()
    private val elements: ArrayList<Node> = ArrayList()
    internal val attributes: LinkedHashMap<String, String> = LinkedHashMap()

    fun getNodes(): ArrayList<Node> = nodes
    fun addNode(node: Node) { nodes.add(node) }
    fun getElements(): ArrayList<Node> = elements
    fun addElement(node: Node) { elements.add(node) }
    fun getAttributes(): LinkedHashMap<String, String> = attributes
    fun putAttribute(name: String, value: String) { attributes[name] = value }
    @Nullable
    fun getAttribute(attr: String): String? = attributes[attr]

    override fun toString(): String = name ?: ""
}
