# PROPDA Device QA Matrix

Date: 2026-05-21

Scope: practical manual QA checklist for PROPDA beta/release verification on real devices and emulators. This checklist is documentation-only and does not change app code, Gradle configuration, dependencies, or runtime behavior.

## Goal

Confirm that the release APK is usable on representative Android devices before beta or release sign-off. A release candidate is not fully verified until the critical flows below are checked on the device matrix and all release blockers are either absent or explicitly triaged.

## Device Matrix

Run at least one smoke pass per Android band and cover both navigation modes across the full matrix. Prefer real devices for the modern/common bands and keep at least one low-end emulator or physical device for memory and WebView stress.

| Target | Device class | Android | Navigation | Display/theme | Required focus |
| --- | --- | --- | --- | --- | --- |
| Low-end legacy | 2-3 GB RAM, older CPU, small/medium screen | Android 8 or 9 | 3-button navigation | Default font, light theme | Startup, login/session, long topic scroll, image-heavy topic, offline recovery |
| Mid-range baseline | 4-6 GB RAM, common 1080p device | Android 10 or 11 | Gesture navigation | Default font, light theme | Topic open/reopen, Smart Button, Reader Modes, reply/quote/edit menu |
| Common current | Recent Samsung/Xiaomi/Pixel class device | Android 12 or 13 | Gesture navigation and 3-button navigation if available | Large font scale, dark and light theme if supported | Insets/snackbars, images, external links, search result open, favorites refresh |
| Modern release | Current Pixel/Samsung or emulator image | Android 14 or 15 | Gesture navigation | Default and large font scale, dark and light theme if supported | Background/foreground, long session, WebView stability, badge/read-state correctness |

Coverage notes:

- Gesture navigation and 3-button navigation must both be verified before release sign-off, even if they are covered on different devices.
- Large font scale should be checked at 1.3x or the closest device setting that visibly changes topic and menu layout.
- Dark/light theme applies only when the build/device supports the relevant app or system theme path.
- Record WebView version for every run because topic, image, and article behavior can vary by WebView package.

## Release Verification Checklist

Use the same signed APK/build that is intended for beta or release distribution. Start from a clean install for the first pass, then repeat selected flows after an upgrade install if upgrade testing is in scope.

For each item, record PASS, FAIL, BLOCKED, or NOT APPLICABLE, plus device ID, Android version, app version/build, and WebView version.

### 1. App Startup

- Launch from cold start after install.
- Confirm the main screen appears without crash, ANR, endless spinner, or blank root screen.
- Rotate the device if supported by the app configuration and confirm the screen remains usable.
- Kill the app from recents and relaunch.

Expected result: app starts reliably and restores to a sensible screen without crash loops.

### 2. Login And Session

- Open the login flow from a clean install.
- Sign in with a test account.
- Fully close and reopen the app.
- Confirm the session remains valid and authenticated sections are available.
- Sign out if the app exposes sign-out, then verify protected actions require authentication again.

Expected result: login succeeds, session persistence is stable, and auth state is not lost unexpectedly.

### 3. Open Topic

- Open a normal forum topic from the forum list, favorites, or a known link.
- Confirm the topic title, posts, avatars, controls, and WebView content render.
- Watch for blank topic, stuck loading state, JavaScript errors visible to the user, or missing post content.

Expected result: topic content renders completely and interactive controls are available.

### 4. Reopen Topic

- Open a topic, leave it with Back or navigation tabs, then open the same topic again.
- Repeat after app background/foreground.
- Repeat after process recreation if possible by enabling "Don't keep activities" only for a targeted debug pass, not as the default release pass.

Expected result: reopened topic does not go blank, duplicate WebView attach issues do not occur, and scroll/history state is reasonable.

### 5. Long Topic Scroll

- Open a topic with many posts or multiple pages.
- Scroll from top to bottom and back for at least two minutes.
- Trigger pagination or jump-to-unread if available.
- Watch for jank, missing posts, jumpy scroll restoration, stuck spinner, or memory pressure.

Expected result: scrolling remains usable and the app does not crash or lose topic content.

### 6. Image-Heavy Topic

- Open a topic with many inline images, avatars, spoilers, or attachments.
- Scroll while images are loading.
- Tap several images and return to the topic.
- Repeat on low-end Android 8/9 if available.

Expected result: images load or fail gracefully, image viewer returns to the topic, and the app remains responsive.

### 7. Smart Button

- Open a topic where Smart Button behavior is expected.
- Verify the button appears in the correct state.
- Use it for unread/next/page navigation according to the current topic state.
- Repeat after scrolling, background/foreground, and reopening the topic.

Expected result: Smart Button actions are correct and do not trigger duplicate or stale navigation.

### 8. Reader Modes

- Toggle each available Reader Mode in a topic.
- Confirm text, quotes, spoilers, code blocks, images, and post controls remain readable.
- Reopen the topic and confirm the expected Reader Mode state.
- Repeat with large font scale.

Expected result: Reader Modes improve readability without hiding critical controls or breaking layout.

### 9. Reply, Quote, And Edit Menu

