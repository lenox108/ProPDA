# Project workflow

- After completing each user task, create a Git commit containing only the changes made for that task.
- Do not include unrelated or pre-existing user changes in the commit.
- The project has a single build channel: flavor `stable` (`ru.forpdateam.forpda.parallel`).
  Build variants are `stableDebug` / `stableRelease` — there is no `store` (Google Play) flavor,
  and it must not be reintroduced. Older docs under `docs/` still mention `assembleStoreDebug`
  and `bundleStoreRelease`; those tasks no longer exist.
