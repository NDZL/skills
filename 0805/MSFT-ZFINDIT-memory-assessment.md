# Memory footprint assessment — MSFT-ZFINDIT

**Target:** https://github.com/NDZL/MSFT-ZFINDIT (clone at `./MSFT-ZFINDIT`, commit `286ca25`
"Gemma 4 added (with some code refactoring); downscaleForVision() to work on a smaller image")
**Assessed:** 2026-08-05
**Skill:** `appquality-memory-assessment-android` v1.0.0-beta.1 — modes run: `inventory`, `estimate`, `plan`
**Assessment kind:** static / scaling-only. **No footprint was measured.**

---

## 0. Header — ceiling, unit, and what is unknown

| Dimension | Value | Source |
|---|---|---|
| Device tier | **UNKNOWN** — not supplied | Developer answered "not decided" |
| Fleet Android spread | **UNKNOWN** — not supplied | Question unanswered |
| Enforced ceiling `C` | **UNKNOWN** | No device attached; `adb devices` returned empty at assessment time |
| Baseline `B` | **UNKNOWN** | Requires a device |
| Accumulation rate `L` | **UNKNOWN** | Requires the iteration protocol |
| Record counts `n` | Partly code-bounded (see §4) | — |
| Unit used throughout | Derived **bytes**, attributed to Java heap or to native/anonymous where distinguishable | — |
| Build system | Gradle 8.13 / AGP 8.5.0, Kotlin 2.2.21 | `gradle/wrapper`, `libs.versions.toml` |
| UI toolkit | XML Views only in the shipped modules (Compose exists only in the excluded `:zfindit`) | manifests + build files |
| `minSdk` / `targetSdk` / `compileSdk` | 29 / 34 / 34 | all module build files |

**Headroom is deliberately omitted from this report.** Per the skill's quantification contract, no
per-device memory threshold is published for any Zebra RAM tier, and none was read from a device.
Reporting a headroom band against an invented ceiling (anti-pattern AP-01) would make every figure
below look precise and be wrong by an unknown factor. §7 lists the exact commands that close this.

**Two consequences of the unknown fleet OS spread**, stated rather than assumed:

- Memory Limiter (`MemoryLimiter:AnonSwap`) exists only on **Android 17+**. Findings whose severity
  depends on the state-dependent ceiling (MEM-PROC-003) are gated and labelled as such.
- Legacy `onTrimMemory` levels stopped being delivered at Android 14. This turns out to be moot here
  (§6) — the app has no pressure handling of any kind — so no version gate was needed for it.

---

## 1. Inventory

### 1.1 Modules, and which actually ship

`settings.gradle.kts` includes **three** of the eight module directories present in the tree:

| Module | In build? | Application ID | Ships | Notes |
|---|---|---|---|---|
| `:msft` | **yes** | `com.ndzl.msft` | ✅ | The substantive app. All LLM, NFC and screen-capture code. |
| `:split-picker` | **yes** | `com.ndzl.splitpicker` | ✅ | Split-screen launcher + accessibility service. |
| `:skills-tester` | **yes** | — | ❌ **cannot build** | Has `src/main/java` only. No `build.gradle.kts`, no `AndroidManifest.xml`, no `res/`. Its `MainActivity` calls `setContentView(R.layout.activity_blocking)`, a layout that does not exist. |
| `:zfindit` | no (commented out) | `com.ndzl.zfindit` | — | Launched at runtime by `:msft` via explicit `ComponentName`, so it is a **separately installed app in its own process**. |
| `:crawler` | no (commented out) | — | — | |
| `:aidcsdk-cloud-index-generator` | no (commented out) | — | — | |
| `:focus-timer`, `:deep-links` | no | — | — | `deep-links` is a bare `assetlinks.json`. |

> **This matters for routing.** The raw scanner reported BLOCKER-severity candidates in `crawler/`,
> `aidcsdk-cloud-index-generator/` and `zfindit/`. None of those are in the build, so none appear in
> the fix list in §5. Listing them would put unbuildable code at the top of a backlog (AP-08's failure
> mode).

> **`:skills-tester` blocks the "Build and behavior" validation level.** The project as checked out
> cannot configure. Nothing in this report was compiled, and no APK analysis (code footprint,
> duplicate native libraries) was possible as a result.

### 1.2 Processes, services and entry points — `:msft`

| Component | Type | Start trigger | Memory relevance |
|---|---|---|---|
| `MicrosoftActivity` | Activity, `singleTop` | LAUNCHER, `TAG_DISCOVERED` | Builds the LLM engine in `onCreate`. 27 buttons, one activity. |
| `ScreenCaptureService` | **Foreground** service, `mediaProjection` | `startForegroundService` from `onActivityResult` | `START_STICKY`, no `stopSelf()`. See finding F3. |
| `NDZLHostApduService` | `HostApduService` | NFC HCE, system-bound | Short-lived APDU handling. Reads a 144-byte asset per call. |

No `android:process` attribute anywhere → **single process** per app. MEM-PROC-002 does not apply
within `:msft`. It *does* apply across the pair `com.ndzl.msft` + `com.ndzl.zfindit`, which are two
processes with two runtimes and — on Android 17+ — two separately enforced limits. That looks
deliberate (it is an inter-app launch demo), so it is routed as *justify*, not *defect*.

### 1.3 Dependencies — `:msft`

```
androidx core-ktx / lifecycle-runtime-ktx / appcompat / activity / constraintlayout
com.google.android.material
com.google.android.gms:play-services-auth-base 18.0.13
com.google.android.libraries.identity.googleid 1.1.1
com.google.android.play:integrity 1.5.0
org.jetbrains.kotlinx:kotlinx-coroutines-android 1.10.2
com.google.ai.client.generativeai 0.9.0            <-- cloud inference path
com.google.mediapipe:tasks-genai 0.10.35           <-- on-device inference runtime #1
com.google.mediapipe:tasks-vision 0.20230731       <-- 2023 build, used for ONE class
com.google.ai.edge.litert:litert-api 2.1.6
com.google.ai.edge.litertlm:litertlm-android 0.14.0 <-- on-device inference runtime #2
```

**Three inference stacks are linked into one APK** — cloud Gemini, MediaPipe `LlmInference`, and
LiteRT-LM `Engine`. Two of them load model weights into this process. See finding F10.

### 1.4 Data layer

There is **no database, no network stack, no serialisation library and no paging**. All persistence
is file I/O to `/sdcard` and `externalCacheDir`. Consequently the MEM-DATA family — the usual
dominant family in an enterprise app — is almost entirely **not applicable** here. This app's memory
profile is driven by *model weights and bitmaps*, not by records.

### 1.5 Bundled assets

| Asset | Bytes on disk | Decoded / in-memory form |
|---|---|---|
| `picture.jpg` | 372,977 | **1600 × 900**, 3 components → 5.49 MiB at ARGB_8888 |
| `AIDCSDK-OCR.txt` | 58,892 | ~57.5 KiB as a Latin-1 `String` |
| `STAGENOW_DATETIME2FEB.bin` | 144 | 144 B |
| `ONE.bin` | 86 | 86 B |

