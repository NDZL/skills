# CPU and performance — a low-tier, all-little-core budget

> **Provenance.** The WS501 spec sheet states the processor as `"Qualcomm® QC2290"`
> (`device-matrix.md` §2.2). Qualcomm's published figures for the IoT part of that number
> (QCM2290 / QCS2290) are quoted in §1. **The WS50's CPU is not stated in any source consulted —
> it is `UNVERIFIED`.** The display refresh rate is `UNVERIFIED` for both devices; §2 assumes
> 60 Hz and tells you how to confirm it. **The frame-budget split in §2 and the A53-vs-phone-core
> ratios in §1.2 are engineering estimates, not published figures** — measure your own app.
>
> **Sources for this file** (full register: `device-matrix.md` §7):
> - **Z4** WS501 spec sheet — `"Qualcomm® QC2290"`
>   https://www.zebra.com/us/en/products/spec-sheets/mobile-computers/wearable/ws501.html
> - **Q1** Qualcomm QCM2290 — quad-core Cortex-A53 up to 2.0 GHz, Adreno 702
>   https://www.qualcomm.com/internet-of-things/products/q2-series/qcm2290
> - **Q2** QCS/QCM2290 SoC product brief (PDF) — Adreno 702 @ 845 MHz, OpenGL ES 3.1 / OpenCL 2.0 /
>   Vulkan 1.1
>   https://www.qualcomm.com/content/dam/qcomm-martech/dm-assets/documents/qcs-qcm2290-soc-product-brief_87-28731-1.pdf
> - **Q3** QCS2290 — https://qualcomm.com/products/technology/processors/application-processors/qcs2290
> - **Z1** WS50 Programmer's Guide — `ConstraintLayout` for performance, "Minimize animations",
>   camera API2 + supported formats
>   https://techdocs.zebra.com/emdk-for-android/13-0/guide/ws50_programming/
> - **W1** Conserve power — high CPU ranked **High**; "Consume flows using Jetpack Compose"; batch
>   to maximise idle; `dumpsys` commands; Perfetto
>   https://developer.android.com/training/wearables/apps/power
> - Baseline Profiles — https://developer.android.com/topic/performance/baselineprofiles/overview
> - Macrobenchmark — https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview
> - Perfetto UI — https://ui.perfetto.dev
>
> ⚠ **The `QC2290` → `QCM2290` mapping is an inference**, not a confirmed identity — see
> `device-matrix.md` §7.5 item 4. Confirm with `/proc/cpuinfo` (§1.3) before depending on a GPU API
> level.

---

## 1. What "low-tier CPU" concretely means here

### 1.1 The WS501's silicon

Zebra names `"Qualcomm® QC2290"`. Qualcomm's IoT part carrying that number is the **QCM2290**
(with a QCS2290 sibling), published as:

| | QCM2290 / QCS2290 |
|---|---|
| CPU | **Quad-core Arm Cortex-A53, up to 2.0 GHz** |
| GPU | **Adreno 702 @ 845 MHz** |
| Graphics APIs | OpenGL ES 3.1, OpenCL 2.0, Vulkan 1.1 |
| Positioning | entry tier, industrial IoT / handhelds |

> The spec sheet's `"QC2290"` is not an exact Qualcomm part name; **QCM2290** is the IoT part with
> that number and the mapping is highly likely — but confirm on device (§1.3) before you depend on
> a GPU API level.

### 1.2 The consequence that actually matters: **there are no big cores**

Four Cortex-A53s is a **homogeneous, all-little-core** design. A phone SoC gives you one or two
wide out-of-order cores to absorb a badly-threaded startup path or a heavy main-thread frame. This
gives you none.

Cortex-A53 is a **short, in-order** pipeline. In practice:

- **Single-thread performance is your hard ceiling**, and it is low. There is no fast core to
  schedule the UI thread onto.
- **Nothing rescues a slow main thread.** On a phone the scheduler migrates your foreground work to
  a big core and the jank disappears. Here, that mechanism does not exist.
- **Branch-heavy, allocation-heavy, interpreted code hurts disproportionately** — in-order cores
  stall where out-of-order cores hide the latency. This is exactly why **Baseline Profiles**
  (`toolkit-choice.md` §4.2) matter more on this hardware than on a phone.
