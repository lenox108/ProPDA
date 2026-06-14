var loader;
try {
    loader = new AvatarLoader();
} catch (ex) {
    console.error(ex);
}

var smartQuotesEnabled = true;

function transformSnapbacks() {
    var snapBacks = document.querySelectorAll("a[href*=findpost][title='Перейти к сообщению'],a[href*=showuser]");
    for (var i = 0; i < snapBacks.length; i++) {
        var snapBack = snapBacks[i];
        //console.log("SNAPBACK " + snapBack.href);
        //console.log(snapBack);
        if (snapBack.classList.contains("snapback")) {
            continue;
        }
        if (snapBack.href.indexOf("showuser") != -1) {
            var temp = snapBack;
            while (temp.firstElementChild != null) {
                temp = temp.firstElementChild;
            }
            temp.insertAdjacentHTML("afterbegin", "<span class=\"icon\"></span>");
            snapBack.classList.add("user");
        } else {
            var temp;

            //Удаление изображения
            temp = snapBack.getElementsByTagName("IMG");
            if (temp.length > 0) {
                temp = temp[0];
                temp.parentNode.removeChild(temp);
            }

            //Обычно идёт следующий эелемент
            temp = snapBack.nextElementSibling;
            if (temp != null && temp != undefined) {
                var nick = temp.textContent;
                //console.log(nick);
                //Обычно ник вконце с запятой
                //Удаляем из текста и вставляем в ссылку
                temp.parentNode.removeChild(temp);
                snapBack.appendChild(temp);

                //Поиск самого "глубокого" элемента для иконки
                while (temp.firstElementChild != null && temp.tagName.toLocaleLowerCase === "img") {
                    temp = temp.firstElementChild;
                }
                temp.insertAdjacentHTML("afterbegin", "<span class=\"icon\"></span>");



                snapBack.classList.add("post");
            }
        }
        snapBack.classList.add("snapback");
    }
}

function transformQuotes() {
    var quotes = document.querySelectorAll(".post-block.quote");
    var titleRegexp = /([\s\S]*?)\s@\s((?:\d+\.\d+\.\d+|[\wа-яА-ЯёЁ][\wа-яА-ЯёЁ._-]*)(?:,\s*\d+:\d+)?)?/;
    for (var i = 0; i < quotes.length; i++) {
        var quote = quotes[i];
        if (quote.classList.contains("transformed")) {
            continue;
        }
        var titleBlock = quote.querySelector(".block-title");
        if (!titleBlock) {
            quote.classList.add("transformed");
            continue;
        }

        var snapbackLink = titleBlock.querySelector(
            "a.snapback[href*='findpost'], a[href*='findpost'][title='Перейти к сообщению']"
        );
        var snapbackHref = snapbackLink ? snapbackLink.getAttribute("href") : null;
        var snapbackTitle = snapbackLink
            ? (snapbackLink.getAttribute("title") || "Перейти к сообщению")
            : "Перейти к сообщению";

        var titleText = (titleBlock.textContent || "").replace(/\s+/g, " ").trim();
        var match = titleRegexp.exec(titleText);
        if (!match) {
            quote.classList.add("transformed");
            continue;
        }

        var nick = (match[1] || "").replace(/^\s*@+/, "").trim();
        var date = match[2];
        var validNick = nick.length > 0;
        if (!validNick) {
            nick = "undefined";
        }
        if (date == null || date === undefined) {
            date = "";
        }

        var match2 = /([a-zA-Zа-яА-ЯёЁ])/.exec(nick);
        var letter = match2 ? match2[1] : (nick.charAt(0) || "?");

        var newHtml = "<div class=\"avatar\"><span class=\"image\"></span>" + escapeHtml(letter) + "</div>";
        newHtml += "<span class=\"title\">";
        newHtml += "<span class=\"name\">" + escapeHtml(nick) + "</span>";
        if (date) {
            newHtml += "<span class=\"date\">" + escapeHtml(date) + "</span>";
        }
        newHtml += "</span>";
        if (snapbackHref) {
            newHtml += "<a class=\"snapback post\" href=\"" + escapeHtml(snapbackHref) +
                "\" title=\"" + escapeHtml(snapbackTitle) + "\"></a>";
        }

        titleBlock.innerHTML = newHtml;

        if (validNick) {
            try {
                if (typeof PageInfo !== "undefined" && PageInfo.enableAvatars) {
                    loadAvatar(titleBlock);
                }
            } catch (ex) {
                console.error(ex);
            }
        }
        quote.classList.add("transformed");
    }
}

