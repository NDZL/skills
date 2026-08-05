# Android 17 Memory Limiter — what it is, and what it means for an enterprise fleet

**Written:** 2026-08-05
**Skill:** `appquality-memory-assessment-android` v1.0.0-beta.1 — `explain` mode
**Scope:** conceptual explainer. No project was inspected to produce this.

Companion documents in this folder:
`MSFT-ZFINDIT-memory-assessment.md` (static, whole repo) ·
`split-picker-memory-assessment.md` (device-measured)

---

## 1. The mechanism

Memory Limiter is a system service built on **Linux cgroup v2**, integrated with the activity
manager. For each app process it sets two knobs:

- `memory.high` — a memory ceiling
- `memory.swap.max` — a cap on how much of that can be pushed to swap

The critical word is **soft**. `memory.high` is not a wall you hit and die against. It is a threshold
past which the kernel starts *pushing back*: reclaiming pages and throttling the allocating process.
Death arrives only later, when sustained allocation exhausts swap capacity and an allocation finally
fails.

So the shape of the failure is **degradation first, death second**. That is fundamentally different
from `OutOfMemoryError`, which is instant, deterministic, and carries a stack trace.

### What gets counted, and what the kernel can do about it

Both file-backed **and** anonymous memory count toward the limit. But the two are not equally
dangerous, because the kernel's options differ:

| Kind of memory | Examples | Under pressure | Cost |
|---|---|---|---|
| **Clean, file-backed** | mapped APK segments, SQLite pages, resources, `.so`, `.art` | **Dropped**, re-read from flash if needed | Cheap |
| **Anonymous** | Java heap, in-memory collections, decoded bitmaps | Must be **compressed into zRAM**, or fail | Expensive |

This is the physics the feature is named for. **Anonymous pages cannot be evicted, only compressed.**
Compressed anonymous memory still occupies physical RAM. You cannot page your way out — anonymous
memory can be made smaller, never free. Hence `MemoryLimiter:AnonSwap`.

A strategic direction falls straight out of this: **shift bytes from anonymous into clean
file-backed**. It is why "let the database be your cache" beats merely shrinking a map — it converts
expensive bytes into cheap ones. File-backed memory still counts toward the limit, so this is not a
loophole; it changes what the kernel *does* when it needs the memory back, which is the difference
between a slowdown and a kill.

### The limit depends on what the system thinks you are doing

| State group | Examples | Limit |
|---|---|---|
| Visible | top, bound-top, important-foreground, top-sleeping | more generous |
| **Not visible** | **foreground service**, bound foreground service, important-background, service, receiver, home, last-activity | **more restrictive** |
| Cached | cached activity, cached empty | frozen, then maximally reclaimed |
| Unrestricted | persistent, persistent-UI | exempt |

### Two consequences that matter more than the mechanism itself

**The limits are the device vendor's to define.** The configuration lives in the vendor partition, so
on a Zebra device the thresholds are Zebra's values. The only publicly published example is for a
device with **at least 14 GB of RAM** — it tells you nothing whatsoever about a 3 GB handheld. There
is no published per-device threshold for any Zebra RAM tier. The enforced value must be read from the
device.

**If the configuration file is missing, unreadable or invalid, Memory Limiter is disabled on that
device.** That is not hypothetical. Measured on the Zebra TC701 in this engagement:

```
$ adb shell am memory-limiter status
Unknown command: memory-limiter

$ adb shell ls -l /vendor/etc/memory-limiter-config.xml
ABSENT

Device: Zebra TC701 (TC701L/TCX01LD) · Android 15, API 35
Build:  15-16-15.04-VG-U00-STD-ERS-04 · MemTotal 11.32 GiB
```

Android 15, so the mechanism does not exist there regardless — but the absent config is the second,
independent reason it would not run.

### Who actually sets `memory.high` and `memory.swap.max`

Short answer: **the OEM. Not the user, and not the app.**

