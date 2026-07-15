function transformImages() {
    var lightBox = document.querySelectorAll("p > a[data-lightbox] > img, p > img, p > iframe");
    for (var i = 0; i < lightBox.length; i++) {
        var p = lightBox[i];
        while (p && p.tagName != "P") {
            p = p.parentElement;
        }
        if (p) {
            p.classList.add("full_width");
        }
    }
}

function transformPoll() {
    var polls = document.querySelectorAll('div[id*="poll-ajax-frame"] form, .news-poll form, form[action*="/pages/poll/"], form[action*="pages/poll"], form[action*="poll"], form[action*="vote"]');
    for (var i = 0; i < polls.length; i++) {
        var poll = polls[i];
        var submitButton = poll.querySelector("button[type=submit], input[type=submit], button:not([type]), .vote, .btn");
        if (!submitButton || submitButton.dataset.newsPollBound === "true") {
            continue;
        }
        submitButton.dataset.newsPollBound = "true";
        submitButton.setAttribute("type", "button");
        submitButton.addEventListener("click", function (ev) {
            var form = ev.target;
            while (form != null && form != undefined && form.nodeName != "FORM") {
                form = form.parentElement;
            }
            if (!form) {
                return;
            }
            var pollIdMatch = /poll_id=(\d+)/g.exec(form.action || "");
            if (!pollIdMatch) {
                var pollIdInput = form.querySelector('input[name="poll_id"], input[name="poll"]');
                if (pollIdInput && pollIdInput.value) {
                    pollIdMatch = ["", pollIdInput.value];
                }
            }
            var formAnswers = form.querySelectorAll('input[name="answer[]"], input[name="answer"], input[name^="answer["]');
            if (!pollIdMatch || !formAnswers.length) {
                return;
            }
            var id = pollIdMatch[1]
            var from = form.elements["from"] ? form.elements["from"].value : "";
            var token = newsPollToken(form);
            var answers = []
            for (var j = 0; j < formAnswers.length; j++) {
                var input = formAnswers[j];
                if (input.checked) {
                    answers.push(input.value);
                }
            }
            var answer = answers.join(",");
            if (!token) {
                newsPollSetError(form, "Не удалось подтвердить опрос");
                return;
            }
            if (answer.length == 0) {
                newsPollSetError(form, "Выберите вариант ответа");
                return;
            }
            newsPollSetLoading(form, true);
            INews.sendPoll(id, answer, from, token);
        });
    }
}

function newsPollContainer(form) {
    var node = form;
    while (node && node.nodeType == 1) {
        if (node.className && ((" " + node.className + " ").indexOf(" news-poll-normalized ") >= 0 || (" " + node.className + " ").indexOf(" news-poll ") >= 0)) {
            return node;
        }
        node = node.parentElement;
    }
    return form;
}

function newsPollToken(form) {
    var container = newsPollContainer(form);
    return container.getAttribute("data-news-poll-token") || "";
}

function newsPollStatus(form) {
    var container = newsPollContainer(form);
    var status = container.querySelector(".news-poll-status");
    if (!status) {
        status = document.createElement("p");
        status.className = "poll_status news-poll-status";
        form.appendChild(status);
    }
    return status;
}

function newsPollSetError(form, message) {
    var status = newsPollStatus(form);
    status.className = "poll_status news-poll-status news-poll-error";
    status.innerHTML = message || "Не удалось отправить голос";
}

function newsPollSetLoading(form, loading) {
    var elements = form.querySelectorAll("input, button");
    for (var i = 0; i < elements.length; i++) {
        var element = elements[i];
        if (loading) {
            element.dataset.newsPollDisabledByVote = element.disabled ? "false" : "true";
            element.disabled = true;
        } else if (element.dataset.newsPollDisabledByVote == "true") {
            element.disabled = false;
        }
    }
    var status = newsPollStatus(form);
    status.className = "poll_status news-poll-status";
    status.innerHTML = loading ? "Отправляем голос..." : "";
}

function onNewsPollVoteSuccess(pollId, html) {
    var form = newsPollFindForm(pollId);
    if (!form) {
        return;
    }
    var container = newsPollContainer(form);
    var wrapper = document.createElement("div");
    wrapper.innerHTML = html;
    var replacement = wrapper.querySelector(".news-poll-normalized, .news-poll, div[id*='poll-ajax-frame']") || wrapper.firstElementChild;
    if (replacement) {
        container.parentNode.replaceChild(replacement, container);
        transformPoll();
        bindPollExternalBrowserButtons();
    } else {
        newsPollSetLoading(form, false);
    }
}

function onNewsPollVoteError(pollId, message) {
    var form = newsPollFindForm(pollId);
    if (!form) {
        return;
    }
    newsPollSetLoading(form, false);
    newsPollSetError(form, message);
}

function newsPollFindForm(pollId) {
    var forms = document.querySelectorAll('.news-poll-normalized form, .news-poll form, form[action*="/pages/poll/"]');
    for (var i = 0; i < forms.length; i++) {
        var form = forms[i];
        var pollIdMatch = /poll_id=(\d+)/g.exec(form.action || "");
        var id = pollIdMatch ? pollIdMatch[1] : "";
        if (!id) {
            var pollIdInput = form.querySelector('input[name="poll_id"], input[name="poll"]');
            id = pollIdInput && pollIdInput.value ? pollIdInput.value : "";
        }
        if (id == pollId) {
            return form;
        }
    }
    return null;
}

function resolveArticleHref(href) {
    if (!href) {
        return null;
    }
    var trimmed = href.trim();
    if (!trimmed || trimmed === "#" || trimmed.charAt(0) === "#") {
        return null;
    }
    if (/^javascript:/i.test(trimmed)) {
        return null;
    }
    try {
        return new URL(trimmed, "https://4pda.to/").href;
    } catch (e) {
        return null;
    }
}

function normalizeMisplacedForumArticleHref(resolved) {
    if (!resolved) {
        return null;
    }
    var link;
    try {
        link = new URL(resolved);
    } catch (e) {
        return resolved;
    }
    var host = link.hostname.toLowerCase();
    if (host !== "4pda.to" && host !== "www.4pda.to") {
        return resolved;
    }
    var path = link.pathname || "";
    if (path.indexOf("/forum/pages/") === 0) {
        link.pathname = path.replace("/forum/pages/", "/pages/");
    } else if (path.indexOf("/forum/stat/") === 0) {
        link.pathname = path.replace("/forum/stat/", "/stat/");
    } else if (path.indexOf("/forum/software/") === 0) {
        link.pathname = path.replace("/forum/software/", "/software/");
    } else if (path.toLowerCase() === "/forum/index.php") {
        var params = link.searchParams;
        var hasPostId = params.has("p");
        var isForumNavigation = params.has("showtopic") ||
            params.has("showuser") ||
            params.has("showforum") ||
            params.has("act");
        if (hasPostId && !isForumNavigation) {
            link.pathname = "/index.php";
        }
    }
    return link.href;
}