Model weights are **not bundled**. Every model path points at `/data/local/tmp/…`, which is an
adb-push location, not app-private storage. On a locked-down Zebra fleet an app cannot write there.
This is a development arrangement; see §8.

### 1.6 Long-lived state

| Declaration | File | Lifetime |
|---|---|---|
| `companion object { lateinit var mediaProjection / imageReader / virtualDisplay }` | `MicrosoftActivity.kt:88-98` | **Process** |
| `companion object { lateinit var metrics; var density; var nfcAction }` | `MicrosoftActivity.kt:90-97` | **Process** |
| `private var litertEngine: Engine?` | `MicrosoftActivity.kt:780` | Activity (closed in `onDestroy`) |
| `private var llmInference: LlmInference?` | `MicrosoftActivity.kt:598` | Activity — **never closed** |
| `private var llmInferenceSession: LlmInferenceSession?` | `MicrosoftActivity.kt:599` | Activity — **never closed** |
| `lateinit var generativeModelWithInstructions` (captures the 57.5 KiB OCR text) | `MicrosoftActivity.kt:960` | Activity |
| `private var latestBitmap: Bitmap?` | `MicrosoftActivity.kt:1000` | Activity |
| `private val apps = mutableListOf<AppEntry>()` (each holds a `Drawable`) | `split-picker/MainActivity.kt:44` | Activity |
| `SplitAccessibilityService.instance` | `SplitAccessibilityService.kt:29` | Bound service; nulled in `onUnbind` ✅ |

### 1.7 Lifecycle registrations

| Registration | Unregistration | Verdict |
|---|---|---|
| `enableForegroundDispatch` (`onResume`) | `disableForegroundDispatch` (`onPause`) | ✅ symmetric |
| `registerScanReceiver` (`onResume`, skills-tester) | `unregisterReceiver` (`onPause`) | ✅ symmetric — **scanner false positive, cleared** |
| `mediaProjection.registerCallback(mpcallback, null)` | none | ⚠️ callback and projection both statically reachable |
| `imageReader.setOnImageAvailableListener` | none | ⚠️ see F3 |

---

## 2. The footprint model, instantiated for this app

```
F  =  B  +  Σ(nᵢ × sᵢ)  +  max(P₁ … P_k)  +  L × t
      │     │              │                 │
      │     │              │                 └─ F4 text/relayout churn; F3 frame loop
      │     │              └─ F6 bitmap decode; F3 per-frame bitmap
      │     └─ F5 icon list; F1 model weights (a step function, not per-record)
      └─ UNKNOWN — measure once per tier
```

**The scaling terms here are unusual.** There are no customer records. The `n` that drives this app
is *number of installed apps* (F5), *screen pixel count* (F3) and *session length in tokens* (F4).
Two of those three are device properties, which means they are knowable without asking the business
— and none of them were knowable without the device tier, which is why §7 leads with it.

---

## 3. Findings — ordered as the catalogue requires

MEM-OBS-004 is reported first, ahead of the BLOCKERs, because it gates the credibility of every
other number in this document.

---

### F0 · MEM-OBS-004 · HIGH · No recorded baseline or budget

- **Evidence:** no committed baseline artifact anywhere in the tree. No CI directory
  (`.github/`, `.gitlab-ci.yml`, `Jenkinsfile` all absent), no test sources, no `lint.xml`.
- **Why this is first:** `B` is not derivable from source *by definition*. Without one measured
  device/OS/unit/state/scenario/value tuple, every figure below is a scaling law with no anchor —
  correct in its exponent, unanchored in its constant.
- **Verification that closes it:** §7, once per device tier, committed to the repo.

---

### F1 · MEM-CACHE-001 + MEM-DEP-002 · **BLOCKER** · Four LLM engines can be created in one session; none is ever closed

- **Where:** `msft/…/MicrosoftActivity.kt:601` (`initializeGemma`), `:626`
  (`initializeGemmaForImages`), reached from `:681`, `:707`, `:729`, `:751`.
- **Signature:** `llmInference = LlmInference.createFromOptions(...)` overwrites the field with **no
  `close()` on the previous instance**. Verified by search: `llmInference?.close` occurs **0 times**
  in the repository; `llmInferenceSession?.close` likewise **0 times**. `onDestroy` (`:1514`) closes
  `litertEngine` only.
- **Why it is a BLOCKER, not a HIGH:** `LlmInference` wraps a native inference graph holding the
  model weights. Native arenas are released by `close()`, not by the garbage collector. Dropping the
  Java reference makes the object collectable while its native memory stays mapped. So the four
  buttons **sum**, they do not `max`.

**Derived weight floor** (model; parameters visible; **uncalibrated**):
`weight_bytes = parameter_count × bits_per_weight ÷ 8`

| Button | Model path | Params | Quant | Derived floor |
|---|---|---|---|---|
| `onClickbt_GEMMA_BASIC_int4` | `/data/local/tmp/llm/gemma-2b-it-cpu-int4.bin` | 2 B | int4 | 1.00 GB (0.93 GiB) |
| `onClickbt_GEMMA_BASIC_int8` | `/data/local/tmp/llm/gemma-2b-it-cpu-int8.bin` | 2 B | int8 | 2.00 GB (1.86 GiB) |
| `onClickbt_GEMMA3_270M_BASIC_int8` | `/data/local/tmp/gemma-3-270m-it-int8.task` | 270 M | int8 | 0.27 GB (0.25 GiB) |
| `onClickbt_GEMMA3_4B_INT4` | `/data/local/tmp/gemma-3n-E4B-it-int4.litertlm` | 4 B eff. | int4 | 2.00 GB (1.86 GiB) |
| **Session total if all four are tapped** | | | | **5.27 GB (4.91 GiB)** |

**Direction of error: this floor UNDERSTATES.** It counts active parameters at nominal
quantisation and excludes (a) embedding and normalisation tensors, which are commonly kept at higher
precision, (b) the per-layer-embedding and MatFormer slices that Gemma 3n *E*-series files still
contain beyond their "effective" parameter count, and (c) runtime scratch. The published `E4B` int4
artifact in particular is materially larger than the 2.00 GB floor derived here.

**5.27 GB exceeds the total physical RAM of every published Zebra tier**, including the 8 GB
TC53/TC58 — before the kernel, the Zebra stack, the EMM agent and any VPN client are counted.

- **Mitigating uncertainty, stated honestly:** MediaPipe and LiteRT typically **mmap** weight files,
  which would make most of those bytes `RssFile` — clean, file-backed, and droppable by the kernel
  rather than compressed into zRAM. That is the difference between degradation and an `AnonSwap`
  kill. **Whether it holds here is not decidable from source.** The command that settles it is in §7
  (`RssAnon` vs `RssFile`), and it is the single highest-value measurement on this app.
- **Fix:** close the previous `LlmInference` and `LlmInferenceSession` before creating the next;
  close both in `onDestroy` alongside `litertEngine`; and make the four buttons mutually exclusive
  rather than additive.