function initSmartQuotes(root) {
    if (!smartQuotesEnabled) {
        return;
    }
    if (document.body && document.body.id === "search") {
        return;
    }
    root = root && root.querySelectorAll ? root : document;
    var quotes = root.querySelectorAll(".post-block.quote");
    for (var i = 0; i < quotes.length; i++) {
        var quote = quotes[i];
        if (quote.classList.contains("smart-quote-collapsible")) {
            continue;
        }
        var body = directChildByClass(quote, "block-body");
        if (!body) {
            continue;
        }
        if (body.textContent.length <= 500 && body.offsetHeight <= 180) {
            continue;
        }
        quote.classList.add("smart-quote-collapsible");
        quote.classList.add("smart-quote-collapsed");
        quote.classList.remove("smart-quote-expanded");
        quote.appendChild(createSmartQuoteToggle("Развернуть цитату"));
    }

    if (!document.smartQuotesClickBound) {
        document.addEventListener("click", onSmartQuoteClick, false);
        document.smartQuotesClickBound = true;
    }

    function directChildByClass(parent, className) {
        for (var i = 0; i < parent.children.length; i++) {
            if (parent.children[i].classList.contains(className)) {
                return parent.children[i];
            }
        }
        return null;
    }

    function createSmartQuoteToggle(text) {
        var toggle = document.createElement("div");
        toggle.className = "smart-quote-toggle";
        toggle.setAttribute("role", "button");
        toggle.setAttribute("tabindex", "0");
        toggle.textContent = text;
        return toggle;
    }

    function onSmartQuoteClick(event) {
        var target = event.target;
        if (!target || !target.classList || !target.classList.contains("smart-quote-toggle")) {
            return;
        }
        event.preventDefault();
        event.stopPropagation();
        var quote = target.parentElement;
        if (!quote || !quote.classList.contains("smart-quote-collapsible")) {
            return;
        }
        if (quote.classList.contains("smart-quote-collapsed")) {
            quote.classList.remove("smart-quote-collapsed");
            quote.classList.add("smart-quote-expanded");
            target.textContent = "Свернуть цитату";
        } else {
            quote.classList.remove("smart-quote-expanded");
            quote.classList.add("smart-quote-collapsed");
            target.textContent = "Развернуть цитату";
        }
    }
}

function loadAvatar(block) {
    var imageEl = block.querySelector(".avatar .image");
    if (imageEl.style.backgroundImage.indexOf("base64") != -1) {
        return;
    }
    var nick = block.querySelector(".name").innerHTML;
    loader.loadByNick(nick, function (loaded) {
        imageEl.style.backgroundImage = "url(\"" + loaded + "\")";
    })
}

