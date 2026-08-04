# Toolkit choice — XML Views or Compose

> **Provenance.** Zebra's recommendation of `ConstraintLayout` "for better performance" is quoted
> from the WS50 Programmer's Guide. Wear Compose versions and component names are from
> developer.android.com (`device-matrix.md` §6.2). **No quantitative claim about Compose's memory or
> startup cost on WS50/WS501 appears in any source consulted** — §3 therefore tells you how to
> measure it rather than asserting a number.
>
> **Sources for this file** (full register: `device-matrix.md` §7):
> - **Z1** WS50 Programmer's Guide — `ConstraintLayout` recommendation, 1 GB RAM, Android 11 AOSP
>   https://techdocs.zebra.com/emdk-for-android/13-0/guide/ws50_programming/
> - **Z4** WS501 spec sheet — 3 GB RAM, QC2290
>   https://www.zebra.com/us/en/products/spec-sheets/mobile-computers/wearable/ws501.html
> - **W4** Wear Compose release notes — **1.6.0 (2026-03-25)**, Navigation3
>   https://developer.android.com/jetpack/androidx/releases/wear-compose
> - **W5** Wear Compose Material 3 — https://developer.android.com/jetpack/androidx/releases/wear-compose-m3
> - **W9** Use Compose on Wear OS — https://developer.android.com/training/wearables/compose
> - **W1** Conserve power — "Consume flows using Jetpack Compose"
>   https://developer.android.com/training/wearables/apps/power
> - Baseline Profiles — https://developer.android.com/topic/performance/baselineprofiles/overview
> - Macrobenchmark — https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview

---

## 1. The decision rule

| Target | Default toolkit | Why |
|---|---|---|
| **Wear OS 6 watch** | **Compose — Wear Compose Material 3** | Not really a choice. The Wear component set (`ScreenScaffold`, `TransformingLazyColumn`, tiles via ProtoLayout M3, Material 3 Expressive) is Compose-first. The Views path is legacy and receives no new components. |
| **Zebra WS501** (3 GB) | **Either.** Compose is comfortable | 3 GB of RAM and a QC2290 absorb the runtime cost. Choose on team skill, not on device limits. |
| **Zebra WS50** (1 GB) | **XML Views + `ConstraintLayout`** | 1 GB shared with the kernel, launcher and the whole Zebra stack. Zebra explicitly recommends ConstraintLayout for performance. Compose is *permissible* — but only behind a measured baseline (§3). |
| **One binary for WS50 + WS501** | **XML Views** | The binary must satisfy the 1 GB device. Build to the floor. |
| **One codebase for Zebra *and* Wear OS** | see §5 | Share the ViewModel/domain layer; do **not** try to share the UI. |

**The short version:** on Wear OS use Compose. On WS501 use whatever your team is good at. On the
1 GB WS50, Views is the conservative default and Compose is a decision you must justify with
measurements on the device.

**What this rule is not.** It is not "Compose is too heavy for small devices." Compose runs
perfectly well on low-end hardware when it is configured correctly — Baseline Profiles enabled,
R8 on, recomposition under control. The rule reflects that on the WS50 you have *no margin* to
absorb a misconfiguration, and that Views has a smaller floor and a shorter list of ways to get it
wrong.

---

## 2. What actually differs on constrained hardware

| Dimension | XML Views | Compose |
|---|---|---|
| Runtime library footprint | in the platform | shipped **in your APK** |
| Cold-start work | inflate XML | initialise the Compose runtime + first composition |
| Startup mitigation | — | **Baseline Profiles are close to mandatory** |
| Per-frame cost model | measure → layout → draw over a **view tree** | **recomposition** of changed scopes |
| Dominant failure mode | **deep hierarchies** causing repeated measure passes | **over-recomposition** from unstable state |
| Debuggability of that failure | Layout Inspector, `dumpsys gfxinfo` | recomposition counts in Layout Inspector |
| Memory per screen | view objects | composition nodes + slot table |
| Text rendering | platform `TextView` | Compose text stack |

