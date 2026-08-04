# Anti-patterns — what to refuse, and what to do instead

> **Provenance.** Each entry cites the reference file holding the underlying quoted evidence.
> This file introduces no new device facts; it is the refusal list.

Every item here is something a competent phone developer does *correctly on a phone*. That is what
makes them dangerous: they arrive as good habits.

---

## 0. The meta anti-pattern: shrinking a phone app

> **Zebra, verbatim:** `"Its two-inch display and 1GB RAM require significant modifications to`
> `existing apps, UIs and workflows."`

Note **workflows**. The three failure modes, in increasing order of how often they happen:

| Approach | Result |
|---|---|
| Port the phone app, shrink the layout | Unusable. Targets too small, text truncated, memory blows up. |
| Make the phone app *adaptive* down to 230 dp | Works, badly. Every screen is a compromise; you carry breakpoints, density buckets and rotation handling you never use; you still have phone-shaped workflows. |
| **Design the workflow for the wrist, then build only that** | The point of this skill. |

**Why "adaptive" specifically fails here**, even though it sounds like the responsible choice:

- Adaptive design *preserves* the information architecture and varies the presentation. But at
  230 dp the information architecture is exactly what has to change — a phone screen holding a
  form, a list and a summary must become **three screens**, not one squeezed screen.
- Adaptive layouts encode "what to hide as we get smaller." Small-screen-first encodes **"what is
  the one thing this screen is for."** Those produce different apps.
- You pay real cost for optionality you never exercise: density buckets, rotation, breakpoint
  logic, a wider test matrix, larger APK — on a device with 8 GB of flash and 1 GB of RAM.

**Test for whether you actually committed:** delete every layout qualifier, every density bucket
except `xhdpi`, and lock the orientation. If the app breaks, you built an adaptive app.

---

## 1. Layout and UI

| ❌ Anti-pattern | Why it fails here | ✅ Instead |
|---|---|---|
| Bottom navigation bar | ~56 dp is **24 %** of a 230 dp canvas spent on chrome | Linear wizard; one hardware Back |
| Navigation drawer / hamburger | needs an edge swipe and an affordance you have no room for | flatten to ≤ 2 levels |
| Tabs | 3 labels at 12 sp across 206 dp are unreadable | one screen per thing |
| Multi-column layouts | 206 dp does not divide | single column, always |
| A form with 4+ fields | the SIP covers the screen; the timeout fires mid-entry | one field per screen, or eliminate typing |
| Free-text entry as the primary input | slowest possible input on a wrist | **scan**, pick-from-list, or voice |
| Snackbar with an action | covers ~25 % of the screen | make it a screen state |
| Progress dialog | blocks a screen that has nothing else to show | inline state |
| Validation errors under each field | no vertical room | validate on submit; show as a screen state |
| Placeholder/shimmer skeletons | an idle animation that holds the screen on | a static "Loading" line |
| Text below 12 sp | Zebra: **`"No text should be smaller than 12pt"`** | 12 sp floor; 14 sp body |
| Touch targets under 48 dp | a fingertip is 8–10 mm ≈ 51–65 dp | 48 dp floor / 60 dp normal / 80 dp at edges |
| **Using the "30 × 30 dp" figure from Zebra's guide** | contradicts the same guide's 48 dp floor and its own fingertip figures — see `device-matrix.md` §4 | 48 dp floor |
| Corner-tucked controls | accuracy is *worse* at the bezel | Zebra says go **larger** near edges: 80 dp |
| Colour as the only status signal | gloves, sunlight, safety glasses, colour-blindness | colour **+** icon **+** word |
| Dark grey (`#121212`) "dark theme" | AMOLED pixels stay lit | **`#FF000000`** — pixels off |
| Blue as the primary accent | Zebra names blue as the hardest colour for the panel | light-on-black neutral; save colour for status |
| Rotation / split-screen support | Zebra says avoid split-screen; nobody rotates a wrist device | `portrait`, `resizeableActivity="false"` |
| `ScalingLazyColumn` on the Zebra square screen | curvature compensation for a bezel that isn't there | plain `LazyColumn` / `RecyclerView` |
| Phone `androidx.compose.material3` on Wear | wrong sizes, no swipe-to-dismiss, no curvature | `androidx.wear.compose:compose-material3` |

Detail: `screen-layout.md`, `wear-os-surfaces.md`.

---

## 2. Workflow

