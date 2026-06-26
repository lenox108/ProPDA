// Эти строки приходят из Java через setLoadAction(...) — Kotlin шлёт имя enum-константы
// (ThemeViewModel.ActionState), а не числовой id. Раньше здесь сравнивались "0"/"1"/"2",
// и условия никогда не срабатывали — сломана память позиции скролла при BACK/REFRESH.
const BACK_ACTION = "BACK";
const REFRESH_ACTION = "REFRESH";
const NORMAL_ACTION = "NORMAL";
const END_ACTION = "END";
/** Задержки (мс) для повторного scrollToElement — вёрстка/картинки подгружаются после первого кадра. */
var SCROLL_ANCHOR_RETRY_DELAYS_MS = [1, 120, 400, 900];
var SCROLL_BOTTOM_RETRY_DELAYS_MS = [1, 80, 180, 420, 900, 1400, 2200];
var END_NAV_TOP_SUPPRESS_MS = 5000;
var END_SCROLL_MIN_Y_THRESHOLD = 480;
/** Mirrors Kotlin [ThemeUnreadHybridAnchorGuardPolicy.ANCHOR_GUARD_MAX_BLOCK_MS]. */
var UNREAD_ANCHOR_GUARD_MAX_MS = 3200;
window.loadAction = NORMAL_ACTION;
window.loadScrollY = 0;
window.loadAnchorPostId = "";
/** True only when Kotlin [hasUnreadTarget]; arms blocking unread hybrid guard. */
window.loadAnchorUnreadTarget = false;
/** True when ambiguous all-read bottom redirect (no unread target); blocks initial top bootstrap. */
window.loadAmbiguousAllReadBottom = false;
window.loadOpenSessionKind = "";
window.loadAnchorOffsetTop = null;
window.loadScrollRatio = null;
window.loadWasNearBottom = false;
window.refreshRestoreId = "";
window.refreshRestoreMode = "";
window.refreshRestoreSource = "";
window.themeLastLinkSourceAnchor = null;
window.__themeLastClickedPostAnchor = null;
/**
 * S-01 / R-03 (audit Finding S-01 / R-03): single-owner handshake for the
 * initial-anchor scroll. Kotlin's [ThemeScrollCommand] (INITIAL_ANCHOR) is the
 * authoritative owner. When Kotlin intends to send that command for the current
 * page-load it sets [window.__themeInitialAnchorExpectedUntil] (a monotonic
 * deadline) via [setThemeInitialAnchorExpected] BEFORE the legacy DOM listener
 * runs. While that deadline is in the future the JS DOM-anchor path yields
 * instead of racing on whether [window.__themeScrollCommandId] happens to be set
 * yet. The JS DOM path stays a FALLBACK: it only runs after the deadline
 * elapses with no Kotlin command (the safety net when Kotlin sends none).
 */
window.__themeInitialAnchorExpectedUntil = 0;
/**
 * R-04 (audit Finding R-04): render-generation scope for the scroll command id.
 * A bare [window.__themeScrollCommandId] could lose its completion across a
 * reload (a stale completion from a previous topic being misattributed). The
 * generation is bumped on every new page-load setup ([setLoadAction]); a
 * completion is only delivered when the command's captured generation still
 * matches the live one. The whole `window` is recreated on `loadDataWithBaseURL`
 * so this counter naturally resets per topic.
 */
window.__themeScrollCommandGeneration = 0;
window.__themeScrollCommandGenerationAtExec = 0;
/** Default window (ms) the DOM-anchor fallback waits for a Kotlin INITIAL_ANCHOR command. */
var INITIAL_ANCHOR_KOTLIN_HANDSHAKE_MS = 700;
/** Hard deadline (ms) for the BACK-anchor settle loop before it completes with a fallback. */
var BACK_ANCHOR_SETTLE_DEADLINE_MS = 4000;
/** Bounded window (ms) after the initial anchor settles during which a late media load may re-anchor (S-02). */
var INITIAL_ANCHOR_MEDIA_REANCHOR_WINDOW_MS = 2500;
/**
 * S-02 (audit Finding S-02): the active initial-anchor name and a monotonic
 * deadline. While the deadline is in the future and the user has not scrolled,
 * a tall image that finishes loading after the initial scroll triggers ONE
 * bounded re-anchor so the target does not drift below the fold. Cleared once
 * the user scrolls, the window elapses, or a new page-load arms a new anchor.
 */
var themeInitialAnchorReanchorName = "";
var themeInitialAnchorReanchorUntil = 0;
/**
 * Monotonic timestamp of the last successful initial end-anchor scroll for the
 * CURRENT page-load. Set on the final retry of
 * [scrollToEndAnchorOrBottomWithRetries] (the "end_anchor" completion path) and
 * reset by [setLoadAction] / any user-initiated scroll.
 *
 * When this is non-zero AND no user scroll has happened since, repeated calls
 * to [scrollToEndAnchorOrBottomWithRetries] for the same page-load are dropped
 * — this is what stops the "anchor jumps to a different post" / "blinks"
 * regression: each subsequent [setLoadAnchorPostId] (from a follow-up page
 * render, infinite-scroll, redirect resolution, read-state update, etc.) used
 * to retrigger end-anchor scroll to the LATEST post id, even though the
 * initial scroll had already settled on the correct post. Pinning the scroll
 * here means the original target is honoured until the user actually scrolls
 * the topic.
 *
 * The very first [scrollToEndAnchorOrBottomWithRetries] call after a new
 * page-load ([setLoadAction] resets the flag) always wins, so legitimate
 * initial scrolls are not blocked.
 */
var endAnchorScrollSettledAt = 0;
/**
 * Monotonic generation counter for the JS anchor-scroll retry chain. Bumped by
 * [cancelThemeAnchorScrollRetries] so any in-flight retry whose captured
 * generation no longer matches is dropped ([isThemeAnchorScrollCurrent]). It is
 * read (via `++` and equality checks) before it is ever explicitly assigned, so
 * it MUST be declared here — otherwise the first read throws a ReferenceError
 * that aborts the whole theme.js runtime (regression observed in the field:
 * "Uncaught ReferenceError: themeAnchorScrollGeneration is not defined").
 */
var themeAnchorScrollGeneration = 0;
/**
 * Name of the anchor a retry chain is currently pending on. Read by the DOM
 * initial-anchor fallback before any retry has written it, so it must be
 * declared up-front for the same reason as [themeAnchorScrollGeneration].
 */
var themeAnchorRetryPendingName = "";
/**
 * Currently-resolved scroll anchor element and the post container that carries
 * the legacy `.active` class. Both are mutated as implicit globals throughout
 * the scroll/activation paths ([scrollToElement], [doScroll], [destroyRuntime])
 * and — critically — READ before they are ever assigned: [doScroll] reads
 * [elemToActivation] (line ~2435) on the very first scroll, and
 * [logScrollAnchorDiag] reads [anchorElem] before the anchor path assigns it.
 * An undeclared read throws "Uncaught ReferenceError: elemToActivation is not
 * defined" (observed in the field at theme.js:2435) which aborts the whole JS
 * action batch — the same failure mode as [themeAnchorScrollGeneration]. They
 * MUST be declared up-front so the first read yields `undefined`, not a throw.
 */
var anchorElem = null;
var elemToActivation = null;
var themeRuntimeDestroyed = false;
var themeRuntimeTimers = [];
var themeRuntimeRafs = [];
var themeInfiniteScroll = {
    threshold: 800,
    loadingTop: false,
    loadingBottom: false,
    lastVisiblePage: null,
    suppressUntil: 0,
    initialTopAutoloadSuppressedUntil: 0,
    endScrollPending: false,
    /** Blocks hybrid top autoload until INITIAL_ANCHOR unread scroll settles. */
    unreadInitialAnchor: "",
    unreadInitialAnchorPending: false,
    unreadAnchorGuardStartedAt: 0,
    userScrolled: false,
    scrollRafPending: false,
    visiblePageRafPending: false,
    visiblePageThrottleTimer: null,
    bootstrapTimers: []
};
var themeMediaImageLoadBatch = {
    rafPending: false,
    images: []
};
// Post action menu opens only via the explicit "..." button in the template.
// Long-press on message body was removed: it conflicts with native text selection on Android WebView.

function isThemeRuntimeAlive() {
    return themeRuntimeDestroyed !== true;
}

function themeRuntimeSetTimeout(callback, delayMs) {
    if (!isThemeRuntimeAlive()) return null;
    var timer = setTimeout(function () {
        removeThemeRuntimeTimer(timer);
        if (!isThemeRuntimeAlive()) return;
        callback();
    }, delayMs || 0);
    themeRuntimeTimers.push(timer);
    return timer;
}

function removeThemeRuntimeTimer(timer) {
    if (!timer) return;
    for (var i = themeRuntimeTimers.length - 1; i >= 0; i--) {
        if (themeRuntimeTimers[i] === timer) {
            themeRuntimeTimers.splice(i, 1);
            return;
        }
    }
}

function themeRuntimeRequestAnimationFrame(callback) {
    if (!isThemeRuntimeAlive()) return null;
    var raf = requestAnimationFrame(function () {
        removeThemeRuntimeRaf(raf);
        if (!isThemeRuntimeAlive()) return;
        callback();
    });
    themeRuntimeRafs.push(raf);
    return raf;
}

function removeThemeRuntimeRaf(raf) {
    if (!raf) return;
    for (var i = themeRuntimeRafs.length - 1; i >= 0; i--) {
        if (themeRuntimeRafs[i] === raf) {
            themeRuntimeRafs.splice(i, 1);
            return;
        }
    }
}

function clearThemeRuntimeAsyncWork() {
    while (themeRuntimeTimers.length) {
        clearTimeout(themeRuntimeTimers.pop());
    }
    while (themeRuntimeRafs.length) {
        cancelAnimationFrame(themeRuntimeRafs.pop());
    }
}

function isThemeRenderDebugEnabled() {
    return typeof PageInfo !== "undefined" && PageInfo.debug === true;
}

function logThemeRender(message) {
    if (!isThemeRenderDebugEnabled()) return;
    console.log("[ThemeRender] " + message);
}

function isReadTopicSoftAnchorOpen() {
    return window.loadAnchorUnreadTarget !== true &&
        window.loadAnchorPostId &&
        window.loadAnchorPostId.length;
}

function isUnreadAnchorHybridBlocked() {
    if (window.loadAnchorUnreadTarget !== true) {
        return false;
    }
    if (themeInfiniteScroll.unreadInitialAnchorPending !== true) {
        return false;
    }
    if (!themeInfiniteScroll.unreadInitialAnchor || !window.loadAnchorPostId) {
        clearUnreadInitialAnchorScroll("guard_no_anchor_target");
        return false;
    }
    if (themeInfiniteScroll.unreadAnchorGuardStartedAt > 0 &&
        Date.now() - themeInfiniteScroll.unreadAnchorGuardStartedAt >= UNREAD_ANCHOR_GUARD_MAX_MS) {
        clearUnreadAnchorHybridGuard("guard_timeout");
        return false;
    }
    return true;
}

function logAnchorGuardBlocked(scope, reason) {
    var msg = "FPDA_THEME_ANCHOR_GUARD blocked_" + scope + " reason=" + reason;
    logThemeRender(msg);
    if (hasThemePresenter() && typeof IThemePresenter.log === "function") {
        IThemePresenter.log(msg);
    }
}

function logThemeRuntimeWarning(scope, error) {
    if (!isThemeRenderDebugEnabled()) return;
    console.log("[" + scope + "] " + error);
}

window.__themeBlankDiagErrors = [];
window.addEventListener("error", function (event) {
    if (!isThemeRenderDebugEnabled()) return;
    var message = event && event.message ? event.message : "unknown";
    window.__themeBlankDiagErrors.push({
        type: "error",
        message: message,
        source: event && event.filename ? event.filename : "",
        line: event && event.lineno ? event.lineno : 0,
        column: event && event.colno ? event.colno : 0
    });
    if (window.__themeBlankDiagErrors.length > 8) window.__themeBlankDiagErrors.shift();
});
window.addEventListener("unhandledrejection", function (event) {
    if (!isThemeRenderDebugEnabled()) return;
    var reason = event && event.reason ? String(event.reason) : "unknown";
    window.__themeBlankDiagErrors.push({type: "promise", message: reason});
    if (window.__themeBlankDiagErrors.length > 8) window.__themeBlankDiagErrors.shift();
});

function hasThemePresenter() {
    return typeof IThemePresenter !== "undefined" && IThemePresenter !== null;
}

function hasBaseBridge() {
    return typeof IBase !== "undefined" && IBase !== null;
}

function themeToast(message) {
    if (hasThemePresenter() && typeof IThemePresenter.toast === "function") {
        IThemePresenter.toast(message);
    }
}

function getThemeRenderToken() {
    return typeof window.__themeRenderToken === "string" ? window.__themeRenderToken : "";
}

function isRealThemePost(el) {
    return !!(el &&
        el.dataset &&
        el.dataset.postId &&
        el.classList &&
        el.classList.contains("post_container") &&
        !el.classList.contains("topic_hat_entry") &&
        !el.classList.contains("topic_hat_fixed"));
}

function getThemeRenderedPostsState() {
    var doc = document.documentElement || {};
    var body = document.body || {};
    var containers = document.querySelectorAll(".theme_page_container[data-page-number]");
    var candidates = document.querySelectorAll(".post_container[data-post-id]:not(.topic_hat_entry):not(.topic_hat_fixed)");
    var postCount = 0;
    for (var i = 0; i < candidates.length; i++) {
        if (isRealThemePost(candidates[i])) postCount++;
    }
    var clientHeight = window.innerHeight || doc.clientHeight || 0;
    var scrollHeight = Math.max(doc.scrollHeight || 0, body.scrollHeight || 0);
    var bodyTextLength = body && body.textContent ? body.textContent.trim().length : 0;
    return {
        ok: containers.length > 0 && postCount > 0 && scrollHeight > Math.max(120, clientHeight * 0.25),
        postCount: postCount,
        containerCount: containers.length,
        hasPostContainer: postCount > 0,
        hasPageContainer: containers.length > 0,
        scrollHeight: scrollHeight,
        clientHeight: clientHeight,
        bodyTextLength: bodyTextLength,
        readyState: document.readyState || ""
    };
}

function getThemeBlankTopicDiagnostics() {
    var doc = document.documentElement || {};
    var body = document.body || {};
    var posts = document.querySelectorAll(".post_container[data-post-id]:not(.topic_hat_entry):not(.topic_hat_fixed)");
    var containers = document.querySelectorAll(".theme_page_container[data-page-number]");
    var postsList = document.querySelector(".posts_list");
    var firstPost = posts.length ? posts[0] : null;
    var firstContainer = containers.length ? containers[0] : null;
    var bodyStyle = body && window.getComputedStyle ? window.getComputedStyle(body) : null;
    var postsListStyle = postsList && window.getComputedStyle ? window.getComputedStyle(postsList) : null;
    var firstPostStyle = firstPost && window.getComputedStyle ? window.getComputedStyle(firstPost) : null;
    var firstContainerStyle = firstContainer && window.getComputedStyle ? window.getComputedStyle(firstContainer) : null;
    var firstPostRect = firstPost && firstPost.getBoundingClientRect ? firstPost.getBoundingClientRect() : null;
    var scrollHeight = Math.max(doc.scrollHeight || 0, body.scrollHeight || 0);
    return {
        ok: getThemeRenderedPostsState().ok === true,
        readyState: document.readyState || "",
        runtimeDestroyed: themeRuntimeDestroyed === true,
        bodyChildren: body && body.children ? body.children.length : 0,
        postCount: posts.length,
        containerCount: containers.length,
        postsListExists: !!postsList,
        scrollHeight: scrollHeight,
        clientHeight: window.innerHeight || doc.clientHeight || 0,
        bodyDisplay: bodyStyle ? bodyStyle.display : "",
        bodyVisibility: bodyStyle ? bodyStyle.visibility : "",
        bodyOpacity: bodyStyle ? bodyStyle.opacity : "",
        postsListDisplay: postsListStyle ? postsListStyle.display : "",
        postsListVisibility: postsListStyle ? postsListStyle.visibility : "",
        postsListHeight: postsList ? postsList.offsetHeight : 0,
        firstContainerDisplay: firstContainerStyle ? firstContainerStyle.display : "",
        firstContainerVisibility: firstContainerStyle ? firstContainerStyle.visibility : "",
        firstContainerHeight: firstContainer ? firstContainer.offsetHeight : 0,
        firstPostId: firstPost && firstPost.dataset ? firstPost.dataset.postId || "" : "",
        firstPostDisplay: firstPostStyle ? firstPostStyle.display : "",
        firstPostVisibility: firstPostStyle ? firstPostStyle.visibility : "",
        firstPostOpacity: firstPostStyle ? firstPostStyle.opacity : "",
        firstPostOffsetHeight: firstPost ? firstPost.offsetHeight : 0,
        firstPostRectTop: firstPostRect ? firstPostRect.top : null,
        firstPostRectHeight: firstPostRect ? firstPostRect.height : null,
        errors: window.__themeBlankDiagErrors.slice()
    };
}

window.PropdaThemeRuntime = window.PropdaThemeRuntime || {};
window.PropdaThemeRuntime.getRenderedPostsState = getThemeRenderedPostsState;
window.PropdaThemeRuntime.getBlankTopicDiagnostics = getThemeBlankTopicDiagnostics;
window.PropdaThemeRuntime.hasRenderedPosts = function () {
    return getThemeRenderedPostsState().ok === true;
};

function bindThemeLinkSourceAnchorEvents() {
    if (!isThemeRuntimeAlive()) return;
    var events = ["pointerdown", "touchstart", "mousedown", "click"];
    for (var i = 0; i < events.length; i++) {
        document.removeEventListener(events[i], rememberThemeLinkSourceAnchor, true);
        document.addEventListener(events[i], rememberThemeLinkSourceAnchor, true);
    }
}

function unbindThemeLinkSourceAnchorEvents() {
    var events = ["pointerdown", "touchstart", "mousedown", "click"];
    for (var i = 0; i < events.length; i++) {
        document.removeEventListener(events[i], rememberThemeLinkSourceAnchor, true);
    }
}

function onThemeAnchorScrollCancelInput(event) {
    if (!isThemeRuntimeAlive()) return;
    themeInfiniteScroll.userScrolled = true;
    // The user has touched / scrolled the topic: any pending end-anchor
    // "already settled" latch is no longer authoritative — the user is in
    // control now, so clear it so a future [setLoadAnchorPostId] can drive a
    // fresh end-anchor scroll if the user navigates elsewhere.
    endAnchorScrollSettledAt = 0;
    // S-02: the user is driving the scroll now — never re-anchor on late media.
    clearThemeInitialAnchorMediaReanchor();
    cancelThemeAnchorScrollRetries();
    logRefreshScroll("cancel userInput event=" + (event && event.type ? event.type : "") + " y=" + getScrollTop());
}

function bindThemeAnchorScrollCancelInputEvents() {
    ["touchstart", "touchmove", "wheel"].forEach(function (name) {
        window.removeEventListener(name, onThemeAnchorScrollCancelInput);
        window.addEventListener(name, onThemeAnchorScrollCancelInput, {passive: true});
    });
}

function unbindThemeAnchorScrollCancelInputEvents() {
    ["touchstart", "touchmove", "wheel"].forEach(function (name) {
        window.removeEventListener(name, onThemeAnchorScrollCancelInput);
    });
}

function onThemeOverlayViewportChanged() {
    if (!isThemeRuntimeAlive()) return;
    updateThemeHatOverlayLayout();
    updateThemePollOverlayLayout();
}

function initThemePostGestureActions() {
    // No-op: kept for DOM bootstrap compatibility.
}

function destroyThemePostGestureActions() {
    // No-op: long-press post menu removed.
}

var themeLayoutSanitizeToken = 0;
var themeFixedContentSanitizedToken = -1;

function bumpThemeLayoutSanitizeToken() {
    themeLayoutSanitizeToken++;
}

function sanitizeThemeInteractiveLayout() {
    sanitizeThemeOverlayState();
    if (themeFixedContentSanitizedToken !== themeLayoutSanitizeToken) {
        themeFixedContentSanitizedToken = themeLayoutSanitizeToken;
        sanitizePostFixedPositionedContent();
    }
}

function sanitizeThemeOverlayState() {
    try {
        var hat = document.querySelector(".topic_hat_fixed.top_hat_overlay_host");
        if (hat) {
            var hatBody = hat.querySelector(".hat_content");
            var hostOpen = hat.classList.contains("open") && !hat.classList.contains("close");
            var bodyOpen = !!(hatBody && hatBody.classList.contains("open") && !hatBody.classList.contains("close"));
            if (hostOpen && bodyOpen) {
                hat.style.pointerEvents = "";
                document.body.classList.add("topic_hat_overlay_open");
            } else {
                hat.classList.remove("open");
                hat.classList.add("close");
                hat.style.pointerEvents = "none";
                if (hatBody) {
                    hatBody.classList.remove("open");
                    hatBody.classList.add("close");
                }
                document.body.classList.remove("topic_hat_overlay_open");
            }
        }
        var poll = document.getElementById("theme_poll_overlay_host") || document.querySelector(".poll.poll_overlay_host");
        if (poll && !poll.classList.contains("open")) {
            poll.classList.add("close");
            poll.style.pointerEvents = "none";
            document.body.classList.remove("theme_poll_overlay_open");
        } else if (poll) {
            poll.style.pointerEvents = "";
        }
    } catch (ex) {
        logThemeRuntimeWarning("ThemeScrollGuard", "overlay state error " + ex);
    }
}

function closeThemeToolbarOverlaysForNavigation(notifyNative) {
    try {
        var hat = document.querySelector(".topic_hat_fixed.top_hat_overlay_host");
        if (hat) {
            var hatBody = hat.querySelector(".hat_content");
            if (typeof closeThemeHatOverlayHost === "function" && hatBody) {
                closeThemeHatOverlayHost(hat, hatBody, notifyNative);
            } else {
                hat.classList.remove("open");
                hat.classList.add("close");
                hat.style.pointerEvents = "none";
                if (hatBody) {
                    hatBody.classList.remove("initial_open");
                    hatBody.classList.remove("open");
                    hatBody.classList.add("close");
                }
                document.body.classList.remove("topic_hat_overlay_open");
                if (notifyNative !== false && typeof IThemePresenter !== "undefined") {
                    IThemePresenter.setHatOpen("false");
                }
            }
        }

        var poll = document.getElementById("theme_poll_overlay_host") || document.querySelector(".poll.poll_overlay_host");
        if (poll) {
            var pollBody = poll.querySelector(".hat_content");
            poll.classList.remove("open");
            poll.classList.add("close");
            poll.style.pointerEvents = "none";
            if (pollBody) {
                pollBody.classList.remove("open");
                pollBody.classList.add("close");
            }
            document.body.classList.remove("theme_poll_overlay_open");
            if (notifyNative !== false && typeof IThemePresenter !== "undefined") {
                IThemePresenter.setPollOpen("false");
            }
        }
    } catch (ex) {
        logThemeRuntimeWarning("ThemeScrollGuard", "close overlays error " + ex);
    }
}