- **False positives:** none. Four distinct `createFromOptions` call sites, zero `close` call sites.

---

### F2 · MEM-PRESSURE-003 · **BLOCKER** (magnitude), MEDIUM (catalogue) · A multi-GB model is loaded at every launch with no device tiering

- **Where:** `MicrosoftActivity.kt:130` — `warmUpGemma4()` is called unconditionally from
  `onCreate`, before any user interaction.
- **What it does:** builds the LiteRT-LM `Engine` for `/data/local/tmp/gemma-4-E2B-it.litertlm` and
  runs a throwaway 64 × 64 image query to force XNNPack graph compilation (`:826-846`).
- **Derived floor:** E2B ≈ 2 B effective parameters × int4 → **1.00 GB (0.93 GiB)**, same model and
  same understating direction as F1.
- **Search result:** `isLowRamDevice`, `memoryClass` and `largeMemoryClass` occur **0 times** in the
  repository. One binary, one hardcoded model path, no branch on what the device reports.
- **Reading against the tier table** — this is where the unknown device tier bites hardest:

  | Tier | Physical RAM | 0.93 GiB model claimed at launch |
  |---|---|---|
  | WS50 | 1 GB | **Cannot run.** Exceeds total RAM before the kernel and launcher. |
  | WS501 / EC50 / EC55 / TC21 / TC26 (3 GB) | 3 GB | ~31 % of *physical* RAM, as one tenant among several. |
  | TC15 / TC21 / TC26 / MC3300ax (4 GB) | 4 GB | ~23 % of physical RAM. |
  | TC22 / TC27 / TC53 / TC58 (6–8 GB) | 6–8 GB | 12–16 %. Plausible. |

