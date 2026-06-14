package forpdateam.ru.forpda.model.data.remote.api.regex.parser
import timber.log.Timber

import forpdateam.ru.forpda.BuildConfig
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Created by radiationx on 13.08.17.
 */
object Parser {

    private const val S_TAG = 1
    private const val S_ATTRS = 2
    private const val S_TEXT = 3
    private const val CLOSING = 4
    private const val TAG = 5
    private const val ATTRS = 6
    private const val TEXT = 7

    private var mainPattern: Pattern? = null
    private var attributePattern: Pattern? = null
    private var uTags: Array<String>? = null

    @JvmStatic
    fun getMainPattern(): Pattern {
        if (mainPattern == null)
            mainPattern = Pattern.compile("\\<(?:(?:(script|style|textarea)(?:([^\\>]+))?\\>)([\\s\\S]*?)(?:\\<\\/\\1)|([\\/])?(!?[\\w]*)(?:([^\\>]+))?\\/?)\\>(?:([^<]+))?", Pattern.CASE_INSENSITIVE)
        return mainPattern!!
    }

    @JvmStatic
    fun getAttributePattern(): Pattern {
        if (attributePattern == null)
            attributePattern = Pattern.compile("([^ \"']*?)\\s*?=\\s*?([\"'])([\\s\\S]*?)\\2", Pattern.CASE_INSENSITIVE)
        return attributePattern!!
    }

    @JvmStatic
    fun getuTags(): Array<String> {
        if (uTags == null)
            uTags = arrayOf("!doctype", "area", "br", "col", "colgroup", "command", "embed", "hr", "img", "input", "keygen", "link", "meta", "param", "source", "track", "wbr")
        return uTags!!
    }

    private fun containsInUTag(tag: String): Boolean =
        getuTags().any { it.equals(tag, ignoreCase = true) }

    @JvmStatic
    fun getMatcher(m: Matcher?, p: Pattern, s: String): Matcher =
        m?.reset(s) ?: p.matcher(s)

    @JvmStatic
    fun parse(html: String): Document {
        val openedNodes = mutableListOf<Node>()
        val root = Document()
        openedNodes.add(root)
        var lastOpened: Node

        val matcher = getMainPattern().matcher(html)
        var ncMatcher: Matcher? = null
        var attrMatcher: Matcher? = null
        var nodesAdd = 0; var nodesClose = 0
        while (matcher.find()) {
            lastOpened = openedNodes[openedNodes.size - 1]
            val node = Node()

            var special = false
            var tagName = matcher.group(TAG)
            if (tagName == null) special = true

            val openAction = matcher.group(CLOSING) == null

            if (openAction) {
                if (special) {
                    tagName = matcher.group(S_TAG)
                    special = tagName != null
                }
                val attrs = matcher.group(if (special) S_ATTRS else ATTRS)
                val text = matcher.group(if (special) S_TEXT else TEXT)

                var addToOpened = true
                if (tagName == null) {
                    if (text == null) {
                        node.name = Node.NODE_COMMENT
                        node.text = matcher.group()
                    }
                    addToOpened = false
                } else {
                    node.name = tagName
                    if (attrs != null) {
                        attrMatcher = getMatcher(attrMatcher, getAttributePattern(), attrs)
                        while (attrMatcher.find()) {
                            node.putAttribute(attrMatcher.group(1), attrMatcher.group(3))
                        }
                    }
                    if (containsInUTag(tagName)) {
                        if (tagName.equals(Document.DOCTYPE_TAG, ignoreCase = true)) {
                            root.docType = attrs ?: root.docType
                        }
                        addToOpened = false
                    }
                    if (text != null) {
                        if (special) addToOpened = false
                        val textNode = Node(Node.NODE_TEXT)
                        textNode.text = text
                        node.addNode(textNode)
                        nodesAdd++
                    }
                }
                lastOpened.addNode(node)
                nodesAdd++
                if (addToOpened) openedNodes.add(node)
            } else {
                val text = matcher.group(TEXT)
                openedNodes.removeAt(openedNodes.size - 1)
                nodesClose++
                if (text != null && openedNodes.isNotEmpty()) {
                    val textNode = Node(Node.NODE_TEXT)
                    textNode.text = text
                    openedNodes[openedNodes.size - 1].addNode(textNode)
                    nodesAdd++
                }
            }
        }
        openedNodes.remove(root)
        if (BuildConfig.DEBUG) Timber.d("Parser FINAL OPENED ${openedNodes.size} : $nodesAdd : $nodesClose")
        return root
    }

