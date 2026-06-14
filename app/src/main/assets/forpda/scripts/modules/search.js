function finalizeSearchPostContentExpand(hat) {
    if (!hat) {
        return;
    }
    hat.classList.remove("close", "over_height");
    hat.classList.add("open");
    hat.style.maxHeight = "";
    hat.style.height = "";
    hat.style.overflow = "";

    var quotes = hat.querySelectorAll(".post-block.quote.smart-quote-collapsed");
    for (var i = 0; i < quotes.length; i++) {
        var quote = quotes[i];
        quote.classList.remove("smart-quote-collapsed");
        quote.classList.add("smart-quote-expanded");
        var toggle = quote.querySelector(".smart-quote-toggle");
        if (toggle) {
            toggle.textContent = "Свернуть цитату";
        }
    }
}

function measurePostHeight() {
    var hats = document.querySelectorAll(".hat_content");
    for (var i = 0; i < hats.length; i++) {
        var hat = hats[i];
        var button = hat.parentElement.querySelector(".hat_button");
        if (!button) {
            continue;
        }

        if (hat.classList.contains("open")) {
            finalizeSearchPostContentExpand(hat);
            button.style.display = "";
            button.setAttribute("aria-expanded", "true");
            continue;
        }

        if (hat.scrollHeight > hat.clientHeight) {
            hat.classList.add("over_height");
            button.style.display = "";
            button.setAttribute("aria-expanded", hat.classList.contains("open") ? "true" : "false");
        } else {
            hat.classList.remove("over_height");
            button.style.display = "none";
        }
    }

}

function bindSearchPaginationLinks() {
    document.addEventListener("click", function (event) {
        var target = event.target;
        while (target && target !== document && target.nodeName !== "A") {
            target = target.parentNode;
        }
        if (!target || target === document) {
            return;
        }
        if (!target.closest || !target.closest(".pagination")) {
            return;
        }

        var href = target.getAttribute("href") || "";
        var stMatch = href.match(/[?&]st=(\d+)/);
        if (!stMatch || typeof IThemePresenter === "undefined") {
            return;
        }

        event.preventDefault();
        IThemePresenter.searchPage(stMatch[1]);
    }, true);
}

function bindSearchResultCardLinks() {
    document.addEventListener("click", function (event) {
        var target = event.target;
        if (!target || !target.closest) {
            return;
        }

        var post = target.closest(".post_container[data-open-url]");
        if (!post) {
            return;
        }

        var openUrl = post.getAttribute("data-open-url") || "";
        if (!openUrl || openUrl === "#") {
            return;
        }

        if (target.closest(".search_jump_to_post, .post_header, .hat_button")) {
            return;
        }

        event.preventDefault();
        event.stopPropagation();
        window.location.href = openUrl;
    }, true);
}

function bindSearchPostContentToggle() {
    if (document.body && document.body.id !== "search") {
        return;
    }
    if (document.searchPostContentToggleBound) {
        return;
    }
    document.searchPostContentToggleBound = true;
    document.addEventListener("click", function (event) {
        var target = event.target;
        if (!target || !target.closest) {
            return;
        }
        var button = target.closest(".post_container > .hat_button");
        if (!button) {
            return;
        }
        event.preventDefault();
        event.stopPropagation();
        toggleButton(button, "hat_content", "search");
    }, true);
}

nativeEvents.addEventListener(nativeEvents.DOM, measurePostHeight, true);
nativeEvents.addEventListener(nativeEvents.DOM, bindSearchPaginationLinks, true);
nativeEvents.addEventListener(nativeEvents.DOM, bindSearchResultCardLinks, true);
nativeEvents.addEventListener(nativeEvents.DOM, bindSearchPostContentToggle, true);
