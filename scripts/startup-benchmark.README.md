# `startup-benchmark.sh` — Cold-start timing for ForPDA

Measures cold-start time of ForPDA over N runs and computes p50 / p95 / min / max.
Optionally captures a Perfetto trace for the first run.

## Requirements

- `adb` (Android platform-tools) on your `PATH`.
- A device or emulator with **USB debugging** enabled, attached and authorised.
- A working copy of the ForPDA source tree.
- `python3` (for the percentile summary; the script falls back gracefully if missing).
- For Perfetto traces: a device running **API 28+** with a `trace` directory
  writable to `perfetto` (no root required for `--sched` slices).

The default package is `ru.forpdateam.forpda` (the `store` flavor). The
script accepts a positional argument to override it for the other flavors,
matching `scripts/battery-audit.sh`:

| Flavor  | `applicationId`                  |
|---------|----------------------------------|
| stable  | `ru.forpdateam.forpda.parallel`  |
| store   | `ru.forpdateam.forpda` (default) |
| beta    | `ru.forpdateam.forpda.beta`      |
| dev     | `ru.forpdateam.forpda.debug`     |

## Usage

```bash
# 1) Make the script executable once.
chmod +x scripts/startup-benchmark.sh

# 2) Default package, 5 cold starts.
./scripts/startup-benchmark.sh

# 3) Beta flavor, 10 runs.
./scripts/startup-benchmark.sh ru.forpdateam.forpda.beta 10

# 4) Capture a Perfetto trace on the first run.
./scripts/startup-benchmark.sh --perfetto ru.forpdateam.forpda 3

# 5) Custom activity (e.g. for a fork).
./scripts/startup-benchmark.sh --activity com.example.Main ru.forpdateam.forpda 5
```

The script exits cleanly (no error) if no device is attached.

## What it produces

For every run, files are written to `build/perf/`:

```
build/perf/20260617-203000-ru_forpdateam_forpda-cold-start.csv
build/perf/20260617-203000-ru_forpdateam_forpda-cold-start.perfetto-trace  # only with --perfetto
```

The CSV has the schema:

| Column                    | Meaning                                                                |
|---------------------------|------------------------------------------------------------------------|
| `run`                     | Run index, 1..N.                                                       |
| `start_to_first_frame_ms` | Best-effort "time to first frame" reported by `dumpsys activity processes`. Falls back to wall-clock delta to `ResumedActivity`. |
| `start_to_resumed_ms`     | Wall-clock delta from `am start` until the activity is in `ResumedActivity`. Always wall-clock. |

After the runs finish, a small block is printed with `p50`, `p95`, `min`,
`max`, `median`, `stdev`.

## How to use the results

This script exists to **freeze a baseline before any startup optimization
work**, and to **regress-check after a change**. See
[`docs/STARTUP_BASELINE.md`](../docs/STARTUP_BASELINE.md) for the protocol.

### Quick orientation

- A "good" cold start on a mid-range device is under 800 ms (resumed-to-first-frame).
- A "good" cold start on a low-end device is under 1500 ms.
- Anything above 2 s is a Play Store visible red flag.

### Comparing two runs

```bash
# Run 1 — before
./scripts/startup-benchmark.sh ru.forpdateam.forpda 10

# ... apply your change ...

# Run 2 — after
./scripts/startup-benchmark.sh ru.forpdateam.forpda 10

# Compare p50 / p95 from both summary prints.
```

Always run on the same device, with the same WiFi/cellular state, and at
the same battery level. Variance between runs of ±10 % is normal.

## Perfetto

With `--perfetto`, the script starts a background `perfetto` trace covering
the first run. The trace is copied back to `build/perf/` as a
`*.perfetto-trace` file. Open it at
<https://ui.perfetto.dev> for slice-level analysis:

- `ActivityStart` — full launch lifecycle, from `am start` to `onResume`.
- `Application creation` — time spent in `Application.onCreate`.
- `MainActivity onCreate` — time spent inflating the launcher activity.
- `First frame` — the system-reported time of the first `Choreographer.doFrame`.
- StrictMode `penaltyLog` events (debug builds only) show up under `am.strict_mode`.

## Notes

- The script is non-destructive. It only `am force-stop`s the package.
- The script never calls `dumpsys batterystats --reset`; combine with
  `battery-audit.sh` if you also want battery numbers.
- The `start_to_first_frame_ms` is best-effort. If `dumpsys activity processes`
  does not report a `start=...` line on your device, the script falls back
  to wall-clock, which is slightly noisier but still useful.
