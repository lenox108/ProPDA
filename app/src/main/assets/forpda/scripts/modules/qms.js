var QMS_SCROLL_BOTTOM_RETRY_DELAYS_MS = [1, 80, 180, 420, 900, 1400, 2200, 3200, 4500];
var qmsScrollBootstrapActive = false;
var qmsScrollBootstrapTimer = null;
var qmsScrollImageLoadBound = false;

function isQmsChatPage() {
    return document.body && document.body.id === "qms";
}

function getQmsDocumentScrollHeight() {
    return Math.max(
        document.documentElement ? document.documentElement.scrollHeight : 0,
        document.body ? document.body.scrollHeight : 0
    );
}

function scrollQmsToBottomOnce() {
    if (!isQmsChatPage()) {
        return false;
    }
    var lastMess = getLastMess();
    if (lastMess && typeof lastMess.scrollIntoView === "function") {
        try {
            lastMess.scrollIntoView(false);
        } catch (e) {
            lastMess.scrollIntoView();
        }
    }
    var viewport = window.innerHeight || document.documentElement.clientHeight || 0;
    var targetY = Math.max(0, getQmsDocumentScrollHeight() - viewport);
    window.scrollTo(0, targetY);
    if (document.documentElement) {
        document.documentElement.scrollTop = targetY;
    }
    if (document.body) {
        document.body.scrollTop = targetY;
    }
    return isQmsNearBottom();
}

function resetQmsScrollPosition() {
    clearQmsScrollBootstrapTimer();
    qmsScrollBootstrapActive = false;
    window.scrollTo(0, 0);
    if (document.documentElement) {
        document.documentElement.scrollTop = 0;
    }
    if (document.body) {
        document.body.scrollTop = 0;
    }
}

function clearQmsScrollBootstrapTimer() {
    if (qmsScrollBootstrapTimer) {
        clearTimeout(qmsScrollBootstrapTimer);
        qmsScrollBootstrapTimer = null;
    }
}

function beginQmsScrollBootstrap() {
    qmsScrollBootstrapActive = true;
    clearQmsScrollBootstrapTimer();
    qmsScrollBootstrapTimer = setTimeout(function () {
        qmsScrollBootstrapActive = false;
        qmsScrollBootstrapTimer = null;
    }, QMS_SCROLL_BOTTOM_RETRY_DELAYS_MS[QMS_SCROLL_BOTTOM_RETRY_DELAYS_MS.length - 1] + 120);
}

function bindQmsScrollImageLoadRetries() {
    if (!qmsScrollBootstrapActive || qmsScrollImageLoadBound || !isQmsChatPage()) {
        return;
    }
    var listElem = document.querySelector(".mess_list");
    if (!listElem) {
        return;
    }
    var images = listElem.querySelectorAll("img");
    if (!images.length) {
        return;
    }
    qmsScrollImageLoadBound = true;
    for (var i = 0; i < images.length; i++) {
        (function (img) {
            if (img.complete) {
                return;
            }
            img.addEventListener("load", function onQmsImageLoaded() {
                img.removeEventListener("load", onQmsImageLoaded);
                if (qmsScrollBootstrapActive) {
                    scrollQmsToBottomOnce();
                }
            });
        })(images[i]);
    }
}

function cancelQmsScrollBootstrap() {
    if (qmsScrollBootstrapActive) {
        qmsScrollBootstrapActive = false;
        clearQmsScrollBootstrapTimer();
    }
}

// The user grabbing the scroll (touch/wheel/nav key) must immediately stop the multi-stage
// scroll-to-bottom pass; otherwise the timed retries and image-load handlers keep yanking the
// viewport back, which reads as the chat freezing / jittering / not scrolling.
if (!window.qmsScrollUserInputBound) {
    window.qmsScrollUserInputBound = true;
    window.addEventListener("touchstart", cancelQmsScrollBootstrap, { passive: true });
    window.addEventListener("wheel", cancelQmsScrollBootstrap, { passive: true });
    window.addEventListener("keydown", function (e) {
        switch (e.key) {
            case "ArrowUp":
            case "ArrowDown":
            case "PageUp":
            case "PageDown":
            case "Home":
            case "End":
            case " ":
                cancelQmsScrollBootstrap();
                break;
        }
    });
}