| Actor | Can they set it? | How |
|---|---|---|
| **Device OEM / vendor** | ✅ **Yes — the only production control surface** | A configuration file in the **vendor partition** (`/vendor/etc/memory-limiter-config.xml`). On a Zebra device these are Zebra's numbers, not Google's. |
| The Memory Limiter service | Applies them | Computes per-process values — **derived from total device RAM** — and writes the cgroup v2 files. It is the executor of the vendor's policy, not an independent decision-maker. |
| **The app** | ❌ **No** | There is no API to raise your own limit. `android:largeHeap="true"` raises the *Java heap* ceiling — a different unit entirely — and makes the cgroup position **worse**, because a bigger heap reaches `memory.high` sooner. |
| **The end user** | ❌ **No** | No Settings surface. Not user-tunable, not per-app tunable, not exposed anywhere in the UI. |
| **A developer over adb** | ⚠️ Temporarily, for debugging only | `am memory-limiter manual <pid> <limit>` forces a tight limit on a running process; `am memory-limiter ignore <uid>` exempts a UID while bisecting. Diagnostic tools — they do not persist as policy. |
| **An EMM / MDM** | ❓ **No documented surface** | Nothing in the sources consulted exposes this as managed configuration. Treat as vendor-only and **confirm with Zebra** rather than assuming an EMM can relax it for a business-critical app. |

Three implications worth internalising:

1. **You cannot negotiate your ceiling.** The denominator is fixed by someone else's decision, taken
   at ROM build time. The only variable under your control is the numerator — your own footprint.
   Every mitigation available to an app is therefore a *reduction*, never an *exemption*.
2. **The exemption that exists is not for you.** `persistent` and `persistent-UI` processes are
   unrestricted, but that is a platform privilege for system components. An ordinary enterprise app
   cannot opt into it, however business-critical it is.
3. **The OEM's control includes an implicit off switch.** Because the mechanism disables itself when
   the configuration is absent or invalid, a vendor that ships a malformed file turns enforcement off
   for the entire device. That is why per-SKU verification matters more here than for most platform
   features: *whether the feature is on at all* is a per-device fact you have to read.

---

## 2. Why enterprise gets hit differently

Three reasons, and they compound.

### 2.1 The not-visible bucket is where enterprise apps do their heaviest work

A warehouse app's largest allocation is rarely the UI. It is the overnight bulk sync, the full
catalogue download, the batch upload — running in a foreground service or a background worker.

Those sit in the **not visible** state group, which receives the **more restrictive** limit.

> **The app receives its smallest allowance at its largest moment.**

A sync that passes every interactive test on a developer's desk gets killed when backgrounded. This
is the most surprising consequence of the design, and it is the one most likely to be discovered in
production rather than in testing. It also means that measuring only the interactive peak flatters
an app *exactly where it is weakest*.

### 2.2 The kill is untraceable

There is **no stack trace**. A Memory Limiter kill surfaces as `REASON_OTHER` with a description
string containing `MemoryLimiter:AnonSwap`, readable only through `ApplicationExitInfo` (API 30+).

Every crash dashboard shows **nothing at all**. The app simply was not there any more.

This is a genuine regression in diagnosability compared with `OutOfMemoryError`, and it is why
`android:largeHeap="true"` goes from "bad idea" to "actively harmful": a larger heap reaches the
cgroup limit *sooner*, converting a reproducible, stack-traced error into a silent disappearance.
It makes diagnosis harder — the exact opposite of what the person who added the flag intended.

### 2.3 Fleet behaviour becomes heterogeneous

Because limits are vendor-set, and because the feature self-disables on absent or invalid
configuration, **the same APK will behave differently across SKUs in a single estate**. One device
throttles and kills; another, running the same app version, does not.

Zebra publishes per-device Android commitments for **16, 18 and 19** — 17 is not listed per device.
So which SKUs inherit this mechanism, and when, is genuinely unknown at the time of writing. What is
certain is that **any device reaching Android 17 or later inherits it**, so an 18 commitment is an
inherited-Memory-Limiter commitment.

For a support organisation this is the worst property of all: the same symptom, on the same app
version, reproducible on some devices and not others, with no stack trace on either.

---

## 3. How does an app learn it is degrading, before it is killed?

**Short answer: it mostly does not. There is no push notification for memory pressure any more.**
This is the sharpest problem in the whole picture, and it is worth being precise about, because the
intuitive assumption — "the system will tell me when I'm in trouble" — was true five years ago and
is not true now.

### The timeline, and what arrives at each stage

| Phase | What is physically happening | What the app is told |
|---|---|---|
| Below `memory.high` | Normal operation | Nothing |
| **Crossing `memory.high`** | Kernel reclaims; the process is **throttled** | **Nothing. No callback fires.** |
| Sustained pressure | Clean file-backed pages dropped; anonymous pages compressed into zRAM | Nothing — but allocations start getting slower |
| Swap capacity exhausted | An allocation fails | Process killed |
| **Just before death** (A17+) | — | `ProfilingManager` `TRIGGER_TYPE_ANOMALY` can deliver a heap dump *just before* termination — **forensics, not a chance to react** |
| Next launch | — | `ApplicationExitInfo`: `REASON_OTHER` + a description containing `MemoryLimiter:AnonSwap` |