| ❌ Anti-pattern | Why it fails here | ✅ Instead |
|---|---|---|
| A screen that takes > 15 s to complete | the 10–15 s screen timeout fires mid-task | one decision per screen |
| Requiring the user to compare 3+ values | ~8 lines of body text total | show the comparison's *result* |
| "Are you sure?" on routine actions | doubles taps on the slowest input device you have | confirm only destructive/irreversible actions |
| Long scrolling lists | ~3 rows visible; nobody scrolls 200 rows on a wrist | scan-to-find, or server-side ranking |
| "Load more" pagination button | costs a whole row and a tap | endless scroll, or a better query |
| Onboarding carousels / tutorials | nobody reads 5 screens on a watch | make step 1 self-evident |
| Assuming the user keeps their arm raised | it is physically tiring within seconds | glance, act, drop |
| Deep hierarchies (> 2 levels) | no breadcrumb room; users get lost | flat, linear |

---

## 3. Memory

| ❌ Anti-pattern | Why it fails here | ✅ Instead |
|---|---|---|
| `android:largeHeap="true"` to silence an OOM | destabilises a 1 GB device shared with the Zebra stack; converts a reproducible crash into a random one | fix the allocation |
| Decoding an image at full sensor size | one 12 MP decode ≈ **48 MB** | `inSampleSize` to target size; `RGB_565` |
| Shipping mdpi→xxxhdpi drawables | dead weight on 8 GB flash | **`xhdpi` only** + vectors |
| Default image-cache size | library defaults are sized for phones | set explicitly; tier on `isLowRamDevice` |
| Loading a full result set | 5 000 objects for a 3-row viewport | Paging 3 / bounded query |
| Buffering a whole HTTP response as a `String` | a 5 MB body can cost 15 MB+ | stream-parse straight to the DB |
| Unbounded `HashMap` cache | grows until the LMK intervenes | `LruCache` with an explicit size |
| Keeping workflow state only in a ViewModel field | the process **will** be killed; the user loses the pick | `SavedStateHandle` + durable storage per step |
| Not nulling Fragment `ViewBinding` | leaks the whole view tree | null it in `onDestroyView` |
| Registering a DataWedge receiver in `onCreate` only | holds the Activity for the process lifetime | `onStart` / `onStop` pair |
| Any GMS/Play Services dependency | **non-GMS device** — it cannot work, and it is a large silent addition | see §6 |
| `WebView` | one of the heaviest objects on Android; nothing to render at 230 dp | native UI |

Detail: `memory.md`.

---

## 4. CPU

