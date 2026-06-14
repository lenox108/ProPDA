# CI / Local Checks

## Discover tasks
```bash
./gradlew tasks --all
```

## Recommended checks
```bash
./gradlew :app:compileStableDebugKotlin
./gradlew :app:testStableDebugUnitTest
./gradlew :app:lintStableDebug
./gradlew detekt
```

## If flavor names differ
Use the closest existing debug compile/test/lint tasks shown by `./gradlew tasks --all`.
Document the actual working commands here.

## Rule for AI agents
Do not disable checks to make a patch pass.
If checks cannot run because of network/sandbox/SDK problems, report the exact error.

## Notes

- The active flavor used in this hardening pass is `stable`; task names may need adjustment if flavor names change.
- In this workspace, rerunning with `--no-daemon` helped recover from intermittent Kotlin/KAPT incremental cache errors.
- `detekt` currently reports existing project-wide findings; use focused fixes and avoid wholesale baseline deletion.
