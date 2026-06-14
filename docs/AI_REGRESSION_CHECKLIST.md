# AI Regression Checklist

## Theme
- Open topic first page.
- Open next/prev/last page.
- Open direct post link / findpost.
- Scroll restore works after reload/rotate.
- Reply, quote, full quote, edit, delete menu still open correct UI.
- Poll view/result/submit still works.
- Spoilers open/close.
- Hat/poll header open state persists.

## Links/downloads
- Internal 4PDA topic opens inside app.
- External https opens externally.
- javascript:, file:, data: links are blocked.
- Image download/view works.

## QMS
- Contacts list opens.
- Theme list opens.
- Chat opens.
- Send message flow still works.

## Favorites
- Favorite list opens.
- Counters update.
- Add/remove favorite works.

## Auth/session
- Existing logged-in session remains valid.
- Logout/login flow not broken.

## Build/checks
- compileStableDebugKotlin or current compile task passes.
- unit tests pass or known blockers are documented.
- lint/detekt results do not regress without explanation.
