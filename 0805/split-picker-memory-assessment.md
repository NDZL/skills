# Memory assessment — `:split-picker` module (device-measured)

**Target:** `MSFT-ZFINDIT/split-picker`, application ID `com.ndzl.splitpicker`, commit `286ca25`
**Assessed:** 2026-08-05
**Skill:** `appquality-memory-assessment-android` v1.0.0-beta.1 — modes: `inventory`, `estimate`, `plan`
**Companion document:** `MSFT-ZFINDIT-memory-assessment.md` (whole-repo, static). This report
**supersedes finding F5** in that document and **corrects two claims** in it — see §8.

**Assessment kind: MEASURED.** The module was built, installed, launched and instrumented on a
connected Zebra device. This reaches **Device** validation level for `:split-picker` — the first time
in this engagement that any level above Inspection has been reached.

---

## 0. Header — device, ceilings, and units

Every figure below is attributed to this tuple:

| Dimension | Measured value | How read |
|---|---|---|
| Device | **Zebra TC701** (`TC701L` / `TCX01LD`) | `getprop ro.product.model` |
| OS | **Android 15, API 35** | `getprop ro.build.version.release` / `.sdk` |
| Build | `15-16-15.04-VG-U00-STD-ERS-04` | `getprop ro.build.display.id` |
| `MemTotal` | **11,866,316 kB = 11.32 GiB** (a 12 GB-class device) | `/proc/meminfo` |
| `MemAvailable` at start | 8,693,808 kB = 8.29 GiB | `/proc/meminfo` |
| Screen | **1080 × 2160 @ 480 dpi (xxhdpi)** | `wm size`, `wm density` |
| Page size | **4096** — *not* a 16 KB-page device | `getconf PAGE_SIZE` |
| `dalvik.vm.heapgrowthlimit` | **256 MB** | `getprop` |
| `dalvik.vm.heapsize` | 512 MB | `getprop` |
| **Memory Limiter** | **ABSENT** | `am memory-limiter status` → `Unknown command`; `/vendor/etc/memory-limiter-config.xml` **absent** |
| cgroup v2 `memory.high` | **not found / empty** | `find /sys/fs/cgroup -name memory.high -path '*uid*'` |
| `ro.config.low_ram` | unset | `getprop` |
| User profiles | **1** (`Owner`, user 0) — no work profile | `pm list users` |

### 0.1 Which ceiling actually enforces anything here

Three deaths, three ceilings — and on this SKU only one of them exists as a readable per-process
limit:

| Death | Ceiling on this device | Status |
|---|---|---|
| `OutOfMemoryError` | **256 MB Java heap growth limit** | ✅ **Read. This is the only enforced per-process ceiling on this device.** |
| `MemoryLimiter:AnonSwap` | — | ❌ **Does not exist.** Android 15; the mechanism is Android 17+, the command is unrecognised, and the vendor config file is absent. Attributing anything here to Memory Limiter would be fabrication. |
| Low-memory killer | System-wide, priority-scored | ⚠️ Applies. Process `oom_score_adj` measured at **700** when backgrounded — the *previous-app* band, one step above cached. |

> **⚠️ AP-03 warning, stated up front because it governs how this whole report may be used.**
> This device has **11.32 GiB of RAM**. That is above *every* tier in the skill's Zebra table, which
> tops out at 8 GB (TC53/TC58). Measuring here is precisely the "newest device in the fleet" trap.
>
> **What transfers to a 1 GB WS50 or a 3 GB TC26:** the leak result (§3), the object counts, the
> retention ratio (§4.1), and the release behaviour (§4.3). Deltas and structural facts are portable.
>
> **What does NOT transfer:** every absolute MB figure and the headroom band in §2. Do not quote
> those against a lower tier.

---

## 1. What was run

| Step | Result |
|---|---|
| `:split-picker:assembleDebug` | ✅ **BUILD SUCCESSFUL** in 1m 09s, 34 tasks |
| APK produced | `split-picker-debug.apk`, **11.18 MiB** (debug, unminified) |
| `adb install -r -g` | ✅ Success |
| Cold launch | `LaunchState: COLD`, **TotalTime 624 ms**, WaitTime 630 ms |
| Iteration protocol | 12 activity recreations via forced rotation |
| Not-visible sampling | Home key, 6 s settle |
| Pressure test | `am send-trim-memory` × 3 levels |
| Runtime errors | **None** in logcat for this process |

