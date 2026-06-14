# Version Catalog Cleanup Plan

## Current State

The project has `gradle/libs.versions.toml`, but most plugin and dependency
versions are still declared directly in `build.gradle` and `app/build.gradle`.
This makes the catalog stale and creates version drift.

## Known Version Drift

| Area | Gradle file | Version catalog | Decision |
| --- | --- | --- | --- |
| App version | `2.8.4` in `app/build.gradle` | `2.5.4` | Keep app version in `app/build.gradle` for now; remove stale catalog app version entries later. |
| `minSdk` | `24` | `21` | Use `24`; catalog is stale. |
| `androidx.core` | forced/used `1.13.1` | `1.15.0` | Resolve separately because `resolutionStrategy.force` currently pins `1.13.1`. |
| `androidx.activity` | `1.10.1` | `1.9.3` | Prefer current Gradle value after compile verification. |
| `androidx.fragment` | `1.8.6` | `1.8.5` | Prefer current Gradle value after compile verification. |
| Coil | `2.7.0` | `2.6.0` | Prefer current Gradle value. |
| Kotlin serialization plugin | `1.9.22` inline | Kotlin plugin `1.9.25` | Align deliberately in a separate plugin migration. |
| Compose BOM | `2024.12.01` inline | Missing | Add to catalog before migrating Compose dependencies. |
| Room | `2.6.1` inline | Missing | Add to catalog as a small AndroidX persistence group. |
| Security crypto | `1.1.0-alpha06` inline | Missing | Add only with existing FIXME preserved. |
| Detekt | `1.23.4` inline | Missing | Add plugin/tooling versions together. |

## Migration Order

1. Migrate build plugins that already match catalog values:
   Android Gradle plugin, Kotlin Android/KAPT, Hilt, Realm.
2. Add missing catalog entries without changing resolved versions:
   desugar, datastore, Room, Compose BOM, serialization JSON, security crypto,
   documentfile, OkHttp Brotli, AppMetrica, detekt, AndroidX test/core.
3. Migrate one dependency group per patch:
   AndroidX core/lifecycle, persistence, Compose, network, images, DI, tests.
4. Only after all usages move to catalog, delete stale unused catalog versions
   such as old app version fields or unused UI libraries.
5. Revisit `resolutionStrategy.force 'androidx.core:core:1.13.1'` separately;
   do not remove it as part of a catalog-only patch.

## Verification Per Patch

- `./gradlew :app:compileStableDebugKotlin`
- Focused tests if the migrated dependency group affects test runtime.
- `git diff --check` scoped to changed Gradle/catalog files.
