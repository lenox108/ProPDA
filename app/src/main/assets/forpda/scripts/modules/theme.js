console.log("LOAD JS SOURCE theme.js");
const BACK_ACTION = "0";
const REFRESH_ACTION = "1";
const NORMAL_ACTION = "2";
/** Задержки (мс) для повторного scrollToElement — вёрстка/картинки подгружаются после первого кадра. */
var SCROLL_ANCHOR_RETRY_DELAYS_MS = [1, 120, 400, 900];
window.loadAction = NORMAL_ACTION;
window.loadScrollY = 0;
var anchorElem, elemToActivation;
var corrector;


function setLoadAction(loadAction) {
    console.log("setLoadAction " + loadAction);
    window.loadAction = loadAction;
}

function setLoadScrollY(loadScrollY) {
    console.log("setLoadScrollY " + loadScrollY);
    window.loadScrollY = Number(loadScrollY);
}

function disableImages() {
    var images = document.querySelectorAll(".linked-image");
    console.log(images);
    for (var i = 0; i < images.length; i++) {
        var image = images[i];
        var src = image.getAttribute("src");
        image.removeAttribute("src");
        image.setAttribute("data-src", src);
    }
}

//Вызывается при обновлении прогресса загрузке страницы и при загрузке её ресурсов
//По идеи должна верно скроллить к нужному элементу, даже если пользователь прокрутил страницу
//Как оно работает и работает ли вообще - объяснить не могу
function onProgressChanged() {
    if (corrector)
        corrector.startObserver();
}

function getScrollTop() {
    return (window.pageYOffset || document.documentElement.scrollTop) - (document.documentElement.clientTop || 0);
}
//name может быть EventObject или строкок
//name это аттрибут тега html, может быть просто якорем или entry+post_id
//Вызывается из джавы, если находится на той-же странице, и в ссылке есть entry или якорь, а также при загрузке страницы
//PageInfo.elemToScroll - переменная, заданная в шаблоне в теге script, содержит в себе якорь или entry



function scrollToElement(name) {
    console.log("scrollToElement " + name);
    // Явный якорь из Java (ссылка на пост, жест «Назад» по истории якорей) — всегда крутим к посту, не к loadScrollY при BACK.
    var explicitAnchor = (arguments.length > 0 && typeof name === 'string' && name.length > 0);
    if (typeof name != 'string') {
        name = PageInfo.elemToScroll;
    }
    var anchorData = /([^-]*)-([\d]*)-(\d+)/g.exec(name);
    if (anchorData) {
        //anchorData[1] - name (spoil, quote, etc)
        //anchorData[2] - post id
        //anchorData[3] - number block of post, begin with 1
        anchorData[1] = anchorData[1].toLowerCase();
        if (anchorData[1] === "spoiler") anchorData[1] = "spoil";
        if (anchorData[1] === "hide") anchorData[1] = "hidden";
        anchorElem = document.querySelector('[name="entry' + anchorData[2] + '"]');
        anchorElem = anchorElem.querySelectorAll(".post-block." + anchorData[1])[Number(anchorData[3]) - 1];
    } else {
        anchorElem = document.querySelector('[name="' + name + '"]');
    }
    if (anchorElem) {
        //Открытие всех спойлеров
        var block = anchorElem;
        while (block.classList && !block.classList.contains('post_body')) {
            /*if (block.classList.contains('spoil')) {
                block.classList.remove('close');
                block.classList.add('open');
            }*/
            toggler("close", "open", block);
            block = block.parentNode;
        }
        // Открытие шапки при скролле к якорю — только если в настройках включено «Открытая шапка темы».
        // Иначе якорь на первый пост/шапку не должен разворачивать шапку (пользователь оставил её скрытой).
        var mayOpenHatForAnchor = (typeof PageInfo.hatOpenedPref === 'undefined' || PageInfo.hatOpenedPref);
        if (mayOpenHatForAnchor) {
            block = anchorElem;
            while (block.classList && !block.classList.contains('post_container')) {
                block = block.parentNode;
            }
            if (block.classList.contains("close")) {
                var button = block.querySelector(".hat_button");
                toggleButton(button, "hat_content");
            }
        }
    } else {
        anchorElem = document.documentElement;
    }
    console.log("ANCHOR " + name);
    console.log("loadAction " + window.loadAction);
    console.log("loadScrollY " + window.loadScrollY);
    if (!explicitAnchor && (window.loadAction == BACK_ACTION || window.loadAction == REFRESH_ACTION)) {
        setTimeout(function () {
            window.scrollTo(0, window.loadScrollY);
        }, 1);
        nativeEvents.addEventListener(nativeEvents.PAGE, function () {
            //setTimeout(function () {
                window.scrollTo(0, window.loadScrollY);
            //}, 1);
        });
    } else if (window.loadAction == NORMAL_ACTION || explicitAnchor) {
        var scrollIntoTarget = resolveThemeScrollIntoTarget(anchorElem);
        setTimeout(function () {
            doScroll(anchorElem, scrollIntoTarget);
        }, 1);
        nativeEvents.addEventListener(nativeEvents.PAGE, function () {
                doScroll(anchorElem, scrollIntoTarget);
        });
    }
}

