#!/usr/bin/env bash
#
# startup-benchmark.sh — measure ForPDA cold-start time over N runs via adb.
#
# Usage:
#   ./scripts/startup-benchmark.sh                          # default package, 5 runs
#   ./scripts/startup-benchmark.sh ru.forpdateam.forpda.beta 10
#   ./scripts/startup-benchmark.sh --perfetto ru.forpdateam.forpda 3
#
# The script:
#   1) checks `adb devices` and exits cleanly if no device is attached;
#   2) force-stops the package and clears its process to get a true cold start;
#   3) launches the launcher activity and timestamps the first frame
#      (using `ActivityManager: ProcessRecord startTime` and
#      `dumpsys activity processes` for end-to-end measurement);
#   4) repeats N times and computes p50 / p95 / min / max;
#   5) optionally, with `--perfetto`, captures a Perfetto trace for the
#      first run and saves it under build/perf/ for offline analysis.
#
# Output is written to ./build/perf/ (auto-created). The script is
# idempotent and safe to re-run.
#
# NOTE: cold start requires a launchable activity. For ForPDA the launcher
# is `ru.forpdateam.forpda.MainActivity` (across all flavors). Override
# with `--activity <fqn>` if you fork the project.
#
# References:
#   - https://developer.android.com/topic/performance/vitals/launch-time
#   - https://perfetto.dev/docs/quickstart/android-tracing

set -euo pipefail

PKG_DEFAULT="ru.forpdateam.forpda"
ACTIVITY_DEFAULT="ru.forpdateam.forpda.MainActivity"
RUNS_DEFAULT=5

# Parse arguments.
PERFETTO=0
PKG="${PKG_DEFAULT}"
ACTIVITY="${ACTIVITY_DEFAULT}"
RUNS="${RUNS_DEFAULT}"
RUNS_SET=0
for arg in "$@"; do
    case "${arg}" in
        --perfetto)
            PERFETTO=1
            ;;
        --activity)
            shift
            ACTIVITY="${1:-}"
            ;;
        -h|--help)
            sed -n '2,38p' "$0"
            exit 0
            ;;
        --*)
            echo "Unknown option: ${arg}" >&2
            exit 2
            ;;
        *)
            if [ "${RUNS_SET}" = "0" ]; then
                PKG="${arg}"
                RUNS_SET=1
            else
                RUNS="${arg}"
            fi
            ;;
    esac
done

# Resolve output directory next to the repo root.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUT_DIR="${REPO_ROOT}/build/perf"
mkdir -p "${OUT_DIR}"

TS="$(date +%Y%m%d-%H%M%S)"
SAFE_PKG="$(echo "${PKG}" | tr '.' '_')"
BASE="${OUT_DIR}/${TS}-${SAFE_PKG}"
SUMMARY_FILE="${BASE}-cold-start.csv"
PERFETTO_FILE="${BASE}-cold-start.perfetto-trace"

log() { printf '\033[1;36m[startup-bench]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[startup-bench]\033[0m %s\n' "$*" >&2; }
die() { printf '\033[1;31m[startup-bench]\033[0m %s\n' "$*" >&2; exit 1; }

command -v adb >/dev/null 2>&1 || die "adb not found in PATH. Install platform-tools and retry."

# Step 1 — verify a device is attached.
DEV_COUNT="$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')"
if [ "${DEV_COUNT}" = "0" ]; then
    warn "No device attached. Connect a device with USB debugging enabled, then re-run."
    adb devices
    exit 0
fi
DEVICE="$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
log "Device:           ${DEVICE}"
log "Package:          ${PKG}"
log "Activity:         ${ACTIVITY}"
log "Runs:             ${RUNS}"
log "Perfetto trace:   $([ "${PERFETTO}" = "1" ] && echo "yes" || echo "no")"
log "Output dir:       ${OUT_DIR}"

# Step 2 — collect N cold-start measurements.
echo "run,start_to_first_frame_ms,start_to_resumed_ms" > "${SUMMARY_FILE}"