- **The design point:** the cost is paid on **every cold launch**, whether or not the operator ever
  taps a Gemma button. The comment at `:128-130` already anticipates this ("Remove this call if you
  don't want the model loaded/compiled at every launch") — the finding is that the decision is
  currently unconditional rather than tiered.
- **Secondary defect — a race.** `warmUpGemma4` guards with `if (litertEngine != null) return@launch`
  and `ensureGemma4Engine` (`:812`) re-checks the same field, but neither is synchronised. A launch
  concurrent with a backend toggle (`:851`) can have both coroutines pass the null check before
  either assigns, building **two** engines and leaking one. Derived cost of the race: a second
  0.93 GiB floor.
- **Fix:** gate the warm-up on `ActivityManager.isLowRamDevice` / a measured tier, make it lazy on
  first use, and guard engine construction with a mutex.

---

### F3 · MEM-PROC-001 + MEM-PROC-003 + MEM-LIFECYCLE-003 · **BLOCKER** · Screen-capture service allocates a full-screen bitmap per frame, forever, from the not-visible bucket

- **Where:** `ScreenCaptureService.kt:55-110`.
- **Four compounding defects in one 55-line method:**
  1. **A fresh full-screen `ARGB_8888` bitmap per delivered frame** (`:73-77`), never `recycle()`d.
     Repository-wide `recycle()` count: **0**.
  2. **`stopSelf()` is commented out** (`:83`, `//stopSelf() //needed to get just one screenshot`)
     and the return is `START_STICKY` (`:109`). The service never ends and the system restarts it
     after a kill.
  3. **`imageReader`, `virtualDisplay` and `mediaProjection` live in `MicrosoftActivity`'s companion
     object** (`MicrosoftActivity.kt:93-95`) — process-lifetime static retention of native-backed
     resources. They are released only in `MyMediaProjectionCallback.onStop()` (`:118-125`), and
     nothing in the codebase calls `mediaProjection.stop()`.
  4. **One JPEG file written to `externalCacheDir` per frame** (`:81`) with no cap and no cleanup.

**Derived per-frame cost** (model; `bytes = (W + rowPadding ÷ pixelStride) × H × 4`):

| Screen | Unpadded bytes | MiB/frame | Note |
|---|---|---|---|
| 460 × 460 | 846,400 | **0.81** | WS50 — the code's own comment at `MicrosoftActivity.kt:527` cites `wm size 460x460` |
| 720 × 1280 | 3,686,400 | **3.52** | typical 3–4 GB handheld |
| 1080 × 2160 | 9,331,200 | **8.90** | typical 6–8 GB handheld |
| 1088 × 2160 (stride-padded to 64 px) | 9,400,320 | **8.97** | the padded case the code explicitly handles |

**Allocation rate:** `ImageReader` is constructed with `maxImages = 1`, which throttles in-flight
images to one — but the listener allocates a *new* bitmap on every callback. At a conservative 10
content-change frames/second on a 1080 × 2160 screen that is **≈ 89 MiB/s of allocation churn**,
sustained for as long as the service lives, which is indefinitely. The `ImageReader`'s own native
buffer adds a further one-frame-sized permanent allocation (8.90 MiB at 1080 × 2160) that is never
closed.

- **The Android 17+ multiplier (version-gated).** A foreground service sits in the **not visible**
  state group, which receives the *more restrictive* Memory Limiter budget. This app therefore does
  its single heaviest and most sustained allocation at its **tightest** ceiling. If the fleet reaches
  Android 17, this finding's severity rises further; below 17 the mechanism does not exist and the
  exposure is to the low-memory killer instead. **Fleet spread is unknown, so both are stated rather
  than one being assumed.**
- **Adjacent non-memory defects worth one line each:** `sleep(1000)` on the main thread inside
  `onStartCommand` (`:107`) is an ANR risk; and `START_STICKY` redelivers a **null** intent, which
  `intent!!.getIntExtra` (`:57`) will dereference — a restart crash loop. `MicrosoftActivity.metrics`
  is also `lateinit` and read by the service (`:63`), so a service restart without a live activity
  throws `UninitializedPropertyAccessException`.
- **Fix:** reuse one bitmap (or write straight from the `ByteBuffer`), call `stopSelf()` after the
  intended single capture, move the three handles out of the companion object into the service, and
  release them in `onDestroy`.

---

### F4 · MEM-CACHE-001 + MEM-DATA-005 · HIGH · The output `TextView` grows without bound, and every token re-lays-out the whole buffer

- **Where:** `appendToTextView` (`MicrosoftActivity.kt:219-225`) does `"$currentText\n$text"`;
  the Gemma-4 stream callback (`:902-911`) sets `tvOut.text = prefix + shown` **once per token**;
  `ScrollingMovementMethod` is installed at `:108`. There is no clear, no cap, and no eviction.
- **Why it qualifies as MEM-CACHE-001:** the bound is set by session length, not by code — the same
  test the catalogue applies to a master-data cache.

**Resident tail** (model, Latin-1 `String` at ~1 B/char plus a `StaticLayout` over the full text at
a modelled 6–16 B/char, central estimate 8):

| Session | Chars in buffer | `String` | `StaticLayout` | Resident |
|---|---|---|---|---|
| 20 queries × 1,500 chars | 30,000 | 30 KB | 0.24 MB | **0.27 MB** |
| 200 queries × 1,500 chars | 300,000 | 0.3 MB | 2.4 MB | **2.7 MB** |
| 600 queries (long shift) | 900,000 | 0.9 MB | 7.2 MB | **8.1 MB** |

The resident tail is modest. **The churn is not, and it grows quadratically with session length**,
because each of the ~375 token updates in a response rebuilds the layout over the *entire* buffer:

`churn_per_response ≈ tokens × buffer_chars × bytes_per_char`

| When in the shift | Buffer | Churn for ONE streamed response |
|---|---|---|
| First response | ~1,500 chars | 375 × 1,500 × 8 B ≈ **4.5 MB** |
| After 200 queries | ~300,000 chars | 375 × 300,000 × 8 B ≈ **0.9 GB** |
| After 600 queries | ~900,000 chars | 375 × 900,000 × 8 B ≈ **2.7 GB** |

- **Read this correctly:** that is *transient allocation*, not resident bytes. A collector keeps up
  with churn — at a CPU cost, and with allocation spikes. Under Memory Limiter, allocation spikes are
  precisely what triggers reclaim and throttling. This is the app's clearest **axis-2 ("dies after
  lunch")** exposure, and it is fully derivable from source: the app gets progressively slower and
  more allocation-heavy the longer the session runs, with no change in what the operator is doing.
- **Tolerance:** the 6–16 B/char layout constant is the weakest term here. **Uncalibrated.** The
  quadratic shape is exact regardless of the constant.
- **Fix:** cap the buffer (keep the last N lines), append via `TextView.append()` on a
  `SpannableStringBuilder` rather than rebuilding a `String`, and throttle the stream callback to
  ~10 Hz instead of per token.

---

### F5 · MEM-BITMAP-003 · HIGH (*justify / bound it*, **not** MEM-CACHE-001) · split-picker holds an icon `Drawable` for every installed app across every profile

- **Where:** `split-picker/MainActivity.kt:44` (`private val apps = mutableListOf<AppEntry>()`),
  populated eagerly by `loadApps()` from `onCreate` (`:56`, `:67-91`), across `launcherApps.profiles`,
  each entry holding `icon = info.getBadgedIcon(0)` (`:83`).
- **Why this is NOT reported as MEM-CACHE-001** (anti-pattern AP-05): the catalogue's test is
  *"is the upper bound set by our code or by the customer's data?"*. It is set by neither — it is set
  by the **device's installed-app count**, which the EMM controls. That is a real, knowable,
  enumerable bound. Reporting it as an unbounded cache would be the noise that gets a report
  disabled. It is still worth fixing, because the *magnitude* is material.

**Derived cost.** The `AppEntry` scaffolding is negligible and the icon dominates:

| Component | Arithmetic | Bytes |
|---|---|---|
| `AppEntry` instance | 8 hdr + 5 refs × 4 = 28 → align 8 | 32 |
| `label` String (~14 ASCII) | 16 + 14 = 30 → align 8 | 32 |
| `ComponentName` + its two Strings (~25 + ~35 chars) | 16 + 48 + 56 | 120 |
| `ArrayList` slot | 4 + growth slack | 4 |
| **Non-icon subtotal** | | **≈ 188 B/entry** |

`icon_bytes = (dp × density ÷ 160)² × 4`, at a 480 dpi (xxhdpi) device:

| Icon kind | Side | Bytes | |
|---|---|---|---|
| Legacy launcher icon, 48 dp | 144 px | 82,944 | **81 KiB** |
| Adaptive icon rasterised at its 108 dp canvas | 324 px | 419,904 | **410 KiB** |

| Installed apps | at 81 KiB/icon | at 410 KiB/icon |
|---|---|---|
| 40 | **3.2 MiB** | **16.0 MiB** |
| 80 (typical, with a work profile) | **6.3 MiB** | **32.0 MiB** |
| 150 | **11.9 MiB** | **60.1 MiB** |

- **Tolerance is wide and the reason is specific.** `AdaptiveIconDrawable` keeps its foreground and
  background as *drawables* and only rasterises on draw, so the retained cost sits somewhere between
  the two columns depending on the icon mix on the device. **Uncalibrated model; the 410 KiB column
  overstates for adaptive icons and the 81 KiB column understates for a high-density screen.** One
  `dumpsys meminfo` on a real device with a real app list collapses the band.
- **Also relevant:** `loadApps()` runs synchronously on the main thread with `QUERY_ALL_PACKAGES`
  across all profiles — a startup-latency concern rather than a memory one.
- **Fix:** load icons lazily in `getView` behind a bounded `LruCache` sized from
  `ActivityManager.memoryClass`; hold the `ComponentName` eagerly and the `Drawable` never.

---

### F6 · MEM-BITMAP-001 + MEM-BITMAP-002 · MEDIUM (downgraded from BLOCKER) · `picture.jpg` decoded at full resolution, then scaled with both copies live

- **Where:** `loadPictureFromAssets` (`MicrosoftActivity.kt:940-950`) —
  `BitmapFactory.decodeStream` with no `inSampleSize`, no `inJustDecodeBounds` bounds pass, and no
  `Bitmap.Config`. Called from `:759` and `:869`.
- **Why downgraded:** the catalogue's false-positive clause for MEM-BITMAP-001 is *"decoding a
  known-small bundled asset"*. This asset is bundled and its dimensions are fixed at build time
  (**1600 × 900**, verified from the JPEG SOF marker), so the bound is set by our code, not by a
  camera or a network. It is not *small*, though, so it is reported rather than cleared.

| Step | Arithmetic | Bytes | MiB |
|---|---|---|---|
| Full decode, ARGB_8888 default | 1600 × 900 × 4 | 5,760,000 | **5.49** |
| `downscaleForVision(maxEdge = 512)` result | 512 × 288 × 4 | 589,824 | **0.56** |
| **Both live at once** — `original` is still referenced at `:874` while `createScaledBitmap` returns | | 6,349,824 | **6.06** |
| JPEG re-encode at q90 + `toByteArray()` copy | ≈ 2 × 150 KB | ~300,000 | ~0.29 |
| **Transient peak** | Σ, because these genuinely overlap | | **≈ 6.35** |

**`Σ` is used here rather than `max`, and the justification is source-derivable:** at
`MicrosoftActivity.kt:874-880` the scaled bitmap, the original, and the encode buffer are all
reachable in the same expression sequence.

- **Two cheap fixes, quantified:**
  - `RGB_565` — the asset is opaque 3-component photographic content, so transparency is not needed:
    5.49 → **2.75 MiB**, exactly half.
  - `inSampleSize = 2` before the scale (800 × 450 is still ≥ the 512 target edge):
    peak 6.06 → **1.94 MiB**, a 68 % reduction with no change to the encoder input.
- **What is already right:** `downscaleForVision` (`:803-808`) and its comment about patch count are
  a genuine, well-reasoned optimisation. The gap is only that it operates *after* a full-resolution
  decode instead of instead of one.

---

### F7 · MEM-CACHE-001 (KV cache sizing) · MEDIUM–HIGH · `setMaxTokens(1024)` allocates ~104 MiB of **anonymous** KV cache

- **Where:** `initializeGemmaForImages` (`MicrosoftActivity.kt:634`) sets `.setMaxTokens(1024)`;
  `initializeGemma` (`:614`) sets `.setMaxTokens(128)`.
- **Why this is called out separately from F1:** unlike the weights, KV cache is **certainly
  anonymous** — it is written at runtime and cannot be mmap'd from a file. Anonymous bytes are the
  ones `MemoryLimiter:AnonSwap` is named for; they can be compressed into zRAM but never evicted.

`kv_bytes = 2 (K and V) × layers × kv_heads × head_dim × max_tokens × bytes_per_element`

Substituting a Gemma-2-2B-shaped configuration (`layers = 26`, `kv_heads = 4`, `head_dim = 256`,
fp16 → 2 B):

| `maxTokens` | Arithmetic | Bytes | |
|---|---|---|---|
| per token | 2 × 26 × 4 × 256 × 2 | 106,496 | **104 KiB/token** |
| 128 (`initializeGemma`) | 104 KiB × 128 | 13,631,488 | **13.0 MiB** |
| 1024 (`initializeGemmaForImages`) | 104 KiB × 1024 | 109,051,904 | **104 MiB** |

- **The architecture constants are the uncalibrated term.** `layers`, `kv_heads` and `head_dim` must
  be confirmed against each model's actual card; substituting different values changes the constant
  but not the **exact linearity in `maxTokens`**, which is the part that forecasts. Raising
  `maxTokens` from 128 to 1024 costs 8× the anonymous KV cache, deterministically.
- **Fix:** set `maxTokens` from the longest prompt the feature actually needs, not from a round
  number, and state the derived KV cost next to it in a comment.

---

### F8 · MEM-OBS-001 · HIGH · No memory-limit kill detection anywhere

- **Evidence:** `ApplicationExitInfo` occurs **0 times** in the repository.
- **Why it matters here more than usual:** this app's dominant risk (F1, F2, F3) is precisely the
  untraceable kind. A `MemoryLimiter:AnonSwap` kill carries **no stack trace** — it surfaces as
  `REASON_OTHER` with the description string, and every crash dashboard shows nothing at all. Given
  a multi-GB model on an unknown tier, being blind to this failure is the single largest
  observability gap.
- **Availability:** `minSdk` is 29, so the call needs an API-30 guard. Zebra's published support
  range starts at Android 11, so the mechanism is available across the fleet in practice.
- **Fix:** read historical exit reasons on startup, inspect `getReason()` and `getDescription()`,
  and report memory-limit kills as a first-class telemetry event.

---

### F9 · MEM-BUILD-002 · HIGH · R8 disabled on every release build type

- **Evidence:** `isMinifyEnabled = false` in the **release** block of `msft/build.gradle.kts:22`,
  `split-picker/build.gradle.kts:20` — and in the three excluded modules too. `isShrinkResources` is
  absent everywhere.
- **These are release-shaped build types**, so the false-positive clause (debug/internal variants)
  does not apply. There are no other build types declared.
- **Partly right already:** all modules reference `proguard-android-optimize.txt` rather than the
  legacy file, and `android.enableR8.fullMode = false` is **not** present (verified: 0 occurrences),
  so full mode is on by AGP 8 default. Both are inert while `isMinifyEnabled = false`.
- **Why this is a memory finding, not a download-size one:** code footprint is resident memory. With
  three inference stacks linked in (§1.3) and `tasks-vision` pulled in for a **single class**
  (`BitmapImageBuilder`, used once at `:767`), the unshrunk class and resource surface is large.
- **Magnitude:** not derivable without a build, and the project **cannot currently build** (§1.1).
  Stated as unquantified rather than estimated.

---

### F10 · MEM-DEP-002 · MEDIUM · Two on-device inference runtimes plus a cloud one, in one process

- **Where:** `msft/build.gradle.kts:59-70`.
- **Overlaps:** MediaPipe `tasks-genai` 0.10.35 and LiteRT-LM `litertlm-android` 0.14.0 are both
  complete on-device LLM runtimes, each with its own native library set and its own model loader.
  `tasks-vision` is pinned at **0.20230731** — a 2023 build alongside a 2025-era `tasks-genai` —
  which is a likely source of duplicated MediaPipe framework classes and native libraries.
  `litert-api` 2.1.6 and `generativeai` 0.9.0 add a third and fourth path.
- **The code's own comment explains why both are present** (`:67-70`): MediaPipe's vision executor
  rejects Gemma 4's multi-signature encoder, so LiteRT-LM was added. That is a **business-required
  reason**, so this is routed as *justify*, not *remove* — but the MediaPipe path (F1) is now
  redundant for the vision use case and could be deleted rather than kept as four extra buttons.
- **Magnitude:** requires APK analysis. Blocked by F0/§1.1.

---

### F11 · MEM-PRESSURE-002 · MEDIUM · No memory-pressure handling of any kind

- **Evidence:** `onTrimMemory` — **0 occurrences**. `onLowMemory` — **0 occurrences**.
- **There is plenty that is releasable:** the LLM engines, the decoded bitmaps, the icon list, the
  `TextView` buffer. The app currently releases none of it under pressure.
- **Good news, and it is genuinely good:** because there is no legacy trim code at all,
  **MEM-PRESSURE-001 does not apply and carries no false-positive risk** — there are no
  `TRIM_MEMORY_RUNNING_*` branches to mistakenly delete from devices where they are still live. This
  is one place where the unknown fleet OS spread costs nothing.
- **Fix:** handle the two levels that are still delivered — `TRIM_MEMORY_UI_HIDDEN` and
  `TRIM_MEMORY_BACKGROUND` — with a threshold comparison, and close the LLM engine there.

---

### F12 · MEM-LIFECYCLE-004 · MEDIUM (construction), LOW (bytes) · An infinite coroutine is launched per button tap

- **Where:** `setSB_ShopID` (`MicrosoftActivity.kt:1378-1384`):
  ```kotlin
  lifecycleScope.launch { while (true) { delay(1000L); println("Periodic task running") } }
  ```
  Reached from `onClickbtn_LAUNCH_Z_FOO` (`:1267`).
- **Behaviour:** every tap starts another never-terminating coroutine. `lifecycleScope` cancels them
  at `onDestroy`, so this is not a process-lifetime leak — but within one activity session the count
  is unbounded, each one wakes the scheduler every second, and each one writes to stdout.
- **Honest magnitude:** a continuation plus a delayed resume task is on the order of a few hundred
  bytes. **Twenty taps is single-digit kilobytes.** The byte cost is negligible and saying otherwise
  would be exactly the noise AP-05 warns about. It is reported because the construction is unbounded
  and the wake-ups are pure waste, not because of the bytes.
- **Fix:** hold the `Job` and cancel the previous one, or use a `repeatOnLifecycle` block.

---

### F13 · MEM-BUILD-005 · MEDIUM · AGP 8.5.0 predates default 16 KB page alignment — **partly vendor-owned**

- **Evidence:** `agp = "8.5.0"` (`libs.versions.toml:2`); 16 KB alignment became the AGP default at
  **8.5.1**. Native code is present via MediaPipe, LiteRT and LiteRT-LM.
- **The developer-actionable half:** bump AGP to ≥ 8.5.1 so the packaging step aligns shared
  libraries.
- **The vendor-owned half (AP-08):** the `.so` files themselves ship inside third-party archives that
  cannot be recompiled here. Whether MediaPipe 0.10.35, `tasks-vision` 0.20230731 and
  `litertlm-android` 0.14.0 ship 16 KB-aligned libraries is **a question for those vendors**, not a
  defect in this codebase. The 2023-vintage `tasks-vision` pin is the most likely to be unaligned.
- **`extractNativeLibs` is clean:** 0 occurrences, so the AGP default (`false` for `minSdk` ≥ 23)
  applies — segments stay mapped from the archive as clean, shareable, file-backed pages. That is the
  correct configuration and no change is needed.
- **Framing, per the catalogue:** the primary reason is **compatibility** on 16 KB-page devices, not
  memory saving. Page size is the device's choice. Scope: Android 15+, and **the fleet spread is
  unknown**, so applicability is unconfirmed.

---

### F14 · MEM-OBS-002 + MEM-OBS-005 · MEDIUM · No leak detection and no memory regression gate

- **Evidence:** no debug-only leak-detection dependency (`leakcanary`: 0 occurrences); no CI
  configuration of any kind; no test sources; no stored baseline to compare against.
- **Consequence:** growth between releases is completely unobservable. Given F3 and F4 — two findings
  whose whole character is accumulation — the absence of an accumulation harness is the gap that
  matters most after F0.
- **Fix:** a debug-only leak detector; and an RSS delta (not PSS) gate in CI against a stored
  baseline. Emulator CI is acceptable **for deltas only** and must be labelled as such — it cannot
  validate headroom.

---

### F15 · MEM-DATA-001 · LOW · Whole-file asset reads

- **Where:** `loadBinaryFileFromAssets` (`MicrosoftActivity.kt:163`, `NDZLHostApduService.kt:125`)
  uses `inputStream.readBytes()`; `readFileFromAssets` (`:953`) uses `bufferedReader().readText()`.
- **All three payloads are bundled and code-bounded**, which is the catalogue's stated false-positive
  gate — none scales with customer data. Two are cleared outright; one is worth a line:

| Asset | Read as | Resident | Transient peak |
|---|---|---|---|
| `ONE.bin` | `ByteArray` | 86 B | 86 B — **cleared** |
| `STAGENOW_DATETIME2FEB.bin` | `ByteArray`, per APDU call | 144 B | 144 B — **cleared** |
| `AIDCSDK-OCR.txt` | `String` → system instruction | ≈ 115 KiB (two copies, one retained by `generativeModelWithInstructions`) | ≈ 290 KiB (`StringBuilder` `char[]` at 2 B/char with 1.5× growth slack: 58,892 × 2 × 1.5 ≈ 173 KiB) |

- **The scanner's hit at `MicrosoftActivity.kt:73` is the `import java.io.ByteArrayOutputStream`
  line — a false positive, cleared.**

---

### F16 · MEM-STATE-001 · LOW / informational · No saved state, and shared state lives in a static

- **Evidence:** `onSaveInstanceState` — 0 occurrences. `SavedStateHandle` — 0 occurrences.
  `nfcAction` is a `var` in a companion object (`:97`) and is the sole coordination channel between
  `MicrosoftActivity` and `NDZLHostApduService`. `latestBitmap` (`:1000`) is a plain field, and is
  nulled only on the Gemini success path (`:1034`) — an exception leaves it held.
- **Kept at LOW deliberately.** This is a developer test harness, not a multi-step operator workflow;
  there is no "40 minutes of scans" to lose. Escalating it would be severity inflation. It is
  recorded so that the judgement is visible rather than absent.
- **MEM-STATE-002 is clean:** nothing is written to the saved-state bundle.

---

## 4. Growth axes

| Axis | Grows with | Derivable here? | This app's exposure |
|---|---|---|---|
| **1 · Data** | customer records | n/a | **No data layer at all.** The `n` terms are *installed apps* (F5) and *screen pixels* (F3) — both device properties, both knowable from the device rather than from the business. |
| **2 · Session** | work done | risk yes, rate no | **The dominant axis.** F4 is quadratic in session length; F3 is linear in frames captured; F1 is a step function per button tap. |
| **3 · Release** | commits | absence of a gate, yes | No CI, no baseline, no tests (F0, F14). Completely unobservable. |
| **4 · Platform** | OS upgrades | version gates yes, schedule no | F3 gets materially worse at Android 17 (not-visible bucket, tighter limit). F13 applies from Android 15. **Fleet schedule not supplied**, so no red-band percentage is computed. |
| **5 · Constraint** | procurement | no | **This is the decisive one and it is unresolved.** F1 and F2 are comfortable at 8 GB and impossible at 1 GB. The tier answer changes the verdict more than any code change would. |

**Crossing points, expressed so they survive a changing business:**

| Structure | Crossing point |
|---|---|
| F2 warm-up | Fails at any tier where 0.93 GiB + kernel + Zebra stack + EMM exceeds physical RAM — **certain at 1 GB, plausible at 3 GB** |
| F1 cumulative | Fails after the **2nd distinct Gemma button tap** on a 3 GB device (1.00 + 2.00 GB floor), the **4th** at 8 GB |
| F5 icon list | Exceeds 32 MiB at **80 installed apps** if icons rasterise at 108 dp |
| F3 frame loop | Sustained ≈ 89 MiB/s allocation churn at 1080 × 2160 from the moment the service starts |
| F4 text buffer | Churn per response exceeds 1 GB at **≈ 220 accumulated queries** |

---

## 5. Prioritised plan (ordered by derived magnitude)

This section is the `memory-plan.md` payload. It is an accelerator for
`appquality-memory-migration-android`, never a dependency — that skill also accepts developer-stated
steps. **Nothing in the target project was modified.**

| # | Rule ID(s) | Sev | File : line | Derived cost | Fix | Verification |
|---|---|---|---|---|---|---|
| 0 | MEM-OBS-004 | HIGH | repo-wide | 0 B — gates every figure below | Commit one baseline tuple per tier | §7 run once |
| 1 | MEM-CACHE-001, MEM-DEP-002 | **BLOCKER** | `MicrosoftActivity.kt:601, 626` | 5.27 GB floor if all four buttons tapped | `close()` prior engine + session; close in `onDestroy`; make buttons exclusive | `RssAnon`/`RssFile` before and after each tap |
| 2 | MEM-PRESSURE-003 | **BLOCKER** | `MicrosoftActivity.kt:130, 788, 812` | 0.93 GiB floor at **every** launch; ×2 under the race | Gate on `isLowRamDevice`/tier; make lazy; mutex the engine build | `RssAnon` at launch, low tier vs high tier |
| 3 | MEM-PROC-001/003, MEM-LIFECYCLE-003 | **BLOCKER** | `ScreenCaptureService.kt:55-110`; `MicrosoftActivity.kt:93-95` | 0.81–8.97 MiB/frame, unbounded rate; ≈ 89 MiB/s at 1080×2160 | Reuse one bitmap; `stopSelf()`; move handles out of the companion; release in `onDestroy` | `RssAnon` slope over 60 s of capture, **sampled in the not-visible state** |
| 4 | MEM-CACHE-001, MEM-DATA-005 | HIGH | `MicrosoftActivity.kt:219, 902-911` | 2.7 MiB resident @200 queries; ≈ 0.9 GB churn/response there | Cap the buffer; `append()` on a `SpannableStringBuilder`; throttle to ~10 Hz | `RssAnon` per 50-query block |
| 5 | MEM-BITMAP-003 | HIGH | `split-picker/MainActivity.kt:44, 83` | 3.2–60.1 MiB by installed-app count | Lazy icons in `getView` behind an `LruCache` sized from `memoryClass` | `dumpsys meminfo` with a real app list |
| 6 | MEM-OBS-001 | HIGH | absent | 0 B — but this is the only trail for #1–#3 | Read `ApplicationExitInfo` on startup, guarded at API 30 | `dumpsys activity exit-info` |
| 7 | MEM-BUILD-002 | HIGH | `msft:22`, `split-picker:20` | Unquantified — no build possible | `isMinifyEnabled = true`, `isShrinkResources = true` | APK diff, once the build is fixed |
| 8 | MEM-CACHE-001 (KV) | MED–HIGH | `MicrosoftActivity.kt:634` | 104 MiB **anonymous** at 1024 tokens | Set `maxTokens` from real prompt length | `RssAnon` at 128 vs 1024 |
| 9 | MEM-BITMAP-001/002 | MEDIUM | `MicrosoftActivity.kt:940-950` | 6.35 MiB transient peak → 1.94 MiB | `inSampleSize` bounds pass; `RGB_565` | Peak `RssAnon` across one describe |
| 10 | MEM-PRESSURE-002 | MEDIUM | absent | Releasable memory currently never released | Handle `UI_HIDDEN` + `BACKGROUND`; close the engine there | `RssAnon` after Home |
| 11 | MEM-LIFECYCLE-004 | MEDIUM | `MicrosoftActivity.kt:1378` | ~hundreds of B/tap — **negligible bytes**, unbounded count | Hold and cancel the `Job` | Coroutine count |
| 12 | MEM-BUILD-005 | MEDIUM | `libs.versions.toml:2` | Compatibility, not memory | AGP ≥ 8.5.1 **+ raise alignment with the MediaPipe/LiteRT vendors** | `check-elf-alignment.sh` on the APK |
| 13 | MEM-DEP-002 | MEDIUM | `msft/build.gradle.kts:59-70` | Unquantified — no build possible | Drop the MediaPipe LLM path now that LiteRT-LM covers vision | APK method/lib count |
| 14 | MEM-OBS-002/005 | MEDIUM | absent | 0 B — makes #3 and #4 unobservable | Debug leak detector; RSS delta gate in CI | CI run |
| 15 | MEM-DATA-001 | LOW | `MicrosoftActivity.kt:953` | 115 KiB resident / 290 KiB transient | Stream if the file ever grows | — |
| 16 | MEM-STATE-001 | LOW | `MicrosoftActivity.kt:97, 1000` | n/a | Informational only | — |
| 17 | MEM-OBS-003 | LOW | absent | Advisory; Android 15+ only, **fleet unknown** | `ProfilingManager` OOM/anomaly triggers | — |

---

## 6. Cleared — checked and found not to apply

Recording these matters as much as the findings: a report that only lists problems gives no signal
about coverage, and one that cries wolf gets disabled.

| Rule | Result |
|---|---|
| **MEM-BUILD-001** `android:largeHeap` | **0 occurrences.** Clean — and notably so, given the model sizes involved. |
| **MEM-BUILD-003** over-broad keep rules | All four `proguard-rules.pro` files are the unmodified template; no `-dontoptimize`/`-dontshrink`/`-dontobfuscate`, no wildcard keeps. Clean. |
| **MEM-BUILD-005** `extractNativeLibs` | **0 occurrences** → AGP default `false` applies. Correct as-is. Only the alignment half of the rule survives (F13). |
| **MEM-PRESSURE-001** dead trim levels | **0 occurrences** of any `TRIM_MEMORY_*` branch. Not applicable, and no risk of wrongly stripping branches still live on Android ≤ 13. |
| **MEM-STATE-002** oversized bundle | Nothing written to saved state. Clean. |
| **MEM-LIFECYCLE-001** in `skills-tester` | Scanner flagged `MainActivity.kt:58`. **False positive** — `registerScanReceiver()` in `onResume` pairs with `unregisterReceiver` in `onPause`. Symmetric. Cleared. |
| **MEM-LIFECYCLE-002** view binding | No view binding used; `findViewById` throughout. Not applicable. |
| **MEM-DATA-001** at `MicrosoftActivity.kt:73` | The `import java.io.ByteArrayOutputStream` line. **False positive.** Cleared. |
| **MEM-CACHE-001** at `split-picker/MainActivity.kt:44` | Re-classified. The bound is the device's installed-app count, not customer data — fails the catalogue's bound test. Re-routed to F5 as MEM-BITMAP-003. |
| **MEM-CACHE-002** image cache default size | No image-loading library present. Not applicable. |
| **MEM-DATA-002/003/004** queries, projections, cursors | **No database and no cursors anywhere.** Not applicable. |
| **MEM-COMPOSE-001/002** | Compose exists only in `:zfindit`, which is not in the build. Not applicable to the shipped surface. |
| **MEM-DEP-001** reflection DI | No DI container. Clean. |
| **MEM-PROC-002** extra processes | No `android:process` attribute. Clean within each app. |
| Findings in `crawler/`, `aidcsdk-cloud-index-generator/`, `zfindit/` | **Not in `settings.gradle.kts`.** Outside the shipped surface; excluded from the fix list. |

### Not a memory defect — one diagnosis worth recording

`MicrosoftActivity.kt:691` carries the comment `//AIDC CONTEXT CRASHES THE APP!` next to a
disabled call to `askGemmaPromptWithAIDCContext`. **This is very unlikely to be a memory failure and
should not be chased as one.** `AIDCSDK-OCR.txt` is 58,892 characters ≈ **14,700 tokens** at ~4
chars/token, fed to an `LlmInference` configured with `setMaxTokens(128)` (`:614`). That is a token
budget overflow of roughly 115×, not an allocation failure. `knowledgeBase` is currently hardcoded to
`""` at `:605` and `:628`, so the path is inert either way. Chasing this as an OOM would burn effort
on the wrong ceiling — the classic unit-confusion failure (AP-02).

---

## 7. To close the model — exact commands and questions

**Everything below is read-only.** `adb` is present at
`C:\Users\CXNT48\AppData\Local\Android\Sdk\platform-tools\adb.exe`, but `adb devices` returned an
**empty list** at assessment time, so none of this was run. Device access was authorised; no device
was attached.

### 7.1 The one measurement that matters most

F1 and F2 hinge entirely on whether model weights are mmap'd (clean, file-backed, droppable) or
anonymous (compressed into zRAM, never free). This is not decidable from source and it is the
difference between *slow* and *killed*:

```bash
PKG=com.ndzl.msft
PID=$(adb shell pidof -s $PKG)
adb shell "grep -E 'VmRSS|RssAnon|RssFile|RssShmem|VmSwap' /proc/$PID/status"
#   RssAnon  <- if the model shows up here, F1/F2 are lethal on every tier below 8 GB
#   RssFile  <- if it shows up here, they are a degradation risk, not a kill risk
```

Sample three times: (a) immediately after launch with the warm-up complete, (b) after tapping one
Gemma button, (c) after tapping all four. The deltas answer F1 without any modelling at all.

### 7.2 Ceilings

```bash
adb shell am memory-limiter status                        # Android 17+ only
adb shell cat /vendor/etc/memory-limiter-config.xml       # Zebra's values, not Google's
adb shell getprop dalvik.vm.heapgrowthlimit               # governs OutOfMemoryError only
adb shell getprop dalvik.vm.heapsize
adb shell getprop ro.config.low_ram
adb shell cat /proc/meminfo | head -3                     # MemAvailable is the one that matters
adb shell "find /sys/fs/cgroup -name 'memory.high' -path '*uid*' 2>/dev/null | head"
```

If `/vendor/etc/memory-limiter-config.xml` is missing, unreadable or invalid, **Memory Limiter is
disabled on that SKU** — record that, because it changes the risk picture per device.

### 7.3 Model file sizes — closes the F1/F7 constants directly

```bash
adb shell ls -l /data/local/tmp/*.litertlm /data/local/tmp/*.task /data/local/tmp/llm/*.bin
```

This replaces every derived weight floor in F1 and F2 with a measured number in one command. It is
the cheapest calibration available and should be run first.

### 7.4 Attribution, kill forensics, and the accumulation rate

```bash
adb shell dumpsys meminfo | head -40          # what the Zebra stack costs BEFORE this app starts
adb shell dumpsys meminfo $PKG                # Activities:/Views: growth == leak indicator
adb shell dumpsys activity exit-info $PKG     # REASON_OTHER + "MemoryLimiter:AnonSwap"

# Axis 2 — F3 and F4 accumulation. Press Home first to sample the NOT-VISIBLE state.
for i in $(seq 1 30); do
  adb shell "grep RssAnon /proc/$PID/status"
done
```

Discard the first ~5 samples for warm-up and fit a line; the slope is the `L` term.
`transactions-to-kill = (C − F₀) / L`.

### 7.5 Business and fleet questions that no command answers

1. **Which Zebra SKUs is this deployed to?** Still unanswered. It decides whether F1/F2 are tuning
   or a redesign. Design against the **lowest** tier in the fleet.
2. **What is the fleet's Android version spread and upgrade schedule?** Gates F3's severity
   (Memory Limiter, Android 17+) and F13's applicability (16 KB pages, Android 15+).
3. **How does the model file reach `/data/local/tmp/` in production?** An app cannot write there on a
   managed device. If the answer is "it does not", F1/F2 are dev-only and their field severity drops
   to zero — a possibility this report cannot resolve and will not assume either way.
4. **Typical installed-app count on the target device**, including the work profile. Collapses F5's
   3.2 MiB–60.1 MiB band to a single figure.

---

## 8. Not checked, and why

| Not checked | Reason |
|---|---|
| Absolute footprint, headroom, headroom band | No device attached. Reporting a band against an unread ceiling is AP-01. |
| Compiled code footprint, duplicate native libraries, APK composition | **The project cannot build** — `:skills-tester` is in `settings.gradle.kts` with no `build.gradle.kts`, no manifest and a missing layout. Blocks F9 and F10 quantification. |
| Whether MediaPipe/LiteRT `.so` files are 16 KB-aligned | Requires an APK. Vendor-owned regardless (AP-08). |
| Runtime leak behaviour | No leak-detection harness and no test sources exist. |
| Model weight `RssAnon`/`RssFile` split | Not decidable from source. §7.1. |
| Actual model file sizes | Files live on a device at `/data/local/tmp/`. §7.3. |
| `crawler`, `aidcsdk-cloud-index-generator`, `zfindit`, `focus-timer` | Not in the build. Scanned, then excluded from the fix list. |
| Gemma model architecture constants (`layers`, `kv_heads`, `head_dim`) | Assumed Gemma-2-2B-shaped in F7. Confirm per model card; the linearity in `maxTokens` holds regardless. |
| APK/download size, DataWedge configuration, responsive layout, CPU, battery, jank | Out of this skill's scope. |

**Out of scope but observed, stated once:** a Google Gemini API key is hardcoded in cleartext at
`MicrosoftActivity.kt:568` and `:962` and is committed to a public repository. That is a security
matter, not a memory one, and belongs to whoever owns credential hygiene for this project — but it
would be wrong to have seen it and said nothing.

---

## 9. Achieved validation level

> ### **Inspection**
>
> Files read, catalogue applied, costs derived. **No footprint was measured.**

| Level | Status |
|---|---|
| **Inspection** | ✅ **Achieved.** 8 modules mapped; 3 identified as shipped. Full catalogue applied — 17 findings, 14 rules cleared, 3 scanner false positives identified and dismissed. Every quantity carries its derivation. |
| Build and behavior | ❌ **Blocked.** The project cannot configure (`:skills-tester`). No build, no instrumented iteration protocol, no leak rate. |
| Device | ❌ **Not achieved.** Device access authorised, but `adb devices` was empty at assessment time. §7 is ready to run. |
| Production review | ❌ **Not achieved.** No accountable owner has reviewed this for security, licensing or deployment. |

### Limitations that apply to every number above

1. **Static analysis cannot measure a footprint.** It derives the function that produces one and
   names which term dominates.
2. **Every figure here is a *derived model*, not a measurement.** Object-sizing models are accurate
   to roughly **±2× on absolute bytes** and **exact on the scaling exponent**. None has been
   calibrated against a heap dump on this codebase. Where the error has a known direction — the F1
   weight floors understate; the F5 icon band brackets rather than centres — it is stated at the
   finding.
3. **No per-device memory threshold is published for any Zebra RAM tier.** The enforced values live
   in the device's vendor partition. This report deliberately computes no headroom.
4. **Units are kept separate throughout.** Java heap governs `OutOfMemoryError`; cgroup anon+swap
   governs `MemoryLimiter:AnonSwap`; PSS is proportional attribution and enforces nothing. Where a
   figure could be either — the model weights in F1 — that ambiguity is named rather than resolved by
   guessing.
5. **The device tier remains unknown**, and it is the term that would change the verdict most. F1 and
   F2 are a tuning exercise at 8 GB and a redesign at 1 GB.

---

*Generated by `appquality-memory-assessment-android` v1.0.0-beta.1 (Beta — validate before
production use). This skill is read-only; no file in the target project was created, modified or
deleted. To act on §5, hand off to `appquality-memory-migration-android`.*
