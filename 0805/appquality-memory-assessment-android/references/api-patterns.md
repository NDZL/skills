# API patterns — memory ceilings, units, and read-only platform counters

> **Compatibility gate — resolve before selecting a route.**
>
> | Platform behaviour | Version scope | If outside scope |
> |---|---|---|
> | `onTrimMemory` legacy levels delivered | Up to Android 13 | From Android 14 they are **not delivered**; deprecated in Android 15 |
> | `TRIM_MEMORY_UI_HIDDEN`, `TRIM_MEMORY_BACKGROUND` | All supported versions | The only two still delivered |
> | Memory Limiter (`MemoryLimiter:AnonSwap` kills) | **Android 17 and higher** | Below 17 the mechanism does not exist; do not attribute kills to it |
> | `ProfilingManager`, `TRIGGER_TYPE_OOM`, `TRIGGER_TYPE_ANOMALY` | `ProfilingManager` Android 15+; those triggers Android 17+ | Unavailable below; advisory only |
> | 16 KB page size devices | Android 15+ | Device-chosen, not app-chosen |
> | `ActivityManager.isLowRamDevice`, `memoryClass` | All supported versions | — |
> | `Debug.MemoryInfo.getMemoryStat` keys | API 23+ | — |
> | `ApplicationExitInfo` | API 30+ | Below 30, kill forensics are unavailable |
>
> **Reviewed:** 2026-08-04. Zebra publishes device commitments for Android 16, 18 and 19, so any
> device reaching 17 or later inherits Memory Limiter. Android 17 is not itself listed per device in
> the Zebra matrix — treat per-SKU availability as unknown and confirm it. Do not infer compatibility
> outside this gate.

This skill only ever **reads** these values. Emitting code that calls them is the migration
capability's job.

## 1. Three deaths, three ceilings, three units

An app can die three distinct ways, against three different ceilings, measured in three
incompatible units. **Comparing a number to the wrong ceiling is the most common analytical error in
this area** and silently invalidates otherwise careful work.

| Death | What is counted | Ceiling | Where to read it |
|---|---|---|---|
| `OutOfMemoryError` | Java/Dalvik heap only | `dalvik.vm.heapgrowthlimit`, or `heapsize` with `largeHeap` | `getprop`; `ActivityManager.memoryClass` / `largeMemoryClass` |
| `MemoryLimiter:AnonSwap` (A17+) | cgroup v2: anonymous + swap, with file-backed also counted toward `memory.high` | `memory.high`, `memory.swap.max` | `am memory-limiter status`; the vendor configuration file |
| Low-memory killer | System-wide pressure against the process priority score | `MemAvailable` and system thresholds | `/proc/meminfo`; usually you are a victim, not the cause |

Consequences that follow directly:

1. **PSS is the wrong unit for enforcement.** PSS distributes shared pages proportionally so
   system-wide totals do not double-count; it is slow to compute, and Memory Limiter does not enforce
   on it. Android's own guidance states that RSS "is better for tracking changes in memory
   allocation". Use PSS to answer *who is using this device*; use RSS and cgroup counters to answer
   *am I near my limit*.
2. **A Java heap figure says nothing about Memory Limiter.** A 3 MB Java heap alongside large native
   bitmap allocations is nowhere near `OutOfMemoryError` and can still be lethal under Memory Limiter.
   Track both independently.
3. **You cannot page your way out.** Reclaimed anonymous pages go to **zRAM, compressed — not
   evicted**. Compressed anonymous memory still occupies physical RAM. Anonymous memory can be made
   smaller, never free, which is why the kill is named `AnonSwap`.

**The single number to track for Android 17 survival is `RssAnon`.**

## 2. How Memory Limiter decides

- A system service using Linux cgroup v2, integrated with the activity manager.
- Sets `memory.high` (a *soft* limit → throttling and reclaim, not an instant kill) and
  `memory.swap.max`.
