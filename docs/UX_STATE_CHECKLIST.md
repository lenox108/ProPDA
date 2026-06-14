# UX State Checklist

## Current Surfaces

- Theme uses refresh/loading events around WebView rendering and shows errors mostly through existing error handling/snackbar paths.
- Favorites uses cached list loading and explicit mark-read progress, but offline/network/empty distinctions still need a UI audit.
- QMS chat uses WebView rendering plus native `MessagePanel`; send/upload errors are handled in native callbacks and snackbar/error paths.

## Required State Distinctions

- Loading: request in progress and retry should not be duplicated.
- Empty: successful load with no items/messages/posts.
- Offline/network error: transport failure or no connection.
- Parse error: response received but markup could not be interpreted.
- Auth/session error: user must log in or session expired.

## Safe Next UI Steps

- Add screen-local messages using existing snackbar/error containers; do not redesign layouts.
- Add retry only where a retry method already exists.
- Keep WebView template changes separate from ViewModel state changes.
- Verify Theme, Favorites, and QMS flows with `docs/AI_REGRESSION_CHECKLIST.md`.
