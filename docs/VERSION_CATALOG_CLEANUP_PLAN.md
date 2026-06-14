# Version Catalog Cleanup Plan

## Current State

- `gradle/libs.versions.toml` exists but `app/build.gradle` still declares dependencies mostly as literal strings.
- Root `build.gradle` duplicates plugin versions already listed in the catalog: AGP, Kotlin, Hilt, Realm.
- Catalog app version fields are stale (`2.5.4`) compared with `app/build.gradle` / manifest (`2.8.4`).
- Several catalog library versions differ from actual Gradle usage.

## Mismatch Examples

| Area | `app/build.gradle` | `libs.versions.toml` | Action |
| --- | --- | --- | --- |
| minSdk | `24` | `21` | Update catalog before using it for Android config. |
| app version | `2.8.4` | `2.5.4` | Remove app version from catalog or sync it from a single source. |
| `androidx.core:core-ktx` | `1.13.1` | `1.15.0` | Decide desired version, then migrate one dependency. |
| `androidx.activity:activity-ktx` | `1.10.1` | `1.9.3` | Decide desired version, then migrate AndroidX group. |
| `io.coil-kt:coil` | `2.7.0` | `2.6.0` | Align catalog with current build before replacing literal. |
| Compose BOM | `2024.12.01` | missing | Add catalog entry before Compose migration. |
| Room | `2.6.1` | missing | Add runtime/ktx/compiler/testing entries together. |
| Desugar JDK libs | `2.1.5` | missing | Add before migrating `coreLibraryDesugaring`. |

## Safe Migration Order

1. Sync catalog metadata with current build values without changing resolved dependencies.
2. Migrate plugin aliases in a separate patch after confirming root/app plugin resolution behavior.
3. Migrate AndroidX core/lifecycle group first, one dependency group at a time.
4. Migrate Room runtime/compiler/testing together.
5. Migrate Compose BOM and Compose libraries together.
6. Migrate test dependencies last.

## Verification For Each Group

- `./gradlew :app:compileStableDebugKotlin`
- `./gradlew :app:testStableDebugUnitTest`
- `./gradlew :app:lintStableDebug` when dependency group affects Android resources/manifests
- `git diff --check -- <touched build files>`