function improveCodeBlock() {
    var codeBlockAll = document.querySelectorAll('.post-block.code');
    for (var i = 0; i < codeBlockAll.length; i++) {
        try {
            var codeBlock = codeBlockAll[i];
            var codeTitle = codeBlock.querySelector('.block-title')
            if (!codeBlock.classList.contains("improve")) {
                var codeBody = codeBlock.querySelector('.block-body'),
                    splitLines = codeBody.innerHTML.split(/<br[^>]*?>/g),
                    count = '',
                    lines = '';
                for (var j = 0; j < splitLines.length; j++) {
                    lines += '<div>' + splitLines[j] + '</div>';
                    count += (j + 1) + '\n';
                }
                codeBlock.classList.add('wrap');
                codeTitle.insertAdjacentHTML("beforeEnd", '<div class="block-controls"><i class="wrap"></i><i class="select_all"></i></div>');
                codeBody.innerHTML = "<div class=\"lines\">" + lines + "</div>";
                codeBlock.classList.add("improve");
            }
            codeTitle.querySelector('.wrap').addEventListener('click', onClickToggleButton);
            codeTitle.querySelector('.select_all').addEventListener('click', SelectText);
        } catch (error) {
            console.log(error);
        }
    }

    function onClickToggleButton(e) {
        e.stopPropagation();
        var button = e.target;
        var block;
        for (var i = 0; i < codeBlockAll.length; i++) {
            if (button == codeBlockAll[i].querySelector('.wrap')) {
                block = codeBlockAll[i];
                break;
            }
        }
        if (!block) return;
        if (block.classList.contains('wrap')) {
            block.classList.remove('wrap');
        } else {
            block.classList.add('wrap');
        }
    }

    function SelectText(e) {
        e.stopPropagation();
        var button = e.target;
        var block;
        for (var i = 0; i < codeBlockAll.length; i++) {
            if (button == codeBlockAll[i].querySelector('.select_all')) {
                block = codeBlockAll[i];
                break;
            }
        }
        var text = block.querySelector(".block-body");
        var range, selection
        if (document.body.createTextRange) {
            range = document.body.createTextRange();
            range.moveToElementText(text);
            range.select();
        } else if (window.getSelection) {
            selection = window.getSelection();
            range = document.createRange();
            range.selectNodeContents(text);
            selection.removeAllRanges();
            selection.addRange(range);
        }
    }
}

function blocksOpenClose() {
    ensureBlocksOpenCloseDelegation();
    var blockAll = document.querySelectorAll('.post-block.spoil,.post-block.code');

    if (!blockAll[0]) return;

    for (var i = 0; i < blockAll.length; i++) {
        var codeBlock = blockAll[i];
        if (codeBlock.classList.contains("trigger")) {
            continue;
        }
        var bt = directChildByClass(codeBlock, "block-title");
        var bb = directChildByClass(codeBlock, "block-body");
        if (!bt || !bb) continue;
        //console.log(bb);
        if (bb.parentElement.classList.contains('code') && bb.scrollHeight <= bb.offsetHeight) {
            //bb.parentElement.classList.remove('box');
        }
        bt.addEventListener('click', clickOnElement, false);
        codeBlock.classList.add("trigger");

        if (codeBlock.classList.contains('spoil')) {
            var btn = directChildByClass(directChildByClass(codeBlock, "block-body"), "btns_container");
            btn = btn ? directChildByClass(btn, "spoil_close") : null;
            if (!btn) {
                var btnsContainer = document.createElement("div");
                btnsContainer.classList.add("btns_container");
                btn = document.createElement('div');
                bb.appendChild(btnsContainer);
                btnsContainer.appendChild(btn);
                btn.innerHTML = 'Закрыть спойлер';
                btn.className = "spoil_close";
                btnsContainer.style.display = "none";
            }

            btn.addEventListener('click', clickBtn);

            function clickBtn(event) {
                clickOnElement(event);
                var t = event.target;
                while (t && t != document.body && !t.classList.contains('post_body') && !t.classList.contains('msg-content')) {
                    if (t.classList.contains('spoil')) {
                        t.scrollIntoView();
                        return;
                    }
                    t = t.parentElement;
                }

            }

        }
    }

    function directChildByClass(parent, className) {
        if (!parent) return null;
        for (var i = 0; i < parent.children.length; i++) {
            if (parent.children[i].classList.contains(className)) {
                return parent.children[i];
            }
        }
        return null;
    }

    function clickOnElement(event) {
        event.stopPropagation();
        var t = event.currentTarget ? event.currentTarget.parentElement : event.target;
        while (t && t != document.body && !t.classList.contains('post_body') && !t.classList.contains('msg-content')) {
            if (t.classList.contains('spoil')) {
                toggleSpoilerBlock(t, true);

                return;
            } else if (t.classList.contains('code')) {
                toggler("unbox", "box", t);
                return;
            }
            t = t.parentElement;
        }
    }

    /*function toggler(c, o, t) {
        if (t.classList.contains(c)) {
            t.classList.remove(c);
            t.classList.add(o);
            addImgesSrc(t);
        } else if (t.classList.contains(o)) {
            t.classList.remove(o);
            t.classList.add(c);
        }
    }*/
}

