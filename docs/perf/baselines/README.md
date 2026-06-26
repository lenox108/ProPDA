# Startup & perf baselines

Each baseline is a dated measurement frozen at a specific commit and device.
Naming: `YYYY-MM-DD-<short-sha>-<device>.md`.

Workflow:
1. Run `./scripts/startup-benchmark.sh [--perfetto] <pkg> <N>` to capture data.
2. Save CSV + summary to `build/perf/`.
3. Copy the device + commit + metrics into a new `*.md` here.
4. Reference the new file from your PR description.

See `../../STARTUP_BASELINE.md` for the full protocol.
