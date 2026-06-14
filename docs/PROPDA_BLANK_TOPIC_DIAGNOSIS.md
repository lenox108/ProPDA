# PROPDA Blank Topic Diagnosis

Task: `DIAG-THEME-BLANK-ROOTCAUSE-001`

## 1. Exact Failing Layer

FAILING LAYER: `UNKNOWN`

Static inspection alone does not prove where the content disappears. The current code can load a screen state with toolbar/page counter/bottom bar while the WebView DOM is empty or hidden, so runtime logcat from the added checkpoints is required to classify the failure as `DATA`, `TEMPLATE`, `WEBVIEW_LOAD`, `JS_RUNTIME`, or `CSS_VISIBILITY`.

## 2. Evidence From Logs/Checks

Added a single concise diagnostic tag: `ThemeBlankDiag`.

Expected evidence by checkpoint:

- `DATA api response`: HTTP status/success, response body length, parsed topic id, page number, parser post count, first post id/author/title.
- `DATA beforeTemplate`: Kotlin model immediately before template generation, including topic/page/posts/first post.
- `TEMPLATE generated`, `TEMPLATE afterMap`, `TEMPLATE postsFragment`: generated HTML length, `posts_list`, `theme_page_container`, `post_container` marker counts, body tag presence, posts fragment length.
- `WEBVIEW_LOAD beforeLoad`, `afterLoadCall`, `pageStarted`, `pageCommitVisible`, `pageFinished`: base URL, content length, attachment/visibility/shown state, measured size, content height and load lifecycle timing.
- `JS_RUNTIME DOMContentLoaded`, `JS_RUNTIME pageComplete`, `JS_RUNTIME domRendered`, `JS_RUNTIME uncaught`, `JS_RUNTIME rejection`: JS bootstrap state, post/container counts, uncaught errors and runtime ready details.
- `CSS_VISIBILITY dom`: body child count, post/container counts, scroll height, display/visibility/opacity/height for body, `.posts_list`, first page container and first post.
- `RECOVERY detector`, `RECOVERY detected`, `RECOVERY reload`: blank detector result, recovery trigger state, recovery HTML length and post marker counts.

Initial static findings:

- Kotlin already passes `ThemePage` through `ThemeUseCase.mapEntity()` and `ThemeTemplate.mapEntity()` before WebView render.
- The existing blank detector in `ThemeFragmentWeb` treats missing posts, missing page container, or too-small document height as blank and can trigger a reload recovery.
- Existing JS `getThemeRenderedPostsState()` counted posts and containers, but did not expose enough CSS/layout visibility evidence to distinguish missing DOM from hidden DOM.
- Existing WebView logs covered lifecycle timing, but did not use one diagnostic tag or include all attachment/visibility/measurement/content markers needed for this task.

## 3. Suspected Root Cause

UNKNOWN until runtime logs are captured.

The most useful split will be:

- If `DATA api response` / `DATA beforeTemplate` shows `posts=0`, root cause is parser/data layer.
- If Kotlin posts are present but `TEMPLATE generated` has no `post_container` or no `theme_page_container`, root cause is template generation.
- If HTML contains post markers but WebView lifecycle does not finish or WebView is detached/zero-sized/hidden, root cause is WebView load/attachment.
- If WebView receives valid HTML but `JS_RUNTIME` logs uncaught errors or missing runtime, root cause is JS runtime/bootstrap.
- If DOM has posts but `CSS_VISIBILITY` shows zero height or hidden display/visibility/opacity, root cause is CSS/layout visibility.
- If recovery logs show the same valid HTML reloaded and still zero DOM posts/height, recovery is not addressing the actual failing layer.

## 4. Minimal Proposed Fix

No fix proposed yet. The minimal fix must target the first failing checkpoint shown by `ThemeBlankDiag`, without adding more recovery systems or retry timers.

## 5. Files Likely Needing Modification

- `app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/theme/ThemeApi.kt`
- `app/src/main/java/forpdateam/ru/forpda/model/data/remote/api/theme/ThemeParser.kt`
- `app/src/main/java/forpdateam/ru/forpda/model/interactors/theme/ThemeUseCase.kt`
- `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeTemplate.kt`
- `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/modules/ThemeWebController.kt`
- `app/src/main/java/forpdateam/ru/forpda/ui/fragments/theme/ThemeFragmentWeb.kt`
- `app/src/main/assets/forpda/scripts/modules/theme.js`
- Theme CSS under `app/src/main/assets/forpda/styles/**` only if `CSS_VISIBILITY` proves posts exist but are hidden.

## 6. What NOT To Change

- Do not add another recovery system.
- Do not add more retry timers.
- Do not rewrite `theme.js`.
- Do not change render flow before identifying the first failing layer.
- Do not modify unrelated screens.
- Do not change UI behavior permanently.
- Do not add dependencies.

## Runtime Capture Steps

1. Install/run a debug build.
2. Reproduce the blank topic screen.
3. Capture logcat filtered by `ThemeBlankDiag`, plus `ThemeBlank` if recovery behavior is needed:

```bash
adb logcat -s ThemeBlankDiag ThemeBlank
```

4. Classify the failure by the first checkpoint where expected post/content counts become zero, or where DOM posts exist but visibility/height is hidden.
