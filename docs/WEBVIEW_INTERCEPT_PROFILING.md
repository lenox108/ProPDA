# WebView Intercept Profiling

## Scope

R01 inspected `shouldInterceptRequest` implementations under
`app/src/main/java`.

## Intercept Paths

| File | Path | Synchronous work | Risk |
| --- | --- | --- | --- |
| `app/src/main/java/forpdateam/ru/forpda/common/webview/CustomWebViewClient.kt` | `app_cache:avatars?nick=...` | `AvatarRepository.getAvatarSync()`, `ForPdaCoil.loadBitmapSync()`, PNG compression to `ByteArray` | High for scroll/load jank if many avatars miss cache. |
| `app/src/main/java/forpdateam/ru/forpda/common/webview/CustomWebViewClient.kt` | `app_cache:avatars?url=...` | `ForPdaCoil.loadBitmapSync()`, PNG compression to `ByteArray` | Medium/high depending on image cache hit rate and avatar size. |
| `app/src/main/java/forpdateam/ru/forpda/model/repository/avatar/AvatarRepository.kt` | `getAvatarSync(nick)` | `runBlocking(Dispatchers.IO)` around Room/cache lookup | Blocks caller until DB/cache lookup completes. |
| `app/src/main/java/forpdateam/ru/forpda/common/ForPdaCoil.kt` | `loadBitmapSync(context, url)` | `runBlocking(Dispatchers.IO)` plus `imageLoader.execute(req)` returning a bitmap | May perform disk/network/cache work synchronously from WebView request handling. |

No other production `shouldInterceptRequest` override currently performs
custom response work.

## Added Instrumentation

Debug builds now log avatar intercept timings:

```text
CustomWebViewClient avatarIntercept type=<nick|url> resolveMs=<n> loadMs=<n> encodeMs=<n> totalMs=<n> bytes=<n>
```

These logs separate:

- avatar URL resolution;
- synchronous bitmap loading;
- PNG encoding / byte-array allocation;
- encoded byte size;
- total WebView intercept time.

## Worst Offenders To Measure

- Topic/news/search pages with many uncached `app_cache:avatars?nick=...`
  entries.
- First load after clearing Coil/HTTP cache.
- Slow network with many avatar URL lookups by nick.
- Large or animated avatar images that are decoded then re-encoded as PNG.

## Follow-up For R02

- Prefer cached bytes/streams over bitmap decode plus PNG re-encode. Initial
  R02 step added a bounded in-memory PNG response cache for repeated avatar
  intercepts.
- Avoid doing network-backed avatar lookup on the WebView intercept thread.
- Add size/time fallbacks so slow avatar requests return no custom response
  rather than blocking page resource loading.

## largeHeap Decision

`app/src/main/AndroidManifest.xml` still declares `android:largeHeap="true"`. It was not removed in this pass because there is no memory profile yet for large topics, image-heavy topics, repeated WebView reloads, and ImageViewer flows. Removing it should wait until the performance checklist is run with `largeHeap=false` on representative devices.