Read the middle rows again. **The entire degradation phase — the part that exists precisely so the
app has a chance to respond — is silent.**

### The push channel that used to exist is gone

| Signal | Status |
|---|---|
| `TRIM_MEMORY_RUNNING_LOW`, `RUNNING_CRITICAL`, `RUNNING_MODERATE`, `MODERATE`, `COMPLETE` | **Not delivered since Android 14.** Deprecated in Android 15. |
| `onLowMemory()` | Same — deprecated, effectively dead |
| `TRIM_MEMORY_UI_HIDDEN`, `TRIM_MEMORY_BACKGROUND` | Still delivered — **but these are *visibility* signals, not *pressure* signals** |

That distinction is the crux. `UI_HIDDEN` tells you *the user can no longer see you*. It says nothing
whatsoever about how close you are to `memory.high`. An app sitting at 99 % of its limit **in the
foreground** receives neither of the two surviving callbacks. The only two signals left fire on a
lifecycle event, not on a memory event.

**Measured directly in this engagement.** `:split-picker` implements no `onTrimMemory` at all, and on
the TC701 (Android 15) all three legacy levels were sent to it:

| Trim level sent | Native Heap RSS after |
|---|---|
| *(before)* | 31,776 kB |
| `RUNNING_CRITICAL` | 31,872 kB |
| `COMPLETE` | 31,872 kB |
| `BACKGROUND` | 31,864 kB |

Total movement ≈ 16 kB — noise. Nothing was delivered, and nothing was released.

> **The net platform change:** the push-based pressure signal was removed at **Android 14**; the hard
> enforcement mechanism arrives at **Android 17**. No replacement push signal was added in between.
> An app that wants to know it is in trouble has to **poll**.

### So: poll. Here is what to poll

| Instrument | Where | Why |
|---|---|---|
| **`RssAnon`** | `/proc/self/status` | Cheap, per-process, and the correct unit. The headline number for Android 17 survival. |
| **`memory.current` vs `memory.high`** | the process's own cgroup v2 directory | **This ratio *is* your headroom**, in the enforced unit. Path varies by build — locate it, do not hardcode it. |
| `memory.swap.current` vs `memory.swap.max` | same directory | How much runway is left before allocation failure |
| `memory.stat` | same directory | Breakdown behind the above |

One more, worth naming with a caveat: cgroup v2 exposes **`memory.events`**, which carries a `high`
counter that increments each time the group is throttled for exceeding `memory.high`. That is the
closest thing to a definitive *"I am being throttled right now"* signal. **Caveat:** it is standard
kernel behaviour rather than something the skill's Android source set documents, and whether it is
readable from inside an unprivileged app process on a given Android build is **unverified**. Check it
on the target device before designing around it.

Note also that `am memory-limiter status` reports **event counts** — so the system is demonstrably
counting these events. There is simply no documented callback that hands them to you.

### What is *not* the right instrument

- **`Debug.MemoryInfo.getMemoryStat` → `summary.total-pss`.** PSS is proportional attribution. It
  enforces nothing and is slow to compute. Use it to answer *who is using this device*, never *am I
  near my limit*.
- **`ActivityManager.MemoryInfo.lowMemory` / `.threshold`.** That is the system-wide low-memory-killer
  view, not your cgroup. It can read perfectly healthy while you personally are being throttled.
- **Any Java heap figure.** Different ceiling, different death. A 3 MB Java heap alongside large
  native bitmaps is nowhere near `OutOfMemoryError` and can still be lethal here.

### The practical trap: it presents as a performance bug

This is the part that costs teams the most time. Throttling manifests as **allocation stalls**, so
what the operator reports is *"the handheld got slow after lunch"* and what the developer sees is
jank and latency. The ticket gets routed to whoever owns performance. Nobody looks at memory, because
memory produced no error, no log line, and no callback — and on low-RAM devices with weaker SoCs the
zRAM compression cost makes the lag worse, which reinforces the misdiagnosis.

Then the app disappears with no crash report, and the two symptoms are never connected.

**The practical recommendation:** sample `RssAnon` and the `memory.current / memory.high` ratio on a
low-frequency timer, log both alongside your business transactions (per pick, per scan, per label),
and treat a rising ratio as the early warning the platform no longer gives you. That plus
`ApplicationExitInfo` at startup is the whole observability story — and both are things you have to
build yourself.

---

## 4. Does it help or hurt on low-RAM devices?

