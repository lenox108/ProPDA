# CP1251 in the ForPDA Android client (X-01)

> Why does an Android app from 2026 still encode form payloads in
> Windows-1251? Background for anyone who has to touch `Cp1251Codec.kt`,
> the news/theme/reputation API bodies, or the login flow.

## TL;DR

4PDA's PHP backend (4pda.to) was originally written in the late 2000s
and decodes form fields with `iconv('CP1251', 'UTF-8', ...)` before
persisting. Sending UTF-8 bytes to those endpoints causes **mojibake**
in post titles, nicknames in search, and reputation comment fields.

The app keeps a small, audited `Cp1251Codec` (`common/Cp1251Codec.kt`)
that converts Kotlin `String` → `application/x-www-form-urlencoded`
payload bytes using the CP1251 table.

## Where it is used

| Site | File | Field |
|------|------|-------|
| News comments | `model/data/remote/api/news/NewsApi.kt:1375` | comment text |
| News filter header | `model/data/remote/api/news/NewsApi.kt:919` | `textField` |
| Theme post reply | `model/data/remote/api/theme/ThemeApi.kt:603` | `message` form field |
| Reputation change | `model/data/remote/api/reputation/ReputationApi.kt:92` | per-key form header |
| Search by nickname | `presentation/search/SearchNavigationUrls.kt:61` | `ARG_NICK` query |
| Media filename decode | `presentation/LinkHandler.kt:345` | `URLDecoder.decode("CP1251")` for forum file names |

The login form (`auth/AuthApi.kt`) is also CP1251-encoded, but the
auth path goes through OkHttp's standard form encoding on the
endpoint side, so the codec is reached through the request body
builder, not through direct calls.

## UX implications

1. **Edit-post preview** shows the user's input verbatim; do not
   double-encode it on round-trip. A common bug pattern is decoding
   the response body and then re-encoding the comment as if it were
   new — this corrupts any non-ASCII character.
2. **Username/nickname search** (`SearchNavigationUrls.kt`) must
   always go through `Cp1251Codec.encodeSmart` so that nicks with
   emoji or rare Unicode are still found.
3. **The login form accepts the same `CP1251` byte stream** as
   forum posts. If you ever swap the OkHttp client to enforce
   `Content-Type: charset=utf-8` on every form body, the login
   flow will break with HTTP 200 + a generic "wrong password" error
   from the server.

## What to do if you add a new form field

1. Wrap the value in `Cp1251Codec.encode(...)` (or
   `encodeSmart(...)` if the field is a user-typed search string).
2. Add a unit test in `app/src/test/java/forpdateam/ru/forpda/common/Cp1251CodecTest.kt`
   if such a test does not exist already; if it does, extend it.
3. Never call `URLEncoder.encode` directly on a CP1251 byte array —
   it is charset-aware and will silently convert back to UTF-8.

## When this stops being true

If 4PDA ever migrates to UTF-8 form encoding, the safest path is:

1. Add a feature flag `prefs.use_utf8_forms = false` (default `false`).
2. Make the call sites in the table above branch on the flag.
3. Flip the default to `true` after a release cycle with no
   complaints from the 4PDA-side logs.

Until then, treat CP1251 as part of the wire contract.