    @JvmStatic
    fun isNotElement(node: Node): Boolean {
        val name = node.name
        return name == null || name == Node.NODE_TEXT || name == Node.NODE_COMMENT
    }

    @JvmStatic
    fun isTextNode(node: Node): Boolean = node.name == Node.NODE_TEXT

    @JvmStatic
    fun getHtml(document: Document, node: Node, matcher: Matcher?): String {
        val resultHtml = StringBuilder()
        val onlyText = isNotElement(node)
        if (onlyText) {
            resultHtml.append(node.text)
        } else {
            resultHtml.append("<").append(node.name)
            if (node.attributes.isNotEmpty()) {
                for ((key, value) in node.attributes) {
                    resultHtml.append(" ").append(key).append("=\"").append(value).append("\"")
                }
            } else {
                if (node.name.equals(Document.DOCTYPE_TAG, ignoreCase = true)) {
                    resultHtml.append(" ").append(document.docType)
                }
            }
            resultHtml.append(">")
        }
        if (!onlyText) {
            for (child in node.getNodes()) {
                resultHtml.append(getHtml(document, child, matcher))
            }
        }
        if (!onlyText) {
            if (!containsInUTag(node.name!!)) {
                resultHtml.append("</").append(node.name).append(">")
            }
        }
        return resultHtml.toString()
    }

    @JvmStatic
    fun getHtml(node: Node, onlyInner: Boolean): String {
        if (isNotElement(node)) return node.text ?: ""
        val resultHtml = StringBuilder()
        if (!onlyInner) {
            resultHtml.append("<").append(node.name)
            if (node.attributes.isNotEmpty()) {
                for ((key, value) in node.attributes) {
                    resultHtml.append(" ").append(key).append("=\"").append(value).append("\"")
                }
            }
            resultHtml.append(">")
        }
        for (child in node.getNodes()) {
            resultHtml.append(getHtml(child, false))
        }
        if (!onlyInner) {
            if (!containsInUTag(node.name!!)) {
                resultHtml.append("</").append(node.name).append(">")
            }
        }
        return resultHtml.toString()
    }

    @JvmStatic
    fun findNode(node: Node, tag: String, attr: String?, value: String): Node? {
        if (isNotElement(node)) return null
        if (node.name.equals(tag, ignoreCase = true)) {
            if (attr == null) return node
            val attrValue = node.attributes[attr]
            if (attrValue != null && attrValue.contains(value)) return node
        }
        for (child in node.getNodes()) {
            val result = findNode(child, tag, attr, value)
            if (result != null) return result
        }
        return null
    }

    @JvmStatic
    fun findChildNodes(node: Node, tag: String, attr: String?, value: String): ArrayList<Node> {
        val result = ArrayList<Node>()
        if (isNotElement(node)) return result
        for (child in node.getNodes()) {
            if (isNotElement(child)) continue
            if (child.name.equals(tag, ignoreCase = true)) {
                if (attr == null) { result.add(child); continue }
                val attrValue = child.attributes[attr]
                if (attrValue != null && attrValue.contains(value)) result.add(child)
            }
        }
        return result
    }

    @JvmStatic
    fun ownText(node: Node): String {
        val sb = StringBuilder()
        for (child in node.getNodes()) {
            if (isTextNode(child)) sb.append(child.text)
        }
        return sb.toString()
    }
}