> **This section is engineering judgement, not vendor guidance.** The sources describe the mechanism;
> they do not take a position on the outcome. Marked explicitly so it is not mistaken for
> documentation.

### Structurally, it helps — and the reason is specific to enterprise

Today, one greedy app degrades the **whole device**. Zebra documents that when a device is low on
memory, DataWedge may not function properly, and the prescribed remedy is to fix the leak or
**uninstall the offending application**.

Read that failure mode carefully: a leaky app breaks **scanning** — the business function — for every
app on the device, and the vendor's own answer is removal. The blast radius today is the entire
handheld.

Memory Limiter converts that into: *the greedy app is contained, and degrades itself.* For a fleet
where the scanner simply must work, that is the right trade and a real improvement. Containment is
worth a great deal when the shared resource is the thing that earns the money.

### But the transition will be rough, and roughest where you would least want it

Three asymmetries stack against low-RAM hardware:

1. **Limits derive from total device RAM.** A 1 GB WS50 gets a *smaller* allowance than a 12 GB
   device. The tightest devices get the tightest enforcement — the mechanism scales down with the
   hardware, which is coherent but unforgiving.
2. **The degradation phase is paid in CPU.** zRAM compression is not free, and low-RAM devices ship
   the weakest SoCs. So the pre-kill misery is worst exactly where the limit is hit most often. The
   operator does not experience "memory pressure"; they experience a handheld that gets progressively
   laggier through the shift.
3. **Then it dies with no crash report.** Slow misery followed by a silent death is a worse support
   experience than a fast, loud crash — even though it is a better *device* outcome.

### The honest expectation

**UX gets worse before it gets better.**

- **Worse during the transition**, because a decade of enterprise Android apps were written assuming
  they could allocate freely and be killed cleanly. Those apps will now throttle, stutter, and vanish
  silently. Nothing about them changed; the ceiling arrived uninvited.
- **Better afterwards**, because the platform finally stops one badly-behaved app from taking the
  scanner — and the shift — down with it.

And there is a compounding pressure the platform cannot fix. Memory prices are keeping low-RAM
devices in service longer and delaying fleet refreshes, so the fleet-weighted median ceiling is
stalling or falling at the same time as app footprints grow. **The denominator is moving the wrong
way.** Evaluate headroom against the worst tier you must support weighted by fleet share — for most
estates a 3 GB handheld, never the newest device on a developer's desk.

---

## 5. What to do about it now

Practical, in priority order, and none of it requires Android 17 to be present yet:

1. **Track `RssAnon`.** Not PSS — PSS is proportional attribution and enforces nothing. Not Java heap
   — that governs `OutOfMemoryError` only. `RssAnon` from `/proc/<pid>/status` is the number this
   mechanism enforces on, and it is cheap to sample.
2. **Add `ApplicationExitInfo` reading now**, before the fleet reaches 17. It is the only trail the
   kill leaves, and adding it after the kills start means the first months of field data are gone.
3. **Measure the not-visible peak specifically.** Press Home while the sync runs, then sample. If you
   only ever measure interactively, you are measuring the state that is not binding.
4. **Never quote a threshold you did not read from the device.** No per-device figure is published
   for any Zebra tier; the 14 GB example is not a substitute. Report the ceiling as UNKNOWN and emit
   the command instead.
5. **Move bytes from anonymous to clean file-backed** where the design allows it. It is the only
   change that alters what the kernel does under pressure rather than merely postponing it.

### Reading the ceilings, when a device is to hand

```bash
PKG=<application-id>
PID=$(adb shell pidof -s $PKG)

# The enforced limits (Android 17+ only)
adb shell am memory-limiter status
adb shell cat /vendor/etc/memory-limiter-config.xml

# The number that matters
adb shell "grep -E 'VmRSS|RssAnon|RssFile|VmSwap' /proc/$PID/status"

# The separate Java heap ceiling, which governs OutOfMemoryError — a different unit
adb shell getprop dalvik.vm.heapgrowthlimit

# Kill forensics
adb shell dumpsys activity exit-info $PKG

# Reproduce deliberately, to test a fix
adb shell am memory-limiter manual $PID 300MB
```

---

## 6. The one analytical error to avoid

**Never compare a number to the wrong ceiling.** Three deaths, three ceilings, three incompatible
units:

| Death | What is counted | Ceiling |
|---|---|---|
| `OutOfMemoryError` | Java/Dalvik heap only | `dalvik.vm.heapgrowthlimit` |
| `MemoryLimiter:AnonSwap` | cgroup v2 anonymous + swap | `memory.high`, `memory.swap.max` |
| Low-memory killer | System-wide pressure vs process priority | `MemAvailable` and system thresholds |

