# Sources, scope, ownership, and redistribution

**Reviewed:** 2026-08-04. Re-verify the device and OS rows before each release; Zebra device
configurations and platform behaviour move independently of this skill.

**Active product-specific guide:** **none.** No maintained Zebra product-specific authoring or
memory guide was present in the authoring context for this skill, so none is claimed as authority.
The offering prefix `appquality-` is therefore **proposed vocabulary, not approved vocabulary**, and
is recorded here as an open decision rather than an established fact.

## Platform sources — primary

| ID | Official URL | Version scope | Reviewed | Derived guidance |
|---|---|---|---|---|
| A1 | https://developer.android.com/topic/performance/memory | Android 14–17 | 2026-08-04 | Legacy trim levels undelivered from Android 14, deprecated in Android 15; only `TRIM_MEMORY_UI_HIDDEN` and `TRIM_MEMORY_BACKGROUND` remain — MEM-PRESSURE-001. Services warning — MEM-PROC-001. Sparse containers, lite generated code, abstraction cost, compile-time injection — MEM-DATA-005/006, MEM-DEP-001/004. R8 configuration — MEM-BUILD-002/003 |
| A2 | https://developer.android.com/blog/posts/prioritizing-memory-efficiency-essential-steps-for-android-17 | Android 17 | 2026-08-04 | Memory-limit kills carry no stack trace; the `MemoryLimiter:AnonSwap` description string — MEM-OBS-001, S1. Image practices including the halved cost of `RGB_565` — MEM-BITMAP-001/002. Leak table — MEM-LIFECYCLE family. Baseline recommendation — MEM-OBS-004 |
| A3 | https://developer.android.com/about/versions/17/behavior-changes-all | Android 17 | 2026-08-04 | Limits derive from total device RAM; exit reason and description for detection; the three memory-limiter shell subcommands — measurement.md §2 |
| A4 | https://source.android.com/docs/core/perf/memory-limiter | Android 17+ | 2026-08-04 | cgroup v2 mechanism, soft high limit and swap cap, both file-backed and anonymous memory counted, visible/not-visible/cached/unrestricted limit groups, vendor configuration path and its units, system-UID exemption, disabled when configuration is absent or invalid — api-patterns.md §2, MEM-PROC-003 |
| A5 | https://developer.android.com/about/versions/17/features | Android 17 | 2026-08-04 | Out-of-memory and anomaly profiling triggers — MEM-OBS-003 |
| A6 | https://developer.android.com/guide/practices/page-sizes | Android 15+ | 2026-08-04 | 16 KB page size is device-selected; unaligned native libraries stop working in future releases; backcompat mode; AGP 8.5.1 alignment default; query the page size rather than hardcoding it — MEM-BUILD-005 |
| A7 | https://android-developers.googleblog.com/2025/07/transition-to-16-kb-page-sizes-android-apps-games-android-studio.html | Android 15+ | 2026-08-04 | Marginal device memory increase traded for a faster reclaim path; measured launch-time improvement under memory pressure — MEM-BUILD-005 rationale |
| A8 | https://developer.android.com/topic/performance/tracing/profiling-manager/trigger-based-capture | Android 15+ | 2026-08-04 | Trigger semantics — MEM-OBS-003 |
| A9 | https://googlesamples.github.io/android-custom-lint-rules/checks/index.md.html | Current | 2026-08-04 | Every Tier A check identifier in rule-catalogue.md |
| A10 | https://developer.android.com/topic/performance/memory-management | Current | 2026-08-04 | RSS, PSS and USS definitions; RSS is better for tracking allocation changes; clean cached pages are dropped while anonymous pages are compressed into zRAM; low-memory-killer priority ordering — api-patterns.md §1 and §3, axis 3 |
| A11 | https://developer.android.com/reference/android/os/Debug.MemoryInfo | API 23+ | 2026-08-04 | In-app memory statistic keys — api-patterns.md §4 |
| A12 | https://developer.android.com/jetpack/androidx/releases/benchmark | Current | 2026-08-04 | Benchmark harness for the release-regression axis — MEM-OBS-005 |

## Zebra sources — primary