function sanitizePostFixedPositionedContent() {
    try {
        var nodes = document.querySelectorAll(".post_body *");
        for (var i = 0; i < nodes.length; i++) {
            var node = nodes[i];
            var style = window.getComputedStyle ? window.getComputedStyle(node) : null;
            if (!style) continue;
            var position = style.position;
            if (position === "fixed" || position === "sticky") {
                node.style.position = "relative";
                node.style.top = "auto";
                node.style.bottom = "auto";
                node.style.left = "auto";
                node.style.right = "auto";
                node.style.zIndex = "auto";
            }
            if (style.pointerEvents !== "none" && parseFloat(style.opacity || "1") === 0) {
                node.style.pointerEvents = "none";
            }
        }
    } catch (ex) {
        logThemeRuntimeWarning("ThemeScrollGuard", "fixed content error " + ex);
    }
}

/**
 * Votes post rating (+/-) for the post that contains the clicked button.
 * Fixes cases when template ids desync due to DOM/layout quirks.
 */
function votePostFromEl(el, type) {
    try {
        if (!hasThemePresenter() || typeof IThemePresenter.votePost !== "function") return;
        var node = el;
        while (node && node.classList && !node.classList.contains("post_container") && !node.dataset.postId) {
            node = node.parentElement;
        }
        var postId = node && node.dataset ? (node.dataset.postId || "") : "";
        if (!postId && el && el.dataset) {
            postId = el.dataset.postId || "";
        }
        if (window.__themeVoteDiag) {
            console.log("[ThemeVoteDiag] type=" + type + " postId=" + postId);
        }
        if (postId) {
            IThemePresenter.votePost("" + postId, Boolean(type), getThemeRenderToken());
        }
    } catch (ex) {
        logThemeRuntimeWarning("ThemeVoteDiag", "error " + ex);
    }
}

function submitThemePoll(form) {
    try {
        if (!hasThemePresenter() || typeof IThemePresenter.submitPoll !== "function") return true;
        if (!form) return true;
        if (form.classList && form.classList.contains("readonly")) {
            themeToast("Голосование недоступно");
            return false;
        }
        var checked = form.querySelector('input[type="radio"]:checked,input[type="checkbox"]:checked');
        if (!checked) {
            themeToast("Выберите вариант ответа");
            return false;
        }

        var params = [];
        var inputs = form.querySelectorAll("input[name]");
        for (var i = 0; i < inputs.length; i++) {
            var input = inputs[i];
            var type = (input.getAttribute("type") || "").toLowerCase();
            if ((type === "radio" || type === "checkbox") && !input.checked) continue;
            params.push(encodeURIComponent(input.name) + "=" + encodeURIComponent(input.value || ""));
        }
        var action = form.getAttribute("action") || "https://4pda.to/forum/index.php";
        IThemePresenter.submitPoll(action, (form.getAttribute("method") || "get").toLowerCase(), params.join("&"), getThemeRenderToken());
        return false;
    } catch (ex) {
        logThemeRuntimeWarning("submitThemePoll", ex);
        return true;
    }
}

function buildUserPostCountHtml(count) {
    var safeCount = Number(count);
    if (!safeCount || safeCount <= 0) return "";
    var accessibility = "Сообщений: " + safeCount;
    return '<span class="inf user_post_count" aria-label="' + accessibility + '">' +
        '<svg class="user_post_count_icon" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">' +
        '<path d="M21 11.5a8.4 8.4 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.4 8.4 0 0 1-3.8-.9L3 21l1.5-5.6a8.4 8.4 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.4 8.4 0 0 1 3.8-.9h.5a8.5 8.5 0 0 1 8 8v.5z"/></svg>' +
        '<span>' + safeCount + '</span></span>';
}

/** Called from Android after deferred desktop metadata merge — avoids full page reload. */
function applyUserPostCountPatch(postIdStr, userPostCount) {
    try {
        var id = String(postIdStr);
        var html = buildUserPostCountHtml(userPostCount);
        if (!html) return;
        var containers = document.querySelectorAll('[data-post-id="' + id + '"]');
        if (!containers || !containers.length) return;
        for (var i = 0; i < containers.length; i++) {
            var container = containers[i];
            var header = container.querySelector(".post_header .header_wrapper");
            if (!header) continue;
            var existing = header.querySelector(".inf.user_post_count");
            if (existing) {
                existing.outerHTML = html;
            } else {
                var holder = document.createElement("span");
                holder.innerHTML = html;
                var node = holder.firstChild;
                if (!node) continue;
                var dateNode = header.querySelector(".inf.date");
                if (dateNode && dateNode.parentNode === header) {
                    header.insertBefore(node, dateNode);
                } else {
                    header.appendChild(node);
                }
            }
        }
    } catch (ex) {
        logThemeRuntimeWarning("applyUserPostCountPatch", ex);
    }
}

/** Called from Android after successful vote — avoids full page reload. */
function applyPostRatingPatch(postIdStr, ratingText, canPlus, canMinus) {
    try {
        var id = String(postIdStr);
        var containers = document.querySelectorAll('[data-post-id="' + id + '"]');
        if (!containers || !containers.length) return;
        var n = parseThemePostRatingNumber(ratingText);
        for (var i = 0; i < containers.length; i++) {
            var container = containers[i];
            var span = container.querySelector(".post_rating");
            if (span) {
                var b = span.querySelector("b");
                if (b) b.textContent = ratingText;
                if (n === 0) {
                    span.classList.add("post_rating_hidden");
                    span.setAttribute("aria-hidden", "true");
                } else {
                    span.classList.remove("post_rating_hidden");
                    span.removeAttribute("aria-hidden");
                }
            }
            var up = container.querySelector(".btn.rep_up");
            var down = container.querySelector(".btn.rep_down");
            if (up) up.style.display = canPlus ? "" : "none";
            if (down) down.style.display = canMinus ? "" : "none";
        }
    } catch (ex) {
        logThemeRuntimeWarning("applyPostRatingPatch", ex);
    }
}

function isThemePostMediaHeavy(post) {
    if (post.dataset && post.dataset.themeMediaHeavy) {
        return post.dataset.themeMediaHeavy === "true";
    }
    var body = post.querySelector(".post_body");
    if (!body) return false;
    var images = body.querySelectorAll("img, .linked-image").length;
    var attachments = body.querySelectorAll(".ipb-attach, .attach_block").length;
    var isHeavy = images + attachments >= 2;
    if (post.dataset) post.dataset.themeMediaHeavy = isHeavy ? "true" : "false";
    return isHeavy;
}