Two honest points:

1. **Compose's cost is mostly fixed, not per-screen.** You pay for the runtime once. That means the
   penalty is worst for a tiny single-screen app — exactly the shape of a wearable app — and best
   amortised by a larger one.
2. **Views' cost is mostly per-screen and grows with nesting.** A 6-level-deep `LinearLayout` tree
   on a low-tier CPU is slower than an equivalent Compose screen. Views is only cheaper if you keep
   it flat, which is precisely why Zebra names ConstraintLayout.

---

## 3. Decide by measurement, not by argument

If you want Compose on the WS50, prove it. Build the same representative screen both ways and
compare on a real device — the emulator will not tell you the truth about a 1 GB device.

```bash
PKG=com.example.smallscreen

# ---- 1. APK size, per-library ----
./gradlew :app:assembleRelease            # always measure RELEASE (R8 on)
# then: Android Studio > Build > Analyze APK...

# ---- 2. Steady-state memory of one screen ----
adb shell am start -n $PKG/.MainActivity
adb shell dumpsys meminfo $PKG            # read TOTAL PSS and "Java Heap"

# ---- 3. Cold start, 10 runs (this is where Compose regresses without profiles) ----
for i in $(seq 1 10); do
  adb shell am force-stop $PKG
  adb shell am start-activity -W -n $PKG/.MainActivity | grep TotalTime
done

# ---- 4. Jank on the real screen ----
adb shell dumpsys gfxinfo $PKG framestats
# see cpu-performance.md §2 for how to read this
```

**Acceptance gate for choosing Compose on a 1 GB WS50** — all four must hold on device:

- `dumpsys meminfo` TOTAL PSS for a typical screen leaves headroom against your budget
  (`memory.md` §2)
- p95 cold start is acceptable for the workflow (a picker relaunching between tasks feels every ms)
- `gfxinfo` shows no frame-deadline misses during the app's main interaction
- the app survives the low-memory kill test in `memory.md` §5

If any fails, use Views. That is not a defeat; it is the constraint doing its job.

---

## 4. Getting each toolkit right

### 4.1 XML Views on a small screen

- **`ConstraintLayout`, flat.** Zebra's stated recommendation. Target a hierarchy **≤ 4 levels
  deep**. Nested `LinearLayout` with weights is the classic double-measure trap — a weighted
  `LinearLayout` measures its children twice, and nesting them multiplies that.
- **`ViewStub`** for anything not shown on first frame (error states, rare panels). It costs nothing
  until inflated.
- **`merge`** as the root of an included layout to avoid a redundant wrapper.
- **`RecyclerView` with stable IDs** and `setHasFixedSize(true)` when the row height is constant.
- **`ViewBinding`, not `findViewById`, and not Kotlin synthetics** (removed).
- **No `WebView`.** It is one of the heaviest objects on Android and there is no room to render web
  content on 230 dp anyway.
- Avoid `RelativeLayout` for anything non-trivial — it measures twice by design.

```kotlin
// ViewStub: the error panel costs nothing until something fails
private var errorView: View? = null

private fun showError(message: String) {
    val view = errorView ?: binding.errorStub.inflate().also { errorView = it }
    view.findViewById<TextView>(R.id.error_text).text = message
    view.isVisible = true
}
```

### 4.2 Compose on a small screen

- **Baseline Profiles — treat as mandatory.** This is the single highest-value Compose optimisation
  on a low-tier CPU, because it lets ART skip interpreting/JIT-ing the startup path.

  ```kotlin
  // app/build.gradle.kts
  plugins { id("androidx.baselineprofile") }

  dependencies {
      implementation("androidx.profileinstaller:profileinstaller:<latest>")
      baselineProfile(project(":baselineprofile"))
  }
  ```

