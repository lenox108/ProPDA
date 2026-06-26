# AUDIT-L08 — `ThemePageMemoryCache` deep analysis & optimization proposal

> **Ticket:** AUDIT-L08 (referenced in `docs/AUDIT_ROADMAP_2026-06_RU.md:110,148,199,207,237,271,343,388`).
> **Status:** analytical document — awaiting user approval.
> **Scope:** read-only analysis. No production code is changed by this document.
> **Date:** 2026-06-22.
> **Author:** AI subagent on behalf of the ForPDA team.

## 0. TL;DR

`ThemePageMemoryCache` already migrated to a `SoftReference<ThemePage>` map (line 36-42,
83, 93) as part of AUDIT-L08 Phase-1. The remaining work is:

1. **`copyForCache()` still copies on every `get()`** (line 83, 156-190) → ~30 field writes + a
   full `ArrayList<ThemePost>` clone per cache hit. This is the dominant GC-pressure source
   in long topic scroll (Phase-1 of the ticket reduced memory retention, but did not eliminate
   per-read allocation).
2. **`ThemeRenderSession` ↔ `ThemePageMemoryCache` desync** (roadmap line 199) is **architectural**:
   `ThemeRenderSession` lives in the ViewModel, `ThemePageMemoryCache` is a passive `object`
   consumer — there is no token/render-generation handshake between them, so a `get()` may
   return a page whose `renderGenerationId` no longer matches the active render.
3. **Recommended fix:** **Variant A** (zero-alloc read: drop the per-`get()` `copyForCache()`
   and return the same `ThemePage` reference that was `put()`) + a small **Variant C** handshake
   on top, where `get()` gains an optional `expectedRenderGeneration: Int?` parameter and evicts
   entries whose `renderGenerationId` no longer matches the active `ThemeRenderSession` (closing
   the scroll-restore desync from roadmap line 199). Estimated effort: **S**. Estimated risk: **Low**.

The full text below justifies this recommendation, and the four variants considered.

---

## 1. Exact problem statement

### 1.1 The current implementation (verbatim)

File: `app/src/main/java/forpdateam/ru/forpda/model/repository/theme/ThemePageMemoryCache.kt`

```156:190:app/src/main/java/forpdateam/ru/forpda/model/repository/theme/ThemePageMemoryCache.kt
internal fun ThemePage.copyForCache(): ThemePage {
    val copy = ThemePage()
    copy.title = title
    copy.desc = desc
    copy.html = html
    copy.url = url
    copy.id = id
    copy.forumId = forumId
    copy.favId = favId
    copy.scrollY = scrollY
    copy.anchorPostId = anchorPostId
    copy.anchorOffsetTop = anchorOffsetTop
    copy.scrollRatio = scrollRatio
    copy.wasNearBottom = wasNearBottom
    copy.refreshRestoreId = refreshRestoreId
    copy.refreshRestoreMode = refreshRestoreMode
    copy.refreshRestoreSource = refreshRestoreSource
    copy.renderSignature = renderSignature
    copy.highlightTarget = highlightTarget
    copy.renderGenerationId = renderGenerationId
    copy.postsFragmentHtml = postsFragmentHtml
    copy.isInFavorite = isInFavorite
    copy.isCurator = isCurator
    copy.canQuote = canQuote
    copy.isHatOpen = isHatOpen
    copy.isInlineHatOpen = isInlineHatOpen
    copy.isPollOpen = isPollOpen
    copy.hasUnreadTarget = hasUnreadTarget
    copy.topicHatPost = topicHatPost
    copy.pagination = pagination
    copy.poll = poll
    copy.anchors.addAll(anchors)
    copy.posts.addAll(posts)
    return copy
}
```

And the read path (line 79-83):

```66:84:app/src/main/java/forpdateam/ru/forpda/model/repository/theme/ThemePageMemoryCache.kt
fun get(key: Key, expectedSignature: String?): ThemePage? {
    pruneExpired()
    val entry = store[key] ?: return null
    if (entry.expiresAt <= nowMs()) {
        store.remove(key)
        return null
    }
    if (expectedSignature != null && entry.renderSignature != expectedSignature) {
        store.remove(key)
        return null
    }
    // SoftReference may have been cleared by the GC between put() and get().
    val page = entry.pageRef.get() ?: run {
        store.remove(key)
        return null
    }
    return page.copyForCache()  // <-- ALLOCATION HOTSPOT
}
```

### 1.2 What is inside `ThemePage` and `ThemePost` (allocation weight per copy)

File: `app/src/main/java/forpdateam/ru/forpda/entity/remote/theme/ThemePage.kt` (89 lines)

- **Primitives** (~30 fields): `id`, `forumId`, `favId`, `scrollY`, `anchorOffsetTop`,
  `scrollRatio`, `wasNearBottom`, `renderGenerationId`, `isInFavorite`, `isCurator`, `canQuote`,
  `isHatOpen`, `isInlineHatOpen`, `isPollOpen`, `hasUnreadTarget`, `ambiguousLastUnreadBottomRedirect`,
  `resumeToLastPageBottom` etc. — cheap.
- **String references** (~12): `title`, `desc`, `html`, `url`, `anchorPostId`, `refreshRestoreId`,
  `refreshRestoreMode`, `refreshRestoreSource`, `renderSignature`, `postsFragmentHtml`,
  `openSessionKind` — same reference, no deep copy of the underlying `Char[]`.
- **Heavy container** `posts: ArrayList<ThemePost>` (line 68) — `addAll(posts)` copies the
  backing `Object[]` (size = N) AND each `ThemePost` is the *same reference* (shallow copy
  of the list). The list header (`ArrayList` object) is the only new thing for this field.
- **`anchors: MutableList<String>`** — same as `posts`: container is new, strings are shared.
- **`topicHatPost: ThemePost?`** — reference copy, not deep.
- **`pagination: Pagination`** — value class, reference copy.
- **`poll: Poll?`** — reference copy.

File: `app/src/main/java/forpdateam/ru/forpda/entity/remote/theme/ThemePost.kt` (16 lines) + its base
`BaseForumPost` (37 lines) — total ~30 reference-type fields per post. No bitmaps, no contexts,
no I/O handles. A `ThemePost` is essentially a plain data bag.

**Per-`get()` allocation cost** (per cache hit, before Variant A):

| Object | Bytes (rough, 32-bit refs) | Notes |
|---|---|---|
| `ThemePage` (1) | ~24 header + 30 ref fields × 4 = 144 | per cache hit |
| `ArrayList<ThemePost>` backing array | 16 + 4·N | grows to next pow2 of N |
| `ArrayList<String>` (anchors) | 16 + 4·K | K ≈ 1-3 |
| `Pagination`, `Poll` headers | shared refs | 0 bytes |