function ensureBlocksOpenCloseDelegation() {
    if (!document || document.fpdaBlocksOpenCloseDelegation === true) {
        return;
    }
    document.fpdaBlocksOpenCloseDelegation = true;
    document.addEventListener('click', function (event) {
        var target = event.target;
        if (!target || typeof target.closest !== "function") {
            return;
        }
        var closeButton = target.closest(".post-block.spoil .spoil_close");
        if (closeButton) {
            var spoiler = closeButton.closest(".post-block.spoil");
            if (spoiler) {
                event.stopPropagation();
                toggleSpoilerBlock(spoiler, true);
                spoiler.scrollIntoView();
            }
            return;
        }
        var title = target.closest(".post-block.spoil > .block-title,.post-block.code > .block-title");
        if (!title) {
            return;
        }
        if (target.closest(".block-controls")) {
            return;
        }
        var block = title.parentElement;
        if (!block || !block.classList || !block.classList.contains("post-block")) {
            return;
        }
        event.stopPropagation();
        if (block.classList.contains("spoil")) {
            toggleSpoilerBlock(block, true);
        } else if (block.classList.contains("code")) {
            toggler("unbox", "box", block);
        }
    }, true);
}

function toggler(c, o, t) {
    if (t && t.classList && t.classList.contains('spoil') && c === "close" && o === "open") {
        toggleSpoilerBlock(t, false);
        return;
    }
    if (t.classList.contains(c)) {
        t.classList.remove(c);
        t.classList.add(o);
        addImgesSrc(t);
    } else if (t.classList.contains(o)) {
        t.classList.remove(o);
        t.classList.add(c);
    }
}

function getDirectChildByClass(parent, className) {
    if (!parent || !parent.children) return null;
    for (var i = 0; i < parent.children.length; i++) {
        if (parent.children[i].classList.contains(className)) {
            return parent.children[i];
        }
    }
    return null;
}

function toggleSpoilerBlock(spoiler, animate) {
    if (!spoiler || !spoiler.classList) return;
    var opening = spoiler.classList.contains("close");

    if (opening) {
        spoiler.classList.remove("close");
        spoiler.classList.add("open");
        addImgesSrc(spoiler);
    } else if (spoiler.classList.contains("open")) {
        spoiler.classList.remove("open");
        spoiler.classList.add("close");
    }

    spoilCloseButton(spoiler);
}
/**
 *		==================
 *		SPOIL CLOSE BUTTON
 *		==================
 */

function spoilCloseButton(t) {
    var el = t;
    if (t.classList.contains('spoil')) {

        t = t.querySelector(".block-body");

        if (t.querySelector('img[src]')) {
            var images = t.querySelectorAll('img[src]');
            images[images.length - 1].addEventListener("load", function () {
                spoilCloseButton(el);
            });
        }


        for (var i = t.childNodes.length; i >= 0; i--) {
            var node = t.childNodes.item(i);
            if ((!!node) && (!!node.classList) && node.classList.contains("btns_container")) {
                var btn = node;
                if (t.clientHeight > document.documentElement.clientHeight) {
                    btn.style.display = "block";
                    return;
                } else {
                    btn.style.display = "none";
                    return;
                }
            }
        }

    }
}

/**
 *		===============================
 *		HIDE AND SHOW IMAGES IN SPOILER
 *		===============================
 */



function removeImgesSrc() {
    if (document.body.classList.contains("noimages")) return;
    var postBlockSpoils = document.body.querySelectorAll('.post-block.spoil.close');
    for (var i = 0; i < postBlockSpoils.length; i++) {
        var codeBlock = postBlockSpoils[i];
        /*if (codeBlock.classList.contains("images")) {
            continue;
        }*/
        var images = codeBlock.querySelector(".block-body").querySelectorAll("img");
        for (var j = 0; j < images.length; j++) {
            var img = images[j];
            //console.log("removeImgesSrc " + img.src + " : " + img.dataset.src);
            if (img.dataset.imageSrc) continue;
            var srcUrl = getSpoilerImageSource(img);
            if (!srcUrl) {
                continue;
            }
            img.dataset.imageSrc = srcUrl;
            img.removeAttribute('src');
        }
        /*if (!codeBlock.classList.contains("images")) {
            codeBlock.classList.add("images");
        }*/
    }
}

