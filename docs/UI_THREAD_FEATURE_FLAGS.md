# UI Thread Feature Flags

## Smart Quotes
- flag: `smartQuotesEnabled`
- file: `app/src/main/assets/forpda/scripts/blocks.js`
- default: `true`
- behavior: when `false`, `initSmartQuotes(...)` exits before marking quote blocks, so topic quotes render with the pre-Smart-Quotes layout.

## Future Settings Hook
- A later UI settings task can map an existing reader/theme preference to `smartQuotesEnabled`.
- Do not add a new settings screen, DataStore key, or SharedPreferences schema change until a task explicitly allows it.
