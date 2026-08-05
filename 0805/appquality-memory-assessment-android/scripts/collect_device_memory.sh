#!/bin/sh
# Collect memory ceilings and counters from a connected Android device.
#
# Optional accelerator for appquality-memory-assessment-android. The manual fallback is to run each
# command individually as listed in references/measurement.md section 2 -- this script only batches
# them. It reads only; it changes nothing on the device or in the project.
#
# ASK THE DEVELOPER BEFORE RUNNING THIS. It touches a device they may not own.
#
# Usage:  ./collect_device_memory.sh <application-id> [iterations]
#
# Every value printed must be recorded with its tuple:
#   {device, ram tier, os build, unit, process state, scenario, value}
# A number without its tuple is not evidence.

set -u

PKG="${1:-}"
ITERATIONS="${2:-0}"

if [ -z "$PKG" ]; then
  echo "usage: $0 <application-id> [iterations]" >&2
  exit 2
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found. Use the manual fallback in references/measurement.md section 2." >&2
  exit 3
fi

if [ -z "$(adb devices | sed -n '2p')" ]; then
  echo "No device connected. The static assessment remains valid as scaling-only;" >&2
  echo "report baseline, accumulation rate and ceiling as UNKNOWN." >&2
  exit 3
fi

section() { echo; echo "=== $1 ==="; }

section "device identity and totals"
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.config.low_ram
adb shell cat /proc/meminfo | head -3

section "ceilings -- Java heap (governs OutOfMemoryError)"
adb shell getprop dalvik.vm.heapgrowthlimit
adb shell getprop dalvik.vm.heapsize

section "ceilings -- Memory Limiter (Android 17+; governs AnonSwap kills)"
adb shell am memory-limiter status 2>/dev/null \
  || echo "unavailable: pre-Android-17, or the mechanism is disabled on this build"
adb shell cat /vendor/etc/memory-limiter-config.xml 2>/dev/null \
  || echo "vendor configuration not readable -- if absent or invalid, Memory Limiter is DISABLED here"

section "resident device software BEFORE the app (the one-tenant picture)"
adb shell dumpsys meminfo | head -40

PID="$(adb shell pidof -s "$PKG" 2>/dev/null | tr -d '\r')"
if [ -z "$PID" ]; then
  echo
  echo "Process not running. Start the app, then re-run for per-process counters."
  exit 0
fi

section "the number that matters -- RssAnon (correct unit for Memory Limiter)"
adb shell "grep -E 'VmRSS|RssAnon|RssFile|RssShmem|VmSwap' /proc/$PID/status"

section "cgroup enforcement counters (path varies by build)"
CG="$(adb shell "find /sys/fs/cgroup -name 'memory.high' -path '*uid*' 2>/dev/null | head -1" | tr -d '\r')"
if [ -n "$CG" ]; then
  DIR="$(dirname "$CG")"
  for f in memory.current memory.high memory.swap.current; do
    printf '%s: ' "$f"
    adb shell "cat $DIR/$f 2>/dev/null" || echo "unreadable"
  done
else
  echo "cgroup memory.high not located -- record as UNKNOWN, do not substitute a figure"
fi

section "attribution and leak indicators (PSS -- NOT an enforcement unit)"
adb shell dumpsys meminfo "$PKG" | head -30
echo "# Activities: greater than the number actually open == a leak"
echo "# Views: growing across repeated navigation == a leak"

section "kill forensics"
adb shell dumpsys activity exit-info "$PKG" 2>/dev/null | head -30 \
  || echo "unavailable below API 30"
echo "# REASON_OTHER with a description containing MemoryLimiter:AnonSwap == a memory-limit kill"

if [ "$ITERATIONS" -gt 0 ]; then
  section "accumulation samples ($ITERATIONS) -- axis 2"
  echo "# Drive ONE full workflow iteration between samples, then fit a line."
  echo "# Discard roughly the first five samples for warm-up."
  echo "# Express the slope PER BUSINESS TRANSACTION, not per hour."
  i=1
  while [ "$i" -le "$ITERATIONS" ]; do
    printf 'sample %s: ' "$i"
    adb shell "grep RssAnon /proc/$PID/status" | tr -d '\r'
    i=$((i + 1))
  done
fi

section "reminders"
echo "- Record every value with its tuple; absolute values are not comparable across devices."
echo "- Sample the NOT-VISIBLE state too: press Home while a worker runs, then re-run."
echo "- Never compare a Java heap figure to a Memory Limiter ceiling."
echo "- 'adb shell am kill $PKG' simulates the low-memory killer; force-stop does NOT."