function isThemePostAttachmentImage(img) {
    if (!img || !img.classList) return false;
    return img.classList.contains("linked-image") ||
        img.classList.contains("attach") ||
        !!img.getAttribute("data-attach-id") ||
        !!img.getAttribute("data-preview");
}

function getThemePostAttachmentDisplayUrl(img) {
    if (!img) return "";
    var preview = img.getAttribute("data-preview") || "";
    if (preview) {
        return normalizeSpoilerImageUrl(preview);
    }
    var srcUrl = img.dataset.imageSrc ||
        img.getAttribute("data-src") ||
        img.getAttribute("data-original") ||
        img.getAttribute("data-lazy-src") ||
        img.getAttribute("src") ||
        img.src ||
        "";
    return normalizeSpoilerImageUrl(srcUrl);
}

function getSpoilerImageSource(img) {
    if (!img) return "";
    if (isThemePostAttachmentImage(img)) {
        return getThemePostAttachmentDisplayUrl(img);
    }
    var srcUrl = img.dataset.imageSrc ||
        img.getAttribute("data-src") ||
        img.getAttribute("data-original") ||
        img.getAttribute("data-lazy-src") ||
        img.getAttribute("src") ||
        img.src ||
        "";
    return normalizeSpoilerImageUrl(srcUrl);
}

