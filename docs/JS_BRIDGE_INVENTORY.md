# JS Bridge Inventory

## Summary
- Total active `addJavascriptInterface` registrations: 7.
- Total exposed `@JavascriptInterface` methods: 52.
- Highest risk area: Theme presenter bridge.
- URL policy blocks `javascript:`, `file:`, `data:`, `content:`, `about:`, `app_cache:`, unknown schemes, control characters, encoded control characters, and percent-encoded dangerous scheme prefixes before WebView or external intent dispatch.

## Registrations
| File | Interface name | Registered object | Trusted content only? | Notes |
|---|---|---|---|---|
| `app/src/main/java/forpdateam/ru/forpda/ui/views/ExtendedWebView.kt` | `IBase` | `this` (`ExtendedWebView`) via `enableBaseBridge()` | Yes. `enableBaseBridge()` only registers for `TRUSTED_LOCAL_TEMPLATE`. | Base lifecycle/click bridge used by trusted local templates. |
| `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeBridgeHandler.kt` | `IThemeView` | `ThemeViewBridge` | Yes. Theme WebView uses `TRUSTED_LOCAL_TEMPLATE`. | Narrow compatibility bridge exposing only the no-op history callback. |
| `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeBridgeHandler.kt` | `IThemePresenter` | `ThemeJsInterface` | Yes. Theme WebView uses `TRUSTED_LOCAL_TEMPLATE`. | Broad, high-risk theme bridge surface. |
| `app/src/main/java/forpdateam/ru/forpda/ui/fragments/search/SearchFragment.kt` | `IThemePresenter` | `SearchJsInterface` | Search uses `TRUSTED_STATIC_ARTICLE` for locally generated result HTML; this specialized bridge is still registered. | Menu/copy/share methods only; no direct destructive methods. |
| `app/src/main/java/forpdateam/ru/forpda/ui/fragments/news/details/ArticleContentFragment.kt` | `INews` | `this` (`ArticleContentFragment`) | Yes. News WebView uses `TRUSTED_STATIC_ARTICLE`; bridge is removed on unexpected page start. | News comments, poll vote, image, and external browser bridge. |
| `app/src/main/java/forpdateam/ru/forpda/ui/fragments/other/ForumRulesFragment.kt` | `IRules` | `this` (`ForumRulesFragment`) | Yes. Rules WebView uses `TRUSTED_STATIC_ARTICLE`. | Copies a rule after native confirmation. |

## Exported methods
| File | Class | Method | Category | Destructive? | Notes |
|---|---|---|---|---|---|
| `app/src/main/java/forpdateam/ru/forpda/ui/views/ExtendedWebView.kt` | `ExtendedWebView` | `playClickEffect()` | UI feedback | No | Registered as `IBase` for trusted local templates. |
| `app/src/main/java/forpdateam/ru/forpda/ui/views/ExtendedWebView.kt` | `ExtendedWebView` | `domContentLoaded()` | WebView lifecycle | No | Flushes queued JS after DOM ready. |
| `app/src/main/java/forpdateam/ru/forpda/ui/views/ExtendedWebView.kt` | `ExtendedWebView` | `onPageLoaded()` | WebView lifecycle | No | Flushes queued JS after page load. |
| `app/src/main/java/forpdateam/ru/forpda/ui/views/ExtendedWebView.kt` | `ExtendedWebView` | `onActionModeComplete()` | Selection lifecycle | No | Public JS method on the WebView object; exposure depends on registration path. |
| `app/src/main/java/forpdateam/ru/forpda/common/webview/jsinterfaces/IBase.kt` | `IBase` | `playClickEffect()`, `domContentLoaded()`, `onPageLoaded()` | Interface contract | No | Interface annotations mirror `ExtendedWebView` implementation. |
| `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeBridgeHandler.kt` | `ThemeViewBridge` | `callbackUpdateHistoryHtml(_value)` | Theme history callback | No | Registered as `IThemeView`; keeps legacy template compatibility without exposing the full fragment. |
| `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeWebController.kt` | `ThemeWebController` | `callbackUpdateHistoryHtml(_value)` | Theme history callback | No | No direct registration found for this controller object. |
| `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeJsInterface.kt` | `ThemeJsInterface` | `firstPage()`, `prevPage()`, `nextPage()`, `lastPage()`, `selectPage()`, `selectPageInput()`, `searchPage(st)`, `infiniteScroll(direction)`, `infiniteRetry(direction)`, `visiblePageChanged(pageNumber)` | Theme navigation/pagination | No | Changes page or rendered position. |
| `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeJsInterface.kt` | `ThemeJsInterface` | `showUserMenu(postId)`, `showReputationMenu(postId)`, `showChangeReputation(postId, type)`, `showPostMenu(postId)` | Theme menus | Yes | Can lead to reputation or post actions. |
| `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeJsInterface.kt` | `ThemeJsInterface` | `reportPost(postId)`, `reply(postId)`, `quotePost(text, postId)`, `quotePostWithDate(text, postId, displayedDate)`, `quoteFullPost(postId)`, `quoteFullPostWithDate(postId, displayedDate)`, `deletePost(postId)`, `editPost(postId)`, `votePost(postId, type)`, `submitPoll(action, method, encodedForm)` | Theme write actions | Yes | Highest-risk bridge methods. |
| `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeJsInterface.kt` | `ThemeJsInterface` | `setHistoryBody(index, body)`, `setPollOpen(bValue)`, `setHatOpen(bValue)`, `setInlineHatOpen(topicId, bValue)` | Theme state | Partial | Mutates local history/open-state. |
| `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeJsInterface.kt` | `ThemeJsInterface` | `copySelectedText(text)`, `toast(text)`, `log(text)`, `showPollResults(url)`, `showPoll()`, `copySpoilerLink(postId, spoilNumber)`, `shareSelectedText(text)`, `openLink(url)`, `rememberLinkSourceAnchor(payload)`, `anchorDialog(postId, name)` | Theme utility/link actions | Partial | `openLink` and link-source methods need URL/source trust validation. |
| `app/src/main/java/forpdateam/ru/forpda/presentation/search/SearchJsInterface.kt` | `SearchJsInterface` | `showUserMenu(postId)`, `showReputationMenu(postId)`, `showPostMenu(postId)`, `toast(text)`, `log(text)`, `copySelectedText(text)`, `shareSelectedText(text)` | Search result actions | No direct destructive method | Menus may lead to native actions after user interaction. |
| `app/src/main/java/forpdateam/ru/forpda/ui/fragments/news/details/ArticleContentFragment.kt` | `ArticleContentFragment` | `toComments()` | News navigation | No | Opens comments. |
| `app/src/main/java/forpdateam/ru/forpda/ui/fragments/news/details/ArticleContentFragment.kt` | `ArticleContentFragment` | `sendPoll(id, answer, from, token)` | News poll | Yes | Uses trusted HTML token check. |
| `app/src/main/java/forpdateam/ru/forpda/ui/fragments/news/details/ArticleContentFragment.kt` | `ArticleContentFragment` | `openImage(url)`, `openExternalBrowser(url)` | News links/media | Partial | Opens supplied image URL or browser URL. |
| `app/src/main/java/forpdateam/ru/forpda/ui/fragments/other/ForumRulesFragment.kt` | `ForumRulesFragment` | `copyRule(text)` | Rules copy action | No | Native confirmation before copy. |