| ID | Official URL | Version scope | Reviewed | Derived guidance |
|---|---|---|---|---|
| Z1 | https://techdocs.zebra.com/datawedge/15-0/guide/programmers-guides/usage-notes/ | DataWedge 15.0 | 2026-08-04 | Usage Notes, Device Functionality item 4: low device memory can stop scanning working, and the documented remedy is to fix the leak or uninstall the offending application — troubleshooting.md S4, MEM-LIFECYCLE-001 rationale |
| Z2 | https://www.zebra.com/android-versions | Current | 2026-08-04 | Per-device Android commitments including 16, 18 and 19; Android 17 is not listed per device, so per-SKU availability is recorded as unknown — api-patterns.md gate |
| Z3 | https://www.zebra.com/us/en/products/spec-sheets/mobile-computers/handheld/tc22-tc27.html | TC22/TC27 | 2026-08-04 | 6 GB/64 GB and 8 GB/128 GB configurations — tier table |
| Z4 | https://www.zebra.com/us/en/products/spec-sheets/mobile-computers/handheld/mc3300ax.html | MC3300ax | 2026-08-04 | 4 GB/32 GB — tier table |
| Z5 | https://www.zebra.com/us/en/products/spec-sheets/mobile-computers/handheld/ec50-ec55.html | EC50/EC55 | 2026-08-04 | 3 GB/32 GB and 4 GB/64 GB — tier table |
| Z6 | https://www.zebra.com/us/en/products/spec-sheets/mobile-computers/handheld/tc21-tc26.html | TC21/TC26 | 2026-08-04 | 4 GB/64 GB and 3 GB/32 GB — tier table |
| Z7 | https://www.zebra.com/gb/en/products/spec-sheets/mobile-computers/handheld/tc15.html | TC15 | 2026-08-04 | 4 GB/64 GB — tier table |
| Z8 | https://www.zebra.com/us/en/products/spec-sheets/mobile-computers/handheld/tc53-tc58.html | TC53/TC58 | 2026-08-04 | Up to 8 GB/128 GB — tier table |
| Z9 | https://techdocs.zebra.com/emdk-for-android/13-0/guide/ws50_programming/ | WS50 | 2026-08-04 | 1 GB shared among the kernel, launcher, vendor stack and other services — tier table, the one-tenant planning model |
| Z10 | https://techdocs.zebra.com/bestpractices/ | Current | 2026-08-04 | Guide inventory; **no dedicated Zebra memory best-practice guide exists**, which is a documentation gap this skill fills |

## Derivation boundary

Rules and thresholds in this skill are **derived** from the sources above plus arithmetic, and are
labelled in place. Specifically derived rather than quoted:

- The object-sizing model in `quantification.md` §2 — runtime layout arithmetic, accurate to roughly
  ±2× on absolute bytes and exact on the scaling exponent. **Calibrate against a heap dump.**
- Bitmap byte figures — `width × height × bytesPerPixel` arithmetic.
- The anonymous-versus-clean-file-backed strategy in `api-patterns.md` §3 — an inference from the
  quoted reclaim mechanics in A4 and A10. The mechanics are quoted; the synthesis is this skill's.
- Severity assignments, headroom bands, the five growth axes, and per-transaction accumulation — this
  skill's engineering judgement, not vendor guidance.

## Known gaps — state these rather than filling them

1. **No published memory threshold exists for any Zebra RAM tier.** The one public example is a device
   with at least 14 GB of RAM. Read the vendor configuration from the device.
2. **Whether Zebra ships a valid memory-limiter configuration at all** is unverified. If it is absent
   or invalid, the mechanism is disabled on that device, which materially changes risk per SKU.
3. **Which Zebra SKUs receive Android 17 or later, and when**, is unverified (Z2 lists 16, 18, 19).
4. **Per-process overhead of an extra process** on a Zebra build is unmeasured.
5. **The Java heap growth limit per Zebra SKU** is undocumented in the sources consulted. Read it.
6. Lazy-layout memory figures circulating publicly are third-party benchmarks; none is used here as
   authoritative.

## Ownership

- Skill owner: **UNASSIGNED — required before release.**
- Product owner: **UNASSIGNED — required before release.**
- Documentation owner: **UNASSIGNED — required before release.**

These are recorded as unresolved rather than invented. They block release, not authoring.

## Bundled material

| Item | Version | Derivation | License / redistribution basis |
|---|---|---|---|
| `../scripts/scan_memory_rules.py` | 1.0.0 | Original to this skill | Apache-2.0 **proposed, not confirmed** |
| `../scripts/estimate_object_size.py` | 1.0.0 | Original to this skill | Apache-2.0 **proposed, not confirmed** |
| `../scripts/collect_device_memory.sh` | 1.0.0 | Original; wraps documented platform shell commands (A3, A4, A10) | Apache-2.0 **proposed, not confirmed** |
| `../assets/test-fixtures/` | 1.0.0 | Original synthetic fixtures; no customer or third-party code | Apache-2.0 **proposed, not confirmed** |

No credentials, signing material, proprietary binaries, restricted models, or third-party code are
bundled. The licensing basis above is a proposal awaiting an accountable owner.
