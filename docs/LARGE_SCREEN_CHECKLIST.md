# Large Screen Checklist

## Existing Support

- `app/src/main/res/values-sw600dp/dimens.xml` already increases list padding and some content dimensions for wide screens.
- No dedicated `layout-sw*` variants were found for Theme, Favorites, or QMS.

## Manual QA Targets

- Theme topic WebView does not become unreadably wide on tablets/foldables.
- Topic list and Favorites rows keep comfortable padding and do not stretch metadata awkwardly.
- QMS chat message bubbles remain readable and message panel stays reachable.
- ImageViewer, downloads, and settings screens work in landscape.

## Safe Next Layout Steps

- Add max content width only after testing on `sw600dp` and landscape devices.
- Prefer dimension overrides before duplicating full layouts.
- Keep WebView content-width changes separate from native list layout changes.
