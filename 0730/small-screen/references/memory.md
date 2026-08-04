# Memory — engineering inside 1 GB

> **Provenance.** The 1 GB figure and its sharing model are quoted verbatim from the WS50
> Programmer's Guide (`device-matrix.md` §2.2). Watch Face Format limits are from
> developer.android.com. **Per-device heap ceilings (`dalvik.vm.heapgrowthlimit`) and the
> `ro.config.low_ram` flag were not found in any source consulted — §1.2 tells you to read them
> off the device, and every budget in this file is expressed relative to what you read.** The
> budget table in §2 is a **recommended starting point, not a quoted figure.**
>
> **Sources for this file** (full register: `device-matrix.md` §7):
> - **Z1** WS50 Programmer's Guide — `"The RAM in the WS50 is limited to 1GB, which must be shared
>   among the Linux kernel, Android app launcher, the Zebra software stack and other services"`;
>   minimize concurrent apps; single-task UIs; camera formats
>   https://techdocs.zebra.com/emdk-for-android/13-0/guide/ws50_programming/
> - **Z4** WS501 spec sheet — 3 GB RAM / 32 GB Flash
>   https://www.zebra.com/us/en/products/spec-sheets/mobile-computers/wearable/ws501.html
> - **Z8** Zebra LifeGuard — WS50 as a non-GMS device (§7 here)
>   https://techdocs.zebra.com/lifeguard/about/
> - **W6** Optimize watch face memory usage — **10 MB ambient / 100 MB interactive**
>   https://developer.android.com/training/wearables/wff/memory-usage
> - **W1** Conserve power — share one database across app, tiles and complications
>   https://developer.android.com/training/wearables/apps/power
> - Manage your app's memory — https://developer.android.com/topic/performance/memory
> - LeakCanary — https://square.github.io/leakcanary/
> - Coil (image cache configuration) — https://coil-kt.github.io/coil/

---

## 1. What you actually get

### 1.1 The stated constraint

> `"The RAM in the WS50 is limited to 1GB, which must be shared among the Linux kernel, Android`
> `app launcher, the Zebra software stack and other services"`

Read the second clause carefully. **1 GB is not your budget — it is the whole machine's budget.**
Subtract, in order:

```
1024 MB   total physical RAM
  −       Linux kernel + drivers
  −       Android system_server, SurfaceFlinger, zygote
  −       Zebra software stack (DataWedge, MX, OEMInfo, EMDK services, StageNow agents…)
  −       the launcher
  −       any MDM agent the customer deploys
  ─────
  =       what is left, shared between your app and anything else running
```

The Zebra stack is not optional and not small. On a customer device there may also be an MDM
agent, a VPN client and a scanning middleware you did not know about. **The honest planning
assumption is that you are one tenant among several on a machine with less RAM than a 2013 phone.**

The WS501's 3 GB removes most of this pressure — but if one binary must serve both, you build to
the WS50.

### 1.2 Read your real budget — do not assume it

```bash
PKG=com.example.smallscreen

# Total and available physical memory
adb shell cat /proc/meminfo | head -3
#   MemTotal:  ~1048576 kB on a WS50
#   MemAvailable:  ← THIS is the number that matters

# Your per-process Java heap ceiling
adb shell getprop dalvik.vm.heapgrowthlimit   # ceiling WITHOUT android:largeHeap
adb shell getprop dalvik.vm.heapsize          # ceiling WITH android:largeHeap="true"

# Is Android itself treating this as a low-RAM device?
adb shell getprop ro.config.low_ram           # "true" => isLowRamDevice() == true

# What the Zebra stack is already consuming, before your app starts
adb shell dumpsys meminfo | head -40
```

`dalvik.vm.heapgrowthlimit` is **the** number to write on the wall. It is the hard ceiling on your
Java heap before `OutOfMemoryError`, and it is what every bitmap and cache decision in §3 is
measured against. On 1 GB Android devices it has historically fallen somewhere in the 96–192 MB
range, but **the WS50's actual value is not documented in any source consulted here — read it.**

In code:

```kotlin
val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

val heapLimitMb   = am.memoryClass          // MB before OOM (no largeHeap)
val largeHeapMb   = am.largeMemoryClass     // MB before OOM (with largeHeap)
val isLowRam      = am.isLowRamDevice       // true => degrade features, see §1.3

val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
val availMb = info.availMem / 1_048_576
val lowMem  = info.lowMemory                // system is already under pressure
```

Log these once at startup in debug builds. You will refer to them constantly.

### 1.3 Branch on `isLowRamDevice()`, don't ignore it

If `ro.config.low_ram=true`, Android disables some of its own features and expects apps to
cooperate. Use it as an explicit switch rather than discovering the consequences at runtime:

```kotlin
val tier = if (am.isLowRamDevice || am.memoryClass <= 128) Tier.CONSTRAINED else Tier.NORMAL

when (tier) {
    Tier.CONSTRAINED -> {
        imageCacheBytes = 2 * 1024 * 1024      // 2 MB
        bitmapConfig    = Bitmap.Config.RGB_565
        prefetchPages   = 0
        enableAnimations = false
    }
    Tier.NORMAL -> {
        imageCacheBytes = 8 * 1024 * 1024
        bitmapConfig    = Bitmap.Config.ARGB_8888
        prefetchPages   = 1
        enableAnimations = true
    }
}
```

This is also how one binary serves WS50 and WS501 honestly: same code, different budget, chosen
from what the device reports rather than from a model string.

---

## 2. Set a budget and hold yourself to it

Pick target numbers before you write code, and gate them in CI or in a manual pre-release check.
A workable starting point for a 1 GB device — adjust once you have read `memoryClass`:

| Metric | Target on WS50 (1 GB) | How to check |
|---|---|---|
| TOTAL PSS, steady state on a typical screen | **< 60 MB** | `dumpsys meminfo $PKG` |
| Java heap in use, steady state | **< 25 % of `memoryClass`** | `dumpsys meminfo $PKG` |
| Peak PSS during the heaviest operation | **< 50 % of `memoryClass`** | `dumpsys meminfo $PKG` during it |
| Bitmap memory total | **< 8 MB** | `dumpsys meminfo $PKG` → *Graphics* |
| Leaks after a full workflow + rotation-free restart | **zero** | LeakCanary |
| APK install size | as small as you can — 8 GB flash total | Analyze APK |

```bash
adb shell dumpsys meminfo $PKG
```

The lines that matter:

```
                   Pss    Private  Private
                 Total     Dirty    Clean
  Native Heap    xxxxx     xxxxx        0    ← bitmaps live here on modern Android
  Dalvik Heap    xxxxx     xxxxx        0    ← your objects
  ...
  TOTAL PSS:     xxxxx                        ← the headline number
  ...
  Views:            xx    ViewRootImpl:  x    ← a leak indicator; should be small and stable
  Activities:        x    AppContexts:   x    ← >1 activity after finishing = a leak
```

**`Activities:` greater than the number you actually have open is a leak.** On this device a leak
does not merely degrade performance over hours — it gets your process killed mid-workflow.

---

## 3. Bitmaps — where the memory actually goes

On a 230 dp screen this should be the *easiest* problem you have, and it is still the most common
cause of OOM. The arithmetic:

| Image | Config | Bytes |
|---|---|---|
| Full screen, 460 × 460 | ARGB_8888 | **~826 KB** |
| Full screen, 460 × 460 | RGB_565 | **~413 KB** |
| A 60 dp (120 px) icon | ARGB_8888 | ~56 KB |
| **A 12 MP camera photo, 4000 × 3000** | ARGB_8888 | **~48 MB** ← this is what kills you |
| The same photo, decoded to fit 460 px | ARGB_8888 | **~750 KB** |

**One unscaled camera decode can exceed a third of your entire heap.** Never decode at full size.

### 3.1 Always decode to the target size

```kotlin
fun decodeSampled(path: String, reqW: Int, reqH: Int): Bitmap? {
    // Pass 1: bounds only — allocates no pixels
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)

    var sample = 1
    while (bounds.outWidth / (sample * 2) >= reqW && bounds.outHeight / (sample * 2) >= reqH) {
        sample *= 2
    }

    // Pass 2: decode at 1/sample scale, in the cheaper config
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.RGB_565   // half the bytes; fine for opaque photos
    }
    return BitmapFactory.decodeFile(path, opts)
}
```

**`RGB_565` halves bitmap memory** and costs you the alpha channel plus some gradient banding.
On a 230 dp AMOLED viewed at arm's length, for photographic content, that trade is almost always
correct. Keep `ARGB_8888` only where you genuinely need transparency.

Prefer `ImageDecoder` on API 28+ for the same job:

```kotlin
val source = ImageDecoder.createSource(file)
val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
    val scale = maxOf(1, minOf(info.size.width / reqW, info.size.height / reqH))
    decoder.setTargetSampleSize(scale)
    decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM   // picks RGB_565 when it can
}
```

### 3.2 Ship the right drawables

- **`xhdpi` only.** The device asks for the 320 dpi bucket; every other bucket is dead weight in
  the APK on an 8 GB device.
