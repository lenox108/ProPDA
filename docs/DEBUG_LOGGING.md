# ForPDA DEBUG diagnostic logging

Structured traces are emitted **only in DEBUG builds** (`BuildConfig.DEBUG`). Release builds compile the helpers out at call sites and do not log.

Central API: `FpdaDebugLog` (`app/src/main/java/forpdateam/ru/forpda/diagnostic/FpdaDebugLog.kt`).

Helper functions (avoid boilerplate):

| Helper | Areas / tags |
|--------|----------------|
| `FpdaDebugLog.logQms(QmsArea, event, fields)` | OPEN, NETWORK, PARSE, STATE, CACHE, WEBVIEW, CHAT |
| `FpdaDebugLog.logTheme(ThemeArea, event, fields)` | OPEN, LOAD, RENDER, SCROLL, SMART_BUTTON |
| `FpdaDebugLog.logSmartButton(event, fields)` | FPDA_SMART_BUTTON |
| `FpdaDebugLog.logArticle(ArticleArea, event, fields)` | OPEN, PARSE, POLL, RENDER, CACHE |
| `FpdaPipelineLog.*` | Thin wrappers for QMS WebView stages, theme load/render, article render |

Each line is a single log record: `event=... key=value ...` (no JSON wrapper, easy to grep).

Use `traceId` / `generationId` / `requestId` fields to correlate one user action across tags.

## Logcat tags

| Tag | Area |
|-----|------|
| `FPDA_QMS_OPEN` | QMS dialog open pipeline (nav, cache/network/parse outcome) |
| `FPDA_QMS_NETWORK` | QMS HTTP fetch / retry |
| `FPDA_QMS_PARSE` | QMS HTML parse phases |
| `FPDA_QMS_STATE` | `QmsThreadUiState` transitions |
| `FPDA_QMS_CACHE` | In-memory QMS thread cache |
| `FPDA_QMS_WEBVIEW` | QMS WebView DOM / inject / evalJs |
| `FPDA_QMS_CHAT` | QMS fragment + ViewModel lifecycle |
| `FPDA_THEME_OPEN` | Topic URL resolution, open intent, scroll-restore decisions |
| `FPDA_THEME_LOAD` | ThemeRepository cache/network, ViewModel `loadData` |
| `FPDA_THEME_RENDER` | Theme WebView render lifecycle, DOM ready |
| `FPDA_TOPIC_SCROLL` | WebView scroll commands, stale view callbacks |
| `FPDA_SMART_BUTTON` | Smart nav «В начало/конец» (goToEnd, scroll commands, fallback LoadSt) |
| `FPDA_ARTICLE_OPEN` | Article load pipeline (network → template) |
| `FPDA_ARTICLE_PARSE` | HTML parser version, sizes, selector outcome |
| `FPDA_ARTICLE_POLL` | News poll extract / sanitize / bind probe |
| `FPDA_ARTICLE_RENDER` | Article WebView load / DOM / timeout / render confirm |
| `FPDA_ARTICLE_CACHE` | In-memory / WebView cache hits and empty rejections |
| `FPDA_STATE_RACE` | `requestId` / `generation` stale or cancelled work |
| `FPDA_NAV_BACKSTACK` | Tab stack and in-topic history push/pop |
| `FPDA_TOPIC_SWITCH` | Cross-topic tab reuse / alone-theme reload |
| `FPDA_COMMENTS_SECTION` | Inline news comments bind/render |

Aliases (same log stream, for older filters):

- `FPDA_TOPIC_OPEN` → `FPDA_THEME_OPEN`
- `FPDA_WEBVIEW_RENDER` → `FPDA_THEME_RENDER`
- `FPDA_ARTICLE_WEBVIEW` → `FPDA_ARTICLE_RENDER`

## Capture all diagnostic tags

```bash
adb logcat -c
adb logcat -s \
  FPDA_QMS_OPEN FPDA_QMS_NETWORK FPDA_QMS_PARSE FPDA_QMS_STATE FPDA_QMS_CACHE FPDA_QMS_WEBVIEW FPDA_QMS_CHAT \
  FPDA_THEME_OPEN FPDA_THEME_LOAD FPDA_THEME_RENDER FPDA_TOPIC_SCROLL FPDA_SMART_BUTTON \
  FPDA_ARTICLE_OPEN FPDA_ARTICLE_PARSE FPDA_ARTICLE_POLL FPDA_ARTICLE_RENDER FPDA_ARTICLE_CACHE \
  FPDA_STATE_RACE FPDA_NAV_BACKSTACK FPDA_TOPIC_SWITCH FPDA_COMMENTS_SECTION
```

Save to a file:

```bash
adb logcat -v time -s \
  FPDA_QMS_OPEN FPDA_QMS_NETWORK FPDA_QMS_PARSE FPDA_QMS_STATE FPDA_QMS_CACHE FPDA_QMS_WEBVIEW FPDA_QMS_CHAT \
  FPDA_THEME_OPEN FPDA_THEME_LOAD FPDA_THEME_RENDER FPDA_TOPIC_SCROLL FPDA_SMART_BUTTON \
  FPDA_ARTICLE_OPEN FPDA_ARTICLE_PARSE FPDA_ARTICLE_POLL FPDA_ARTICLE_RENDER FPDA_ARTICLE_CACHE \
  FPDA_STATE_RACE FPDA_NAV_BACKSTACK FPDA_TOPIC_SWITCH FPDA_COMMENTS_SECTION \
  > forpda-debug.log
```

### Filter examples

One QMS dialog open by trace id:

```bash
adb logcat -s FPDA_QMS_OPEN FPDA_QMS_NETWORK FPDA_QMS_PARSE FPDA_QMS_STATE FPDA_QMS_WEBVIEW FPDA_QMS_CHAT | grep 'traceId=YOUR_TRACE'
```

One topic load by trace id:

```bash
adb logcat -s FPDA_THEME_OPEN FPDA_THEME_LOAD FPDA_THEME_RENDER FPDA_TOPIC_SCROLL FPDA_SMART_BUTTON | grep 'traceId=YOUR_TRACE'
```

One article open by id:

```bash
adb logcat -s FPDA_ARTICLE_OPEN FPDA_ARTICLE_PARSE FPDA_ARTICLE_RENDER FPDA_ARTICLE_POLL FPDA_STATE_RACE | grep 'articleId=12345'
```

Smart button / goToEnd only:

```bash
adb logcat -s FPDA_SMART_BUTTON FPDA_TOPIC_SCROLL
```

## Repro: QMS dialog empty or stale

1. Install **devDebug** APK (`BuildConfig.DEBUG=true`).
2. Clear logcat, start QMS tags capture.
3. Open a dialog from the themes list (note `FPDA_QMS_OPEN event=themes_item_click`).
4. Compare:
   - `FPDA_QMS_OPEN` / `FPDA_QMS_NETWORK` / `FPDA_QMS_PARSE` pipeline phases
   - `FPDA_QMS_STATE event=transition` — Loading → Content/Empty/Error
   - `FPDA_QMS_WEBVIEW` — `dom_inject_*`, `eval_js_*`, `dom_content_complete`
   - `FPDA_STATE_RACE event=stale_ignored` — fast re-open race

## Repro: topic opens at wrong place (LAST_UNREAD)

1. Settings → Forums → set **Open topic** to **Last unread**.
2. Clear logcat, start theme tags capture.
3. Open a topic with unread indicator from favorites or list.
4. Compare:
   - `FPDA_THEME_OPEN` `event=load_url` / `event=resolution` — `userSetting`, `resolverReason`, `resolvedUrl`
   - `FPDA_THEME_LOAD` `event=load_start` → `network_success`
   - `FPDA_THEME_RENDER` `event=dom_content_complete` — `blockScrollRestoreForUnread`, `anchorPostId`
   - `FPDA_TOPIC_SCROLL` — `scroll_to_unread_scheduled` / `scroll_to_unread_executed`

## Repro: smart button «В конец» wrong page

1. Open a multi-page topic, tap «В конец».
2. In logs:
   - `FPDA_SMART_BUTTON event=go_to_end` — `transition`, `loadedPages`, `targetPage`
   - `FPDA_SMART_BUTTON event=go_to_end_fallback_load` — fallback LoadSt when page not in DOM
   - `FPDA_TOPIC_SCROLL event=scroll_to_bottom` / `scroll_exec` / `scroll_dropped`

## Repro: news article blank after “success”

1. Clear logcat, start article tags.
2. Open a news item from the feed.
3. Look for:
   - `FPDA_ARTICLE_OPEN` phases: `load_start` → `network_success` → `template_mapped`
   - `FPDA_ARTICLE_RENDER event=render_start` → `render_confirmed` or `webview_timeout`
   - `FPDA_ARTICLE_CACHE event=rejected_empty` / `EMPTY_SUCCESS_REJECTED`
   - `FPDA_ARTICLE_POLL event=bind_probe_start` / `webview_bind_probe` — poll DOM state
   - `FPDA_STATE_RACE event=stale_ignored`

## Privacy

Logs intentionally **exclude**:

- Cookies, auth tokens, passwords
- Full HTML or private message/comment bodies

They **may include**:

- Topic/article/dialog ids, sanitized URLs (sensitive query keys stripped), page numbers, post ids
- HTML **lengths**, durations, parser version, state transitions, error class names

Do not share log files publicly without reviewing them first.

## Optional legacy tags

Older ad-hoc tags (`RefreshScroll`, `ThemeRender`, `ThemeHistory`, `HybridScroll`, `ArticleOpenTrace`) may still appear alongside `FPDA_*` during migration. Prefer `FPDA_*` for new investigations.