window.ThemeDomPatch = window.ThemeDomPatch || (function () {
    function result(ok, reason, extra) {
        var payload = extra || {};
        payload.ok = !!ok;
        payload.reason = reason || "";
        return JSON.stringify(payload);
    }

    function parsePayload(payload) {
        if (!payload) return null;
        if (typeof payload === "string") {
            return JSON.parse(payload);
        }
        return payload;
    }

    function postSelector(id) {
        return '.post_container[data-post-id="' + String(id).replace(/"/g, '\\"') + '"]:not(.topic_hat_fixed):not(.topic_hat_entry)';
    }

    function firstPostFromHtml(html) {
        var holder = document.createElement("div");
        holder.innerHTML = html || "";
        return holder.querySelector(".post_container[data-post-id]:not(.topic_hat_fixed):not(.topic_hat_entry)");
    }

    function appendPostHtml(pageContainer, html) {
        var post = firstPostFromHtml(html);
        if (!post) return false;
        pageContainer.appendChild(post);
        return true;
    }

    function applyPostsPatch(payload) {
        try {
            var data = parsePayload(payload);
            if (!data) return result(false, "bad_payload");
            if (typeof PageInfo === "undefined") return result(false, "page_info_missing");
            if (Number(PageInfo.currentPage) !== Number(data.pageNumber)) return result(false, "page_mismatch");
            if (Number(PageInfo.allPagesCount) !== Number(data.allPages)) return result(false, "all_pages_mismatch");

            var pageContainer = document.querySelector('.theme_page_container[data-page-number="' + data.pageNumber + '"]');
            if (!pageContainer) return result(false, "container_missing");

            var expectedIds = data.expectedPostIds || [];
            var actualPosts = pageContainer.querySelectorAll(".post_container[data-post-id]:not(.topic_hat_entry):not(.topic_hat_fixed)");
            if (actualPosts.length !== expectedIds.length) {
                return result(false, "post_count_mismatch", {actual: actualPosts.length, expected: expectedIds.length});
            }
            for (var i = 0; i < expectedIds.length; i++) {
                var actualId = actualPosts[i].dataset ? String(actualPosts[i].dataset.postId || "") : "";
                if (actualId !== String(expectedIds[i])) {
                    return result(false, "post_order_mismatch", {index: i});
                }
            }

            var changed = data.changedPosts || [];
            for (var c = 0; c < changed.length; c++) {
                var changedPost = changed[c];
                var current = pageContainer.querySelector(postSelector(changedPost.id));
                var replacement = firstPostFromHtml(changedPost.html);
                if (!current || !replacement) return result(false, "changed_post_missing", {postId: changedPost.id});
                current.parentNode.replaceChild(replacement, current);
            }

            var added = data.addedPosts || [];
            for (var a = 0; a < added.length; a++) {
                if (!appendPostHtml(pageContainer, added[a].html)) {
                    return result(false, "added_post_invalid", {index: a});
                }
            }

            PageInfo.postsOnPageCount = Number(data.postsOnPage) || PageInfo.postsOnPageCount;
            PageInfo.currentPage = Number(data.pageNumber) || PageInfo.currentPage;
            PageInfo.allPagesCount = Number(data.allPages) || PageInfo.allPagesCount;
            if (data.restoreId && typeof setRefreshRestoreRequest === "function") {
                setRefreshRestoreRequest(data.restoreId, data.restoreMode || "", data.restoreSource || "");
            }
            if (typeof setLoadScrollY === "function" && typeof data.restoreScrollY !== "undefined") {
                setLoadScrollY(Number(data.restoreScrollY) || 0);
            }
            if (typeof setLoadAnchorPostId === "function") {
                setLoadAnchorPostId(data.restoreAnchorPostId || "");
            }
            if (typeof setLoadAnchorOffsetTop === "function") {
                setLoadAnchorOffsetTop(
                    typeof data.restoreAnchorOffsetTop === "undefined" || data.restoreAnchorOffsetTop === null
                        ? null
                        : Number(data.restoreAnchorOffsetTop)
                );
            }
            if (typeof setLoadScrollRatio === "function") {
                setLoadScrollRatio(
                    typeof data.restoreScrollRatio === "undefined" || data.restoreScrollRatio === null
                        ? null
                        : Number(data.restoreScrollRatio)
                );
            }
            if (typeof setLoadWasNearBottom === "function") {
                setLoadWasNearBottom(data.restoreWasNearBottom === true);
            }
            refreshThemeDynamicPostBlocks(pageContainer);
            transformAnchor();
            normalizeThemePageSeparators();
            scheduleVisibleThemePageLayoutChecks();
            if (data.keepBottom && typeof scrollToThemeBottomOnce === "function") {
                themeRuntimeRequestAnimationFrame(function () {
                    if (typeof restoreThemeToBottomAfterRefreshWithRetries === "function") {
                        restoreThemeToBottomAfterRefreshWithRetries();
                    } else {
                        scrollToThemeBottomOnce();
                    }
                });
            } else if (window.refreshRestoreId && typeof restoreThemeRefreshScrollAnchorWithRetries === "function") {
                themeRuntimeRequestAnimationFrame(restoreThemeRefreshScrollAnchorWithRetries);
            }
            return result(true, "patched", {changed: changed.length, added: added.length});
        } catch (ex) {
            return result(false, "exception:" + ex);
        }
    }

    return {
        applyPostsPatch: applyPostsPatch
    };
})();

function parseThemePostRatingNumber(s) {
    if (s == null || s === "") return 0;
    var t = String(s).trim().replace(/^\u2212|^\-|^–/, "-").replace(/^\+/, "");
    var v = parseInt(t, 10);
    return isNaN(v) ? 0 : v;
}


function setLoadAction(loadAction) {
    logThemeRender("setLoadAction " + loadAction);
    window.loadAction = loadAction;
    // New page-load: clear the "end-anchor already settled" latch so the very
    // first scrollToEndAnchorOrBottomWithRetries() for this load can run.
    // See the comment on `endAnchorScrollSettledAt` for the full rationale.
    endAnchorScrollSettledAt = 0;
    // R-04: a new page-load opens a new scroll-command generation so a stale
    // completion from the previous load cannot be misattributed to a new id.
    var __prevScrollGen = Number(window.__themeScrollCommandGeneration) || 0;
    window.__themeScrollCommandGeneration = __prevScrollGen + 1;
    // Carry an IN-FLIGHT scroll command across this generation bump. Device log 26_06-16-29
    // (621742/1121191/1084306/826244): a PASSIVE re-render — hat-overlay metadata preload / hybrid
    // neighbor insert that keeps the SAME anchor (`coalesce anchor=entry…` unchanged) — bumps the
    // generation while the INITIAL_ANCHOR scroll is still retrying. maybeCompleteThemeScrollCommand
    // then drops the completion as stale (`dropped stale gen exec=1 live=2`), so Kotlin never gets
    // `anchor_scroll_settled`, the reveal falls to the watchdog and the user SEES the page scroll to
    // the post instead of opening already positioned. Re-stamp the in-flight command's exec
    // generation to the new one so its completion still matches. A genuine reload re-arms via
    // executeThemeScrollCommand (which overwrites `__themeScrollCommandGenerationAtExec` with the
    // live generation), so R-04's protection against misattributing a previous page's completion is
    // preserved; and if the old anchor is no longer on the page the retry simply reports success=false.
    if (window.__themeScrollCommandId &&
        (Number(window.__themeScrollCommandGenerationAtExec) || 0) === __prevScrollGen) {
        window.__themeScrollCommandGenerationAtExec = window.__themeScrollCommandGeneration;
    }
    // S-02: a new page-load invalidates any pending late-media re-anchor.
    themeInitialAnchorReanchorName = "";
    themeInitialAnchorReanchorUntil = 0;
    if (loadAction == END_ACTION && typeof PageInfo !== "undefined") {
        PageInfo.elemToScroll = "";
        window.loadScrollY = 0;
        window.loadScrollRatio = null;
        window.loadWasNearBottom = true;
        themeInfiniteScroll.endScrollPending = true;
        themeInfiniteScroll.initialTopAutoloadSuppressedUntil = Date.now() + END_NAV_TOP_SUPPRESS_MS;
        suppressThemeInfiniteScrollFor(END_NAV_TOP_SUPPRESS_MS);
    } else if (loadAction != END_ACTION) {
        themeInfiniteScroll.endScrollPending = false;
    }
}

function setLoadScrollY(loadScrollY) {
    logThemeRender("setLoadScrollY " + loadScrollY);
    window.loadScrollY = Number(loadScrollY);
}

function setLoadAnchorUnreadTarget(isUnread) {
    window.loadAnchorUnreadTarget = isUnread === true;
    logThemeRender("setLoadAnchorUnreadTarget " + window.loadAnchorUnreadTarget);
}

function setLoadAmbiguousAllReadBottom(isAmbiguous) {
    window.loadAmbiguousAllReadBottom = isAmbiguous === true;
    logThemeRender("setLoadAmbiguousAllReadBottom " + window.loadAmbiguousAllReadBottom);
}

function setLoadOpenSessionKind(kind) {
    window.loadOpenSessionKind = kind ? String(kind) : "";
    logThemeRender("setLoadOpenSessionKind " + window.loadOpenSessionKind);
}

function setLoadAnchorPostId(postId) {
    logThemeRender("setLoadAnchorPostId " + postId);
    window.loadAnchorPostId = postId || "";
    if (window.loadAnchorPostId && window.loadAnchorPostId.length && window.loadAnchorUnreadTarget === true) {
        var anchorName = resolveThemeInitialAnchorName();
        if (anchorName) {
            armUnreadInitialAnchorScroll(anchorName);
        }
    }
}

/**
 * S-01 / R-03: Kotlin announces that it will own the initial-anchor scroll for
 * this page-load by arming a short handshake window. Called from
 * [ThemeFragmentWeb.onDomContentComplete] BEFORE `nativeEvents.onNativeDomComplete()`
 * runs the legacy DOM listener, so the DOM fallback yields deterministically
 * (instead of depending on whether the Kotlin command id happens to be set yet).
 * Passing a non-positive value, or omitting it, disarms the window.
 */
function setThemeInitialAnchorExpected(windowMs) {
    var ms = Number(windowMs);
    if (!isFinite(ms) || ms <= 0) {
        window.__themeInitialAnchorExpectedUntil = 0;
        logThemeRender("setThemeInitialAnchorExpected disarmed");
        return;
    }
    window.__themeInitialAnchorExpectedUntil = Date.now() + ms;
    logThemeRender("setThemeInitialAnchorExpected windowMs=" + ms);
}

/** True while a Kotlin INITIAL_ANCHOR command is still expected for the current page-load (S-01/R-03). */
function isThemeInitialAnchorExpected() {
    var until = Number(window.__themeInitialAnchorExpectedUntil) || 0;
    return until > 0 && Date.now() < until;
}

/** Best-effort scroll after reveal for all-read last-read resume; does not block WebView alpha. */
function scheduleSoftLoadAnchorScroll(postId) {
    if (!postId) return;
    var normalized = String(postId).replace(/^entry/i, "");
    if (!normalized.length) return;
    var anchorName = "entry" + normalized;
    logThemeRender("scheduleSoftLoadAnchorScroll " + anchorName);
    themeInfiniteScroll.initialTopAutoloadSuppressedUntil = Date.now() + 1800;
    suppressThemeInfiniteScrollFor(1800);
    var runSoftAnchorScroll = function () {
        if (!isThemeRuntimeAlive()) return;
        if (typeof scrollToElementWithRetries === "function") {
            scrollToElementWithRetries(anchorName, false);
        } else if (typeof scrollToElement === "function") {
            scrollToElement(anchorName);
        }
        clearUnreadAnchorHybridGuard("soft_anchor_done");
    };
    // Called after resetThemeRuntimeState from Kotlin; defer one frame so layout height is stable.
    if (typeof themeRuntimeRequestAnimationFrame === "function") {
        themeRuntimeRequestAnimationFrame(runSoftAnchorScroll);
    } else {
        themeRuntimeSetTimeout(runSoftAnchorScroll, 16);
    }
}

/**
 * All-read bottom-redirect resume on the last page: bottom-align the resolved post (and fall back to
 * document bottom) so the viewport lands at the END of the page, not at the top of a tall final post.
 */
function scheduleSoftLoadAnchorBottomScroll(postId) {
    if (!postId) return;
    var normalized = String(postId).replace(/^entry/i, "");
    if (!normalized.length) return;
    logThemeRender("scheduleSoftLoadAnchorBottomScroll entry" + normalized);
    themeInfiniteScroll.initialTopAutoloadSuppressedUntil = Date.now() + 1800;
    suppressThemeInfiniteScrollFor(1800);
    var runSoftAnchorBottomScroll = function () {
        if (!isThemeRuntimeAlive()) return;
        if (typeof scrollToEndAnchorOrBottomWithRetries === "function") {
            scrollToEndAnchorOrBottomWithRetries(normalized);
        } else if (typeof scrollToElementWithRetries === "function") {
            scrollToElementWithRetries("entry" + normalized, false);
        } else if (typeof scrollToElement === "function") {
            scrollToElement("entry" + normalized);
        }
        clearUnreadAnchorHybridGuard("soft_anchor_bottom_done");
    };
    if (typeof themeRuntimeRequestAnimationFrame === "function") {
        themeRuntimeRequestAnimationFrame(runSoftAnchorBottomScroll);
    } else {
        themeRuntimeSetTimeout(runSoftAnchorBottomScroll, 16);
    }
}

function setLoadAnchorOffsetTop(offsetTop) {
    if (offsetTop === null || typeof offsetTop === "undefined") {
        window.loadAnchorOffsetTop = null;
        logThemeRender("setLoadAnchorOffsetTop null");
        return;
    }
    var n = Number(offsetTop);
    window.loadAnchorOffsetTop = isFinite(n) ? n : null;
    logThemeRender("setLoadAnchorOffsetTop " + window.loadAnchorOffsetTop);
}

function setLoadScrollRatio(ratio) {
    if (ratio === null || typeof ratio === "undefined") {
        window.loadScrollRatio = null;
        logThemeRender("setLoadScrollRatio null");
        return;
    }
    var n = Number(ratio);
    window.loadScrollRatio = isFinite(n) ? Math.max(0, Math.min(1, n)) : null;
    logThemeRender("setLoadScrollRatio " + window.loadScrollRatio);
}

function setLoadWasNearBottom(wasNearBottom) {
    window.loadWasNearBottom = wasNearBottom === true || wasNearBottom === "true";
    logThemeRender("setLoadWasNearBottom " + window.loadWasNearBottom);
}

function setRefreshRestoreRequest(id, mode, source) {
    window.refreshRestoreId = id || "";
    window.refreshRestoreMode = mode || "";
    window.refreshRestoreSource = source || "";
    logRefreshScroll("setRestoreRequest id=" + window.refreshRestoreId + " mode=" + window.refreshRestoreMode + " source=" + window.refreshRestoreSource);
}

function maybeCompleteThemeScrollCommand(success, reason) {
    var commandId = window.__themeScrollCommandId;
    if (!commandId) return;
    // R-04: drop a completion whose render generation no longer matches the live
    // one — a reload between the command and its completion already invalidated
    // it, and delivering it would misattribute a stale completion to a new id.
    var execGeneration = Number(window.__themeScrollCommandGenerationAtExec) || 0;
    var liveGeneration = Number(window.__themeScrollCommandGeneration) || 0;
    if (execGeneration !== 0 && execGeneration !== liveGeneration) {
        window.__themeScrollCommandId = "";
        window.__themeScrollCommandGenerationAtExec = 0;
        if (typeof logRefreshScroll === "function") {
            logRefreshScroll("scrollCmdComplete dropped stale gen exec=" + execGeneration + " live=" + liveGeneration + " id=" + commandId);
        }
        return;
    }
    window.__themeScrollCommandId = "";
    window.__themeScrollCommandGenerationAtExec = 0;
    var reasonText = String(reason || "");
    if (reasonText.indexOf("end_") === 0 || reasonText.indexOf("bottom") >= 0) {
        themeInfiniteScroll.endScrollPending = false;
    }
    var metrics = getThemeScrollMetrics();
    var detail = reasonText +
        "|y=" + metrics.scrollY +
        "|max=" + metrics.maxScroll +
        "|lastPost=" + getLastRealThemePostIdInDom();
    if (typeof logRefreshScroll === "function") {
        logRefreshScroll("scrollCmdComplete id=" + commandId + " ok=" + (success === true || success === "true" || success === 1) + " " + detail);
    }
    if (hasThemePresenter() && typeof IThemePresenter.onScrollCommandComplete === "function") {
        IThemePresenter.onScrollCommandComplete(
            String(commandId),
            success === true || success === "true" || success === 1,
            detail
        );
    }
}

function executeThemeScrollCommand(payload) {
    var data = payload;
    if (typeof payload === "string") {
        try {
            data = JSON.parse(payload);
        } catch (ex) {
            data = null;
        }
    }
    if (!data || !data.commandId) {
        maybeCompleteThemeScrollCommand(false, "bad_payload");
        return;
    }
    // S-01 / R-03: Kotlin owns the initial-anchor scroll — the command arriving
    // disarms the DOM-fallback handshake window so the JS DOM path stays a pure
    // safety net (it only runs when no command ever arrives).
    window.__themeInitialAnchorExpectedUntil = 0;
    window.__themeScrollCommandId = data.commandId;
    // R-04: capture the live render generation so a completion that arrives after
    // a reload (which bumped the generation) is dropped instead of misattributed.
    window.__themeScrollCommandGenerationAtExec = Number(window.__themeScrollCommandGeneration) || 0;
    if (typeof logRefreshScroll === "function") {
        logRefreshScroll("execScrollCmd kind=" + data.kind + " id=" + data.commandId + " restoreId=" + (data.restoreId || "") + " restoreMode=" + (data.restoreMode || "") + " anchor=" + (data.anchorPostId || "") + " alive=" + isThemeRuntimeAlive());
    }
    switch (data.kind) {
        case "REFRESH_RESTORE":
            if (data.restoreId && typeof setRefreshRestoreRequest === "function") {
                setRefreshRestoreRequest(data.restoreId, data.restoreMode || "", window.refreshRestoreSource || "");
            }
            if (data.restoreMode === "BOTTOM" && typeof restoreThemeToBottomAfterRefreshWithRetries === "function") {
                restoreThemeToBottomAfterRefreshWithRetries();
            } else if (typeof restoreThemeRefreshScrollAnchorWithRetries === "function") {
                restoreThemeRefreshScrollAnchorWithRetries();
            } else {
                maybeCompleteThemeScrollCommand(false, "missing_restore_fn");
            }
            break;
        case "BOTTOM":
            if (typeof scrollToThemeBottomWithRetries === "function") {
                scrollToThemeBottomWithRetries();
            } else {
                themeInstantScrollToY(document.documentElement.scrollHeight);
                maybeCompleteThemeScrollCommand(true, "bottom");
            }
            break;
        case "ANCHOR":
            if (data.anchorPostId && typeof scrollToElementWithRetries === "function") {
                scrollToElementWithRetries(data.anchorPostId);
            } else {
                maybeCompleteThemeScrollCommand(false, "missing_anchor");
            }
            break;
        case "END_ANCHOR_OR_BOTTOM":
            themeInfiniteScroll.endScrollPending = true;
            themeInfiniteScroll.initialTopAutoloadSuppressedUntil = Date.now() + END_NAV_TOP_SUPPRESS_MS;
            suppressThemeInfiniteScrollFor(END_NAV_TOP_SUPPRESS_MS);
            if (typeof scrollToThemeBottomWithRetries === "function") {
                scrollToThemeBottomWithRetries(5);
            } else if (typeof scrollToEndAnchorOrBottomWithRetries === "function") {
                scrollToEndAnchorOrBottomWithRetries(data.anchorPostId);
            } else {
                themeInstantScrollToY(document.documentElement.scrollHeight);
                maybeCompleteThemeScrollCommand(true, "bottom_fallback");
            }
            break;
        case "INITIAL_ANCHOR":
            if (themeInfiniteScroll.unreadInitialAnchorPending) {
                var coalescedAnchor = resolveThemeInitialAnchorName() || themeInfiniteScroll.unreadInitialAnchor;
                logRefreshScroll("coalesce INITIAL_ANCHOR id=" + window.__themeScrollCommandId + " anchor=" + coalescedAnchor);
                if (coalescedAnchor && typeof scrollToElementWithRetries === "function") {
                    scrollToElementWithRetries(coalescedAnchor, true);
                } else if (coalescedAnchor && typeof scrollToElement === "function") {
                    scrollToElement(coalescedAnchor);
                    clearUnreadInitialAnchorScroll("initial_anchor_coalesced");
                    maybeCompleteThemeScrollCommand(true, "initial_anchor_coalesced");
                } else {
                    clearUnreadAnchorHybridGuard("initial_anchor_coalesced_missing");
                    maybeCompleteThemeScrollCommand(false, "initial_anchor_coalesced_missing");
                }
                break;
            }
            var initialAnchor = resolveThemeInitialAnchorName();
            if (initialAnchor && typeof scrollToElementWithRetries === "function") {
                if (window.loadAnchorUnreadTarget === true) {
                    armUnreadInitialAnchorScroll(initialAnchor);
                }
                scrollToElementWithRetries(initialAnchor, true);
            } else if (initialAnchor && typeof scrollToElement === "function") {
                if (window.loadAnchorUnreadTarget === true) {
                    armUnreadInitialAnchorScroll(initialAnchor);
                }
                scrollToElement(initialAnchor);
                clearUnreadInitialAnchorScroll("initial_anchor_single");
                maybeCompleteThemeScrollCommand(true, "initial_anchor");
            } else {
                maybeCompleteThemeScrollCommand(false, "missing_initial_anchor");
            }
            break;
        default:
            maybeCompleteThemeScrollCommand(false, "unsupported_kind");
    }
}

function cancelThemeAnchorScrollRetries() {
    themeAnchorScrollGeneration++;
    themeAnchorRetryPendingName = "";
}

function resolveThemeAnchorElement(name) {
    if (typeof name !== 'string' || !name.length) return null;
    var anchorData = /([^-]*)-([\d]*)-(\d+)/g.exec(name);
    if (anchorData) {
        anchorData[1] = anchorData[1].toLowerCase();
        if (anchorData[1] === "spoiler") anchorData[1] = "spoil";
        if (anchorData[1] === "hide") anchorData[1] = "hidden";
        var entry = document.querySelector('[name="entry' + anchorData[2] + '"]');
        return entry ? entry.querySelectorAll(".post-block." + anchorData[1])[Number(anchorData[3]) - 1] : null;
    }
    return document.querySelector('[name="' + name + '"]');
}

function isThemeAnchorNearViewportTop(anchorName, slackPx) {
    var anchorElem = resolveThemeAnchorElement(anchorName);
    if (!anchorElem) return false;
    var rect = anchorElem.getBoundingClientRect();
    var metrics = getThemeScrollMetrics();
    var activationOffset = Math.max(1, Math.min(48, Math.round(metrics.clientHeight * 0.06)));
    var slack = typeof slackPx === "number" ? slackPx : Math.max(96, activationOffset + 48);
    return rect.top >= -8 && rect.top <= slack;
}

function scheduleThemeScrollAttempt(scrollGeneration, delayMs, action) {
    themeRuntimeSetTimeout(function () {
        themeRuntimeRequestAnimationFrame(function () {
            if (!isThemeAnchorScrollCurrent(scrollGeneration)) return;
            action();
        });
    }, delayMs || 0);
}

function logRefreshScroll(message) {
    if (!isThemeRenderDebugEnabled()) return;
    console.log("[RefreshScroll] " + message);
}

function logThemeHistory(message) {
    if (!isThemeRenderDebugEnabled()) return;
    console.log("[ThemeHistory] " + message);
}

function getThemeScrollMetrics() {
    var scrollElement = document.scrollingElement || document.documentElement || document.body;
    var scrollY = scrollElement ? scrollElement.scrollTop : getScrollTop();
    var visualHeight = window.visualViewport ? window.visualViewport.height : 0;
    var clientHeight = window.innerHeight || visualHeight || document.documentElement.clientHeight || 0;
    var scrollHeight = Math.max(
        getThemeDocumentScrollHeight(),
        scrollElement ? scrollElement.scrollHeight : 0
    );
    var maxScroll = Math.max(0, scrollHeight - clientHeight);
    return {
        scrollY: scrollY,
        clientHeight: clientHeight,
        innerHeight: window.innerHeight || 0,
        visualHeight: visualHeight,
        elementClientHeight: scrollElement ? scrollElement.clientHeight : 0,
        scrollHeight: scrollHeight,
        elementScrollHeight: scrollElement ? scrollElement.scrollHeight : 0,
        maxScroll: maxScroll,
        ratio: maxScroll > 0 ? Math.max(0, Math.min(1, scrollY / maxScroll)) : 0
    };
}

function isThemeBottomSpacerStable() {
    if (typeof getExpectedBottomSpacerHeight !== "function") return true;
    var expected = Math.max(0, Number(getExpectedBottomSpacerHeight()) || 0);
    var actual = getThemeBottomSpacerHeight();
    return Math.abs(actual - expected) <= 1;
}

function findThemeViewportAnchorPost() {
    var metrics = getThemeScrollMetrics();
    var viewportCenter = metrics.clientHeight / 2;
    var posts = document.querySelectorAll(".post_container[data-post-id]");
    var best = null;
    var bestDistance = Number.MAX_VALUE;
    for (var i = 0; i < posts.length; i++) {
        var el = posts[i];
        if (!isRealThemePost(el)) continue;
        var rect = el.getBoundingClientRect();
        var visible = rect.bottom > 0 && rect.top < metrics.clientHeight;
        if (!visible) continue;
        var clampedTop = Math.max(0, rect.top);
        var clampedBottom = Math.min(metrics.clientHeight, rect.bottom);
        var distance = Math.abs(((clampedTop + clampedBottom) / 2) - viewportCenter);
        if (!best || distance < bestDistance) {
            best = el;
            bestDistance = distance;
        }
    }
    return best;
}

function findThemePostContainer(el) {
    while (el && el.classList && !el.classList.contains("post_container")) {
        el = el.parentElement;
    }
    return isRealThemePost(el) ? el : null;
}

function findThemeLinkElement(el) {
    while (el && el !== document && el.nodeType === 1) {
        if (el.tagName && el.tagName.toLowerCase() === "a") {
            if (el.classList && el.classList.contains("menu")) return null;
            return el;
        }
        el = el.parentElement;
    }
    return null;
}

function findRealThemePostById(postId) {
    if (!postId) return null;
    var id = String(postId).replace(/^entry/i, "");
    var posts = document.querySelectorAll('.post_container[data-post-id="' + id.replace(/"/g, '\\"') + '"]:not(.topic_hat_fixed):not(.topic_hat_entry)');
    for (var i = 0; i < posts.length; i++) {
        if (isRealThemePost(posts[i])) return posts[i];
    }
    return null;
}

function findFirstRealThemePostInDom() {
    var posts = document.querySelectorAll(".post_container[data-post-id]");
    for (var i = 0; i < posts.length; i++) {
        if (isRealThemePost(posts[i])) return posts[i];
    }
    return null;
}

function findLastRealThemePostInDom() {
    var posts = document.querySelectorAll(".post_container[data-post-id]");
    for (var i = posts.length - 1; i >= 0; i--) {
        if (isRealThemePost(posts[i])) return posts[i];
    }
    return null;
}

/**
 * STEP 4 — topmost visible real post for position-preserving top-prepend. Returns the first
 * real (non-hat) post whose top is at or below the viewport top (the post the user is currently
 * looking at the top of). Falls back to the first real post in the DOM when none is in/below
 * view (e.g. scrolled to the very top hat). Excludes topic-hat entries so a prepended hat does
 * not get pinned.
 */
function findTopmostVisibleRealThemePostForPrepend() {
    var posts = document.querySelectorAll(".post_container[data-post-id]:not(.topic_hat_entry):not(.topic_hat_fixed)");
    var viewportTop = window.pageYOffset || document.documentElement.scrollTop || 0;
    var first = null;
    for (var i = 0; i < posts.length; i++) {
        var node = posts[i];
        if (!isRealThemePost(node)) continue;
        if (first === null) first = node;
        var rect = node.getBoundingClientRect();
        // First post whose top is at/under the viewport top — the topmost visible post.
        if (rect.top >= -1) {
            return node;
        }
    }
    // Viewport scrolled past all post tops (e.g. mid-post): pin to the last post above the fold.
    return first;
}

function findLastRealThemePostOnLastLoadedPageInDom() {
    var bounds = getThemeLoadedPageBounds();
    var pageNumber = bounds.maxPage;
    if (!pageNumber) return findLastRealThemePostInDom();
    var container = document.querySelector('.theme_page_container[data-page-number="' + String(pageNumber).replace(/"/g, '\\"') + '"]');
    if (!container) return findLastRealThemePostInDom();
    var posts = container.querySelectorAll(".post_container[data-post-id]");
    for (var i = posts.length - 1; i >= 0; i--) {
        if (isRealThemePost(posts[i])) return posts[i];
    }
    return findLastRealThemePostInDom();
}

function getFirstRealThemePostIdInDom() {
    var post = findFirstRealThemePostInDom();
    return post && post.dataset ? String(post.dataset.postId || "") : "";
}

function getLastRealThemePostIdInDom() {
    var post = findLastRealThemePostOnLastLoadedPageInDom();
    return post && post.dataset ? String(post.dataset.postId || "") : "";
}

function pickHigherThemePostId(left, right) {
    var leftId = String(left || "").replace(/^entry/i, "");
    var rightId = String(right || "").replace(/^entry/i, "");
    if (!leftId.length) return rightId;
    if (!rightId.length) return leftId;
    var leftNum = parseInt(leftId, 10);
    var rightNum = parseInt(rightId, 10);
    if (!isNaN(leftNum) && !isNaN(rightNum)) {
        return leftNum >= rightNum ? leftId : rightId;
    }
    return rightId;
}

function isThemeEndNavigationActive() {
    return themeInfiniteScroll.endScrollPending || window.loadAction == END_ACTION;
}

function resolveEndScrollTargetPostId(postId) {
    var normalized = String(postId || "").replace(/^entry/i, "");
    var lastDomId = getLastRealThemePostIdInDom();
    if (!normalized.length) return lastDomId;
    if (isThemeEndNavigationActive() && lastDomId.length) {
        var maxId = pickHigherThemePostId(normalized, lastDomId);
        if (maxId !== normalized) {
            logThemeRender("[ThemeScrollDiag] endAnchorRemap maxId from=" + normalized + " to=" + maxId);
        }
        normalized = maxId;
    }
    if (!lastDomId.length || normalized === lastDomId) return normalized;
    var firstDomId = getFirstRealThemePostIdInDom();
    if (firstDomId.length && normalized === firstDomId) {
        logThemeRender("[ThemeScrollDiag] endAnchorRemap from=" + normalized + " to=" + lastDomId);
        return lastDomId;
    }
    // getlastpost #entry is often the first post on the final page; in hybrid mode the DOM may
    // start with an older page, so compare against loadAnchorPostId instead of firstDomId only.
    if (isThemeEndNavigationActive() && window.loadAnchorPostId) {
        var loadAnchor = String(window.loadAnchorPostId).replace(/^entry/i, "");
        if (loadAnchor.length && normalized === loadAnchor && normalized !== lastDomId) {
            logThemeRender("[ThemeScrollDiag] endAnchorRemap serverLoad=" + normalized + " to=" + lastDomId);
            return lastDomId;
        }
    }
    return normalized;
}

function getThemePostDisplayedDate(postId, sourceEl) {
    try {
        var post = null;
        if (sourceEl) {
            post = findThemePostContainer(sourceEl);
            if (post && post.dataset && String(post.dataset.postId || "") !== String(postId)) {
                post = null;
            }
        }
        if (!post) {
            post = findRealThemePostById(postId);
        }
        if (!post) return "";
        var datasetDate = post.dataset ? (post.dataset.displayDate || "") : "";
        if (datasetDate) return String(datasetDate).trim();
        var header = null;
        for (var i = 0; i < post.children.length; i++) {
            var child = post.children[i];
            if (child.classList && child.classList.contains("post_header")) {
                header = child;
                break;
            }
            if (child.classList && child.classList.contains("hat_content")) {
                header = child.querySelector(".post_header");
                if (header) break;
            }
        }
        var date = header ? header.querySelector(".inf.date > span") : null;
        if (!date) date = post.querySelector(".post_header .inf.date > span");
        return date ? (date.textContent || "").trim() : "";
    } catch (ex) {
        logThemeRuntimeWarning("ThemeQuote", "displayed date error " + ex);
        return "";
    }
}

function quoteFullThemePost(postId, sourceEl) {
    if (!hasThemePresenter() || typeof IThemePresenter.quoteFullPostWithDate !== "function") return;
    var displayedDate = getThemePostDisplayedDate(postId, sourceEl);
    logThemeRender("[ThemeQuote] full postId=" + postId + " displayDate=" + displayedDate);
    IThemePresenter.quoteFullPostWithDate("" + postId, displayedDate, getThemeRenderToken());
}

function replyThemePost(postId) {
    if (!hasThemePresenter() || typeof IThemePresenter.reply !== "function") return;
    IThemePresenter.reply("" + postId, getThemeRenderToken());
}

function buildThemeAnchorSnapshot(post, metrics, source) {
    var rect = post ? post.getBoundingClientRect() : null;
    var selectedTop = rect ? rect.top : null;
    var selectedDistance = rect ? Math.abs(((Math.max(0, rect.top) + Math.min(metrics.clientHeight, rect.bottom)) / 2) - (metrics.clientHeight / 2)) : null;
    return {
        postId: post && post.dataset ? (post.dataset.postId || "") : "",
        offsetTop: rect ? rect.top : null,
        selectedPostTop: selectedTop,
        selectedDistance: selectedDistance,
        scrollY: metrics.scrollY,
        clientHeight: metrics.clientHeight,
        scrollHeight: metrics.scrollHeight,
        maxScroll: metrics.maxScroll,
        ratio: metrics.ratio,
        wasNearBottom: metrics.maxScroll <= 0 || metrics.scrollY >= metrics.maxScroll - 32,
        containers: document.querySelectorAll(".theme_page_container[data-page-number]").length,
        separators: document.querySelectorAll(".theme_page_separator[data-page-number]").length,
        posts: document.querySelectorAll(".post_container[data-post-id]:not(.topic_hat_entry):not(.topic_hat_fixed)").length,
        source: source || "viewport"
    };
}

function rememberThemeLinkSourceAnchor(event) {
    try {
        var link = findThemeLinkElement(event.target);
        if (!link) return;
        var post = findThemePostContainer(link);
        if (!post) return;
        var metrics = getThemeScrollMetrics();
        var eventType = event && event.type ? event.type : "unknown";
        var data = buildThemeAnchorSnapshot(post, metrics, "link-" + eventType);
        data.href = link.href || link.getAttribute("href") || "";
        data.eventType = eventType;
        data.time = Date.now();
        window.themeLastLinkSourceAnchor = data;
        window.__themeLastClickedPostAnchor = data;
        logRefreshScroll("linkSource remember event=" + eventType + " href=" + data.href + " post=" + data.postId + " offset=" + data.offsetTop + " pageY=" + data.scrollY + " ratio=" + data.ratio);
        logThemeHistory("JS " + eventType + " captured href=" + data.href + " sourcePostId=" + data.postId + " offsetTop=" + data.offsetTop + " ratio=" + data.ratio);
        if (hasThemePresenter() && typeof IThemePresenter.rememberLinkSourceAnchor === "function") {
            IThemePresenter.rememberLinkSourceAnchor(JSON.stringify(data));
        }
    } catch (ex) {
        logRefreshScroll("linkSource remember error " + ex);
    }
}

function captureThemeLinkSourceAnchor(targetUrl) {
    try {
        var data = window.themeLastLinkSourceAnchor;
        if (!data || !data.postId) {
            logRefreshScroll("linkSource missing target=" + (targetUrl || ""));
            return "";
        }
        data.targetUrl = targetUrl || "";
        data.ageMs = Date.now() - (data.time || 0);
        if (data.ageMs > 5000) {
            logRefreshScroll("linkSource stale target=" + data.targetUrl + " post=" + data.postId + " ageMs=" + data.ageMs);
            window.themeLastLinkSourceAnchor = null;
            return "";
        }
        logRefreshScroll("linkSource capture target=" + data.targetUrl + " post=" + data.postId + " offset=" + data.offsetTop + " pageY=" + data.scrollY + " ratio=" + data.ratio + " ageMs=" + data.ageMs);
        logThemeHistory("link capture target=" + data.targetUrl + " sourcePostId=" + data.postId + " offset=" + data.offsetTop + " ratio=" + data.ratio + " ageMs=" + data.ageMs);
        window.themeLastLinkSourceAnchor = null;
        return JSON.stringify(data);
    } catch (ex) {
        logRefreshScroll("linkSource capture error " + ex);
        return "";
    }
}

function captureThemeRefreshScrollAnchor(source) {
    try {
        var metrics = getThemeScrollMetrics();
        var anchor = source === "link-click" && window.themeLastLinkSourceAnchor && window.themeLastLinkSourceAnchor.postId
            ? findRealThemePostById(window.themeLastLinkSourceAnchor.postId)
            : findThemeViewportAnchorPost();
        var data = buildThemeAnchorSnapshot(anchor, metrics, source || "viewport");
        logRefreshScroll("capture source=" + window.refreshRestoreSource + " anchorSource=" + data.source + " domY=" + data.scrollY + " max=" + data.maxScroll + " height=" + data.scrollHeight + " viewport=" + data.clientHeight + " post=" + data.postId + " selectedTop=" + data.selectedPostTop + " selectedDistance=" + data.selectedDistance + " offset=" + data.offsetTop + " ratio=" + data.ratio + " wasNearBottom=" + data.wasNearBottom + " containers=" + data.containers + " separators=" + data.separators + " posts=" + data.posts);
        return JSON.stringify(data);
    } catch (ex) {
        logRefreshScroll("capture error " + ex);
        return "";
    }
}

/**
 * INSTANT programmatic vertical scroll. This WebView ANIMATES `window.scrollTo` / `scrollIntoView`
 * even with an explicit `behavior: "auto"` — the object form is NOT enough here. Device logs
 * 26_06-18-09 and 26_06-18-31 (after the behavior:"auto" change shipped) still show a restore to y
 * ramping smoothly 0 → target over ~700ms in a textbook ease-in-out curve (dt≈8ms, accelerating then
 * decelerating dy) with the WebView already revealed — that ramp IS the "visible scroll" the user
 * reports on link navigation, back, and end-of-topic. The only reliably INSTANT positioning primitive
 * is a DIRECT `scrollTop` assignment on the scrolling element: scroll-behavior in the theme CSS is the
 * default (auto), so the assignment is never animated. Route ALL programmatic positioning through
 * these helpers so a stray `window.scrollTo` can never re-introduce the animation.
 */
function themeInstantScrollToY(top) {
    var y = Number(top);
    if (!isFinite(y) || y < 0) y = 0;
    var se = document.scrollingElement || document.documentElement || null;
    if (se) {
        try { se.scrollTop = y; } catch (ex) {}
    }
    if (document.body && document.body !== se) {
        // Quirks-mode / engines that scroll <body>: assign there too. A no-op on the non-scroller.
        try { document.body.scrollTop = y; } catch (ex) {}
    }
}

function themeInstantScrollIntoView(el, alignBottom) {
    if (!el || typeof el.getBoundingClientRect !== "function") return;
    // Compute the absolute target Y and use the instant scrollTop primitive instead of
    // `el.scrollIntoView()`, which this WebView animates (see themeInstantScrollToY).
    var rect = el.getBoundingClientRect();
    var se = document.scrollingElement || document.documentElement || null;
    var pageY = window.pageYOffset || (se ? se.scrollTop : 0) || 0;
    var top = rect.top + pageY;
    if (alignBottom) {
        var viewport = window.innerHeight || (document.documentElement ? document.documentElement.clientHeight : 0) || 0;
        top = top + rect.height - viewport;
    }
    themeInstantScrollToY(top);
}

function restoreThemeRefreshScrollAnchorOnce(scrollGeneration, reason, allowMissingAnchorFallback) {
    if (!isThemeAnchorScrollCurrent(scrollGeneration)) return false;
    var metrics = getThemeScrollMetrics();
    var targetY = null;
    var method = "none";
    var anchorId = window.loadAnchorPostId || "";
    var isExplicitBottomRestore = window.refreshRestoreMode === "BOTTOM";
    if (!isExplicitBottomRestore && window.loadWasNearBottom && anchorId && typeof resolveEndScrollTargetPostId === "function") {
        anchorId = resolveEndScrollTargetPostId(anchorId);
    }
    // STEP 3 — anchor-relative restore is PRIMARY. When the anchor post IS in the DOM, use its
    // current getBoundingClientRect().top (reflow-safe: always current even after a prepend or
    // late image/smile render) plus the saved intra-post offset. Pixel/ratio fallbacks only run
    // when the anchor is genuinely missing — a reflow-corrupted ratio can otherwise bleed in and
    // land the viewport on the wrong post.
    if (!isExplicitBottomRestore && anchorId) {
        var post = findRealThemePostById(anchorId);
        var anchor = post || document.querySelector('[name="entry' + anchorId + '"]');
        if (anchor) {
            var rect = (post || anchor).getBoundingClientRect();
            var intraOffset = Number(window.loadAnchorOffsetTop) || 0;
            targetY = metrics.scrollY + rect.top - intraOffset;
            method = "anchor-offset";
            logThemeHistory("restore found reason=" + reason + " post=" + anchorId + " method=" + method + " rectTop=" + rect.top + " targetY=" + targetY);
        } else {
            logRefreshScroll("restore anchorMissing reason=" + reason + " source=" + window.refreshRestoreSource + " post=" + anchorId + " ratio=" + window.loadScrollRatio + " savedY=" + window.loadScrollY);
            logThemeHistory("restore missing reason=" + reason + " post=" + anchorId + " source=" + window.refreshRestoreSource + " savedY=" + window.loadScrollY);
            if (window.loadAction == BACK_ACTION && !allowMissingAnchorFallback) return false;
        }
    }
    if (targetY === null && (isExplicitBottomRestore || window.loadWasNearBottom)) {
        targetY = metrics.maxScroll;
        method = "bottom";
    }
    if (targetY === null && window.loadScrollRatio !== null) {
        targetY = metrics.maxScroll * Number(window.loadScrollRatio);
        method = "ratio";
    }
    if (targetY === null) {
        targetY = Number(window.loadScrollY) || 0;
        method = targetY > 0 ? "saved-scrollY" : "pageTop fallback";
    }
    targetY = Math.max(0, Math.min(metrics.maxScroll, targetY));
    logRefreshScroll("restore " + reason + " method=" + method + " source=" + window.refreshRestoreSource + " fromY=" + metrics.scrollY + " toY=" + targetY + " max=" + metrics.maxScroll + " post=" + anchorId + " offset=" + window.loadAnchorOffsetTop + " ratio=" + window.loadScrollRatio + " wasNearBottom=" + window.loadWasNearBottom + " spacer=" + getThemeBottomSpacerHeight());
    logThemeHistory("restore final reason=" + reason + " method=" + method + " post=" + anchorId + " fromY=" + metrics.scrollY + " toY=" + targetY + " max=" + metrics.maxScroll);
    if (Math.abs(metrics.scrollY - targetY) > 2) {
        themeInstantScrollToY(targetY);
    }
    updateVisibleThemePage();
    return true;
}

function getThemeBottomSpacerHeight() {
    var spacer = document.getElementById("bottom_chrome_spacer");
    if (!spacer) return 0;
    var rect = spacer.getBoundingClientRect();
    return rect ? rect.height : spacer.offsetHeight || 0;
}

function restoreThemeRefreshScrollAnchorWithRetries() {
    if (!isThemeRuntimeAlive()) return;
    // Re-dispatch guard (device log 26_06-16-36, BACK to 1121483 #entry143876380 landing on the page
    // top 143860995). On a BACK/REFRESH restore this is invoked from BOTH the Kotlin REFRESH_RESTORE
    // command AND the per-render BACK/REFRESH branch, so repeated renders (HYBRID neighbor insert /
    // hat-overlay preload) call it again for the SAME restoreId. Each call cancelled the prior retry
    // chain, so the FINAL retry — the one that fires maybeCompleteThemeScrollCommand — never ran: the
    // command was abandoned via scrollStuckReveal and the page revealed at scrollY=0 (page top) instead
    // of the restored post. If a chain for this restoreId is already in flight and not yet completed,
    // let it finish (its own retries re-scroll as the content lays out) instead of restarting.
    var __restoreId = window.refreshRestoreId;
    if (__restoreId &&
        __restoreId === window.__activeRestoreId &&
        window.__activeRestoreCompleted === false &&
        window.refreshRestoreMode !== "BOTTOM"
    ) {
        if (typeof logRefreshScroll === "function") {
            logRefreshScroll("restoreSkipReentry id=" + __restoreId + " mode=" + window.refreshRestoreMode);
        }
        return;
    }
    cancelThemeAnchorScrollRetries();
    if (typeof applyBottomSpacer === "function") {
        applyBottomSpacer();
    }
    window.__activeRestoreId = __restoreId || "";
    window.__activeRestoreCompleted = false;
    if (window.refreshRestoreMode === "BOTTOM") {
        restoreThemeToBottomAfterRefreshWithRetries();
        return;
    }
    var scrollGeneration = themeAnchorScrollGeneration;
    // A BACK restore that targets a specific post must land ON that post. On a HYBRID page the post is
    // not in the DOM the instant the restore fires, and the page is still laying out (maxScroll == 0):
    // the old fixed retry schedule gave up at its final delay and completed with a pageTop / zero-ratio
    // fallback — device log 26_06-16-50: `scrollCmdComplete ok=true y=0 max=6768`, i.e. the page top.
    // Route it to a settle loop that keeps polling until the anchor post is actually present AND the
    // page is laid out (then positions to it and completes), bounded by a hard deadline so the reveal
    // is never blocked indefinitely. Infinite scroll is suppressed for the whole window so a top-insert
    // / native-edge cannot reset the scroll to 0 mid-restore.
    var backAnchorId = window.loadAnchorPostId || "";
    if (window.loadAction == BACK_ACTION &&
        backAnchorId.length > 0 &&
        window.refreshRestoreMode !== "BOTTOM" &&
        !window.loadWasNearBottom
    ) {
        suppressThemeInfiniteScrollFor(BACK_ANCHOR_SETTLE_DEADLINE_MS + 400);
        restoreBackAnchorUntilSettled(scrollGeneration, backAnchorId);
        return;
    }
    suppressThemeInfiniteScrollFor(window.loadWasNearBottom ? 2600 : 1800);
    var delays = window.refreshRestoreMode === "TARGET_POST"
        ? [1, 80, 180, 420]
        : window.loadWasNearBottom
        ? [1, 80, 180, 420, 900, 1400, 2200]
        : [1, 80, 180, 420, 900, 1400];
    logRefreshScroll("restoreSchedule id=" + window.refreshRestoreId + " mode=" + window.refreshRestoreMode + " source=" + window.refreshRestoreSource + " action=" + window.loadAction + " delays=" + delays.join(",") + " anchor=" + (window.loadAnchorPostId || "") + " ratio=" + window.loadScrollRatio + " savedY=" + window.loadScrollY + " bottom=" + window.loadWasNearBottom);
    for (var i = 0; i < delays.length; i++) {
        (function (ms) {
            themeRuntimeSetTimeout(function () {
                themeRuntimeRequestAnimationFrame(function () {
                    var isFinalRetry = ms === delays[delays.length - 1];
                    restoreThemeRefreshScrollAnchorOnce(scrollGeneration, (isFinalRetry ? "final+" : "retry+") + ms, isFinalRetry);
                    if (isFinalRetry) {
                        window.__activeRestoreCompleted = true;
                        maybeCompleteThemeScrollCommand(true);
                    }
                    if (window.loadWasNearBottom && ms >= 900) {
                        var metrics = getThemeScrollMetrics();
                        logRefreshScroll("final bottomCheck reason=retry+" + ms + " y=" + metrics.scrollY + " max=" + metrics.maxScroll);
                    }
                });
            }, ms);
        })(delays[i]);
    }
}

/**
 * BACK-anchor restore settle loop (device log 26_06-16-50, back to 1121483 #entry143876380 landing on
 * the page top). Polls until the target post is actually in the DOM AND the page is laid out
 * (maxScroll > 0), then positions to the post and completes the scroll command. This avoids the
 * fixed-schedule failure where the final retry fired while the HYBRID page was still empty
 * (maxScroll == 0 → anchor missing / ratio*0 → page top) and completed at y=0. Bounded by
 * [BACK_ANCHOR_SETTLE_DEADLINE_MS] so the reveal is never blocked indefinitely; cancelled implicitly
 * when the user scrolls or a new load bumps [themeAnchorScrollGeneration].
 */
function restoreBackAnchorUntilSettled(scrollGeneration, anchorId) {
    var startedAt = Date.now();
    var intervalMs = 96;
    var myRestoreId = window.__activeRestoreId;
    // Ownership is DECOUPLED from the shared [themeAnchorScrollGeneration]. Device log 26_06-17-45:
    // the settle started (restoreSchedule mode=BACK_ANCHOR_SETTLE) but NEVER completed because a HYBRID
    // neighbor insert / hat-overlay re-render bumped that generation, so `isThemeAnchorScrollCurrent`
    // bailed and the loop returned silently — the REFRESH_RESTORE command then hung until a safety
    // watchdog revealed the page top. The settle owns the scroll until ITS restore is superseded
    // (a different restoreId begins), the runtime dies, or the USER scrolls (then we yield to them).
    function settleStillOwned() {
        return isThemeRuntimeAlive() &&
            window.__activeRestoreId === myRestoreId &&
            window.__activeRestoreCompleted === false &&
            !(themeInfiniteScroll && themeInfiniteScroll.userScrolled === true);
    }
    function settleAttempt() {
        if (!settleStillOwned()) {
            // Yielded (user scroll / superseded): complete so the Kotlin command + reveal are released
            // instead of hanging until a safety watchdog fires.
            if (window.__activeRestoreId === myRestoreId && window.__activeRestoreCompleted === false) {
                window.__activeRestoreCompleted = true;
                maybeCompleteThemeScrollCommand(true);
            }
            return;
        }
        // Re-capture the LIVE generation each tick so a HYBRID re-render bump can't make the scroll
        // call see itself as "stale" and no-op.
        var liveGen = themeAnchorScrollGeneration;
        var elapsed = Date.now() - startedAt;
        var metrics = getThemeScrollMetrics();
        var post = findRealThemePostById(anchorId) ||
            document.querySelector('[name="entry' + anchorId + '"]');
        var laidOut = metrics.maxScroll > 0;
        var deadline = elapsed >= BACK_ANCHOR_SETTLE_DEADLINE_MS;
        if ((post && laidOut) || deadline) {
            var settledEarly = post && laidOut;
            var settledReason = settledEarly ? "backSettled+" + elapsed : "backDeadline+" + elapsed;
            // allowMissingAnchorFallback=true so the deadline path still positions (ratio/savedY) and
            // never leaves the page un-scrolled.
            restoreThemeRefreshScrollAnchorOnce(liveGen, settledReason, true);
            // Restore settled: release most of the infinite-scroll suppression (kept long enough only
            // to bridge the restore) so scroll-up auto-load of previous pages resumes promptly.
            if (settledEarly && themeInfiniteScroll.suppressUntil > Date.now() + 600) {
                themeInfiniteScroll.suppressUntil = Date.now() + 600;
            }
            if (typeof logRefreshScroll === "function") {
                logRefreshScroll("backAnchorSettle reason=" + settledReason + " post=" + anchorId +
                    " found=" + !!post + " maxScroll=" + metrics.maxScroll + " y=" + getThemeScrollMetrics().scrollY);
            }
            window.__activeRestoreCompleted = true;
            maybeCompleteThemeScrollCommand(true);
            return;
        }
        // Not settled yet: nudge toward the post if it exists, then keep polling.
        if (post) {
            restoreThemeRefreshScrollAnchorOnce(liveGen, "backPoll+" + elapsed, false);
        }
        themeRuntimeSetTimeout(function () {
            themeRuntimeRequestAnimationFrame(settleAttempt);
        }, intervalMs);
    }
    logRefreshScroll("restoreSchedule id=" + window.refreshRestoreId + " mode=BACK_ANCHOR_SETTLE source=" +
        window.refreshRestoreSource + " action=" + window.loadAction + " anchor=" + anchorId +
        " ratio=" + window.loadScrollRatio + " savedY=" + window.loadScrollY + " deadline=" + BACK_ANCHOR_SETTLE_DEADLINE_MS);
    themeRuntimeRequestAnimationFrame(settleAttempt);
}

function restoreThemeToBottomAfterRefreshWithRetries() {
    cancelThemeAnchorScrollRetries();
    if (!isThemeRuntimeAlive()) return;
    if (typeof applyBottomSpacer === "function") {
        applyBottomSpacer();
    }
    var scrollGeneration = themeAnchorScrollGeneration;
    suppressThemeInfiniteScrollFor(2200);
    var startedAt = Date.now();
    var lastMaxScroll = -1;
    var lastSpacer = -1;
    var stableCount = 0;
    var attempt = 0;
    var minDurationMs = 420;
    var maxDurationMs = 1800;
    var intervalMs = 160;
    var bottomTolerancePx = 2;

    function scheduleNext(delayMs) {
        themeRuntimeSetTimeout(function () {
            themeRuntimeRequestAnimationFrame(runAttempt);
        }, delayMs);
    }

    function runAttempt() {
        if (!isThemeAnchorScrollCurrent(scrollGeneration)) return;
        attempt++;
        var elapsed = Date.now() - startedAt;
        if (typeof applyBottomSpacer === "function") {
            applyBottomSpacer();
        }
        var before = getThemeScrollMetrics();
        var spacer = getThemeBottomSpacerHeight();
        var spacerReady = isThemeBottomSpacerStable();
        var targetY = Math.max(0, before.maxScroll);
        var maxStable = Math.abs(before.maxScroll - lastMaxScroll) <= 2;
        var spacerStable = Math.abs(spacer - lastSpacer) <= 1;
        if (maxStable && spacerStable) {
            stableCount++;
        } else {
            stableCount = 0;
            lastMaxScroll = before.maxScroll;
            lastSpacer = spacer;
        }

        if (Math.abs(before.scrollY - targetY) > bottomTolerancePx) {
            themeInstantScrollToY(targetY);
        }
        updateVisibleThemePage();

        themeRuntimeSetTimeout(function () {
            if (!isThemeAnchorScrollCurrent(scrollGeneration)) return;
            var after = getThemeScrollMetrics();
            var afterSpacer = getThemeBottomSpacerHeight();
            var delta = Math.max(0, after.maxScroll - after.scrollY);
            if (delta > bottomTolerancePx) {
                themeInstantScrollToY(after.maxScroll);
                after = getThemeScrollMetrics();
                delta = Math.max(0, after.maxScroll - after.scrollY);
            }
            var shouldContinue = elapsed < minDurationMs ||
                    stableCount < 2 ||
                    !spacerReady ||
                    delta > bottomTolerancePx ||
                    Math.abs(after.maxScroll - lastMaxScroll) > 2 ||
                    Math.abs(afterSpacer - lastSpacer) > 1;
            logRefreshScroll(
                "bottomRestore attempt=" + attempt +
                " elapsed=" + elapsed +
                " source=" + window.refreshRestoreSource +
                " fromY=" + before.scrollY +
                " toY=" + targetY +
                " y=" + after.scrollY +
                " max=" + after.maxScroll +
                " delta=" + delta +
                " stable=" + stableCount +
                " contentHeight=" + after.scrollHeight +
                " elementHeight=" + after.elementScrollHeight +
                " innerHeight=" + after.innerHeight +
                " viewport=" + after.clientHeight +
                " elementViewport=" + after.elementClientHeight +
                " visualViewport=" + after.visualHeight +
                " spacer=" + afterSpacer +
                " spacerReady=" + spacerReady +
                " bottomPadding=" + (typeof bottomChromePadding !== "undefined" ? bottomChromePadding : "null") +
                " messagePadding=" + (typeof messagePanelPadding !== "undefined" ? messagePanelPadding : "null") +
                " continue=" + shouldContinue
            );
            if (shouldContinue && elapsed < maxDurationMs) {
                scheduleNext(intervalMs);
            } else {
                var finalTarget = getThemeScrollMetrics().maxScroll;
                if (Math.abs(getThemeScrollMetrics().scrollY - finalTarget) > bottomTolerancePx) {
                    themeInstantScrollToY(finalTarget);
                }
                var finalMetrics = getThemeScrollMetrics();
                logRefreshScroll("bottomRestore final y=" + finalMetrics.scrollY + " max=" + finalMetrics.maxScroll + " delta=" + Math.max(0, finalMetrics.maxScroll - finalMetrics.scrollY) + " attempts=" + attempt + " contentHeight=" + finalMetrics.scrollHeight + " viewport=" + finalMetrics.clientHeight + " spacer=" + getThemeBottomSpacerHeight());
                maybeCompleteThemeScrollCommand(true);
            }
        }, 32);
    }

    scheduleNext(1);
    [700, 1200, 1800].forEach(function (ms) {
        themeRuntimeSetTimeout(function () {
            themeRuntimeRequestAnimationFrame(function () {
                if (!isThemeAnchorScrollCurrent(scrollGeneration)) return;
                var metrics = getThemeScrollMetrics();
                var delta = Math.max(0, metrics.maxScroll - metrics.scrollY);
                if (delta > bottomTolerancePx) {
                    themeInstantScrollToY(metrics.maxScroll);
                    metrics = getThemeScrollMetrics();
                    delta = Math.max(0, metrics.maxScroll - metrics.scrollY);
                }
                logRefreshScroll("bottomRestore guard+" + ms + " y=" + metrics.scrollY + " max=" + metrics.maxScroll + " delta=" + delta + " contentHeight=" + metrics.scrollHeight + " elementHeight=" + metrics.elementScrollHeight + " innerHeight=" + metrics.innerHeight + " viewport=" + metrics.clientHeight + " spacer=" + getThemeBottomSpacerHeight() + " bottomPadding=" + (typeof bottomChromePadding !== "undefined" ? bottomChromePadding : "null") + " messagePadding=" + (typeof messagePanelPadding !== "undefined" ? messagePanelPadding : "null"));
            });
        }, ms);
    });
}

bindThemeAnchorScrollCancelInputEvents();

function suppressThemeInfiniteScrollFor(ms) {
    themeInfiniteScroll.suppressUntil = Date.now() + (ms || 600);
}

function getThemeHatBottomPadding() {
    if (typeof bottomChromePadding !== "undefined" || typeof messagePanelPadding !== "undefined") {
        return Math.max(0, (Number(bottomChromePadding) || 0) + (Number(messagePanelPadding) || 0));
    }
    var cssValue = getComputedStyle(document.documentElement).getPropertyValue("--theme-bottom-chrome-padding");
    return Math.max(0, parseFloat(cssValue) || 0);
}

function updateThemeHatOverlayLayout() {
    var block = document.querySelector(".topic_hat_fixed.top_hat_overlay_host");
    if (!block) return;
    var viewportHeight = window.innerHeight || document.documentElement.clientHeight || 0;
    if (!viewportHeight) return;
    var topChrome = 0;
    if (typeof topChromePadding !== "undefined") {
        topChrome = Math.max(0, Number(topChromePadding) || 0);
    } else {
        var cssTop = getComputedStyle(document.documentElement).getPropertyValue("--theme-top-chrome-padding");
        topChrome = Math.max(0, parseFloat(cssTop) || 0);
    }
    var bottomPadding = getThemeHatBottomPadding();
    var maxHeight = Math.max(96, viewportHeight - topChrome - bottomPadding - 8);
    block.style.setProperty("--theme-hat-max-height", maxHeight + "px");
    block.style.top = "0";
    var hatContent = block.querySelector(".hat_content");
    if (hatContent) {
        hatContent.style.paddingTop = topChrome + "px";
    }
}

function openThemeHatOverlayHost(block, body) {
    if (!block || !body) return false;
    if (typeof updateThemeHatOverlayLayout === "function") {
        updateThemeHatOverlayLayout();
    }
    block.classList.remove("initial_open", "close", "theme_hat_overlay_enter", "theme_hat_overlay_preparing");
    body.classList.remove("initial_open", "close");
    block.classList.add("open", "theme_hat_overlay_preparing");
    body.classList.add("open");
    block.style.pointerEvents = "";
    block.style.display = "flex";
    block.style.top = "0";
    block.style.opacity = "0";
    document.body.classList.add("topic_hat_overlay_open");
    block.offsetHeight;
    requestAnimationFrame(function () {
        requestAnimationFrame(function () {
            block.classList.remove("theme_hat_overlay_preparing");
            block.classList.add("theme_hat_overlay_enter");
            block.style.removeProperty("opacity");
            block.style.removeProperty("transform");
            block.style.removeProperty("display");
        });
    });
    if (typeof IThemePresenter !== "undefined") {
        IThemePresenter.setHatOpen("true");
    }
    return true;
}

function closeThemeHatOverlayHost(block, body, notifyNative) {
    if (!block || !body) return false;
    block.classList.remove("initial_open", "open", "theme_hat_overlay_enter", "theme_hat_overlay_preparing");
    block.classList.add("close");
    block.style.pointerEvents = "none";
    block.style.removeProperty("opacity");
    block.style.removeProperty("transform");
    block.style.removeProperty("display");
    block.style.removeProperty("top");
    body.classList.remove("initial_open", "open");
    body.classList.add("close");
    body.style.removeProperty("padding-top");
    document.body.classList.remove("topic_hat_overlay_open");
    if (typeof updateThemeHatOverlayLayout === "function") {
        updateThemeHatOverlayLayout();
    }
    if (notifyNative !== false && typeof IThemePresenter !== "undefined") {
        IThemePresenter.setHatOpen("false");
    }
    return true;
}

function updateThemePollOverlayLayout() {
    var block = document.getElementById("theme_poll_overlay_host") || document.querySelector(".poll.poll_overlay_host");
    if (!block) return;
    var viewportHeight = window.innerHeight || document.documentElement.clientHeight || 0;
    if (!viewportHeight) return;
    var rect = block.getBoundingClientRect();
    var top = Math.max(0, rect.top);
    var bottomPadding = getThemeHatBottomPadding();
    var maxHeight = Math.max(96, viewportHeight - top - bottomPadding - 8);
    block.style.setProperty("--theme-poll-max-height", maxHeight + "px");
}

window.removeEventListener("resize", onThemeOverlayViewportChanged);
window.addEventListener("resize", onThemeOverlayViewportChanged);
window.removeEventListener("orientationchange", onThemeOverlayViewportChanged);
window.addEventListener("orientationchange", onThemeOverlayViewportChanged);

function isThemeAnchorScrollCurrent(generation) {
    return generation === themeAnchorScrollGeneration;
}

function isThemeAttachmentInOpenSpoiler(image) {
    if (typeof findParentSpoilerBlock === "function") {
        var spoiler = findParentSpoilerBlock(image);
        return !!(spoiler && spoiler.classList.contains("open"));
    }
    var node = image ? image.parentElement : null;
    while (node && node !== document.body) {
        if (node.classList && node.classList.contains("post-block") && node.classList.contains("spoil")) {
            return node.classList.contains("open");
        }
        node = node.parentElement;
    }
    return false;
}

function resolveThemeMediaImageLoading(image) {
    if (typeof findParentSpoilerBlock === "function") {
        var spoiler = findParentSpoilerBlock(image);
        if (spoiler && spoiler.classList.contains("close")) {
            return "lazy";
        }
    }
    return "eager";
}

function initThemeMediaImageStability(root) {
    if (!isThemeRuntimeAlive()) return;
    var container = root && root.querySelectorAll ? root : document;
    if (typeof promoteAttachmentImageSources === "function") {
        promoteAttachmentImageSources(container);
    }
    var images = container.querySelectorAll("body#topic .post_body img.linked-image:not([data-theme-media-ready='true']), body#topic .post_body img.attach:not([data-theme-media-ready='true'])");
    for (var i = 0; i < images.length; i++) {
        prepareThemeMediaImage(images[i]);
    }
}

function resetThemeMediaImageState(image) {
    if (!image || !image.classList) return;
    delete image.dataset.themeMediaReady;
    image.classList.remove("theme-media-pending", "theme-media-loaded", "theme-media-has-ratio");
    image.style.aspectRatio = "";
    image.removeEventListener("load", onThemeMediaImageLoad);
    image.removeEventListener("error", onThemeMediaImageError);
}

function prepareThemeMediaImage(image) {
    if (!image) return;
    if (typeof hasWorkingImageSrc === "function" && !hasWorkingImageSrc(image)) return;
    if (image.dataset.themeMediaReady === "true") return;
    image.dataset.themeMediaReady = "true";
    image.setAttribute("loading", resolveThemeMediaImageLoading(image));
    image.setAttribute("decoding", "async");
    applyThemeMediaAspectRatio(image);
    if (image.complete && image.naturalWidth > 0) {
        queueThemeMediaImageLoaded(image);
        return;
    }
    image.classList.add("theme-media-pending");
    image.addEventListener("load", onThemeMediaImageLoad, {once: true});
    image.addEventListener("error", onThemeMediaImageError, {once: true});
    scheduleThemeMediaImageLoadedRecheck(image);
}

function scheduleThemeMediaImageLoadedRecheck(image) {
    var delays = [16, 80, 240, 600, 1200, 2000, 4000];
    for (var i = 0; i < delays.length; i++) {
        (function (delay, isLast) {
            themeRuntimeSetTimeout(function () {
                if (!image || !image.classList) return;
                if (!image.classList.contains("theme-media-pending")) return;
                if (image.complete && image.naturalWidth > 0) {
                    markThemeMediaImageLoaded(image);
                    return;
                }
                if (isLast && typeof hasWorkingImageSrc === "function" && hasWorkingImageSrc(image)) {
                    markThemeMediaImageLoaded(image);
                }
            }, delay);
        })(delays[i], i === delays.length - 1);
    }
}

function resetThemeMediaImageBatch() {
    themeMediaImageLoadBatch.images.length = 0;
    themeMediaImageLoadBatch.rafPending = false;
}

function clearThemeMediaImageListeners(root) {
    try {
        var container = root && root.querySelectorAll ? root : document;
        var images = container.querySelectorAll("body#topic .post_body img[data-theme-media-ready='true']");
        for (var i = 0; i < images.length; i++) {
            images[i].removeEventListener("load", onThemeMediaImageLoad);
            images[i].removeEventListener("error", onThemeMediaImageError);
        }
    } catch (ex) {
        logThemeRuntimeWarning("ThemeMedia", "clear listeners error " + ex);
    }
}

function applyThemeMediaAspectRatio(image) {
    var width = parseFloat(image.getAttribute("width")) || image.naturalWidth || 0;
    var height = parseFloat(image.getAttribute("height")) || image.naturalHeight || 0;
    if (!width || !height || width < 2 || height < 2) return;
    image.style.aspectRatio = width + " / " + height;
    image.classList.add("theme-media-has-ratio");
}

function markThemeMediaImageLoaded(image) {
    applyThemeMediaAspectRatio(image);
    image.classList.remove("theme-media-pending");
    image.classList.add("theme-media-loaded");
    // S-02: a tall image that finished after the initial scroll may have pushed
    // the anchor below the fold; re-pin it once (bounded, no-op after user scroll).
    if (typeof maybeReanchorInitialAfterMediaLoad === "function") {
        maybeReanchorInitialAfterMediaLoad();
    }
}

function queueThemeMediaImageLoaded(image) {
    if (!image || !image.classList) return;
    themeMediaImageLoadBatch.images.push(image);
    if (themeMediaImageLoadBatch.rafPending) return;
    themeMediaImageLoadBatch.rafPending = true;
    themeRuntimeRequestAnimationFrame(function () {
        themeMediaImageLoadBatch.rafPending = false;
        var images = themeMediaImageLoadBatch.images.splice(0);
        for (var i = 0; i < images.length; i++) {
            markThemeMediaImageLoaded(images[i]);
        }
    });
}

function onThemeMediaImageLoad(event) {
    var image = event.target;
    image.removeEventListener("error", onThemeMediaImageError);
    queueThemeMediaImageLoaded(image);
}

function onThemeMediaImageError(event) {
    if (!event || !event.target || !event.target.classList) return;
    var image = event.target;
    image.removeEventListener("load", onThemeMediaImageLoad);
    image.classList.remove("theme-media-pending");
}

//Вызывается при обновлении прогресса загрузке страницы и при загрузке её ресурсов
// Оставлен как публичная точка входа для Kotlin-моста; старый scroll-corrector больше ничего не делал.
function onProgressChanged() {
}

function getScrollTop() {
    return (window.pageYOffset || document.documentElement.scrollTop) - (document.documentElement.clientTop || 0);
}
//name может быть EventObject или строкок
//name это аттрибут тега html, может быть просто якорем или entry+post_id
//Вызывается из джавы, если находится на той-же странице, и в ссылке есть entry или якорь, а также при загрузке страницы
//PageInfo.elemToScroll - переменная, заданная в шаблоне в теге script, содержит в себе якорь или entry



function scrollToElement(name, keepScrollGeneration) {
    try {
    logThemeRender("scrollToElement " + name);
    if (keepScrollGeneration !== true) {
        cancelThemeAnchorScrollRetries();
    }
    var scrollGeneration = themeAnchorScrollGeneration;
    // Явный якорь из Java (ссылка на пост, жест «Назад» по истории якорей) — всегда крутим к посту, не к loadScrollY при BACK.
    var explicitAnchor = (arguments.length > 0 && typeof name === 'string' && name.length > 0);
    if (typeof name != 'string') {
        name = PageInfo.elemToScroll;
    }
    anchorElem = resolveThemeAnchorElement(name);
    if (anchorElem) {
        //Открытие всех спойлеров
        var block = anchorElem;
        while (block && block.classList && !block.classList.contains('post_body')) {
            /*if (block.classList.contains('spoil')) {
                block.classList.remove('close');
                block.classList.add('open');
            }*/
            toggler("close", "open", block);
            block = block.parentNode;
        }
        block = anchorElem;
        while (block && block.classList && !block.classList.contains('post_container')) {
            block = block.parentNode;
        }
        if (block && block.classList && block.classList.contains("close")) {
            var button = block.querySelector(".hat_button");
            if (button) toggleButton(button, "hat_content");
        }
    } else if (!explicitAnchor && (window.loadAction == BACK_ACTION || window.loadAction == REFRESH_ACTION)) {
        anchorElem = document.documentElement;
    }
    logThemeRender("ANCHOR " + name);
    logThemeRender("loadAction " + window.loadAction);
    logThemeRender("loadScrollY " + window.loadScrollY);
    logThemeRender("loadAnchorPostId " + window.loadAnchorPostId);
    logThemeRender("loadAnchorOffsetTop " + window.loadAnchorOffsetTop);
    logThemeRender("explicitAnchor " + explicitAnchor);
    if (!explicitAnchor && (window.loadAction == BACK_ACTION || window.loadAction == REFRESH_ACTION)) {
        logThemeRender("BACK/REFRESH branch");
        // При BACK: если есть anchorPostId — скроллим к посту (устойчиво к изменению высоты контента),
        // иначе — к сохранённому scrollY.
        var backAnchorPostId = window.loadAnchorPostId;
        var hasPreciseBackSnapshot = window.loadAction == BACK_ACTION && (
            window.loadWasNearBottom ||
            (backAnchorPostId && backAnchorPostId.length > 0 && window.loadAnchorOffsetTop !== null) ||
            window.loadScrollRatio !== null
        );
        if ((window.loadAction == REFRESH_ACTION && (window.loadWasNearBottom || backAnchorPostId || window.loadScrollRatio !== null || window.loadScrollY > 0)) || hasPreciseBackSnapshot) {
            restoreThemeRefreshScrollAnchorWithRetries();
        } else if (backAnchorPostId && backAnchorPostId.length > 0) {
            var backElem = document.querySelector('[name="entry' + backAnchorPostId + '"]');
            if (backElem) {
                scheduleThemeScrollAttempt(scrollGeneration, 1, function () {
                    themeInstantScrollIntoView(backElem);
                });
            } else {
                scheduleThemeScrollAttempt(scrollGeneration, 1, function () {
                    themeInstantScrollToY(window.loadScrollY);
                });
            }
        } else {
            scheduleThemeScrollAttempt(scrollGeneration, 1, function () {
                themeInstantScrollToY(window.loadScrollY);
            });
        }
    } else if (window.loadAction == END_ACTION && !explicitAnchor) {
        logThemeRender("END branch");
        if (window.loadAnchorPostId && window.loadAnchorPostId.length && typeof scrollToEndAnchorOrBottomWithRetries === "function") {
            scrollToEndAnchorOrBottomWithRetries(window.loadAnchorPostId);
        } else if (typeof scrollToThemeBottomOnce === "function") {
            scheduleThemeScrollAttempt(scrollGeneration, 1, function () {
                scrollToThemeBottomOnce();
            });
        }
    } else if (window.loadAction == NORMAL_ACTION || explicitAnchor) {
        logThemeRender("NORMAL/EXPLICIT branch");
        var skipRedundantExplicitScroll = explicitAnchor &&
            keepScrollGeneration === true &&
            isThemeAnchorNearViewportTop(name);
        if (!skipRedundantExplicitScroll) {
            scheduleThemeScrollAttempt(scrollGeneration, 1, function () {
                doScroll(anchorElem);
            });
        }
    }
    } catch (ex) {
        logThemeRuntimeWarning("ThemeScrollGuard", "scrollToElement error " + ex);
        anchorElem = document.documentElement;
    } finally {
        sanitizeThemeInteractiveLayout();
        scheduleThemeInfiniteScrollBootstrap(80);
        scheduleVisibleThemePageLayoutChecks();
    }
}

function resolveThemeInitialAnchorName() {
    if (typeof PageInfo !== "undefined" && PageInfo.elemToScroll && PageInfo.elemToScroll.length) {
        return PageInfo.elemToScroll;
    }
    if (window.loadAnchorPostId && window.loadAnchorPostId.length) {
        var postId = String(window.loadAnchorPostId).replace(/^entry/i, "");
        return postId.length ? "entry" + postId : "";
    }
    return "";
}

var unreadInitialAnchorGuardTimer = null;

function armUnreadInitialAnchorScroll(anchorName) {
    if (!anchorName || !anchorName.length) return;
    themeInfiniteScroll.unreadInitialAnchor = anchorName;
    themeInfiniteScroll.unreadInitialAnchorPending = true;
    themeInfiniteScroll.unreadAnchorGuardStartedAt = Date.now();
    themeInfiniteScroll.initialTopAutoloadSuppressedUntil = Date.now() + 3200;
    suppressThemeInfiniteScrollFor(1800);
    logThemeRender("armUnreadInitialAnchor anchor=" + anchorName);
    if (unreadInitialAnchorGuardTimer) {
        clearTimeout(unreadInitialAnchorGuardTimer);
    }
    unreadInitialAnchorGuardTimer = themeRuntimeSetTimeout(function () {
        unreadInitialAnchorGuardTimer = null;
        if (!themeInfiniteScroll.unreadInitialAnchorPending) return;
        logThemeRender("FPDA_THEME_ANCHOR_GUARD anchor_guard_timeout anchor=" + themeInfiniteScroll.unreadInitialAnchor);
        clearUnreadAnchorHybridGuard("js_guard_timeout");
    }, 3200);
}

function clearUnreadAnchorHybridGuard(reason) {
    if (unreadInitialAnchorGuardTimer) {
        clearTimeout(unreadInitialAnchorGuardTimer);
        unreadInitialAnchorGuardTimer = null;
    }
    clearUnreadInitialAnchorScroll(reason || "guard_release");
    window.__themeScrollCommandId = "";
}

function clearUnreadInitialAnchorScroll(reason) {
    var wasPending = themeInfiniteScroll.unreadInitialAnchorPending === true;
    themeInfiniteScroll.unreadInitialAnchorPending = false;
    themeInfiniteScroll.unreadInitialAnchor = "";
    themeInfiniteScroll.unreadAnchorGuardStartedAt = 0;
    if (!wasPending && !reason) return;
    logThemeRender("clearUnreadInitialAnchor reason=" + (reason || ""));
    if (reason && String(reason).indexOf("initial_anchor") === 0) {
        logThemeRender("FPDA_THEME_ANCHOR_GUARD anchor_scroll_settled reason=" + reason);
    }
}

function maybeReanchorUnreadInitialAfterTopPrepend() {
    if (!themeInfiniteScroll.unreadInitialAnchorPending) return;
    var anchorName = themeInfiniteScroll.unreadInitialAnchor;
    if (!anchorName || !resolveThemeAnchorElement(anchorName)) return;
    var scrollGeneration = themeAnchorScrollGeneration;
    scheduleThemeScrollAttempt(scrollGeneration, 1, function () {
        if (!themeInfiniteScroll.unreadInitialAnchorPending) return;
        scrollToElement(anchorName, true);
    });
}

/**
 * S-02: arm a bounded window during which a late-loading tall image may re-pin
 * the just-settled initial anchor. The retry ladder ([1,120,400,900]) can settle
 * before a tall image finishes decoding, drifting the target below the fold; a
 * single re-anchor on the late media load corrects that. Bounded by
 * [INITIAL_ANCHOR_MEDIA_REANCHOR_WINDOW_MS] and disarmed by user scroll / new
 * page-load, so it can never loop or fight the user.
 */
function armThemeInitialAnchorMediaReanchor(name) {
    if (typeof name !== "string" || !name.length) return;
    themeInitialAnchorReanchorName = name;
    themeInitialAnchorReanchorUntil = Date.now() + INITIAL_ANCHOR_MEDIA_REANCHOR_WINDOW_MS;
}

function clearThemeInitialAnchorMediaReanchor() {
    themeInitialAnchorReanchorName = "";
    themeInitialAnchorReanchorUntil = 0;
}

/**
 * S-02: re-pin the active initial anchor after a tall image finishes loading.
 * No-op once the user has scrolled, the bounded window has elapsed, the anchor
 * is already near the viewport top, or a blocking command/unread anchor is in
 * flight (those own the scroll). Single, instant correction — never a loop.
 */
function maybeReanchorInitialAfterMediaLoad() {
    if (!themeInitialAnchorReanchorName) return;
    if (Date.now() >= themeInitialAnchorReanchorUntil) {
        clearThemeInitialAnchorMediaReanchor();
        return;
    }
    if (themeInfiniteScroll.userScrolled) {
        clearThemeInitialAnchorMediaReanchor();
        return;
    }
    // A live command / pending unread anchor owns the scroll; let it finish.
    if (window.__themeScrollCommandId || themeInfiniteScroll.unreadInitialAnchorPending) return;
    var name = themeInitialAnchorReanchorName;
    var anchor = resolveThemeAnchorElement(name);
    if (!anchor) return;
    if (isThemeAnchorNearViewportTop(name)) return;
    logThemeRender("media reanchor initial anchor=" + name);
    scrollToElement(name, true);
}

/**
 * Повторы после сдвига вёрстки (картинки, шрифты): один вызов scrollToElement часто оставляет верх страницы.
 */
function scrollToElementWithRetries(name, requireFinalRetry) {
    if (typeof name !== 'string' || !name.length) return;
    cancelThemeAnchorScrollRetries();
    themeAnchorRetryPendingName = name;
    var scrollGeneration = themeAnchorScrollGeneration;
    var completed = false;
    var successfulScrolls = 0;
    var finalDelay = SCROLL_ANCHOR_RETRY_DELAYS_MS[SCROLL_ANCHOR_RETRY_DELAYS_MS.length - 1];
    // Deadline fallback (device log 26_06-18-42, forward nav 239158 p=119890221). The retry chain
    // above is dispatched through scheduleThemeScrollAttempt, which is gated on `scrollGeneration`
    // (themeAnchorScrollGeneration). A concurrent HYBRID re-render bumps that generation, so EVERY
    // retry callback — including the final one that completes the scroll command — silently no-ops.
    // The INITIAL_ANCHOR command then never completes, the post is never positioned, and reveal is
    // stuck until the 3.2s alphaRevealSafety watchdog reveals at the page top (after which the
    // highlight code is left to position the post). This deadline is NOT generation-gated: if the
    // command is still pending when it fires, it positions the anchor (instant) and completes so reveal
    // happens promptly with the post already placed. Only armed for the blocking INITIAL_ANCHOR path.
    if (requireFinalRetry) {
        var ownedCommandId = window.__themeScrollCommandId;
        if (ownedCommandId) {
            themeRuntimeSetTimeout(function () {
                if (completed) return;
                if (window.__themeScrollCommandId !== ownedCommandId) return; // a newer command owns it now
                var el = resolveThemeAnchorElement(name);
                completed = true;
                themeAnchorRetryPendingName = "";
                clearUnreadInitialAnchorScroll("initial_anchor_deadline");
                // We verified __themeScrollCommandId still equals our id, so this IS our command — force
                // the completion past the stale-generation gate (a render-gen bump is exactly what
                // orphaned the retries; that same bump must not also drop this rescue completion).
                window.__themeScrollCommandGenerationAtExec = Number(window.__themeScrollCommandGeneration) || 0;
                if (el) {
                    doScroll(el);
                    armThemeInitialAnchorMediaReanchor(name);
                    maybeCompleteThemeScrollCommand(true, "initial_anchor_deadline");
                    scheduleThemeInfiniteScrollBootstrap(80);
                } else {
                    maybeCompleteThemeScrollCommand(false, "initial_anchor_deadline_missing");
                }
            }, finalDelay + 450);
        }
    }
    for (var i = 0; i < SCROLL_ANCHOR_RETRY_DELAYS_MS.length; i++) {
        (function (ms) {
            scheduleThemeScrollAttempt(scrollGeneration, ms, function () {
                if (completed) return;
                if (!resolveThemeAnchorElement(name)) {
                    if (ms === finalDelay) {
                        logThemeRender("[ThemeScrollDiag] anchorMissing=" + name + " retryFinal=true scrollY=" + (window.pageYOffset || 0));
                        if (requireFinalRetry) {
                            completed = true;
                            themeAnchorRetryPendingName = "";
                            clearUnreadInitialAnchorScroll("initial_anchor_missing");
                            maybeCompleteThemeScrollCommand(false, "initial_anchor_missing");
                        }
                    }
                    return;
                }
                var nearTarget = isThemeAnchorNearViewportTop(name);
                if (!(requireFinalRetry && ms !== finalDelay && nearTarget)) {
                    scrollToElement(name, true);
                }
                successfulScrolls++;
                if (requireFinalRetry && nearTarget && successfulScrolls > 0) {
                    completed = true;
                    themeAnchorRetryPendingName = "";
                    cancelThemeAnchorScrollRetries();
                    clearUnreadInitialAnchorScroll("initial_anchor_early");
                    armThemeInitialAnchorMediaReanchor(name);
                    maybeCompleteThemeScrollCommand(true, "initial_anchor");
                    return;
                }
                var shouldComplete = requireFinalRetry
                    ? ms === finalDelay
                    : (successfulScrolls >= 2 || ms >= 120);
                if (shouldComplete) {
                    completed = true;
                    themeAnchorRetryPendingName = "";
                    cancelThemeAnchorScrollRetries();
                    if (requireFinalRetry) {
                        clearUnreadInitialAnchorScroll("initial_anchor_final");
                    }
                    if (ms === finalDelay) {
                        if (successfulScrolls > 0 && requireFinalRetry) {
                            armThemeInitialAnchorMediaReanchor(name);
                        }
                        maybeCompleteThemeScrollCommand(successfulScrolls > 0, requireFinalRetry ? "initial_anchor" : "");
                        if (successfulScrolls > 0 && requireFinalRetry) {
                            scheduleThemeInfiniteScrollBootstrap(80);
                        }
                    }
                }
            });
        })(SCROLL_ANCHOR_RETRY_DELAYS_MS[i]);
    }
    if (window.__themeScrollAnchorDiag) {
        var maxMs = SCROLL_ANCHOR_RETRY_DELAYS_MS[SCROLL_ANCHOR_RETRY_DELAYS_MS.length - 1];
        themeRuntimeSetTimeout(function () {
            logScrollAnchorDiag(name);
        }, maxMs + 150);
    }
}

function isThemeScrollSettledNearBottom(minScrollY) {
    var metrics = getThemeScrollMetrics();
    var threshold = typeof minScrollY === "number" ? minScrollY : END_SCROLL_MIN_Y_THRESHOLD;
    if (metrics.maxScroll <= 0) return metrics.scrollY >= threshold;
    return metrics.scrollY >= Math.max(threshold, metrics.maxScroll - Math.max(96, themeInfiniteScroll.threshold / 2));
}

function getThemeVisibleBandReserves() {
    var topReserve = (typeof topChromePadding !== "undefined" ? Math.max(0, Number(topChromePadding) || 0) : 0);
    var bottomReserve = (typeof bottomChromePadding !== "undefined" ? Math.max(0, Number(bottomChromePadding) || 0) : 0)
        + (typeof messagePanelPadding !== "undefined" ? Math.max(0, Number(messagePanelPadding) || 0) : 0);
    return {top: topReserve, bottom: bottomReserve};
}

/**
 * Place the resolved end-anchor post inside the VISIBLE band (innerHeight minus the top toolbar and the
 * bottom tabbar/message panel that overlay the full-bleed WebView). Always aligns the post TOP to the
 * visible top so the whole post (incl. its action-bar footer) reads from the start and is never hidden
 * under the bottom navigation. The scroll is INSTANT (`behavior: "auto"`) — the
 * initial end-anchor placement on topic open must never animate. Returns
 * whether the post bottom fits inside the visible band.
 */
function scrollEndAnchorIntoVisibleBand(anchor) {
    if (!anchor || typeof anchor.getBoundingClientRect !== "function") return false;
    var reserves = getThemeVisibleBandReserves();
    var viewport = window.innerHeight || document.documentElement.clientHeight || 0;
    var visibleHeight = Math.max(0, viewport - reserves.top - reserves.bottom);
    var rect = anchor.getBoundingClientRect();
    var postTopAbs = rect.top + window.pageYOffset;
    var postHeight = rect.height;
    var maxY = Math.max(0, getThemeDocumentScrollHeight() - viewport);
    var postFits = postHeight <= visibleHeight;
    var y;
    if (postFits) {
        // Short last post: land at the actual END of the topic (absolute bottom) so "to end" lands
        // at the end and the whole post is visible.
        y = maxY;
    } else {
        // Tall last post that does NOT fit the viewport: TOP-align it (post top to the visible top)
        // so the user reads it from the beginning. Device log 26_06-17-55: the end scroll went to the
        // absolute bottom, cropping a tall last post top & bottom so only its middle showed.
        y = Math.max(0, Math.min(maxY, postTopAbs - reserves.top));
    }
    // The initial end-anchor placement on topic open must NEVER animate. This WebView animates
    // window.scrollTo even with behavior:"auto", so position via the instant scrollTop primitive.
    themeInstantScrollToY(y);
    return postFits;
}

function scrollToEndAnchorOrBottomWithRetries(postId) {
    // Latch: once an end-anchor scroll for the CURRENT page-load has settled
    // (and the user has not scrolled in the meantime), do NOT re-run the
    // scroll on a follow-up [setLoadAnchorPostId] from a follow-up render.
    // See the comment on `endAnchorScrollSettledAt` for the full rationale.
    // Without this guard, the original target is overwritten by the LATEST
    // post id from the most recent `setLoadAnchorPostId`, and the viewport
    // blinks / jumps to a different post mid-load.
    if (endAnchorScrollSettledAt > 0) {
        logThemeRender("[ThemeScrollDiag] endAnchorScrollSkipped reason=already_settled lastSettledAt=" + endAnchorScrollSettledAt + " requestedPostId=" + postId);
        return;
    }
    var targetPostId = resolveEndScrollTargetPostId(postId);
    if (!targetPostId.length) {
        if (typeof scrollToThemeBottomWithRetries === "function") scrollToThemeBottomWithRetries();
        return;
    }
    themeInfiniteScroll.endScrollPending = true;
    themeInfiniteScroll.initialTopAutoloadSuppressedUntil = Date.now() + END_NAV_TOP_SUPPRESS_MS;
    suppressThemeInfiniteScrollFor(END_NAV_TOP_SUPPRESS_MS);
    cancelThemeAnchorScrollRetries();
    var scrollGeneration = themeAnchorScrollGeneration;
    var completed = false;
    var finalDelay = SCROLL_ANCHOR_RETRY_DELAYS_MS[SCROLL_ANCHOR_RETRY_DELAYS_MS.length - 1];
    for (var i = 0; i < SCROLL_ANCHOR_RETRY_DELAYS_MS.length; i++) {
        (function (ms) {
            scheduleThemeScrollAttempt(scrollGeneration, ms, function () {
                if (completed) return;
                // Re-check the latch at every retry — by the time the final
                // retry fires, the user may already have scrolled (which
                // clears the latch) or — more commonly — another concurrent
                // setLoadAnchorPostId may have flipped the latch in a
                // race-free way via this same function's outer guard. We do
                // NOT re-check the outer guard here because the outer guard
                // only protects against NEW invocations, not retries from
                // this one already-armed run.
                var desiredPostId = resolveEndScrollTargetPostId(targetPostId);
                if (!desiredPostId.length) {
                    desiredPostId = getLastRealThemePostIdInDom();
                }
                var anchor = desiredPostId.length
                    ? (findRealThemePostById(desiredPostId) || resolveThemeAnchorElement("entry" + String(desiredPostId).replace(/^entry/i, "")))
                    : null;
                if (anchor) {
                    // Always use instant scrolling (`behavior: 'auto'`) for the
                    // initial end-anchor placement on topic open. Smooth
                    // scrolling here would produce a visible animation right
                    // when the topic is opened, which the user reads as
                    // "the app is scrolling the page on me". Programmatic
                    // initial scrolls must be instant; only user-initiated
                    // scrolls (touch / wheel / keyboard) get smooth handling
                    // — that is decided by the browser, not by us.
                    var postFitsBand = scrollEndAnchorIntoVisibleBand(anchor);
                    updateVisibleThemePage();
                    var scrolledId = anchor.dataset ? String(anchor.dataset.postId || "") : "";
                    var lastDomId = getLastRealThemePostIdInDom();
                    var settledOnLastPost = !lastDomId.length || !scrolledId.length || scrolledId === lastDomId;
                    if (!settledOnLastPost && ms < finalDelay) {
                        return;
                    }
                    if (ms >= finalDelay) {
                        completed = true;
                        cancelThemeAnchorScrollRetries();
                        themeInfiniteScroll.endScrollPending = false;
                        endAnchorScrollSettledAt = Date.now();
                        logThemeRender("[ThemeScrollDiag] endAnchorBand=" + (window.pageYOffset || 0) + " fits=" + postFitsBand + " anchor=" + desiredPostId);
                        maybeCompleteThemeScrollCommand(true, "end_anchor");
                    }
                    return;
                }
                if (ms === finalDelay) {
                    completed = true;
                    logThemeRender("[ThemeScrollDiag] endAnchorMissing=" + desiredPostId + " fallback=bottom scrollY=" + (window.pageYOffset || 0));
                    if (typeof scrollToThemeBottomWithRetries === "function") {
                        scrollToThemeBottomWithRetries();
                    } else {
                        themeInstantScrollToY(document.documentElement.scrollHeight);
                        updateVisibleThemePage();
                        themeInfiniteScroll.endScrollPending = false;
                        maybeCompleteThemeScrollCommand(true, "end_bottom_fallback");
                    }
                }
            });
        })(SCROLL_ANCHOR_RETRY_DELAYS_MS[i]);
    }
}

/** После последнего retry: найден ли элемент и положение (Android: window.__themeScrollAnchorDiag). */
function logScrollAnchorDiag(name) {
    try {
        var el = anchorElem;
        if (!el && typeof name === 'string') {
            el = resolveThemeAnchorElement(name);
        }
        var top = el ? el.getBoundingClientRect().top : null;
        logThemeRender("[ThemeScrollDiag] anchor=" + name + " found=" + !!el + " rectTop=" + top + " scrollY=" + (window.pageYOffset || 0));
    } catch (ex) {
        logThemeRuntimeWarning("ThemeScrollDiag", "error " + ex);
    }
}

function doScroll(tAnchorElem, scrollIntoElem) {
    var toScroll = scrollIntoElem || tAnchorElem;
    if (!toScroll || typeof toScroll.scrollIntoView !== "function") return;
    try {
        // preventScroll: HTMLElement.focus() scrolls the element into view by default, and this WebView
        // animates that focus-induced scroll — a second source of the visible "forced scroll". The
        // explicit instant scrollTop positioning below owns placement; focus must not move the viewport.
        toScroll.focus({preventScroll: true});
        var access_anchor = tAnchorElem && tAnchorElem.querySelector ? tAnchorElem.querySelector(".accessibility_anchor") : null;
        if (access_anchor) {
            access_anchor.focus({preventScroll: true});
        }
    } catch (ex) {
        logThemeRuntimeWarning("ThemeScroll", ex);
    }

    // Manual scroll based on the VISIBLE band only. The WebView is full-bleed: it draws under the
    // top toolbar (topChromePadding) and under the bottom tabbar/message panel (bottomReserve).
    // Align the post top to the visible top in both cases: a post that fits is shown whole (incl.
    // the action bar), a taller post is read from its beginning. The footer never hides under chrome.
    try {
        var postRect = toScroll.getBoundingClientRect();
        var postTopAbs = postRect.top + window.pageYOffset;
        var viewport = window.innerHeight;
        var topReserve = (typeof topChromePadding !== 'undefined' ? Math.max(0, Number(topChromePadding) || 0) : 0);
        var maxY = Math.max(0, document.documentElement.scrollHeight - viewport);
        var y = postTopAbs - topReserve;
        themeInstantScrollToY(Math.max(0, Math.min(maxY, y)));
    } catch (ex) {
        logThemeRuntimeWarning("ThemeScroll", ex);
        themeInstantScrollIntoView(toScroll);
    }

    // Legacy `.active` block-flash highlight REMOVED (Audit Finding H-04).
    // Adding `.active` to the scrolled-to `.post_container` triggered the
    // `.post_container.active:before { -webkit-animation: highlight 1s }` rule —
    // a full-size black `:before` overlay that tinted the WHOLE post for ~1s
    // ("the whole block flashes"). The "where I stopped" highlight is now owned
    // solely by the native `ppda_highlight_post` box-shadow ring
    // (ThemeWebController.reapplyTopicHighlight -> PPDA_applyHighlight), so the
    // legacy flash is intentionally not re-applied here. `elemToActivation`
    // stays declared/reset (destroyRuntime) for the read-before-assign safety
    // documented at its declaration; we simply never add the `active` class.
    if (elemToActivation) {
        elemToActivation.classList.remove('active');
        elemToActivation = null;
    }
}

function selectionToQuote() {
    if (!hasThemePresenter()) return;
    var selObj = window.getSelection();
    if (selObj.rangeCount === 0) {
        themeToast("Для этого действия необходимо выбрать текст сообщения");
        return;
    }
    var range = selObj.getRangeAt(0);
    var fragment = range.cloneContents();
    var div = document.createElement("div");
    div.appendChild(fragment);
    var nestedQuotes = div.querySelectorAll(".post-block.quote");
    for (var i = nestedQuotes.length - 1; i >= 0; i--) {
        var q = nestedQuotes[i];
        if (q.parentNode) q.parentNode.removeChild(q);
    }
    var nestedBq = div.querySelectorAll("blockquote");
    for (var j = nestedBq.length - 1; j >= 0; j--) {
        var b = nestedBq[j];
        if (b.parentNode) b.parentNode.removeChild(b);
    }
    var scripts = div.querySelectorAll("script,style");
    for (var k = scripts.length - 1; k >= 0; k--) {
        var sc = scripts[k];
        if (sc.parentNode) sc.parentNode.removeChild(sc);
    }
    // innerText теряет спойлеры/теги — в цитату нужен HTML, Kotlin переведёт в BBCode ([spoiler], [img]).
    var selectedText = div.innerHTML || div.textContent || "";
    if (hasBaseBridge() && typeof IBase.onActionModeComplete === "function") {
        IBase.onActionModeComplete();
    }

    var p = selObj.anchorNode.parentNode;
    while (p.classList && !p.classList.contains('post_container')) {
        p = p.parentNode;
    }
    if (typeof p === "undefined" || typeof p.dataset === "undefined") {
        themeToast("Для этого действия необходимо выбрать текст сообщения");
        return;
    }
    var postId = p.dataset.postId;
    if (selectedText != null && postId != null) {
        var displayedDate = getThemePostDisplayedDate(postId);
        logThemeRender("[ThemeQuote] selection postId=" + postId + " displayDate=" + displayedDate);
        IThemePresenter.quotePostWithDate(selectedText.trim(), "" + postId, displayedDate, getThemeRenderToken());
    } else {
        themeToast("Ошибка создания цитаты: [" + selectedText + ", " + postId + "]");
        return;
    }
}

function copySelectedText() {
    if (!hasThemePresenter()) return;
    var selectedText = window.getSelection().toString();
    if (hasBaseBridge() && typeof IBase.onActionModeComplete === "function") {
        IBase.onActionModeComplete();
    }
    if (selectedText != null && selectedText) {
        IThemePresenter.copySelectedText(selectedText);
    }
}

function shareSelectedText() {
    if (!hasThemePresenter()) return;
    var selectedText = window.getSelection().toString();
    if (hasBaseBridge() && typeof IBase.onActionModeComplete === "function") {
        IBase.onActionModeComplete();
    }
    if (selectedText != null && selectedText) {
        IThemePresenter.shareSelectedText(selectedText);
    }
}


function selectAllPostText() {
    if (!hasThemePresenter()) return;
    var selObj = window.getSelection();
    var p = selObj.anchorNode.parentNode;
    while (p.classList && !p.classList.contains('post_body')) {
        p = p.parentNode;
    }
    if (typeof p.classList === "undefined" || !p.classList.contains('post_body')) {
        themeToast("Для этого действия необходимо выбрать текст сообщения");
        return;
    }
    var rng, sel;
    if (document.createRange) {
        rng = document.createRange();
        rng.selectNode(p);
        sel = window.getSelection();
        sel.removeAllRanges();
        sel.addRange(rng);
    } else {
        rng = document.body.createTextRange();
        rng.moveToElementText(p);
        rng.select();
    }
}

function transformAnchor() {
    bumpThemeLayoutSanitizeToken();
    var anchors = [];
    var links = document.querySelectorAll(".post_container .post_body a[name][title]");
    for (var i = 0; i < links.length; i++) {
        if (links[i].innerHTML === "ˇ" && links[i].dataset.themeAnchorBound !== "true") {
            anchors.push(links[i]);
        }
    }

    for (var i = 0; i < anchors.length; i++) {
        var item = anchors[i];
        item.dataset.themeAnchorBound = "true";
        item.classList.add("anchor");
        item.innerHTML = "";
        item.addEventListener("click", function (event) {
            if (!isThemeRuntimeAlive()) return;
            var t = event.target;
            while (!t.classList.contains('post_container')) {
                t = t.parentElement;
            }
            if (hasThemePresenter() && typeof IThemePresenter.anchorDialog === "function") {
                IThemePresenter.anchorDialog(t.dataset.postId, event.target.name);
            }
        });
    }
}

function getForumBlacklistSingleLabel() {
    if (typeof PageInfo !== "undefined" &&
        PageInfo.forumBlacklist &&
        PageInfo.forumBlacklist.single) {
        return PageInfo.forumBlacklist.single;
    }
    return "Сообщение скрыто";
}

function getBlacklistedRevealedBucket() {
    var topicId = typeof PageInfo !== "undefined" && PageInfo.topicId ? String(PageInfo.topicId) : "0";
    var token = getThemeRenderToken() || "default";
    var key = topicId + ":" + token;
    if (!window.__blacklistedRevealedByTopic) {
        window.__blacklistedRevealedByTopic = {};
    }
    if (!window.__blacklistedRevealedByTopic[key]) {
        window.__blacklistedRevealedByTopic[key] = {};
    }
    return window.__blacklistedRevealedByTopic[key];
}

function updateBlacklistedPlaceholder(post, label) {
    var placeholder = post.querySelector(".blacklisted_post_placeholder");
    if (!placeholder) return;
    var text = placeholder.querySelector(".blacklisted_post_placeholder_text");
    if (text) {
        text.textContent = label;
    } else {
        placeholder.textContent = label;
    }
    placeholder.hidden = false;
    placeholder.setAttribute("aria-hidden", "false");
    placeholder.setAttribute("aria-expanded", "false");
}

function prepareBlacklistedPostStub(post) {
    post.classList.remove("blacklisted_post_group_leader", "blacklisted_post_group_member");
    post.removeAttribute("data-blacklist-group");
    updateBlacklistedPlaceholder(post, getForumBlacklistSingleLabel());
}

function prepareBlacklistedPostStubs(root) {
    if (!isThemeRuntimeAlive()) return;
    var posts = (root || document).querySelectorAll(".post_container.blacklisted_post:not(.revealed)");
    for (var i = 0; i < posts.length; i++) {
        prepareBlacklistedPostStub(posts[i]);
    }
}

function rememberBlacklistedPostRevealed(postId) {
    if (!postId) return;
    getBlacklistedRevealedBucket()[String(postId)] = true;
}

function revealBlacklistedPost(container) {
    if (!container || container.classList.contains("revealed")) return;
    container.classList.add("revealed");
    container.classList.remove("blacklisted_post_group_leader", "blacklisted_post_group_member");
    var content = container.querySelector(".blacklisted_post_content");
    var placeholder = container.querySelector(".blacklisted_post_placeholder");
    if (content) {
        content.hidden = false;
        content.setAttribute("aria-hidden", "false");
    }
    if (placeholder) {
        placeholder.hidden = true;
        placeholder.setAttribute("aria-hidden", "true");
        placeholder.setAttribute("aria-expanded", "true");
    }
    rememberBlacklistedPostRevealed(container.dataset.postId);
}

function restoreRevealedBlacklistedPosts(root) {
    var bucket = getBlacklistedRevealedBucket();
    var posts = (root || document).querySelectorAll(".post_container.blacklisted_post");
    for (var i = 0; i < posts.length; i++) {
        var postId = posts[i].dataset ? posts[i].dataset.postId : "";
        if (postId && bucket[postId]) {
            revealBlacklistedPost(posts[i]);
        }
    }
}

function findBlacklistedPostContainer(postId, triggerEl) {
    var normalizedId = String(postId || "");
    if (!normalizedId) return null;
    if (triggerEl) {
        var node = triggerEl.nodeType === 1 ? triggerEl : null;
        while (node && node !== document) {
            if (node.classList &&
                node.classList.contains("post_container") &&
                node.classList.contains("blacklisted_post") &&
                String(node.dataset ? node.dataset.postId || "" : "") === normalizedId) {
                return node;
            }
            node = node.parentElement;
        }
    }
    var selector = '.post_container.blacklisted_post[data-post-id="' + normalizedId.replace(/"/g, '\\"') + '"]';
    return document.querySelector(selector);
}

function resolveThemePostsListRoot(root) {
    if (root && root.querySelectorAll) {
        if (root.classList && root.classList.contains("posts_list")) {
            return root;
        }
        var nestedList = root.querySelector(".posts_list");
        if (nestedList) return nestedList;
    }
    return document.querySelector(".posts_list");
}

function dedupeThemePostContainers(root) {
    var list = resolveThemePostsListRoot(root);
    if (!list || !list.querySelectorAll) return;
    var posts = list.querySelectorAll(
        ".post_container[data-post-id]:not(.topic_hat_entry):not(.topic_hat_fixed)"
    );
    var postList = Array.prototype.slice.call(posts);
    var seen = {};
    for (var i = 0; i < postList.length; i++) {
        var post = postList[i];
        var postId = post.dataset ? String(post.dataset.postId || "") : "";
        if (!postId) continue;
        if (seen[postId]) {
            if (post.parentNode) post.parentNode.removeChild(post);
        } else {
            seen[postId] = true;
        }
    }
}

function dedupeBlacklistedPostStubs(root) {
    dedupeThemePostContainers(root);
}

function initBlacklistedPosts(root) {
    if (!isThemeRuntimeAlive()) return;
    dedupeBlacklistedPostStubs(root);
    prepareBlacklistedPostStubs(root);
    restoreRevealedBlacklistedPosts(root);
}

function toggleBlacklistedPost(postId, triggerEl) {
    if (!postId) return false;
    var container = findBlacklistedPostContainer(postId, triggerEl);
    if (!container || container.classList.contains("revealed")) return false;
    revealBlacklistedPost(container);
    if (typeof refreshThemeDynamicPostBlocks === "function") {
        refreshThemeDynamicPostBlocks(container);
    }
    return false;
}

nativeEvents.addEventListener(nativeEvents.DOM, transformAnchor);
nativeEvents.addEventListener(nativeEvents.DOM, bindThemeLinkSourceAnchorEvents);
nativeEvents.addEventListener(nativeEvents.DOM, function () {
    initBlacklistedPosts(document);
});
nativeEvents.addEventListener(nativeEvents.DOM, function () {
    if (!isThemeRuntimeAlive()) return;
    if (window.loadAction == REFRESH_ACTION) {
        logRefreshScroll("skip legacy scrollToElement during refresh id=" + window.refreshRestoreId + " source=" + window.refreshRestoreSource + " savedY=" + window.loadScrollY);
        return;
    }
    if (window.loadAction == END_ACTION) {
        if (typeof scrollToThemeBottomWithRetries === "function") {
            scrollToThemeBottomWithRetries(5);
        } else if (window.loadAnchorPostId && window.loadAnchorPostId.length && typeof scrollToEndAnchorOrBottomWithRetries === "function") {
            scrollToEndAnchorOrBottomWithRetries(window.loadAnchorPostId);
        } else {
            themeInstantScrollToY(document.documentElement.scrollHeight);
            maybeCompleteThemeScrollCommand(true, "end_dom_bottom");
        }
        return;
    }
    if (window.loadAction == NORMAL_ACTION) {
        maybeRunDomInitialAnchorFallback("dom_event");
        return;
    }
    scrollToElement();
});

/**
 * S-01 / R-03: FALLBACK-ONLY DOM-anchor scroll for a NORMAL load. Kotlin's
 * INITIAL_ANCHOR command is the authoritative owner; this path must only run
 * when no Kotlin command owns or is expected to own the scroll. While a Kotlin
 * command is still expected (handshake window armed by
 * [setThemeInitialAnchorExpected]) the fallback re-schedules itself for the end
 * of the window instead of racing on whether [__themeScrollCommandId] is set
 * yet. This preserves the legacy retry ladder ([scrollToElementWithRetries])
 * and the near-top / pending de-dup that the JS-facing tests pin.
 */
function maybeRunDomInitialAnchorFallback(source) {
    if (!isThemeRuntimeAlive()) return;
    if (window.loadAction != NORMAL_ACTION) return;
    // A command is already executing or an unread initial anchor is pending —
    // Kotlin owns this scroll; never run the fallback.
    if (window.__themeScrollCommandId || themeInfiniteScroll.unreadInitialAnchorPending) {
        return;
    }
    // A Kotlin command is still expected: yield and re-check after the handshake
    // window. If the command arrives it disarms the window (see
    // executeThemeScrollCommand) and the rescheduled check will bail above.
    if (isThemeInitialAnchorExpected()) {
        var remaining = Math.max(1, (Number(window.__themeInitialAnchorExpectedUntil) || 0) - Date.now());
        logThemeRender("dom initial anchor deferred source=" + source + " remainingMs=" + remaining);
        themeRuntimeSetTimeout(function () {
            maybeRunDomInitialAnchorFallback("handshake_timeout");
        }, remaining + 16);
        return;
    }
    var domInitialAnchor = resolveThemeInitialAnchorName();
    if (domInitialAnchor && typeof scrollToElementWithRetries === "function") {
        if (themeAnchorRetryPendingName === domInitialAnchor || isThemeAnchorNearViewportTop(domInitialAnchor)) {
            logThemeRender("skip duplicate dom initial anchor " + domInitialAnchor);
            return;
        }
        logThemeRender("dom initial anchor fallback source=" + source + " anchor=" + domInitialAnchor);
        scrollToElementWithRetries(domInitialAnchor, true);
    }
}
nativeEvents.addEventListener(nativeEvents.DOM, initThemeInfiniteScroll);

/**
 * Возвращает dataset.postId первого видимого на экране поста.
 * Используется при сохранении позиции скролла для точного возврата назад.
 */
function findFirstVisiblePostId() {
    var windowHeight = document.documentElement.clientHeight;
    var posts = document.querySelectorAll(".post_container[data-post-id]");
    for (var i = 0; i < posts.length; i++) {
        var el = posts[i];
        if (!isRealThemePost(el)) continue;
        var top = el.getBoundingClientRect().top;
        var bottom = top + el.offsetHeight;
        if (bottom > 0 && top < windowHeight) {
            return el.dataset.postId || "";
        }
    }
    return "";
}

function isThemeHybridScrollEnabled() {
    return typeof PageInfo !== "undefined" && PageInfo.hybridScroll !== false && PageInfo.scrollMode !== "classic";
}

function initThemeInfiniteScroll() {
    if (!isThemeRuntimeAlive()) return;
    sanitizeThemeInteractiveLayout();
    if (!isThemeHybridScrollEnabled()) {
        window.removeEventListener("scroll", onThemeInfiniteScroll);
        clearThemeInfiniteScrollBootstrapTimers();
        return;
    }
    normalizeThemePageSeparators();
    updateVisibleThemePage();
    window.removeEventListener("scroll", onThemeInfiniteScroll);
    window.addEventListener("scroll", onThemeInfiniteScroll, {passive: true});
    if (!themeInfiniteScroll.userScrolled) {
        // Keep the longer End-navigation guard from setLoadAction(); shortening it to 1400ms lets
        // bootstrap+1450 fire requestInitialTop before scrollToEndAnchorOrBottomWithRetries lands.
        // Read-topic soft anchor (hasUnreadTarget=false) must not arm the 3200ms top gate — it
        // blocked manual scroll-up on device after getlastpost/getnewpost on a read row (log 665).
        if (window.loadAnchorUnreadTarget === true && window.loadAnchorPostId && window.loadAnchorPostId.length) {
            themeInfiniteScroll.initialTopAutoloadSuppressedUntil = Math.max(
                themeInfiniteScroll.initialTopAutoloadSuppressedUntil || 0,
                Date.now() + 3200
            );
        } else if (window.loadAnchorUnreadTarget === true &&
            typeof PageInfo !== "undefined" && PageInfo.elemToScroll && PageInfo.elemToScroll.length) {
            themeInfiniteScroll.initialTopAutoloadSuppressedUntil = Math.max(
                themeInfiniteScroll.initialTopAutoloadSuppressedUntil || 0,
                Date.now() + 3200
            );
        } else if (window.loadAction == END_ACTION || themeInfiniteScroll.endScrollPending) {
            themeInfiniteScroll.initialTopAutoloadSuppressedUntil = Math.max(
                themeInfiniteScroll.initialTopAutoloadSuppressedUntil || 0,
                Date.now() + END_NAV_TOP_SUPPRESS_MS
            );
        } else {
            themeInfiniteScroll.initialTopAutoloadSuppressedUntil = Date.now() + 1400;
        }
    }
    if (window.refreshRestoreId) {
        suppressThemeInfiniteScrollFor(window.refreshRestoreMode === "BOTTOM" ? 2600 : 1800);
        themeInfiniteScroll.initialTopAutoloadSuppressedUntil = Date.now() + 2600;
        logRefreshScroll("suppress hybrid bootstrap id=" + window.refreshRestoreId + " source=" + window.refreshRestoreSource + " mode=" + window.refreshRestoreMode);
    }
    logThemeRender("initInfinite " + describeThemeRenderDom("init") + " suppressTopUntil=" + themeInfiniteScroll.initialTopAutoloadSuppressedUntil + " ambiguousAllRead=" + (window.loadAmbiguousAllReadBottom === true));
    if (isUnreadAnchorHybridBlocked()) {
        logAnchorGuardBlocked("bootstrap", "awaiting_anchor");
        return;
    }
    if (window.loadAmbiguousAllReadBottom === true) {
        logThemeRender("initInfinite skipBootstrap ambiguousAllRead");
        return;
    }
    scheduleThemeInfiniteScrollBootstrap(0);
    scheduleThemeInfiniteScrollBootstrap(180);
    scheduleThemeInfiniteScrollBootstrap(600);
    scheduleThemeInfiniteScrollBootstrap(1450);
}

function onThemeInfiniteScroll() {
    if (!isThemeRuntimeAlive()) return;
    if (!isThemeHybridScrollEnabled()) return;
    if (!window.__themeScrollCommandId && !themeInfiniteScroll.unreadInitialAnchorPending) {
        themeInfiniteScroll.userScrolled = true;
        // The viewport is scrolling and no native scroll command is driving
        // it — clear the end-anchor "already settled" latch so a follow-up
        // setLoadAnchorPostId from a follow-up render (e.g. infinite scroll
        // prepending / appending pages) can drive a fresh end-anchor scroll
        // if the user navigates away via a link in the newly-loaded
        // content.
        endAnchorScrollSettledAt = 0;
    }
    if (themeInfiniteScroll.scrollRafPending) return;
    themeInfiniteScroll.scrollRafPending = true;
    themeRuntimeRequestAnimationFrame(runThemeInfiniteScrollCheck);
}

function runThemeInfiniteScrollCheck() {
    themeInfiniteScroll.scrollRafPending = false;
    if (!isThemeRuntimeAlive()) return;
    if (!isThemeHybridScrollEnabled()) return;
    scheduleVisibleThemePageUpdate();
    var scrollTop = getScrollTop();
    var viewport = window.innerHeight || document.documentElement.clientHeight || 0;
    var height = Math.max(document.documentElement.scrollHeight, document.body ? document.body.scrollHeight : 0);
    var bounds = getThemeLoadedPageBounds();
    // Exclude the bottom chrome spacer (message-panel padding) from the underfill check: a short last
    // page that lands at the bottom must still be detected as underfilled even when the spacer inflates
    // the document height, otherwise the upward "load previous" trigger never arms on first open.
    var bottomSpacer = typeof getThemeBottomSpacerHeight === "function" ? getThemeBottomSpacerHeight() : 0;
    var contentMaxScroll = Math.max(0, height - viewport - bottomSpacer);
    var isUnderfilledInitialPage = bounds.hasPrevious &&
        scrollTop <= themeInfiniteScroll.threshold &&
        contentMaxScroll <= Math.max(24, themeInfiniteScroll.threshold);
    // The generic suppression window is set by programmatic bottom scrolls (END / jump-to-bottom) to keep
    // the BOTTOM trigger from immediately re-firing. It must NOT gate the upward trigger, which has its own
    // guard (initialTopAutoloadSuppressedUntil): otherwise opening a topic that lands on the last page keeps
    // re-extending suppressUntil via the bottom-scroll retries and leaves previous pages unreachable until
    // the topic is reopened.
    var bottomSuppressed = Date.now() < themeInfiniteScroll.suppressUntil && !isUnderfilledInitialPage;
    var hasInitialAnchorTarget = (window.loadAnchorPostId && window.loadAnchorPostId.length) ||
        (typeof PageInfo !== "undefined" && PageInfo.elemToScroll && PageInfo.elemToScroll.length);
    var anchorHybridBlocked = isUnreadAnchorHybridBlocked();
    var suppressInitialTop = !themeInfiniteScroll.userScrolled && (
        window.loadAmbiguousAllReadBottom === true ||
        anchorHybridBlocked ||
        themeInfiniteScroll.unreadInitialAnchorPending ||
        (hasInitialAnchorTarget &&
            Date.now() < themeInfiniteScroll.initialTopAutoloadSuppressedUntil &&
            scrollTop <= themeInfiniteScroll.threshold &&
            !isUnderfilledInitialPage) ||
        (window.loadAnchorUnreadTarget === true &&
            hasInitialAnchorTarget &&
            Date.now() < themeInfiniteScroll.initialTopAutoloadSuppressedUntil) ||
        (Date.now() < themeInfiniteScroll.initialTopAutoloadSuppressedUntil &&
            scrollTop <= themeInfiniteScroll.threshold &&
            !isUnderfilledInitialPage &&
            window.loadAnchorUnreadTarget === true)
    );
    var suppressInitialBottom = anchorHybridBlocked || themeInfiniteScroll.unreadInitialAnchorPending;
    var blockTopForEndNavigation = isThemeEndNavigationActive();
    if (bounds.hasPrevious && scrollTop <= themeInfiniteScroll.threshold && !themeInfiniteScroll.loadingTop && !suppressInitialTop && !blockTopForEndNavigation) {
        logThemeRender("requestInitialTop direction=top underfilled=" + isUnderfilledInitialPage + " " + describeThemeRenderDom("beforeTopRequest") + " loaded=" + bounds.minPage + ".." + bounds.maxPage + "/" + bounds.allPages);
        themeInfiniteScroll.loadingTop = true;
        if (hasThemePresenter() && typeof IThemePresenter.infiniteScroll === "function") {
            IThemePresenter.infiniteScroll("top");
        }
    } else if (anchorHybridBlocked && bounds.hasPrevious && scrollTop <= themeInfiniteScroll.threshold) {
        logAnchorGuardBlocked("top", "awaiting_anchor");
    }
    if (!bottomSuppressed && !suppressInitialBottom && bounds.hasNext && (height - (scrollTop + viewport)) <= themeInfiniteScroll.threshold && !themeInfiniteScroll.loadingBottom) {
        logThemeRender("requestBottom " + describeThemeRenderDom("beforeBottomRequest") + " loaded=" + bounds.minPage + ".." + bounds.maxPage + "/" + bounds.allPages);
        themeInfiniteScroll.loadingBottom = true;
        if (hasThemePresenter() && typeof IThemePresenter.infiniteScroll === "function") {
            IThemePresenter.infiniteScroll("bottom");
        }
    } else if (anchorHybridBlocked && bounds.hasNext && (height - (scrollTop + viewport)) <= themeInfiniteScroll.threshold) {
        logAnchorGuardBlocked("bottom", "awaiting_anchor");
    }
}

function scheduleThemeInfiniteScrollBootstrap(delayMs) {
    if (!isThemeRuntimeAlive()) return;
    if (window.loadAmbiguousAllReadBottom === true && !themeInfiniteScroll.userScrolled) {
        logThemeRender("bootstrap skip ambiguousAllRead delay=" + (delayMs || 0));
        return;
    }
    if (isUnreadAnchorHybridBlocked()) {
        logAnchorGuardBlocked("bootstrap", "awaiting_anchor");
        return;
    }
    var timer = themeRuntimeSetTimeout(function () {
        removeThemeInfiniteScrollBootstrapTimer(timer);
        if (!isThemeHybridScrollEnabled()) return;
        if (isUnreadAnchorHybridBlocked()) {
            logAnchorGuardBlocked("bootstrap", "awaiting_anchor");
            return;
        }
        logThemeRender("bootstrap+" + (delayMs || 0) + " " + describeThemeRenderDom("bootstrap"));
        onThemeInfiniteScroll();
    }, delayMs || 0);
    themeInfiniteScroll.bootstrapTimers.push(timer);
}

function removeThemeInfiniteScrollBootstrapTimer(timer) {
    var timers = themeInfiniteScroll.bootstrapTimers;
    for (var i = timers.length - 1; i >= 0; i--) {
        if (timers[i] === timer) {
            timers.splice(i, 1);
            return;
        }
    }
}

function clearThemeInfiniteScrollBootstrapTimers() {
    var timers = themeInfiniteScroll.bootstrapTimers;
    while (timers.length) {
        var timer = timers.pop();
        clearTimeout(timer);
        removeThemeRuntimeTimer(timer);
    }
}

function getThemeLoadedPageBounds() {
    var containers = document.querySelectorAll(".theme_page_container[data-page-number]");
    var minPage = typeof PageInfo !== "undefined" ? Number(PageInfo.currentPage) || 1 : 1;
    var maxPage = minPage;
    for (var i = 0; i < containers.length; i++) {
        var page = parseInt(containers[i].dataset ? containers[i].dataset.pageNumber : "", 10);
        if (isNaN(page)) continue;
        minPage = Math.min(minPage, page);
        maxPage = Math.max(maxPage, page);
    }
    var allPages = typeof PageInfo !== "undefined" ? Number(PageInfo.allPagesCount) || maxPage : maxPage;
    return {
        minPage: minPage,
        maxPage: maxPage,
        hasPrevious: minPage > 1,
        hasNext: maxPage < allPages,
        allPages: allPages
    };
}

function describeThemeRenderDom(reason) {
    if (!isThemeRenderDebugEnabled()) return "";
    var doc = document.documentElement || {};
    var body = document.body || {};
    var scrollY = getScrollTop();
    var clientHeight = window.innerHeight || doc.clientHeight || 0;
    var scrollHeight = Math.max(doc.scrollHeight || 0, body.scrollHeight || 0);
    var containers = document.querySelectorAll(".theme_page_container[data-page-number]").length;
    var posts = document.querySelectorAll(".post_container[data-post-id]:not(.topic_hat_entry):not(.topic_hat_fixed)").length;
    var separators = document.querySelectorAll(".theme_page_separator[data-page-number]").length;
    var bounds = getThemeLoadedPageBounds();
    return "reason=" + reason +
        " y=" + scrollY +
        " max=" + Math.max(0, scrollHeight - clientHeight) +
        " scrollHeight=" + scrollHeight +
        " viewport=" + clientHeight +
        " containers=" + containers +
        " posts=" + posts +
        " separators=" + separators +
        " loaded=" + bounds.minPage + ".." + bounds.maxPage + "/" + bounds.allPages;
}

function scheduleVisibleThemePageUpdate() {
    if (!themeInfiniteScroll.visiblePageRafPending) {
        themeInfiniteScroll.visiblePageRafPending = true;
        themeRuntimeRequestAnimationFrame(function () {
            themeInfiniteScroll.visiblePageRafPending = false;
            updateVisibleThemePage();
        });
    }
    if (themeInfiniteScroll.visiblePageThrottleTimer) {
        clearTimeout(themeInfiniteScroll.visiblePageThrottleTimer);
    }
    themeInfiniteScroll.visiblePageThrottleTimer = themeRuntimeSetTimeout(function () {
        themeInfiniteScroll.visiblePageThrottleTimer = null;
        updateVisibleThemePage();
    }, 140);
}

function scheduleVisibleThemePageLayoutChecks() {
    var delays = [0, 80, 240, 600];
    for (var i = 0; i < delays.length; i++) {
        (function (delay) {
            themeRuntimeSetTimeout(function () {
                themeRuntimeRequestAnimationFrame(updateVisibleThemePage);
            }, delay);
        })(delays[i]);
    }
}

function isThemePageSyncDebugEnabled() {
    return window.__themePageSyncDebug === true ||
        (typeof PageInfo !== "undefined" && PageInfo.debug === true);
}

function logThemePageSync(method, page, metrics, detail) {
    if (!isThemePageSyncDebugEnabled()) return;
    var y = metrics ? metrics.scrollY : getScrollTop();
    console.log("[ThemePageSync] method=" + method + " page=" + page + " scrollY=" + y + (detail ? " " + detail : ""));
}

function emitVisibleThemePage(page, method, metrics, detail) {
    if (!page) return;
    logThemePageSync(method || "unknown", page, metrics, detail);
    if (page === themeInfiniteScroll.lastVisiblePage) return;
    themeInfiniteScroll.lastVisiblePage = page;
    if (typeof PageInfo !== "undefined") {
        PageInfo.currentPage = page;
    }
    if (hasThemePresenter() && typeof IThemePresenter.visiblePageChanged === "function") {
        IThemePresenter.visiblePageChanged(String(page));
    }
}

function updateVisibleThemePage() {
    var containers = document.querySelectorAll(".theme_page_container[data-page-number]");
    if (!containers || containers.length === 0) return;
    var metrics = getThemeScrollMetrics();
    var viewportHeight = metrics.clientHeight || window.innerHeight || document.documentElement.clientHeight || 0;
    var activationOffset = Math.max(1, Math.min(48, Math.round(viewportHeight * 0.06)));
    var activationY = metrics.scrollY + activationOffset;
    var viewportBottomY = metrics.scrollY + viewportHeight;
    var separators = document.querySelectorAll(".theme_page_separator[data-page-number]");
    var visibleSeparator = null;
    var visibleSeparatorTop = Number.MAX_VALUE;
    var passedSeparator = null;
    var passedSeparatorTop = -Number.MAX_VALUE;
    var separatorTops = [];
    for (var s = 0; s < separators.length; s++) {
        var separator = separators[s];
        var separatorTop = getThemeElementDocumentTop(separator, metrics);
        separatorTops[s] = separatorTop;
        var separatorHeight = separator.offsetHeight || separator.getBoundingClientRect().height || 0;
        var separatorBottom = separatorTop + separatorHeight;
        if (separatorBottom > metrics.scrollY && separatorTop < viewportBottomY && separatorTop < visibleSeparatorTop) {
            visibleSeparatorTop = separatorTop;
            visibleSeparator = separator;
        }
        if (separatorTop <= activationY && separatorTop >= passedSeparatorTop) {
            passedSeparatorTop = separatorTop;
            passedSeparator = separator;
        }
    }

    var separatorSignal = visibleSeparator || passedSeparator;
    if (separatorSignal && separatorSignal.dataset) {
        var markerPage = parseThemePageNumber(separatorSignal.dataset.pageNumber);
        if (markerPage) {
            var method = visibleSeparator ? "separator-visible" : "separator";
            var markerTop = visibleSeparator ? visibleSeparatorTop : passedSeparatorTop;
            emitVisibleThemePage(markerPage, method, metrics, "activationY=" + activationY + " separatorTop=" + markerTop);
            return;
        }
    }

    var posts = document.querySelectorAll(".post_container[data-post-id]:not(.topic_hat_entry):not(.topic_hat_fixed)");
    var bestPost = null;
    var bestPostScore = Number.MAX_VALUE;
    for (var p = 0; p < posts.length; p++) {
        var post = posts[p];
        var postTop = getThemeElementDocumentTop(post, metrics);
        var postBottom = postTop + (post.offsetHeight || post.getBoundingClientRect().height || 0);
        if (postBottom <= metrics.scrollY || postTop >= viewportBottomY) continue;
        var clampedPostTop = Math.max(metrics.scrollY, postTop);
        var score = Math.abs(clampedPostTop - activationY);
        if (postTop <= activationY && postBottom > activationY) {
            bestPost = post;
            bestPostScore = -1;
            break;
        }
        if (score < bestPostScore) {
            bestPostScore = score;
            bestPost = post;
        }
    }
    if (bestPost) {
        var postPage = getThemePageNumberForElement(bestPost, separators, metrics, separatorTops);
        if (postPage) {
            emitVisibleThemePage(postPage, "post", metrics, "activationY=" + activationY);
            return;
        }
    }

    var bestContainer = null;
    var bestContainerScore = Number.MAX_VALUE;
    for (var i = 0; i < containers.length; i++) {
        var container = containers[i];
        var top = getThemeElementDocumentTop(container, metrics);
        var bottom = top + (container.offsetHeight || container.getBoundingClientRect().height || 0);
        var intersectsReadingLine = top <= activationY && bottom > activationY;
        var containerScore = intersectsReadingLine ? 0 : Math.min(Math.abs(top - activationY), Math.abs(bottom - activationY));
        if (intersectsReadingLine) {
            bestContainer = container;
            break;
        }
        if (containerScore < bestContainerScore) {
            bestContainerScore = containerScore;
            bestContainer = container;
        }
    }
    if (!bestContainer || !bestContainer.dataset) return;
    var page = parseThemePageNumber(bestContainer.dataset.pageNumber);
    emitVisibleThemePage(page, "container", metrics, "activationY=" + activationY);
}

function parseThemePageNumber(value) {
    var page = parseInt(value, 10);
    return isNaN(page) ? null : page;
}

function getThemeElementDocumentTop(element, metrics) {
    var rect = element.getBoundingClientRect();
    return (metrics ? metrics.scrollY : getScrollTop()) + rect.top;
}

function getThemePageNumberForElement(element, separators, metrics, separatorTops) {
    var node = element;
    while (node && node !== document) {
        if (node.classList && node.classList.contains("theme_page_container") && node.dataset) {
            var page = parseThemePageNumber(node.dataset.pageNumber);
            if (page) return page;
        }
        node = node.parentElement;
    }
    var elementTop = getThemeElementDocumentTop(element, metrics);
    var pageFromSeparator = null;
    var separatorTop = -Number.MAX_VALUE;
    for (var i = 0; i < separators.length; i++) {
        var separator = separators[i];
        var top = separatorTops && typeof separatorTops[i] === "number" ? separatorTops[i] : getThemeElementDocumentTop(separator, metrics);
        if (top <= elementTop && top >= separatorTop && separator.dataset) {
            var separatorPage = parseThemePageNumber(separator.dataset.pageNumber);
            if (separatorPage) {
                separatorTop = top;
                pageFromSeparator = separatorPage;
            }
        }
    }
    return pageFromSeparator;
}

function normalizeThemePageSeparators() {
    var list = document.querySelector(".posts_list");
    if (!list) return;

    var existingSeparators = list.querySelectorAll(".theme_page_separator");
    for (var i = 0; i < existingSeparators.length; i++) {
        existingSeparators[i].parentNode.removeChild(existingSeparators[i]);
    }

    var containers = list.querySelectorAll(".theme_page_container[data-page-number]");
    for (var j = 1; j < containers.length; j++) {
        var pageNumber = containers[j].dataset ? containers[j].dataset.pageNumber : "";
        if (!pageNumber) continue;

        var separator = document.createElement("div");
        separator.className = "theme_page_separator";
        separator.id = "theme_page_" + pageNumber;
        separator.setAttribute("data-page-number", pageNumber);
        separator.textContent = "Страница " + pageNumber;
        list.insertBefore(separator, containers[j]);
    }
    initBlacklistedPosts(document);
}

function refreshThemeDynamicPostBlocks(root) {
    if (typeof transformSnapbacks === "function") transformSnapbacks();
    if (typeof transformQuotes === "function") transformQuotes();
    if (typeof improveSpoilBlock === "function") improveSpoilBlock();
    if (typeof improveCodeBlock === "function") improveCodeBlock();
    if (typeof blocksOpenClose === "function") blocksOpenClose();
    if (typeof removeImgesSrc === "function") removeImgesSrc();
    if (typeof promoteAttachmentImageSources === "function") promoteAttachmentImageSources(root);
    if (typeof addIcons === "function") addIcons();
    if (typeof fixImagesSizeWithDensity === "function") fixImagesSizeWithDensity();
    initThemeMediaImageStability(root);
    initBlacklistedPosts(document);
}

function stripPrependedTopicHatFromList(hatPostId) {
    if (!isThemeRuntimeAlive()) return 0;
    var id = String(hatPostId || "");
    if (!id || id === "0") return 0;
    var removed = 0;
    var selector = '.post_container[data-post-id="' + id.replace(/"/g, '\\"') + '"]:not(.topic_hat_fixed):not(.topic_hat_entry)';
    var nodes = document.querySelectorAll(selector);
    for (var i = 0; i < nodes.length; i++) {
        var node = nodes[i];
        if (node.parentNode) {
            node.parentNode.removeChild(node);
            removed++;
        }
    }
    if (removed > 0) {
        normalizeThemePageSeparators();
        scheduleVisibleThemePageLayoutChecks();
    }
    return removed;
}

function injectTopicHatOverlayHost(overlayHostHtml, openAfterInject) {
    if (!isThemeRuntimeAlive() || !overlayHostHtml) return false;
    try {
        var body = document.body;
        if (!body) {
            themeRuntimeSetTimeout(function () {
                if (isThemeRuntimeAlive() && document.body) {
                    injectTopicHatOverlayHost(overlayHostHtml, openAfterInject);
                }
            }, 50);
            return false;
        }
        if (typeof suppressThemeInfiniteScrollFor === "function") {
            suppressThemeInfiniteScrollFor(1200);
        }
        var holder = document.createElement("div");
        holder.innerHTML = overlayHostHtml;
        var incoming = holder.querySelector(".topic_hat_fixed.top_hat_overlay_host");
        if (!incoming) return false;
        var existing = document.querySelector(".topic_hat_fixed.top_hat_overlay_host");
        if (existing && existing.parentNode) {
            existing.parentNode.replaceChild(incoming, existing);
        } else {
            var spacer = document.getElementById("theme_top_chrome_spacer");
            var insertParent = spacer && spacer.parentNode ? spacer.parentNode : body;
            if (!insertParent) {
                return false;
            }
            if (spacer && spacer.parentNode) {
                insertParent.insertBefore(incoming, spacer.nextSibling);
            } else {
                insertParent.insertBefore(incoming, insertParent.firstChild);
            }
        }
        transformAnchor();
        refreshThemeDynamicPostBlocks(incoming);
        if (openAfterInject === true) {
            if (typeof toggleThemeHatFromFixed === "function") {
                return toggleThemeHatFromFixed(true) === true;
            }
        }
        return true;
    } catch (ex) {
        logThemeRuntimeWarning("injectTopicHatOverlayHost", ex);
        return false;
    }
}

function findThemePageContainerInHtml(html) {
    if (!html) return null;
    var holder = document.createElement("div");
    holder.innerHTML = html;
    return holder.querySelector(".theme_page_container[data-page-number]");
}

function applyThemeInfinitePage(direction, html) {
    if (!isThemeRuntimeAlive()) return;
    if (isUnreadAnchorHybridBlocked()) {
        logAnchorGuardBlocked("prepend", "awaiting_anchor");
        setThemeInfiniteState(direction, "idle", "");
        return;
    }
    var list = document.querySelector(".posts_list");
    if (!list || !html) {
        setThemeInfiniteState(direction, "idle", "");
        return;
    }
    var incomingContainer = findThemePageContainerInHtml(html);
    if (incomingContainer && incomingContainer.dataset) {
        var incomingPage = incomingContainer.dataset.pageNumber;
        if (incomingPage && document.querySelector('.theme_page_container[data-page-number="' + incomingPage.replace(/"/g, '\\"') + '"]')) {
            logThemeRender("applyInfiniteSkip duplicate page=" + incomingPage + " " + describeThemeRenderDom("duplicatePage"));
            setThemeInfiniteState(direction, "idle", "");
            initBlacklistedPosts(document);
            return;
        }
    }
    logThemeRender("applyInfiniteStart direction=" + direction + " fragmentHtml=" + html.length + " " + describeThemeRenderDom("beforeApply"));
    if (direction === "top") {
        // STEP 4 — position-preserving top-prepend by element. The legacy document-height-delta
        // restore (`oldY + max(0, newHeight - oldHeight)`) drifted when late image/smile rendering
        // shifted content after the prepend. Pin to the topmost visible real post instead: its
        // getBoundingClientRect().top is current before AND after the insert (the element is stable
        // across reflow above it), so scrollBy(topAfter - topBefore) keeps it visually fixed.
        var pinnedElement = findTopmostVisibleRealThemePostForPrepend();
        var pinnedTopBefore = pinnedElement ? pinnedElement.getBoundingClientRect().top : null;
        var oldHeight = document.documentElement.scrollHeight;
        var oldY = window.pageYOffset || document.documentElement.scrollTop || 0;
        var topState = document.getElementById("theme_infinite_top");
        var tempTop = document.createElement("div");
        tempTop.innerHTML = html;
        while (tempTop.firstChild) {
            list.insertBefore(tempTop.firstChild, topState ? topState.nextSibling : list.firstChild);
        }
        normalizeThemePageSeparators();
        if (typeof PageInfo !== "undefined" && PageInfo.topicHatPostId) {
            stripPrependedTopicHatFromList(PageInfo.topicHatPostId);
        }
        themeRuntimeRequestAnimationFrame(function () {
            // STEP 4: prefer element-relative pinning. Fall back to the height-delta formula only
            // when no pinnable post was captured (e.g. hat-only first page), preserving old behavior.
            if (pinnedElement && pinnedTopBefore !== null) {
                var pinnedTopAfter = pinnedElement.getBoundingClientRect().top;
                var delta = pinnedTopAfter - pinnedTopBefore;
                if (delta !== 0) {
                    var se = document.scrollingElement || document.documentElement || null;
                    var curY = window.pageYOffset || (se ? se.scrollTop : 0) || 0;
                    themeInstantScrollToY(curY + delta);
                }
            } else {
                var newHeight = document.documentElement.scrollHeight;
                themeInstantScrollToY(oldY + Math.max(0, newHeight - oldHeight));
            }
            maybeReanchorUnreadInitialAfterTopPrepend();
            themeInfiniteScroll.loadingTop = false;
            logThemeRender("applyInfiniteEnd direction=top oldHeight=" + oldHeight + " pinned=" + (pinnedElement ? pinnedElement.getAttribute("data-post-id") : "none") + " " + describeThemeRenderDom("afterApplyTop"));
            scheduleVisibleThemePageLayoutChecks();
            if (!themeInfiniteScroll.unreadInitialAnchorPending) {
                scheduleThemeInfiniteScrollBootstrap(80);
            }
        });
    } else {
        var bottomState = document.getElementById("theme_infinite_bottom");
        var tempBottom = document.createElement("div");
        tempBottom.innerHTML = html;
        while (tempBottom.firstChild) {
            list.insertBefore(tempBottom.firstChild, bottomState || null);
        }
        normalizeThemePageSeparators();
        if (typeof PageInfo !== "undefined" && PageInfo.topicHatPostId) {
            stripPrependedTopicHatFromList(PageInfo.topicHatPostId);
        }
        themeInfiniteScroll.loadingBottom = false;
        logThemeRender("applyInfiniteEnd direction=bottom " + describeThemeRenderDom("afterApplyBottom"));
        scheduleVisibleThemePageLayoutChecks();
        scheduleThemeInfiniteScrollBootstrap(80);
    }
    transformAnchor();
    dedupeThemePostContainers(document);
    refreshThemeDynamicPostBlocks();
    initBlacklistedPosts(document);
}

function setThemeInfiniteState(direction, state, message) {
    if (!isThemeRuntimeAlive()) return;
    var isTop = direction === "top";
    var holder = document.getElementById(isTop ? "theme_infinite_top" : "theme_infinite_bottom");
    if (!holder) return;
    if (state === "loading") {
        holder.style.display = "block";
        holder.textContent = "Загрузка...";
        holder.onclick = null;
        if (isTop) themeInfiniteScroll.loadingTop = true;
        else themeInfiniteScroll.loadingBottom = true;
    } else if (state === "error") {
        holder.style.display = "block";
        holder.textContent = message || "Ошибка загрузки страницы. Нажмите, чтобы повторить.";
        holder.onclick = function () {
            if (hasThemePresenter() && typeof IThemePresenter.infiniteRetry === "function") {
                IThemePresenter.infiniteRetry(direction);
            }
        };
        if (isTop) themeInfiniteScroll.loadingTop = false;
        else themeInfiniteScroll.loadingBottom = false;
    } else {
        holder.style.display = "none";
        holder.textContent = "";
        holder.onclick = null;
        if (isTop) themeInfiniteScroll.loadingTop = false;
        else themeInfiniteScroll.loadingBottom = false;
        scheduleThemeInfiniteScrollBootstrap(80);
    }
}

function scrollToThemePage(pageNumber) {
    var page = Number(pageNumber);
    if (!page || isNaN(page)) {
        return false;
    }
    var target = document.querySelector('.theme_page_separator[data-page-number="' + page + '"]') ||
        document.querySelector('.theme_page_container[data-page-number="' + page + '"]');
    if (!target) {
        return false;
    }
    themeInstantScrollIntoView(target);
    updateVisibleThemePage();
    return true;
}

function scrollToThemePageAndBottom(pageNumber) {
    var page = Number(pageNumber);
    if (!page || isNaN(page)) {
        maybeCompleteThemeScrollCommand(false, "bad_page_number");
        return;
    }
    var hasPageInDom = !!document.querySelector('.theme_page_separator[data-page-number="' + page + '"]') ||
        !!document.querySelector('.theme_page_container[data-page-number="' + page + '"]');
    if (!hasPageInDom) {
        maybeCompleteThemeScrollCommand(false, "page_not_in_dom");
        return;
    }
    if (!scrollToThemePage(page)) {
        maybeCompleteThemeScrollCommand(false, "page_scroll_failed");
        return;
    }
    if (typeof scrollToThemeBottomWithRetries === "function") {
        scrollToThemeBottomWithRetries();
    } else {
        themeInstantScrollToY(document.documentElement.scrollHeight);
        maybeCompleteThemeScrollCommand(true, "bottom_fallback");
    }
}

function getThemeDocumentScrollHeight() {
    return Math.max(
        document.documentElement ? document.documentElement.scrollHeight : 0,
        document.body ? document.body.scrollHeight : 0
    );
}

function scrollToThemeBottomOnce() {
    suppressThemeInfiniteScrollFor(1800);
    var viewport = window.innerHeight || document.documentElement.clientHeight || 0;
    var targetY = Math.max(0, getThemeDocumentScrollHeight() - viewport);
    themeInstantScrollToY(targetY);
    updateVisibleThemePage();
}

/**
 * End placement that reads the LAST post correctly: a short last post lands at the absolute topic
 * bottom (the end), a TALL last post that does not fit the viewport is TOP-aligned so the user reads
 * it from the start instead of seeing only its middle at the absolute bottom (device log 26_06-17-55).
 * Falls back to the plain absolute-bottom scroll when no last post is resolvable.
 */
function scrollThemeEndIntoBandOnce() {
    suppressThemeInfiniteScrollFor(1800);
    var lastId = getLastRealThemePostIdInDom();
    var lastPost = lastId.length ? findRealThemePostById(lastId) : null;
    if (lastPost && typeof scrollEndAnchorIntoVisibleBand === "function") {
        scrollEndAnchorIntoVisibleBand(lastPost);
        updateVisibleThemePage();
        return;
    }
    scrollToThemeBottomOnce();
}

/** True once the END scroll has landed the last post (top-aligned tall, or near the bottom). */
function isThemeEndSettled() {
    if (isThemeScrollSettledNearBottom(96)) return true;
    var lastId = getLastRealThemePostIdInDom();
    var lastPost = lastId.length ? findRealThemePostById(lastId) : null;
    if (!lastPost) return false;
    var reserves = getThemeVisibleBandReserves();
    var rect = lastPost.getBoundingClientRect();
    // The last post's top is at (or just below) the visible top band — i.e. top-aligned.
    return rect.top >= reserves.top - 8 && rect.top <= reserves.top + 64;
}

function scrollToThemeBottomWithRetries(maxRetries) {
    cancelThemeAnchorScrollRetries();
    themeInfiniteScroll.endScrollPending = true;
    themeInfiniteScroll.initialTopAutoloadSuppressedUntil = Date.now() + END_NAV_TOP_SUPPRESS_MS;
    suppressThemeInfiniteScrollFor(END_NAV_TOP_SUPPRESS_MS);
    var retryCount = typeof maxRetries === "number" && maxRetries > 0
        ? Math.min(maxRetries, SCROLL_BOTTOM_RETRY_DELAYS_MS.length)
        : SCROLL_BOTTOM_RETRY_DELAYS_MS.length;
    for (var i = 0; i < retryCount; i++) {
        (function (ms) {
            themeRuntimeSetTimeout(function () {
                scrollThemeEndIntoBandOnce();
            }, ms);
        })(SCROLL_BOTTOM_RETRY_DELAYS_MS[i]);
    }
    var lastBottomRetryMs = SCROLL_BOTTOM_RETRY_DELAYS_MS[retryCount - 1];
    themeRuntimeSetTimeout(function () {
        if (!isThemeEndSettled()) {
            scrollThemeEndIntoBandOnce();
        }
        themeInfiniteScroll.endScrollPending = false;
        // A TALL last post is top-aligned (not near the bottom) yet is correctly placed, so accept
        // either the near-bottom OR the top-aligned-last-post settle as success.
        maybeCompleteThemeScrollCommand(isThemeEndSettled(), "bottom");
        scheduleThemeInfiniteScrollBootstrap(0);
    }, lastBottomRetryMs + 80);
}

function resetThemeRuntimeState() {
    clearThemeRuntimeAsyncWork();
    themeRuntimeDestroyed = false;
    resetThemeMediaImageBatch();
    cancelThemeAnchorScrollRetries();
    clearThemeInfiniteScrollBootstrapTimers();
    var preserveEndNavigation = window.loadAction == END_ACTION || themeInfiniteScroll.endScrollPending;
    var preserveUnreadAnchor = window.loadAnchorUnreadTarget === true ? resolveThemeInitialAnchorName() : "";
    if (themeInfiniteScroll.visiblePageThrottleTimer) {
        clearTimeout(themeInfiniteScroll.visiblePageThrottleTimer);
        removeThemeRuntimeTimer(themeInfiniteScroll.visiblePageThrottleTimer);
    }
    themeInfiniteScroll.loadingTop = false;
    themeInfiniteScroll.loadingBottom = false;
    themeInfiniteScroll.scrollRafPending = false;
    themeInfiniteScroll.lastVisiblePage = null;
    themeInfiniteScroll.suppressUntil = preserveEndNavigation ? Date.now() + END_NAV_TOP_SUPPRESS_MS : 0;
    if (preserveUnreadAnchor && preserveUnreadAnchor.length) {
        armUnreadInitialAnchorScroll(preserveUnreadAnchor);
    } else {
        themeInfiniteScroll.initialTopAutoloadSuppressedUntil = preserveEndNavigation ? Date.now() + END_NAV_TOP_SUPPRESS_MS : 0;
        themeInfiniteScroll.unreadInitialAnchor = "";
        themeInfiniteScroll.unreadInitialAnchorPending = false;
        themeInfiniteScroll.unreadAnchorGuardStartedAt = 0;
    }
    themeInfiniteScroll.endScrollPending = preserveEndNavigation;
    themeInfiniteScroll.userScrolled = false;
    themeInfiniteScroll.visiblePageRafPending = false;
    themeInfiniteScroll.visiblePageThrottleTimer = null;
}

function destroyThemeInfiniteScrollRuntime() {
    window.removeEventListener("scroll", onThemeInfiniteScroll);
    clearThemeInfiniteScrollBootstrapTimers();
    if (themeInfiniteScroll.visiblePageThrottleTimer) {
        clearTimeout(themeInfiniteScroll.visiblePageThrottleTimer);
        removeThemeRuntimeTimer(themeInfiniteScroll.visiblePageThrottleTimer);
        themeInfiniteScroll.visiblePageThrottleTimer = null;
    }
    themeInfiniteScroll.visiblePageRafPending = false;
    themeInfiniteScroll.scrollRafPending = false;
    themeInfiniteScroll.loadingTop = false;
    themeInfiniteScroll.loadingBottom = false;
}

function destroyThemeRuntime(reason) {
    if (themeRuntimeDestroyed) return;
    themeRuntimeDestroyed = true;
    logThemeLongSessionSnapshot("destroy:" + (reason || ""));
    cancelThemeAnchorScrollRetries();
    destroyThemePostGestureActions();
    destroyThemeInfiniteScrollRuntime();
    if (typeof destroyInlineTopicHeaderVisibilityObserver === "function") {
        destroyInlineTopicHeaderVisibilityObserver();
    }
    unbindThemeLinkSourceAnchorEvents();
    unbindThemeAnchorScrollCancelInputEvents();
    window.removeEventListener("resize", onThemeOverlayViewportChanged);
    window.removeEventListener("orientationchange", onThemeOverlayViewportChanged);
    clearThemeMediaImageListeners(document);
    resetThemeMediaImageBatch();
    clearThemeRuntimeAsyncWork();
    window.themeLastLinkSourceAnchor = null;
    window.__themeLastClickedPostAnchor = null;
    window.__themeRenderToken = "";
    anchorElem = null;
    elemToActivation = null;
    logThemeRender("destroyRuntime reason=" + (reason || ""));
}

function logThemeLongSessionSnapshot(reason) {
    if (!isThemeRenderDebugEnabled()) return;
    try {
        console.log("[ThemeLongSession] " + reason + " " + JSON.stringify({
            timers: themeRuntimeTimers.length,
            rafs: themeRuntimeRafs.length,
            mediaBatch: themeMediaImageLoadBatch.images.length,
            posts: document.querySelectorAll(".post_container[data-post-id]:not(.topic_hat_entry):not(.topic_hat_fixed)").length,
            containers: document.querySelectorAll(".theme_page_container[data-page-number]").length,
            images: document.querySelectorAll("body#topic .post_body img").length,
            scrollHeight: getThemeDocumentScrollHeight(),
            runtimeDestroyed: themeRuntimeDestroyed === true
        }));
    } catch (ex) {
        logThemeRuntimeWarning("ThemeLongSession", "snapshot error " + ex);
    }
}