function normalizeSpoilerImageUrl(srcUrl) {
    if (!srcUrl) return "";
    srcUrl = String(srcUrl).trim();
    if (!srcUrl || srcUrl.indexOf("data:") === 0 || srcUrl.indexOf("blob:") === 0) return srcUrl;
    if (srcUrl.indexOf("//") === 0) return "https:" + srcUrl;
    if (srcUrl.charAt(0) === "/") return "https://4pda.to" + srcUrl;
    if (/^https?:\/\//i.test(srcUrl) || /^file:\/\//i.test(srcUrl) || /^app_cache:/i.test(srcUrl)) return srcUrl;
    try {
        var base = (typeof PageInfo !== "undefined" && PageInfo.url && /^https?:\/\//i.test(PageInfo.url))
            ? PageInfo.url
            : "https://4pda.to/forum/";
        return new URL(srcUrl, base).href;
    } catch (ignore) {
        return srcUrl;
    }
}

function hasWorkingImageSrc(img) {
    if (!img) return false;
    var attrSrc = img.getAttribute("src");
    return !!(attrSrc && attrSrc.trim());
}

function getAttachmentPreviewSource(img) {
    return getThemePostAttachmentDisplayUrl(img);
}

function findParentSpoilerBlock(img) {
    var node = img ? img.parentElement : null;
    while (node && node !== document.body) {
        if (node.classList && node.classList.contains("post-block") && node.classList.contains("spoil")) {
            return node;
        }
        node = node.parentElement;
    }
    return null;
}

function promoteAttachmentImageSources(root) {
    if (document.body.classList.contains("noimages")) return;
    var container = root && root.querySelectorAll ? root : document;
    var images = container.querySelectorAll("body#topic .post_body img.attach, body#topic .post_body img.linked-image");
    for (var i = 0; i < images.length; i++) {
        var img = images[i];
        var spoiler = findParentSpoilerBlock(img);
        if (spoiler && spoiler.classList.contains("close")) continue;
        var srcUrl = getThemePostAttachmentDisplayUrl(img);
        if (!srcUrl) continue;
        var current = normalizeSpoilerImageUrl(img.getAttribute("src") || "");
        if (current === srcUrl && hasWorkingImageSrc(img)) continue;
        img.src = srcUrl;
        if (typeof resetThemeMediaImageState === "function") {
            resetThemeMediaImageState(img);
        }
    }
}

function addImgesSrc(target) {
    while (target != null) {
        if (target.classList && target.classList.contains('spoil')) {
            var images = target.querySelectorAll('img');
            var restored = false;
            for (var i = 0; i < images.length; i++) {
                var img = images[i];
                var srcUrl = getSpoilerImageSource(img);
                if (!srcUrl) continue;
                var current = normalizeSpoilerImageUrl(img.getAttribute("src") || "");
                if (current === srcUrl && hasWorkingImageSrc(img)) continue;
                img.src = srcUrl;
                img.removeAttribute('data-image-src');
                if (typeof resetThemeMediaImageState === "function") {
                    resetThemeMediaImageState(img);
                }
                restored = true;
            }
            if (restored) {
                refreshSpoilerImagesAfterOpen(target);
            }
            return;
        }
        target = target.parentNode;
    }
}

function refreshSpoilerImagesAfterOpen(spoiler) {
    if (typeof initThemeMediaImageStability === "function") {
        initThemeMediaImageStability(spoiler);
    }
    if (typeof fixImagesSizeWithDensity === "function") {
        fixImagesSizeWithDensity();
    }
    if (typeof corrector !== 'undefined')
        corrector.startObserver();
    if (typeof scheduleVisibleThemePageLayoutChecks === "function") {
        scheduleVisibleThemePageLayoutChecks();
    }
}

function addIcons(e) {
    var blockAll = document.querySelectorAll(".post-block");
    var newIcon;
    for (var i = 0; i < blockAll.length; i++) {
        var codeBlock = blockAll[i];
        if (!codeBlock.classList.contains("icons")) {
            var blockTitle = codeBlock.querySelector(".block-title");
            if (blockTitle.innerText.length == 0) {
                blockTitle.classList.add("empty");
            }
            newIcon = document.createElement('i');
            newIcon.classList.add("icon");
            blockTitle.appendChild(newIcon);
            codeBlock.classList.add("icons");
        }
    }
}

function improveSpoilBlock() {
    var posts = document.querySelectorAll('.post_container');
    for (var j = 0; j < posts.length; j++) {
        var post = posts[j];
        var spoilBlockAll = post.querySelectorAll('.post-block.spoil');
        for (var i = 0; i < spoilBlockAll.length; i++) {
            try {
                var codeBlock = spoilBlockAll[i];
                var codeTitle = codeBlock.querySelector('.block-title')
                if (!codeBlock.classList.contains("improve")) {
                    codeTitle.insertAdjacentHTML("beforeEnd", '<div class="block-controls"><i class="link" data-spoil-number="' + (i + 1) + '" data-post-id="' + post.getAttribute("data-post-id") + '"></i></div>');
                    codeBlock.classList.add("improve");
                }
                codeTitle.querySelector('.link').addEventListener('click', function (e) {
                    e.stopPropagation();
                    var postId = e.target.getAttribute("data-post-id");
                    var spoilerNumber = e.target.getAttribute("data-spoil-number");
                    console.log(postId + " : " + spoilerNumber);
                    IThemePresenter.copySpoilerLink(postId, spoilerNumber);
                });
            } catch (error) {
                console.log(error);
            }
        }
    }
}

nativeEvents.addEventListener(nativeEvents.DOM, transformSnapbacks, true);
nativeEvents.addEventListener(nativeEvents.DOM, transformQuotes, true);
nativeEvents.addEventListener(nativeEvents.DOM, initSmartQuotes, true);

nativeEvents.addEventListener(nativeEvents.DOM, improveSpoilBlock, true);
nativeEvents.addEventListener(nativeEvents.DOM, improveCodeBlock, true);
nativeEvents.addEventListener(nativeEvents.DOM, blocksOpenClose, true);
nativeEvents.addEventListener(nativeEvents.DOM, removeImgesSrc, true);
nativeEvents.addEventListener(nativeEvents.DOM, function onThemeAttachmentImagesReady() {
    promoteAttachmentImageSources(document);
    if (typeof initThemeMediaImageStability === "function") {
        initThemeMediaImageStability(document);
    }
}, true);
nativeEvents.addEventListener(nativeEvents.DOM, addIcons, true);