- **Vector drawables for icons.** One asset, any size, negligible bytes.
- **WebP for photographic assets**, not PNG.
- **Never ship a 1080p splash or hero image.** The screen is 460 px wide.

### 3.3 Image-loading libraries

Coil or Glide are fine and both are better than hand-rolling — but **cap the cache explicitly**;
the defaults are sized for phones and will happily take a percentage of a heap you cannot spare.

```kotlin
ImageLoader.Builder(context)
    .memoryCache {
        MemoryCache.Builder(context)
            .maxSizeBytes(if (am.isLowRamDevice) 2 * 1024 * 1024 else 8 * 1024 * 1024)
            .build()
    }
    .diskCache {
        DiskCache.Builder()
            .directory(context.cacheDir.resolve("img"))
            .maxSizeBytes(16L * 1024 * 1024)   // 8 GB flash total — be modest
            .build()
    }
    .allowRgb565(true)
    .build()
```

---

## 4. Your process **will** be killed — design for it

This is the most important section in this file.

On a 1 GB device shared with the Zebra stack, the moment your app goes to background it becomes a
prime candidate for the low-memory killer. The WS50 also puts **Home on a hardware button that
always works** — a user *will* leave your app mid-workflow, and they will expect to come back to
where they were. If your state lives only in memory, the picker loses a half-finished pick and
blames the app.

**Assume every backgrounding is a process death.** Then it is never a bug.

### 4.1 Persist workflow state, don't just save UI state

```kotlin
class PickViewModel(
    private val savedState: SavedStateHandle,      // survives process death
    private val repo: PickRepository,              // survives everything (DB)
) : ViewModel() {

    // Small, transient UI state -> SavedStateHandle
    var scrollIndex: Int
        get() = savedState["scrollIndex"] ?: 0
        set(v) { savedState["scrollIndex"] = v }

    // Real workflow progress -> durable storage, written as it happens
    fun onItemPicked(item: Item, qty: Int) {
        viewModelScope.launch { repo.recordPick(item.id, qty) }   // committed immediately
    }
}
```

Rules:

- **`SavedStateHandle` for small UI state** (selected index, scroll position, a filter string).
  It survives process death; a plain ViewModel field does not.
- **A database or DataStore for anything the user would be annoyed to redo.** Write on each step,
  not at the end of the workflow.
- **Never hold the only copy of user input in a field.**
- Keep `onSaveInstanceState` payloads small — the Binder transaction has a hard limit and a
  `TransactionTooLargeException` on a background transition is an ugly, hard-to-reproduce crash.

### 4.2 Test it, every release

```bash
PKG=com.example.smallscreen

# 1. Start the app, get part-way through a workflow, then press Home.
# 2. Simulate the low-memory killer:
adb shell am kill $PKG            # kills like the LMK does (not force-stop)
# 3. Reopen from the launcher.
#    The user MUST land where they left off, with no data lost.

# Verify the process really died:
adb shell ps -A | grep $PKG
```

`am kill` is the correct simulation — `force-stop` is more aggressive than what the system does
and will mislead you about restoration behaviour.

---

## 5. Respond to memory pressure

Implement `onTrimMemory`. It is free, and it is how you avoid being the process the system picks.

```kotlin
class App : Application() {
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            // App is in background; the system is deciding who to kill.
            TRIM_MEMORY_UI_HIDDEN,
            TRIM_MEMORY_BACKGROUND -> {
                imageLoader.memoryCache?.clear()
                inMemoryCaches.clear()
            }
            // We are near the top of the kill list. Release everything releasable.
            TRIM_MEMORY_MODERATE,
            TRIM_MEMORY_COMPLETE -> {
                imageLoader.memoryCache?.clear()
                inMemoryCaches.clear()
                database.closeIfIdle()
            }
            // Foreground but the whole device is tight.
            TRIM_MEMORY_RUNNING_LOW,
            TRIM_MEMORY_RUNNING_CRITICAL -> {
                imageLoader.memoryCache?.trimToSize(0)
            }
        }
    }
}
```

Also release view-scoped references in `onDestroyView` / `onDestroy`:

```kotlin
override fun onDestroyView() {
    _binding = null          // ViewBinding in a Fragment: mandatory, or you leak the whole tree
    super.onDestroyView()
}
```

---

## 6. Data, not just pixels

- **Never load a full result set.** Use Paging 3, or a bounded query. A 5 000-row list on a screen
  showing three rows is 5 000 objects you have no use for.
- **Query only the columns you need.** `SELECT *` on a wide table materialises every column.
- **Stream, don't buffer.** Parse JSON with a streaming reader and write straight to the DB; do not
  build a `String` of the whole response then a full object graph of it. A 5 MB response can cost
  15 MB+ as a `String` plus objects.
