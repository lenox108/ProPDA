# QMS Bridge Inventory

## Files inspected
- `app/src/main/java/forpdateam/ru/forpda/ui/fragments/qms/chat/QmsChatJsInterface.kt`
- `app/src/main/java/forpdateam/ru/forpda/ui/fragments/qms/chat/QmsChatFragment.kt`
- `app/src/main/java/forpdateam/ru/forpda/presentation/qms/chat/QmsChatViewModel.kt`
- `app/src/main/java/forpdateam/ru/forpda/presentation/qms/chat/QmsChatTemplate.kt`
- `app/src/main/assets/forpda/scripts/modules/qms.js`

QMS chat uses a local trusted template loaded into `ExtendedWebView` with
`WebViewSecurityProfile.TRUSTED_LOCAL_TEMPLATE`.

## Bridge registrations
| File | Interface name | Registered object | Notes |
|---|---|---|---|
| `app/src/main/java/forpdateam/ru/forpda/ui/fragments/qms/chat/QmsChatFragment.kt` | `IBase` | `ExtendedWebView` base bridge | Required to flush queued QMS JavaScript after DOM/page load; trusted local QMS template/assets only. |
| `app/src/main/java/forpdateam/ru/forpda/ui/fragments/qms/chat/QmsChatFragment.kt` | `IChat` | `QmsChatJsInterface` | Triggered by `qms.js` when the user scrolls to the top; trusted local QMS template/assets only. |

## Exported QMS methods
| Method | Parameters | Destructive? | Risk | Notes |
|---|---|---|---|---|
| `loadMoreMessages()` | None | No | Low | Delegates to `QmsChatWebCallbacks.loadMoreMessages()` on the UI thread. It does not send messages, upload files, delete data, block users, or change server state. |

`app/src/main/assets/forpda/scripts/modules/qms.js` calls:

```javascript
IChat.loadMoreMessages();
```

## Destructive actions
- Message sending and new-theme creation are native UI actions from `MessagePanel` / `ChatThemeCreator`, not JavaScript bridge calls.
- Attachment add/delete/retry flows are native `AttachmentsPopup` callbacks, not JavaScript bridge methods.
- QMS dialog/theme deletion exists in QMS list screens, not in the chat WebView bridge.
- No exported QMS bridge method currently sends messages, edits content, deletes messages, deletes dialogs, uploads files, or opens arbitrary URLs.

## Conclusion
- Token guard required: no for the current QMS bridge.
- Why: the only exported chat method is non-destructive pagination, so adding a token protocol would change behavior without protecting any current mutating QMS action.
- Recommended next task: none for QMS until a destructive QMS JavaScript call is introduced. If that happens, follow the Theme render-token pattern before exposing it.