## Destructive / sensitive methods
- `showChangeReputation(postId, type)` can start reputation changes.
- `reportPost(postId)` opens report flow.
- `reply(postId)`, `quotePost(...)`, `quotePostWithDate(...)`, `quoteFullPost(...)`, and `quoteFullPostWithDate(...)` start reply/quote flows.
- `deletePost(postId)` and `editPost(postId)` start destructive or content-changing post flows.
- `votePost(postId, type)` and `submitPoll(action, method, encodedForm)` submit votes/polls.
- `openLink(url)`, `openImage(url)`, and `openExternalBrowser(url)` cross the URL/content boundary.
- `sendPoll(id, answer, from, token)` submits a news poll vote.
- QMS chat has no bridge at all: the screen renders natively (`QmsMessagesAdapter` + `BodyBlockViewFactory`), see `QMS_BRIDGE_INVENTORY.md`.

## Risks
- Fragment registered as JS interface: partly. `ThemeBridgeHandler` no longer registers `ThemeFragmentWeb`; `ArticleContentFragment` and `ForumRulesFragment` still register `this` for their trusted static WebViews.
- Broad bridge surface: yes. `ThemeJsInterface` contains most exported methods and all theme destructive actions.
- Methods without token/session validation: theme destructive methods (`reportPost`, `reply`, `quote*`, `deletePost`, `editPost`, `votePost`, `submitPoll`) do not have the new `ThemeRenderGuard` integrated yet. News `sendPoll` has its own trusted HTML token check.
- External link handling: WebView navigation, theme `openLink`, link-handler redirects, and downloads pass through `UrlPolicy`/`SystemLinkHandler`. `mailto:` remains allowed as an external URL; all other non-http(s) schemes are blocked to prevent arbitrary app intent abuse.
- File-like schemes: `file:`, `content:`, `data:`, `javascript:`, `about:`, and `app_cache:` are blocked as navigations/downloads and are not forwarded to external intents.

## Recommended next tasks
- S02 ThemeRenderGuard
- S03/S04 guarded destructive methods
- S05 remove Fragment direct registration if present

## `@JavascriptInterface` Methods Without Direct Registration In Same File

| File | Object / method | Registration path | Destructive? | Notes |
| --- | --- | --- | --- | --- |
| `app/src/main/java/forpdateam/ru/forpda/common/webview/jsinterfaces/IBase.kt` | `IBase.playClickEffect()`, `IBase.domContentLoaded()`, `IBase.onPageLoaded()` | Implemented by `ExtendedWebView`, registered by `ExtendedWebView.enableBaseBridge()` as `IBase`. | No | Interface annotations mirror the implementation. |
| `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeWebController.kt` | `callbackUpdateHistoryHtml(_value)` | No direct `addJavascriptInterface` registration found for `ThemeWebController`. | No | The controller owns WebView runtime wiring, but this method is not exposed unless the object is registered elsewhere. |
| `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeBridgeHandler.kt` | `ThemeViewBridge.callbackUpdateHistoryHtml(_value)` | Registered by `ThemeBridgeHandler` as `IThemeView`. | No | No-op compatibility callback used by legacy theme history flow. |

## Registrations With No Exposed Methods Found

| File | Interface name | Registered object | Trusted content only | Notes |
| --- | --- | --- | --- | --- |
| `app/src/main/java/forpdateam/ru/forpda/ui/fragments/other/AnnounceFragment.kt` | `IAnnounce` | No active registration found | Yes, if enabled. | `JS_INTERFACE` exists for template compatibility, but no `addJavascriptInterface` call or `@JavascriptInterface` methods were found in the current file. |

## High-Risk Summary

High-risk/destructive bridge surface is concentrated in the trusted theme bridge (`IThemePresenter`) and news bridge (`INews`):

- Theme actions: delete/edit/vote/reply/quote/report/poll submit/open link/history body mutation.
- News actions: poll voting and external URL/image opening.
- File/download behavior was found in WebView clients and `ExtendedWebView` download listeners, not as a direct JS bridge method in this inventory.
