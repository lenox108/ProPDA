package forpdateam.ru.forpda.presentation.articles.detail

import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.diagnostic.FpdaDebugLog
import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import forpdateam.ru.forpda.entity.remote.news.Tag
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleBlock
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleHtmlValidator
import forpdateam.ru.forpda.ui.TemplateManager

class ArticleTemplate(
        private val templateManager: TemplateManager
) {

    fun mapEntity(page: DetailsPage): DetailsPage = page.apply { html = mapString(page) }

    /** Stable key baked into mapped HTML so cache hits can skip full template rebuild. */
    fun currentThemeCacheKey(): String {
        val style = templateManager.getThemeType()
        val overridesHash = templateManager.getThemeOverridesCss().hashCode()
        return "$style|$overridesHash"
    }

    fun isMappedForCurrentTheme(html: String?): Boolean {
        val source = html.orEmpty()
        if (source.isBlank()) return false
        return source.contains("data-fpda-theme-key=\"${currentThemeCacheKey()}\"")
    }

    /**
     * Rebuilds mapped WebView HTML using the current app theme (light/dark + palette overrides).
     * Required for disk/memory cache entries that still contain an older baked [style_type].
     */
    fun remapWithCurrentTheme(page: DetailsPage): DetailsPage {
        if (isMappedForCurrentTheme(page.html)) return page
        val body = prepareBodyHtml(page.html)
        if (body.isNullOrBlank()) return page
        return mapEntity(page.apply { html = body })
    }

    fun mapString(page: DetailsPage): String {
        val template = templateManager.getTemplate(TemplateManager.TEMPLATE_NEWS)

        // Кэшированный экземпляр MiniTemplator ОБЩИЙ и НЕ потокобезопасен: конкурентный маппинг
        // (префетч статьи в фоне + открытие другой) переплетал setVariable/reset разных потоков, и в
        // шаблон подставлялся ПУСТОЙ ${style_type} → ссылки ломались (styles//_main.css,
        // styles//_news.css не грузились) → перемежающийся срыв вёрстки, лечился лишь рестартом
        // (см. style_state_probe: hrefs=[…,"/_main.css","/_news.css"]). Сериализуем на экземпляре.
        return synchronized(template) {
        template.reset()
        template.apply {
            templateManager.fillStaticStrings(this)
            setVariableOpt("style_type", templateManager.getThemeType())
            setVariableOpt("theme_overrides_css", templateManager.getThemeOverridesCss())
            setVariableOpt("theme_cache_key", currentThemeCacheKey())

            setVariableOpt("details_title", htmlEncode(page.title))
            setVariableOpt("details_header", buildHeader(page))
            // Remove duplicate title from HTML content (title is already shown in UI toolbar)
            val normalizedBodyHtml = prepareBodyHtml(page.html)
            val sanitizedBodyHtml = ArticleHtmlSecuritySanitizer.sanitize(normalizedBodyHtml)
            val typedPollBefore = ArticleBlock.findPollBlock(normalizedBodyHtml)
            val typedPollAfter = ArticleBlock.findPollBlock(sanitizedBodyHtml)
            val pollInvariantOk = ArticleBlock.pollSurvivedSanitize(normalizedBodyHtml, sanitizedBodyHtml)
            if (BuildConfig.DEBUG) {
                val hadPollBefore = typedPollBefore != null ||
                        normalizedBodyHtml?.contains("poll", ignoreCase = true) == true ||
                        normalizedBodyHtml?.contains("vote", ignoreCase = true) == true
                val hasPollAfter = typedPollAfter != null ||
                        sanitizedBodyHtml?.contains("news-poll", ignoreCase = true) == true ||
                        sanitizedBodyHtml?.contains("poll-ajax-frame", ignoreCase = true) == true
                if (hadPollBefore || hasPollAfter) {
                    val sanitizerRemovedPoll = typedPollBefore != null && !pollInvariantOk
                    FpdaDebugLog.log(
                            FpdaDebugLog.TAG_ARTICLE_POLL,
                            "template_sanitize",
                            mapOf(
                                    "articleId" to page.id,
                                    "hadPollMarkersBefore" to hadPollBefore,
                                    "hasNormalizedPollAfter" to hasPollAfter,
                                    "hasPollToken" to (sanitizedBodyHtml?.contains("data-news-poll-token") == true),
                                    "sanitizerRemovedPoll" to sanitizerRemovedPoll,
                                    "pollInvariantOk" to pollInvariantOk,
                                    "renderedPollBlock" to hasPollAfter,
                                    "typedPollBefore" to (typedPollBefore != null),
                                    "typedPollAfter" to (typedPollAfter != null),
                                    "pollId" to typedPollAfter?.pollId,
                                    "bodyLenBefore" to (normalizedBodyHtml?.length ?: 0),
                                    "bodyLenAfter" to (sanitizedBodyHtml?.length ?: 0)
                            )
                    )
                    if (sanitizerRemovedPoll) {
                        FpdaDebugLog.warn(
                                FpdaDebugLog.TAG_ARTICLE_POLL,
                                "poll_sanitizer_invariant_broken",
                                mapOf(
                                        "articleId" to page.id,
                                        "pollId" to typedPollBefore?.pollId
                                )
                        )
                    }
                }
            }
            val contentWithoutTitle = removeTitleFromHtml(sanitizedBodyHtml, page.title)
            val contentWithoutEmbeddedComments = removeEmbeddedCommentsFromHtml(contentWithoutTitle)
            // The native header card already shows the hero image (page.imgUrl); 4pda repeats that same
            // lead image as the first `article-figure-big` in the body, so the reader saw it twice. Drop
            // the body's leading hero figure ONLY when the header actually renders a hero — articles
            // without a header image (imgUrl blank) keep their body figure untouched.
            val contentDeduped = if (!page.imgUrl.isNullOrBlank()) {
                removeLeadingHeroFigure(contentWithoutEmbeddedComments)
            } else {
                contentWithoutEmbeddedComments
            }
            setVariableOpt("details_content", stabilizeMediaLayout(contentDeduped))
            setVariableOpt(
                    "details_comments_footer",
                    commentsFooterHtml(
                            commentsCount = page.commentsCount,
                            hasCommentsSource = !page.commentsSource.isNullOrBlank()
                    )
            )
            for (material in page.materials) {
                setVariableOpt("material_id", material.id)
                setVariableOpt("material_image", material.imageUrl)
                setVariableOpt("material_title", material.title)
                addBlockOpt("material")
            }
        }

        val result = template.generateOutput()
        template.reset()
        result
        }
    }

    /**
     * Some pipelines may accidentally pass already-templated HTML back into the template mapper
     * (e.g. deferred "extras" remap). If we wrap a full news template again, the WebView will
     * render a nested header/content block inside `.content`.
     *
     * To keep mapping idempotent, detect already-templated "news" HTML and extract the inner
     * article body (`<div class="content">...</div>`). If extraction fails, fall back to the
     * original HTML.
     */
    private fun prepareBodyHtml(html: String?): String? {
        if (html.isNullOrBlank()) return html
        val extracted = normalizeIncomingBodyHtml(html) ?: html
        return ArticleBodyThemeNormalizer.sanitizeForAppTheme(
                extracted,
                templateManager.getThemeType() == "dark"
        )
    }

    private fun normalizeIncomingBodyHtml(html: String?): String? {
        if (html.isNullOrBlank()) return html
        val source = html

        // Fast-path: raw body fragments usually don't contain <html>/<body>.
        val looksLikeDocument = documentMarkerRegex.containsMatchIn(source)
        val looksLikeNewsTemplate = looksLikeDocument && newsTemplateMarkerRegex.containsMatchIn(source)
        if (!looksLikeNewsTemplate) return source

        // Extract the full `.content` block (balanced divs — nested wrappers are common on 4PDA).
        val inner = ArticleHtmlValidator.extractFirstDivWithClassInner(source, "content") ?: return source
        return inner.trim()
    }

    fun detectMappedStyleType(html: String): String? =
            mappedStyleTypeRegex.find(html)?.groupValues?.get(1)?.lowercase()
                    ?.takeIf { it == "light" || it == "dark" }

    fun commentsFooterHtml(commentsCount: Int, hasCommentsSource: Boolean = false): String =
            buildCommentsFooter(commentsCount, hasCommentsSource)

    /**
     * Updates baked comment totals in already-mapped article HTML (cache/prefetch may carry a
     * first-batch undercount such as 20 while the feed badge shows the real total).
     */
    fun restampCommentsCountInMappedHtml(html: String?, commentsCount: Int): String? {
        if (html.isNullOrBlank() || commentsCount <= 0) return html
        var result = html
        result = FOOTER_COMMENTS_COUNT_ATTR_REGEX.replace(result) { match ->
            "${match.groupValues[1]}$commentsCount${match.groupValues[2]}"
        }
        result = FOOTER_TOGGLE_TITLE_COUNT_REGEX.replace(result) { match ->
            "${match.groupValues[1]} ($commentsCount)${match.groupValues[2]}"
        }
        result = HEADER_META_MIDDLE_COUNT_REGEX.replace(result) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}$commentsCount${match.groupValues[3]}"
        }
        return result
    }

    private fun buildCommentsFooter(commentsCount: Int, @Suppress("UNUSED_PARAMETER") hasCommentsSource: Boolean = false): String {
        // Always render footer: phase-1 often has count=0 and no comments UL; deferred extras update the title later.
        val commentsLabel = templateManager.getStaticString("res_s_comments")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: DEFAULT_COMMENTS_LABEL
        val showActionLabel = templateManager.getStaticString("news_inline_comments_show")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: DEFAULT_SHOW_ACTION_LABEL
        val hideActionLabel = templateManager.getStaticString("news_inline_comments_hide")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: DEFAULT_HIDE_ACTION_LABEL
        val retryLabel = templateManager.getStaticString("retry")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: DEFAULT_RETRY_LABEL
        val countSuffix = when {
            commentsCount > 0 -> " ($commentsCount)"
            commentsCount < 0 -> " (?)"
            else -> ""
        }

        return """
<section id="news-comments-section" class="news-comments-section" data-collapsed="true" data-state="not-loaded" data-comments-count="$commentsCount" data-label-show="${htmlEncode(showActionLabel)}" data-label-hide="${htmlEncode(hideActionLabel)}">
    <button id="news-comments-toggle" type="button" class="news-comments-toggle" aria-expanded="false" aria-controls="news-comments-body" onclick="$COMMENTS_TOGGLE_ONCLICK">
        <span class="news-comments-toggle-title">${htmlEncode(commentsLabel)}$countSuffix</span>
        <span class="news-comments-toggle-action">${htmlEncode(showActionLabel)}</span>
        <span class="news-comments-toggle-arrow" aria-hidden="true">▼</span>
    </button>
    <div id="news-comments-body" class="news-comments-body" hidden>
        <div id="news-inline-comments-status" class="news-inline-comments-status"></div>
        <button id="news-inline-comments-retry" type="button" class="news-inline-comments-retry" aria-controls="news-inline-comments-list" onclick="$COMMENTS_RETRY_ONCLICK">${htmlEncode(retryLabel)}</button>
        <div id="news-inline-comments-list" class="news-inline-comments-list"></div>
        <button id="news-inline-comments-more" type="button" class="news-inline-comments-more" hidden>Показать ещё</button>
    </div>
</section>
""".trim()
    }

    private fun buildHeader(page: DetailsPage): String {
        val imageUrl = page.imgUrl?.takeIf { it.isNotBlank() }
        val title = htmlEncode(page.title).orEmpty()
        val author = htmlEncode(page.author).orEmpty()
        val date = htmlEncode(page.date).orEmpty()
        val comments = page.commentsCount.takeIf { it >= 0 }?.toString().orEmpty()
        val meta = listOf(author, comments, date)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        val taxonomy = buildCategory(page.category)

        if (imageUrl == null && title.isBlank() && meta.isBlank() && taxonomy.isBlank()) return ""
        val image = imageUrl?.let {
            """    <img class="news-detail-header-image app-stable-media" src="${htmlEncode(it)}" alt="" loading="eager" style="aspect-ratio: 1.7857; width: 100%; height: auto;" />"""
        }.orEmpty()

        return """
<article class="news-detail-header">
$image
    <div class="news-detail-header-text">
        ${title.takeIf { it.isNotBlank() }?.let { "<h1>$it</h1>" }.orEmpty()}
        ${meta.takeIf { it.isNotBlank() }?.let { "<div class=\"news-detail-header-meta\">$it</div>" }.orEmpty()}
        $taxonomy
    </div>
</article>
""".trim()
    }

    private fun buildCategory(category: Tag?): String =
            category?.let { buildTaxonomySection("Раздел", listOf(it), "news-detail-category") }.orEmpty()

    private fun buildTaxonomySection(label: String, tags: List<Tag>, sectionClass: String): String {
        val chips = linkedMapOf<String, Tag>()
        tags.forEach { tag ->
            val key = chipKey(tag)
            if (key.isNotBlank()) chips.putIfAbsent(key, tag)
        }
        if (chips.isEmpty()) return ""
        return chips.values.joinToString(
                separator = "",
                prefix = "<div class=\"news-detail-taxonomy $sectionClass\"><span class=\"news-detail-taxonomy-label\">${htmlEncode(label)}</span>",
                postfix = "</div>"
        ) { tag ->
            val title = htmlEncode(tag.title).orEmpty()
            val href = safeCategoryHref(tag.url)
            if (href == null) {
                """<span class="news-detail-chip news-detail-chip-disabled">$title</span>"""
            } else {
                val escapedHref = htmlEncode(href)
                """<a class="news-detail-chip" href="$escapedHref" data-taxonomy-url="$escapedHref">$title</a>"""
            }
        }
    }

    private fun chipKey(tag: Tag): String =
            tag.url.orEmpty().lowercase()
                    .ifBlank { tag.tag.orEmpty().lowercase() }
                    .ifBlank { tag.title.orEmpty().lowercase() }

    private fun safeCategoryHref(url: String?): String? {
        val value = url?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val absolute = when {
            value.startsWith("https://4pda.to/", ignoreCase = true) -> value
            value.startsWith("http://4pda.to/", ignoreCase = true) ->
                "https://4pda.to/" + value.substringAfter("://").substringAfter("/")
            value.startsWith("//4pda.to/", ignoreCase = true) -> "https:$value"
            value.startsWith("/") -> "https://4pda.to$value"
            else -> return null
        }
        val path = absolute.substringAfter("4pda.to", "")
        if (!path.contains("/category/", ignoreCase = true) &&
                !path.matches(Regex("""(?i)/[a-z0-9_-]+/?$"""))) {
            return null
        }
        return absolute
    }

    private fun stabilizeMediaLayout(html: String?): String? {
        if (html.isNullOrEmpty()) return html

        return mediaTagRegex.replace(html) { match ->
            val tag = match.value
            val dimensions = readDimensions(tag) ?: defaultMediaDimensions
            val aspectRatio = dimensions.first.toFloat() / dimensions.second.toFloat()
            val stableStyle = buildStableMediaStyle(readAttribute(tag, "style").orEmpty(), aspectRatio)
            val preparedTag = upsertAttribute(tag, "style", stableStyle)
            upsertAttribute(preparedTag, "class", appendClass(readAttribute(preparedTag, "class"), STABLE_MEDIA_CLASS))
        }
    }

    private fun readDimensions(tag: String): Pair<Int, Int>? {
        val width = readPositiveIntAttribute(tag, "width")
        val height = readPositiveIntAttribute(tag, "height")
        if (width != null && height != null) return width to height

        val style = readAttribute(tag, "style").orEmpty()
        var styleWidth: Int? = null
        var styleHeight: Int? = null
        cssPxValueRegex.findAll(style).forEach { match ->
            val value = match.groupValues[2].toIntOrNull()?.takeIf { it > 0 }
            when {
                match.groupValues[1].equals("width", ignoreCase = true) -> styleWidth = value
                match.groupValues[1].equals("height", ignoreCase = true) -> styleHeight = value
            }
        }
        if (styleWidth != null && styleHeight != null) return styleWidth!! to styleHeight!!

        return null
    }

    private fun readPositiveIntAttribute(tag: String, name: String): Int? {
        return readAttribute(tag, name)
            ?.substringBefore('.')
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
    }

    private fun buildStableMediaStyle(style: String, aspectRatio: Float): String {
        val withoutHeight = style
            .split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.substringBefore(':').trim().equals("height", ignoreCase = true) }
            .filterNot { it.substringBefore(':').trim().equals("width", ignoreCase = true) }
            .joinToString("; ")
        val prefix = withoutHeight.takeIf { it.isNotBlank() }?.let { "$it; " }.orEmpty()
        return "${prefix}aspect-ratio: ${formatAspectRatio(aspectRatio)}; width: 100%; height: auto;"
    }

    private fun formatAspectRatio(aspectRatio: Float): String {
        return "%.4f".format(java.util.Locale.US, aspectRatio.coerceIn(0.2f, 5.0f))
    }

    private fun readAttribute(tag: String, name: String): String? {
        val pattern = Regex("""\b${Regex.escape(name)}\s*=\s*(["'])(.*?)\1""", RegexOption.IGNORE_CASE)
        return pattern.find(tag)?.groupValues?.get(2)
    }

    private fun upsertAttribute(tag: String, name: String, value: String): String {
        val escaped = htmlEncode(value).orEmpty()
        val pattern = Regex("""\b${Regex.escape(name)}\s*=\s*(["'])(.*?)\1""", RegexOption.IGNORE_CASE)
        if (pattern.containsMatchIn(tag)) {
            return pattern.replaceFirst(tag, "$name=\"$escaped\"")
        }

        val insertIndex = tag.indexOf('>').takeIf { it >= 0 } ?: return tag
        val selfClosingOffset = if (insertIndex > 0 && tag[insertIndex - 1] == '/') 1 else 0
        val position = insertIndex - selfClosingOffset
        return tag.substring(0, position) + " $name=\"$escaped\"" + tag.substring(position)
    }

    private fun appendClass(classes: String?, newClass: String): String {
        val parts = classes.orEmpty().split(Regex("\\s+")).filter { it.isNotBlank() }
        return if (newClass in parts) parts.joinToString(" ") else (parts + newClass).joinToString(" ")
    }

    private fun htmlEncode(value: String?): String? =
            value?.let {
                buildString(it.length) {
                    it.forEach { char ->
                        when (char) {
                            '&' -> append("&amp;")
                            '<' -> append("&lt;")
                            '>' -> append("&gt;")
                            '"' -> append("&quot;")
                            '\'' -> append("&#39;")
                            else -> append(char)
                        }
                    }
                }
            }

    /**
     * Remove duplicate title from HTML content.
     * The title is already shown in the UI toolbar, so we remove it from WebView content.
     */
    // First `<figure class="…article-figure-big…">…</figure>` — 4pda's lead image, duplicated by the
    // native header card. Figures do not nest, so the non-greedy `</figure>` closes the right one.
    private val leadingHeroFigureRegex = Regex(
            """(?is)<figure\b(?=[^>]*\bclass\s*=\s*["'][^"']*\barticle-figure-big\b)[^>]*>[\s\S]*?</figure>"""
    )

    /** Strips the body's leading hero figure (the one mirrored in the header card). */
    private fun removeLeadingHeroFigure(html: String?): String? {
        if (html.isNullOrBlank()) return html
        return leadingHeroFigureRegex.replaceFirst(html, "")
    }

    private fun removeTitleFromHtml(html: String?, title: String?): String? {
        if (html.isNullOrEmpty() || title.isNullOrEmpty()) return html

        var result = html

        // Try to remove h1 with exact title text
        val titleRegex1 = Regex("<h1[^>]*>\\s*${Regex.escape(title)}\\s*</h1>", RegexOption.IGNORE_CASE)
        result = result.replace(titleRegex1, "")

        // Try to remove h2 with exact title text
        val titleRegex2 = Regex("<h2[^>]*>\\s*${Regex.escape(title)}\\s*</h2>", RegexOption.IGNORE_CASE)
        result = result.replace(titleRegex2, "")

        // Try to remove div with class containing "title" and title text
        val titleRegex3 = Regex("<div[^>]*class=[\"'][^\"']*title[^\"']*[\"'][^>]*>\\s*${Regex.escape(title)}\\s*</div>", RegexOption.IGNORE_CASE)
        result = result.replace(titleRegex3, "")

        // Try to remove any element with news-title class
        val titleRegex4 = Regex("<[^>]+class=[\"'][^\"']*news-title[^\"']*[\"'][^>]*>.*?</[^>]+>", RegexOption.IGNORE_CASE)
        result = result.replace(titleRegex4, "")

        return result
    }

    /**
     * Some sources include the site comments block (e.g. <div id="comments"> or <ul class="comment-list">)
     * inside the article HTML. We render our own inline comments footer separately,
     * so we must strip embedded comments from the content to avoid duplicated sections
     * and broken layout when the user toggles inline comments.
     */
    private fun removeEmbeddedCommentsFromHtml(html: String?): String? {
        if (html.isNullOrBlank()) return html
        val source = html

        // Most common: a "comments" container near the end.
        val commentsContainerIndex = indexOfFirstMatch(
                source,
                Regex("""(?i)<div\b[^>]*\bid\s*=\s*["']comments["'][^>]*>""")
        )
        if (commentsContainerIndex >= 0) {
            return source.substring(0, commentsContainerIndex).trimEnd()
        }

        // Fallback: standalone comment list near the end.
        val commentsListIndex = indexOfFirstMatch(
                source,
                Regex("""(?i)<ul\b[^>]*\bclass\s*=\s*["'][^"']*(comment-list|comments-list)[^"']*["'][^>]*>""")
        )
        if (commentsListIndex >= 0) {
            return source.substring(0, commentsListIndex).trimEnd()
        }

        // Some pages expose a comment form anchor or footer wrappers even without a list.
        val commentFormIndex = indexOfFirstMatch(
                source,
                Regex("""(?i)\bid\s*=\s*["']commentform["']""")
        )
        if (commentFormIndex >= 0) {
            val tagStart = source.lastIndexOf('<', commentFormIndex).takeIf { it >= 0 } ?: commentFormIndex
            return source.substring(0, tagStart).trimEnd()
        }

        return source
    }

    private fun indexOfFirstMatch(source: String, pattern: Regex): Int {
        val match = pattern.find(source) ?: return -1
        return match.range.first
    }

    private companion object {
        private const val DEFAULT_RETRY_LABEL = "Повторить"
        private const val DEFAULT_COMMENTS_LABEL = "Комментарии"
        private const val DEFAULT_SHOW_ACTION_LABEL = "Показать"
        private const val DEFAULT_HIDE_ACTION_LABEL = "Скрыть"
        private const val DEFAULT_COMMENTS_DESCRIPTION = "Комментарии откроются прямо под статьей"
        /** Prefer news.js handler; inline fallback expands DOM and calls INews before scripts load. */
        private const val COMMENTS_TOGGLE_ONCLICK =
                "try{var btn=event&&event.currentTarget?event.currentTarget:this;" +
                        "var ev=event||window.event;" +
                        "if(typeof newsInlineCommentsHandleToggleFromNativeButton==='function'){" +
                        "return newsInlineCommentsHandleToggleFromNativeButton(btn,ev);}" +
                        "var s=btn&&btn.closest?btn.closest('#news-comments-section'):null;" +
                        "if(!s)return false;" +
                        "var c=(s.getAttribute('data-collapsed')||'true')==='true';" +
                        "var b=s.querySelector('#news-comments-body');" +
                        "var act=btn.querySelector('.news-comments-toggle-action');" +
                        "var show=s.getAttribute('data-label-show')||'Показать';" +
                        "var hide=s.getAttribute('data-label-hide')||'Скрыть';" +
                        "if(c){s.setAttribute('data-collapsed','false');if(b)b.hidden=false;" +
                        "btn.setAttribute('aria-expanded','true');if(act)act.textContent=hide;" +
                        "try{if(window.INews&&INews.onCommentsSectionTapReceived)" +
                        "INews.onCommentsSectionTapReceived('toggle_expand');}catch(e){}" +
                        "}else{s.setAttribute('data-collapsed','true');if(b)b.hidden=true;" +
                        "btn.setAttribute('aria-expanded','false');if(act)act.textContent=show;" +
                        "try{if(window.INews&&INews.onInlineCommentsSectionToggled)" +
                        "INews.onInlineCommentsSectionToggled(true);}catch(e){}}return false;}catch(e){return false;}"
        private const val COMMENTS_RETRY_ONCLICK =
                "try{if(typeof newsInlineCommentsHandleRetryFromNativeButton==='function'){" +
                        "return newsInlineCommentsHandleRetryFromNativeButton(this);}" +
                        "var s=this.closest?this.closest('#news-comments-section'):null;" +
                        "if(!s)return false;s.setAttribute('data-collapsed','false');" +
                        "var b=s.querySelector('#news-comments-body');if(b)b.hidden=false;" +
                        "try{if(window.INews&&INews.onLoadInlineCommentsRequested)" +
                        "INews.onLoadInlineCommentsRequested();else if(window.INews&&INews.onCommentsSectionTapReceived)" +
                        "INews.onCommentsSectionTapReceived('retry');}catch(e){}return false;}catch(e){return false;}"
        private const val STABLE_MEDIA_CLASS = "app-stable-media"
        private val defaultMediaDimensions = 16 to 9
        private val mediaTagRegex = Regex("<(?:img|iframe)\\b[^>]*>", RegexOption.IGNORE_CASE)
        private val cssPxValueRegex = Regex("""(?:^|;)\s*(width|height)\s*:\s*(\d+)px\b""", RegexOption.IGNORE_CASE)
        private val documentMarkerRegex = Regex("""(?i)<\s*(html|body)\b""")
        private val newsTemplateMarkerRegex = Regex("""(?i)<body\b[^>]*\bid\s*=\s*["']news["']""")
        private val mappedStyleTypeRegex = Regex("""(?i)/styles/(light|dark)/(?:light|dark)_""")
        private val FOOTER_COMMENTS_COUNT_ATTR_REGEX =
                Regex("""(id="news-comments-section"[^>]*data-comments-count=")\d+(")""")
        private val FOOTER_TOGGLE_TITLE_COUNT_REGEX =
                Regex("""(class="news-comments-toggle-title">[^<]*?)\s*\(\d+\)(</span>)""")
        private val HEADER_META_MIDDLE_COUNT_REGEX =
                Regex("""(class="news-detail-header-meta">[^<]*?)(\s·\s)\d+(\s·\s)""")
    }

}