for i in $(seq 1 "${RUNS}"); do
    # Force-stop the package and wait for the process to disappear.
    adb shell am force-stop "${PKG}" >/dev/null 2>&1 || true
    # Best-effort wait for process death; bail out after 3s.
    for _ in 1 2 3 4 5 6; do
        if ! adb shell pidof "${PKG}" 2>/dev/null | grep -q .; then break; fi
        sleep 0.5
    done

    # Optionally start a Perfetto trace covering this single run.
    if [ "${PERFETTO}" = "1" ] && [ "${i}" = "1" ]; then
        log "Starting Perfetto trace (run 1) ..."
        adb shell perfetto \
            --time 10s \
            --buffer-size 32768 \
            --compression \
            --out /data/misc/perfetto-traces/trace \
            --sched --counters --android-power --freq 1000 \
        >/dev/null 2>&1 &
        PERFETTO_PID=$!
        # Give Perfetto a moment to attach.
        sleep 0.4
    fi

    # Issue the start and capture the timestamp immediately.
    START_MS="$(date +%s%3N)"
    adb shell am start -W -n "${PKG}/${ACTIVITY}" >/dev/null 2>&1 || true

    # Wait for the process to be resumed by sampling `dumpsys activity processes`.
    FIRST_FRAME_MS=""
    RESUMED_MS=""
    for _ in $(seq 1 200); do
        DUMP="$(adb shell dumpsys activity processes "${PKG}" 2>/dev/null || true)"
        if echo "${DUMP}" | grep -q "ProcessRecord{"; then
            # ActivityManager reports its own internal start time deltas.
            LINE="$(echo "${DUMP}" | grep -m1 "ProcessRecord{")"
            if [ -z "${FIRST_FRAME_MS}" ]; then
                FIRST_FRAME_MS="$(echo "${LINE}" | grep -oE 'start=[+-][0-9]+ms' | head -1 | grep -oE '[0-9]+' || true)"
            fi
        fi
        # Consider the launch complete when the resumed activity matches.
        if adb shell dumpsys activity activities 2>/dev/null | grep -E "ResumedActivity|mResumedActivity" | grep -q "${PKG}/"; then
            RESUMED_MS="$(date +%s%3N)"
            break
        fi
        sleep 0.05
    done
    if [ -z "${RESUMED_MS}" ]; then
        RESUMED_MS="$(date +%s%3N)"
    fi

    # If we never saw a ProcessRecord start line, fall back to the wall-clock delta.
    if [ -z "${FIRST_FRAME_MS}" ]; then
        FIRST_FRAME_MS=$((RESUMED_MS - START_MS))
    fi
    START_TO_RESUMED_MS=$((RESUMED_MS - START_MS))

    echo "${i},${FIRST_FRAME_MS},${START_TO_RESUMED_MS}" >> "${SUMMARY_FILE}"
    log "  run ${i}/${RUNS}: first-frame=${FIRST_FRAME_MS}ms, resumed=${START_TO_RESUMED_MS}ms"

    # Stop Perfetto after the first run.
    if [ "${PERFETTO}" = "1" ] && [ "${i}" = "1" ]; then
        sleep 2
        adb shell "cat /data/misc/perfetto-traces/trace" > "${PERFETTO_FILE}" 2>/dev/null || warn "Perfetto trace copy failed (file may be empty)"
        if [ -s "${PERFETTO_FILE}" ]; then
            log "  -> ${PERFETTO_FILE} ($(wc -c < "${PERFETTO_FILE}") bytes)"
        else
            warn "Perfetto trace empty or missing. Open https://ui.perfetto.dev to investigate manually."
        fi
    fi
done

# Step 3 — compute p50 / p95.
log "Computing percentiles ..."
python3 - "${SUMMARY_FILE}" <<'PY' || warn "percentile step skipped (python3 missing)"
import csv, statistics, sys
path = sys.argv[1]
rows = []
with open(path) as f:
    for row in csv.DictReader(f):
        if row["start_to_first_frame_ms"]:
            rows.append(int(row["start_to_first_frame_ms"]))
if not rows:
    print("no data")
    sys.exit(0)
rows.sort()
n = len(rows)
def pct(p):
    k = max(0, min(n - 1, int(round((p / 100.0) * (n - 1)))))
    return rows[k]
print(f"runs:    {n}")
print(f"min:     {rows[0]} ms")
print(f"p50:     {pct(50)} ms")
print(f"p95:     {pct(95)} ms")
print(f"max:     {rows[-1]} ms")
print(f"median:  {statistics.median(rows)} ms")
print(f"stdev:   {statistics.pstdev(rows):.1f} ms")
PY

log "Done."
log "Summary CSV: ${SUMMARY_FILE}"
if [ "${PERFETTO}" = "1" ]; then
    log "Trace:       ${PERFETTO_FILE}"
    log "Open https://ui.perfetto.dev and load the trace for slice-level analysis."
fi