/** При свёрнутой шапке и выключенной настройке «открытая шапка» — скролл к шапке поста, а не к якорю внутри скрытого тела. */
function resolveThemeScrollIntoTarget(anchorEl) {
    if (!anchorEl || typeof PageInfo === 'undefined' || PageInfo.bodyType !== 'topic') {
        return anchorEl;
    }
    var mayOpenHat = (typeof PageInfo.hatOpenedPref === 'undefined' || PageInfo.hatOpenedPref);
    if (mayOpenHat) return anchorEl;
    var pc = anchorEl;
    while (pc && (!pc.classList || !pc.classList.contains('post_container'))) {
        pc = pc.parentElement;
    }
    if (!pc) return anchorEl;
    var hat = pc.querySelector('.hat_content');
    if (!hat || !hat.classList.contains('close')) return anchorEl;
    var header = pc.querySelector('.post_header');
    return header || anchorEl;
}

/**
 * Повторы после сдвига вёрстки (картинки, шрифты): один вызов scrollToElement часто оставляет верх страницы.
 */
function scrollToElementWithRetries(name) {
    if (typeof name !== 'string' || !name.length) return;
    for (var i = 0; i < SCROLL_ANCHOR_RETRY_DELAYS_MS.length; i++) {
        (function (ms) {
            setTimeout(function () {
                scrollToElement(name);
            }, ms);
        })(SCROLL_ANCHOR_RETRY_DELAYS_MS[i]);
    }
    if (window.__themeScrollAnchorDiag) {
        var maxMs = SCROLL_ANCHOR_RETRY_DELAYS_MS[SCROLL_ANCHOR_RETRY_DELAYS_MS.length - 1];
        setTimeout(function () {
            logScrollAnchorDiag(name);
        }, maxMs + 150);
    }
}

/** После последнего retry: найден ли элемент и положение (Android: window.__themeScrollAnchorDiag). */
function logScrollAnchorDiag(name) {
    try {
        var el = anchorElem;
        if (!el && typeof name === 'string') {
            var anchorData = /([^-]*)-([\d]*)-(\d+)/g.exec(name);
            if (anchorData) {
                anchorData[1] = anchorData[1].toLowerCase();
                if (anchorData[1] === "spoiler") anchorData[1] = "spoil";
                if (anchorData[1] === "hide") anchorData[1] = "hidden";
                el = document.querySelector('[name="entry' + anchorData[2] + '"]');
                if (el) el = el.querySelectorAll(".post-block." + anchorData[1])[Number(anchorData[3]) - 1];
            } else {
                el = document.querySelector('[name="' + name + '"]');
            }
        }
        var top = el ? el.getBoundingClientRect().top : null;
        console.log("[ThemeScrollDiag] anchor=" + name + " found=" + !!el + " rectTop=" + top + " scrollY=" + (window.pageYOffset || 0));
    } catch (ex) {
        console.log("[ThemeScrollDiag] error " + ex);
    }
}

function doScroll(tAnchorElem, scrollIntoElem) {
    var toScroll = scrollIntoElem || tAnchorElem;
    try {
        toScroll.focus();
        var access_anchor = tAnchorElem.querySelector(".accessibility_anchor");
        if (access_anchor) {
            access_anchor.focus();
        }
    } catch (ex) {
        console.error(ex);
    }

    toScroll.scrollIntoView();

    //Активация элементов, убирается класс active с уже активированных
    if (elemToActivation)
        elemToActivation.classList.remove('active');

    var postElem = tAnchorElem;
    console.log(postElem);
    while (postElem && !postElem.classList.contains("post_container")) {
        postElem = postElem.parentElement;
        console.log(postElem);
    }
    elemToActivation = postElem;
    if (elemToActivation)
        elemToActivation.classList.add('active');
}

