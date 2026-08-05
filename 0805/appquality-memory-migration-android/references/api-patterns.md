# API patterns — the platform APIs these steps emit

> **Compatibility gate — resolve before selecting a step.** Applying a version-gated step outside its
> range is a regression, not an improvement.
>
> | API or behaviour | Version scope | Outside scope |
> |---|---|---|
> | `TRIM_MEMORY_UI_HIDDEN`, `TRIM_MEMORY_BACKGROUND` | All supported versions | The only two levels still delivered |
> | Legacy `TRIM_MEMORY_*` levels | Delivered up to Android 13 | **Not delivered from Android 14; deprecated in Android 15.** STEP-05 must not remove them while the fleet floor is below 14 |
> | `ApplicationExitInfo`, `getHistoricalProcessExitReasons` | **API 30+** | STEP-06 does not apply below 30; guard it |
> | `MemoryLimiter:AnonSwap` exit description | **Android 17+** | The mechanism does not exist earlier; do not claim it |
> | `ProfilingManager` | Android 15+ | Triggers `TRIGGER_TYPE_OOM` / `TRIGGER_TYPE_ANOMALY` are Android 17+ |
> | `ImageDecoder`, `setTargetSampleSize`, low-memory policy | API 28+ | Below 28 use `BitmapFactory` with `inSampleSize` |
> | `ActivityManager.isLowRamDevice`, `memoryClass` | All supported versions | — |
> | 16 KB page alignment by default | AGP 8.5.1+ | Below that, alignment is not automatic |
> | R8 full mode default | AGP 8.0+ | Verify the flag is not disabled |
>
> **Reviewed:** 2026-08-04. Zebra publishes device commitments for Android 16, 18 and 19; Android 17
> is not listed per device, so treat per-SKU availability of Memory Limiter as unknown and confirm it.
> Do not infer compatibility outside this gate. Provenance: [sources.md](sources.md).

## 1. Which ceiling a step is aimed at

Three deaths, three ceilings, three units. A step aimed at one does not help the others, and mixing
units when verifying proves nothing.

| Death | Counted | Ceiling | Steps that target it |
|---|---|---|---|
| `OutOfMemoryError` | Java heap only | `dalvik.vm.heapgrowthlimit` | STEP-02, STEP-03 |
| `MemoryLimiter:AnonSwap` (A17+) | cgroup anonymous + swap | `memory.high`, `memory.swap.max` | STEP-01, STEP-02, STEP-03, STEP-07 |
| Low-memory kill | System-wide pressure | `MemAvailable` | STEP-01, STEP-02, plus process and service reduction |

**Verify in the unit the step targets.** `RssAnon` from the process status file is the number for
memory-limit survival; Java heap is the number for `OutOfMemoryError`.

## 2. Anonymous versus clean file-backed memory — why STEP-01 and STEP-02 work

Reclaimed anonymous pages are compressed into zRAM, not evicted, so they continue to occupy physical
RAM. Clean file-backed pages can simply be dropped and re-read.

| Kind | Under pressure | Cost |
|---|---|---|
| Clean file-backed — database pages, mapped archive segments | Dropped | Cheap |
| Anonymous — Java heap, in-memory collections, decoded bitmaps | Compressed, never freed | Expensive; what the kill is named for |

So moving a large map into the database is not merely "a smaller cache": it **converts anonymous bytes
into clean file-backed bytes**, which is why it usually beats shrinking the map. Same for streaming a
parse instead of materialising it. This is the mechanism behind the two highest-value steps, and it is
worth stating to a developer who asks why a bounded cache is not enough.

File-backed memory still counts toward the limit, so this is not a loophole. It changes what the
kernel does when it needs the memory back — the difference between a slowdown and a kill.

## 3. Pressure handling — STEP-05

Only two levels are delivered. Google's own shape is a threshold comparison, not equality matching:

```kotlin
override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
        // release UI-tied memory: bitmap caches, playback buffers, animation resources
    }
    if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
        // release background-processing memory
    }
}
```

**The trap:** a `when (level)` block over the legacy constants looks diligent and releases nothing on
Android 14 or later. But those branches are **live** below Android 14. So STEP-05 is a *rewrite* to
the threshold shape, not a deletion, unless the fleet floor is Android 14 or higher. Getting this
backwards removes working code from part of the fleet.

Ownership and cleanup: implement it on `Application`, release only what can be rebuilt, and make the
release path idempotent — it can be invoked more than once.

## 4. Kill forensics — STEP-06

Memory-limit kills carry **no stack trace**. The only signal is the exit reason plus a description
containing `MemoryLimiter:AnonSwap`.

```kotlin
val am = getSystemService(ActivityManager::class.java)
am.getHistoricalProcessExitReasons(packageName, 0, 10).forEach { info ->
    if (info.reason == ApplicationExitInfo.REASON_OTHER &&
        info.description?.contains("MemoryLimiter") == true) {
        telemetry.logMemoryLimitKill(info.description, info.timestamp)
    }
}
```

Lifecycle and ownership: read once at startup, off the main thread; treat entries as historical rather
than live; guard the whole block on API 30+. Reading exit reasons has no side effects, so repeating it
is safe, but de-duplicate by timestamp before reporting so one kill is not counted twice.

## 5. Device tiering — STEP-07

One binary across a 1 GB to 8 GB fleet needs different budgets. Tier from what the device reports,
never from a model string.

```kotlin
val am = getSystemService(ActivityManager::class.java)
val constrained = am.isLowRamDevice || am.memoryClass <= 128
```

Drive cache sizes, bitmap configuration, prefetch depth, and page size from that single decision.

## 6. Bitmap decoding — STEP-03

`bytes = width × height × bytesPerPixel`, where ARGB_8888 is 4 and RGB_565 is 2. A 12 MP image is
48 MB at ARGB_8888; downsampled to display size it is roughly 3.5 MB.

- Two-pass decode: bounds only, then a sampled decode. The bounds pass allocates no pixels.
- `RGB_565` halves the cost and is correct wherever transparency is not needed.
- On API 28+ prefer `ImageDecoder` with a target sample size and the low-memory policy.
- Ownership: whoever creates a bitmap outside a managed pool must release it and drop the reference.

## 7. Caveats

- `largeHeap` does not fix an allocation. On Android 17 it reaches the cgroup limit **sooner**,
  converting a stack-traced error into an untraceable kill. STEP-09 removes it.
- Streaming changes failure timing. A partial parse can leave partial data, so batched writes belong
  in a transaction.
- Bounding a cache changes latency, not correctness — unless the code depended on the cache holding
  everything. Check for that before applying STEP-02.
- Absolute memory values are not comparable across devices; deltas on one device are.

Unsafe changes to refuse: [anti-patterns.md](anti-patterns.md). Step recipes:
[migration-steps.md](migration-steps.md).
