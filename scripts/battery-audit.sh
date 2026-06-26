#!/usr/bin/env bash
#
# battery-audit.sh — collect Android battery stats for ForPDA via adb.
#
# Usage:
#   ./scripts/battery-audit.sh                       # default package (ru.forpdateam.forpda)
#   ./scripts/battery-audit.sh ru.forpdateam.forpda.beta
#   ./scripts/battery-audit.sh --reset ru.forpdateam.forpda.debug
#
# The script:
#   1) checks `adb devices` and exits cleanly if no device is attached;
#   2) captures `dumpsys batterystats --checkin <package>` into a timestamped
#      text file (safe, non-destructive);
#   3) prints a small human-readable summary of the same data (wake locks,
#      alarms, JobScheduler entries for the package);
#   4) runs `adb shell dumpsys alarm` and `dumpsys jobscheduler` filtered to
#      the package so you can see scheduled work at a glance;
#   5) optionally, with `--reset`, calls `dumpsys batterystats --reset` to
#      wipe history AFTER the checkin dump is safely written.
#
# Output is written to ./build/battery-audit/ (auto-created). The script is
# idempotent and safe to re-run.
#
# NOTE: `dumpsys batterystats --reset` is destructive. Only use it after you
# have a baseline file on disk.

set -euo pipefail

PKG_DEFAULT="ru.forpdateam.forpda"

# Parse arguments.
RESET=0
PKG="${PKG_DEFAULT}"
for arg in "$@"; do
    case "${arg}" in
        --reset)
            RESET=1
            ;;
        -h|--help)
            sed -n '2,30p' "$0"
            exit 0
            ;;
        --*)
            echo "Unknown option: ${arg}" >&2
            exit 2
            ;;
        *)
            PKG="${arg}"
            ;;
    esac
done

# Resolve output directory next to the repo root.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUT_DIR="${REPO_ROOT}/build/battery-audit"
mkdir -p "${OUT_DIR}"

TS="$(date +%Y%m%d-%H%M%S)"
SAFE_PKG="$(echo "${PKG}" | tr '.' '_')"
BASE="${OUT_DIR}/${TS}-${SAFE_PKG}"
CHECKIN_FILE="${BASE}-batterystats-checkin.txt"
RAW_FILE="${BASE}-batterystats-raw.txt"
ALARM_FILE="${BASE}-alarms.txt"
JOB_FILE="${BASE}-jobs.txt"
SUMMARY_FILE="${BASE}-summary.txt"

log() { printf '\033[1;36m[battery-audit]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[battery-audit]\033[0m %s\n' "$*" >&2; }
die() { printf '\033[1;31m[battery-audit]\033[0m %s\n' "$*" >&2; exit 1; }

command -v adb >/dev/null 2>&1 || die "adb not found in PATH. Install platform-tools and retry."

# Step 1 — verify a device is attached.
DEV_COUNT="$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')"
if [ "${DEV_COUNT}" = "0" ]; then
    warn "No device attached. Connect a device with USB debugging enabled, then re-run."
    adb devices
    exit 0
fi
DEVICE="$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
log "Device: ${DEVICE}"
log "Package: ${PKG}"
log "Output dir: ${OUT_DIR}"

# Step 2 — checkin dump (safe, machine-readable).
log "Capturing dumpsys batterystats --checkin ..."
adb shell dumpsys batterystats --checkin "${PKG}" > "${CHECKIN_FILE}"
log "  -> ${CHECKIN_FILE} ($(wc -l < "${CHECKIN_FILE}") lines)"

# Step 2b — full raw dump (for grep-ability).
log "Capturing dumpsys batterystats (raw) ..."
adb shell dumpsys batterystats > "${RAW_FILE}"
log "  -> ${RAW_FILE} ($(wc -l < "${RAW_FILE}") lines)"

# Step 3 — alarms and jobs filtered to the package.
log "Capturing dumpsys alarm (filtered) ..."
adb shell dumpsys alarm > "${ALARM_FILE}"
log "  -> ${ALARM_FILE}"
log "Capturing dumpsys jobscheduler (filtered) ..."
adb shell dumpsys jobscheduler > "${JOB_FILE}"
log "  -> ${JOB_FILE}"

# Step 4 — human-readable summary.
log "Building summary ..."
{
    echo "Battery audit summary for package ${PKG}"
    echo "Device: ${DEVICE}"
    echo "Generated: ${TS}"
    echo
    echo "=== Files ==="
    echo "checkin: ${CHECKIN_FILE}"
    echo "raw:     ${RAW_FILE}"
    echo "alarms:  ${ALARM_FILE}"
    echo "jobs:    ${JOB_FILE}"
    echo
    echo "=== Top wake-lock holders (raw, all packages) ==="
    grep -E "Wake lock|partial|wake " "${RAW_FILE}" | head -40 || true
    echo
    echo "=== forpda wake-lock lines (raw) ==="
    grep -nE "forpda|Wake lock" "${RAW_FILE}" | grep -i forpda | head -40 || true
    echo
    echo "=== Alarms matching ${PKG} ==="
    grep -nE "${PKG}|Alarm" "${ALARM_FILE}" | grep -i forpda | head -40 || true
    echo
    echo "=== JobScheduler entries matching ${PKG} ==="
    grep -nA2 -E "${PKG}" "${JOB_FILE}" | head -80 || true
} > "${SUMMARY_FILE}"
log "  -> ${SUMMARY_FILE}"

# Step 5 — optional reset, only after the checkin dump is safely on disk.
if [ "${RESET}" = "1" ]; then
    log "Captured baseline. Now resetting batterystats (destructive) ..."
    adb shell dumpsys batterystats --reset
    log "Done. From now on, batterystats will only reflect post-reset activity."
fi

log "Suggested next steps:"
log "  grep -E 'Wake lock|partial|wake' ${RAW_FILE} | grep -i forpda"
log "  grep -A5 '${PKG}' ${JOB_FILE}"
log "  grep -E 'Alarm' ${ALARM_FILE} | grep -i forpda"
log
log "Done."
