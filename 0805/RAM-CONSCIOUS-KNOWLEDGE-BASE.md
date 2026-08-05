# Reducing the RAM Footprint of Android Enterprise Apps on Zebra Devices

**Working notes and rule catalogue — the knowledge layer for a future `memory-conscious-lint` skill.**

Author: Claude Opus 5 · Compiled 2026-08-04 · Status: draft knowledge base, not yet a skill

---

## 0. How to read this document

This is **not** a best-practices essay. It is deliberately shaped as the **source of truth for a
linter**, because that is what it has to become. Everything is therefore organised around one
question:

> *Given only the source tree, what can an agent state with confidence, and what must it refuse to
> claim?*

That question splits every piece of memory knowledge into three tiers, and the tiering is the most
important idea in this document:

| Tier | What it is | Who checks it | Lint verdict strength |
|---|---|---|---|
| **A** | Statically decidable, **and a check already exists** | Android Lint / R8 / AGP | Deterministic. Wire it up; don't reimplement. |
| **B** | Statically decidable, **no existing check** | The skill (grep + AST + reading) | High-confidence finding. **This is where the skill earns its keep.** |
| **C** | **Not** statically decidable — needs a running device | `adb`, LeakCanary, ProfilingManager | Assert the harness and the *scaling law*; take the absolute value from measurement. |

A linter that reports a bare "your app uses 47 MB" from source alone is fabricating. But the opposite
error is just as bad, and I made it in the first draft: refusing to quantify at all leaves the skill
unable to prioritise, forecast, or prove its own value. **§8 resolves this** — the prohibition is on
*inventing* numbers, not on *deriving* them, and a source tree very often fully determines the
**scaling function** even when it cannot reveal the value. §8 is the section I would defend hardest
in review, and the one that makes the skill more than a nag.

Two things this document therefore quantifies that a conventional linter does not: **cost per record
derived from a data class declaration** (§8.4), and **five distinct "future footprint" axes** with a
different instrument for each (§8.5).

Provenance convention used throughout: **[V]** = verified verbatim from a primary source listed in
§10; **[D]** = derived by arithmetic or direct deduction from a [V] fact; **[U]** = unverified,
engineering judgement, or requires measurement on your fleet. Do not let [U] items harden into
quoted numbers.

---

## 1. Why this is suddenly urgent (and not a nice-to-have)

Two independent forces landed at the same time. Either one alone would justify the work; together
they change memory from an optimisation into a compliance requirement.

### 1.1 The hardware economics closed the escape hatch — [V]

The historical answer to memory pressure was "the next device generation has more RAM." That answer
has expired:

- DRAM contract prices rose **more than 50 % Q4-2025 → Q1-2026**, with TrendForce later revising to
  **90–95 % quarter-over-quarter**; analysts called it "unprecedented" in the industry's history.
- **DRAM, LPDDR5x and NAND all surged 90–95 % QoQ in Q1 2026.**
- Samsung, SK Hynix and Micron have shifted **93 % of combined production toward HBM** for AI data
  centres, crowding out consumer and embedded LPDDR.
- SK Hynix has warned the shortage **may persist past 2030**.

Three consequences for an enterprise Android fleet, all of which push the same direction:

1. **New device SKUs will hold or cut RAM tiers**, not raise them. A 4 GB device will not quietly
   become a 6 GB device at the same price.
2. **Refresh cycles lengthen.** Capex pressure keeps 3 GB and 4 GB devices — and 1 GB wearables —
   in production service years longer than planned. The low-RAM fleet is your *growing* fleet.
3. **"Just spec more RAM" is no longer a purchasing option** for many customers, so it cannot be
   the engineering answer either.

### 1.2 The OS started enforcing limits — [V]

Independently, Android 17 began **killing processes for memory use**:

> "Android 17 introduces app memory limits based on the device's total RAM to create a more stable
> and deterministic environment for your apps and Android users."

> "If an app exceeds those limits, Android will kill the process with no associated stack trace."

Read that second sentence twice. **No stack trace.** Your crash reporter will show nothing. The
field symptom is an enterprise app that "just closes" mid-workflow, and the only forensic trail is
`ApplicationExitInfo`:

> "if your app was affected, the exit reason will be `REASON_OTHER` and the description will contain
> the string `"MemoryLimiter:AnonSwap"` along with other information."

This is the single most important operational fact in this document, and rule **MEM-OBS-001** exists
solely because of it.

Zebra is on the path to this OS. Zebra's published Android support matrix lists devices committed to
**A16, A18 and A19** — including A19 for ET401, TC501 and TC701, and A18 for MC3400/MC3450,
MC9400/MC9450, TC53e/TC58e, WT5400/WT6400, PS30 and FR55. [V] Any device that reaches A17+
inherits the Memory Limiter. Planning as though this is a phone-only concern is wrong.

### 1.3 On Zebra specifically, low memory breaks the *business function* — [V]

This is the argument that lands with a warehouse operations owner, and it is quotable:

> "When a device is low in memory, DataWedge may not function properly. In this situation, Zebra
> recommends to investigate the cause of the low memory and take appropriate actions. For example,
> if an application is found to be causing a memory leak, it should either be uninstalled or the
> memory leak should be fixed."
> — DataWedge Programmer's Guide, *Usage Notes → Device Functionality*, item 4

So on a Zebra device the failure mode of a memory-hungry app is **not** "my app is slow." It is
**"the scanner stops working"** — device-wide, including for other apps. And note Zebra's prescribed
remedy: *uninstall the offending application.* A memory leak is, in Zebra's own documentation,
grounds for removing your app from the fleet.

---

## 2. The mental model: you are one tenant, and the budget is not yours

### 2.1 Physical RAM ≠ your budget

Every memory conversation goes wrong at the same point: someone reads "4 GB RAM" off a spec sheet
and treats it as the app's allowance. On a Zebra device the subtraction is severe: [D]

```
   total physical RAM
 −  Linux kernel, drivers, DMA/carveout reservations
 −  Android: system_server, SurfaceFlinger, zygote, ART, media, etc.
 −  The Zebra software stack — DataWedge, MX / MXMS, StageNow agent,
    OEMInfo, EMDK services, LifeGuard/OTA, Zebra Data Service
 −  The customer's EMM/MDM agent (SOTI, Ivanti, VMware/Omnissa, AirWatch…)
 −  Frequently: a VPN client, a scanning/printing middleware, a browser kiosk shell
 −  The launcher (often a locked-down enterprise launcher)
 ────
 =  what remains, shared between your app and every other line-of-business app
```

**The honest planning assumption is that you are one tenant among six or more, on a machine whose
usable free memory is a small fraction of the number on the spec sheet.** An enterprise device is
much more crowded than a consumer phone with the same RAM.

### 2.2 The Zebra RAM tiers you must actually support — [V]

Verified from Zebra spec sheets (retrieved 2026-08-04). **Re-verify per SKU and per region before
relying on any row** — Zebra ships multiple memory configurations per model and varies them by
region.

| Device | RAM configurations (verbatim where quoted) | Note |
|---|---|---|
| **WS50** | **1 GB**, "must be shared among the Linux kernel, Android app launcher, the Zebra software stack and other services" | The floor. Design here. |
| **WS501** | 3 GB | |
| **EC50 / EC55** | "3 GB/32 GB; 4 GB/64 GB" | |
| **TC21 / TC26** | "4 GB RAM/64 GB Flash memory; 3 GB RAM/32 GB Flash memory" | High-volume fleet |
| **TC15** | "4 GB RAM/64 GB Flash" | |
| **MC3300ax** | "Worldwide: 4 GB RAM/32 GB Flash Memory" | |
| **TC22 / TC27** | "6 GB RAM/64 GB UFS Flash; 8 GB RAM/128 GB UFS Flash" | |
| **TC53 / TC58** | up to 8 GB RAM / 128 GB Flash | |

**Design target: the 3 GB tier, with the 1 GB WS50 as a separate hard case.** If one binary serves
the whole fleet, the 3 GB device sets the budget — not the TC58 on the developer's desk. This is the
most common root cause of "it works in dev, it dies in the warehouse."

### 2.3 What Memory Limiter actually measures — [V]

Understanding the mechanism prevents two expensive misconceptions.

- It is a **system service using Linux cgroup v2**, integrated with ActivityManagerService.
- It sets **`memory.high`** (a *soft* limit → throttling and reclaim, not an instant kill) and
  **`memory.swap.max`**.
- Counted memory includes **both file-backed and anonymous memory**. The kernel first evicts clean
  pages, then swaps anonymous pages.
- On breach: reclaim and swap pressure → **performance degradation first**; sustained allocation
  past swap capacity → allocation failure and likely death.
- Limits are **per process-visibility state**:

  | State group | oom_adj-ish members | Limit |
  |---|---|---|
  | **Visible** | TOP, BOUND_TOP, IMPORTANT_FOREGROUND, TOP_SLEEPING | "a more generous memory limit" |
  | **Not visible** | FOREGROUND_SERVICE, BOUND_FOREGROUND_SERVICE, IMPORTANT_BACKGROUND, TRANSIENT_BACKGROUND, BACKUP, SERVICE, RECEIVER, HEAVY_WEIGHT, HOME, LAST_ACTIVITY | "a more restrictive limit" |
  | **Cached** | CACHED_ACTIVITY, CACHED_ACTIVITY_CLIENT, CACHED_RECENT, CACHED_EMPTY | frozen, then "maximally reclaimed" |
  | **Unrestricted** | PERSISTENT, PERSISTENT_UI | exempt |

- **System processes (UID < 10000) are generally exempt. There is no per-app allowlist.** Your
  enterprise app cannot be exempted by policy.
- Config lives at **`/vendor/etc/memory-limiter-config.xml`**, values in **MiB**. The published
  example, for a device with `minimumRequiredMemTotal` 14336:
  `memVisible 8192`, `memNotVisible 4096`, `swapVisible 4096`, `swapNotVisible 4096`.
- **"If the configuration file is missing, unreadable, or invalid, the Memory Limiter is disabled."**

Two consequences that matter enormously for Zebra and that I have seen nobody state:

1. **The limits are the OEM's to define.** That file is in `/vendor` — on a Zebra device, the
   thresholds are **Zebra's** values, not Google's. The published 14 GB example tells you nothing
   about a 3 GB TC26. **You must read the real limit off the device** (§8.7). Any lint rule quoting
   a hard MB threshold is fabricating it.
2. **A foreground service moves you into the "not visible" bucket** — the *more restrictive* limit.
   An app that runs a long sync as a foreground service while the UI is hidden is asking for the
   tighter budget at precisely its heaviest moment. See **MEM-PROC-003**.

### 2.4 Two ambient facts that change the arithmetic

- **16 KB page size — this is a compatibility item, not a memory-saving one. Do not confuse the
  two.** [V] The two facts below are about *different actors*, and collapsing them into one
  recommendation is a mistake:

  1. **The device chooses the page size; you don't.** "Beginning with Android 15, AOSP supports
     devices that are configured to use a page size of 16 KB (16 KB devices)," and "many of these
     devices will adopt 16 KB (and eventually greater) page sizes to optimize the device's
     performance." The marginal extra memory use is therefore **spent by the kernel on that device
     whether or not your app does anything.** It is not a cost you opt into and not a cost you can
     avoid. The device-level trade is deliberate: coarser allocation granularity in exchange for a
     faster reclaim path, measured at **"3.16 % lower [app launch times] on average, with more
     significant improvements (up to 30 %) for some apps"** while under memory pressure — i.e. the
     OEM is buying speed *exactly when memory is tight*, which is the case you care about.
  2. **Your only decision is whether your app still runs there.** If your native libraries are not
     16 KB aligned: "Without recompiling, apps won't work on 16 KB devices in future Android
     releases." There is a fallback — 16 KB backcompat mode shows a first-launch warning and
     "allows *some* apps to work" — but it is a reprieve, not a plan.

  So the advice is **not** "adopt 16 KB pages to reduce memory." It is: *the device will do this to
  you, so be compatible.* Align your libraries (AGP **8.5.1+** does it by default for uncompressed
  shared libraries) and **never hardcode 4096** — query `getpagesize()` or `sysconf(_SC_PAGESIZE)`.
  Also note Google Play's mandate, which may or may not bind an MDM-deployed enterprise app but
  binds anything you ship through the store: "Starting November 1st, 2025, all new apps and updates
  to existing apps submitted to Google Play and targeting Android 15+ devices must support 16 KB
  page sizes on 64-bit devices."

  The one *genuine* memory angle here is a second-order effect, and it is covered separately in
  §2.5 — it concerns `extractNativeLibs`, not page size as such.