- **R8 / minification on for release, always.** Without it you ship the whole Compose runtime
  unshrunk.

  ```kotlin
  buildTypes {
      release {
          isMinifyEnabled = true
          isShrinkResources = true
          proguardFiles(
              getDefaultProguardFile("proguard-android-optimize.txt"),
              "proguard-rules.pro",
          )
      }
  }
  ```

- **Keep state reads as deep as possible.** Reading state high in the tree recomposes everything
  below it. Pass lambdas, not values, when the value changes often.

  ```kotlin
  // ✗ recomposes the whole screen on every tick
  @Composable
  fun Screen(count: Int) { Header(); Body(); Footer(count) }

  // ✓ only Footer's scope recomposes
  @Composable
  fun Screen(count: () -> Int) { Header(); Body(); Footer(count) }
  ```

- **Stable types only across composable boundaries.** An unstable parameter (a `List`, a
  non-`@Immutable` data holder from another module) forces recomposition even when equal. Use
  `ImmutableList` (kotlinx.collections.immutable) or annotate your models.

- **`derivedStateOf`** for values computed from state, so downstream scopes only recompose when the
  *derived* value changes.

- **Never allocate in a composable body.** No list building, no formatting, no `SimpleDateFormat`.
  Hoist into `remember` or the ViewModel — a composable body can run many times per second.

- **`collectAsStateWithLifecycle()`**, not `collectAsState()`, so flows stop collecting when the
  screen is not visible. On a battery-critical device this is not a micro-optimisation.

- **Verify recomposition counts** in Layout Inspector's *Recomposition counts* column. A counter
  climbing while the screen is idle is a bug, and on this hardware it is a battery bug
  (`battery-power.md` §4).

- **Skip `Modifier` allocation in loops** — hoist shared modifiers out of `items { }`.

### 4.3 Wear OS Compose specifics

Use the Wear artifacts, not the phone ones — this is a frequent and consequential mistake:

```kotlin
// ✓ Wear
implementation("androidx.wear.compose:compose-material3:1.6.0")
implementation("androidx.wear.compose:compose-foundation:1.6.0")
implementation("androidx.wear.compose:compose-navigation:1.6.0")

// ✗ NOT androidx.compose.material3 — phone Material 3 on a watch gives wrong
//   sizing, wrong shapes, no curvature support, and no swipe-to-dismiss.
```

- `AppScaffold` at the app level, `ScreenScaffold` per screen (handles `TimeText` and scroll
  coordination).
- `TransformingLazyColumn` + `rememberTransformationSpec()` for lists.
- `rememberResponsiveColumnPadding` (Horologist) for percentage-based padding — pass it to **both**
  `ScreenScaffold`'s and `TransformingLazyColumn`'s `contentPadding`.
- Google's power guidance explicitly lists **"Consume flows using Jetpack Compose"** as the
  mitigation for high CPU usage on Wear.

---

## 5. If you must target both Zebra and Wear OS

They are different platforms with different UI toolkits, different navigation models, different
power models, and different service availability (Wear has GMS; Zebra does not). Share the parts
that are genuinely shared:

```
:domain          ← pure Kotlin. Entities, use cases. No Android deps.
:data            ← repositories, Room, network. Android but no UI.
:ui-shared       ← ViewModels + UiState. No toolkit imports.
   ├── :app-zebra   ← XML Views, ConstraintLayout, hardware key handling, DataWedge
   └── :app-wear    ← Wear Compose M3, tiles, complications, Health Services
```

**Share:** domain, data, ViewModels, `UiState` shapes, formatting, validation.
**Do not share:** layouts, navigation, notification/feedback code, power management, anything
touching GMS.

Trying to share the UI layer between a square non-GMS 230 dp Android 11 device and a round GMS
Wear OS 6 watch produces a codebase serving neither — which is the same adaptive-app failure mode
this skill rejects, just at the module level.