**Two environment notes, so the run is reproducible and nothing is hidden:**

1. `ANDROID_HOME` was supplied **via environment variable**, not by writing `local.properties` into
   the project. Per AP-07 this skill does not modify the target project, and it did not: the only
   files created under `MSFT-ZFINDIT/` are Gradle's own `build/` outputs.
2. **This is a DEBUG build.** `isMinifyEnabled = false` is therefore not exercised — a release build
   would be smaller. The 11.18 MiB APK and the 133,380 kB `.apk mmap` RSS in §2 both reflect debug
   packaging and must not be read as release figures.

---

## 2. Measured footprint

All values kB unless noted. Java-heap figures are the `OutOfMemoryError` unit; RSS figures are the
kernel unit. **They are never compared to each other.**

### 2.1 Cold launch, settled, visible

| Counter | Value | |
|---|---|---|
| `VmRSS` | 335,260 | 327.4 MiB |
| **`RssAnon`** | **85,948** | **83.9 MiB** — the expensive kind |
| `RssFile` | 233,264 | 227.8 MiB — clean, droppable |
| `RssShmem` | 16,048 | 15.7 MiB |
| `VmSwap` | 68 | negligible |
| Native Heap RSS / Alloc | 32,696 / 35,645 | 31.9 / 34.8 MiB |
| **Dalvik Heap Alloc** | **3,650** | **3.57 MiB** |
| Graphics (EGL+GL mtrack) | 42,416 | 41.4 MiB |
| `.apk mmap` RSS | 133,380 | 130.3 MiB (debug APK) |
| TOTAL PSS / TOTAL RSS | 169,533 / 378,628 | *PSS is attribution, not enforcement* |
| Views / Activities / AppContexts | **62 / 1 / 7** | |

### 2.2 Headroom — computed against a ceiling that was actually read

```
H = (C_binding − P_binding) / C_binding
  = (262,144 kB − 3,650 kB) / 262,144 kB
  = 98.6 %
```

| Unit | Ceiling | Peak observed | Headroom | Band |
|---|---|---|---|---|
| **Java heap** (`OutOfMemoryError`) | 256 MB (`heapgrowthlimit`, measured) | 3.57 MiB (Dalvik Heap Alloc) | **98.6 %** | 🟢 **GREEN** |
| cgroup anon+swap (`AnonSwap`) | — | — | **N/A** | Mechanism absent on this SKU |
| LMK | System-wide | — | Not a per-process ratio | `oom_score_adj` 700 |

**Binding constraint: the Java heap, at 1.4 % utilisation.** The module uses 3.57 MiB of a 256 MB
allowance.

**Why that headroom is less reassuring than it looks, and this is the important part:** since API 26
Bitmap pixel data lives in **native** memory, not the Java heap. The icon `Drawable`s — the entire
subject of finding F5 — are therefore **not counted by the 98.6 % figure at all**. The Java heap
ceiling is comfortably green *and it is not the ceiling that the icons load*. On this device nothing
else enforces a per-process limit, so the icons currently face no ceiling whatsoever. On an
Android 17+ device they would face Memory Limiter, in the `RssAnon` unit.

### 2.3 Visible vs not-visible — and the binding state is the *unusual* one

| Counter | Visible (warm) | Not-visible (Home) | Δ |
|---|---|---|---|
| Native Heap RSS | 44,100 | 31,776 | **−12,324** (−12.0 MiB) |
| Native Heap Alloc | 48,935 | 32,505 | −16,430 |
| Dalvik Heap Alloc | 3,577 | 3,526 | −51 |
| **Graphics** | **44,592** | **6,772** | **−37,820 (−36.9 MiB)** |
| TOTAL RSS | 274,660 | 223,916 | −50,744 |
| TOTAL PSS | 125,566 | 74,670 | −50,896 |
| Views / Activities | 62 / 1 | 62 / 1 | 0 / 0 |