### 2.5 The principle that actually unifies several rules: anonymous vs. clean file-backed memory

This is the most useful single idea I took from the Memory Limiter mechanics, and it explains why
several otherwise-unrelated rules in §5 point the same way. [D — reasoning derived from the [V]
mechanics in §2.3; the inference is mine, the mechanics are quoted.]

Recall two quoted facts from §2.3 and §1.2:

- Counted memory "includes both file-backed memory and anonymous memory," and on approaching the
  limit **"the kernel attempt[s] to evict clean pages and swap unused anonymous pages."**
- The kill signature is literally **`MemoryLimiter:AnonSwap`**.

Read together: **not all bytes are equally dangerous.**

| Kind of memory | What the kernel can do under pressure | Cost to you |
|---|---|---|
| **Clean, file-backed** (mmap'd APK segments, SQLite pages, resources) | **Drop it.** Re-read from flash if needed. | Cheap — a possible re-read |
| **Anonymous** (Java heap, your `HashMap`, decoded bitmaps) | Must **swap** it — or fail | Expensive, and it is what the `AnonSwap` kill is named after |

**The strategic move is therefore to shift bytes out of anonymous memory and into clean file-backed
memory wherever the workload allows.** That single sentence is the reason behind rules that look
unconnected:

- **MEM-CACHE-001's fix — "let the database be your cache"** — is not merely about bounding size.
  A 40 MB `HashMap` is 40 MB of *anonymous* memory that must be swapped. The same data in SQLite is
  file-backed pages the kernel can drop for free. You are converting expensive bytes into cheap
  ones, which is why this refactor usually beats "just make the map smaller."
- **MEM-BUILD-005's `extractNativeLibs=false`** keeps `.so` segments mapped from the APK as clean,
  file-backed, **shareable-across-processes** pages, instead of extracted copies. This is the real
  memory reason to prefer it, and it is why the "slight increase in binary size" from 16 KB
  alignment is an acceptable trade — Google notes "optimizations to the package manager in Android 15
  negate the runtime costs from this increase." [V]
- **MEM-DATA-001's streaming parse** avoids materialising the response `String` and its object graph
  — both anonymous — in favour of streaming through to file-backed database pages.

Caveat to keep this honest: file-backed memory still counts toward the limit, so this is not a
loophole and you cannot mmap your way out of a genuinely oversized working set. It changes *what the
kernel does when it needs the memory back* — which is precisely the difference between a slowdown
and a kill.
- **`onTrimMemory` is mostly dead.** — [V] This one invalidates a large fraction of the memory code
  in the wild, including well-regarded internal guidance:

  > "Beginning with Android 14, the system no longer delivers notifications for the other, legacy
  > constants. Those constants were formally deprecated in Android 15."

  **Only `TRIM_MEMORY_UI_HIDDEN` and `TRIM_MEMORY_BACKGROUND` are still delivered.** Every
  `when (level)` block branching on `TRIM_MEMORY_RUNNING_LOW`, `TRIM_MEMORY_RUNNING_CRITICAL`,
  `TRIM_MEMORY_MODERATE` or `TRIM_MEMORY_COMPLETE` is **dead code that will never execute** on a
  modern device. Rule **MEM-PRESSURE-001**. I flag this as the highest-yield single rule in the
  catalogue, because the code *looks* diligent and is silently inert.

---

## 3. Where the memory actually goes

Ranked by how often it is the answer, in enterprise line-of-business apps specifically. Optimising
in a different order wastes effort.

1. **Leaked Activities / Fragments / Contexts.** One leaked Activity retains its whole view tree,
   its bitmaps and often its adapter's dataset. On Zebra the classic vector is a **DataWedge scan
   `BroadcastReceiver` registered in `onCreate` and never unregistered** — it holds the Activity for
   the life of the process, and the process is long-lived because it is a shift-long app.
2. **Unbounded in-memory collections.** The single most common enterprise pattern: load the entire
   product / location / order master into a `HashMap` "for speed." 200 000 rows × a fat data class
   is tens of MB, permanently resident, mostly unread.
3. **Bitmaps decoded at source resolution.** Signature capture, proof-of-delivery photos, damage
   photos, item images. See §3.1.
4. **Whole-response JSON.** `response.body.string()` → `Gson.fromJson` → `List<Dto>` → mapped to
   `List<Entity>`. Peak holds the string, the parse tree, and both object graphs simultaneously.
5. **Unbounded image-library caches.** Coil and Glide default to a *percentage of available heap* —
   sized for phones, and they will happily take a share you cannot spare.
6. **Extra processes.** Each `android:process` is a separate ART runtime, class loading and heap.
7. **Code footprint.** Dex, class metadata and JIT/AOT artefacts are real resident memory. R8 is a
   *memory* optimisation, not only a size one — Google states plainly that "a larger compiled
   codebase directly increases the physical RAM your app requires." [V]

### 3.1 Bitmap arithmetic — the numbers to keep on the wall — [D]

`bytes = width × height × bytesPerPixel`, where ARGB_8888 = 4 and RGB_565 = 2.

| Source | Config | Bytes |
|---|---|---|
| 12 MP photo, 4000 × 3000 | ARGB_8888 | **48.0 MB** |
| 12 MP photo, 4000 × 3000 | RGB_565 | 24.0 MB |
| 8 MP photo, 3264 × 2448 | ARGB_8888 | **~32.0 MB** |
| 1080 × 1920 full-screen | ARGB_8888 | ~8.3 MB |
| 1080 × 1920 full-screen | RGB_565 | ~4.1 MB |
| Same 12 MP photo downsampled to fit 1080 px wide (1080 × 810) | ARGB_8888 | **~3.5 MB** |
| 96 × 96 list thumbnail | ARGB_8888 | ~36 KB |

**A single unscaled 12 MP decode is 48 MB.** On a 3 GB device sharing memory with the Zebra stack,
that one line of code can be the difference between a working app and a `MemoryLimiter:AnonSwap`
kill. Downsampling the same image to display size costs **~3.5 MB — a 93 % reduction** for zero
perceptible loss, because the screen cannot show 12 MP anyway.

Google's five stated image practices, verbatim in substance: [V] **downsample** (`inSampleSize`,
`DownsampleStrategy`, Coil's `ImageLoader`); **don't bake letterbox padding into image files** (use
`InsetDrawable` or view padding); **use `RGB_565` when transparency isn't needed — "half the memory
of the default `ARGB_8888`"**; **prefer vector drawables and `ShapeDrawable`**; **reuse** — call
`bitmap.recycle()` and drop the reference, or return the bitmap to its library-managed pool.

---

## 4. Tier A — already machine-checkable. Wire it up first.

The skill's **first action** on any project should be to verify these are switched on, because they
cost nothing and catch real defects deterministically. Reimplementing them in an agent would be
slower, less precise, and unmaintainable.

### 4.1 Build configuration — [V]

Google's Android 17 memory guidance is unusually prescriptive here:

- `isMinifyEnabled = true`
- `isShrinkResources = true`
- Use **`proguard-android-optimize.txt`**, *not* the legacy `proguard-android.txt`
- **Remove `android.enableR8.fullMode = false`** from `gradle.properties`
- Avoid the global escapes `-dontoptimize`, `-dontshrink`, `-dontobfuscate`
- Avoid package-wide keep wildcards such as `-keep class com.example.package.** { *; }` —
  "Poorly written keep rules prevent R8 from optimizing large portions of your codebase."

The cited outcome, which is the number to put in front of a sceptical stakeholder: Monzo enabled
full R8 and saw **"a 35 % reduction in their ANR rate, a 30 % improvement in cold start rate, and a
9 % reduction in overall app size."** [V]

Note for enterprise reality: keep rules are usually bloated because of reflection in Zebra/EMM
SDK integrations and JSON DTOs. The fix is **narrow, class-specific keep rules**, not a package
wildcard. This is where most enterprise apps silently lose R8.

### 4.2 Existing Android Lint checks worth promoting to `error` — [V]

All IDs verified present in the official lint issue index:

| Lint ID | Catches |
|---|---|
| `StaticFieldLeak` | Android context classes in static fields |
| `HandlerLeak` | Inner-class `Handler` preventing outer-class GC |
| `Recycle` | Missing `recycle()` on `TypedArray`, `VelocityTracker`, etc. |
| `DrawAllocation` | Allocations inside draw/layout — GC churn during scroll |
| `UseSparseArrays` | `HashMap` with Integer keys → `SparseArray` |
| `UnusedResources` | Dead resources inflating APK and resource tables |
| `WifiManagerLeak`, `WifiManagerPotentialLeak` | Classic `WifiManager` context leak |
| `CastingViewContextToActivity` | Unsafe `Context`→`Activity` cast, a leak smell |
| `CommitPrefEdits`, `CommitTransaction` | Unclosed editors/transactions |
| `AutoDispose` | Undisposed Rx `Disposable` |
| `BrokenIterator` | Iterator misuse |
| **Compose:** `AutoboxingStateCreation` | `State<T>` autoboxing primitives |
| **Compose:** `AutoboxingStateValueProperty` | Autoboxing on state read |
| **Compose:** `UnrememberedMutableState` | State created in composition without `remember` |
| **Compose:** `MutableCollectionMutableState` | `MutableState` holding a *mutable* collection |
| **Compose:** `ComposeRememberMissing`, `RememberInComposition` | Missing `remember` |
| **Compose:** `RetainLeaksContext` | `retain { }` storing a `Context` → leak |

Recommended posture: **fail the build** on the leak-family checks. A leak on a shift-long enterprise
app is not a warning.

Also: Android Studio lint now flags native libraries that are **not 16 KB aligned**. [V]

### 4.3 The trap in Tier A

Tier A is necessary and radically insufficient. Not one of these checks can detect the two most
expensive defects in real enterprise apps: **an unbounded master-data cache** and **a whole-response
JSON parse**. Both are idiomatic, warning-free code. That is precisely the gap the skill fills.

---

## 5. Tier B — the rule catalogue (the skill's actual payload)

Format per rule, chosen so a rule is *executable by an agent* rather than merely readable:

- **ID · Severity**
- **Signature** — what to search for. Concrete enough to grep; refined by reading.
- **Why** — the memory consequence, quantified where honest.
- **Fix** — the replacement.
- **False positives** — when *not* to report. Non-negotiable; see §9.

Severity ladder, tied to consequence rather than taste:

| Severity | Meaning |
|---|---|
| **BLOCKER** | Can cause an untraceable `MemoryLimiter` kill or OOM in normal field use |
| **HIGH** | Unbounded or per-record growth; fails on the largest customer, passes in dev |
| **MEDIUM** | Fixed avoidable overhead, or dead/ineffective memory code |
| **LOW** | Hygiene; matters in aggregate or in hot paths only |

---

### 5.1 Family `MEM-BUILD` — build & manifest

**MEM-BUILD-001 · BLOCKER — `android:largeHeap="true"`**
- **Signature:** `android:largeHeap` in any `AndroidManifest.xml`.
- **Why:** Almost always added to silence an OOM rather than fix it. It lets the process grow until
  it destabilises a device shared with the Zebra stack, and under Memory Limiter a bigger heap
  reaches the cgroup limit *sooner* — converting a reproducible `OutOfMemoryError` (with a stack
  trace) into an **untraceable** `MemoryLimiter:AnonSwap` kill. It makes the problem *harder to
  diagnose*, which is the opposite of what the author intended.
- **Fix:** Remove it; fix the allocation (§5.2, §5.4). Defensible only for a genuine, bounded,
  unavoidable large working set, and only with measurement attached.
- **False positives:** none. Always report; accept a documented, measured waiver.

**MEM-BUILD-002 · HIGH — R8 not fully enabled**
- **Signature:** release build type with `isMinifyEnabled = false`; or missing
  `isShrinkResources = true`; or `android.enableR8.fullMode = false` in `gradle.properties`; or
  `proguard-android.txt` instead of `proguard-android-optimize.txt`.
- **Why:** Code footprint is resident RAM, not just APK bytes. [V]
- **Fix:** §4.1.
- **False positives:** debug/internal variants. Only assert on release-shaped build types.

**MEM-BUILD-003 · MEDIUM — over-broad keep rules**
- **Signature:** `-keep class …** { *; }`, `-dontoptimize`, `-dontshrink`, `-dontobfuscate`.
- **Why:** Disables R8 across large regions. [V]
- **Fix:** Narrow to the specific reflected classes/members.
- **False positives:** a genuinely reflection-driven SDK boundary may need a broad rule — require a
  comment naming the SDK, and report as *needs justification* rather than *defect*.

**MEM-BUILD-004 · LOW — multiple density buckets / oversized raster assets**
- **Signature:** `drawable-ldpi|mdpi|hdpi|xxhdpi|xxxhdpi` present for a fixed-density fleet;
  PNGs larger than the target screen; a 1080p+ splash asset.
- **Why:** Wasted flash, wasted resource-table entries, and tempting oversized decodes.
- **Fix:** Ship only the buckets your fleet needs; vectors for icons; WebP for photos.
- **False positives:** a genuinely multi-density fleet (WS50 + TC58 + ET40 tablets). Confirm the
  target device list before reporting.

**MEM-BUILD-005 · MEDIUM — `extractNativeLibs="true"` / unaligned native libs**

*Two distinct concerns share one signature. Report them as two findings, because the severities and
the arguments differ — see §2.4 and §2.5.*

- **Signature:** `android:extractNativeLibs="true"` in the manifest; AGP below **8.5.1**; hardcoded
  `4096` or `PAGE_SIZE` assumptions in native/JNI code.
- **Why (a) — compatibility, the stronger reason:** on a 16 KB-page device, unaligned native
  libraries mean "apps won't work on 16 KB devices in future Android releases," with 16 KB backcompat
  mode as a warning-bearing reprieve that "allows some apps to work." [V] **This is a correctness
  risk, not a memory saving** — the device's page size is not your choice (§2.4).
- **Why (b) — memory, the second-order reason:** `extractNativeLibs=false` keeps `.so` segments
  mapped from the APK as **clean, file-backed, cross-process-shareable** pages rather than extracted
  private copies. Under Memory Limiter, clean file-backed pages are the kind the kernel can simply
  *drop*; anonymous pages are the kind it must swap, and are what the `MemoryLimiter:AnonSwap` kill
  is named for (§2.5). [D]
- **Fix:** AGP 8.5.1+ (aligns uncompressed shared libraries by default); `extractNativeLibs=false`;
  replace page-size constants with `getpagesize()` / `sysconf(_SC_PAGESIZE)`.
- **False positives:** **pure-JVM apps with no `.so` at all — skip this rule entirely, including the
  transitive-dependency case where the only native code is inside a third-party AAR you cannot
  recompile.** In that situation the finding is real but not actionable by you: report it as *raise
  with the vendor*, not as a defect in this codebase. Zebra/EMM SDKs are the likely instance.
- **Version scope:** relevant from Android 15 onward; Play mandate from 2025-11-01 for new/updated
  apps targeting Android 15+ on 64-bit (may not bind MDM-sideloaded enterprise apps — verify your
  distribution route before citing it as a requirement).

---

### 5.2 Family `MEM-BITMAP`

**MEM-BITMAP-001 · BLOCKER — decode without downsampling**
- **Signature:** `BitmapFactory.decodeFile|decodeStream|decodeByteArray|decodeResource` where the
  enclosing function contains **no** `inSampleSize`, no `inJustDecodeBounds` bounds pass, and no
  `setTargetSampleSize`.
- **Why:** Up to **48 MB for one 12 MP image** (§3.1). This is the highest-magnitude single defect
  reachable from source.
- **Fix:** Two-pass bounds-then-sample decode, or `ImageDecoder` with `setTargetSampleSize` and
  `MEMORY_POLICY_LOW_RAM`; or hand it to Coil/Glide with an explicit target size.
- **False positives:** decoding a known-small bundled asset (an icon, a fixed 64 × 64 resource).
  Check the source's provenance; camera/gallery/file/network input is never small.

**MEM-BITMAP-002 · HIGH — `ARGB_8888` for opaque content**
- **Signature:** explicit `ARGB_8888`, or *no* `inPreferredConfig` / `bitmapConfig` on a
  photographic path (camera, signature, POD photo).
- **Why:** Exactly **2× the memory** of `RGB_565`. [V]
- **Fix:** `RGB_565` where there is no alpha; Coil `bitmapConfig`, Glide `DecodeFormat`.
- **False positives:** anything needing transparency — icons, overlays, masks, PNG logos, and
  gradient-heavy imagery where banding would be visible.

**MEM-BITMAP-003 · MEDIUM — bitmap retained past its consumer**
- **Signature:** `Bitmap` in a field, `companion object`, `object`, or a `static`; a bitmap put in a
  collection with no removal; no `recycle()` on a manually created bitmap.
- **Why:** Directly retains the largest objects the app owns.
- **Fix:** View-scoped ownership; `recycle()` + drop the reference; or a library-managed pool. [V]
- **False positives:** a deliberately cached, *small*, *bounded* set (a signature pad's current
  stroke buffer). Judge by bound and size, not by the presence of a field.

**MEM-BITMAP-004 · MEDIUM — full-resolution camera capture with no resize step**
- **Signature:** `CameraX ImageCapture` / `ACTION_IMAGE_CAPTURE` / `takePicture` with no
  `setTargetResolution`, no downscale, and the result read straight into a bitmap or uploaded raw.
- **Why:** Enterprise POD/damage photos rarely need 12 MP; the decode *and* the upload buffer *and*
  often a Base64 copy all scale with it.
- **Fix:** Capture or immediately resize to the resolution the business actually needs; stream the
  file to the network rather than materialising bytes.
- **False positives:** a genuine requirement for archival-resolution evidence (some insurance and
  compliance flows). Then require a **streaming** path, not an in-memory one.

---

### 5.3 Family `MEM-CACHE`

**MEM-CACHE-001 · BLOCKER — unbounded in-memory cache**
- **Signature:** a long-lived `HashMap`/`MutableMap`/`ArrayList`/`MutableList`/`mutableStateListOf`
  in an `object`, `companion object`, singleton, `@Singleton`, or `Application` subclass, that is
  **written in a loop or from a network/DB result** and has **no eviction** — no `LruCache`, no size
  cap, no `clear()` on any lifecycle or pressure signal.
- **Why:** The defining enterprise memory defect. Master data — items, locations, customers, price
  lists — grows with the *customer's* data, not the developer's. It passes every test on a 500-row
  fixture and dies at the customer with 200 000 SKUs. Permanently resident, so it counts against the
  Memory Limiter in **every** process state, including the restrictive "not visible" one.
- **Fix:** `LruCache` with an explicitly computed size; or don't cache — query Room/SQLite, which
  already has a tuned page cache backed by mmap'd file pages the kernel can reclaim. **Let the
  database be your cache.** This is usually a net *simplification*.
- **False positives:** genuinely bounded, enumerable domain data (a 12-entry status lookup, a
  50-entry country list). The test is **"is the upper bound set by our code or by the customer's
  data?"** Only the latter is a defect.

**MEM-CACHE-002 · HIGH — image-library cache left at default size**
- **Signature:** Coil `ImageLoader` / Glide `AppGlideModule` with no `memoryCache` /
  `setMemoryCache` size; or no custom `ImageLoader` at all while loading remote images.
- **Why:** Defaults are a **percentage of available heap**, calibrated for consumer phones. On a
  shared 3 GB Zebra device that percentage is memory you needed for the workflow.
- **Fix:** Set `maxSizeBytes` explicitly, tiered on `isLowRamDevice` / `memoryClass`; cap the disk
  cache too (flash is 32 GB on several tiers).
- **False positives:** an app that loads no remote/large images.

**MEM-CACHE-003 · MEDIUM — cache never cleared under pressure**
- **Signature:** a cache exists but no `onTrimMemory` path (or a *deprecated-only* one — cross-check
  MEM-PRESSURE-001) clears it.
- **Why:** Free, one-time win that measurably lowers your kill probability.
- **Fix:** Clear on `TRIM_MEMORY_UI_HIDDEN` / `TRIM_MEMORY_BACKGROUND`.
- **False positives:** caches whose rebuild cost exceeds the benefit *and* whose size is trivially
  bounded.

---

### 5.4 Family `MEM-DATA`

**MEM-DATA-001 · BLOCKER — whole-response deserialisation**
- **Signature:** `response.body()?.string()` / `bodyAsText()` / `readText()` / `readBytes()` followed
  by `Gson().fromJson(...)`, `Json.decodeFromString(...)`, `moshi.adapter(...).fromJson(String)`;
  or a `ByteArrayOutputStream` accumulating a whole download.
- **Why:** Peak resident = the raw `String` (**2 bytes/char** in a Java `String`) **+** the parser's
  intermediate tree **+** the DTO graph **+** frequently a second mapped entity graph — all live
  simultaneously. A 5 MB payload routinely peaks past 30 MB. [U — magnitude varies by shape; the
  *multiplicity* is structural and certain.]
- **Fix:** Stream. `JsonReader` / Moshi's `fromJson(BufferedSource)` / kotlinx
  `decodeFromStream`, writing to Room in batched transactions as you parse. Peak becomes O(batch),
  not O(response). This is *the* highest-leverage refactor in most enterprise sync code.
- **False positives:** small, bounded responses — a login, a config blob, a single record. Gate on
  whether the payload scales with customer data.

**MEM-DATA-002 · HIGH — unpaged full-table query**
- **Signature:** Room `@Query` returning `List<T>` / `Flow<List<T>>` with no `LIMIT`, no
  `PagingSource`; `cursor.moveToNext()` accumulating into a list; `SELECT *` on a wide table.
- **Why:** Materialises every row *and* every column as objects. A 5 000-row list backing a screen
  that shows eight rows is 5 000 objects with no reader.
- **Fix:** Paging 3, or a bounded query; project only the columns needed into a narrow DTO.
- **False positives:** a query with a genuine small bound (a settings table, a `LIMIT 20` search).

**MEM-DATA-003 · MEDIUM — `SELECT *` / over-wide projection**
- **Signature:** `SELECT *`; a DTO with many unread fields; `@Embedded` pulling unused columns.
- **Why:** Every column becomes a field on every row, including the BLOB you never read.
- **Fix:** Explicit column lists into purpose-built projections.
- **False positives:** the entity genuinely needs all columns (a detail screen).

**MEM-DATA-004 · MEDIUM — unclosed `Cursor` / `Closeable`**
- **Signature:** `rawQuery` / `query` / `openInputStream` without `use { }`, try-with-resources, or a
  `finally` close.
- **Why:** Cursor windows are real off-heap buffers; leaked ones accumulate.
- **Fix:** `use { }` everywhere.
- **False positives:** cursors deliberately owned by a `CursorAdapter`/`LoaderManager` that closes
  them. Verify the owner exists before reporting.

**MEM-DATA-005 · LOW — boxing and copying in hot paths**
- **Signature:** `HashMap<Integer, …>` (also caught by Tier A `UseSparseArrays`), `toList()` /
  `map {}` chains inside loops over large datasets, `String` concatenation in a loop.
- **Why:** Allocation churn → GC pressure → jank; matters at scale, not in one-off code.
- **Fix:** `SparseArray` / `LongSparseArray`; sequences; `StringBuilder`.
- **False positives:** small collections and cold code. **Do not report this on a 10-element list** —
  this rule is the easiest way to make the linter feel like noise.

**MEM-DATA-006 · LOW — verbose generated code**
- **Signature:** full (non-lite) protobuf on the client.
- **Why:** "Regular protobufs generate extremely verbose code, which increases your app's code
  footprint in RAM." [V]
- **Fix:** protobuf-lite (`javalite`).
- **False positives:** shared server modules that genuinely need full protobuf — but not on-device.

---

### 5.5 Family `MEM-LIFECYCLE` — leaks

**MEM-LIFECYCLE-001 · BLOCKER — receiver/listener registered without a matching unregister**
- **Signature:** `registerReceiver` / `addListener` / `addObserver` / `setCallback` / `subscribe`
  with no symmetric `unregisterReceiver` / `remove*` in the mirror lifecycle callback. **Weight
  DataWedge scan receivers, EMDK/scanner listeners and MX/OEMInfo callbacks highest.**
- **Why:** The signature Zebra leak. A receiver registered in `onCreate` and never unregistered
  holds the Activity — and its view tree, adapter and bitmaps — for the life of a process that lives
  a whole shift. Recall Zebra's stated remedy for a leaking app: **uninstall it** (§1.3).
- **Fix:** Register in `onStart`, unregister in `onStop` (or `onResume`/`onPause`); or use a
  `DefaultLifecycleObserver`; in Compose, `DisposableEffect` with cleanup in `onDispose`. [V]
- **False positives:** registration genuinely scoped to the application lifetime with
  `applicationContext` and no Activity capture — verify which `Context` is captured.

**MEM-LIFECYCLE-002 · HIGH — Fragment `ViewBinding` not nulled**
- **Signature:** a `_binding` field with no `_binding = null` in `onDestroyView`.
- **Why:** Retains the entire inflated view hierarchy after the view is destroyed. [V]
- **Fix:** Null it in `onDestroyView`.
- **False positives:** binding held by a delegate that already handles this
  (`viewBinding {}`-style property delegates). Check for the delegate before reporting.

**MEM-LIFECYCLE-003 · BLOCKER — `Context`/`Activity`/`View` reachable from a static**
- **Signature:** `companion object` / `object` / `static` field typed `Context`, `Activity`,
  `View`, `Fragment`; a `Context` passed into a `ViewModel` constructor;
  `LocalContext.current` handed to a ViewModel.
- **Why:** Permanent retention. Largely covered by Tier A `StaticFieldLeak`, but the **ViewModel**
  and **Compose** variants are exactly the ones lint misses, and Google calls both out explicitly.
  [V]
- **Fix:** `applicationContext` where a Context is truly needed; otherwise DI, or expose UI state
  through a Kotlin flow instead of passing a Context inward. [V]
- **False positives:** `Application` itself held statically is conventional and safe.

**MEM-LIFECYCLE-004 · HIGH — unscoped coroutine / unmanaged thread**
- **Signature:** `GlobalScope.launch`, `CoroutineScope(...)` created in a UI class with no
  `cancel()`, bare `Thread {}.start()`, `Executors.new*` as a field with no `shutdown()`,
  `Timer`/`TimerTask`.
- **Why:** The captured receiver — usually the Activity or Fragment — is retained until the work
  ends, which may be never.
- **Fix:** `viewModelScope`, `lifecycleScope`, `repeatOnLifecycle`; `WorkManager` for deferrable
  work.
- **False positives:** a deliberately application-scoped supervisor in a DI graph, owned by the
  `Application`.

**MEM-LIFECYCLE-005 · MEDIUM — non-static inner class with implicit outer reference**
- **Signature:** inner `Handler`, `AsyncTask`, `Runnable`, `BroadcastReceiver`, or callback declared
  as a Java non-static inner class or a Kotlin `inner class`, retained beyond the outer scope.
- **Why:** Implicit `this$0` retains the outer instance. Partly Tier A (`HandlerLeak`).
- **Fix:** `static` / top-level class + `WeakReference`, or a lifecycle-aware component.
- **False positives:** short-lived inner classes that never outlive their outer instance.

---

### 5.6 Family `MEM-PROC` — processes and services

**MEM-PROC-001 · HIGH — long-running / always-on `Service`**
- **Signature:** `Service` returning `START_STICKY`, restarted from `BOOT_COMPLETED`, or with no
  `stopSelf`; a foreground service kept alive across the whole shift for periodic work.
- **Why:** Google's wording is unusually blunt: **"Leaving unnecessary services running is one of
  the worst memory-management mistakes an Android app can make."** [V] It also keeps the *whole
  process* — with all its caches — resident and countable.
- **Fix:** `WorkManager`. [V] Keep a foreground service only for genuinely continuous,
  user-visible work.
- **False positives:** legitimately continuous work — an active picking session, a persistent
  scanner or BLE connection. Then MEM-PROC-003 applies instead.

**MEM-PROC-002 · MEDIUM — extra processes**
- **Signature:** `android:process=":something"` on any component.
- **Why:** Each process is a separate ART runtime with its own class loading, JIT/AOT state and heap
  — a fixed overhead per process, paid permanently. [U — magnitude must be measured with
  `dumpsys meminfo`; the *existence* of the overhead is certain.] Under Memory Limiter each process
  is *also* limited separately, which is occasionally a deliberate design, so this is a question,
  not a verdict.
- **Fix:** Consolidate unless there is a stated reason (crash isolation, a `WebView` sandbox, a
  32-bit native dependency).
- **False positives:** the deliberate cases above. Report as *justify this*, severity MEDIUM.

**MEM-PROC-003 · HIGH — heavy work while in the "not visible" state**
- **Signature:** a bulk sync, full download, or large in-memory transform started from a foreground
  service, `WorkManager` worker, or `TRIM_MEMORY_UI_HIDDEN` path — i.e. while the UI is hidden.
- **Why:** **A novel and under-appreciated consequence of §2.3.** Foreground services and workers
  sit in the **"not visible"** bucket, which gets *"a more restrictive limit."* The app therefore
  receives its **tightest** budget at the exact moment it does its **heaviest** allocation. An
  overnight sync that passes interactively can be killed — untraceably — when backgrounded.
- **Fix:** Stream and batch (MEM-DATA-001); keep worker peak allocation flat and small; measure the
  *not-visible* limit specifically via `am memory-limiter status` (§8.7) and test under
  `am memory-limiter manual <pid> <limit>`.
- **False positives:** genuinely small background tasks.

---

### 5.7 Family `MEM-PRESSURE` — pressure response and device tiering

**MEM-PRESSURE-001 · MEDIUM — `onTrimMemory` branching on deprecated constants**
- **Signature:** `TRIM_MEMORY_RUNNING_LOW`, `TRIM_MEMORY_RUNNING_CRITICAL`,
  `TRIM_MEMORY_RUNNING_MODERATE`, `TRIM_MEMORY_MODERATE`, `TRIM_MEMORY_COMPLETE`; or an
  `onLowMemory()` override as the only pressure handling.
- **Why:** **Dead code.** Not delivered since Android 14, deprecated in Android 15. [V] The app
  *looks* well-behaved and releases nothing. High-yield precisely because it reads as diligence —
  reviewers skip it.
- **Fix:** Handle only `TRIM_MEMORY_UI_HIDDEN` and `TRIM_MEMORY_BACKGROUND`. Google's own shape is a
  threshold comparison, not a `when` over equality:
  ```kotlin
  override fun onTrimMemory(level: Int) {
      if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) { /* release UI memory, bitmap caches */ }
      if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) { /* release background memory */ }
  }
  ```
- **False positives:** an app with `minSdk` below 34 that must still support Android 13 devices —
  then the branches are live on *those* devices. Check `minSdk` and the actual fleet OS spread
  before reporting. On a Zebra fleet this is a real consideration: plenty of devices ship A11–A15.

**MEM-PRESSURE-002 · MEDIUM — no pressure handling at all**
- **Signature:** caches or bitmaps present, no `ComponentCallbacks2` implementation anywhere.
- **Why:** Free reduction in kill probability; you release before the system takes the decision from
  you.
- **Fix:** Implement it in `Application` and clear the caches you actually have.
- **False positives:** an app with genuinely nothing releasable.

**MEM-PRESSURE-003 · MEDIUM — no device tiering**
- **Signature:** no reference anywhere to `isLowRamDevice`, `memoryClass`, or `largeMemoryClass`,
  in an app that targets both a 1 GB WS50 and a 6–8 GB TC27.
- **Why:** One binary across a 1 GB → 8 GB fleet with one fixed set of cache sizes is either
  wasteful at the top or fatal at the bottom. Tier from what the device *reports*, never from a
  model string — model strings are unreliable and multiply per region.
- **Fix:**
  ```kotlin
  val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
  val constrained = am.isLowRamDevice || am.memoryClass <= 128
  // drive cache sizes, bitmap config, prefetch depth, page size from this
  ```
- **False positives:** a single-SKU deployment. Confirm the target device list first.

---

### 5.8 Family `MEM-STATE` — surviving the kill

**MEM-STATE-001 · HIGH — workflow state held only in memory**
- **Signature:** multi-step workflow state (picked quantities, scanned lines, a part-completed form)
  in plain `ViewModel` fields or a singleton, with no `SavedStateHandle` and no DB/DataStore write
  per step.
- **Why:** **This rule is the philosophical core of the catalogue.** You cannot guarantee you will
  never be killed — Memory Limiter, the LMK, and a hardware Home button all say otherwise. So
  correctness must not depend on survival. *Assume every backgrounding is a process death; then it
  is never a bug.* The business failure — a picker losing 40 minutes of scans — is far more
  expensive than the technical one.
- **Fix:** `SavedStateHandle` for small UI state (it survives process death; a plain field does
  not); Room/DataStore written **per step**, not at workflow end.
- **False positives:** genuinely transient state (a scroll position, a filter string).

**MEM-STATE-002 · MEDIUM — oversized `onSaveInstanceState`**
- **Signature:** lists, bitmaps, or large parcelables written to the saved-state `Bundle`.
- **Why:** The Binder transaction has a hard limit; `TransactionTooLargeException` on a background
  transition is an ugly, hard-to-reproduce crash. It also *doubles* your peak at the worst moment.
- **Fix:** Save an ID; rehydrate from the database.
- **False positives:** small primitives and short strings.

---

### 5.9 Family `MEM-COMPOSE`

Most Compose memory rules are Tier A (§4.2) — use them. Beyond those:

**MEM-COMPOSE-001 · HIGH — whole dataset hoisted into composition state**
- **Signature:** `mutableStateOf(emptyList<T>())` / `mutableStateListOf<T>()` populated from a full
  query or network result, rather than `collectAsLazyPagingItems()` or a paged flow.
- **Why:** `LazyColumn` virtualises *composables*, not your **data**. Lazy layouts only pay off if
  the list itself is lazy — a 50 000-item `List<T>` in state is fully resident regardless of how few
  rows are drawn. The measured wins reported for lazy layouts (up to **73 %** memory reduction on
  large lists) assume paged data. [U — third-party benchmark, directionally right, not a Google
  figure; do not quote as authoritative.]
- **Fix:** Paging 3 + `collectAsLazyPagingItems()`, or a bounded query.
- **False positives:** small, bounded lists.

**MEM-COMPOSE-002 · MEDIUM — missing stable keys in lazy lists**
- **Signature:** `items(list)` with no `key = { it.id }`.
- **Why:** Poor reuse across data changes → more churn than necessary. Primarily a performance rule,
  memory-adjacent.
- **Fix:** Provide a stable key.
- **False positives:** static, never-reordered lists.

---

### 5.10 Family `MEM-DEP` — dependencies are memory

**MEM-DEP-001 · MEDIUM — reflection-based DI**
- **Signature:** a runtime-reflection DI container instead of Hilt/Dagger.
- **Why:** "Dagger doesn't use reflection to scan your app's code… without needless runtime cost or
  memory usage." [V]
- **Fix:** Hilt or Dagger 2 (compile-time).
- **False positives:** small service-locator patterns with no reflection.

**MEM-DEP-002 · MEDIUM — unnecessary heavyweight dependencies**
- **Signature:** `WebView` on a low-RAM target; an analytics/crash SDK spawning its own thread
  pools; GMS/Play-Services libraries on a **non-GMS Zebra device** (they cannot initialise at all,
  so this is a correctness bug *and* dead weight); multiple overlapping image or HTTP stacks.
- **Why:** Each is APK bytes, classes to load, and often an uninvited background initialiser.
- **Fix:** Audit `./gradlew :app:dependencies` and justify each top-level entry. Verify GMS
  availability for the specific target SKU rather than assuming either way.
- **False positives:** business-required components. Report as *justify*, not *remove*.

**MEM-DEP-003 · LOW — unwanted `androidx.startup` initialisers**
- **Signature:** transitive `InitializationProvider` entries you never asked for.
- **Why:** Cold-start work and resident objects for features you don't use.
- **Fix:** `tools:node="remove"` the specific `meta-data` entries.
- **False positives:** initialisers that are actually required.

**MEM-DEP-004 · LOW — gratuitous abstraction layers**
- **Signature:** deep wrapper/interface hierarchies with a single implementation and no seam value.
- **Why:** "abstractions generally require more code to be executed… a larger compiled codebase
  directly increases the physical RAM your app requires. If your abstractions aren't significantly
  beneficial, avoid them." [V]
- **Fix:** Collapse them.
- **False positives:** abstractions carrying real testing or platform-variation value. **Be
  conservative here** — this rule is easily abused into bad architecture advice, and I would set it
  to *informational* by default.

---

### 5.11 Family `MEM-OBS` — observability

**MEM-OBS-001 · HIGH — no `MemoryLimiter` detection**
- **Signature:** no `ApplicationExitInfo` inspection anywhere in the codebase.
- **Why:** **Without this you are blind to the exact failure this whole effort is about.** Memory
  Limiter kills carry **no stack trace**; the *only* signal is `REASON_OTHER` plus a description
  containing `"MemoryLimiter:AnonSwap"`. [V] Every crash dashboard on the market will show this as
  nothing at all.
- **Fix:** On startup, read `ActivityManager.getHistoricalProcessExitReasons(...)`, inspect
  `getReason()` and `getDescription()`, and report memory-limit kills to your telemetry as a
  first-class event class.
- **False positives:** none worth honouring on a fleet app.

**MEM-OBS-002 · MEDIUM — no LeakCanary in debug**
- **Signature:** no `debugImplementation` LeakCanary.
- **Why:** Cheapest possible detection for the MEM-LIFECYCLE family, which is the family most likely
  to get your app uninstalled per §1.3.
- **Fix:** Add it to debug builds only.
- **False positives:** teams using an equivalent heap-analysis pipeline.

**MEM-OBS-003 · LOW — no `ProfilingManager` triggers**
- **Signature:** no `ProfilingManager` registration on an app targeting A15+.
- **Why:** `TRIGGER_TYPE_OOM` yields a Java heap dump at the moment of `OutOfMemoryError`;
  `TRIGGER_TYPE_ANOMALY` "delivers a heap dump **just prior to** the system terminating the app" —
  i.e. real-user forensics for the untraceable kill. [V]
- **Fix:**
  ```kotlin
  val pm = applicationContext.getSystemService(ProfilingManager::class.java)
  pm.registerForAllProfilingResults(executor, resultCallback)
  pm.addProfilingTriggers(listOf(ProfilingTrigger.Builder(ProfilingTrigger.TRIGGER_TYPE_ANOMALY)))
  ```
  Analyse with **Heap Dump Explorer** in Perfetto UI (`ui.perfetto.dev`). [V]
- **False positives:** fleets entirely below A15 — likely today, not for long. Advisory only.

**MEM-OBS-004 · HIGH — no recorded memory baseline or budget**
- **Signature:** no committed baseline artefact — no recorded `{device, OS, unit, state, scenario,
  value}` tuple anywhere in the repo, docs or CI config; no stated headroom target.
- **Why:** **Without a baseline, every other number in this document is unanchored.** You cannot
  compute headroom (§8.6), cannot detect regression (axis 3), and cannot close the footprint model —
  `B` is unmeasurable from source by definition (§8.3). Google's own A17 guidance names this
  explicitly: *"we recommend the following memory best practices, **including establishing a baseline
  for memory**."* [V]
- **Fix:** Run §8.7 once per device tier; commit the resulting tuples as a checked-in file
  (`memory-baseline.json` or similar) next to your Baseline Profile. Restate on each device-tier
  change.
- **False positives:** none. This is the prerequisite for everything quantitative.
- **Note:** this is the rule the skill should report **first**, ahead of any BLOCKER, because it gates
  the credibility of the rest of the report.

**MEM-OBS-005 · MEDIUM — no memory regression gate in CI**
- **Signature:** no Macrobenchmark memory metric in CI; no stored baseline to diff against; no
  headroom assertion in any release check.
- **Why:** Axis 3 (§8.5). Footprint growth between releases is otherwise **completely unobservable**
  — you discover it from field kills, months later, with no stack trace (§1.2). Track **RSS**, not
  PSS: Google states RSS "is better for tracking changes in memory allocation" and is faster to
  compute. [V]
- **Fix:** Macrobenchmark on a fixed device in CI, same scenario every run, fail on >5 % steady-state
  RSS regression or on headroom dropping a band. Absolute values are not portable across devices;
  **deltas on one device are the valid signal.**
- **False positives:** teams with an equivalent field-telemetry pipeline already trending RSS per
  release. Emulator-based CI is acceptable *for deltas only* — say so rather than implying it
  validates headroom.

---

## 6. Proposed lint output contract

A linter is only adopted if its output is actionable and stable. What I would specify:

1. **Group by family, order by severity**, BLOCKER first. Never emit a flat 300-line list.
2. **One line per finding**: `file:line · RULE-ID · severity · one-sentence claim`.
3. **Every finding carries the fix**, not just the diagnosis.
4. **State the tier, and split Tier C correctly.** A Tier C item is never phrased as a defect — but
   it is also not simply "unknown." Say what *is* derivable and what is missing:
   *"scaling derived (≈166 B/row); baseline and ceiling require §8.7"*. "Go measure it" without the
   derived half throws away the skill's best work.
5. **Cap the noisy families.** Report at most N findings for LOW rules with a
   *"+ 34 more of this kind"* summary. Unbounded LOW output is how linters get disabled.
6. **Quantify every scaling finding** (§8.4). Any rule whose cost grows with customer data must carry
   a derived bytes-per-record figure, a projection table, and a crossing point — labelled as a model.
   **Order the BLOCKERs by derived magnitude, not by rule ID**, so the biggest win is first.
7. **Separate the two summaries: what it costs now, and what it will cost.** Present current headroom
   and then the axis-1/axis-2 forecasts (§8.5). These are different questions with different
   confidence levels and must not be blended into one number.
8. **End with the measurement plan** (§8.7), naming the specific missing terms (`B`, `L`, `n_max`,
   `C`) rather than a generic "go measure." The source-only verdict is genuinely incomplete, and
   saying precisely *which term* is missing is what makes the rest credible.
9. **Report what was *not* checked.** If the project has no manifest in scope, or native code was
   skipped, say so.

Suggested report skeleton:

```
RAM-CONSCIOUS LINT · <module>
Device tier: TC26 / 3 GB / A16    Ceiling C: 512 MB (not-visible, measured)
Headroom:  visible 61% 🟢   not-visible 14% 🟠  <- BINDING: sync worker

── COSTS NOW ─────────────────────────────────────────────────────────────
BLOCKER (3)  — ordered by derived magnitude
  ItemRepository.kt:88 · MEM-CACHE-001 · ~166 B/row × n, no eviction
      200k rows ≈ 33 MB · 2M rows ≈ 332 MB (model, uncalibrated)
      → bounded LruCache, or query Room (converts anon → file-backed)
  SyncRepository.kt:142 · MEM-DATA-001 · whole-response parse, peak ≈ 4-6× payload
      → stream with JsonReader, batch-insert; peak becomes O(batch)
  ...
HIGH (7) … MEDIUM (12) … LOW (31, showing 5)

── WILL COST ─────────────────────────────────────────────────────────────
Axis 1 data:      exceeds headroom at n ≈ 480 000 rows
Axis 2 session:   leak risk present; rate UNMEASURED — run the iteration protocol
Axis 3 release:   no CI memory gate — growth is unobservable between releases
Axis 4 platform:  3 A17-gated rules; 62% of fleet reaches A17 by Q2
Axis 5 fleet:     binding tier is 3 GB (EC50/TC26), not the 8 GB dev device

── TO CLOSE THE MODEL ────────────────────────────────────────────────────
Missing: B (baseline), L (leak rate), n_max (largest dataset)
  adb shell "grep RssAnon /proc/$(pidof -s <pkg>)/status"    # B, and axis-2 slope
  adb shell am memory-limiter status                          # C, both states
  Business input needed: largest production row count

NOT CHECKED: native code (no .so found), instrumentation tests
```

---

## 7. Golden path the skill should recommend

Ordered by return on effort, which is deliberately *not* the order developers instinctively pick
(they start at 5):

0. **Establish the baseline and the ceiling.** Read the real limits off a real device (§8.7) and
   commit the tuple (MEM-OBS-004). Without this everything else is opinion. Google's own framing:
   "including establishing a baseline for memory." [V] **Note both ceilings and both process states**
   — the binding one is usually not-visible (§8.6).
1. **Turn on Tier A** (§4). Hours of work, deterministic findings.
2. **Fix the BLOCKERs, ordered by derived magnitude** (§8.4) — typically MEM-CACHE-001 and
   MEM-DATA-001 first because their cost scales with customer data, then MEM-BITMAP-001,
   MEM-LIFECYCLE-001/003, MEM-BUILD-001. In enterprise apps these account for the large majority of
   real memory trouble. **Quantify before you sequence** — the biggest rule ID is not the biggest win.
3. **Add observability** — MEM-OBS-001 first. You cannot manage what you cannot see, and this
   failure mode is invisible by default.
3a. **Close the forecast** — measure the axis-2 leak rate per business transaction and compute
   time-to-kill (§8.5). This is what turns "we have a leak" into "we lose the app at pick 800."
4. **Make death survivable** — MEM-STATE-001, and test with `adb shell am kill` (not `force-stop`,
   which is more aggressive than the system's own behaviour and will mislead you about restore).
5. **Then** micro-optimise — the MEM-DATA-005 / MEM-DEP-004 tail. Last, and only where measured.

---

## 8. Quantification: gauging current and future footprint

**This section supersedes an earlier, over-strict stance in this document.** The first draft said a
source-only linter must make *structural claims only* — "this cache is unbounded by construction" —
and never a quantitative one. That protects against fabrication, but it is too conservative, and it
cripples the skill in three ways that matter:

- **It cannot prioritise.** Twelve BLOCKERs with no magnitudes is a list, not a plan. Which one is
  40 MB and which is 400 KB?
- **It cannot forecast.** The question an enterprise owner actually asks is *"will this app still fit
  next year, on the next customer, on the next OS?"* — and "unbounded" does not answer it.
- **It cannot prove value.** Without a before-and-after number, the work cannot be justified or
  defended in review.

The resolution is not to relax the honesty rule but to **locate it more precisely**. The real
prohibition is on *inventing* numbers, not on *deriving* them.

### 8.1 Three kinds of quantitative claim — only one is forbidden

| Kind | Basis | Example | Allowed? |
|---|---|---|---|
| **Measured** | Device evidence, stated device + unit + method | "Peak RSS 214 MB on TC26 (3 GB), sampled during full sync" | **Yes** — with the evidence attached |
| **Derived / parametric** | Source-derived cost function + explicitly stated parameters, derivation shown | "≈170 B/row × row count; at 200 000 rows ≈ 34 MB" | **Yes** — labelled a *model*, with its inputs visible |
| **Invented** | A number with no measurement and no shown derivation | "This probably uses about 60 MB" | **Never** |

**The operative rule is therefore: never state a quantity without either a measurement or a shown
derivation.** The middle row is what the first draft missed, and it is where nearly all of the
forecasting value lives — because a source tree does not tell you the *value*, but it very often
fully determines the **function**.

That distinction is the whole idea of this section:

> **You cannot read your footprint off the source. You can very often read your *scaling law* off
> the source — and a scaling law plus one measurement is a forecast.**

### 8.2 Three ceilings, three units — and the unit error that invalidates most measurements

Before quantifying anything, fix the unit. An app can die three distinct deaths, against three
different ceilings, measured in three incompatible units. **Comparing a number to the wrong ceiling
is the most common analytical mistake in this whole area**, and it silently invalidates otherwise
careful work.

| Death | What is counted | Ceiling | Read it from |
|---|---|---|---|
| **`OutOfMemoryError`** | Java/Dalvik heap only | `dalvik.vm.heapgrowthlimit` (or `heapsize` with `largeHeap`) | `getprop`; `ActivityManager.memoryClass` / `largeMemoryClass` |
| **`MemoryLimiter:AnonSwap`** (A17+) | cgroup v2 — **anonymous + swap**, with file-backed also counted toward `memory.high` | `memory.high`, `memory.swap.max` | `am memory-limiter status`; `/vendor/etc/memory-limiter-config.xml` |
| **LMK kill** | System-wide pressure vs. your `oom_adj_score` | `MemAvailable` and the LMK thresholds | `/proc/meminfo`; you are usually a *victim* here, not the cause |

Three consequences that follow directly, and that I would put on the first page of any measurement
guide:

1. **PSS is the wrong unit for enforcement.** PSS exists for *system-wide attribution* — it splits
   shared pages proportionally so system totals don't double-count. Google is explicit that it "takes
   a long time to calculate" and that **"RSS … is better for tracking changes in memory
   allocation."** [V] Memory Limiter enforces on cgroup counters, not PSS. So the near-universal
   habit of quoting `dumpsys meminfo`'s TOTAL PSS and comparing it to a limit is a unit error. Use
   PSS to answer *"who is using this device?"*; use **RSS / cgroup counters** to answer *"am I near
   my limit?"*.
2. **Your Java heap number tells you nothing about Memory Limiter.** A 3 MB Java heap alongside 400 MB
   of native bitmap allocations is nowhere near `OutOfMemoryError` and comfortably lethal under
   Memory Limiter. The two ceilings are independent; you must track both.
3. **You cannot page your way out.** On Android, reclaimed anonymous pages go to **zRAM**, where they
   are *compressed*, not evicted — "moving dirty cached pages and anonymous dirty pages to zRAM where
   they are compressed." [V] Compressed anonymous memory **still occupies physical RAM**. This is the
   quantitative teeth behind §2.5: clean file-backed pages can be dropped to zero cost; anonymous
   pages can only be made *smaller*, never free. It is also why the kill is named `AnonSwap`.

**The single number to track for A17 survival is `RssAnon`**, and conveniently it is per-process and
cheap to read (§8.7).

### 8.3 The footprint model

A quantified skill needs one shared model so that every number has a place to sit:

```
F(n, t)  =  B  +  Σ (nᵢ × sᵢ)  +  max(P₁ … P_k)  +  L × t
            │      │                │                 │
            │      │                │                 └─ accumulation: leak rate × uptime  (axis 2)
            │      │                └─ transient peaks: take the MAX, they rarely coincide
            │      └─ scaling terms: record count × bytes-per-record  (axis 1)
            └─ fixed baseline: ART runtime, dex/code, resources, framework, view tree
```

Who can supply each term:

| Term | Source-derivable? | How obtained |
|---|---|---|
| **B** baseline | **No** | Measure once per device tier. Largely outside your control; R8 moves it (§4.1). |
| **sᵢ** bytes per record | **Yes — fully** | Computed from the data class + container type (§8.4) |
| **nᵢ** record count | **No — it is the customer's** | Ask; or read from the largest production dataset |
| **Pⱼ** transient peaks | **Mostly yes** | Bitmap dimensions × config; payload size × parse multiplier (§3.1, MEM-DATA-001) |
| **L** leak rate | **No** | Measure by iteration (§8.5, axis 2) |

**So a source-only linter can derive `sᵢ` and `Pⱼ` — the two terms that scale — and needs exactly two
measurements (`B` and `L`) plus one business input (`nᵢ`) to close the model.** That is a small,
achievable ask, and it is the difference between a linter that nags and one that forecasts.

Note the `max()` on the transient terms: a 48 MB bitmap decode and a 30 MB JSON parse that never
happen simultaneously do not sum. Summing them is how memory reports become alarmist and get
ignored. Conversely, if they *can* overlap — a photo upload during a background sync — they do sum,
and that overlap is a source-derivable fact about your concurrency.

### 8.4 Deriving `sᵢ` from source — object sizing arithmetic

This is the part I had wrongly assumed was un-derivable. **A data class's memory cost is fully
determined by its declaration.** [D — model; assumptions stated below]

ART layout assumptions:

| Element | Bytes |
|---|---|
| Object header | 8 (4-byte class pointer + 4-byte lock word) |
| Array header | 12 (8 + 4-byte length), then padded |
| Object reference | 4 (ART uses 32-bit references) |
| `boolean`, `byte` | 1 |
| `char`, `short` | 2 |
| `int`, `float` | 4 |
| `long`, `double` | 8 |
| Object alignment | round up to a multiple of 8 |
| `String` (ASCII/Latin-1, compressed) | ≈ 16 + 1 per char, then aligned |
| `String` (non-Latin-1) | ≈ 16 + 2 per char, then aligned |
| Boxed `Integer`/`Long` vs primitive | ≈ 16 + a 4-byte reference, vs 4 or 8 — **the autoboxing tax** |
| `HashMap` entry overhead | ≈ 30 (24-byte `Node`, aligned, + 4-byte table slot + 0.75 load-factor slack) |
| `ArrayList` slot overhead | 4 per slot + up to 50 % growth slack |

Worked example — the archetypal enterprise master-data cache:

```kotlin
data class Item(
    val sku: String,          // ~24 chars, ASCII
    val description: String,  // ~40 chars, ASCII
    val price: Double,
    val qtyOnHand: Int,
    val locationId: Long,
    val active: Boolean,
)
val cache = HashMap<String, Item>()   // keyed by sku — key object shared with the field, not doubled
```

| Component | Arithmetic | Bytes |
|---|---|---|
| `Item` instance | 8 hdr + 4 + 4 refs + 8 double + 4 int + 8 long + 1 bool = 37 → align 8 | **40** |
| `sku` String | 16 + 24 = 40 | **40** |
| `description` String | 16 + 40 = 56 | **56** |
| `HashMap` entry overhead | Node 24 + slot 4 + load slack ≈ 30 | **30** |
| **Total per row** | | **≈ 166 B** |

And now the forecast — the thing the user actually wants:

| Row count `n` | Derived footprint | Verdict |
|---|---|---|
| 5 000 (dev fixture) | **0.8 MB** | invisible — *this is why it passes in dev* |
| 50 000 | 8.3 MB | tolerable |
| 200 000 | **33 MB** | material on a 3 GB device |
| 1 000 000 | **166 MB** | fatal on every Zebra tier |
| 2 000 000 (largest customer) | **332 MB** | fatal, and *anonymous* — cannot be dropped, only compressed (§8.2) |

**That table is derivable from the source tree plus one business question.** It also quantifies
exactly why MEM-CACHE-001 is a BLOCKER and why "it works in dev" is guaranteed: the dev fixture is
40× to 400× smaller than production. The linter should emit precisely this table per cache it finds.

Precision discipline for this model:

- It is accurate to roughly **±2×** on absolute bytes, and **exact on the scaling exponent**.
  The exponent is what forecasts; the constant is what you calibrate.
- **Calibrate once against a real heap dump** and keep the ratio. After one calibration the model
  is genuinely trustworthy for that codebase.
- Report it as **"≈ 166 B/row (model; calibrate)"**, never "166 B/row".
- Watch the traps that break the arithmetic: shared/interned `String`s (do not double-count),
  `data class` `copy()` producing transient duplicates, nested collections (recurse), lazy fields,
  and — the big one — **`String` fields whose length is customer-controlled**. A description field
  that is 40 chars in your fixture and 400 in production moves the whole table by 10×. **Derive
  string lengths from the DB schema's column widths, not from the test data.** That is a genuinely
  useful trick and it is fully static.

### 8.5 The five growth axes — what "future footprint" actually means

"Future" is ambiguous, and the ambiguity hides the fact that these axes need *different* instruments.
Separating them is most of the work.

#### Axis 1 — Data growth (`n`): the customer's data, not yours
- **Model:** `F = B + n × s`, with `s` derived per §8.4.
- **Forecast:** evaluate at `n_max` = the largest production dataset, not the fixture.
- **Skill output:** the §8.4 table, plus the crossing point — *"exceeds available headroom at
  n ≈ 480 000 rows."* A crossing point is far more actionable than a footprint.
- **Source-derivable?** The function yes; `n_max` is one business question.

#### Axis 2 — Session growth (`t`): the "dies after lunch" axis
This is the axis nobody instruments, and it is the one that produces the classic, maddening
enterprise bug report.

- **Model:** `F(t) = F₀ + L × t`, so **`time-to-kill = (C − F₀) / L`**.
- **Worked example:**

  | Quantity | Value |
  |---|---|
  | Ceiling `C` (not-visible limit, read from device) | 512 MB |
  | Steady-state `F₀` after warm-up | 180 MB |
  | Headroom | 332 MB |
  | Measured accumulation `L` | 12 MB/h → **27.7 h to kill** → survives a 10 h shift ✓ |
  | If instead `L` = 40 MB/h | → **8.3 h to kill** → **dies before shift end** ✗ |

- **The important refinement: measure `L` per business transaction, not per hour.** A leak does not
  accrue with the clock; it accrues with *work done*. Express it as **memory per pick / per scan /
  per label / per order line**:

  ```
  L = ΔRssAnon per workflow iteration        (measure over 20–50 iterations)
  shift cost = L × transactions per shift
  ```

  This reframing pays off three ways. It is **stable across customers** where MB/hour is not. It
  makes the budget conversation native to operations — *"0.4 MB per pick × 800 picks = 320 MB"*. And
  it **explains the observation that mystifies support teams: the app fails first for the *fastest*
  operators**, because they reach the ceiling sooner. A metric expressed per hour hides that; a
  metric expressed per pick makes it obvious.

- **Skill output:** *"Accumulates ≈0.4 MB per pick. At 800 picks/shift that is 320 MB against 332 MB
  headroom — expect kills late in high-throughput shifts."* That sentence is a forecast, a diagnosis
  and a business case at once.
- **Source-derivable?** No — but the *risk* is (any MEM-LIFECYCLE or MEM-CACHE finding predicts a
  non-zero `L`), so the linter should demand the measurement wherever it finds one.

#### Axis 3 — Release growth (`v`): regression across builds
- **Model:** footprint per commit, versus a stored baseline.
- **Instrument:** Macrobenchmark in CI, tracking **RSS** — Google's stated reason being that RSS "is
  better for tracking changes in memory allocation" and is faster to compute. [V] Same-device,
  same-scenario comparison; absolute values are not portable across devices, deltas are.
- **Gate:** fail on > 5 % regression in steady-state RSS, or any regression that reduces headroom
  below its band (§8.6). Store the baseline in the repo like a Baseline Profile.
- **Skill output:** *"No memory regression gate in CI — footprint growth is currently unobservable
  between releases."* Which is, for most enterprise apps, the true state of affairs.

#### Axis 4 — Platform growth: the ceiling arrives, uninvited
Your app can be unchanged and still start dying, because the *ceiling* changed:
- **A17 Memory Limiter switches on** as fleet devices upgrade. An app comfortable on A13 meets
  enforcement on A17 with no code change.
- **16 KB pages** raise device-level memory use marginally (§2.4) — the OEM's cost, but it shifts
  `MemAvailable`.
- **Each OS version's own framework growth** eats shared headroom.
- **Forecast:** overlay your fleet's OS-upgrade schedule on your headroom.
  *"62 % of the fleet reaches A17 by Q2; at current headroom, 18 % of devices are in the red band."*
- **Skill output:** flag the version gate on every A17-dependent rule, and require the fleet OS
  spread as an input (§9.5).

#### Axis 5 — Constraint change: the denominator moves against you
The §1.1 economics, expressed as a term in the model. `C` is not a constant across your fleet's
lifetime: as DRAM prices keep low-RAM SKUs in service and delay refreshes, the **fleet-weighted
median `C` stalls or falls** while axes 1–4 push `F` up.
- **Forecast:** evaluate headroom against the *worst* tier you must support, weighted by fleet share
  — not against the newest device.
- **Skill output:** compute headroom per device tier, and report the **binding tier**. For most Zebra
  estates that is a 3 GB TC21/TC26 or EC50, never the TC58 on the developer's desk.

**Summary of instruments:**

| Axis | Grows with | Derivable from source? | Instrument | Headline number |
|---|---|---|---|---|
| 1 Data | customer records | **function: yes** | §8.4 model + `n_max` | crossing point in rows |
| 2 Session | work done | risk: yes; rate: no | ΔRssAnon per transaction | time-to-kill / picks-to-kill |
| 3 Release | commits | no | Macrobenchmark RSS in CI | % regression vs baseline |
| 4 Platform | OS upgrades | version gates: yes | fleet OS spread × headroom | % of fleet in red band |
| 5 Constraint | procurement | no | fleet mix | headroom on the **binding tier** |

### 8.6 Headroom — the one number to lead with

Absolute MB is not comparable across a 1 GB WS50 and an 8 GB TC27. **Headroom is.**

```
H = (C_binding − P_binding) / C_binding
```

where `P_binding` is peak usage **in the process state whose ceiling is tightest**. Per §2.3 and
MEM-PROC-003, that is very often the **not-visible** state — because foreground-service and worker
code does the heaviest allocation under the *more restrictive* limit. **Reporting only the
interactive peak is the second big measurement error in this area**, and it flatters the app exactly
where it is weakest.

| Band | Headroom | Reading |
|---|---|---|
| 🟢 Green | > 50 % | Comfortable; hold with a CI gate (axis 3) |
| 🟡 Amber | 25–50 % | Fits today; axes 1–2 will close it. Forecast before shipping |
| 🟠 Orange | 10–25 % | Kills under adverse conditions — big customer, long shift, crowded device |
| 🔴 Red | < 10 % | Expect `MemoryLimiter:AnonSwap` in the field. Treat as a live incident |

Report headroom in **both** states and name the binding one:
*"TC26 (3 GB): visible 61 % 🟢 · not-visible 14 % 🟠 — binding constraint is the sync worker."*

### 8.7 The measurement protocol — [V] for commands, [U] for cgroup paths

```bash
PKG=com.example.enterprise
PID=$(adb shell pidof -s $PKG)

# ── CEILINGS ────────────────────────────────────────────────────────────────
adb shell am memory-limiter status            # A17+: enforced limits in MB, event & process counts
adb shell cat /vendor/etc/memory-limiter-config.xml   # Zebra's values, NOT Google's
adb shell getprop dalvik.vm.heapgrowthlimit   # separate Java-heap ceiling (OutOfMemoryError)
adb shell getprop dalvik.vm.heapsize          # ceiling with largeHeap
adb shell getprop ro.config.low_ram           # "true" => isLowRamDevice()
adb shell cat /proc/meminfo | head -3         # MemTotal, MemAvailable

# ── THE NUMBER THAT MATTERS (RssAnon) — cheap, per-process, right unit ──────
adb shell "grep -E 'VmRSS|RssAnon|RssFile|RssShmem|VmSwap' /proc/$PID/status"
#   RssAnon  <- anonymous: what AnonSwap kills you for. TRACK THIS.
#   RssFile  <- clean file-backed: the cheap kind (§2.5)

# cgroup v2 counters — the actual enforcement values. Path varies by build: [U]
adb shell "find /sys/fs/cgroup -name 'memory.high' -path '*uid*' 2>/dev/null | head"
# then: cat memory.current  memory.high  memory.swap.current  memory.stat

# ── BREAKDOWN (attribution, not enforcement) ────────────────────────────────
adb shell dumpsys meminfo $PKG    # PSS split; also Views: and Activities: leak indicators
adb shell dumpsys meminfo | head -40   # what the Zebra stack costs BEFORE your app starts
# Activities: > the number actually open  ==  a leak
# Views: growing across repeated navigation  ==  a leak

# ── AXIS 2: leak rate per business transaction ──────────────────────────────
for i in $(seq 1 30); do
  # drive one full workflow iteration here (uiautomator / adb shell input / instrumented test)
  adb shell "grep RssAnon /proc/$PID/status"
done
# Fit a line. Slope = MB per transaction. Discard the first ~5 (warm-up, JIT, caches filling).

# ── REPRODUCE THE KILL DELIBERATELY ─────────────────────────────────────────
adb shell am memory-limiter manual $PID 300MB   # force a tight limit and confirm behaviour
adb shell am memory-limiter ignore <uid>        # exempt while bisecting
adb shell am kill $PKG                          # LMK-like kill; force-stop is NOT equivalent
adb shell ps -A | grep $PKG                     # confirm death, then relaunch and check restore
```

Protocol discipline — without these, the numbers are not comparable:

1. **Same device, same OS build, same fleet software** (DataWedge, EMM agent, VPN all present).
2. **Warm up first.** Discard the first several iterations; JIT, caches and lazy init inflate them.
3. **Sample the not-visible state too** — press Home, keep the worker running, sample again (§8.6).
4. **Record the tuple** every time: `{device, RAM tier, OS, unit, state, scenario, value}`. A number
   without its tuple is not evidence, and will be misquoted later.
5. **Real hardware only.** A 4 GB emulator with no Zebra stack, no EMM agent and no DataWedge cannot
   reproduce the conditions that matter. It is valid for *deltas* in CI, not for absolute headroom.

### 8.8 What a quantified finding looks like

The difference the whole section buys — same defect, before and after:

```
BEFORE (structural only)
  MEM-CACHE-001 · BLOCKER · ItemRepository.kt:88
      Unbounded in-memory cache; no eviction policy.

AFTER (quantified)
  MEM-CACHE-001 · BLOCKER · ItemRepository.kt:88
      HashMap<String, Item>, loaded from SELECT * FROM items, no eviction.
      Derived cost: ~166 B/row (model, uncalibrated)
        5 000 rows (dev fixture) .....   0.8 MB   <- why this passes in dev
        200 000 rows (typical prod) ...  33 MB
        2 000 000 rows (largest cust) . 332 MB    <- fatal on every Zebra tier
      Anonymous memory: cannot be dropped, only zRAM-compressed (§2.5)
      Crossing point: exceeds measured headroom (332 MB) at ~480 000 rows
      → Fix: bounded LruCache, or query Room directly (converts anon → file-backed)
      → Confirm: heap dump to calibrate the 166 B/row constant
```

The second version is prioritisable, forecastable and defensible in a review. Neither version
invented a number: every figure is either derived-and-shown or measured-and-attributed.

### 8.9 Honesty rules for quantification

Non-negotiable, because this is the section most able to damage the skill's credibility:

1. **Every number carries its unit and its ceiling.** "214 MB RSS against a 512 MB not-visible
   limit," never "214 MB."
2. **Never mix units** (§8.2). Java heap vs. `heapgrowthlimit`; RssAnon vs. `memory.high`. A
   cross-unit comparison is worse than no number.
3. **Label models as models**, with their parameters and their calibration state visible.
4. **Attribute every measurement** to its device, OS build and scenario tuple.
5. **State the direction of error.** The §8.4 model omits alignment slack and shared strings — say
   which way it is likely wrong.
6. **Distinguish sum from max** on transient peaks (§8.3), and justify which you used.
7. **Never extrapolate past one axis at a time** without saying so. Compounding axis 1 and axis 2
   projections multiplies their error too.
8. **Emit the crossing point, not just the value.** "Fails at ~480 000 rows" survives contact with
   a changing business; "uses 33 MB" does not.

### 8.10 What remains genuinely undecidable from source

The boundary still exists — it has just moved to the right. Refined:

| Cannot be determined from source | Honest lint assertion |
|---|---|
| Baseline `B`, and therefore absolute footprint | "Baseline not measured. Run §8.7; the model gives scaling only." |
| Leak rate `L` | "Leak risk present (MEM-LIFECYCLE-001). Rate requires the axis-2 iteration protocol." |
| Record counts `n` | "Scaling derived; supply `n_max` to close the forecast." |
| The device's enforced ceiling `C` | "Unknown — read `/vendor/etc/memory-limiter-config.xml` and `am memory-limiter status`." |
| Whether the app leaks *in practice* | "LeakCanary absent (MEM-OBS-002)." |
| Whether kills occur in the field | "No `ApplicationExitInfo` check (MEM-OBS-001)." |
| Whether state survives process death | "No `SavedStateHandle`/durable write (MEM-STATE-001); verify with `am kill`." |
| Real string lengths and data shape | "Derived from schema column widths; confirm against production data." |

The refined principle, replacing the old absolutism:

> **A linter cannot measure a footprint. It can derive the function that produces one, identify which
> term dominates, and name the measurement that would close the gap. Doing that — and never inventing
> the missing term — is both honest and genuinely useful.**

---

## 9. Design principles for the skill itself

These are the decisions I would want fixed before any file is written, because each one is a way the
skill could fail even with perfect rules.

1. **Precision over recall.** A memory linter that cries wolf gets disabled in a week, and then
   catches nothing forever. Every rule in §5 has a mandatory **False positives** clause for exactly
   this reason. When in doubt, downgrade to *"justify this"* rather than *"defect."*
2. **Never fabricate a threshold.** There is no published MB limit for a 3 GB Zebra device. The
   skill must say *"read it from the device"* and mean it. This is the most likely way an LLM-backed
   linter embarrasses itself.
3. **Never state a quantity without a measurement or a shown derivation** (§8.1). This replaces the
   earlier, cruder "structural claims only." *"Unbounded by construction"* — decidable. *"≈166 B/row;
   at 200 000 rows ≈33 MB (model, uncalibrated)"* — a legitimate derivation, and far more useful.
   *"Probably around 60 MB"* — never. **Quantify wherever the derivation can be shown; the shown
   derivation is what makes it honest**, not the absence of numbers.
3a. **Always name the binding constraint, not the friendliest one.** Peak in the *not-visible* state
   against the *not-visible* ceiling, on the *lowest* fleet tier (§8.6). Reporting the interactive
   peak on the newest device is technically true and practically worthless.
4. **Don't reimplement Android Lint.** Check it's on, promote severities, move on (§4).
5. **Ask for the device target before ruling.** Density buckets, tiering, GMS and the budget itself
   all depend on it. MEM-BUILD-004 and MEM-PRESSURE-003 are *unresolvable* without it, and
   MEM-PRESSURE-001's false-positive gate needs `minSdk` plus the fleet OS spread.
6. **Lead with the business consequence, not the byte count.** "The scanner stops working and Zebra's
   documented remedy is to uninstall your app" (§1.3) moves an enterprise backlog. "Reduce PSS by
   12 MB" does not.
7. **Every rule must be fixable in one sitting.** If the fix is "rearchitect," it is guidance, not a
   lint rule — say so and separate it.
8. **State the version scope on every rule.** Memory Limiter is A17+. Trim deprecation is A14/A15.
   16 KB pages are staged. A Zebra fleet spans **A11 → A19** simultaneously, so a rule without a
   version gate will be wrong on some part of every real fleet.
9. **Never sell a compatibility fix as a memory saving.** MEM-BUILD-005 is the test case: 16 KB
   alignment is something the *device* imposes on you, and complying does not reduce your footprint
   (§2.4). If the skill claims it as a memory win, an engineer who checks will stop trusting every
   other number in the report — and they would be right to. Where a rule's real justification is
   compatibility, correctness, or cost-of-ownership, **say that** and let it stand on its own merit.
10. **Distinguish "who can act on this."** A finding inside a third-party AAR, a vendor SDK, or the
   OEM's `/vendor` partition is not a defect in the codebase being linted. Route those to *raise with
   the vendor* rather than mixing them into the developer's fix list.

---

## 10. Sources

All retrieved **2026-08-04**. **[P]** primary, **[S]** secondary/supplemental.

### Android platform — [P]

| # | Source | Establishes |
|---|---|---|
| A1 | Manage your app's memory · https://developer.android.com/topic/performance/memory | Trim-level deprecation (A14 no delivery, A15 deprecated); `ActivityManager.getMemoryInfo`; R8 guidance; services warning; `SparseArray`; protobuf-lite; abstraction cost; Dagger/Hilt |
| A2 | Prioritizing Memory Efficiency: Essential Steps for Android 17 · https://developer.android.com/blog/posts/prioritizing-memory-efficiency-essential-steps-for-android-17 | "kill the process with no associated stack trace"; `MemoryLimiter:AnonSwap`; R8 flags + Monzo 35 %/30 %/9 %; five image practices; leak table; trim code shape; `ProfilingManager` snippet |
| A3 | Behavior changes: all apps (Android 17) · https://developer.android.com/about/versions/17/behavior-changes-all | Memory-limits intro verbatim; `ApplicationExitInfo` `REASON_OTHER`; the three `am memory-limiter` subcommands |
| A4 | **Memory Limiter (AOSP)** · https://source.android.com/docs/core/perf/memory-limiter | cgroup v2, `memory.high` / `memory.swap.max`; process-state limit table; `/vendor/etc/memory-limiter-config.xml` + 14 GB example; UID < 10000 exemption; "disabled if config missing" |
| A5 | Android 17 features and APIs · https://developer.android.com/about/versions/17/features | `TRIGGER_TYPE_OOM`, `TRIGGER_TYPE_ANOMALY`, `TRIGGER_TYPE_COLD_START`, `TRIGGER_TYPE_KILL_EXCESSIVE_CPU_USAGE` |
| A6 | Support 16 KB page sizes · https://developer.android.com/guide/practices/page-sizes | 16 KB alignment; AGP 8.5.1+; `getpagesize()`; marginal memory increase |
| A7 | Transition to 16 KB page sizes (blog) · https://android-developers.googleblog.com/2025/07/transition-to-16-kb-page-sizes-android-apps-games-android-studio.html | 3.16 % avg / up to 30 % launch-time improvement under pressure; lint flags unaligned libs |
| A8 | Trigger-based profiling · https://developer.android.com/topic/performance/tracing/profiling-manager/trigger-based-capture | Trigger semantics and registration |
| A9 | Lint issue index · https://googlesamples.github.io/android-custom-lint-rules/checks/index.md.html | All Tier A check IDs in §4.2 |
| A10 | **Memory allocation among processes** · https://developer.android.com/topic/performance/memory-management | RSS/PSS/USS definitions verbatim; **"RSS … is better for tracking changes in memory allocation"**; kswapd drops clean cached pages and moves dirty + anonymous pages **to zRAM where they are compressed**; LMK `oom_adj_score` ordering — the basis of §8.2 and §2.5 |
| A11 | `Debug.MemoryInfo` · https://developer.android.com/reference/android/os/Debug.MemoryInfo | `getMemoryStat()` keys incl. `summary.java-heap`, `summary.total-pss` (API 23+); in-app breakdown for §8.7 |
| A12 | Benchmark release notes · https://developer.android.com/jetpack/androidx/releases/benchmark | Macrobenchmark for the axis-3 CI gate (MEM-OBS-005) |

### Zebra — [P]

| # | Source | Establishes |
|---|---|---|
| Z1 | DataWedge Programmer's Guide — Usage Notes & Behavior · https://techdocs.zebra.com/datawedge/15-0/guide/programmers-guides/usage-notes/ | **"When a device is low in memory, DataWedge may not function properly…"** (Device Functionality item 4) — the §1.3 quote |
| Z2 | Zebra supported Android versions · https://www.zebra.com/android-versions | A16/A18/A19 device commitments |
| Z3 | TC22/TC27 spec sheet · https://www.zebra.com/us/en/products/spec-sheets/mobile-computers/handheld/tc22-tc27.html | "6 GB RAM/64 GB UFS Flash; 8 GB RAM/128 GB UFS Flash"; QCM5430 hex-core 2.1 GHz |
| Z4 | MC3300ax spec sheet · https://www.zebra.com/us/en/products/spec-sheets/mobile-computers/handheld/mc3300ax.html | "Worldwide: 4 GB RAM/32 GB Flash Memory"; Snapdragon 660 |
| Z5 | EC50/EC55 spec sheet · https://www.zebra.com/us/en/products/spec-sheets/mobile-computers/handheld/ec50-ec55.html | "3 GB/32 GB; 4 GB/64 GB" |
| Z6 | TC21/TC26 spec sheet · https://www.zebra.com/us/en/products/spec-sheets/mobile-computers/handheld/tc21-tc26.html | "4 GB RAM/64 GB Flash memory; 3 GB RAM/32 GB Flash memory" |
| Z7 | TC15 spec sheet · https://www.zebra.com/gb/en/products/spec-sheets/mobile-computers/handheld/tc15.html | "4 GB RAM/64 GB Flash" |
| Z8 | TC53/TC58 spec sheet · https://www.zebra.com/us/en/products/spec-sheets/mobile-computers/handheld/tc53-tc58.html | up to 8 GB RAM / 128 GB Flash |
| Z9 | Zebra best-practice guides index · https://techdocs.zebra.com/bestpractices/ | Guide inventory; no dedicated memory guide exists today (a gap worth noting) |
| Z10 | WS50 Programmer's Guide (via sibling `small-screen` skill) · https://techdocs.zebra.com/emdk-for-android/13-0/guide/ws50_programming/ | 1 GB shared-RAM quote |

### Market context — [S]

| # | Source | Establishes |
|---|---|---|
| M1 | Tom's Hardware — memory price surge through Q3 2026 · https://www.tomshardware.com/pc-components/ram/memory-price-surge-begins-to-cool-as-consumers-hit-affordability-limit-ai-demand-still-keeps-dram-and-nand-prices-climbing-through-q3-2026 | Continued DRAM/NAND price climb |
| M2 | IDC — Global Memory Shortage Crisis · https://www.idc.com/resource-center/blog/global-memory-shortage-crisis-market-analysis-and-the-potential-impact-on-the-smartphone-and-pc-markets-in-2026/ | Smartphone/PC market impact |
| M3 | Avnet — Riding the AI Supercycle · https://www.avnet.com/integrated/resources/article/2026-memory-shortage-ai-supercycle/ | HBM crowding out commodity DRAM |
| M4 | IPO Forum — Memory Shortage Persists · https://eii.nat.gov.tw/ipoforum/en/news/79 | >50 % Q1-2026 contract rise; TrendForce 90–95 % revision; "unprecedented" |

### Known gaps — be explicit about these

1. **No published MB threshold for any Zebra RAM tier.** The AOSP example is a 14 GB phone. **Read
   `/vendor/etc/memory-limiter-config.xml` and `am memory-limiter status` per SKU.** [U]
2. **Whether Zebra ships a valid `memory-limiter-config.xml` at all** — if it is absent or invalid,
   Memory Limiter is *disabled* on that device. This materially changes the risk picture per SKU and
   is unverified. [U]
3. **Which Zebra SKUs will actually receive A17+**, and when. The support matrix lists A16/A18/A19
   but not A17 explicitly. [U]
4. **Per-process overhead of `android:process`** in MB on a Zebra build — must be measured. [U]
5. **`dalvik.vm.heapgrowthlimit` per Zebra SKU** — not documented anywhere consulted. Read it. [U]
6. **Zebra has no dedicated memory best-practices guide** (Z9). This skill would be filling a real
   documentation gap, which is a good argument for building it.
7. The Compose lazy-layout memory figures in MEM-COMPOSE-001 are **third-party benchmarks**, not
   Google numbers. Directionally useful; do not quote as authoritative. [U]

---

## 11. Next step: from knowledge to skill

This document is the knowledge layer. Turning it into a skill under the local
`zebra-skill-author` standard would mean roughly:

```
skills/memory-conscious-lint-android/
├── SKILL.md                      # modes: lint | explain | fix | measure | forecast
│                                 #   the §6 output contract;
│                                 #   required input = target device(s) + minSdk (§9.5)
│                                 #   optional input = n_max, measured baseline tuple
├── references/
│   ├── rule-catalogue.md         # §5, one section per family
│   ├── tier-a-setup.md           # §4 — lint.xml + R8 wiring
│   ├── quantification.md         # §8 — units/ceilings, the F(n,t) model, object sizing,
│   │                             #   the five growth axes, headroom bands, honesty rules
│   ├── measurement.md            # §8.7 — protocol, commands, sampling discipline
│   ├── device-budgets.md         # §2.2/§2.3 — Zebra tiers, Memory Limiter mechanics
│   ├── anti-patterns.md          # BLOCKERs with exposure conditions + violated invariant
│   └── sources.md                # §10
├── assets/
│   ├── code-patterns/            # streaming JSON→Room; sampled decode; bounded LruCache;
│   │                             #   tiered ImageLoader; ApplicationExitInfo check; trim handler;
│   │                             #   Macrobenchmark memory gate; RssAnon sampler
│   └── test-fixtures/            # sizing fixtures: data classes with KNOWN expected byte costs,
│                                 #   so the §8.4 model itself is regression-tested
└── evals/evals.json              # per-rule positive AND negative fixtures — see below
```

`quantification.md` is the reference I would write first. It is the part with no equivalent in any
existing tool, and every other file leans on its unit discipline.

Four things I would insist on for the eval set, because they are where this kind of skill usually
fails:

- **Every rule needs a negative fixture**, not just a positive one — a bounded cache that must
  *not* be flagged, a small-asset decode that must *not* be flagged, a pre-A14 app whose trim
  branches must *not* be flagged. Precision is the whole product (§9.1).
- **At least one eval asserting the skill refuses to invent a number** — given a source tree and no
  device, it must report `B`, `L` and `C` as UNKNOWN and emit the §8.7 commands, while *still*
  producing the derived per-record scaling. Both halves matter: refusing to fabricate **and**
  refusing to withhold what is legitimately derivable.
- **Sizing accuracy evals.** Feed the §8.4 model a data class whose true retained size is known from
  a real heap dump, and assert the model lands within a stated tolerance (I would start at ±2× and
  tighten). **Without this, the quantification is unfalsifiable** — which would be a worse failure
  than not quantifying at all.
- **A unit-error eval.** Give it a Java-heap figure and an A17 cgroup ceiling and assert it *refuses*
  to compare them (§8.2). This is the mistake most likely to slip through and most damaging to trust.

Open questions genuinely worth your input before I build it:

1. **Which devices and Android versions define your fleet?** This sets the binding tier (§8.6), the
   default ceiling, and the version gates on MEM-PRESSURE-001 and the Memory Limiter rules. It is the
   one input that changes the most output.
2. **Lint-only, or lint-and-fix?** Reporting is safe; auto-applying the MEM-DATA-001 streaming
   refactor is a real code change and needs a different risk posture.
3. **Kotlin/Compose only, or also legacy Java + XML Views?** Most enterprise Zebra code I would
   expect to be the latter, which changes which rule families carry weight (more MEM-LIFECYCLE-005,
   less MEM-COMPOSE).
4. **Should it read from a connected device** when one is available — `/vendor/etc/memory-limiter-config.xml`,
   `am memory-limiter status`, `RssAnon` — promoting Tier C from *asserted* to *measured*? That is the
   difference between a good linter and a genuinely authoritative one, and with §8's model in place it
   is now the highest-value single capability: **one device connection closes `B` and `C`, leaving only
   `n_max` as a business question.**
5. **Do you have production row counts** for the largest customer datasets (items, locations, orders)?
   That single input turns the axis-1 model from a shape into a forecast, and it is usually available
   from a DBA in minutes.
6. **Is per-transaction leak measurement (axis 2) automatable in your setup** — is there an existing
   UI-automation or instrumented-test path that can drive a full workflow iteration 30 times? If yes,
   time-to-kill becomes a CI metric rather than a manual investigation.