- **Parallelism helps only if the work is genuinely parallel.** Four A53s are decent for four
  independent background tasks and useless for one slow serial one.

The design rule that follows: **the main thread does layout and nothing else.** Every phone habit
of "this is only 20 ms, it's fine on the UI thread" is wrong here — 20 ms on a modern phone core is
comfortably 60–100 ms on an A53, which is 4–6 dropped frames.

### 1.3 Read it off the device

```bash
adb shell cat /proc/cpuinfo                        # core list, part numbers
adb shell cat /sys/devices/system/cpu/present      # e.g. "0-3" => 4 cores
adb shell cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq   # kHz
adb shell getprop ro.hardware
adb shell getprop ro.board.platform

# refresh rate (the frame budget in §2 depends on it)
adb shell dumpsys display | grep -i -E 'fps|refresh'
```

---

## 2. The frame budget

Assume **60 Hz until measured** — that is **16.67 ms per frame** for everything: input, your
`onDraw`/recomposition, layout, and the system's compositing. Confirm with the `dumpsys display`
command above; if the panel is 60 Hz the budget is fixed and non-negotiable.

Practical split on an A53:

```
16.67 ms total
 ├─ ~2-4 ms   system compositing / SurfaceFlinger  (not yours)
 ├─ ~2-3 ms   measure + layout                     (yours — keep the tree flat)
 ├─ ~3-5 ms   draw / record                        (yours — keep overdraw down)
 └─ the rest  your callbacks and state updates     (yours — should be ~0)
```

**Your realistic main-thread budget is single-digit milliseconds.** Anything with an unbounded
input size — a loop over a result set, a JSON parse, a regex over a long string, a `SimpleDateFormat`
construction — does not belong on it.

### 2.1 Measure jank properly

```bash
PKG=com.example.smallscreen

# Reset, exercise the app's main interaction, then read
adb shell dumpsys gfxinfo $PKG reset
#   ... perform the workflow on the device ...
adb shell dumpsys gfxinfo $PKG
```

Read these lines:

```
Total frames rendered: N
Janky frames: J (x.xx%)          ← target < 1% for the primary workflow
50th percentile: xx ms
90th percentile: xx ms           ← should be under the frame budget
95th percentile: xx ms
99th percentile: xx ms
Number Missed Vsync: n           ← main thread was too slow
Number High input latency: n     ← input handling too slow
Number Slow UI thread: n         ← your code
Number Slow bitmap uploads: n    ← textures too large; see memory.md §3
Number Slow issue draw commands: n
```

`Number Slow UI thread` is the one you own outright. `Slow bitmap uploads` almost always means an
oversized image and is fixed in `memory.md` §3.2, not here.

For per-frame detail use **Perfetto** (`ui.perfetto.dev`) — Google's Wear guidance recommends it
specifically for inspecting what runs while the screen is off or in ambient mode.

### 2.2 Regression-test performance, don't just measure once

`androidx.benchmark` Macrobenchmark gives you a startup and jank number you can gate in CI:

```kotlin
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test
    fun coldStart() = rule.measureRepeated(
        packageName = "com.example.smallscreen",
        metrics = listOf(StartupTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.COLD,
    ) {
        pressHome()
        startActivityAndWait()
    }
}
```

Run it **on the target device**. A benchmark on an emulator or a developer's phone tells you
nothing about an A53.

---

## 3. Keep the view hierarchy flat

Zebra's own recommendation: use **`ConstraintLayout` for better performance**. The reason is
measure cost, and it compounds on a slow core.

- **Target ≤ 4 levels of nesting.** Every level is another measure/layout traversal.
- **Nested weighted `LinearLayout` measures its children twice — per level.** Two levels of nesting
  is four passes; three is eight. This is the single most common self-inflicted jank source in
  XML layouts.
- **`RelativeLayout` always measures twice** by design. Avoid it for anything non-trivial.
- **`merge`** to remove redundant wrapper groups from included layouts.
- **`ViewStub`** for anything not on the first frame.

```bash
# Count your actual depth and view count
adb shell dumpsys activity top | grep -c "View"
# Better: Android Studio > Layout Inspector, check the tree depth directly
```