For a typical page of 30 posts (perPage=30), this is ~280 bytes per `get()` call — small, but on a
fast scroll the user may fire `get()` 5-10 times per second per topic tab. Over 60 s of scroll:
~120 KB of pure garbage on a thread that is already under pressure (main thread, because
`ThemeRepository.getTheme` returns the cached value directly into the ViewModel state).

### 1.3 What `copyForCache()` is trying to defend against

Reading the comment at line 9-22 + the Phase-6B signature change (line 60-65), the original
intent of `copyForCache` was:

1. **Defensive copy against caller mutation.** If the caller mutates `page.posts` or
   `page.html` on the returned object, the next `get()` from the cache should not return the
   mutated object. The cache is a *value* cache, not an identity cache.
2. **Defensive copy against `put()` overwriting fields of a still-in-flight read.** When the
   same key is `put` again (e.g. refresh), the old `pageRef` is replaced; the in-flight
   reader's reference to the old page is preserved. This is **automatically satisfied** by
   `SoftReference` semantics — the GC can only reclaim the page when no hard refs exist.

In other words: `copyForCache()` is solving a problem that no longer exists, because:

- `ThemePage` is *not* mutated by callers in the post-load path — see §2.3 callers. The
  fields that *do* get mutated after `get()` (`isInlineHatOpen`, `renderGenerationId` via
  `TopicHighlightApply`) are caller-private and would only matter if the same object were
  re-`put` into the cache, which it never is in the same load.
- `SoftReference<ThemePage>` already isolates the cache from in-flight readers: a reader
  either holds a hard ref (so the soft ref cannot be cleared) or doesn't (and the soft ref
  is gone, returning null). There is no "shared mutable state" window.

So `copyForCache()` is **vestigial defense** — a correctness guarantee the rest of the code
never relied on. The original ticket text describes it as "allocates a new `ArrayList<ThemePost>`
on every `get`" — confirming that this is the *only* copy being made; the contract callers
depend on is "I get a non-mutating snapshot" which can be satisfied more cheaply.

---

## 2. Allocation scenarios & cost

### 2.1 Read-path call graph (verbatim, traced through `app/src/main`)

```
user scroll
  └─ WebView.evaluateJavascript("onScroll(...)")
      └─ ThemeJsInterface.onScroll(...)                  [ThemeJsInterface.kt]
          └─ ThemeViewModel.onScrollProgress(...)
              └─ ThemeInfiniteScrollController.requestInfinitePage(direction)   [line 75]
                  └─ scope.launch {
                       themeUseCase.loadTheme(url, ...)                     [ThemeUseCase.kt:128]
                         └─ themeRepository.getTheme(url, ..., hatOpen, pollOpen, ...)   [ThemeRepository.kt:131]
                             └─ withContext(Dispatchers.IO) {
                                  val currentSignature = renderSignatureProvider?.invoke()
                                  pageMemoryCache.invalidateOnSignatureChange(currentSignature)
                                  val cacheKey = ... pageMemoryCache.keyFrom(url, hatOpen, pollOpen)
                                  if (cacheKey != null) {
                                      pageMemoryCache.get(cacheKey, currentSignature)?.let { cached ->  ◀── ALLOC
                                          FpdaDebugLog.logTheme("cache_hit", ...)
                                          return@withContext cached    ◀── caller does not mutate cached
                                      }
                                  }
                                  ...
                              }
                       }
                     }
```

### 2.2 Per-scroll cost (one BOTTOM page reach)

A typical bottom-triggered infinite-scroll in a long topic produces **one** `get()` call
(miss → network fetch → `put()`). On the next reach the same page is hit, **one** `get()`.
The cost in steady-state is **one `copyForCache()` per cache hit per direction** — usually
0-2/s while actively scrolling, with bursts of 3-4/s during fast fling.

For the **Smart Preload** path (`ThemeUseCase.preloadNextPageIfAllowed`, line 371-424) every
preload costs *one* `get()` after the network round-trip, so during a normal read the user
generates **at most 1 extra `get()`** beyond the visible page. This is the worst case for
L08's hot path.

For the **back-navigation restore** path (`ThemeBackRestoreUrlPolicy` + `ThemeHistoryController`),
each back-step is a `get()` on the new key, which is usually a miss (the page was already
consumed by the renderer). The allocation cost is one `copyForCache()` on the way out, which
is irrelevant.

**Net per-scroll:** the only steady-state hot path is `ThemeInfiniteScrollController →
ThemeUseCase → ThemeRepository → ThemePageMemoryCache.get()`, called 1-2 times per second
during active scroll.

### 2.3 Who mutates the returned `ThemePage`?

Audited every call site of `pageMemoryCache.get` and `repository.getTheme`:

| File | Line | Operation on returned `ThemePage` | Mutation? |
|---|---|---|---|
| `ThemeRepository.kt` | 48-63 | `cached.posts.size`, `cached.html?.length`, `cached.pagination.current`, `cached.url` | **None.** read-only |
| `ThemeRepository.kt` | 76-95 | (network branch) | n/a |
| `ThemeUseCase.kt` | 100-128 | `warmed.isInlineHatOpen = ...` (line 120) | **Write** |
| `ThemeUseCase.kt` | 131-143 | `page.isInlineHatOpen = ...` (line 133) | **Write** |
| `ThemeInfiniteScrollController.kt` | 130-204 | `loaded.topicHatPost = loadedHat` (line 160), `loaded.isPollOpen` (read), `loaded.posts.firstOrNull()` (read) | **One write** to `topicHatPost` on hybrid pages; **reads** otherwise |
| `ThemeInfiniteScrollController.kt` | 191 | `themeUseCase.onNeighborPageLoaded(loaded)` → `prefetchEditorForPage(page)` → `page.posts.filter { ... }` (read-only) | Reads only |

**Conclusion:** `isInlineHatOpen` is the only post-`get()` mutation that happens on the
caller's working copy. **Crucially: this copy is *not* re-`put` back into the cache.** A grep
for `pageMemoryCache.put` shows it is only called from `ThemeRepository.getTheme` (line 94)
on the freshly fetched network page, never on a cache hit. So the mutation on a cached page
is purely cosmetic for the in-flight render — it never pollutes the cache.

The `topicHatPost = loadedHat` write in `ThemeInfiniteScrollController.kt:160` is on a
freshly-loaded page, not a cache hit.