function scrollQmsToBottomWithRetries() {
    if (!isQmsChatPage()) {
        return;
    }
    qmsScrollImageLoadBound = false;
    beginQmsScrollBootstrap();
    for (var i = 0; i < QMS_SCROLL_BOTTOM_RETRY_DELAYS_MS.length; i++) {
        (function (ms) {
            setTimeout(function () {
                if (!qmsScrollBootstrapActive) {
                    return;
                }
                scrollQmsToBottomOnce();
                if (ms === QMS_SCROLL_BOTTOM_RETRY_DELAYS_MS[0]) {
                    bindQmsScrollImageLoadRetries();
                }
            }, ms);
        })(QMS_SCROLL_BOTTOM_RETRY_DELAYS_MS[i]);
    }
    var verifyDelay = QMS_SCROLL_BOTTOM_RETRY_DELAYS_MS[QMS_SCROLL_BOTTOM_RETRY_DELAYS_MS.length - 1] + 160;
    setTimeout(function () {
        if (!qmsScrollBootstrapActive) {
            return;
        }
        if (!isQmsNearBottom()) {
            scrollQmsToBottomOnce();
        }
    }, verifyDelay);
}

function initQms() {
    // DOM ready for empty shell; messages arrive via showNewMess from native code.
}

function scrollQms() {
    if (!isQmsChatPage()) {
        return;
    }
    if (!getLastMess()) {
        return;
    }
    scrollQmsToBottomWithRetries();
}

var lastMessRequestTS = new Date().getTime();

window.addEventListener("scroll", function () {
    if (!isQmsChatPage()) {
        return;
    }
    if (qmsScrollBootstrapActive) {
        return;
    }
    var date = new Date();
    if (window.pageYOffset == 0 && (date.getTime() - lastMessRequestTS >= 500)) {
        lastMessRequestTS = date.getTime();
        IChat.loadMoreMessages();
    }
});

function getLastMess() {
    var listElem = document.querySelector(".mess_list");
    if (!listElem) {
        return null;
    }
    var messages = listElem.querySelectorAll(".mess_container");
    if (!messages.length) {
        return null;
    }
    return messages[messages.length - 1];
}

function showMoreMess(listSrc) {
    var listElem = document.querySelector(".mess_list");
    var lastHeight = listElem.offsetHeight;
    var beforeCount = listElem.querySelectorAll(".mess_container").length;
    listElem.insertAdjacentHTML("afterbegin", listSrc);
    addedNewMessages(0, listElem.querySelectorAll(".mess_container").length - beforeCount);
    window.scrollBy(0, listElem.offsetHeight - lastHeight);
}

function isQmsNearBottom() {
    var scrollTop = window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0;
    var viewportHeight = window.innerHeight || document.documentElement.clientHeight || 0;
    var fullHeight = getQmsDocumentScrollHeight();
    return fullHeight - (scrollTop + viewportHeight) <= 96;
}

function resetQmsMessageList() {
    var listElem = document.querySelector(".mess_list");
    if (!listElem) {
        return false;
    }
    listElem.innerHTML = "";
    resetQmsScrollPosition();
    return true;
}

function isQmsMessageListReady() {
    return isQmsChatPage() &&
        !!document.querySelector(".mess_list") &&
        typeof showNewMess === "function" &&
        typeof countQmsMessageContainers === "function";
}

function countQmsMessageContainers() {
    if (!isQmsChatPage()) {
        return 0;
    }
    var listElem = document.querySelector(".mess_list");
    if (!listElem) {
        return 0;
    }
    return listElem.querySelectorAll(".mess_container").length;
}

function showNewMess(listSrc, withScroll) {
    var listElem = document.querySelector(".mess_list");
    if (!listElem) {
        return 0;
    }
    var shouldScroll = withScroll || isQmsNearBottom();
    var beforeCount = listElem.querySelectorAll(".mess_container").length;
    listElem.insertAdjacentHTML("beforeend", listSrc);
    addedNewMessages(beforeCount);
    if (shouldScroll) {
        scrollQmsToBottomWithRetries();
    }
    return countQmsMessageContainers();
}

