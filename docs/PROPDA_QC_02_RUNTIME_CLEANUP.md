# PROPDA QC-02 Runtime Cleanup

## Cleaned Warnings

- Removed unconditional `theme.js` load logging from production WebView startup.
- Gated noisy theme scroll/restore logs behind the existing `PageInfo.debug`/runtime debug path.
- Replaced unconditional scroll, quote, image-disable and focus exception logging with debug-only runtime logging.
- Kept explicit diagnostic hooks (`__themeVoteDiag`, `__themePageSyncDebug`, `__themeScrollAnchorDiag`) available for manual debugging without default logcat noise.

## Fixed Runtime Risks

- Made theme anchor-scroll cancel listeners removable by replacing anonymous global handlers with named bind/unbind functions.
- Added cleanup for theme overlay viewport listeners during `destroyThemeRuntime`, avoiding duplicate resize/orientation handlers after WebView recreation.
- Prevented duplicate anchor click listener attachment in `transformAnchor()` by marking already-bound anchors.
- Hardened delayed image-viewer scroll restore so it does not run after the theme WebView controller has been disposed or the fragment view is gone.
- Guarded async DOM metrics callback after `evaluateJavascript()` to avoid late logging/presenter access after cleanup.
- Routed delayed fragment view tasks through a lifecycle-owned main handler and clear them in `onDestroyView()`, reducing stale tab-unread restore work after view destruction.

## Remaining Suspicious Areas

- `theme.js` still has several legacy direct global listeners and DOM mutation entry points outside a unified lifecycle wrapper; they are now mostly debug-gated or explicitly cleaned where touched.
- Existing diagnostic logs for vote/page-sync remain opt-in but still use `console.log` when those flags are enabled.
- WebView destroy still depends on `ExtendedWebView.endWork()` calling `loadUrl("about:blank")`; deeper teardown behavior should be checked with device logcat.
- Manual rotation/background/rapid-navigation checks were not available in this pass.

## Known Technical Debt

- `theme.js` remains a large shared runtime module with mixed rendering, restore, gesture, and diagnostics responsibilities.
- Scroll restore has overlapping native and JS paths; it is guarded, but difficult to reason about without runtime traces.
- Some lifecycle protection is split between `ThemeFragmentWeb`, `ThemeWebController`, `ExtendedWebView`, and JS runtime state.
- WebView callback ownership is still implicit in several call sites rather than enforced through a single safe evaluation helper.

## Recommended Future Runtime Cleanup Targets

- Add a small, local `safeEvaluateJavascript` helper for Theme WebView callbacks to standardize disposed/view checks.
- Inventory all `nativeEvents.addEventListener` registrations and document which ones are one-shot, idempotent, or teardown-bound.
- Run a device logcat pass for topic open/close, rotation, image open/close, reply panel open/close, back navigation, hybrid pagination, and process death restore.
- Consider moving remaining opt-in JS diagnostics behind a single `ThemeDebug` object to make production logging policy easier to audit.