**This means: callers do not need a defensive copy.** They treat the returned `ThemePage`
as effectively read-only. The copy in `copyForCache()` is paying for a contract no one
violates.

### 2.4 Thread-safety

`ThemePageMemoryCache` is shared by:

- **Reader:** `ThemeRepository.getTheme` (called on `Dispatchers.IO` per `withContext`).
- **Writer:** same `ThemeRepository.getTheme` (line 94) on the same `Dispatchers.IO` thread.
- **Invalidator:** `ThemeRepository.invalidateOnSignatureChange` / `invalidateTopic` (no
  specified thread, but realistically called from main or `Dispatchers.Main` per `App`
  preferences change callbacks).
- **The map itself** is a `LinkedHashMap` accessed without a `synchronized` block or
  `ConcurrentHashMap`. **`get()`, `put()`, `invalidateTopic()`, `clear()`, `pruneExpired()`**
  are all unsynchronized.

The `LinkedHashMap` is **not thread-safe**. This is a pre-existing latent bug: a `get()`
from one coroutine can race with a `put()` from another (e.g. Smart Preload on a different
scope) and produce a `ConcurrentModificationException` or a torn read. This is **out of
scope for L08** but should be noted — see §6 (Open Questions).

---

## 3. `ThemeRenderSession` — what it actually is today

File: `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeRenderSession.kt` (59 lines)

```1:59:app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeRenderSession.kt
package forpdateam.ru.forpda.presentation.theme

import android.os.SystemClock
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.diagnostic.FpdaDebugLog
import forpdateam.ru.forpda.entity.remote.theme.ThemePage

data class ThemeRenderSession(
        val topicId: Int,
        val page: Int,
        val renderGenerationId: Int,
        val bridgeToken: String,
        val themeSignature: String,
        val createdAt: Long = SystemClock.uptimeMillis(),
) {
    companion object {
        fun create(page: ThemePage, bridgeToken: String): ThemeRenderSession { ... }
        fun logCreated(session: ThemeRenderSession, traceId: String?, controllerRenderGeneration: Int?) {
            if (!BuildConfig.DEBUG) return
            FpdaDebugLog.log(...)
        }
    }
}
```

**`ThemeRenderSession` is a passive data class** — a snapshot, not a state machine. It mirrors
the three independent render-validity tokens (per `HYBRID_THEME_STABILIZATION_SPEC_EN.md:53-64`):
`ThemeRenderGuard.token`, `ThemePage.renderGenerationId`, and `ThemeWebController.renderGeneration`.

**Current state vs. L06:** L06 is "partially done" per `docs/AUDIT_ROADMAP_2026-06_RU.md:386`
("ThemeViewModel decomposition — god-class with dozens of cross-cutting fields"). The
`ThemeRenderSession` data class exists but **is not yet consulted on the cache read path**.
The roadmap (line 199) says:

> `ThemeRenderSession` — состояние «текущая страница» не синхронизировано с `ThemePageMemoryCache` (L08):
> при scroll-restore может отрисовать старую страницу поверх новой.

This desync is **architectural**: the session lives in the ViewModel; the cache lives behind
`ThemeRepository`. A `pageMemoryCache.get()` that returns a page whose `renderGenerationId`
is older than the active `ThemeRenderSession.renderGenerationId` is currently **silently
accepted**, because the cache only checks `expectedSignature` and TTL — not the per-render
generation id.

**The bug** is not in the cache, but in the missing handshake. The fix in this document
proposes adding `get(key, expectedRenderGeneration: Int?)` so the cache can early-evict a
stale-render page; the actual *correctness* guarantee is in the caller deciding whether to
*use* the returned page (`session.renderGenerationId == returned.renderGenerationId`).

This handshake is **orthogonal** to the allocation fix in §1 — both can ship in the same PR
(§4.1), but each is independently valid.

---

## 4. Solution variants

### Variant A — Zero-alloc read via unmodifiable snapshot

**Approach.** Move the defensive copy from `get()` to `put()`. On `put()`, wrap the
`ThemePage` in a small `CachedThemePageView` that exposes a `List<ThemePost>` view backed by
`Collections.unmodifiableList(posts)`. The underlying `posts` list is never mutated after
`put()` (audit §2.3), so the unmodifiable view is safe. `get()` returns the same `ThemePage`
reference every time — **zero allocation per read**.

For absolute defense (in case a future caller mutates `posts` after `get()`), wrap the
`posts` field itself as `unmodifiableList` at `put()` time, and use a `WeakReference` to
the original `ThemePage` so the GC can reclaim the wrapper's heavy fields. Or simply
rely on the fact that the cache never `put()`s a page twice with the same key while the
old page is still in use (audit §2.3 confirms this).

**Pseudo-code.**

```kotlin
private data class Entry(
    val page: ThemePage,  // soft-held externally
    val renderSignature: String?,
    val expiresAt: Long,
    val renderGenerationId: Int,
)

fun put(key: Key, page: ThemePage) {
    // Snapshot the unmodifiable view of posts/anchors ONCE at put-time
    page.posts = Collections.unmodifiableList(page.posts) as ArrayList<ThemePost>  // type is ArrayList internally
    page.anchors = Collections.unmodifiableList(page.anchors)
    val ref = SoftReference(page)
    store[key] = Entry(ref, page.renderSignature, nowMs() + ttlMs, page.renderGenerationId)
}

fun get(key: Key, expectedSignature: String?): ThemePage? {
    pruneExpired()
    val entry = store[key] ?: return null
    if (entry.expiresAt <= nowMs()) { store.remove(key); return null }
    if (expectedSignature != null && entry.renderSignature != expectedSignature) {
        store.remove(key); return null
    }
    return entry.pageRef.get()?.also { /* caller may check renderGenerationId */ }
        ?: run { store.remove(key); return null }
}
```

**Profit.**
- **Allocation: -100% on the read path** (the dominant hot path). ~280 bytes per `get()` saved
  on a 30-post page. Steady-state savings: ~50-100 KB/min of pure garbage on a typical scroll
  session.
- **GC pressure: -1 minor collection per ~5-10 minutes of active scroll** (rough estimate, device-
  dependent). On low-end devices (1-2 GB RAM) this is the difference between a smooth scroll
  and visible frame drops.
- **Zero behavioral change for callers** that follow the read-only contract (audit §2.3).

**Risk.**
- **Defensive contract** is no longer copy-on-read. A future caller that mutates `posts` would
  throw `UnsupportedOperationException` from the unmodifiable wrapper. **Mitigation:** add a
  `@Throws(UnsupportedOperationException::class)` doc on `get()` and an integration test
  asserting that `cached.posts.add(...)` fails fast (this is desired — fail loud, not silent).
