package forpdateam.ru.forpda.common.bbcode

/**
 * Единый каталог BBCode, который используют редактор, подсветка и локальный предпросмотр.
 *
 * UI хранит только ресурсы и специальные диалоги конкретных инструментов. Имена тегов,
 * порядок основной панели и перечень распознаваемых тегов задаются здесь, чтобы новые
 * возможности не приходилось синхронизировать вручную в нескольких классах.
 */
object BbcodeRegistry {

    enum class Tool(val tag: String) {
        BOLD("B"),
        ITALIC("I"),
        UNDERLINE("U"),
        STRIKE("S"),
        URL("URL"),
        SPOILER("SPOILER"),
        OFFTOP("OFFTOP"),
        QUOTE("QUOTE"),
        CODE("CODE"),
        COLOR("COLOR"),
        SIZE("SIZE"),
        FONT("FONT"),
        HIDE("HIDE"),
        BACKGROUND("BACKGROUND"),
        LIST("LIST"),
        NUMBERED_LIST("NUMLIST"),
        LEFT("LEFT"),
        CENTER("CENTER"),
        RIGHT("RIGHT"),
        SUBSCRIPT("SUB"),
        SUPERSCRIPT("SUP"),
        CURATOR("CUR"),
        RELEASER("RELEASER"),
    }

    val editorTools: List<Tool> = Tool.entries

    /** Теги, которые подсвечивает [forpdateam.ru.forpda.ui.views.CodeEditor]. */
    val syntaxTags: Set<String> = buildSet {
        addAll(editorTools.map { it.tag.lowercase() })
        addAll(
            listOf(
                "attachment",
                "nomergetime",
                "mergetime",
                "snapback",
                "img",
                "*",
            )
        )
    }

    /** Теги, которые безопасно интерпретирует локальный предпросмотр. */
    val previewTags: Set<String> = setOf(
        "b",
        "i",
        "u",
        "s",
        "strike",
        "url",
        "quote",
        "spoiler",
        "offtop",
        "hide",
        "code",
        "snapback",
        "mergetime",
        "br",
        "size",
        "color",
        "background",
        "font",
        "left",
        "center",
        "right",
        "sub",
        "sup",
        "cur",
        "list",
        "numlist",
        "*",
    )

    fun findTool(tag: String): Tool? =
        editorTools.firstOrNull { it.tag.equals(tag, ignoreCase = true) }

    fun createPair(tag: String, argument: String? = null): Pair<String, String> {
        val normalized = tag.uppercase()
        val opening = buildString {
            append('[')
            append(normalized)
            argument?.takeIf { it.isNotBlank() }?.let {
                append('=')
                append(it)
            }
            append(']')
        }
        return opening to "[/$normalized]"
    }
}