> **The binding state for `:split-picker` is VISIBLE, not not-visible.** This is the *opposite* of the
> general rule in the catalogue, and the reason is structural and worth stating: this module has **no
> service, no worker and no background allocation**. It does its only heavy work — 20 icon loads —
> while the user is looking at it, and the framework then reclaims 36.9 MiB of graphics buffers the
> moment it is backgrounded.
>
> This is exactly why AP-03 says to *name* the binding state rather than assume it. For the `:msft`
> module's `ScreenCaptureService` the not-visible state is binding; for `:split-picker` it is not.
> Same repo, opposite answer.

### 2.4 A live demonstration of anonymous vs clean file-backed memory

On re-foregrounding after the app had been backgrounded:

| Counter | Before backgrounding | After re-foregrounding | Δ |
|---|---|---|---|
| `RssAnon` | 85,948 | 85,552 | −396 (flat) |
| **`RssFile`** | **233,264** | **114,704** | **−118,560 (−115.8 MiB)** |

The kernel dropped **115.8 MiB of clean file-backed pages** (mapped APK, `.art`, `.so`, resources)
while the app was in the background, and the app resumed without re-faulting most of them.
`RssAnon` did not move.

This is `api-patterns.md` §3 happening live: **clean file-backed pages are the cheap kind — the
kernel simply drops them. Anonymous pages must be compressed or the process dies.** It is the
empirical case for the strategic direction "shift bytes from anonymous into clean file-backed".

---

## 3. The leak test — the result that transfers to every tier

`split-picker`'s manifest declares **no `configChanges`**, so every rotation fully destroys and
recreates `MainActivity`, re-running `loadApps()` and re-fetching all 20 icons. That is a clean,
repeatable business transaction. Twelve were run.

| Iteration | `RssAnon` (kB) | | Iteration | `RssAnon` (kB) |
|---|---|---|---|---|
| *pre* | 85,948 | | 7 | 118,780 |
| 1 | 106,412 | | 8 | 118,916 |
| 2 | 119,088 | | 9 | 119,044 |
| 3 | 118,996 | | 10 | 118,972 |
| 4 | 119,032 | | 11 | 118,920 |
| 5 | 118,944 | | 12 | 118,820 |
| 6 | 118,868 | | | |

**Warm-up completes by iteration 2** (measurement.md prescribes discarding the first several).
Fitting iterations 3–12:

| Statistic | Value |
|---|---|
| Mean | 118,929 kB |
| Min / Max | 118,780 / 119,044 kB |
| Range | 264 kB = **0.22 % of mean** |
| **Least-squares slope** | **−9.5 kB per recreation** |
| Views across all 12 | **constant at 62** |
| Activities across all 12 | **constant at 1** |

### `L ≈ 0`. There is no leak.

The slope is *negative* — i.e. indistinguishable from zero and well inside sampling noise. All three
independent leak indicators agree:

1. **`RssAnon` slope ≈ 0** across 12 full activity recreations.
2. **`Activities: 1`** — the catalogue's rule is *"greater than the number actually open == a leak"*.
   One activity is open; one is reported.
3. **`Views: 62`, unchanged** — the rule is *"growing across repeated navigation == a leak"*. It did
   not grow at all.

Therefore `transactions-to-kill = (C − F₀) / L` is **not applicable**: with `L ≈ 0` there is no
accumulation to project. The axis-2 "dies after lunch" exposure that dominates the `:msft` module is
**absent** from `:split-picker`.

**What this vindicates in the code:** `loadApps()` calls `apps.clear()` before repopulating
(`MainActivity.kt:68`), the `AppAdapter` honours `convertView` (`:201`), and
`SplitAccessibilityService` nulls its static `instance` in `onUnbind` (`:19-21`). Those three are
correct and the measurement proves it.

**This result is tier-portable.** Absence of a leak is a structural property of the code, not of the
device's RAM. It holds on a WS50 as much as on this TC701.

---

## 4. Findings

### 4.1 · F5-REVISED · MEM-BITMAP-003 · **MEDIUM** (was HIGH) · 20 icons held for 7 rendered rows

**Measured, not modelled:**