function selectionToQuote() {
    var selObj = window.getSelection();
    if (selObj.rangeCount === 0) {
        IThemePresenter.toast("Для этого действия необходимо выбрать текст сообщения");
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
    IBase.onActionModeComplete();

    var p = selObj.anchorNode.parentNode;
    while (p.classList && !p.classList.contains('post_container')) {
        p = p.parentNode;
    }
    if (typeof p === "undefined" || typeof p.dataset === "undefined") {
        IThemePresenter.toast("Для этого действия необходимо выбрать текст сообщения");
        return;
    }
    var postId = p.dataset.postId;
    if (selectedText != null && postId != null) {
        IThemePresenter.quotePost(selectedText.trim(), "" + postId);
    } else {
        IThemePresenter.toast("Ошибка создания цитаты: [" + selectedText + ", " + postId + "]");
        return;
    }
}

function copySelectedText() {
    var selectedText = window.getSelection().toString();
    IBase.onActionModeComplete();
    if (selectedText != null && selectedText) {
        IThemePresenter.copySelectedText(selectedText);
    }
}

function shareSelectedText() {
    var selectedText = window.getSelection().toString();
    IBase.onActionModeComplete();
    if (selectedText != null && selectedText) {
        IThemePresenter.shareSelectedText(selectedText);
    }
}


function selectAllPostText() {
    var selObj = window.getSelection();
    var p = selObj.anchorNode.parentNode;
    while (p.classList && !p.classList.contains('post_body')) {
        p = p.parentNode;
    }
    if (typeof p.classList === "undefined" || !p.classList.contains('post_body')) {
        IThemePresenter.toast("Для этого действия необходимо выбрать текст сообщения");
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

function ScrollCorrector() {
    console.log("Scroll Corrector initialized");
    var postElements = document.querySelectorAll(".post_container");
    var visibleElements = [];
    var visibleElement = anchorElem;
    var lastPosition = 0;
    var frames = 60 * 1;
    var frame = 0;
    var observerId = 0;

    for (var i = 0; i < postElements.length; i++) {
        postElements[i].addEventListener("mousedown", downEvent);
        postElements[i].addEventListener("touchdown", downEvent);
    }

    function downEvent(e) {

        var elem = e.target;
        while (!elem.classList.contains("post_container")) {
            elem = elem.parentElement;
        }
        visibleElement = elem;
        updateLastPosition();
    }

    this.startObserver = function () {
        startObserver();
    }

    window.addEventListener("scroll", function () {
        setVisible();
        updateLastPosition();
        frame = 0;
    });

    function updateLastPosition() {
        lastPosition = getCoordinates(visibleElement).top;
        //console.log("Update LastPosition: " + lastPosition);
    }

    function tryScroll() {

        var delta = getCoordinates(visibleElement).top - lastPosition;
        if (delta == 0)
            return;
        /*for (var i = 0; i < visibleElements.length; i++) {
            var elem = visibleElements[i];
            console.log("Elem [" + i + "]: " + getCoordinates(elem).top);
        }*/
        console.log("Scroll by delta: " + delta + ", lastPosition: " + lastPosition + ", visElemTop: " + getCoordinates(visibleElement).top);
        window.scrollBy(0, delta);
        updateLastPosition();
        frame = 0;
    }

    function startObserver() {
        if (observerId == 1) {
            return;
        }
        setVisible();
        console.log("Start Scroll Observer");

        function observerLoop() {
            tryScroll();
            if (frame < frames) {
                requestAnimationFrame(observerLoop);
                frame++;
            } else {
                cancelAnimationFrame(observerLoop);
                observerId = 0;
                frame = 0;
                console.log("Stop Scroll Observer");
            }
        }
        observerId = 1;
        observerLoop();
    }

    /*function setVisible(newVisible){
        if (visibleElement) {
            visibleElement.style.opacity = 1;
        }
        visibleElement = newVisible;
        visibleElement.style.opacity = 0.5;
    }*/

    function setVisible() {
        return;
        visibleElements = getVisiblePosts();
        if (visibleElement) {
            visibleElement.style.opacity = 1;
        }
        /*if (visibleElements.length > 0) {
            visibleElement = getNearest(visibleElements);
        }*/
        visibleElement = getNearest(visibleElements);
        visibleElement.style.opacity = 0.5;
    }

    function getVisiblePosts() {
        var scrollTop = getScrollTop();
        var windowHeight = document.documentElement.clientHeight;
        if (!visibleElement)
            visibleElements = [];
        visibleElements.length = 0;
        for (var i = 0; i < postElements.length; i++) {
            var el = postElements[i];
            if (el.offsetHeight + el.offsetTop < scrollTop || el.offsetTop > scrollTop + windowHeight)
                continue;
            visibleElements.push(el);
        }
        return visibleElements;
    }

    function getNearest(visibleElements) {
        var scrollTop = getScrollTop();
        var windowHeight = document.documentElement.clientHeight;
        var nearest = visibleElements[0];
        var deltaHeight = windowHeight;
        var delta = 0;
        for (var i = 0; i < visibleElements.length; i++) {
            var el = visibleElements[i];
            var bottomY = Math.abs(el.offsetTop + el.offsetHeight - scrollTop - windowHeight);
            if (deltaHeight - bottomY < delta) {
                break;
            }
            delta = deltaHeight - bottomY;
            deltaHeight = bottomY;
            nearest = el;
        }
        return nearest;
    }
}

function initScrollCorrector() {
    corrector = new ScrollCorrector();
}

function transformAnchor() {
    var anchors = [];
    var links = document.querySelectorAll(".post_container .post_body a[name][title]");
    for (var i = 0; i < links.length; i++) {
        if (links[i].innerHTML === "ˇ") {
            anchors.push(links[i]);
        }
    }

    for (var i = 0; i < anchors.length; i++) {
        var item = anchors[i];
        item.classList.add("anchor");
        item.innerHTML = "";
        item.addEventListener("click", function (event) {
            var t = event.target;
            while (!t.classList.contains('post_container')) {
                t = t.parentElement;
            }
            IThemePresenter.anchorDialog(t.dataset.postId, event.target.name);
        });
    }
}

nativeEvents.addEventListener(nativeEvents.DOM, transformAnchor);
nativeEvents.addEventListener(nativeEvents.DOM, initScrollCorrector);
nativeEvents.addEventListener(nativeEvents.DOM, scrollToElement);