### 3.1 Overdraw

The GPU here is an Adreno 702 — adequate, but there is no reason to make it composite the same
pixel four times.

```bash
adb shell setprop debug.hwui.overdraw show
# on device: blue = 1x (fine), green = 2x, light red = 3x, dark red = 4x+ (fix it)
adb shell setprop debug.hwui.overdraw false
```

The usual fix on this form factor is free: **the window background is already true black
(`screen-layout.md` §10), so remove per-view backgrounds that just repaint it.**

```kotlin
// If your theme sets the window background, strip the redundant one
window.setBackgroundDrawable(null)
```

---

## 4. Main-thread discipline

**Turn on StrictMode in debug and let it shout.** On an A53 the things StrictMode catches are not
theoretical.

```kotlin
if (BuildConfig.DEBUG) {
    StrictMode.setThreadPolicy(
        StrictMode.ThreadPolicy.Builder()
            .detectDiskReads()
            .detectDiskWrites()
            .detectNetwork()
            .detectCustomSlowCalls()
            .penaltyLog()
            .build()
    )
    StrictMode.setVmPolicy(
        StrictMode.VmPolicy.Builder()
            .detectLeakedSqlLiteObjects()
            .detectLeakedClosableObjects()
            .detectActivityLeaks()
            .penaltyLog()
            .build()
    )
}
```

Move off the main thread, with a bounded dispatcher — four cores means an unbounded thread pool
just causes contention:

```kotlin
// Bound background work to the hardware you actually have
private val ioDispatcher = Dispatchers.IO.limitedParallelism(4)

viewModelScope.launch {
    val rows = withContext(ioDispatcher) { repo.loadPage(page) }   // off main
    _uiState.update { it.copy(rows = rows) }                       // back on main
}
```

Specific things that must never touch the main thread here:

| Operation | Instead |
|---|---|
| Any SharedPreferences/DataStore **read** at startup | DataStore + `first()` in a coroutine |
| Room query | `suspend` DAO or `Flow` |
| JSON parse | streaming parser on `ioDispatcher` |
| Bitmap decode | `ioDispatcher` (and downsampled — `memory.md` §3) |
| `SimpleDateFormat` / `DateTimeFormatter` construction in a loop or a bind | hoist; construct once |
| Regex compilation | hoist to a `val` |
| File I/O of any size | `ioDispatcher` |

---

## 5. Cold start

On a wrist-worn device the user glances, acts, and drops their arm. They may open the app dozens
of times a shift, and the process is likely to have been killed between each (`memory.md` §4).
**Cold start is not a launch-day metric here — it is the dominant interaction cost.**

```bash
PKG=com.example.smallscreen
for i in $(seq 1 10); do
  adb shell am force-stop $PKG
  adb shell am start-activity -W -n $PKG/.MainActivity | grep -E 'TotalTime|WaitTime'
done
```

What to do about it:

- **Baseline Profiles.** Highest-value single change on an in-order core, because it removes
  interpretation and JIT from the startup path. Mandatory for Compose, valuable for Views.
- **Nothing heavy in `Application.onCreate()`.** It is on the critical path of every launch. Audit
  `androidx.startup` initialisers (`memory.md` §7) — libraries add them silently.
- **Lazy-init everything not needed for the first frame**: analytics, DB warmup, network clients.
- **No disk read before the first frame.** Not preferences, not a config file.
- **R8 with `proguard-android-optimize.txt`** for release.
- Consider whether your process should stay warm at all. If the workflow is "scan, confirm, done,
  repeat", a **foreground service** for the duration of a shift may be more appropriate than being
  killed and cold-started forty times — but weigh that against `battery-power.md` §5, because a
  foreground service also keeps you alive to burn power.

---

## 6. Animation

Zebra: `"Minimize animations"`. Google's Wear guidance is blunter — animations keep the **screen**
on, and screen-on is the highest-impact battery event after network.

- **Transitions ≤ 150–200 ms.** Long enough to show causality, short enough not to delay the user.
- **No looping or idle animations.** No pulsing buttons, no shimmer placeholders, no animated
  spinners that run for seconds. Google's guidance: if a loop is genuinely required, **pause
  between loops for at least as long as the animation itself**.
