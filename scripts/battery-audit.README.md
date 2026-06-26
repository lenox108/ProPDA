# `battery-audit.sh` — Android battery stats for ForPDA

A small wrapper around `adb shell dumpsys` that captures battery-related
subsystem dumps (battery stats, alarms, JobScheduler) for a single
application, so you can investigate "big battery drain in the background"
without leaving the terminal.

## Requirements

- `adb` (Android platform-tools) on your `PATH`.
- A device or emulator with **USB debugging** enabled, attached and
  authorised.
- A working copy of the ForPDA source tree (the script writes output
  under `./build/battery-audit/`).

The default package is `ru.forpdateam.forpda` (the `store` flavor). The
script accepts a positional argument to override it for the other
flavors:

| Flavor  | `applicationId`                  |
|---------|----------------------------------|
| stable  | `ru.forpdateam.forpda.parallel`  |
| store   | `ru.forpdateam.forpda` (default) |
| beta    | `ru.forpdateam.forpda.beta`      |
| dev     | `ru.forpdateam.forpda.debug`     |

## Usage

```bash
# 1) Make the script executable once.
chmod +x scripts/battery-audit.sh

# 2) Default package (ru.forpdateam.forpda), non-destructive.
./scripts/battery-audit.sh

# 3) Beta flavor.
./scripts/battery-audit.sh ru.forpdateam.forpda.beta

# 4) Reset battery history AFTER the dump is safely on disk.
./scripts/battery-audit.sh --reset ru.forpdateam.forpda.debug
```

The script exits cleanly (no error) if no device is attached, after
printing `adb devices`.

## What it produces

For every run, a timestamped directory of files is created under
`build/battery-audit/`:

```
build/battery-audit/20260616-191000-ru_forpdateam_forpda-batterystats-checkin.txt
build/battery-audit/20260616-191000-ru_forpdateam_forpda-batterystats-raw.txt
build/battery-audit/20260616-191000-ru_forpdateam_forpda-alarms.txt
build/battery-audit/20260616-191000-ru_forpdateam_forpda-jobs.txt
build/battery-audit/20260616-191000-ru_forpdateam_forpda-summary.txt
```

| File             | Source                                              | Use it for                                                  |
|------------------|-----------------------------------------------------|-------------------------------------------------------------|
| `*-checkin.txt`  | `dumpsys batterystats --checkin <pkg>`              | Machine-readable history. This is the file you attach to bug reports. |
| `*-raw.txt`      | `dumpsys batterystats` (full)                       | Greppable. Wake-lock lines live here.                       |
| `*-alarms.txt`   | `dumpsys alarm` (filtered output)                   | Find recurring `AlarmManager` entries.                      |
| `*-jobs.txt`     | `dumpsys jobscheduler` (filtered output)            | See WorkManager and other jobs scheduled by the package.    |
| `*-summary.txt`  | Pre-filtered snippets of the above                  | Quick orientation; start here.                              |

## What to look for

The ForPDA codebase has only a few places that can hold the CPU or the
radio awake in the background; the dump is supposed to **name** them.

### Wake locks

```bash
grep -E "Wake lock|partial|wake" build/battery-audit/*-raw.txt | grep -i forpda
```

`partial` wakelocks are the ones to worry about — they keep the CPU
running while the screen is off. Expected for ForPDA:

- `WebSocketController` (only when foreground realtime notifications
  are on, and only when the WS is reconnecting).
- `DownloadWorker` (only while a download is in flight; the worker is
  foregrounded with `FOREGROUND_SERVICE_TYPE_DATA_SYNC`).

Anything outside those two is a real bug.

### Alarms

```bash
grep -E "Alarm" build/battery-audit/*-alarms.txt | grep -i forpda
```

ForPDA should not register repeating alarms from the app code itself
(it goes through `WorkManager`). Any entry you see is most likely
WorkManager's internal scheduler, which is fine; a *large* count of
entries for the same tag means the worker is retrying too eagerly.

### JobScheduler / WorkManager

```bash
grep -A5 "ru.forpdateam.forpda" build/battery-audit/*-jobs.txt
```

Look for:

- `DownloadWorker` — should be a `OneTimeWorkRequest` with constraints
  `setRequiredNetworkType(CONNECTED)` and `setRequiresBatteryNotLow(true)`.
  Multiple `ENQUEUED` instances of the same `uniqueName` are harmless
  (they `REPLACE`); multiple `FAILED` instances in a short window mean
  the network is unstable or the URL is bad.
- `AppUpdateWorker` — a `PeriodicWorkRequest` with a 24 h interval
  and the same battery-not-low constraint. You should see **at most one
  instance**, always `ENQUEUED` or `BLOCKED`.
- `EventsCheckWorker` (notifications) — should be OneTime and only
  present while realtime is foreground-enabled.

## Resetting history (advanced)

`dumpsys batterystats --reset` is **destructive** — it wipes the
battery history buffer. The script's `--reset` flag only runs it
*after* the checkin dump is on disk, so you always have a baseline to
compare against.

Typical workflow:

1. Charge to 100 %, unplug, run the script **without** `--reset`.
2. Use the app for 4–8 hours (or leave it idle on a stable network).
3. Run the script with `--reset` to grab a focused snapshot.

## Notes

- The script never calls `dumpsys batterystats --reset` by default.
- Output is plain text — attach the `*-checkin.txt` to bug reports
  inside a `.zip` along with `bugreport` output if available.
- If you see `error: no devices/emulators found`, plug in the device
  and accept the USB-debugging prompt before retrying.

## Related scripts

- `startup-benchmark.sh` — cold-start timing over N runs with optional
  Perfetto trace. Pairs well with this script for "battery AND startup"
  investigations. See `docs/STARTUP_BASELINE.md` for the protocol.
