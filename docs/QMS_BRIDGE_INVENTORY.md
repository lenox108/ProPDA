# QMS Bridge Inventory

## Status: no bridge — QMS chat has no WebView

The QMS chat screen was migrated off the WebView engine. It renders natively:
`ui/fragments/qms/chat/QmsChatFragment.kt` hosts a `RecyclerView` + `QmsMessagesAdapter`, and message
bodies are segmented by the shared `PostBodyRenderer` and drawn by `BodyBlockViewFactory` — the same
renderer the native topic screen uses for post bodies.

Removed with the engine swap:

| Removed | What it was |
|---|---|
| `ui/fragments/qms/chat/QmsChatJsInterface.kt` | The `IChat` bridge (`loadMoreMessages`, `openLink`) |
| `presentation/qms/chat/QmsChatWebCallbacks.kt` | The bridge's callback interface on the ViewModel |
| `presentation/qms/chat/QmsChatTemplate.kt` | MiniTemplator assembly of the chat HTML |
| `presentation/qms/chat/QmsWebRenderPolicy.kt`, `QmsWebRenderProbe.kt` | Blank-render retry / DOM-probe policy |
| `assets/template_qms_chat.html`, `template_qms_chat_mess.html` | The HTML shell + message template |
| `assets/forpda/scripts/modules/qms.js` | Scroll bootstrap, upward pagination, `makeAllRead`, link binding |
| `assets/forpda/styles/**/qms.{less,css}` | Chat bubble styling |
| `WebViewSecurityProfile.TRUSTED_QMS_CHAT` | The QMS-only trust tier that allowed the base bridge |

## Bridge registrations

None. The QMS chat registers no `@JavascriptInterface` object and creates no `WebView`.

## Destructive actions

Message sending, new-dialog creation and attachment upload were already native UI actions
(`MessagePanel` / `ChatThemeCreator` / `AttachmentsPopup`) and remain so. Pagination, marking a thread
read and link navigation are now plain Kotlin calls into `QmsChatViewModel` / `ILinkHandler`.

## Conclusion

- Token guard required: no — there is no bridge to guard.
- If a QMS WebView is ever reintroduced, follow the Theme render-token pattern before exposing any
  mutating method.