- **Animate only compositor-friendly properties** — `alpha`, `translation`, `scale`, `rotation`.
  Never animate a property that triggers layout (width, height, margins, padding, text size) on
  this CPU.
- **Prefer no animation to a janky one.** At 230 dp there is no room for motion to add much, and a
  stuttering transition reads as a broken device.

```kotlin
// Compose: cheap, bounded, compositor-friendly
val alpha by animateFloatAsState(
    targetValue = if (visible) 1f else 0f,
    animationSpec = tween(durationMillis = 150),
    label = "fade",
)
```

---

## 7. Thermal throttling

A sealed, wrist-worn plastic device the size of a watch has **no heatsink and no airflow**, and it
is pressed against a human body. Sustained load will throttle, and the frequency you measured in a
30-second test is not the frequency you get in hour six of a shift.

```kotlin
// API 29+: react instead of being surprised
val pm = getSystemService(PowerManager::class.java)
pm.addThermalStatusListener(mainExecutor) { status ->
    when (status) {
        PowerManager.THERMAL_STATUS_MODERATE,
        PowerManager.THERMAL_STATUS_SEVERE -> reduceWork()   // stop prefetch, drop frame rate
        PowerManager.THERMAL_STATUS_CRITICAL -> stopNonEssentialWork()
        else -> resumeNormalWork()
    }
}
```

Test it honestly: run your heaviest workflow **continuously for 30+ minutes on device** and watch
whether frame times drift. Continuous camera/scanning workloads are the usual trigger.

---

## 8. Scanning and camera — the heaviest thing most of these apps do

- Use **DataWedge** where you can. It runs the decode in Zebra's own service; your app receives a
  broadcast. That is dramatically cheaper than running a decode loop in your process, and it is
  the reason DataWedge exists. *(Its configuration is out of scope for this skill — see the
  `datawedge-*` skills.)*
- If you must drive the camera yourself: **Camera API2**, and the WS50 supports **only JPEG,
  PRIVATE, YUV_420_888, YV12** (`device-matrix.md` §2.6). A pipeline assuming `NV21` fails on
  device.
- **Never analyse every frame.** Drop to the newest frame and skip the backlog
  (`STRATEGY_KEEP_ONLY_LATEST`); on four A53s a per-frame analyser will fall behind and stay behind.
- **Close every `ImageProxy`/`Image` exactly once**, promptly. A missed close stalls the pipeline.
- **Analyse at the smallest resolution that decodes reliably** — not the preview resolution and
  certainly not the sensor resolution.
- **Stop the camera the moment it is not needed.** It is simultaneously the biggest CPU, memory and
  battery consumer available to you.

---

## 9. Wear OS notes

- Google lists **high CPU usage** as a **High** battery-impact event, and names the mitigation:
  **"Consume flows using Jetpack Compose."**
- **Keep CPU usage short and batch related operations to maximise idle time** — the SoC can only
  reach its deep idle states if you leave it alone in long, uninterrupted stretches. Ten small
  wakeups cost far more than one batched one of the same total work.
- **Use Health Services rather than `SensorManager`** so the platform can batch sensor delivery.
- Inspect what runs while the screen is off:

  ```bash
  adb shell dumpsys activity service WearableService   # Data Layer traffic
  adb shell dumpsys sensorservice                      # who is still registered
  ```

---

## 10. Checklist

- [ ] Core count and max frequency read from the device; no assumption of a big core
- [ ] Refresh rate confirmed; frame budget written down
- [ ] `dumpsys gfxinfo` janky-frame rate **< 1 %** on the primary workflow, measured on device
- [ ] View hierarchy ≤ 4 levels; no nested weighted `LinearLayout`; no `RelativeLayout`
- [ ] Overdraw checked with `debug.hwui.overdraw`; no dark red regions
- [ ] StrictMode clean in debug
- [ ] Baseline Profile generated and shipped
- [ ] Cold start measured on device across 10 runs; `Application.onCreate()` audited
- [ ] No animation longer than ~200 ms; no idle loops
- [ ] Thermal listener wired; 30-minute sustained-load test run
- [ ] Camera analysis downsampled, latest-frame-only, and stopped when idle