A 3 MB Java heap sitting alongside large native bitmap allocations is nowhere near
`OutOfMemoryError` and can still be lethal under Memory Limiter. An app declared safe at "40 % of the
limit" gets killed in the field because the measured quantity was never the enforced one. This is the
most common analytical mistake in the area and it silently invalidates otherwise careful work.

Worth noting concretely: since API 26, **bitmap pixel data lives in native memory, not the Java
heap** — so a green Java-heap headroom figure says nothing at all about the bitmaps that dominate
most apps' footprint. That was measured directly on the TC701: `:split-picker` sat at 98.6 % Java-heap
headroom while its entire icon working set was invisible to that number.

---

## 7. Sources

Platform behaviour above is drawn from the skill's reference set (`references/sources.md`,
reviewed 2026-08-04):

| ID | Source | Contributes |
|---|---|---|
| A1 | `developer.android.com/topic/performance/memory` | **§3:** legacy trim levels undelivered from Android 14, deprecated in 15; only `TRIM_MEMORY_UI_HIDDEN` and `TRIM_MEMORY_BACKGROUND` remain |
| A2 | `developer.android.com/blog/posts/prioritizing-memory-efficiency-essential-steps-for-android-17` | No stack trace on memory-limit kills; the `MemoryLimiter:AnonSwap` description string; baseline recommendation |
| A3 | `developer.android.com/about/versions/17/behavior-changes-all` | **Limits derive from total device RAM**; exit reason and description; the three memory-limiter shell subcommands |
| A4 | `source.android.com/docs/core/perf/memory-limiter` | cgroup v2 mechanism; soft high limit and swap cap; both file-backed and anonymous counted; the four limit groups; **vendor configuration path and its units**; system-UID exemption; **disabled when configuration is absent or invalid** |
| A5 | `developer.android.com/about/versions/17/features` | **§3:** out-of-memory and anomaly profiling triggers |
| A8 | `developer.android.com/topic/performance/tracing/profiling-manager/trigger-based-capture` | **§3:** trigger semantics — `TRIGGER_TYPE_ANOMALY` fires just before termination |
| A10 | `developer.android.com/topic/performance/memory-management` | RSS vs PSS; clean cached pages dropped while anonymous pages compress into zRAM; LMK priority ordering |
| A11 | `developer.android.com/reference/android/os/Debug.MemoryInfo` | **§3:** in-app memory statistic keys, and why `summary.total-pss` is the wrong instrument |
| Z1 | `techdocs.zebra.com/datawedge/15-0/…/usage-notes/` | Low device memory can stop scanning working; documented remedy is to fix the leak or uninstall the app |
| Z2 | `zebra.com/android-versions` | Per-device commitments for 16, 18 and 19; **17 not listed per device** → per-SKU availability unknown |
| Z9 | `techdocs.zebra.com/emdk-for-android/13-0/guide/ws50_programming/` | WS50's 1 GB shared among kernel, launcher, vendor stack and other services |

**Derived rather than quoted** — the anonymous-versus-clean-file-backed strategy in §1 is an inference
from the reclaim mechanics in A4 and A10; the mechanics are quoted, the synthesis is the skill's.
**§4 in its entirety is engineering judgement**, as flagged there.

**Outside the sourced set, and flagged in place:** `memory.events` and its `high` throttle counter
(§3) are standard cgroup v2 kernel behaviour, not something the Android sources above document.
Readability from an unprivileged app process is **unverified** — confirm on the target device.

**Measured in this engagement, not quoted from any source:** the absent memory-limiter configuration
and unrecognised `am memory-limiter` command on the TC701 (§1), and the trim-level non-response table
(§3).

### Known gaps — stated rather than filled

1. No published memory threshold exists for any Zebra RAM tier. Read it from the device.
2. **Whether Zebra ships a valid memory-limiter configuration at all is unverified.** One data point
   collected here: TC701 on Android 15 has none — but that device predates the feature, so it proves
   nothing about a future Android 18 build.
3. Which Zebra SKUs receive Android 17 or later, and when, is unverified.
4. The Java heap growth limit per Zebra SKU is undocumented in the sources consulted. On the TC701 it
   measured 256 MB (`dalvik.vm.heapgrowthlimit`), with `heapsize` 512 MB.

---

*Generated by `appquality-memory-assessment-android` v1.0.0-beta.1 (Beta — validate before production
use). Explainer only; no project was inspected and no file in any target project was modified.*
