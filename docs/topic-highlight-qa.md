# Topic post highlight — manual QA checklist

This checklist exercises the topic post highlight end-to-end on a real device
(Android, WebView-based topic rendering). The feature highlights the **first
unread post** of an unread topic, the **last read / last viewed post** of an
already-read topic, and (optionally) the **explicit** post of a deep link.

Filter logcat for diagnostic events:

```
adb logcat -s PPDA_TOPIC_HIGHLIGHT
```

## Pre-flight

- [ ] Build type: **debug** (only debug builds emit the highlight diagnostics).
- [ ] Clear the app's data to start from a known state.
- [ ] Log in as a user that has the following topics in favorites:
  1. An **already-read** topic with at least 20 posts.
  2. An **unread** topic with at least 20 posts.
  3. A topic with an **explicit** post (bookmark link from another topic).

## Topic open

- [ ] **1. Open already-read topic** → the last read post is highlighted (left
      accent line + subtle background tint, NOT a pressed/selected look). Logs
      show `event=highlight_target_resolved type=LastRead postId=<id>` and
      `event=render_highlight_applied appliedSuccessfully=true`.
- [ ] **2. Open unread topic** → the first unread post is highlighted. Logs
      show `event=highlight_target_resolved type=FirstUnread`. The highlight is
      clearly distinguishable from `LastRead` (slightly stronger accent colour).
- [ ] **3. Open explicit post link** → the targeted post is highlighted. Logs
      show `event=highlight_target_resolved type=Explicit`. When the post
      exists on the page and there is also an unread target, the unread
      highlight wins; the explicit highlight only applies when there is no
      unread or the unread target is on a different page.

## Stability

- [ ] **4. Refresh topic** → after `pull-to-refresh`, the same post is still
      highlighted. `renderGenerationId` increases; logs show
      `event=render_highlight_applied generationId=<new>`.
- [ ] **5. Change page** → tap page-2 button. On a page that does not contain
      the highlight target, no post is highlighted; logs show
      `event=highlight_target_missing reason=last_read_off_page` (or
      `unread_off_page`, `no_inputs`).
- [ ] **6. Smart button (top / bottom)** → tapping "to bottom" or "to top"
      does not lose the highlight. The highlight remains on the same post.
- [ ] **7. Rotate screen** → after rotation, the same post is highlighted
      (if it is on the rendered page). The view model survives configuration
      changes; the highlight state is part of the renderer state.
- [ ] **Stale callback** → simulate by sending two
      `window.PPDA_applyHighlight` calls back-to-back with a stale
      `generationId` from the developer console. Logs show
      `event=stale_highlight_ignored`. The visual highlight does not flicker.

## Rendering modes

- [ ] **8. Classic mode** (`Settings → Topic rendering → Classic WebView`) →
      highlight works as above. The template applies the
      `post-highlight-*` class on the post's `<div>`. Logs show
      `event=render_highlight_applied mode=classic`.
- [ ] **9. Hybrid mode** (`Settings → Topic rendering → Hybrid (RecyclerView
      + WebView)`) → highlight works on the WebView body for each item. Logs
      show `event=render_highlight_applied mode=hybrid`.

## Themes

- [ ] **10a. Light theme** → accent is visible against light background.
- [ ] **10b. Dark theme** → accent is visible against dark background.
- [ ] **10c. AMOLED (true black)** → accent is visible; background tint
      does not wash out the post body.

## Density

- [ ] **11a. Compact** → highlight accent line does not push the post body;
      the post content (text, images, code) remains readable.
- [ ] **11b. Super-compact** → same as compact; accent line still visible.
- [ ] **11c. System font** → highlight does not interact with system font
      metrics; accent line still aligned.

## Diagnostics verification

For each successful test case, the following log lines must be present in
logcat under the `PPDA_TOPIC_HIGHLIGHT` tag, in this order:

1. `event=highlight_resolve_started` (resolver was called with the right
   inputs).
2. `event=highlight_target_resolved` (or `highlight_target_missing` for the
   off-page case).
3. `event=render_highlight_applied` (the template and/or JS fallback
   actually applied the highlight).

If a highlight is expected but not visible:

- Check `event=highlight_target_missing reason=<reason>` for why the resolver
  produced `None`.
- Check `event=render_highlight_applied appliedSuccessfully=false` for why
  the renderer could not find the post anchor.
- Check `event=stale_highlight_ignored` for a dropped JS callback.
- Check `event=highlight_failed_post_not_found` (emitted when the post id
  is on the page but the per-post class binding did not match).

## Pass criteria

The feature is considered passing when:

1. All 11 checklist items above pass on a debug build.
2. No `event=highlight_failed_post_not_found` lines are emitted in normal
   flow (the resolver's `postId in pagePostIds` guard must catch them
   first).
3. The log lines are emitted in the correct order for a normal open.
4. No visual regression: the highlight is subtle, not a pressed/selected
   look; the post content remains readable in all density / theme / mode
   combinations.
