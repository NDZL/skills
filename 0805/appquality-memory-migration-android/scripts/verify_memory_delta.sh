#!/bin/sh
# Capture one side of a before-and-after memory comparison for a single migration step.
#
# Optional accelerator for appquality-memory-migration-android. The manual fallback is to run each
# command individually as listed in references/usage-patterns.md -- this script only batches them.
# It reads only; it changes nothing on the device or in the project.
#
# ASK THE DEVELOPER BEFORE RUNNING THIS. It touches a device they may not own.
#
# Usage:
#   ./verify_memory_delta.sh <application-id> before <scenario-label> [visible|not-visible]
#   ./verify_memory_delta.sh <application-id> after  <scenario-label> [visible|not-visible]
#
# THE RULE THIS SCRIPT EXISTS TO ENFORCE: the before and after samples must use the SAME unit, the
# SAME process state, the SAME scenario and the SAME device. A before figure in Java heap and an
# after figure in RSS prove nothing -- that is a fabricated delta (anti-pattern AP-06).

set -u

PKG="${1:-}"
PHASE="${2:-}"
SCENARIO="${3:-unspecified}"
STATE="${4:-visible}"

if [ -z "$PKG" ] || [ -z "$PHASE" ]; then
  echo "usage: $0 <application-id> before|after <scenario-label> [visible|not-visible]" >&2
  exit 2
fi

case "$PHASE" in
  before|after) ;;
  *) echo "phase must be 'before' or 'after'" >&2; exit 2 ;;
esac

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found. Use the manual fallback in references/usage-patterns.md." >&2
  echo "Without a device the step is still applicable; report the improvement as UNMEASURED." >&2
  exit 3
fi

if [ -z "$(adb devices | sed -n '2p')" ]; then
  echo "No device connected." >&2
  echo "Apply the step and report it as applied with the improvement UNMEASURED." >&2
  echo "Cap the achieved validation level at 'Build and behavior'." >&2
  exit 3
fi

MODEL="$(adb shell getprop ro.product.model | tr -d '\r')"
OSVER="$(adb shell getprop ro.build.version.release | tr -d '\r')"
PID="$(adb shell pidof -s "$PKG" 2>/dev/null | tr -d '\r')"

if [ -z "$PID" ]; then
  echo "Process not running. Start the app and drive the scenario first." >&2
  exit 4
fi

echo "=== $PHASE sample ==="
echo "tuple: {device: $MODEL, os: $OSVER, unit: RssAnon, state: $STATE, scenario: $SCENARIO}"
echo

# RssAnon is the number that matters for memory-limit survival: anonymous pages can be compressed
# into zRAM but never dropped, which is what AnonSwap kills for.
adb shell "grep -E 'RssAnon|RssFile|VmRSS|VmSwap' /proc/$PID/status" | tr -d '\r'

echo
echo "--- Java heap (separate ceiling; governs OutOfMemoryError -- do NOT mix with the above) ---"
adb shell dumpsys meminfo "$PKG" | grep -iE "Dalvik Heap|Native Heap|TOTAL PSS" | tr -d '\r'

echo
echo "--- leak indicators ---"
adb shell dumpsys meminfo "$PKG" | grep -iE "Activities:|Views:" | tr -d '\r'

if [ "$STATE" = "visible" ]; then
  echo
  echo "REMINDER: for any step touching a service or worker, sample the NOT-VISIBLE state too."
  echo "Press Home while the work runs, then re-run with 'not-visible'. That ceiling is tighter"
  echo "and is usually the binding one."
fi

if [ "$PHASE" = "after" ]; then
  echo
  echo "Compare against the 'before' sample only if EVERY tuple field matches. If any field"
  echo "differs, the delta is not evidence -- re-measure instead of reporting it."
fi