- **Bound your Room cursor windows** for large queries, and close cursors deterministically.
- **Prefer primitives and arrays in hot paths.** `SparseArray`/`SparseIntArray` over
  `HashMap<Integer, …>` avoids boxing — this matters in loops, not in one-off code.
- **Bound every in-memory cache.** `LruCache` with an explicit size, never an unbounded `HashMap`.

---

## 7. Dependencies are memory

Every library is APK bytes, classes to load, and often a background initialiser you did not ask
for. On an 8 GB / 1 GB device, audit them.

- **Check for `androidx.startup` initialisers** you are paying for at cold start, and disable the
  ones you do not need:

  ```xml
  <provider
      android:name="androidx.startup.InitializationProvider"
      android:authorities="${applicationId}.androidx-startup"
      android:exported="false"
      tools:node="merge">
      <meta-data
          android:name="com.example.unused.SomeInitializer"
          tools:node="remove" />
  </provider>
  ```

- **No GMS libraries** — they will not work anyway (`device-matrix.md` §2.7), and a transitive
  Play Services dependency is a large silent addition.
- **Avoid reflection-heavy DI at runtime.** Prefer Hilt/Dagger (compile-time) over anything
  reflective.
- **No `WebView`, no in-app browser, no analytics SDK that spins up its own thread pool** unless
  the business genuinely requires it.
- Run `./gradlew :app:dependencies` and justify each top-level entry. On this hardware,
  "it was convenient" is not a justification.

---

## 8. Leaks

On a phone a leak is a slow degradation. Here it is a crash and a lost workflow.

```kotlin
// debugImplementation("com.squareup.leakcanary:leakcanary-android:<latest>")
```

The recurring offenders on this form factor:

| Leak | Fix |
|---|---|
| Fragment `ViewBinding` not nulled in `onDestroyView` | null it |
| Listener registered on a long-lived Zebra/system service, never unregistered | unregister in the mirror lifecycle callback |
| `BroadcastReceiver` (e.g. a DataWedge scan receiver) registered in `onCreate`, unregistered nowhere | register in `onStart`, unregister in `onStop` |
| Inner class / lambda holding an `Activity` | use `applicationContext`, or a `WeakReference` |
| Static `Context` | never |
| Coroutine in `GlobalScope` | `viewModelScope` / `lifecycleScope` |

The DataWedge receiver case is the one that bites on Zebra devices specifically — scan receivers
are easy to register once and forget, and they hold an Activity for the life of the process.

---

## 9. `largeHeap` — almost always the wrong answer

```xml
<!-- ✗ Do not do this to make an OOM go away -->
<application android:largeHeap="true" ... >
```

On a 1 GB device shared with the Zebra stack, `largeHeap` lets your process grow until it
destabilises the *whole device* — and the system's response is to kill something, quite possibly
you, at a less predictable moment. It converts a reproducible OOM into an intermittent
disappearance.

Fix the allocation instead: §3 (decode smaller), §6 (load less), §8 (stop leaking).

The one defensible use is a genuine, bounded, unavoidable large working set — a large image being
processed on-device, say — and even then, prefer streaming or native allocation with explicit
lifetime, and prove there is no alternative first.

---

## 10. Wear OS notes

- Watch faces using **Watch Face Format** have hard limits: **10 MB in ambient mode, 100 MB in
  interactive mode**. These are enforced, not advisory.
- Wear devices are commonly cited at **512 MB – 1 GB RAM**; per-model values are `UNVERIFIED`, so
  read them the same way (§1.2).
- **Share one database across the app, its tiles and its complications.** Google states this
  explicitly. Three surfaces with three caches on a wearable is a self-inflicted wound.
- Tiles and complications run **in other processes** and are memory-constrained in their own right
  — keep their data model tiny and precomputed.

---

## 11. Checklist

- [ ] `memoryClass`, `largeMemoryClass`, `isLowRamDevice`, `heapgrowthlimit` **read from the real
      device** and recorded
- [ ] A written PSS/heap budget, checked before each release
- [ ] No bitmap decoded at greater than display size; `RGB_565` where alpha is not needed
- [ ] `xhdpi` drawables only; vectors for icons; WebP for photos
- [ ] Image cache size set explicitly, tiered on `isLowRamDevice`
- [ ] `onTrimMemory` implemented and actually releasing something
- [ ] Workflow state durable; `adb shell am kill` test passes with zero data loss
- [ ] LeakCanary clean through a full workflow
- [ ] `largeHeap` **not** set
- [ ] Dependency list audited; no GMS; no WebView
- [ ] `dumpsys meminfo` shows `Activities:` and `Views:` stable after repeated navigation