| Quantity | Measured value | How |
|---|---|---|
| Launchable activities on device | **39** | `cmd package query-activities -a MAIN -c LAUNCHER` |
| Excluded by `isZebraApp` (`com.zebra.` / `com.symbol.`) | **18** | prefix filter |
| Own package excluded | 1 | `MainActivity.kt:75` |
| **Entries `apps` actually holds** | **20** | 39 − 18 − 1 |
| **Rows inflated and on screen** | **7** | `uiautomator dump` |
| **Over-retention ratio** | **2.86 ×** | 20 / 7 |
| User profiles enumerated | **1** | `pm list users` |

The rendered list confirmed the module works: *ADF EAN-13 Sample, Calculator, Calendar, Chrome,
Clock, Contacts, DisplayLink Desktop (OEM)* — alphabetical, personal-profile-first as coded, with
**zero "Work" chips** (single profile, so the cross-profile branch at `:71` is inert on this device).

**The defect shape, precisely:** the `ListView` virtualises **views** correctly — `Views: 62`, stable
across 12 recreations. It does not virtualise **data**. All 20 `Drawable`s are resident to render 7.
This is the same principle the catalogue states for Compose (MEM-COMPOSE-001: *"lazy layouts
virtualise composables, not your data"*), and it applies identically to a `BaseAdapter`.

**Model calibration against measurement:**

| My previous derivation | Measured | Verdict |
|---|---|---|
| Non-icon scaffolding ≈ 188 B/entry → 20 × 188 = **3.7 KiB** | Total Dalvik Heap Alloc = **3,650 kB** for the *entire app* | ✅ **Confirmed negligible** — the scaffolding is ~0.1 % of the Java heap. The call to ignore it was right. |
| Icon band: **1.58 MiB** (81 KiB/icon) to **8.0 MiB** (410 KiB/icon) at N=20 | **Not isolated.** Whole-app Native Heap Alloc = 32.5 MiB (not-visible) / 48.9 MiB (visible) | ⚠️ **Neither confirmed nor falsified.** The band's ceiling is bounded above by 32.5 MiB but cannot be separated from AppCompat, the ListView and the graphics stack. |
| Projection table started at **40 apps** | **20** | ❌ **My table was 2× pessimistic for this SKU.** See §8. |

**Why the icon term could not be isolated, stated rather than papered over:** on API 26+ bitmap
pixels are native, so they do not appear in the Java heap; and `dumpsys meminfo` does not break out
bitmaps within the native heap. Isolating it needs either a heap dump analysed with a proper tool, or
a build variant that skips icon loading — and **this skill does not modify the target project**.
Direction of the model's error: the **410 KiB/icon column almost certainly overstates**, because
`AdaptiveIconDrawable` keeps its layers as drawables and defers rasterisation, and most icons on this
device (AOSP system apps, Chrome, Calculator, Clock) are vector-backed.

**Why the severity drops from HIGH to MEDIUM:** measurement showed N is 20 not 40+, there is no leak,
the Java heap is at 1.4 %, and graphics are released on backgrounding. The architectural point stands
— eager, unbounded-by-code retention that scales linearly with installed-app count — but calling it
HIGH after measuring it would be exactly the precision theatre AP-04 warns against.

**Still worth fixing, and the reason is scaling not current cost:** a device with a work profile
doubles the enumeration; a fleet SKU with a larger app catalogue raises N; and on a 1 GB WS50 the
same 2.86× over-retention costs proportionally far more headroom than it does here.

---

### 4.2 · MEM-PRESSURE-002 · MEDIUM · **Confirmed by measurement: nothing is released under pressure**

The static finding was "no `onTrimMemory` anywhere" (0 occurrences). That was tested directly:

| Trim level sent | Native Heap RSS | Native Heap Alloc |
|---|---|---|
| *(before)* | 31,776 | 32,505 |
| `RUNNING_CRITICAL` | 31,872 | 32,756 |
| `COMPLETE` | 31,872 | 32,746 |
| `BACKGROUND` | 31,864 | 32,740 |

**Total movement across all three levels: ~16 kB — noise.** Nothing was released.

**Two distinct facts fall out, and they must not be conflated:**

1. **The app implements no pressure handling**, so it releases nothing. MEM-PRESSURE-002 confirmed
   empirically, not just by grep.
2. **`RUNNING_CRITICAL` and `COMPLETE` are not delivered on this device anyway.** This is Android 15;
   those levels stopped being delivered at Android 14. So even a diligent implementation of them
   would have been dead code. **This is a live, on-device confirmation of the MEM-PRESSURE-001
   platform behaviour** — and it also confirms that the previous report was right to *clear*
   MEM-PRESSURE-001 for this codebase rather than flag it, since there are no legacy branches here to
   wrongly delete.

The fix remains: handle only `TRIM_MEMORY_UI_HIDDEN` and `TRIM_MEMORY_BACKGROUND`, with a threshold
comparison, and drop the icon cache there.

---

### 4.3 · Positive findings — measured, and worth recording

A report that only lists defects gives no signal about what is already right.

| Behaviour | Evidence |
|---|---|
| **No memory leak across activity recreation** | `RssAnon` slope −9.5 kB over 12 recreations; `Views` 62 constant; `Activities` 1 constant |
| **`ListView` view recycling works** | 62 views for 7 visible rows across 12 recreations, no growth |
| **`apps.clear()` prevents list accumulation** | Corollary of the flat slope |
| **Graphics released on backgrounding** | 44,592 → 6,772 kB (−36.9 MiB), framework-driven |
| **Accessibility service static is nulled** | `onUnbind` sets `instance = null` (`:19-21`) — the one static in the module, correctly managed |
| **Java heap barely touched** | 3.57 MiB of a 256 MB ceiling |
| **No runtime errors** | logcat clean for the process |
| **Not a 16 KB-page device** | `PAGE_SIZE 4096` → MEM-BUILD-005's compatibility half does not bite *on this SKU* |

---

### 4.4 · Observations that are not memory defects

Recorded because they were measured, and routed correctly rather than inflated into memory findings.

- **`loadApps()` runs synchronously on the main thread** in `onCreate` (`:56`), enumerating all
  profiles with `QUERY_ALL_PACKAGES` and fetching 20 badged icons. Measured cold launch:
  **624 ms**. That is a **startup-latency** matter, not a memory one, and belongs to a performance
  capability. It would grow linearly with N.
- **`oom_score_adj` = 700** when backgrounded (previous-app band). The module is a prime LMK
  candidate — but it holds ~219 MiB doing nothing, so being killed is cheap and correct. There is no
  workflow state to lose (`onSaveInstanceState`: 0 occurrences), and for this module that is
  genuinely fine rather than a MEM-STATE-001 defect: the selection is two list positions.
- **`QUERY_ALL_PACKAGES`** is declared in the manifest. A policy/privacy review item, not memory.

---

## 5. Growth axes — re-evaluated with measurement

| Axis | Static verdict (prev. report) | **Measured verdict** |
|---|---|---|
| **1 · Data** | 3.2–60.1 MiB across 40–150 apps | **N = 20 measured.** Function confirmed linear in N; the constant remains uncalibrated. Crossing point still expressed in apps, not MB. |
| **2 · Session** | Risk present | ❌ **Falsified. `L ≈ 0`.** No accumulation across 12 recreations. This module does not degrade over a shift. |
| **3 · Release** | No gate exists | Unchanged — still no CI, no baseline. **This report is now the baseline** for `:split-picker`; see §7. |
| **4 · Platform** | Gated, unknown | **Partly resolved for this SKU.** A15 → Memory Limiter absent, legacy trim levels already dead (confirmed §4.2), 4 KB pages. A17 arriving would introduce an `RssAnon` ceiling this module has never faced. |
| **5 · Constraint** | Decisive and unresolved | **Still unresolved, and still decisive.** This is a 12 GB device. The binding tier is whatever the lowest fleet SKU is, and that is still unknown. |

---

## 6. Plan for `:split-picker`, ordered by derived magnitude

| # | Rule ID | Sev | File : line | Measured / derived cost | Fix | Verification |
|---|---|---|---|---|---|---|
| 1 | MEM-BITMAP-003 | MEDIUM | `MainActivity.kt:44, 83` | **Measured 20 held / 7 rendered = 2.86×.** Icon term ≤ 32.5 MiB (not isolated) | Lazy icons in `getView` behind an `LruCache` sized from `memoryClass`; hold `ComponentName` eagerly, `Drawable` never | Re-run §3 protocol; expect Native Heap Alloc to fall and the ratio → 1.0 |
| 2 | MEM-PRESSURE-002 | MEDIUM | absent | **Measured: 0 kB released across 3 trim levels** | Implement `onTrimMemory` for `UI_HIDDEN` + `BACKGROUND` only; clear the icon cache | Re-run `am send-trim-memory BACKGROUND`; expect a measurable drop |
| 3 | MEM-PRESSURE-003 | MEDIUM | absent | No `isLowRamDevice` / `memoryClass` anywhere | Size the icon cache from the tier | Compare cache size on a 1 GB vs 8 GB device |
| 4 | MEM-OBS-004 | — | — | **Now partly closed by this report** | Commit §7 as the baseline artifact | — |
| 5 | MEM-OBS-001 | HIGH | absent | Module-wide: no `ApplicationExitInfo` | Read exit reasons at startup (API 30+; device is API 35) | `dumpsys activity exit-info` |
| 6 | MEM-BUILD-002 | HIGH | `build.gradle.kts:20` | **Not exercised — debug build** | `isMinifyEnabled = true`, `isShrinkResources = true` | Release APK diff |
| — | *startup latency* | — | `MainActivity.kt:56` | **Measured 624 ms cold** | Move `loadApps()` off the main thread | `am start -W` |

---

## 7. Baseline artifact — commit this

The first entries in the repository's memory baseline. Per measurement.md §3, one line per sample:

```
{device: TC701, ram: 12GB, os: A15/API35, build: 15-16-15.04-VG-U00-STD-ERS-04,
 unit: RssAnon,       state: visible,     scenario: cold-launch-settled, value: 85948kB}
{device: TC701, ram: 12GB, os: A15/API35, unit: RssAnon,       state: visible,     scenario: warm-steady-12-recreations, value: 118929kB}
{device: TC701, ram: 12GB, os: A15/API35, unit: NativeHeapRss, state: visible,     scenario: warm-steady, value: 44100kB}
{device: TC701, ram: 12GB, os: A15/API35, unit: NativeHeapRss, state: not-visible, scenario: home-pressed, value: 31776kB}
{device: TC701, ram: 12GB, os: A15/API35, unit: DalvikHeapAlloc, state: visible,   scenario: warm-steady, value: 3577kB}
{device: TC701, ram: 12GB, os: A15/API35, unit: Graphics,      state: visible,     scenario: warm-steady, value: 44592kB}
{device: TC701, ram: 12GB, os: A15/API35, unit: Graphics,      state: not-visible, scenario: home-pressed, value: 6772kB}
{device: TC701, ram: 12GB, os: A15/API35, unit: RssAnon-slope, state: visible,     scenario: 12x-activity-recreation, value: -9.5kB/iter}
{device: TC701, ram: 12GB, os: A15/API35, unit: Views,         state: any,         scenario: 12x-activity-recreation, value: 62-constant}
{device: TC701, ram: 12GB, os: A15/API35, unit: JavaHeapHeadroom, ceiling: 256MB-heapgrowthlimit, value: 98.6%}
{device: TC701, ram: 12GB, os: A15/API35, unit: entries-held,  scenario: 1-profile-39-launchables, value: 20}
```

**Track `RssAnon` deltas, not absolutes**, per Android's own guidance and axis 3. These absolutes are
valid only for this device.

---

## 8. Corrections to the previous report

Two claims in `MSFT-ZFINDIT-memory-assessment.md` are now falsified by measurement, and one finding
is re-graded.

1. **"The project cannot build" — wrong.** I inferred that `:skills-tester` being in
   `settings.gradle.kts` without a `build.gradle.kts` would block configuration. It does not: Gradle
   tolerates a project directory with no build file as long as nothing depends on it.
   `:split-picker:assembleDebug` configured and built cleanly. The accurate statement is that
   `:skills-tester` **contributes no artifact and its source is dead code**, not that it blocks the
   build. `:msft` was not built and remains untested.
   *Consequence:* the previous report's F9 and F10 were marked "blocked — no build possible". They
   are **not** blocked; a release build of `:msft` would quantify both.

2. **F5's projection table was 2× pessimistic for this SKU.** It started at 40 installed apps.
   The measured value is **20**. The table itself remains correct as a function of N — the crossing
   points hold — but the *entry point* was too high. Note this cuts both ways: a work profile would
   double the enumeration, and this is a GMS-loaded device whose consumer apps (Chrome, YouTube,
   Maps, Photos) a locked-down warehouse SKU would not have.

3. **F5 re-graded HIGH → MEDIUM** on measured evidence (§4.1).

**Not corrected, and worth saying:** the derived claim that the `AppEntry` scaffolding is negligible
at ~188 B/entry was **confirmed** — total Java heap for the whole app is 3.57 MiB. The static
analysis got that right, and it got the leak-free structure right by implication too.

---

## 9. Not checked

| Not checked | Why |
|---|---|
| **Isolated per-icon bitmap cost** | Needs a heap dump plus an analyser, or a code change. This skill is read-only. §4.1 states the bound and the direction of error. |
| Release-build footprint | Debug build only. MEM-BUILD-002 not exercised. |
| Behaviour with a work profile | Device has one profile. The cross-profile path at `MainActivity.kt:71-88` and `:175-186` was **never executed**. |
| The actual split-screen launch path | Requires the accessibility service to be enabled by hand; not enabled, so `launchSelected()` short-circuits at `:123`. Peak during a real dual-app launch is unmeasured. |
| Behaviour at a lower tier | This is a 12 GB device. Absolute headroom for 1–4 GB SKUs remains **UNKNOWN**. |
| `:msft` module on-device | Not requested this round. Its BLOCKERs (multi-GB Gemma residency, the frame-loop service) are untested and remain the repo's dominant risk. |
| Android 17 behaviour | Memory Limiter absent on this SKU. The module has never faced an `RssAnon` ceiling. |

---

## 10. Achieved validation level

> ### **Device** — for `:split-picker` only

| Level | Status |
|---|---|
| Inspection | ✅ Achieved (previous report) |
| **Build and behavior** | ✅ **Achieved.** Built, installed, launched; 12-iteration protocol produced a leak rate of `L ≈ 0`. |
| **Device** | ✅ **Achieved.** Ceilings and counters read from a Zebra TC701 under its real software load, with the Zebra stack, DataWedge and GMS resident. |
| Production review | ❌ Not achieved. No accountable owner has reviewed this. |

### The honest bottom line

**`:split-picker` is not a memory problem on this device.** No leak, 98.6 % Java-heap headroom,
correct view recycling, graphics released on backgrounding. Its two real findings — 2.86×
over-retention of icons, and zero response to memory pressure — are **latent and scale with N**,
not current failures.

**But the ceiling that matters was never tested.** This device has 11.32 GiB and no Memory Limiter.
The lowest fleet tier is still unknown, and it is still the term that would change the verdict most.
The leak result transfers to that tier; the headroom figure does not.

---

## 11. Device state — changes I made, and what to restore

I altered one device setting to make the rotation protocol deterministic, and **I did not record its
prior value** before changing it:

| Setting | Current | Note |
|---|---|---|
| `system accelerometer_rotation` | **`0`** (auto-rotate OFF) | **I set this.** Original value not captured — restore with `settings put system accelerometer_rotation 1` if auto-rotate was on. |
| `system user_rotation` | `0` (natural/portrait) | Left where the loop ended. |
| `com.ndzl.splitpicker` | **installed** | Remove with `adb uninstall com.ndzl.splitpicker`. |
| `/sdcard/sp.xml` | deleted | UI dump cleaned up. |

Nothing in `MSFT-ZFINDIT/` was modified except Gradle's own `build/` output directories.

---

*Generated by `appquality-memory-assessment-android` v1.0.0-beta.1 (Beta — validate before
production use). Read-only: no source file in the target project was created, modified or deleted.
To act on §6, hand off to `appquality-memory-migration-android`.*