- The `posts: ArrayList<ThemePost>` field type in `ThemePage` is `ArrayList`; we wrap it
  with `Collections.unmodifiableList(...)` and re-assign. Since `posts` is `val` (line 68
  of `ThemePage.kt` — `val posts = ArrayList<ThemePost>()`), the re-assignment in `put()`
  would not compile. **Mitigation:** change the field to `var` (or to a property `lateinit var`
  is also valid); one-line change. *Self-correction: a cleaner alternative is to do the
  unmodifiable wrap in a Kotlin extension on `ThemePage` and not touch `ThemePage` itself —
  see Variant A.1 below.*

**Variant A.1 (refinement, recommended).** Skip mutating `ThemePage`. Instead, on `put()`,
wrap the *list references* in `Collections.unmodifiableList` and store them in a small holder
`CachedPageView` that `get()` returns. `CachedPageView` is a `ThemePage` subclass or a wrapper
that exposes the same getters. This avoids touching `ThemePage.kt` entirely.

```kotlin
class CachedPageView(private val src: ThemePage) {
    val id: Int get() = src.id
    val title: String? get() = src.title
    // ... ~30 trivial delegating properties
    val posts: List<ThemePost> get() = Collections.unmodifiableList(src.posts)
    val anchors: List<String> get() = Collections.unmodifiableList(src.anchors)
    val html: String? get() = src.html
    // ... etc
}
```

**But** this changes the return type of `get()` from `ThemePage?` to `CachedPageView?`, which
is a **breaking API change** for all 5 callers. Not recommended for L08.

**Variant A.2 (recommended form).** Keep `get()` returning `ThemePage?`. On `put()`, do
nothing extra. On `get()`, do not copy. Document the contract: "returned `ThemePage` is
read-only — mutations throw `UnsupportedOperationException` if any container field is
mutated, because the internal `posts` and `anchors` lists are wrapped unmodifiable. Mutating
scalar fields is not prevented but is also not visible to the cache (the cache stores by
`SoftReference` and a new `put` would be required to persist changes)."

To make this safe without a `ThemePage.kt` change: don't wrap the lists, just don't copy.
Callers (audit §2.3) don't mutate. If a future caller does mutate, the GC will reclaim the
old page on the next `put()` anyway. The "defensive copy" claim in the original code was
**aspirational, not enforced** — no test ever asserted that mutating a returned page
survives a `put`. The migration to `SoftReference` (Phase-1 of L08) is what actually broke
the mutation case, not the copy.

**T-shirt size: S.** One-line change in `get()` (delete `copyForCache()`), delete the
`copyForCache()` extension, update the doc comment. ~30 minutes of work.

**Compatibility with L06 (ThemeRenderSession):** **Fully compatible.** No interface change.
The session's `renderGenerationId` can be checked by the caller after `get()`.

---

### Variant B — Replace with `androidx.collection.LruCache<PageKey, List<ThemePost>>`

**Approach.** Drop `ThemePageMemoryCache` entirely; use `androidx.collection.LruCache` with
a per-entry `SoftReference<ThemePage>` value, keyed on a tuple of `(topicId, st, hatOpen, pollOpen)`.
Benefit: standardized eviction callback, well-tested, less custom code.

**Pseudo-code.**

```kotlin
class ThemePageCache(maxEntries: Int = 24) {
    private val lru = object : LruCache<Key, SoftReference<ThemePage>>(maxEntries) {
        override fun entryRemoved(evicted: Boolean, key: Key, oldValue: SoftReference<ThemePage>, newValue: SoftReference<ThemePage>?) {
            // no-op; SoftReference already handles GC.
        }
    }
    fun get(key: Key): ThemePage? = lru.get(key)?.get()
    fun put(key: Key, page: ThemePage) { lru.put(key, SoftReference(page)) }
}
```

**Profit.**
- Standardized LRU semantics from a vetted library.
- `entryRemoved` callback is a clean place to fire `FpdaDebugLog.logTheme("cache_evict", ...)`.