- Open the post action menu.
- Verify reply, quote, full quote, and edit menu entries according to account permissions.
- Start each action far enough to confirm the correct editor/action target opens.
- Cancel safely without posting accidental content.

Expected result: actions target the correct post, destructive actions are not triggered accidentally, and unavailable actions are not shown as usable.

### 10. Answers Badge And Read-State

- Open a topic or section that shows answers/unread badges.
- Mark content as read by opening it.
- Navigate away, refresh, and return.
- Verify badge count and read-state update consistently.
- Repeat after background/foreground and after temporary offline mode.

Expected result: unread/answers badges do not stay stale, disappear incorrectly, or reappear after read state is confirmed.

### 11. Favorites Refresh

- Open Favorites.
- Pull to refresh or use the available refresh action.
- Open an updated favorite topic, return, and refresh again.
- Confirm counters, topic rows, and duplicate rows remain correct.

Expected result: Favorites refresh completes without duplicate rows, stale unread counters, or crashes.

### 12. Search Result Open

- Run a forum/topic search that returns posts.
- Open a result that should navigate to a specific topic/post.
- Return to search results and open a second result.
- Repeat with a user-specific or topic-specific search if available.

Expected result: search results open the intended topic/post and preserve enough context for Back navigation.

### 13. News And Article Images

- Open the News feed or article section.
- Open several articles with images, polls, or fallback images.
- Tap article images and external article links if present.
- Repeat after clearing app cache if image fallback behavior is under investigation.

Expected result: article content and images render without parser crashes, blank article body, or unsafe link handling.

### 14. External Links

- Tap trusted http/https links from topic and article content.
- Tap or test known unsafe schemes only if safe test content is available.
- Confirm the app uses the expected in-app or external browser behavior.
- Verify blocked/unsupported links fail safely and visibly.

Expected result: safe links open as intended, unsafe schemes are blocked or ignored safely, and no unexpected app/internal action is triggered.

### 15. Snackbar And Insets

- Trigger snackbars, error messages, and bottom actions.
- Repeat with gesture navigation and 3-button navigation.
- Repeat with keyboard visible in reply/search fields.
- Repeat with large font scale.

Expected result: snackbars and controls are not hidden behind navigation bars, gesture areas, or keyboard.

### 16. Offline And No Network

- Start online, open a topic, then enable airplane mode or disable network.
- Attempt refresh, open another topic, open Favorites, and open Search.
- Restore network and retry.
- Confirm error messages are clear and retry works.

Expected result: offline state does not crash or corrupt read-state/session data, and recovery works after network returns.

### 17. Background And Foreground

- Open a topic or article, background the app for 1-5 minutes, then foreground it.
- Repeat while image loading is in progress.
- Repeat during login/session-sensitive screens if safe.

Expected result: app resumes without blank WebView, stale loading state, duplicate actions, or session loss.

### 18. Long Session 30+ Minutes

- Use the app continuously for at least 30 minutes on one common current or modern device.
- Mix topic reads, long scroll, image opens, Reader Modes, Favorites refresh, Search, News/articles, and external links.
- Keep logcat running during the session.

Expected result: no crash, ANR, runaway memory growth, severe scroll jank, badge/read-state drift, or unrecoverable blank screens.

## Bug Report Template

Use this template for every failed, blocked, or suspicious QA item:

```text
Title:
Severity: P0 / P1 / P2 / P3
Device model:
Android version:
Navigation mode: gesture / 3-button
Font scale:
Theme: light / dark / system / not applicable
WebView package and version:
App version/build:
Install type: clean install / upgrade install
Network state: online / offline / flaky

Steps to reproduce:
1.
2.
3.

Expected result:

Actual result:

Frequency: always / often / intermittent / once
Regression: yes / no / unknown
Screenshot or video:
Logcat attached: yes / no
Additional notes:
```

Minimum evidence for release-critical bugs:

- Screenshot or screen recording for visible UI failures.
- Logcat covering at least 30 seconds before and after the failure.
- Exact topic/article/search link or reproducible navigation path when content-specific.
- APK filename or build SHA if available.

## Release Blocker Categories

Treat the following as release blockers until fixed, explicitly waived, or downgraded by release owner review:

- P0 crash or ANR in startup, login, topic open, topic scroll, reply/editor entry, Favorites, Search, News/article open, or background/foreground.
- Blank topic, blank article, endless spinner, or unrecoverable WebView state in normal online use.
- Login broken, session immediately lost, or authenticated areas unusable after successful login.
- Unread, Answers badge, or read-state broken in a way that misleads users after refresh/reopen.
- Data loss or destructive action bug, including accidental delete/edit/vote/report/post action or action targeting the wrong post.
- Unsafe external link behavior, including unsupported schemes launching unexpectedly, blocked links triggering internal actions, or untrusted content reaching privileged app behavior.

## Sign-Off Summary

Before beta/release sign-off, attach or link:

- Completed device matrix with PASS/FAIL/BLOCKED status per device.
- Bug reports for every failed or suspicious item.
- Confirmation that all release blocker categories were checked.
- Logcat from the 30+ minute long session.
- Explicit risk acceptance for any known failing automated tests or manual QA gaps.
