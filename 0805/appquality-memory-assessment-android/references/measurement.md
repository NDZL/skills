# Measurement — reading real values from a device

Everything here needs a shell and a connected device, and is therefore an **enhancement**, never the
core path. Without it the assessment is valid as scaling-only and must say so. The batching helper is
`../scripts/collect_device_memory.sh`; every command below can be run individually by hand, which is
the manual fallback.

**Ask before running anything.** These commands read from a device the developer may not own.

## 1. Protocol discipline

Without these, numbers are not comparable and will be misquoted later:

1. **Same device, same OS build, same resident software** — the Zebra stack, the management agent, and
   any VPN client all present. An emulator cannot reproduce the conditions that matter; it is valid
   for *deltas* only, never for absolute headroom.
2. **Warm up first.** Discard the first several iterations; just-in-time compilation, caches, and lazy
   initialisation inflate them.
3. **Sample the not-visible state too** — press Home while a worker runs, then sample again.
4. **Record the tuple every time:** device, RAM tier, OS build, unit, process state, scenario, value.
   A number without its tuple is not evidence.
5. **Never compare across units** — see [api-patterns.md](api-patterns.md).

## 2. Commands

Set the package once:

```bash
PKG=<application-id>
PID=$(adb shell pidof -s $PKG)
```

### Ceilings

```bash
# Android 17+: the enforced limits, in MB, plus event and process counts
adb shell am memory-limiter status

# The vendor configuration — these are the device vendor's values, not Google's
adb shell cat /vendor/etc/memory-limiter-config.xml

# The separate Java heap ceiling, which governs OutOfMemoryError
adb shell getprop dalvik.vm.heapgrowthlimit
adb shell getprop dalvik.vm.heapsize

# Is the platform treating this as a low-memory device?
adb shell getprop ro.config.low_ram

# Device totals; MemAvailable is the number that matters
adb shell cat /proc/meminfo | head -3
```

If the vendor configuration file is missing, unreadable, or invalid, Memory Limiter is **disabled** on
that device. Record that, because it changes the risk picture for that SKU.

### The number that matters

```bash
# Correct unit for Memory Limiter, cheap, per-process
adb shell "grep -E 'VmRSS|RssAnon|RssFile|RssShmem|VmSwap' /proc/$PID/status"
#   RssAnon  <- anonymous: what AnonSwap kills you for. TRACK THIS.
#   RssFile  <- clean file-backed: the cheap kind that can simply be dropped
```

Enforcement counters live in the process cgroup v2 directory. The path varies by build, so locate it
rather than assuming:

```bash
adb shell "find /sys/fs/cgroup -name 'memory.high' -path '*uid*' 2>/dev/null | head"
# then read, in that directory: memory.current  memory.high  memory.swap.current  memory.stat
```

### Attribution and leak indicators

```bash
# What the resident device software costs BEFORE the app starts
adb shell dumpsys meminfo | head -40

# The app's own breakdown
adb shell dumpsys meminfo $PKG
#   Activities: greater than the number actually open  ==  a leak
#   Views: growing across repeated navigation          ==  a leak
```

PSS from `dumpsys meminfo` is **attribution, not enforcement**. Do not compare it to a Memory Limiter
ceiling.

### Kill forensics

```bash
adb shell dumpsys activity exit-info $PKG
```

A Memory Limiter kill reports `REASON_OTHER` with a description containing `MemoryLimiter:AnonSwap`.
There is no stack trace, so this is the only trail.

### Axis 2 — accumulation rate per business transaction

```bash
for i in $(seq 1 30); do
  # drive exactly one full workflow iteration here
  # (an instrumented test, a UI-automation script, or a scripted input sequence)
  adb shell "grep RssAnon /proc/$PID/status"
done
```

Fit a line to the samples; the slope is memory per transaction. Discard roughly the first five for
warm-up. Then:

```
transactions-to-kill = (ceiling − steady state) / per-transaction cost
```

### Reproducing a kill deliberately

```bash
# Force a tight limit on a running process and confirm behaviour
adb shell am memory-limiter manual $PID 300MB

# Exempt a UID while bisecting
adb shell am memory-limiter ignore <uid>

# Simulate the low-memory killer. force-stop is NOT equivalent and will mislead you.
adb shell am kill $PKG
adb shell ps -A | grep $PKG        # confirm the process died, then relaunch
```

`am kill` followed by relaunch is also the test for whether workflow state survives process death.

## 3. Recording the result

Emit one line per sample so the plan can cite it:

```
{device: TC26, ram: 3GB, os: A16, unit: RssAnon, state: not-visible, scenario: full-sync, value: 214MB}
```

Then compute headroom per [quantification.md](quantification.md) §4, and name the binding state.

## 4. What measurement still cannot tell you

- Record counts at the largest customer — a business question.
- Whether a structure is unbounded *by construction* — that is static, and stronger evidence than any
  single measurement.
- Future ceilings as the fleet upgrades — see axis 4.

Provenance for every command and behaviour: [sources.md](sources.md).