| ❌ Anti-pattern | Why it fails here | ✅ Instead |
|---|---|---|
| "It's only 20 ms, the UI thread is fine" | 20 ms on a modern phone core is 60–100 ms on an A53 — **4–6 dropped frames** | everything non-layout off the main thread |
| Assuming the scheduler will save you | **there are no big cores** — 4× Cortex-A53, in-order | fix the work, not the placement |
| Nested weighted `LinearLayout` | measures children twice **per level** | flat `ConstraintLayout` (Zebra's own recommendation) |
| `RelativeLayout` for complex layouts | always measures twice | `ConstraintLayout` |
| Shipping Compose without a Baseline Profile | in-order cores are hit hardest by interpretation/JIT | generate and ship one |
| Release build without R8 | ships the unshrunk runtime | `isMinifyEnabled = true` |
| Analysing every camera frame | four A53s fall behind and stay behind | latest-frame-only, downsampled |
| Constructing formatters/regex in a bind or loop | allocation on the hot path | hoist to a `val` |
| Reading prefs/disk before the first frame | on the cold-start critical path, every launch | async; nothing heavy in `Application.onCreate()` |
| Long or looping animations | jank **and** screen-on time | ≤ 200 ms; no idle loops |
| Benchmarking on an emulator or a phone | tells you nothing about an A53 | measure on the target device |
| Unbounded thread pools | 4 cores; extra threads are contention | `limitedParallelism(4)` |

Detail: `cpu-performance.md`.

---

## 5. Battery

| ❌ Anti-pattern | Why it fails here | ✅ Instead |
|---|---|---|
| `FLAG_KEEP_SCREEN_ON` | screen is the #2 power draw; defeats the timeout that makes 800 mAh last 10 h | scope to one screen, clear in `onPause` |
| **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, or an MDM battery whitelist** | **directly contrary to Zebra's stated guidance**; Doze is what buys the shift | WorkManager; a scoped foreground service |
| Polling every 30 s | keeps the radio out of idle permanently | long intervals; sync when charging |
| Sync on a timer regardless of state | network is ranked **Very high** impact | `setRequiresCharging(true)` + `UNMETERED` |
| Sending full state on every change | each transmission wakes the radio (and, on Wear, the phone) | send deltas that change the UI |
| Manual wakelocks | ranked Medium and nearly always avoidable | let WorkManager/JobScheduler hold it |
| Continuous camera/scanner | the most expensive subsystem, all three budgets at once | trigger-driven; release in `onPause` |
| Boosting brightness for legibility | undoes the largest available saving | contrast and type size |
| Sensors unregistered only in `onDestroy` | keeps running while backgrounded | `onStop`; verify with `dumpsys sensorservice` |
| Large white or blue surfaces | worst case for AMOLED drive current | true black + light text |
| (Wear) tiles/complications refreshing every few minutes | fastest route to blowing 3.2 %/h | **≥ 2 hours**, or push |
| Claiming battery life without measuring | estimates are not evidence | a full unplugged shift on device |

Detail: `battery-power.md`.

---

## 6. Platform — the non-GMS trap

**WS50 has no Google Mobile Services** (verified); **assume the same for WS501 but confirm it** —
its GMS status is `UNVERIFIED` in the sources behind this skill (`device-matrix.md` §2.7). This is
not a small-screen issue; it arrives with the same device and is the most common cause of "it
initialises fine on my phone."

| ❌ Will not work | ✅ Instead |
|---|---|
| Firebase Cloud Messaging | long-interval pull; sync when charging; MDM push |
| Play Services ML Kit (unbundled) | Zebra AI Suite, or ML Kit **bundled** variants |
| Google Maps SDK | offline/alternative maps, or omit |
| Google Sign-In | enterprise auth |
| Play Store / in-app updates | MDM / StageNow |
| Play Integrity, Play Billing | not applicable |
| Fused location provider | framework `LocationManager` |
| `GoogleApiAvailability` checks | remove |

```bash
# Confirm before you argue about it — expect NO output
adb shell pm list packages | grep -E 'com.google.android.gms|com.android.vending'
```

Detail: `device-matrix.md` §2.7.

---

## 7. Process and architecture

| ❌ Anti-pattern | Why it fails here | ✅ Instead |
|---|---|---|
| Assuming your process survives backgrounding | on 1 GB shared with the Zebra stack, it will not — and **Home is a hardware button** | treat every backgrounding as process death |
| Testing restore with `force-stop` | more aggressive than the LMK; misleads you | `adb shell am kill $PKG` |
| Sharing the UI layer between Zebra and Wear OS | square/round, AOSP/GMS, Views/Compose, keys/swipe | share domain + data + ViewModels only |
| Ignoring hardware Back | **no navigation bar** — the user is trapped | `onBackPressedDispatcher` |
| Overriding `onBackPressed()` | deprecated; breaks predictive back | `onBackPressedDispatcher.addCallback` |
| Assuming the Back button is where you left it | customers remap buttons via MX/Key Programmer | also offer an on-screen exit from any flow that can strand |
| Adding libraries "for convenience" | every one is APK bytes, classes, and often a startup initialiser | audit `./gradlew :app:dependencies` |
| Trusting a spec sheet over the device | fleets differ in OS, BSP and battery variant | read it: `device-matrix.md` §5 |

---

## 8. The refusal test

Before generating code for a small-screen app, check:

1. Is any touch target **< 48 dp**? → refuse and resize.
2. Is any text **< 12 sp**? → refuse and resize.
3. Does the layout assume more than **230 dp** in either axis (Zebra) or fewer than **192 dp**
   (Wear)? → refuse and redesign.
4. Is there a **bottom nav bar, drawer, or tab row**? → refuse; flatten.
5. Does any screen need **> 15 s** to complete? → split it.
6. Is a bitmap decoded **without** `inSampleSize`? → refuse.
7. Is `largeHeap`, `FLAG_KEEP_SCREEN_ON`, or `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` present? →
   refuse.
8. Is any **GMS** dependency present for a Zebra target? → refuse.
9. Is workflow state held only in memory? → refuse; persist per step.
10. Does the code ship **density buckets other than `xhdpi`**, or handle rotation, for a Zebra
    target? → you are building an adaptive app. Stop and re-read §0.