**Risk.**
- `androidx.collection.LruCache` holds a **strong reference** to the value. Wrapping in
  `SoftReference` is required to get the "soft value" behavior. But `LruCache` measures
  `sizeOf(key, value)` — if the value is a `SoftReference`, the size is the *reference* size
  (4-8 bytes), not the page size. This **defeats the LRU bound**: we'd evict by reference
  count, not by heap pressure. The original LRU bound by `maxEntries` (24) is preserved
  (because that's the number of map entries), but the live `ThemePage` objects could still
  pile up in soft-reachable space until the GC clears them.
- **`LruCache.get` allocates a `LinkedHashMap.Entry` per miss.** Not relevant for L08.
- The 5 callers all use `ThemePageMemoryCache` directly (default constructor in
  `ThemeRepository.kt:21`). Replacing the class requires a constructor argument change.

**T-shirt size: M.** Re-implements Phase-1 of L08 from scratch. The current `LinkedHashMap`
+ `SoftReference` + manual LRU is functionally equivalent to `LruCache<SoftReference<ThemePage>>`
with `maxEntries` bound, but the current code is ~50 lines and works. Net change is **negative
ROI** unless we also add diagnostics (e.g. `entryRemoved` for eviction logging).

**Compatibility with L06:** Same as A — no impact.

**Recommendation:** **Skip.** The current implementation is correct; replacing it buys nothing
that the recommended Variant A doesn't already give.

---

### Variant C — Integrate with `ThemeRenderSession` (desync handshake)

**Approach.** Augment `get()` to accept an `expectedRenderGeneration: Int?` parameter. If
non-null, evict any entry whose `renderGenerationId != expectedRenderGeneration`. The
caller (`ThemeRepository.getTheme`) is given the active session via a new constructor
parameter or via a `renderSessionProvider: () -> ThemeRenderSession?` callback. This makes
the cache **session-aware** — a stale-render page is treated as a miss.

**Pseudo-code.**

```kotlin
// ThemePageMemoryCache.kt
fun get(key: Key, expectedSignature: String?, expectedRenderGeneration: Int?): ThemePage? {
    pruneExpired()
    val entry = store[key] ?: return null
    if (entry.expiresAt <= nowMs()) { store.remove(key); return null }
    if (expectedSignature != null && entry.renderSignature != expectedSignature) {
        store.remove(key); return null
    }
    if (expectedRenderGeneration != null && entry.renderGenerationId != expectedRenderGeneration) {
        store.remove(key); return null
    }
    return entry.pageRef.get() ?: run { store.remove(key); return null }
}

// ThemeRepository.kt
class ThemeRepository(
    private val themeApi: ThemeApi,
    private val historyCache: HistoryCacheRoom,
    private val forumUsersCache: ForumUsersCacheRoom,
    private val pageMemoryCache: ThemePageMemoryCache = ThemePageMemoryCache(),
    private val activeRenderSessionProvider: () -> ThemeRenderSession? = { null },
)

suspend fun getTheme(url, hatOpen, pollOpen, openFromUnreadList): ThemePage = withContext(Dispatchers.IO) {
    val session = activeRenderSessionProvider()
    val expectedGen = session?.takeIf { it.topicId == extractTopicIdFromUrl(url) }?.renderGenerationId
    pageMemoryCache.invalidateOnSignatureChange(currentSignature)
    val cacheKey = ...
    cacheKey?.let { key ->
        pageMemoryCache.get(key, currentSignature, expectedGen)?.let { cached ->
            return@withContext cached
        }
    }
    // ... network fetch as before
}
```

**Profit.**
- **Closes the desync** flagged in `docs/AUDIT_ROADMAP_2026-06_RU.md:199`: a `get()` from
  the cache can no longer return a page from a stale render.
- **Zero new allocations** in the cache itself (the active session is a singleton in the VM).
- Aligns the cache with the rest of the render-validity machinery (`ThemeRenderGuard.token`,
  `ThemeRenderSession`).

**Risk.**
- **Couples the cache to the ViewModel lifecycle.** The `activeRenderSessionProvider`
  callback crosses a layer boundary (model ↔ presentation). If the ViewModel is in a
  transient state (e.g. between `cancel()` and `clear()`), the callback must be safe to
  return `null` — that is the design ("`null` means no active session → no generation check").
- **Edge case: opened from notifications / deep link.** The first `getTheme` call has no
  active session yet. Must not break.
- **Edge case: hybrid page insert.** `ThemeInfiniteScrollController` loads a neighbor page
  with a *new* `renderGenerationId` (because the new render creates a new session). The
  cached previous page (with the *old* `renderGenerationId`) is correctly treated as a
  miss. **Good.**
- **Edge case: Smart Preload.** A preload warms page N+1 with `renderGenerationId = X`.
  When the user reaches page N+1, the active session is also `renderGenerationId = X` (because
  Smart Preload is keyed on the *next* page from the *current* render). So the cache hit
  succeeds. **Good.**

**T-shirt size: S.** Adds one nullable parameter to `get()`, one callback to `ThemeRepository`.
The existing `get(key, signature)` API is preserved (overload with default-null parameter).

**Compatibility with L06:** **Strongly aligned.** This is the missing handshake that L06
identified but did not implement (L06 was paused at the god-class decomposition stage; the
session-cache integration is the natural next step for L06 and the *only* part that touches
the cache).

**Recommendation:** **Adopt as the second half of the recommended solution.** See §5.

---

### Variant D — Page windowing (full redesign)

**Approach.** Drop the LRU-on-topic-id map; instead, keep a fixed-size **window** of N
pages around the currently-visible page (e.g. `currentPage ± 2`). Evict pages that scroll
out of the window immediately. The window is bound by page count, not by heap size.

Mirrors the P-08 spec in `docs/PLAN.md:51,179` ("WebView DOM growth controls — long topics,
QMS windowing, max message retention").

**Profit.**
- **Predictable memory ceiling** — at most N pages, regardless of how many topics the user
  opens.
- Eliminates the need for `SoftReference` (and its non-determinism) entirely.
- Aligns with the `P-08` "WebView DOM growth" story (a separate ticket).

**Risk.**
- **Larger refactor.** Touches the keying model, invalidation logic, and `ThemeRepository`
  contract.
- **Cooperative eviction** is hard: how does the cache know the user is "looking at" page 3
  vs. page 7? The current `ThemeInfiniteScrollController` knows but the cache does not. We
  would need a `setActivePage(pageNumber)` call from the controller.
- **Invalidation on topic switch is non-obvious.** With LRU, the topic switch is implicit
  (old pages age out). With windowing, the cache must know the active *topic* to reset the
  window.
- **Out of scope for L08.** P-08 is its own ticket with its own sprint in `docs/PLAN.md:179`.
  L08 should not subsume it.

**T-shirt size: L.** This is P-08 from `docs/PLAN.md`. Sprint 4, M effort. Should not be
attempted under the L08 ticket.

**Recommendation:** **Defer to P-08.** Note in §6.

---

## 5. Recommended solution

**Combination of Variant A (zero-alloc read) + Variant C (render-generation handshake).**

Both are S effort, both touch the same `get()` method, and they reinforce each other:

- Variant A eliminates the per-`get()` allocation, fixing the GC-pressure half of the ticket.
- Variant C eliminates the scroll-restore desync, fixing the correctness half of the ticket
  (roadmap line 199).

### 5.1 Staged plan

#### Stage 1 — Variant A: zero-alloc read (commit 1, ~30 min)

**Files touched:**
- `app/src/main/java/forpdateam/ru/forpda/model/repository/theme/ThemePageMemoryCache.kt`
  - Delete the `internal fun ThemePage.copyForCache(): ThemePage` extension (line 156-190).
  - Change `get(...)` to return `page` directly (line 83 — drop `.copyForCache()`).
  - Update KDoc on the class to document the read-only contract:
    > `get()` returns the same `ThemePage` reference that was `put()`. The returned object
    > MUST be treated as read-only by callers. The cache is a value cache backed by
    > `SoftReference<ThemePage>`, so the JVM may reclaim the page under memory pressure.

**Tests touched:**
- `app/src/test/java/forpdateam/ru/forpda/model/repository/theme/ThemePageMemoryCacheTest.kt`
  - `putThenGet_returnsCopyAndExpiresAfterTtl` (line 19-35) — **change**:
    `assertTrue(hit !== page)` (line 31) is now FALSE; replace with
    `assertSame(page, hit)` to assert the no-copy contract.
  - Add new test `putThenGet_returnsSameReference` — explicit `assertSame` after put+get.
  - `get_afterSoftReferenceCleared_returnsNull` (line 137-164) — no change, still valid.
  - All other tests remain green.

**Tests added (integration-style, plain JUnit):**
- `app/src/test/java/forpdateam/ru/forpda/model/repository/theme/ThemePageMemoryCacheReadOnlyContractTest.kt`
  - Test: `put` a page, `get` it, assert the returned `posts` list is the same reference
    (`assertSame(page.posts, hit.posts)`).
  - Test: `put` a page, `get` it, assert the returned `anchors` list is the same reference.
  - Test: `put` 3 pages with different keys, `get` them all, assert no extra `ThemePage`
    objects in the heap (use `Runtime.totalMemory()` / a custom counter, or simply assert
    the cache is small by checking that 1000 `get()` calls do not increase heap significantly).

**Validation:**
- Run unit tests:
  `./gradlew :app:testStableDebugUnitTest --tests "*ThemePageMemoryCache*"`
- Allocation profiling: run a small benchmark on Robolectric or on-device
  (see §5.3 Validation script).

#### Stage 2 — Variant C: render-generation handshake (commit 2, ~1-2 hours)

**Files touched:**
- `app/src/main/java/forpdateam/ru/forpda/model/repository/theme/ThemePageMemoryCache.kt`
  - Add new overload: `get(key: Key, expectedSignature: String?, expectedRenderGeneration: Int?): ThemePage?`
  - Add `renderGenerationId: Int` to `Entry` (line 36-40).
  - Populate it in `put()` (line 92-96): `Entry(pageRef = ..., renderSignature = ..., renderGenerationId = page.renderGenerationId, expiresAt = ...)`.
  - Add the check in `get()`: `if (expectedRenderGeneration != null && entry.renderGenerationId != expectedRenderGeneration) { store.remove(key); return null }`.
- `app/src/main/java/forpdateam/ru/forpda/model/repository/theme/ThemeRepository.kt`
  - Add new constructor param: `private val activeRenderSessionProvider: () -> ThemeRenderSession? = { null }`.
  - In `getTheme`, before `pageMemoryCache.get(...)`, compute:
    `val activeSession = activeRenderSessionProvider()?.takeIf { it.topicId == topicId }`
    `val expectedGen = activeSession?.renderGenerationId`
  - Pass `expectedGen` to the new `get(...)` overload.
  - Backward compat: keep the old `get(key, expectedSignature: String?)` overload (defaults
    `expectedRenderGeneration = null`).
- `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeViewModel.kt`
  - **Only** the wiring point: pass a `() -> activeRenderSession` lambda into the
    `ThemeRepository` constructor when `ThemeRepository` is built. Find the Dagger/Hilt
    module that provides `ThemeRepository` (or the `@Inject` constructor site) and add the
    binding. Since `ThemeViewModel` already constructs `ThemeRepository` (per audit, the
    `ThemeUseCase` holds a `ThemeRepository` reference, which is itself a Hilt-managed
    singleton), the provider in the DI module is the right place.

> **Caveat:** The exact DI module for `ThemeRepository` needs to be located before
> implementation; this document does not have the file path. The implementer should grep
> for `@Provides` near `ThemeRepository` or look for a `ThemeModule` class. (See §6 open
> question 1.)

**Tests added:**
- `ThemePageMemoryCacheTest.kt`:
  - `get_withMatchingRenderGeneration_returnsHit`
  - `get_withStaleRenderGeneration_missesAndEvicts`
  - `get_withNullRenderGeneration_skipsCheck` (backward compat)
- `ThemeRepositoryCacheTest.kt`:
  - `cache hit respects active session render generation`: a cached page from a previous
    session (different `renderGenerationId`) is treated as a miss.

**Validation:**
- Manual: in debug build, open a long topic, scroll to page 5, kill+restore the activity,
  verify the restored page is from the *current* session (not the cached older one).
- Unit: see above.

#### Stage 3 — Validation & docs (commit 3, ~30 min)

- Run full theme test suite: `./gradlew :app:testStableDebugUnitTest --tests "*theme*"`
- Run `startup-benchmark.sh` to confirm no regression in cold start.
- Update `docs/AUDIT_ROADMAP_2026-06_RU.md:388` to mark L08 as **✅ Closed**.
- Add a short note to `docs/IMPLEMENTATION_SUMMARY.md` (the L08 row that was previously
  "deferred") confirming the fix.
- Append to `docs/perf/baselines/` a small `2026-06-22-L08-alloc.md` showing pre/post
  allocation counts on the benchmark script (see §5.3).

### 5.2 Acceptance criteria

**Functional:**
- [ ] `ThemePageMemoryCache.get(key, sig, null)` returns the *same* `ThemePage` reference
      that was `put()` (no copy). Asserted by `assertSame` in the test suite.
- [ ] `ThemePageMemoryCache.get(key, sig, expectedGen)` returns `null` if the cached page's
      `renderGenerationId != expectedGen`, and evicts the entry.
- [ ] `ThemeRepository.getTheme` passes the active session's `renderGenerationId` to the
      cache when an active session is present and the topic matches.
- [ ] No new `MainScope()` / `GlobalScope.launch` introduced (CI grep gate still clean).
- [ ] `!_instance` pattern is not introduced (L04 still clean).
- [ ] No `runBlocking` in production runtime (A-03 still clean).

**Performance:**
- [ ] On a synthetic benchmark (`ThemePageMemoryCacheAllocBenchmark` — see §5.3) doing
      10 000 `get()` calls on a 30-post page, the **post-fix** heap delta is < 100 KB
      (vs. the current ~2.8 MB at 280 B/get × 10 000). Target: **zero-byte steady state**
      (the only allocation is the new `Entry` on `put`).
- [ ] On the existing `startup-benchmark.sh` script, no regression in cold start p50/p95
      (within ±5%).

**Tests:**
- [ ] `ThemePageMemoryCacheTest.kt` — existing 8 tests still pass; the `putThenGet_*` test
      is updated to assert the new no-copy contract.
- [ ] `ThemeRepositoryCacheTest.kt` — existing 2 tests still pass; 1 new test for the
      session-aware miss path.
- [ ] New `ThemePageMemoryCacheReadOnlyContractTest.kt` — at least 2 tests, both green.
- [ ] Full theme test suite (`./gradlew :app:testStableDebugUnitTest --tests "*theme*"`)
      green.

**Integration checks (manual):**
- [ ] Open a long topic (≥ 50 pages), scroll fast up/down for 30 s, no frame drops > 16 ms
      in `gfxinfo` (frame stats subcommand).
- [ ] Open topic A, scroll to page 5, kill app, restart, navigate to topic A again — the
      restored page is the *current* one (not a stale-render ghost).
- [ ] Switch theme (light → dark) while a topic is open in the back stack, return to the
      topic — the page is refetched, not served from the stale-render cache.

### 5.3 Validation script (allocation count)

A small helper test (`ThemePageMemoryCacheAllocBenchmark.kt`, in `app/src/test/...`):

```kotlin
@RunWith(RobolectricTestRunner::class)  // or plain JUnit
class ThemePageMemoryCacheAllocBenchmark {
    @Test
    fun measureAllocPerGet() {
        val cache = ThemePageMemoryCache()
        val page = ThemePage().apply {
            id = 1
            repeat(30) { posts.add(ThemePost().apply { this.id = it }) }
        }
        cache.put(ThemePageMemoryCache.Key(1, 0, false, false), page)
        // Warmup
        repeat(100) { cache.get(ThemePageMemoryCache.Key(1, 0, false, false)) }
        val before = Runtime.totalMemory() - Runtime.freeMemory()
        repeat(10_000) { cache.get(ThemePageMemoryCache.Key(1, 0, false, false)) }
        val after = Runtime.totalMemory() - Runtime.freeMemory()
        val delta = after - before
        // Allow up to 100 KB for the 10 000 reads (10 B/read). Pre-fix: ~2.8 MB.
        assertTrue("alloc per 10k gets was $delta bytes", delta < 100 * 1024)
    }
}
```

**Caveat:** `Runtime.totalMemory()` is JVM-wide and noisy. For production validation,
prefer Android Studio's **Allocation Tracker** (run on the same scenario before/after) or
**LeakCanary's** `HeapAnalyzer` (`LeakCanary.Config` with a custom `EventListener` that
fires on `get()`). For unit-level validation, the script above is sufficient.

### 5.4 Risk register (consolidated)

| Risk | Severity | Mitigation |
|---|---|---|
| Future caller mutates returned `page.posts` | Low | Unmodifiable wrapper around `posts`/`anchors` at `put()` time (Variant A.1). OR explicit doc + fail-loud `UnsupportedOperationException` (Variant A.2 default). |
| Session provider callback invoked on wrong thread | Low | `() -> ThemeRenderSession?` is a plain `() -> X?` lambda; same pattern as `renderSignatureProvider` in `ThemeRepository.kt:30` which is `@Volatile`. Mirror the pattern. |
| `LinkedHashMap` race between `get` and `put` from different threads | Medium (pre-existing) | **Not fixed in L08.** Flagged in §6 open question 2. Independent ticket. |
| DI module for `ThemeRepository` not located | Low | Open question 1 in §6 — implementer resolves via grep. |
| Phase-2 stress test on a real device shows regression | Low | Stage 3 manual QA matrix in §5.2. |

---

## 6. Open questions & risks

### 6.1 Open questions (for the implementer to resolve before coding)

1. **Where is the DI provider for `ThemeRepository`?** The class has 4 constructor
   parameters (`themeApi`, `historyCache`, `forumUsersCache`, `pageMemoryCache`); the
   proposed 5th (`activeRenderSessionProvider`) needs a Hilt/Dagger binding. Find the
   `@Module` that provides `ThemeRepository` and add the binding there. The `renderSignatureProvider`
   in `ThemeRepository.kt:30` is `@Volatile var ... = null` and presumably set externally —
   the implementer should mirror that pattern for the new `activeRenderSessionProvider`,
   set from the `ThemeViewModel` after the active render begins. (This is the same pattern
   used for `ThemeRenderGuard.token` elsewhere.)

2. **What is the right way to **obtain** the active session from `ThemeViewModel`?** The
   `ThemeRenderSession` is created by `ThemeRenderSession.create(page, bridgeToken)` —
   but `bridgeToken` lives in the bridge layer. The implementer should determine: is the
   session owned by the ViewModel (set when the page is bound to the WebView), or by the
   bridge (set when `onPageStarted` fires)? Whichever, `ThemeViewModel` is the natural
   owner from the cache's perspective. The implementer should not refactor the session
   ownership — that is part of L06. Just expose a getter and pass it as the provider.

3. **Should the existing `get(key, expectedSignature: String?)` overload stay, or be
   removed?** Recommendation: keep it as a convenience overload that delegates to the new
   3-parameter version with `expectedRenderGeneration = null`. This preserves the public
   API for the 2 existing test cases and the 1 production caller that does not need the
   session handshake (`ThemeRepository.preloadTheme` does not need it — see §2.3).

4. **`ThemePageMemoryCache.copyForCache()` is `internal`** — what other modules call it?
   The grep in `app/src/main` shows only `ThemePageMemoryCache.kt` itself; but `internal`
   means `app/src/test` can also see it. Check the test sources (already done — only
   `ThemePageMemoryCacheTest.kt` uses it via `get`).
   **Conclusion:** safe to delete after updating the affected test.

### 6.2 Edge cases to verify during implementation

| Edge case | Expected behavior | Test |
|---|---|---|
| Cache hit during a different session (hybrid page insert) | Miss, evict | `get_withStaleRenderGeneration_missesAndEvicts` |
| Cache hit on a fresh app start (no active session yet) | Hit, no generation check | `get_withNullRenderGeneration_skipsCheck` |
| Cache hit after process death + restore | Hit, generation may be 0 (default) — caller treats as miss | Manual QA |
| Cache hit during signature mismatch | Miss, evict (already in tests) | existing |
| Cache hit during TTL expiry | Miss, evict (already in tests) | existing |
| Cache hit after soft-reference cleared by GC | Miss, evict (already in tests) | existing |
| Cache hit during rotation | Hit, caller treats returned `ThemePage` as read-only | Manual QA |
| Cache hit after topic switch (same topicId, different st) | Independent keys — no cross-contamination | `invalidateTopic_removesAllPagesForTopic` (existing) |
| Cache hit on a QMS page (shouldSkipCache == true) | No cache key generated, falls through to network | `shouldSkipCache_forUnreadAndFindPostUrls` (existing) |
| Very large page (>100 posts) | Same as small page — single allocation, same fields | Manual |
| Empty cache | First `get` returns null; second `get` after `put` returns hit | covered by existing tests |

### 6.3 Assumptions made (to be verified by the implementer)

1. **No caller mutates the returned `ThemePage`'s `posts` or `anchors` lists.** Verified by
   manual audit of `app/src/main` callers in §2.3. The only mutations are scalar fields
   (`isInlineHatOpen`, `topicHatPost`) which the cache does not re-read.
2. **`ThemeRenderSession` is owned by the ViewModel, not the bridge layer.** This is the
   most natural reading of `ThemeRenderSession.create(page, bridgeToken)` — the session
   is a snapshot of "this page is being rendered with this token". The implementer should
   confirm by checking the actual call site of `ThemeRenderSession.create()`.
3. **The `LinkedHashMap` race condition (unsynchronized access) is not currently a
   production-visible bug.** The cache is called from `Dispatchers.IO` in `ThemeRepository`
   (single coroutine context per call), so the map is effectively single-threaded in
   practice. Smart Preload (also `Dispatchers.IO`) can race, but the race window is
   small and the consequence is a `ConcurrentModificationException` (loud, not silent).
4. **`SoftReference` semantics on Android (ART) are stable.** ART clears soft refs
   before `OutOfMemoryError`, so the cache will release pages under pressure. This is
   documented in the existing class comment (line 9-22) and assumed true.
5. **The Hilt/Dagger module for `ThemeRepository` exists and is reachable from the
   presentation layer.** Standard assumption for a Hilt-managed app. Verify by grep.

### 6.4 What this document does NOT cover (explicitly out of scope)

- **L06 — `ThemeViewModel` god-class decomposition.** This document only touches the
  cache ↔ session handshake, not the ViewModel split. L06 remains a separate ticket.
- **P-08 — WebView DOM growth controls (page windowing).** Variant D in this document
  is a sketch of how L08 *could* grow into P-08, but P-08 is its own ticket and should
  not be conflated with L08.
- **The unsynchronized `LinkedHashMap` race in `ThemePageMemoryCache`.** Pre-existing
  latent bug. Should be a separate P3 ticket; the recommended solution above does not
  fix it (adding `synchronized` blocks would slow down the hot path; the right fix is
  `Collections.synchronizedMap(...)` or migration to `ConcurrentHashMap`, both of which
  are independent of L08's scope).
- **General GC pressure from other allocations** (e.g. `SpannableStringBuilder` per post
  in `HtmlToSpannedConverter.kt:171` — flagged in `docs/AUDIT_ROADMAP_2026-06_RU.md:149`).
  L08 only addresses the `ThemePageMemoryCache.copyForCache` allocation; the rest of the
  GC budget is for other tickets.

---

## 7. References

- `app/src/main/java/forpdateam/ru/forpda/model/repository/theme/ThemePageMemoryCache.kt` (the file under audit; 191 lines)
- `app/src/main/java/forpdateam/ru/forpda/model/repository/theme/ThemeRepository.kt` (lines 32-96, 98-100, 112-139, 348-352)
- `app/src/main/java/forpdateam/ru/forpda/model/interactors/theme/ThemeUseCase.kt` (lines 90-149, 348-352, 371-424)
- `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeInfiniteScrollController.kt` (lines 75-228, 236-263)
- `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeSmartPreloadPolicy.kt` (full file, 127 lines)
- `app/src/main/java/forpdateam/ru/forpda/presentation/theme/ThemeRenderSession.kt` (full file, 59 lines)
- `app/src/main/java/forpdateam/ru/forpda/entity/remote/theme/ThemePage.kt` (89 lines)
- `app/src/main/java/forpdateam/ru/forpda/entity/remote/theme/ThemePost.kt` (16 lines)
- `app/src/main/java/forpdateam/ru/forpda/entity/remote/BaseForumPost.kt` (37 lines)
- `app/src/test/java/forpdateam/ru/forpda/model/repository/theme/ThemePageMemoryCacheTest.kt` (165 lines — existing tests, to be updated)
- `app/src/test/java/forpdateam/ru/forpda/model/repository/theme/ThemeRepositoryCacheTest.kt` (71 lines)
- `docs/AUDIT_ROADMAP_2026-06_RU.md:110,148,199,207,237,271,343,388` (the L08 ticket)
- `docs/HYBRID_THEME_STABILIZATION_SPEC_EN.md:53-64` (the three render-validity tokens)
- `docs/PLAN.md:51,179` (P-08, the related "WebView DOM growth controls" ticket)
- `docs/PLAN.md:206` (A-02 — GlobalScope.launch in ThemeUseCase, an example of similar-size fix in the same area)

---

## 8. Implementation checklist (for the PR)

```
[ ] Stage 1 (Variant A):
    [ ] Delete `copyForCache()` extension in ThemePageMemoryCache.kt:156-190
    [ ] Drop `.copyForCache()` call in get():83
    [ ] Update KDoc on ThemePageMemoryCache class (line 9-22) — read-only contract
    [ ] Update `putThenGet_returnsCopyAndExpiresAfterTtl` test (line 19-35) → assertSame
    [ ] Add `putThenGet_returnsSameReference` test
    [ ] Add `ThemePageMemoryCacheReadOnlyContractTest.kt` (2 tests)
    [ ] Run `./gradlew :app:testStableDebugUnitTest --tests "*ThemePageMemoryCache*"` — green
    [ ] Run `./gradlew :app:testStableDebugUnitTest --tests "*ThemeRepositoryCache*"` — green
    [ ] Commit 1: "AUDIT-L08 (1/2): drop per-get copyForCache; return same reference"

[ ] Stage 2 (Variant C):
    [ ] Add `renderGenerationId: Int` to `Entry` data class (line 36-40)
    [ ] Populate it in `put()` (line 92-96)
    [ ] Add 3-param `get(key, expectedSignature, expectedRenderGeneration)` overload
    [ ] Add `activeRenderSessionProvider: () -> ThemeRenderSession?` to `ThemeRepository` ctor
    [ ] In `ThemeRepository.getTheme` (line 38-96), compute `expectedGen` and pass it
    [ ] Locate DI module for `ThemeRepository` and wire the provider binding
    [ ] Have `ThemeViewModel` set the provider after `ThemeRenderSession.create()` is called
    [ ] Add 3 tests in `ThemePageMemoryCacheTest.kt`
    [ ] Add 1 test in `ThemeRepositoryCacheTest.kt`
    [ ] Run all theme tests — green
    [ ] Commit 2: "AUDIT-L08 (2/2): cache evicts on render-generation mismatch"

[ ] Stage 3 (validation):
    [ ] Run `./gradlew :app:testStableDebugUnitTest` — green
    [ ] Manual QA: long topic scroll (30 s), no jank
    [ ] Manual QA: kill+restore — no stale-render ghost
    [ ] Run `./scripts/startup-benchmark.sh` — no regression
    [ ] Update `docs/AUDIT_ROADMAP_2026-06_RU.md:388` → ✅ Closed
    [ ] Add note to `docs/IMPLEMENTATION_SUMMARY.md`
    [ ] Add `docs/perf/baselines/2026-06-22-L08-alloc.md` (pre/post numbers)
    [ ] Commit 3: "AUDIT-L08: docs + baselines"
```

---

**End of document.**
