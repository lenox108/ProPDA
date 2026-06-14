# PROPDA Hardening Report

## Hardened systems

- `ThemeFragmentWeb` view lifecycle: delayed work is now guarded by a view runtime generation token so stale callbacks from a destroyed view cannot mutate the next fragment instance.
- Theme WebView attachment: the WebView is detached from any previous parent before registration and removed from `refreshLayout` during host disposal.
- Theme WebView runtime controller: page callbacks, progress notifications, history captures, infinite-page updates, and JS callbacks now stop when the fragment view is disposed.
- WebView shutdown path: queued JS batches, pending lifecycle actions, scroll/direction listeners, dialogs, clients, focus state, and JS readiness are cleared in `ExtendedWebView.endWork()`.
- `theme.js` runtime: added explicit runtime liveness, cancellable runtime timers/RAF wrappers, and `destroyThemeRuntime(reason)` for Android-side teardown.
- Infinite scroll runtime: bootstrap timers, visible-page throttles, scroll listener state, loading flags, and presenter calls are guarded against destroyed runtime state.
- Scroll restore runtime: refresh anchor retries, bottom restore retries, bottom guards, diagnostics, and bottom scroll retries now use cancellable runtime scheduling.
- Gesture/listener cleanup: long-press post gesture listeners, link-source listeners, and scroll corrector listeners can be removed during runtime destroy.

## Removed risks

- Stale `postDelayed` tasks restoring refresh state or revealing overlays after `onDestroyView`.
- Duplicate WebView parent attachment during fragment recreation.
- WebView client and chrome callbacks touching presenter/UI after the theme view has been disposed.
- Queued JS flushes surviving WebView shutdown.
- JS restore retry storms continuing after Android destroys the WebView.
- Infinite scroll bootstrap timers and throttled visible-page updates stacking across repeated DOM/page events.
- Scroll corrector listeners retaining old post node references.
- JS bridge calls from infinite scroll and anchor UI paths when the native bridge is unavailable.

## Remaining technical debt

- `theme.js` still relies on broad global mutable state. The new reset/destroy path contains the risk but does not replace the architecture.
- `nativeEvents` does not expose listener removal, so DOM/PAGE handlers are guarded for idempotence instead of fully deregistered.
- Several legacy content transforms still attach per-node listeners and should eventually move to delegated handlers.
- `ScrollCorrector` remains mostly legacy behavior and is now made disposable, but its behavior is still difficult to reason about.
- Image pressure is still mainly mitigated by cleanup and guarded scheduling; there is no full image virtualization or pruning strategy.

## Dangerous runtime areas

- Refresh scroll restoration around `refreshRestoreId`, `loadAnchorPostId`, bottom restore, and late image/layout reflow.
- Hybrid infinite scroll near top/bottom thresholds, especially while restore suppression is active.
- DOM patching/infinite page insertion followed by post transforms and separator normalization.
- Native/JS bridge lifecycle around WebView recreation and delayed Android callbacks.
- Overlay state for topic hat, poll, page swipe, and bottom refresh during navigation/back/reopen.

## Recommended future cleanup plan

1. Add a small JS runtime registry for `nativeEvents` handlers so DOM/PAGE listeners can be explicitly removed, not only guarded.
2. Move post gesture, link-source capture, infinite scroll, and restore logic into small self-contained modules with `init()`/`destroy()` contracts.
3. Convert legacy per-node click listeners in transformed post content to delegated listeners on `.posts_list`.
4. Add instrumentation/manual QA scenarios for long sessions: open topic, refresh, navigate child topic/back, pagination, reader modes, reply, image viewer return, and repeated reopen.
5. Add targeted WebView lifecycle regression tests if the project gains a test harness for fragment recreation.