function makeAllRead() {
    var listElem = document.querySelector(".mess_list");
    var unreaded = listElem.querySelectorAll(".mess_container.unread");
    for (var i = 0; i < unreaded.length; i++) {
        unreaded[i].classList.remove("unread");
    }
}
const savepicPattern = /Thumb:\s?([\s\S]*)/g;

function resolveQmsLinkHref(href) {
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
        return new URL(trimmed, "https://4pda.to/forum/").href;
    } catch (e) {
        return null;
    }
}

function openResolvedQmsLink(resolved) {
    if (!resolved) {
        return;
    }
    if (typeof IChat !== "undefined" && typeof IChat.openLink === "function") {
        IChat.openLink(resolved);
        return;
    }
    window.location.href = resolved;
}

function bindQmsContentLinks(root) {
    if (!root) {
        return;
    }
    var links = root.querySelectorAll(".mess .content a[href]");
    for (var i = 0; i < links.length; i++) {
        var link = links[i];
        if (link.dataset.qmsContentLinkBound === "true") {
            continue;
        }
        link.dataset.qmsContentLinkBound = "true";
        link.addEventListener("click", function (ev) {
            var rawHref = this.getAttribute("href");
            var resolved = resolveQmsLinkHref(rawHref);
            if (!resolved) {
                return;
            }
            ev.preventDefault();
            ev.stopPropagation();
            openResolvedQmsLink(resolved);
        }, true);
    }
}

function bindQmsContentLinksInList() {
    if (!isQmsChatPage()) {
        return;
    }
    var listElem = document.querySelector(".mess_list");
    if (!listElem) {
        return;
    }
    bindQmsContentLinks(listElem);
}

function transformQmsAttachments() {
    var links = document.querySelectorAll("a[href*='image.ibb.co']");
    for (var i = 0; i < links.length; i++) {
        var link = links[i];
        if(link.classList.contains("transformed")){
            continue;
        }
        var alt = link.textContent;
        var match
        var previewImg;
        while (match = savepicPattern.exec(link.textContent)) {
            previewImg = match[1];
        }
        link.innerHTML = "<img src=\""+previewImg+"\" alt=\"" + alt + "\" class=\"attach\">";
        link.classList.add("transformed");
    }
}

function runQmsMessageTransform(fn) {
    if (typeof fn !== "function") {
        return;
    }
    try {
        fn();
    } catch (e) {
        // A transform failing on one message's content must not abort the rest:
        // bubbles are already inserted, so swallowing keeps the chat visible.
        if (window.console && console.warn) {
            console.warn("QMS message transform failed", e);
        }
    }
}

function addedNewMessages(fromIndex, limit) {
    var listElem = document.querySelector(".mess_list");
    if (!listElem) {
        return;
    }
    var containers = listElem.querySelectorAll(".mess_container");
    var start = Math.max(0, fromIndex || 0);
    var end = limit != null ? Math.min(containers.length, start + limit) : containers.length;
    if (start >= end) {
        return;
    }
    var placeholders = [];
    for (var i = 0; i < containers.length; i++) {
        if (i < start || i >= end) {
            var placeholder = document.createComment("qms-skip-transform");
            listElem.replaceChild(placeholder, containers[i]);
            placeholders.push({ placeholder: placeholder, node: containers[i] });
        }
    }
    try {
        runQmsMessageTransform(transformQmsAttachments);
        runQmsMessageTransform(startTransformer2);
        runQmsMessageTransform(transformSnapbacks);
        runQmsMessageTransform(transformQuotes);

        runQmsMessageTransform(improveCodeBlock);
        runQmsMessageTransform(blocksOpenClose);
        runQmsMessageTransform(removeImgesSrc);
        runQmsMessageTransform(addIcons);
        runQmsMessageTransform(function () { jsEmoticons.parseAll(); });
        runQmsMessageTransform(fixImagesSizeWithDensity);
        runQmsMessageTransform(bindQmsContentLinksInList);
    } finally {
        for (var p = 0; p < placeholders.length; p++) {
            listElem.replaceChild(placeholders[p].node, placeholders[p].placeholder);
        }
    }
}

nativeEvents.addEventListener(nativeEvents.DOM, initQms);
nativeEvents.addEventListener(nativeEvents.DOM, bindQmsContentLinksInList, true);
nativeEvents.addEventListener(nativeEvents.PAGE, fixImagesSizeWithDensity, true);