- Counts **both** file-backed and anonymous memory. The kernel evicts clean pages first, then swaps
  anonymous pages.
- On breach: reclaim and swap pressure, so **degradation comes before death**; sustained allocation
  past swap capacity then causes allocation failure.
- Limits differ by process visibility:

| State group | Examples | Limit |
|---|---|---|
| Visible | top, bound-top, important-foreground, top-sleeping | "a more generous memory limit" |
| Not visible | foreground service, bound foreground service, important-background, service, receiver, home, last-activity | "a more restrictive limit" |
| Cached | cached activity, cached empty | frozen, then maximally reclaimed |
| Unrestricted | persistent, persistent-UI | exempt |

Two consequences that matter for assessment:

1. **The limits are the device vendor's to define.** The configuration lives in the vendor partition,
   so on a Zebra device the thresholds are Zebra's values. The published example is for a device with
   at least 14 GB of RAM and tells you nothing about a 3 GB handheld. **Read the real value.** If the
   configuration file is missing, unreadable, or invalid, Memory Limiter is disabled on that device —
   which changes the risk picture per SKU.
2. **A foreground service sits in the *not visible* bucket**, which gets the *more restrictive* limit.
   An app doing its heaviest allocation in a background sync therefore receives its **tightest**
   budget at its **heaviest** moment. Assess the not-visible peak specifically; reporting only the
   interactive peak flatters the app exactly where it is weakest.

## 3. Anonymous versus clean file-backed memory

Not all bytes are equally dangerous.

| Kind | What the kernel can do under pressure | Cost |
|---|---|---|
| Clean, file-backed (mapped APK segments, SQLite pages, resources) | **Drop it**, re-read from flash if needed | Cheap |
| Anonymous (Java heap, in-memory collections, decoded bitmaps) | Must compress into zRAM, or fail | Expensive, and what the kill is named for |

So a strategic direction falls out: **shift bytes from anonymous into clean file-backed memory**. This
is why "let the database be your cache" outperforms merely shrinking a map — it converts expensive
bytes into cheap ones. File-backed memory still counts toward the limit, so this is not a loophole;
it changes what the kernel does when it needs the memory back, which is the difference between a
slowdown and a kill.

## 4. Read-only counters this skill uses

| What | Where | Notes |
|---|---|---|
| `RssAnon`, `RssFile`, `VmRSS`, `VmSwap` | `/proc/<pid>/status` | Cheap, per-process, correct unit. `RssAnon` is the headline. |
| `memory.current`, `memory.high`, `memory.swap.current` | the process cgroup v2 directory | The actual enforcement values. Path varies by build — locate it rather than assuming. |
| Enforced limits and event counts | `am memory-limiter status` | Android 17+ only |
| PSS breakdown, `Views:`, `Activities:` | `dumpsys meminfo <package>` | Attribution and leak indicators, not enforcement |
| `summary.java-heap`, `summary.total-pss` | `Debug.MemoryInfo.getMemoryStat` | API 23+, in-app |
| `memoryClass`, `largeMemoryClass`, `isLowRamDevice` | `ActivityManager` | Device tiering inputs |
| Kill forensics | `ApplicationExitInfo.getReason()` / `getDescription()` | A Memory Limiter kill reports `REASON_OTHER` with a description containing `MemoryLimiter:AnonSwap` |

Exact commands and the sampling protocol: [measurement.md](measurement.md).

## 5. Caveats

- `Activities:` greater than the number actually open indicates a leak; so does `Views:` growing
  across repeated navigation. Both are indicators, not proof.
- `am kill <package>` simulates the low-memory killer. `force-stop` is more aggressive than the
  system's own behaviour and will mislead you about restoration.
- Absolute values are not comparable across devices. Deltas on one device are.
- Every figure needs its tuple recorded: device, RAM tier, OS build, unit, process state, scenario.

Unsafe practices to avoid while assessing: [anti-patterns.md](anti-patterns.md). Provenance for every
quoted behaviour: [sources.md](sources.md).
