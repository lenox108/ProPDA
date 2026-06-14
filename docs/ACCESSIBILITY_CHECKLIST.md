# Accessibility Checklist

## Main Targets

- Icon-only toolbar actions have meaningful content descriptions.
- Message panel buttons announce send, attach, formatting, and delete actions.
- Topic/Favorites/QMS list rows remain readable with large font scale.
- Touch targets for toolbar, FAB, pagination, and message-panel actions are at least 48dp where practical.
- WebView-hosted controls still have native alternatives or clear labels where possible.

## Manual QA

- Enable TalkBack and navigate Theme, Favorites, and QMS chat.
- Increase system font size and verify message panel, pagination, and list rows.
- Check portrait and landscape for clipped action buttons.
- Verify that adding descriptions does not duplicate visible text announcements.

## Safe Implementation Notes

- Prefer string resources for reusable descriptions.
- Do not change visual style unless the target is too small or unlabeled.
- Keep WebView template accessibility changes separate from native XML layout changes.