function isExternalArticleHref(resolved) {
    if (!resolved) {
        return false;
    }
    try {
        var host = new URL(resolved).hostname.toLowerCase();
        return host !== "4pda.to" && host !== "www.4pda.to";
    } catch (e) {
        return false;
    }
}

function extractYoutubeVideoIdFromUrl(url) {
    if (!url) {
        return "";
    }
    var link;
    try {
        link = new URL(url);
    } catch (e) {
        return "";
    }
    var host = link.hostname.toLowerCase();
    var id = "";
    if (host === "youtu.be" || host.slice(-8) === ".youtu.be") {
        id = (link.pathname || "").replace(/^\//, "").split("/")[0] || "";
    } else if (host === "youtube.com" || host === "www.youtube.com" || host === "m.youtube.com") {
        var path = link.pathname || "";
        if (path === "/watch") {
            id = link.searchParams.get("v") || "";
        } else if (path.indexOf("/embed/") === 0 ||
            path.indexOf("/shorts/") === 0 ||
            path.indexOf("/live/") === 0 ||
            path.indexOf("/v/") === 0) {
            id = path.split("/")[2] || "";
        }
    } else if (host === "youtube-nocookie.com" || host === "www.youtube-nocookie.com") {
        if ((link.pathname || "").indexOf("/embed/") === 0) {
            id = (link.pathname || "").split("/")[2] || "";
        }
    }
    return /^[A-Za-z0-9_-]{11}$/.test(id) ? id : "";
}

function openResolvedArticleHref(resolved) {
    if (!resolved || typeof INews === "undefined") {
        return;
    }
    if (isExternalArticleHref(resolved)) {
        var youtubeId = extractYoutubeVideoIdFromUrl(resolved);
        if (youtubeId && INews.playVideoInArticle) {
            INews.playVideoInArticle(youtubeId);
            return;
        }
        if (INews.openExternalBrowser) {
            INews.openExternalBrowser(resolved);
        }
        return;
    }
    if (INews.openArticleLink) {
        INews.openArticleLink(resolved);
    } else if (INews.openExternalBrowser) {
        INews.openExternalBrowser(resolved);
    }
}

function bindArticleContentLinks() {
    var root = document.querySelector("#news .content") || document.querySelector("#news");
    if (!root) {
        return;
    }
    var links = root.querySelectorAll("a[href]");
    for (var i = 0; i < links.length; i++) {
        var link = links[i];
        if (link.dataset.newsContentLinkBound === "true") {
            continue;
        }
        link.dataset.newsContentLinkBound = "true";
        link.addEventListener("click", function (ev) {
            if (this.closest(".news-video-card")) {
                return;
            }
            var rawHref = this.getAttribute("href");
            var resolved = normalizeMisplacedForumArticleHref(resolveArticleHref(rawHref));
            if (!resolved) {
                return;
            }
            ev.preventDefault();
            ev.stopPropagation();
            openResolvedArticleHref(resolved);
        }, true);
    }
}

function bindArticleTargetBlankLinks() {
    bindArticleContentLinks();
}

function bindPollExternalBrowserButtons() {
    var buttons = document.querySelectorAll('.news-poll-normalized button[data-open-external-browser="true"]');
    for (var i = 0; i < buttons.length; i++) {
        var button = buttons[i];
        if (button.dataset.newsPollExternalBound === "true") {
            continue;
        }
        button.dataset.newsPollExternalBound = "true";
        button.addEventListener("click", function (ev) {
            ev.preventDefault();
            ev.stopPropagation();
            var href = this.getAttribute("data-href");
            if (href) {
                INews.openExternalBrowser(href);
            }
        });
    }
}

function bindNewsPolls() {
    transformPoll();
    bindPollExternalBrowserButtons();
}

function newsVideoCardsPruneDuplicates() {
    var seen = {};
    var cards = document.querySelectorAll(".news-video-card[data-video-id]");
    for (var i = 0; i < cards.length; i++) {
        var card = cards[i];
        var id = card.getAttribute("data-video-id") || "";
        if (!id) {
            continue;
        }
        if (seen[id]) {
            if (card.parentNode) {
                card.parentNode.removeChild(card);
            }
        } else {
            seen[id] = true;
        }
    }
}

function bindVideoCards() {
    newsVideoCardsPruneDuplicates();
    var previews = document.querySelectorAll(
        '.news-video-card [data-video-play="true"], .news-video-card-preview[data-video-play="true"]'
    );
    for (var i = 0; i < previews.length; i++) {
        var preview = previews[i];
        if (preview.dataset.newsVideoPlayBound === "true") {
            continue;
        }
        preview.dataset.newsVideoPlayBound = "true";
        preview.addEventListener("click", function (ev) {
            ev.preventDefault();
            ev.stopPropagation();
            var card = newsVideoCard(this);
            if (!card) {
                return;
            }
            if (typeof renderYoutubePlayer === "function") {
                renderYoutubePlayer(card);
                return;
            }
            var videoId = card.getAttribute("data-video-id") || "";
            if (videoId && typeof INews !== "undefined" && INews.playVideoInArticle) {
                INews.playVideoInArticle(videoId);
            }
        });
    }

    var links = document.querySelectorAll('.news-video-card .news-video-card-youtube[href]');
    for (var j = 0; j < links.length; j++) {
        var link = links[j];
        if (link.dataset.newsVideoOpenBound === "true") {
            continue;
        }
        link.dataset.newsVideoOpenBound = "true";
        link.addEventListener("click", function (ev) {
            ev.preventDefault();
            ev.stopPropagation();
            var href = this.getAttribute("href");
            if (href) {
                INews.openVideo(href);
            }
        });
    }
}

function newsVideoCard(element) {
    var node = element;
    while (node && node.nodeType == 1) {
        if (node.className && (" " + node.className + " ").indexOf(" news-video-card ") >= 0) {
            return node;
        }
        node = node.parentElement;
    }
    return null;
}

function renderYoutubePlayer(card) {
    var videoId = card.getAttribute("data-video-id") || "";
    if (!/^[A-Za-z0-9_-]{11}$/.test(videoId)) {
        return;
    }
    var embedUrl = card.getAttribute("data-video-embed-url") ||
        ("https://www.youtube-nocookie.com/embed/" + videoId + "?autoplay=1&rel=0");
    var frame = document.createElement("iframe");
    frame.className = "news-video-card-frame app-stable-media";
    frame.setAttribute("src", embedUrl);
    frame.setAttribute("title", "YouTube video player");
    frame.setAttribute("allow", "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share");
    frame.setAttribute("allowfullscreen", "allowfullscreen");
    frame.setAttribute("referrerpolicy", "strict-origin-when-cross-origin");
    frame.setAttribute("loading", "lazy");

    var preview = card.querySelector('[data-video-play="true"]');
    if (preview && preview.parentNode) {
        preview.parentNode.replaceChild(frame, preview);
    } else {
        card.insertBefore(frame, card.firstChild);
    }
}

function isSafeTaxonomyUrl(url) {
    if (!url) {
        return false;
    }
    var link;
    try {
        link = new URL(url, "https://4pda.to/");
    } catch (e) {
        return false;
    }
    var host = link.hostname.toLowerCase();
    if (host != "4pda.to" && host != "www.4pda.to") {
        return false;
    }
    var path = link.pathname.toLowerCase();
    return path.indexOf("/category/") >= 0 ||
            /^\/[a-z0-9_-]+\/?$/.test(path);
}

function bindTaxonomyChips() {
    var chips = document.querySelectorAll('.news-detail-taxonomy a.news-detail-chip[data-taxonomy-url]');
    for (var i = 0; i < chips.length; i++) {
        var chip = chips[i];
        if (chip.dataset.newsTaxonomyBound === "true") {
            continue;
        }
        chip.dataset.newsTaxonomyBound = "true";
        chip.addEventListener("click", function (ev) {
            var url = this.getAttribute("data-taxonomy-url") || this.getAttribute("href");
            if (!isSafeTaxonomyUrl(url)) {
                return;
            }
            ev.preventDefault();
            ev.stopPropagation();
            INews.openTaxonomy(url);
        });
    }
}

var newsCommentsSectionDocumentClickHandler = null;

function newsInlineCommentsRoot() {
    newsInlineCommentsPruneDuplicates();
    var sections = document.querySelectorAll("#news-comments-section");
    if (!sections || sections.length === 0) {
        return null;
    }
    // Keep the first section (template footer after materials); late JS mounts append duplicates at body end.
    return sections[0];
}

function newsInlineCommentsShouldIgnoreDuplicateToggle(ev, root) {
    var now = Date.now();
    if (root) {
        var lastAt = root.fpdaLastToggleAt || 0;
        if (now - lastAt < 350) {
            fpdaCommentsSectionLog("toggle_ignored_duplicate", { deltaMs: now - lastAt });
            return true;
        }
        root.fpdaLastToggleAt = now;
    }
    if (ev && ev.fpdaCommentsToggleHandled) {
        fpdaCommentsSectionLog("toggle_ignored_duplicate", { source: "event_flag" });
        return true;
    }
    if (ev) {
        ev.fpdaCommentsToggleHandled = true;
    }
    return false;
}

function newsInlineCommentsHandleToggleClick(root, ev) {
    if (!root) {
        return;
    }
    if (newsInlineCommentsShouldIgnoreDuplicateToggle(ev, root)) {
        return;
    }
    fpdaCommentsSectionLog("toggle_click", { source: "handler" });
    newsInlineCommentsLogDomStats();
    var current = (root.getAttribute("data-collapsed") || "true") === "true";
    var nextCollapsed = !current;
    // Expand: Kotlin is notified only via onCommentsSectionTapReceived (requestExpand).
    // Collapse: sync native coordinator via onInlineCommentsSectionToggled.
    // notifyNative on expand caused duplicate expand paths and debounced tap drops.
    if (nextCollapsed) {
        newsInlineCommentsSetCollapsed(true, true, root);
    } else {
        newsInlineCommentsSetCollapsed(false, false, root);
        newsInlineCommentsRequestExpand(root);
    }
    newsInlineCommentsLogDomStats();
}

function newsInlineCommentsRequestExpand(root) {
    if (!root) {
        fpdaCommentsSectionLog("expand_failed_reason", { reason: "no_root" });
        return;
    }
    fpdaCommentsSectionLog("expand_clicked", { source: "request_expand" });
    var domState = root.getAttribute("data-state") || "not-loaded";
    var list = root.querySelector("#news-inline-comments-list");
    var hasRenderedList = list && list.children && list.children.length > 0;
    var needsLoad = domState === "not-loaded" ||
        domState === "empty" ||
        domState === "error" ||
        (domState === "loaded" && !hasRenderedList);
    var bridgeUsed = false;
    try {
        if (typeof INews !== "undefined" && typeof INews.onCommentsSectionTapReceived === "function") {
            INews.onCommentsSectionTapReceived("toggle_expand");
            fpdaCommentsSectionLog("bridge_called", { method: "onCommentsSectionTapReceived", source: "toggle_expand", domState: domState });
            bridgeUsed = true;
            if (needsLoad || domState === "loading") {
                newsInlineCommentsSetState("loading", "Загружаем комментарии...", null);
            }
        }
    } catch (e) {
        fpdaCommentsSectionLog("expand_bridge_error", { message: String(e && e.message ? e.message : e) });
    }
    if (!bridgeUsed) {
        if (needsLoad) {
            newsInlineCommentsLoad(root);
        } else if (domState === "loading") {
            fpdaCommentsSectionLog("load_skipped_reason", { reason: "already_loading", source: "expand_no_bridge" });
        }
        return;
    }
    // Kotlin tap already owns loading. A second bridge call in the same click is debounced
    // by CommentsExpandCoordinator and can leave the footer expanded but idle.
}

function newsInlineCommentsHandleRetryClick(root) {
    if (!root) {
        return;
    }
    fpdaCommentsSectionLog("retry_click", { source: "handler" });
    newsInlineCommentsSetCollapsed(false, true, root);
    newsInlineCommentsLoad(root);
}

function newsInlineCommentsHandleToggleFromNativeButton(buttonEl, ev) {
    var eventObj = ev || (typeof window !== "undefined" ? window.event : null);
    var root = buttonEl && typeof buttonEl.closest === "function"
        ? buttonEl.closest("#news-comments-section")
        : newsInlineCommentsRoot();
    if (!root) {
        fpdaCommentsSectionLog("toggle_direct_no_root");
        return false;
    }
    if (eventObj) {
        eventObj.preventDefault();
        eventObj.stopPropagation();
    }
    fpdaCommentsSectionLog("toggle_click", { source: "onclick" });
    newsInlineCommentsHandleToggleClick(root, eventObj);
    return false;
}

function newsInlineCommentsHandleRetryFromNativeButton(buttonEl) {
    var root = buttonEl && typeof buttonEl.closest === "function"
        ? buttonEl.closest("#news-comments-section")
        : newsInlineCommentsRoot();
    if (!root) {
        fpdaCommentsSectionLog("retry_direct_no_root");
        return false;
    }
    fpdaCommentsSectionLog("retry_click", { source: "onclick" });
    newsInlineCommentsHandleRetryClick(root);
    return false;
}

function ensureNewsCommentsSectionClickDelegation() {
    var doc = document;
    if (!doc) {
        return;
    }
    // Per-document marker: WebView keeps global JS vars across loadDataWithBaseURL,
    // but document is replaced — a global "installed" flag breaks taps on next article.
    if (doc.documentElement && doc.documentElement.getAttribute("data-fpda-comments-delegation") === "1") {
        return;
    }
    if (doc.documentElement) {
        doc.documentElement.setAttribute("data-fpda-comments-delegation", "1");
    }
    var handler = doc.fpdaCommentsSectionDocumentClickHandler;
    if (!handler) {
        handler = function (ev) {
            var target = ev.target;
            if (!target || typeof target.closest !== "function") {
                return;
            }
            var root = target.closest("#news-comments-section");
            if (!root) {
                return;
            }
            var toggle = target.closest("#news-comments-toggle");
            if (toggle && root.contains(toggle)) {
                ev.preventDefault();
                ev.stopPropagation();
                fpdaCommentsSectionLog("toggle_click", { source: "delegation" });
                newsInlineCommentsHandleToggleClick(root, ev);
                return;
            }
            var retry = target.closest("#news-inline-comments-retry");
            if (retry && root.contains(retry)) {
                ev.preventDefault();
                ev.stopPropagation();
                fpdaCommentsSectionLog("retry_click", { source: "delegation" });
                newsInlineCommentsHandleRetryClick(root);
                return;
            }
            var more = target.closest("#news-inline-comments-more");
            if (more && root.contains(more)) {
                ev.preventDefault();
                ev.stopPropagation();
                fpdaCommentsSectionLog("load_more_click", { source: "delegation" });
                newsInlineCommentsRequestLoadMore(root, "button");
            }
        };
        doc.fpdaCommentsSectionDocumentClickHandler = handler;
    }
    newsCommentsSectionDocumentClickHandler = handler;
    doc.addEventListener("click", handler, true);
    fpdaCommentsSectionLog("delegation_installed");
}

function fpdaCommentsSectionLog(event, extra) {
    try {
        var payload = { event: event || "" };
        if (extra && typeof extra === "object") {
            for (var k in extra) {
                if (Object.prototype.hasOwnProperty.call(extra, k)) {
                    payload[k] = extra[k];
                }
            }
        }
        if (typeof INews !== "undefined" && typeof INews.onCommentsSectionJsEvent === "function") {
            INews.onCommentsSectionJsEvent(JSON.stringify(payload));
        }
    } catch (e) {
    }
}

function newsInlineCommentsSetCollapsed(collapsed, notifyNative, rootNode) {
    var root = rootNode || newsInlineCommentsRoot();
    if (!root) {
        fpdaCommentsSectionLog("collapsed_no_root");
        return;
    }
    var toggle = root.querySelector("#news-comments-toggle");
    var body = root.querySelector("#news-comments-body");
    var isCollapsed = !!collapsed;
    root.setAttribute("data-collapsed", isCollapsed ? "true" : "false");
    try {
        var action = toggle ? toggle.querySelector(".news-comments-toggle-action") : null;
        if (action) {
            var showLabel = root.getAttribute("data-label-show") || "Показать";
            var hideLabel = root.getAttribute("data-label-hide") || "Скрыть";
            action.textContent = isCollapsed ? showLabel : hideLabel;
        }
    } catch (e) {
    }
    if (toggle) {
        toggle.setAttribute("aria-expanded", isCollapsed ? "false" : "true");
    }
    if (body) {
        body.hidden = isCollapsed;
    }
    var list = root.querySelector("#news-inline-comments-list");
    if (list && isCollapsed) {
        list.style.display = "none";
    } else if (list) {
        list.style.display = "";
    }
    if (!!notifyNative) {
        try {
            if (typeof INews !== "undefined" && typeof INews.onInlineCommentsSectionToggled === "function") {
                INews.onInlineCommentsSectionToggled(isCollapsed);
            }
        } catch (e) {
        }
    }
    fpdaCommentsSectionLog("collapsed_set", { collapsed: isCollapsed, notifyNative: !!notifyNative });
}

function newsInlineCommentsPruneDuplicates() {
    var roots = document.querySelectorAll("#news-comments-section");
    if (!roots || roots.length <= 1) {
        return;
    }
    // Keep the first one (from article template); remove late duplicate mounts at body end.
    var canonical = roots[0];
    for (var i = 1; i < roots.length; i++) {
        var node = roots[i];
        if (!node || node === canonical) {
            continue;
        }
        if (node.parentNode) {
            node.parentNode.removeChild(node);
        }
    }
}

function newsInlineCommentsUpdateCount(root, commentsCount) {
    if (!root) {
        return;
    }
    var count = commentsCount || 0;
    root.setAttribute("data-comments-count", String(count));
    var toggle = root.querySelector("#news-comments-toggle");
    var title = toggle ? toggle.querySelector(".news-comments-toggle-title") : null;
    if (!title) {
        return;
    }
    var base = (title.textContent || "").replace(/\s*(\(\d+\)|\(\?\))\s*$/, "").trim();
    if (!base) {
        base = "Комментарии";
    }
    var suffix = count > 0 ? (" (" + count + ")") : (count < 0 ? " (?)" : "");
    title.textContent = base + suffix;
}

function newsInlineCommentsUpdateHeaderCount(commentsCount) {
    var meta = document.querySelector(".news-detail-header-meta");
    if (!meta) {
        return;
    }
    var count = commentsCount || 0;
    if (count <= 0) {
        return;
    }
    var parts = (meta.textContent || "").split("·").map(function (p) { return p.trim(); }).filter(Boolean);
    if (parts.length < 2) {
        return;
    }
    if (parts.length === 2) {
        if (/^\d+$/.test(parts[0])) {
            parts[0] = String(count);
        } else {
            parts.splice(1, 0, String(count));
        }
    } else {
        parts[1] = String(count);
    }
    meta.textContent = parts.join(" · ");
}

/**
 * Idempotent footer mount: use template section when present, append only when missing.
 * Safe to call from native before news.js helpers are fully initialized.
 */
function newsInlineCommentsInsertFooterHtml(footerHtml) {
    if (!footerHtml) {
        return null;
    }
    var host = document.querySelector("#news") || document.body;
    if (!host) {
        return null;
    }
    var wrap = document.createElement("div");
    wrap.innerHTML = footerHtml;
    var script = host.querySelector("script");
    var first = null;
    while (wrap.firstChild) {
        if (script) {
            host.insertBefore(wrap.firstChild, script);
        } else {
            host.appendChild(wrap.firstChild);
        }
        if (!first) {
            first = host.querySelector("#news-comments-section");
        }
    }
    return first;
}

function newsInlineCommentsEnsureFooter(commentsCount, footerHtml, collapsed) {
    newsInlineCommentsPruneDuplicates();
    var sections = document.querySelectorAll("#news-comments-section");
    var root = sections.length ? sections[0] : null;
    if (!root && footerHtml) {
        root = newsInlineCommentsInsertFooterHtml(footerHtml);
        newsInlineCommentsPruneDuplicates();
        sections = document.querySelectorAll("#news-comments-section");
        root = sections.length ? sections[0] : root;
    }
    if (!root) {
        return false;
    }
    newsInlineCommentsUpdateCount(root, commentsCount);
    if (typeof newsInlineCommentsUpdateHeaderCount === "function") {
        newsInlineCommentsUpdateHeaderCount(commentsCount);
    }
    newsInlineCommentsPruneDuplicates();
    var isCollapsed = collapsed !== false;
    if (typeof bindCommentsSection === "function") {
        bindCommentsSection(isCollapsed, root.getAttribute("data-state") || "not-loaded");
    } else if (typeof bindNewsInlineCommentsLoad === "function") {
        bindNewsInlineCommentsLoad();
    }
    return true;
}

function newsInlineCommentsRemoveLegacyBlocks() {
    // Some sources still contain the site comments block inside article HTML.
    // Our app renders inline comments separately, so remove legacy blocks to avoid duplicates.
    // IMPORTANT: scope to article content only.
    // When inline comments are expanded/inserted, global selectors can accidentally remove
    // nodes outside the article (including the inline comments UI itself), which breaks layout.
    var article = document.querySelector("#news > .content");
    if (!article) {
        return;
    }
    var legacy = article.querySelectorAll("#comments, .comment-box, ul.comment-list, ul.comments-list");
    for (var i = 0; i < legacy.length; i++) {
        var node = legacy[i];
        // Do not remove our own container.
        if (node && node.id === "news-comments-section") {
            continue;
        }
        if (node && node.parentNode) {
            node.parentNode.removeChild(node);
        }
    }
}

function newsInlineCommentsLogDomStats() {
    try {
        var sections = document.querySelectorAll("#news-comments-section");
        var legacy = document.querySelectorAll("#comments, .comment-box, ul.comment-list, ul.comments-list");
        var contents = document.querySelectorAll(".content");
        var article = document.querySelector("#news > .content");
        var articleChildren = article ? article.children.length : 0;
        var articleHtmlLen = article ? (article.innerHTML || "").length : 0;
        var inlineRoot = newsInlineCommentsRoot();
        var inlineBody = inlineRoot ? inlineRoot.querySelector("#news-comments-body") : null;
        if (typeof INews !== "undefined" && typeof INews.onInlineCommentsDomStats === "function") {
            INews.onInlineCommentsDomStats(JSON.stringify({
                sections: sections ? sections.length : 0,
                legacy: legacy ? legacy.length : 0,
                contents: contents ? contents.length : 0,
                articleChildren: articleChildren,
                articleHtmlLen: articleHtmlLen,
                inlineCollapsed: inlineRoot ? ((inlineRoot.getAttribute("data-collapsed") || "true") === "true") : true,
                inlineHidden: inlineBody ? !!inlineBody.hidden : null
            }));
        }
    } catch (e) {
    }
}

function newsInlineCommentsSetState(state, message, html, scrollToCommentId) {
    var root = newsInlineCommentsRoot();
    if (!root) {
        return;
    }
    if (root.newsInlineCommentsLoadTimer) {
        clearTimeout(root.newsInlineCommentsLoadTimer);
        root.newsInlineCommentsLoadTimer = null;
    }
    var retry = root.querySelector("#news-inline-comments-retry");
    var status = root.querySelector("#news-inline-comments-status");
    var list = root.querySelector("#news-inline-comments-list");
    var previousState = root.getAttribute("data-state") || "not-loaded";
    root.setAttribute("data-state", state || "not-loaded");
    root.setAttribute("aria-busy", state === "loading" ? "true" : "false");
    if (retry) {
        retry.style.display = state === "error" ? "block" : "none";
        retry.disabled = state === "loading";
    }
    if (status) {
        status.innerHTML = message || "";
    }
    var more = root.querySelector("#news-inline-comments-more");
    if (more && state === "loading") {
        var listCount = list && list.children ? list.children.length : 0;
        if (listCount <= 0) {
            more.hidden = true;
            more.style.display = "none";
        }
        root.dataset.newsCommentsLoadingMore = "false";
    }
    var collapsed = (root.getAttribute("data-collapsed") || "true") === "true";
    if (list && typeof html === "string") {
        if (collapsed) {
            list.style.display = "none";
            root.setAttribute("data-fpda-pending-html", "true");
        } else {
            root.removeAttribute("data-fpda-pending-html");
            newsInlineCommentsInjectHtml(html, root.getAttribute("data-fpda-webview-gen") || "0", scrollToCommentId || 0);
        }
    } else if (state === "loading" && list && (previousState === "loaded" || previousState === "empty")) {
        if (status) {
            status.innerHTML = message || "";
        }
        return;
    }
}

/**
 * Idempotent HTML inject into #news-inline-comments-list with WebView generation guard.
 * Returns: ok | no_root | collapsed | no_list | stale
 */
function newsInlineCommentsInjectHtml(html, generation, scrollToCommentId, canLoadMore, totalCount, renderedCount) {
    var root = newsInlineCommentsRoot();
    if (!root) {
        fpdaCommentsSectionLog("inject_failed", { reason: "no_root", generation: generation });
        return "no_root";
    }
    var collapsed = (root.getAttribute("data-collapsed") || "true") === "true";
    if (collapsed) {
        fpdaCommentsSectionLog("inject_skipped", { reason: "collapsed", generation: generation });
        return "collapsed";
    }
    var list = root.querySelector("#news-inline-comments-list");
    if (!list) {
        fpdaCommentsSectionLog("inject_failed", { reason: "no_list", generation: generation });
        return "no_list";
    }
    var expectedGen = root.getAttribute("data-fpda-webview-gen") || "0";
    if (generation > 0 && String(generation) !== String(expectedGen)) {
        root.setAttribute("data-fpda-webview-gen", String(generation));
        expectedGen = String(generation);
        fpdaCommentsSectionLog("inject_gen_resync", { generation: generation });
    }
    var currentSeq = parseInt(root.getAttribute("data-fpda-inject-seq") || "0", 10);
    var incomingSeq = parseInt(generation || 0, 10);
    if (incomingSeq > 0 && incomingSeq < currentSeq) {
        fpdaCommentsSectionLog("inject_skipped", { reason: "stale_seq", generation: generation, currentSeq: currentSeq });
        return "stale";
    }
    root.setAttribute("data-fpda-inject-seq", String(incomingSeq || 0));
    root.setAttribute("data-fpda-inject-gen", String(generation || 0));
    root.setAttribute("data-state", "loaded");
    root.setAttribute("aria-busy", "false");
    list.innerHTML = html || "";
    list.style.display = "";
    var status = root.querySelector("#news-inline-comments-status");
    if (status) {
        status.innerHTML = "";
    }
    var retry = root.querySelector("#news-inline-comments-retry");
    if (retry) {
        retry.style.display = "none";
        retry.disabled = false;
    }
    bindNewsInlineCommentActions();
    if (typeof jsEmoticons !== "undefined") {
        jsEmoticons.parseAll("file:///android_asset/smiles/");
    }
    if (scrollToCommentId > 0) {
        newsInlineCommentScrollIntoView(scrollToCommentId);
    }
    fpdaCommentsSectionLog("inject_ok", { generation: generation, childCount: list.children.length, event: "render_success" });
    newsInlineCommentsBindLoadMore(root);
    newsInlineCommentsBindInfiniteScroll(root);
    if (typeof newsInlineCommentsUpdateLoadMore === "function") {
        var resolvedCanLoadMore = arguments.length >= 4
            ? (canLoadMore === true || canLoadMore === "true")
            : ((root.getAttribute("data-can-load-more") || "false") === "true");
        var resolvedTotal = arguments.length >= 5
            ? (parseInt(totalCount, 10) || 0)
            : (parseInt(root.getAttribute("data-total-count") || "0", 10) || 0);
        var resolvedRendered = arguments.length >= 6
            ? (parseInt(renderedCount, 10) || (list.children ? list.children.length : 0))
            : (list.children ? list.children.length : 0);
        newsInlineCommentsUpdateLoadMore(resolvedCanLoadMore, resolvedTotal, resolvedRendered);
    }
    return "ok";
}

/**
 * Appends the next comment batch without replacing the whole list (infinite scroll).
 */
function newsInlineCommentsAppendHtml(html, generation, canLoadMore, totalCount, renderedCount) {
    var root = newsInlineCommentsRoot();
    if (!root) {
        return "no_root";
    }
    var collapsed = (root.getAttribute("data-collapsed") || "true") === "true";
    if (collapsed) {
        return "collapsed";
    }
    var list = root.querySelector("#news-inline-comments-list");
    if (!list) {
        return "no_list";
    }
    var expectedGen = root.getAttribute("data-fpda-webview-gen") || "0";
    if (generation > 0 && String(generation) !== String(expectedGen)) {
        root.setAttribute("data-fpda-webview-gen", String(generation));
        fpdaCommentsSectionLog("inject_gen_resync", { generation: generation });
    }
    if (!html) {
        return "ok";
    }
    // Idempotent append: skip any comment whose id is already in the DOM so that
    // re-runs (resume replay, inject retries, duplicate deltas) never duplicate rows.
    var temp = document.createElement("div");
    temp.innerHTML = html;
    var incoming = Array.prototype.slice.call(temp.children);
    var added = 0;
    for (var i = 0; i < incoming.length; i++) {
        var node = incoming[i];
        var cid = node.getAttribute ? node.getAttribute("data-news-comment-id") : null;
        if (cid && list.querySelector('[data-news-comment-id="' + cid + '"]')) {
            continue;
        }
        list.appendChild(node);
        added++;
    }
    bindNewsInlineCommentActions();
    if (typeof jsEmoticons !== "undefined") {
        jsEmoticons.parseAll("file:///android_asset/smiles/");
    }
    fpdaCommentsSectionLog("append_ok", { generation: generation, childCount: list.children.length, added: added });
    newsInlineCommentsBindLoadMore(root);
    if (arguments.length >= 3 && typeof newsInlineCommentsUpdateLoadMore === "function") {
        newsInlineCommentsUpdateLoadMore(
            canLoadMore === true || canLoadMore === "true",
            parseInt(totalCount, 10) || 0,
            parseInt(renderedCount, 10) || (list.children ? list.children.length : 0)
        );
    } else {
        newsInlineCommentsMaybeLoadMore(root, "append_ok");
    }
    return "ok";
}

function newsInlineCommentScrollIntoView(commentId) {
    if (!commentId) {
        return false;
    }
    var item = document.querySelector("[data-news-comment-id='" + commentId + "']");
    if (!item || typeof item.getBoundingClientRect !== "function") {
        return false;
    }
    // element.scrollIntoView()/window.scrollTo() are animated by this WebView even with
    // behavior:"auto" (see theme.js themeInstantScrollToY). The scroll is re-asserted on every
    // comment render while the section is still expanding and later batches keep appending, so a
    // smooth animation never settles on the target — you land somewhere above it. Compute the
    // absolute centered Y and assign scrollTop directly: instant and idempotent, so the final
    // re-assert pins the comment exactly into view regardless of intervening layout shifts.
    var rect = item.getBoundingClientRect();
    var se = document.scrollingElement || document.documentElement || null;
    var pageY = window.pageYOffset || (se ? se.scrollTop : 0) || 0;
    var viewport = window.innerHeight || (document.documentElement ? document.documentElement.clientHeight : 0) || 0;
    var top = rect.top + pageY - Math.max(0, (viewport - rect.height) / 2);
    if (!isFinite(top) || top < 0) top = 0;
    if (se) {
        try { se.scrollTop = top; } catch (ex) {}
    }
    if (document.body && document.body !== se) {
        try { document.body.scrollTop = top; } catch (ex) {}
    }
    return true;
}

function newsInlineCommentsLoad(rootNode) {
    var root = rootNode || newsInlineCommentsRoot();
    if (!root) {
        return;
    }
    var state = root.getAttribute("data-state") || "not-loaded";
    var list = root.querySelector("#news-inline-comments-list");
    var hasRenderedList = list && list.children && list.children.length > 0;
    if (state === "loading") {
        fpdaCommentsSectionLog("load_skipped_reason", { reason: "already_loading" });
        return;
    }
    if (state === "loaded" && hasRenderedList) {
        fpdaCommentsSectionLog("load_skipped_reason", { reason: "already_loaded_dom" });
        return;
    }
    if (typeof INews === "undefined" || typeof INews.onLoadInlineCommentsRequested !== "function") {
        fpdaCommentsSectionLog("bridge_called", {
            method: "onLoadInlineCommentsRequested",
            available: false,
            reason: "INews_missing"
        });
        newsInlineCommentsSetState("error", "Не удалось загрузить комментарии. Попробуйте обновить страницу.", null);
        return;
    }
    fpdaCommentsSectionLog("bridge_called", { method: "onLoadInlineCommentsRequested", available: true });
    newsInlineCommentsSetState("loading", "Загружаем комментарии...", null);
    root.newsInlineCommentsLoadTimer = setTimeout(function () {
        if ((root.getAttribute("data-state") || "") === "loading") {
            newsInlineCommentsSetState("error", "Не удалось загрузить комментарии. Попробуйте ещё раз.", null);
        }
    }, 15000);
    INews.onLoadInlineCommentsRequested();
}

function bindNewsInlineCommentsLoad() {
    newsInlineCommentsPruneDuplicates();
    newsInlineCommentsRemoveLegacyBlocks();
    ensureNewsCommentsSectionClickDelegation();
    newsInlineCommentsLogDomStats();

    var root = newsInlineCommentsRoot();
    if (!root) {
        fpdaCommentsSectionLog("bind_skipped", { hasRoot: false });
        return;
    }

    var toggle = root.querySelector("#news-comments-toggle");
    if (root.dataset.newsCommentsLoadBound === "true" && toggle) {
        fpdaCommentsSectionLog("bind_recheck");
    } else {
        if (!toggle) {
            fpdaCommentsSectionLog("bind_rebind", { reason: "toggle_missing" });
        }
        root.dataset.newsCommentsLoadBound = "true";
        fpdaCommentsSectionLog("bind_attached");
    }

    // Sync "Показать/Скрыть" after DOM refresh; clicks use document delegation.
    newsInlineCommentsSetCollapsed((root.getAttribute("data-collapsed") || "true") === "true", false, root);
    newsInlineCommentsBindLoadMore(root);
    newsInlineCommentsBindInfiniteScroll(root);
    if (!root.querySelector("#news-comments-toggle")) {
        fpdaCommentsSectionLog("toggle_missing");
    }
}

function newsInlineCommentsEnsureScrollSentinel(rootNode) {
    var root = rootNode || newsInlineCommentsRoot();
    if (!root) {
        return null;
    }
    var sentinel = root.querySelector("#news-inline-comments-scroll-sentinel");
    if (sentinel) {
        return sentinel;
    }
    var list = root.querySelector("#news-inline-comments-list");
    if (!list || !list.parentNode) {
        return null;
    }
    sentinel = document.createElement("div");
    sentinel.id = "news-inline-comments-scroll-sentinel";
    sentinel.className = "news-inline-comments-scroll-sentinel";
    sentinel.setAttribute("aria-hidden", "true");
    sentinel.style.height = "1px";
    sentinel.style.width = "100%";
    list.parentNode.insertBefore(sentinel, list.nextSibling);
    return sentinel;
}

function newsInlineCommentsEnsureLoadMoreButton(rootNode) {
    var root = rootNode || newsInlineCommentsRoot();
    if (!root) {
        return null;
    }
    var more = root.querySelector("#news-inline-comments-more");
    if (more) {
        return more;
    }
    var body = root.querySelector("#news-comments-body");
    if (!body) {
        var list = root.querySelector("#news-inline-comments-list");
        body = list && list.parentNode ? list.parentNode : root;
        fpdaCommentsSectionLog("load_more_button_parent_fallback", { usedRoot: body === root });
    }
    more = document.createElement("button");
    more.id = "news-inline-comments-more";
    more.type = "button";
    more.className = "news-inline-comments-more";
    more.hidden = true;
    more.textContent = "Показать ещё";
    body.appendChild(more);
    fpdaCommentsSectionLog("load_more_button_created");
    return more;
}

function newsInlineCommentsCanLoadMore(root) {
    if (!root) {
        return false;
    }
    return (root.getAttribute("data-can-load-more") || "false") === "true";
}

function newsInlineCommentsLoadMoreCooldownActive(root) {
    if (!root || !root.dataset) {
        return false;
    }
    var until = parseInt(root.dataset.newsCommentsAutoloadCooldownUntil || "0", 10);
    return until > 0 && Date.now() < until;
}

function newsInlineCommentsSuppressAutoload(root, ms) {
    if (!root || !root.dataset) {
        return;
    }
    var delay = parseInt(ms, 10);
    if (!delay || delay < 0) {
        delay = 500;
    }
    root.dataset.newsCommentsAutoloadCooldownUntil = String(Date.now() + delay);
}

function newsInlineCommentsRequestLoadMore(rootNode, source) {
    var root = rootNode || newsInlineCommentsRoot();
    if (!root || !newsInlineCommentsCanLoadMore(root)) {
        fpdaCommentsSectionLog("load_more_blocked", {
            source: source || "unknown",
            reason: !root ? "no_root" : "cannot_load_more"
        });
        return false;
    }
    if (root.dataset.newsCommentsLoadingMore === "true") {
        fpdaCommentsSectionLog("load_more_blocked", { source: source || "unknown", reason: "loading_more" });
        return false;
    }
    if (newsInlineCommentsLoadMoreCooldownActive(root)) {
        fpdaCommentsSectionLog("load_more_blocked", { source: source || "unknown", reason: "autoload_cooldown" });
        return false;
    }
    if (typeof INews === "undefined" || typeof INews.onLoadMoreCommentsRequested !== "function") {
        return false;
    }
    root.dataset.newsCommentsLoadingMore = "true";
    var list = root.querySelector("#news-inline-comments-list");
    var more = newsInlineCommentsEnsureLoadMoreButton(root);
    if (more) {
        more.hidden = false;
        more.style.display = "block";
        more.disabled = true;
        more.textContent = "Загрузка...";
    }
    fpdaCommentsSectionLog("scroll_load_more", {
        source: source || "scroll",
        rendered: list && list.children ? list.children.length : 0
    });
    INews.onLoadMoreCommentsRequested();
    return true;
}

function newsInlineCommentsMaybeLoadMore(rootNode, source) {
    var root = rootNode || newsInlineCommentsRoot();
    if (!root || !newsInlineCommentsCanLoadMore(root)) {
        return false;
    }
    var sentinel = root.querySelector("#news-inline-comments-scroll-sentinel");
    if (!sentinel || typeof sentinel.getBoundingClientRect !== "function") {
        return false;
    }
    var rect = sentinel.getBoundingClientRect();
    var viewportBottom = window.innerHeight || document.documentElement.clientHeight || 0;
    if (rect.top <= viewportBottom + 160) {
        return newsInlineCommentsRequestLoadMore(root, source || "sentinel_probe");
    }
    return false;
}

function newsInlineCommentsBindInfiniteScroll(rootNode) {
    var root = rootNode || newsInlineCommentsRoot();
    if (!root) {
        return;
    }
    newsInlineCommentsEnsureScrollSentinel(root);
    if (root.dataset.newsCommentsScrollBound !== "true") {
        if (typeof IntersectionObserver !== "undefined") {
            var sentinel = root.querySelector("#news-inline-comments-scroll-sentinel");
            if (sentinel) {
                root.dataset.newsCommentsScrollBound = "true";
                var observer = new IntersectionObserver(function (entries) {
                    for (var i = 0; i < entries.length; i++) {
                        if (!entries[i].isIntersecting) {
                            continue;
                        }
                        newsInlineCommentsRequestLoadMore(root, "intersection");
                    }
                }, { root: null, rootMargin: "160px 0px", threshold: 0 });
                observer.observe(sentinel);
            }
        }
    }
    if (root.dataset.newsCommentsScrollFallbackBound === "true") {
        return;
    }
    var onScroll = function () {
        newsInlineCommentsMaybeLoadMore(root, "scroll");
    };
    root.dataset.newsCommentsScrollFallbackBound = "true";
    window.addEventListener("scroll", onScroll, { passive: true });
    document.addEventListener("scroll", onScroll, { passive: true });
    fpdaCommentsSectionLog("scroll_fallback_bound");
}

function newsInlineCommentsBindLoadMore(rootNode) {
    var root = rootNode || newsInlineCommentsRoot();
    if (!root) {
        return;
    }
    var more = newsInlineCommentsEnsureLoadMoreButton(root);
    if (!more || more.dataset.newsCommentsMoreBound === "true") {
        return;
    }
    more.dataset.newsCommentsMoreBound = "true";
    more.addEventListener("click", function (ev) {
        ev.preventDefault();
        ev.stopPropagation();
        newsInlineCommentsRequestLoadMore(root, "button");
    });
}

function newsInlineCommentsUpdateLoadMore(canLoadMore, totalCount, renderedCount) {
    var root = newsInlineCommentsRoot();
    if (!root) {
        return;
    }
    root.dataset.newsCommentsLoadingMore = "false";
    root.setAttribute("data-can-load-more", canLoadMore ? "true" : "false");
    root.setAttribute("data-rendered-count", String(renderedCount || 0));
    root.setAttribute("data-total-count", String(totalCount || 0));
    newsInlineCommentsEnsureScrollSentinel(root);
    newsInlineCommentsBindInfiniteScroll(root);
    var more = newsInlineCommentsEnsureLoadMoreButton(root);
    if (!more) {
        if (canLoadMore) {
            fpdaCommentsSectionLog("load_more_blocked", { source: "update_load_more", reason: "no_button" });
        }
        return;
    }
    newsInlineCommentsBindLoadMore(root);
    if (totalCount > 0 && typeof newsInlineCommentsUpdateCount === "function") {
        newsInlineCommentsUpdateCount(root, totalCount);
        if (typeof newsInlineCommentsUpdateHeaderCount === "function") {
            newsInlineCommentsUpdateHeaderCount(totalCount);
        }
    }
    if (canLoadMore) {
        var remaining = Math.max(0, (totalCount || 0) - (renderedCount || 0));
        more.hidden = false;
        more.style.display = "block";
        more.textContent = remaining > 0 ? ("Показать ещё (" + remaining + ")") : "Показать ещё";
        more.disabled = false;
        // Debounce autoload ONLY right after a real new batch. This function is called many times per
        // batch (inject, count patches, model syncs); calling suppressAutoload every time kept pushing
        // the 500ms cooldown forward, so infinite-scroll never fired and comments appeared to "stop
        // loading" after the first page. Suppress only when the rendered count actually grew.
        var lastAutoloadRendered = parseInt(root.dataset.newsCommentsAutoloadRendered || "-1", 10);
        if ((renderedCount || 0) > lastAutoloadRendered) {
            newsInlineCommentsSuppressAutoload(root, 500);
            root.dataset.newsCommentsAutoloadRendered = String(renderedCount || 0);
        }
        fpdaCommentsSectionLog("load_more_shown", {
            rendered: renderedCount || 0,
            total: totalCount || 0,
            remaining: remaining
        });
    } else {
        more.hidden = true;
        more.style.display = "none";
        fpdaCommentsSectionLog("load_more_hidden", {
            rendered: renderedCount || 0,
            total: totalCount || 0
        });
    }
}

function newsInlineCommentsSyncDomState(root, domState) {
    if (!root || !domState) {
        return;
    }
    var state = domState || "not-loaded";
    root.setAttribute("data-state", state);
    root.setAttribute("aria-busy", state === "loading" ? "true" : "false");
    var retry = root.querySelector("#news-inline-comments-retry");
    if (retry) {
        retry.style.display = state === "error" ? "block" : "none";
        retry.disabled = state === "loading";
    }
}

function bindCommentsSection(collapsed, domState) {
    // Called from native after any WebView rerender/reload.
    var inewsAvailable = typeof INews !== "undefined";
    fpdaCommentsSectionLog("bindCommentsSection", {
        collapsed: !!collapsed,
        domState: domState || "",
        inewsAvailable: inewsAvailable,
        hasTapBridge: inewsAvailable && typeof INews.onCommentsSectionTapReceived === "function",
        hasJsEventBridge: inewsAvailable && typeof INews.onCommentsSectionJsEvent === "function"
    });
    ensureNewsCommentsSectionClickDelegation();
    bindNewsInlineCommentsLoad();
    var root = newsInlineCommentsRoot();
    if (root) {
        if (domState) {
            newsInlineCommentsSyncDomState(root, domState);
        }
        var domCollapsed = (root.getAttribute("data-collapsed") || "true") === "true";
        var targetCollapsed = !!collapsed;
        // Ignore stale native bind that would re-collapse after the user already expanded in DOM.
        if (!(domCollapsed === false && targetCollapsed === true)) {
            newsInlineCommentsSetCollapsed(targetCollapsed, false, root);
        } else {
            fpdaCommentsSectionLog("bind_skip_stale_collapse");
        }
    }
    newsInlineCommentsLogDomStats();
}

function toComments() {
    newsInlineCommentsLoad();
}

function bindNewsInlineCommentActions() {
    var buttons = document.querySelectorAll("[data-news-comment-action]");
    for (var i = 0; i < buttons.length; i++) {
        var button = buttons[i];
        if (button.dataset.newsCommentBound === "true") {
            continue;
        }
        button.dataset.newsCommentBound = "true";
        button.addEventListener("click", function (ev) {
            ev.preventDefault();
            ev.stopPropagation();
            var id = this.getAttribute("data-comment-id") || "";
            var action = this.getAttribute("data-news-comment-action") || "";
            if (action === "like") {
                INews.commentLike(id);
            } else if (action === "menu") {
                INews.commentMenu(id);
            } else if (action === "profile") {
                INews.commentProfile(id);
            } else if (action === "reply") {
                INews.commentReply(id);
            }
        });
    }
}

function newsInlineCommentSetPending(commentId, pending) {
    newsInlineCommentUpdateLike(commentId, null, null, pending);
}

function newsInlineCommentUpdateLike(commentId, liked, count, pending) {
    var item = document.querySelector("[data-news-comment-id='" + commentId + "']");
    if (!item) {
        return;
    }
    var like = item.querySelector("[data-news-comment-action='like']");
    if (!like) {
        return;
    }
    if (typeof pending === "boolean") {
        like.disabled = !!pending;
        like.classList.toggle("is-pending", !!pending);
    }
    if (typeof liked !== "boolean") {
        return;
    }
    like.classList.toggle("liked", liked);
    like.classList.toggle("not-liked", !liked);
    var safeCount = typeof count === "number" && count > 0 ? count : 0;
    like.textContent = safeCount > 0 ? String(safeCount) : "";
}

// Replace a single comment node in place with freshly built HTML. Used after editing a
// comment: the idempotent append path skips nodes whose id is already in the DOM, so an edit
// would otherwise never refresh the rendered text. Replacing just the one node keeps scroll
// position and avoids a full list re-render.
function newsInlineCommentReplaceNode(commentId, html) {
    if (!commentId || !html) {
        return "no_args";
    }
    var item = document.querySelector("[data-news-comment-id='" + commentId + "']");
    if (!item) {
        return "no_node";
    }
    var temp = document.createElement("div");
    temp.innerHTML = html;
    var fresh = temp.firstElementChild;
    if (!fresh) {
        return "no_html";
    }
    item.replaceWith(fresh);
    bindNewsInlineCommentActions();
    if (typeof jsEmoticons !== "undefined") {
        jsEmoticons.parseAll("file:///android_asset/smiles/");
    }
    return "ok";
}
//nativeEvents.addEventListener(nativeEvents.DOM, fixImagesSizeWithDensity, true);
nativeEvents.addEventListener(nativeEvents.DOM, transformImages, true);
nativeEvents.addEventListener(nativeEvents.DOM, bindNewsPolls, true);
nativeEvents.addEventListener(nativeEvents.PAGE, bindNewsPolls, true);
nativeEvents.addEventListener(nativeEvents.DOM, bindArticleContentLinks, true);
nativeEvents.addEventListener(nativeEvents.PAGE, bindArticleContentLinks, true);
nativeEvents.addEventListener(nativeEvents.DOM, bindVideoCards, true);
nativeEvents.addEventListener(nativeEvents.PAGE, bindVideoCards, true);
nativeEvents.addEventListener(nativeEvents.DOM, bindTaxonomyChips, true);
nativeEvents.addEventListener(nativeEvents.DOM, newsInlineCommentsPruneDuplicates, true);
nativeEvents.addEventListener(nativeEvents.DOM, bindNewsInlineCommentsLoad, true);
nativeEvents.addEventListener(nativeEvents.DOM, bindNewsInlineCommentActions, true);
